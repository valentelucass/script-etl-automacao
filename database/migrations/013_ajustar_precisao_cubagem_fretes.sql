PRINT 'Migration 013: ajustar precisao de cubagem em fretes';
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;

DECLARE @MigrationId NVARCHAR(255) = N'013_ajustar_precisao_cubagem_fretes';

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
    PRINT 'Migracao 013_ajustar_precisao_cubagem_fretes ja aplicada. Nenhuma acao necessaria.';
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

ALTER TABLE dbo.fretes ALTER COLUMN total_cubic_volume DECIMAL(18, 6) NULL;
ALTER TABLE dbo.fretes ALTER COLUMN cubages_cubed_weight DECIMAL(18, 6) NULL;

UPDATE dbo.fretes
SET
    total_cubic_volume = COALESCE(
        TRY_CONVERT(DECIMAL(18, 6), JSON_VALUE(metadata, '$.totalCubicVolume')),
        total_cubic_volume
    ),
    cubages_cubed_weight = COALESCE(
        TRY_CONVERT(DECIMAL(18, 6), JSON_VALUE(metadata, '$.cubagesCubedWeight')),
        cubages_cubed_weight
    )
WHERE metadata IS NOT NULL
  AND ISJSON(metadata) = 1
  AND (
      JSON_VALUE(metadata, '$.totalCubicVolume') IS NOT NULL
      OR JSON_VALUE(metadata, '$.cubagesCubedWeight') IS NOT NULL
  );

INSERT INTO dbo.schema_migrations (migration_id, notes)
VALUES (
    @MigrationId,
    N'Aumenta escala dos campos de cubagem para preservar M3 fracionario vindo do GraphQL e reidrata valores a partir do metadata.'
);

COMMIT TRANSACTION;
PRINT 'Migration 013_ajustar_precisao_cubagem_fretes concluida com sucesso.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
    BEGIN
        ROLLBACK TRANSACTION;
    END;
    THROW;
END CATCH;
GO
