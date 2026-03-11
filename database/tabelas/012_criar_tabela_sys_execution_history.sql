-- ============================================
-- Script de criacao da tabela 'sys_execution_history'
-- ============================================

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.sys_execution_history') AND type in (N'U'))
BEGIN
    CREATE TABLE dbo.sys_execution_history (
        id BIGINT IDENTITY(1,1) PRIMARY KEY,
        start_time DATETIME NOT NULL,
        end_time DATETIME NOT NULL,
        duration_seconds INT NOT NULL,
        status VARCHAR(20) NOT NULL,
        total_records INT NOT NULL,
        error_category VARCHAR(50) NULL,
        error_message VARCHAR(500) NULL
    );
    PRINT 'Tabela sys_execution_history criada com sucesso!';
END
ELSE
BEGIN
    PRINT 'Tabela sys_execution_history ja existe. Pulando criacao.';
END
GO
