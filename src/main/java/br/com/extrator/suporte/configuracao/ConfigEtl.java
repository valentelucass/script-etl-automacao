package br.com.extrator.suporte.configuracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/suporte/configuracao/ConfigEtl.java
Classe  : ConfigEtl (class)
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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigEtl {
    private static final Logger logger = LoggerFactory.getLogger(ConfigEtl.class);

    private ConfigEtl() {
    }

    public static long obterDelayEntreExtracoes() {
        return ConfigValueParser.parseLong(
            ConfigSource.obterConfiguracao("EXTRACAO_DELAY_MS", "extracao.delay.ms"),
            2000L,
            value -> value > 0L,
            null,
            null,
            null
        );
    }

    public static int obterThreadsProcessamentoFaturas() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_ENRIQUECIMENTO_FATURAS_THREADS", "api.enriquecimento.faturas.threads"),
            5,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static int obterLimiteErrosConsecutivos() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_ENRIQUECIMENTO_ERROS_LIMITE", "api.enriquecimento.erros_consecutivos_limite"),
            10,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static double obterMultiplicadorDelayErros() {
        return ConfigValueParser.parseDouble(
            ConfigSource.obterConfiguracao("API_ENRIQUECIMENTO_DELAY_MULTIPLIER", "api.enriquecimento.delay_multiplier_erros"),
            2.0d,
            value -> value > 1.0d,
            null,
            null,
            null
        );
    }

    public static int obterIntervaloLogProgressoEnriquecimento() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_ENRIQUECIMENTO_INTERVALO_LOG", "api.enriquecimento.intervalo_log_progresso"),
            100,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static int obterHeartbeatSegundos() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_ENRIQUECIMENTO_HEARTBEAT", "api.enriquecimento.heartbeat_segundos"),
            10,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static int obterMaxInvalidosToleradosPorEntidade() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("ETL_INVALIDOS_QUANTIDADE_MAX", "etl.invalidos.quantidade.max"),
            500,
            value -> value >= 0,
            null,
            null,
            null
        );
    }

    public static double obterPercentualMaxInvalidosToleradosPorEntidade() {
        return ConfigValueParser.parseDouble(
            ConfigSource.obterConfiguracao("ETL_INVALIDOS_PERCENTUAL_MAX", "etl.invalidos.percentual.max"),
            2.5d,
            value -> value >= 0.0d,
            null,
            null,
            null
        );
    }

    public static CarregadorConfig.EtlIntegridadeModo obterModoIntegridadeEtl() {
        final String valor = ConfigSource.obterConfiguracao("ETL_INTEGRIDADE_MODO", "etl.integridade.modo");
        if (valor == null || valor.isBlank()) {
            return CarregadorConfig.EtlIntegridadeModo.STRICT_INTEGRITY;
        }
        try {
            return CarregadorConfig.EtlIntegridadeModo.valueOf(valor.trim().toUpperCase());
        } catch (final IllegalArgumentException ex) {
            logger.warn("Modo de integridade ETL invalido '{}'. Usando STRICT_INTEGRITY.", valor);
            return CarregadorConfig.EtlIntegridadeModo.STRICT_INTEGRITY;
        }
    }

    public static boolean isModoIntegridadeEstrito() {
        return CarregadorConfig.EtlIntegridadeModo.STRICT_INTEGRITY.equals(obterModoIntegridadeEtl());
    }

    public static int obterMaxOrfaosManifestosTolerados() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "ETL_REFERENCIAL_MANIFESTOS_ORFAOS_QUANTIDADE_MAX",
                "etl.referencial.manifestos.orfaos.quantidade.max"
            ),
            500,
            value -> value >= 0,
            null,
            null,
            null
        );
    }

    public static double obterPercentualMaxOrfaosManifestosTolerados() {
        return ConfigValueParser.parseDouble(
            ConfigSource.obterConfiguracao(
                "ETL_REFERENCIAL_MANIFESTOS_ORFAOS_PERCENTUAL_MAX",
                "etl.referencial.manifestos.orfaos.percentual.max"
            ),
            35.0d,
            value -> value >= 0.0d,
            null,
            null,
            null
        );
    }

    public static int obterEtlReferencialColetasBackfillDias() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "ETL_REFERENCIAL_COLETAS_BACKFILL_DIAS",
                "etl.referencial.coletas.backfill.dias"
            ),
            1,
            value -> value >= 0,
            null,
            null,
            null
        );
    }

    public static int obterEtlReferencialColetasBackfillBufferDias() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "ETL_REFERENCIAL_COLETAS_BACKFILL_BUFFER_DIAS",
                "etl.referencial.coletas.backfill.buffer_dias"
            ),
            7,
            value -> value >= 0,
            null,
            null,
            null
        );
    }

    public static int obterEtlReferencialColetasLookaheadDias() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "ETL_REFERENCIAL_COLETAS_LOOKAHEAD_DIAS",
                "etl.referencial.coletas.lookahead.dias"
            ),
            1,
            value -> value >= 0,
            null,
            null,
            null
        );
    }
}
