/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/extracao/daemon/DaemonLifecycleService.java
Classe  : DaemonLifecycleService (class)
Pacote  : br.com.extrator.comandos.extracao.daemon
Modulo  : Comando CLI (daemon)
Papel   : Implementa responsabilidade de daemon lifecycle service.

Conecta com:
- Main

Fluxo geral:
1) Recebe parametros de execucao em modo daemon.
2) Coordena ciclo, persistencia de estado e logs de runtime.
3) Controla retomada e historico de ciclos.

Estrutura interna:
Metodos principais:
- criarPadrao(...1 args): instancia ou monta estrutura de dados.
- DaemonLifecycleService(...3 args): realiza operacao relacionada a "daemon lifecycle service".
- localizarPidDaemonAtivo(): realiza operacao relacionada a "localizar pid daemon ativo".
- localizarProcessosAlvoParada(): realiza operacao relacionada a "localizar processos alvo parada".
- processoEhLoopDaemonAtivo(...1 args): realiza operacao relacionada a "processo eh loop daemon ativo".
- adicionarProcessoSeAtivo(...3 args): realiza operacao relacionada a "adicionar processo se ativo".
- localizarProcessosDaemonAtivos(): realiza operacao relacionada a "localizar processos daemon ativos".
- ehProcessoLoopDaemon(...1 args): realiza operacao relacionada a "eh processo loop daemon".
- ehComandoLoopDaemon(...1 args): realiza operacao relacionada a "eh comando loop daemon".
- prepararJarRuntime(...1 args): realiza operacao relacionada a "preparar jar runtime".
- ehCaminhoWindowsComBarraInicial(...1 args): realiza operacao relacionada a "eh caminho windows com barra inicial".
- extrairPrimeiroToken(...1 args): realiza operacao relacionada a "extrair primeiro token".
- resolverExecutavelJava(): realiza operacao relacionada a "resolver executavel java".
Atributos-chave:
- FLAG_SEM_FATURAS_GRAPHQL: campo de estado para "flag sem faturas graphql".
- FLAG_LOOP_DAEMON_RUN: campo de estado para "flag loop daemon run".
- stateStore: campo de estado para "state store".
- daemonStdoutFile: campo de estado para "daemon stdout file".
- runtimeDir: campo de estado para "runtime dir".
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.extracao.daemon;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;

import br.com.extrator.Main;

/**
 * Gerencia operacoes de ciclo de vida do processo daemon.
 */
public final class DaemonLifecycleService {
    private static final String FLAG_SEM_FATURAS_GRAPHQL = "--sem-faturas-graphql";
    private static final String FLAG_LOOP_DAEMON_RUN = "--loop-daemon-run";
    private static final String RUNTIME_JAR_PREFIX = "extrator-daemon-runtime";
    private static final String RUNTIME_JAR_SUFFIX = ".jar";
    private static final int MAX_RUNTIME_JARS = 3;

    private final DaemonStateStore stateStore;
    private final Path daemonStdoutFile;
    private final Path runtimeDir;

    public static DaemonLifecycleService criarPadrao(final DaemonStateStore stateStore) {
        return new DaemonLifecycleService(
            stateStore,
            DaemonPaths.DAEMON_STDOUT_FILE,
            DaemonPaths.RUNTIME_DIR
        );
    }

    public DaemonLifecycleService(final DaemonStateStore stateStore,
                                  final Path daemonStdoutFile,
                                  final Path runtimeDir) {
        this.stateStore = stateStore;
        this.daemonStdoutFile = daemonStdoutFile;
        this.runtimeDir = runtimeDir;
    }

    public Process startChildProcess(final List<String> comando) throws IOException {
        final ProcessBuilder processBuilder = new ProcessBuilder(comando);
        processBuilder.redirectOutput(Redirect.appendTo(daemonStdoutFile.toFile()));
        processBuilder.redirectError(Redirect.appendTo(daemonStdoutFile.toFile()));
        return processBuilder.start();
    }

    public OptionalLong localizarPidDaemonAtivo() {
        final OptionalLong pidArquivo = stateStore.readPidFile();
        if (pidArquivo.isPresent() && processoEhLoopDaemonAtivo(pidArquivo.getAsLong())) {
            return pidArquivo;
        }

        final OptionalLong pidEstado = stateStore.readPidState();
        if (pidEstado.isPresent() && processoEhLoopDaemonAtivo(pidEstado.getAsLong())) {
            return pidEstado;
        }

        return localizarProcessosDaemonAtivos().stream()
            .mapToLong(ProcessHandle::pid)
            .findFirst();
    }

    public List<ProcessHandle> localizarProcessosAlvoParada() {
        final Map<Long, ProcessHandle> processos = new LinkedHashMap<>();
        adicionarProcessoSeAtivo(processos, stateStore.readPidFile());
        adicionarProcessoSeAtivo(processos, stateStore.readPidState());
        for (final ProcessHandle processo : localizarProcessosDaemonAtivos()) {
            processos.putIfAbsent(processo.pid(), processo);
        }
        return new ArrayList<>(processos.values());
    }

    public boolean processoEhLoopDaemonAtivo(final long pid) {
        return ProcessHandle.of(pid)
            .filter(ProcessHandle::isAlive)
            .filter(this::ehProcessoLoopDaemon)
            .isPresent();
    }

    public void aguardarEncerramentoProcessos(final List<ProcessHandle> processos, final long timeoutMillis)
        throws InterruptedException {
        final long limiteMillis = System.currentTimeMillis() + Math.max(0L, timeoutMillis);
        while (System.currentTimeMillis() < limiteMillis) {
            final boolean algumVivo = processos.stream().anyMatch(ProcessHandle::isAlive);
            if (!algumVivo) {
                return;
            }
            Thread.sleep(300L);
        }
    }

    public List<String> construirComandoFilho(final boolean incluirFaturasGraphQL) throws URISyntaxException {
        final List<String> comando = new ArrayList<>();
        comando.add(resolverExecutavelJava());
        comando.add("-Dfile.encoding=UTF-8");
        comando.add("-Dsun.stdout.encoding=UTF-8");
        comando.add("-Dsun.stderr.encoding=UTF-8");
        comando.add("-Dextrator.logger.console.mirror=false");

        final Path jarAtual = resolverJarAtual();
        if (jarAtual != null && Files.exists(jarAtual)) {
            final Path jarRuntime = prepararJarRuntime(jarAtual);
            comando.add("-jar");
            comando.add(jarRuntime.toString());
            comando.add(FLAG_LOOP_DAEMON_RUN);
            if (!incluirFaturasGraphQL) {
                comando.add(FLAG_SEM_FATURAS_GRAPHQL);
            }
            return comando;
        }

        final String classpath = System.getProperty("java.class.path");
        if (classpath == null || classpath.isBlank()) {
            throw new IllegalStateException("Nao foi possivel resolver classpath para iniciar loop daemon.");
        }
        comando.add("-cp");
        comando.add(classpath);
        comando.add(Main.class.getName());
        comando.add(FLAG_LOOP_DAEMON_RUN);
        if (!incluirFaturasGraphQL) {
            comando.add(FLAG_SEM_FATURAS_GRAPHQL);
        }
        return comando;
    }

    private void adicionarProcessoSeAtivo(final Map<Long, ProcessHandle> processos, final OptionalLong pidOpt) {
        if (pidOpt.isEmpty()) {
            return;
        }
        ProcessHandle.of(pidOpt.getAsLong())
            .filter(ProcessHandle::isAlive)
            .filter(this::ehProcessoLoopDaemon)
            .ifPresent(processo -> processos.putIfAbsent(processo.pid(), processo));
    }

    private List<ProcessHandle> localizarProcessosDaemonAtivos() {
        return ProcessHandle.allProcesses()
            .filter(ProcessHandle::isAlive)
            .filter(this::ehProcessoLoopDaemon)
            .toList();
    }

    private boolean ehProcessoLoopDaemon(final ProcessHandle processo) {
        final ProcessHandle.Info info = processo.info();
        final StringBuilder comando = new StringBuilder();
        info.commandLine().ifPresent(comando::append);
        if (comando.length() == 0) {
            info.command().ifPresent(comando::append);
            info.arguments().ifPresent(args -> {
                for (final String arg : args) {
                    comando.append(' ').append(arg);
                }
            });
        }
        if (comando.length() == 0) {
            return false;
        }
        return ehComandoLoopDaemon(comando.toString());
    }

    private boolean ehComandoLoopDaemon(final String comandoCompleto) {
        final String normalizado = comandoCompleto.toLowerCase(Locale.ROOT).replace('\\', '/');
        if (!normalizado.contains(FLAG_LOOP_DAEMON_RUN)) {
            return false;
        }
        return normalizado.contains("extrator-daemon-runtime")
            || normalizado.contains("/target/extrator.jar")
            || normalizado.contains(" br.com.extrator.main ");
    }

    private Path prepararJarRuntime(final Path jarAtual) {
        final Path jarDestino = runtimeDir.resolve(
            RUNTIME_JAR_PREFIX + "-" + System.currentTimeMillis() + RUNTIME_JAR_SUFFIX
        );
        try {
            stateStore.ensureDaemonDirectory();
            if (!Files.exists(runtimeDir)) {
                Files.createDirectories(runtimeDir);
            }
            Files.copy(jarAtual, jarDestino, StandardCopyOption.REPLACE_EXISTING);
            limparRuntimesAntigos(jarDestino);
            return jarDestino.toAbsolutePath().normalize();
        } catch (final IOException e) {
            throw new IllegalStateException(
                "Falha ao preparar JAR runtime do daemon em " + jarDestino.toAbsolutePath() + ": " + e.getMessage(),
                e
            );
        }
    }

    private void limparRuntimesAntigos(final Path runtimeAtual) {
        try (var arquivos = Files.list(runtimeDir)) {
            final List<Path> runtimes = arquivos
                .filter(Files::isRegularFile)
                .filter(this::ehArquivoRuntimeJar)
                .sorted((a, b) -> Long.compare(obterUltimaModificacaoMillis(b), obterUltimaModificacaoMillis(a)))
                .toList();

            for (int i = MAX_RUNTIME_JARS; i < runtimes.size(); i++) {
                final Path candidato = runtimes.get(i);
                if (candidato.equals(runtimeAtual)) {
                    continue;
                }
                try {
                    Files.deleteIfExists(candidato);
                } catch (final IOException ignored) {
                    // Falha de limpeza nao impede inicializacao do daemon.
                }
            }
        } catch (final IOException ignored) {
            // Falha de limpeza nao impede inicializacao do daemon.
        }
    }

    private boolean ehArquivoRuntimeJar(final Path caminho) {
        final String nome = caminho.getFileName().toString();
        return nome.startsWith(RUNTIME_JAR_PREFIX) && nome.endsWith(RUNTIME_JAR_SUFFIX);
    }

    private long obterUltimaModificacaoMillis(final Path caminho) {
        try {
            return Files.getLastModifiedTime(caminho).toMillis();
        } catch (final IOException ignored) {
            return Long.MIN_VALUE;
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

        final String codeSource = Main.class.getProtectionDomain().getCodeSource().getLocation().toString();
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

    private Path resolverPathJar(final String pathJar) throws URISyntaxException {
        if (pathJar == null || pathJar.isBlank()) {
            return null;
        }
        if (pathJar.startsWith("file:")) {
            return Path.of(new java.net.URI(pathJar)).toAbsolutePath().normalize();
        }

        String caminho = URLDecoder.decode(pathJar, StandardCharsets.UTF_8);
        if (ehCaminhoWindowsComBarraInicial(caminho)) {
            caminho = caminho.substring(1);
        }
        return Path.of(caminho).toAbsolutePath().normalize();
    }

    private boolean ehCaminhoWindowsComBarraInicial(final String caminho) {
        return caminho != null
            && caminho.length() >= 3
            && caminho.charAt(0) == '/'
            && Character.isLetter(caminho.charAt(1))
            && caminho.charAt(2) == ':';
    }

    private String extrairPrimeiroToken(final String comandoCompleto) {
        final String valor = comandoCompleto.trim();
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
        final Path executavel = Path.of(javaHome, "bin", nomeExecutavel).toAbsolutePath().normalize();
        return executavel.toString();
    }
}
