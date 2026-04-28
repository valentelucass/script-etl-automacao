package br.com.extrator.plataforma.auditoria.persistencia.sqlserver;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.aplicacao.portas.ExecutionAuditPort;
import br.com.extrator.observabilidade.LogStoragePaths;
import br.com.extrator.observabilidade.ProjectPaths;
import br.com.extrator.plataforma.auditoria.dominio.ExecutionAuditRecord;
import br.com.extrator.suporte.banco.GerenciadorConexao;
import br.com.extrator.suporte.configuracao.ConfigEtl;
import br.com.extrator.suporte.mapeamento.MapperUtil;

public final class SqlServerExecutionAuditPortAdapter implements ExecutionAuditPort {
    private static final Logger logger = LoggerFactory.getLogger(SqlServerExecutionAuditPortAdapter.class);

    @Override
    public void registrarResultado(final ExecutionAuditRecord record) {
        final String sql = """
            MERGE dbo.sys_execution_audit AS T
            USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?))
                AS S (
                    execution_uuid, entidade, janela_consulta_inicio, janela_consulta_fim,
                    janela_confirmacao_inicio, janela_confirmacao_fim, status_execucao,
                    api_total_bruto, api_total_unico, db_upserts, db_persistidos, api_completa,
                    motivo_incompletude, paginas_processadas, noop_count, invalid_count,
                    started_at, finished_at, command_name, cycle_id, detalhe
                )
            ON T.execution_uuid = S.execution_uuid AND T.entidade = S.entidade
            WHEN MATCHED THEN
                UPDATE SET
                    janela_consulta_inicio = S.janela_consulta_inicio,
                    janela_consulta_fim = S.janela_consulta_fim,
                    janela_confirmacao_inicio = S.janela_confirmacao_inicio,
                    janela_confirmacao_fim = S.janela_confirmacao_fim,
                    status_execucao = S.status_execucao,
                    api_total_bruto = S.api_total_bruto,
                    api_total_unico = S.api_total_unico,
                    db_upserts = S.db_upserts,
                    db_persistidos = S.db_persistidos,
                    api_completa = S.api_completa,
                    motivo_incompletude = S.motivo_incompletude,
                    paginas_processadas = S.paginas_processadas,
                    noop_count = S.noop_count,
                    invalid_count = S.invalid_count,
                    started_at = S.started_at,
                    finished_at = S.finished_at,
                    command_name = S.command_name,
                    cycle_id = S.cycle_id,
                    detalhe = S.detalhe,
                    updated_at = SYSDATETIME()
            WHEN NOT MATCHED THEN
                INSERT (
                    execution_uuid, entidade, janela_consulta_inicio, janela_consulta_fim,
                    janela_confirmacao_inicio, janela_confirmacao_fim, status_execucao,
                    api_total_bruto, api_total_unico, db_upserts, db_persistidos, api_completa,
                    motivo_incompletude, paginas_processadas, noop_count, invalid_count,
                    started_at, finished_at, command_name, cycle_id, detalhe
                )
                VALUES (
                    S.execution_uuid, S.entidade, S.janela_consulta_inicio, S.janela_consulta_fim,
                    S.janela_confirmacao_inicio, S.janela_confirmacao_fim, S.status_execucao,
                    S.api_total_bruto, S.api_total_unico, S.db_upserts, S.db_persistidos, S.api_completa,
                    S.motivo_incompletude, S.paginas_processadas, S.noop_count, S.invalid_count,
                    S.started_at, S.finished_at, S.command_name, S.cycle_id, S.detalhe
                );
            """;

        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            if (!tabelaExiste(conexao, "sys_execution_audit")) {
                tratarFalhaEscrita(
                    "Tabela dbo.sys_execution_audit ausente; auditoria estruturada nao pode ser persistida.",
                    null,
                    criarPayloadFallback("execution_audit", record)
                );
                return;
            }
            try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
                int index = 1;
                stmt.setString(index++, record.executionUuid());
                stmt.setString(index++, record.entidade());
                stmt.setTimestamp(index++, timestamp(record.janelaConsultaInicio()));
                stmt.setTimestamp(index++, timestamp(record.janelaConsultaFim()));
                stmt.setTimestamp(index++, timestamp(record.janelaConfirmacaoInicio()));
                stmt.setTimestamp(index++, timestamp(record.janelaConfirmacaoFim()));
                stmt.setString(index++, record.statusExecucao());
                stmt.setInt(index++, record.apiTotalBruto());
                stmt.setInt(index++, record.apiTotalUnico());
                stmt.setInt(index++, record.dbUpserts());
                stmt.setInt(index++, record.dbPersistidos());
                stmt.setBoolean(index++, record.apiCompleta());
                stmt.setString(index++, record.motivoIncompletude());
                stmt.setInt(index++, record.paginasProcessadas());
                stmt.setInt(index++, record.noopCount());
                stmt.setInt(index++, record.invalidCount());
                stmt.setTimestamp(index++, timestamp(record.startedAt()));
                stmt.setTimestamp(index++, timestamp(record.finishedAt()));
                stmt.setString(index++, record.commandName());
                stmt.setString(index++, record.cycleId());
                stmt.setString(index, record.detalhe());
                stmt.executeUpdate();
            }
        } catch (final SQLException e) {
            tratarFalhaEscrita(
                "Falha ao gravar sys_execution_audit: " + e.getMessage(),
                e,
                criarPayloadFallback("execution_audit", record)
            );
        }
    }

    @Override
    public Optional<ExecutionAuditRecord> buscarResultado(final String executionUuid, final String entidade) {
        final String sql = """
            SELECT TOP 1 *
            FROM dbo.sys_execution_audit
            WHERE execution_uuid = ?
              AND entidade = ?
            ORDER BY finished_at DESC
            """;
        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            if (!tabelaExiste(conexao, "sys_execution_audit")) {
                return Optional.empty();
            }
            try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
                stmt.setString(1, executionUuid);
                stmt.setString(2, entidade);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRecord(rs));
                    }
                }
            }
        } catch (final SQLException e) {
            logger.warn("Falha ao buscar sys_execution_audit por execucao: {}", e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public List<ExecutionAuditRecord> listarResultados(final String executionUuid) {
        final List<ExecutionAuditRecord> resultados = new ArrayList<>();
        final String sql = """
            SELECT *
            FROM dbo.sys_execution_audit
            WHERE execution_uuid = ?
            ORDER BY entidade
            """;
        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            if (!tabelaExiste(conexao, "sys_execution_audit")) {
                return List.of();
            }
            try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
                stmt.setString(1, executionUuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        resultados.add(mapRecord(rs));
                    }
                }
            }
        } catch (final SQLException e) {
            logger.warn("Falha ao listar sys_execution_audit por execucao: {}", e.getMessage());
        }
        return List.copyOf(resultados);
    }

    @Override
    public Optional<LocalDateTime> buscarWatermarkConfirmado(final String entidade) {
        final String sql = """
            SELECT watermark_confirmado
            FROM dbo.sys_execution_watermark
            WHERE entidade = ?
            """;
        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            if (!tabelaExiste(conexao, "sys_execution_watermark")) {
                return Optional.empty();
            }
            try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
                stmt.setString(1, entidade);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        final Timestamp timestamp = rs.getTimestamp("watermark_confirmado");
                        return Optional.ofNullable(timestamp == null ? null : timestamp.toLocalDateTime());
                    }
                }
            }
        } catch (final SQLException e) {
            logger.warn("Falha ao buscar watermark confirmado de {}: {}", entidade, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public void atualizarWatermarkConfirmado(final String entidade, final LocalDateTime watermarkConfirmado) {
        final String sql = """
            MERGE dbo.sys_execution_watermark AS T
            USING (VALUES (?, ?)) AS S (entidade, watermark_confirmado)
            ON T.entidade = S.entidade
            WHEN MATCHED THEN
                UPDATE SET
                    watermark_confirmado = S.watermark_confirmado,
                    updated_at = SYSDATETIME()
            WHEN NOT MATCHED THEN
                INSERT (entidade, watermark_confirmado)
                VALUES (S.entidade, S.watermark_confirmado);
            """;
        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            if (!tabelaExiste(conexao, "sys_execution_watermark")) {
                tratarFalhaEscrita(
                    "Tabela dbo.sys_execution_watermark ausente; watermark confirmado de '" + entidade + "' nao pode ser atualizado.",
                    null,
                    criarPayloadFallback("execution_watermark", Map.of(
                        "entidade", entidade,
                        "watermark_confirmado", watermarkConfirmado
                    ))
                );
                return;
            }
            try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
                stmt.setString(1, entidade);
                stmt.setTimestamp(2, timestamp(watermarkConfirmado));
                stmt.executeUpdate();
            }
        } catch (final SQLException e) {
            tratarFalhaEscrita(
                "Falha ao atualizar watermark de " + entidade + ": " + e.getMessage(),
                e,
                criarPayloadFallback("execution_watermark", Map.of(
                    "entidade", entidade,
                    "watermark_confirmado", watermarkConfirmado
                ))
            );
        }
    }

    @Override
    public boolean isDisponivel() {
        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            return tabelaExiste(conexao, "sys_execution_audit")
                && tabelaExiste(conexao, "sys_execution_watermark");
        } catch (final SQLException e) {
            return false;
        }
    }

    private ExecutionAuditRecord mapRecord(final ResultSet rs) throws SQLException {
        return new ExecutionAuditRecord(
            rs.getString("execution_uuid"),
            rs.getString("entidade"),
            toLocalDateTime(rs.getTimestamp("janela_consulta_inicio")),
            toLocalDateTime(rs.getTimestamp("janela_consulta_fim")),
            toLocalDateTime(rs.getTimestamp("janela_confirmacao_inicio")),
            toLocalDateTime(rs.getTimestamp("janela_confirmacao_fim")),
            rs.getString("status_execucao"),
            rs.getInt("api_total_bruto"),
            rs.getInt("api_total_unico"),
            rs.getInt("db_upserts"),
            rs.getInt("db_persistidos"),
            rs.getBoolean("api_completa"),
            rs.getString("motivo_incompletude"),
            rs.getInt("paginas_processadas"),
            rs.getInt("noop_count"),
            rs.getInt("invalid_count"),
            toLocalDateTime(rs.getTimestamp("started_at")),
            toLocalDateTime(rs.getTimestamp("finished_at")),
            rs.getString("command_name"),
            rs.getString("cycle_id"),
            rs.getString("detalhe")
        );
    }

    private boolean tabelaExiste(final Connection conexao, final String nomeTabela) throws SQLException {
        final String sql = """
            SELECT CASE WHEN OBJECT_ID(?, 'U') IS NULL THEN 0 ELSE 1 END
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, "dbo." + nomeTabela);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 1;
            }
        }
    }

    private Timestamp timestamp(final LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private LocalDateTime toLocalDateTime(final Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    @SuppressWarnings("unused")
    private void tratarFalhaEscrita(final String mensagem) {
        tratarFalhaEscrita(mensagem, null, Map.of("tipo", "audit_write_failure"));
    }

    private void tratarFalhaEscrita(final String mensagem,
                                    final Exception causa,
                                    final Map<String, Object> payloadFallback) {
        registrarFallbackLocal(mensagem, causa, payloadFallback);
        if (ConfigEtl.isModoIntegridadeEstrito()) {
            throw causa == null ? new IllegalStateException(mensagem) : new IllegalStateException(mensagem, causa);
        }
        if (causa == null) {
            logger.warn(mensagem);
            return;
        }
        logger.warn(mensagem, causa);
    }

    private void registrarFallbackLocal(final String mensagem,
                                        final Exception causa,
                                        final Map<String, Object> payloadFallback) {
        final Map<String, Object> registro = new LinkedHashMap<>();
        registro.put("recorded_at", LocalDateTime.now().toString());
        registro.put("message", mensagem);
        if (causa != null) {
            registro.put("exception_type", causa.getClass().getName());
            registro.put("exception_message", causa.getMessage());
        }
        if (payloadFallback != null && !payloadFallback.isEmpty()) {
            registro.put("payload", payloadFallback);
        }

        final Path arquivo = resolverArquivoFallback();
        try {
            final Path parent = arquivo.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            final String linha = MapperUtil.sharedJson().writeValueAsString(registro) + System.lineSeparator();
            Files.writeString(
                arquivo,
                linha,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE
            );
        } catch (final IOException e) {
            logger.error("Falha ao registrar fallback local de auditoria em {}: {}", arquivo.toAbsolutePath(), e.getMessage(), e);
        }
    }

    private Map<String, Object> criarPayloadFallback(final String tipo, final Object detalhe) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tipo", tipo);
        payload.put("detalhe", detalhe);
        return payload;
    }

    private Path resolverArquivoFallback() {
        final String override = System.getProperty("etl.audit.fallback.file");
        if (override != null && !override.isBlank()) {
            return ProjectPaths.resolveFlexible(override);
        }
        return LogStoragePaths.RUNTIME_STATE_DIR
            .resolve("audit")
            .resolve("sys_execution_audit_fallback.jsonl")
            .toAbsolutePath()
            .normalize();
    }
}
