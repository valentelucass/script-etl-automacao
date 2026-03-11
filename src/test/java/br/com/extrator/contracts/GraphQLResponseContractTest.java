package br.com.extrator.contracts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class GraphQLResponseContractTest {

    @Test
    void deveRespeitarContratoMinimoDeRespostaGraphQL() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        try (InputStream stream = getClass().getResourceAsStream("/contracts/graphql-fretes-sample.json")) {
            assertNotNull(stream, "fixture de contrato nao encontrada");
            final JsonNode root = mapper.readTree(stream);

            final JsonNode nodes = root.path("data").path("shipmentList").path("nodes");
            assertTrue(nodes.isArray());
            assertFalse(nodes.isEmpty());

            final JsonNode first = nodes.get(0);
            assertTrue(first.hasNonNull("id"));
            assertTrue(first.hasNonNull("code"));
            assertTrue(first.hasNonNull("createdAt"));
        }
    }
}
