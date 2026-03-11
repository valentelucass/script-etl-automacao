package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/DataExportRequestFactory.java
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
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.extrator.suporte.mapeamento.MapperUtil;

final class DataExportRequestFactory {
    private static final Logger logger = LoggerFactory.getLogger(DataExportRequestFactory.class);

    private final String token;

    DataExportRequestFactory(final String token) {
        this.token = token;
    }

    HttpRequest construirRequisicao(final String url,
                                    final String corpoJson,
                                    final Duration timeout,
                                    final String metodoHttp,
                                    final String acceptHeader,
                                    final boolean usarQueryNoGet) {
        final String metodo = metodoHttp == null ? "POST" : metodoHttp.trim().toUpperCase(Locale.ROOT);
        final String urlFinal = "GET".equals(metodo) && usarQueryNoGet
            ? construirUrlComQueryParaGet(url, corpoJson)
            : url;
        final HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(urlFinal))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .timeout(timeout);
        if (acceptHeader != null && !acceptHeader.isBlank()) {
            builder.header("Accept", acceptHeader);
        }

        return switch (metodo) {
            case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(corpoJson)).build();
            case "GET" -> usarQueryNoGet
                ? builder.GET().build()
                : builder.method("GET", HttpRequest.BodyPublishers.ofString(corpoJson)).build();
            default -> throw new IllegalArgumentException("Metodo HTTP DataExport nao suportado: " + metodo);
        };
    }

    private String construirUrlComQueryParaGet(final String urlBase, final String corpoJson) {
        if (corpoJson == null || corpoJson.isBlank()) {
            return urlBase;
        }
        try {
            final JsonNode raiz = MapperUtil.sharedJson().readTree(corpoJson);
            final StringBuilder query = new StringBuilder();

            if (raiz.has("search") && raiz.get("search").isObject()) {
                final JsonNode search = raiz.get("search");
                final var tabelas = search.fields();
                while (tabelas.hasNext()) {
                    final var tabela = tabelas.next();
                    final String nomeTabela = tabela.getKey();
                    final JsonNode filtroTabela = tabela.getValue();
                    if (filtroTabela == null || filtroTabela.isNull()) {
                        continue;
                    }
                    if (filtroTabela.isObject()) {
                        final var campos = filtroTabela.fields();
                        while (campos.hasNext()) {
                            final var campo = campos.next();
                            adicionarParametroQuery(
                                query,
                                "search[" + nomeTabela + "][" + campo.getKey() + "]",
                                extrairValorJsonComoTexto(campo.getValue())
                            );
                        }
                    } else {
                        adicionarParametroQuery(
                            query,
                            "search[" + nomeTabela + "]",
                            extrairValorJsonComoTexto(filtroTabela)
                        );
                    }
                }
            }

            if (raiz.has("page")) {
                adicionarParametroQuery(query, "page", extrairValorJsonComoTexto(raiz.get("page")));
            }
            if (raiz.has("per")) {
                adicionarParametroQuery(query, "per", extrairValorJsonComoTexto(raiz.get("per")));
            }
            if (raiz.has("order_by")) {
                adicionarParametroQuery(query, "order_by", extrairValorJsonComoTexto(raiz.get("order_by")));
            }

            if (query.length() == 0) {
                return urlBase;
            }
            return urlBase + "?" + query;
        } catch (final Exception e) {
            logger.warn(
                "Falha ao converter corpo JSON em query string GET DataExport. Fallback para URL base. erro={}",
                e.getMessage()
            );
            return urlBase;
        }
    }

    private void adicionarParametroQuery(final StringBuilder query, final String chave, final String valor) {
        if (chave == null || chave.isBlank()) {
            return;
        }
        if (query.length() > 0) {
            query.append('&');
        }
        final String valorSeguro = valor == null ? "" : valor;
        query.append(URLEncoder.encode(chave, StandardCharsets.UTF_8));
        query.append('=');
        query.append(URLEncoder.encode(valorSeguro, StandardCharsets.UTF_8));
    }

    private String extrairValorJsonComoTexto(final JsonNode valorNode) {
        if (valorNode == null || valorNode.isNull()) {
            return "";
        }
        if (valorNode.isTextual()) {
            return valorNode.asText();
        }
        return valorNode.toString();
    }
}
