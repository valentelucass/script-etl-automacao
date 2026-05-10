package br.com.extrator.analises;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import br.com.extrator.suporte.configuracao.ConfigEtl;

class AuditoriaConfiabilidadeCodeInspectionTest {

    @Test
    void ddlDeColetasEndureceSequenceCodeComoChaveObrigatoriaEUnica() throws IOException {
        final String ddl = lerArquivo("database", "tabelas", "001_criar_tabela_coletas.sql");
        final String normalizado = normalizarEspacos(ddl);

        assertTrue(normalizado.contains("sequence_code BIGINT NOT NULL"));
        assertTrue(normalizado.contains("CONSTRAINT UQ_coletas_sequence_code UNIQUE (sequence_code)"));
    }

    @Test
    void migrationDeColetasValidaDadosERecriaDependenciasAntesDoHardening() throws IOException {
        final String migration = lerArquivo("database", "migrations", "010_harden_coletas_sequence_code.sql");
        final String normalizado = normalizarEspacos(migration);

        assertTrue(normalizado.contains("sequence_code IS NULL"));
        assertTrue(normalizado.contains("HAVING COUNT(*) > 1"));
        assertTrue(normalizado.contains("BEGIN TRANSACTION"));
        assertTrue(normalizado.contains("ROLLBACK TRANSACTION"));
        assertTrue(normalizado.contains("DROP INDEX IX_coletas_data_extracao ON dbo.coletas"));
        assertTrue(normalizado.contains("CREATE NONCLUSTERED INDEX IX_coletas_data_extracao"));
        assertTrue(normalizado.contains("DROP INDEX IX_coletas_service_date ON dbo.coletas"));
        assertTrue(normalizado.contains("CREATE NONCLUSTERED INDEX IX_coletas_service_date"));
        assertTrue(normalizado.contains("ALTER TABLE dbo.coletas ALTER COLUMN sequence_code BIGINT NOT NULL"));
        assertTrue(normalizado.contains("ADD CONSTRAINT UQ_coletas_sequence_code UNIQUE (sequence_code)"));
        assertTrue(normalizado.contains("FK_manifestos_pick_sequence_code_coletas"));
    }

    @Test
    void gateDeReplayPassaAFecharStatusFinalComOwnershipPorExecutionUuid() throws IOException {
        final String codigoFonte = lerArquivo(
            "src",
            "main",
            "java",
            "br",
            "com",
            "extrator",
            "plataforma",
            "extracao",
            "persistencia",
            "sqlserver",
            "SqlServerRecoveryReplayGate.java"
        );
        final String normalizado = normalizarEspacos(codigoFonte);

        assertTrue(normalizado.contains("WHERE idempotency_key = ? AND execution_uuid = ? AND status = 'STARTED'"));
    }

    @Test
    void compositionRootNasceComAbortPipelineComoPadraoDosStepsCore() throws IOException {
        final String codigoFonte = lerArquivo(
            "src",
            "main",
            "java",
            "br",
            "com",
            "extrator",
            "bootstrap",
            "pipeline",
            "PipelineCompositionRoot.java"
        );
        final String normalizado = normalizarEspacos(codigoFonte);

        assertTrue(normalizado.contains("etl.failure.default\", \"ABORT_PIPELINE"));
        assertTrue(normalizado.contains("etl.failure.graphql\", \"ABORT_PIPELINE"));
        assertTrue(normalizado.contains("etl.failure.dataexport\", \"ABORT_PIPELINE"));
        assertTrue(normalizado.contains("etl.failure.faturas_graphql\", \"ABORT_PIPELINE"));
        assertTrue(normalizado.contains("etl.failure.raster\", \"DEGRADE"));
    }

    @Test
    void configDeRuntimeMantemHardeningDosStepsCore() throws IOException {
        final String propriedades = lerArquivo("src", "main", "resources", "config.properties");
        final String normalizado = normalizarEspacos(propriedades);

        assertTrue(normalizado.contains("etl.pipeline.shutdown.timeout.ms=5000"));
        assertTrue(normalizado.contains("etl.pipeline.timeout.step.dataexport.ms=3600000"));
        assertTrue(normalizado.contains("etl.failure.graphql=ABORT_PIPELINE"));
        assertTrue(normalizado.contains("etl.failure.dataexport=ABORT_PIPELINE"));
        assertTrue(normalizado.contains("etl.failure.faturas_graphql=ABORT_PIPELINE"));
        assertTrue(normalizado.contains("etl.failure.raster=DEGRADE"));
        assertTrue(normalizado.contains("etl.failure.default=ABORT_PIPELINE"));
    }

    @Test
    void timeoutDeShutdownDoExecutorRecebeGracePadraoMaisRealista() {
        assertEquals(5_000L, ConfigEtl.obterTimeoutShutdownExecutorMs());
    }

    private String lerArquivo(final String... segmentos) throws IOException {
        return Files.readString(Path.of("", segmentos));
    }

    private String normalizarEspacos(final String texto) {
        return texto.replaceAll("\\s+", " ").trim();
    }
}
