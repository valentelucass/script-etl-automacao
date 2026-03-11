package br.com.extrator.suporte.observabilidade;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/suporte/observabilidade/ExecutionContext.java
Classe  : ExecutionContext (class)
Pacote  : br.com.extrator.suporte.observabilidade
Modulo  : Suporte - Observabilidade
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import org.slf4j.MDC;

/**
 * Tracks execution context (execution id and command) in MDC.
 */
public final class ExecutionContext {

    public static final String MDC_EXECUTION_ID = "etl_execution_id";
    public static final String MDC_COMMAND = "etl_command";
    private static final String NA = "n/a";

    private ExecutionContext() {
        // utility
    }

    public static String initialize(final String commandName) {
        final String executionId = UUID.randomUUID().toString();
        MDC.put(MDC_EXECUTION_ID, executionId);
        MDC.put(MDC_COMMAND, sanitizeCommand(commandName));
        return executionId;
    }

    public static String currentExecutionId() {
        final String value = MDC.get(MDC_EXECUTION_ID);
        return value == null || value.isBlank() ? NA : value;
    }

    public static Runnable wrapRunnable(final Runnable delegate) {
        if (delegate == null) {
            return () -> {
                // noop
            };
        }

        final Map<String, String> capturedContext = MDC.getCopyOfContextMap();
        return () -> {
            MDC.clear();
            try {
                if (capturedContext != null) {
                    MDC.setContextMap(capturedContext);
                }
                delegate.run();
            } finally {
                MDC.clear();
            }
        };
    }

    public static <V> Callable<V> wrapCallable(final Callable<V> delegate) {
        if (delegate == null) {
            return () -> null;
        }
        final Map<String, String> capturedContext = MDC.getCopyOfContextMap();
        return () -> {
            MDC.clear();
            try {
                if (capturedContext != null) {
                    MDC.setContextMap(capturedContext);
                }
                return delegate.call();
            } finally {
                MDC.clear();
            }
        };
    }

    public static void clear() {
        MDC.clear();
    }

    private static String sanitizeCommand(final String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return NA;
        }
        return commandName.trim().replaceAll("\\s+", "_");
    }
}
