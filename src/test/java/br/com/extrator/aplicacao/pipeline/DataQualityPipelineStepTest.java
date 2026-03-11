package br.com.extrator.aplicacao.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.aplicacao.pipeline.runtime.StepStatus;
import br.com.extrator.observabilidade.quality.DataQualityCheck;
import br.com.extrator.observabilidade.quality.DataQualityCheckResult;
import br.com.extrator.observabilidade.quality.DataQualityContext;
import br.com.extrator.observabilidade.quality.DataQualityQueryPort;
import br.com.extrator.observabilidade.quality.DataQualityService;

class DataQualityPipelineStepTest {

    @Test
    void devePropagarDetalhesDasFalhasNoResultadoDoStep() {
        final DataQualityService service = new DataQualityService(
            new QueryPortStub(),
            List.of(
                new StaticCheck(
                    "schema",
                    new DataQualityCheckResult("faturas_graphql", "schema", false, 1.0, 0.0, "schema=v1 esperado=v2")
                )
            ),
            5,
            "v2",
            0
        );
        final DataQualityPipelineStep step = new DataQualityPipelineStep(service, List.of("faturas_graphql"));

        final StepExecutionResult result = step.executar(LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 9));

        assertEquals(StepStatus.FAILED, result.getStatus());
        assertEquals(1L, result.getMetadata().get("checks_failed"));
        assertTrue(String.valueOf(result.getMetadata().get("checks_failed_detail")).contains("faturas_graphql:schema"));
        assertTrue(result.getMessage().contains("checks_failed=1"));
        assertTrue(result.getMessage().contains("schema=v1 esperado=v2"));
    }

    private static final class StaticCheck implements DataQualityCheck {
        private final String nome;
        private final DataQualityCheckResult resultado;

        private StaticCheck(final String nome, final DataQualityCheckResult resultado) {
            this.nome = nome;
            this.resultado = resultado;
        }

        @Override
        public String obterNome() {
            return nome;
        }

        @Override
        public DataQualityCheckResult executar(final DataQualityContext context) {
            return resultado;
        }
    }

    private static final class QueryPortStub implements DataQualityQueryPort {
        @Override
        public long contarDuplicidadesChaveNatural(final String entidade, final LocalDate dataInicio, final LocalDate dataFim) {
            return 0L;
        }

        @Override
        public long contarLinhasIncompletas(final String entidade, final LocalDate dataInicio, final LocalDate dataFim) {
            return 0L;
        }

        @Override
        public java.time.LocalDateTime buscarTimestampMaisRecente(final String entidade) {
            return null;
        }

        @Override
        public long contarQuebrasReferenciais(final String entidade) {
            return 0L;
        }

        @Override
        public String detectarVersaoSchema(final String entidade) {
            return "v2";
        }
    }
}
