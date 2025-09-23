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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        logger.info("Iniciando busca de coletas a partir de: {}", dataInicio);
        
        String query = construirQueryColetas(dataInicio);
        return executarQueryGraphQL(query, "coletas");
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
     * Constrói a query GraphQL para buscar coletas
     * @param dataInicio Data de início da busca
     * @return String com a query GraphQL formatada
     */
    private String construirQueryColetas(String dataInicio) {
        return String.format("{\n" +
                "  \"query\": \"query BuscarColetas($dataInicio: String!) { \n" +
                "    coletas(where: { createdAt: { gte: $dataInicio } }) { \n" +
                "      id \n" +
                "      numero \n" +
                "      status \n" +
                "      dataColeta \n" +
                "      endereco { \n" +
                "        logradouro \n" +
                "        numero \n" +
                "        bairro \n" +
                "        cidade \n" +
                "        uf \n" +
                "        cep \n" +
                "      } \n" +
                "      cliente { \n" +
                "        id \n" +
                "        nome \n" +
                "        documento \n" +
                "      } \n" +
                "      itens { \n" +
                "        id \n" +
                "        descricao \n" +
                "        quantidade \n" +
                "        peso \n" +
                "        valor \n" +
                "      } \n" +
                "      createdAt \n" +
                "      updatedAt \n" +
                "    } \n" +
                "  }\",\n" +
                "  \"variables\": {\n" +
                "    \"dataInicio\": \"%s\"\n" +
                "  }\n" +
                "}", dataInicio);
    }

    /**
     * Executa uma query GraphQL e processa a resposta
     * @param query Query GraphQL a ser executada
     * @param tipoEntidade Tipo de entidade para logging
     * @return Lista de entidades processadas
     */
    private List<EntidadeDinamica> executarQueryGraphQL(String query, String tipoEntidade) {
        List<EntidadeDinamica> entidades = new ArrayList<>();
        
        // Validação básica de configuração
        if (urlBase == null || urlBase.isBlank() || endpointGraphQL == null || endpointGraphQL.isBlank() || token == null || token.isBlank()) {
            logger.error("Configurações inválidas para chamada GraphQL (urlBase/endpoint/token)");
            return entidades;
        }

        try {
            String url = urlBase + endpointGraphQL;
            logger.debug("Executando query GraphQL em: {}", url);
            
            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(query))
                    .build();
            
            long inicioMs = System.currentTimeMillis();
            HttpResponse<String> resposta = clienteHttp.send(requisicao, HttpResponse.BodyHandlers.ofString());
            long duracaoMs = System.currentTimeMillis() - inicioMs;
            
            if (resposta.statusCode() == 200) {
                String corpo = resposta.body();
                if (corpo == null || corpo.isBlank()) {
                    logger.error("Resposta vazia da API GraphQL (200) em {} ({} ms)", url, duracaoMs);
                    return entidades;
                }
                JsonNode respostaJson = mapeadorJson.readTree(corpo);
                
                // Verifica se há erros na resposta GraphQL
                if (respostaJson.has("errors")) {
                    logger.error("Erro na query GraphQL: {}", respostaJson.get("errors"));
                    return entidades;
                }
                
                // Processa os dados retornados
                JsonNode dados = respostaJson.get("data");
                if (dados != null && dados.has(tipoEntidade)) {
                    JsonNode arrayEntidades = dados.get(tipoEntidade);
                    
                    if (arrayEntidades.isArray()) {
                        for (JsonNode entidadeJson : arrayEntidades) {
                            EntidadeDinamica entidade = processarEntidadeGraphQL(entidadeJson);
                            entidades.add(entidade);
                        }
                    }
                }
                
                logger.info("Query GraphQL executada com sucesso ({} ms). {} {} encontradas", duracaoMs, entidades.size(), tipoEntidade);
                
            } else {
                logger.error("Erro na requisição GraphQL. Status: {}, ({} ms) Resposta: {}", 
                           resposta.statusCode(), duracaoMs, resposta.body());
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Execução interrompida ao executar query GraphQL: {}", e.getMessage(), e);
        } catch (IOException e) {
            logger.error("Erro de I/O ao executar query GraphQL: {}", e.getMessage(), e);
        }
        
        return entidades;
    }

    /**
     * Processa uma entidade retornada pela API GraphQL
     * @param entidadeJson Nó JSON da entidade
     * @return EntidadeDinamica processada
     */
    private EntidadeDinamica processarEntidadeGraphQL(JsonNode entidadeJson) {
        EntidadeDinamica entidade = new EntidadeDinamica();
        
        // Processa todos os campos da entidade
        entidadeJson.fields().forEachRemaining(campo -> {
            String nomeCampo = campo.getKey();
            JsonNode valorCampo = campo.getValue();
            
            if (valorCampo.isObject()) {
                // Para objetos aninhados, converte para Map
                Map<String, Object> objetoAninhado = new HashMap<>();
                valorCampo.fields().forEachRemaining(subcampo -> {
                    objetoAninhado.put(subcampo.getKey(), extrairValor(subcampo.getValue()));
                });
                entidade.adicionarCampo(nomeCampo, objetoAninhado);
            } else if (valorCampo.isArray()) {
                // Para arrays, converte para List
                List<Object> lista = new ArrayList<>();
                valorCampo.forEach(item -> {
                    if (item.isObject()) {
                        Map<String, Object> itemMap = new HashMap<>();
                        item.fields().forEachRemaining(subcampo -> {
                            itemMap.put(subcampo.getKey(), extrairValor(subcampo.getValue()));
                        });
                        lista.add(itemMap);
                    } else {
                        lista.add(extrairValor(item));
                    }
                });
                entidade.adicionarCampo(nomeCampo, lista);
            } else {
                entidade.adicionarCampo(nomeCampo, extrairValor(valorCampo));
            }
        });
        
        return entidade;
    }

    /**
     * Extrai o valor de um nó JSON baseado no seu tipo
     * @param no Nó JSON
     * @return Valor extraído
     */
    private Object extrairValor(JsonNode no) {
        if (no.isNull()) {
            return null;
        } else if (no.isBoolean()) {
            return no.asBoolean();
        } else if (no.isInt()) {
            return no.asInt();
        } else if (no.isLong()) {
            return no.asLong();
        } else if (no.isDouble()) {
            return no.asDouble();
        } else {
            return no.asText();
        }
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