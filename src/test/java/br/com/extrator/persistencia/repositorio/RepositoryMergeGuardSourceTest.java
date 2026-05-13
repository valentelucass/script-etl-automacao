package br.com.extrator.persistencia.repositorio;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class RepositoryMergeGuardSourceTest {

    @Test
    void deveExigirGuardasMonotonicasNosMergesCriticos() throws IOException {
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/ColetaRepository.java",
            List.of("WITH (HOLDLOCK)", "WHEN MATCHED AND", "target.status_updated_at", "source.status_updated_at")
        );
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/FreteRepository.java",
            List.of("WITH (HOLDLOCK)", "WHEN MATCHED AND", "target.cte_created_at", "source.cte_created_at")
        );
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/ManifestoRepository.java",
            List.of("WITH (HOLDLOCK)", "WHEN MATCHED AND", "target.finished_at", "source.finished_at")
        );
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/CotacaoRepository.java",
            List.of("WITH (HOLDLOCK)", "WHEN MATCHED AND", "target.nfse_issued_at", "source.nfse_issued_at")
        );
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/LocalizacaoCargaRepository.java",
            List.of("WITH (HOLDLOCK)", "WHEN MATCHED AND", "target.predicted_delivery_at", "source.predicted_delivery_at")
        );
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/FaturaGraphQLRepository.java",
            List.of("WITH (HOLDLOCK)", "WHEN MATCHED AND", "target.updated_at", "source.updated_at")
        );
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/UsuarioSistemaRepository.java",
            List.of("WITH (HOLDLOCK)", "WHEN MATCHED AND", "T.data_atualizacao", "S.data_atualizacao")
        );
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/FaturaPorClienteRepository.java",
            List.of("WITH (HOLDLOCK)", "WHEN MATCHED AND %s THEN", "construirExpressaoFreshness(\"target\")", "construirExpressaoFreshness(\"source\")")
        );
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/ContasAPagarRepository.java",
            List.of("WITH (HOLDLOCK)", "WHEN MATCHED AND", "construirExpressaoFreshness(\"target\")", "construirExpressaoFreshness(\"source\")")
        );
    }

    @Test
    void deveHabilitarStagingNosRepositoriosCriticos() throws Exception {
        assertStagingHabilitado(new ManifestoRepository());
        assertStagingHabilitado(new CotacaoRepository());
        assertStagingHabilitado(new LocalizacaoCargaRepository());
        assertStagingHabilitado(new ContasAPagarRepository());
        assertStagingHabilitado(new FaturaPorClienteRepository());
        assertStagingHabilitado(new FreteRepository());
    }

    @Test
    void freteRepositoryNaoDeveDependerDeParameterMetadataParaStagingTemporario() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/br/com/extrator/persistencia/repositorio/FreteRepository.java"
        ));
        assertTrue(
            !source.contains("getParameterMetaData("),
            "FreteRepository nao deve consultar ParameterMetaData ao gravar em staging temporario."
        );
    }

    @Test
    void abstractRepositoryDeveRefrescarDataExtracaoEmNoOpsDeStaging() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/br/com/extrator/persistencia/repositorio/AbstractRepository.java"
        ));
        assertTrue(source.contains("SET data_extracao = source.data_extracao"));
        assertTrue(source.contains("WHERE NOT ("));
    }

    @Test
    void sinistroRepositoryDeveRefrescarDataExtracaoQuandoNoOpForAceito() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/br/com/extrator/persistencia/repositorio/SinistroRepository.java"
        ));
        assertTrue(source.contains("protected int refrescarDataExtracaoQuandoNoOp("));
        assertTrue(source.contains("UPDATE dbo.sinistros"));
        assertTrue(source.contains("WHERE identificador_unico = ?"));
        assertTrue(source.contains("data_extracao IS NULL OR data_extracao < ?"));
    }

    @Test
    void abstractRepositoryDeveGerarGreatestTimestampComValuesMaxParaEvitarExplosaoDeExpressao() throws IOException {
        final String source = Files.readString(Path.of(
            "src/main/java/br/com/extrator/persistencia/repositorio/AbstractRepository.java"
        ));
        assertTrue(source.contains("SELECT MAX(v.ts)"));
        assertTrue(source.contains("FROM (VALUES"));
    }

    @Test
    void repositoriosCustomizadosDeStagingDevemRefrescarDataExtracaoNosNoOps() throws IOException {
        assertRefreshNoOpStaging(
            "src/main/java/br/com/extrator/persistencia/repositorio/CotacaoRepository.java",
            "target.sequence_code = source.sequence_code"
        );
        assertRefreshNoOpStaging(
            "src/main/java/br/com/extrator/persistencia/repositorio/LocalizacaoCargaRepository.java",
            "target.sequence_number = source.sequence_number"
        );
        assertRefreshNoOpStaging(
            "src/main/java/br/com/extrator/persistencia/repositorio/ContasAPagarRepository.java",
            "target.sequence_code = source.sequence_code"
        );
        assertRefreshNoOpStaging(
            "src/main/java/br/com/extrator/persistencia/repositorio/FaturaPorClienteRepository.java",
            "target.unique_id = source.unique_id"
        );
    }

    @Test
    void repositoriosComCamposDateDevemNormalizarFreshnessParaFimDoDia() throws IOException {
        assertSqlFreshnessNormalizado(
            "src/main/java/br/com/extrator/persistencia/repositorio/ContasAPagarRepository.java",
            List.of(
                "buildGreatestTimestampExpression(",
                "castDateToEndOfDayExpr(alias + \".data_transacao\")",
                "castDateToEndOfDayExpr(alias + \".data_liquidacao\")",
                "castToDateTimeExpr(alias + \".data_criacao\")"
            )
        );
        assertSqlFreshnessNormalizado(
            "src/main/java/br/com/extrator/persistencia/repositorio/FaturaPorClienteRepository.java",
            List.of(
                "buildGreatestTimestampExpression(",
                "castDateToEndOfDayExpr(alias + \".data_baixa_fatura\")",
                "castDateToEndOfDayExpr(alias + \".data_vencimento_fatura\")",
                "castDateToEndOfDayExpr(alias + \".data_emissao_fatura\")",
                "castToDateTimeExpr(alias + \".data_emissao_cte\")",
                "castDateToEndOfDayExpr(alias + \".fit_ant_issue_date\")"
            )
        );
    }

    private void assertMergeGuard(final String filePath, final List<String> expectedTokens) throws IOException {
        final String source = Files.readString(Path.of(filePath));
        for (final String token : expectedTokens) {
            assertTrue(
                source.contains(token),
                () -> "Arquivo " + filePath + " deve conter token de guarda monotônica: " + token
            );
        }
    }

    private void assertStagingHabilitado(final AbstractRepository<?> repository) throws Exception {
        final Method method = repository.getClass().getDeclaredMethod("usarStagingPorExecucao");
        method.setAccessible(true);
        final Object resultado = method.invoke(repository);
        assertTrue(Boolean.TRUE.equals(resultado), () -> repository.getClass().getSimpleName() + " deve usar staging por execucao.");
    }

    private void assertRefreshNoOpStaging(final String filePath, final String condicaoMerge) throws IOException {
        final String source = Files.readString(Path.of(filePath));
        assertTrue(
            source.contains("refrescarDataExtracaoEmNoOpsDeStaging("),
            () -> "Arquivo " + filePath + " deve refrescar data_extracao na promocao do staging."
        );
        assertTrue(
            source.contains(condicaoMerge),
            () -> "Arquivo " + filePath + " deve usar a mesma condicao de merge no refresh do no-op."
        );
    }

    private void assertSqlFreshnessNormalizado(final String filePath, final List<String> expectedTokens) throws IOException {
        final String source = Files.readString(Path.of(filePath));
        for (final String token : expectedTokens) {
            assertTrue(
                source.contains(token),
                () -> "Arquivo " + filePath + " deve conter token de freshness normalizado: " + token
            );
        }
    }
}
