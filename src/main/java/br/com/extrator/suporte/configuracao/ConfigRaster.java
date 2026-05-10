package br.com.extrator.suporte.configuracao;

import java.time.Duration;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConfigRaster {
    public static final String ENABLED_AUTO = "auto";
    public static final String DEFAULT_BASE_URL =
        "https://integra.rastergr.com.br:8443/datasnap/rest/TWebService";

    private static final Logger logger = LoggerFactory.getLogger(ConfigRaster.class);

    private ConfigRaster() {
    }

    public static String obterEnabledRaw() {
        final String valor = ConfigSource.obterConfiguracao("RASTER_ENABLED", "raster.enabled");
        if (valor == null || valor.isBlank()) {
            return ENABLED_AUTO;
        }
        return valor.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isExplicitamenteHabilitado() {
        return "true".equals(obterEnabledRaw());
    }

    public static boolean isExplicitamenteDesabilitado() {
        return "false".equals(obterEnabledRaw());
    }

    public static boolean isAuto() {
        return ENABLED_AUTO.equals(obterEnabledRaw());
    }

    public static boolean possuiCredenciais() {
        return !isBlank(obterLogin()) && !isBlank(obterSenha());
    }

    public static boolean isHabilitadoParaExecucao() {
        if (isExplicitamenteDesabilitado()) {
            return false;
        }
        if (isExplicitamenteHabilitado()) {
            return true;
        }
        return possuiCredenciais();
    }

    public static String obterLogin() {
        return ConfigSource.obterConfiguracao("RASTER_LOGIN", "raster.login");
    }

    public static String obterSenha() {
        return ConfigSource.obterConfiguracao(new String[] {"RASTER_SENHA", "RASTER_PASSWORD"}, "raster.senha");
    }

    public static String obterLoginObrigatorio() {
        return ConfigSource.obterConfiguracaoObrigatoria("RASTER_LOGIN");
    }

    public static String obterSenhaObrigatoria() {
        return ConfigSource.obterConfiguracaoObrigatoria(new String[] {"RASTER_SENHA", "RASTER_PASSWORD"});
    }

    public static String obterAmbiente() {
        final String valor = ConfigSource.obterConfiguracao("RASTER_AMBIENTE", "raster.ambiente");
        return isBlank(valor) ? "Producao" : valor.trim();
    }

    public static String obterBaseUrl() {
        final String valor = ConfigSource.obterConfiguracao("RASTER_BASE_URL", "raster.base_url");
        return isBlank(valor) ? DEFAULT_BASE_URL : valor.trim();
    }

    public static String obterStatusViagem() {
        final String valor = ConfigSource.obterConfiguracao("RASTER_STATUS_VIAGEM", "raster.status_viagem");
        return isBlank(valor) ? "T" : valor.trim();
    }

    public static Duration obterTimeout() {
        final long segundos = ConfigValueParser.parseLong(
            ConfigSource.obterConfiguracao("RASTER_TIMEOUT_SECONDS", "raster.timeout.seconds"),
            120L,
            value -> value > 0L,
            logger,
            "raster.timeout.seconds",
            "120 segundos"
        );
        return Duration.ofSeconds(segundos);
    }

    public static Duration obterTimeoutStep() {
        final long segundos = ConfigValueParser.parseLong(
            ConfigSource.obterConfiguracao("RASTER_STEP_TIMEOUT_SECONDS", "raster.step_timeout.seconds"),
            900L,
            value -> value > 0L,
            logger,
            "raster.step_timeout.seconds",
            "900 segundos"
        );
        return Duration.ofSeconds(segundos);
    }

    public static int obterMaxDiasPorJanela() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("RASTER_MAX_DIAS_JANELA", "raster.max_dias_janela"),
            1,
            value -> value > 0,
            logger,
            "raster.max_dias_janela",
            "1"
        );
    }

    public static int obterLookbackDays() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("RASTER_LOOKBACK_DAYS", "raster.lookback.days"),
            1,
            value -> value >= 0,
            logger,
            "raster.lookback.days",
            "1"
        );
    }

    private static boolean isBlank(final String valor) {
        return valor == null || valor.trim().isEmpty();
    }
}
