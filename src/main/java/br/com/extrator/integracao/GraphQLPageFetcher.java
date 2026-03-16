package br.com.extrator.integracao;

import java.util.Map;

@FunctionalInterface
interface GraphQLPageFetcher {
    <T> PaginatedGraphQLResponse<T> fetch(
        String query,
        String nomeEntidade,
        Map<String, Object> variaveis,
        Class<T> tipoClasse
    );
}
