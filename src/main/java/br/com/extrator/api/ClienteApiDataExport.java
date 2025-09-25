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
 * Cliente especializado para a API Data Export do ESL Cloud.
 * Responsável por extrair Manifestos e Localização da Carga através do fluxo POST+GET.
 * 
 * Fluxo de funcionamento:
 * 1. POST para solicitar geração do relatório
 * 2. GET periódico para verificar status do relatório
 * 3. GET para baixar o relatório quando pronto
 * 
 * @author Sistema de Extração ESL Cloud
 * @version 1.0
 */
public class ClienteApiDataExport {
    
    private static final Logger logger = LoggerFactory.getLogger(ClienteApiDataExport.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String urlBase;
    private final String token;
    
    // IDs dos templates existentes no ESL Cloud (baseado na nova documentação)
    private static final String TEMPLATE_ID_MANIFESTOS = "relacao-manifestos-detalhada";
    private static final String TEMPLATE_ID_LOCALIZACAO = "localizador-cargas";
    private static final String TEMPLATE_ID_COTACOES = "cotacoes-detalhadas";
    private static final String TEMPLATE_ID_FRETES = "fretes-detalhados";
    
    // Configurações do fluxo Data Export
    private static final int MAX_TENTATIVAS_STATUS = 30; // Máximo 30 tentativas
    private static final int INTERVALO_VERIFICACAO_MS = 10000; // 10 segundos entre verificações
    private static final int TIMEOUT_REQUISICAO_SEGUNDOS = 60;
    
    // Rate limiting para evitar erro HTTP 429
    private static final int RATE_LIMIT_MS = 2000; // 2 segundos entre requisições
    private static long ultimaRequisicao = 0;
    
    /**
     * Construtor do cliente da API Data Export.
     * Inicializa as configurações necessárias para o fluxo POST+GET.
     */
    public ClienteApiDataExport() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_REQUISICAO_SEGUNDOS))
                .build();
        this.objectMapper = new ObjectMapper();
        this.urlBase = CarregadorConfig.obterUrlBaseApi();
        this.token = CarregadorConfig.obterTokenApiDataExport();
        
        logger.info("Cliente API Data Export inicializado para: {}", urlBase);
    }
    
    /**
     * Busca manifestos para o período especificado.
     * Implementa o fluxo completo POST+GET da API Data Export.
     * 
     * @param dataInicio Data de início da busca
     * @return Lista de manifestos encontrados
     */
    public List<EntidadeDinamica> buscarManifestos(LocalDateTime dataInicio) {
        logger.info("Iniciando busca de manifestos a partir de: {}", dataInicio);
        
        try {
            // Etapa 1: Solicitar geração do relatório
            String requestId = solicitarRelatorioManifestos(dataInicio);
            if (requestId == null) {
                logger.error("Falha ao solicitar relatório de manifestos");
                return new ArrayList<>();
            }
            
            // Etapa 2: Aguardar processamento do relatório
            String downloadUrl = aguardarProcessamentoRelatorio(requestId);
            if (downloadUrl == null) {
                logger.error("Relatório de manifestos não ficou pronto no tempo esperado");
                return new ArrayList<>();
            }
            
            // Etapa 3: Baixar e processar o relatório
            return baixarEProcessarRelatorio(downloadUrl, "manifestos");
            
        } catch (Exception e) {
            logger.error("Erro ao buscar manifestos", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Busca localização da carga para o período especificado.
     * 
     * @param dataInicio Data de início da busca
     * @return Lista de localizações encontradas
     */
    public List<EntidadeDinamica> buscarLocalizacaoCarga(LocalDateTime dataInicio) {
        logger.info("Iniciando busca de localização da carga a partir de: {}", dataInicio);
        
        try {
            // Etapa 1: Solicitar geração do relatório
            String requestId = solicitarRelatorioLocalizacao(dataInicio);
            if (requestId == null) {
                logger.error("Falha ao solicitar relatório de localização da carga");
                return new ArrayList<>();
            }
            
            // Etapa 2: Aguardar processamento do relatório
            String downloadUrl = aguardarProcessamentoRelatorio(requestId);
            if (downloadUrl == null) {
                logger.error("Relatório de localização não ficou pronto no tempo esperado");
                return new ArrayList<>();
            }
            
            // Etapa 3: Baixar e processar o relatório
            return baixarEProcessarRelatorio(downloadUrl, "localizacao_carga");
            
        } catch (Exception e) {
            logger.error("Erro ao buscar localização da carga", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Solicita a geração do relatório de manifestos via POST (versão pública).
     * 
     * @param dataInicio Data de início da busca (formato ISO: yyyy-MM-dd'T'HH:mm:ss)
     * @return Request ID do relatório ou null se falhar
     */
    public String solicitarRelatorioManifestos(String dataInicio) {
        try {
            LocalDateTime dataInicioDateTime = LocalDateTime.parse(dataInicio);
            return solicitarRelatorioManifestos(dataInicioDateTime);
        } catch (Exception e) {
            logger.error("Erro ao solicitar relatório de manifestos", e);
            return null;
        }
    }
    
    /**
     * Solicita a geração do relatório de localização da carga via POST (versão pública).
     * 
     * @param dataInicio Data de início da busca (formato ISO: yyyy-MM-dd'T'HH:mm:ss)
     * @return Request ID do relatório ou null se falhar
     */
    public String solicitarRelatorioLocalizacao(String dataInicio) {
        try {
            LocalDateTime dataInicioDateTime = LocalDateTime.parse(dataInicio);
            return solicitarRelatorioLocalizacao(dataInicioDateTime);
        } catch (Exception e) {
            logger.error("Erro ao solicitar relatório de localização da carga", e);
            return null;
        }
    }
    
    /**
     * Busca um relatório processado usando o Request ID.
     * 
     * @param requestId ID da solicitação do relatório
     * @param tipoRelatorio Tipo do relatório (manifestos, localizacao_carga)
     * @return Lista de entidades encontradas
     */
    public List<EntidadeDinamica> buscarRelatorioProcessado(String requestId, String tipoRelatorio) {
        try {
            // Aguarda processamento do relatório
            String downloadUrl = aguardarProcessamentoRelatorio(requestId);
            if (downloadUrl == null) {
                logger.error("Relatório {} não ficou pronto no tempo esperado", tipoRelatorio);
                return new ArrayList<>();
            }
            
            // Baixa e processa o relatório
            return baixarEProcessarRelatorio(downloadUrl, tipoRelatorio);
            
        } catch (Exception e) {
            logger.error("Erro ao buscar relatório processado para {}", tipoRelatorio, e);
            return new ArrayList<>();
        }
    }

    /**
     * Solicita a geração do relatório de manifestos via POST.
     */
    private String solicitarRelatorioManifestos(LocalDateTime dataInicio) throws IOException, InterruptedException {
        return solicitarRelatorioComTemplate(TEMPLATE_ID_MANIFESTOS, dataInicio, "manifestos");
    }
    
    /**
     * Solicita a geração do relatório de localização da carga via POST.
     */
    private String solicitarRelatorioLocalizacao(LocalDateTime dataInicio) throws IOException, InterruptedException {
        return solicitarRelatorioComTemplate(TEMPLATE_ID_LOCALIZACAO, dataInicio, "localizacao");
    }
    
    /**
     * Método genérico para solicitar relatórios usando templates existentes.
     * Baseado na nova documentação: GET /api/analytics/reports/{ID_TEMPLATE}/data
     */
    private String solicitarRelatorioComTemplate(String templateId, LocalDateTime dataInicio, String tipoRelatorio) throws IOException, InterruptedException {
        // Primeiro, tenta buscar diretamente os dados do template
        String endpointDireto = "/api/analytics/reports/" + templateId + "/data";
        
        try {
            List<EntidadeDinamica> dados = buscarDadosTemplate(endpointDireto, dataInicio);
            if (!dados.isEmpty()) {
                logger.info("Dados obtidos diretamente do template {} para {}: {} registros", templateId, tipoRelatorio, dados.size());
                // Para compatibilidade com o fluxo existente, retornamos um ID fictício
                return "direct-" + templateId + "-" + System.currentTimeMillis();
            }
        } catch (Exception e) {
            logger.debug("Busca direta do template {} falhou, tentando fluxo tradicional: {}", templateId, e.getMessage());
        }
        
        // Se a busca direta falhar, tenta o fluxo tradicional POST+GET
        return solicitarRelatorioFluxoTradicional(templateId, dataInicio, tipoRelatorio);
    }
    
    /**
     * Busca dados diretamente do template usando o endpoint correto da nova documentação.
     * Primeiro tenta obter o ID numérico do template, depois busca os dados.
     */
    private List<EntidadeDinamica> buscarDadosTemplate(String endpoint, LocalDateTime dataInicio) throws IOException, InterruptedException {
        // Aplica rate limiting antes da requisição
        aplicarRateLimiting();
        
        // Extrai o template ID do endpoint (ex: "/api/analytics/reports/relacao-manifestos-detalhada/data")
        String templateId = extrairTemplateIdDoEndpoint(endpoint);
        
        // Primeiro, tenta obter o ID numérico do template
        String idNumerico = obterIdNumericoTemplate(templateId);
        if (idNumerico == null) {
            logger.debug("Não foi possível obter ID numérico para template: {}", templateId);
            return new ArrayList<>();
        }
        
        // Constrói a URL correta usando o ID numérico
        String dataFormatada = dataInicio.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String url = urlBase + "/api/analytics/reports/" + idNumerico + "/data?since=" + dataFormatada + "&format=json";
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(TIMEOUT_REQUISICAO_SEGUNDOS))
                .build();
        
        logger.debug("Buscando dados do template (ID: {}): {}", idNumerico, url);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Tratamento específico para erro 429
        if (response.statusCode() == 429) {
            logger.warn("Rate limit excedido ao buscar template (HTTP 429). Aguardando...");
            Thread.sleep(RATE_LIMIT_MS * 2);
            
            // Tenta novamente após aguardar
            aplicarRateLimiting();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
        
        if (response.statusCode() == 200) {
            String tipoRelatorio = determinarTipoRelatorio(templateId);
            return processarJsonRelatorio(response.body(), tipoRelatorio);
        } else {
            logger.debug("Template não retornou dados. Status: {}, Body: {}", response.statusCode(), response.body());
            return new ArrayList<>();
        }
    }
    
    /**
     * Extrai o template ID do endpoint fornecido
     */
    private String extrairTemplateIdDoEndpoint(String endpoint) {
        // Ex: "/api/analytics/reports/relacao-manifestos-detalhada/data" -> "relacao-manifestos-detalhada"
        String[] partes = endpoint.split("/");
        for (int i = 0; i < partes.length - 1; i++) {
            if ("reports".equals(partes[i]) && i + 1 < partes.length) {
                return partes[i + 1];
            }
        }
        return endpoint.substring(endpoint.lastIndexOf("/") + 1);
    }
    
    /**
     * Obtém o ID numérico do template baseado no nome/identificador
     */
    private String obterIdNumericoTemplate(String templateId) {
        try {
            // Aplica rate limiting antes da requisição
            aplicarRateLimiting();
            
            String url = urlBase + "/api/analytics/reports";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(TIMEOUT_REQUISICAO_SEGUNDOS))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                
                // Procura pelo template com o ID correspondente
                if (jsonResponse.isArray()) {
                    for (JsonNode template : jsonResponse) {
                        String nome = template.has("name") ? template.get("name").asText() : "";
                        String identificador = template.has("identifier") ? template.get("identifier").asText() : "";
                        
                        if (templateId.equals(nome) || templateId.equals(identificador)) {
                            return template.has("id") ? template.get("id").asText() : null;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Erro ao obter ID numérico do template {}: {}", templateId, e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Determina o tipo de relatório baseado no template ID
     */
    private String determinarTipoRelatorio(String templateId) {
        if (templateId.contains("manifestos")) return "manifestos";
        if (templateId.contains("localizacao") || templateId.contains("cargas")) return "localizacao_carga";
        if (templateId.contains("cotacoes")) return "cotacoes";
        if (templateId.contains("fretes")) return "fretes";
        return "dados";
    }

    /**
     * Fluxo tradicional POST+GET para compatibilidade com versões antigas da API
     */
    private String solicitarRelatorioFluxoTradicional(String templateId, LocalDateTime dataInicio, String tipoRelatorio) throws IOException, InterruptedException {
        // Endpoints alternativos baseados no template ID
        String[] endpointsParaTestar = gerarEndpointsAlternativos(templateId, tipoRelatorio);
        
        String dataFormatada = dataInicio.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        String requestBody = String.format("{\n" +
                "    \"templateId\": \"%s\",\n" +
                "    \"dataInicio\": \"%s\",\n" +
                "    \"formato\": \"json\",\n" +
                "    \"incluirDetalhes\": true\n" +
                "}", templateId, dataFormatada);
        
        // Tenta cada endpoint até encontrar um que funcione
        for (String endpoint : endpointsParaTestar) {
            try {
                String result = enviarSolicitacaoRelatorio(endpoint, requestBody);
                if (result != null) {
                    logger.info("Endpoint funcional encontrado para {}: {}", tipoRelatorio, endpoint);
                    return result;
                }
            } catch (Exception e) {
                logger.debug("Endpoint {} não funcionou para {}: {}", endpoint, tipoRelatorio, e.getMessage());
            }
        }
        
        logger.error("Nenhum endpoint válido encontrado para {} (template: {})", tipoRelatorio, templateId);
        return null;
    }
    
    /**
     * Gera endpoints alternativos baseados no template ID e tipo de relatório
     */
    private String[] gerarEndpointsAlternativos(String templateId, String tipoRelatorio) {
        if ("manifestos".equals(tipoRelatorio)) {
            return new String[]{
                "/api/analytics/reports/" + templateId + "/generate",
                "/api/data-export/manifestos",
                "/api/reports/manifests",
                "/api/export/manifests", 
                "/api/data/manifests",
                "/api/v1/reports/manifests"
            };
        } else if ("localizacao".equals(tipoRelatorio)) {
            return new String[]{
                "/api/analytics/reports/" + templateId + "/generate",
                "/api/data-export/localizacao-carga",
                "/api/reports/tracking",
                "/api/export/tracking",
                "/api/data/tracking", 
                "/api/reports/location",
                "/api/export/location"
            };
        } else {
            return new String[]{
                "/api/analytics/reports/" + templateId + "/generate",
                "/api/data-export/" + tipoRelatorio,
                "/api/reports/" + tipoRelatorio,
                "/api/export/" + tipoRelatorio
            };
        }
    }

    /**
     * Envia a solicitação POST para gerar o relatório.
     */
    private String enviarSolicitacaoRelatorio(String endpoint, String requestBody) throws IOException, InterruptedException {
        // Aplica rate limiting antes da requisição
        aplicarRateLimiting();
        
        String url = urlBase + endpoint;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(TIMEOUT_REQUISICAO_SEGUNDOS))
                .build();
        
        logger.debug("Enviando solicitação POST para: {}", url);
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Tratamento específico para erro 429 (Too Many Requests)
        if (response.statusCode() == 429) {
            logger.warn("Rate limit excedido (HTTP 429). Aguardando antes de tentar novamente...");
            Thread.sleep(RATE_LIMIT_MS * 2); // Aguarda o dobro do tempo normal
            
            // Tenta novamente após aguardar
            aplicarRateLimiting();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
        
        if (response.statusCode() == 202) { // Accepted
            JsonNode jsonResponse = objectMapper.readTree(response.body());
            String requestId = jsonResponse.get("requestId").asText();
            logger.info("Relatório solicitado com sucesso. Request ID: {}", requestId);
            return requestId;
        } else {
            logger.error("Erro ao solicitar relatório. Status: {}, Body: {}", response.statusCode(), response.body());
            return null;
        }
    }
    
    /**
     * Aguarda o processamento do relatório verificando periodicamente o status.
     */
    private String aguardarProcessamentoRelatorio(String requestId) throws IOException, InterruptedException {
        String statusEndpoint = "/api/data-export/status/" + requestId;
        String statusUrl = urlBase + statusEndpoint;
        
        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS_STATUS; tentativa++) {
            logger.debug("Verificando status do relatório (tentativa {}/{}): {}", tentativa, MAX_TENTATIVAS_STATUS, requestId);
            
            // Aplica rate limiting antes da requisição
            aplicarRateLimiting();
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(statusUrl))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .timeout(Duration.ofSeconds(TIMEOUT_REQUISICAO_SEGUNDOS))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Tratamento específico para erro 429
            if (response.statusCode() == 429) {
                logger.warn("Rate limit excedido ao verificar status (HTTP 429). Aguardando...");
                Thread.sleep(RATE_LIMIT_MS * 2);
                continue; // Tenta novamente na próxima iteração
            }
            
            if (response.statusCode() == 200) {
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                String status = jsonResponse.get("status").asText();
                
                switch (status.toLowerCase()) {
                    case "completed":
                        String downloadUrl = jsonResponse.get("downloadUrl").asText();
                        logger.info("Relatório processado com sucesso. URL de download: {}", downloadUrl);
                        return downloadUrl;
                        
                    case "processing":
                        logger.debug("Relatório ainda em processamento...");
                        break;
                        
                    case "failed":
                        String error = jsonResponse.has("error") ? jsonResponse.get("error").asText() : "Erro desconhecido";
                        logger.error("Falha no processamento do relatório: {}", error);
                        return null;
                        
                    default:
                        logger.warn("Status desconhecido do relatório: {}", status);
                }
            } else {
                logger.warn("Erro ao verificar status do relatório. Status: {}", response.statusCode());
            }
            
            // Aguarda antes da próxima verificação (exceto na última tentativa)
            if (tentativa < MAX_TENTATIVAS_STATUS) {
                Thread.sleep(INTERVALO_VERIFICACAO_MS);
            }
        }
        
        logger.error("Timeout: Relatório não ficou pronto após {} tentativas", MAX_TENTATIVAS_STATUS);
        return null;
    }
    
    /**
     * Baixa e processa o relatório usando a URL fornecida.
     */
    private List<EntidadeDinamica> baixarEProcessarRelatorio(String downloadUrl, String tipoRelatorio) throws IOException, InterruptedException {
        logger.info("Baixando relatório de {}: {}", tipoRelatorio, downloadUrl);
        
        // Aplica rate limiting antes da requisição
        aplicarRateLimiting();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .header("Authorization", "Bearer " + token)
                .GET()
                .timeout(Duration.ofSeconds(TIMEOUT_REQUISICAO_SEGUNDOS))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Tratamento específico para erro 429
        if (response.statusCode() == 429) {
            logger.warn("Rate limit excedido ao baixar relatório (HTTP 429). Aguardando...");
            Thread.sleep(RATE_LIMIT_MS * 2);
            
            // Tenta novamente após aguardar
            aplicarRateLimiting();
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
        
        if (response.statusCode() == 200) {
            return processarJsonRelatorio(response.body(), tipoRelatorio);
        } else {
            logger.error("Erro ao baixar relatório. Status: {}, Body: {}", response.statusCode(), response.body());
            return new ArrayList<>();
        }
    }
    
    /**
     * Processa o JSON do relatório e converts para EntidadeDinamica.
     */
    private List<EntidadeDinamica> processarJsonRelatorio(String jsonContent, String tipoRelatorio) {
        List<EntidadeDinamica> entidades = new ArrayList<>();
        
        try {
            JsonNode rootNode = objectMapper.readTree(jsonContent);
            JsonNode dataArray = rootNode.isArray() ? rootNode : rootNode.get("data");
            
            if (dataArray != null && dataArray.isArray()) {
                for (JsonNode item : dataArray) {
                    EntidadeDinamica entidade = new EntidadeDinamica();
                    entidade.setTipoEntidade(tipoRelatorio);
                    
                    // Processar cada campo do JSON
                    item.fields().forEachRemaining(entry -> {
                        entidade.adicionarCampo(entry.getKey(), entry.getValue().asText());
                    });
                    
                    entidades.add(entidade);
                }
            }
            
            logger.info("Processados {} registros do relatório de {}", entidades.size(), tipoRelatorio);
            
        } catch (Exception e) {
            logger.error("Error ao processar JSON do relatório de {}", tipoRelatorio, e);
        }
        
        return entidades;
    }
    
    /**
     * Valida se a API está acessível e funcionando corretamente.
     * Testa endpoints básicos para verificar conectividade.
     */
    public boolean validarAcessoApi() {
        logger.info("Validando acesso à API Data Export...");
        
        if (urlBase == null || urlBase.isBlank()) {
            logger.error("URL base não configurada para API Data Export");
            return false;
        }
        
        if (token == null || token.isBlank()) {
            logger.error("Token não configurado para API Data Export");
            return false;
        }
        
        // Testa endpoints básicos para verificar se a API está respondendo
        String[] endpointsParaTestar = {
            "/api/data-export/status",
            "/api/reports/status",
            "/api/export/status",
            "/api/status",
            "/api/health"
        };
        
        for (String endpoint : endpointsParaTestar) {
            try {
                String url = urlBase + endpoint;
                
                HttpRequest requisicao = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .GET()
                        .timeout(Duration.ofSeconds(10))
                        .build();
                
                HttpResponse<String> resposta = httpClient.send(requisicao, HttpResponse.BodyHandlers.ofString());
                
                if (resposta.statusCode() == 200 || resposta.statusCode() == 404) {
                    logger.info("API Data Export respondeu no endpoint {}: status {}", endpoint, resposta.statusCode());
                    return true;
                }
                
            } catch (Exception e) {
                logger.debug("Endpoint {} não respondeu: {}", endpoint, e.getMessage());
            }
        }
        
        logger.error("Nenhum endpoint de status respondeu para API Data Export");
        return false;
    }
    
    /**
     * Busca e lista todos os templates disponíveis na API Data Export.
     * Útil para descobrir os IDs corretos dos templates.
     */
    public List<String> listarTemplatesDisponiveis() {
        logger.info("Buscando templates disponíveis na API Data Export...");
        
        String[] endpointsParaTestar = {
            "/api/analytics/reports",
            "/api/analytics/templates",
            "/api/data-export/templates",
            "/api/reports/templates",
            "/api/templates"
        };
        
        for (String endpoint : endpointsParaTestar) {
            try {
                String url = urlBase + endpoint;
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .GET()
                        .timeout(Duration.ofSeconds(TIMEOUT_REQUISICAO_SEGUNDOS))
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    logger.info("Templates encontrados no endpoint {}", endpoint);
                    return processarListaTemplates(response.body());
                } else {
                    logger.debug("Endpoint {} retornou status {}", endpoint, response.statusCode());
                }
                
            } catch (Exception e) {
                logger.debug("Erro ao testar endpoint {}: {}", endpoint, e.getMessage());
            }
        }
        
        logger.warn("Nenhum endpoint de templates respondeu, usando IDs padrão da documentação");
        return List.of(TEMPLATE_ID_MANIFESTOS, TEMPLATE_ID_LOCALIZACAO, TEMPLATE_ID_COTACOES, TEMPLATE_ID_FRETES);
    }
    
    /**
     * Processa a resposta JSON da lista de templates para extrair os IDs.
     */
    private List<String> processarListaTemplates(String jsonResponse) {
        List<String> templateIds = new ArrayList<>();
        
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            // Tenta diferentes estruturas de resposta
            JsonNode templatesNode = rootNode.get("templates");
            if (templatesNode == null) {
                templatesNode = rootNode.get("data");
            }
            if (templatesNode == null) {
                templatesNode = rootNode;
            }
            
            if (templatesNode.isArray()) {
                for (JsonNode templateNode : templatesNode) {
                    String id = extrairIdTemplate(templateNode);
                    if (id != null) {
                        templateIds.add(id);
                        logger.info("Template encontrado: {} - {}", id, 
                                   templateNode.has("name") ? templateNode.get("name").asText() : "Nome não disponível");
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("Erro ao processar lista de templates: {}", e.getMessage());
        }
        
        return templateIds;
    }
    
    /**
     * Extrai o ID do template de um nó JSON, tentando diferentes campos possíveis.
     */
    private String extrairIdTemplate(JsonNode templateNode) {
        // Tenta diferentes campos que podem conter o ID
        String[] camposId = {"id", "templateId", "template_id", "name", "slug", "key"};
        
        for (String campo : camposId) {
            if (templateNode.has(campo)) {
                String valor = templateNode.get(campo).asText();
                if (valor != null && !valor.isBlank()) {
                    return valor;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Verifica se um template específico existe e está ativo.
     */
    public boolean verificarTemplateExiste(String templateId) {
        logger.debug("Verificando se template {} existe...", templateId);
        
        String endpoint = "/api/analytics/reports/" + templateId;
        String url = urlBase + endpoint;
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                logger.info("Template {} confirmado como existente", templateId);
                return true;
            } else {
                logger.debug("Template {} não encontrado. Status: {}", templateId, response.statusCode());
                return false;
            }
            
        } catch (Exception e) {
            logger.debug("Erro ao verificar template {}: {}", templateId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Busca cotações usando a API Data Export.
     * Baseado na nova documentação que confirma o uso da API Data Export para cotações.
     * 
     * @param dataInicio Data de início da busca
     * @return Lista de cotações encontradas
     */
    public List<EntidadeDinamica> buscarCotacoes(LocalDateTime dataInicio) {
        logger.info("Iniciando busca de cotações via API Data Export para data: {}", dataInicio);
        
        try {
            String requestId = solicitarRelatorioCotacoes(dataInicio);
            if (requestId != null) {
                return buscarRelatorioProcessado(requestId, "cotacoes");
            } else {
                logger.error("Falha ao solicitar relatório de cotações");
                return new ArrayList<>();
            }
        } catch (Exception e) {
            logger.error("Erro ao buscar cotações", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Solicita a geração do relatório de cotações via POST (versão pública).
     * 
     * @param dataInicio Data de início da busca (formato ISO: yyyy-MM-dd'T'HH:mm:ss)
     * @return Request ID do relatório ou null se falhar
     */
    public String solicitarRelatorioCotacoes(String dataInicio) {
        try {
            LocalDateTime dataInicioDateTime = LocalDateTime.parse(dataInicio);
            return solicitarRelatorioCotacoes(dataInicioDateTime);
        } catch (Exception e) {
            logger.error("Erro ao solicitar relatório de cotações", e);
            return null;
        }
    }

    /**
     * Solicita a geração do relatório de cotações via POST.
     */
    private String solicitarRelatorioCotacoes(LocalDateTime dataInicio) throws IOException, InterruptedException {
        return solicitarRelatorioComTemplate(TEMPLATE_ID_COTACOES, dataInicio, "cotacoes");
    }

    /**
     * Aplica rate limiting para evitar erro HTTP 429.
     * Garante que haja pelo menos 2 segundos entre requisições consecutivas.
     */
    private void aplicarRateLimiting() {
        long agora = System.currentTimeMillis();
        long tempoDecorrido = agora - ultimaRequisicao;
        
        if (tempoDecorrido < RATE_LIMIT_MS) {
            long tempoEspera = RATE_LIMIT_MS - tempoDecorrido;
            try {
                logger.debug("Aplicando rate limiting: aguardando {} ms", tempoEspera);
                Thread.sleep(tempoEspera);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Rate limiting interrompido: {}", e.getMessage());
            }
        }
        
        ultimaRequisicao = System.currentTimeMillis();
    }
}