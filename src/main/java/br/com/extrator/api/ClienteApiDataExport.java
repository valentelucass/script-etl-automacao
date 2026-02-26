package br.com.extrator.api;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.UUID;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.extrator.api.constantes.ConstantesApiDataExport;
import br.com.extrator.api.constantes.ConstantesApiDataExport.ConfiguracaoEntidade;
import br.com.extrator.modelo.dataexport.cotacao.CotacaoDTO;
import br.com.extrator.modelo.dataexport.localizacaocarga.LocalizacaoCargaDTO;
import br.com.extrator.modelo.dataexport.contasapagar.ContasAPagarDTO;
import br.com.extrator.modelo.dataexport.manifestos.ManifestoDTO;
import br.com.extrator.util.configuracao.CarregadorConfig;
import br.com.extrator.util.validacao.ConstantesEntidades;
import br.com.extrator.util.formatacao.FormatadorData;
import br.com.extrator.util.http.GerenciadorRequisicaoHttp;
import br.com.extrator.util.mapeamento.MapperUtil;
import br.com.extrator.db.entity.PageAuditEntity;
import br.com.extrator.db.repository.PageAuditRepository;

/**
 * Cliente para extraÃƒÂ§ÃƒÂ£o de dados da API Data Export do ESL Cloud.
 * 
 * @author Sistema de ExtraÃƒÂ§ÃƒÂ£o ESL Cloud
 * @version 2.0
 */
public class ClienteApiDataExport {
    private static final Logger logger = LoggerFactory.getLogger(ClienteApiDataExport.class);

    // Atributos da classe
    private final HttpClient httpClient;
    private final String urlBase;
    private final String token;
    private final GerenciadorRequisicaoHttp gerenciadorRequisicao;
    private final Duration timeoutRequisicao;
    private final PageAuditRepository pageAuditRepository;
    private String executionUuid;

    // PROTEÃƒâ€¡Ãƒâ€¢ES CONTRA LOOPS INFINITOS - Replicadas do ClienteApiRest
    // PROBLEMA #7 CORRIGIDO: Valor agora obtido de CarregadorConfig
    private static final int INTERVALO_LOG_PROGRESSO = 10; // A cada 10 pÃƒÂ¡ginas

    // CIRCUIT BREAKER
    private final Map<String, Integer> contadorFalhasConsecutivas = new HashMap<>();
    private final Set<String> templatesComCircuitAberto = new HashSet<>();
    private static final int MAX_FALHAS_CONSECUTIVAS = 5;

    // NOTA: Constantes de Template IDs, campos de data e tabelas foram movidas para:
    // ConstantesApiDataExport.java - usar ConstantesApiDataExport.obterConfiguracao(entidade)

    /**
     * Construtor que inicializa o cliente da API Data Export.
     * Carrega as configuraÃƒÂ§ÃƒÂµes necessÃƒÂ¡rias e inicializa os componentes HTTP.
     */
    public ClienteApiDataExport() {
        logger.info("Inicializando cliente da API Data Export");

        // Inicializa HttpClient
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        // Carrega configuraÃƒÂ§ÃƒÂµes usando CarregadorConfig
        this.urlBase = CarregadorConfig.obterUrlBaseApi();
        this.token = CarregadorConfig.obterTokenApiDataExport();
        this.timeoutRequisicao = CarregadorConfig.obterTimeoutApiRest();

        // Valida configuraÃƒÂ§ÃƒÂµes obrigatÃƒÂ³rias
        if (urlBase == null || urlBase.trim().isEmpty()) {
            throw new IllegalStateException("URL base da API nÃƒÂ£o configurada");
        }
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalStateException("Token da API Data Export nÃƒÂ£o configurado");
        }

        // Template IDs agora sÃƒÂ£o obtidos de ConstantesApiDataExport.obterConfiguracao(entidade)
        logger.debug("Template IDs configurados via ConstantesApiDataExport");

        // Inicializa o gerenciador de requisiÃƒÂ§ÃƒÂµes HTTP (Singleton - throttling GLOBAL)
        this.gerenciadorRequisicao = GerenciadorRequisicaoHttp.getInstance();
        this.pageAuditRepository = new PageAuditRepository();

        logger.info("Cliente da API Data Export inicializado com sucesso");
        logger.debug("URL base configurada: {}", urlBase);
    }

    public void setExecutionUuid(final String uuid) {
        this.executionUuid = uuid;
    }

    /**
     * Busca dados de manifestos da API Data Export (ÃƒÂºltimas 24h).
     * MÃƒÂ©todo de conveniÃƒÂªncia que delega para buscarManifestos(dataInicio, dataFim).
     * 
     * @return ResultadoExtracao indicando se a busca foi completa ou interrompida
     */
    public ResultadoExtracao<ManifestoDTO> buscarManifestos() {
        final LocalDate hoje = LocalDate.now();
        final LocalDate ontem = hoje.minusDays(1);
        return buscarManifestos(ontem, hoje);
    }

    /**
     * Busca dados de cotaÃƒÂ§ÃƒÂµes da API Data Export (ÃƒÂºltimas 24h).
     * MÃƒÂ©todo de conveniÃƒÂªncia que delega para buscarCotacoes(dataInicio, dataFim).
     * 
     * @return ResultadoExtracao indicando se a busca foi completa ou interrompida
     */
    public ResultadoExtracao<CotacaoDTO> buscarCotacoes() {
        final LocalDate hoje = LocalDate.now();
        final LocalDate ontem = hoje.minusDays(1);
        return buscarCotacoes(ontem, hoje);
    }

    /**
     * Busca dados de localizaÃƒÂ§ÃƒÂ£o de carga da API Data Export (ÃƒÂºltimas 24h).
     * MÃƒÂ©todo de conveniÃƒÂªncia que delega para buscarLocalizacaoCarga(dataInicio, dataFim).
     * 
     * @return ResultadoExtracao indicando se a busca foi completa ou interrompida
     */
    public ResultadoExtracao<LocalizacaoCargaDTO> buscarLocalizacaoCarga() {
        final LocalDate hoje = LocalDate.now();
        final LocalDate ontem = hoje.minusDays(1);
        return buscarLocalizacaoCarga(ontem, hoje);
    }

    /**
     * Busca dados de Faturas a Pagar (Contas a Pagar) da API Data Export (ÃƒÂºltimas 24h).
     * MÃƒÂ©todo de conveniÃƒÂªncia que delega para buscarContasAPagar(dataInicio, dataFim).
     */
    public ResultadoExtracao<ContasAPagarDTO> buscarContasAPagar() {
        final LocalDate hoje = LocalDate.now();
        final LocalDate ontem = hoje.minusDays(1);
        return buscarContasAPagar(ontem, hoje);
    }

    /**
     * Busca dados de Faturas por Cliente da API Data Export (ÃƒÂºltimas 24h).
     * MÃƒÂ©todo de conveniÃƒÂªncia que delega para buscarFaturasPorCliente(dataInicio, dataFim).
     */
    public ResultadoExtracao<br.com.extrator.modelo.dataexport.faturaporcliente.FaturaPorClienteDTO> buscarFaturasPorCliente() {
        final LocalDate hoje = LocalDate.now();
        final LocalDate ontem = hoje.minusDays(1);
        return buscarFaturasPorCliente(ontem, hoje);
    }

    /**
     * Busca dados de manifestos da API Data Export para um intervalo de datas.
     * 
     * @param dataInicio Data de inÃƒÂ­cio do perÃƒÂ­odo
     * @param dataFim Data de fim do perÃƒÂ­odo
     * @return ResultadoExtracao indicando se a busca foi completa ou interrompida
     */
    public ResultadoExtracao<ManifestoDTO> buscarManifestos(final java.time.LocalDate dataInicio, final java.time.LocalDate dataFim) {
        logger.info("Buscando manifestos da API DataExport - PerÃƒÂ­odo: {} a {}", dataInicio, dataFim);
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.MANIFESTOS);
        final Instant inicio = dataInicio.atStartOfDay().atZone(java.time.ZoneOffset.UTC).toInstant();
        final Instant fim = dataFim.atTime(23, 59, 59).atZone(java.time.ZoneOffset.UTC).toInstant();
        return buscarDadosGenericos(config.templateId(), config.tabelaApi(), config.campoData(),
                new TypeReference<List<ManifestoDTO>>() {}, inicio, fim, config);
    }

    /**
     * Busca dados de cotaÃƒÂ§ÃƒÂµes da API Data Export para um intervalo de datas.
     * 
     * @param dataInicio Data de inÃƒÂ­cio do perÃƒÂ­odo
     * @param dataFim Data de fim do perÃƒÂ­odo
     * @return ResultadoExtracao indicando se a busca foi completa ou interrompida
     */
    public ResultadoExtracao<CotacaoDTO> buscarCotacoes(final java.time.LocalDate dataInicio, final java.time.LocalDate dataFim) {
        logger.info("Buscando cotaÃƒÂ§ÃƒÂµes da API DataExport - PerÃƒÂ­odo: {} a {}", dataInicio, dataFim);
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.COTACOES);
        final Instant inicio = dataInicio.atStartOfDay().atZone(java.time.ZoneOffset.UTC).toInstant();
        final Instant fim = dataFim.atTime(23, 59, 59).atZone(java.time.ZoneOffset.UTC).toInstant();
        return buscarDadosGenericos(config.templateId(), config.tabelaApi(), config.campoData(),
                new TypeReference<List<CotacaoDTO>>() {}, inicio, fim, config);
    }

    /**
     * Busca dados de localizaÃƒÂ§ÃƒÂ£o de carga da API Data Export para um intervalo de datas.
     * 
     * @param dataInicio Data de inÃƒÂ­cio do perÃƒÂ­odo
     * @param dataFim Data de fim do perÃƒÂ­odo
     * @return ResultadoExtracao indicando se a busca foi completa ou interrompida
     */
    public ResultadoExtracao<LocalizacaoCargaDTO> buscarLocalizacaoCarga(final java.time.LocalDate dataInicio, final java.time.LocalDate dataFim) {
        logger.info("Buscando localizaÃƒÂ§ÃƒÂ£o de carga da API DataExport - PerÃƒÂ­odo: {} a {}", dataInicio, dataFim);
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.LOCALIZACAO_CARGAS);
        final Instant inicio = dataInicio.atStartOfDay().atZone(java.time.ZoneOffset.UTC).toInstant();
        final Instant fim = dataFim.atTime(23, 59, 59).atZone(java.time.ZoneOffset.UTC).toInstant();
        return buscarDadosGenericos(config.templateId(), config.tabelaApi(), config.campoData(),
                new TypeReference<List<LocalizacaoCargaDTO>>() {}, inicio, fim, config);
    }

    /**
     * Busca dados de Faturas a Pagar (Contas a Pagar) da API Data Export para um intervalo de datas.
     * 
     * @param dataInicio Data de inÃƒÂ­cio do perÃƒÂ­odo
     * @param dataFim Data de fim do perÃƒÂ­odo
     * @return ResultadoExtracao indicando se a busca foi completa ou interrompida
     */
    public ResultadoExtracao<ContasAPagarDTO> buscarContasAPagar(final java.time.LocalDate dataInicio, final java.time.LocalDate dataFim) {
        logger.info("Buscando Faturas a Pagar da API DataExport - PerÃƒÂ­odo: {} a {}", dataInicio, dataFim);
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.CONTAS_A_PAGAR);
        final Instant inicio = dataInicio.atStartOfDay().atZone(java.time.ZoneOffset.UTC).toInstant();
        final Instant fim = dataFim.atTime(23, 59, 59).atZone(java.time.ZoneOffset.UTC).toInstant();

        final List<ConfiguracaoEntidade> tentativas = List.of(
            config,
            new ConfiguracaoEntidade(
                config.templateId(),
                config.campoData(),
                config.tabelaApi(),
                "50",
                Duration.ofSeconds(Math.max(120, config.timeout().getSeconds())),
                "issue_date desc",
                config.usaSearchNested()
            ),
            new ConfiguracaoEntidade(
                config.templateId(),
                config.campoData(),
                config.tabelaApi(),
                "25",
                Duration.ofSeconds(Math.max(180, config.timeout().getSeconds())),
                "issue_date desc",
                config.usaSearchNested()
            )
        );

        RuntimeException ultimoErro = null;
        for (int tentativa = 0; tentativa < tentativas.size(); tentativa++) {
            final ConfiguracaoEntidade configTentativa = tentativas.get(tentativa);
            if (tentativa > 0) {
                logger.warn(
                    "Retry Contas a Pagar apos timeout/422 | tentativa={} | per={} | timeout={}s | order_by={}",
                    tentativa + 1,
                    configTentativa.valorPer(),
                    configTentativa.timeout().getSeconds(),
                    configTentativa.orderBy()
                );
            }
            try {
                return buscarDadosGenericos(
                    configTentativa.templateId(),
                    configTentativa.tabelaApi(),
                    configTentativa.campoData(),
                    new TypeReference<List<ContasAPagarDTO>>() {},
                    inicio,
                    fim,
                    configTentativa
                );
            } catch (final RuntimeException e) {
                ultimoErro = e;
                final boolean timeoutOu422 = ehErroTimeoutOu422(e);
                final boolean ultimaTentativa = tentativa == tentativas.size() - 1;
                if (!timeoutOu422 || ultimaTentativa) {
                    throw e;
                }
                logger.warn(
                    "Falha Contas a Pagar com timeout/422. Nova tentativa sera executada com payload mais leve. erro={}",
                    e.getMessage()
                );
            }
        }

        throw ultimoErro != null ? ultimoErro : new RuntimeException("Falha inesperada ao extrair Contas a Pagar");
    }

    /**
     * Busca dados de Faturas por Cliente da API Data Export para um intervalo de datas.
     * 
     * @param dataInicio Data de inÃƒÂ­cio do perÃƒÂ­odo
     * @param dataFim Data de fim do perÃƒÂ­odo
     * @return ResultadoExtracao indicando se a busca foi completa ou interrompida
     */
    public ResultadoExtracao<br.com.extrator.modelo.dataexport.faturaporcliente.FaturaPorClienteDTO> buscarFaturasPorCliente(final java.time.LocalDate dataInicio, final java.time.LocalDate dataFim) {
        logger.info("Buscando Faturas por Cliente da API DataExport - PerÃƒÂ­odo: {} a {}", dataInicio, dataFim);
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.FATURAS_POR_CLIENTE);
        final Instant inicio = dataInicio.atStartOfDay().atZone(java.time.ZoneOffset.UTC).toInstant();
        final Instant fim = dataFim.atTime(23, 59, 59).atZone(java.time.ZoneOffset.UTC).toInstant();
        return buscarDadosGenericos(
            config.templateId(),
            config.tabelaApi(),
            config.campoData(),
            new com.fasterxml.jackson.core.type.TypeReference<java.util.List<br.com.extrator.modelo.dataexport.faturaporcliente.FaturaPorClienteDTO>>() {},
            inicio,
            fim,
            config
        );
    }

    /**
     * MÃƒÂ©todo genÃƒÂ©rico para buscar dados de qualquer template da API Data Export
     * com proteÃƒÂ§ÃƒÂµes contra loops infinitos e circuit breaker.
     * 
     * @param templateId   ID do template na API Data Export
     * @param nomeTabela   Nome da tabela para filtros
     * @param campoData    Campo de data para filtros
     * @param typeReference ReferÃƒÂªncia de tipo para desserializaÃƒÂ§ÃƒÂ£o
     * @param dataInicio   Data de inÃƒÂ­cio do perÃƒÂ­odo
     * @param dataFim      Data de fim do perÃƒÂ­odo
     * @param config       ConfiguraÃƒÂ§ÃƒÂ£o da entidade (de ConstantesApiDataExport)
     * @return ResultadoExtracao indicando se a busca foi completa ou interrompida
     */
    private <T> ResultadoExtracao<T> buscarDadosGenericos(final int templateId, final String nomeTabela, final String campoData,
            final TypeReference<List<T>> typeReference, final Instant dataInicio, final Instant dataFim, 
            final ConfiguracaoEntidade config) {
        
        // Determina o nome amigÃƒÂ¡vel do tipo de dados baseado na tabela
        final String tipoAmigavel = obterNomeAmigavelTipo(nomeTabela);
        final String chaveTemplate = "Template-" + templateId;
        if (this.executionUuid == null || this.executionUuid.isEmpty()) {
            this.executionUuid = UUID.randomUUID().toString();
        }
        final String runUuid = UUID.randomUUID().toString();
        
        // CIRCUIT BREAKER - Verificar se o template estÃƒÂ¡ com circuit aberto
        if (templatesComCircuitAberto.contains(chaveTemplate)) {
            logger.warn("Ã¢Å¡Â Ã¯Â¸Â CIRCUIT BREAKER ATIVO - Template {} ({}) temporariamente desabilitado devido a falhas consecutivas", 
                    templateId, tipoAmigavel);
            return ResultadoExtracao.incompleto(new ArrayList<>(), ResultadoExtracao.MotivoInterrupcao.CIRCUIT_BREAKER, 0, 0);
        }
        
        // Obter valor de 'per' e timeout da configuraÃƒÂ§ÃƒÂ£o
        final String valorPer = config.valorPer();
        final Duration timeout = config.timeout();
        int perInt;
        try {
            perInt = Integer.parseInt(valorPer);
        } catch (final NumberFormatException e) {
            perInt = 100;
        }
        final LocalDate janelaInicio = dataInicio.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        final LocalDate janelaFim = dataFim.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        
        logger.info("Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â");
        logger.info("INICIANDO EXTRAÃƒâ€¡ÃƒÆ’O: Template {} - {}", templateId, tipoAmigavel);
        logger.info("PerÃƒÂ­odo: {} atÃƒÂ© {}", 
                dataInicio.atZone(java.time.ZoneOffset.UTC).toLocalDate(), 
                dataFim.atZone(java.time.ZoneOffset.UTC).toLocalDate());
        logger.info("Valor 'per': {}", valorPer);
        logger.info("Timeout: {} segundos", timeout.getSeconds());
        logger.info("Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â");

        final List<T> resultadosFinais = new ArrayList<>();
        int paginaAtual = 1;
        int totalPaginas = 0;
        int totalRegistrosProcessados = 0;
        boolean interrompido = false;
        ResultadoExtracao.MotivoInterrupcao motivoInterrupcao = null;
        
        // Limites especÃƒÂ­ficos por template (para templates com muitos dados)
        final int limitePaginasBase = CarregadorConfig.obterLimitePaginasApiDataExport();
        final int maxRegistrosBase = CarregadorConfig.obterMaxRegistrosDataExport();
        
        // Aumentar limites para templates que tÃƒÂªm muitos dados em perÃƒÂ­odos longos
        // Template 4924 = FATURAS_POR_CLIENTE, Template 8656 = LOCALIZACAO_CARGAS
        final boolean templateComMuitosDados = templateId == 4924 || templateId == 8656;
        
        final int limitePaginas = templateComMuitosDados ? limitePaginasBase * 2 : limitePaginasBase; // Dobrar limite de pÃƒÂ¡ginas (1000)
        final int maxRegistros = templateComMuitosDados ? maxRegistrosBase * 10 : maxRegistrosBase; // 10x mais registros (100.000)

        try {
            while (true) {
                // PROTEÃƒâ€¡ÃƒÆ’O 1: Limite mÃƒÂ¡ximo de pÃƒÂ¡ginas
                if (paginaAtual > limitePaginas) {
                    logger.warn("Ã°Å¸Å¡Â¨ PROTEÃƒâ€¡ÃƒÆ’O ATIVADA - Template {} ({}): Limite de {} pÃƒÂ¡ginas atingido. Interrompendo busca para evitar loop infinito.", 
                            templateId, tipoAmigavel, limitePaginas);
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS;
                    break;
                }

                // PROTEÃƒâ€¡ÃƒÆ’O 2: Limite mÃƒÂ¡ximo de registros
                // PROBLEMA #7 CORRIGIDO: Usar valor de CarregadorConfig em vez de constante hardcoded
                if (totalRegistrosProcessados >= maxRegistros) {
                    logger.warn("Ã°Å¸Å¡Â¨ PROTEÃƒâ€¡ÃƒÆ’O ATIVADA - Template {} ({}): Limite de {} registros atingido. Interrompendo busca para evitar sobrecarga.", 
                            templateId, tipoAmigavel, maxRegistros);
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.LIMITE_REGISTROS;
                    break;
                }

                // Log inÃƒÂ­cio da pÃƒÂ¡gina
                logger.info("Ã¢â€ â€™ Requisitando pÃƒÂ¡gina {}...", paginaAtual);

                // URL base limpa sem parÃƒÂ¢metros de query (filtros e paginaÃƒÂ§ÃƒÂ£o vÃƒÂ£o no corpo JSON)
                final String url = urlBase + ConstantesApiDataExport.formatarEndpoint(templateId);

                // ConstrÃƒÂ³i o corpo JSON com search, page, per conforme formato do Postman
                final String corpoJson = construirCorpoRequisicao(nomeTabela, campoData, dataInicio, dataFim, paginaAtual, config);

                logger.debug("URL: {} | Corpo: {}", url, corpoJson);
                String reqHash;
                try {
                    final byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(corpoJson.getBytes(StandardCharsets.UTF_8));
                    final StringBuilder sb = new StringBuilder(d.length * 2);
                    for (final byte b : d) sb.append(String.format("%02x", b));
                    reqHash = sb.toString();
                } catch (final java.security.NoSuchAlgorithmException ex) {
                    reqHash = "";
                }

                final HttpRequest requisicao = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .timeout(timeout) // Timeout especÃƒÂ­fico por template
                        .method("GET", HttpRequest.BodyPublishers.ofString(corpoJson))
                        .build();

                // Executar requisiÃƒÂ§ÃƒÂ£o com mediÃƒÂ§ÃƒÂ£o de tempo
                final long tempoInicio = System.currentTimeMillis();
                final HttpResponse<String> resposta = gerenciadorRequisicao.executarRequisicao(this.httpClient, requisicao, 
                        "DataExport-Template-" + templateId + "-Page-" + paginaAtual);
                final long duracaoMs = System.currentTimeMillis() - tempoInicio;

                // Verificar resposta
                if (resposta == null) {
                    logger.error("Ã¢ÂÅ’ Erro: resposta nula na pÃƒÂ¡gina {}", paginaAtual);
                    incrementarContadorFalhas(chaveTemplate, tipoAmigavel);
                    throw new RuntimeException("Resposta nula na paginaÃƒÂ§ÃƒÂ£o - pÃƒÂ¡gina " + paginaAtual);
                }

                logger.info("Ã¢â€ Â Resposta recebida: Status {}, Tempo: {}ms", resposta.statusCode(), duracaoMs);
                String respHash;
                try {
                    final byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(resposta.body().getBytes(StandardCharsets.UTF_8));
                    final StringBuilder sb = new StringBuilder(d.length * 2);
                    for (final byte b : d) sb.append(String.format("%02x", b));
                    respHash = sb.toString();
                } catch (final java.security.NoSuchAlgorithmException ex) {
                    respHash = "";
                }

                if (resposta.statusCode() != 200) {
                    logger.error("Ã¢ÂÅ’ Erro HTTP {} na pÃƒÂ¡gina {}: {}", 
                            resposta.statusCode(), paginaAtual, resposta.body());
                    incrementarContadorFalhas(chaveTemplate, tipoAmigavel);
                    throw new RuntimeException("Erro HTTP " + resposta.statusCode() + " na pÃƒÂ¡gina " + paginaAtual);
                }

                // Parse da resposta
                List<T> registrosPagina;
                try {
                    final JsonNode raizJson = MapperUtil.sharedJson().readTree(resposta.body());
                    final JsonNode dadosNode = raizJson.has("data") ? raizJson.get("data") : raizJson;
                    // ObtÃƒÂ©m o campo de ID primÃƒÂ¡rio do orderBy da configuraÃƒÂ§ÃƒÂ£o
                    final String idKey = ConstantesApiDataExport.obterCampoIdPrimario(config);

                    if (dadosNode != null && dadosNode.isArray()) {
                        if (dadosNode.size() == 0) {
                            final PageAuditEntity audit = new PageAuditEntity();
                            audit.setExecutionUuid(this.executionUuid);
                            audit.setRunUuid(runUuid);
                            audit.setTemplateId(templateId);
                            audit.setPage(paginaAtual);
                            audit.setPer(perInt);
                            audit.setJanelaInicio(janelaInicio);
                            audit.setJanelaFim(janelaFim);
                            audit.setReqHash(reqHash);
                            audit.setRespHash(respHash);
                            audit.setTotalItens(0);
                            audit.setIdKey(idKey);
                            audit.setStatusCode(resposta.statusCode());
                            audit.setDuracaoMs((int) duracaoMs);
                            pageAuditRepository.inserir(audit);
                            logger.info("Ã¢â€“Â  Fim da paginaÃƒÂ§ÃƒÂ£o (pÃƒÂ¡gina vazia)");
                            totalPaginas = paginaAtual - 1;
                            break;
                        }
                        Long minNum = null;
                        Long maxNum = null;
                        String minStr = null;
                        String maxStr = null;
                        if (idKey != null) {
                            for (final JsonNode it : dadosNode) {
                                if (!it.has(idKey)) continue;
                                final JsonNode v = it.get(idKey);
                                if (v.isNumber()) {
                                    final long val = v.asLong();
                                    minNum = (minNum == null || val < minNum) ? val : minNum;
                                    maxNum = (maxNum == null || val > maxNum) ? val : maxNum;
                                } else {
                                    final String sv = v.asText();
                                    minStr = (minStr == null || sv.compareTo(minStr) < 0) ? sv : minStr;
                                    maxStr = (maxStr == null || sv.compareTo(maxStr) > 0) ? sv : maxStr;
                                }
                            }
                        }
                        final PageAuditEntity audit = new PageAuditEntity();
                        audit.setExecutionUuid(this.executionUuid);
                        audit.setRunUuid(runUuid);
                        audit.setTemplateId(templateId);
                        audit.setPage(paginaAtual);
                        audit.setPer(perInt);
                        audit.setJanelaInicio(janelaInicio);
                        audit.setJanelaFim(janelaFim);
                        audit.setReqHash(reqHash);
                        audit.setRespHash(respHash);
                        audit.setTotalItens(dadosNode.size());
                        audit.setIdKey(idKey);
                        audit.setIdMinNum(minNum);
                        audit.setIdMaxNum(maxNum);
                        audit.setIdMinStr(minStr);
                        audit.setIdMaxStr(maxStr);
                        audit.setStatusCode(resposta.statusCode());
                        audit.setDuracaoMs((int) duracaoMs);
                        pageAuditRepository.inserir(audit);
                        registrosPagina = MapperUtil.sharedJson().convertValue(dadosNode, typeReference);
                    } else {
                        final PageAuditEntity audit = new PageAuditEntity();
                        audit.setExecutionUuid(this.executionUuid);
                        audit.setRunUuid(runUuid);
                        audit.setTemplateId(templateId);
                        audit.setPage(paginaAtual);
                        audit.setPer(perInt);
                        audit.setJanelaInicio(janelaInicio);
                        audit.setJanelaFim(janelaFim);
                        audit.setReqHash(reqHash);
                        audit.setRespHash(respHash);
                        audit.setTotalItens(0);
                        audit.setStatusCode(resposta.statusCode());
                        audit.setDuracaoMs((int) duracaoMs);
                        pageAuditRepository.inserir(audit);
                        logger.warn("Ã¢Å¡Â Ã¯Â¸Â Resposta nÃƒÂ£o ÃƒÂ© um array vÃƒÂ¡lido na pÃƒÂ¡gina {}. Tratando como vazio.", paginaAtual);
                        totalPaginas = paginaAtual - 1;
                        break;
                    }
                } catch (final Exception e) {
                    logger.error("Ã¢ÂÅ’ Erro ao parsear JSON da pÃƒÂ¡gina {}: {}", paginaAtual, e.getMessage());
                    incrementarContadorFalhas(chaveTemplate, tipoAmigavel);
                    throw new RuntimeException("Erro ao parsear pÃƒÂ¡gina " + paginaAtual, e);
                }

                logger.info("Ã¢Å“â€œ PÃƒÂ¡gina {}: {} registros parseados", paginaAtual, registrosPagina.size());

                

                // Adicionar registros
                resultadosFinais.addAll(registrosPagina);
                totalRegistrosProcessados += registrosPagina.size();
                
                // Reset do contador de falhas em caso de sucesso
                contadorFalhasConsecutivas.put(chaveTemplate, 0);
                
                logger.info("Ã¢â€ â€˜ Total acumulado: {} registros", totalRegistrosProcessados);

                // Log de progresso a cada intervalo definido
                if (paginaAtual % INTERVALO_LOG_PROGRESSO == 0) {
                    logger.info("Ã¢ÂÂ³ Progresso: {} pÃƒÂ¡ginas processadas, {} registros", 
                            paginaAtual, totalRegistrosProcessados);
                }

                // PrÃƒÂ³xima pÃƒÂ¡gina
                paginaAtual++;
            }

            // Reset circuit breaker em caso de sucesso
            contadorFalhasConsecutivas.put(chaveTemplate, 0);

            logger.info("Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â");
            logger.info("Ã¢Å“â€¦ EXTRAÃƒâ€¡ÃƒÆ’O CONCLUÃƒÂDA: {} registros em {} pÃƒÂ¡ginas", 
                    totalRegistrosProcessados, totalPaginas > 0 ? totalPaginas : (paginaAtual - 1));
            logger.info("Ã¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢ÂÃ¢â€¢Â");

            // Retornar ResultadoExtracao
            if (interrompido) {
                // Usar o motivo correto da interrupÃƒÂ§ÃƒÂ£o (LIMITE_PAGINAS ou LIMITE_REGISTROS)
                final ResultadoExtracao.MotivoInterrupcao motivo = motivoInterrupcao != null 
                        ? motivoInterrupcao 
                        : ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS; // Fallback
                return ResultadoExtracao.incompleto(resultadosFinais, motivo, 
                        totalPaginas > 0 ? totalPaginas : (paginaAtual - 1), totalRegistrosProcessados);
            } else {
                return ResultadoExtracao.completo(resultadosFinais, 
                        totalPaginas > 0 ? totalPaginas : (paginaAtual - 1), totalRegistrosProcessados);
            }

        } catch (final RuntimeException e) {
            logger.error("Ã¢ÂÅ’ ERRO CRÃƒÂTICO na extraÃƒÂ§ÃƒÂ£o de {}: {}", tipoAmigavel, e.getMessage(), e);
            incrementarContadorFalhas(chaveTemplate, tipoAmigavel);
            throw new RuntimeException("Falha na extraÃƒÂ§ÃƒÂ£o de " + tipoAmigavel, e);
        }
    }

    /**
     * Incrementa o contador de falhas consecutivas e ativa o circuit breaker se necessÃƒÂ¡rio.
     * 
     * @param chaveTemplate Chave identificadora do template
     * @param tipoAmigavel Nome amigÃƒÂ¡vel do tipo para logs
     */
    private void incrementarContadorFalhas(final String chaveTemplate, final String tipoAmigavel) {
        final int falhas = contadorFalhasConsecutivas.getOrDefault(chaveTemplate, 0) + 1;
        contadorFalhasConsecutivas.put(chaveTemplate, falhas);
        
        if (falhas >= MAX_FALHAS_CONSECUTIVAS) {
            templatesComCircuitAberto.add(chaveTemplate);
            logger.error("Ã°Å¸Å¡Â¨ CIRCUIT BREAKER ATIVADO - Template {} ({}): {} falhas consecutivas. Template temporariamente desabilitado.", 
                    chaveTemplate, tipoAmigavel, falhas);
        } else {
            logger.warn("Ã¢Å¡Â Ã¯Â¸Â Falha {}/{} para template {} ({})", falhas, MAX_FALHAS_CONSECUTIVAS, chaveTemplate, tipoAmigavel);
        }
    }

    /**
     * Detecta erros de timeout retornados pela API, inclusive quando encapsulados
     * em HTTP 422 com mensagem textual de timeout.
     */
    private boolean ehErroTimeoutOu422(final Throwable erro) {
        Throwable atual = erro;
        while (atual != null) {
            final String mensagem = atual.getMessage();
            if (mensagem != null) {
                final String msg = mensagem.toLowerCase(Locale.ROOT);
                if (msg.contains("http 422") || msg.contains("tempo limite") || msg.contains("timeout")) {
                    return true;
                }
            }
            atual = atual.getCause();
        }
        return false;
    }

    /**
     * Determina o nome amigÃƒÂ¡vel do tipo de dados baseado no nome da tabela da API.
     * 
     * @param nomeTabela Nome da tabela da API
     * @return Nome amigÃƒÂ¡vel para logs
     */
    private String obterNomeAmigavelTipo(final String nomeTabela) {
        return switch (nomeTabela) {
            case "manifests" -> "Manifestos";
            case "quotes" -> "CotaÃƒÂ§ÃƒÂµes";
            case "freights" -> "LocalizaÃƒÂ§ÃƒÂµes de Carga / Fretes";
            case "accounting_debits" -> "Contas a Pagar";
            default -> "Dados";
        };
    }

    /**
     * ConstrÃƒÂ³i o corpo JSON da requisiÃƒÂ§ÃƒÂ£o conforme formato esperado pela API DataExport.
     * Formato: {"search": {"nomeTabela": {"campoData": "yyyy-MM-dd - yyyy-MM-dd"}}, "page": "1", "per": "1000|10000"}
     * 
     * @param nomeTabela Nome da tabela para o campo search
     * @param campoData Nome do campo de data especÃƒÂ­fico do template
     * @param dataInicio Data de inÃƒÂ­cio do filtro
     * @param dataFim Data de fim do filtro
     * @param pagina NÃƒÂºmero da pÃƒÂ¡gina atual
     * @param config ConfiguraÃƒÂ§ÃƒÂ£o da entidade (de ConstantesApiDataExport)
     * @return String JSON formatada para o corpo da requisiÃƒÂ§ÃƒÂ£o
     */
    private String construirCorpoRequisicao(final String nomeTabela, final String campoData, 
            final Instant dataInicio, final Instant dataFim, final int pagina, final ConfiguracaoEntidade config) {
        try {
            final ObjectNode corpo = MapperUtil.sharedJson().createObjectNode();
            final ObjectNode search = MapperUtil.sharedJson().createObjectNode();
            final ObjectNode table = MapperUtil.sharedJson().createObjectNode();

            // PROBLEMA 13 CORRIGIDO: Usar FormatadorData em vez de criar formatter inline
            // Formata as datas no formato yyyy-MM-dd - yyyy-MM-dd
            final String dataInicioStr = dataInicio.atZone(java.time.ZoneOffset.UTC).toLocalDate().format(FormatadorData.ISO_DATE);
            final String dataFimStr = dataFim.atZone(java.time.ZoneOffset.UTC).toLocalDate().format(FormatadorData.ISO_DATE);
            final String range = dataInicioStr + " - " + dataFimStr;

            // ConstrÃƒÂ³i a estrutura JSON conforme formato do Postman
            // Usa config.usaSearchNested() para determinar estrutura
            if (config.usaSearchNested()) {
                final ObjectNode searchNested = MapperUtil.sharedJson().createObjectNode();
                searchNested.put(campoData, range);
                searchNested.put("created_at", "");
                search.set(nomeTabela, searchNested);
            } else {
                table.put(campoData, range);
                search.set(nomeTabela, table);
            }

            corpo.set("search", search);
            corpo.put("page", String.valueOf(pagina));
            corpo.put("per", config.valorPer());
            corpo.put("order_by", config.orderBy());

            final String corpoJson = MapperUtil.toJson(corpo);
            logger.debug("Corpo JSON construÃƒÂ­do: {}", corpoJson);
            return corpoJson;
            
        } catch (final Exception e) {
            logger.error("Erro ao construir corpo da requisiÃƒÂ§ÃƒÂ£o: {}", e.getMessage(), e);
            return "{}";
        }
    }

    /**
     * ObtÃƒÂ©m a contagem total de manifestos para uma data de referÃƒÂªncia especÃƒÂ­fica
     * Baixa o CSV e conta as linhas de forma eficiente usando NIO
     * 
     * @param dataReferencia Data de referÃƒÂªncia para filtrar os manifestos
     * @return NÃƒÂºmero total de manifestos encontrados
     * @throws RuntimeException se houver erro no download ou processamento do CSV
     */
    public int obterContagemManifestos(final LocalDate dataReferencia) {
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.MANIFESTOS);
        return obterContagemGenericaCsv(
            config.templateId(), 
            config.tabelaApi(), 
            config.campoData(), 
            dataReferencia, 
            "manifestos"
        );
    }

    /**
     * ObtÃƒÂ©m a contagem total de cotaÃƒÂ§ÃƒÂµes para uma data de referÃƒÂªncia especÃƒÂ­fica
     * Baixa o CSV e conta as linhas de forma eficiente usando NIO
     * 
     * @param dataReferencia Data de referÃƒÂªncia para filtrar as cotaÃƒÂ§ÃƒÂµes
     * @return NÃƒÂºmero total de cotaÃƒÂ§ÃƒÂµes encontradas
     * @throws RuntimeException se houver erro no download ou processamento do CSV
     */
    public int obterContagemCotacoes(final LocalDate dataReferencia) {
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.COTACOES);
        return obterContagemGenericaCsv(
            config.templateId(), 
            config.tabelaApi(), 
            config.campoData(), 
            dataReferencia, 
            "cotaÃƒÂ§ÃƒÂµes"
        );
    }

    /**
     * ObtÃƒÂ©m a contagem total de localizaÃƒÂ§ÃƒÂµes de carga para uma data de referÃƒÂªncia especÃƒÂ­fica
     * Baixa o CSV e conta as linhas de forma eficiente usando NIO
     * 
     * @param dataReferencia Data de referÃƒÂªncia para filtrar as localizaÃƒÂ§ÃƒÂµes
     * @return NÃƒÂºmero total de localizaÃƒÂ§ÃƒÂµes de carga encontradas
     * @throws RuntimeException se houver erro no download ou processamento do CSV
     */
    public int obterContagemLocalizacoesCarga(final LocalDate dataReferencia) {
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.LOCALIZACAO_CARGAS);
        return obterContagemGenericaCsv(
            config.templateId(), 
            config.tabelaApi(), 
            config.campoData(), 
            dataReferencia, 
            "localizaÃƒÂ§ÃƒÂµes de carga"
        );
    }

    /**
     * ObtÃƒÂ©m contagem via CSV para Faturas a Pagar.
     */
    public int obterContagemContasAPagar(final LocalDate dataReferencia) {
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.CONTAS_A_PAGAR);
        return obterContagemGenericaCsv(
            config.templateId(),
            config.tabelaApi(),
            config.campoData(),
            dataReferencia,
            "faturas a pagar"
        );
    }

    /**
     * MÃƒÂ©todo genÃƒÂ©rico para obter contagem de registros via download e contagem de CSV
     * Implementa a estratÃƒÂ©gia recomendada na documentaÃƒÂ§ÃƒÂ£o: baixar CSV e contar linhas
     * 
     * @param templateId ID do template para a requisiÃƒÂ§ÃƒÂ£o
     * @param nomeTabela Nome da tabela para filtros
     * @param campoData Campo de data para filtros
     * @param dataReferencia Data de referÃƒÂªncia para filtros
     * @param tipoAmigavel Nome amigÃƒÂ¡vel do tipo de dados para logs
     * @return NÃƒÂºmero total de registros encontrados
     * @throws RuntimeException se houver erro no download ou processamento
     */
    private int obterContagemGenericaCsv(final int templateId, final String nomeTabela, final String campoData, 
            final LocalDate dataReferencia, final String tipoAmigavel) {
        
        final String chaveTemplate = "Template-" + templateId;
        
        // CIRCUIT BREAKER - Verificar se o template estÃƒÂ¡ com circuit aberto
        if (templatesComCircuitAberto.contains(chaveTemplate)) {
            logger.warn("Ã¢Å¡Â Ã¯Â¸Â CIRCUIT BREAKER ATIVO - Template {} ({}) temporariamente desabilitado para contagem", 
                    templateId, tipoAmigavel);
            return 0;
        }

        logger.info("Ã°Å¸â€Â¢ Obtendo contagem de {} via CSV - Template: {}, Data: {}", 
                tipoAmigavel, templateId, dataReferencia);

        final Path arquivoTemporario = null;
        try {
            // Converter LocalDate para Instant (inÃƒÂ­cio e fim do dia)
            final Instant dataInicio = dataReferencia.atStartOfDay().atZone(java.time.ZoneOffset.UTC).toInstant();
            final Instant dataFim = dataReferencia.plusDays(1).atStartOfDay().atZone(java.time.ZoneOffset.UTC).toInstant();

            // URL para download do CSV
            final String url = urlBase + ConstantesApiDataExport.formatarEndpoint(templateId);

            // Construir corpo da requisiÃƒÂ§ÃƒÂ£o com per=1 para otimizaÃƒÂ§ÃƒÂ£o (apenas primeira pÃƒÂ¡gina)
            final String corpoJson = construirCorpoRequisicaoCsv(nomeTabela, campoData, dataInicio, dataFim);

            logger.debug("Baixando CSV para contagem via URL: {} com corpo: {}", url, corpoJson);

            final HttpRequest requisicao = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/csv") // Solicitar formato CSV
                    .timeout(this.timeoutRequisicao)
                    .method("GET", HttpRequest.BodyPublishers.ofString(corpoJson))
                    .build();

            final long inicioMs = System.currentTimeMillis();
            final HttpResponse<String> resposta = gerenciadorRequisicao.executarRequisicaoComCharset(
                    this.httpClient, requisicao, "contagem-csv-" + tipoAmigavel.replace(" ", "-"), StandardCharsets.ISO_8859_1);
            final long duracaoMs = System.currentTimeMillis() - inicioMs;

            if (resposta == null) {
                logger.error("Erro: resposta nula ao baixar CSV para contagem de {}", tipoAmigavel);
                throw new RuntimeException("Falha na requisiÃƒÂ§ÃƒÂ£o CSV: resposta ÃƒÂ© null");
            }

            if (resposta.statusCode() != 200) {
                final String mensagemErro = String.format("Erro ao baixar CSV para contagem de %s. Status: %d", 
                    tipoAmigavel, resposta.statusCode());
                logger.error("{} ({} ms) Body: {}", mensagemErro, duracaoMs, resposta.body());
                incrementarContadorFalhas(chaveTemplate, tipoAmigavel);
                throw new RuntimeException(mensagemErro);
            }

            // Contar linhas diretamente do corpo da resposta para evitar problemas de charset
            final String conteudoCsv = resposta.body();
            final long totalLinhas = conteudoCsv.lines().count();

            // Subtrair 1 para desconsiderar o cabeÃƒÂ§alho
            final int contagem = Math.max(0, (int) (totalLinhas - 1));

            contadorFalhasConsecutivas.put(chaveTemplate, 0);

            logger.info("Ã¢Å“â€¦ Contagem de {} obtida com sucesso via CSV: {} registros ({} ms)", 
                    tipoAmigavel, contagem, duracaoMs);

            return contagem;

        } catch (final RuntimeException e) {
            logger.error("Erro de runtime ao obter contagem de {} via CSV: {}", tipoAmigavel, e.getMessage(), e);
            incrementarContadorFalhas(chaveTemplate, tipoAmigavel);
            throw e; // Re-lanÃƒÂ§ar RuntimeException sem encapsular
        } catch (final Exception e) {
            logger.error("Erro inesperado ao obter contagem de {} via CSV: {}", tipoAmigavel, e.getMessage(), e);
            incrementarContadorFalhas(chaveTemplate, tipoAmigavel);
            throw new RuntimeException("Erro inesperado ao processar contagem de " + tipoAmigavel + " via CSV", e);
        } finally {
            // Garantir que o arquivo temporÃƒÂ¡rio seja deletado
            if (arquivoTemporario != null) {
                try {
                    Files.deleteIfExists(arquivoTemporario);
                    logger.debug("Arquivo temporÃƒÂ¡rio deletado: {}", arquivoTemporario);
                } catch (final IOException e) {
                    logger.warn("NÃƒÂ£o foi possÃƒÂ­vel deletar arquivo temporÃƒÂ¡rio {}: {}", 
                            arquivoTemporario, e.getMessage());
                } catch (final SecurityException e) {
                    logger.warn("Sem permissÃƒÂ£o para deletar arquivo temporÃƒÂ¡rio {}: {}", 
                            arquivoTemporario, e.getMessage());
                }
            }
        }
    }

    // NOTA: MÃƒÂ©todos obterValorPerPorTemplate() e obterTimeoutPorTemplate() foram removidos.
    // Agora usar config.valorPer() e config.timeout() de ConstantesApiDataExport.

    /**
     * ConstrÃƒÂ³i o corpo da requisiÃƒÂ§ÃƒÂ£o JSON para contagem via CSV
     * Similar ao mÃƒÂ©todo original, mas otimizado para contagem
     * 
     * @param nomeTabela Nome da tabela para filtros
     * @param campoData Campo de data para filtros
     * @param dataInicio Data de inÃƒÂ­cio do perÃƒÂ­odo
     * @param dataFim Data de fim do perÃƒÂ­odo
     * @return String JSON do corpo da requisiÃƒÂ§ÃƒÂ£o
     */
    private String construirCorpoRequisicaoCsv(final String nomeTabela, final String campoData, 
            final Instant dataInicio, final Instant dataFim) {
        try {
            final ObjectNode corpo = MapperUtil.sharedJson().createObjectNode();
            final ObjectNode search = MapperUtil.sharedJson().createObjectNode();
            final ObjectNode table = MapperUtil.sharedJson().createObjectNode();

            // Formatar as datas no formato yyyy-MM-dd - yyyy-MM-dd
            final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            final String dataInicioStr = dataInicio.atZone(java.time.ZoneOffset.UTC).toLocalDate().format(fmt);
            final String dataFimStr = dataFim.atZone(java.time.ZoneOffset.UTC).toLocalDate().format(fmt);
            final String range = dataInicioStr + " - " + dataFimStr;

            // Construir a estrutura JSON
            table.put(campoData, range);
            search.set(nomeTabela, table);

            corpo.set("search", search);
            corpo.put("page", "1");
            corpo.put("per", "10000");
            corpo.put("order_by", "sequence_code asc");

            final String corpoJson = MapperUtil.toJson(corpo);
            logger.debug("Corpo JSON para contagem CSV construÃƒÂ­do: {}", corpoJson);
            return corpoJson;
            
        } catch (final Exception e) {
            logger.error("Erro ao construir corpo da requisiÃƒÂ§ÃƒÂ£o para contagem CSV: {}", e.getMessage(), e);
            return "{}";
        }
    }

}

