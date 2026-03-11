/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/GraphQLTypedResponseParser.java
Classe  : GraphQLTypedResponseParser (class)
Pacote  : br.com.extrator.integracao
Modulo  : Integracao HTTP

Papel   : Parser tipado de respostas GraphQL (edges/node, paginacao, suporta formato antigo).

Conecta com:
- ObjectMapper (Jackson)

Fluxo geral:
1) extrairPagina(dadosEntidade, nomeEntidade, tipoClasse) deserializa JSON.
2) Detecta formato: edges/pageInfo (novo) ou array direto (antigo).
3) Retorna ParsedGraphQLPage com entidades, hasNextPage, endCursor.

Estrutura interna:
Inner record ParsedGraphQLPage<T>:
- entidades: List<T>.
- hasNextPage, endCursor: paginacao.
Metodos principais:
- extrairPagina(): parser principal.
- adicionarEntidade(): deserializacao com tratamento de erro.
[DOC-FILE-END]============================================================== */
package br.com.extrator.integracao;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class GraphQLTypedResponseParser {
    private static final Logger logger = LoggerFactory.getLogger(GraphQLTypedResponseParser.class);

    private final ObjectMapper objectMapper;

    GraphQLTypedResponseParser(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    <T> ParsedGraphQLPage<T> extrairPagina(final JsonNode dadosEntidade,
                                           final String nomeEntidade,
                                           final Class<T> tipoClasse) {
        final List<T> entidades = new ArrayList<>();
        boolean hasNextPage = false;
        String endCursor = null;

        if (dadosEntidade.has("edges")) {
            logger.debug("Processando resposta paginada com edges/node para {}", nomeEntidade);
            final JsonNode edges = dadosEntidade.get("edges");
            if (edges.isArray()) {
                for (final JsonNode edge : edges) {
                    if (edge.has("node")) {
                        adicionarEntidade(entidades, edge.get("node"), nomeEntidade, tipoClasse, "node");
                    }
                }
            }

            if (dadosEntidade.has("pageInfo")) {
                final JsonNode pageInfo = dadosEntidade.get("pageInfo");
                if (pageInfo.has("hasNextPage")) {
                    hasNextPage = pageInfo.get("hasNextPage").asBoolean();
                }
                if (pageInfo.has("endCursor") && !pageInfo.get("endCursor").isNull()) {
                    endCursor = pageInfo.get("endCursor").asText();
                }
                logger.debug(
                    "Informacoes de paginacao extraidas - hasNextPage: {}, endCursor: {}",
                    hasNextPage,
                    endCursor
                );
            }

            return new ParsedGraphQLPage<>(entidades, hasNextPage, endCursor);
        }

        logger.debug("Processando resposta no formato antigo (array direto) para {}", nomeEntidade);
        if (dadosEntidade.isArray()) {
            for (final JsonNode item : dadosEntidade) {
                adicionarEntidade(entidades, item, nomeEntidade, tipoClasse, "item");
            }
        }
        return new ParsedGraphQLPage<>(entidades, false, null);
    }

    private <T> void adicionarEntidade(final List<T> entidades,
                                       final JsonNode origem,
                                       final String nomeEntidade,
                                       final Class<T> tipoClasse,
                                       final String tipoOrigem) {
        try {
            entidades.add(objectMapper.treeToValue(origem, tipoClasse));
        } catch (final JsonProcessingException | IllegalArgumentException e) {
            logger.warn(
                "Erro ao deserializar {} de {} para {}: {}",
                tipoOrigem,
                nomeEntidade,
                tipoClasse.getSimpleName(),
                e.getMessage()
            );
        }
    }

    record ParsedGraphQLPage<T>(List<T> entidades, boolean hasNextPage, String endCursor) {
    }
}
