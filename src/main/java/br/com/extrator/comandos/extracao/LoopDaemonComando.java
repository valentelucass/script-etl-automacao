/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/extracao/LoopDaemonComando.java
Classe  : LoopDaemonComando (class)
Pacote  : br.com.extrator.comandos.extracao
Modulo  : Comando CLI (extracao)
Papel   : Implementa responsabilidade de loop daemon comando.

Conecta com:
- Comando (comandos.base)
- DaemonHistoryWriter (comandos.extracao.daemon)
- DaemonLifecycleService (comandos.extracao.daemon)
- DaemonPaths (comandos.extracao.daemon)
- DaemonStateStore (comandos.extracao.daemon)
- LoopReconciliationService (comandos.extracao.reconciliacao)
- ReconciliationSummary (comandos.extracao.reconciliacao.LoopReconciliationService)

Fluxo geral:
1) Interpreta parametros e escopo de extracao.
2) Dispara runners/extratores conforme alvo.
3) Consolida status final e tratamento de falhas.

Estrutura interna:
Metodos principais:
- LoopDaemonComando(...1 args): realiza operacao relacionada a "loop daemon comando".
- LoopDaemonComando(...4 args): realiza operacao relacionada a "loop daemon comando".
- processarReconciliacao(...5 args): realiza operacao relacionada a "processar reconciliacao".
- adicionarDetalheReconciliacao(...2 args): realiza operacao relacionada a "adicionar detalhe reconciliacao".
- possuiFlag(...2 args): realiza operacao relacionada a "possui flag".
- ehFalhaIntegridadeOperacional(...1 args): realiza operacao relacionada a "eh falha integridade operacional".
- descreverModoFaturas(...1 args): realiza operacao relacionada a "descrever modo faturas".
- valorOuNull(...1 args): realiza operacao relacionada a "valor ou null".
Atributos-chave:
- FLAG_SEM_FATURAS_GRAPHQL: campo de estado para "flag sem faturas graphql".
- FLAG_MODO_LOOP_DAEMON: campo de estado para "flag modo loop daemon".
- MENSAGEM_FALHA_INTEGRIDADE: campo de estado para "mensagem falha integridade".
- INTERVALO_MINUTOS: campo de estado para "intervalo minutos".
- modo: campo de estado para "modo".
- stateStore: campo de estado para "state store".
- lifecycleService: servico de negocio/coordenacao.
- historyWriter: campo de estado para "history writer".
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.extracao;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.OptionalLong;
import java.util.Properties;

import br.com.extrator.comandos.base.Comando;
import br.com.extrator.comandos.extracao.daemon.DaemonHistoryWriter;
import br.com.extrator.comandos.extracao.daemon.DaemonLifecycleService;
import br.com.extrator.comandos.extracao.daemon.DaemonPaths;
import br.com.extrator.comandos.extracao.daemon.DaemonStateStore;
import br.com.extrator.comandos.extracao.reconciliacao.LoopReconciliationService;
import br.com.extrator.comandos.extracao.reconciliacao.LoopReconciliationService.ReconciliationSummary;

/**
 * Gerencia loop de extracao em segundo plano (daemon).
 */
public class LoopDaemonComando implements Comando {
    private static final String FLAG_SEM_FATURAS_GRAPHQL = "--sem-faturas-graphql";
    private static final String FLAG_MODO_LOOP_DAEMON = "--modo-loop-daemon";
    private static final String MENSAGEM_FALHA_INTEGRIDADE = "Fluxo completo interrompido por falha de integridade";
    private static final long INTERVALO_MINUTOS = 30L;

    public enum Modo {
        START,
        STOP,
        STATUS,
        RUN
    }

    private final Modo modo;
    private final DaemonStateStore stateStore;
    private final DaemonLifecycleService lifecycleService;
    private final DaemonHistoryWriter historyWriter;

    public LoopDaemonComando(final Modo modo) {
        this.modo = modo;
        this.stateStore = DaemonStateStore.criarPadrao();
        this.lifecycleService = DaemonLifecycleService.criarPadrao(this.stateStore);
        this.historyWriter = DaemonHistoryWriter.criarPadrao();
    }

    LoopDaemonComando(final Modo modo,
                      final DaemonStateStore stateStore,
                      final DaemonLifecycleService lifecycleService,
                      final DaemonHistoryWriter historyWriter) {
        this.modo = modo;
        this.stateStore = stateStore;
        this.lifecycleService = lifecycleService;
        this.historyWriter = historyWriter;
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
        final OptionalLong pidExistente = lifecycleService.localizarPidDaemonAtivo();
        final String modoFaturas = descreverModoFaturas(incluirFaturasGraphQL);
        if (pidExistente.isPresent()) {
            stateStore.syncPidFile(pidExistente.getAsLong());
            stateStore.requestForceRun();
            final Properties estadoAtual = stateStore.loadState();
            final String statusAtual = estadoAtual.getProperty("status", "RUNNING");
            final String ultimoCiclo = valorOuNull(estadoAtual.getProperty("last_run_at"));
            final String proximoCiclo = valorOuNull(estadoAtual.getProperty("next_run_at"));
            stateStore.saveState(
                statusAtual,
                pidExistente.getAsLong(),
                "Loop daemon ja estava em execucao. Ciclo imediato solicitado manualmente. " + modoFaturas,
                ultimoCiclo,
                proximoCiclo
            );
            System.out.println("Loop daemon ja esta em execucao. PID: " + pidExistente.getAsLong());
            System.out.println("Solicitacao registrada: ciclo imediato sera executado assim que possivel.");
            System.out.println("Acompanhe em tempo real: " + DaemonPaths.DAEMON_STDOUT_FILE.toAbsolutePath());
            return;
        }

        stateStore.clearFileIfExists(stateStore.getPidFile());
        stateStore.clearFileIfExists(stateStore.getStopFile());
        stateStore.clearFileIfExists(stateStore.getForceRunFile());

        final List<String> comando = lifecycleService.construirComandoFilho(incluirFaturasGraphQL);
        final Process processo = lifecycleService.startChildProcess(comando);
        final long pid = processo.pid();
        stateStore.syncPidFile(pid);
        stateStore.saveState("STARTING", pid, "Processo daemon iniciado. " + modoFaturas, null, null);

        Thread.sleep(1200L);
        if (!processo.isAlive()) {
            stateStore.clearFileIfExists(stateStore.getPidFile());
            stateStore.clearFileIfExists(stateStore.getStopFile());
            throw new IllegalStateException("Falha ao iniciar loop daemon. Consulte " + DaemonPaths.DAEMON_STDOUT_FILE.toAbsolutePath());
        }

        System.out.println("Loop daemon iniciado com sucesso. PID: " + pid);
        System.out.println(modoFaturas);
        System.out.println("Log do daemon: " + DaemonPaths.DAEMON_STDOUT_FILE.toAbsolutePath());
    }

    private void pararDaemon() throws Exception {
        garantirDiretorioLogs();
        final List<ProcessHandle> processosAtivos = lifecycleService.localizarProcessosAlvoParada();
        if (processosAtivos.isEmpty()) {
            stateStore.clearFileIfExists(stateStore.getPidFile());
            stateStore.clearFileIfExists(stateStore.getStopFile());
            stateStore.clearFileIfExists(stateStore.getForceRunFile());
            stateStore.saveState("STOPPED", -1L, "Loop daemon ja estava parado.", null, null);
            System.out.println("Loop daemon nao estava em execucao.");
            return;
        }

        final long pid = processosAtivos.get(0).pid();
        stateStore.syncPidFile(pid);
        stateStore.requestStop();
        stateStore.saveState("STOPPING", pid, "Solicitado encerramento do loop daemon. processos_detectados=" + processosAtivos.size(), null, null);

        lifecycleService.aguardarEncerramentoProcessos(processosAtivos, 20_000L);

        for (final ProcessHandle processo : processosAtivos) {
            if (processo.isAlive()) {
                processo.destroy();
            }
        }
        lifecycleService.aguardarEncerramentoProcessos(processosAtivos, 2_000L);

        for (final ProcessHandle processo : processosAtivos) {
            if (processo.isAlive()) {
                processo.destroyForcibly();
            }
        }
        lifecycleService.aguardarEncerramentoProcessos(processosAtivos, 1_000L);

        final List<Long> pidsAindaAtivos = processosAtivos.stream()
            .filter(ProcessHandle::isAlive)
            .map(ProcessHandle::pid)
            .toList();
        if (!pidsAindaAtivos.isEmpty()) {
            stateStore.syncPidFile(pidsAindaAtivos.get(0));
            stateStore.saveState(
                "STOPPING",
                pidsAindaAtivos.get(0),
                "Encerramento solicitado, mas processos ainda ativos: " + pidsAindaAtivos,
                null,
                null
            );
            throw new IllegalStateException("Nao foi possivel parar completamente o loop daemon. PID(s) ativos: " + pidsAindaAtivos);
        }

        stateStore.clearFileIfExists(stateStore.getPidFile());
        stateStore.clearFileIfExists(stateStore.getStopFile());
        stateStore.clearFileIfExists(stateStore.getForceRunFile());
        stateStore.saveState("STOPPED", pid, "Loop daemon encerrado por comando de parada.", null, null);
        System.out.println("Loop daemon parado.");
    }

    private void exibirStatus() throws Exception {
        garantirDiretorioLogs();
        final OptionalLong pidArquivo = stateStore.readPidFile();
        final OptionalLong pidOpt = lifecycleService.localizarPidDaemonAtivo();
        if (pidOpt.isPresent()) {
            stateStore.syncPidFile(pidOpt.getAsLong());
        }
        final Properties state = stateStore.loadState();

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
        System.out.println("  Log: " + DaemonPaths.DAEMON_STDOUT_FILE.toAbsolutePath());

        if (!vivo && pidArquivo.isPresent()) {
            stateStore.saveState("STOPPED", pidArquivo.getAsLong(), "PID registrado nao esta mais ativo.", ultimoCiclo, proximoCiclo);
            stateStore.clearFileIfExists(stateStore.getPidFile());
        }
    }

    private void executarDaemon(final boolean incluirFaturasGraphQL) throws Exception {
        garantirDiretorioLogs();
        stateStore.clearFileIfExists(stateStore.getStopFile());
        stateStore.clearFileIfExists(stateStore.getForceRunFile());
        final long pid = ProcessHandle.current().pid();
        final String modoFaturas = descreverModoFaturas(incluirFaturasGraphQL);
        final LoopReconciliationService reconciliacaoService = LoopReconciliationService.criarPadrao(DaemonPaths.RECONCILIACAO_STATE_FILE);
        stateStore.syncPidFile(pid);
        stateStore.saveState("RUNNING", pid, "Daemon iniciado e aguardando ciclos. " + modoFaturas, null, null);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                stateStore.clearFileIfExists(stateStore.getPidFile());
                stateStore.clearFileIfExists(stateStore.getStopFile());
                stateStore.clearFileIfExists(stateStore.getForceRunFile());
                stateStore.saveState("STOPPED", pid, "Daemon finalizado.", null, null);
            } catch (final IOException | RuntimeException ignored) {
                // no-op
            }
        }));

        while (true) {
            if (stateStore.stopRequested()) {
                stateStore.saveState("STOPPED", pid, "Sinal de parada detectado antes do ciclo.", null, null);
                break;
            }

            final LocalDateTime inicio = LocalDateTime.now();
            final Path cicloLog = historyWriter.createCycleLogFile(inicio);
            stateStore.saveState(
                "RUNNING",
                pid,
                "Executando ciclo de extracao. " + modoFaturas + " | log_ciclo=" + cicloLog.toAbsolutePath(),
                inicio.toString(),
                null
            );

            boolean sucesso = true;
            String detalhe = "Ciclo concluido com sucesso.";
            try {
                try (SaidaCicloTee ignored = iniciarTeeCiclo(cicloLog)) {
                    if (incluirFaturasGraphQL) {
                        new ExecutarFluxoCompletoComando().executar(new String[] {"--fluxo-completo", FLAG_MODO_LOOP_DAEMON});
                    } else {
                        new ExecutarFluxoCompletoComando().executar(
                            new String[] {"--fluxo-completo", FLAG_SEM_FATURAS_GRAPHQL, FLAG_MODO_LOOP_DAEMON}
                        );
                    }
                }
            } catch (final Error e) {
                throw e;
            } catch (final RuntimeException e) {
                if (ehFalhaIntegridadeOperacional(e)) {
                    sucesso = true;
                    detalhe = "Ciclo concluido com alerta de integridade: " + historyWriter.summarizeMessage(e.getMessage());
                    System.err.println("ALERTA LOOP: Falha de integridade detectada. O loop continuara no proximo ciclo.");
                } else {
                    sucesso = false;
                    detalhe = "Falha no ciclo: " + historyWriter.summarizeMessage(e.getMessage());
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
            historyWriter.registerReconciliationHistory(inicio, fimExtracao, sucesso, resumoReconciliacao, cicloLog);

            final LocalDateTime fim = LocalDateTime.now();
            final String detalheCiclo = adicionarDetalheReconciliacao(detalhe, resumoReconciliacao);
            final DaemonHistoryWriter.CycleSummary resumoCiclo = historyWriter.buildCycleSummary(inicio, fim, cicloLog, sucesso, detalheCiclo);
            historyWriter.appendFinalSummary(cicloLog, resumoCiclo);
            historyWriter.registerCycleHistory(resumoCiclo);
            historyWriter.registerCompatibilityHistory(resumoCiclo);

            final LocalDateTime proximo = fim.plusMinutes(INTERVALO_MINUTOS);
            final boolean falhaReconciliacao = resumoReconciliacao != null
                && resumoReconciliacao.isAtivo()
                && resumoReconciliacao.getFalhas() > 0;
            final String statusDaemon = (sucesso && !falhaReconciliacao) ? "WAITING_NEXT_CYCLE" : "WAITING_NEXT_CYCLE_WITH_ERROR";
            stateStore.saveState(
                statusDaemon,
                pid,
                resumoCiclo.getDetalhe() + " " + modoFaturas + " | log_ciclo=" + cicloLog.toAbsolutePath(),
                fim.toString(),
                proximo.toString()
            );

            final ResultadoEspera resultadoEspera = aguardarProximoCiclo(proximo);
            if (resultadoEspera == ResultadoEspera.STOP_REQUESTED) {
                stateStore.saveState("STOPPED", pid, "Sinal de parada detectado durante espera.", fim.toString(), null);
                break;
            }
            if (resultadoEspera == ResultadoEspera.FORCE_RUN_REQUESTED) {
                stateStore.saveState("RUNNING", pid, "Disparo manual detectado: iniciando novo ciclo imediato. " + modoFaturas, fim.toString(), null);
            }
        }

        stateStore.clearFileIfExists(stateStore.getPidFile());
        stateStore.clearFileIfExists(stateStore.getStopFile());
        stateStore.clearFileIfExists(stateStore.getForceRunFile());
    }

    private ReconciliationSummary processarReconciliacao(final LoopReconciliationService reconciliacaoService,
                                                         final LocalDateTime inicio,
                                                         final LocalDateTime fimExtracao,
                                                         final boolean cicloSucesso,
                                                         final boolean incluirFaturasGraphQL) {
        try {
            return reconciliacaoService.processarPosCiclo(inicio, fimExtracao, cicloSucesso, incluirFaturasGraphQL);
        } catch (final RuntimeException e) {
            System.err.println("ALERTA LOOP: Falha ao processar reconciliacao automatica: " + historyWriter.summarizeMessage(e.getMessage()));
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
            detalhe.append(", detalhe_erro=").append(
                historyWriter.summarizeMessage(String.join(" | ", resumoReconciliacao.getDetalhesFalha()))
            );
        }
        detalhe.append("]");
        return detalhe.toString();
    }

    private ResultadoEspera aguardarProximoCiclo(final LocalDateTime proximo) throws InterruptedException {
        while (LocalDateTime.now().isBefore(proximo)) {
            if (stateStore.stopRequested()) {
                return ResultadoEspera.STOP_REQUESTED;
            }
            if (stateStore.forceRunRequested()) {
                try {
                    stateStore.clearFileIfExists(stateStore.getForceRunFile());
                } catch (final IOException ignored) {
                    // Se nao conseguir limpar, segue com ciclo imediato mesmo assim.
                }
                return ResultadoEspera.FORCE_RUN_REQUESTED;
            }
            Thread.sleep(1000L);
        }
        return ResultadoEspera.TIME_ELAPSED;
    }

    private void garantirDiretorioLogs() throws IOException {
        stateStore.ensureDaemonDirectory();
        historyWriter.ensureDirectories();
        if (!Files.exists(DaemonPaths.RUNTIME_DIR)) {
            Files.createDirectories(DaemonPaths.RUNTIME_DIR);
        }
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

    private String valorOuNull(final String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor;
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

    private static final class SaidaCicloTee implements AutoCloseable {
        private final PrintStream originalOut;
        private final PrintStream originalErr;
        private final PrintStream teeOut;
        private final PrintStream teeErr;
        private final FileOutputStream arquivoOut;
        private final FileOutputStream arquivoErr;

        private SaidaCicloTee(final PrintStream originalOut,
                              final PrintStream originalErr,
                              final PrintStream teeOut,
                              final PrintStream teeErr,
                              final FileOutputStream arquivoOut,
                              final FileOutputStream arquivoErr) {
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
