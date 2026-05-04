PRINT 'Migration 012: adicionar coluna cortesia em fretes';
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;

DECLARE @MigrationId NVARCHAR(255) = N'012_adicionar_frete_cortesia';

IF OBJECT_ID(N'dbo.schema_migrations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.schema_migrations (
        migration_id NVARCHAR(255) NOT NULL,
        applied_at DATETIME2(0) NOT NULL CONSTRAINT DF_schema_migrations_applied_at DEFAULT SYSUTCDATETIME(),
        notes NVARCHAR(MAX) NULL,
        CONSTRAINT PK_schema_migrations PRIMARY KEY (migration_id)
    );
END;

IF EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE migration_id = @MigrationId)
BEGIN
    PRINT 'Migracao 012_adicionar_frete_cortesia ja aplicada. Nenhuma acao necessaria.';
    RETURN;
END;

IF OBJECT_ID(N'dbo.fretes', N'U') IS NULL
BEGIN
    PRINT 'Tabela dbo.fretes nao encontrada. Nada a fazer.';
    INSERT INTO dbo.schema_migrations (migration_id, notes)
    VALUES (@MigrationId, N'Tabela dbo.fretes ausente no momento da migracao; nenhuma alteracao aplicada.');
    RETURN;
END;

BEGIN TRY
BEGIN TRANSACTION;

IF COL_LENGTH('dbo.fretes', 'cortesia') IS NULL
BEGIN
    ALTER TABLE dbo.fretes ADD cortesia BIT NULL;
    PRINT 'Coluna dbo.fretes.cortesia adicionada.';
END;
ELSE
BEGIN
    PRINT 'Coluna dbo.fretes.cortesia ja existe.';
END;

INSERT INTO dbo.schema_migrations (migration_id, notes)
VALUES (
    @MigrationId,
    N'Adiciona flag de cortesia dos fretes GraphQL para filtros oficiais dos indicadores de gestao a vista.'
);

COMMIT TRANSACTION;
PRINT 'Migration 012_adicionar_frete_cortesia concluida com sucesso.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
    BEGIN
        ROLLBACK TRANSACTION;
    END;
    THROW;
END CATCH;
GO
