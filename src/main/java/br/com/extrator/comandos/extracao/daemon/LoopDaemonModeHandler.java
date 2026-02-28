package br.com.extrator.comandos.extracao.daemon;

@FunctionalInterface
public interface LoopDaemonModeHandler {
    void executar(boolean incluirFaturasGraphQL) throws Exception;
}
