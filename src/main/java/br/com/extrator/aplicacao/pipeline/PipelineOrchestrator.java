/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/pipeline/PipelineOrchestrator.java
Classe  : PipelineOrchestrator (class)
Pacote  : br.com.extrator.aplicacao.pipeline
Modulo  : Pipeline - Aplicacao

Papel   : Orquestra execucao de pipeline (steps sequencial + paralelo com politicas de resiliencia).

Conecta com:
- RetryPolicy, FailurePolicy, CircuitBreaker, ErrorClassifier (politicas)
- ExtractionLoggerPort (logging estruturado)
- PipelineMetricsPort (metricas)
- PipelineStep (steps a orquestrar)

Fluxo geral:
1) executar(dataInicio, dataFim, steps) itera steps.
2) Detecta executar-em-paralelo (graphql + dataexport).
3) Para cada step: aplica circuit breaker, retry, failure policy.
4) Retorna PipelineReport (resultados, aborted, metricas).

Estrutura interna:
Politicas principais:
- Circuit breaker: controla acesso (CLOSED/OPEN/HALF_OPEN).
- Retry: reexecuta com exponential backoff.
- Failure policy: decide modo (ABORT, DEGRADE, CONTINUE_WITH_ALERT, RETRY).
- Error classifier: classifica erro para decisoes.
Metodos principais:
- executar(): loop de steps com politicas.
- executarCoreEmParalelo(): graphql+dataexport em paralelo.
- executarStepComPoliticas(): aplica circuit breaker + retry.
- tratarFalhaStep(): aplica failure policy.
Atributos-chave:
- retryPolicy, failurePolicy, circuitBreaker, errorClassifier.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.pipeline;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import br.com.extrator.suporte.observabilidade.ExecutionContext;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.aplicacao.pipeline.runtime.StepStatus;
import br.com.extrator.aplicacao.portas.PipelineMetricsPort;
import br.com.extrator.aplicacao.politicas.CircuitBreaker;
import br.com.extrator.aplicacao.politicas.ErrorClassifier;
import br.com.extrator.aplicacao.politicas.ErrorTaxonomy;
import br.com.extrator.aplicacao.politicas.FailureMode;
import br.com.extrator.aplicacao.politicas.FailurePolicy;
import br.com.extrator.aplicacao.politicas.RetryPolicy;
import br.com.extrator.aplicacao.portas.ExtractionLoggerPort;

public final class PipelineOrchestrator {
    private static final Set<String> ENTIDADES_CORE_PARALELAS = Set.of("graphql", "dataexport");

    private final RetryPolicy retryPolicy;
    private final FailurePolicy failurePolicy;
    private final CircuitBreaker circuitBreaker;
    private final ErrorClassifier errorClassifier;
    private final ExtractionLoggerPort logger;
    private final PipelineMetricsPort metrics;

    public PipelineOrchestrator(
        final RetryPolicy retryPolicy,
        final FailurePolicy failurePolicy,
        final CircuitBreaker circuitBreaker,
        final ErrorClassifier errorClassifier,
        final ExtractionLoggerPort logger,
        final PipelineMetricsPort metrics
    ) {
        this.retryPolicy = retryPolicy;
        this.failurePolicy = failurePolicy;
        this.circuitBreaker = circuitBreaker;
        this.errorClassifier = errorClassifier;
        this.logger = logger;
        this.metrics = metrics;
    }

    public PipelineReport executar(final LocalDate dataInicio, final LocalDate dataFim, final List<PipelineStep> steps) {
        final PipelineReport.Builder report = PipelineReport.builder(dataInicio, dataFim);
        boolean aborted = false;
        String abortedBy = null;

        for (int index = 0; index < steps.size();) {
            if (deveExecutarCoreEmParalelo(steps, index)) {
                final PipelineStep stepA = steps.get(index);
                final PipelineStep stepB = steps.get(index + 1);
                final List<StepRunOutcome> outcomes = executarCoreEmParalelo(stepA, stepB, dataInicio, dataFim);
                for (final StepRunOutcome outcome : outcomes) {
                    report.addResult(outcome.result());
                    if (outcome.abortPipeline()) {
                        aborted = true;
                        abortedBy = outcome.result().obterNomeEtapa();
                        break;
                    }
                }
                if (aborted) {
                    break;
                }
                index += 2;
                continue;
            }

            final StepRunOutcome outcome = executarStepComPoliticas(steps.get(index), dataInicio, dataFim);
            report.addResult(outcome.result());
            if (outcome.abortPipeline()) {
                aborted = true;
                abortedBy = outcome.result().obterNomeEtapa();
                break;
            }
            index++;
        }

        report.aborted(aborted).abortedBy(abortedBy).generatedAt(LocalDateTime.now());
        metrics.obterSnapshot().forEach(report::metric);
        return report.build();
    }

    private boolean deveExecutarCoreEmParalelo(final List<PipelineStep> steps, final int index) {
        if (index < 0 || index + 1 >= steps.size()) {
            return false;
        }
        final String entidadeAtual = normalizarEntidade(steps.get(index).obterNomeEntidade());
        final String entidadeProxima = normalizarEntidade(steps.get(index + 1).obterNomeEntidade());
        return ENTIDADES_CORE_PARALELAS.contains(entidadeAtual)
            && ENTIDADES_CORE_PARALELAS.contains(entidadeProxima)
            && !entidadeAtual.equals(entidadeProxima);
    }

    private List<StepRunOutcome> executarCoreEmParalelo(final PipelineStep stepA,
                                                        final PipelineStep stepB,
                                                        final LocalDate dataInicio,
                                                        final LocalDate dataFim) {
        logger.logarEstruturado("pipeline.parallel.start", Map.of(
            "step_a", valorOuUnknown(stepA.obterNomeEtapa()),
            "entidade_a", valorOuUnknown(stepA.obterNomeEntidade()),
            "step_b", valorOuUnknown(stepB.obterNomeEtapa()),
            "entidade_b", valorOuUnknown(stepB.obterNomeEntidade())
        ));

        final ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<StepRunOutcome> futureA = executor.submit(ExecutionContext.wrapCallable(() -> executarStepComPoliticas(stepA, dataInicio, dataFim)));
            final Future<StepRunOutcome> futureB = executor.submit(ExecutionContext.wrapCallable(() -> executarStepComPoliticas(stepB, dataInicio, dataFim)));

            final StepRunOutcome outcomeA = aguardarOutcomeParalelo(stepA, futureA);
            final StepRunOutcome outcomeB = aguardarOutcomeParalelo(stepB, futureB);

        logger.logarEstruturado("pipeline.parallel.end", Map.of(
            "step_a", valorOuUnknown(stepA.obterNomeEtapa()),
            "status_a", outcomeA.result().getStatus().name(),
            "step_b", valorOuUnknown(stepB.obterNomeEtapa()),
            "status_b", outcomeB.result().getStatus().name()
        ));
            return List.of(outcomeA, outcomeB);
        } finally {
            encerrarExecutor(executor);
        }
    }

    private void encerrarExecutor(final ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.logarEstruturado("pipeline.parallel.shutdown.warning", Map.of(
                        "reason", "executor_linger"
                    ));
                }
            }
        } catch (final InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private StepRunOutcome aguardarOutcomeParalelo(final PipelineStep step,
                                                   final Future<StepRunOutcome> future) {
        try {
            return future.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return criarFalhaInternaParalela(step, e);
        } catch (final ExecutionException e) {
            final Throwable causa = e.getCause() == null ? e : e.getCause();
            return criarFalhaInternaParalela(step, causa);
        }
    }

    private StepRunOutcome criarFalhaInternaParalela(final PipelineStep step, final Throwable erroBruto) {
        final Exception erro = erroBruto instanceof Exception ex
            ? ex
            : new RuntimeException("Falha interna na execucao paralela do step", erroBruto);
        final String stepName = valorOuUnknown(step.obterNomeEtapa());
        final String entidade = normalizarEntidade(step.obterNomeEntidade());
        final LocalDateTime startedAt = LocalDateTime.now();

        circuitBreaker.registrarFalha(entidade);
        final ErrorTaxonomy taxonomy = errorClassifier.classificar(erro);
        final StepExecutionResult failure = StepExecutionResult.builder(stepName, entidade)
            .status(StepStatus.FAILED)
            .startedAt(startedAt)
            .finishedAt(LocalDateTime.now())
            .message(mensagemNaoNula(erro))
            .errorTaxonomy(taxonomy)
            .metadata("failure_mode", FailureMode.ABORT_PIPELINE.name())
            .build();

        metrics.incrementarFalha(entidade);
        metrics.registrarDuracaoEntidade(entidade, failure.durationMillis());
        logger.logarEstruturado("pipeline.step.failure", Map.of(
            "step", stepName,
            "entidade", entidade,
            "taxonomy", taxonomy.name(),
            "failure_mode", FailureMode.ABORT_PIPELINE.name(),
            "mensagem", failure.getMessage()
        ));
        return new StepRunOutcome(failure, true);
    }

    private StepRunOutcome executarStepComPoliticas(final PipelineStep step,
                                                    final LocalDate dataInicio,
                                                    final LocalDate dataFim) {
        final String stepName = valorOuUnknown(step.obterNomeEtapa());
        final String entidade = normalizarEntidade(step.obterNomeEntidade());
        final LocalDateTime startedAt = LocalDateTime.now();

        if (!circuitBreaker.permite(entidade)) {
            final StepExecutionResult skipped = StepExecutionResult.builder(stepName, entidade)
                .status(StepStatus.SKIPPED)
                .startedAt(startedAt)
                .finishedAt(LocalDateTime.now())
                .message("Step ignorado: circuit breaker aberto")
                .metadata("circuit_state", circuitBreaker.estadoDe(entidade).name())
                .build();
            logger.logarEstruturado("pipeline.step.skipped", Map.of(
                "step", stepName,
                "entidade", entidade,
                "reason", "circuit_open"
            ));
            return new StepRunOutcome(skipped, false);
        }

        try {
            final StepExecutionResult result = retryPolicy.executar(
                () -> step.executar(dataInicio, dataFim),
                stepName
            );
            final StepExecutionResult safe = result == null
                ? StepExecutionResult.builder(stepName, entidade)
                    .status(StepStatus.SUCCESS)
                    .startedAt(startedAt)
                    .finishedAt(LocalDateTime.now())
                    .message("Sem retorno explicito do step")
                    .build()
                : result;

            circuitBreaker.registrarSucesso(entidade);
            metrics.incrementarSucesso(entidade);
            metrics.registrarDuracaoEntidade(entidade, safe.durationMillis());

            logger.logarEstruturado("pipeline.step.success", Map.of(
                "step", stepName,
                "entidade", entidade,
                "status", safe.getStatus().name(),
                "duration_ms", safe.durationMillis()
            ));
            return new StepRunOutcome(safe, false);
        } catch (final Exception e) {
            return tratarFalhaStep(step, dataInicio, dataFim, startedAt, e);
        }
    }

    private StepRunOutcome tratarFalhaStep(final PipelineStep step,
                                           final LocalDate dataInicio,
                                           final LocalDate dataFim,
                                           final LocalDateTime startedAt,
                                           final Exception erro) {
        final String stepName = valorOuUnknown(step.obterNomeEtapa());
        final String entidade = normalizarEntidade(step.obterNomeEntidade());

        circuitBreaker.registrarFalha(entidade);
        final ErrorTaxonomy taxonomy = errorClassifier.classificar(erro);
        final FailureMode modo = failurePolicy.resolver(entidade, taxonomy);

        StepExecutionResult failure = StepExecutionResult.builder(stepName, entidade)
            .status(StepStatus.FAILED)
            .startedAt(startedAt)
            .finishedAt(LocalDateTime.now())
            .message(mensagemNaoNula(erro))
            .errorTaxonomy(taxonomy)
            .metadata("failure_mode", modo.name())
            .build();

        if (modo == FailureMode.RETRY) {
            try {
                final StepExecutionResult retried = step.executar(dataInicio, dataFim);
                final StepExecutionResult retryResult = retried == null
                    ? StepExecutionResult.builder(stepName, entidade)
                        .status(StepStatus.SUCCESS)
                        .startedAt(startedAt)
                        .finishedAt(LocalDateTime.now())
                        .attempt(2)
                        .message("Sucesso no retry da failure policy")
                        .build()
                    : StepExecutionResult.builder(retried.obterNomeEtapa(), retried.obterNomeEntidade())
                        .status(retried.getStatus())
                        .startedAt(retried.getStartedAt())
                        .finishedAt(retried.getFinishedAt())
                        .attempt(Math.max(2, retried.getAttempt()))
                        .message(retried.getMessage())
                        .errorTaxonomy(retried.getErrorTaxonomy())
                        .build();

                circuitBreaker.registrarSucesso(entidade);
                metrics.incrementarSucesso(entidade);
                metrics.registrarDuracaoEntidade(entidade, retryResult.durationMillis());
                logger.logarEstruturado("pipeline.step.retry_success", Map.of(
                    "step", stepName,
                    "entidade", entidade,
                    "attempt", retryResult.getAttempt()
                ));
                return new StepRunOutcome(retryResult, false);
            } catch (final Exception retryError) {
                failure = StepExecutionResult.builder(stepName, entidade)
                    .status(StepStatus.FAILED)
                    .startedAt(startedAt)
                    .finishedAt(LocalDateTime.now())
                    .attempt(2)
                    .message(mensagemNaoNula(retryError))
                    .errorTaxonomy(errorClassifier.classificar(retryError))
                    .metadata("failure_mode", modo.name())
                    .build();
            }
        } else if (modo == FailureMode.DEGRADE) {
            failure = StepExecutionResult.builder(stepName, entidade)
                .status(StepStatus.DEGRADED)
                .startedAt(startedAt)
                .finishedAt(LocalDateTime.now())
                .message(mensagemNaoNula(erro))
                .errorTaxonomy(taxonomy)
                .metadata("failure_mode", modo.name())
                .build();
        }

        metrics.incrementarFalha(entidade);
        metrics.registrarDuracaoEntidade(entidade, failure.durationMillis());
        logger.logarEstruturado("pipeline.step.failure", Map.of(
            "step", stepName,
            "entidade", entidade,
            "taxonomy", (failure.getErrorTaxonomy() == null ? taxonomy : failure.getErrorTaxonomy()).name(),
            "failure_mode", modo.name(),
            "mensagem", valorOuUnknown(failure.getMessage())
        ));

        return new StepRunOutcome(failure, modo == FailureMode.ABORT_PIPELINE);
    }

    private String normalizarEntidade(final String entidade) {
        return entidade == null ? "" : entidade.trim().toLowerCase();
    }

    private String valorOuUnknown(final String valor) {
        return (valor == null || valor.isBlank()) ? "unknown" : valor;
    }

    private String mensagemNaoNula(final Throwable erro) {
        if (erro == null) {
            return "erro-desconhecido";
        }
        return erro.getMessage() == null ? erro.getClass().getSimpleName() : erro.getMessage();
    }

    private record StepRunOutcome(StepExecutionResult result, boolean abortPipeline) {
    }
}

