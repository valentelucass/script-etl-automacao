package br.com.extrator.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.extrator.modelo.graphql.coletas.ColetaNodeDTO;
import br.com.extrator.modelo.graphql.fretes.FreteNodeDTO;
import br.com.extrator.util.CarregadorConfig;
import br.com.extrator.util.GerenciadorRequisicaoHttp;

/**
 * Cliente especializado para comunicação com a API GraphQL do ESL Cloud
 * Responsável por buscar dados de Coletas através de queries GraphQL
 * com proteções contra loops infinitos e circuit breaker.
 */
public class ClienteApiGraphQL {
    private static final Logger logger = LoggerFactory.getLogger(ClienteApiGraphQL.class);
    
    // PROTEÇÕES CONTRA LOOPS INFINITOS - Replicadas do ClienteApiRest
    private static final int MAX_REGISTROS_POR_EXECUCAO = 50000;
    private static final int INTERVALO_LOG_PROGRESSO = 50;
    
    // CIRCUIT BREAKER - Controle de falhas consecutivas
    private static final int MAX_FALHAS_CONSECUTIVAS = 5;
    private final Map<String, Integer> contadorFalhasConsecutivas = new HashMap<>();
    private final Set<String> entidadesComCircuitAberto = new HashSet<>();
    
    private final String urlBase;
    private final String endpointGraphQL;
    private final String token;
    private final HttpClient clienteHttp;
    private final ObjectMapper mapeadorJson;
    private final GerenciadorRequisicaoHttp gerenciadorRequisicao;
    private final Duration timeoutRequisicao;

    /**
     * Executa uma query GraphQL com paginação automática e proteções contra loops infinitos
     * 
     * @param query Query GraphQL a ser executada
     * @param nomeEntidade Nome da entidade na resposta GraphQL
     * @param variaveis Variáveis da query GraphQL
     * @param tipoClasse Classe para desserialização tipada
     * @return ResultadoExtracao indicando se a extração foi completa ou interrompida
     */
    private <T> ResultadoExtracao<T> executarQueryPaginada(final String query, final String nomeEntidade, final Map<String, Object> variaveis, final Class<T> tipoClasse) {
        final String chaveEntidade = "GraphQL-" + nomeEntidade;
        
        // CIRCUIT BREAKER - Verificar se a entidade está com circuit aberto
        if (entidadesComCircuitAberto.contains(chaveEntidade)) {
            logger.warn("⚠️ CIRCUIT BREAKER ATIVO - Entidade {} temporariamente desabilitada devido a falhas consecutivas", nomeEntidade);
            return ResultadoExtracao.completo(new ArrayList<>(), 0, 0);
        }
        
        logger.info("🔍 Executando query GraphQL paginada para entidade: {}", nomeEntidade);
        
        final List<T> todasEntidades = new ArrayList<>();
        String cursor = null;
        boolean hasNextPage = true;
        int paginaAtual = 1;
        int totalRegistrosProcessados = 0;
        boolean interrompido = false; // NOVO: Rastrear se foi interrompido
        
        // ✅ LER A CONFIGURAÇÃO UMA VEZ ANTES DO LOOP
        final int limitePaginas = CarregadorConfig.obterLimitePaginasApiGraphQL();

        while (hasNextPage) {
            try {
                // PROTEÇÃO 1: Limite máximo de páginas (agora usa a variável já lida)
                if (paginaAtual > limitePaginas) {
                    logger.warn("🚨 PROTEÇÃO ATIVADA - Entidade {}: Limite de {} páginas atingido. Interrompendo busca para evitar loop infinito.", 
                            nomeEntidade, limitePaginas);
                    interrompido = true; // NOVO: Marcar como interrompido
                    break;
                }

                // PROTEÇÃO 2: Limite máximo de registros
                if (totalRegistrosProcessados >= MAX_REGISTROS_POR_EXECUCAO) {
                    logger.warn("🚨 PROTEÇÃO ATIVADA - Entidade {}: Limite de {} registros atingido. Interrompendo busca para evitar sobrecarga.", 
                            nomeEntidade, MAX_REGISTROS_POR_EXECUCAO);
                    interrompido = true; // NOVO: Marcar como interrompido
                    break;
                }

                // Log de progresso a cada intervalo definido
                if (paginaAtual % INTERVALO_LOG_PROGRESSO == 0) {
                    logger.info("📊 Progresso GraphQL {}: Página {}, {} registros processados", 
                            nomeEntidade, paginaAtual, totalRegistrosProcessados);
                }

                logger.debug("Executando página {} da query GraphQL para {}", paginaAtual, nomeEntidade);
                
                // Adicionar cursor às variáveis se não for a primeira página
                final Map<String, Object> variaveisComCursor = new java.util.HashMap<>(variaveis);
                if (cursor != null) {
                    variaveisComCursor.put("after", cursor);
                }

                // Executar a query para esta página
                final PaginatedGraphQLResponse<T> resposta = executarQueryGraphQLTipado(query, nomeEntidade, variaveisComCursor, tipoClasse);
                
                // Adicionar entidades desta página ao resultado total
                todasEntidades.addAll(resposta.getEntidades());
                totalRegistrosProcessados += resposta.getEntidades().size();
                
                // Reset do contador de falhas em caso de sucesso
                contadorFalhasConsecutivas.put(chaveEntidade, 0);
                
                // Atualizar informações de paginação
                hasNextPage = resposta.getHasNextPage();
                cursor = resposta.getEndCursor();
                
                logger.debug("✅ Página {} processada: {} entidades encontradas. Próxima página: {} (Total: {})", 
                            paginaAtual, resposta.getEntidades().size(), hasNextPage, totalRegistrosProcessados);
                
                paginaAtual++;
                
                // Não é mais necessário pausar entre requisições - o GerenciadorRequisicaoHttp já controla o throttling
                
            } catch (final Exception e) {
                logger.error("💥 Erro ao executar query GraphQL para entidade {} página {}: {}", 
                        nomeEntidade, paginaAtual, e.getMessage(), e);
                incrementarContadorFalhas(chaveEntidade, nomeEntidade);
                break;
            }
        }

        // NOVO: Retornar ResultadoExtracao baseado na flag de interrupção
        if (interrompido) {
            logger.warn("⚠️ Query GraphQL INCOMPLETA - Entidade {}: {} registros extraídos em {} páginas (INTERROMPIDA por proteções)", 
                    nomeEntidade, totalRegistrosProcessados, paginaAtual - 1);
            return ResultadoExtracao.incompleto(todasEntidades, ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS, paginaAtual - 1, totalRegistrosProcessados);
        } else {
            // Log final com resultado claro e diferenciado
            if (todasEntidades.isEmpty()) {
                logger.info("❌ Query GraphQL concluída - Entidade {}: Nenhum registro encontrado", nomeEntidade);
            } else {
                logger.info("✅ Query GraphQL COMPLETA - Entidade {}: {} registros extraídos em {} páginas (Proteções: ✓ Ativas)", 
                        nomeEntidade, totalRegistrosProcessados, paginaAtual - 1);
            }
            return ResultadoExtracao.completo(todasEntidades, paginaAtual - 1, totalRegistrosProcessados);
        }
    }

    

    /**
     * Construtor da classe ClienteApiGraphQL
     * Inicializa as configurações necessárias para comunicação com a API GraphQL
     */
    public ClienteApiGraphQL() {
        this.urlBase = CarregadorConfig.obterUrlBaseApi();
        this.endpointGraphQL = CarregadorConfig.obterEndpointGraphQL();
        this.token = CarregadorConfig.obterTokenApiGraphQL();
        this.timeoutRequisicao = CarregadorConfig.obterTimeoutApiRest();
        this.clienteHttp = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapeadorJson = new ObjectMapper();
        this.gerenciadorRequisicao = new GerenciadorRequisicaoHttp();
    }

    /**
     * Busca coletas via GraphQL para as últimas 24h (ontem + hoje)
     * API GraphQL de coletas (PickInput) SÓ aceita 1 data específica em requestDate, não aceita intervalo.
     * Para obter coletas das últimas 24h, precisa buscar 2 dias separadamente.
     * 
     * @param dataReferencia Data de referência para buscar as coletas (LocalDate)
     * @return ResultadoExtracao indicando se a busca foi completa ou interrompida
     */
    public ResultadoExtracao<ColetaNodeDTO> buscarColetas(final LocalDate dataReferencia) {
        // Query GraphQL expandida conforme documentação em docs/descobertas-endpoints/coletas.md
        // Query: BuscarColetasExpandidaV2, Tipo: Pick
        final String query = """
                query BuscarColetasExpandidaV2($params: PickInput!, $after: String) {
                  pick(params: $params, after: $after, first: 100) {
                    edges {
                      cursor
                      node {
                        id
                        status
                        requestDate
                        serviceDate
                        sequenceCode
                        requestHour
                        serviceStartHour
                        finishDate
                        serviceEndHour
                        requester
                        corporation {
                          id
                          person { nickname cnpj }
                        }
                        customer { id name cnpj }
                        pickAddress {
                          line1
                          number
                          neighborhood
                          postalCode
                          city { name state { code } }
                        }
                        invoicesValue
                        invoicesWeight
                        invoicesVolumes
                        user { id name }
                        comments
                        agentId
                        manifestItemPickId
                        vehicleTypeId
                        invoicesCubedWeight
                        cancellationReason
                        cancellationUserId
                        cargoClassificationId
                        costCenterId
                        destroyReason
                        destroyUserId
                        lunchBreakEndHour
                        lunchBreakStartHour
                        notificationEmail
                        notificationPhone
                        pickTypeId
                        pickupLocationId
                        statusUpdatedAt
                      }
                    }
                    pageInfo { hasNextPage endCursor }
                  }
                }""";

        // Calcular dia anterior (ontem)
        final LocalDate diaAnterior = dataReferencia.minusDays(1);
        
        // Lista consolidada para armazenar todas as coletas
        final List<ColetaNodeDTO> todasColetas = new ArrayList<>();
        int totalPaginas = 0;
        boolean ambasCompletas = true;

        // 1. Buscar coletas do dia anterior (ontem)
        logger.info("🔍 Coletas - Dia 1/2: {}", diaAnterior);
        final String dataAnteriorFormatada = formatarDataParaApiGraphQL(diaAnterior);
        final Map<String, Object> variaveisDiaAnterior = Map.of(
            "params", Map.of("requestDate", dataAnteriorFormatada)
        );
        
        final ResultadoExtracao<ColetaNodeDTO> resultadoDiaAnterior = executarQueryPaginada(query, "pick", variaveisDiaAnterior, ColetaNodeDTO.class);
        todasColetas.addAll(resultadoDiaAnterior.getDados());
        totalPaginas += resultadoDiaAnterior.getPaginasProcessadas();
        
        if (resultadoDiaAnterior.isCompleto()) {
            logger.info("✅ Dia 1/2: {} coletas", resultadoDiaAnterior.getDados().size());
        } else {
            logger.warn("⚠️ Dia 1/2: {} coletas (INCOMPLETO)", resultadoDiaAnterior.getDados().size());
            ambasCompletas = false;
        }

        // 2. Buscar coletas do dia atual (hoje)
        logger.info("🔍 Coletas - Dia 2/2: {}", dataReferencia);
        final String dataAtualFormatada = formatarDataParaApiGraphQL(dataReferencia);
        final Map<String, Object> variaveisDataAtual = Map.of(
            "params", Map.of("requestDate", dataAtualFormatada)
        );
        
        final ResultadoExtracao<ColetaNodeDTO> resultadoDataAtual = executarQueryPaginada(query, "pick", variaveisDataAtual, ColetaNodeDTO.class);
        todasColetas.addAll(resultadoDataAtual.getDados());
        totalPaginas += resultadoDataAtual.getPaginasProcessadas();
        
        if (resultadoDataAtual.isCompleto()) {
            logger.info("✅ Dia 2/2: {} coletas", resultadoDataAtual.getDados().size());
        } else {
            logger.warn("⚠️ Dia 2/2: {} coletas (INCOMPLETO)", resultadoDataAtual.getDados().size());
            ambasCompletas = false;
        }

        // 3. Consolidar resultados
        logger.info("✅ Total: {} coletas", todasColetas.size());

        // 4. Retornar resultado consolidado
        if (ambasCompletas) {
            return ResultadoExtracao.completo(todasColetas, totalPaginas, todasColetas.size());
        } else {
            // Se qualquer uma das buscas foi incompleta, marcar o resultado como incompleto
            final ResultadoExtracao.MotivoInterrupcao motivo = ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS;
            return ResultadoExtracao.incompleto(todasColetas, motivo, totalPaginas, todasColetas.size());
        }
    }





    /**
     * Busca fretes via GraphQL para as últimas 24 horas a partir de uma data de referência.
     * 
     * @param dataReferencia Data de referência que representa o FIM do intervalo de busca.
     * @return ResultadoExtracao indicando se a busca foi completa ou interrompida
     */
    public ResultadoExtracao<FreteNodeDTO> buscarFretes(final LocalDate dataReferencia) {
        final String query = """
                query BuscarFretes_Master_V8($params: FreightInput!, $after: String) {
                  freight(params: $params, after: $after, first: 100) {
                    edges {
                      node {
                        id
                        referenceNumber
                        serviceAt
                        createdAt
                        cte { id key number series issuedAt createdAt emissionType }
                        total
                        subtotal
                        invoicesValue
                        invoicesWeight
                        taxedWeight
                        realWeight
                        cubagesCubedWeight
                        totalCubicVolume
                        invoicesTotalVolumes
                        freightInvoices { invoice { number series key value weight } }
                        sender { id name cnpj cpf inscricaoEstadual mainAddress { city { name state { code } } } }
                        receiver { id name cnpj cpf inscricaoEstadual mainAddress { city { name state { code } } } }
                        payer { id name cnpj cpf }
                        modal
                        modalCte
                        status
                        type
                        serviceDate
                        serviceType
                        deliveryPredictionDate
                        corporation { id nickname cnpj }
                        customerPriceTable { name }
                        freightClassification { name }
                        costCenter { name }
                        
                        originCity { name state { code } }
                        destinationCity { name state { code } }
                        destinationCityId
                        corporationId
                        freightWeightSubtotal
                        freightWeightSubtotal
                        globalized
                        globalizedType
                        grisSubtotal
                        adValoremSubtotal
                        insuranceAccountableType
                        insuranceEnabled
                        insuranceId
                        insuredValue
                        itrSubtotal
                        tollSubtotal
                        km
                        nfseNumber
                        nfseSeries
                        otherFees
                        paymentAccountableType
                        paymentType
                        previousDocumentType
                        priceTableAccountableType
                        productsValue
                        redispatchSubtotal
                        secCatSubtotal
                        suframaSubtotal
                        tdeSubtotal
                        fiscalDetail { cstType cfopCode calculationBasis taxRate taxValue pisRate pisValue cofinsRate cofinsValue hasDifal difalTaxValueOrigin difalTaxValueDestination }
                        trtSubtotal
                      }
                    }
                    pageInfo { hasNextPage endCursor }
                  }
                }""";

        // --- INÍCIO DAS MUDANÇAS ---

        // 1. Calcular o intervalo de 24 horas
        final LocalDate dataInicio = dataReferencia.minusDays(1);
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        final String intervaloServiceAt = dataInicio.format(formatter) + " - " + dataReferencia.format(formatter);

        // 2. Construir variáveis SEM o corporationId, usando o intervalo de datas
        final Map<String, Object> variaveis = Map.of(
            "params", Map.of("serviceAt", intervaloServiceAt)
        );

        // 3. Atualizar os logs para refletir a nova busca
        logger.info("🔍 Buscando fretes via GraphQL - Período: {}", intervaloServiceAt);
        logger.debug("Executando query GraphQL para fretes - URL: {}{}, Variáveis: {}", 
                    urlBase, endpointGraphQL, variaveis);

        final ResultadoExtracao<FreteNodeDTO> resultado = executarQueryPaginada(query, "freight", variaveis, FreteNodeDTO.class);
        
        // 4. Atualizar o log de resultado
        if (resultado.getDados().isEmpty()) {
            logger.warn("❌ Sem fretes encontrados para o período {}", intervaloServiceAt);
        } else {
            logger.info("✅ Encontrados {} fretes para o período {}", resultado.getDados().size(), intervaloServiceAt);
        }
        
        // --- FIM DAS MUDANÇAS ---
        
        return resultado;
    }

    /**
     * Executa uma query GraphQL de forma genérica e robusta com desserialização tipada
     * 
     * @param query        A query GraphQL a ser executada
     * @param nomeEntidade Nome da entidade para logs e tratamento de erros
     * @param variaveis    Variáveis da query GraphQL
     * @param tipoClasse   Classe para desserialização tipada
     * @return Resposta paginada contendo entidades tipadas e informações de paginação
     */
    private <T> PaginatedGraphQLResponse<T> executarQueryGraphQLTipado(final String query, final String nomeEntidade,
            final Map<String, Object> variaveis, final Class<T> tipoClasse) {
        logger.debug("Executando query GraphQL tipada para {} - URL: {}{}, Variáveis: {}", 
                    nomeEntidade, urlBase, endpointGraphQL, variaveis);
        final List<T> entidades = new ArrayList<>();
        boolean hasNextPage = false;
        String endCursor = null;

        // Validação básica de configuração
        if (urlBase == null || urlBase.isBlank() || token == null || token.isBlank()) {
            logger.error("Configurações inválidas para chamada GraphQL (urlBase/token)");
            return new PaginatedGraphQLResponse<>(entidades, false, null);
        }

        try {
            // Construir o corpo da requisição GraphQL usando ObjectMapper
            final ObjectNode corpoJson = mapeadorJson.createObjectNode();
            corpoJson.put("query", query);
            if (variaveis != null && !variaveis.isEmpty()) {
                corpoJson.set("variables", mapeadorJson.valueToTree(variaveis));
            }
            final String corpoRequisicao = mapeadorJson.writeValueAsString(corpoJson);

            // Construir a requisição HTTP
            final HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(urlBase + endpointGraphQL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(corpoRequisicao))
                    .timeout(this.timeoutRequisicao)
                    .build();

            // Executar a requisição usando o gerenciador central
            final HttpResponse<String> resposta = gerenciadorRequisicao.executarRequisicao(this.clienteHttp, requisicao, "GraphQL-" + nomeEntidade);

            // Parsear a resposta JSON
            final JsonNode respostaJson = mapeadorJson.readTree(resposta.body());

            // Verificar se há erros na resposta GraphQL
            if (respostaJson.has("errors")) {
                final JsonNode erros = respostaJson.get("errors");
                logger.error("Erros na query GraphQL para {}: {}", nomeEntidade, erros.toString());
                return new PaginatedGraphQLResponse<>(entidades, false, null);
            }

            // Extrair os dados da resposta
            if (!respostaJson.has("data")) {
                logger.warn("Resposta GraphQL sem campo 'data' para {}", nomeEntidade);
                return new PaginatedGraphQLResponse<>(entidades, false, null);
            }

            final JsonNode dados = respostaJson.get("data");
            if (!dados.has(nomeEntidade)) {
                logger.warn("Campo '{}' não encontrado na resposta GraphQL. Campos disponíveis: {}",
                        nomeEntidade, dados.fieldNames());
                return new PaginatedGraphQLResponse<>(entidades, false, null);
            }

            final JsonNode dadosEntidade = dados.get(nomeEntidade);

            // Verificar se a resposta segue o padrão paginado com edges/node
            if (dadosEntidade.has("edges")) {
                logger.debug("Processando resposta paginada com edges/node para {}", nomeEntidade);
                final JsonNode edges = dadosEntidade.get("edges");

                if (edges.isArray()) {
                    for (final JsonNode edge : edges) {
                        if (edge.has("node")) {
                            final JsonNode node = edge.get("node");
                            try {
                                // Deserializa diretamente para a classe tipada usando Jackson
                                final T entidade = mapeadorJson.treeToValue(node, tipoClasse);
                                entidades.add(entidade);
                            } catch (JsonProcessingException | IllegalArgumentException e) {
                                logger.warn("Erro ao deserializar node de {} para {}: {}", 
                                          nomeEntidade, tipoClasse.getSimpleName(), e.getMessage());
                            }
                        }
                    }
                }
                
                // Extrair informações de paginação do pageInfo
                if (dadosEntidade.has("pageInfo")) {
                    final JsonNode pageInfo = dadosEntidade.get("pageInfo");
                    if (pageInfo.has("hasNextPage")) {
                        hasNextPage = pageInfo.get("hasNextPage").asBoolean();
                    }
                    if (pageInfo.has("endCursor") && !pageInfo.get("endCursor").isNull()) {
                        endCursor = pageInfo.get("endCursor").asText();
                    }
                    logger.debug("Informações de paginação extraídas - hasNextPage: {}, endCursor: {}", hasNextPage, endCursor);
                }
            } else {
                // Processar resposta no formato antigo (array direto) para compatibilidade
                logger.debug("Processando resposta no formato antigo (array direto) para {}", nomeEntidade);

                if (dadosEntidade.isArray()) {
                    for (final JsonNode item : dadosEntidade) {
                        try {
                            // Deserializa diretamente para a classe tipada usando Jackson
                            final T entidade = mapeadorJson.treeToValue(item, tipoClasse);
                            entidades.add(entidade);
                        } catch (JsonProcessingException | IllegalArgumentException e) {
                            logger.warn("Erro ao deserializar item de {} para {}: {}", 
                                      nomeEntidade, tipoClasse.getSimpleName(), e.getMessage());
                        }
                    }
                }
            }

            logger.debug("Query GraphQL tipada concluída para {}. Total encontrado: {}", nomeEntidade, entidades.size());

        } catch (final JsonProcessingException e) {
            logger.error("Erro de processamento JSON durante execução da query GraphQL para {}: {}", nomeEntidade, e.getMessage(), e);
        } catch (final RuntimeException e) {
            logger.error("Erro durante execução da query GraphQL para {}: {}", nomeEntidade, e.getMessage(), e);
        }

        return new PaginatedGraphQLResponse<>(entidades, hasNextPage, endCursor);
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
            final String queryTeste = "{ __schema { queryType { name } } }";

            // Construir o corpo da requisição GraphQL usando ObjectMapper
            final ObjectNode corpoJson = mapeadorJson.createObjectNode();
            corpoJson.put("query", queryTeste);
            final String corpoRequisicao = mapeadorJson.writeValueAsString(corpoJson);

            final String url = urlBase + endpointGraphQL;
            final HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corpoRequisicao))
                    .build();

            final HttpResponse<String> resposta = clienteHttp.send(requisicao, HttpResponse.BodyHandlers.ofString());

            if (resposta.statusCode() == 200) {
                final JsonNode respostaJson = mapeadorJson.readTree(resposta.body());
                final boolean sucesso = !respostaJson.has("errors");

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

        } catch (java.io.IOException | InterruptedException e) {
            logger.error("❌ Erro durante validação da API GraphQL: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Formatar LocalDate para o formato esperado pela API GraphQL (YYYY-MM-DD).
     * 
     * @param data A data a ser formatada
     * @return String no formato YYYY-MM-DD
     */
    private String formatarDataParaApiGraphQL(final LocalDate data) {
        return data.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    /**
     * Incrementa o contador de falhas consecutivas e ativa o circuit breaker se necessário.
     * 
     * @param chaveEntidade Chave identificadora da entidade GraphQL
     * @param nomeEntidade Nome amigável da entidade para logs
     */
    private void incrementarContadorFalhas(final String chaveEntidade, final String nomeEntidade) {
        final int falhas = contadorFalhasConsecutivas.getOrDefault(chaveEntidade, 0) + 1;
        contadorFalhasConsecutivas.put(chaveEntidade, falhas);
        
        if (falhas >= MAX_FALHAS_CONSECUTIVAS) {
            entidadesComCircuitAberto.add(chaveEntidade);
            logger.error("🚨 CIRCUIT BREAKER ATIVADO - Entidade {} ({}): {} falhas consecutivas. Entidade temporariamente desabilitada.", 
                    chaveEntidade, nomeEntidade, falhas);
        } else {
            logger.warn("⚠️ Falha {}/{} para entidade {} ({})", falhas, MAX_FALHAS_CONSECUTIVAS, chaveEntidade, nomeEntidade);
        }
    }
}
