package br.com.extrator.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.LogExtracaoEntity;
import br.com.extrator.db.entity.LogExtracaoEntity.StatusExtracao;
import br.com.extrator.util.GerenciadorConexao;

/**
 * Repository para gerenciar logs de extração
 */
public class LogExtracaoRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(LogExtracaoRepository.class);
    
    /**
     * Grava um novo log de extração
     */
    public void gravarLogExtracao(final LogExtracaoEntity logExtracao) {
        final String sql = """
            INSERT INTO dbo.log_extracoes
            (entidade, timestamp_inicio, timestamp_fim, status_final, registros_extraidos, paginas_processadas, mensagem)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = GerenciadorConexao.obterConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, logExtracao.getEntidade());
            stmt.setTimestamp(2, Timestamp.valueOf(logExtracao.getTimestampInicio()));
            stmt.setTimestamp(3, Timestamp.valueOf(logExtracao.getTimestampFim()));
            stmt.setString(4, logExtracao.getStatusFinal().getValor());
            stmt.setInt(5, logExtracao.getRegistrosExtraidos());
            stmt.setInt(6, logExtracao.getPaginasProcessadas());
            stmt.setString(7, logExtracao.getMensagem());
            
            final int linhasAfetadas = stmt.executeUpdate();
            
            if (linhasAfetadas > 0) {
                logger.debug("Log de extração gravado: entidade={}, status={}, registros={}", 
                    logExtracao.getEntidade(), logExtracao.getStatusFinal(), logExtracao.getRegistrosExtraidos());
            }
            
        } catch (final SQLException e) {
            logger.error("Erro ao gravar log de extração para entidade {}: {}", 
                logExtracao.getEntidade(), e.getMessage(), e);
            throw new RuntimeException("Falha ao gravar log de extração", e);
        }
    }
    
    /**
     * Busca o último log de extração para uma entidade
     */
    public Optional<LogExtracaoEntity> buscarUltimoLogPorEntidade(final String entidade) {
        final String sql = """
            SELECT TOP 1 id, entidade, timestamp_inicio, timestamp_fim, status_final,
                   registros_extraidos, paginas_processadas, mensagem
            FROM dbo.log_extracoes
            WHERE entidade = ?
            ORDER BY timestamp_fim DESC
            """;
        
        try (Connection conn = GerenciadorConexao.obterConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, entidade);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final LogExtracaoEntity log = new LogExtracaoEntity();
                    log.setId(rs.getLong("id"));
                    log.setEntidade(rs.getString("entidade"));
                    log.setTimestampInicio(rs.getTimestamp("timestamp_inicio").toLocalDateTime());
                    log.setTimestampFim(rs.getTimestamp("timestamp_fim").toLocalDateTime());
                    log.setStatusFinal(StatusExtracao.fromString(rs.getString("status_final")));
                    log.setRegistrosExtraidos(rs.getInt("registros_extraidos"));
                    log.setPaginasProcessadas(rs.getInt("paginas_processadas"));
                    log.setMensagem(rs.getString("mensagem"));
                    
                    return Optional.of(log);
                }
            }
            
        } catch (final SQLException e) {
            logger.error("Erro ao buscar último log para entidade {}: {}", entidade, e.getMessage(), e);
        }
        
        return Optional.empty();
    }
    
    /**
     * Verifica se a tabela log_extracoes existe
     */
    public boolean tabelaExiste() {
        final String sql = """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'log_extracoes' AND TABLE_SCHEMA = 'dbo'
            """;
        
        try (Connection conn = GerenciadorConexao.obterConexao();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            
        } catch (final SQLException e) {
            logger.error("Erro ao verificar existência da tabela log_extracoes: {}", e.getMessage(), e);
        }
        
        return false;
    }
    
    /**
     * Cria a tabela log_extracoes se não existir
     */
    public void criarTabelaSeNaoExistir() {
        try (Connection conn = GerenciadorConexao.obterConexao()) {
            final String ddl =
                """
                BEGIN TRY
                    IF OBJECT_ID('dbo.log_extracoes', 'U') IS NULL
                    BEGIN
                        CREATE TABLE dbo.log_extracoes (
                            id BIGINT IDENTITY PRIMARY KEY,
                            entidade NVARCHAR(50) NOT NULL,
                            timestamp_inicio DATETIME2 NOT NULL,
                            timestamp_fim DATETIME2 NOT NULL,
                            status_final NVARCHAR(20) NOT NULL,
                            registros_extraidos INT NOT NULL,
                            paginas_processadas INT NOT NULL,
                            mensagem NVARCHAR(MAX)
                        );
                    END
                END TRY
                BEGIN CATCH
                    -- Ignora erro de objeto já existente
                    IF ERROR_MESSAGE() NOT LIKE '%already exists%'
                        THROW;
                END CATCH;
                
                BEGIN TRY
                    IF NOT EXISTS (
                        SELECT 1 FROM sys.indexes
                        WHERE name = 'idx_entidade_timestamp'
                          AND object_id = OBJECT_ID('dbo.log_extracoes')
                    )
                    BEGIN
                        CREATE INDEX idx_entidade_timestamp ON dbo.log_extracoes (entidade, timestamp_fim DESC);
                    END
                END TRY
                BEGIN CATCH
                    -- Ignora erro de índice já existente, repropaga demais
                    IF ERROR_MESSAGE() NOT LIKE '%already exists%'
                        THROW;
                END CATCH;""";

            try (PreparedStatement stmt = conn.prepareStatement(ddl)) {
                stmt.executeUpdate();
            }
            logger.info("✅ Tabela dbo.log_extracoes verificada/criada com sucesso");
        } catch (final SQLException e) {
            logger.error("❌ Erro ao verificar/criar tabela dbo.log_extracoes: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao verificar/criar tabela dbo.log_extracoes", e);
        }
    }

    public void criarOuAtualizarViewDimFiliais() {
        try (Connection conn = br.com.extrator.util.GerenciadorConexao.obterConexao()) {
            final String[] fontes = new String[]{
                "vw_fretes_powerbi",
                "vw_manifestos_powerbi",
                "vw_contas_a_pagar_powerbi",
                "vw_faturas_por_cliente_powerbi"
            };
            final java.util.List<String> existentes = new java.util.ArrayList<>();
            for (final String v : fontes) {
                try (PreparedStatement s = conn.prepareStatement(
                    "SELECT 1 FROM sys.views WHERE name = ? AND schema_id = SCHEMA_ID('dbo')")) {
                    s.setString(1, v);
                    try (ResultSet rs = s.executeQuery()) {
                        if (rs.next()) existentes.add(v);
                    }
                }
            }
            final StringBuilder sb = new StringBuilder();
            if (existentes.isEmpty()) {
                sb.append("SELECT TOP 0 CAST(NULL AS NVARCHAR(255)) AS [NomeFilial] FROM sys.objects");
            } else {
                boolean first = true;
                for (final String v : existentes) {
                    if (!first) sb.append(" UNION ");
                    sb.append("SELECT DISTINCT [Filial] AS [NomeFilial] FROM dbo.").append(v);
                    first = false;
                }
            }
            final String ddl = "CREATE OR ALTER VIEW dbo.vw_dim_filiais AS " + sb.toString();
            try (PreparedStatement stmt = conn.prepareStatement(ddl)) {
                stmt.executeUpdate();
            }
            logger.info("✅ View dbo.vw_dim_filiais criada/atualizada com {} fonte(s)", existentes.size());
        } catch (final SQLException e) {
            logger.error("❌ Erro ao criar/atualizar view dbo.vw_dim_filiais: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao criar/atualizar view dbo.vw_dim_filiais", e);
        }
    }

    public void criarOuAtualizarViewDimClientes() {
        try (Connection conn = br.com.extrator.util.GerenciadorConexao.obterConexao()) {
            final String[] fontes = new String[]{
                "vw_fretes_powerbi",
                "vw_coletas_powerbi"
            };
            final java.util.List<String> existentes = new java.util.ArrayList<>();
            for (final String v : fontes) {
                try (PreparedStatement s = conn.prepareStatement(
                    "SELECT 1 FROM sys.views WHERE name = ? AND schema_id = SCHEMA_ID('dbo')")) {
                    s.setString(1, v);
                    try (ResultSet rs = s.executeQuery()) {
                        if (rs.next()) existentes.add(v);
                    }
                }
            }
            final StringBuilder sb = new StringBuilder();
            if (existentes.isEmpty()) {
                sb.append("SELECT TOP 0 CAST(NULL AS BIGINT) AS [ID], CAST(NULL AS NVARCHAR(255)) AS [Nome] FROM sys.objects");
            } else {
                boolean first = true;
                for (final String v : existentes) {
                    if (!first) sb.append(" UNION ");
                    if ("vw_fretes_powerbi".equals(v)) {
                        sb.append("SELECT DISTINCT [Pagador ID] AS [ID], [Pagador] AS [Nome] FROM dbo.").append(v)
                          .append(" WHERE [Pagador ID] IS NOT NULL");
                    } else if ("vw_coletas_powerbi".equals(v)) {
                        sb.append("SELECT DISTINCT [Cliente ID] AS [ID], [Cliente] AS [Nome] FROM dbo.").append(v)
                          .append(" WHERE [Cliente ID] IS NOT NULL");
                    }
                    first = false;
                }
            }
            final String ddl = "CREATE OR ALTER VIEW dbo.vw_dim_clientes AS " + sb.toString();
            try (PreparedStatement stmt = conn.prepareStatement(ddl)) {
                stmt.executeUpdate();
            }
            logger.info("✅ View dbo.vw_dim_clientes criada/atualizada com {} fonte(s)", existentes.size());
        } catch (final SQLException e) {
            logger.error("❌ Erro ao criar/atualizar view dbo.vw_dim_clientes: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao criar/atualizar view dbo.vw_dim_clientes", e);
        }
    }

    public void criarOuAtualizarViewDimVeiculos() {
        try (Connection conn = br.com.extrator.util.GerenciadorConexao.obterConexao()) {
            boolean existe = false;
            try (PreparedStatement s = conn.prepareStatement(
                "SELECT 1 FROM sys.views WHERE name = 'vw_manifestos_powerbi' AND schema_id = SCHEMA_ID('dbo')")) {
                try (ResultSet rs = s.executeQuery()) {
                    if (rs.next()) existe = true;
                }
            }
            final String ddl;
            if (existe) {
                ddl = """
                    CREATE OR ALTER VIEW dbo.vw_dim_veiculos AS
                    SELECT DISTINCT
                        UPPER(LTRIM(RTRIM([Veículo/Placa]))) AS Placa,
                        MAX(UPPER(LTRIM(RTRIM([Tipo Veículo/Nome])))) AS TipoVeiculo,
                        MAX(UPPER(LTRIM(RTRIM([Proprietário/Nome])))) AS Proprietario
                    FROM dbo.vw_manifestos_powerbi
                    WHERE [Veículo/Placa] IS NOT NULL AND LTRIM(RTRIM([Veículo/Placa])) <> ''
                    GROUP BY UPPER(LTRIM(RTRIM([Veículo/Placa])))""";
            } else {
                ddl = """
                    CREATE OR ALTER VIEW dbo.vw_dim_veiculos AS
                    SELECT TOP 0 CAST(NULL AS NVARCHAR(50)) AS Placa,
                           CAST(NULL AS NVARCHAR(255)) AS TipoVeiculo,
                           CAST(NULL AS NVARCHAR(255)) AS Proprietario
                    FROM sys.objects""";
            }
            try (PreparedStatement stmt = conn.prepareStatement(ddl)) {
                stmt.executeUpdate();
            }
            logger.info("✅ View dbo.vw_dim_veiculos criada/atualizada");
        } catch (final SQLException e) {
            logger.error("❌ Erro ao criar/atualizar view dbo.vw_dim_veiculos: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao criar/atualizar view dbo.vw_dim_veiculos", e);
        }
    }

    public void criarOuAtualizarViewDimMotoristas() {
        try (Connection conn = br.com.extrator.util.GerenciadorConexao.obterConexao()) {
            boolean existe = false;
            try (PreparedStatement s = conn.prepareStatement(
                "SELECT 1 FROM sys.views WHERE name = 'vw_manifestos_powerbi' AND schema_id = SCHEMA_ID('dbo')")) {
                try (ResultSet rs = s.executeQuery()) {
                    if (rs.next()) existe = true;
                }
            }
            final String ddl;
            if (existe) {
                ddl = """
                    CREATE OR ALTER VIEW dbo.vw_dim_motoristas AS
                    SELECT DISTINCT UPPER(LTRIM(RTRIM([Motorista]))) AS NomeMotorista
                    FROM dbo.vw_manifestos_powerbi
                    WHERE [Motorista] IS NOT NULL
                      AND LTRIM(RTRIM([Motorista])) <> ''
                      AND [Motorista] NOT LIKE '%MOTORISTA%'""";
            } else {
                ddl = """
                    CREATE OR ALTER VIEW dbo.vw_dim_motoristas AS
                    SELECT TOP 0 CAST(NULL AS NVARCHAR(255)) AS NomeMotorista
                    FROM sys.objects""";
            }
            try (PreparedStatement stmt = conn.prepareStatement(ddl)) {
                stmt.executeUpdate();
            }
            logger.info("✅ View dbo.vw_dim_motoristas criada/atualizada");
        } catch (final SQLException e) {
            logger.error("❌ Erro ao criar/atualizar view dbo.vw_dim_motoristas: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao criar/atualizar view dbo.vw_dim_motoristas", e);
        }
    }

    public void criarOuAtualizarViewDimPlanoContas() {
        try (Connection conn = br.com.extrator.util.GerenciadorConexao.obterConexao()) {
            boolean existe = false;
            try (PreparedStatement s = conn.prepareStatement(
                "SELECT 1 FROM sys.views WHERE name = 'vw_contas_a_pagar_powerbi' AND schema_id = SCHEMA_ID('dbo')")) {
                try (ResultSet rs = s.executeQuery()) {
                    if (rs.next()) existe = true;
                }
            }
            final String ddl;
            if (existe) {
                ddl = """
                    CREATE OR ALTER VIEW dbo.vw_dim_planocontas AS
                    SELECT
                        UPPER(LTRIM(RTRIM([Conta Contábil/Descrição]))) AS Descricao,
                        ISNULL(MAX([Conta Contábil/Classificação]), 'OUTROS / NÃO CLASSIFICADO') AS Classificacao
                    FROM dbo.vw_contas_a_pagar_powerbi
                    WHERE [Conta Contábil/Descrição] IS NOT NULL
                      AND LTRIM(RTRIM([Conta Contábil/Descrição])) <> ''
                    GROUP BY UPPER(LTRIM(RTRIM([Conta Contábil/Descrição])))""";
            } else {
                ddl = """
                    CREATE OR ALTER VIEW dbo.vw_dim_planocontas AS
                    SELECT TOP 0 CAST(NULL AS NVARCHAR(255)) AS Descricao,
                           CAST(NULL AS NVARCHAR(255)) AS Classificacao
                    FROM sys.objects""";
            }
            try (PreparedStatement stmt = conn.prepareStatement(ddl)) {
                stmt.executeUpdate();
            }
            logger.info("✅ View dbo.vw_dim_planocontas criada/atualizada");
        } catch (final SQLException e) {
            logger.error("❌ Erro ao criar/atualizar view dbo.vw_dim_planocontas: {}", e.getMessage(), e);
            throw new RuntimeException("Falha ao criar/atualizar view dbo.vw_dim_planocontas", e);
        }
    }
}
