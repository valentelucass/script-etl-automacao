package br.com.extrator.api;

import br.com.extrator.modelo.EntidadeDinamica;
import br.com.extrator.util.CarregadorConfig;
import br.com.extrator.util.UtilitarioHttpRetry;

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
import java.util.function.Supplier;

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
     * Busca manifestos a partir de uma data específica.
     * Orquestra o fluxo assíncrono completo: solicitar → aguardar → descarregar.
     * 
     * @param dataInicio Data de início para busca dos manifestos
     * @param modoTeste Se true, limita a busca à primeira página de resultados
     * @return Lista de manifestos encontrados ou lista vazia se falhar
     */
    public List<EntidadeDinamica> buscarManifestos(String dataInicio, boolean modoTeste) {
        if (dataInicio == null || dataInicio.trim().isEmpty()) {
            logger.error("Data de início não pode ser nula ou vazia para buscar manifestos");
            return new ArrayList<>();
        }
        
        if (modoTeste) {
            logger.info("Modo de teste ativo - limitando busca de manifestos à primeira página");
        }
        
        logger.info("Iniciando busca de manifestos a partir de: {}", dataInicio);
        
        try {
            // Passo 1: Solicitar a geração do relatório
            logger.info("Passo 1/3: Solicitando geração do relatório de manifestos...");
            String requestId = solicitarRelatorio(TEMPLATE_ID_MANIFESTOS, dataInicio);
            
            if (requestId == null || requestId.trim().isEmpty()) {
                logger.error("Falha ao solicitar relatório de manifestos. RequestId não obtido.");
                return new ArrayList<>();
            }
            
            logger.info("Relatório solicitado com sucesso. RequestId: {}", requestId);
            
            // Passo 2: Aguardar o processamento do relatório
            logger.info("Passo 2/3: Aguardando processamento do relatório...");
            String urlDownload = aguardarProcessamento(requestId);
            
            if (urlDownload == null || urlDownload.trim().isEmpty()) {
                logger.error("Falha ao aguardar processamento do relatório de manifestos. URL de download não obtida.");
                return new ArrayList<>();
            }
            
            logger.info("Processamento concluído. URL de download obtida: {}", urlDownload);
            
            // Passo 3: Descarregar e processar o relatório
            logger.info("Passo 3/3: Descarregando e processando relatório...");
            List<EntidadeDinamica> manifestos = descarregarRelatorio(urlDownload, "manifestos");
            
            logger.info("Busca de manifestos concluída. Total encontrado: {}", manifestos.size());
            return manifestos;
            
        } catch (Exception e) {
            logger.error("Erro durante a busca de manifestos para data {}: {}", dataInicio, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Busca localização da carga a partir de uma data específica.
     * Orquestra o fluxo assíncrono completo: solicitar → aguardar → descarregar.
     * 
     * @param dataInicio Data de início para busca da localização da carga
     * @param modoTeste Se true, limita a busca à primeira página de resultados
     * @return Lista de localizações encontradas ou lista vazia se falhar
     */
    public List<EntidadeDinamica> buscarLocalizacaoCarga(String dataInicio, boolean modoTeste) {
        if (dataInicio == null || dataInicio.trim().isEmpty()) {
            logger.error("Data de início não pode ser nula ou vazia para buscar localização da carga");
            return new ArrayList<>();
        }
        
        if (modoTeste) {
            logger.info("Modo de teste ativo - limitando busca de localização da carga à primeira página");
        }
        
        logger.info("Iniciando busca de localização da carga a partir de: {}", dataInicio);
        
        try {
            // Passo 1: Solicitar a geração do relatório
            logger.info("Passo 1/3: Solicitando geração do relatório de localização da carga...");
            String requestId = solicitarRelatorio(TEMPLATE_ID_LOCALIZACAO, dataInicio);
            
            if (requestId == null || requestId.trim().isEmpty()) {
                logger.error("Falha ao solicitar relatório de localização da carga. RequestId não obtido.");
                return new ArrayList<>();
            }
            
            logger.info("Relatório solicitado com sucesso. RequestId: {}", requestId);
            
            // Passo 2: Aguardar o processamento do relatório
            logger.info("Passo 2/3: Aguardando processamento do relatório...");
            String urlDownload = aguardarProcessamento(requestId);
            
            if (urlDownload == null || urlDownload.trim().isEmpty()) {
                logger.error("Falha ao aguardar processamento do relatório de localização da carga. URL de download não obtida.");
                return new ArrayList<>();
            }
            
            logger.info("Processamento concluído. URL de download obtida: {}", urlDownload);
            
            // Passo 3: Descarregar e processar o relatório
            logger.info("Passo 3/3: Descarregando e processando relatório...");
            List<EntidadeDinamica> localizacoes = descarregarRelatorio(urlDownload, "localizacao_carga");
            
            logger.info("Busca de localização da carga concluída. Total encontrado: {}", localizacoes.size());
            return localizacoes;
            
        } catch (Exception e) {
            logger.error("Erro durante a busca de localização da carga para data {}: {}", dataInicio, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    // ========== MÉTODOS PRIVADOS PARA FLUXO ASSÍNCRONO ==========
    
    /**
     * Passo 1: Solicita a geração de um relatório via POST.
     * Envia uma requisição para a API solicitando a geração do relatório.
     * 
     * @param templateId ID do template do relatório (ex: "relacao-manifestos-detalhada")
     * @param dataInicio Data de início da busca
     * @return Request ID do relatório ou null se falhar
     */
    private String solicitarRelatorio(String templateId, String dataInicio) {
        try {
            // Formata a data para o padrão esperado pela API
            String dataFormatada = formatarDataParaApi(dataInicio);
            
            // Constrói o corpo da requisição JSON
            String requestBody = construirCorpoRequisicao(templateId, dataFormatada);
            
            // Constrói a URL do endpoint específico baseado no template
            String endpoint = obterEndpointPorTemplate(templateId);
            String url = urlBase + endpoint;
            
            logger.info("Solicitando geração de relatório: template={}, data={}", templateId, dataFormatada);
            
            // Encapsula a criação do HttpRequest em um Supplier
            Supplier<HttpRequest> fornecedorRequisicao = () -> HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(TIMEOUT_REQUISICAO_SEGUNDOS))
                    .build();
            
            // Executa a requisição usando o utilitário centralizado
            HttpResponse<String> response = UtilitarioHttpRetry.executarComRetry(
                    httpClient, 
                    fornecedorRequisicao, 
                    "solicitarRelatorio-" + templateId
            );
            
            if (response.statusCode() == 200 || response.statusCode() == 202) {
                // Extrai o requestId da resposta JSON
                String requestId = extrairRequestIdDaResposta(response.body());
                if (requestId != null) {
                    logger.info("Relatório solicitado com sucesso. Request ID: {}", requestId);
                    return requestId;
                } else {
                    logger.error("Resposta da API não contém requestId válido: {}", response.body());
                    return null;
                }
            } else {
                logger.error("Erro ao solicitar relatório. Status: {}, Resposta: {}", 
                           response.statusCode(), response.body());
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Erro ao solicitar relatório para template {}: {}", templateId, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Obtém o endpoint específico baseado no template ID conforme a documentação da API.
     */
    private String obterEndpointPorTemplate(String templateId) {
        switch (templateId) {
            case TEMPLATE_ID_MANIFESTOS:
                return "/api/data-export/manifestos";
            case TEMPLATE_ID_LOCALIZACAO:
                return "/api/data-export/localizacao-carga";
            case TEMPLATE_ID_COTACOES:
                return "/api/data-export/cotacoes";
            case TEMPLATE_ID_FRETES:
                return "/api/data-export/fretes";
            default:
                // Fallback para templates não mapeados
                return "/api/data-export/" + templateId;
        }
    }

    /**
     * Formata a data de início para o padrão esperado pela API.
     */
    private String formatarDataParaApi(String dataInicio) {
        try {
            // Se já está no formato ISO, usa diretamente
            if (dataInicio.contains("T")) {
                return dataInicio;
            }
            // Se está no formato de data simples, adiciona horário
            if (dataInicio.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return dataInicio + "T00:00:00";
            }
            // Retorna como está se não conseguir identificar o formato
            return dataInicio;
        } catch (Exception e) {
            logger.warn("Erro ao formatar data {}, usando valor original: {}", dataInicio, e.getMessage());
            return dataInicio;
        }
    }
    
    /**
     * Constrói o corpo da requisição JSON para solicitar o relatório.
     */
    private String construirCorpoRequisicao(String templateId, String dataInicio) {
        return String.format(
            "{\n" +
            "  \"templateId\": \"%s\",\n" +
            "  \"parameters\": {\n" +
            "    \"startDate\": \"%s\",\n" +
            "    \"format\": \"json\"\n" +
            "  }\n" +
            "}", 
            templateId, dataInicio
        );
    }
    
    /**
     * Extrai o requestId da resposta JSON da API.
     */
    private String extrairRequestIdDaResposta(String jsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            // Tenta diferentes campos possíveis para o requestId
            String[] camposPossiveis = {"requestId", "id", "request_id", "jobId", "taskId"};
            
            for (String campo : camposPossiveis) {
                JsonNode requestIdNode = rootNode.get(campo);
                if (requestIdNode != null && !requestIdNode.isNull()) {
                    String requestId = requestIdNode.asText();
                    if (requestId != null && !requestId.trim().isEmpty()) {
                        return requestId;
                    }
                }
            }
            
            logger.warn("RequestId não encontrado na resposta JSON: {}", jsonResponse);
            return null;
            
        } catch (Exception e) {
            logger.error("Erro ao extrair requestId da resposta: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Passo 2: Aguarda o processamento do relatório através de polling.
     * Faz verificações periódicas do status até que o relatório esteja pronto.
     * 
     * @param requestId ID da solicitação do relatório
     * @return URL de download do relatório ou null se falhar/timeout
     */
    private String aguardarProcessamento(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            logger.error("RequestId inválido para aguardar processamento");
            return null;
        }
        
        logger.info("Iniciando polling para requestId: {}", requestId);
        
        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS_STATUS; tentativa++) {
            try {
                // Constrói a URL do endpoint de status
                String url = urlBase + "/api/data-export/status/" + requestId;
                
                logger.debug("Verificando status do relatório (tentativa {}/{}): {}", 
                           tentativa, MAX_TENTATIVAS_STATUS, requestId);
                
                // Encapsula a criação do HttpRequest em um Supplier
                Supplier<HttpRequest> fornecedorRequisicao = () -> HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .GET()
                        .timeout(Duration.ofSeconds(TIMEOUT_REQUISICAO_SEGUNDOS))
                        .build();
                
                // Executa a requisição usando o utilitário centralizado
                HttpResponse<String> response = UtilitarioHttpRetry.executarComRetry(
                        httpClient, 
                        fornecedorRequisicao, 
                        "aguardarProcessamento-" + requestId
                );
                
                if (response.statusCode() == 200) {
                    String status = extrairStatusDaResposta(response.body());
                    
                    if (status == null) {
                        logger.warn("Status não encontrado na resposta para requestId: {}", requestId);
                        continue;
                    }
                    
                    logger.debug("Status atual do relatório {}: {}", requestId, status);
                    
                    switch (status.toLowerCase()) {
                        case "completed":
                        case "ready":
                        case "finished":
                            // Relatório pronto, extrai URL de download
                            String downloadUrl = extrairUrlDownloadDaResposta(response.body());
                            if (downloadUrl != null) {
                                logger.info("Relatório {} processado com sucesso. URL: {}", requestId, downloadUrl);
                                return downloadUrl;
                            } else {
                                logger.error("Relatório {} marcado como pronto mas URL de download não encontrada", requestId);
                                return null;
                            }
                            
                        case "failed":
                        case "error":
                        case "cancelled":
                            logger.error("Processamento do relatório {} falhou com status: {}", requestId, status);
                            String errorMessage = extrairMensagemErroDaResposta(response.body());
                            if (errorMessage != null) {
                                logger.error("Mensagem de erro: {}", errorMessage);
                            }
                            return null;
                            
                        case "processing":
                        case "pending":
                        case "running":
                        case "queued":
                            // Continua aguardando
                            logger.debug("Relatório {} ainda em processamento, aguardando...", requestId);
                            break;
                            
                        default:
                            logger.warn("Status desconhecido para relatório {}: {}", requestId, status);
                            break;
                    }
                    
                } else {
                    logger.warn("Erro ao verificar status do relatório {}. Status HTTP: {}, Resposta: {}", 
                              requestId, response.statusCode(), response.body());
                }
                
                // Aguarda antes da próxima tentativa (exceto na última)
                if (tentativa < MAX_TENTATIVAS_STATUS) {
                    logger.debug("Aguardando {} segundos antes da próxima verificação...", 
                               INTERVALO_VERIFICACAO_MS / 1000);
                    Thread.sleep(INTERVALO_VERIFICACAO_MS);
                }
                
            } catch (InterruptedException e) {
                logger.warn("Polling interrompido para requestId: {}", requestId);
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                logger.error("Erro durante polling para requestId {} (tentativa {}): {}", 
                           requestId, tentativa, e.getMessage(), e);
            }
        }
        
        logger.error("Timeout: Relatório {} não ficou pronto após {} tentativas", requestId, MAX_TENTATIVAS_STATUS);
        return null;
    }
    
    /**
     * Extrai o status do relatório da resposta JSON.
     */
    private String extrairStatusDaResposta(String jsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            // Tenta diferentes campos possíveis para o status
            String[] camposPossiveis = {"status", "state", "progress", "phase"};
            
            for (String campo : camposPossiveis) {
                JsonNode statusNode = rootNode.get(campo);
                if (statusNode != null && !statusNode.isNull()) {
                    return statusNode.asText();
                }
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("Erro ao extrair status da resposta: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Extrai a URL de download da resposta JSON.
     */
    private String extrairUrlDownloadDaResposta(String jsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            // Tenta diferentes campos possíveis para a URL de download
            String[] camposPossiveis = {"downloadUrl", "url", "download_url", "fileUrl", "resultUrl"};
            
            for (String campo : camposPossiveis) {
                JsonNode urlNode = rootNode.get(campo);
                if (urlNode != null && !urlNode.isNull()) {
                    String url = urlNode.asText();
                    if (url != null && !url.trim().isEmpty()) {
                        return url;
                    }
                }
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("Erro ao extrair URL de download da resposta: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Extrai a mensagem de erro da resposta JSON.
     */
    private String extrairMensagemErroDaResposta(String jsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            
            // Tenta diferentes campos possíveis para a mensagem de erro
            String[] camposPossiveis = {"error", "message", "errorMessage", "description", "details"};
            
            for (String campo : camposPossiveis) {
                JsonNode errorNode = rootNode.get(campo);
                if (errorNode != null && !errorNode.isNull()) {
                    return errorNode.asText();
                }
            }
            
            return null;
            
        } catch (Exception e) {
            logger.error("Erro ao extrair mensagem de erro da resposta: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Passo 3: Descarrega e processa o relatório usando a URL de download.
     * Faz o download do arquivo e converte o conteúdo JSON em lista de EntidadeDinamica.
     * 
     * @param urlDownload URL para download do relatório
     * @param tipoEntidade Tipo da entidade para processamento (manifestos, localizacao_carga)
     * @return Lista de entidades processadas ou lista vazia se falhar
     */
    private List<EntidadeDinamica> descarregarRelatorio(String urlDownload, String tipoEntidade) {
        if (urlDownload == null || urlDownload.trim().isEmpty()) {
            logger.error("URL de download inválida para descarregar relatório");
            return new ArrayList<>();
        }
        
        if (tipoEntidade == null || tipoEntidade.trim().isEmpty()) {
            logger.error("Tipo de entidade inválido para descarregar relatório");
            return new ArrayList<>();
        }
        
        try {
            logger.info("Iniciando download do relatório: {} (tipo: {})", urlDownload, tipoEntidade);
            
            Supplier<HttpRequest> requestSupplier = () -> HttpRequest.newBuilder()
                    .uri(URI.create(urlDownload))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(TIMEOUT_REQUISICAO_SEGUNDOS))
                    .build();
            
            HttpResponse<String> response = UtilitarioHttpRetry.executarComRetry(
                    httpClient, 
                    requestSupplier, 
                    "descarregarRelatorio-" + tipoEntidade
            );
            
            if (response.statusCode() == 200) {
                String conteudoJson = response.body();
                
                if (conteudoJson == null || conteudoJson.trim().isEmpty()) {
                    logger.warn("Conteúdo do relatório está vazio para URL: {}", urlDownload);
                    return new ArrayList<>();
                }
                
                logger.info("Download concluído com sucesso. Tamanho do conteúdo: {} caracteres", 
                          conteudoJson.length());
                
                // Processa o JSON e converte para lista de EntidadeDinamica
                List<EntidadeDinamica> entidades = processarJsonRelatorio(conteudoJson, tipoEntidade);
                
                logger.info("Processamento concluído. Total de entidades extraídas: {}", entidades.size());
                return entidades;
                
            } else {
                logger.error("Erro ao descarregar relatório. Status HTTP: {}, URL: {}, Resposta: {}", 
                           response.statusCode(), urlDownload, response.body());
                return new ArrayList<>();
            }
            
        } catch (Exception e) {
            logger.error("Erro ao descarregar relatório da URL {}: {}", urlDownload, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Processa o conteúdo JSON do relatório e converte em lista de EntidadeDinamica.
     * Suporta diferentes formatos de resposta da API.
     */
    private List<EntidadeDinamica> processarJsonRelatorio(String conteudoJson, String tipoEntidade) {
        List<EntidadeDinamica> entidades = new ArrayList<>();
        
        try {
            JsonNode rootNode = objectMapper.readTree(conteudoJson);
            
            // Tenta diferentes estruturas possíveis da resposta JSON
            JsonNode dadosNode = encontrarNoDados(rootNode);
            
            if (dadosNode == null) {
                logger.warn("Nó de dados não encontrado no JSON para tipo: {}", tipoEntidade);
                return entidades;
            }
            
            if (dadosNode.isArray()) {
                // Processa array de objetos
                for (JsonNode itemNode : dadosNode) {
                    EntidadeDinamica entidade = criarEntidadeAPartirDoJson(itemNode, tipoEntidade);
                    if (entidade != null) {
                        entidades.add(entidade);
                    }
                }
            } else if (dadosNode.isObject()) {
                // Processa objeto único
                EntidadeDinamica entidade = criarEntidadeAPartirDoJson(dadosNode, tipoEntidade);
                if (entidade != null) {
                    entidades.add(entidade);
                }
            } else {
                logger.warn("Formato de dados não reconhecido no JSON para tipo: {}", tipoEntidade);
            }
            
            logger.debug("Processamento JSON concluído. {} entidades criadas para tipo: {}", 
                       entidades.size(), tipoEntidade);
            
        } catch (Exception e) {
            logger.error("Erro ao processar JSON do relatório para tipo {}: {}", tipoEntidade, e.getMessage(), e);
        }
        
        return entidades;
    }
    
    /**
     * Encontra o nó que contém os dados no JSON, tentando diferentes estruturas possíveis.
     */
    private JsonNode encontrarNoDados(JsonNode rootNode) {
        // Lista de possíveis caminhos para os dados
        String[] caminhosPossiveis = {
            "data", "items", "results", "records", "content", 
            "manifestos", "localizacoes", "cargas", "entities"
        };
        
        // Primeiro, tenta encontrar diretamente no nó raiz
        for (String caminho : caminhosPossiveis) {
            JsonNode node = rootNode.get(caminho);
            if (node != null && !node.isNull()) {
                return node;
            }
        }
        
        // Se não encontrou, verifica se o próprio rootNode é um array
        if (rootNode.isArray()) {
            return rootNode;
        }
        
        // Se não encontrou nada específico, retorna o próprio rootNode
        return rootNode;
    }
    
    /**
     * Cria uma EntidadeDinamica a partir de um nó JSON.
     */
    private EntidadeDinamica criarEntidadeAPartirDoJson(JsonNode itemNode, String tipoEntidade) {
        try {
            EntidadeDinamica entidade = new EntidadeDinamica();
            entidade.setTipoEntidade(tipoEntidade);
            
            // Itera sobre todos os campos do JSON e adiciona à entidade
            itemNode.properties().forEach(entry -> {
                String campo = entry.getKey();
                JsonNode valorNode = entry.getValue();
                
                // Converte o valor para string, tratando diferentes tipos
                String valor = converterValorJsonParaString(valorNode);
                entidade.adicionarCampo(campo, valor);
            });
            
            return entidade;
            
        } catch (Exception e) {
            logger.error("Erro ao criar entidade a partir do JSON para tipo {}: {}", tipoEntidade, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Converte um valor JSON para string, tratando diferentes tipos de dados.
     */
    private String converterValorJsonParaString(JsonNode valorNode) {
        if (valorNode == null || valorNode.isNull()) {
            return null;
        }
        
        if (valorNode.isTextual()) {
            return valorNode.asText();
        }
        
        if (valorNode.isNumber()) {
            return valorNode.asText();
        }
        
        if (valorNode.isBoolean()) {
            return String.valueOf(valorNode.asBoolean());
        }
        
        if (valorNode.isArray() || valorNode.isObject()) {
            // Para arrays e objetos, converte para JSON string
            try {
                return objectMapper.writeValueAsString(valorNode);
            } catch (Exception e) {
                logger.debug("Erro ao converter valor complexo para string: {}", e.getMessage());
                return valorNode.toString();
            }
        }
        
        return valorNode.asText();
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
        // Usa apenas o fluxo documentado POST+GET conforme ARQUITETURA-TECNICA.md
        logger.info("Solicitando relatório {} usando fluxo POST+GET documentado", tipoRelatorio);
        
        // Formata a data para o padrão esperado pela API
        String dataFormatada = dataInicio.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        // Constrói o corpo da requisição conforme documentação
        String requestBody = String.format("{\n" +
                "    \"dataInicio\": \"%s\",\n" +
                "    \"formato\": \"json\",\n" +
                "    \"incluirDetalhes\": true\n" +
                "}", dataFormatada);
        
        // Usa o endpoint específico baseado no template
        String endpoint = obterEndpointPorTemplate(templateId);
        
        return enviarSolicitacaoRelatorio(endpoint, requestBody);
    }
    

    

    




    /**
     * Envia a solicitação POST para gerar o relatório.
     */
    private String enviarSolicitacaoRelatorio(String endpoint, String requestBody) throws IOException, InterruptedException {
        String url = urlBase + endpoint;
        
        Supplier<HttpRequest> requestSupplier = () -> HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(TIMEOUT_REQUISICAO_SEGUNDOS))
                .build();
        
        logger.debug("Enviando solicitação POST para: {}", url);
        HttpResponse<String> response = UtilitarioHttpRetry.executarComRetry(
                httpClient, 
                requestSupplier, 
                "enviarSolicitacaoRelatorio-" + endpoint
        );
        
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
            
            Supplier<HttpRequest> requestSupplier = () -> HttpRequest.newBuilder()
                    .uri(URI.create(statusUrl))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .timeout(Duration.ofSeconds(TIMEOUT_REQUISICAO_SEGUNDOS))
                    .build();
            
            HttpResponse<String> response = UtilitarioHttpRetry.executarComRetry(
                    httpClient, 
                    requestSupplier, 
                    "aguardarProcessamentoRelatorio-" + requestId
            );
            
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
        
        Supplier<HttpRequest> requestSupplier = () -> HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .header("Authorization", "Bearer " + token)
                .GET()
                .timeout(Duration.ofSeconds(TIMEOUT_REQUISICAO_SEGUNDOS))
                .build();
        
        HttpResponse<String> response = UtilitarioHttpRetry.executarComRetry(
                httpClient, 
                requestSupplier, 
                "baixarEProcessarRelatorio-" + tipoRelatorio
        );
        
        if (response.statusCode() == 200) {
            return processarJsonRelatorio(response.body(), tipoRelatorio);
        } else {
            logger.error("Erro ao baixar relatório. Status: {}, Body: {}", response.statusCode(), response.body());
            return new ArrayList<>();
        }
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
                
                Supplier<HttpRequest> requestSupplier = () -> HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .GET()
                        .timeout(Duration.ofSeconds(10))
                        .build();
                
                HttpResponse<String> resposta = UtilitarioHttpRetry.executarComRetry(
                        httpClient, 
                        requestSupplier, 
                        "validarAcessoApi-" + endpoint
                );
                
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
                
                Supplier<HttpRequest> requestSupplier = () -> HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .GET()
                        .timeout(Duration.ofSeconds(TIMEOUT_REQUISICAO_SEGUNDOS))
                        .build();
                
                HttpResponse<String> response = UtilitarioHttpRetry.executarComRetry(
                        httpClient, 
                        requestSupplier, 
                        "listarTemplatesDisponiveis-" + endpoint
                );
                
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
            Supplier<HttpRequest> requestSupplier = () -> HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();
            
            HttpResponse<String> response = UtilitarioHttpRetry.executarComRetry(
                    httpClient, 
                    requestSupplier, 
                    "verificarTemplateExiste-" + templateId
            );
            
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
     * Busca fretes detalhados a partir de uma data específica.
     * Orquestra o fluxo assíncrono completo: solicitar → aguardar → descarregar.
     * 
     * @param dataInicio Data de início para busca dos fretes
     * @return Lista de fretes encontrados ou lista vazia se falhar
     */
    public List<EntidadeDinamica> buscarFretesDetalhados(LocalDateTime dataInicio) {
        logger.info("Iniciando busca de fretes detalhados via API Data Export para data: {}", dataInicio);
        
        try {
            String requestId = solicitarRelatorioFretesDetalhados(dataInicio);
            if (requestId != null) {
                return buscarRelatorioProcessado(requestId, "fretes_detalhados");
            } else {
                logger.error("Falha ao solicitar relatório de fretes detalhados");
                return new ArrayList<>();
            }
        } catch (Exception e) {
            logger.error("Erro ao buscar fretes detalhados", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Solicita a geração do relatório de fretes detalhados via POST (versão pública).
     * 
     * @param dataInicio Data de início da busca (formato ISO: yyyy-MM-dd'T'HH:mm:ss)
     * @return Request ID do relatório ou null se falhar
     */
    public String solicitarRelatorioFretesDetalhados(String dataInicio) {
        try {
            LocalDateTime dataInicioDateTime = LocalDateTime.parse(dataInicio);
            return solicitarRelatorioFretesDetalhados(dataInicioDateTime);
        } catch (Exception e) {
            logger.error("Erro ao solicitar relatório de fretes detalhados", e);
            return null;
        }
    }

    /**
     * Solicita a geração do relatório de fretes detalhados via POST.
     */
    private String solicitarRelatorioFretesDetalhados(LocalDateTime dataInicio) throws IOException, InterruptedException {
        return solicitarRelatorioComTemplate(TEMPLATE_ID_FRETES, dataInicio, "fretes_detalhados");
    }

    /**
     * Aplica rate limiting para evitar erro HTTP 429.
     * Garante que haja pelo menos 2 segundos entre requisições consecutivas.
     */
}