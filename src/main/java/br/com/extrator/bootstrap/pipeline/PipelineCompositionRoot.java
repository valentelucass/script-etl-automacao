/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/bootstrap/pipeline/PipelineCompositionRoot.java
Classe  : PipelineCompositionRoot (class)
Pacote  : br.com.extrator.bootstrap.pipeline
Modulo  : Bootstrap - Wiring

Papel   : Composicao root de dependency injection: wires politicas, adapters, factory methods para PipelineOrchestrator.

Conecta com:
- AplicacaoContexto (registra factories e adapters)
- PipelineOrchestrator, ExtractorRegistry (cria instancias)
- DataQualityService, CircuitBreaker, RetryPolicy, FailurePolicy (compoe)

Fluxo geral:
1) inicializarContexto() static: bootstrap entry point (chamado em Main.java).
2) Cria adapters (GraphQL, DataExport, gateways, repositories).
3) Registra factories em AplicacaoContexto.
4) criarOrquestrador(): fabrica orchestrator com politicas.
5) criarStepsFluxoCompleto(): ordena steps (graphql, dataexport, quality).

Estrutura interna:
Factory methods:
- criarOrquestrador(): PipelineOrchestrator com retry, failure, circuit breaker.
- criarStepsFluxoCompleto(), criarRegistryFluxoCompleto(): ExtractorRegistry.
- criarDataQualityService(): DataQualityService com 5 checks.
- criarPoliticaFalhaPorEntidade(): Map de failure modes.
Atributos-chave:
- config, clock, extractionLogger, metrics: portas de suporte.
[DOC-FILE-END]============================================================== */
package br.com.extrator.bootstrap.pipeline;

import br.com.extrator.aplicacao.contexto.AplicacaoContexto;
import br.com.extrator.observabilidade.adaptador.CompletudePortAdapter;
import br.com.extrator.observabilidade.adaptador.IntegridadeEtlPortAdapter;
import br.com.extrator.persistencia.adaptador.ExtractionLogQueryAdapter;
import br.com.extrator.persistencia.adaptador.ManifestoOrfaoQueryAdapter;
import br.com.extrator.aplicacao.pipeline.DataExportPipelineStep;
import br.com.extrator.aplicacao.pipeline.DataQualityPipelineStep;
import br.com.extrator.aplicacao.pipeline.ExtractorRegistry;
import br.com.extrator.aplicacao.pipeline.GraphQLPipelineStep;
import br.com.extrator.aplicacao.pipeline.PipelineOrchestrator;
import br.com.extrator.aplicacao.pipeline.PipelineStep;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import br.com.extrator.observabilidade.quality.CompletenessCheck;
import br.com.extrator.observabilidade.quality.DataQualityCheck;
import br.com.extrator.observabilidade.quality.DataQualityService;
import br.com.extrator.observabilidade.quality.FreshnessCheck;
import br.com.extrator.observabilidade.quality.ReferentialIntegrityCheck;
import br.com.extrator.observabilidade.quality.SchemaValidationCheck;
import br.com.extrator.observabilidade.quality.SqlServerDataQualityQueryAdapter;
import br.com.extrator.observabilidade.quality.UniquenessCheck;
import br.com.extrator.observabilidade.pipeline.InMemoryPipelineMetrics;
import br.com.extrator.observabilidade.pipeline.JsonStructuredExtractionLogger;
import br.com.extrator.aplicacao.portas.PipelineMetricsPort;
import br.com.extrator.aplicacao.politicas.CircuitBreaker;
import br.com.extrator.aplicacao.politicas.ErrorClassifier;
import br.com.extrator.aplicacao.politicas.FailureMode;
import br.com.extrator.aplicacao.politicas.FailurePolicy;
import br.com.extrator.aplicacao.politicas.MapFailurePolicy;
import br.com.extrator.aplicacao.politicas.ExponentialBackoffRetryPolicy;
import br.com.extrator.aplicacao.politicas.RetryPolicy;
import br.com.extrator.aplicacao.portas.ClockPort;
import br.com.extrator.aplicacao.portas.ConfigPort;
import br.com.extrator.aplicacao.portas.ExtractionLoggerPort;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public final class PipelineCompositionRoot {
    private final ConfigPort config;
    private final ClockPort clock;
    private final ExtractionLoggerPort extractionLogger;
    private final PipelineMetricsPort metrics;

    public PipelineCompositionRoot(
        final ConfigPort config,
        final ClockPort clock,
        final ExtractionLoggerPort extractionLogger,
        final PipelineMetricsPort metrics
    ) {
        this.config = config;
        this.clock = clock;
        this.extractionLogger = extractionLogger;
        this.metrics = metrics;
    }

    public static PipelineCompositionRoot criarPadrao() {
        return new PipelineCompositionRoot(
            new CarregadorConfigAdapter(),
            new SystemClockAdapter(),
            new JsonStructuredExtractionLogger(),
            new InMemoryPipelineMetrics()
        );
    }

    /**
     * Inicializa o AplicacaoContexto com todas as portas necessarias.
     * Deve ser chamado no bootstrap antes de qualquer execucao de comando.
     */
    public static void inicializarContexto() {
        final PipelineCompositionRoot root = criarPadrao();
        final GraphQLGatewayAdapter graphQLGateway = new GraphQLGatewayAdapter();
        final DataExportGatewayAdapter dataExportGateway = new DataExportGatewayAdapter();

        AplicacaoContexto.registrar((br.com.extrator.aplicacao.portas.PipelineOrchestratorFactory) root::criarOrquestrador);
        AplicacaoContexto.registrar((br.com.extrator.aplicacao.portas.PipelineStepsFactory) root::criarStepsFluxoCompleto);
        AplicacaoContexto.registrar((br.com.extrator.aplicacao.portas.GraphQLGateway) graphQLGateway);
        AplicacaoContexto.registrar((br.com.extrator.aplicacao.portas.DataExportGateway) dataExportGateway);
        AplicacaoContexto.registrar(new ExtractionLogQueryAdapter());
        AplicacaoContexto.registrar(new CompletudePortAdapter());
        AplicacaoContexto.registrar(new IntegridadeEtlPortAdapter());
        AplicacaoContexto.registrar(new ManifestoOrfaoQueryAdapter());
    }

    public PipelineOrchestrator criarOrquestrador() {
        final RetryPolicy retryPolicy = new ExponentialBackoffRetryPolicy(
            config.obterInteiro("etl.retry.max_tentativas", config.obterInteiro("api.retry.max_tentativas", 3)),
            config.obterLongo("etl.retry.delay_base_ms", config.obterLongo("api.retry.delay_base_ms", 1_000L)),
            config.obterDecimal("etl.retry.multiplicador", config.obterDecimal("api.retry.multiplicador", 2.0d)),
            config.obterDecimal("etl.retry.jitter", 0.2d),
            clock
        );

        final FailurePolicy failurePolicy = new MapFailurePolicy(
            criarPoliticaFalhaPorEntidade(),
            parseFailureMode(config.obterTexto("etl.failure.default", "CONTINUE_WITH_ALERT"), FailureMode.CONTINUE_WITH_ALERT)
        );

        final CircuitBreaker circuitBreaker = new CircuitBreaker(
            config.obterInteiro("etl.circuit.failure_threshold", 3),
            Duration.ofSeconds(config.obterLongo("etl.circuit.open_seconds", 60L)),
            clock
        );

        return new PipelineOrchestrator(
            retryPolicy,
            failurePolicy,
            circuitBreaker,
            new ErrorClassifier(),
            extractionLogger,
            metrics
        );
    }

    public List<PipelineStep> criarStepsFluxoCompleto(
        final boolean incluirFaturasGraphQL,
        final boolean incluirDataQuality
    ) {
        final ExtractorRegistry registry = criarRegistryFluxoCompleto(incluirFaturasGraphQL, incluirDataQuality);
        final List<String> ordem = new ArrayList<>(List.of("graphql", "dataexport"));
        if (incluirFaturasGraphQL) {
            ordem.add(ConstantesEntidades.FATURAS_GRAPHQL);
        }
        if (incluirDataQuality) {
            ordem.add("quality");
        }
        return registry.listarPorEntidades(ordem);
    }

    public ExtractorRegistry criarRegistryFluxoCompleto(
        final boolean incluirFaturasGraphQL,
        final boolean incluirDataQuality
    ) {
        final ExtractorRegistry registry = new ExtractorRegistry();
        registry.registrar("graphql", () -> new GraphQLPipelineStep(new GraphQLGatewayAdapter(), "graphql"));
        registry.registrar("dataexport", () -> new DataExportPipelineStep(new DataExportGatewayAdapter(), "dataexport"));

        if (incluirFaturasGraphQL) {
            registry.registrar(
                ConstantesEntidades.FATURAS_GRAPHQL,
                () -> new GraphQLPipelineStep(new GraphQLGatewayAdapter(), ConstantesEntidades.FATURAS_GRAPHQL)
            );
        }

        if (incluirDataQuality) {
            registry.registrar(
                "quality",
                () -> new DataQualityPipelineStep(
                    criarDataQualityService(),
                    criarEntidadesPadraoDataQuality(incluirFaturasGraphQL)
                )
            );
        }

        return registry;
    }

    private DataQualityService criarDataQualityService() {
        final List<DataQualityCheck> checks = List.of(
            new UniquenessCheck(),
            new CompletenessCheck(),
            new FreshnessCheck(),
            new ReferentialIntegrityCheck(),
            new SchemaValidationCheck()
        );

        return new DataQualityService(
            new SqlServerDataQualityQueryAdapter(),
            checks,
            config.obterInteiro("etl.quality.max_lag_minutes", 180),
            config.obterTexto("etl.quality.schema_version", "v2"),
            config.obterInteiro("etl.quality.max_referential_breaks", 500)
        );
    }

    private List<String> criarEntidadesPadraoDataQuality(final boolean incluirFaturasGraphQL) {
        final List<String> entidades = new ArrayList<>(List.of(
            ConstantesEntidades.USUARIOS_SISTEMA,
            ConstantesEntidades.COLETAS,
            ConstantesEntidades.FRETES,
            ConstantesEntidades.MANIFESTOS,
            ConstantesEntidades.COTACOES,
            ConstantesEntidades.LOCALIZACAO_CARGAS,
            ConstantesEntidades.CONTAS_A_PAGAR,
            ConstantesEntidades.FATURAS_POR_CLIENTE
        ));
        if (incluirFaturasGraphQL) {
            entidades.add(ConstantesEntidades.FATURAS_GRAPHQL);
        }
        return List.copyOf(entidades);
    }

    private Map<String, FailureMode> criarPoliticaFalhaPorEntidade() {
        final Map<String, FailureMode> porEntidade = new LinkedHashMap<>();
        porEntidade.put(
            "graphql",
            parseFailureMode(config.obterTexto("etl.failure.graphql", "CONTINUE_WITH_ALERT"), FailureMode.CONTINUE_WITH_ALERT)
        );
        porEntidade.put(
            "dataexport",
            parseFailureMode(config.obterTexto("etl.failure.dataexport", "CONTINUE_WITH_ALERT"), FailureMode.CONTINUE_WITH_ALERT)
        );
        porEntidade.put(
            ConstantesEntidades.FATURAS_GRAPHQL,
            parseFailureMode(config.obterTexto("etl.failure.faturas_graphql", "CONTINUE_WITH_ALERT"), FailureMode.CONTINUE_WITH_ALERT)
        );
        porEntidade.put(
            "quality",
            parseFailureMode(config.obterTexto("etl.failure.quality", "DEGRADE"), FailureMode.DEGRADE)
        );
        return porEntidade;
    }

    private FailureMode parseFailureMode(final String raw, final FailureMode fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return FailureMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}


