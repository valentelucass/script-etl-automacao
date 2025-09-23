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
    
    // Configurações do fluxo Data Export
    private static final int MAX_TENTATIVAS_STATUS = 30; // Máximo 30 tentativas
    private static final int INTERVALO_VERIFICACAO_MS = 10000; // 10 segundos entre verificações
    private static final int TIMEOUT_REQUISICAO_SEGUNDOS = 60;
    
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
     * Solicita a geração do relatório de manifestos via POST.
     */
    private String solicitarRelatorioManifestos(LocalDateTime dataInicio) throws IOException, InterruptedException {
        String endpoint = "/api/data-export/manifestos";
        String dataFormatada = dataInicio.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        String requestBody = String.format("{\n" +
                "    \"dataInicio\": \"%s\",\n" +
                "    \"formato\": \"json\",\n" +
                "    \"incluirDetalhes\": true\n" +
                "}", dataFormatada);
        
        return enviarSolicitacaoRelatorio(endpoint, requestBody);
    }
    
    /**
     * Solicita a geração do relatório de localização da carga via POST.
     */
    private String solicitarRelatorioLocalizacao(LocalDateTime dataInicio) throws IOException, InterruptedException {
        String endpoint = "/api/data-export/localizacao-carga";
        String dataFormatada = dataInicio.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        String requestBody = String.format("{\n" +
                "    \"dataInicio\": \"%s\",\n" +
                "    \"formato\": \"json\",\n" +
                "    \"incluirHistorico\": true\n" +
                "}", dataFormatada);
        
        return enviarSolicitacaoRelatorio(endpoint, requestBody);
    }
    
    /**
     * Envia a solicitação POST para gerar o relatório.
     */
    private String enviarSolicitacaoRelatorio(String endpoint, String requestBody) throws IOException, InterruptedException {
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
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(statusUrl))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .timeout(Duration.ofSeconds(TIMEOUT_REQUISICAO_SEGUNDOS))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
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
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .header("Authorization", "Bearer " + token)
                .GET()
                .timeout(Duration.ofSeconds(TIMEOUT_REQUISICAO_SEGUNDOS))
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
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
     * Valida o acesso à API Data Export.
     * 
     * @return true se o acesso estiver válido, false caso contrário
     */
    public boolean validarAcessoApi() {
        try {
            String url = urlBase + "/api/data-export/health";
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            boolean acessoValido = response.statusCode() == 200;
            
            if (acessoValido) {
                logger.info("Validação da API Data Export bem-sucedida");
            } else {
                logger.error("Falha na validação da API Data Export. Status: {}", response.statusCode());
            }
            
            return acessoValido;
            
        } catch (Exception e) {
            logger.error("Erro durante a validação da API Data Export", e);
            return false;
        }
    }
}