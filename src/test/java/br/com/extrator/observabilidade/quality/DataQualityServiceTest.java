package br.com.extrator.observabilidade.quality;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class DataQualityServiceTest {

    @Test
    void deveAprovarQuandoTodosOsChecksPassam() {
        final DataQualityService service = new DataQualityService(
            new StubQualityQueryPort(0, 0, LocalDateTime.now().minusMinutes(5), 0, "v2"),
            checks(),
            60,
            "v2",
            500
        );

        final DataQualityReport report = service.avaliar(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 2),
            List.of("coletas")
        );

        assertTrue(report.isAprovado());
        assertTrue(report.totalFalhas() == 0);
    }

    @Test
    void deveReprovarQuandoSchemaNaoCompativel() {
        final DataQualityService service = new DataQualityService(
            new StubQualityQueryPort(0, 0, LocalDateTime.now().minusMinutes(5), 0, "legacy"),
            checks(),
            60,
            "v2",
            500
        );

        final DataQualityReport report = service.avaliar(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 2),
            List.of("manifestos")
        );

        assertFalse(report.isAprovado());
        assertTrue(report.totalFalhas() > 0);
    }

    private List<DataQualityCheck> checks() {
        return List.of(
            new UniquenessCheck(),
            new CompletenessCheck(),
            new FreshnessCheck(),
            new ReferentialIntegrityCheck(),
            new SchemaValidationCheck()
        );
    }

    private record StubQualityQueryPort(
        long duplicated,
        long incompletos,
        LocalDateTime latest,
        long breaks,
        String schemaVersion
    ) implements DataQualityQueryPort {
        @Override
        public long contarDuplicidadesChaveNatural(final String entidade, final LocalDate dataInicio, final LocalDate dataFim) {
            return duplicated;
        }

        @Override
        public long contarLinhasIncompletas(final String entidade, final LocalDate dataInicio, final LocalDate dataFim) {
            return incompletos;
        }

        @Override
        public LocalDateTime buscarTimestampMaisRecente(final String entidade) {
            return latest;
        }

        @Override
        public long contarQuebrasReferenciais(final String entidade) {
            return breaks;
        }

        @Override
        public String detectarVersaoSchema(final String entidade) {
            return schemaVersion;
        }
    }
}

