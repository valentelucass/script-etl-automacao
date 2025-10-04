package br.com.extrator.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Classe utilitária para executar requisições HTTP com políticas de throttling e retry.
 * Garante um espaçamento mínimo entre requisições e implementa uma estratégia de
 * backoff exponencial com jitter para erros de limite de taxa (HTTP 429).
 */
public final class UtilitarioHttpRetry {

    private static final Logger logger = LoggerFactory.getLogger(UtilitarioHttpRetry.class);
    private static final Random random = new Random();

    // Timestamp da última requisição para controle de throttling
    private static long timestampUltimaRequisicao = 0;

    /**
     * Construtor privado para impedir a instanciação da classe utilitária.
     */
    private UtilitarioHttpRetry() {
    }

    /**
     * Executa uma requisição HTTP aplicando as políticas de throttling e retry.
     *
     * @param cliente          O HttpClient a ser usado.
     * @param fornecedorRequisicao Uma função que fornece a HttpRequest a ser enviada.
     * Isso garante que a requisição seja recriada a cada tentativa, se necessário.
     * @param nomeOperacao     Um nome descritivo para a operação, usado para logs.
     * @return A HttpResponse em caso de sucesso.
     * @throws IOException          Se ocorrer um erro de I/O irrecuperável.
     * @throws InterruptedException Se a thread for interrompida durante as pausas.
     */
    public static HttpResponse<String> executarComRetry(HttpClient cliente, Supplier<HttpRequest> fornecedorRequisicao, String nomeOperacao)
            throws IOException, InterruptedException {

        // Carrega as configurações de retry do arquivo de propriedades
        final int maximoTentativas = CarregadorConfig.obterMaxTentativasRetry();
        final long delayBaseMs = CarregadorConfig.obterDelayBaseRetry();
        final long throttlingMs = CarregadorConfig.obterThrottlingPadrao();

        HttpResponse<String> resposta = null;

        for (int tentativa = 1; tentativa <= maximoTentativas; tentativa++) {
            // Passo 1: Aplicar Throttling (espaçamento mínimo entre requisições)
            aplicarThrottling(throttlingMs);

            // A cada iteração, obtemos uma nova instância da requisição
            HttpRequest requisicao = fornecedorRequisicao.get();

            // Atualiza o timestamp *antes* de enviar a requisição
            timestampUltimaRequisicao = System.currentTimeMillis();
            
            logger.debug("Enviando requisição para '{}' (Tentativa {}/{})", nomeOperacao, tentativa, maximoTentativas);
            HttpResponse<String> respostaAtual = cliente.send(requisicao, HttpResponse.BodyHandlers.ofString());

            // Se a resposta for bem-sucedida (2xx), retorna imediatamente.
            if (respostaAtual.statusCode() >= 200 && respostaAtual.statusCode() < 300) {
                if (tentativa > 1) {
                    logger.info("✅ Sucesso na tentativa {}/{} para '{}'", tentativa, maximoTentativas, nomeOperacao);
                }
                return respostaAtual;
            }

            // Se for erro 429 (Too Many Requests), implementa a lógica de backoff.
            if (respostaAtual.statusCode() == 429) {
                resposta = respostaAtual; // Guarda a última resposta de erro
                if (tentativa < maximoTentativas) {
                    long tempoDeEspera = calcularTempoDeEsperaBackoff(tentativa, delayBaseMs);
                    logger.warn("⚠️ HTTP 429 para '{}' (Tentativa {}/{}). Aguardando {}ms...",
                            nomeOperacao, tentativa, maximoTentativas, tempoDeEspera);
                    Thread.sleep(tempoDeEspera);
                } else {
                    logger.error("❌ Limite de taxa excedido para '{}' após {} tentativas.", nomeOperacao, maximoTentativas);
                    // Lança uma exceção para indicar falha permanente após todas as tentativas
                    throw new IOException("Rate limit excedido após " + maximoTentativas + " tentativas para " + nomeOperacao);
                }
            } else {
                // Para qualquer outro erro (401, 404, 500, etc.), falha imediatamente.
                logger.error("❌ Erro HTTP {} inesperado para '{}'. Abortando retentativas.", respostaAtual.statusCode(), nomeOperacao);
                return respostaAtual;
            }
        }
        
        // Retorna a última resposta de erro se todas as tentativas falharem com 429
        return resposta;
    }

    /**
     * Garante que um tempo mínimo se passou desde a última requisição.
     *
     * @param throttlingMs O tempo mínimo de espaçamento em milissegundos.
     * @throws InterruptedException Se a thread for interrompida.
     */
    private static synchronized void aplicarThrottling(long throttlingMs) throws InterruptedException {
        long agora = System.currentTimeMillis();
        long tempoDesdeUltimaRequisicao = agora - timestampUltimaRequisicao;

        if (tempoDesdeUltimaRequisicao < throttlingMs) {
            long tempoDeEspera = throttlingMs - tempoDesdeUltimaRequisicao;
            if (tempoDeEspera > 0) {
                logger.debug("Aplicando throttling. Aguardando {}ms.", tempoDeEspera);
                Thread.sleep(tempoDeEspera);
            }
        }
    }

    /**
     * Calcula o tempo de espera para a próxima tentativa usando backoff exponencial com jitter.
     * Fórmula: (delayBase * 2^(tentativa - 1)) + jitter
     *
     * @param tentativa      O número da tentativa atual (começando em 1).
     * @param delayBaseMs    O tempo de espera inicial.
     * @return O tempo de espera calculado em milissegundos.
     */
    private static long calcularTempoDeEsperaBackoff(int tentativa, long delayBaseMs) {
        // Calcula o backoff exponencial. Ex: 2s, 4s, 8s...
        long backoffExponencial = delayBaseMs * (long) Math.pow(2, tentativa - 1);
        
        // Adiciona jitter (um valor aleatório) para evitar o efeito "thundering herd".
        // O jitter será de até 1000ms a mais.
        long jitter = random.nextInt(1001);

        return backoffExponencial + jitter;
    }
}