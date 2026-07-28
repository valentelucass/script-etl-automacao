package br.com.extrator.persistencia.repositorio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import br.com.extrator.persistencia.entidade.ColetaEntity;
import br.com.extrator.suporte.configuracao.ConfigBanco;

class ColetaTerminalStatusMergeTest {

    @Test
    void deveAplicarStatusTerminalMesmoQuandoTimestampDaOrigemForRetroativo() throws SQLException {
        final String id = "it-coleta-terminal-" + System.nanoTime();
        final long sequenceCode = Math.abs(System.nanoTime() % 1_000_000_000L) + 8_000_000_000L;

        try (Connection conexao = DriverManager.getConnection(
            ConfigBanco.obterUrlBancoDados(),
            ConfigBanco.obterUsuarioBancoDados(),
            ConfigBanco.obterSenhaBancoDados()
        )) {
            conexao.setAutoCommit(false);
            try {
                inserirPendenteComTimestampPosterior(conexao, id, sequenceCode);

                final ColetaEntity coletaTerminal = new ColetaEntity();
                coletaTerminal.setId(id);
                coletaTerminal.setSequenceCode(sequenceCode);
                coletaTerminal.setRequestDate(LocalDate.of(2026, 6, 30));
                coletaTerminal.setServiceDate(LocalDate.of(2026, 7, 1));
                coletaTerminal.setStatus("finished");
                coletaTerminal.setFinishDate(LocalDate.of(2026, 7, 1));
                coletaTerminal.setStatusUpdatedAt("2026-07-01T17:48:00-03:00");
                coletaTerminal.setMetadata("{\"integrationTest\":true}");

                assertEquals(1, new TestableColetaRepository().merge(conexao, coletaTerminal));

                try (PreparedStatement statement = conexao.prepareStatement("""
                    SELECT status, finish_date
                      FROM dbo.coletas
                     WHERE id = ?
                    """)) {
                    statement.setString(1, id);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        assertTrue(resultSet.next());
                        assertEquals("finished", resultSet.getString("status"));
                        assertEquals(LocalDate.of(2026, 7, 1), resultSet.getDate("finish_date").toLocalDate());
                    }
                }
            } finally {
                conexao.rollback();
            }
        }
    }

    private static void inserirPendenteComTimestampPosterior(final Connection conexao,
                                                              final String id,
                                                              final long sequenceCode) throws SQLException {
        try (PreparedStatement statement = conexao.prepareStatement("""
            INSERT INTO dbo.coletas (
                id, sequence_code, request_date, service_date, status,
                status_updated_at, metadata, data_extracao, excluido_na_origem
            ) VALUES (?, ?, '2026-06-30', '2026-07-01', N'pending',
                N'2026-07-01T19:21:00-03:00', N'{"integrationTest":true}', SYSUTCDATETIME(), 0)
            """)) {
            statement.setString(1, id);
            statement.setLong(2, sequenceCode);
            statement.executeUpdate();
        }
    }

    private static final class TestableColetaRepository extends ColetaRepository {
        private int merge(final Connection conexao, final ColetaEntity coleta) throws SQLException {
            return executarMerge(conexao, coleta);
        }
    }
}
