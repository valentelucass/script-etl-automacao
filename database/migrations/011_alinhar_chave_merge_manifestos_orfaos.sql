PRINT 'Migration 011: alinhar chave_merge_hash de manifestos para fallback em identificador_unico';
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;

DECLARE @MigrationId NVARCHAR(255) = N'011_alinhar_chave_merge_manifestos_orfaos';

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
    PRINT 'Migracao 011_alinhar_chave_merge_manifestos_orfaos ja aplicada. Nenhuma acao necessaria.';
    RETURN;
END;

IF OBJECT_ID(N'dbo.manifestos', N'U') IS NULL
BEGIN
    PRINT 'Tabela dbo.manifestos nao encontrada. Nada a fazer.';
    INSERT INTO dbo.schema_migrations (migration_id, notes)
    VALUES (@MigrationId, N'Tabela dbo.manifestos ausente no momento da migracao; nenhuma alteracao aplicada.');
    RETURN;
END;

BEGIN TRY
BEGIN TRANSACTION;

IF EXISTS (
    SELECT 1
      FROM sys.key_constraints
     WHERE name = 'UQ_manifestos_chave_composta'
       AND parent_object_id = OBJECT_ID(N'dbo.manifestos')
)
BEGIN
    ALTER TABLE dbo.manifestos DROP CONSTRAINT UQ_manifestos_chave_composta;
    PRINT 'UQ_manifestos_chave_composta removida temporariamente.';
END;

IF COL_LENGTH('dbo.manifestos', 'chave_merge_hash') IS NOT NULL
BEGIN
    ALTER TABLE dbo.manifestos DROP COLUMN chave_merge_hash;
    PRINT 'Coluna computada chave_merge_hash removida.';
END;

ALTER TABLE dbo.manifestos
ADD chave_merge_hash AS (
    CAST(sequence_code AS VARCHAR(20)) + '|' +
    ISNULL(CAST(pick_sequence_code AS VARCHAR(20)), ISNULL(identificador_unico, '-1')) + '|' +
    ISNULL(CAST(mdfe_number AS VARCHAR(20)), '-1')
) PERSISTED;
PRINT 'Coluna computada chave_merge_hash recriada com fallback em identificador_unico.';

ALTER TABLE dbo.manifestos
    ADD CONSTRAINT UQ_manifestos_chave_composta UNIQUE (chave_merge_hash);
PRINT 'UQ_manifestos_chave_composta recriada.';

INSERT INTO dbo.schema_migrations (migration_id, notes)
VALUES (
    @MigrationId,
    N'chave_merge_hash passou a usar identificador_unico quando pick_sequence_code for NULL, preservando manifestos orfaos distintos.'
);

COMMIT TRANSACTION;
PRINT 'Migration 011_alinhar_chave_merge_manifestos_orfaos concluida com sucesso.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
    BEGIN
        ROLLBACK TRANSACTION;
    END;
    THROW;
END CATCH;
GO
