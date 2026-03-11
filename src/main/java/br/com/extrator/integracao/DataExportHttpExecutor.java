package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/DataExportHttpExecutor.java
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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;

import br.com.extrator.suporte.http.GerenciadorRequisicaoHttp;

final class DataExportHttpExecutor {
    private static final String MODO_GET_CORPO = "corpo";
    private static final String MODO_GET_QUERY = "query";

    private final Logger logger;
    private final HttpClient httpClient;
    private final GerenciadorRequisicaoHttp gerenciadorRequisicao;
    private final DataExportRequestFactory requestFactory;
    private final long delayBaseTimeoutPorPaginaMs;
    private final long delayMaximoTimeoutPorPaginaMs;
    private final double jitterTimeoutPorPagina;
    private volatile String metodoDataExportEfetivo;
    private volatile String modoGetDataExportEfetivo;

    DataExportHttpExecutor(final Logger logger,
                           final HttpClient httpClient,
                           final GerenciadorRequisicaoHttp gerenciadorRequisicao,
                           final DataExportRequestFactory requestFactory,
                           final String metodoDataExportEfetivo,
                           final String modoGetDataExportEfetivo,
                           final long delayBaseTimeoutPorPaginaMs,
                           final long delayMaximoTimeoutPorPaginaMs,
                           final double jitterTimeoutPorPagina) {
        this.logger = logger;
        this.httpClient = httpClient;
        this.gerenciadorRequisicao = gerenciadorRequisicao;
        this.requestFactory = requestFactory;
        this.metodoDataExportEfetivo = metodoDataExportEfetivo;
        this.modoGetDataExportEfetivo = modoGetDataExportEfetivo;
        this.delayBaseTimeoutPorPaginaMs = delayBaseTimeoutPorPaginaMs;
        this.delayMaximoTimeoutPorPaginaMs = delayMaximoTimeoutPorPaginaMs;
        this.jitterTimeoutPorPagina = jitterTimeoutPorPagina;
    }

    HttpResponse<String> executarRequisicaoDataExportJson(final String url,
                                                          final String corpoJson,
                                                          final Duration timeout,
                                                          final String requestKey) {
        final String metodoPreferencial = metodoDataExportEfetivo;
        String metodoUsado = metodoPreferencial;
        HttpResponse<String> resposta = executarRequisicaoDataExportJsonComMetodo(
            url,
            corpoJson,
            timeout,
            requestKey,
            metodoPreferencial
        );

        if (deveTentarFallbackMetodo(resposta)) {
            final String metodoFallback = "POST".equalsIgnoreCase(metodoPreferencial) ? "GET" : "POST";
            logger.warn(
                "DataExport respondeu HTTP {} com metodo {} em {}. Tentando fallback {}.",
                resposta.statusCode(),
                metodoPreferencial,
                requestKey,
                metodoFallback
            );
            resposta = executarRequisicaoDataExportJsonComMetodo(
                url,
                corpoJson,
                timeout,
                requestKey + "-fallback-" + metodoFallback.toLowerCase(Locale.ROOT),
                metodoFallback
            );
            metodoUsado = metodoFallback;
        }
        atualizarMetodoEfetivoSeNecessario(metodoUsado, resposta);
        return resposta;
    }

    HttpResponse<String> executarRequisicaoDataExportCsv(final String url,
                                                         final String corpoJson,
                                                         final Duration timeout,
                                                         final String requestKey) {
        final String metodoPreferencial = metodoDataExportEfetivo;
        String metodoUsado = metodoPreferencial;
        HttpResponse<String> resposta = executarRequisicaoDataExportCsvComMetodo(
            url,
            corpoJson,
            timeout,
            requestKey,
            metodoPreferencial
        );

        if (deveTentarFallbackMetodo(resposta)) {
            final String metodoFallback = "POST".equalsIgnoreCase(metodoPreferencial) ? "GET" : "POST";
            logger.warn(
                "DataExport CSV respondeu HTTP {} com metodo {} em {}. Tentando fallback {}.",
                resposta.statusCode(),
                metodoPreferencial,
                requestKey,
                metodoFallback
            );
            resposta = executarRequisicaoDataExportCsvComMetodo(
                url,
                corpoJson,
                timeout,
                requestKey + "-fallback-" + metodoFallback.toLowerCase(Locale.ROOT),
                metodoFallback
            );
            metodoUsado = metodoFallback;
        }
        atualizarMetodoEfetivoSeNecessario(metodoUsado, resposta);
        return resposta;
    }

    boolean ehRespostaTimeout422(final HttpResponse<String> resposta) {
        if (resposta == null || resposta.statusCode() != 422) {
            return false;
        }
        final String corpo = resposta.body();
        if (corpo == null || corpo.isBlank()) {
            return false;
        }
        final String corpoLower = corpo.toLowerCase(Locale.ROOT);
        return corpoLower.contains("tempo limite") || corpoLower.contains("timeout");
    }

    long calcularAtrasoRetryTimeoutPagina(final int tentativaAtual) {
        final int expoente = Math.max(0, tentativaAtual - 1);
        final double atrasoExponencial = delayBaseTimeoutPorPaginaMs * Math.pow(2.0d, expoente);
        final long atrasoBase = Math.round(atrasoExponencial);
        final long atrasoLimitado = Math.min(delayMaximoTimeoutPorPaginaMs, Math.max(0L, atrasoBase));
        if (jitterTimeoutPorPagina <= 0.0d || atrasoLimitado <= 0L) {
            return atrasoLimitado;
        }
        final long faixaJitter = Math.round(atrasoLimitado * jitterTimeoutPorPagina);
        if (faixaJitter <= 0L) {
            return atrasoLimitado;
        }
        return atrasoLimitado + ThreadLocalRandom.current().nextLong(faixaJitter + 1L);
    }

    String extrairAmostraPayload(final String payload, final int limite) {
        if (payload == null || payload.isBlank()) {
            return "<vazio>";
        }
        final String normalizado = payload.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalizado.length() <= limite) {
            return normalizado;
        }
        return normalizado.substring(0, Math.max(0, limite)) + "...";
    }

    private HttpResponse<String> executarRequisicaoDataExportJsonComMetodo(final String url,
                                                                           final String corpoJson,
                                                                           final Duration timeout,
                                                                           final String requestKey,
                                                                           final String metodoHttp) {
        if (!"GET".equalsIgnoreCase(metodoHttp)) {
            return executarRequisicaoDataExportJsonComMetodo(url, corpoJson, timeout, requestKey, metodoHttp, false);
        }

        final boolean preferirQuery = MODO_GET_QUERY.equalsIgnoreCase(modoGetDataExportEfetivo);
        final HttpResponse<String> respostaPrimaria = executarRequisicaoDataExportJsonComMetodo(
            url,
            corpoJson,
            timeout,
            requestKey,
            metodoHttp,
            preferirQuery
        );
        if (!deveTentarFallbackModoGet(respostaPrimaria)) {
            atualizarModoGetEfetivoSeNecessario(preferirQuery, respostaPrimaria);
            return respostaPrimaria;
        }

        final boolean usarQueryNoFallback = !preferirQuery;
        logger.warn(
            "DataExport GET em modo {} retornou status {} para {}. Tentando modo {}.",
            descreverModoGet(preferirQuery),
            respostaPrimaria == null ? "<null>" : respostaPrimaria.statusCode(),
            requestKey,
            descreverModoGet(usarQueryNoFallback)
        );
        final HttpResponse<String> respostaFallback = executarRequisicaoDataExportJsonComMetodo(
            url,
            corpoJson,
            timeout,
            requestKey,
            metodoHttp,
            usarQueryNoFallback
        );
        if (ehRespostaSucesso(respostaFallback)) {
            atualizarModoGetEfetivoSeNecessario(usarQueryNoFallback, respostaFallback);
            return respostaFallback;
        }
        if (ehRespostaSucesso(respostaPrimaria)) {
            atualizarModoGetEfetivoSeNecessario(preferirQuery, respostaPrimaria);
            return respostaPrimaria;
        }
        return respostaPrimaria != null ? respostaPrimaria : respostaFallback;
    }

    private HttpResponse<String> executarRequisicaoDataExportJsonComMetodo(final String url,
                                                                           final String corpoJson,
                                                                           final Duration timeout,
                                                                           final String requestKey,
                                                                           final String metodoHttp,
                                                                           final boolean usarQueryNoGet) {
        final HttpRequest requisicao = construirRequisicaoDataExport(url, corpoJson, timeout, metodoHttp, null, usarQueryNoGet);
        final String sufixoModoGet = "GET".equalsIgnoreCase(metodoHttp) ? ("-" + descreverModoGet(usarQueryNoGet)) : "";
        return gerenciadorRequisicao.executarRequisicao(httpClient, requisicao, requestKey + "-" + metodoHttp + sufixoModoGet);
    }

    private HttpResponse<String> executarRequisicaoDataExportCsvComMetodo(final String url,
                                                                          final String corpoJson,
                                                                          final Duration timeout,
                                                                          final String requestKey,
                                                                          final String metodoHttp) {
        if (!"GET".equalsIgnoreCase(metodoHttp)) {
            return executarRequisicaoDataExportCsvComMetodo(url, corpoJson, timeout, requestKey, metodoHttp, false);
        }

        final boolean preferirQuery = MODO_GET_QUERY.equalsIgnoreCase(modoGetDataExportEfetivo);
        final HttpResponse<String> respostaPrimaria = executarRequisicaoDataExportCsvComMetodo(
            url,
            corpoJson,
            timeout,
            requestKey,
            metodoHttp,
            preferirQuery
        );
        if (!deveTentarFallbackModoGet(respostaPrimaria)) {
            atualizarModoGetEfetivoSeNecessario(preferirQuery, respostaPrimaria);
            return respostaPrimaria;
        }

        final boolean usarQueryNoFallback = !preferirQuery;
        logger.warn(
            "DataExport CSV GET em modo {} retornou status {} para {}. Tentando modo {}.",
            descreverModoGet(preferirQuery),
            respostaPrimaria == null ? "<null>" : respostaPrimaria.statusCode(),
            requestKey,
            descreverModoGet(usarQueryNoFallback)
        );
        final HttpResponse<String> respostaFallback = executarRequisicaoDataExportCsvComMetodo(
            url,
            corpoJson,
            timeout,
            requestKey,
            metodoHttp,
            usarQueryNoFallback
        );
        if (ehRespostaSucesso(respostaFallback)) {
            atualizarModoGetEfetivoSeNecessario(usarQueryNoFallback, respostaFallback);
            return respostaFallback;
        }
        if (ehRespostaSucesso(respostaPrimaria)) {
            atualizarModoGetEfetivoSeNecessario(preferirQuery, respostaPrimaria);
            return respostaPrimaria;
        }
        return respostaPrimaria != null ? respostaPrimaria : respostaFallback;
    }

    private HttpResponse<String> executarRequisicaoDataExportCsvComMetodo(final String url,
                                                                          final String corpoJson,
                                                                          final Duration timeout,
                                                                          final String requestKey,
                                                                          final String metodoHttp,
                                                                          final boolean usarQueryNoGet) {
        final HttpRequest requisicao = construirRequisicaoDataExport(url, corpoJson, timeout, metodoHttp, "text/csv", usarQueryNoGet);
        final String sufixoModoGet = "GET".equalsIgnoreCase(metodoHttp) ? ("-" + descreverModoGet(usarQueryNoGet)) : "";
        return gerenciadorRequisicao.executarRequisicaoComCharset(
            httpClient,
            requisicao,
            requestKey + "-" + metodoHttp + sufixoModoGet,
            StandardCharsets.ISO_8859_1
        );
    }

    private void atualizarMetodoEfetivoSeNecessario(final String metodoUsado,
                                                    final HttpResponse<String> resposta) {
        if (!ehRespostaSucesso(resposta)) {
            return;
        }
        final String metodoNovo = "GET".equalsIgnoreCase(metodoUsado) ? "GET" : "POST";
        if (!metodoNovo.equalsIgnoreCase(metodoDataExportEfetivo)) {
            logger.info(
                "Metodo HTTP DataExport ajustado automaticamente de {} para {} apos resposta bem-sucedida.",
                metodoDataExportEfetivo,
                metodoNovo
            );
            metodoDataExportEfetivo = metodoNovo;
        }
    }

    private void atualizarModoGetEfetivoSeNecessario(final boolean usandoQueryNoGet,
                                                     final HttpResponse<String> resposta) {
        if (!ehRespostaSucesso(resposta)) {
            return;
        }
        final String novoModo = usandoQueryNoGet ? MODO_GET_QUERY : MODO_GET_CORPO;
        if (!novoModo.equalsIgnoreCase(modoGetDataExportEfetivo)) {
            logger.info(
                "Modo GET DataExport ajustado automaticamente de {} para {} apos resposta bem-sucedida.",
                modoGetDataExportEfetivo,
                novoModo
            );
            modoGetDataExportEfetivo = novoModo;
        }
    }

    private boolean ehRespostaSucesso(final HttpResponse<String> resposta) {
        return resposta != null && resposta.statusCode() >= 200 && resposta.statusCode() < 300;
    }

    private boolean deveTentarFallbackModoGet(final HttpResponse<String> resposta) {
        if (resposta == null) {
            return true;
        }
        final int status = resposta.statusCode();
        return status == 400 || status == 404 || status == 405 || status == 411 || status == 415 || status == 501;
    }

    private String descreverModoGet(final boolean usarQueryNoGet) {
        return usarQueryNoGet ? MODO_GET_QUERY : MODO_GET_CORPO;
    }

    private HttpRequest construirRequisicaoDataExport(final String url,
                                                      final String corpoJson,
                                                      final Duration timeout,
                                                      final String metodoHttp,
                                                      final String acceptHeader,
                                                      final boolean usarQueryNoGet) {
        return requestFactory.construirRequisicao(url, corpoJson, timeout, metodoHttp, acceptHeader, usarQueryNoGet);
    }

    private boolean deveTentarFallbackMetodo(final HttpResponse<String> resposta) {
        if (resposta == null) {
            return false;
        }
        final int status = resposta.statusCode();
        return status == 404 || status == 405 || status == 415 || status == 501;
    }
}
