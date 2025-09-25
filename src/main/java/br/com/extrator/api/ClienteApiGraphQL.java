package br.com.extrator.api;

import br.com.extrator.modelo.EntidadeDinamica;
import br.com.extrator.util.CarregadorConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * Cliente especializado para comunicação com a API GraphQL do ESL Cloud
 * Responsável por buscar dados de Coletas através de queries GraphQL
 */
public class ClienteApiGraphQL {
    private static final Logger logger = LoggerFactory.getLogger(ClienteApiGraphQL.class);
    private final String urlBase;
    private final String endpointGraphQL;
    private final String token;
    private final HttpClient clienteHttp;
    private final ObjectMapper mapeadorJson;

    /**
     * Construtor da classe ClienteApiGraphQL
     * Inicializa as configurações necessárias para comunicação com a API GraphQL
     */
    public ClienteApiGraphQL() {
        this.urlBase = CarregadorConfig.obterUrlBaseApi();
        this.endpointGraphQL = CarregadorConfig.obterEndpointGraphQL();
        this.token = CarregadorConfig.obterTokenApiGraphQL();
        this.clienteHttp = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapeadorJson = new ObjectMapper();
    }

    /**
     * Busca coletas a partir de uma data específica
     * @param dataInicio Data de início da busca no formato ISO (ex: "2025-01-20T00:00:00")
     * @return Lista de entidades de coletas encontradas
     */
    public List<EntidadeDinamica> buscarColetas(String dataInicio) {
        logger.warn("Simulação: API GraphQL indisponível. Retornando lista vazia para 'coletas'.");
        return new ArrayList<>();
    }

    /**
     * Busca coletas das últimas 24 horas
     * @return Lista de entidades de coletas encontradas
     */
    public List<EntidadeDinamica> buscarColetasUltimas24Horas() {
        LocalDateTime dataInicio = LocalDateTime.now().minusHours(24);
        String dataInicioFormatada = dataInicio.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return buscarColetas(dataInicioFormatada);
    }

    /**
     * Busca fretes a partir de uma data específica
     * @param dataInicio Data de início da busca no formato ISO (ex: "2025-01-20T00:00:00")
     * @return Lista de entidades de fretes encontradas
     */
    public List<EntidadeDinamica> buscarFretes(String dataInicio) {
        logger.warn("Simulação: API GraphQL indisponível. Retornando lista vazia para 'fretes'.");
        return new ArrayList<>();
    }

    /**
     * Busca fretes das últimas 24 horas usando a API GraphQL.
     * 
     * @return Lista de fretes encontrados
     */
    public List<EntidadeDinamica> buscarFretesUltimas24Horas() {
        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime ontemMesmaHora = agora.minusHours(24);
        String dataInicio = ontemMesmaHora.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        return buscarFretes(dataInicio);
    }

    /**
     * Valida se as credenciais de acesso à API GraphQL estão funcionando
     * @return true se a validação foi bem-sucedida, false caso contrário
     */
    public boolean validarAcessoApi() {
        logger.info("Validando acesso à API GraphQL...");
        
        try {
            // Query simples para testar a conectividade
            String queryTeste = "{\n" +
                    "  \"query\": \"{ __schema { queryType { name } } }\"\n" +
                    "}";
            
            String url = urlBase + endpointGraphQL;
            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(queryTeste))
                    .build();
            
            HttpResponse<String> resposta = clienteHttp.send(requisicao, HttpResponse.BodyHandlers.ofString());
            
            if (resposta.statusCode() == 200) {
                JsonNode respostaJson = mapeadorJson.readTree(resposta.body());
                boolean sucesso = !respostaJson.has("errors");
                
                if (sucesso) {
                    logger.info("✅ Validação da API GraphQL bem-sucedida");
                } else {
                    logger.error("❌ Erro na validação da API GraphQL: {}", respostaJson.get("errors"));
                }
                
                return sucesso;
            } else {
                logger.error("❌ Falha na validação da API GraphQL. Status: {}", resposta.statusCode());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("❌ Erro durante validação da API GraphQL: {}", e.getMessage(), e);
            return false;
        }
    }
}