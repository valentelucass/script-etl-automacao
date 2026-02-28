package br.com.extrator.comandos.extracao.daemon;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.function.LongSupplier;

import br.com.extrator.comandos.extracao.ExecutarFluxoCompletoComando;
import br.com.extrator.comandos.extracao.reconciliacao.LoopReconciliationService;
import br.com.extrator.comandos.extracao.reconciliacao.LoopReconciliationService.ReconciliationSummary;

public final class LoopDaemonRunHandler implements LoopDaemonModeHandler {
    @FunctionalInterface
    public interface FluxoExecutor {
        void executar(boolean incluirFaturasGraphQL) throws Exception;
    }

    @FunctionalInterface
    public interface ReconciliationProcessor {
        ReconciliationSummary processar(LocalDateTime inicio,
                                        LocalDateTime fimExtracao,
                                        boolean cicloSucesso,
                                        boolean incluirFaturasGraphQL);
    }

    public enum WaitResult {
        STOP_REQUESTED,
        FORCE_RUN_REQUESTED,
        TIME_ELAPSED
    }

    @FunctionalInterface
    public interface CycleWaitStrategy {
        WaitResult aguardar(LocalDateTime proximoCiclo, DaemonStateStore stateStore) throws InterruptedException;
    }

    @FunctionalInterface
    public interface TeeFactory {
        AutoCloseable abrir(Path cicloLog) throws IOException;
    }

    private final DaemonStateStore stateStore;
    private final DaemonHistoryWriter historyWriter;
    private final FluxoExecutor fluxoExecutor;
    private final ReconciliationProcessor reconciliationProcessor;
    private final CycleWaitStrategy waitStrategy;
    private final TeeFactory teeFactory;
    private final LongSupplier pidSupplier;
    private final long intervaloMinutos;
    private final boolean registrarShutdownHook;

    public LoopDaemonRunHandler(final DaemonStateStore stateStore, final DaemonHistoryWriter historyWriter) {
        this(
            stateStore,
            historyWriter,
            LoopDaemonRunHandler::executarFluxoCompletoPadrao,
            criarProcessadorReconciliacaoPadrao(),
            LoopDaemonRunHandler::aguardarProximoCicloPadrao,
            LoopDaemonRunHandler::iniciarTeeCicloPadrao,
            () -> ProcessHandle.current().pid(),
            LoopDaemonHandlerSupport.INTERVALO_MINUTOS_PADRAO,
            true
        );
    }

    LoopDaemonRunHandler(final DaemonStateStore stateStore,
                         final DaemonHistoryWriter historyWriter,
                         final FluxoExecutor fluxoExecutor,
                         final ReconciliationProcessor reconciliationProcessor,
                         final CycleWaitStrategy waitStrategy,
                         final TeeFactory teeFactory,
                         final LongSupplier pidSupplier,
                         final long intervaloMinutos,
                         final boolean registrarShutdownHook) {
        this.stateStore = stateStore;
        this.historyWriter = historyWriter;
        this.fluxoExecutor = fluxoExecutor;
        this.reconciliationProcessor = reconciliationProcessor;
        this.waitStrategy = waitStrategy;
        this.teeFactory = teeFactory;
        this.pidSupplier = pidSupplier;
        this.intervaloMinutos = intervaloMinutos;
        this.registrarShutdownHook = registrarShutdownHook;
    }

    @Override
    public void executar(final boolean incluirFaturasGraphQL) throws Exception {
        LoopDaemonHandlerSupport.garantirDiretorioLogs(stateStore, historyWriter);
        stateStore.clearFileIfExists(stateStore.getStopFile());
        stateStore.clearFileIfExists(stateStore.getForceRunFile());

        final long pid = pidSupplier.getAsLong();
        final String modoFaturas = LoopDaemonHandlerSupport.descreverModoFaturas(incluirFaturasGraphQL);
        stateStore.syncPidFile(pid);
        stateStore.saveState("RUNNING", pid, "Daemon iniciado e aguardando ciclos. " + modoFaturas, null, null);

        if (registrarShutdownHook) {
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
        }

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
                try (AutoCloseable ignored = teeFactory.abrir(cicloLog)) {
                    fluxoExecutor.executar(incluirFaturasGraphQL);
                }
            } catch (final Error e) {
                throw e;
            } catch (final RuntimeException e) {
                if (LoopDaemonHandlerSupport.ehFalhaIntegridadeOperacional(e)) {
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
                inicio,
                fimExtracao,
                sucesso,
                incluirFaturasGraphQL
            );
            historyWriter.registerReconciliationHistory(inicio, fimExtracao, sucesso, resumoReconciliacao, cicloLog);

            final LocalDateTime fim = LocalDateTime.now();
            final String detalheCiclo = adicionarDetalheReconciliacao(detalhe, resumoReconciliacao);
            final DaemonHistoryWriter.CycleSummary resumoCiclo = historyWriter.buildCycleSummary(
                inicio,
                fim,
                cicloLog,
                sucesso,
                detalheCiclo
            );
            historyWriter.appendFinalSummary(cicloLog, resumoCiclo);
            historyWriter.registerCycleHistory(resumoCiclo);
            historyWriter.registerCompatibilityHistory(resumoCiclo);

            final LocalDateTime proximo = fim.plusMinutes(intervaloMinutos);
            final String statusDaemon = determinarStatusDaemon(sucesso, resumoReconciliacao);
            stateStore.saveState(
                statusDaemon,
                pid,
                resumoCiclo.getDetalhe() + " " + modoFaturas + " | log_ciclo=" + cicloLog.toAbsolutePath(),
                fim.toString(),
                proximo.toString()
            );

            final WaitResult resultadoEspera = waitStrategy.aguardar(proximo, stateStore);
            if (resultadoEspera == WaitResult.STOP_REQUESTED) {
                stateStore.saveState("STOPPED", pid, "Sinal de parada detectado durante espera.", fim.toString(), null);
                break;
            }
            if (resultadoEspera == WaitResult.FORCE_RUN_REQUESTED) {
                stateStore.saveState(
                    "RUNNING",
                    pid,
                    "Disparo manual detectado: iniciando novo ciclo imediato. " + modoFaturas,
                    fim.toString(),
                    null
                );
            }
        }

        stateStore.clearFileIfExists(stateStore.getPidFile());
        stateStore.clearFileIfExists(stateStore.getStopFile());
        stateStore.clearFileIfExists(stateStore.getForceRunFile());
    }

    static String determinarStatusDaemon(final boolean sucesso, final ReconciliationSummary resumoReconciliacao) {
        final boolean falhaReconciliacao = resumoReconciliacao != null
            && resumoReconciliacao.isAtivo()
            && resumoReconciliacao.getFalhas() > 0;
        return (sucesso && !falhaReconciliacao) ? "WAITING_NEXT_CYCLE" : "WAITING_NEXT_CYCLE_WITH_ERROR";
    }

    private ReconciliationSummary processarReconciliacao(final LocalDateTime inicio,
                                                         final LocalDateTime fimExtracao,
                                                         final boolean cicloSucesso,
                                                         final boolean incluirFaturasGraphQL) {
        try {
            return reconciliationProcessor.processar(inicio, fimExtracao, cicloSucesso, incluirFaturasGraphQL);
        } catch (final RuntimeException e) {
            System.err.println(
                "ALERTA LOOP: Falha ao processar reconciliacao automatica: " + historyWriter.summarizeMessage(e.getMessage())
            );
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

    private static void executarFluxoCompletoPadrao(final boolean incluirFaturasGraphQL) throws Exception {
        if (incluirFaturasGraphQL) {
            new ExecutarFluxoCompletoComando().executar(
                new String[] {"--fluxo-completo", LoopDaemonHandlerSupport.FLAG_MODO_LOOP_DAEMON}
            );
            return;
        }
        new ExecutarFluxoCompletoComando().executar(
            new String[] {
                "--fluxo-completo",
                LoopDaemonHandlerSupport.FLAG_SEM_FATURAS_GRAPHQL,
                LoopDaemonHandlerSupport.FLAG_MODO_LOOP_DAEMON
            }
        );
    }

    private static ReconciliationProcessor criarProcessadorReconciliacaoPadrao() {
        final LoopReconciliationService service = LoopReconciliationService.criarPadrao(DaemonPaths.RECONCILIACAO_STATE_FILE);
        return service::processarPosCiclo;
    }

    private static WaitResult aguardarProximoCicloPadrao(final LocalDateTime proximoCiclo, final DaemonStateStore stateStore)
        throws InterruptedException {
        while (LocalDateTime.now().isBefore(proximoCiclo)) {
            if (stateStore.stopRequested()) {
                return WaitResult.STOP_REQUESTED;
            }
            if (stateStore.forceRunRequested()) {
                try {
                    stateStore.clearFileIfExists(stateStore.getForceRunFile());
                } catch (final IOException ignored) {
                    // Em caso de falha de limpeza, executa ciclo imediato mesmo assim.
                }
                return WaitResult.FORCE_RUN_REQUESTED;
            }
            Thread.sleep(1000L);
        }
        return WaitResult.TIME_ELAPSED;
    }

    private static AutoCloseable iniciarTeeCicloPadrao(final Path cicloLog) throws IOException {
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
}
