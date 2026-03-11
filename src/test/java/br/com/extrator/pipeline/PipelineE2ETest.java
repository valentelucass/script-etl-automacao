package br.com.extrator.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import br.com.extrator.aplicacao.portas.ClockPort;
import br.com.extrator.aplicacao.portas.ExtractionLoggerPort;
import br.com.extrator.aplicacao.pipeline.PipelineOrchestrator;
import br.com.extrator.aplicacao.pipeline.PipelineReport;
import br.com.extrator.aplicacao.pipeline.PipelineStep;
import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.aplicacao.pipeline.runtime.StepStatus;
import br.com.extrator.observabilidade.pipeline.InMemoryPipelineMetrics;
import br.com.extrator.aplicacao.politicas.CircuitBreaker;
import br.com.extrator.aplicacao.politicas.ErrorClassifier;
import br.com.extrator.aplicacao.politicas.FailureMode;
import br.com.extrator.aplicacao.politicas.RetryPolicy;

class PipelineE2ETest {

    @Test
    void deveExecutarFluxoCompletoSinteticoSemAbortar() {
        final AtomicInteger totalRegistros = new AtomicInteger();
        final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        final ClockPort clock = new FixedClock(now);

        final PipelineOrchestrator orchestrator = new PipelineOrchestrator(
            retryNoop(),
            (entidade, taxonomy) -> FailureMode.CONTINUE_WITH_ALERT,
            new CircuitBreaker(3, Duration.ofSeconds(60), clock),
            new ErrorClassifier(),
            noOpLogger(),
            new InMemoryPipelineMetrics()
        );

        final List<PipelineStep> steps = List.of(
            stepSucesso("graphql:coletas", "coletas", 5, totalRegistros, now),
            stepSucesso("dataexport:manifestos", "manifestos", 7, totalRegistros, now),
            stepSucesso("quality:checks", "quality", 0, totalRegistros, now)
        );

        final PipelineReport report = orchestrator.executar(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), steps);
        assertFalse(report.isAborted());
        assertTrue(report.totalFalhasExecucao() == 0);
        assertTrue(report.totalSucessos() == 3);
        assertTrue(totalRegistros.get() == 12);
    }

    private RetryPolicy retryNoop() {
        return new RetryPolicy() {
            @Override
            public <T> T executar(final CheckedSupplier<T> supplier, final String operationName) throws Exception {
                return supplier.get();
            }
        };
    }

    private ExtractionLoggerPort noOpLogger() {
        return (eventName, fields) -> { };
    }

    private PipelineStep stepSucesso(
        final String stepName,
        final String entidade,
        final int registros,
        final AtomicInteger acumulador,
        final LocalDateTime now
    ) {
        return new PipelineStep() {
            @Override
            public StepExecutionResult executar(final LocalDate dataInicio, final LocalDate dataFim) {
                acumulador.addAndGet(registros);
                return StepExecutionResult.builder(stepName, entidade)
                    .status(StepStatus.SUCCESS)
                    .startedAt(now)
                    .finishedAt(now.plusSeconds(1))
                    .metadata("records", registros)
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

    private static final class FixedClock implements ClockPort {
        private final LocalDateTime now;

        private FixedClock(final LocalDateTime now) {
            this.now = now;
        }

        @Override
        public LocalDate hoje() {
            return now.toLocalDate();
        }

        @Override
        public LocalDateTime agora() {
            return now;
        }

        @Override
        public void dormir(final Duration duration) {
        }
    }
}


