package br.com.extrator.aplicacao.extracao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.extrator.aplicacao.contexto.AplicacaoContexto;
import br.com.extrator.aplicacao.pipeline.PipelineOrchestrator;
import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.aplicacao.pipeline.runtime.StepStatus;
import br.com.extrator.aplicacao.politicas.CircuitBreaker;
import br.com.extrator.aplicacao.politicas.ErrorClassifier;
import br.com.extrator.aplicacao.politicas.FailureMode;
import br.com.extrator.aplicacao.politicas.FailurePolicy;
import br.com.extrator.aplicacao.politicas.RetryPolicy;
import br.com.extrator.aplicacao.portas.ClockPort;
import br.com.extrator.aplicacao.portas.DataExportGateway;
import br.com.extrator.aplicacao.portas.ExtractionLogQueryPort;
import br.com.extrator.aplicacao.portas.GraphQLGateway;
import br.com.extrator.aplicacao.portas.IntegridadeEtlPort;
import br.com.extrator.aplicacao.portas.PipelineOrchestratorFactory;
import br.com.extrator.observabilidade.pipeline.InMemoryPipelineMetrics;

class ExtracaoPorIntervaloUseCaseTest {

    private static final String PROP_TIMEOUT_COLETAS = "ETL_GRAPHQL_TIMEOUT_ENTIDADE_COLETAS_MS";
    private static final String PROP_BACKFILL_MAX_EXPANSAO = "ETL_REFERENCIAL_COLETAS_BACKFILL_MAX_EXPANSAO_DIAS";
    private static final String PROP_TIMEOUT_COLETAS_INTERVALO = "etl.graphql.timeout.entidade.coletas.intervalo.ms";
    private static final String PROP_MAX_EXPANSAO_INTERVALO = "etl.referencial.coletas.backfill.max_expansao_dias.intervalo";
    private static final String PROP_MAX_FALHAS_INTERVALO = "etl.intervalo.coletas.max_consecutive_failures";
    private static final List<String> CAMPOS_CONTEXTO = List.of(
        "orchestratorFactory",
        "graphQLGateway",
        "dataExportGateway",
        "extractionLogQueryPort",
        "integridadeEtlPort"
    );

    private final Map<String, Object> contextoAnterior = new HashMap<>();

    @BeforeEach
    void prepararContexto() throws Exception {
        for (final String campo : CAMPOS_CONTEXTO) {
            contextoAnterior.put(campo, lerCampoContexto(campo));
        }
    }

    @AfterEach
    void restaurarContextoEPropriedades() throws Exception {
        for (final String campo : CAMPOS_CONTEXTO) {
            escreverCampoContexto(campo, contextoAnterior.get(campo));
        }
        System.clearProperty(PROP_TIMEOUT_COLETAS);
        System.clearProperty(PROP_BACKFILL_MAX_EXPANSAO);
        System.clearProperty(PROP_TIMEOUT_COLETAS_INTERVALO);
        System.clearProperty(PROP_MAX_EXPANSAO_INTERVALO);
        System.clearProperty(PROP_MAX_FALHAS_INTERVALO);
    }

    @Test
    void deveAplicarERestaurarOverridesTemporariosDeColetasNoIntervalo() {
        final String timeoutAnterior = "120000";
        final String expansaoAnterior = "7";
        System.setProperty(PROP_TIMEOUT_COLETAS, timeoutAnterior);
        System.setProperty(PROP_BACKFILL_MAX_EXPANSAO, expansaoAnterior);

        final Queue<StepExecutionResult> resultados = filaDeResultados(
            resultadoGraphql("coletas", StepStatus.SUCCESS, "ok")
        );
        final Queue<Optional<LogExtracaoInfo>> logs = filaDeLogs(logCompleto(321));
        final Queue<IntegridadeEtlPort.ResultadoIntegridade> integridade = filaDeIntegridade(integridadeValida());
        final Queue<String> timeoutsObservados = new ArrayDeque<>();
        final Queue<String> expansoesObservadas = new ArrayDeque<>();

        final GraphQLGateway gateway = (dataInicio, dataFim, entidade) -> {
            timeoutsObservados.add(System.getProperty(PROP_TIMEOUT_COLETAS));
            expansoesObservadas.add(System.getProperty(PROP_BACKFILL_MAX_EXPANSAO));
            return resultados.remove();
        };

        final ExtracaoPorIntervaloUseCase useCase = criarUseCase(
            gateway,
            new SequencialExtractionLogQueryPort(logs),
            new SequencialIntegridadePort(integridade)
        );

        assertDoesNotThrow(() -> useCase.executar(requestComBlocos(1)));
        assertEquals(List.of("1800000"), List.copyOf(timeoutsObservados));
        assertEquals(List.of("400"), List.copyOf(expansoesObservadas));
        assertEquals(timeoutAnterior, System.getProperty(PROP_TIMEOUT_COLETAS));
        assertEquals(expansaoAnterior, System.getProperty(PROP_BACKFILL_MAX_EXPANSAO));
    }

    @Test
    void deveAbortarAposDuasFalhasDiretasConsecutivasDeColetas() {
        final Queue<StepExecutionResult> resultados = filaDeResultados(
            resultadoGraphql("coletas", StepStatus.FAILED, "falha bloco 1"),
            resultadoGraphql("coletas", StepStatus.FAILED, "falha bloco 2")
        );
        final Queue<Optional<LogExtracaoInfo>> logs = filaDeLogs(Optional.empty(), Optional.empty());
        final Queue<IntegridadeEtlPort.ResultadoIntegridade> integridade = filaDeIntegridade(
            integridadeValida(),
            integridadeValida()
        );
        final AtomicInteger chamadas = new AtomicInteger(0);

        final GraphQLGateway gateway = (dataInicio, dataFim, entidade) -> {
            chamadas.incrementAndGet();
            return resultados.remove();
        };

        final ExtracaoPorIntervaloUseCase useCase = criarUseCase(
            gateway,
            new SequencialExtractionLogQueryPort(logs),
            new SequencialIntegridadePort(integridade)
        );

        final PartialExecutionException erro = assertThrows(
            PartialExecutionException.class,
            () -> useCase.executar(requestComBlocos(3))
        );

        assertEquals(2, chamadas.get());
        assertTrue(erro.getMessage().contains("falhas criticas consecutivas de coletas"));
        assertTrue(erro.getMessage().contains("2/3"));
    }

    @Test
    void deveResetarContadorQuandoBlocoIntermediarioDeColetasForSaudavel() {
        final Queue<StepExecutionResult> resultados = filaDeResultados(
            resultadoGraphql("coletas", StepStatus.FAILED, "falha bloco 1"),
            resultadoGraphql("coletas", StepStatus.SUCCESS, "ok bloco 2"),
            resultadoGraphql("coletas", StepStatus.FAILED, "falha bloco 3"),
            resultadoGraphql("coletas", StepStatus.FAILED, "falha bloco 4")
        );
        final Queue<Optional<LogExtracaoInfo>> logs = filaDeLogs(
            Optional.empty(),
            logCompleto(100),
            Optional.empty(),
            Optional.empty()
        );
        final Queue<IntegridadeEtlPort.ResultadoIntegridade> integridade = filaDeIntegridade(
            integridadeValida(),
            integridadeValida(),
            integridadeValida(),
            integridadeValida()
        );
        final AtomicInteger chamadas = new AtomicInteger(0);

        final GraphQLGateway gateway = (dataInicio, dataFim, entidade) -> {
            chamadas.incrementAndGet();
            return resultados.remove();
        };

        final ExtracaoPorIntervaloUseCase useCase = criarUseCase(
            gateway,
            new SequencialExtractionLogQueryPort(logs),
            new SequencialIntegridadePort(integridade)
        );

        final PartialExecutionException erro = assertThrows(
            PartialExecutionException.class,
            () -> useCase.executar(requestComBlocos(5))
        );

        assertEquals(4, chamadas.get(), "Bloco saudavel intermediario deve resetar o contador antes do abort.");
        assertTrue(erro.getMessage().contains("4/5"));
    }

    @Test
    void deveAbortarAposDuasFalhasAuditAusenteDeColetas() {
        final Queue<StepExecutionResult> resultados = filaDeResultados(
            resultadoGraphql("coletas", StepStatus.SUCCESS, "ok bloco 1"),
            resultadoGraphql("coletas", StepStatus.SUCCESS, "ok bloco 2")
        );
        final Queue<Optional<LogExtracaoInfo>> logs = filaDeLogs(
            logCompleto(210),
            logCompleto(215)
        );
        final Queue<IntegridadeEtlPort.ResultadoIntegridade> integridade = filaDeIntegridade(
            integridadeInvalida("AUDIT_AUSENTE | Sem sys_execution_audit para entidade 'coletas' na execucao teste-1."),
            integridadeInvalida("AUDIT_AUSENTE | Sem sys_execution_audit para entidade 'coletas' na execucao teste-2.")
        );
        final AtomicInteger chamadas = new AtomicInteger(0);

        final GraphQLGateway gateway = (dataInicio, dataFim, entidade) -> {
            chamadas.incrementAndGet();
            return resultados.remove();
        };

        final ExtracaoPorIntervaloUseCase useCase = criarUseCase(
            gateway,
            new SequencialExtractionLogQueryPort(logs),
            new SequencialIntegridadePort(integridade)
        );

        final PartialExecutionException erro = assertThrows(
            PartialExecutionException.class,
            () -> useCase.executar(requestComBlocos(3))
        );

        assertEquals(2, chamadas.get());
        assertTrue(erro.getMessage().contains("falhas criticas consecutivas de coletas"));
    }

    @Test
    void deveAbortarAposDuasFalhasReferenciaisAtribuidasAColetas() {
        final Queue<StepExecutionResult> resultados = filaDeResultados(
            resultadoGraphql("coletas", StepStatus.SUCCESS, "ok bloco 1"),
            resultadoGraphql("coletas", StepStatus.SUCCESS, "ok bloco 2")
        );
        final Queue<Optional<LogExtracaoInfo>> logs = filaDeLogs(
            logCompleto(180),
            logCompleto(181)
        );
        final Queue<IntegridadeEtlPort.ResultadoIntegridade> integridade = filaDeIntegridade(
            integridadeInvalida(
                "INTEGRIDADE_REFERENCIAL_MANIFESTOS | Manifestos orfaos | contexto_coletas={sem_auditoria}"
            ),
            integridadeInvalida(
                "INTEGRIDADE_REFERENCIAL_MANIFESTOS | Manifestos orfaos | contexto_coletas={sem_auditoria}"
            )
        );
        final AtomicInteger chamadas = new AtomicInteger(0);

        final GraphQLGateway gateway = (dataInicio, dataFim, entidade) -> {
            chamadas.incrementAndGet();
            return resultados.remove();
        };

        final ExtracaoPorIntervaloUseCase useCase = criarUseCase(
            gateway,
            new SequencialExtractionLogQueryPort(logs),
            new SequencialIntegridadePort(integridade)
        );

        final PartialExecutionException erro = assertThrows(
            PartialExecutionException.class,
            () -> useCase.executar(requestComBlocos(4))
        );

        assertEquals(2, chamadas.get());
        assertTrue(erro.getMessage().contains("falhas criticas consecutivas de coletas"));
    }

    @Test
    void deveClassificarResumoErroApiComTimeoutComoTimeout() {
        final String reason = ExtracaoPorIntervaloUseCase.resolverReasonCodeResumo(
            "ERRO_API",
            0,
            "Erro: Thread interrompida durante requisicao"
        );

        assertEquals("TIMEOUT", reason);
    }

    @Test
    void deveInformarFaturasGraphqlComoNaoAplicavelParaRasterEDataExport() {
        assertEquals("NAO SE APLICA", ExtracaoPorIntervaloUseCase.descreverFaturasGraphQL("raster", true));
        assertEquals("NAO SE APLICA", ExtracaoPorIntervaloUseCase.descreverFaturasGraphQL("dataexport", true));
        assertEquals("INCLUIDO", ExtracaoPorIntervaloUseCase.descreverFaturasGraphQL("graphql", true));
        assertEquals(
            "DESABILITADO (flag --sem-faturas-graphql)",
            ExtracaoPorIntervaloUseCase.descreverFaturasGraphQL(null, false)
        );
    }

    private ExtracaoPorIntervaloUseCase criarUseCase(
        final GraphQLGateway graphQLGateway,
        final ExtractionLogQueryPort extractionLogQueryPort,
        final IntegridadeEtlPort integridadeEtlPort
    ) {
        AplicacaoContexto.registrar((PipelineOrchestratorFactory) this::criarOrchestrator);
        AplicacaoContexto.registrar(graphQLGateway);
        AplicacaoContexto.registrar((DataExportGateway) (dataInicio, dataFim, entidade) ->
            StepExecutionResult.builder("dataexport:" + entidade, entidade)
                .status(StepStatus.SUCCESS)
                .startedAt(LocalDateTime.now())
                .finishedAt(LocalDateTime.now())
                .build()
        );
        AplicacaoContexto.registrar(extractionLogQueryPort);
        AplicacaoContexto.registrar(integridadeEtlPort);

        return new ExtracaoPorIntervaloUseCase(
            new NoOpPreBackfillReferencialColetasUseCase(),
            new PlanejadorEscopoExtracaoIntervalo(),
            resourceName -> () -> { }
        );
    }

    private PipelineOrchestrator criarOrchestrator() {
        final RetryPolicy retryPolicy = new RetryPolicy() {
            @Override
            public <T> T executar(final RetryPolicy.CheckedSupplier<T> supplier, final String operationName) throws Exception {
                return supplier.get();
            }
        };
        final FailurePolicy failurePolicy = (entidade, taxonomy) -> FailureMode.CONTINUE_WITH_ALERT;
        final ClockPort clock = new ClockPort() {
            @Override
            public LocalDate hoje() {
                return LocalDate.now();
            }

            @Override
            public LocalDateTime agora() {
                return LocalDateTime.now();
            }

            @Override
            public void dormir(final Duration duration) throws InterruptedException {
                Thread.sleep(duration.toMillis());
            }
        };
        return new PipelineOrchestrator(
            retryPolicy,
            failurePolicy,
            new CircuitBreaker(5, Duration.ofSeconds(60), clock),
            new ErrorClassifier(),
            (eventName, fields) -> { },
            new InMemoryPipelineMetrics()
        );
    }

    private ExtracaoPorIntervaloRequest requestComBlocos(final int quantidadeBlocos) {
        final LocalDate inicio = LocalDate.of(2026, 1, 1);
        final LocalDate fim = inicio.plusDays((30L * quantidadeBlocos) - 1L);
        return new ExtracaoPorIntervaloRequest(inicio, fim, "graphql", "coletas", false, false);
    }

    private Queue<StepExecutionResult> filaDeResultados(final StepExecutionResult... resultados) {
        return new ArrayDeque<>(List.of(resultados));
    }

    @SafeVarargs
    private Queue<Optional<LogExtracaoInfo>> filaDeLogs(final Optional<LogExtracaoInfo>... logs) {
        return new ArrayDeque<>(List.of(logs));
    }

    @SafeVarargs
    private Queue<IntegridadeEtlPort.ResultadoIntegridade> filaDeIntegridade(
        final IntegridadeEtlPort.ResultadoIntegridade... resultados
    ) {
        return new ArrayDeque<>(List.of(resultados));
    }

    private Optional<LogExtracaoInfo> logCompleto(final int registros) {
        return Optional.of(new LogExtracaoInfo(
            LogExtracaoInfo.StatusExtracao.COMPLETO,
            LocalDateTime.now(),
            registros
        ));
    }

    private IntegridadeEtlPort.ResultadoIntegridade integridadeValida() {
        return new IntegridadeEtlPort.ResultadoIntegridade(true, 2, 0, List.of());
    }

    private IntegridadeEtlPort.ResultadoIntegridade integridadeInvalida(final String falha) {
        return new IntegridadeEtlPort.ResultadoIntegridade(false, 2, 1, List.of(falha));
    }

    private StepExecutionResult resultadoGraphql(final String entidade, final StepStatus status, final String mensagem) {
        final LocalDateTime inicio = LocalDateTime.now();
        return StepExecutionResult.builder("graphql:" + entidade, entidade)
            .status(status)
            .startedAt(inicio)
            .finishedAt(inicio.plusSeconds(1))
            .message(mensagem)
            .build();
    }

    private Object lerCampoContexto(final String nomeCampo) throws Exception {
        final Field campo = AplicacaoContexto.class.getDeclaredField(nomeCampo);
        campo.setAccessible(true);
        return campo.get(null);
    }

    private void escreverCampoContexto(final String nomeCampo, final Object valor) throws Exception {
        final Field campo = AplicacaoContexto.class.getDeclaredField(nomeCampo);
        campo.setAccessible(true);
        campo.set(null, valor);
    }

    private static final class NoOpPreBackfillReferencialColetasUseCase extends PreBackfillReferencialColetasUseCase {
        @Override
        public void executar(final LocalDate dataInicio, final LocalDate dataFim) {
        }

        @Override
        public void executarPosExtracao(final LocalDate dataInicio, final LocalDate dataFim) {
        }
    }

    private static final class SequencialExtractionLogQueryPort implements ExtractionLogQueryPort {
        private final Queue<Optional<LogExtracaoInfo>> logs;

        private SequencialExtractionLogQueryPort(final Queue<Optional<LogExtracaoInfo>> logs) {
            this.logs = logs;
        }

        @Override
        public Optional<LogExtracaoInfo> buscarUltimoLogPorEntidadeNoIntervaloExecucao(
            final String entidade,
            final LocalDateTime inicio,
            final LocalDateTime fim
        ) {
            return logs.isEmpty() ? Optional.empty() : logs.remove();
        }

        @Override
        public Optional<LogExtracaoInfo> buscarUltimaExtracaoPorPeriodo(
            final String entidade,
            final LocalDate dataInicio,
            final LocalDate dataFim
        ) {
            return Optional.empty();
        }
    }

    private static final class SequencialIntegridadePort implements IntegridadeEtlPort {
        private final Queue<ResultadoIntegridade> resultados;

        private SequencialIntegridadePort(final Queue<ResultadoIntegridade> resultados) {
            this.resultados = resultados;
        }

        @Override
        public ResultadoIntegridade validarExecucao(
            final LocalDateTime inicioExecucao,
            final LocalDateTime fimExecucao,
            final Set<String> entidadesEsperadas,
            final boolean modoLoopDaemon
        ) {
            return resultados.isEmpty() ? new ResultadoIntegridade(true, entidadesEsperadas.size(), 0, List.of()) : resultados.remove();
        }
    }
}
