/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/repository/ExecutionHistoryRepository.java
Classe  : ExecutionHistoryRepository (class)
Pacote  : br.com.extrator.db.repository
Modulo  : Repositorio de dados
Papel   : Implementa responsabilidade de execution history repository.

Conecta com:
- GerenciadorConexao (util.banco)

Fluxo geral:
1) Monta comandos SQL e parametros.
2) Executa operacoes de persistencia/consulta no banco.
3) Converte resultado para entidades de dominio.

Estrutura interna:
Metodos principais:
- inserirHistorico(...7 args): inclui registros no destino configurado.
- calcularTotalRegistros(...2 args): realiza operacao relacionada a "calcular total registros".
- existeTabelaLogExtracoes(...1 args): realiza operacao relacionada a "existe tabela log extracoes".
- limitar(...2 args): realiza operacao relacionada a "limitar".
Atributos-chave:
- logger: logger da classe para diagnostico.
- estruturaGarantida: campo de estado para "estrutura garantida".
[DOC-FILE-END]============================================================== */

package br.com.extrator.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.util.banco.GerenciadorConexao;

/**
 * Repository for sys_execution_history.
 */
public class ExecutionHistoryRepository {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionHistoryRepository.class);
    private static final String SQL_CREATE_SYS_EXECUTION_HISTORY = """
        IF OBJECT_ID(N'dbo.sys_execution_history', N'U') IS NULL
        BEGIN
            CREATE TABLE dbo.sys_execution_history (
                id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
                start_time DATETIME2 NOT NULL,
                end_time DATETIME2 NOT NULL,
                duration_seconds INT NOT NULL,
                status VARCHAR(20) NOT NULL,
                total_records INT NOT NULL DEFAULT 0,
                error_category VARCHAR(50) NULL,
                error_message VARCHAR(500) NULL,
                created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
            );

            CREATE INDEX IX_sys_execution_history_start_time
                ON dbo.sys_execution_history (start_time DESC);
        END
        """;
    private static final String SQL_CHECK_LOG_EXTRACOES = """
        SELECT CASE WHEN OBJECT_ID(N'dbo.log_extracoes', N'U') IS NULL THEN 0 ELSE 1 END
        """;
    private static volatile boolean estruturaGarantida = false;

    /**
     * Inserts an execution history record.
     */
    public void inserirHistorico(final LocalDateTime inicio,
                                 final LocalDateTime fim,
                                 final int durationSeconds,
                                 final String status,
                                 final int totalRecords,
                                 final String errorCategory,
                                 final String errorMessage) {
        final String sql = """
            INSERT INTO dbo.sys_execution_history
            (start_time, end_time, duration_seconds, status, total_records, error_category, error_message)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = GerenciadorConexao.obterConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            garantirEstruturaHistorico(conn);

            stmt.setTimestamp(1, Timestamp.valueOf(inicio));
            stmt.setTimestamp(2, Timestamp.valueOf(fim));
            stmt.setInt(3, durationSeconds);
            stmt.setString(4, limitar(status, 20));
            stmt.setInt(5, totalRecords);
            stmt.setString(6, limitar(errorCategory, 50));
            stmt.setString(7, limitar(errorMessage, 500));

            stmt.executeUpdate();
        } catch (final SQLException e) {
            logger.error("Falha ao inserir sys_execution_history: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao inserir sys_execution_history", e);
        }
    }

    /**
     * Calculates the total records processed using log_extracoes within the execution window.
     */
    public int calcularTotalRegistros(final LocalDateTime inicio, final LocalDateTime fim) {
        final String sql = """
            SELECT COALESCE(SUM(registros_extraidos), 0) AS total
            FROM dbo.log_extracoes
            WHERE timestamp_fim >= ? AND timestamp_inicio <= ?
              AND (status_final = 'COMPLETO' OR status_final LIKE 'INCOMPLETO%')
            """;

        try (Connection conn = GerenciadorConexao.obterConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (!existeTabelaLogExtracoes(conn)) {
                logger.info("Tabela dbo.log_extracoes ainda nao existe. Total de registros do historico sera 0.");
                return 0;
            }

            stmt.setTimestamp(1, Timestamp.valueOf(inicio));
            stmt.setTimestamp(2, Timestamp.valueOf(fim));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            }
        } catch (final SQLException e) {
            logger.warn("Falha ao calcular total de registros: {}", e.getMessage());
        }

        return 0;
    }

    private void garantirEstruturaHistorico(final Connection conn) throws SQLException {
        if (estruturaGarantida) {
            return;
        }

        synchronized (ExecutionHistoryRepository.class) {
            if (estruturaGarantida) {
                return;
            }
            try (PreparedStatement stmt = conn.prepareStatement(SQL_CREATE_SYS_EXECUTION_HISTORY)) {
                stmt.execute();
                estruturaGarantida = true;
            }
        }
    }

    private boolean existeTabelaLogExtracoes(final Connection conn) {
        try (PreparedStatement stmt = conn.prepareStatement(SQL_CHECK_LOG_EXTRACOES);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1) == 1;
            }
        } catch (final SQLException e) {
            logger.warn("Falha ao verificar existencia de dbo.log_extracoes: {}", e.getMessage());
        }
        return false;
    }

    private String limitar(final String valor, final int max) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        if (texto.length() > max) {
            texto = texto.substring(0, max);
        }
        return texto;
    }
}
