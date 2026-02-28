package br.com.extrator.comandos.extracao.daemon;

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
