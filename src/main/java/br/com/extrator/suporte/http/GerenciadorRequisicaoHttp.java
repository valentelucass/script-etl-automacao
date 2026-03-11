package br.com.extrator.suporte.http;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/suporte/http/GerenciadorRequisicaoHttp.java
Classe  : GerenciadorRequisicaoHttp (class)
Pacote  : br.com.extrator.suporte.http
Modulo  : Suporte - HTTP
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
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.suporte.ThreadUtil;
import br.com.extrator.suporte.configuracao.ConfigApi;

/**
 * Gerenciador centralizado para requests HTTP com throttling global, retry,
 * backoff exponencial e circuit breaker.
 */
public class GerenciadorRequisicaoHttp {
    private static final Logger logger = LoggerFactory.getLogger(GerenciadorRequisicaoHttp.class);
    private static final long DELAY_HTTP_429_MS = 2000L;

    private static final class CircuitBreakerState {
        private static final int FAILURE_THRESHOLD = 10;
        private static final long RESET_TIMEOUT_MS = 60_000L;

        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicLong lastFailureTime = new AtomicLong(0);
        private final AtomicBoolean isOpen = new AtomicBoolean(false);

        boolean canExecute() {
            if (!isOpen.get()) {
                return true;
            }

            final long timeSinceFailure = System.currentTimeMillis() - lastFailureTime.get();
            if (timeSinceFailure >= RESET_TIMEOUT_MS) {
                logger.warn("Circuit breaker tentando HALF-OPEN state apos {}s", timeSinceFailure / 1000);
                return true;
            }
            return false;
        }

        void recordSuccess() {
            final int previousFailures = failureCount.getAndSet(0);
            if (isOpen.getAndSet(false) || previousFailures > 0) {
                logger.info("Circuit breaker FECHADO apos sucesso (havia {} falhas)", previousFailures);
            }
        }

        void recordFailure() {
            final int failures = failureCount.incrementAndGet();
            lastFailureTime.set(System.currentTimeMillis());

            if (failures >= FAILURE_THRESHOLD && !isOpen.get()) {
                isOpen.set(true);
                logger.error(
                    "CIRCUIT BREAKER ABERTO apos {} falhas consecutivas. Requests bloqueadas por {}s",
                    failures,
                    RESET_TIMEOUT_MS / 1000
                );
            } else if (failures < FAILURE_THRESHOLD) {
                logger.warn("Falha {}/{} - Circuit ainda fechado", failures, FAILURE_THRESHOLD);
            }
        }

        long getTimeUntilReset() {
            if (!isOpen.get()) {
                return 0;
            }
            final long elapsed = System.currentTimeMillis() - lastFailureTime.get();
            final long remaining = RESET_TIMEOUT_MS - elapsed;
            return Math.max(0, remaining / 1000);
        }
    }

    private static final class Holder {
        private static final GerenciadorRequisicaoHttp INSTANCE = new GerenciadorRequisicaoHttp();
    }

    private final CircuitBreakerState circuitBreaker = new CircuitBreakerState();
    private final ReentrantLock lockThrottling = new ReentrantLock(true);
    private final AtomicLong ultimaRequisicaoTimestamp = new AtomicLong(0);

    private final int maxTentativas;
    private final long delayBaseMs;
    private final double multiplicador;
    private final long throttlingMinimoMs;

    public static GerenciadorRequisicaoHttp getInstance() {
        return Holder.INSTANCE;
    }

    private GerenciadorRequisicaoHttp() {
        this.maxTentativas = ConfigApi.obterMaxTentativasRetry();
        this.delayBaseMs = ConfigApi.obterDelayBaseRetry();
        this.multiplicador = ConfigApi.obterMultiplicadorRetry();
        this.throttlingMinimoMs = ConfigApi.obterThrottlingMinimo();

        logger.info(
            "GerenciadorRequisicaoHttp inicializado - maxTentativas={}, delayBaseMs={}, multiplicador={}, throttlingMinimoMs={}",
            maxTentativas,
            delayBaseMs,
            multiplicador,
            throttlingMinimoMs
        );
    }

    private void aguardarComTratamentoInterrupcao(final long delayMs, final String contexto) {
        try {
            ThreadUtil.aguardar(delayMs);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrompida durante " + contexto, e);
        }
    }

    private boolean deveRetentar(final int statusCode) {
        if (statusCode == 404 || statusCode == 401 || statusCode == 403) {
            return false;
        }
        if (statusCode == 429) {
            return true;
        }
        if (statusCode == 500 || statusCode == 502 || statusCode == 503) {
            return true;
        }
        if (statusCode >= 500 && statusCode <= 599) {
            return true;
        }
        if (statusCode >= 400 && statusCode <= 499) {
            return false;
        }
        return false;
    }

    public HttpResponse<String> executarRequisicao(final HttpClient cliente,
                                                   final HttpRequest requisicao,
                                                   final String tipoEntidade) {
        return executarRequisicaoInterna(
            cliente,
            requisicao,
            tipoEntidade,
            HttpResponse.BodyHandlers.ofString(),
            null,
            true
        );
    }

    public HttpResponse<String> executarRequisicaoEstrita(final HttpClient cliente,
                                                          final HttpRequest requisicao,
                                                          final String tipoEntidade) {
        return executarRequisicaoEstrita(cliente, requisicao, tipoEntidade, Collections.emptySet());
    }

    public HttpResponse<String> executarRequisicaoEstrita(final HttpClient cliente,
                                                          final HttpRequest requisicao,
                                                          final String tipoEntidade,
                                                          final Set<Integer> statusPermitidos) {
        final HttpResponse<String> resposta = executarRequisicao(cliente, requisicao, tipoEntidade);
        validarRespostaEstrita(resposta, tipoEntidade, statusPermitidos);
        return resposta;
    }

    public HttpResponse<String> executarRequisicaoComCharset(final HttpClient cliente,
                                                             final HttpRequest requisicao,
                                                             final String tipoEntidade,
                                                             final Charset charset) {
        return executarRequisicaoInterna(
            cliente,
            requisicao,
            tipoEntidade,
            HttpResponse.BodyHandlers.ofString(charset),
            charset,
            false
        );
    }

    public HttpResponse<String> executarRequisicaoComCharsetEstrita(final HttpClient cliente,
                                                                    final HttpRequest requisicao,
                                                                    final String tipoEntidade,
                                                                    final Charset charset) {
        return executarRequisicaoComCharsetEstrita(
            cliente,
            requisicao,
            tipoEntidade,
            charset,
            Collections.emptySet()
        );
    }

    public HttpResponse<String> executarRequisicaoComCharsetEstrita(final HttpClient cliente,
                                                                    final HttpRequest requisicao,
                                                                    final String tipoEntidade,
                                                                    final Charset charset,
                                                                    final Set<Integer> statusPermitidos) {
        final HttpResponse<String> resposta = executarRequisicaoComCharset(cliente, requisicao, tipoEntidade, charset);
        validarRespostaEstrita(resposta, tipoEntidade, statusPermitidos);
        return resposta;
    }

    private HttpResponse<String> executarRequisicaoInterna(final HttpClient cliente,
                                                           final HttpRequest requisicao,
                                                           final String tipoEntidade,
                                                           final HttpResponse.BodyHandler<String> bodyHandler,
                                                           final Charset charset,
                                                           final boolean detalheProtecao) {
        validarCircuitBreaker(tipoEntidade, detalheProtecao);
        aplicarThrottling();

        for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {
            try {
                registrarTentativa(tipoEntidade, tentativa, charset);
                final HttpResponse<String> resposta = cliente.send(requisicao, bodyHandler);
                final int statusCode = resposta.statusCode();

                if (statusCode >= 200 && statusCode < 300) {
                    circuitBreaker.recordSuccess();
                    logger.debug("Request bem-sucedida para {} - status={}", descreverTipoEntidade(tipoEntidade), statusCode);
                    return resposta;
                }

                if (!deveRetentar(statusCode)) {
                    registrarErroDefinitivo(tipoEntidade, statusCode, resposta.body());
                    return resposta;
                }

                tratarStatusRetentavel(tipoEntidade, tentativa, statusCode, resposta.body());
            } catch (final HttpTimeoutException e) {
                tratarTimeout(tipoEntidade, tentativa, e);
            } catch (final IOException e) {
                tratarIOException(tipoEntidade, tentativa, e);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread interrompida durante requisicao", e);
            }
        }

        final String mensagemFalha = String.format(
            "Request para %s falhou apos %d tentativas. Verifique conectividade e configuracoes da API.",
            descreverTipoEntidade(tipoEntidade),
            maxTentativas
        );
        logger.error(mensagemFalha);
        throw new RuntimeException(mensagemFalha);
    }

    private void validarCircuitBreaker(final String tipoEntidade, final boolean detalheProtecao) {
        if (!circuitBreaker.canExecute()) {
            final long timeUntilReset = circuitBreaker.getTimeUntilReset();
            final String mensagem;
            if (detalheProtecao) {
                mensagem = String.format(
                    "CIRCUIT BREAKER ABERTO - API indisponivel para %s. Sistema em protecao. Aguarde %d segundos para nova tentativa.",
                    tipoEntidade != null ? tipoEntidade : "requisicao",
                    timeUntilReset
                );
            } else {
                mensagem = String.format(
                    "CIRCUIT BREAKER ABERTO - API indisponivel para %s. Aguarde %d segundos.",
                    tipoEntidade != null ? tipoEntidade : "requisicao",
                    timeUntilReset
                );
            }
            logger.error(mensagem);
            throw new RuntimeException(mensagem);
        }
    }

    private void registrarTentativa(final String tipoEntidade, final int tentativa, final Charset charset) {
        if (charset == null) {
            logger.debug(
                "Executando requisicao para {} - tentativa {}/{}",
                descreverTipoEntidade(tipoEntidade),
                tentativa,
                maxTentativas
            );
            return;
        }

        logger.debug(
            "Executando requisicao (charset={}) para {} - tentativa {}/{}",
            charset.displayName(),
            descreverTipoEntidade(tipoEntidade),
            tentativa,
            maxTentativas
        );
    }

    private void registrarErroDefinitivo(final String tipoEntidade, final int statusCode, final String corpoResposta) {
        if (statusCode == 404) {
            logger.debug(
                "HTTP 404 para {} (esperado). Resposta: {}",
                descreverTipoEntidade(tipoEntidade),
                resumirCorpo(corpoResposta)
            );
            return;
        }

        logger.error(
            "Erro definitivo na requisicao para {} - HTTP {} (nao sera retentado). Resposta: {}",
            descreverTipoEntidade(tipoEntidade),
            statusCode,
            resumirCorpo(corpoResposta)
        );
    }

    private void tratarStatusRetentavel(final String tipoEntidade,
                                        final int tentativa,
                                        final int statusCode,
                                        final String corpoResposta) {
        circuitBreaker.recordFailure();

        if (statusCode == 429) {
            logger.warn(
                "Rate limit atingido (HTTP 429) para {} - tentativa {}/{}. Aguardando {}s...",
                descreverTipoEntidade(tipoEntidade),
                tentativa,
                maxTentativas,
                DELAY_HTTP_429_MS / 1000
            );
            if (tentativa < maxTentativas) {
                aguardarComTratamentoInterrupcao(DELAY_HTTP_429_MS, "retry apos HTTP 429");
            }
            return;
        }

        final String respostaResumida = resumirCorpo(corpoResposta);
        if (tentativa < maxTentativas) {
            logger.warn(
                "Erro de servidor (HTTP {}) para {} - tentativa {}/{}. Resposta: {}",
                statusCode,
                descreverTipoEntidade(tipoEntidade),
                tentativa,
                maxTentativas,
                respostaResumida
            );
        } else {
            logger.error(
                "Erro de servidor (HTTP {}) para {} - tentativa final {}/{}. Resposta: {}",
                statusCode,
                descreverTipoEntidade(tipoEntidade),
                tentativa,
                maxTentativas,
                respostaResumida
            );
        }

        aguardarComBackoffSeNecessario(tentativa, "backoff exponencial");
    }

    private void tratarTimeout(final String tipoEntidade, final int tentativa, final HttpTimeoutException e) {
        circuitBreaker.recordFailure();
        if (tentativa < maxTentativas) {
            logger.warn(
                "Timeout na requisicao para {} - tentativa {}/{}",
                descreverTipoEntidade(tipoEntidade),
                tentativa,
                maxTentativas
            );
        } else {
            logger.error(
                "Timeout na requisicao para {} - tentativa final {}/{}",
                descreverTipoEntidade(tipoEntidade),
                tentativa,
                maxTentativas,
                e
            );
        }

        aguardarComBackoffSeNecessario(tentativa, "retry apos timeout");
    }

    private void tratarIOException(final String tipoEntidade, final int tentativa, final IOException e) {
        circuitBreaker.recordFailure();
        if (tentativa < maxTentativas) {
            logger.warn(
                "IOException na requisicao para {} - tentativa {}/{}: {}",
                descreverTipoEntidade(tipoEntidade),
                tentativa,
                maxTentativas,
                e.getMessage()
            );
        } else {
            logger.error(
                "IOException na requisicao para {} - tentativa final {}/{}: {}",
                descreverTipoEntidade(tipoEntidade),
                tentativa,
                maxTentativas,
                e.getMessage()
            );
        }

        aguardarComBackoffSeNecessario(tentativa, "retry apos IOException");
    }

    private void aplicarThrottling() {
        lockThrottling.lock();
        try {
            final long agora = System.currentTimeMillis();
            final long ultimaRequisicao = ultimaRequisicaoTimestamp.get();
            final long tempoDecorrido = agora - ultimaRequisicao;

            if (tempoDecorrido < throttlingMinimoMs) {
                final long tempoEspera = throttlingMinimoMs - tempoDecorrido;
                logger.debug(
                    "Throttling global aplicado - espera={}ms, limite={}ms, decorrido={}ms",
                    tempoEspera,
                    throttlingMinimoMs,
                    tempoDecorrido
                );
                aguardarComTratamentoInterrupcao(tempoEspera, "throttling global");
            } else {
                logger.debug("Throttling global OK - decorrido={}ms, limite={}ms", tempoDecorrido, throttlingMinimoMs);
            }

            ultimaRequisicaoTimestamp.set(System.currentTimeMillis());
        } finally {
            lockThrottling.unlock();
        }
    }

    private void aguardarComBackoffSeNecessario(final int tentativa, final String contexto) {
        if (tentativa < maxTentativas) {
            final long delayMs = calcularDelayBackoffExponencial(tentativa);
            logger.info("Aguardando {}ms antes da proxima tentativa ({})...", delayMs, contexto);
            aguardarComTratamentoInterrupcao(delayMs, contexto);
        }
    }

    private long calcularDelayBackoffExponencial(final int tentativa) {
        final double delay = delayBaseMs * Math.pow(multiplicador, tentativa - 1);
        return Math.round(delay);
    }

    private void validarRespostaEstrita(final HttpResponse<String> resposta,
                                        final String tipoEntidade,
                                        final Set<Integer> statusPermitidos) {
        if (resposta == null) {
            final String mensagem = String.format(
                "Resposta HTTP nula para %s.",
                tipoEntidade != null ? tipoEntidade : "API"
            );
            logger.error(mensagem);
            throw new IllegalStateException(mensagem);
        }

        final int statusCode = resposta.statusCode();
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }

        final Set<Integer> permitidos = statusPermitidos == null ? Collections.emptySet() : statusPermitidos;
        if (permitidos.contains(statusCode)) {
            logger.debug("Status HTTP {} permitido explicitamente para {}.", statusCode, tipoEntidade);
            return;
        }

        final String mensagem = String.format(
            "Erro HTTP nao-sucesso para %s: status=%d, resposta=%s",
            tipoEntidade != null ? tipoEntidade : "API",
            statusCode,
            resumirResposta(resposta)
        );
        logger.error(mensagem);
        throw new IllegalStateException(mensagem);
    }

    private String resumirResposta(final HttpResponse<String> resposta) {
        if (resposta == null || resposta.body() == null) {
            return "<sem-corpo>";
        }
        return resumirCorpo(resposta.body());
    }

    private String resumirCorpo(final String corpo) {
        if (corpo == null) {
            return "<sem-corpo>";
        }
        return corpo.length() > 200 ? corpo.substring(0, 200) + "..." : corpo;
    }

    private String descreverTipoEntidade(final String tipoEntidade) {
        return tipoEntidade != null ? tipoEntidade : "API";
    }
}
