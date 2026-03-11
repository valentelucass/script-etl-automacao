-- ============================================================================
-- MIGRACAO 001: criar tabela de controle de migracoes aplicadas
-- ============================================================================

IF OBJECT_ID(N'dbo.schema_migrations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.schema_migrations (
        migration_id NVARCHAR(255) NOT NULL,
        applied_at DATETIME2(0) NOT NULL CONSTRAINT DF_schema_migrations_applied_at DEFAULT SYSUTCDATETIME(),
        checksum_sha256 VARCHAR(64) NULL,
        notes NVARCHAR(500) NULL,
        CONSTRAINT PK_schema_migrations PRIMARY KEY (migration_id)
    );
    PRINT 'Tabela dbo.schema_migrations criada com sucesso.';
END
ELSE
BEGIN
    PRINT 'Tabela dbo.schema_migrations ja existe. Pulando criacao.';
END
GO
