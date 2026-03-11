/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/repository/ExecutionHistoryRepository.java
Classe  : ExecutionHistoryRepository (class)
Pacote  : br.com.extrator.persistencia.repositorio
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

package br.com.extrator.persistencia.repositorio;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLRecoverableException;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.suporte.ThreadUtil;
import br.com.extrator.suporte.banco.GerenciadorConexao;
import br.com.extrator.suporte.log.SensitiveDataSanitizer;

/**
 * Repository for sys_execution_history.
 */
public class ExecutionHistoryRepository {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionHistoryRepository.class);
    private static final int MAX_INSERT_ATTEMPTS = 3;
    private static final long BASE_RETRY_DELAY_MS = 300L;
    private static final int MAX_STATUS_SIZE = 20;
    private static final int MAX_ERROR_CATEGORY_SIZE = 50;
    private static final int MAX_ERROR_MESSAGE_SIZE = 500;
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
        SQLException ultimoErro = null;
        for (int tentativa = 1; tentativa <= MAX_INSERT_ATTEMPTS; tentativa++) {
            try {
                inserirHistoricoUmaTentativa(
                    inicio,
                    fim,
                    durationSeconds,
                    status,
                    totalRecords,
                    errorCategory,
                    errorMessage
                );
                return;
            } catch (final SQLException e) {
                ultimoErro = e;
                if (!isRetryable(e) || tentativa >= MAX_INSERT_ATTEMPTS) {
                    logger.error(
                        "Falha ao inserir sys_execution_history apos {} tentativa(s): {}",
                        tentativa,
                        sanitize(e.getMessage()),
                        e
                    );
                    throw new RuntimeException("Falha ao inserir sys_execution_history", e);
                }

                final long delayMs = calcularBackoff(tentativa);
                logger.warn(
                    "Falha transiente ao inserir sys_execution_history (tentativa {}/{}). Novo retry em {}ms. erro={}",
                    tentativa,
                    MAX_INSERT_ATTEMPTS,
                    delayMs,
                    sanitize(e.getMessage())
                );
                aguardarRetry(delayMs);
            }
        }

        throw new RuntimeException("Falha ao inserir sys_execution_history", ultimoErro);
    }

    private void inserirHistoricoUmaTentativa(final LocalDateTime inicio,
                                              final LocalDateTime fim,
                                              final int durationSeconds,
                                              final String status,
                                              final int totalRecords,
                                              final String errorCategory,
                                              final String errorMessage) throws SQLException {
        final String sql = """
            INSERT INTO dbo.sys_execution_history
            (start_time, end_time, duration_seconds, status, total_records, error_category, error_message)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = GerenciadorConexao.obterConexao()) {
            final boolean autoCommitOriginal = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try {
                garantirEstruturaHistorico(conn);

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setTimestamp(1, Timestamp.valueOf(inicio));
                    stmt.setTimestamp(2, Timestamp.valueOf(fim));
                    stmt.setInt(3, durationSeconds);
                    stmt.setString(4, limitar(status, MAX_STATUS_SIZE));
                    stmt.setInt(5, totalRecords);
                    stmt.setString(6, limitar(errorCategory, MAX_ERROR_CATEGORY_SIZE));
                    stmt.setString(7, limitar(errorMessage, MAX_ERROR_MESSAGE_SIZE));
                    stmt.executeUpdate();
                }

                conn.commit();
            } catch (final SQLException e) {
                rollbackSilencioso(conn);
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(autoCommitOriginal);
                } catch (final SQLException e) {
                    logger.warn("Falha ao restaurar auto-commit da conexao de historico: {}", sanitize(e.getMessage()));
                }
            }
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
            logger.warn("Falha ao calcular total de registros: {}", sanitize(e.getMessage()));
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
            logger.warn("Falha ao verificar existencia de dbo.log_extracoes: {}", sanitize(e.getMessage()));
        }
        return false;
    }

    private String limitar(final String valor, final int max) {
        if (valor == null) {
            return null;
        }
        String texto = sanitize(valor).trim();
        if (texto.length() > max) {
            texto = texto.substring(0, max);
        }
        return texto;
    }

    private boolean isRetryable(final SQLException e) {
        if (e instanceof SQLTransientException || e instanceof SQLRecoverableException) {
            return true;
        }
        final String mensagem = e.getMessage();
        if (mensagem == null) {
            return false;
        }
        final String lower = mensagem.toLowerCase();
        return lower.contains("timeout")
            || lower.contains("temporarily")
            || lower.contains("deadlock")
            || lower.contains("could not open connection");
    }

    private long calcularBackoff(final int tentativa) {
        final int fator = Math.max(1, tentativa);
        final long atrasoBase = BASE_RETRY_DELAY_MS * fator * fator;
        final long jitter = ThreadLocalRandom.current().nextLong(0L, BASE_RETRY_DELAY_MS + 1L);
        return atrasoBase + jitter;
    }

    private void aguardarRetry(final long delayMs) {
        try {
            ThreadUtil.aguardar(delayMs);
        } catch (final InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new HistoryPersistenceInterruptedException("Retry de historico interrompido.", ie);
        }
    }

    private void rollbackSilencioso(final Connection conn) {
        try {
            conn.rollback();
        } catch (final SQLException e) {
            logger.warn("Falha no rollback de sys_execution_history: {}", sanitize(e.getMessage()));
        }
    }

    private String sanitize(final String value) {
        return SensitiveDataSanitizer.sanitize(value);
    }
}
