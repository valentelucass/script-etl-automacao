PRINT 'Migration 006: alterar fretes para indicadores de gestao';
GO

IF OBJECT_ID(N'dbo.schema_migrations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.schema_migrations (
        migration_id NVARCHAR(255) NOT NULL,
        applied_at DATETIME2(0) NOT NULL CONSTRAINT DF_schema_migrations_applied_at DEFAULT SYSUTCDATETIME(),
        checksum_sha256 VARCHAR(64) NULL,
        notes NVARCHAR(500) NULL,
        CONSTRAINT PK_schema_migrations PRIMARY KEY (migration_id)
    );
END;
GO

IF COL_LENGTH('dbo.fretes', 'finished_at') IS NULL
BEGIN
    ALTER TABLE dbo.fretes ADD finished_at DATETIMEOFFSET NULL;
END
GO

IF COL_LENGTH('dbo.fretes', 'fit_dpn_performance_finished_at') IS NULL
BEGIN
    ALTER TABLE dbo.fretes ADD fit_dpn_performance_finished_at DATETIMEOFFSET NULL;
END
GO

IF COL_LENGTH('dbo.fretes', 'corporation_sequence_number') IS NULL
BEGIN
    ALTER TABLE dbo.fretes ADD corporation_sequence_number BIGINT NULL;
END
GO

IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE migration_id = N'006_alterar_fretes_indicadores_gestao')
BEGIN
    INSERT INTO dbo.schema_migrations (migration_id, notes)
    VALUES (
        N'006_alterar_fretes_indicadores_gestao',
        N'Adiciona campos finished_at, fit_dpn_performance_finished_at e corporation_sequence_number em dbo.fretes.'
    );
END;
GO
