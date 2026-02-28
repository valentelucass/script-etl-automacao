package br.com.extrator.comandos.extracao.daemon;

import java.util.OptionalLong;
import java.util.Properties;

public final class LoopDaemonStatusHandler implements LoopDaemonModeHandler {
    private final DaemonStateStore stateStore;
    private final DaemonLifecycleService lifecycleService;
    private final DaemonHistoryWriter historyWriter;

    public LoopDaemonStatusHandler(final DaemonStateStore stateStore,
                                   final DaemonLifecycleService lifecycleService,
                                   final DaemonHistoryWriter historyWriter) {
        this.stateStore = stateStore;
        this.lifecycleService = lifecycleService;
        this.historyWriter = historyWriter;
    }

    @Override
    public void executar(final boolean incluirFaturasGraphQL) throws Exception {
        LoopDaemonHandlerSupport.garantirDiretorioLogs(stateStore, historyWriter);
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
}
