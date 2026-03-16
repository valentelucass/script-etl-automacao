package br.com.extrator.suporte.configuracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/suporte/configuracao/ConfigApi.java
Classe  : ConfigApi (class)
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
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigApi {
    private static final Logger logger = LoggerFactory.getLogger(ConfigApi.class);

    private ConfigApi() {
    }

    public static String obterUrlBaseApi() {
        return ConfigSource.obterConfiguracao(new String[] { "API_BASEURL", "API_BASE_URL" }, "api.baseurl");
    }

    public static String obterTokenApiRest() {
        return ConfigSource.obterConfiguracaoObrigatoria("API_REST_TOKEN");
    }

    public static String obterTokenApiGraphQL() {
        return ConfigSource.obterConfiguracaoObrigatoria("API_GRAPHQL_TOKEN");
    }

    public static String obterEndpointGraphQL() {
        return ConfigSource.obterConfiguracao("API_GRAPHQL_ENDPOINT", "api.graphql.endpoint");
    }

    public static String obterTokenApiDataExport() {
        return ConfigSource.obterConfiguracaoObrigatoria("API_DATAEXPORT_TOKEN");
    }

    public static Duration obterTimeoutApiRest() {
        final long segundos = ConfigValueParser.parseLong(
            ConfigSource.obterConfiguracao("API_REST_TIMEOUT_SECONDS", "api.rest.timeout.seconds"),
            120L,
            value -> value > 0L,
            logger,
            "api.rest.timeout.seconds",
            "120 segundos"
        );
        return Duration.ofSeconds(segundos);
    }

    public static String obterCorporationId() {
        return ConfigSource.obterConfiguracao("API_CORPORATION_ID", "api.corporation.id");
    }

    public static int obterMaxTentativasRetry() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_RETRY_MAX_TENTATIVAS", "api.retry.max_tentativas"),
            5,
            value -> value > 0,
            logger,
            "api.retry.max_tentativas",
            "5"
        );
    }

    public static int obterMaxTentativasRetryGraphQLPorDia() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "API_GRAPHQL_RETRY_MAX_TENTATIVAS_DIA",
                "api.graphql.retry.max_tentativas_dia"
            ),
            obterMaxTentativasRetry(),
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static long obterDelayBaseRetry() {
        return ConfigValueParser.parseLong(
            ConfigSource.obterConfiguracao("API_RETRY_DELAY_BASE_MS", "api.retry.delay_base_ms"),
            2000L,
            value -> value > 0L,
            logger,
            "api.retry.delay_base_ms",
            "2000ms"
        );
    }

    public static double obterMultiplicadorRetry() {
        return ConfigValueParser.parseDouble(
            ConfigSource.obterConfiguracao("API_RETRY_MULTIPLICADOR", "api.retry.multiplicador"),
            2.0d,
            value -> value >= 1.0d,
            logger,
            "api.retry.multiplicador",
            "2.0"
        );
    }

    public static long obterThrottlingMinimo() {
        return ConfigValueParser.parseLong(
            ConfigSource.obterConfiguracao("API_THROTTLING_MINIMO_MS", "api.throttling.minimo_ms"),
            2200L,
            value -> value > 0L,
            logger,
            "api.throttling.minimo_ms",
            "2200ms"
        );
    }

    public static int obterLimitePaginasApiGraphQL() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_GRAPHQL_MAX_PAGINAS", "api.graphql.max.paginas"),
            2000,
            value -> value > 0,
            logger,
            "api.graphql.max.paginas",
            "2000"
        );
    }

    public static int obterLimitePaginasFaturasGraphQL() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_GRAPHQL_FATURAS_MAX_PAGINAS", "api.graphql.faturas.max_paginas"),
            200,
            value -> value > 0,
            logger,
            "api.graphql.faturas.max_paginas",
            "200"
        );
    }

    public static int obterLimitePaginasUsuariosGraphQL() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_GRAPHQL_USUARIOS_MAX_PAGINAS", "api.graphql.usuarios.max_paginas"),
            5000,
            value -> value > 0,
            logger,
            "api.graphql.usuarios.max_paginas",
            "5000"
        );
    }

    public static int obterDiasJanelaFaturasGraphQL() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_GRAPHQL_FATURAS_DIAS_JANELA", "api.graphql.faturas.dias_janela"),
            2,
            value -> value > 0,
            logger,
            "api.graphql.faturas.dias_janela",
            "2"
        );
    }

    public static int obterLimitePaginasApiDataExport() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_DATAEXPORT_MAX_PAGINAS", "api.dataexport.max.paginas"),
            500,
            value -> value > 0,
            logger,
            "api.dataexport.max.paginas",
            "500"
        );
    }

    public static int obterLimitePaginasApiDataExportPorTemplate(final int templateId) {
        final String valor = ConfigSource.obterConfiguracao(
            "API_DATAEXPORT_MAX_PAGINAS_TEMPLATE_" + templateId,
            "api.dataexport.max.paginas.template." + templateId
        );
        return ConfigValueParser.parseInt(valor, obterLimitePaginasApiDataExport(), value -> value > 0, null, null, null);
    }

    public static int obterMaxRegistrosGraphQL() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_GRAPHQL_MAX_REGISTROS", "api.graphql.max.registros.execucao"),
            50000,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static int obterMaxRegistrosUsuariosGraphQL() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_GRAPHQL_USUARIOS_MAX_REGISTROS", "api.graphql.usuarios.max.registros.execucao"),
            100000,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static int obterMaxRegistrosDataExport() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_DATAEXPORT_MAX_REGISTROS", "api.dataexport.max.registros.execucao"),
            10000,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static int obterMaxRegistrosDataExportPorTemplate(final int templateId) {
        final String valor = ConfigSource.obterConfiguracao(
            "API_DATAEXPORT_MAX_REGISTROS_TEMPLATE_" + templateId,
            "api.dataexport.max.registros.template." + templateId
        );
        return ConfigValueParser.parseInt(valor, obterMaxRegistrosDataExport(), value -> value > 0, null, null, null);
    }

    public static boolean isParticionamentoJanelaDataExportAtivo() {
        final String valor = ConfigSource.obterConfiguracao(
            "API_DATAEXPORT_PARTICIONAR_JANELA_AUTOMATICA",
            "api.dataexport.partitionar.janela.automatica"
        );
        return valor == null || valor.isBlank() || Boolean.parseBoolean(valor.trim());
    }

    public static String obterMetodoHttpDataExportPreferencial() {
        final String valor = ConfigSource.obterConfiguracao("API_DATAEXPORT_HTTP_METHOD", "api.dataexport.http.method");
        if (valor == null || valor.isBlank()) {
            return "POST";
        }
        final String normalizado = valor.trim().toUpperCase();
        if ("POST".equals(normalizado) || "GET".equals(normalizado)) {
            return normalizado;
        }
        logger.warn("Metodo HTTP DataExport invalido '{}'. Usando POST.", valor);
        return "POST";
    }

    public static int obterMaxTentativasTimeoutApiDataExportPorPagina() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "API_DATAEXPORT_TIMEOUT_RETRY_MAX_TENTATIVAS_PAGINA",
                "api.dataexport.timeout.retry.max_tentativas_pagina"
            ),
            8,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static int obterMaxTentativasTimeoutApiDataExportPaginaUm() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "API_DATAEXPORT_TIMEOUT_RETRY_MAX_TENTATIVAS_PAGINA_1",
                "api.dataexport.timeout.retry.max_tentativas_pagina_1"
            ),
            3,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static long obterDelayBaseTimeoutApiDataExportPorPaginaMs() {
        return ConfigValueParser.parseLong(
            ConfigSource.obterConfiguracao(
                "API_DATAEXPORT_TIMEOUT_RETRY_DELAY_BASE_MS",
                "api.dataexport.timeout.retry.delay_base_ms"
            ),
            1500L,
            value -> value > 0L,
            null,
            null,
            null
        );
    }

    public static long obterDelayMaximoTimeoutApiDataExportPorPaginaMs() {
        return ConfigValueParser.parseLong(
            ConfigSource.obterConfiguracao(
                "API_DATAEXPORT_TIMEOUT_RETRY_DELAY_MAXIMO_MS",
                "api.dataexport.timeout.retry.delay_maximo_ms"
            ),
            45_000L,
            value -> value > 0L,
            null,
            null,
            null
        );
    }

    public static double obterJitterTimeoutApiDataExportPorPagina() {
        return ConfigValueParser.parseDouble(
            ConfigSource.obterConfiguracao(
                "API_DATAEXPORT_TIMEOUT_RETRY_JITTER",
                "api.dataexport.timeout.retry.jitter"
            ),
            0.35d,
            value -> value >= 0.0d,
            null,
            null,
            null
        );
    }

    public static ZoneId obterZoneIdDataExport() {
        final String valorSystemProperty = System.getProperty("api.dataexport.timezone");
        final String valorConfigurado = valorSystemProperty != null && !valorSystemProperty.isBlank()
            ? valorSystemProperty
            : ConfigSource.obterConfiguracao("API_DATAEXPORT_TIMEZONE", "api.dataexport.timezone");
        if (valorConfigurado == null || valorConfigurado.isBlank()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(valorConfigurado.trim());
        } catch (final RuntimeException ex) {
            logger.warn("Timezone DataExport invalido '{}'. Usando timezone do sistema '{}'.", valorConfigurado, ZoneId.systemDefault());
            return ZoneId.systemDefault();
        }
    }
}
