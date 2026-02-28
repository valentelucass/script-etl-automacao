-- ============================================================================
-- Script de criacao da tabela temporaria de auditoria de campos da API
-- Tabela: sys_auditoria_temp
-- ============================================================================

IF OBJECT_ID(N'dbo.sys_auditoria_temp', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.sys_auditoria_temp (
        entidade NVARCHAR(100) NULL,
        campo_api NVARCHAR(400) NULL,
        data_auditoria DATETIME2 NULL CONSTRAINT DF_sys_auditoria_temp_data_auditoria DEFAULT SYSDATETIME()
    );

    PRINT 'Tabela sys_auditoria_temp criada com sucesso!';
END
ELSE
BEGIN
    PRINT 'Tabela sys_auditoria_temp ja existe. Pulando criacao.';
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_sys_auditoria_temp_entidade'
      AND object_id = OBJECT_ID(N'dbo.sys_auditoria_temp')
)
BEGIN
    CREATE INDEX IX_sys_auditoria_temp_entidade
        ON dbo.sys_auditoria_temp (entidade);

    PRINT 'Indice IX_sys_auditoria_temp_entidade criado com sucesso!';
END
ELSE
BEGIN
    PRINT 'Indice IX_sys_auditoria_temp_entidade ja existe. Pulando criacao.';
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_sys_auditoria_temp_campo'
      AND object_id = OBJECT_ID(N'dbo.sys_auditoria_temp')
)
BEGIN
    CREATE INDEX IX_sys_auditoria_temp_campo
        ON dbo.sys_auditoria_temp (campo_api);

    PRINT 'Indice IX_sys_auditoria_temp_campo criado com sucesso!';
END
ELSE
BEGIN
    PRINT 'Indice IX_sys_auditoria_temp_campo ja existe. Pulando criacao.';
END
GO
