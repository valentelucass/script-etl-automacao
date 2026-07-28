PRINT 'Migration 051: corrigir prioridade operacional de status em manifestos';
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO

DECLARE @MigrationId NVARCHAR(255) = N'051_corrigir_prioridade_status_manifestos';

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

IF EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE migration_id = @MigrationId)
BEGIN
    PRINT 'Migracao 051_corrigir_prioridade_status_manifestos ja aplicada. Nenhuma acao necessaria.';
    RETURN;
END;

IF OBJECT_ID(N'dbo.sp_carga_fato_gestao_vista_manifestos', N'P') IS NULL
    THROW 51151, 'Procedure dbo.sp_carga_fato_gestao_vista_manifestos nao encontrada.', 1;

DECLARE @Definition NVARCHAR(MAX) = OBJECT_DEFINITION(OBJECT_ID(N'dbo.sp_carga_fato_gestao_vista_manifestos', N'P'));
DECLARE @Legado NVARCHAR(MAX) = N'MAX(ml.status) AS status_raw,';
DECLARE @Corrigido NVARCHAR(MAX) = N'CASE
                    WHEN MAX(CASE WHEN LOWER(LTRIM(RTRIM(ml.status))) = N''closed'' THEN 1 ELSE 0 END) = 1
                        THEN N''closed''
                    WHEN MAX(CASE WHEN LOWER(LTRIM(RTRIM(ml.status))) = N''in_transit'' THEN 1 ELSE 0 END) = 1
                        THEN N''in_transit''
                    WHEN MAX(CASE WHEN LOWER(LTRIM(RTRIM(ml.status))) = N''pending'' THEN 1 ELSE 0 END) = 1
                        THEN N''pending''
                    ELSE MAX(ml.status)
                END AS status_raw,';

IF @Definition IS NULL
    THROW 51152, 'Nao foi possivel ler a definicao da procedure de manifestos.', 1;

BEGIN TRY
BEGIN TRANSACTION;

IF CHARINDEX(@Legado, @Definition) > 0
BEGIN
    SET @Definition = REPLACE(@Definition, @Legado, @Corrigido);
    SET @Definition = REPLACE(@Definition, N'CREATE OR ALTER PROCEDURE', N'ALTER PROCEDURE');
    SET @Definition = REPLACE(@Definition, N'CREATE PROCEDURE', N'ALTER PROCEDURE');
    EXEC sys.sp_executesql @Definition;
END
ELSE IF CHARINDEX(N'LOWER(LTRIM(RTRIM(ml.status))) = N''closed''', @Definition) = 0
BEGIN
    THROW 51153, 'Definicao inesperada da procedure de manifestos; publicacao manual necessaria.', 1;
END;

INSERT INTO dbo.schema_migrations (migration_id, notes)
VALUES (
    @MigrationId,
    N'Prioriza closed, in_transit e pending na consolidacao de status por manifesto.'
);

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;

PRINT 'Migration 051_corrigir_prioridade_status_manifestos concluida com sucesso.';
GO
