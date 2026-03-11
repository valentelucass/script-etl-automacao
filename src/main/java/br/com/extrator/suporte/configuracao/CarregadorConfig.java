package br.com.extrator.suporte.configuracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/suporte/configuracao/CarregadorConfig.java
Classe  : CarregadorConfig (class)
Pacote  : br.com.extrator.suporte.configuracao
Modulo  : Suporte - Config
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.time.Duration;
import java.util.Properties;

public final class CarregadorConfig {
    public enum EtlIntegridadeModo {
        OPERACIONAL,
        STRICT_INTEGRITY
    }

    private CarregadorConfig() {
    }

    public static Properties carregarPropriedades() {
        return ConfigSource.carregarPropriedades();
    }

    public static void validarConexaoBancoDados() {
        ConfigBanco.validarConexaoBancoDados();
    }

    public static void validarTabelasEssenciais() {
        ConfigBanco.validarTabelasEssenciais();
    }

    public static String obterPropriedade(final String chave) {
        return ConfigBanco.obterPropriedade(chave);
    }

    public static String obterUrlBaseApi() {
        return ConfigApi.obterUrlBaseApi();
    }

    public static String obterTokenApiRest() {
        return ConfigApi.obterTokenApiRest();
    }

    public static String obterTokenApiGraphQL() {
        return ConfigApi.obterTokenApiGraphQL();
    }

    public static String obterEndpointGraphQL() {
        return ConfigApi.obterEndpointGraphQL();
    }

    public static String obterTokenApiDataExport() {
        return ConfigApi.obterTokenApiDataExport();
    }

    public static String obterUrlBancoDados() {
        return ConfigBanco.obterUrlBancoDados();
    }

    public static String obterUsuarioBancoDados() {
        return ConfigBanco.obterUsuarioBancoDados();
    }

    public static String obterSenhaBancoDados() {
        return ConfigBanco.obterSenhaBancoDados();
    }

    public static String obterNomeBancoDados() {
        return ConfigBanco.obterNomeBancoDados();
    }

    public static long obterThrottlingPadrao() {
        return ConfigValueParser.parseLong(
            ConfigSource.obterConfiguracao("API_THROTTLING_PADRAO_MS", "api.throttling.padrao_ms"),
            2000L,
            value -> value > 0L,
            null,
            null,
            null
        );
    }

    public static int obterMaxTentativasRetry() {
        return ConfigApi.obterMaxTentativasRetry();
    }

    public static int obterMaxTentativasRetryGraphQLPorDia() {
        return ConfigApi.obterMaxTentativasRetryGraphQLPorDia();
    }

    public static long obterDelayBaseRetry() {
        return ConfigApi.obterDelayBaseRetry();
    }

    public static double obterMultiplicadorRetry() {
        return ConfigApi.obterMultiplicadorRetry();
    }

    public static Duration obterTimeoutApiRest() {
        return ConfigApi.obterTimeoutApiRest();
    }

    public static String obterCorporationId() {
        return ConfigApi.obterCorporationId();
    }

    public static long obterThrottlingMinimo() {
        return ConfigApi.obterThrottlingMinimo();
    }

    public static int obterLimitePaginasApiRest() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_REST_MAX_PAGINAS", "api.rest.max.paginas"),
            500,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static int obterLimitePaginasApiGraphQL() {
        return ConfigApi.obterLimitePaginasApiGraphQL();
    }

    public static int obterLimitePaginasFaturasGraphQL() {
        return ConfigApi.obterLimitePaginasFaturasGraphQL();
    }

    public static int obterDiasJanelaFaturasGraphQL() {
        return ConfigApi.obterDiasJanelaFaturasGraphQL();
    }

    public static int obterLimitePaginasApiDataExport() {
        return ConfigApi.obterLimitePaginasApiDataExport();
    }

    public static int obterLimitePaginasApiDataExportPorTemplate(final int templateId) {
        return ConfigApi.obterLimitePaginasApiDataExportPorTemplate(templateId);
    }

    public static int obterMaxRegistrosGraphQL() {
        return ConfigApi.obterMaxRegistrosGraphQL();
    }

    public static int obterMaxRegistrosDataExport() {
        return ConfigApi.obterMaxRegistrosDataExport();
    }

    public static int obterMaxRegistrosDataExportPorTemplate(final int templateId) {
        return ConfigApi.obterMaxRegistrosDataExportPorTemplate(templateId);
    }

    public static boolean isParticionamentoJanelaDataExportAtivo() {
        return ConfigApi.isParticionamentoJanelaDataExportAtivo();
    }

    public static String obterMetodoHttpDataExportPreferencial() {
        return ConfigApi.obterMetodoHttpDataExportPreferencial();
    }

    public static int obterMaxTentativasTimeoutApiDataExportPorPagina() {
        return ConfigApi.obterMaxTentativasTimeoutApiDataExportPorPagina();
    }

    public static int obterMaxTentativasTimeoutApiDataExportPaginaUm() {
        return ConfigApi.obterMaxTentativasTimeoutApiDataExportPaginaUm();
    }

    public static long obterDelayBaseTimeoutApiDataExportPorPaginaMs() {
        return ConfigApi.obterDelayBaseTimeoutApiDataExportPorPaginaMs();
    }

    public static long obterDelayMaximoTimeoutApiDataExportPorPaginaMs() {
        return ConfigApi.obterDelayMaximoTimeoutApiDataExportPorPaginaMs();
    }

    public static double obterJitterTimeoutApiDataExportPorPagina() {
        return ConfigApi.obterJitterTimeoutApiDataExportPorPagina();
    }

    public static int obterBatchSize() {
        return ConfigBanco.obterBatchSize();
    }

    public static boolean isContinuarAposErro() {
        return ConfigBanco.isContinuarAposErro();
    }

    public static long obterDelayEntreExtracoes() {
        return ConfigEtl.obterDelayEntreExtracoes();
    }

    public static int obterTimeoutValidacaoConexao() {
        return ConfigBanco.obterTimeoutValidacaoConexao();
    }

    public static int obterThreadsProcessamentoFaturas() {
        return ConfigEtl.obterThreadsProcessamentoFaturas();
    }

    public static int obterLimiteErrosConsecutivos() {
        return ConfigEtl.obterLimiteErrosConsecutivos();
    }

    public static double obterMultiplicadorDelayErros() {
        return ConfigEtl.obterMultiplicadorDelayErros();
    }

    public static int obterIntervaloLogProgressoEnriquecimento() {
        return ConfigEtl.obterIntervaloLogProgressoEnriquecimento();
    }

    public static int obterHeartbeatSegundos() {
        return ConfigEtl.obterHeartbeatSegundos();
    }

    public static int obterMaxInvalidosToleradosPorEntidade() {
        return ConfigEtl.obterMaxInvalidosToleradosPorEntidade();
    }

    public static double obterPercentualMaxInvalidosToleradosPorEntidade() {
        return ConfigEtl.obterPercentualMaxInvalidosToleradosPorEntidade();
    }

    public static EtlIntegridadeModo obterModoIntegridadeEtl() {
        return ConfigEtl.obterModoIntegridadeEtl();
    }

    public static boolean isModoIntegridadeEstrito() {
        return ConfigEtl.isModoIntegridadeEstrito();
    }

    public static int obterMaxOrfaosManifestosTolerados() {
        return ConfigEtl.obterMaxOrfaosManifestosTolerados();
    }

    public static double obterPercentualMaxOrfaosManifestosTolerados() {
        return ConfigEtl.obterPercentualMaxOrfaosManifestosTolerados();
    }

    public static int obterEtlReferencialColetasBackfillDias() {
        return ConfigEtl.obterEtlReferencialColetasBackfillDias();
    }

    public static boolean isLoopReconciliacaoAtiva() {
        return ConfigLoop.isReconciliacaoAtiva();
    }

    public static int obterLoopReconciliacaoMaxPorCiclo() {
        return ConfigLoop.obterReconciliacaoMaxPorCiclo();
    }

    public static int obterLoopReconciliacaoDiasRetroativosFalha() {
        return ConfigLoop.obterReconciliacaoDiasRetroativosFalha();
    }
}
