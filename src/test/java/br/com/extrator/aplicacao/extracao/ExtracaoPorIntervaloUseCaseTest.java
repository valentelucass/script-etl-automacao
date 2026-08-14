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
    private static final String PROP_LOOKBACK_MODO_FRETES = "ETL_FRETES_PERFORMANCE_LOOKBACK_MODO";
    private static final String PROP_PRUNE_AUSENTES_FRETES = "ETL_FRETES_PRUNE_AUSENTES";
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
        System.clearProperty(PROP_LOOKBACK_MODO_FRETES);
        System.clearProperty(PROP_PRUNE_AUSENTES_FRETES);
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
            resultadoGraphql("usuarios_sistema", StepStatus.SUCCESS, "ok usuarios"),
            resultadoGraphql("coletas", StepStatus.SUCCESS, "ok")
        );
        final Queue<Optional<LogExtracaoInfo>> logs = filaDeLogs(logCompleto(12), logCompleto(321));
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
        assertEquals(List.of("1800000", "1800000"), List.copyOf(timeoutsObservados));
        assertEquals(List.of("400", "400"), List.copyOf(expansoesObservadas));
        assertEquals(timeoutAnterior, System.getProperty(PROP_TIMEOUT_COLETAS));
        assertEquals(expansaoAnterior, System.getProperty(PROP_BACKFILL_MAX_EXPANSAO));
    }

    @Test
    void retrofitGlobalDevePularHidratacaoReferencialForaDoPeriodoSolicitado() {
        final Queue<StepExecutionResult> resultados = filaDeResultados(
            resultadoGraphql("coletas", StepStatus.SUCCESS, "ok coletas"),
            resultadoGraphql("fretes", StepStatus.SUCCESS, "ok fretes")
        );
        final List<String> entidadesGraphqlExecutadas = new java.util.ArrayList<>();
        final TrackingPreBackfillReferencialColetasUseCase backfill = new TrackingPreBackfillReferencialColetasUseCase();
        final ExtracaoPorIntervaloUseCase useCase = criarUseCase(
            (dataInicio, dataFim, entidade) -> {
                entidadesGraphqlExecutadas.add(entidade);
                return resultados.remove();
            },
            new ExtractionLogQueryPort() {
                @Override
                public Optional<LogExtracaoInfo> buscarUltimoLogPorEntidadeNoIntervaloExecucao(
                    final String entidade,
                    final LocalDateTime inicio,
                    final LocalDateTime fim
                ) {
                    return logCompleto(1);
                }

                @Override
                public Optional<LogExtracaoInfo> buscarUltimaExtracaoPorPeriodo(
                    final String entidade,
                    final LocalDate dataInicio,
                    final LocalDate dataFim
                ) {
                    return Optional.empty();
                }
            },
            new SequencialIntegridadePort(filaDeIntegridade(integridadeValida())),
            backfill
        );
        final ExtracaoPorIntervaloRequest request = new ExtracaoPorIntervaloRequest(
            LocalDate.of(2026, 7, 29),
            LocalDate.of(2026, 8, 12),
            null,
            null,
            false,
            false,
            ExtracaoPorIntervaloRequest.ModoExecucao.RETROFIT
        );

        assertDoesNotThrow(() -> useCase.executar(request));
        assertEquals(0, backfill.preExecucoes.get());
        assertEquals(0, backfill.posExecucoes.get());
        assertEquals(List.of("coletas", "fretes"), entidadesGraphqlExecutadas);
    }

    @Test
    void modoLoopDaemonDeveSinalizarMicroBatchSemAtivarReconciliacaoDeFretes() {
        final Queue<StepExecutionResult> resultados = filaDeResultados(
            resultadoGraphql("usuarios_sistema", StepStatus.SUCCESS, "ok usuarios"),
            resultadoGraphql("fretes", StepStatus.SUCCESS, "ok")
        );
        final Queue<Optional<LogExtracaoInfo>> logs = filaDeLogs(logCompleto(12), logCompleto(42));
        final Queue<IntegridadeEtlPort.ResultadoIntegridade> integridade = filaDeIntegridade(integridadeValida());
        final Queue<String> modosObservados = new ArrayDeque<>();

        final GraphQLGateway gateway = (dataInicio, dataFim, entidade) -> {
            modosObservados.add(System.getProperty(PROP_LOOKBACK_MODO_FRETES));
            return resultados.remove();
        };

        final ExtracaoPorIntervaloUseCase useCase = criarUseCase(
            gateway,
            new SequencialExtractionLogQueryPort(logs),
            new SequencialIntegridadePort(integridade)
        );
        final ExtracaoPorIntervaloRequest request = new ExtracaoPorIntervaloRequest(
            LocalDate.of(2026, 4, 27),
            LocalDate.of(2026, 4, 28),
            "graphql",
            "fretes",
            false,
            true
        );

        assertDoesNotThrow(() -> useCase.executar(request));

        assertEquals(ExtracaoPorIntervaloRequest.ModoExecucao.MICRO_BATCH, request.modoExecucao());
        assertEquals(List.of("micro_batch", "micro_batch"), List.copyOf(modosObservados));
    }

    @Test
    void reconciliacaoDeveDeclararModoReconciliacaoExplicitamente() throws Exception {
        final CapturingExtracaoPorIntervaloUseCase extracao = new CapturingExtracaoPorIntervaloUseCase();
        final ReconciliacaoUseCase useCase = new ReconciliacaoUseCase(extracao);

        useCase.executar(LocalDate.of(2026, 4, 27), "graphql", "fretes").join();

        assertEquals(ExtracaoPorIntervaloRequest.ModoExecucao.RECONCILIACAO, extracao.requestCapturada.modoExecucao());
        assertTrue(extracao.requestCapturada.modoLoopDaemon());
    }

    @Test
    void microBatchDoDaemonDeveDesabilitarPruneDeAusentesFretes() {
        assertPruneConfiguradoParaModo(ExtracaoPorIntervaloRequest.ModoExecucao.MICRO_BATCH, "false");
    }

    @Test
    void reconciliacaoDedicadaDeveHabilitarPruneDeAusentesFretes() {
        assertPruneConfiguradoParaModo(ExtracaoPorIntervaloRequest.ModoExecucao.RECONCILIACAO, "true");
    }

    @Test
    void backfillDeveDesabilitarPruneDeAusentesFretes() {
        assertPruneConfiguradoParaModo(ExtracaoPorIntervaloRequest.ModoExecucao.BACKFILL, "false");
    }

    @Test
    void retrofitDeveDesabilitarPruneDeAusentesFretes() {
        assertPruneConfiguradoParaModo(ExtracaoPorIntervaloRequest.ModoExecucao.RETROFIT, "false");
    }

    @Test
    void deveAbortarAposDuasFalhasDiretasConsecutivasDeColetas() {
        final Queue<StepExecutionResult> resultados = filaDeResultados(
            resultadoGraphql("usuarios_sistema", StepStatus.SUCCESS, "ok usuarios bloco 1"),
            resultadoGraphql("coletas", StepStatus.FAILED, "falha bloco 1"),
            resultadoGraphql("usuarios_sistema", StepStatus.SUCCESS, "ok usuarios bloco 2"),
            resultadoGraphql("coletas", StepStatus.FAILED, "falha bloco 2")
        );
        final Queue<Optional<LogExtracaoInfo>> logs = filaDeLogs(
            logCompleto(12),
            Optional.empty(),
            logCompleto(13),
            Optional.empty()
        );
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

        assertEquals(4, chamadas.get());
        assertTrue(erro.getMessage().contains("falhas criticas consecutivas de coletas"));
        assertTrue(erro.getMessage().contains("2/3"));
    }

    @Test
    void deveResetarContadorQuandoBlocoIntermediarioDeColetasForSaudavel() {
        final Queue<StepExecutionResult> resultados = filaDeResultados(
            resultadoGraphql("usuarios_sistema", StepStatus.SUCCESS, "ok usuarios bloco 1"),
            resultadoGraphql("coletas", StepStatus.FAILED, "falha bloco 1"),
            resultadoGraphql("usuarios_sistema", StepStatus.SUCCESS, "ok usuarios bloco 2"),
            resultadoGraphql("coletas", StepStatus.SUCCESS, "ok bloco 2"),
            resultadoGraphql("usuarios_sistema", StepStatus.SUCCESS, "ok usuarios bloco 3"),
            resultadoGraphql("coletas", StepStatus.FAILED, "falha bloco 3"),
            resultadoGraphql("usuarios_sistema", StepStatus.SUCCESS, "ok usuarios bloco 4"),
            resultadoGraphql("coletas", StepStatus.FAILED, "falha bloco 4")
        );
        final Queue<Optional<LogExtracaoInfo>> logs = filaDeLogs(
            logCompleto(12),
            Optional.empty(),
            logCompleto(13),
            logCompleto(100),
            logCompleto(14),
            Optional.empty(),
            logCompleto(15),
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

        assertEquals(8, chamadas.get(), "Bloco saudavel intermediario deve resetar o contador antes do abort.");
        assertTrue(erro.getMessage().contains("4/5"));
    }

    @Test
    void deveAbortarAposDuasFalhasAuditAusenteDeColetas() {
        final Queue<StepExecutionResult> resultados = filaDeResultados(
            resultadoGraphql("usuarios_sistema", StepStatus.SUCCESS, "ok usuarios bloco 1"),
            resultadoGraphql("coletas", StepStatus.SUCCESS, "ok bloco 1"),
            resultadoGraphql("usuarios_sistema", StepStatus.SUCCESS, "ok usuarios bloco 2"),
            resultadoGraphql("coletas", StepStatus.SUCCESS, "ok bloco 2")
        );
        final Queue<Optional<LogExtracaoInfo>> logs = filaDeLogs(
            logCompleto(12),
            logCompleto(210),
            logCompleto(13),
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

        assertEquals(4, chamadas.get());
        assertTrue(erro.getMessage().contains("falhas criticas consecutivas de coletas"));
    }

    @Test
    void deveAbortarAposDuasFalhasReferenciaisAtribuidasAColetas() {
        final Queue<StepExecutionResult> resultados = filaDeResultados(
            resultadoGraphql("usuarios_sistema", StepStatus.SUCCESS, "ok usuarios bloco 1"),
            resultadoGraphql("coletas", StepStatus.SUCCESS, "ok bloco 1"),
            resultadoGraphql("usuarios_sistema", StepStatus.SUCCESS, "ok usuarios bloco 2"),
            resultadoGraphql("coletas", StepStatus.SUCCESS, "ok bloco 2")
        );
        final Queue<Optional<LogExtracaoInfo>> logs = filaDeLogs(
            logCompleto(12),
            logCompleto(180),
            logCompleto(13),
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

        assertEquals(4, chamadas.get());
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

    private ExtracaoPorIntervaloUseCase criarUseCase(
        final GraphQLGateway graphQLGateway,
        final ExtractionLogQueryPort extractionLogQueryPort,
        final IntegridadeEtlPort integridadeEtlPort
    ) {
        return criarUseCase(
            graphQLGateway,
            extractionLogQueryPort,
            integridadeEtlPort,
            new NoOpPreBackfillReferencialColetasUseCase()
        );
    }

    private ExtracaoPorIntervaloUseCase criarUseCase(
        final GraphQLGateway graphQLGateway,
        final ExtractionLogQueryPort extractionLogQueryPort,
        final IntegridadeEtlPort integridadeEtlPort,
        final PreBackfillReferencialColetasUseCase preBackfillReferencialColetasUseCase
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
            preBackfillReferencialColetasUseCase,
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

    private void assertPruneConfiguradoParaModo(
        final ExtracaoPorIntervaloRequest.ModoExecucao modoExecucao,
        final String valorEsperado
    ) {
        final Queue<StepExecutionResult> resultados = filaDeResultados(
            resultadoGraphql("usuarios_sistema", StepStatus.SUCCESS, "ok usuarios"),
            resultadoGraphql("fretes", StepStatus.SUCCESS, "ok fretes")
        );
        final Queue<Optional<LogExtracaoInfo>> logs = filaDeLogs(logCompleto(12), logCompleto(42));
        final Queue<IntegridadeEtlPort.ResultadoIntegridade> integridade = filaDeIntegridade(integridadeValida());
        final Queue<String> pruneObservado = new ArrayDeque<>();

        final GraphQLGateway gateway = (dataInicio, dataFim, entidade) -> {
            pruneObservado.add(System.getProperty(PROP_PRUNE_AUSENTES_FRETES));
            return resultados.remove();
        };

        final ExtracaoPorIntervaloUseCase useCase = criarUseCase(
            gateway,
            new SequencialExtractionLogQueryPort(logs),
            new SequencialIntegridadePort(integridade)
        );
        final ExtracaoPorIntervaloRequest request = new ExtracaoPorIntervaloRequest(
            LocalDate.of(2026, 4, 27),
            LocalDate.of(2026, 4, 28),
            "graphql",
            "fretes",
            true,
            false,
            modoExecucao
        );

        assertDoesNotThrow(() -> useCase.executar(request));

        assertEquals(List.of(valorEsperado, valorEsperado), List.copyOf(pruneObservado));
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

    private static final class TrackingPreBackfillReferencialColetasUseCase extends PreBackfillReferencialColetasUseCase {
        private final AtomicInteger preExecucoes = new AtomicInteger();
        private final AtomicInteger posExecucoes = new AtomicInteger();

        @Override
        public void executar(final LocalDate dataInicio, final LocalDate dataFim) {
            preExecucoes.incrementAndGet();
        }

        @Override
        public void executarPosExtracao(final LocalDate dataInicio, final LocalDate dataFim) {
            posExecucoes.incrementAndGet();
        }
    }

    private static final class CapturingExtracaoPorIntervaloUseCase extends ExtracaoPorIntervaloUseCase {
        private ExtracaoPorIntervaloRequest requestCapturada;

        @Override
        public void executar(final ExtracaoPorIntervaloRequest request) {
            this.requestCapturada = request;
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
