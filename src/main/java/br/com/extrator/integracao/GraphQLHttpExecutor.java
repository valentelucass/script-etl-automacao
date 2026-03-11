package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/GraphQLHttpExecutor.java
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.extrator.suporte.ThreadUtil;
import br.com.extrator.suporte.configuracao.ConfigApi;
import br.com.extrator.suporte.http.GerenciadorRequisicaoHttp;

final class GraphQLHttpExecutor {
    private final Logger logger;
    private final String urlBase;
    private final String endpointGraphQL;
    private final String token;
    private final HttpClient clienteHttp;
    private final ObjectMapper mapeadorJson;
    private final GerenciadorRequisicaoHttp gerenciadorRequisicao;
    private final GraphQLTypedResponseParser typedResponseParser;
    private final GraphQLRequestFactory requestFactory;

    GraphQLHttpExecutor(final Logger logger,
                        final String urlBase,
                        final String endpointGraphQL,
                        final String token,
                        final HttpClient clienteHttp,
                        final ObjectMapper mapeadorJson,
                        final GerenciadorRequisicaoHttp gerenciadorRequisicao,
                        final GraphQLTypedResponseParser typedResponseParser,
                        final GraphQLRequestFactory requestFactory) {
        this.logger = logger;
        this.urlBase = urlBase;
        this.endpointGraphQL = endpointGraphQL;
        this.token = token;
        this.clienteHttp = clienteHttp;
        this.mapeadorJson = mapeadorJson;
        this.gerenciadorRequisicao = gerenciadorRequisicao;
        this.typedResponseParser = typedResponseParser;
        this.requestFactory = requestFactory;
    }

    <T> PaginatedGraphQLResponse<T> executarQueryGraphQLTipado(final String query,
                                                               final String nomeEntidade,
                                                               final Map<String, Object> variaveis,
                                                               final Class<T> tipoClasse) {
        logger.debug("Executando query GraphQL tipada para {} - URL: {}{}, Vari?veis: {}",
            nomeEntidade, urlBase, endpointGraphQL, variaveis);
        final List<T> entidades = new ArrayList<>();

        if (urlBase == null || urlBase.isBlank() || token == null || token.isBlank()) {
            logger.error("Configuracoes invalidas para chamada GraphQL (urlBase/token)");
            return new PaginatedGraphQLResponse<>(entidades, false, null, 0, 0, "", "", entidades.size(), true, "CONFIG_INVALIDA");
        }

        final int maxTentativasGraphQl = Math.max(1, ConfigApi.obterMaxTentativasRetry());
        final long delayBaseMs = Math.max(250L, ConfigApi.obterDelayBaseRetry());
        final double multiplicador = Math.max(1.0d, ConfigApi.obterMultiplicadorRetry());

        for (int tentativa = 1; tentativa <= maxTentativasGraphQl; tentativa++) {
            entidades.clear();
            try {
                final long tempoInicio = System.currentTimeMillis();
                final GraphQLRequestFactory.RequestPayload payload = requestFactory.criarPost(query, variaveis);
                final String reqHash = PayloadHashUtil.sha256Hex(payload.corpoRequisicao());

                final HttpResponse<String> resposta = gerenciadorRequisicao.executarRequisicaoEstrita(
                    clienteHttp,
                    payload.requisicao(),
                    "GraphQL-" + nomeEntidade
                );
                final int statusCode = resposta != null ? resposta.statusCode() : 0;
                final int duracaoMs = (int) (System.currentTimeMillis() - tempoInicio);
                final String respHash = PayloadHashUtil.sha256Hex(resposta != null ? resposta.body() : "");

                if (resposta == null || resposta.body() == null) {
                    logger.warn("Resposta GraphQL nula para {}", nomeEntidade);
                    return new PaginatedGraphQLResponse<>(entidades, false, null, statusCode, duracaoMs, reqHash, respHash, entidades.size(), true, "RESPOSTA_NULA");
                }
                final JsonNode respostaJson = mapeadorJson.readTree(resposta.body());

                if (respostaJson.has("errors")) {
                    final JsonNode erros = respostaJson.get("errors");
                    if (deveRetentarErroGraphQl(erros) && tentativa < maxTentativasGraphQl) {
                        final long delayMs = calcularDelayBackoffGraphQl(tentativa, delayBaseMs, multiplicador);
                        logger.warn(
                            "Erro GraphQL retentavel para {} (tentativa {}/{}): {}. Aguardando {}ms para nova tentativa.",
                            nomeEntidade,
                            tentativa,
                            maxTentativasGraphQl,
                            extrairCodigoErroGraphQl(erros),
                            delayMs
                        );
                        aguardarRetryGraphQl(delayMs, nomeEntidade, tentativa, maxTentativasGraphQl);
                        continue;
                    }
                    logger.error("Erros na query GraphQL para {}: {}", nomeEntidade, erros.toString());
                    return new PaginatedGraphQLResponse<>(entidades, false, null, statusCode, duracaoMs, reqHash, respHash, entidades.size(), true, "GRAPHQL_ERRORS");
                }

                if (!respostaJson.has("data")) {
                    logger.warn("Resposta GraphQL sem campo 'data' para {}", nomeEntidade);
                    return new PaginatedGraphQLResponse<>(entidades, false, null, statusCode, duracaoMs, reqHash, respHash, entidades.size(), true, "SEM_DATA");
                }

                final JsonNode dados = respostaJson.get("data");
                if (!dados.has(nomeEntidade)) {
                    logger.warn("Campo '{}' nao encontrado na resposta GraphQL. Campos disponiveis: {}", nomeEntidade, dados.fieldNames());
                    return new PaginatedGraphQLResponse<>(entidades, false, null, statusCode, duracaoMs, reqHash, respHash, entidades.size(), true, "SEM_ENTIDADE:" + nomeEntidade);
                }

                final GraphQLTypedResponseParser.ParsedGraphQLPage<T> pagina =
                    typedResponseParser.extrairPagina(dados.get(nomeEntidade), nomeEntidade, tipoClasse);
                entidades.addAll(pagina.entidades());

                logger.debug("Query GraphQL tipada concluida para {}. Total encontrado: {}", nomeEntidade, entidades.size());
                return new PaginatedGraphQLResponse<>(
                    entidades,
                    pagina.hasNextPage(),
                    pagina.endCursor(),
                    statusCode,
                    duracaoMs,
                    reqHash,
                    respHash,
                    entidades.size(),
                    false,
                    null
                );
            } catch (final JsonProcessingException e) {
                if (tentativa < maxTentativasGraphQl) {
                    final long delayMs = calcularDelayBackoffGraphQl(tentativa, delayBaseMs, multiplicador);
                    logger.warn(
                        "Erro JSON na query GraphQL para {} (tentativa {}/{}): {}. Aguardando {}ms para nova tentativa.",
                        nomeEntidade,
                        tentativa,
                        maxTentativasGraphQl,
                        e.getMessage(),
                        delayMs
                    );
                    aguardarRetryGraphQl(delayMs, nomeEntidade, tentativa, maxTentativasGraphQl);
                    continue;
                }
                logger.error("Erro de processamento JSON durante execucao da query GraphQL para {}: {}", nomeEntidade, e.getMessage(), e);
                return new PaginatedGraphQLResponse<>(entidades, false, null, 0, 0, "", "", entidades.size(), true, "JSON_PROCESSING");
            } catch (final RuntimeException e) {
                if (tentativa < maxTentativasGraphQl) {
                    final long delayMs = calcularDelayBackoffGraphQl(tentativa, delayBaseMs, multiplicador);
                    logger.warn(
                        "Erro transitorio na query GraphQL para {} (tentativa {}/{}): {}. Aguardando {}ms para nova tentativa.",
                        nomeEntidade,
                        tentativa,
                        maxTentativasGraphQl,
                        e.getMessage(),
                        delayMs
                    );
                    aguardarRetryGraphQl(delayMs, nomeEntidade, tentativa, maxTentativasGraphQl);
                    continue;
                }
                logger.error("Erro durante execucao da query GraphQL para {}: {}", nomeEntidade, e.getMessage(), e);
                return new PaginatedGraphQLResponse<>(entidades, false, null, 0, 0, "", "", entidades.size(), true, "RUNTIME_EXCEPTION");
            }
        }

        logger.error("Falha ao executar query GraphQL para {} apos esgotar tentativas.", nomeEntidade);
        return new PaginatedGraphQLResponse<>(entidades, false, null, 0, 0, "", "", entidades.size(), true, "MAX_RETRY_EXCEDIDO");
    }

    private boolean deveRetentarErroGraphQl(final JsonNode erros) {
        if (erros == null || !erros.isArray() || erros.isEmpty()) {
            return false;
        }
        for (final JsonNode erro : erros) {
            final String codigo = erro.path("extensions").path("code").asText("");
            final String mensagem = erro.path("message").asText("").toLowerCase(Locale.ROOT);
            if ("INTERNAL_SERVER_ERROR".equalsIgnoreCase(codigo)
                    || "SERVICE_UNAVAILABLE".equalsIgnoreCase(codigo)
                    || "TIMEOUT".equalsIgnoreCase(codigo)
                    || mensagem.contains("statement timeout")
                    || mensagem.contains("querycanceled")
                    || mensagem.contains("timeout")
                    || mensagem.contains("temporar")) {
                return true;
            }
        }
        return false;
    }

    private String extrairCodigoErroGraphQl(final JsonNode erros) {
        if (erros == null || !erros.isArray() || erros.isEmpty()) {
            return "SEM_CODIGO";
        }
        final JsonNode primeiroErro = erros.get(0);
        final String codigo = primeiroErro.path("extensions").path("code").asText("");
        if (codigo != null && !codigo.isBlank()) {
            return codigo;
        }
        final String mensagem = primeiroErro.path("message").asText("");
        return mensagem == null || mensagem.isBlank() ? "SEM_CODIGO" : mensagem;
    }

    private long calcularDelayBackoffGraphQl(final int tentativa, final long delayBaseMs, final double multiplicador) {
        final double fator = Math.pow(multiplicador, Math.max(0, tentativa - 1));
        final long delayCalculado = Math.round(delayBaseMs * fator);
        return Math.min(delayCalculado, 60_000L);
    }

    private void aguardarRetryGraphQl(final long delayMs,
                                      final String nomeEntidade,
                                      final int tentativaAtual,
                                      final int maxTentativas) {
        try {
            ThreadUtil.aguardar(delayMs);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                "Thread interrompida durante retry GraphQL de " + nomeEntidade
                    + " (tentativa " + tentativaAtual + "/" + maxTentativas + ")",
                e
            );
        }
    }
}
