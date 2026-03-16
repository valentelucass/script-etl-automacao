package br.com.extrator.integracao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class DataExportTimeWindowSupportTest {

    @Test
    void deveRespeitarTimezoneConfiguradoNaJanelaDiaria() {
        final DataExportTimeWindowSupport support = new DataExportTimeWindowSupport(ZoneId.of("America/Sao_Paulo"));
        final LocalDate data = LocalDate.of(2026, 3, 9);

        final Instant inicio = support.inicioDoDia(data);
        final Instant fim = support.fimDoDia(data);

        assertEquals(data, support.toLocalDate(inicio));
        assertEquals(data, support.toLocalDate(fim));
        assertEquals("2026-03-09 - 2026-03-09", support.formatarRange(inicio, fim));
    }
}
