package br.com.extrator.dominio.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ColetaStatusPolicyTest {

    @Test
    void deveNormalizarAliasesEIdentificarEstadosTerminais() {
        assertEquals("in_transit", ColetaStatusPolicy.normalize(" In-Transit "));
        assertTrue(ColetaStatusPolicy.isTerminal("finished"));
        assertTrue(ColetaStatusPolicy.isTerminal("cancelled"));
        assertFalse(ColetaStatusPolicy.isTerminal("manifested"));
        assertEquals("Cancelada", ColetaStatusPolicy.displayName("cancelled").orElseThrow());
    }

    @Test
    void deveManterStatusDesconhecidoForaDoCatalogo() {
        assertFalse(ColetaStatusPolicy.isKnown("novo_status_esl"));
        assertTrue(ColetaStatusPolicy.displayName("novo_status_esl").isEmpty());
    }
}
