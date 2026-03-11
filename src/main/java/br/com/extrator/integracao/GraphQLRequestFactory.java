package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/GraphQLRequestFactory.java
Classe  :  (class)
Pacote  : br.com.extrator.integracao
Modulo  : Integracao HTTP
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class GraphQLRequestFactory {
    private final ObjectMapper objectMapper;
    private final String urlBase;
    private final String endpointGraphQL;
    private final String token;
    private final Duration timeoutRequisicao;

    GraphQLRequestFactory(final ObjectMapper objectMapper,
                          final String urlBase,
                          final String endpointGraphQL,
                          final String token,
                          final Duration timeoutRequisicao) {
        this.objectMapper = objectMapper;
        this.urlBase = urlBase;
        this.endpointGraphQL = endpointGraphQL;
        this.token = token;
        this.timeoutRequisicao = timeoutRequisicao;
    }

    RequestPayload criarPost(final String query) throws JsonProcessingException {
        return criarPost(query, null, true);
    }

    RequestPayload criarPost(final String query,
                             final Map<String, Object> variaveis) throws JsonProcessingException {
        return criarPost(query, variaveis, true);
    }

    RequestPayload criarPostSemTimeout(final String query) throws JsonProcessingException {
        return criarPost(query, null, false);
    }

    private RequestPayload criarPost(final String query,
                                     final Map<String, Object> variaveis,
                                     final boolean aplicarTimeout) throws JsonProcessingException {
        final ObjectNode corpoJson = objectMapper.createObjectNode();
        corpoJson.put("query", query);
        if (variaveis != null && !variaveis.isEmpty()) {
            corpoJson.set("variables", objectMapper.valueToTree(variaveis));
        }
        final String corpoRequisicao = objectMapper.writeValueAsString(corpoJson);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(urlBase + endpointGraphQL))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token)
            .POST(HttpRequest.BodyPublishers.ofString(corpoRequisicao));
        if (aplicarTimeout) {
            builder = builder.timeout(timeoutRequisicao);
        }
        return new RequestPayload(corpoRequisicao, builder.build());
    }

    record RequestPayload(String corpoRequisicao, HttpRequest requisicao) {
    }
}
