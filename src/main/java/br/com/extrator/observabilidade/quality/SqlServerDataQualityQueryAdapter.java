package br.com.extrator.observabilidade.quality;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/quality/SqlServerDataQualityQueryAdapter.java
Classe  : DataQualityQueryPort (class)
Pacote  : br.com.extrator.observabilidade.quality
Modulo  : Observabilidade - Quality
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.suporte.banco.GerenciadorConexao;

public final class SqlServerDataQualityQueryAdapter implements DataQualityQueryPort {
    private static final Logger logger = LoggerFactory.getLogger(SqlServerDataQualityQueryAdapter.class);

    @Override
    public long contarDuplicidadesChaveNatural(final String entidade, final LocalDate dataInicio, final LocalDate dataFim) {
        final String sql = """
            SELECT COALESCE(SUM(x.duplicated), 0)
            FROM (
                SELECT COUNT(*) - 1 AS duplicated
                FROM dbo.log_extracoes
                WHERE entidade = ?
                  AND CAST(timestamp_inicio AS DATE) >= ?
                  AND CAST(timestamp_fim AS DATE) <= ?
                GROUP BY entidade, timestamp_inicio, timestamp_fim, status_final
                HAVING COUNT(*) > 1
            ) x
            """;
        return queryLong(sql, ps -> {
            ps.setString(1, normalize(entidade));
            ps.setDate(2, java.sql.Date.valueOf(dataInicio));
            ps.setDate(3, java.sql.Date.valueOf(dataFim));
        });
    }

    @Override
    public long contarLinhasIncompletas(final String entidade, final LocalDate dataInicio, final LocalDate dataFim) {
        final String sql = """
            SELECT COUNT(*)
            FROM dbo.log_extracoes
            WHERE entidade = ?
              AND CAST(timestamp_inicio AS DATE) >= ?
              AND CAST(timestamp_fim AS DATE) <= ?
              AND status_final <> 'COMPLETO'
            """;
        return queryLong(sql, ps -> {
            ps.setString(1, normalize(entidade));
            ps.setDate(2, java.sql.Date.valueOf(dataInicio));
            ps.setDate(3, java.sql.Date.valueOf(dataFim));
        });
    }

    @Override
    public LocalDateTime buscarTimestampMaisRecente(final String entidade) {
        final String sql = """
            SELECT MAX(timestamp_fim)
            FROM dbo.log_extracoes
            WHERE entidade = ?
            """;
        try (Connection connection = GerenciadorConexao.obterConexao();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(entidade));
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    final java.sql.Timestamp ts = rs.getTimestamp(1);
                    return ts == null ? null : ts.toLocalDateTime();
                }
            }
        } catch (SQLException e) {
            logger.warn("Erro ao obter latest timestamp entidade={}: {}", entidade, e.getMessage());
        }
        return null;
    }

    @Override
    public long contarQuebrasReferenciais(final String entidade) {
        if (!"manifestos".equals(normalize(entidade))) {
            return 0L;
        }
        final String sql = """
            SELECT COUNT(*)
            FROM dbo.manifestos m
            LEFT JOIN dbo.coletas c ON c.sequence_code = m.pick_sequence_code
            WHERE m.pick_sequence_code IS NOT NULL
              AND c.sequence_code IS NULL
            """;
        return queryLong(sql, ps -> { });
    }

    @Override
    public String detectarVersaoSchema(final String entidade) {
        final String normalized = normalize(entidade);
        if ("manifestos".equals(normalized)) {
            final boolean composite = columnExists("manifestos", "identificador_unico");
            final boolean extraction = columnExists("manifestos", "data_extracao");
            return composite && extraction ? "v2" : "legacy";
        }
        if ("coletas".equals(normalized)) {
            final boolean metadata = columnExists("coletas", "metadata");
            final boolean extraction = columnExists("coletas", "data_extracao");
            return metadata && extraction ? "v2" : "legacy";
        }
        if ("usuarios_sistema".equals(normalized)) {
            // dim_usuarios é a tabela de referência introduzida no v2; presença de user_id + data_atualizacao confirma schema correto
            final boolean userId = columnExists("dim_usuarios", "user_id");
            final boolean dataAtualizacao = columnExists("dim_usuarios", "data_atualizacao");
            return userId && dataAtualizacao ? "v2" : "legacy";
        }
        final String nomeTabela = resolverNomeTabela(normalized);
        if (nomeTabela != null) {
            final boolean metadata = columnExists(nomeTabela, "metadata");
            final boolean extraction = columnExists(nomeTabela, "data_extracao");
            return metadata && extraction ? "v2" : "legacy";
        }
        return "v1";
    }

    private String resolverNomeTabela(final String entidade) {
        return switch (entidade) {
            case "fretes"              -> "fretes";
            case "cotacoes"            -> "cotacoes";
            case "localizacao_cargas"  -> "localizacao_cargas";
            case "contas_a_pagar"      -> "contas_a_pagar";
            case "faturas_por_cliente" -> "faturas_por_cliente";
            case "faturas_graphql"     -> "faturas_graphql";
            default                    -> null;
        };
    }

    private boolean columnExists(final String table, final String column) {
        final String sql = """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = 'dbo'
              AND TABLE_NAME = ?
              AND COLUMN_NAME = ?
            """;
        return queryLong(sql, ps -> {
            ps.setString(1, table);
            ps.setString(2, column);
        }) > 0L;
    }

    private long queryLong(final String sql, final SqlBinder binder) {
        try (Connection connection = GerenciadorConexao.obterConexao();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            logger.warn("Falha em query de data quality: {}", e.getMessage());
        }
        return 0L;
    }

    private String normalize(final String entidade) {
        return entidade == null ? "" : entidade.trim().toLowerCase(Locale.ROOT);
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }
}


