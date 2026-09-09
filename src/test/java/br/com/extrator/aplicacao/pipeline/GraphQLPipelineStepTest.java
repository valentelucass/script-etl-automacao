package br.com.extrator.aplicacao.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import br.com.extrator.aplicacao.portas.GraphQLGateway;
import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.suporte.configuracao.ConfigEtl;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

class GraphQLPipelineStepTest {

    @Test
    void deveUsarTimeoutAgregadoParaStepGraphqlCompleto() {
        final GraphQLPipelineStep step = new GraphQLPipelineStep(new NoOpGraphQLGateway(), "graphql");

        assertEquals(ConfigEtl.obterTimeoutStepGraphQLCompleto(), step.obterTimeoutExecucao());
    }

    @Test
    void deveUsarTimeoutDaEntidadeQuandoStepForEspecifico() {
        final String chave = "ETL_GRAPHQL_TIMEOUT_ENTIDADE_USUARIOS_SISTEMA_MS";
        final String anterior = System.getProperty(chave);
        try {
            System.setProperty(chave, "420000");
            final GraphQLPipelineStep step = new GraphQLPipelineStep(
                new NoOpGraphQLGateway(),
                ConstantesEntidades.USUARIOS_SISTEMA
            );

            assertEquals(Duration.ofMinutes(7), step.obterTimeoutExecucao());
        } finally {
            if (anterior == null) {
                System.clearProperty(chave);
            } else {
                System.setProperty(chave, anterior);
            }
        }
    }

    private static final class NoOpGraphQLGateway implements GraphQLGateway {
        @Override
        public StepExecutionResult executar(final java.time.LocalDate dataInicio,
                                            final java.time.LocalDate dataFim,
                                            final String entidade) {
            return StepExecutionResult.builder("graphql:" + entidade, entidade).build();
        }
    }
}
