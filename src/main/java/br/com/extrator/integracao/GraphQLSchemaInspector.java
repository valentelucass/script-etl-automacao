package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/GraphQLSchemaInspector.java
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


import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.extrator.suporte.http.GerenciadorRequisicaoHttp;

final class GraphQLSchemaInspector {
    private final Logger logger;
    private final HttpClient clienteHttp;
    private final ObjectMapper mapeadorJson;
    private final GerenciadorRequisicaoHttp gerenciadorRequisicao;
    private final GraphQLRequestFactory requestFactory;

    GraphQLSchemaInspector(final Logger logger,
                          final HttpClient clienteHttp,
                          final ObjectMapper mapeadorJson,
                          final GerenciadorRequisicaoHttp gerenciadorRequisicao,
                          final GraphQLRequestFactory requestFactory) {
        this.logger = logger;
        this.clienteHttp = clienteHttp;
        this.mapeadorJson = mapeadorJson;
        this.gerenciadorRequisicao = gerenciadorRequisicao;
        this.requestFactory = requestFactory;
    }

    Set<String> listarCamposInput(final String introspectionQuery,
                                  final String requestKey,
                                  final String warningMessage) {
        final Set<String> campos = new LinkedHashSet<>();
        try {
            final GraphQLRequestFactory.RequestPayload payload = requestFactory.criarPost(introspectionQuery);
            final HttpResponse<String> resposta = gerenciadorRequisicao.executarRequisicaoEstrita(
                clienteHttp,
                payload.requisicao(),
                requestKey
            );
            final JsonNode respostaJson = mapeadorJson.readTree(resposta.body());
            final JsonNode fields = respostaJson.path("data").path("__type").path("inputFields");
            if (fields.isArray()) {
                for (final JsonNode f : fields) {
                    final String nome = f.path("name").asText();
                    if (nome != null && !nome.isBlank()) {
                        campos.add(nome);
                    }
                }
            }
        } catch (final RuntimeException | java.io.IOException e) {
            logger.warn("{}: {}", warningMessage, e.getMessage());
        }
        return campos;
    }
}
