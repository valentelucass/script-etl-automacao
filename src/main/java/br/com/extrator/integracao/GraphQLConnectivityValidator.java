package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/GraphQLConnectivityValidator.java
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


import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import org.slf4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class GraphQLConnectivityValidator {
    private final HttpClient clienteHttp;
    private final ObjectMapper mapeadorJson;
    private final GraphQLRequestFactory requestFactory;
    private final Logger logger;

    GraphQLConnectivityValidator(final HttpClient clienteHttp,
                                 final ObjectMapper mapeadorJson,
                                 final GraphQLRequestFactory requestFactory,
                                 final Logger logger) {
        this.clienteHttp = clienteHttp;
        this.mapeadorJson = mapeadorJson;
        this.requestFactory = requestFactory;
        this.logger = logger;
    }

    boolean validarAcessoApi() {
        logger.info("Validando acesso a API GraphQL...");

        try {
            final String queryTeste = "{ __schema { queryType { name } } }";
            final GraphQLRequestFactory.RequestPayload payload = requestFactory.criarPostSemTimeout(queryTeste);
            final HttpResponse<String> resposta = clienteHttp.send(
                payload.requisicao(),
                HttpResponse.BodyHandlers.ofString()
            );

            if (resposta.statusCode() != 200) {
                logger.error("Falha na validacao da API GraphQL. Status: {}", resposta.statusCode());
                return false;
            }

            final JsonNode respostaJson = mapeadorJson.readTree(resposta.body());
            final boolean sucesso = !respostaJson.has("errors");
            if (sucesso) {
                logger.info("Validacao da API GraphQL bem-sucedida");
            } else {
                logger.error("Erro na validacao da API GraphQL: {}", respostaJson.get("errors"));
            }
            return sucesso;
        } catch (final IOException | InterruptedException e) {
            logger.error("Erro durante validacao da API GraphQL: {}", e.getMessage(), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}
