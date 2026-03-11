package br.com.extrator.aplicacao.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.aplicacao.pipeline.runtime.StepStatus;
import br.com.extrator.aplicacao.portas.ClockPort;
import br.com.extrator.aplicacao.portas.ExtractionLoggerPort;
import br.com.extrator.observabilidade.pipeline.InMemoryPipelineMetrics;
import br.com.extrator.aplicacao.politicas.CircuitBreaker;
import br.com.extrator.aplicacao.politicas.ErrorClassifier;
import br.com.extrator.aplicacao.politicas.FailureMode;
import br.com.extrator.aplicacao.politicas.RetryPolicy;

class PipelineOrchestratorTest {

    @Test
    void deveAbortarQuandoFailurePolicyDeterminaAbort() {
        final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 1, 1, 0, 0));
        final AtomicBoolean stepFinalExecutado = new AtomicBoolean(false);
        final PipelineOrchestrator orchestrator = criarOrchestrator(
            clock,
            (entidade, taxonomy) -> "coletas".equals(entidade) ? FailureMode.ABORT_PIPELINE : FailureMode.CONTINUE_WITH_ALERT
        );

        final List<PipelineStep> steps = List.of(
            sucesso("step-ok", "graphql"),
            falha("step-falha", "coletas"),
            new PipelineStep() {
                @Override
                public StepExecutionResult executar(final LocalDate dataInicio, final LocalDate dataFim) {
                    stepFinalExecutado.set(true);
                    return StepExecutionResult.builder("step-final", "dataexport")
                        .status(StepStatus.SUCCESS)
                        .startedAt(clock.agora())
                        .finishedAt(clock.agora().plusSeconds(1))
                        .build();
                }

                @Override
                public String obterNomeEtapa() {
                    return "step-final";
                }

                @Override
                public String obterNomeEntidade() {
                    return "dataexport";
                }
            }
        );

        final PipelineReport report = orchestrator.executar(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), steps);
        assertTrue(report.isAborted());
        assertEquals("step-falha", report.getAbortedBy());
        assertEquals(2, report.getResultados().size());
        assertFalse(stepFinalExecutado.get());
    }

    @Test
    void deveContinuarQuandoFailurePolicyPermite() {
        final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 1, 1, 0, 0));
        final PipelineOrchestrator orchestrator = criarOrchestrator(
            clock,
            (entidade, taxonomy) -> FailureMode.CONTINUE_WITH_ALERT
        );

        final List<PipelineStep> steps = List.of(
            falha("step-falha", "graphql"),
            sucesso("step-ok", "dataexport")
        );

        final PipelineReport report = orchestrator.executar(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), steps);
        assertFalse(report.isAborted());
        assertEquals(2, report.getResultados().size());
        assertEquals(1, report.totalFalhasExecucao());
        assertEquals(1, report.totalSucessos());
    }

    @Test
    void deveExecutarGraphQLEDataExportEmParaleloQuandoAdjacentes() {
        final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 1, 1, 0, 0));
        final PipelineOrchestrator orchestrator = criarOrchestrator(
            clock,
            (entidade, taxonomy) -> FailureMode.CONTINUE_WITH_ALERT
        );

        final CountDownLatch latchInicio = new CountDownLatch(2);
        final List<PipelineStep> steps = List.of(
            passoComSincronizacao("step-graphql", "graphql", latchInicio),
            passoComSincronizacao("step-dataexport", "dataexport", latchInicio)
        );

        final long inicioMs = System.currentTimeMillis();
        final PipelineReport report = orchestrator.executar(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), steps);
        final long duracaoMs = System.currentTimeMillis() - inicioMs;

        assertFalse(report.isAborted());
        assertEquals(2, report.getResultados().size());
        assertEquals(2, report.totalSucessos());
        assertTrue(
            duracaoMs < 700,
            "GraphQL e DataExport devem rodar em paralelo quando adjacentes (duracao observada=" + duracaoMs + "ms)"
        );
    }

    private PipelineOrchestrator criarOrchestrator(
        final MutableClock clock,
        final br.com.extrator.aplicacao.politicas.FailurePolicy failurePolicy
    ) {
        final RetryPolicy retryPolicy = new RetryPolicy() {
            @Override
            public <T> T executar(final CheckedSupplier<T> supplier, final String operationName) throws Exception {
                return supplier.get();
            }
        };
        final ExtractionLoggerPort logger = (eventName, fields) -> { };
        return new PipelineOrchestrator(
            retryPolicy,
            failurePolicy,
            new CircuitBreaker(5, Duration.ofSeconds(60), clock),
            new ErrorClassifier(),
            logger,
            new InMemoryPipelineMetrics()
        );
    }

    private PipelineStep sucesso(final String stepName, final String entidade) {
        return new PipelineStep() {
            @Override
            public StepExecutionResult executar(final LocalDate dataInicio, final LocalDate dataFim) {
                final LocalDateTime now = LocalDateTime.now();
                return StepExecutionResult.builder(stepName, entidade)
                    .status(StepStatus.SUCCESS)
                    .startedAt(now)
                    .finishedAt(now.plusSeconds(1))
                    .build();
            }

            @Override
            public String obterNomeEtapa() {
                return stepName;
            }

            @Override
            public String obterNomeEntidade() {
                return entidade;
            }
        };
    }

    private PipelineStep falha(final String stepName, final String entidade) {
        return new PipelineStep() {
            @Override
            public StepExecutionResult executar(final LocalDate dataInicio, final LocalDate dataFim) {
                throw new IllegalStateException("falha forcada");
            }

            @Override
            public String obterNomeEtapa() {
                return stepName;
            }

            @Override
            public String obterNomeEntidade() {
                return entidade;
            }
        };
    }

    private PipelineStep passoComSincronizacao(final String stepName,
                                               final String entidade,
                                               final CountDownLatch latchInicio) {
        return new PipelineStep() {
            @Override
            public StepExecutionResult executar(final LocalDate dataInicio, final LocalDate dataFim) {
                try {
                    latchInicio.countDown();
                    final boolean iniciouParalelo = latchInicio.await(2, TimeUnit.SECONDS);
                    if (!iniciouParalelo) {
                        throw new IllegalStateException("steps nao iniciaram em paralelo");
                    }
                    Thread.sleep(400);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrompido no teste de paralelismo", e);
                }

                final LocalDateTime now = LocalDateTime.now();
                return StepExecutionResult.builder(stepName, entidade)
                    .status(StepStatus.SUCCESS)
                    .startedAt(now)
                    .finishedAt(now.plusNanos(400_000_000L))
                    .build();
            }

            @Override
            public String obterNomeEtapa() {
                return stepName;
            }

            @Override
            public String obterNomeEntidade() {
                return entidade;
            }
        };
    }

    private static final class MutableClock implements ClockPort {
        private LocalDateTime current;

        private MutableClock(final LocalDateTime current) {
            this.current = current;
        }

        @Override
        public LocalDate hoje() {
            return current.toLocalDate();
        }

        @Override
        public LocalDateTime agora() {
            return current;
        }

        @Override
        public void dormir(final Duration duration) {
            current = current.plus(duration);
        }
    }
}


