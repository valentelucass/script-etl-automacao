package br.com.extrator.persistencia.repositorio;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class RepositoryMergeGuardSourceTest {

    @Test
    void deveExigirGuardasMonotonicasNosMergesCriticos() throws IOException {
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/ColetaRepository.java",
            List.of("WHEN MATCHED AND", "target.status_updated_at", "source.status_updated_at")
        );
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/FreteRepository.java",
            List.of("WHEN MATCHED AND", "target.cte_created_at", "source.cte_created_at")
        );
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/ManifestoRepository.java",
            List.of("WHEN MATCHED AND", "target.finished_at", "source.finished_at")
        );
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/CotacaoRepository.java",
            List.of("WHEN MATCHED AND", "target.nfse_issued_at", "source.nfse_issued_at")
        );
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/LocalizacaoCargaRepository.java",
            List.of("WHEN MATCHED AND", "target.predicted_delivery_at", "source.predicted_delivery_at")
        );
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/FaturaGraphQLRepository.java",
            List.of("WHEN MATCHED AND", "target.updated_at", "source.updated_at")
        );
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/UsuarioSistemaRepository.java",
            List.of("WHEN MATCHED AND", "T.data_atualizacao", "S.data_atualizacao")
        );
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/FaturaPorClienteRepository.java",
            List.of("WHEN MATCHED AND %s THEN", "target.data_baixa_fatura", "source.data_baixa_fatura")
        );
        assertMergeGuard(
            "src/main/java/br/com/extrator/persistencia/repositorio/ContasAPagarRepository.java",
            List.of("WHEN MATCHED AND", "target.data_transacao", "source.data_transacao")
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
}
