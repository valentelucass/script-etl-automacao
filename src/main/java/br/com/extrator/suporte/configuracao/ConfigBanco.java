package br.com.extrator.suporte.configuracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/suporte/configuracao/ConfigBanco.java
Classe  : ConfigBanco (class)
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

public final class ConfigBanco {
    private static final Logger logger = LoggerFactory.getLogger(ConfigBanco.class);
    private static final int POOL_MAX_SIZE_DEFAULT = 10;
    private static final int POOL_MIN_IDLE_DEFAULT = 2;
    private static final long POOL_IDLE_TIMEOUT_DEFAULT = 600_000L;
    private static final long POOL_CONNECTION_TIMEOUT_DEFAULT = 30_000L;
    private static final long POOL_MAX_LIFETIME_DEFAULT = 1_800_000L;
    private static final long POOL_INITIALIZATION_FAIL_TIMEOUT_DEFAULT = 30_000L;

    private ConfigBanco() {
    }

    public static String obterUrlBancoDados() {
        return ConfigSource.obterConfiguracaoObrigatoria("DB_URL");
    }

    public static String obterUsuarioBancoDados() {
        return ConfigSource.obterConfiguracaoObrigatoria("DB_USER");
    }

    public static String obterSenhaBancoDados() {
        return ConfigSource.obterConfiguracaoObrigatoria("DB_PASSWORD");
    }

    public static String obterNomeBancoDados() {
        return ConfigSource.obterConfiguracao("DB_NAME", "db.name");
    }

    public static int obterBatchSize() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("DB_BATCH_SIZE", "db.batch.size"),
            100,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static boolean isContinuarAposErro() {
        final String valorSystemProperty = System.getProperty("db.continue.on.error");
        final String valor = valorSystemProperty != null
            ? valorSystemProperty
            : ConfigSource.obterConfiguracao("DB_CONTINUE_ON_ERROR", "db.continue.on.error");
        return valor == null || valor.isEmpty() || Boolean.parseBoolean(valor);
    }

    public static boolean isModoCommitAtomico() {
        final String valorSystemProperty = System.getProperty("db.atomic.commit");
        final String valor = valorSystemProperty != null
            ? valorSystemProperty
            : ConfigSource.obterConfiguracao("DB_ATOMIC_COMMIT", "db.atomic.commit");
        return valor == null || valor.isBlank() || Boolean.parseBoolean(valor.trim());
    }

    public static int obterTimeoutValidacaoConexao() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("DB_VALIDATION_TIMEOUT", "db.validation.timeout"),
            5,
            value -> value > 0,
            logger,
            "db.validation.timeout",
            "5"
        );
    }

    public static int obterPoolMaximumSize() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("DB_POOL_MAX_SIZE", "db.pool.maximum_size"),
            POOL_MAX_SIZE_DEFAULT,
            value -> value > 0,
            logger,
            "db.pool.maximum_size",
            Integer.toString(POOL_MAX_SIZE_DEFAULT)
        );
    }

    public static int obterPoolMinimumIdle() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(new String[] { "DB_POOL_MIN_IDLE", "DB_POOL_MIN_SIZE" }, "db.pool.minimum_idle"),
            POOL_MIN_IDLE_DEFAULT,
            value -> value >= 0,
            logger,
            "db.pool.minimum_idle",
            Integer.toString(POOL_MIN_IDLE_DEFAULT)
        );
    }

    public static long obterPoolIdleTimeoutMs() {
        return ConfigValueParser.parseLong(
            ConfigSource.obterConfiguracao("DB_POOL_IDLE_TIMEOUT", "db.pool.idle_timeout_ms"),
            POOL_IDLE_TIMEOUT_DEFAULT,
            value -> value >= 0L,
            logger,
            "db.pool.idle_timeout_ms",
            Long.toString(POOL_IDLE_TIMEOUT_DEFAULT)
        );
    }

    public static long obterPoolConnectionTimeoutMs() {
        return ConfigValueParser.parseLong(
            ConfigSource.obterConfiguracao("DB_POOL_CONN_TIMEOUT", "db.pool.connection_timeout_ms"),
            POOL_CONNECTION_TIMEOUT_DEFAULT,
            value -> value > 0L,
            logger,
            "db.pool.connection_timeout_ms",
            Long.toString(POOL_CONNECTION_TIMEOUT_DEFAULT)
        );
    }

    public static long obterPoolMaxLifetimeMs() {
        return ConfigValueParser.parseLong(
            ConfigSource.obterConfiguracao("DB_POOL_MAX_LIFETIME", "db.pool.max_lifetime_ms"),
            POOL_MAX_LIFETIME_DEFAULT,
            value -> value > 0L,
            logger,
            "db.pool.max_lifetime_ms",
            Long.toString(POOL_MAX_LIFETIME_DEFAULT)
        );
    }

    public static long obterPoolInitializationFailTimeoutMs() {
        return ConfigValueParser.parseLong(
            ConfigSource.obterConfiguracao("DB_POOL_INIT_FAIL_TIMEOUT", "db.pool.initialization_fail_timeout_ms"),
            POOL_INITIALIZATION_FAIL_TIMEOUT_DEFAULT,
            value -> value > 0L,
            logger,
            "db.pool.initialization_fail_timeout_ms",
            Long.toString(POOL_INITIALIZATION_FAIL_TIMEOUT_DEFAULT)
        );
    }

    public static String obterPropriedade(final String chave) {
        return ConfigSource.obterPropriedade(chave);
    }

    public static void validarConexaoBancoDados() {
        ConfigBancoValidator.validarConexaoBancoDados();
    }

    public static void validarTabelasEssenciais() {
        ConfigBancoValidator.validarTabelasEssenciais();
    }
}
