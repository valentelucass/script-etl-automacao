/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/extracao/daemon/LoopDaemonStartHandler.java
Classe  : LoopDaemonStartHandler
Pacote  : br.com.extrator.comandos.cli.extracao.daemon
Modulo  : Comando CLI (daemon)
Papel   : Inicia o loop daemon ou solicita execucao imediata se ja estiver ativo.

Conecta com:
- DaemonStateStore
- DaemonLifecycleService
- DaemonHistoryWriter
- LoopDaemonHandlerSupport
- DaemonPaths

Fluxo geral:
1) Garante estrutura de logs e estado.
2) Detecta daemon ativo e solicita ciclo imediato quando aplicavel.
3) Inicia processo filho em modo daemon quando nao ha instancia ativa.
4) Atualiza estado/PID e valida se o processo subiu corretamente.

Estrutura interna:
Metodos principais:
- executar(...1 args): inicia processo daemon ou reaproveita o ativo.
Atributos-chave:
- stateStore: persiste estado e arquivos de controle.
- lifecycleService: gerencia descoberta e start de processos.
- historyWriter: dependencia de suporte para historico/log estrutural.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.cli.extracao.daemon;

import java.util.List;
import java.util.OptionalLong;
import java.util.Properties;

public final class LoopDaemonStartHandler implements LoopDaemonModeHandler {
    private final DaemonStateStore stateStore;
    private final DaemonLifecycleService lifecycleService;
    private final DaemonHistoryWriter historyWriter;

    public LoopDaemonStartHandler(final DaemonStateStore stateStore,
                                  final DaemonLifecycleService lifecycleService,
                                  final DaemonHistoryWriter historyWriter) {
        this.stateStore = stateStore;
        this.lifecycleService = lifecycleService;
        this.historyWriter = historyWriter;
    }

    @Override
    public void executar(final boolean incluirFaturasGraphQL) throws Exception {
        LoopDaemonHandlerSupport.garantirDiretorioLogs(stateStore, historyWriter);
        final OptionalLong pidExistente = lifecycleService.localizarPidDaemonAtivo();
        final String modoFaturas = LoopDaemonHandlerSupport.descreverModoFaturas(incluirFaturasGraphQL);

        if (pidExistente.isPresent()) {
            stateStore.syncPidFile(pidExistente.getAsLong());
            stateStore.requestForceRun();
            final Properties estadoAtual = stateStore.loadState();
            final String statusAtual = estadoAtual.getProperty("status", "RUNNING");
            final String ultimoCiclo = LoopDaemonHandlerSupport.valorOuNull(estadoAtual.getProperty("last_run_at"));
            final String proximoCiclo = LoopDaemonHandlerSupport.valorOuNull(estadoAtual.getProperty("next_run_at"));
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
            throw new IllegalStateException(
                "Falha ao iniciar loop daemon. Consulte " + DaemonPaths.DAEMON_STDOUT_FILE.toAbsolutePath()
            );
        }

        System.out.println("Loop daemon iniciado com sucesso. PID: " + pid);
        System.out.println(modoFaturas);
        System.out.println("Log do daemon: " + DaemonPaths.DAEMON_STDOUT_FILE.toAbsolutePath());
    }
}
