package br.com.extrator.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MainBootstrapRoutingTest {

    @Test
    void comandosLevesNaoDevemInicializarContexto() {
        assertFalse(Main.requerInicializacaoContexto("--auth-check"));
        assertFalse(Main.requerInicializacaoContexto("--loop-daemon-start"));
        assertFalse(Main.requerInicializacaoContexto("--validar"));
        assertFalse(Main.requerInicializacaoContexto("--exportar-csv"));
    }

    @Test
    void comandosDeExecucaoDevemInicializarContexto() {
        assertTrue(Main.requerInicializacaoContexto("--fluxo-completo"));
        assertTrue(Main.requerInicializacaoContexto("--recovery"));
        assertTrue(Main.requerInicializacaoContexto("--loop-daemon-run"));
        assertTrue(Main.requerInicializacaoContexto("--validar-api-banco-24h-detalhado"));
        assertTrue(Main.requerInicializacaoContexto("--validar-etl-extremo"));
    }
}
