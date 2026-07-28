package br.com.extrator.persistencia.repositorio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import br.com.extrator.persistencia.entidade.FreteEntity;
import br.com.extrator.suporte.configuracao.ConfigBanco;

class FreteTerminalStatusMergeTest {

    @Test
    void devePromoverStatusTerminalMesmoComCtePersistidoMaisNovoEOrigemSemCte() throws SQLException {
        final long id = Math.abs(System.nanoTime() % 1_000_000_000L) + 6_000_000_000L;
        final OffsetDateTime criadoEm = OffsetDateTime.of(2026, 6, 10, 17, 42, 7, 0, ZoneOffset.ofHours(-3));
        final OffsetDateTime ctePersistido = criadoEm.plusSeconds(6);

        try (Connection conexao = DriverManager.getConnection(
            ConfigBanco.obterUrlBancoDados(),
            ConfigBanco.obterUsuarioBancoDados(),
            ConfigBanco.obterSenhaBancoDados()
        )) {
            conexao.setAutoCommit(false);
            try {
                inserirFretePendenteComCte(conexao, id, criadoEm, ctePersistido);

                final TestableFreteRepository repository = new TestableFreteRepository();
                repository.prepararStaging(conexao);
                assertEquals(1, repository.mergeNoStaging(conexao, freteTerminal(id, criadoEm)));
                assertTrue(repository.promoverStaging(conexao) >= 1);

                try (PreparedStatement statement = conexao.prepareStatement("""
                    SELECT status, cte_created_at, cte_issued_at
                      FROM dbo.fretes
                     WHERE id = ?
                    """)) {
                    statement.setLong(1, id);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        assertTrue(resultSet.next());
                        assertEquals("done", resultSet.getString("status"));
                        assertEquals(ctePersistido.toInstant(), resultSet.getObject("cte_created_at", OffsetDateTime.class).toInstant());
                        assertEquals(ctePersistido.toInstant(), resultSet.getObject("cte_issued_at", OffsetDateTime.class).toInstant());
                    }
                }
            } finally {
                conexao.rollback();
            }
        }
    }

    private static void inserirFretePendenteComCte(final Connection conexao,
                                                   final long id,
                                                   final OffsetDateTime criadoEm,
                                                   final OffsetDateTime ctePersistido) throws SQLException {
        try (PreparedStatement statement = conexao.prepareStatement("""
            INSERT INTO dbo.fretes (
                id, servico_em, criado_em, status, cte_created_at, cte_issued_at,
                metadata, data_extracao, excluido_na_origem
            ) VALUES (?, ?, ?, N'pending', ?, ?, N'{"integrationTest":true}', SYSUTCDATETIME(), 0)
            """)) {
            statement.setLong(1, id);
            statement.setObject(2, criadoEm.minusMinutes(1));
            statement.setObject(3, criadoEm);
            statement.setObject(4, ctePersistido);
            statement.setObject(5, ctePersistido);
            statement.executeUpdate();
        }
    }

    private static FreteEntity freteTerminal(final long id, final OffsetDateTime criadoEm) {
        final FreteEntity frete = new FreteEntity();
        frete.setId(id);
        frete.setServicoEm(criadoEm.minusMinutes(1));
        frete.setCriadoEm(criadoEm);
        frete.setStatus("done");
        frete.setMetadata("{\"integrationTest\":true}");
        return frete;
    }

    private static final class TestableFreteRepository extends FreteRepository {
        private void prepararStaging(final Connection conexao) throws SQLException {
            prepararStagingPorExecucao(conexao);
        }

        private int mergeNoStaging(final Connection conexao, final FreteEntity frete) throws SQLException {
            return executarMergeNoDestinoDaExecucao(conexao, frete);
        }

        private int promoverStaging(final Connection conexao) throws SQLException {
            return promoverStagingPorExecucao(conexao);
        }
    }
}
