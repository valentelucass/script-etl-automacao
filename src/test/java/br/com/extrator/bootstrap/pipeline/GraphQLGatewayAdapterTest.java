package br.com.extrator.bootstrap.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import br.com.extrator.aplicacao.contexto.AplicacaoContexto;
import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.integracao.graphql.services.GraphQLExtractionService;
import br.com.extrator.suporte.configuracao.ConfigEtl;
import br.com.extrator.suporte.observabilidade.ExecutionContext;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

class GraphQLGatewayAdapterTest {

    @AfterEach
    void limparContexto() {
        ExecutionContext.clear();
        System.clearProperty("ETL_PROCESS_ISOLATION_ENABLED");
        System.clearProperty("etl.process.isolation.enabled");
    }

    @Test
    void deveForcarIsolamentoNoLoopDaemonMesmoComFlagGlobalDesativada() throws Exception {
        System.setProperty("ETL_PROCESS_ISOLATION_ENABLED", "false");
        ExecutionContext.initialize("--loop-daemon-run");
        ExecutionContext.setCycleId("cycle-daemon");

        final RecordingGraphQLService service = new RecordingGraphQLService();
        final RecordingIsolatedExecutor isolatedExecutor = new RecordingIsolatedExecutor();
        final GraphQLGatewayAdapter adapter = new GraphQLGatewayAdapter(service, isolatedExecutor);

        final StepExecutionResult result = adapter.executar(
            LocalDate.of(2026, 3, 18),
            LocalDate.of(2026, 3, 18),
            "graphql"
        );

        assertTrue(isolatedExecutor.executado);
        assertFalse(service.executado);
        assertEquals(IsolatedStepProcessExecutor.ApiType.GRAPHQL, isolatedExecutor.apiType);
        assertEquals(ConfigEtl.obterTimeoutStepGraphQLCompleto(), isolatedExecutor.timeout);
        assertEquals("isolated_process", result.getMetadata().get("execution_mode"));
        assertEquals(Boolean.TRUE, result.getMetadata().get("forced_by_daemon"));
    }

    @Test
    void deveUsarTimeoutDaEntidadeQuandoExecutaGraphqlEspecifico() throws Exception {
        System.setProperty("ETL_PROCESS_ISOLATION_ENABLED", "true");
        final String chave = "ETL_GRAPHQL_TIMEOUT_ENTIDADE_USUARIOS_SISTEMA_MS";
        final String anterior = System.getProperty(chave);
        try {
            System.setProperty(chave, "420000");
            final RecordingGraphQLService service = new RecordingGraphQLService();
            final RecordingIsolatedExecutor isolatedExecutor = new RecordingIsolatedExecutor();
            final GraphQLGatewayAdapter adapter = new GraphQLGatewayAdapter(service, isolatedExecutor);

            adapter.executar(
                LocalDate.of(2026, 3, 18),
                LocalDate.of(2026, 3, 18),
                ConstantesEntidades.USUARIOS_SISTEMA
            );

            assertEquals(Duration.ofMinutes(7), isolatedExecutor.timeout);
        } finally {
            if (anterior == null) {
                System.clearProperty(chave);
            } else {
                System.setProperty(chave, anterior);
            }
        }
    }

    private static final class RecordingGraphQLService extends GraphQLExtractionService {
        private boolean executado;

        private RecordingGraphQLService() {
            super(
                null,
                new br.com.extrator.persistencia.repositorio.LogExtracaoRepository(),
                AplicacaoContexto.executionAuditPort(),
                new br.com.extrator.integracao.comum.ExtractionLogger(RecordingGraphQLService.class),
                br.com.extrator.suporte.console.LoggerConsole.getLogger(RecordingGraphQLService.class)
            );
        }

        @Override
        public void executar(final LocalDate dataInicio, final LocalDate dataFim, final String entidade) {
            executado = true;
        }
    }

    private static final class RecordingIsolatedExecutor extends IsolatedStepProcessExecutor {
        private boolean executado;
        private ApiType apiType;
        private Duration timeout;

        @Override
        public ProcessExecutionResult executar(final ApiType apiType,
                                               final LocalDate dataInicio,
                                               final LocalDate dataFim,
                                               final String entidade,
                                               final Duration timeout) {
            this.executado = true;
            this.apiType = apiType;
            this.timeout = timeout;
            return new ProcessExecutionResult(777L, Path.of("logs", "isolated_test.log"));
        }
    }
}
