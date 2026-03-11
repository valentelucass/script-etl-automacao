package br.com.extrator.aplicacao.politicas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class MapFailurePolicyTest {

    @Test
    void deveResolverModoPorEntidadeCaseInsensitive() {
        final MapFailurePolicy policy = new MapFailurePolicy(
            Map.of(
                "graphql", FailureMode.CONTINUE_WITH_ALERT,
                "quality", FailureMode.DEGRADE
            ),
            FailureMode.ABORT_PIPELINE
        );

        assertEquals(FailureMode.CONTINUE_WITH_ALERT, policy.resolver("GraphQL", ErrorTaxonomy.TRANSIENT_API_ERROR));
        assertEquals(FailureMode.DEGRADE, policy.resolver("QUALITY", ErrorTaxonomy.DATA_QUALITY_BREACH));
        assertEquals(FailureMode.ABORT_PIPELINE, policy.resolver("desconhecida", ErrorTaxonomy.TIMEOUT));
    }
}

