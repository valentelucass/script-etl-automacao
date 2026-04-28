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
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.plataforma.auditoria.dominio.ExecutionPlanContext;
import br.com.extrator.plataforma.auditoria.dominio.ExecutionWindowPlan;
import br.com.extrator.suporte.banco.GerenciadorConexao;
import br.com.extrator.suporte.observabilidade.ExecutionContext;

public final class SqlServerDataQualityQueryAdapter implements DataQualityQueryPort {
    private static final Logger logger = LoggerFactory.getLogger(SqlServerDataQualityQueryAdapter.class);
    private final ConnectionProvider connectionProvider;

    public SqlServerDataQualityQueryAdapter() {
        this(GerenciadorConexao::obterConexao);
    }

    SqlServerDataQualityQueryAdapter(final ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

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
        final String normalized = normalize(entidade);
        final Long incompletosExecucaoAtual = contarIncompletudeDaExecucaoAtual(normalized);
        if (incompletosExecucaoAtual != null) {
            return incompletosExecucaoAtual.longValue();
        }

        final ExecutionWindow window = resolverJanelaConsulta(normalized, dataInicio, dataFim);
        final MessagePatterns patterns = MessagePatterns.of(window.inicio(), window.fim());
        final String sql = """
            WITH latest_run AS (
                SELECT TOP 1 status_final
                FROM dbo.log_extracoes
                WHERE entidade = ?
                  AND (mensagem LIKE ? OR mensagem LIKE ?)
                ORDER BY timestamp_fim DESC, timestamp_inicio DESC
            )
            SELECT CASE
                WHEN NOT EXISTS (SELECT 1 FROM latest_run) THEN 1
                WHEN EXISTS (SELECT 1 FROM latest_run WHERE status_final <> 'COMPLETO') THEN 1
                ELSE 0
            END
            """;
        return queryLong(sql, ps -> {
            ps.setString(1, normalized);
            ps.setString(2, patterns.primary());
            ps.setString(3, patterns.secondary());
        });
    }

    @Override
    public LocalDateTime buscarTimestampMaisRecente(final String entidade) {
        final String sql = """
            SELECT MAX(timestamp_fim)
            FROM dbo.log_extracoes
            WHERE entidade = ?
            """;
        try (Connection connection = connectionProvider.get();
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

    static String resolverNomeTabela(final String entidade) {
        return switch (entidade) {
            case "fretes"              -> "fretes";
            case "cotacoes"            -> "cotacoes";
            case "localizacao_cargas"  -> "localizacao_cargas";
            case "contas_a_pagar"      -> "contas_a_pagar";
            case "faturas_por_cliente" -> "faturas_por_cliente";
            case "inventario"          -> "inventario";
            case "sinistros"           -> "sinistros";
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
        try (Connection connection = connectionProvider.get();
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

    private Long queryNullableLong(final String sql, final SqlBinder binder) {
        try (Connection connection = connectionProvider.get();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    final long value = rs.getLong(1);
                    return rs.wasNull() ? null : value;
                }
            }
        } catch (SQLException e) {
            logger.warn("Falha em query de data quality: {}", e.getMessage());
        }
        return null;
    }

    private Long contarIncompletudeDaExecucaoAtual(final String entidade) {
        final String executionUuid = ExecutionContext.currentExecutionId();
        if (executionUuid == null || executionUuid.isBlank() || "n/a".equalsIgnoreCase(executionUuid)) {
            return null;
        }

        final String sql = """
            IF OBJECT_ID(N'dbo.sys_execution_audit', N'U') IS NULL
            BEGIN
                SELECT CAST(NULL AS BIGINT);
            END
            ELSE
            BEGIN
                WITH current_run AS (
                    SELECT TOP 1 status_execucao, api_completa, api_total_unico, db_persistidos, invalid_count
                    FROM dbo.sys_execution_audit
                    WHERE execution_uuid = ?
                      AND entidade = ?
                    ORDER BY updated_at DESC, finished_at DESC, started_at DESC
                )
                SELECT CASE
                    WHEN NOT EXISTS (SELECT 1 FROM current_run) THEN CAST(NULL AS BIGINT)
                    WHEN EXISTS (
                        SELECT 1
                        FROM current_run
                        WHERE status_execucao <> 'COMPLETO'
                           OR api_completa = 0
                           OR db_persistidos < api_total_unico
                           OR invalid_count > 0
                    ) THEN CAST(1 AS BIGINT)
                    ELSE CAST(0 AS BIGINT)
                END
            END
            """;
        return queryNullableLong(sql, ps -> {
            ps.setString(1, executionUuid);
            ps.setString(2, entidade);
        });
    }

    private ExecutionWindow resolverJanelaConsulta(final String entidade, final LocalDate dataInicio, final LocalDate dataFim) {
        final ExecutionWindowPlan plano = ExecutionPlanContext.getPlano(entidade).orElse(null);
        if (plano != null) {
            return new ExecutionWindow(plano.consultaDataInicio(), plano.consultaDataFim());
        }

        final LocalDate inicio = dataInicio;
        final LocalDate fim = dataFim == null ? dataInicio : dataFim;
        return new ExecutionWindow(inicio, fim);
    }

    private String normalize(final String entidade) {
        return entidade == null ? "" : entidade.trim().toLowerCase(Locale.ROOT);
    }

    private record ExecutionWindow(LocalDate inicio, LocalDate fim) {
    }

    private record MessagePatterns(String primary, String secondary) {
        private static MessagePatterns of(final LocalDate dataInicio, final LocalDate dataFim) {
            if (dataInicio == null) {
                return new MessagePatterns("%", "%");
            }
            final LocalDate fim = dataFim == null ? dataInicio : dataFim;
            if (dataInicio.equals(fim)) {
                return new MessagePatterns(
                    "%Data: " + dataInicio + "%",
                    "%Per\u00edodo: " + dataInicio + " a " + fim + "%"
                );
            }
            return new MessagePatterns(
                "%" + dataInicio + " a " + fim + "%",
                "%" + dataInicio + "%" + fim + "%"
            );
        }
    }

    @FunctionalInterface
    private interface SqlBinder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    @FunctionalInterface
    interface ConnectionProvider {
        Connection get() throws SQLException;
    }
}
