package br.com.extrator.dominio.coletas;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/** Normaliza o instante de alteração de status retornado pela ESL. */
public final class ColetaStatusTimestampParser {
    private static final ZoneId ESL_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final List<DateTimeFormatter> LOCAL_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/uuuu H:mm:ss")
    );

    private ColetaStatusTimestampParser() {
    }

    public static Optional<OffsetDateTime> parse(final String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        final String value = rawValue.trim();
        try {
            return Optional.of(OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        } catch (final DateTimeParseException ignored) {
            // A ESL também retorna datas locais sem offset em integrações legadas.
        }
        for (final DateTimeFormatter formatter : LOCAL_FORMATS) {
            try {
                return Optional.of(ZonedDateTime.of(
                    java.time.LocalDateTime.parse(value, formatter),
                    ESL_ZONE
                ).toOffsetDateTime());
            } catch (final DateTimeParseException ignored) {
                // Tenta o próximo formato suportado.
            }
        }
        return Optional.empty();
    }
}
