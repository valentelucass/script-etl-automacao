package br.com.extrator.comandos.extracao.daemon;

import java.io.IOException;
import java.nio.file.Files;

public final class LoopDaemonHandlerSupport {
    public static final String FLAG_SEM_FATURAS_GRAPHQL = "--sem-faturas-graphql";
    public static final String FLAG_MODO_LOOP_DAEMON = "--modo-loop-daemon";
    public static final String MENSAGEM_FALHA_INTEGRIDADE = "Fluxo completo interrompido por falha de integridade";
    public static final long INTERVALO_MINUTOS_PADRAO = 30L;

    private LoopDaemonHandlerSupport() {
    }

    public static void garantirDiretorioLogs(final DaemonStateStore stateStore, final DaemonHistoryWriter historyWriter)
        throws IOException {
        stateStore.ensureDaemonDirectory();
        historyWriter.ensureDirectories();
        if (!Files.exists(DaemonPaths.RUNTIME_DIR)) {
            Files.createDirectories(DaemonPaths.RUNTIME_DIR);
        }
    }

    public static String descreverModoFaturas(final boolean incluirFaturasGraphQL) {
        return "Faturas GraphQL: " + (incluirFaturasGraphQL
            ? "INCLUIDO"
            : "DESABILITADO (" + FLAG_SEM_FATURAS_GRAPHQL + ")");
    }

    public static String valorOuNull(final String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor;
    }

    public static boolean ehFalhaIntegridadeOperacional(final Throwable erro) {
        Throwable atual = erro;
        while (atual != null) {
            final String mensagem = atual.getMessage();
            if (mensagem != null && mensagem.contains(MENSAGEM_FALHA_INTEGRIDADE)) {
                return true;
            }
            atual = atual.getCause();
        }
        return false;
    }
}
