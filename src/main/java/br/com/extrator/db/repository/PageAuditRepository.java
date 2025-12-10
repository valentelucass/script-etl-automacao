package br.com.extrator.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

import br.com.extrator.db.entity.PageAuditEntity;
import br.com.extrator.util.GerenciadorConexao;

public class PageAuditRepository {
    private static final String SQL =
        """
        INSERT INTO dbo.page_audit(\
        execution_uuid, run_uuid, template_id, page, per, janela_inicio, janela_fim, \
        req_hash, resp_hash, total_itens, id_key, id_min_num, id_max_num, \
        id_min_str, id_max_str, status_code, duracao_ms) \
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    public void inserir(final PageAuditEntity a) {
        try (Connection conn = GerenciadorConexao.obterConexao();
             PreparedStatement stmt = conn.prepareStatement(SQL)) {
            stmt.setString(1, a.getExecutionUuid());
            stmt.setString(2, a.getRunUuid());
            stmt.setInt(3, a.getTemplateId());
            stmt.setInt(4, a.getPage());
            stmt.setInt(5, a.getPer());
            stmt.setObject(6, a.getJanelaInicio());
            stmt.setObject(7, a.getJanelaFim());
            stmt.setString(8, a.getReqHash());
            stmt.setString(9, a.getRespHash());
            stmt.setInt(10, a.getTotalItens());
            stmt.setString(11, a.getIdKey());
            stmt.setObject(12, a.getIdMinNum());
            stmt.setObject(13, a.getIdMaxNum());
            stmt.setString(14, a.getIdMinStr());
            stmt.setString(15, a.getIdMaxStr());
            stmt.setInt(16, a.getStatusCode());
            stmt.setInt(17, a.getDuracaoMs());
            stmt.executeUpdate();
        } catch (final java.sql.SQLException e) {
            throw new RuntimeException("Falha ao gravar page_audit", e);
        }
    }

    public void criarTabelaSeNaoExistir() {
        final String ddl = """
            CREATE TABLE dbo.page_audit (
                id BIGINT IDENTITY PRIMARY KEY,
                execution_uuid NVARCHAR(36) NOT NULL,
                run_uuid NVARCHAR(36) NOT NULL,
                template_id INT NOT NULL,
                page INT NOT NULL,
                per INT NOT NULL,
                janela_inicio DATE NULL,
                janela_fim DATE NULL,
                req_hash CHAR(64) NOT NULL,
                resp_hash CHAR(64) NOT NULL,
                total_itens INT NOT NULL,

                id_key NVARCHAR(50) NULL,
                id_min_num BIGINT NULL,
                id_max_num BIGINT NULL,
                id_min_str NVARCHAR(80) NULL,
                id_max_str NVARCHAR(80) NULL,

                status_code INT NOT NULL,
                duracao_ms INT NOT NULL,

                timestamp DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
            );

            CREATE UNIQUE INDEX ux_page_audit_run_template_page
                ON dbo.page_audit (run_uuid, template_id, page);

            CREATE INDEX ix_page_audit_exec_timestamp
                ON dbo.page_audit (execution_uuid, timestamp DESC);

            ALTER TABLE dbo.page_audit ADD CONSTRAINT ck_page_audit_status
                CHECK (status_code BETWEEN 100 AND 599);

            ALTER TABLE dbo.page_audit ADD CONSTRAINT ck_page_audit_hash_len
                CHECK (LEN(req_hash) = 64 AND LEN(resp_hash) = 64);

            ALTER TABLE dbo.page_audit ADD CONSTRAINT ck_page_audit_bounds
                CHECK (page >= 1 AND per >= 1 AND total_itens >= 0);

            ALTER TABLE dbo.page_audit ADD CONSTRAINT ck_page_audit_id_range
                CHECK ((id_min_num IS NULL OR id_max_num IS NULL) OR (id_min_num <= id_max_num));
            """;
        try (Connection conn = GerenciadorConexao.obterConexao();
             Statement stmt = conn.createStatement()) {
            final String[] parts = ddl.split(";\\s*\\n");
            for (final String sql : parts) {
                final String trimmed = sql.trim();
                if (trimmed.isEmpty()) continue;
                try {
                    stmt.executeUpdate(trimmed);
                } catch (final java.sql.SQLException e) {
                    final String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    if (msg.contains("already exists") || msg.contains("there is already an object named") || msg.contains("cannot create index") || msg.contains("already an object named")) {
                        continue;
                    }
                    throw e;
                }
            }
        } catch (final java.sql.SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("already exists")) return;
            throw new RuntimeException("Falha ao criar tabela page_audit", e);
        }
    }
}
