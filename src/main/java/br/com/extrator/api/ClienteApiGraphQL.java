package br.com.extrator.api;

import br.com.extrator.modelo.EntidadeDinamica;
import br.com.extrator.util.CarregadorConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
     * Busca coletas da API GraphQL
     * AVISO: A execução desta query depende da liberação de permissão pelo
     * administrador da API, mas o código está pronto.
     * 
     * @param dataInicio Data de início para filtrar as coletas
     * @param modoTeste  Se true, usa query simplificada para teste
     * @return Lista de coletas encontradas
     */
    public List<EntidadeDinamica> buscarColetas(String dataInicio, boolean modoTeste) {
        logger.info("Buscando coletas da API GraphQL...");

        try {
            // Formatação da data para o campo requestDate (API de Coletas espera data única)
            // CORREÇÃO: Sempre usar a data passada como parâmetro, independente do modo teste
            LocalDateTime dataReferencia = LocalDateTime.parse(dataInicio);
            
            // Formata a data como um valor único YYYY-MM-DD para a API de Coletas
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String dataUnica = dataReferencia.format(formatter);

            // Query GraphQL correta para coletas (baseada na descoberta da estrutura real
            // da API)
            String query = """
                    query BuscarColetas($params: PickInput!, $after: String) {
                        pick(params: $params, after: $after, first: 100) {
                        edges {
                            cursor
                            node {
                            id
                            agentId
                            cancellationReason
                            cancellationUserId
                            cargoClassificationId
                            comments
                            costCenterId
                            destroyReason
                            destroyUserId
                            invoicesCubedWeight
                            invoicesValue
                            invoicesVolumes
                            invoicesWeight
                            lunchBreakEndHour
                            lunchBreakStartHour
                            notificationEmail
                            notificationPhone
                            pickTypeId
                            pickupLocationId
                            requestDate
                            requestHour
                            requester
                            sequenceCode
                            serviceDate
                            serviceEndHour
                            serviceStartHour
                            status
                            statusUpdatedAt
                            taxedWeight
                            vehicleTypeId
                            }
                        }
                        pageInfo {
                            hasNextPage
                            endCursor
                        }
                        }
                    }""";
            Map<String, Object> variaveis = Map.of("params", Map.of("requestDate", dataUnica));

            logger.info("Executando query GraphQL para coletas com requestDate = {}", dataUnica);
            List<EntidadeDinamica> resultado = executarQueryGraphQL(query, "pick", variaveis);

            logger.info("Query GraphQL concluída para coletas. Total encontrado: {}", resultado.size());
            return resultado;

        } catch (Exception e) {
            logger.error("Erro ao buscar coletas: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Busca coletas das últimas 24 horas
     * 
     * @return Lista de coletas das últimas 24 horas
     */
    public List<EntidadeDinamica> buscarColetasUltimas24Horas() {
        LocalDateTime agora = LocalDateTime.now();
        String dataInicio = agora.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return buscarColetas(dataInicio, false);
    }

    /**
     * Realiza introspecção GraphQL para descobrir os campos disponíveis em um tipo
     * específico
     * 
     * @param nomeDoTipo Nome do tipo GraphQL a ser inspecionado (ex:
     *                   "FreightInput")
     * @return Lista de campos disponíveis no tipo
     */
    public List<String> inspecionarTipoGraphQL(String nomeDoTipo) {
        logger.info("Realizando introspecção detalhada do tipo GraphQL: {}", nomeDoTipo);

        try {
            // Query de introspecção mais detalhada para descobrir campos de um tipo
            // específico
            String queryIntrospeccao = "query IntrospectType($typeName: String!) { " +
                    "__type(name: $typeName) { " +
                    "name " +
                    "kind " +
                    "description " +
                    "inputFields { " +
                    "name " +
                    "description " +
                    "type { " +
                    "name " +
                    "kind " +
                    "ofType { " +
                    "name " +
                    "kind " +
                    "} " +
                    "} " +
                    "defaultValue " +
                    "} " +
                    "fields { " +
                    "name " +
                    "description " +
                    "type { " +
                    "name " +
                    "kind " +
                    "ofType { " +
                    "name " +
                    "kind " +
                    "} " +
                    "} " +
                    "} " +
                    "} " +
                    "}";

            // Construir variáveis para a query de introspecção
            Map<String, Object> variaveis = Map.of("typeName", nomeDoTipo);

            // Executar query de introspecção
            String urlCompleta = urlBase + endpointGraphQL;

            ObjectNode requestBody = mapeadorJson.createObjectNode();
            requestBody.put("query", queryIntrospeccao);
            requestBody.set("variables", mapeadorJson.valueToTree(variaveis));

            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(urlCompleta))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> resposta = clienteHttp.send(requisicao, HttpResponse.BodyHandlers.ofString());

            if (resposta.statusCode() != 200) {
                logger.error("Erro HTTP na introspecção: {} - {}", resposta.statusCode(), resposta.body());
                return new ArrayList<>();
            }

            JsonNode jsonResposta = mapeadorJson.readTree(resposta.body());

            // Verificar se há erros GraphQL
            if (jsonResposta.has("errors")) {
                logger.error("Erros GraphQL na introspecção: {}", jsonResposta.get("errors"));
                return new ArrayList<>();
            }

            // Extrair campos do tipo
            List<String> campos = new ArrayList<>();
            JsonNode tipoNode = jsonResposta.path("data").path("__type");

            if (tipoNode.isNull() || tipoNode.isMissingNode()) {
                logger.warn("Tipo '{}' não encontrado na API GraphQL", nomeDoTipo);
                return campos;
            }

            String tipoKind = tipoNode.path("kind").asText();
            String tipoDescricao = tipoNode.path("description").asText("");

            logger.info("Tipo encontrado: {} (kind: {}) - {}", nomeDoTipo, tipoKind, tipoDescricao);

            // Processar inputFields (para INPUT_OBJECT)
            if (tipoNode.has("inputFields") && !tipoNode.get("inputFields").isNull()) {
                JsonNode inputFields = tipoNode.get("inputFields");
                logger.info("Processando {} inputFields para {}", inputFields.size(), nomeDoTipo);

                for (JsonNode campo : inputFields) {
                    String nomeCampo = campo.path("name").asText();
                    String descricaoCampo = campo.path("description").asText("");
                    String valorPadrao = campo.path("defaultValue").asText("");

                    JsonNode tipoCampoNode = campo.path("type");
                    String tipoCampo = obterTipoCompleto(tipoCampoNode);

                    campos.add(nomeCampo);
                    logger.info("  ✓ Campo: {} (tipo: {}) - {} [padrão: {}]",
                            nomeCampo, tipoCampo, descricaoCampo, valorPadrao);
                }
            }

            // Processar fields (para OBJECT)
            if (tipoNode.has("fields") && !tipoNode.get("fields").isNull()) {
                JsonNode fields = tipoNode.get("fields");
                logger.info("Processando {} fields para {}", fields.size(), nomeDoTipo);

                for (JsonNode campo : fields) {
                    String nomeCampo = campo.path("name").asText();
                    String descricaoCampo = campo.path("description").asText("");

                    JsonNode tipoCampoNode = campo.path("type");
                    String tipoCampo = obterTipoCompleto(tipoCampoNode);

                    campos.add(nomeCampo);
                    logger.info("  ✓ Campo: {} (tipo: {}) - {}", nomeCampo, tipoCampo, descricaoCampo);
                }
            }

            logger.info("Introspecção concluída. Encontrados {} campos no tipo {} (kind: {})",
                    campos.size(), nomeDoTipo, tipoKind);
            return campos;

        } catch (Exception e) {
            logger.error("Erro na introspecção do tipo {}: {}", nomeDoTipo, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Extrai o tipo completo de um nó de tipo GraphQL, incluindo tipos aninhados
     */
    private String obterTipoCompleto(JsonNode tipoNode) {
        if (tipoNode.isNull() || tipoNode.isMissingNode()) {
            return "Unknown";
        }

        String nome = tipoNode.path("name").asText();
        String kind = tipoNode.path("kind").asText();

        if (!nome.isEmpty()) {
            return nome + " (" + kind + ")";
        }

        // Se não tem nome, pode ser um tipo wrapper (NON_NULL, LIST)
        JsonNode ofType = tipoNode.path("ofType");
        if (!ofType.isNull() && !ofType.isMissingNode()) {
            String tipoInterno = obterTipoCompleto(ofType);
            if ("NON_NULL".equals(kind)) {
                return tipoInterno + "!";
            } else if ("LIST".equals(kind)) {
                return "[" + tipoInterno + "]";
            }
            return tipoInterno + " (" + kind + ")";
        }

        return kind;
    }

    /**
     * Busca fretes da API GraphQL
     * 
     * @param dataInicio Data de início para filtrar os fretes
     * @param modoTeste  Se está em modo de teste
     * @return Lista de fretes encontradas
     */
    public List<EntidadeDinamica> buscarFretes(String dataInicio, boolean modoTeste) {
        logger.info("Iniciando busca de fretes via GraphQL a partir de: {} (Modo Teste: {})", dataInicio, modoTeste);

        

        try {
            // Código corrigido que trata dataInicio como data final e calcula um dia anterior como data inicial
            LocalDateTime dataReferencia = LocalDateTime.parse(dataInicio);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            // Calcula a data inicial (um dia antes) e usa dataInicio como data final
            LocalDateTime dataInicialCalculada = dataReferencia.minusDays(1);
            String dataInicialFormatada = dataInicialCalculada.format(formatter);
            String dataFinalFormatada = dataReferencia.format(formatter);
            String intervaloDatas = dataInicialFormatada + " - " + dataFinalFormatada;

            // Query GraphQL correta para fretes (baseada na análise dos logs - única que
            // funciona)
            String query = """
                    query BuscarFretes($params: FreightInput!, $after: String) {
                        freight(params: $params, after: $after, first: 100) {
                        edges {
                            cursor
                            node {
                            id
                            accountingCreditId
                            accountingCreditInstallmentId
                            adValoremSubtotal
                            additionalsSubtotal
                            admFeeSubtotal
                            calculationType
                            collectSubtotal
                            comments
                            corporationId
                            costCenterId
                            createdAt
                            cubagesCubedWeight
                            customerPriceTableId
                            deliveryDeadlineInDays
                            deliveryPredictionDate
                            deliveryPredictionHour
                            deliveryRegionId
                            deliverySubtotal
                            destinationCityId
                            dispatchSubtotal
                            draftEmissionAt
                            emergencySubtotal
                            emissionType
                            finishedAt
                            freightClassificationId
                            freightCubagesCount
                            freightInvoicesCount
                            freightWeightSubtotal
                            globalized
                            globalizedType
                            grisSubtotal
                            insuranceAccountableType
                            insuranceEnabled
                            insuranceId
                            insuredValue
                            invoicesTotalVolumes
                            invoicesValue
                            invoicesWeight
                            itrSubtotal
                            km
                            modal
                            modalCte
                            nfseNumber
                            nfseSeries
                            otherFees
                            paymentAccountableType
                            paymentType
                            previousDocumentType
                            priceTableAccountableType
                            productsValue
                            realWeight
                            redispatchSubtotal
                            referenceNumber
                            secCatSubtotal
                            serviceAt
                            serviceDate
                            serviceType
                            status
                            subtotal
                            suframaSubtotal
                            taxedWeight
                            tdeSubtotal
                            tollSubtotal
                            total
                            totalCubicVolume
                            trtSubtotal
                            type
                            }
                        }
                        pageInfo {
                            hasNextPage
                            endCursor
                        }
                        }
                    }""";
            
            // Converte o corporationId de String para Integer
            String corporationIdStr = CarregadorConfig.obterCorporationId();
            Integer corporationIdInt = Integer.parseInt(corporationIdStr);

            Map<String, Object> variaveis = Map.of("params", Map.of(
                "serviceAt", intervaloDatas,       // Envia o intervalo de texto
                "corporationId", corporationIdInt // Envia o ID como número
            ));

            logger.info("Executando query GraphQL para fretes com serviceAt = {} e corporationId = {}", intervaloDatas, corporationIdInt);
            List<EntidadeDinamica> resultado = executarQueryGraphQL(query, "freight", variaveis);

            logger.info("Query GraphQL concluída para fretes. Total encontrado: {}", resultado.size());
            return resultado;

        } catch (Exception e) {
            logger.error("Erro ao buscar fretes: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
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

        return buscarFretes(dataInicio, false);
    }

    /**
     * Executa uma query GraphQL de forma genérica e robusta
     * 
     * @param query        A query GraphQL a ser executada
     * @param nomeEntidade Nome da entidade para logs e tratamento de erros
     * @return Lista de entidades encontradas
     */
    private List<EntidadeDinamica> executarQueryGraphQL(String query, String nomeEntidade,
            Map<String, Object> variaveis) {
        logger.info("Executando query GraphQL para {}", nomeEntidade);
        List<EntidadeDinamica> entidades = new ArrayList<>();

        // Validação básica de configuração
        if (urlBase == null || urlBase.isBlank() || token == null || token.isBlank()) {
            logger.error("Configurações inválidas para chamada GraphQL (urlBase/token)");
            return entidades;
        }

        try {

            // Construir o corpo da requisição GraphQL usando ObjectMapper
            ObjectNode corpoJson = mapeadorJson.createObjectNode();
            corpoJson.put("query", query);
            if (variaveis != null && !variaveis.isEmpty()) {
                corpoJson.set("variables", mapeadorJson.valueToTree(variaveis));
            }
            final String corpoRequisicao = mapeadorJson.writeValueAsString(corpoJson);

            logger.info("Corpo JSON exato da requisição: {}", corpoRequisicao);

            
            // Cria a requisição HTTP como um Supplier para ser passada ao utilitário de
            // retry
            final String finalUrl = urlBase + endpointGraphQL;
            java.util.function.Supplier<HttpRequest> fornecedorRequisicao = () -> HttpRequest.newBuilder()
                    .uri(URI.create(finalUrl))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corpoRequisicao))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            // Executa a requisição usando o novo utilitário com throttling e backoff
            // exponencial
            HttpResponse<String> resposta = br.com.extrator.util.UtilitarioHttpRetry.executarComRetry(
                    this.clienteHttp,
                    fornecedorRequisicao,
                    nomeEntidade // Nome da operação para os logs
            );

            // Se a resposta for nula ou o status não for 200, retorna lista vazia.
            // O UtilitarioHttpRetry já cuidou de logar os erros.
            if (resposta == null || resposta.statusCode() != 200) {
                if (resposta != null) { // Log adicional se o erro não for 429
                    logger.error("Erro HTTP {} ao buscar {}: {}", resposta.statusCode(), nomeEntidade, resposta.body());
                }
                return entidades;
            }

            // Processar a resposta JSON
            JsonNode respostaJson = mapeadorJson.readTree(resposta.body());

            // Verificar se há erros na resposta GraphQL
            if (respostaJson.has("errors")) {
                JsonNode erros = respostaJson.get("errors");
                logger.error("Erros GraphQL ao buscar {}: {}", nomeEntidade, erros.toString());
                return entidades; // Retorna lista vazia em caso de erro
            }

            // Verificar se há dados na resposta
            if (!respostaJson.has("data")) {
                logger.warn("Resposta GraphQL sem campo 'data' para {}", nomeEntidade);
                return entidades;
            }

            JsonNode dados = respostaJson.get("data");

            // Tentar encontrar os dados da entidade (pode ter nomes diferentes)
            JsonNode dadosEntidade = null;
            String[] possiveisNomes = { nomeEntidade, nomeEntidade + "s",
                    nomeEntidade.toLowerCase(), nomeEntidade.toLowerCase() + "s" };

            for (String nome : possiveisNomes) {
                if (dados.has(nome)) {
                    dadosEntidade = dados.get(nome);
                    break;
                }
            }

            if (dadosEntidade == null) {
                logger.warn("Campo '{}' não encontrado na resposta GraphQL. Campos disponíveis: {}",
                        nomeEntidade, dados.fieldNames());
                return entidades;
            }

            // Verificar se a resposta segue o padrão paginado com edges/node
            if (dadosEntidade.has("edges")) {
                logger.debug("Processando resposta paginada com edges/node para {}", nomeEntidade);
                JsonNode edges = dadosEntidade.get("edges");

                if (edges.isArray()) {
                    for (JsonNode edge : edges) {
                        if (edge.has("node")) {
                            JsonNode node = edge.get("node");
                            try {
                                // Cria uma nova entidade dinâmica
                                EntidadeDinamica entidade = new EntidadeDinamica();
                                entidade.setTipoEntidade(nomeEntidade);

                                // Processa cada campo do node
                                node.properties().forEach(campo -> {
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
                                    } else if (valorCampo.isObject() || valorCampo.isArray()) {
                                        // Para objetos aninhados, converte para string JSON
                                        valor = valorCampo.toString();
                                    } else {
                                        valor = valorCampo.toString();
                                    }

                                    entidade.adicionarCampo(nomeCampo, valor);
                                });

                                entidades.add(entidade);
                            } catch (Exception e) {
                                logger.warn("Erro ao processar node de {}: {}", nomeEntidade, e.getMessage());
                            }
                        }
                    }
                }
            } else {
                // Processar resposta no formato antigo (array direto) para compatibilidade
                logger.debug("Processando resposta no formato antigo (array direto) para {}", nomeEntidade);

                if (dadosEntidade.isArray()) {
                    for (JsonNode item : dadosEntidade) {
                        try {
                            // Cria uma nova entidade dinâmica
                            EntidadeDinamica entidade = new EntidadeDinamica();
                            entidade.setTipoEntidade(nomeEntidade);

                            // Processa cada campo do JSON
                            item.properties().forEach(campo -> {
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
                                } else if (valorCampo.isObject() || valorCampo.isArray()) {
                                    // Para objetos aninhados (como cliente, endereco), converte para string JSON
                                    valor = valorCampo.toString();
                                } else {
                                    valor = valorCampo.toString();
                                }

                                entidade.adicionarCampo(nomeCampo, valor);
                            });

                            entidades.add(entidade);
                        } catch (Exception e) {
                            logger.warn("Erro ao processar item de {}: {}", nomeEntidade, e.getMessage());
                        }
                    }
                }
            }

            logger.info("Query GraphQL concluída para {}. Total encontrado: {}", nomeEntidade, entidades.size());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Thread interrompida durante execução da query GraphQL para {}", nomeEntidade);
        } catch (Exception e) {
            logger.error("Erro durante execução da query GraphQL para {}: {}", nomeEntidade, e.getMessage(), e);
        }

        return entidades;
    }

    /**
     * Valida se as credenciais de acesso à API GraphQL estão funcionando
     * 
     * @return true se a validação foi bem-sucedida, false caso contrário
     */
    public boolean validarAcessoApi() {
        logger.info("Validando acesso à API GraphQL...");

        try {
            // Query simples para testar a conectividade
            String queryTeste = "{ __schema { queryType { name } } }";

            // Construir o corpo da requisição GraphQL usando ObjectMapper
            ObjectNode corpoJson = mapeadorJson.createObjectNode();
            corpoJson.put("query", queryTeste);
            String corpoRequisicao = mapeadorJson.writeValueAsString(corpoJson);

            String url = urlBase + endpointGraphQL;
            HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corpoRequisicao))
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