package br.com.extrator.bootstrap.pipeline;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import br.com.extrator.observabilidade.LogStoragePaths;
import br.com.extrator.observabilidade.LogRetentionPolicy;
import br.com.extrator.plataforma.auditoria.dominio.ExecutionPlanContext;
import br.com.extrator.suporte.concorrencia.ExecutionTimeoutException;
import br.com.extrator.suporte.configuracao.ConfigEtl;
import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.observabilidade.ExecutionContext;

public class IsolatedStepProcessExecutor {
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final String MAIN_CLASS_NAME = "br.com.extrator.bootstrap.Main";
    private static final String CHILD_PROCESS_PROPERTY = "etl.process.isolated.child";
    private static final long HEARTBEAT_INTERVAL_MS = 30_000L;
    private static final LoggerConsole log = LoggerConsole.getLogger(IsolatedStepProcessExecutor.class);

    public ProcessExecutionResult executar(final ApiType apiType,
                                           final LocalDate dataInicio,
                                           final LocalDate dataFim,
                                           final String entidade) throws Exception {
        return executar(apiType, dataInicio, dataFim, entidade, FaultMode.NONE, resolverTimeoutPadrao(apiType, entidade));
    }

    public ProcessExecutionResult executar(final ApiType apiType,
                                           final LocalDate dataInicio,
                                           final LocalDate dataFim,
                                           final String entidade,
                                           final FaultMode faultMode) throws Exception {
        return executar(apiType, dataInicio, dataFim, entidade, faultMode, resolverTimeoutPadrao(apiType, entidade));
    }

    public ProcessExecutionResult executar(final ApiType apiType,
                                           final LocalDate dataInicio,
                                           final LocalDate dataFim,
                                           final String entidade,
                                           final Duration timeout) throws Exception {
        return executar(apiType, dataInicio, dataFim, entidade, FaultMode.NONE, timeout);
    }

    public ProcessExecutionResult executar(final ApiType apiType,
                                           final LocalDate dataInicio,
                                           final LocalDate dataFim,
                                           final String entidade,
                                           final FaultMode faultMode,
                                           final Duration timeout) throws Exception {
        final Path logFile = criarArquivoLog(apiType, entidade, faultMode);
        final List<String> comando = construirComando(apiType, dataInicio, dataFim, entidade, faultMode);
        final ProcessBuilder processBuilder = new ProcessBuilder(comando);
        processBuilder.directory(LogStoragePaths.PROJECT_ROOT.toFile());
        processBuilder.redirectOutput(logFile.toFile());
        processBuilder.redirectError(logFile.toFile());

        final Process process = processBuilder.start();
        final Thread shutdownHook = criarShutdownHook(process, apiType, entidade);
        final boolean shutdownHookRegistrado = registrarShutdownHook(shutdownHook);
        final Duration timeoutAplicado = timeout == null || timeout.isNegative() || timeout.isZero()
            ? resolverTimeoutPadrao(apiType, entidade)
            : timeout;
        final long inicioNanos = System.nanoTime();
        final long deadlineNanos = System.nanoTime() + timeoutAplicado.toNanos();
        long proximoHeartbeatNanos = inicioNanos
            + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(HEARTBEAT_INTERVAL_MS);
        try {
            while (true) {
                try {
                    final long restanteNanos = deadlineNanos - System.nanoTime();
                    if (restanteNanos <= 0L) {
                        destruirProcesso(process);
                        throw new ExecutionTimeoutException(
                            "Processo isolado "
                                + apiType.name().toLowerCase(Locale.ROOT)
                                + " excedeu timeout de "
                                + timeoutAplicado.toMillis()
                                + " ms. Log: "
                                + logFile.toAbsolutePath()
                                + ". Ultimas linhas: "
                                + lerTail(logFile)
                        );
                    }
                    final long esperaAtualMs = Math.max(
                        1L,
                        Math.min(250L, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(restanteNanos))
                    );
                    if (process.waitFor(esperaAtualMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                        break;
                    }
                    final long agoraNanos = System.nanoTime();
                    if (agoraNanos >= proximoHeartbeatNanos) {
                        log.console(
                            "[INFO] Step isolado {}:{} em andamento ha {} min. Log runtime: {}",
                            apiType.name().toLowerCase(Locale.ROOT),
                            entidade == null || entidade.isBlank() ? "all" : entidade,
                            Math.max(1L, java.util.concurrent.TimeUnit.NANOSECONDS.toMinutes(agoraNanos - inicioNanos)),
                            LogStoragePaths.APP_RUNTIME_DIR.resolve("extrator-esl.log").toAbsolutePath()
                        );
                        proximoHeartbeatNanos = agoraNanos
                            + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(HEARTBEAT_INTERVAL_MS);
                    }
                } catch (final InterruptedException e) {
                    destruirProcesso(process);
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
            final int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new IllegalStateException(
                    "Processo isolado " + apiType.name().toLowerCase(Locale.ROOT)
                        + " falhou com exit_code=" + exitCode
                        + " | log=" + logFile.toAbsolutePath()
                        + ". Ultimas linhas: " + lerTail(logFile)
                );
            }
            return new ProcessExecutionResult(process.pid(), logFile);
        } finally {
            if (process.isAlive()) {
                destruirProcesso(process);
            }
            if (shutdownHookRegistrado) {
                removerShutdownHook(shutdownHook);
            }
        }
    }

    protected List<String> construirComando(final ApiType apiType,
                                            final LocalDate dataInicio,
                                            final LocalDate dataFim,
                                            final String entidade,
                                            final FaultMode faultMode) throws URISyntaxException {
        final List<String> comando = new ArrayList<>();
        comando.add(resolverExecutavelJava());
        comando.add("-Dfile.encoding=UTF-8");
        comando.add("-Dsun.stdout.encoding=UTF-8");
        comando.add("-Dsun.stderr.encoding=UTF-8");
        comando.add("-DETL_BASE_DIR=" + LogStoragePaths.PROJECT_ROOT);
        comando.add("-Detl.base.dir=" + LogStoragePaths.PROJECT_ROOT);
        comando.add("-Dextrator.logger.console.mirror=true");
        comando.add("-Detl.process.isolation.enabled=false");
        comando.add("-D" + CHILD_PROCESS_PROPERTY + "=true");
        comando.add("-Detl.parent.execution.id=" + ExecutionContext.currentExecutionId());
        comando.add("-Detl.parent.command=" + ExecutionContext.currentCommand());
        comando.add("-Detl.parent.cycle.id=" + ExecutionContext.currentCycleId());
        if (ExecutionContext.hasRetryContext()) {
            comando.add("-Detl.parent.retry.attempt=" + ExecutionContext.currentRetryAttempt());
            comando.add("-Detl.parent.retry.max_attempts=" + ExecutionContext.currentRetryMaxAttempts());
        }
        adicionarSystemPropertiesConfiguracao(comando);
        adicionarPlanosExecucao(comando);

        final Path jarAtual = resolverJarAtual();
        if (jarAtual != null && Files.exists(jarAtual)) {
            comando.add("-jar");
            comando.add(jarAtual.toString());
        } else {
            final String classpath = montarClasspathExecucao();
            if (classpath == null || classpath.isBlank()) {
                throw new IllegalStateException("Nao foi possivel resolver classpath para execucao isolada.");
            }
            comando.add("-cp");
            comando.add(classpath);
            comando.add(MAIN_CLASS_NAME);
        }

        comando.add("--executar-step-isolado");
        comando.add(apiType.name().toLowerCase(Locale.ROOT));
        comando.add(dataInicio.toString());
        comando.add(dataFim.toString());
        comando.add(entidade == null || entidade.isBlank() ? "all" : entidade);
        if (faultMode != null && faultMode != FaultMode.NONE) {
            comando.add("--fault");
            comando.add(faultMode.cliValue());
        }
        return comando;
    }

    private String montarClasspathExecucao() throws URISyntaxException {
        final List<String> entradas = new ArrayList<>();
        final Path codeSource = resolverCodeSourceAtual();
        if (codeSource != null && Files.exists(codeSource)) {
            entradas.add(codeSource.toString());
        }

        entradas.addAll(resolverClasspathDoClassLoader(Thread.currentThread().getContextClassLoader()));

        final String runtimeClasspath = java.lang.management.ManagementFactory.getRuntimeMXBean().getClassPath();
        if (runtimeClasspath != null && !runtimeClasspath.isBlank()) {
            entradas.add(runtimeClasspath);
        }

        final String classpathAtual = System.getProperty("java.class.path");
        if (classpathAtual != null && !classpathAtual.isBlank() && !classpathAtual.equals(runtimeClasspath)) {
            entradas.add(classpathAtual);
        }

        return String.join(java.io.File.pathSeparator, entradas.stream().distinct().toList());
    }

    private List<String> resolverClasspathDoClassLoader(final ClassLoader classLoader) {
        final List<String> entradas = new ArrayList<>();
        ClassLoader atual = classLoader;
        while (atual != null) {
            try {
                final java.lang.reflect.Method metodo = atual.getClass().getMethod("getURLs");
                final Object retorno = metodo.invoke(atual);
                if (retorno instanceof URL[] urls) {
                    for (final URL url : urls) {
                        if (url == null || !"file".equalsIgnoreCase(url.getProtocol())) {
                            continue;
                        }
                        entradas.add(Path.of(url.toURI()).toAbsolutePath().normalize().toString());
                    }
                }
            } catch (final ReflectiveOperationException | IllegalArgumentException | URISyntaxException ignored) {
                // Sem suporte a getURLs neste classloader; segue para o pai.
            }
            atual = atual.getParent();
        }
        return entradas;
    }

    private Path criarArquivoLog(final ApiType apiType,
                                 final String entidade,
                                 final FaultMode faultMode) throws IOException {
        LogStoragePaths.ensureBaseDirectories();
        final Path dir = LogStoragePaths.ISOLATED_STEPS_DIR;
        Files.createDirectories(dir);
        final String nomeEntidade = entidade == null || entidade.isBlank()
            ? "all"
            : entidade.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        final String nomeFault = faultMode == null ? FaultMode.NONE.cliValue() : faultMode.cliValue();
        final String sufixoExecucao = resolverSufixoExecucaoArquivo();
        final Path arquivo = dir.resolve(
            "isolated_step_"
                + apiType.name().toLowerCase(Locale.ROOT)
                + "_"
                + nomeEntidade
                + "_"
                + nomeFault
                + sufixoExecucao
                + "_"
                + FILE_TS.format(LocalDateTime.now())
                + ".log"
        );
        if (!Files.exists(arquivo)) {
            Files.createFile(arquivo);
        }
        LogRetentionPolicy.retainRecentFiles(
            dir,
            LogStoragePaths.MAX_FILES_PER_BUCKET,
            path -> LogRetentionPolicy.hasExtension(path, ".log")
                && path.getFileName().toString().startsWith("isolated_step_")
        );
        return arquivo;
    }

    private Thread criarShutdownHook(final Process process, final ApiType apiType, final String entidade) {
        return new Thread(() -> {
            if (process != null && process.isAlive()) {
                log.console(
                    "[AVISO] Encerrando processo isolado {}:{} junto com a execucao principal.",
                    apiType.name().toLowerCase(Locale.ROOT),
                    entidade == null || entidade.isBlank() ? "all" : entidade
                );
                destruirProcesso(process);
            }
        }, "isolated-step-shutdown-" + (process == null ? "unknown" : process.pid()));
    }

    private boolean registrarShutdownHook(final Thread shutdownHook) {
        try {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            return true;
        } catch (final IllegalStateException e) {
            return false;
        }
    }

    private void removerShutdownHook(final Thread shutdownHook) {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (final IllegalStateException ignored) {
            // JVM ja esta em shutdown; o hook cuidara do encerramento do filho.
        }
    }

    private void destruirProcesso(final Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroy();
        try {
            if (!process.waitFor(ConfigEtl.obterTimeoutDestruicaoProcessoIsoladoMs(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(ConfigEtl.obterTimeoutDestruicaoProcessoIsoladoMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        } catch (final InterruptedException e) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    private void adicionarPlanosExecucao(final List<String> comando) {
        ExecutionPlanContext.exportarSystemProperties()
            .forEach((chave, valor) -> comando.add("-D" + chave + "=" + valor));
    }

    private String lerTail(final Path logFile) {
        try {
            final List<String> linhas = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            final int inicio = Math.max(0, linhas.size() - 8);
            return String.join(" | ", linhas.subList(inicio, linhas.size()));
        } catch (final IOException e) {
            return "log_indisponivel=" + logFile.toAbsolutePath();
        }
    }

    private Path resolverJarAtual() throws URISyntaxException {
        final String sunCommand = System.getProperty("sun.java.command", "");
        if (sunCommand != null && !sunCommand.isBlank()) {
            final String primeiroToken = extrairPrimeiroToken(sunCommand);
            if (primeiroToken.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                return resolverPathJar(primeiroToken);
            }
        }

        final String codeSource = IsolatedStepProcessExecutor.class.getProtectionDomain().getCodeSource().getLocation().toString();
        final int idx = codeSource.toLowerCase(Locale.ROOT).indexOf(".jar");
        if (idx >= 0) {
            String pathJar = codeSource.substring(0, idx + 4);
            if (pathJar.startsWith("jar:")) {
                pathJar = pathJar.substring(4);
            }
            if (pathJar.startsWith("nested:")) {
                pathJar = pathJar.substring(7);
            }
            return resolverPathJar(pathJar);
        }
        return null;
    }

    private Path resolverCodeSourceAtual() throws URISyntaxException {
        final String codeSource = IsolatedStepProcessExecutor.class.getProtectionDomain().getCodeSource().getLocation().toString();
        if (codeSource == null || codeSource.isBlank()) {
            return null;
        }
        if (codeSource.startsWith("file:")) {
            return Path.of(new java.net.URI(codeSource)).toAbsolutePath().normalize();
        }
        return Path.of(codeSource).toAbsolutePath().normalize();
    }

    private Path resolverPathJar(final String pathJar) throws URISyntaxException {
        if (pathJar == null || pathJar.isBlank()) {
            return null;
        }
        if (pathJar.startsWith("file:")) {
            return Path.of(new java.net.URI(pathJar)).toAbsolutePath().normalize();
        }

        String caminho = URLDecoder.decode(pathJar, StandardCharsets.UTF_8);
        if (caminho.length() >= 3
            && caminho.charAt(0) == '/'
            && Character.isLetter(caminho.charAt(1))
            && caminho.charAt(2) == ':') {
            caminho = caminho.substring(1);
        }
        return Path.of(caminho).toAbsolutePath().normalize();
    }

    private String extrairPrimeiroToken(final String comandoCompleto) {
        final String valor = comandoCompleto == null ? "" : comandoCompleto.trim();
        if (valor.isEmpty()) {
            return valor;
        }
        if (valor.startsWith("\"")) {
            final int fim = valor.indexOf('"', 1);
            if (fim > 1) {
                return valor.substring(1, fim);
            }
            return valor.substring(1);
        }
        final int espaco = valor.indexOf(' ');
        return espaco > 0 ? valor.substring(0, espaco) : valor;
    }

    private String resolverExecutavelJava() {
        final String javaHome = System.getProperty("java.home");
        final boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        final String nomeExecutavel = windows ? "java.exe" : "java";
        return Path.of(javaHome, "bin", nomeExecutavel).toAbsolutePath().normalize().toString();
    }

    private void adicionarSystemPropertiesConfiguracao(final List<String> comando) {
        final Set<String> chavesJaConfiguradas = new HashSet<>();
        for (final String argumento : comando) {
            if (argumento != null && argumento.startsWith("-D")) {
                final int separador = argumento.indexOf('=');
                final String chave = separador > 0
                    ? argumento.substring(2, separador)
                    : argumento.substring(2);
                chavesJaConfiguradas.add(chave);
            }
        }

        final List<String> chavesOrdenadas = System.getProperties()
            .stringPropertyNames()
            .stream()
            .filter(this::devePropagarSystemProperty)
            .sorted()
            .toList();

        for (final String chave : chavesOrdenadas) {
            if (chavesJaConfiguradas.contains(chave)) {
                continue;
            }
            final String valor = System.getProperty(chave);
            if (valor == null) {
                continue;
            }
            comando.add("-D" + chave + "=" + valor);
        }
    }

    private boolean devePropagarSystemProperty(final String chave) {
        if (chave == null || chave.isBlank()) {
            return false;
        }
        if (CHILD_PROCESS_PROPERTY.equals(chave)
            || "etl.process.isolation.enabled".equals(chave)
            || "ETL_PROCESS_ISOLATION_ENABLED".equals(chave)
            || chave.startsWith("etl.parent.")) {
            return false;
        }
        return chave.startsWith("API_")
            || chave.startsWith("ETL_")
            || chave.startsWith("api.")
            || chave.startsWith("etl.");
    }

    private Duration resolverTimeoutPadrao(final ApiType apiType, final String entidade) {
        if (apiType == ApiType.DATAEXPORT) {
            return ConfigEtl.obterTimeoutStepDataExport();
        }
        if (entidade != null && "faturas_graphql".equalsIgnoreCase(entidade)) {
            return ConfigEtl.obterTimeoutStepFaturasGraphQL();
        }
        if (entidade == null || entidade.isBlank() || "all".equalsIgnoreCase(entidade)) {
            return ConfigEtl.obterTimeoutStepGraphQLCompleto();
        }
        return ConfigEtl.obterTimeoutEntidadeGraphQL(entidade);
    }

    private String resolverSufixoExecucaoArquivo() {
        final String executionId = sanitizarComponenteArquivo(ExecutionContext.currentExecutionId());
        final String cycleId = sanitizarComponenteArquivo(ExecutionContext.currentCycleId());
        final StringBuilder sufixo = new StringBuilder();
        if (!executionId.isBlank()) {
            sufixo.append("_exec_").append(executionId);
        }
        if (!cycleId.isBlank()) {
            sufixo.append("_cycle_").append(cycleId);
        }
        return sufixo.toString();
    }

    private String sanitizarComponenteArquivo(final String valor) {
        if (valor == null || valor.isBlank() || "n/a".equalsIgnoreCase(valor)) {
            return "";
        }
        return valor
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9_-]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }

    public enum ApiType {
        GRAPHQL,
        DATAEXPORT
    }

    public enum FaultMode {
        NONE("none"),
        HANG_IGNORE_INTERRUPT("hang_ignore_interrupt"),
        ERROR("error");

        private final String cliValue;

        FaultMode(final String cliValue) {
            this.cliValue = cliValue;
        }

        public String cliValue() {
            return cliValue;
        }

        public static FaultMode fromCliValue(final String value) {
            if (value == null || value.isBlank()) {
                return NONE;
            }
            final String normalized = value.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "hang_ignore_interrupt" -> HANG_IGNORE_INTERRUPT;
                case "error" -> ERROR;
                default -> NONE;
            };
        }
    }

    public record ProcessExecutionResult(long pid, Path logFile) {
    }
}
