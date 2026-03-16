package br.com.extrator.integracao.graphql;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GraphQLQueriesContractTest {

    @Test
    void queryUsuariosSistemaDeveUsarVariavelAfterCompativelComPaginador() {
        assertTrue(GraphQLQueries.QUERY_USUARIOS_SISTEMA.contains("$after: String"));
        assertTrue(GraphQLQueries.QUERY_USUARIOS_SISTEMA.contains("after: $after"));
    }
}
