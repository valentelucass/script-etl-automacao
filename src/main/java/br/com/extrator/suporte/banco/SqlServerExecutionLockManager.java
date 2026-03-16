package br.com.extrator.suporte.banco;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.aplicacao.extracao.ExecutionLockManager;
import br.com.extrator.suporte.configuracao.ConfigEtl;

public class SqlServerExecutionLockManager implements ExecutionLockManager {
    private static final Logger logger = LoggerFactory.getLogger(SqlServerExecutionLockManager.class);
    private final int timeoutMs;

    public SqlServerExecutionLockManager() {
        this(ConfigEtl.obterTimeoutLockExecucaoMs());
    }

    SqlServerExecutionLockManager(final int timeoutMs) {
        this.timeoutMs = Math.max(0, timeoutMs);
    }

    @Override
    public AutoCloseable acquire(final String resourceName) throws SQLException {
        final Connection conexao = GerenciadorConexao.obterConexao();
        try {
            final int resultado = adquirirLock(conexao, resourceName);
            if (resultado < 0) {
                throw new SQLException("Nao foi possivel adquirir lock global de execucao '" + resourceName + "'. Codigo=" + resultado);
            }
            logger.info("Lock global de execucao adquirido: {}", resourceName);
            return () -> liberar(resourceName, conexao);
        } catch (final SQLException e) {
            try {
                conexao.close();
            } catch (final SQLException closeEx) {
                logger.warn("Falha ao fechar conexao apos erro de lock global: {}", closeEx.getMessage());
            }
            throw e;
        }
    }

    private int adquirirLock(final Connection conexao, final String resourceName) throws SQLException {
        final String sql = """
            DECLARE @resultado INT;
            EXEC @resultado = sp_getapplock
                @Resource = ?,
                @LockMode = 'Exclusive',
                @LockOwner = 'Session',
                @LockTimeout = ?;
            SELECT @resultado;
            """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, resourceName);
            ps.setInt(2, timeoutMs);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -999;
            }
        }
    }

    private void liberar(final String resourceName, final Connection conexao) throws SQLException {
        SQLException erroRelease = null;
        try (PreparedStatement ps = conexao.prepareStatement(
            "EXEC sp_releaseapplock @Resource = ?, @LockOwner = 'Session'"
        )) {
            ps.setString(1, resourceName);
            ps.execute();
            logger.info("Lock global de execucao liberado: {}", resourceName);
        } catch (final SQLException e) {
            erroRelease = e;
            logger.error("Falha ao liberar lock global de execucao {}: {}", resourceName, e.getMessage(), e);
        } finally {
            try {
                conexao.close();
            } catch (final SQLException closeEx) {
                if (erroRelease == null) {
                    throw closeEx;
                }
                erroRelease.addSuppressed(closeEx);
            }
        }
        if (erroRelease != null) {
            throw erroRelease;
        }
    }
}
