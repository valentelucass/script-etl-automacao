package br.com.extrator.suporte.configuracao;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConfigBancoValidatorTest {

    @AfterEach
    void limparOverrides() {
        System.clearProperty("etl.integridade.modo");
        System.clearProperty("db.atomic.commit");
        System.clearProperty("etl.environment");
        System.clearProperty("DB_POOL_MIN_IDLE");
        System.clearProperty("DB_POOL_MIN_SIZE");
        System.clearProperty("DB_POOL_CONN_TIMEOUT");
        System.clearProperty("DB_POOL_INIT_FAIL_TIMEOUT");
        System.clearProperty("db.pool.minimum_idle");
    }

    @Test
    void deveBloquearCommitNaoAtomicoEmModoEstrito() throws Exception {
        System.setProperty("etl.integridade.modo", "STRICT_INTEGRITY");
        System.setProperty("db.atomic.commit", "false");

        final Method method = ConfigBancoValidator.class.getDeclaredMethod("validarConfiguracaoPersistenciaSegura");
        method.setAccessible(true);

        assertThrows(InvocationTargetException.class, () -> method.invoke(null));
    }

    @Test
    void deveAceitarConfiguracaoPadraoSegura() throws Exception {
        System.setProperty("etl.integridade.modo", "STRICT_INTEGRITY");
        System.setProperty("db.atomic.commit", "true");

        final Method method = ConfigBancoValidator.class.getDeclaredMethod("validarConfiguracaoPersistenciaSegura");
        method.setAccessible(true);

        assertDoesNotThrow(() -> method.invoke(null));
    }

    @Test
    void deveCentralizarAliasLegadoDoMinIdleNoConfigBanco() {
        System.setProperty("DB_POOL_MIN_SIZE", "4");

        assertEquals(4, ConfigBanco.obterPoolMinimumIdle());
    }

    @Test
    void devePriorizarNomeAtualDoMinIdleSobreAliasLegado() {
        System.setProperty("DB_POOL_MIN_IDLE", "5");
        System.setProperty("DB_POOL_MIN_SIZE", "4");

        assertEquals(5, ConfigBanco.obterPoolMinimumIdle());
    }

    @Test
    void deveUsarDefaultParaTimeoutDeConexaoInvalido() {
        System.setProperty("DB_POOL_CONN_TIMEOUT", "0");

        assertEquals(30_000L, ConfigBanco.obterPoolConnectionTimeoutMs());
    }

    @Test
    void devePermitirConfigurarTimeoutDeInicializacaoDoPool() {
        System.setProperty("DB_POOL_INIT_FAIL_TIMEOUT", "45000");

        assertEquals(45_000L, ConfigBanco.obterPoolInitializationFailTimeoutMs());
    }
}
