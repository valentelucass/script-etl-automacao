/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/extracao/daemon/LoopDaemonRunHandler.java
Classe  : LoopDaemonRunHandler (class)
Pacote  : br.com.extrator.comandos.cli.extracao.daemon
Modulo  : Comando CLI (daemon)
Papel   : Executa o ciclo continuo do loop daemon e reconciliacao pos-ciclo.

Conecta com:
- ExecutarFluxoCompletoComando (comandos.extracao)
- LoopReconciliationService (comandos.extracao.reconciliacao)
- DaemonStateStore
- DaemonHistoryWriter

Fluxo geral:
1) Inicializa estado runtime do daemon.
2) Executa ciclo de extracao em loop com controle de parada/forca.
3) Persiste historico e agenda proximo ciclo.

Estrutura interna:
Metodos principais:
- executar(...1 args): loop principal do daemon.
- executarFluxoCompletoPadrao(...1 args): executa fluxo ETL padrao.
- aguardarProximoCicloPadrao(...2 args): aguarda gatilho do proximo ciclo.
Atributos-chave:
- stateStore: estado/persistencia do daemon.
- historyWriter: escrita de historico operacional.
- fluxoExecutor: callback do fluxo de extracao.
- reconciliationProcessor: callback de reconciliacao.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.cli.extracao.daemon;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;

import br.com.extrator.aplicacao.extracao.ExecutionLockBusyException;
import br.com.extrator.aplicacao.extracao.FluxoCompletoUseCase;
import br.com.extrator.comandos.cli.extracao.reconciliacao.LoopReconciliationService;
import br.com.extrator.comandos.cli.extracao.reconciliacao.LoopReconciliationService.ReconciliationSummary;
import br.com.extrator.suporte.concorrencia.ExecutionTimeoutException;
import br.com.extrator.suporte.concorrencia.OperationTimeoutGuard;
import br.com.extrator.suporte.configuracao.ConfigEtl;
import br.com.extrator.suporte.observabilidade.ExecutionContext;

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
                                        boolean incluirFaturasGraphQL,
                                        String detalheFalhaCiclo);
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
    private final Function<Boolean, Duration> cycleTimeoutProvider;

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
            true,
            ConfigEtl::obterTimeoutCicloDaemon
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
                         final boolean registrarShutdownHook,
                         final Function<Boolean, Duration> cycleTimeoutProvider) {
        this.stateStore = stateStore;
        this.historyWriter = historyWriter;
        this.fluxoExecutor = fluxoExecutor;
        this.reconciliationProcessor = reconciliationProcessor;
        this.waitStrategy = waitStrategy;
        this.teeFactory = teeFactory;
        this.pidSupplier = pidSupplier;
        this.intervaloMinutos = intervaloMinutos;
        this.registrarShutdownHook = registrarShutdownHook;
        this.cycleTimeoutProvider = cycleTimeoutProvider;
    }

    @Override
    public void executar(final boolean incluirFaturasGraphQL) throws Exception {
        LoopDaemonHandlerSupport.garantirDiretorioLogs(stateStore, historyWriter);
        stateStore.clearFileIfExists(stateStore.getStopFile());
        stateStore.clearFileIfExists(stateStore.getForceRunFile());
        final int limiteAlertasConsecutivos = ConfigEtl.obterLoopDaemonMaxConsecutiveAlertCycles();

        final long pid = pidSupplier.getAsLong();
        final String modoFaturas = LoopDaemonHandlerSupport.descreverModoFaturas(incluirFaturasGraphQL);
        stateStore.syncPidFile(pid);
        stateStore.saveState("RUNNING", pid, "Daemon iniciado e aguardando ciclos. " + modoFaturas, null, null, 0, 0);

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
            final String cycleId = UUID.randomUUID().toString();
            ExecutionContext.setCycleId(cycleId);
            stateStore.saveState(
                "RUNNING",
                pid,
                "Executando ciclo de extracao. cycle_id=" + cycleId + " " + modoFaturas + " | log_ciclo=" + cicloLog.toAbsolutePath(),
                inicio.toString(),
                null
            );

            boolean sucesso = true;
            boolean cicloComAlertaIntegridade = false;
            boolean cicloPuladoPorLock = false;
            String detalhe = "Ciclo concluido com sucesso.";
            try {
                try (AutoCloseable ignored = teeFactory.abrir(cicloLog)) {
                    executarFluxoComWatchdog(incluirFaturasGraphQL);
                }
            } catch (final Error e) {
                throw e;
            } catch (final Exception e) {
                if (ehFalhaLockOcupado(e)) {
                    sucesso = true;
                    cicloPuladoPorLock = true;
                    detalhe = "Ciclo pulado porque outra execucao esta segurando o lock global [cycle_id="
                        + cycleId
                        + "]: "
                        + historyWriter.summarizeMessage(e.getMessage());
                    System.err.println("AVISO LOOP: Lock global ocupado por outra execucao. O loop tentara novamente no proximo ciclo.");
                } else if (LoopDaemonHandlerSupport.ehFalhaIntegridadeOperacional(e)) {
                    sucesso = true;
                    cicloComAlertaIntegridade = true;
                    detalhe = "Ciclo concluido com alerta de integridade [cycle_id=" + cycleId + "]: "
                        + historyWriter.summarizeMessage(e.getMessage());
                    System.err.println("ALERTA LOOP: Falha de integridade detectada. O loop continuara no proximo ciclo.");
                } else if (e instanceof ExecutionTimeoutException) {
                    sucesso = false;
                    detalhe = "Falha no ciclo por timeout global [cycle_id=" + cycleId + "]: "
                        + historyWriter.summarizeMessage(e.getMessage());
                } else {
                    sucesso = false;
                    detalhe = "Falha no ciclo [cycle_id=" + cycleId + "]: " + historyWriter.summarizeMessage(e.getMessage());
                }
            }

            final LocalDateTime fimExtracao = LocalDateTime.now();
            final ReconciliationSummary resumoReconciliacao = cicloPuladoPorLock
                ? ReconciliationSummary.semAcao()
                : processarReconciliacao(
                    inicio,
                    fimExtracao,
                    sucesso,
                    incluirFaturasGraphQL,
                    sucesso ? null : detalhe
                );
            historyWriter.registerReconciliationHistory(inicio, fimExtracao, sucesso, resumoReconciliacao, cicloLog);

            final LocalDateTime fim = LocalDateTime.now();
            final String detalheCiclo = cicloPuladoPorLock
                ? adicionarDetalheReconciliacaoIgnorada(detalhe)
                : adicionarDetalheReconciliacao(detalhe, resumoReconciliacao);
            final CycleSummary resumoCiclo = historyWriter.buildCycleSummary(
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
            final boolean falhaReconciliacao = houveFalhaReconciliacao(resumoReconciliacao);
            final boolean cicloSaudavel = sucesso && !cicloComAlertaIntegridade && !falhaReconciliacao;
            final int consecutiveAlertCycles = cicloPuladoPorLock
                ? stateStore.readConsecutiveAlertCycles()
                : cicloComAlertaIntegridade
                    ? stateStore.readConsecutiveAlertCycles() + 1
                    : 0;
            final int consecutiveNonSuccessCycles = cicloPuladoPorLock
                ? stateStore.readConsecutiveNonSuccessCycles()
                : cicloSaudavel
                    ? 0
                    : stateStore.readConsecutiveNonSuccessCycles() + 1;
            final boolean waitingManualIntervention = !cicloPuladoPorLock
                && (consecutiveAlertCycles >= limiteAlertasConsecutivos
                    || consecutiveNonSuccessCycles >= limiteAlertasConsecutivos);
            final String statusDaemon = cicloPuladoPorLock
                ? "WAITING_NEXT_CYCLE"
                : waitingManualIntervention
                    ? "WAITING_MANUAL_INTERVENTION"
                    : determinarStatusDaemon(sucesso, cicloComAlertaIntegridade, resumoReconciliacao);
            final String detalheOperacional = adicionarContadoresOperacionais(
                resumoCiclo.getDetalhe(),
                consecutiveAlertCycles,
                consecutiveNonSuccessCycles,
                limiteAlertasConsecutivos,
                waitingManualIntervention
            );
            stateStore.saveState(
                statusDaemon,
                pid,
                detalheOperacional + " cycle_id=" + cycleId + " " + modoFaturas + " | log_ciclo=" + cicloLog.toAbsolutePath(),
                fim.toString(),
                proximo.toString(),
                consecutiveAlertCycles,
                consecutiveNonSuccessCycles
            );
            ExecutionContext.clearCycleId();
            if (waitingManualIntervention) {
                System.err.println(
                    "ALERTA LOOP: limite de degradacao atingido. O daemon foi colocado em WAITING_MANUAL_INTERVENTION."
                );
                break;
            }

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
        return determinarStatusDaemon(sucesso, false, resumoReconciliacao);
    }

    static String determinarStatusDaemon(final boolean sucesso,
                                         final boolean cicloComAlertaIntegridade,
                                         final ReconciliationSummary resumoReconciliacao) {
        final boolean falhaReconciliacao = houveFalhaReconciliacao(resumoReconciliacao);
        return (sucesso && !falhaReconciliacao && !cicloComAlertaIntegridade)
            ? "WAITING_NEXT_CYCLE"
            : "WAITING_NEXT_CYCLE_WITH_ERROR";
    }

    private static boolean ehFalhaLockOcupado(final Throwable erro) {
        Throwable atual = erro;
        while (atual != null) {
            if (atual instanceof ExecutionLockBusyException) {
                return true;
            }
            final String mensagem = atual.getMessage();
            if (mensagem != null
                && mensagem.toLowerCase(Locale.ROOT).contains("segurando o lock global")) {
                return true;
            }
            atual = atual.getCause();
        }
        return false;
    }

    private ReconciliationSummary processarReconciliacao(final LocalDateTime inicio,
                                                         final LocalDateTime fimExtracao,
                                                         final boolean cicloSucesso,
                                                         final boolean incluirFaturasGraphQL,
                                                         final String detalheFalhaCiclo) {
        try {
            return reconciliationProcessor.processar(
                inicio,
                fimExtracao,
                cicloSucesso,
                incluirFaturasGraphQL,
                detalheFalhaCiclo
            );
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

    private String adicionarDetalheReconciliacaoIgnorada(final String detalheBase) {
        return (detalheBase == null ? "Sem detalhes." : detalheBase)
            + " | reconciliacao[ignorada=true, motivo=lock_global_ocupado]";
    }

    private String adicionarContadoresOperacionais(final String detalheBase,
                                                   final int consecutiveAlertCycles,
                                                   final int consecutiveNonSuccessCycles,
                                                   final int limiteAlertasConsecutivos,
                                                   final boolean waitingManualIntervention) {
        final StringBuilder detalhe = new StringBuilder(detalheBase == null ? "Sem detalhes." : detalheBase);
        detalhe.append(" | daemon_health[alert_cycles=").append(consecutiveAlertCycles);
        detalhe.append(", non_success_cycles=").append(consecutiveNonSuccessCycles);
        detalhe.append(", threshold=").append(limiteAlertasConsecutivos);
        detalhe.append(", manual_intervention=").append(waitingManualIntervention);
        detalhe.append("]");
        return detalhe.toString();
    }

    private static boolean houveFalhaReconciliacao(final ReconciliationSummary resumoReconciliacao) {
        return resumoReconciliacao != null
            && resumoReconciliacao.isAtivo()
            && resumoReconciliacao.getFalhas() > 0;
    }

    private static void executarFluxoCompletoPadrao(final boolean incluirFaturasGraphQL) throws Exception {
        new FluxoCompletoUseCase().executar(incluirFaturasGraphQL, true);
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
        return DaemonCycleTee.abrir(cicloLog);
    }

    private void executarFluxoComWatchdog(final boolean incluirFaturasGraphQL) throws Exception {
        final Duration timeout = resolverTimeoutCiclo(incluirFaturasGraphQL);
        OperationTimeoutGuard.executar(
            "ciclo_loop_daemon",
            timeout,
            () -> {
                fluxoExecutor.executar(incluirFaturasGraphQL);
                return null;
            }
        );
    }

    private Duration resolverTimeoutCiclo(final boolean incluirFaturasGraphQL) {
        if (cycleTimeoutProvider == null) {
            return ConfigEtl.obterTimeoutCicloDaemon(incluirFaturasGraphQL);
        }
        final Duration timeout = cycleTimeoutProvider.apply(incluirFaturasGraphQL);
        return timeout == null ? ConfigEtl.obterTimeoutCicloDaemon(incluirFaturasGraphQL) : timeout;
    }
}
