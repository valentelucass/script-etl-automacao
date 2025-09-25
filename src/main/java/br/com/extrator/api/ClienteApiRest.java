package br.com.extrator.api;

import br.com.extrator.modelo.EntidadeDinamica;
import br.com.extrator.util.CarregadorConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe responsável pela comunicação com a API REST do ESL Cloud
 * Especializada em buscar Faturas e Ocorrências via endpoints REST
 */
public class ClienteApiRest {
    private static final Logger logger = LoggerFactory.getLogger(ClienteApiRest.class);
    private final String urlBase;
    private final String token;
    private final HttpClient clienteHttp;
    private final ObjectMapper mapeadorJson;

    /**
     * Construtor que inicializa o cliente HTTP e carrega as configurações da API
     * REST
     */
    public ClienteApiRest() {
        this.urlBase = CarregadorConfig.obterUrlBaseApi();
        this.token = CarregadorConfig.obterTokenApiRest();
        this.clienteHttp = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapeadorJson = new ObjectMapper();
    }

    /**
     * Busca faturas da API REST ESL Cloud com paginação
     * 
     * @param dataInicio Data de início para busca (formato ISO:
     *                   yyyy-MM-dd'T'HH:mm:ss)
     * @return Lista de entidades encontradas
     */
    public List<EntidadeDinamica> buscarFaturasAReceber(String dataInicio) {
        return buscarEntidades("/api/accounting/credit/billings", dataInicio, "faturas_a_receber");
    }

    /**
     * Busca faturas a PAGAR da API REST.
     * Endpoint sugerido baseado na documentação do projeto.
     * 
     * @param dataInicio Data de início para busca (formato ISO:
     *                   yyyy-MM-dd'T'HH:mm:ss)
     * @return Lista de entidades encontradas
     */
    public List<EntidadeDinamica> buscarFaturasAPagar(String dataInicio) {
        // Endpoint para Faturas a Pagar (ajustar se necessário conforme documentação da
        // API)
        return buscarEntidades("/api/accounting/debit/billings", dataInicio, "faturas_a_pagar");
    }

    /**
     * Busca ocorrências da API REST ESL Cloud com paginação
     * 
     * @param dataInicio Data de início para busca (formato ISO:
     *                   yyyy-MM-dd'T'HH:mm:ss)
     * @return Lista de entidades encontradas
     */
    public List<EntidadeDinamica> buscarOcorrencias(String dataInicio) {
        return buscarEntidades("/api/invoice_occurrences", dataInicio, "ocorrencias");
    }

    /**
     * Busca entidades de um endpoint específico da API REST ESL Cloud com paginação
     * 
     * @param endpoint   Endpoint específico (ex: "/api/accounting/credit/billings")
     * @param dataInicio Data de início para busca (formato ISO:
     *                   yyyy-MM-dd'T'HH:mm:ss)
     * @return Lista de entidades encontradas
     */
    public List<EntidadeDinamica> buscarEntidades(String endpoint, String dataInicio, String tipoEntidade) {
        logger.info("Iniciando busca de {} a partir de: {}", endpoint, dataInicio);
        List<EntidadeDinamica> entidades = new ArrayList<>();

        String proximoId = null;
        boolean primeiraPagina = true;

        // Validação básica de configuração
        if (urlBase == null || urlBase.isBlank() || token == null || token.isBlank()) {
            logger.error("Configurações inválidas para chamada REST (urlBase/token)");
            return entidades;
        }

        try {
            do {
                // Constrói a URL com os parâmetros adequados
                String url;
                if (primeiraPagina) {
                    url = urlBase + endpoint + "?since=" + dataInicio;
                    primeiraPagina = false;
                } else {
                    url = urlBase + endpoint + "?start=" + proximoId;
                }

                logger.debug("Fazendo requisição para: {}", url);

                // Pausa obrigatória de 2 segundos ANTES de cada requisição
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupção durante pausa pré-requisição", e);
                }

                // Cria a requisição HTTP
                HttpRequest requisicao = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .GET()
                        .timeout(Duration.ofSeconds(30))
                        .build();

                // Executa a requisição com retry para HTTP 429
                long inicioMs = System.currentTimeMillis();
                HttpResponse<String> resposta = executarRequisicaoComRetry(requisicao, tipoEntidade, endpoint);
                long duracaoMs = System.currentTimeMillis() - inicioMs;

                // Verifica se a resposta foi bem-sucedida (agora só para outros erros, não 429)
                if (resposta.statusCode() != 200) {
                    String mensagemErro = criarMensagemErroDetalhada(resposta.statusCode(), tipoEntidade, endpoint);
                    logger.error("Erro ao buscar {}. Código de status: {}, ({} ms) Body: {}", tipoEntidade,
                            resposta.statusCode(), duracaoMs, resposta.body());
                    throw new RuntimeException(mensagemErro);
                }

                // Processa a resposta JSON
                JsonNode raizJson = mapeadorJson.readTree(resposta.body());
                JsonNode dadosJson = raizJson.get("data");
                JsonNode paginacaoJson = raizJson.get("paging");

                // Extrai o próximo ID para paginação
                proximoId = paginacaoJson != null && paginacaoJson.has("next_id")
                        ? paginacaoJson.get("next_id").asText()
                        : null;

                // Converte os dados JSON em objetos EntidadeDinamica
                int entidadesNestaPagina = 0; // Nova variável para contar
                if (dadosJson != null && dadosJson.isArray()) {
                    entidadesNestaPagina = dadosJson.size(); // Conta quantos itens vieram
                    for (JsonNode entidadeJson : dadosJson) {
                        try {
                            // Cria uma nova entidade dinâmica
                            EntidadeDinamica entidade = new EntidadeDinamica(tipoEntidade);

                            // Processa cada campo do JSON
                            entidadeJson.fields().forEachRemaining(campo -> {
                                String nomeCampo = campo.getKey();
                                JsonNode valorCampo = campo.getValue();

                                // Converte o valor do campo para o tipo apropriado
                                Object valor;
                                if (valorCampo.isTextual()) {
                                    valor = valorCampo.asText();
                                } else if (valorCampo.isNumber()) {
                                    valor = valorCampo.isInt() ? valorCampo.asInt() : valorCampo.asDouble();
                                } else if (valorCampo.isBoolean()) {
                                    valor = valorCampo.asBoolean();
                                } else {
                                    valor = valorCampo.toString();
                                }

                                entidade.adicionarCampo(nomeCampo, valor);
                            });

                            entidades.add(entidade);
                        } catch (Exception e) {
                            logger.warn("Erro ao processar {}: {}", tipoEntidade, e.getMessage());
                        }
                    }
                }

                logger.info("Processadas {} entidades nesta página ({} ms)", entidadesNestaPagina, duracaoMs);

                // CONDIÇÃO DE PARAGEM MELHORADA
                if (entidadesNestaPagina == 0) {
                    proximoId = null; // Força a paragem do loop se não vierem mais dados
                }

            } while (proximoId != null);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Execução interrompida durante a comunicação com a API", e);
            throw new RuntimeException("Execução interrompida durante comunicação com a API ESL Cloud", e);
        } catch (IOException e) {
            logger.error("Erro de I/O durante a comunicação com a API", e);
            throw new RuntimeException("Erro de I/O ao comunicar com a API ESL Cloud", e);
        }

        logger.info("Busca de {} concluída. Total de entidades encontradas: {}", endpoint, entidades.size());
        return entidades;
    }

    /**
     * Busca faturas das últimas 24 horas
     * 
     * @return Lista de entidades encontradas
     */
    public List<EntidadeDinamica> buscarFaturasUltimas24Horas() {
        LocalDateTime dataInicio = LocalDateTime.now().minusHours(24);
        String dataInicioFormatada = dataInicio.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return buscarFaturasAReceber(dataInicioFormatada);
    }

    /**
     * Busca faturas a PAGAR das últimas 24 horas
     * 
     * @return Lista de entidades encontradas
     */
    public List<EntidadeDinamica> buscarFaturasAPagarUltimas24Horas() {
        LocalDateTime dataInicio = LocalDateTime.now().minusHours(24);
        String dataInicioFormatada = dataInicio.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return buscarFaturasAPagar(dataInicioFormatada);
    }

    /**
     * Busca ocorrências das últimas 24 horas
     * 
     * @return Lista de entidades encontradas
     */
    public List<EntidadeDinamica> buscarOcorrenciasUltimas24Horas() {
        LocalDateTime dataInicio = LocalDateTime.now().minusHours(24);
        String dataInicioFormatada = dataInicio.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return buscarOcorrencias(dataInicioFormatada);
    }

    /**
     * Valida se as credenciais de acesso à API ESL estão funcionando
     * 
     * @return true se a validação foi bem-sucedida, false caso contrário
     */
    public boolean validarAcessoApi() {
        logger.info("Validando acesso à API ESL Cloud...");

        // Lista de endpoints para testar (do mais específico para o mais geral)
        String[] endpointsParaTestar = {
                "/api/v1/invoices?limit=1",
                "/api/invoices?limit=1",
                "/invoices?limit=1",
                "/api/v1/invoice",
                "/api/invoice",
                "/api/v1",
                "/api",
                "/"
        };

        for (String endpoint : endpointsParaTestar) {
            try {
                String url = urlBase + endpoint;
                logger.info("Testando endpoint: {}", url);

                HttpRequest requisicao = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> resposta = clienteHttp.send(requisicao, HttpResponse.BodyHandlers.ofString());

                logger.info("Resposta do endpoint {}: Status={}, Body_Length={}",
                        endpoint, resposta.statusCode(), resposta.body().length());

                // Verifica se a resposta foi bem-sucedida
                if (resposta.statusCode() == 200) {
                    logger.info("✅ Validação bem-sucedida! Endpoint funcional: {}", endpoint);
                    return true;
                } else if (resposta.statusCode() == 401) {
                    logger.error("❌ Erro de autenticação! Token inválido ou expirado. Endpoint: {}", endpoint);
                    return false;
                } else if (resposta.statusCode() == 403) {
                    logger.error("❌ Erro de autorização! Token sem permissões. Endpoint: {}", endpoint);
                    return false;
                } else if (resposta.statusCode() == 404) {
                    logger.warn("⚠️  Endpoint não encontrado: {} (Testando próximo...)", endpoint);
                    // Continua testando outros endpoints
                } else if (resposta.statusCode() == 405) {
                    logger.warn("⚠️  Método não permitido: {} (Endpoint existe, mas GET não é suportado)", endpoint);
                    // Ainda assim, significa que a API está acessível
                    logger.info("✅ API acessível (endpoint existe): {}", endpoint);
                    return true;
                } else {
                    logger.warn("⚠️  Resposta inesperada do endpoint {}: Status={}", endpoint, resposta.statusCode());
                }

            } catch (IOException | InterruptedException e) {
                logger.warn("⚠️  Erro ao testar endpoint {}: {}", endpoint, e.getMessage());
                // Continua testando outros endpoints
            }
        }

        logger.error("❌ Nenhum endpoint válido encontrado. Verifique a URL base e o token.");
        return false;
    }

    /**
     * Cria uma mensagem de erro detalhada baseada no código de status HTTP
     * 
     * @param statusCode   Código de status HTTP
     * @param tipoEntidade Tipo da entidade sendo buscada
     * @param endpoint     Endpoint que falhou
     * @return Mensagem de erro detalhada
     */
    private String criarMensagemErroDetalhada(int statusCode, String tipoEntidade, String endpoint) {
        switch (statusCode) {
            case 401:
                return String.format("❌ ERRO DE AUTENTICAÇÃO (HTTP 401)\n" +
                        "Endpoint: %s\n" +
                        "Problema: Token de acesso inválido, expirado ou sem permissões para acessar '%s'\n" +
                        "Soluções:\n" +
                        "  • Verifique se o token no config.properties está correto\n" +
                        "  • Confirme se o token não expirou\n" +
                        "  • Solicite permissões de leitura para o endpoint '%s' à equipe da plataforma\n" +
                        "  • Consulte a documentação da API para verificar os endpoints disponíveis",
                        endpoint, tipoEntidade, endpoint);

            case 403:
                return String.format("❌ ERRO DE AUTORIZAÇÃO (HTTP 403)\n" +
                        "Endpoint: %s\n" +
                        "Problema: Token válido mas sem permissões suficientes para acessar '%s'\n" +
                        "Soluções:\n" +
                        "  • Solicite permissões de leitura para o endpoint '%s' à equipe da plataforma\n" +
                        "  • Verifique se sua conta tem acesso aos dados de '%s'",
                        endpoint, tipoEntidade, endpoint, tipoEntidade);

            case 404:
                return String.format("❌ ERRO DE ENDPOINT NÃO ENCONTRADO (HTTP 404)\n" +
                        "Endpoint: %s\n" +
                        "Problema: O endpoint solicitado não existe ou foi movido\n" +
                        "Soluções:\n" +
                        "  • Verifique se a URL base no config.properties está correta\n" +
                        "  • Confirme se o endpoint '%s' existe na documentação da API\n" +
                        "  • Verifique se não há erros de digitação no endpoint",
                        endpoint, endpoint);

            case 500:
                return String.format("❌ ERRO INTERNO DO SERVIDOR (HTTP 500)\n" +
                        "Endpoint: %s\n" +
                        "Problema: Erro interno no servidor da API\n" +
                        "Soluções:\n" +
                        "  • Verifique se os parâmetros enviados estão no formato correto\n" +
                        "  • Tente novamente em alguns minutos\n" +
                        "  • Entre em contato com o suporte técnico se o problema persistir",
                        endpoint);

            case 406:
                return String.format("❌ ERRO DE FORMATO NÃO ACEITÁVEL (HTTP 406)\n" +
                        "Endpoint: %s\n" +
                        "Problema: Formato dos dados enviados não é aceito pela API\n" +
                        "Soluções:\n" +
                        "  • Verifique se a data está no formato correto (yyyy-MM-ddTHH:mm:ss)\n" +
                        "  • Confirme se os headers da requisição estão corretos\n" +
                        "  • Verifique a documentação da API para o formato esperado",
                        endpoint);

            default:
                return String.format("❌ ERRO HTTP %d\n" +
                        "Endpoint: %s\n" +
                        "Problema: Erro inesperado ao buscar '%s'\n" +
                        "Solução: Verifique os logs para mais detalhes e consulte a documentação da API",
                        statusCode, endpoint, tipoEntidade);
        }
    }

    /**
     * Executa uma requisição HTTP com retry automático para erros 429 (Too Many Requests)
     * Implementa exponential backoff: 2s, 4s, 6s, 8s
     * 
     * @param requisicao HttpRequest a ser executada
     * @param tipoEntidade Tipo da entidade para logging
     * @param endpoint Endpoint para logging
     * @return HttpResponse da requisição bem-sucedida
     * @throws RuntimeException se todas as tentativas falharem
     */
    private HttpResponse<String> executarRequisicaoComRetry(HttpRequest requisicao, String tipoEntidade, String endpoint) 
            throws IOException, InterruptedException {
        
        final int MAX_TENTATIVAS = CarregadorConfig.obterMaxTentativasRetry();
        
        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                logger.debug("Tentativa {}/{} para {}", tentativa, MAX_TENTATIVAS, endpoint);
                
                long inicioMs = System.currentTimeMillis();
                HttpResponse<String> resposta = clienteHttp.send(requisicao, HttpResponse.BodyHandlers.ofString());
                long duracaoMs = System.currentTimeMillis() - inicioMs;
                
                // Se a resposta for bem-sucedida, retorna imediatamente
                if (resposta.statusCode() == 200) {
                    if (tentativa > 1) {
                        logger.info("✅ Requisição bem-sucedida na tentativa {}/{} para {} ({} ms)", 
                                tentativa, MAX_TENTATIVAS, tipoEntidade, duracaoMs);
                    }
                    return resposta;
                }
                
                // Se for erro 429 (Too Many Requests), implementa retry com backoff
                if (resposta.statusCode() == 429) {
                    if (tentativa < MAX_TENTATIVAS) {
                        long tempoEspera = tentativa * CarregadorConfig.obterDelayBaseRetry();
                        logger.warn("⚠️  HTTP 429 (Too Many Requests) na tentativa {}/{}. " +
                                "Aguardando {} ms antes da próxima tentativa... Body: {}", 
                                tentativa, MAX_TENTATIVAS, tempoEspera, resposta.body());
                        
                        // Exponential backoff: 2s, 4s, 6s, 8s
                        Thread.sleep(tempoEspera);
                        continue;
                    } else {
                        // Última tentativa falhou com 429
                        logger.error("❌ Todas as {} tentativas falharam com HTTP 429. " +
                                "Rate limit persistente para {}. Body: {}", 
                                MAX_TENTATIVAS, tipoEntidade, resposta.body());
                        throw new RuntimeException(String.format(
                                "Rate limit excedido após %d tentativas para %s. " +
                                "Verifique se há outras instâncias do aplicativo rodando ou " +
                                "se o rate limit da API foi alterado.", 
                                MAX_TENTATIVAS, tipoEntidade));
                    }
                }
                
                // Para qualquer outro erro (401, 404, 500, etc.), falha imediatamente
                logger.debug("Erro HTTP {} na tentativa {}, falhando imediatamente", 
                        resposta.statusCode(), tentativa);
                return resposta;
                
            } catch (IOException | InterruptedException e) {
                if (tentativa == MAX_TENTATIVAS) {
                    logger.error("Erro de I/O na última tentativa para {}: {}", tipoEntidade, e.getMessage());
                    throw e;
                }
                logger.warn("Erro de I/O na tentativa {}/{} para {}: {}. Tentando novamente...", 
                        tentativa, MAX_TENTATIVAS, tipoEntidade, e.getMessage());
                
                // Pequena pausa antes de tentar novamente em caso de erro de I/O
                Thread.sleep(1000);
            }
        }
        
        // Este ponto nunca deve ser alcançado, mas incluído por segurança
        throw new RuntimeException("Erro inesperado no retry logic para " + tipoEntidade);
    }
}