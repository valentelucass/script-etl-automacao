package br.com.extrator.suporte.concorrencia;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.suporte.configuracao.ConfigEtl;
import br.com.extrator.suporte.observabilidade.ExecutionContext;

public final class OperationTimeoutGuard {
    private static final Logger logger = LoggerFactory.getLogger(OperationTimeoutGuard.class);
    private static final Duration TIMEOUT_PADRAO = Duration.ofMinutes(10);
    private static final String THREAD_PREFIX = "timeout-guard-";
    private static final long MAX_GRACE_APOS_TIMEOUT_MS = 500L;
    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    private OperationTimeoutGuard() {
    }

    public static <T> T executar(final String nomeOperacao,
                                 final Duration timeout,
                                 final Callable<T> tarefa) throws Exception {
        final Duration limite = sanitizarTimeout(timeout);
        final AtomicReference<Thread> workerThreadRef = new AtomicReference<>();
        final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            final Thread thread = new Thread(
                runnable,
                THREAD_PREFIX + THREAD_COUNTER.incrementAndGet() + "-" + normalizarNomeOperacao(nomeOperacao)
            );
            thread.setDaemon(true);
            workerThreadRef.set(thread);
            return thread;
        });

        Future<T> future = null;
        T resultado = null;
        Exception pendingException = null;
        Error pendingError = null;
        try {
            future = executor.submit(ExecutionContext.wrapCallable(tarefa));
            resultado = future.get(limite.toMillis(), TimeUnit.MILLISECONDS);
        } catch (final TimeoutException e) {
            cancelar(future);
            pendingException = new ExecutionTimeoutException(
                "Timeout ao executar " + descreverOperacao(nomeOperacao) + " apos " + limite.toMillis() + " ms",
                e
            );
        } catch (final InterruptedException e) {
            cancelar(future);
            Thread.currentThread().interrupt();
            pendingException = e;
        } catch (final ExecutionException e) {
            final Throwable causa = e.getCause() == null ? e : e.getCause();
            if (causa instanceof Error error) {
                pendingError = error;
            } else if (causa instanceof Exception exception) {
                pendingException = exception;
            } else {
                pendingException = new RuntimeException(
                    "Falha nao tratada ao executar " + descreverOperacao(nomeOperacao),
                    causa
                );
            }
        } finally {
            pendingException = enriquecerComThreadLeakSeNecessario(
                nomeOperacao,
                pendingException,
                encerrarExecutor(
                    nomeOperacao,
                    executor,
                    workerThreadRef.get(),
                    pendingException instanceof ExecutionTimeoutException
                )
            );
        }

        if (pendingError != null) {
            throw pendingError;
        }
        if (pendingException != null) {
            throw pendingException;
        }
        return resultado;
    }

    private static void cancelar(final Future<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    private static ThreadLeakDetector.LeakReport encerrarExecutor(final String nomeOperacao,
                                                                  final ExecutorService executor,
                                                                  final Thread workerThread,
                                                                  final boolean timeoutJaAtingido) {
        final long graceMs = resolverGraceEncerramento(timeoutJaAtingido);
        boolean terminated = false;
        try {
            executor.shutdown();
            terminated = executor.awaitTermination(graceMs, TimeUnit.MILLISECONDS);
            if (!terminated) {
                executor.shutdownNow();
                terminated = executor.awaitTermination(graceMs, TimeUnit.MILLISECONDS);
                if (!terminated) {
                    logger.warn(
                        "Executor do timeout guard nao finalizou dentro do grace period | operacao={} | grace_ms={}",
                        descreverOperacao(nomeOperacao),
                        graceMs
                    );
                }
            }
        } catch (final InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (terminated) {
            return ThreadLeakDetector.LeakReport.none();
        }

        final ThreadLeakDetector.LeakReport leakReport = ThreadLeakDetector.inspectThread(workerThread);

        if (leakReport.hasLeaks()) {
            logger.error(
                "Thread leak detectado no timeout guard | operacao={} | leak={}",
                descreverOperacao(nomeOperacao),
                leakReport.summary()
            );
        }

        return leakReport;
    }

    private static long resolverGraceEncerramento(final boolean timeoutJaAtingido) {
        final long configuradoMs = Math.max(0L, ConfigEtl.obterTimeoutThreadLeakGraceMs());
        if (!timeoutJaAtingido) {
            return configuradoMs;
        }
        return Math.min(configuradoMs, MAX_GRACE_APOS_TIMEOUT_MS);
    }

    private static Exception enriquecerComThreadLeakSeNecessario(final String nomeOperacao,
                                                                 final Exception pendingException,
                                                                 final ThreadLeakDetector.LeakReport leakReport) {
        if (leakReport == null || !leakReport.hasLeaks()) {
            return pendingException;
        }

        final String mensagemBase = "Thread leak detectado ao executar "
            + descreverOperacao(nomeOperacao)
            + ": "
            + leakReport.summary();

        if (!ConfigEtl.isFalharAoDetectarThreadLeak()) {
            return pendingException;
        }

        if (pendingException instanceof ThreadLeakDetectedException existente) {
            return existente;
        }

        if (pendingException == null) {
            return new ThreadLeakDetectedException(mensagemBase, null, leakReport);
        }

        return new ThreadLeakDetectedException(
            mensagemBase,
            pendingException,
            leakReport
        );
    }

    private static Duration sanitizarTimeout(final Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            return TIMEOUT_PADRAO;
        }
        return timeout;
    }

    private static String descreverOperacao(final String nomeOperacao) {
        if (nomeOperacao == null || nomeOperacao.isBlank()) {
            return "operacao";
        }
        return nomeOperacao.trim();
    }

    private static String normalizarNomeOperacao(final String nomeOperacao) {
        final String normalizado = descreverOperacao(nomeOperacao)
            .toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        return normalizado.isBlank() ? "operacao" : normalizado;
    }
}
