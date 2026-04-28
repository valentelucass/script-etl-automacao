package br.com.extrator.bootstrap.pipeline;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import br.com.extrator.suporte.concorrencia.ExecutionTimeoutException;
import br.com.extrator.suporte.observabilidade.ExecutionContext;

class IsolatedStepProcessExecutorTest {

    @AfterEach
    void limparSystemProperties() {
        System.clearProperty("API_THROTTLING_MINIMO_MS");
        System.clearProperty("ETL_GRAPHQL_TIMEOUT_ENTIDADE_COLETAS_MS");
        System.clearProperty("ETL_GRAPHQL_TIMEOUT_ENTIDADE_FRETES_MS");
        System.clearProperty("ETL_GRAPHQL_TIMEOUT_ENTIDADE_USUARIOS_SISTEMA_MS");
        System.clearProperty("ETL_REFERENCIAL_COLETAS_BACKFILL_MAX_EXPANSAO_DIAS");
        System.clearProperty("etl.parent.execution.id");
        System.clearProperty("etl.parent.retry.attempt");
        System.clearProperty("etl.parent.retry.max_attempts");
        System.clearProperty("etl.process.isolation.enabled");
        ExecutionContext.clear();
    }

    @Test
    void deveEncerrarProcessoFilhoTravadoQuandoTimeoutExpira() {
        final IsolatedStepProcessExecutor executor = new HangingCommandExecutor();
        final long inicioMs = System.currentTimeMillis();

        final ExecutionTimeoutException erro = assertThrows(
            ExecutionTimeoutException.class,
            () -> executor.executar(
                IsolatedStepProcessExecutor.ApiType.GRAPHQL,
                LocalDate.of(2026, 3, 18),
                LocalDate.of(2026, 3, 18),
                "all",
                Duration.ofMillis(1_500L)
            )
        );

        final long duracaoMs = System.currentTimeMillis() - inicioMs;
        assertTrue(duracaoMs < 10_000L, "Processo filho travado deve ser abortado em tempo finito");
        assertTrue(erro.getMessage().contains("timeout") || erro.getMessage().contains("excedeu"));
    }

    @Test
    void devePropagarOverridesApiEEtlParaProcessoFilho() throws Exception {
        System.setProperty("API_THROTTLING_MINIMO_MS", "500");
        System.setProperty("ETL_GRAPHQL_TIMEOUT_ENTIDADE_COLETAS_MS", "1800000");
        System.setProperty("ETL_GRAPHQL_TIMEOUT_ENTIDADE_FRETES_MS", "900000");
        System.setProperty("ETL_GRAPHQL_TIMEOUT_ENTIDADE_USUARIOS_SISTEMA_MS", "5400000");
        System.setProperty("ETL_REFERENCIAL_COLETAS_BACKFILL_MAX_EXPANSAO_DIAS", "400");
        System.setProperty("etl.process.isolation.enabled", "true");

        final InspectingExecutor executor = new InspectingExecutor();
        final List<String> comando = executor.construir(
            IsolatedStepProcessExecutor.ApiType.GRAPHQL,
            LocalDate.of(2026, 3, 18),
            LocalDate.of(2026, 3, 18),
            "usuarios_sistema"
        );

        assertTrue(comando.contains("-DAPI_THROTTLING_MINIMO_MS=500"));
        assertTrue(comando.contains("-DETL_GRAPHQL_TIMEOUT_ENTIDADE_COLETAS_MS=1800000"));
        assertTrue(comando.contains("-DETL_GRAPHQL_TIMEOUT_ENTIDADE_FRETES_MS=900000"));
        assertTrue(comando.contains("-DETL_GRAPHQL_TIMEOUT_ENTIDADE_USUARIOS_SISTEMA_MS=5400000"));
        assertTrue(comando.contains("-DETL_REFERENCIAL_COLETAS_BACKFILL_MAX_EXPANSAO_DIAS=400"));
        assertTrue(comando.contains("-Dextrator.logger.console.mirror=true"));
        assertFalse(comando.contains("-Detl.process.isolation.enabled=true"));
    }

    @Test
    void devePropagarContextoDeRetryParaProcessoFilho() throws Exception {
        ExecutionContext.initialize("--loop-daemon-run");
        ExecutionContext.setRetryContext(2, 3);

        final InspectingExecutor executor = new InspectingExecutor();
        final List<String> comando = executor.construir(
            IsolatedStepProcessExecutor.ApiType.GRAPHQL,
            LocalDate.of(2026, 3, 18),
            LocalDate.of(2026, 3, 18),
            "fretes"
        );

        assertTrue(comando.contains("-Detl.parent.retry.attempt=2"));
        assertTrue(comando.contains("-Detl.parent.retry.max_attempts=3"));
    }

    private static final class HangingCommandExecutor extends IsolatedStepProcessExecutor {
        @Override
        protected List<String> construirComando(final ApiType apiType,
                                                final LocalDate dataInicio,
                                                final LocalDate dataFim,
                                                final String entidade,
                                                final FaultMode faultMode) throws URISyntaxException {
            return List.of(
                "powershell",
                "-NoProfile",
                "-Command",
                "while ($true) { Start-Sleep -Milliseconds 200 }"
            );
        }
    }

    private static final class InspectingExecutor extends IsolatedStepProcessExecutor {
        private List<String> construir(final ApiType apiType,
                                       final LocalDate dataInicio,
                                       final LocalDate dataFim,
                                       final String entidade) throws URISyntaxException {
            return construirComando(apiType, dataInicio, dataFim, entidade, FaultMode.NONE);
        }
    }
}
