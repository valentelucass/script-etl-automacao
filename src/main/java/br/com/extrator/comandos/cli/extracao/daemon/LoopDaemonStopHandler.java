/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/extracao/daemon/LoopDaemonStopHandler.java
Classe  : LoopDaemonStopHandler
Pacote  : br.com.extrator.comandos.cli.extracao.daemon
Modulo  : Comando CLI (daemon)
Papel   : Solicita parada do loop daemon e garante encerramento dos processos alvo.

Conecta com:
- DaemonStateStore
- DaemonLifecycleService
- DaemonHistoryWriter
- LoopDaemonHandlerSupport

Fluxo geral:
1) Garante estrutura de logs e estado.
2) Localiza processos alvo do daemon.
3) Solicita parada graciosa e aguarda encerramento.
4) Escala para destroy/destroyForcibly quando necessario.
5) Atualiza estado final e limpa arquivos de controle.

Estrutura interna:
Metodos principais:
- executar(...1 args): coordena parada do daemon com fallback forcado.
Atributos-chave:
- stateStore: persiste estado e controla arquivos PID/stop/force-run.
- lifecycleService: localiza processos e coordena espera de encerramento.
- historyWriter: dependencia de suporte para historico/log estrutural.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.cli.extracao.daemon;

import java.util.List;

public final class LoopDaemonStopHandler implements LoopDaemonModeHandler {
    private final DaemonStateStore stateStore;
    private final DaemonLifecycleService lifecycleService;
    private final DaemonHistoryWriter historyWriter;

    public LoopDaemonStopHandler(final DaemonStateStore stateStore,
                                 final DaemonLifecycleService lifecycleService,
                                 final DaemonHistoryWriter historyWriter) {
        this.stateStore = stateStore;
        this.lifecycleService = lifecycleService;
        this.historyWriter = historyWriter;
    }

    @Override
    public void executar(final boolean incluirFaturasGraphQL) throws Exception {
        LoopDaemonHandlerSupport.garantirDiretorioLogs(stateStore, historyWriter);
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
        stateStore.saveState(
            "STOPPING",
            pid,
            "Solicitado encerramento do loop daemon. processos_detectados=" + processosAtivos.size(),
            null,
            null
        );

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
}
