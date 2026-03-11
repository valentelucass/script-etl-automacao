package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/ClienteApiDataExport.java
Classe  : ClienteApiDataExport (class)
Pacote  : br.com.extrator.integracao
Modulo  : Integracao HTTP
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;

import br.com.extrator.integracao.constantes.ConstantesApiDataExport;
import br.com.extrator.integracao.constantes.ConstantesApiDataExport.ConfiguracaoEntidade;
import br.com.extrator.persistencia.repositorio.PageAuditRepository;
import br.com.extrator.dominio.dataexport.contasapagar.ContasAPagarDTO;
import br.com.extrator.dominio.dataexport.cotacao.CotacaoDTO;
import br.com.extrator.dominio.dataexport.localizacaocarga.LocalizacaoCargaDTO;
import br.com.extrator.dominio.dataexport.manifestos.ManifestoDTO;
import br.com.extrator.suporte.configuracao.ConfigApi;
import br.com.extrator.suporte.http.GerenciadorRequisicaoHttp;
import br.com.extrator.suporte.tempo.RelogioSistema;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public class ClienteApiDataExport {
    private static final Logger logger = LoggerFactory.getLogger(ClienteApiDataExport.class);
    private static final int INTERVALO_LOG_PROGRESSO = 10;
    private static final int MAX_FALHAS_CONSECUTIVAS = 5;
    private static final Duration JANELA_REABERTURA_CIRCUITO = Duration.ofMinutes(10);

    private final String urlBase;
    private final Duration timeoutRequisicao;
    private final DataExportRetryConfigFactory retryConfigFactory;
    private final DataExportAdaptiveRetrySupport adaptiveRetrySupport;
    private final DataExportCsvCountSupport csvCountSupport;
    private final DataExportPaginationSupport paginationSupport;
    private final DataExportPaginator paginator;
    private String executionUuid;

    public ClienteApiDataExport() {
        logger.info("Inicializando cliente da API Data Export");

        final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.urlBase = ConfigApi.obterUrlBaseApi();
        final String token = ConfigApi.obterTokenApiDataExport();
        this.timeoutRequisicao = ConfigApi.obterTimeoutApiRest();
        final String metodoDataExportEfetivo = ConfigApi.obterMetodoHttpDataExportPreferencial();
        final String modoGetDataExportEfetivo = "corpo";
        final int maxTentativasTimeoutPorPagina = ConfigApi.obterMaxTentativasTimeoutApiDataExportPorPagina();
        final int maxTentativasTimeoutPaginaUm = ConfigApi.obterMaxTentativasTimeoutApiDataExportPaginaUm();
        final long delayBaseTimeoutPorPaginaMs = ConfigApi.obterDelayBaseTimeoutApiDataExportPorPaginaMs();
        final long delayMaximoTimeoutPorPaginaMs = ConfigApi.obterDelayMaximoTimeoutApiDataExportPorPaginaMs();
        final double jitterTimeoutPorPagina = ConfigApi.obterJitterTimeoutApiDataExportPorPagina();

        this.retryConfigFactory = new DataExportRetryConfigFactory();
        this.adaptiveRetrySupport = new DataExportAdaptiveRetrySupport(logger);

        if (urlBase == null || urlBase.trim().isEmpty()) {
            throw new IllegalStateException("URL base da API não configurada");
        }
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalStateException("Token da API Data Export não configurado");
        }

        final GerenciadorRequisicaoHttp gerenciadorRequisicao = GerenciadorRequisicaoHttp.getInstance();
        final DataExportRequestFactory requestFactory = new DataExportRequestFactory(token);
        final DataExportRequestBodyFactory requestBodyFactory = new DataExportRequestBodyFactory(logger);
        final DataExportPageAuditLogger pageAuditLogger = new DataExportPageAuditLogger(new PageAuditRepository());
        final DataExportHttpExecutor httpExecutor = new DataExportHttpExecutor(
            logger,
            httpClient,
            gerenciadorRequisicao,
            requestFactory,
            metodoDataExportEfetivo,
            modoGetDataExportEfetivo,
            delayBaseTimeoutPorPaginaMs,
            delayMaximoTimeoutPorPaginaMs,
            jitterTimeoutPorPagina
        );
        this.paginationSupport = new DataExportPaginationSupport(
            logger,
            MAX_FALHAS_CONSECUTIVAS,
            JANELA_REABERTURA_CIRCUITO,
            new java.util.HashMap<>(),
            new java.util.HashSet<>(),
            new java.util.HashMap<>()
        );
        this.paginator = new DataExportPaginator(
            logger,
            this.urlBase,
            requestBodyFactory,
            pageAuditLogger,
            httpExecutor,
            maxTentativasTimeoutPorPagina,
            maxTentativasTimeoutPaginaUm,
            INTERVALO_LOG_PROGRESSO,
            this.paginationSupport
        );
        this.csvCountSupport = new DataExportCsvCountSupport(
            logger,
            this.urlBase,
            this.timeoutRequisicao,
            requestBodyFactory,
            httpExecutor::executarRequisicaoDataExportCsv,
            paginationSupport::isCircuitBreakerAtivo,
            paginationSupport::resetarEstadoFalhasTemplate,
            paginationSupport::incrementarContadorFalhas
        );

        logger.info("Cliente da API Data Export inicializado com sucesso");
        logger.debug("URL base configurada: {}", urlBase);
    }

    public void setExecutionUuid(final String uuid) {
        this.executionUuid = uuid;
    }

    public ResultadoExtracao<ManifestoDTO> buscarManifestos() {
        final LocalDate hoje = RelogioSistema.hoje();
        return buscarManifestos(hoje.minusDays(1), hoje);
    }

    public ResultadoExtracao<CotacaoDTO> buscarCotacoes() {
        final LocalDate hoje = RelogioSistema.hoje();
        return buscarCotacoes(hoje.minusDays(1), hoje);
    }

    public ResultadoExtracao<LocalizacaoCargaDTO> buscarLocalizacaoCarga() {
        final LocalDate hoje = RelogioSistema.hoje();
        return buscarLocalizacaoCarga(hoje.minusDays(1), hoje);
    }

    public ResultadoExtracao<ContasAPagarDTO> buscarContasAPagar() {
        final LocalDate hoje = RelogioSistema.hoje();
        return buscarContasAPagar(hoje.minusDays(1), hoje);
    }

    public ResultadoExtracao<br.com.extrator.dominio.dataexport.faturaporcliente.FaturaPorClienteDTO> buscarFaturasPorCliente() {
        final LocalDate hoje = RelogioSistema.hoje();
        return buscarFaturasPorCliente(hoje.minusDays(1), hoje);
    }

    public ResultadoExtracao<ManifestoDTO> buscarManifestos(final LocalDate dataInicio, final LocalDate dataFim) {
        logger.info("Buscando manifestos da API DataExport - Período: {} a {}", dataInicio, dataFim);
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.MANIFESTOS);
        final String chaveTemplate = "Template-" + config.templateId();
        paginationSupport.resetarEstadoFalhasTemplate(chaveTemplate);
        final Instant inicio = dataInicio.atStartOfDay().atZone(java.time.ZoneOffset.UTC).toInstant();
        final Instant fim = dataFim.atTime(23, 59, 59).atZone(java.time.ZoneOffset.UTC).toInstant();
        final List<ConfiguracaoEntidade> tentativas = retryConfigFactory.criarTentativasManifestos(config);

        return adaptiveRetrySupport.executar(
            "Manifestos",
            chaveTemplate,
            tentativas,
            configTentativa -> paginator.buscarDadosGenericos(
                this.executionUuid,
                configTentativa.templateId(),
                configTentativa.tabelaApi(),
                configTentativa.campoData(),
                new TypeReference<List<ManifestoDTO>>() {},
                inicio,
                fim,
                configTentativa
            ),
            paginationSupport::deveRetentarResultadoIncompleto,
            paginationSupport::selecionarMelhorResultadoParcial,
            paginationSupport::ehErroTimeoutOu422,
            paginationSupport::resetarEstadoFalhasTemplate,
            paginationSupport::resetarEstadoFalhasTemplate,
            paginationSupport::resetarEstadoFalhasTemplate
        );
    }

    public ResultadoExtracao<CotacaoDTO> buscarCotacoes(final LocalDate dataInicio, final LocalDate dataFim) {
        logger.info("Buscando cotações da API DataExport - Período: {} a {}", dataInicio, dataFim);
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.COTACOES);
        return buscarDadosDiretos(
            dataInicio,
            dataFim,
            config,
            new TypeReference<List<CotacaoDTO>>() {}
        );
    }

    public ResultadoExtracao<LocalizacaoCargaDTO> buscarLocalizacaoCarga(final LocalDate dataInicio, final LocalDate dataFim) {
        logger.info("Buscando localização de carga da API DataExport - Período: {} a {}", dataInicio, dataFim);
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.LOCALIZACAO_CARGAS);
        return buscarDadosDiretos(
            dataInicio,
            dataFim,
            config,
            new TypeReference<List<LocalizacaoCargaDTO>>() {}
        );
    }

    public ResultadoExtracao<ContasAPagarDTO> buscarContasAPagar(final LocalDate dataInicio, final LocalDate dataFim) {
        logger.info("Buscando Faturas a Pagar da API DataExport - Período: {} a {}", dataInicio, dataFim);
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.CONTAS_A_PAGAR);
        final Instant inicio = dataInicio.atStartOfDay().atZone(java.time.ZoneOffset.UTC).toInstant();
        final Instant fim = dataFim.atTime(23, 59, 59).atZone(java.time.ZoneOffset.UTC).toInstant();
        final List<ConfiguracaoEntidade> tentativas = retryConfigFactory.criarTentativasContasAPagar(config);

        return adaptiveRetrySupport.executar(
            "Contas a Pagar",
            null,
            tentativas,
            configTentativa -> paginator.buscarDadosGenericos(
                this.executionUuid,
                configTentativa.templateId(),
                configTentativa.tabelaApi(),
                configTentativa.campoData(),
                new TypeReference<List<ContasAPagarDTO>>() {},
                inicio,
                fim,
                configTentativa
            ),
            paginationSupport::deveRetentarResultadoIncompleto,
            paginationSupport::selecionarMelhorResultadoParcial,
            paginationSupport::ehErroTimeoutOu422,
            null,
            null,
            null
        );
    }

    public ResultadoExtracao<br.com.extrator.dominio.dataexport.faturaporcliente.FaturaPorClienteDTO> buscarFaturasPorCliente(final LocalDate dataInicio, final LocalDate dataFim) {
        logger.info("Buscando Faturas por Cliente da API DataExport - Período: {} a {}", dataInicio, dataFim);
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.FATURAS_POR_CLIENTE);
        return buscarDadosDiretos(
            dataInicio,
            dataFim,
            config,
            new TypeReference<List<br.com.extrator.dominio.dataexport.faturaporcliente.FaturaPorClienteDTO>>() {}
        );
    }

    public int obterContagemManifestos(final LocalDate dataReferencia) {
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.MANIFESTOS);
        try {
            return obterContagemGenericaCsv(config.templateId(), config.tabelaApi(), config.campoData(), dataReferencia, "manifestos");
        } catch (final RuntimeException e) {
            if (!paginationSupport.ehErroTimeoutOu422(e)) {
                throw e;
            }
            logger.warn("Contagem CSV de manifestos falhou por timeout/422. Aplicando fallback para extracao paginada. erro={}", e.getMessage());
            final ResultadoExtracao<ManifestoDTO> resultado = buscarManifestos(dataReferencia, dataReferencia);
            final int total = resultado.getDados() == null ? 0 : resultado.getDados().size();
            logger.info("Contagem de manifestos via fallback paginado: {} registros (completo={}, motivo={})", total, resultado.isCompleto(), resultado.getMotivoInterrupcao());
            return total;
        }
    }

    public int obterContagemCotacoes(final LocalDate dataReferencia) {
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.COTACOES);
        return obterContagemGenericaCsv(config.templateId(), config.tabelaApi(), config.campoData(), dataReferencia, "cotações");
    }

    public int obterContagemLocalizacoesCarga(final LocalDate dataReferencia) {
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.LOCALIZACAO_CARGAS);
        return obterContagemGenericaCsv(config.templateId(), config.tabelaApi(), config.campoData(), dataReferencia, "localizações de carga");
    }

    public int obterContagemContasAPagar(final LocalDate dataReferencia) {
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.CONTAS_A_PAGAR);
        return obterContagemGenericaCsv(config.templateId(), config.tabelaApi(), config.campoData(), dataReferencia, "faturas a pagar");
    }

    public int obterContagemFaturasPorCliente(final LocalDate dataReferencia) {
        final ConfiguracaoEntidade config = ConstantesApiDataExport.obterConfiguracao(ConstantesEntidades.FATURAS_POR_CLIENTE);
        return obterContagemGenericaCsv(config.templateId(), config.tabelaApi(), config.campoData(), dataReferencia, "faturas por cliente");
    }

    private <T> ResultadoExtracao<T> buscarDadosDiretos(final LocalDate dataInicio,
                                                        final LocalDate dataFim,
                                                        final ConfiguracaoEntidade config,
                                                        final TypeReference<List<T>> typeReference) {
        final Instant inicio = dataInicio.atStartOfDay().atZone(java.time.ZoneOffset.UTC).toInstant();
        final Instant fim = dataFim.atTime(23, 59, 59).atZone(java.time.ZoneOffset.UTC).toInstant();
        return paginator.buscarDadosGenericos(
            this.executionUuid,
            config.templateId(),
            config.tabelaApi(),
            config.campoData(),
            typeReference,
            inicio,
            fim,
            config
        );
    }

    private int obterContagemGenericaCsv(final int templateId,
                                         final String nomeTabela,
                                         final String campoData,
                                         final LocalDate dataReferencia,
                                         final String tipoAmigavel) {
        return csvCountSupport.obterContagemGenericaCsv(templateId, nomeTabela, campoData, dataReferencia, tipoAmigavel);
    }
}
