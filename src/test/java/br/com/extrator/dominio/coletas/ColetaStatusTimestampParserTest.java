package br.com.extrator.dominio.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class ColetaStatusTimestampParserTest {

    @Test
    void devePreservarOffsetQuandoOrigemEnviaIso8601() {
        final OffsetDateTime value = ColetaStatusTimestampParser
            .parse("2026-06-12T06:58:00-03:00")
            .orElseThrow();

        assertEquals(OffsetDateTime.parse("2026-06-12T06:58:00-03:00"), value);
    }

    @Test
    void deveInterpretarDataLegadaDaEslNoFusoDeSaoPaulo() {
        final OffsetDateTime value = ColetaStatusTimestampParser
            .parse("11/06/2026 00:27:00")
            .orElseThrow();

        assertEquals(OffsetDateTime.parse("2026-06-11T00:27:00-03:00"), value);
    }

    @Test
    void deveRejeitarTextoQueNaoRepresentaInstante() {
        assertTrue(ColetaStatusTimestampParser.parse("sem-data").isEmpty());
    }
}
