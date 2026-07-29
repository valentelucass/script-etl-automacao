package br.com.extrator.persistencia.repositorio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;

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
                coletaTerminal.setStatusUpdatedAtEm(OffsetDateTime.parse("2026-07-01T17:48:00-03:00"));
                coletaTerminal.setMetadata("{\"integrationTest\":true}");

                assertEquals(1, new TestableColetaRepository().merge(conexao, coletaTerminal));

                try (PreparedStatement statement = conexao.prepareStatement("""
                    SELECT status, finish_date, status_updated_at_em
                      FROM dbo.coletas
                     WHERE id = ?
                    """)) {
                    statement.setString(1, id);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        assertTrue(resultSet.next());
                        assertEquals("finished", resultSet.getString("status"));
                        assertEquals(LocalDate.of(2026, 7, 1), resultSet.getDate("finish_date").toLocalDate());
                        assertEquals(
                            OffsetDateTime.parse("2026-07-01T17:48:00-03:00").toInstant(),
                            resultSet.getObject("status_updated_at_em", OffsetDateTime.class).toInstant()
                        );
                    }
                }
            } finally {
                conexao.rollback();
            }
        }
    }

    @Test
    void deveAtualizarEstadoAbertoQuandoStatusDaOrigemForMaisNovo() throws SQLException {
        final String id = "it-coleta-aberta-" + System.nanoTime();
        final long sequenceCode = Math.abs(System.nanoTime() % 1_000_000_000L) + 7_000_000_000L;

        try (Connection conexao = DriverManager.getConnection(
            ConfigBanco.obterUrlBancoDados(),
            ConfigBanco.obterUsuarioBancoDados(),
            ConfigBanco.obterSenhaBancoDados()
        )) {
            conexao.setAutoCommit(false);
            try {
                inserirEstadoAbertoComTimestamp(conexao, id, sequenceCode, "manifested", "2026-07-27T17:47:00-03:00");

                final ColetaEntity coletaAtualizada = new ColetaEntity();
                coletaAtualizada.setId(id);
                coletaAtualizada.setSequenceCode(sequenceCode);
                coletaAtualizada.setRequestDate(LocalDate.of(2026, 6, 2));
                coletaAtualizada.setServiceDate(LocalDate.of(2026, 6, 3));
                coletaAtualizada.setStatus("in_transit");
                coletaAtualizada.setStatusUpdatedAt("2026-07-28T13:31:00-03:00");
                coletaAtualizada.setStatusUpdatedAtEm(OffsetDateTime.parse("2026-07-28T13:31:00-03:00"));
                coletaAtualizada.setMetadata("{\"integrationTest\":true}");

                assertEquals(1, new TestableColetaRepository().merge(conexao, coletaAtualizada));

                try (PreparedStatement statement = conexao.prepareStatement("SELECT status FROM dbo.coletas WHERE id = ?")) {
                    statement.setString(1, id);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        assertTrue(resultSet.next());
                        assertEquals("in_transit", resultSet.getString("status"));
                    }
                }
            } finally {
                conexao.rollback();
            }
        }
    }

    @Test
    void deveReativarColetaQueReapareceAposMarcacaoDeAusencia() throws SQLException {
        final String id = "it-coleta-reativada-" + System.nanoTime();
        final long sequenceCode = Math.abs(System.nanoTime() % 1_000_000_000L) + 6_000_000_000L;

        try (Connection conexao = DriverManager.getConnection(
            ConfigBanco.obterUrlBancoDados(),
            ConfigBanco.obterUsuarioBancoDados(),
            ConfigBanco.obterSenhaBancoDados()
        )) {
            conexao.setAutoCommit(false);
            try {
                try (PreparedStatement statement = conexao.prepareStatement("""
                    INSERT INTO dbo.coletas (
                        id, sequence_code, request_date, service_date, status,
                        status_updated_at, status_updated_at_em, metadata, data_extracao,
                        excluido_na_origem, data_exclusao_origem, ausente_na_origem_desde,
                        confirmacoes_ausencia_origem, motivo_exclusao_origem
                    ) VALUES (?, ?, '2026-06-17', '2026-06-18', N'in_transit',
                        N'2026-06-18T10:13:00-03:00', ?, N'{\"integrationTest\":true}', SYSUTCDATETIME(),
                        1, SYSUTCDATETIME(), SYSUTCDATETIME(), 2, N'AUSENTE_EM_SNAPSHOT_GRAPHQL_COMPLETO')
                    """)) {
                    statement.setString(1, id);
                    statement.setLong(2, sequenceCode);
                    statement.setObject(3, OffsetDateTime.parse("2026-06-18T10:13:00-03:00"));
                    statement.executeUpdate();
                }

                final ColetaEntity coletaRetornada = new ColetaEntity();
                coletaRetornada.setId(id);
                coletaRetornada.setSequenceCode(sequenceCode);
                coletaRetornada.setRequestDate(LocalDate.of(2026, 6, 17));
                coletaRetornada.setServiceDate(LocalDate.of(2026, 6, 18));
                coletaRetornada.setStatus("in_transit");
                coletaRetornada.setStatusUpdatedAt("2026-06-18T10:13:00-03:00");
                coletaRetornada.setStatusUpdatedAtEm(OffsetDateTime.parse("2026-06-18T10:13:00-03:00"));
                coletaRetornada.setMetadata("{\"integrationTest\":true}");

                assertEquals(1, new TestableColetaRepository().merge(conexao, coletaRetornada));

                try (PreparedStatement statement = conexao.prepareStatement("""
                    SELECT excluido_na_origem, ausente_na_origem_desde, confirmacoes_ausencia_origem
                      FROM dbo.coletas
                     WHERE id = ?
                    """)) {
                    statement.setString(1, id);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        assertTrue(resultSet.next());
                        assertEquals(false, resultSet.getBoolean("excluido_na_origem"));
                        assertEquals(null, resultSet.getObject("ausente_na_origem_desde"));
                        assertEquals(0, resultSet.getInt("confirmacoes_ausencia_origem"));
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

    private static void inserirEstadoAbertoComTimestamp(final Connection conexao,
                                                         final String id,
                                                         final long sequenceCode,
                                                         final String status,
                                                         final String statusUpdatedAt) throws SQLException {
        try (PreparedStatement statement = conexao.prepareStatement("""
            INSERT INTO dbo.coletas (
                id, sequence_code, request_date, service_date, status,
                status_updated_at, status_updated_at_em, metadata, data_extracao, excluido_na_origem
            ) VALUES (?, ?, '2026-06-02', '2026-06-03', ?, ?, ?, N'{\"integrationTest\":true}', SYSUTCDATETIME(), 0)
            """)) {
            statement.setString(1, id);
            statement.setLong(2, sequenceCode);
            statement.setString(3, status);
            statement.setString(4, statusUpdatedAt);
            statement.setObject(5, OffsetDateTime.parse(statusUpdatedAt));
            statement.executeUpdate();
        }
    }

    private static final class TestableColetaRepository extends ColetaRepository {
        private int merge(final Connection conexao, final ColetaEntity coleta) throws SQLException {
            return executarMerge(conexao, coleta);
        }
    }
}
