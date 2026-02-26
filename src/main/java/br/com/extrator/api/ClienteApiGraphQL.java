package br.com.extrator.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.extrator.db.entity.PageAuditEntity;
import br.com.extrator.db.repository.PageAuditRepository;
import br.com.extrator.api.constantes.ConstantesApiGraphQL;
import br.com.extrator.api.graphql.GraphQLIntervaloHelper;
import br.com.extrator.api.graphql.GraphQLQueries;
import br.com.extrator.util.validacao.ConstantesEntidades;
import br.com.extrator.modelo.graphql.coletas.ColetaNodeDTO;
import br.com.extrator.modelo.graphql.fretes.FreteNodeDTO;
import br.com.extrator.modelo.graphql.faturas.CreditCustomerBillingNodeDTO;
import br.com.extrator.modelo.graphql.bancos.BankAccountNodeDTO;
import br.com.extrator.util.ThreadUtil;
import br.com.extrator.util.configuracao.CarregadorConfig;
import br.com.extrator.util.http.GerenciadorRequisicaoHttp;
import br.com.extrator.util.formatacao.FormatadorData;

/**
 * Cliente especializado para comunicaÃƒÂ§ÃƒÂ£o com a API GraphQL do ESL Cloud
 * ResponsÃƒÂ¡vel por buscar dados de Coletas atravÃƒÂ©s de queries GraphQL
 * com proteÃƒÂ§ÃƒÂµes contra loops infinitos e circuit breaker.
 */
public class ClienteApiGraphQL {
    private static final Logger logger = LoggerFactory.getLogger(ClienteApiGraphQL.class);
    
    // PROTEÃƒâ€¡Ãƒâ€¢ES CONTRA LOOPS INFINITOS - Replicadas do ClienteApiRest
    // PROBLEMA #7 CORRIGIDO: Valor agora obtido de CarregadorConfig
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
    private final PageAuditRepository pageAuditRepository;
    private String executionUuid;
    private volatile Set<String> camposPickInputCache;

    /**
     * Executa uma query GraphQL com paginaÃƒÂ§ÃƒÂ£o automÃƒÂ¡tica e proteÃƒÂ§ÃƒÂµes contra loops infinitos
     * 
     * @param query Query GraphQL a ser executada
     * @param nomeEntidade Nome da entidade na resposta GraphQL
     * @param variaveis VariÃƒÂ¡veis da query GraphQL
     * @param tipoClasse Classe para desserializaÃƒÂ§ÃƒÂ£o tipada
     * @return ResultadoExtracao indicando se a extraÃƒÂ§ÃƒÂ£o foi completa ou interrompida
     */
    private <T> ResultadoExtracao<T> executarQueryPaginada(final String query,
                                                           final String nomeEntidade,
                                                           final Map<String, Object> variaveis,
                                                           final Class<T> tipoClasse) {
        final String chaveEntidade = "GraphQL-" + nomeEntidade;

        if (entidadesComCircuitAberto.contains(chaveEntidade)) {
            logger.warn("Circuit breaker ativo para entidade {}", nomeEntidade);
            final java.util.List<T> vazio = new java.util.ArrayList<>();
            return ResultadoExtracao.incompleto(vazio, ResultadoExtracao.MotivoInterrupcao.CIRCUIT_BREAKER, 0, 0);
        }

        logger.info("Executando query GraphQL paginada para entidade: {}", nomeEntidade);

        final List<T> todasEntidades = new ArrayList<>();
        String cursor = null;
        boolean hasNextPage = true;
        int paginaAtual = 1;
        int totalRegistrosProcessados = 0;
        boolean interrompido = false;
        ResultadoExtracao.MotivoInterrupcao motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS;

        final int limitePaginasGeral = CarregadorConfig.obterLimitePaginasApiGraphQL();
        final String nomeEntidadeFaturasGraphQL = ConstantesApiGraphQL.obterNomeEntidadeApi(ConstantesEntidades.FATURAS_GRAPHQL);
        final int limitePaginas = nomeEntidadeFaturasGraphQL.equals(nomeEntidade)
                ? CarregadorConfig.obterLimitePaginasFaturasGraphQL()
                : limitePaginasGeral;
        final boolean auditar = nomeEntidadeFaturasGraphQL.equals(nomeEntidade);
        final String runUuid = auditar ? java.util.UUID.randomUUID().toString() : null;
        final int perInt = 100;

        java.time.LocalDate janelaInicio = null;
        java.time.LocalDate janelaFim = null;
        try {
            final Object paramsObj = variaveis != null ? variaveis.get("params") : null;
            if (paramsObj instanceof final java.util.Map<?, ?> m) {
                final Object v1 = m.get("issueDate");
                final Object v2 = m.get("dueDate");
                final Object v3 = m.get("originalDueDate");
                final String dataStr = v1 != null ? v1.toString() : (v2 != null ? v2.toString() : (v3 != null ? v3.toString() : null));
                if (dataStr != null && !dataStr.isBlank()) {
                    try {
                        final java.time.LocalDate d = java.time.LocalDate.parse(dataStr);
                        janelaInicio = d;
                        janelaFim = d;
                    } catch (final RuntimeException ignored) {
                        // no-op
                    }
                }
            }
        } catch (final RuntimeException ignored) {
            // no-op
        }

        while (hasNextPage) {
            try {
                if (paginaAtual > limitePaginas) {
                    logger.warn("Limite de paginas atingido para {}: {}", nomeEntidade, limitePaginas);
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS;
                    break;
                }

                final int maxRegistros = CarregadorConfig.obterMaxRegistrosGraphQL();
                if (totalRegistrosProcessados >= maxRegistros) {
                    logger.warn("Limite de registros atingido para {}: {}", nomeEntidade, maxRegistros);
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.LIMITE_REGISTROS;
                    break;
                }

                if (paginaAtual % INTERVALO_LOG_PROGRESSO == 0) {
                    logger.info("Progresso GraphQL {}: pagina {} com {} registros processados",
                            nomeEntidade, paginaAtual, totalRegistrosProcessados);
                }

                final Map<String, Object> variaveisComCursor = new java.util.HashMap<>(variaveis);
                if (cursor != null) {
                    variaveisComCursor.put("after", cursor);
                }

                final PaginatedGraphQLResponse<T> resposta =
                    executarQueryGraphQLTipado(query, nomeEntidade, variaveisComCursor, tipoClasse);

                if (resposta.isErroApi()) {
                    logger.error("Falha de API GraphQL para {} na pagina {}: {}",
                        nomeEntidade,
                        paginaAtual,
                        resposta.getErroDetalhe() == null ? "SEM_DETALHE" : resposta.getErroDetalhe());
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.ERRO_API;
                    break;
                }

                todasEntidades.addAll(resposta.getEntidades());
                totalRegistrosProcessados += resposta.getEntidades().size();

                if (auditar && this.executionUuid != null && runUuid != null) {
                    final PageAuditEntity audit = new PageAuditEntity();
                    audit.setExecutionUuid(this.executionUuid);
                    audit.setRunUuid(runUuid);
                    audit.setTemplateId(ConstantesApiGraphQL.TEMPLATE_ID_AUDIT);
                    audit.setPage(paginaAtual);
                    audit.setPer(perInt);
                    audit.setJanelaInicio(janelaInicio);
                    audit.setJanelaFim(janelaFim);
                    audit.setReqHash(resposta.getReqHash() != null ? resposta.getReqHash() : "");
                    audit.setRespHash(resposta.getRespHash() != null ? resposta.getRespHash() : "");
                    audit.setTotalItens(resposta.getTotalItens());
                    audit.setIdKey("id");
                    Long minNum = null;
                    Long maxNum = null;
                    if (tipoClasse != null && tipoClasse.getName().endsWith("CreditCustomerBillingNodeDTO")) {
                        for (final T it : resposta.getEntidades()) {
                            try {
                                final Long idVal = ((br.com.extrator.modelo.graphql.faturas.CreditCustomerBillingNodeDTO) it).getId();
                                if (idVal != null) {
                                    minNum = (minNum == null || idVal < minNum) ? idVal : minNum;
                                    maxNum = (maxNum == null || idVal > maxNum) ? idVal : maxNum;
                                }
                            } catch (final RuntimeException ignored) {
                                // no-op
                            }
                        }
                    }
                    audit.setIdMinNum(minNum);
                    audit.setIdMaxNum(maxNum);
                    audit.setStatusCode(resposta.getStatusCode());
                    audit.setDuracaoMs(resposta.getDuracaoMs());
                    this.pageAuditRepository.inserir(audit);
                }

                contadorFalhasConsecutivas.put(chaveEntidade, 0);

                final String novoCursor = resposta.getEndCursor();
                if (novoCursor != null && cursor != null && novoCursor.equals(cursor)) {
                    if (resposta.getHasNextPage()) {
                        final int registrosEsperados = perInt;
                        final int registrosRecebidos = resposta.getEntidades().size();
                        if (registrosRecebidos < registrosEsperados) {
                            logger.warn("Cursor repetido em {} com pagina incompleta ({} < {})",
                                nomeEntidade, registrosRecebidos, registrosEsperados);
                            break;
                        }

                        logger.warn("Cursor repetido com hasNextPage=true em {}. Interrompendo para evitar loop.",
                            nomeEntidade);
                        interrompido = true;
                        motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.LOOP_DETECTADO;
                        break;
                    }
                }

                if (resposta.getEntidades().isEmpty() && resposta.getHasNextPage()) {
                    logger.warn("Pagina vazia com hasNextPage=true em {}. Interrompendo para evitar loop.",
                        nomeEntidade);
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.PAGINA_VAZIA;
                    break;
                }

                hasNextPage = resposta.getHasNextPage();
                cursor = novoCursor;
                paginaAtual++;

            } catch (final RuntimeException e) {
                logger.error("Erro ao executar query GraphQL para entidade {} pagina {}: {}",
                        nomeEntidade, paginaAtual, e.getMessage(), e);
                incrementarContadorFalhas(chaveEntidade, nomeEntidade);
                interrompido = true;
                motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.ERRO_API;
                break;
            }
        }

        if (interrompido) {
            logger.warn("Query GraphQL incompleta para {}: {} registros em {} paginas",
                    nomeEntidade, totalRegistrosProcessados, paginaAtual - 1);
            return ResultadoExtracao.incompleto(todasEntidades, motivoInterrupcao, paginaAtual - 1, totalRegistrosProcessados);
        }

        if (todasEntidades.isEmpty()) {
            logger.info("Query GraphQL concluida para {} sem registros", nomeEntidade);
        } else {
            logger.info("Query GraphQL completa para {}: {} registros em {} paginas",
                    nomeEntidade, totalRegistrosProcessados, paginaAtual - 1);
        }
        return ResultadoExtracao.completo(todasEntidades, paginaAtual - 1, totalRegistrosProcessados);
    }

    

    /**
     * Construtor da classe ClienteApiGraphQL
     * Inicializa as configuraÃƒÂ§ÃƒÂµes necessÃƒÂ¡rias para comunicaÃƒÂ§ÃƒÂ£o com a API GraphQL
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
        this.gerenciadorRequisicao = GerenciadorRequisicaoHttp.getInstance();
        this.pageAuditRepository = new PageAuditRepository();
        this.executionUuid = java.util.UUID.randomUUID().toString();
    }

    public void setExecutionUuid(final String uuid) {
        this.executionUuid = uuid;
    }
    /**
     * Busca coletas via GraphQL para as ÃƒÂºltimas 24h (ontem + hoje).
     * MÃƒÂ©todo de conveniÃƒÂªncia que delega para buscarColetas(dataInicio, dataFim).
     * 
     * @param dataReferencia Data de referÃƒÂªncia para buscar as coletas (LocalDate)
     * @return ResultadoExtracao indicando se a busca foi completa ou interrompida
     */
    public ResultadoExtracao<ColetaNodeDTO> buscarColetas(final LocalDate dataReferencia) {
        final LocalDate diaAnterior = dataReferencia.minusDays(1);
        return buscarColetas(diaAnterior, dataReferencia);
    }





    /**
     * Busca fretes via GraphQL para as ÃƒÂºltimas 24 horas a partir de uma data de referÃƒÂªncia.
     * MÃƒÂ©todo de conveniÃƒÂªncia que delega para buscarFretes(dataInicio, dataFim).
     * 
     * @param dataReferencia Data de referÃƒÂªncia que representa o FIM do intervalo de busca.
     * @return ResultadoExtracao indicando se a busca foi completa ou interrompida
     */
    public ResultadoExtracao<FreteNodeDTO> buscarFretes(final LocalDate dataReferencia) {
        final LocalDate dataInicio = dataReferencia.minusDays(1);
        return buscarFretes(dataInicio, dataReferencia);
    }

    /**
     * Busca coletas via GraphQL para um intervalo de datas.
     * Utiliza GraphQLIntervaloHelper para iterar dia a dia (API nÃƒÂ£o suporta intervalo).
     * 
     * @param dataInicio Data de inÃƒÂ­cio do perÃƒÂ­odo
     * @param dataFim Data de fim do perÃƒÂ­odo
     * @return ResultadoExtracao indicando se a busca foi completa ou interrompida
     */
    public ResultadoExtracao<ColetaNodeDTO> buscarColetas(final LocalDate dataInicio, final LocalDate dataFim) {
        final boolean suportaServiceDate = suportaFiltroPick("serviceDate");
        if (suportaServiceDate) {
            logger.info("Ã°Å¸â€Â Coletas: usando filtros combinados requestDate + serviceDate para reduzir perdas referenciais.");
            return buscarColetasComFiltrosCombinados(dataInicio, dataFim);
        }
        logger.info("Ã¢â€žÂ¹Ã¯Â¸Â Coletas: filtro serviceDate nÃƒÂ£o disponÃƒÂ­vel no schema atual, usando requestDate.");
        return GraphQLIntervaloHelper.executarPorDia(
            dataInicio,
            dataFim,
            this::buscarColetasDia,
            "Coletas"
        );
    }

    private ResultadoExtracao<ColetaNodeDTO> buscarColetasComFiltrosCombinados(final LocalDate dataInicio, final LocalDate dataFim) {
        final List<ColetaNodeDTO> acumulado = new ArrayList<>();
        int totalPaginas = 0;
        boolean completo = true;
        String motivoInterrupcao = null;

        LocalDate dataAtual = dataInicio;
        while (!dataAtual.isAfter(dataFim)) {
            final ResultadoExtracao<ColetaNodeDTO> porRequestDate = buscarColetasDiaComCampo(dataAtual, "requestDate");
            final ResultadoExtracao<ColetaNodeDTO> porServiceDate = buscarColetasDiaComCampo(dataAtual, "serviceDate");

            acumulado.addAll(porRequestDate.getDados());
            acumulado.addAll(porServiceDate.getDados());
            totalPaginas += porRequestDate.getPaginasProcessadas();
            totalPaginas += porServiceDate.getPaginasProcessadas();

            if (!porRequestDate.isCompleto() || !porServiceDate.isCompleto()) {
                completo = false;
                motivoInterrupcao = selecionarMotivoInterrupcao(motivoInterrupcao, porRequestDate.getMotivoInterrupcao());
                motivoInterrupcao = selecionarMotivoInterrupcao(motivoInterrupcao, porServiceDate.getMotivoInterrupcao());
            }
            dataAtual = dataAtual.plusDays(1);
        }

        final List<ColetaNodeDTO> deduplicado = deduplicarColetasPorId(acumulado);
        final int duplicadosRemovidos = acumulado.size() - deduplicado.size();
        if (duplicadosRemovidos > 0) {
            logger.info("Ã¢â€žÂ¹Ã¯Â¸Â Coletas combinadas: {} duplicado(s) removido(s) por id/sequenceCode.", duplicadosRemovidos);
        }

        if (completo) {
            return ResultadoExtracao.completo(deduplicado, totalPaginas, deduplicado.size());
        }
        return ResultadoExtracao.incompleto(
            deduplicado,
            motivoInterrupcao != null ? motivoInterrupcao : ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS.getCodigo(),
            totalPaginas,
            deduplicado.size()
        );
    }

    private String selecionarMotivoInterrupcao(final String atual, final String candidato) {
        if (candidato == null || candidato.isBlank()) {
            return atual;
        }
        if (ResultadoExtracao.MotivoInterrupcao.ERRO_API.getCodigo().equals(candidato)
            || ResultadoExtracao.MotivoInterrupcao.CIRCUIT_BREAKER.getCodigo().equals(candidato)) {
            return candidato;
        }
        if (atual == null || atual.isBlank()) {
            return candidato;
        }
        return atual;
    }

    private List<ColetaNodeDTO> deduplicarColetasPorId(final List<ColetaNodeDTO> coletas) {
        final Map<String, ColetaNodeDTO> unicos = new LinkedHashMap<>();
        for (final ColetaNodeDTO coleta : coletas) {
            if (coleta == null) {
                continue;
            }
            final String chave = coleta.getId() != null && !coleta.getId().isBlank()
                ? coleta.getId()
                : String.valueOf(coleta.getSequenceCode());
            unicos.put(chave, coleta);
        }
        return new ArrayList<>(unicos.values());
    }

    /**
     * Busca coletas para um ÃƒÂºnico dia especÃƒÂ­fico.
     * MÃƒÂ©todo auxiliar usado pelo GraphQLIntervaloHelper.
     * 
     * @param data Data especÃƒÂ­fica para buscar coletas
     * @return ResultadoExtracao das coletas do dia
     */
    private ResultadoExtracao<ColetaNodeDTO> buscarColetasDia(final LocalDate data) {
        return buscarColetasDiaComCampo(data, "requestDate");
    }

    private ResultadoExtracao<ColetaNodeDTO> buscarColetasDiaComCampo(final LocalDate data, final String campoData) {
        final String dataFormatada = formatarDataParaApiGraphQL(data);
        final Map<String, Object> variaveis = Map.of(
            "params", Map.of(campoData, dataFormatada)
        );
        return executarQueryPaginada(GraphQLQueries.QUERY_COLETAS, 
            ConstantesApiGraphQL.obterNomeEntidadeApi(ConstantesEntidades.COLETAS), variaveis, ColetaNodeDTO.class);
    }

    private boolean suportaFiltroPick(final String campo) {
        final Set<String> campos = listarCamposInputPick();
        return campos.contains(campo);
    }

    private Set<String> listarCamposInputPick() {
        if (camposPickInputCache != null) {
            return camposPickInputCache;
        }

        final Set<String> campos = new HashSet<>();
        try {
            final ObjectNode corpoJson = mapeadorJson.createObjectNode();
            corpoJson.put("query", GraphQLQueries.INTROSPECTION_PICK_INPUT);
            final String corpoRequisicao = mapeadorJson.writeValueAsString(corpoJson);
            final HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(urlBase + endpointGraphQL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(corpoRequisicao))
                    .timeout(this.timeoutRequisicao)
                    .build();
            final HttpResponse<String> resposta = gerenciadorRequisicao.executarRequisicao(this.clienteHttp, requisicao, "GraphQL-Introspection-PickInput");
            final JsonNode respostaJson = mapeadorJson.readTree(resposta.body());
            final JsonNode fields = respostaJson.path("data").path("__type").path("inputFields");
            if (fields.isArray()) {
                for (final JsonNode f : fields) {
                    final String nome = f.path("name").asText();
                    if (nome != null && !nome.isBlank()) {
                        campos.add(nome);
                    }
                }
            }
        } catch (final RuntimeException | java.io.IOException e) {
            logger.warn("Falha ao introspectar PickInput. Seguira com requestDate apenas: {}", e.getMessage());
        }

        camposPickInputCache = Set.copyOf(campos);
        return camposPickInputCache;
    }

    /**
     * Busca fretes via GraphQL para um intervalo de datas.
     * API de fretes suporta intervalo diretamente via serviceAt.
     * 
     * @param dataInicio Data de inÃƒÂ­cio do perÃƒÂ­odo
     * @param dataFim Data de fim do perÃƒÂ­odo
     * @return ResultadoExtracao indicando se a busca foi completa ou interrompida
     */
    public ResultadoExtracao<FreteNodeDTO> buscarFretes(final LocalDate dataInicio, final LocalDate dataFim) {
        logger.info("Ã°Å¸â€Â Buscando fretes via GraphQL - PerÃƒÂ­odo: {} a {}", dataInicio, dataFim);
        
        // Usar formato "dataInicio - dataFim" no filtro serviceAt (jÃƒÂ¡ suportado pela API)
        final String intervaloServiceAt = formatarDataParaApiGraphQL(dataInicio) + " - " + formatarDataParaApiGraphQL(dataFim);
        
        final Map<String, Object> variaveis = Map.of(
            "params", Map.of("serviceAt", intervaloServiceAt)
        );

        return executarQueryPaginada(GraphQLQueries.QUERY_FRETES, 
            ConstantesApiGraphQL.obterNomeEntidadeApi(ConstantesEntidades.FRETES), variaveis, FreteNodeDTO.class);
    }

    /**
     * Busca usuÃƒÂ¡rios do sistema (Individual) via GraphQL.
     * NÃƒÂ£o utiliza filtro de data, apenas filtra por enabled: true.
     * Utiliza paginaÃƒÂ§ÃƒÂ£o cursor-based para extrair todos os usuÃƒÂ¡rios ativos.
     * 
     * @return ResultadoExtracao indicando se a busca foi completa ou interrompida
     */
    public ResultadoExtracao<br.com.extrator.modelo.graphql.usuarios.IndividualNodeDTO> buscarUsuariosSistema() {
        try {
            final Map<String, Object> variaveis = new HashMap<>();
            variaveis.put("params", Map.of("enabled", true));
            
            logger.info("Buscando UsuÃƒÂ¡rios do Sistema via GraphQL (enabled: true)");
            return executarQueryPaginada(
                GraphQLQueries.QUERY_USUARIOS_SISTEMA, 
                ConstantesApiGraphQL.obterNomeEntidadeApi(ConstantesEntidades.USUARIOS_SISTEMA), 
                variaveis, 
                br.com.extrator.modelo.graphql.usuarios.IndividualNodeDTO.class
            );
        } catch (final RuntimeException e) {
            logger.warn("Falha ao buscar UsuÃƒÂ¡rios do Sistema: {}", e.getMessage());
            final List<br.com.extrator.modelo.graphql.usuarios.IndividualNodeDTO> vazio = new ArrayList<>();
            return ResultadoExtracao.incompleto(vazio, ResultadoExtracao.MotivoInterrupcao.ERRO_API, 0, 0);
        }
    }

    /**
     * Busca NFSe diretamente via GraphQL para enriquecer fretes com metadados.
     * Utiliza paginaÃƒÂ§ÃƒÂ£o e traz campos diretos e o XML bruto.
     */
    public ResultadoExtracao<br.com.extrator.modelo.graphql.fretes.nfse.NfseNodeDTO> buscarNfseDireta(final LocalDate dataReferencia) {
        try {
            final LocalDate dataInicio = dataReferencia.minusDays(1);
            final String intervaloIssuedAt = dataInicio.format(FormatadorData.ISO_DATE) + " - " + dataReferencia.format(FormatadorData.ISO_DATE);
            final Map<String, Object> variaveis = Map.of(
                "params", Map.of("issuedAt", intervaloIssuedAt)
            );
            logger.info("Buscando NFSe via GraphQL - PerÃƒÂ­odo: {}", intervaloIssuedAt);
            return executarQueryPaginada(GraphQLQueries.QUERY_NFSE, 
                ConstantesApiGraphQL.obterNomeEntidadeApi("nfse"), variaveis, br.com.extrator.modelo.graphql.fretes.nfse.NfseNodeDTO.class);
        } catch (final RuntimeException e) {
            logger.warn("Falha ao buscar NFSe direta: {}", e.getMessage());
            final List<br.com.extrator.modelo.graphql.fretes.nfse.NfseNodeDTO> vazio = new ArrayList<>();
            return ResultadoExtracao.incompleto(vazio, ResultadoExtracao.MotivoInterrupcao.ERRO_API, 0, 0);
        }
    }

    /**
     * Busca capa de faturas via GraphQL para a data de referÃƒÂªncia.
     * Utiliza janela configurÃƒÂ¡vel para buscar dias anteriores.
     * 
     * @param dataReferencia Data de referÃƒÂªncia (normalmente hoje)
     * @return ResultadoExtracao das faturas encontradas
     */
    public ResultadoExtracao<br.com.extrator.modelo.graphql.faturas.CreditCustomerBillingNodeDTO> buscarCapaFaturas(final LocalDate dataReferencia) {
        final int diasJanela = CarregadorConfig.obterDiasJanelaFaturasGraphQL();
        final LocalDate dataInicio = dataReferencia.minusDays(Math.max(0, diasJanela - 1));
        return buscarCapaFaturas(dataInicio, dataReferencia);
    }

    /**
     * Busca capa de faturas via GraphQL para um intervalo de datas especÃƒÂ­fico.
     * Utiliza GraphQLIntervaloHelper para iterar dia a dia (API nÃƒÂ£o suporta intervalo).
     * 
     * @param dataInicio Data de inÃƒÂ­cio do perÃƒÂ­odo
     * @param dataFim Data de fim do perÃƒÂ­odo
     * @return ResultadoExtracao indicando se a busca foi completa ou interrompida
     */
    public ResultadoExtracao<br.com.extrator.modelo.graphql.faturas.CreditCustomerBillingNodeDTO> buscarCapaFaturas(final LocalDate dataInicio, final LocalDate dataFim) {
        return GraphQLIntervaloHelper.executarPorDia(
            dataInicio,
            dataFim,
            this::buscarCapaFaturasDia,
            "Capa Faturas"
        );
    }
    
    /**
     * Busca capa de faturas para um ÃƒÂºnico dia especÃƒÂ­fico.
     * MÃƒÂ©todo auxiliar usado pelo GraphQLIntervaloHelper.
     * 
     * @param data Data especÃƒÂ­fica para buscar faturas
     * @return ResultadoExtracao das faturas do dia
     */
    private ResultadoExtracao<br.com.extrator.modelo.graphql.faturas.CreditCustomerBillingNodeDTO> buscarCapaFaturasDia(final LocalDate data) {
        final List<String> camposDisponiveis = listarCamposInputCreditCustomerBilling();
        final List<String> camposFiltro = resolverCamposFiltroFaturas(camposDisponiveis);
        ResultadoExtracao<CreditCustomerBillingNodeDTO> ultimoResultado = null;

        for (int indice = 0; indice < camposFiltro.size(); indice++) {
            final String campoFiltro = camposFiltro.get(indice);
            final Map<String, Object> params = montarParametrosCapaFatura(campoFiltro, data, camposDisponiveis);
            final Map<String, Object> variaveis = Map.of("params", params);
            final ResultadoExtracao<CreditCustomerBillingNodeDTO> resultado = executarQueryPaginada(
                GraphQLQueries.QUERY_FATURAS,
                ConstantesApiGraphQL.obterNomeEntidadeApi(ConstantesEntidades.FATURAS_GRAPHQL),
                variaveis,
                CreditCustomerBillingNodeDTO.class
            );

            ultimoResultado = resultado;
            if (!deveRetentarResultadoFaturas(resultado) || indice == camposFiltro.size() - 1) {
                return resultado;
            }

            logger.warn(
                "Falha transitória ao buscar Capa Faturas em {} usando filtro '{}'. Tentando filtro alternativo...",
                data,
                campoFiltro
            );
        }

        return ultimoResultado != null
            ? ultimoResultado
            : ResultadoExtracao.completo(new ArrayList<>(), 0, 0);
    }

    private List<String> resolverCamposFiltroFaturas(final List<String> camposDisponiveis) {
        final LinkedHashSet<String> camposOrdenados = new LinkedHashSet<>();
        if (camposDisponiveis == null || camposDisponiveis.isEmpty()) {
            camposOrdenados.add("dueDate");
            return new ArrayList<>(camposOrdenados);
        }

        if (camposDisponiveis.contains("dueDate")) {
            camposOrdenados.add("dueDate");
        }
        if (camposDisponiveis.contains("originalDueDate")) {
            camposOrdenados.add("originalDueDate");
        }
        if (camposDisponiveis.contains("issueDate")) {
            camposOrdenados.add("issueDate");
        }
        if (camposOrdenados.isEmpty()) {
            camposOrdenados.add("dueDate");
        }
        return new ArrayList<>(camposOrdenados);
    }

    private Map<String, Object> montarParametrosCapaFatura(
            final String campoFiltro,
            final LocalDate data,
            final List<String> camposDisponiveis) {
        final Map<String, Object> params = new HashMap<>();
        params.put(campoFiltro, data.format(FormatadorData.ISO_DATE));

        final String corpId = CarregadorConfig.obterCorporationId();
        if (corpId != null && !corpId.isBlank()) {
            if (camposDisponiveis != null && camposDisponiveis.contains("corporationId")) {
                params.put("corporationId", corpId);
            } else if (camposDisponiveis != null && camposDisponiveis.contains("corporation")) {
                params.put("corporation", Map.of("id", corpId));
            }
        }
        return params;
    }

    private boolean deveRetentarResultadoFaturas(final ResultadoExtracao<CreditCustomerBillingNodeDTO> resultado) {
        if (resultado == null || resultado.isCompleto()) {
            return false;
        }
        final String motivo = resultado.getMotivoInterrupcao();
        final boolean erroApi = ResultadoExtracao.MotivoInterrupcao.ERRO_API.getCodigo().equals(motivo)
            || ResultadoExtracao.MotivoInterrupcao.CIRCUIT_BREAKER.getCodigo().equals(motivo);
        final boolean semDados = resultado.getRegistrosExtraidos() == 0
            && (resultado.getDados() == null || resultado.getDados().isEmpty());
        return erroApi && semDados;
    }

    /**
     * Lista os campos disponÃƒÂ­veis no tipo CreditCustomerBillingInput via introspection.
     * Usado para determinar qual campo de filtro usar (dueDate, issueDate, etc).
     * 
     * @return Lista de nomes de campos disponÃƒÂ­veis
     */
    private List<String> listarCamposInputCreditCustomerBilling() {
        try {
            final ObjectNode corpoJson = mapeadorJson.createObjectNode();
            corpoJson.put("query", GraphQLQueries.INTROSPECTION_CREDIT_CUSTOMER_BILLING);
            final String corpoRequisicao = mapeadorJson.writeValueAsString(corpoJson);
            final HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(urlBase + endpointGraphQL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(corpoRequisicao))
                    .timeout(this.timeoutRequisicao)
                    .build();
            final HttpResponse<String> resposta = gerenciadorRequisicao.executarRequisicao(this.clienteHttp, requisicao, "GraphQL-Introspection");
            final JsonNode respostaJson = mapeadorJson.readTree(resposta.body());
            final List<String> campos = new ArrayList<>();
            final JsonNode fields = respostaJson.path("data").path("__type").path("inputFields");
            if (fields.isArray()) {
                for (final JsonNode f : fields) {
                    final String nome = f.path("name").asText();
                    if (nome != null && !nome.isBlank()) {
                        campos.add(nome);
                    }
                }
            }
            return campos;
        } catch (final java.io.IOException e) {
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Executa uma query GraphQL de forma genÃƒÂ©rica e robusta com desserializaÃƒÂ§ÃƒÂ£o tipada
     * 
     * @param query        A query GraphQL a ser executada
     * @param nomeEntidade Nome da entidade para logs e tratamento de erros
     * @param variaveis    VariÃƒÂ¡veis da query GraphQL
     * @param tipoClasse   Classe para desserializaÃƒÂ§ÃƒÂ£o tipada
     * @return Resposta paginada contendo entidades tipadas e informaÃƒÂ§ÃƒÂµes de paginaÃƒÂ§ÃƒÂ£o
     */
    private <T> PaginatedGraphQLResponse<T> executarQueryGraphQLTipado(final String query, final String nomeEntidade,
            final Map<String, Object> variaveis, final Class<T> tipoClasse) {
        logger.debug("Executando query GraphQL tipada para {} - URL: {}{}, Variáveis: {}",
            nomeEntidade, urlBase, endpointGraphQL, variaveis);
        final List<T> entidades = new ArrayList<>();

        if (urlBase == null || urlBase.isBlank() || token == null || token.isBlank()) {
            logger.error("Configuracoes invalidas para chamada GraphQL (urlBase/token)");
            return new PaginatedGraphQLResponse<>(entidades, false, null, 0, 0, "", "", entidades.size(), true, "CONFIG_INVALIDA");
        }

        final int maxTentativasGraphQl = Math.max(1, CarregadorConfig.obterMaxTentativasRetry());
        final long delayBaseMs = Math.max(250L, CarregadorConfig.obterDelayBaseRetry());
        final double multiplicador = Math.max(1.0d, CarregadorConfig.obterMultiplicadorRetry());

        for (int tentativa = 1; tentativa <= maxTentativasGraphQl; tentativa++) {
            entidades.clear();
            boolean hasNextPage = false;
            String endCursor = null;
            int statusCode = 0;
            int duracaoMs = 0;
            String reqHash = "";
            String respHash = "";

            try {
                final long tempoInicio = System.currentTimeMillis();
                final ObjectNode corpoJson = mapeadorJson.createObjectNode();
                corpoJson.put("query", query);
                if (variaveis != null && !variaveis.isEmpty()) {
                    corpoJson.set("variables", mapeadorJson.valueToTree(variaveis));
                }
                final String corpoRequisicao = mapeadorJson.writeValueAsString(corpoJson);
                try {
                    final byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(corpoRequisicao.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    final StringBuilder sb = new StringBuilder(d.length * 2);
                    for (final byte b : d) {
                        sb.append(String.format("%02x", b));
                    }
                    reqHash = sb.toString();
                } catch (final java.security.NoSuchAlgorithmException ex) {
                    reqHash = "";
                }

                final HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(urlBase + endpointGraphQL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(corpoRequisicao))
                    .timeout(this.timeoutRequisicao)
                    .build();

                final HttpResponse<String> resposta = gerenciadorRequisicao.executarRequisicao(
                    this.clienteHttp,
                    requisicao,
                    "GraphQL-" + nomeEntidade
                );
                statusCode = resposta != null ? resposta.statusCode() : 0;
                duracaoMs = (int) (System.currentTimeMillis() - tempoInicio);
                try {
                    final byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                        .digest((resposta != null ? resposta.body() : "").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    final StringBuilder sb = new StringBuilder(d.length * 2);
                    for (final byte b : d) {
                        sb.append(String.format("%02x", b));
                    }
                    respHash = sb.toString();
                } catch (final java.security.NoSuchAlgorithmException ex) {
                    respHash = "";
                }

                if (resposta == null || resposta.body() == null) {
                    logger.warn("Resposta GraphQL nula para {}", nomeEntidade);
                    return new PaginatedGraphQLResponse<>(entidades, false, null, statusCode, duracaoMs, reqHash, respHash, entidades.size(), true, "RESPOSTA_NULA");
                }
                final JsonNode respostaJson = mapeadorJson.readTree(resposta.body());

                if (respostaJson.has("errors")) {
                    final JsonNode erros = respostaJson.get("errors");
                    if (deveRetentarErroGraphQl(erros) && tentativa < maxTentativasGraphQl) {
                        final long delayMs = calcularDelayBackoffGraphQl(tentativa, delayBaseMs, multiplicador);
                        logger.warn(
                            "Erro GraphQL retentavel para {} (tentativa {}/{}): {}. Aguardando {}ms para nova tentativa.",
                            nomeEntidade,
                            tentativa,
                            maxTentativasGraphQl,
                            extrairCodigoErroGraphQl(erros),
                            delayMs
                        );
                        aguardarRetryGraphQl(delayMs, nomeEntidade, tentativa, maxTentativasGraphQl);
                        continue;
                    }
                    logger.error("Erros na query GraphQL para {}: {}", nomeEntidade, erros.toString());
                    return new PaginatedGraphQLResponse<>(entidades, false, null, statusCode, duracaoMs, reqHash, respHash, entidades.size(), true, "GRAPHQL_ERRORS");
                }

                if (!respostaJson.has("data")) {
                    logger.warn("Resposta GraphQL sem campo 'data' para {}", nomeEntidade);
                    return new PaginatedGraphQLResponse<>(entidades, false, null, statusCode, duracaoMs, reqHash, respHash, entidades.size(), true, "SEM_DATA");
                }

                final JsonNode dados = respostaJson.get("data");
                if (!dados.has(nomeEntidade)) {
                    logger.warn("Campo '{}' nao encontrado na resposta GraphQL. Campos disponiveis: {}",
                            nomeEntidade, dados.fieldNames());
                    return new PaginatedGraphQLResponse<>(entidades, false, null, statusCode, duracaoMs, reqHash, respHash, entidades.size(), true, "SEM_ENTIDADE:" + nomeEntidade);
                }

                final JsonNode dadosEntidade = dados.get(nomeEntidade);
                if (dadosEntidade.has("edges")) {
                    logger.debug("Processando resposta paginada com edges/node para {}", nomeEntidade);
                    final JsonNode edges = dadosEntidade.get("edges");

                    if (edges.isArray()) {
                        for (final JsonNode edge : edges) {
                            if (edge.has("node")) {
                                final JsonNode node = edge.get("node");
                                try {
                                    final T entidade = mapeadorJson.treeToValue(node, tipoClasse);
                                    entidades.add(entidade);
                                } catch (JsonProcessingException | IllegalArgumentException e) {
                                    logger.warn("Erro ao deserializar node de {} para {}: {}",
                                        nomeEntidade, tipoClasse.getSimpleName(), e.getMessage());
                                }
                            }
                        }
                    }

                    if (dadosEntidade.has("pageInfo")) {
                        final JsonNode pageInfo = dadosEntidade.get("pageInfo");
                        if (pageInfo.has("hasNextPage")) {
                            hasNextPage = pageInfo.get("hasNextPage").asBoolean();
                        }
                        if (pageInfo.has("endCursor") && !pageInfo.get("endCursor").isNull()) {
                            endCursor = pageInfo.get("endCursor").asText();
                        }
                        logger.debug("Informacoes de paginacao extraidas - hasNextPage: {}, endCursor: {}",
                            hasNextPage, endCursor);
                    }
                } else {
                    logger.debug("Processando resposta no formato antigo (array direto) para {}", nomeEntidade);
                    if (dadosEntidade.isArray()) {
                        for (final JsonNode item : dadosEntidade) {
                            try {
                                final T entidade = mapeadorJson.treeToValue(item, tipoClasse);
                                entidades.add(entidade);
                            } catch (JsonProcessingException | IllegalArgumentException e) {
                                logger.warn("Erro ao deserializar item de {} para {}: {}",
                                    nomeEntidade, tipoClasse.getSimpleName(), e.getMessage());
                            }
                        }
                    }
                }

                logger.debug("Query GraphQL tipada concluida para {}. Total encontrado: {}", nomeEntidade, entidades.size());
                return new PaginatedGraphQLResponse<>(entidades, hasNextPage, endCursor, statusCode, duracaoMs, reqHash, respHash, entidades.size(), false, null);

            } catch (final JsonProcessingException e) {
                if (tentativa < maxTentativasGraphQl) {
                    final long delayMs = calcularDelayBackoffGraphQl(tentativa, delayBaseMs, multiplicador);
                    logger.warn(
                        "Erro JSON na query GraphQL para {} (tentativa {}/{}): {}. Aguardando {}ms para nova tentativa.",
                        nomeEntidade,
                        tentativa,
                        maxTentativasGraphQl,
                        e.getMessage(),
                        delayMs
                    );
                    aguardarRetryGraphQl(delayMs, nomeEntidade, tentativa, maxTentativasGraphQl);
                    continue;
                }
                logger.error("Erro de processamento JSON durante execucao da query GraphQL para {}: {}", nomeEntidade, e.getMessage(), e);
                return new PaginatedGraphQLResponse<>(entidades, false, null, 0, 0, "", "", entidades.size(), true, "JSON_PROCESSING");
            } catch (final RuntimeException e) {
                if (tentativa < maxTentativasGraphQl) {
                    final long delayMs = calcularDelayBackoffGraphQl(tentativa, delayBaseMs, multiplicador);
                    logger.warn(
                        "Erro transitorio na query GraphQL para {} (tentativa {}/{}): {}. Aguardando {}ms para nova tentativa.",
                        nomeEntidade,
                        tentativa,
                        maxTentativasGraphQl,
                        e.getMessage(),
                        delayMs
                    );
                    aguardarRetryGraphQl(delayMs, nomeEntidade, tentativa, maxTentativasGraphQl);
                    continue;
                }
                logger.error("Erro durante execucao da query GraphQL para {}: {}", nomeEntidade, e.getMessage(), e);
                return new PaginatedGraphQLResponse<>(entidades, false, null, 0, 0, "", "", entidades.size(), true, "RUNTIME_EXCEPTION");
            }
        }

        logger.error("Falha ao executar query GraphQL para {} apos esgotar tentativas.", nomeEntidade);
        return new PaginatedGraphQLResponse<>(entidades, false, null, 0, 0, "", "", entidades.size(), true, "MAX_RETRY_EXCEDIDO");
    }

    private boolean deveRetentarErroGraphQl(final JsonNode erros) {
        if (erros == null || !erros.isArray() || erros.isEmpty()) {
            return false;
        }

        for (final JsonNode erro : erros) {
            final String codigo = erro.path("extensions").path("code").asText("");
            final String mensagem = erro.path("message").asText("").toLowerCase(Locale.ROOT);
            if ("INTERNAL_SERVER_ERROR".equalsIgnoreCase(codigo)
                    || "SERVICE_UNAVAILABLE".equalsIgnoreCase(codigo)
                    || "TIMEOUT".equalsIgnoreCase(codigo)
                    || mensagem.contains("statement timeout")
                    || mensagem.contains("querycanceled")
                    || mensagem.contains("timeout")
                    || mensagem.contains("temporar")) {
                return true;
            }
        }
        return false;
    }

    private String extrairCodigoErroGraphQl(final JsonNode erros) {
        if (erros == null || !erros.isArray() || erros.isEmpty()) {
            return "SEM_CODIGO";
        }
        final JsonNode primeiroErro = erros.get(0);
        final String codigo = primeiroErro.path("extensions").path("code").asText("");
        if (codigo != null && !codigo.isBlank()) {
            return codigo;
        }
        final String mensagem = primeiroErro.path("message").asText("");
        return mensagem == null || mensagem.isBlank() ? "SEM_CODIGO" : mensagem;
    }

    private long calcularDelayBackoffGraphQl(final int tentativa, final long delayBaseMs, final double multiplicador) {
        final double fator = Math.pow(multiplicador, Math.max(0, tentativa - 1));
        final long delayCalculado = Math.round(delayBaseMs * fator);
        return Math.min(delayCalculado, 60_000L);
    }

    private void aguardarRetryGraphQl(final long delayMs,
                                      final String nomeEntidade,
                                      final int tentativaAtual,
                                      final int maxTentativas) {
        try {
            ThreadUtil.aguardar(delayMs);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                "Thread interrompida durante retry GraphQL de " + nomeEntidade
                    + " (tentativa " + tentativaAtual + "/" + maxTentativas + ")",
                e
            );
        }
    }

    /**
     * Valida se as credenciais de acesso ÃƒÂ  API GraphQL estÃƒÂ£o funcionando
     * 
     * @return true se a validaÃƒÂ§ÃƒÂ£o foi bem-sucedida, false caso contrÃƒÂ¡rio
     */
    public boolean validarAcessoApi() {
        logger.info("Validando acesso ÃƒÂ  API GraphQL...");

        try {
            // Query simples para testar a conectividade
            final String queryTeste = "{ __schema { queryType { name } } }";

            // Construir o corpo da requisiÃƒÂ§ÃƒÂ£o GraphQL usando ObjectMapper
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
                    logger.info("Ã¢Å“â€¦ ValidaÃƒÂ§ÃƒÂ£o da API GraphQL bem-sucedida");
                } else {
                    logger.error("Ã¢ÂÅ’ Erro na validaÃƒÂ§ÃƒÂ£o da API GraphQL: {}", respostaJson.get("errors"));
                }

                return sucesso;
            } else {
                logger.error("Ã¢ÂÅ’ Falha na validaÃƒÂ§ÃƒÂ£o da API GraphQL. Status: {}", resposta.statusCode());
                return false;
            }

        } catch (java.io.IOException | InterruptedException e) {
            logger.error("Ã¢ÂÅ’ Erro durante validaÃƒÂ§ÃƒÂ£o da API GraphQL: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Busca dados de enriquecimento de uma fatura especÃƒÂ­fica via GraphQL.
     * Executa a query EnriquecerFaturas para obter NÃ‚Â° NFS-e, Carteira e InstruÃƒÂ§ÃƒÂ£o Customizada.
     * 
     * @param billingId ID da cobranÃƒÂ§a (creditCustomerBilling)
     * @return Optional com CreditCustomerBillingNodeDTO contendo os dados enriquecidos, ou empty se nÃƒÂ£o encontrado
     */
    public java.util.Optional<CreditCustomerBillingNodeDTO> enriquecerFatura(final String billingId) {
        if (billingId == null || billingId.isBlank()) {
            logger.warn("Ã¢Å¡Â Ã¯Â¸Â Tentativa de enriquecer fatura com ID nulo ou vazio");
            return java.util.Optional.empty();
        }
        
        try {
            final Map<String, Object> variaveis = Map.of("id", billingId);
            final PaginatedGraphQLResponse<CreditCustomerBillingNodeDTO> resposta = 
                executarQueryGraphQLTipado(
                    GraphQLQueries.QUERY_ENRIQUECER_FATURAS,
                    "creditCustomerBilling",
                    variaveis,
                    CreditCustomerBillingNodeDTO.class
                );
            
            if (resposta.getEntidades() != null && !resposta.getEntidades().isEmpty()) {
                return java.util.Optional.of(resposta.getEntidades().get(0));
            }
            
            return java.util.Optional.empty();
        } catch (final Exception e) {
            logger.error("Ã¢ÂÅ’ Erro ao enriquecer fatura com ID {}: {}", billingId, e.getMessage(), e);
            return java.util.Optional.empty();
        }
    }
    
    /**
     * Enriquece fatura usando o nÃƒÂºmero do documento (fallback quando billingId nÃƒÂ£o estÃƒÂ¡ disponÃƒÂ­vel).
     * Usa fit_ant_document do DataExport para buscar a cobranÃƒÂ§a no GraphQL.
     * 
     * @param document NÃƒÂºmero do documento da fatura (ex: "112025/1-3")
     * @return Optional com os dados de enriquecimento ou empty se nÃƒÂ£o encontrado
     */
    public java.util.Optional<CreditCustomerBillingNodeDTO> enriquecerFaturaPorDocumento(final String document) {
        if (document == null || document.isBlank()) {
            logger.warn("Ã¢Å¡Â Ã¯Â¸Â Tentativa de enriquecer fatura com documento nulo ou vazio");
            return java.util.Optional.empty();
        }
        
        try {
            final Map<String, Object> variaveis = Map.of("document", document);
            final PaginatedGraphQLResponse<CreditCustomerBillingNodeDTO> resposta = 
                executarQueryGraphQLTipado(
                    GraphQLQueries.QUERY_ENRIQUECER_FATURAS_POR_DOCUMENTO,
                    "creditCustomerBilling",
                    variaveis,
                    CreditCustomerBillingNodeDTO.class
                );
            
            if (resposta.getEntidades() != null && !resposta.getEntidades().isEmpty()) {
                logger.debug("Ã¢Å“â€¦ Fatura encontrada via documento: {}", document);
                return java.util.Optional.of(resposta.getEntidades().get(0));
            }
            
            logger.debug("Ã¢Å¡Â Ã¯Â¸Â Fatura nÃƒÂ£o encontrada via documento: {}", document);
            return java.util.Optional.empty();
        } catch (final Exception e) {
            logger.error("Ã¢ÂÅ’ Erro ao enriquecer fatura por documento {}: {}", document, e.getMessage(), e);
            return java.util.Optional.empty();
        }
    }

    /**
     * Busca capa de fatura por ID da cobranca.
     * Usado no backfill referencial de fretes orfaos (accounting_credit_id).
     *
     * @param billingId ID da cobranca em creditCustomerBilling
     * @return Optional contendo a capa completa da fatura, quando encontrada
     */
    public java.util.Optional<CreditCustomerBillingNodeDTO> buscarCapaFaturaPorId(final Long billingId) {
        if (billingId == null) {
            logger.warn("Tentativa de buscar capa de fatura com ID nulo");
            return java.util.Optional.empty();
        }

        try {
            final Map<String, Object> variaveis = Map.of("params", Map.of("id", String.valueOf(billingId)));
            final PaginatedGraphQLResponse<CreditCustomerBillingNodeDTO> resposta =
                executarQueryGraphQLTipado(
                    GraphQLQueries.QUERY_FATURAS,
                    "creditCustomerBilling",
                    variaveis,
                    CreditCustomerBillingNodeDTO.class
                );

            if (resposta.isErroApi()) {
                logger.warn("Falha ao buscar capa da fatura {} por ID: {}", billingId, resposta.getErroDetalhe());
                return java.util.Optional.empty();
            }

            if (resposta.getEntidades() != null && !resposta.getEntidades().isEmpty()) {
                return java.util.Optional.of(resposta.getEntidades().get(0));
            }

            return java.util.Optional.empty();
        } catch (final Exception e) {
            logger.error("Erro ao buscar capa da fatura {} por ID: {}", billingId, e.getMessage(), e);
            return java.util.Optional.empty();
        }
    }
    
    /**
     * Formatar LocalDate para o formato esperado pela API GraphQL (YYYY-MM-DD).
     * 
     * @param data A data a ser formatada
     * @return String no formato YYYY-MM-DD
     */
    private String formatarDataParaApiGraphQL(final LocalDate data) {
        return data.format(FormatadorData.ISO_DATE);
    }

    /**
     * Incrementa o contador de falhas consecutivas e ativa o circuit breaker se necessÃƒÂ¡rio.
     * 
     * @param chaveEntidade Chave identificadora da entidade GraphQL
     * @param nomeEntidade Nome amigÃƒÂ¡vel da entidade para logs
     */
    private void incrementarContadorFalhas(final String chaveEntidade, final String nomeEntidade) {
        final int falhas = contadorFalhasConsecutivas.getOrDefault(chaveEntidade, 0) + 1;
        contadorFalhasConsecutivas.put(chaveEntidade, falhas);
        
        if (falhas >= MAX_FALHAS_CONSECUTIVAS) {
            entidadesComCircuitAberto.add(chaveEntidade);
            logger.error("Ã°Å¸Å¡Â¨ CIRCUIT BREAKER ATIVADO - Entidade {} ({}): {} falhas consecutivas. Entidade temporariamente desabilitada.", 
                    chaveEntidade, nomeEntidade, falhas);
        } else {
            logger.warn("Ã¢Å¡Â Ã¯Â¸Â Falha {}/{} para entidade {} ({})", falhas, MAX_FALHAS_CONSECUTIVAS, chaveEntidade, nomeEntidade);
        }
    }
    
    /**
     * Busca dados de enriquecimento de uma cobranÃƒÂ§a especÃƒÂ­fica via GraphQL.
     * Retorna ticketAccountId, NFS-e e mÃƒÂ©todo de pagamento da primeira parcela.
     * 
     * @param billingId ID da cobranÃƒÂ§a (creditCustomerBilling)
     * @return Optional com CreditCustomerBillingNodeDTO contendo os dados enriquecidos, ou empty se nÃƒÂ£o encontrado
     */
    public java.util.Optional<CreditCustomerBillingNodeDTO> buscarDadosCobranca(final Long billingId) {
        if (billingId == null) {
            logger.warn("Ã¢Å¡Â Ã¯Â¸Â Tentativa de buscar dados de cobranÃƒÂ§a com ID nulo");
            return java.util.Optional.empty();
        }
        
        try {
            final Map<String, Object> variaveis = Map.of("id", billingId.toString());
            final PaginatedGraphQLResponse<CreditCustomerBillingNodeDTO> resposta = 
                executarQueryGraphQLTipado(
                    GraphQLQueries.QUERY_ENRIQUECER_COBRANCA_NFSE,
                    "creditCustomerBilling",
                    variaveis,
                    CreditCustomerBillingNodeDTO.class
                );
            
            if (resposta.getEntidades() != null && !resposta.getEntidades().isEmpty()) {
                return java.util.Optional.of(resposta.getEntidades().get(0));
            }
            
            return java.util.Optional.empty();
        } catch (final Exception e) {
            logger.error("Ã¢ÂÅ’ Erro ao buscar dados de cobranÃƒÂ§a com ID {}: {}", billingId, e.getMessage(), e);
            return java.util.Optional.empty();
        }
    }
    
    /**
     * Busca detalhes de uma conta bancÃƒÂ¡ria via GraphQL.
     * Usado para resolver dados do banco via ticketAccountId (cache otimizado).
     * 
     * @param bankAccountId ID da conta bancÃƒÂ¡ria (ticketAccountId)
     * @return Optional com BankAccountNodeDTO contendo os detalhes do banco, ou empty se nÃƒÂ£o encontrado
     */
    public java.util.Optional<BankAccountNodeDTO> buscarDetalhesBanco(final Integer bankAccountId) {
        if (bankAccountId == null) {
            logger.warn("Ã¢Å¡Â Ã¯Â¸Â Tentativa de buscar detalhes de banco com ID nulo");
            return java.util.Optional.empty();
        }
        
        try {
            final Map<String, Object> variaveis = Map.of("id", bankAccountId);
            final PaginatedGraphQLResponse<BankAccountNodeDTO> resposta = 
                executarQueryGraphQLTipado(
                    GraphQLQueries.QUERY_RESOLVER_CONTA_BANCARIA,
                    "bankAccount",
                    variaveis,
                    BankAccountNodeDTO.class
                );
            
            if (resposta.getEntidades() != null && !resposta.getEntidades().isEmpty()) {
                return java.util.Optional.of(resposta.getEntidades().get(0));
            }
            
            return java.util.Optional.empty();
        } catch (final Exception e) {
            logger.error("Ã¢ÂÅ’ Erro ao buscar detalhes de banco com ID {}: {}", bankAccountId, e.getMessage(), e);
            return java.util.Optional.empty();
        }
    }
}

