package br.com.extrator.api;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.extrator.modelo.EntidadeDinamica;
import br.com.extrator.util.CarregadorConfig;

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
     * Busca faturas a RECEBER da API REST.
     * Endpoint sugerido baseado na documentação do projeto.
     * 
     * @param dataInicio Data de início para busca (formato ISO:
     *                   yyyy-MM-dd'T'HH:mm:ss)
     * @param modoTeste  Se verdadeiro, limita a busca a 5 registros
     * @return Lista de entidades encontradas
     */
    public List<EntidadeDinamica> buscarFaturasAReceber(String dataInicio, boolean modoTeste) {
        return buscarEntidades("/api/accounting/credit/billings", dataInicio, "faturas_a_receber", modoTeste);
    }

    /**
     * Busca faturas a PAGAR da API REST.
     * Endpoint sugerido baseado na documentação do projeto.
     * 
     * @param dataInicio Data de início para busca (formato ISO:
     *                   yyyy-MM-dd'T'HH:mm:ss)
     * @param modoTeste  Se verdadeiro, limita a busca a 5 registros
     * @return Lista de entidades encontradas
     */
    public List<EntidadeDinamica> buscarFaturasAPagar(String dataInicio, boolean modoTeste) {
        // Endpoint para Faturas a Pagar (ajustar se necessário conforme documentação da
        // API)
        return buscarEntidades("/api/accounting/debit/billings", dataInicio, "faturas_a_pagar", modoTeste);
    }

    /**
     * Busca ocorrências da API REST ESL Cloud com paginação
     * 
     * @param dataInicio Data de início para busca (formato ISO:
     *                   yyyy-MM-dd'T'HH:mm:ss)
     * @param modoTeste  Se verdadeiro, limita a busca a 5 registros
     * @return Lista de entidades encontradas
     */
    public List<EntidadeDinamica> buscarOcorrencias(String dataInicio, boolean modoTeste) {
        return buscarEntidades("/api/invoice_occurrences", dataInicio, "ocorrencias", modoTeste);
    }

    /**
     * Busca entidades de um endpoint específico da API REST ESL Cloud com paginação
     * 
     * @param endpoint     Endpoint específico (ex:
     *                     "/api/accounting/credit/billings")
     * @param dataInicio   Data de início para busca (formato ISO:
     *                     yyyy-MM-dd'T'HH:mm:ss)
     * @param tipoEntidade Tipo da entidade para logs
     * @param modoTeste    Se verdadeiro, limita a busca a 5 registros e desativa
     *                     paginação
     * @return Lista de entidades encontradas
     */
    public List<EntidadeDinamica> buscarEntidades(String endpoint, String dataInicio, String tipoEntidade,
            boolean modoTeste) {
        logger.info("Iniciando busca de {} a partir de: {} (Modo Teste: {})", endpoint, dataInicio, modoTeste);
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
                    // --- INÍCIO DA CORREÇÃO FINAL ---
                    // Converte a data de início para o formato YYYY-MM-DD
                    LocalDateTime dataDeBusca = LocalDateTime.parse(dataInicio);
                    String dataFim = dataDeBusca.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    String dataComeco = dataDeBusca.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                    // Lógica de parâmetros por endpoint
                    if (endpoint.contains("credit/billings")) { // Faturas a Receber
                        url = urlBase + endpoint + "?created_at_start=" + dataComeco + "&created_at_end=" + dataFim;
                    } else if (endpoint.contains("debit/billings")) { // Faturas a Pagar
                        url = urlBase + endpoint + "?issue_date_start=" + dataComeco + "&issue_date_end=" + dataFim;
                    } else if (endpoint.contains("invoice_occurrences")) { // Ocorrências
                        url = urlBase + endpoint + "?date_start=" + dataComeco + "&date_end=" + dataFim;
                    } else { // Fallback para um padrão antigo, se necessário
                        url = urlBase + endpoint + "?since=" + dataInicio;
                    }
                    // --- FIM DA CORREÇÃO FINAL ---

                    // Adiciona limite se estiver em modo de teste
                    if (modoTeste) {
                        url += "&per=5";
                    }
                    primeiraPagina = false;
                } else {
                    url = urlBase + endpoint + "?start=" + proximoId;
                    // Adiciona limite se estiver em modo de teste
                    if (modoTeste) {
                        url += "&per=5";
                    }
                }

                logger.debug("Fazendo requisição para: {}", url);

                // Pausa obrigatória de 2 segundos ANTES de cada requisição

                long inicioMs = System.currentTimeMillis();

                // Cria a requisição HTTP como um Supplier para ser passada ao utilitário de
                // retry
                final String finalUrl = url; // Variável precisa ser final ou efetivamente final para ser usada na
                                             // lambda
                java.util.function.Supplier<HttpRequest> fornecedorRequisicao = () -> HttpRequest.newBuilder()
                        .uri(URI.create(finalUrl))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .GET()
                        .timeout(Duration.ofSeconds(30))
                        .build();

                // Executa a requisição usando o novo utilitário com throttling e backoff
                // exponencial
                HttpResponse<String> resposta = br.com.extrator.util.UtilitarioHttpRetry.executarComRetry(
                        this.clienteHttp,
                        fornecedorRequisicao,
                        tipoEntidade);

                long duracaoMs = System.currentTimeMillis() - inicioMs;

                // A verificação de erro para resposta nula foi corrigida para não usar a
                // variável inexistente.
                if (resposta == null) {
                    logger.error(
                            "Erro irrecuperável: a resposta da requisição é nula para {} após todas as tentativas.",
                            tipoEntidade);
                    throw new RuntimeException("Falha na requisição: resposta é null após todas as tentativas.");
                }

                // Verifica se a resposta foi bem-sucedida (para outros erros que não são 429)
                if (resposta != null && resposta.statusCode() != 200) {
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
                            entidadeJson.properties().forEach(campo -> {
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

                // Se estiver em modo de teste, força a parada após a primeira página
                if (modoTeste) {
                    logger.info("Modo de teste ativo: parando após primeira página");
                    proximoId = null;
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
        return buscarFaturasAReceber(dataInicioFormatada, false);
    }

    /**
     * Busca faturas a PAGAR das últimas 24 horas
     * 
     * @return Lista de entidades encontradas
     */
    public List<EntidadeDinamica> buscarFaturasAPagarUltimas24Horas() {
        LocalDateTime dataInicio = LocalDateTime.now().minusHours(24);
        String dataInicioFormatada = dataInicio.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return buscarFaturasAPagar(dataInicioFormatada, false);
    }

    /**
     * Busca ocorrências das últimas 24 horas
     * 
     * @return Lista de entidades encontradas
     */
    public List<EntidadeDinamica> buscarOcorrenciasUltimas24Horas() {
        LocalDateTime dataInicio = LocalDateTime.now().minusHours(24);
        String dataInicioFormatada = dataInicio.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return buscarOcorrencias(dataInicioFormatada, false);
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
                switch (resposta.statusCode()) {
                    case 200:
                        logger.info("✅ Validação bem-sucedida! Endpoint funcional: {}", endpoint);
                        return true;
                    case 401:
                        logger.error("❌ Erro de autenticação! Token inválido ou expirado. Endpoint: {}", endpoint);
                        return false;
                    case 403:
                        logger.error("❌ Erro de autorização! Token sem permissões. Endpoint: {}", endpoint);
                        return false;
                    case 404:
                        logger.warn("⚠️  Endpoint não encontrado: {} (Testando próximo...)", endpoint);
                        // Continua testando outros endpoints
                        break;
                    case 405:
                        logger.warn("⚠️  Método não permitido: {} (Endpoint existe, mas GET não é suportado)",
                                endpoint);
                        // Ainda assim, significa que a API está acessível
                        logger.info("✅ API acessível (endpoint existe): {}", endpoint);
                        return true;
                    default:
                        logger.warn("⚠️  Resposta inesperada do endpoint {}: Status={}", endpoint,
                                resposta.statusCode());
                        break;
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

}