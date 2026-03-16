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
