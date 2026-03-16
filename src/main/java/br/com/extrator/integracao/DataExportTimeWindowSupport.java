package br.com.extrator.integracao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import br.com.extrator.suporte.formatacao.FormatadorData;

final class DataExportTimeWindowSupport {
    private final ZoneId zoneId;

    DataExportTimeWindowSupport(final ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    static DataExportTimeWindowSupport createConfigured() {
        return new DataExportTimeWindowSupport(br.com.extrator.suporte.configuracao.ConfigApi.obterZoneIdDataExport());
    }

    Instant inicioDoDia(final LocalDate data) {
        return data.atStartOfDay(zoneId).toInstant();
    }

    Instant fimDoDia(final LocalDate data) {
        return data.plusDays(1).atStartOfDay(zoneId).minusSeconds(1).toInstant();
    }

    LocalDate toLocalDate(final Instant instant) {
        return instant.atZone(zoneId).toLocalDate();
    }

    String formatarRange(final Instant dataInicio, final Instant dataFim) {
        final String dataInicioStr = toLocalDate(dataInicio).format(FormatadorData.ISO_DATE);
        final String dataFimStr = toLocalDate(dataFim).format(FormatadorData.ISO_DATE);
        return dataInicioStr + " - " + dataFimStr;
    }

    ZoneId getZoneId() {
        return zoneId;
    }
}
