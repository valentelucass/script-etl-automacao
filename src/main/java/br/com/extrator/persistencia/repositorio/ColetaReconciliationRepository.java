package br.com.extrator.persistencia.repositorio;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

import br.com.extrator.aplicacao.expurgo.ColetaMissingRegistration;
import br.com.extrator.aplicacao.expurgo.ColetaReconciliationStore;
import br.com.extrator.suporte.banco.GerenciadorConexao;

/** Consultas sargable e confirmação em duas leituras para ausências de Coletas. */
public final class ColetaReconciliationRepository implements ColetaReconciliationStore {
    private static final int LOCK_TIMEOUT_MS = 30_000;
    private static final String MOTIVO_AUSENCIA = "AUSENTE_EM_SNAPSHOT_GRAPHQL_COMPLETO";

    @Override
    public Set<String> buscarIdsAtivos(final LocalDate dataInicio, final LocalDate dataFim) throws SQLException {
        final String sql = """
            SELECT id
              FROM dbo.coletas
             WHERE excluido_na_origem = 0
               AND request_date >= ?
               AND request_date < DATEADD(day, 1, ?)
             ORDER BY id
            """;
        final Set<String> ids = new LinkedHashSet<>();
        try (Connection connection = GerenciadorConexao.obterConexao();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(dataInicio));
            statement.setDate(2, Date.valueOf(dataFim));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final String id = resultSet.getString("id");
                    if (id != null && !id.isBlank()) {
                        ids.add(id.trim());
                    }
                }
            }
        }
        return ids;
    }

    @Override
    public ColetaMissingRegistration registrarAusencias(final List<String> idsAusentes,
                                                         final String runId,
                                                         final int confirmacoesNecessarias,
                                                         final int batchSize) throws SQLException {
        if (idsAusentes == null || idsAusentes.isEmpty()) {
            return new ColetaMissingRegistration(0, 0);
        }
        final int tamanhoLote = Math.max(1, batchSize);
        int markedMissing = 0;
        int logicallyExcluded = 0;
        try (Connection connection = GerenciadorConexao.obterConexao()) {
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            connection.setAutoCommit(false);
            configurarLockTimeout(connection);
            for (int inicio = 0; inicio < idsAusentes.size(); inicio += tamanhoLote) {
                final int fim = Math.min(idsAusentes.size(), inicio + tamanhoLote);
                final List<String> batch = new ArrayList<>(idsAusentes.subList(inicio, fim));
                try {
                    final Map<String, Integer> confirmations = buscarConfirmacoesAtuais(connection, batch);
                    final int excludedInBatch = (int) confirmations.values().stream()
                        .filter(confirmacoes -> confirmacoes + 1 >= confirmacoesNecessarias)
                        .count();
                    markedMissing += atualizarAusencias(connection, batch, runId, confirmacoesNecessarias);
                    logicallyExcluded += excludedInBatch;
                    connection.commit();
                } catch (final SQLException e) {
                    connection.rollback();
                    throw e;
                }
            }
        }
        return new ColetaMissingRegistration(markedMissing, logicallyExcluded);
    }

    private Map<String, Integer> buscarConfirmacoesAtuais(final Connection connection,
                                                           final List<String> ids) throws SQLException {
        final String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        final String sql = """
            SELECT id, confirmacoes_ausencia_origem
              FROM dbo.coletas WITH (UPDLOCK, HOLDLOCK)
             WHERE excluido_na_origem = 0
               AND id IN (%s)
            """.formatted(placeholders);
        final Map<String, Integer> confirmations = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (final String id : ids) {
                statement.setString(index++, id);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    confirmations.put(resultSet.getString("id"), resultSet.getInt("confirmacoes_ausencia_origem"));
                }
            }
        }
        return confirmations;
    }

    private int atualizarAusencias(final Connection connection,
                                   final List<String> ids,
                                   final String runId,
                                   final int confirmacoesNecessarias) throws SQLException {
        final String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        final String sql = """
            UPDATE dbo.coletas
               SET ausente_na_origem_desde = COALESCE(ausente_na_origem_desde, SYSUTCDATETIME()),
                   confirmacoes_ausencia_origem = CASE
                       WHEN confirmacoes_ausencia_origem < ? THEN confirmacoes_ausencia_origem + 1
                       ELSE confirmacoes_ausencia_origem
                   END,
                   ultima_reconciliacao_origem_em = SYSUTCDATETIME(),
                   reconciliacao_origem_run_id = ?,
                   motivo_exclusao_origem = ?,
                   excluido_na_origem = CASE
                       WHEN confirmacoes_ausencia_origem + 1 >= ? THEN 1
                       ELSE 0
                   END,
                   data_exclusao_origem = CASE
                       WHEN confirmacoes_ausencia_origem + 1 >= ? THEN COALESCE(data_exclusao_origem, SYSUTCDATETIME())
                       ELSE NULL
                   END
             WHERE excluido_na_origem = 0
               AND id IN (%s)
            """.formatted(placeholders);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setInt(index++, confirmacoesNecessarias);
            statement.setString(index++, runId);
            statement.setString(index++, MOTIVO_AUSENCIA);
            statement.setInt(index++, confirmacoesNecessarias);
            statement.setInt(index++, confirmacoesNecessarias);
            for (final String id : ids) {
                statement.setString(index++, id);
            }
            return statement.executeUpdate();
        }
    }

    private void configurarLockTimeout(final Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET LOCK_TIMEOUT " + LOCK_TIMEOUT_MS);
        }
    }
}
