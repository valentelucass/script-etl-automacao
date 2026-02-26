package br.com.extrator.comandos.extracao;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.BufferedWriter;
import java.lang.ProcessBuilder.Redirect;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.extrator.Main;
import br.com.extrator.auditoria.execucao.ExecutionAuditor;
import br.com.extrator.comandos.base.Comando;
import br.com.extrator.comandos.extracao.reconciliacao.LoopReconciliationService;
import br.com.extrator.comandos.extracao.reconciliacao.LoopReconciliationService.ReconciliationSummary;
import br.com.extrator.db.repository.ExecutionHistoryRepository;

/**
 * Gerencia loop de extracao em segundo plano (daemon).
 */
public class LoopDaemonComando implements Comando {
    private static final String FLAG_SEM_FATURAS_GRAPHQL = "--sem-faturas-graphql";
    private static final String FLAG_MODO_LOOP_DAEMON = "--modo-loop-daemon";
    private static final String MENSAGEM_FALHA_INTEGRIDADE = "Fluxo completo interrompido por falha de integridade";
    private static final Path DAEMON_DIR = Paths.get("logs", "daemon");
    private static final Path CYCLES_DIR = DAEMON_DIR.resolve("ciclos");
    private static final Path DAEMON_HISTORY_DIR = DAEMON_DIR.resolve("history");
    private static final Path RECONCILIACAO_HISTORY_DIR_DEFAULT = DAEMON_DIR.resolve("reconciliacao");
    private static final String RECONCILIACAO_HISTORY_DIR_OVERRIDE_KEY = "extrator.loop.reconciliacao.history.dir";
    private static final Path RUNTIME_DIR = DAEMON_DIR.resolve("runtime");
    private static final Path RUNTIME_JAR = RUNTIME_DIR.resolve("extrator-daemon-runtime.jar");
    private static final Path PID_FILE = DAEMON_DIR.resolve("loop_daemon.pid");
    private static final Path STATE_FILE = DAEMON_DIR.resolve("loop_daemon.state");
    private static final Path RECONCILIACAO_STATE_FILE = DAEMON_DIR.resolve("loop_reconciliation.state");
    private static final Path STOP_FILE = DAEMON_DIR.resolve("loop_daemon.stop");
    private static final Path FORCE_RUN_FILE = DAEMON_DIR.resolve("loop_daemon.force_run");
    private static final Path DAEMON_STDOUT_FILE = DAEMON_DIR.resolve("loop_daemon_console.log");
    private static final DateTimeFormatter CYCLE_LOG_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter HISTORY_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy_MM");
    private static final DateTimeFormatter HISTORY_LINE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern CYCLE_FILENAME_PATTERN =
        Pattern.compile("^extracao_daemon_(\\d{4}-\\d{2}-\\d{2})_\\d{2}-\\d{2}-\\d{2}\\.log$");
    private static final String DAEMON_HISTORY_HEADER =
        "DATA_HORA_FIM;INICIO;FIM;DURACAO_S;STATUS;TOTAL_RECORDS;WARNS;ERRORS;DETALHE;LOG_CICLO";
    private static final String RECONCILIACAO_HISTORY_HEADER =
        "DATA_HORA;INICIO_CICLO;FIM_EXTRACAO;CICLO_SUCESSO;STATUS_RECONCILIACAO;ATIVA;EXECUTADAS;FALHAS;PENDENTES;"
            + "AGENDOU_DIARIA;PENDENCIA_POR_FALHA;DETALHE;LOG_CICLO";
    private static final long INTERVALO_MINUTOS = 30L;

    public enum Modo {
        START,
        STOP,
        STATUS,
        RUN
    }

    private final Modo modo;

    public LoopDaemonComando(final Modo modo) {
        this.modo = modo;
    }

    @Override
    public void executar(final String[] args) throws Exception {
        final boolean incluirFaturasGraphQL = !possuiFlag(args, FLAG_SEM_FATURAS_GRAPHQL);
        switch (modo) {
            case START -> iniciarDaemon(incluirFaturasGraphQL);
            case STOP -> pararDaemon();
            case STATUS -> exibirStatus();
            case RUN -> executarDaemon(incluirFaturasGraphQL);
            default -> throw new IllegalStateException("Modo de loop daemon nao suportado: " + modo);
        }
    }

    private void iniciarDaemon(final boolean incluirFaturasGraphQL) throws Exception {
        garantirDiretorioLogs();
        final OptionalLong pidExistente = localizarPidDaemonAtivo();
        final String modoFaturas = descreverModoFaturas(incluirFaturasGraphQL);
        if (pidExistente.isPresent() && processoEhLoopDaemonAtivo(pidExistente.getAsLong())) {
            sincronizarPidArquivo(pidExistente.getAsLong());
            solicitarCicloImediato();
            final Properties estadoAtual = carregarEstado();
            final String statusAtual = estadoAtual.getProperty("status", "RUNNING");
            final String ultimoCiclo = valorOuNull(estadoAtual.getProperty("last_run_at"));
            final String proximoCiclo = valorOuNull(estadoAtual.getProperty("next_run_at"));
            salvarEstado(
                statusAtual,
                pidExistente.getAsLong(),
                "Loop daemon ja estava em execucao. Ciclo imediato solicitado manualmente. " + modoFaturas,
                ultimoCiclo,
                proximoCiclo
            );
            System.out.println("Loop daemon ja esta em execucao. PID: " + pidExistente.getAsLong());
            System.out.println("Solicitacao registrada: ciclo imediato sera executado assim que possivel.");
            System.out.println("Acompanhe em tempo real: " + DAEMON_STDOUT_FILE.toAbsolutePath());
            return;
        }

        limparArquivoSeExistir(PID_FILE);
        limparArquivoSeExistir(STOP_FILE);
        limparArquivoSeExistir(FORCE_RUN_FILE);

        final List<String> comando = construirComandoFilho(incluirFaturasGraphQL);
        final ProcessBuilder processBuilder = new ProcessBuilder(comando);
        processBuilder.redirectOutput(Redirect.appendTo(DAEMON_STDOUT_FILE.toFile()));
        processBuilder.redirectError(Redirect.appendTo(DAEMON_STDOUT_FILE.toFile()));

        final Process processo = processBuilder.start();
        final long pid = processo.pid();
        Files.writeString(PID_FILE, String.valueOf(pid), StandardCharsets.UTF_8);
        salvarEstado("STARTING", pid, "Processo daemon iniciado. " + modoFaturas, null, null);

        Thread.sleep(1200L);
        if (!processo.isAlive()) {
            limparArquivoSeExistir(PID_FILE);
            limparArquivoSeExistir(STOP_FILE);
            throw new IllegalStateException("Falha ao iniciar loop daemon. Consulte " + DAEMON_STDOUT_FILE.toAbsolutePath());
        }

        System.out.println("Loop daemon iniciado com sucesso. PID: " + pid);
        System.out.println(modoFaturas);
        System.out.println("Log do daemon: " + DAEMON_STDOUT_FILE.toAbsolutePath());
    }

    private void pararDaemon() throws Exception {
        garantirDiretorioLogs();
        final List<ProcessHandle> processosAtivos = localizarProcessosAlvoParada();
        if (processosAtivos.isEmpty()) {
            limparArquivoSeExistir(PID_FILE);
            limparArquivoSeExistir(STOP_FILE);
            limparArquivoSeExistir(FORCE_RUN_FILE);
            salvarEstado("STOPPED", -1L, "Loop daemon ja estava parado.", null, null);
            System.out.println("Loop daemon nao estava em execucao.");
            return;
        }

        final long pid = processosAtivos.get(0).pid();
        sincronizarPidArquivo(pid);
        Files.writeString(STOP_FILE, "stop@" + LocalDateTime.now(), StandardCharsets.UTF_8);
        salvarEstado("STOPPING", pid, "Solicitado encerramento do loop daemon. processos_detectados=" + processosAtivos.size(), null, null);

        aguardarEncerramentoProcessos(processosAtivos, 20_000L);

        for (final ProcessHandle processo : processosAtivos) {
            if (processo.isAlive()) {
                processo.destroy();
            }
        }

        aguardarEncerramentoProcessos(processosAtivos, 2_000L);

        for (final ProcessHandle processo : processosAtivos) {
            if (processo.isAlive()) {
                processo.destroyForcibly();
            }
        }

        aguardarEncerramentoProcessos(processosAtivos, 1_000L);

        final List<Long> pidsAindaAtivos = processosAtivos.stream()
            .filter(ProcessHandle::isAlive)
            .map(ProcessHandle::pid)
            .toList();
        if (!pidsAindaAtivos.isEmpty()) {
            sincronizarPidArquivo(pidsAindaAtivos.get(0));
            salvarEstado(
                "STOPPING",
                pidsAindaAtivos.get(0),
                "Encerramento solicitado, mas processos ainda ativos: " + pidsAindaAtivos,
                null,
                null
            );
            throw new IllegalStateException("Nao foi possivel parar completamente o loop daemon. PID(s) ativos: " + pidsAindaAtivos);
        }

        limparArquivoSeExistir(PID_FILE);
        limparArquivoSeExistir(STOP_FILE);
        limparArquivoSeExistir(FORCE_RUN_FILE);
        salvarEstado("STOPPED", pid, "Loop daemon encerrado por comando de parada.", null, null);
        System.out.println("Loop daemon parado.");
    }

    private void exibirStatus() throws Exception {
        garantirDiretorioLogs();
        final OptionalLong pidArquivo = lerPidArquivo();
        final OptionalLong pidOpt = localizarPidDaemonAtivo();
        if (pidOpt.isPresent()) {
            sincronizarPidArquivo(pidOpt.getAsLong());
        }
        final Properties state = carregarEstado();

        final long pid = pidOpt.orElse(-1L);
        final boolean vivo = pid > 0;
        final String statusEstado = state.getProperty("status", vivo ? "RUNNING" : "STOPPED");
        final String atualizadoEm = state.getProperty("updated_at", "N/A");
        final String detalhe = state.getProperty("detail", "N/A");
        final String ultimoCiclo = state.getProperty("last_run_at", "N/A");
        final String proximoCiclo = state.getProperty("next_run_at", "N/A");

        System.out.println("Status do loop daemon");
        System.out.println("  PID: " + (pid > 0 ? pid : "N/A"));
        System.out.println("  Processo vivo: " + (vivo ? "SIM" : "NAO"));
        System.out.println("  Estado: " + statusEstado);
        System.out.println("  Atualizado em: " + atualizadoEm);
        System.out.println("  Ultimo ciclo: " + ultimoCiclo);
        System.out.println("  Proximo ciclo: " + proximoCiclo);
        System.out.println("  Detalhe: " + detalhe);
        System.out.println("  Log: " + DAEMON_STDOUT_FILE.toAbsolutePath());

        if (!vivo && pidArquivo.isPresent()) {
            salvarEstado("STOPPED", pidArquivo.getAsLong(), "PID registrado nao esta mais ativo.", ultimoCiclo, proximoCiclo);
            limparArquivoSeExistir(PID_FILE);
        }
    }

    private void executarDaemon(final boolean incluirFaturasGraphQL) throws Exception {
        garantirDiretorioLogs();
        limparArquivoSeExistir(STOP_FILE);
        limparArquivoSeExistir(FORCE_RUN_FILE);
        final long pid = ProcessHandle.current().pid();
        final String modoFaturas = descreverModoFaturas(incluirFaturasGraphQL);
        final LoopReconciliationService reconciliacaoService = LoopReconciliationService.criarPadrao(RECONCILIACAO_STATE_FILE);
        Files.writeString(PID_FILE, String.valueOf(pid), StandardCharsets.UTF_8);
        salvarEstado("RUNNING", pid, "Daemon iniciado e aguardando ciclos. " + modoFaturas, null, null);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                limparArquivoSeExistir(PID_FILE);
                limparArquivoSeExistir(STOP_FILE);
                limparArquivoSeExistir(FORCE_RUN_FILE);
                salvarEstado("STOPPED", pid, "Daemon finalizado.", null, null);
            } catch (final Exception ignored) {
                // no-op
            }
        }));

        while (true) {
            if (deveParar()) {
                salvarEstado("STOPPED", pid, "Sinal de parada detectado antes do ciclo.", null, null);
                break;
            }

            final LocalDateTime inicio = LocalDateTime.now();
            final Path cicloLog = criarArquivoLogCiclo(inicio);
            salvarEstado("RUNNING", pid, "Executando ciclo de extracao. " + modoFaturas + " | log_ciclo=" + cicloLog.toAbsolutePath(), inicio.toString(), null);

            boolean sucesso = true;
            String detalhe = "Ciclo concluido com sucesso.";
            try {
                try (SaidaCicloTee ignored = iniciarTeeCiclo(cicloLog)) {
                    if (incluirFaturasGraphQL) {
                        new ExecutarFluxoCompletoComando().executar(new String[] {"--fluxo-completo", FLAG_MODO_LOOP_DAEMON});
                    } else {
                        new ExecutarFluxoCompletoComando().executar(new String[] {"--fluxo-completo", FLAG_SEM_FATURAS_GRAPHQL, FLAG_MODO_LOOP_DAEMON});
                    }
                }
            } catch (final Throwable e) {
                if (ehErroIrrecuperavel(e)) {
                    throw e;
                }
                if (ehFalhaIntegridadeOperacional(e)) {
                    sucesso = true;
                    detalhe = "Ciclo concluido com alerta de integridade: " + resumirMensagem(e.getMessage());
                    System.err.println("ALERTA LOOP: Falha de integridade detectada. O loop continuara no proximo ciclo.");
                } else {
                    sucesso = false;
                    detalhe = "Falha no ciclo: " + resumirMensagem(e.getMessage());
                }
            }

            final LocalDateTime fimExtracao = LocalDateTime.now();
            final ReconciliationSummary resumoReconciliacao = processarReconciliacao(
                reconciliacaoService,
                inicio,
                fimExtracao,
                sucesso,
                incluirFaturasGraphQL
            );
            registrarHistoricoReconciliacao(inicio, fimExtracao, sucesso, resumoReconciliacao, cicloLog);
            final LocalDateTime fim = LocalDateTime.now();
            final String detalheCiclo = adicionarDetalheReconciliacao(detalhe, resumoReconciliacao);
            final ResumoCiclo resumoCiclo = construirResumoCiclo(inicio, fim, cicloLog, sucesso, detalheCiclo);
            anexarResumoFinalNoLogCiclo(cicloLog, resumoCiclo);
            registrarHistoricoCiclo(resumoCiclo);
            registrarHistoricoCompatibilidade(resumoCiclo);

            final LocalDateTime proximo = fim.plusMinutes(INTERVALO_MINUTOS);
            final boolean falhaReconciliacao = resumoReconciliacao != null
                && resumoReconciliacao.isAtivo()
                && resumoReconciliacao.getFalhas() > 0;
            final String statusDaemon = (sucesso && !falhaReconciliacao) ? "WAITING_NEXT_CYCLE" : "WAITING_NEXT_CYCLE_WITH_ERROR";
            salvarEstado(
                statusDaemon,
                pid,
                resumoCiclo.detalhe + " " + modoFaturas + " | log_ciclo=" + cicloLog.toAbsolutePath(),
                fim.toString(),
                proximo.toString()
            );

            final ResultadoEspera resultadoEspera = aguardarProximoCiclo(proximo);
            if (resultadoEspera == ResultadoEspera.STOP_REQUESTED) {
                salvarEstado("STOPPED", pid, "Sinal de parada detectado durante espera.", fim.toString(), null);
                break;
            }
            if (resultadoEspera == ResultadoEspera.FORCE_RUN_REQUESTED) {
                salvarEstado("RUNNING", pid, "Disparo manual detectado: iniciando novo ciclo imediato. " + modoFaturas, fim.toString(), null);
            }
        }

        limparArquivoSeExistir(PID_FILE);
        limparArquivoSeExistir(STOP_FILE);
        limparArquivoSeExistir(FORCE_RUN_FILE);
    }

    private ReconciliationSummary processarReconciliacao(
        final LoopReconciliationService reconciliacaoService,
        final LocalDateTime inicio,
        final LocalDateTime fimExtracao,
        final boolean cicloSucesso,
        final boolean incluirFaturasGraphQL
    ) {
        try {
            return reconciliacaoService.processarPosCiclo(inicio, fimExtracao, cicloSucesso, incluirFaturasGraphQL);
        } catch (final RuntimeException e) {
            System.err.println("ALERTA LOOP: Falha ao processar reconciliacao automatica: " + resumirMensagem(e.getMessage()));
            return null;
        }
    }

    private String adicionarDetalheReconciliacao(final String detalheBase, final ReconciliationSummary resumoReconciliacao) {
        if (resumoReconciliacao == null) {
            return (detalheBase == null ? "Sem detalhes." : detalheBase) + " | reconciliacao[erro_processamento=true]";
        }
        if (!resumoReconciliacao.isAtivo()) {
            return detalheBase;
        }

        final StringBuilder detalhe = new StringBuilder();
        detalhe.append(detalheBase == null ? "Sem detalhes." : detalheBase);
        detalhe.append(" | reconciliacao[executadas=").append(resumoReconciliacao.getReconciliacoesExecutadas());
        detalhe.append(", falhas=").append(resumoReconciliacao.getFalhas());
        detalhe.append(", pendentes=").append(resumoReconciliacao.getPendenciasRestantes().size());
        detalhe.append(", diaria_agendada=").append(resumoReconciliacao.isAgendouReconciliacaoDiaria());
        detalhe.append(", por_falha=").append(resumoReconciliacao.isPendenciaPorFalha());
        if (!resumoReconciliacao.getDetalhesFalha().isEmpty()) {
            detalhe.append(", detalhe_erro=").append(resumirMensagem(String.join(" | ", resumoReconciliacao.getDetalhesFalha())));
        }
        detalhe.append("]");
        return detalhe.toString();
    }

    private ResultadoEspera aguardarProximoCiclo(final LocalDateTime proximo) throws InterruptedException {
        while (LocalDateTime.now().isBefore(proximo)) {
            if (deveParar()) {
                return ResultadoEspera.STOP_REQUESTED;
            }
            if (deveExecutarImediatamente()) {
                try {
                    limparArquivoSeExistir(FORCE_RUN_FILE);
                } catch (final IOException ignored) {
                    // Se nao conseguir limpar, segue com ciclo imediato mesmo assim.
                }
                return ResultadoEspera.FORCE_RUN_REQUESTED;
            }
            Thread.sleep(1000L);
        }
        return ResultadoEspera.TIME_ELAPSED;
    }

    private boolean deveParar() {
        return Files.exists(STOP_FILE);
    }

    private boolean deveExecutarImediatamente() {
        return Files.exists(FORCE_RUN_FILE);
    }

    private List<String> construirComandoFilho(final boolean incluirFaturasGraphQL) throws URISyntaxException {
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
            comando.add("--loop-daemon-run");
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
        comando.add("--loop-daemon-run");
        if (!incluirFaturasGraphQL) {
            comando.add(FLAG_SEM_FATURAS_GRAPHQL);
        }
        return comando;
    }

    private Path prepararJarRuntime(final Path jarAtual) {
        try {
            garantirDiretorioLogs();
            if (!Files.exists(RUNTIME_DIR)) {
                Files.createDirectories(RUNTIME_DIR);
            }
            Files.copy(jarAtual, RUNTIME_JAR, StandardCopyOption.REPLACE_EXISTING);
            return RUNTIME_JAR.toAbsolutePath().normalize();
        } catch (final IOException e) {
            throw new IllegalStateException(
                "Falha ao preparar JAR runtime do daemon em " + RUNTIME_JAR.toAbsolutePath() + ": " + e.getMessage(),
                e
            );
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
            return Paths.get(new java.net.URI(pathJar)).toAbsolutePath().normalize();
        }

        String caminho = URLDecoder.decode(pathJar, StandardCharsets.UTF_8);
        if (ehCaminhoWindowsComBarraInicial(caminho)) {
            caminho = caminho.substring(1);
        }
        return Paths.get(caminho).toAbsolutePath().normalize();
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
        final Path executavel = Paths.get(javaHome, "bin", nomeExecutavel).toAbsolutePath().normalize();
        return executavel.toString();
    }

    private OptionalLong lerPidArquivo() {
        if (!Files.exists(PID_FILE)) {
            return OptionalLong.empty();
        }
        try {
            final String conteudo = Files.readString(PID_FILE, StandardCharsets.UTF_8).trim();
            if (conteudo.isEmpty()) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(Long.parseLong(conteudo));
        } catch (final Exception e) {
            return OptionalLong.empty();
        }
    }

    private OptionalLong lerPidEstado() {
        final Properties estado = carregarEstado();
        final String pidTexto = estado.getProperty("pid", "").trim();
        if (pidTexto.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(pidTexto));
        } catch (final NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private Properties carregarEstado() {
        final Properties p = new Properties();
        if (!Files.exists(STATE_FILE)) {
            return p;
        }
        try (var in = Files.newInputStream(STATE_FILE)) {
            p.load(in);
        } catch (final IOException e) {
            // Mantem estado vazio em caso de falha de leitura.
        }
        return p;
    }

    private void salvarEstado(
        final String status,
        final long pid,
        final String detalhe,
        final String lastRunAt,
        final String nextRunAt
    ) {
        final Properties p = new Properties();
        p.setProperty("status", status);
        p.setProperty("pid", pid > 0 ? String.valueOf(pid) : "");
        p.setProperty("detail", detalhe == null ? "" : detalhe);
        p.setProperty("updated_at", LocalDateTime.now().toString());
        p.setProperty("last_run_at", lastRunAt == null ? "" : lastRunAt);
        p.setProperty("next_run_at", nextRunAt == null ? "" : nextRunAt);

        try (var out = Files.newOutputStream(STATE_FILE)) {
            p.store(out, "loop-daemon-state");
        } catch (final IOException e) {
            throw new RuntimeException("Falha ao salvar estado do loop daemon.", e);
        }
    }

    private OptionalLong localizarPidDaemonAtivo() {
        final OptionalLong pidArquivo = lerPidArquivo();
        if (pidArquivo.isPresent() && processoEhLoopDaemonAtivo(pidArquivo.getAsLong())) {
            return pidArquivo;
        }

        final OptionalLong pidEstado = lerPidEstado();
        if (pidEstado.isPresent() && processoEhLoopDaemonAtivo(pidEstado.getAsLong())) {
            return pidEstado;
        }

        return localizarProcessosDaemonAtivos().stream()
            .mapToLong(ProcessHandle::pid)
            .findFirst();
    }

    private List<ProcessHandle> localizarProcessosAlvoParada() {
        final Map<Long, ProcessHandle> processos = new LinkedHashMap<>();
        adicionarProcessoSeAtivo(processos, lerPidArquivo());
        adicionarProcessoSeAtivo(processos, lerPidEstado());
        for (final ProcessHandle processo : localizarProcessosDaemonAtivos()) {
            processos.putIfAbsent(processo.pid(), processo);
        }
        return new ArrayList<>(processos.values());
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

    private boolean processoEhLoopDaemonAtivo(final long pid) {
        return ProcessHandle.of(pid)
            .filter(ProcessHandle::isAlive)
            .filter(this::ehProcessoLoopDaemon)
            .isPresent();
    }

    private boolean ehComandoLoopDaemon(final String comandoCompleto) {
        final String normalizado = comandoCompleto.toLowerCase(Locale.ROOT).replace('\\', '/');
        if (!normalizado.contains("--loop-daemon-run")) {
            return false;
        }
        return normalizado.contains("extrator-daemon-runtime.jar")
            || normalizado.contains("/target/extrator.jar")
            || normalizado.contains(" br.com.extrator.main ");
    }

    private void aguardarEncerramentoProcessos(final List<ProcessHandle> processos, final long timeoutMillis)
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

    private void sincronizarPidArquivo(final long pid) {
        if (pid <= 0) {
            return;
        }
        try {
            Files.writeString(PID_FILE, String.valueOf(pid), StandardCharsets.UTF_8);
        } catch (final IOException ignored) {
            // Falha ao sincronizar PID nao deve interromper fluxo principal.
        }
    }

    private void garantirDiretorioLogs() throws IOException {
        if (!Files.exists(DAEMON_DIR)) {
            Files.createDirectories(DAEMON_DIR);
        }
        if (!Files.exists(CYCLES_DIR)) {
            Files.createDirectories(CYCLES_DIR);
        }
        if (!Files.exists(DAEMON_HISTORY_DIR)) {
            Files.createDirectories(DAEMON_HISTORY_DIR);
        }
        final Path reconciliacaoHistoryDir = obterDiretorioHistoricoReconciliacao();
        if (!Files.exists(reconciliacaoHistoryDir)) {
            Files.createDirectories(reconciliacaoHistoryDir);
        }
        if (!Files.exists(RUNTIME_DIR)) {
            Files.createDirectories(RUNTIME_DIR);
        }
        organizarCiclosLegadosPorData();
    }

    private void limparArquivoSeExistir(final Path path) throws IOException {
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }

    private Path obterDiretorioHistoricoReconciliacao() {
        final String override = System.getProperty(RECONCILIACAO_HISTORY_DIR_OVERRIDE_KEY);
        if (override != null && !override.isBlank()) {
            return Paths.get(override.trim());
        }
        return RECONCILIACAO_HISTORY_DIR_DEFAULT;
    }

    private String resumirMensagem(final String msg) {
        if (msg == null || msg.isBlank()) {
            return "Sem detalhes.";
        }
        final String limpa = msg.replace('\n', ' ').replace('\r', ' ').trim();
        return limpa.length() > 240 ? limpa.substring(0, 240) + "..." : limpa;
    }

    private boolean ehErroIrrecuperavel(final Throwable throwable) {
        return throwable instanceof VirtualMachineError || throwable instanceof ThreadDeath;
    }

    private Path criarArquivoLogCiclo(final LocalDateTime inicio) throws IOException {
        garantirDiretorioLogs();
        final String timestamp = inicio.format(CYCLE_LOG_FORMAT);
        final LocalDate dataCiclo = inicio.toLocalDate();
        final Path pastaDia = CYCLES_DIR.resolve(dataCiclo.toString());
        if (!Files.exists(pastaDia)) {
            Files.createDirectories(pastaDia);
        }
        final Path arquivo = pastaDia.resolve("extracao_daemon_" + timestamp + ".log");
        if (!Files.exists(arquivo)) {
            Files.createFile(arquivo);
        }
        return arquivo;
    }

    private void organizarCiclosLegadosPorData() {
        if (!Files.exists(CYCLES_DIR)) {
            return;
        }
        try (var stream = Files.list(CYCLES_DIR)) {
            stream.filter(Files::isRegularFile).forEach(arquivo -> moverCicloLegadoParaPastaData(arquivo));
        } catch (final IOException ignored) {
            // Se a organizacao falhar, nao interrompe o daemon.
        }
    }

    private void moverCicloLegadoParaPastaData(final Path arquivo) {
        final String nome = arquivo.getFileName().toString();
        final Matcher matcher = CYCLE_FILENAME_PATTERN.matcher(nome);
        if (!matcher.matches()) {
            return;
        }
        final String data = matcher.group(1);
        final Path destinoDir = CYCLES_DIR.resolve(data);
        final Path destino = destinoDir.resolve(nome);
        try {
            if (!Files.exists(destinoDir)) {
                Files.createDirectories(destinoDir);
            }
            Files.move(arquivo, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException ignored) {
            // Mantem arquivo no local atual em caso de falha de movimentacao.
        }
    }

    private ResumoCiclo construirResumoCiclo(
        final LocalDateTime inicio,
        final LocalDateTime fim,
        final Path cicloLog,
        final boolean sucesso,
        final String detalheBase
    ) {
        int warns = 0;
        int errors = 0;
        boolean alertaIntegridade = false;
        boolean statusIncompleto = false;
        boolean resumoFinalSucesso = false;

        try {
            final List<String> linhas = Files.readAllLines(cicloLog, StandardCharsets.UTF_8);
            for (final String linha : linhas) {
                if (linha.contains(" WARN ")) {
                    warns++;
                }
                if (linha.contains(" ERROR ")) {
                    errors++;
                }
                if (linha.contains("ALERTA_LOOP")
                    || linha.contains("EXTRACAO CONCLUIDA COM ALERTA DE INTEGRIDADE")
                    || linha.contains("RESUMO DA EXTRACAO (com alerta de integridade no loop)")) {
                    alertaIntegridade = true;
                }
                if (linha.contains("status_code=INCOMPLETO")) {
                    statusIncompleto = true;
                }
                if (linha.contains("Todas as APIs foram processadas com sucesso.")) {
                    resumoFinalSucesso = true;
                }
            }
        } catch (final IOException ignored) {
            // Em caso de falha de leitura, o resumo segue com valores default.
        }

        final long duracaoSegundos = Math.max(0L, Duration.between(inicio, fim).getSeconds());
        final int totalRegistros = calcularTotalRegistrosCiclo(inicio, fim);
        final String statusCiclo = determinarStatusCiclo(sucesso, alertaIntegridade, statusIncompleto, errors);
        final String detalheResumo = construirDetalheResumo(
            detalheBase,
            alertaIntegridade,
            statusIncompleto,
            resumoFinalSucesso,
            warns,
            errors
        );

        return new ResumoCiclo(inicio, fim, duracaoSegundos, statusCiclo, totalRegistros, warns, errors, detalheResumo, cicloLog.toAbsolutePath().toString());
    }

    private int calcularTotalRegistrosCiclo(final LocalDateTime inicio, final LocalDateTime fim) {
        try {
            final ExecutionHistoryRepository repo = new ExecutionHistoryRepository();
            return repo.calcularTotalRegistros(inicio, fim);
        } catch (final Throwable ignored) {
            return 0;
        }
    }

    private String determinarStatusCiclo(
        final boolean sucesso,
        final boolean alertaIntegridade,
        final boolean statusIncompleto,
        final int errors
    ) {
        if (!sucesso) {
            return "ERROR";
        }
        if (alertaIntegridade || statusIncompleto || errors > 0) {
            return "ALERT";
        }
        return "SUCCESS";
    }

    private String construirDetalheResumo(
        final String detalheBase,
        final boolean alertaIntegridade,
        final boolean statusIncompleto,
        final boolean resumoFinalSucesso,
        final int warns,
        final int errors
    ) {
        final StringBuilder detalhe = new StringBuilder();
        detalhe.append(detalheBase == null ? "Sem detalhes." : detalheBase);
        detalhe.append(" | warns=").append(warns);
        detalhe.append(" | errors=").append(errors);
        if (alertaIntegridade) {
            detalhe.append(" | alerta_integridade=true");
        }
        if (statusIncompleto) {
            detalhe.append(" | status_incompleto=true");
        }
        detalhe.append(" | resumo_final_ok=").append(resumoFinalSucesso);
        return resumirMensagem(detalhe.toString());
    }

    private void anexarResumoFinalNoLogCiclo(final Path cicloLog, final ResumoCiclo resumo) {
        final List<String> linhas = new ArrayList<>();
        linhas.add("");
        linhas.add("============================================================");
        linhas.add("RESUMO FINAL DO CICLO (DAEMON)");
        linhas.add("============================================================");
        linhas.add("Inicio: " + HISTORY_LINE_FORMAT.format(resumo.inicio));
        linhas.add("Fim: " + HISTORY_LINE_FORMAT.format(resumo.fim));
        linhas.add("Duracao (segundos): " + resumo.duracaoSegundos);
        linhas.add("Status do ciclo: " + resumo.status);
        linhas.add("Total de registros (log_extracoes): " + resumo.totalRegistros);
        linhas.add("Warnings: " + resumo.warns + " | Errors: " + resumo.errors);
        linhas.add("Detalhe: " + resumo.detalhe);
        linhas.add("Log do ciclo: " + resumo.logCiclo);
        linhas.add("============================================================");
        try {
            Files.write(cicloLog, linhas, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (final IOException ignored) {
            // Falha ao anexar resumo nao deve interromper o loop.
        }
    }

    private void registrarHistoricoCiclo(final ResumoCiclo resumo) {
        final String nomeArquivo = "execucao_daemon_" + resumo.fim.format(HISTORY_MONTH_FORMAT) + ".csv";
        final Path arquivoCsv = DAEMON_HISTORY_DIR.resolve(nomeArquivo);
        final boolean escreverHeader;
        try {
            escreverHeader = !Files.exists(arquivoCsv) || Files.size(arquivoCsv) == 0L;
        } catch (final IOException e) {
            return;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(
            arquivoCsv,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )) {
            if (escreverHeader) {
                writer.write(DAEMON_HISTORY_HEADER);
                writer.newLine();
            }

            final String linha = String.join(";",
                HISTORY_LINE_FORMAT.format(resumo.fim),
                HISTORY_LINE_FORMAT.format(resumo.inicio),
                HISTORY_LINE_FORMAT.format(resumo.fim),
                String.valueOf(resumo.duracaoSegundos),
                sanitizarCsv(resumo.status),
                String.valueOf(resumo.totalRegistros),
                String.valueOf(resumo.warns),
                String.valueOf(resumo.errors),
                sanitizarCsv(resumo.detalhe),
                sanitizarCsv(resumo.logCiclo)
            );
            writer.write(linha);
            writer.newLine();
        } catch (final IOException ignored) {
            // Falha de historico nao interrompe o daemon.
        }
    }

    private void registrarHistoricoCompatibilidade(final ResumoCiclo resumo) {
        ExecutionAuditor.registrarCsv(
            resumo.fim,
            resumo.status,
            resumo.duracaoSegundos,
            resumo.totalRegistros,
            "loop_daemon_ciclo",
            resumo.detalhe + " | log_ciclo=" + resumo.logCiclo
        );
    }

    private void registrarHistoricoReconciliacao(
        final LocalDateTime inicioCiclo,
        final LocalDateTime fimExtracao,
        final boolean cicloSucesso,
        final ReconciliationSummary resumo,
        final Path cicloLog
    ) {
        final String nomeArquivo = "reconciliacao_daemon_" + fimExtracao.format(HISTORY_MONTH_FORMAT) + ".csv";
        final Path arquivoCsv = obterDiretorioHistoricoReconciliacao().resolve(nomeArquivo);
        final boolean escreverHeader;
        try {
            escreverHeader = !Files.exists(arquivoCsv) || Files.size(arquivoCsv) == 0L;
        } catch (final IOException e) {
            return;
        }

        final String statusReconciliacao = determinarStatusReconciliacao(resumo);
        final boolean ativa = resumo != null && resumo.isAtivo();
        final int executadas = resumo == null ? 0 : resumo.getReconciliacoesExecutadas();
        final int falhas = resumo == null ? 0 : resumo.getFalhas();
        final int pendentes = resumo == null ? -1 : resumo.getPendenciasRestantes().size();
        final boolean agendouDiaria = resumo != null && resumo.isAgendouReconciliacaoDiaria();
        final boolean pendenciaPorFalha = resumo != null && resumo.isPendenciaPorFalha();
        final String detalhe = construirDetalheHistoricoReconciliacao(resumo);

        try (BufferedWriter writer = Files.newBufferedWriter(
            arquivoCsv,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )) {
            if (escreverHeader) {
                writer.write(RECONCILIACAO_HISTORY_HEADER);
                writer.newLine();
            }

            final String linha = String.join(";",
                HISTORY_LINE_FORMAT.format(fimExtracao),
                HISTORY_LINE_FORMAT.format(inicioCiclo),
                HISTORY_LINE_FORMAT.format(fimExtracao),
                String.valueOf(cicloSucesso),
                sanitizarCsv(statusReconciliacao),
                String.valueOf(ativa),
                String.valueOf(executadas),
                String.valueOf(falhas),
                String.valueOf(pendentes),
                String.valueOf(agendouDiaria),
                String.valueOf(pendenciaPorFalha),
                sanitizarCsv(detalhe),
                sanitizarCsv(cicloLog.toAbsolutePath().toString())
            );
            writer.write(linha);
            writer.newLine();
        } catch (final IOException ignored) {
            // Falha no historico de reconciliacao nao interrompe o daemon.
        }
    }

    private String determinarStatusReconciliacao(final ReconciliationSummary resumo) {
        if (resumo == null) {
            return "ERRO_PROCESSAMENTO";
        }
        if (!resumo.isAtivo()) {
            return "INATIVA";
        }
        if (resumo.getFalhas() > 0) {
            return "COM_FALHAS";
        }
        if (resumo.getReconciliacoesExecutadas() > 0 || resumo.isAgendouReconciliacaoDiaria() || resumo.isPendenciaPorFalha()) {
            return "EXECUTADA";
        }
        return "SEM_ACAO";
    }

    private String construirDetalheHistoricoReconciliacao(final ReconciliationSummary resumo) {
        if (resumo == null) {
            return "Falha ao processar reconciliacao automatica.";
        }
        if (!resumo.isAtivo()) {
            return "Reconciliacao desativada por configuracao.";
        }

        final StringBuilder detalhe = new StringBuilder();
        detalhe.append("pendencias_restantes=");
        if (resumo.getPendenciasRestantes().isEmpty()) {
            detalhe.append("nenhuma");
        } else {
            detalhe.append(
                resumo.getPendenciasRestantes().stream()
                    .map(LocalDate::toString)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("nenhuma")
            );
        }
        if (!resumo.getDetalhesFalha().isEmpty()) {
            detalhe.append(" | falhas=").append(String.join(" | ", resumo.getDetalhesFalha()));
        }
        return detalhe.toString();
    }

    private String sanitizarCsv(final String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("\r", " ").replace("\n", " ").replace(";", ",").trim();
    }

    private SaidaCicloTee iniciarTeeCiclo(final Path cicloLog) throws IOException {
        final PrintStream originalOut = System.out;
        final PrintStream originalErr = System.err;
        final FileOutputStream arquivoOut = new FileOutputStream(cicloLog.toFile(), true);
        final FileOutputStream arquivoErr = new FileOutputStream(cicloLog.toFile(), true);
        final PrintStream teeOut = new PrintStream(new TeeOutputStream(originalOut, arquivoOut), true, StandardCharsets.UTF_8);
        final PrintStream teeErr = new PrintStream(new TeeOutputStream(originalErr, arquivoErr), true, StandardCharsets.UTF_8);
        System.setOut(teeOut);
        System.setErr(teeErr);
        return new SaidaCicloTee(originalOut, originalErr, teeOut, teeErr, arquivoOut, arquivoErr);
    }

    private void solicitarCicloImediato() throws IOException {
        Files.writeString(FORCE_RUN_FILE, "force-run@" + LocalDateTime.now(), StandardCharsets.UTF_8);
    }

    private String valorOuNull(final String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor;
    }

    private boolean possuiFlag(final String[] args, final String flag) {
        if (args == null || flag == null) {
            return false;
        }
        for (final String arg : args) {
            if (arg != null && flag.equalsIgnoreCase(arg.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean ehFalhaIntegridadeOperacional(final Throwable e) {
        Throwable atual = e;
        while (atual != null) {
            final String mensagem = atual.getMessage();
            if (mensagem != null && mensagem.contains(MENSAGEM_FALHA_INTEGRIDADE)) {
                return true;
            }
            atual = atual.getCause();
        }
        return false;
    }

    private String descreverModoFaturas(final boolean incluirFaturasGraphQL) {
        return "Faturas GraphQL: " + (incluirFaturasGraphQL ? "INCLUIDO" : "DESABILITADO (" + FLAG_SEM_FATURAS_GRAPHQL + ")");
    }

    private static final class ResumoCiclo {
        private final LocalDateTime inicio;
        private final LocalDateTime fim;
        private final long duracaoSegundos;
        private final String status;
        private final int totalRegistros;
        private final int warns;
        private final int errors;
        private final String detalhe;
        private final String logCiclo;

        private ResumoCiclo(
            final LocalDateTime inicio,
            final LocalDateTime fim,
            final long duracaoSegundos,
            final String status,
            final int totalRegistros,
            final int warns,
            final int errors,
            final String detalhe,
            final String logCiclo
        ) {
            this.inicio = inicio;
            this.fim = fim;
            this.duracaoSegundos = duracaoSegundos;
            this.status = status;
            this.totalRegistros = totalRegistros;
            this.warns = warns;
            this.errors = errors;
            this.detalhe = detalhe;
            this.logCiclo = logCiclo;
        }
    }

    private static final class SaidaCicloTee implements AutoCloseable {
        private final PrintStream originalOut;
        private final PrintStream originalErr;
        private final PrintStream teeOut;
        private final PrintStream teeErr;
        private final FileOutputStream arquivoOut;
        private final FileOutputStream arquivoErr;

        private SaidaCicloTee(
            final PrintStream originalOut,
            final PrintStream originalErr,
            final PrintStream teeOut,
            final PrintStream teeErr,
            final FileOutputStream arquivoOut,
            final FileOutputStream arquivoErr
        ) {
            this.originalOut = originalOut;
            this.originalErr = originalErr;
            this.teeOut = teeOut;
            this.teeErr = teeErr;
            this.arquivoOut = arquivoOut;
            this.arquivoErr = arquivoErr;
        }

        @Override
        public void close() {
            System.setOut(originalOut);
            System.setErr(originalErr);
            teeOut.flush();
            teeErr.flush();
            teeOut.close();
            teeErr.close();
            try {
                arquivoOut.close();
            } catch (final IOException ignored) {
                // no-op
            }
            try {
                arquivoErr.close();
            } catch (final IOException ignored) {
                // no-op
            }
        }
    }

    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream out1;
        private final OutputStream out2;

        private TeeOutputStream(final OutputStream out1, final OutputStream out2) {
            this.out1 = out1;
            this.out2 = out2;
        }

        @Override
        public void write(final int b) throws IOException {
            out1.write(b);
            out2.write(b);
        }

        @Override
        public void write(final byte[] b, final int off, final int len) throws IOException {
            out1.write(b, off, len);
            out2.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            out1.flush();
            out2.flush();
        }
    }

    private enum ResultadoEspera {
        STOP_REQUESTED,
        FORCE_RUN_REQUESTED,
        TIME_ELAPSED
    }
}
