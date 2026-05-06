package br.com.extrator.suporte.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConfigRasterTest {
    private static final String[] PROPS = {
        "RASTER_ENABLED",
        "RASTER_LOGIN",
        "RASTER_SENHA",
        "RASTER_PASSWORD",
        "RASTER_TIMEOUT_SECONDS",
        "RASTER_LOOKBACK_DAYS"
    };

    @AfterEach
    void limpar() {
        for (final String prop : PROPS) {
            System.clearProperty(prop);
        }
    }

    @Test
    void deveDesabilitarExplicitamenteMesmoComCredenciais() {
        System.setProperty("RASTER_ENABLED", "false");
        System.setProperty("RASTER_LOGIN", "usuario");
        System.setProperty("RASTER_SENHA", "senha");

        assertFalse(ConfigRaster.isHabilitadoParaExecucao());
    }

    @Test
    void deveHabilitarAutoQuandoCredenciaisExistirem() {
        System.setProperty("RASTER_ENABLED", "auto");
        System.setProperty("RASTER_LOGIN", "usuario");
        System.setProperty("RASTER_SENHA", "senha");

        assertTrue(ConfigRaster.isHabilitadoParaExecucao());
    }

    @Test
    void deveAceitarAliasRasterPassword() {
        System.setProperty("RASTER_PASSWORD", "senha-alias");

        assertEquals("senha-alias", ConfigRaster.obterSenha());
    }

    @Test
    void deveAplicarDefaultsNumericos() {
        System.setProperty("RASTER_TIMEOUT_SECONDS", "0");
        System.setProperty("RASTER_LOOKBACK_DAYS", "-1");

        assertEquals(120, ConfigRaster.obterTimeout().toSeconds());
        assertEquals(1, ConfigRaster.obterLookbackDays());
    }
}
