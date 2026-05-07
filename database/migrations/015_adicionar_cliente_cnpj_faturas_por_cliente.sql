PRINT 'Migration 015: adicionar cliente_cnpj em faturas_por_cliente';
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
SET XACT_ABORT ON;

DECLARE @MigrationId NVARCHAR(255) = N'015_adicionar_cliente_cnpj_faturas_por_cliente';

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

DECLARE @MigrationJaRegistrada BIT = CASE
    WHEN EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE migration_id = @MigrationId) THEN 1
    ELSE 0
END;

IF @MigrationJaRegistrada = 1
BEGIN
    PRINT 'Migracao 015_adicionar_cliente_cnpj_faturas_por_cliente ja registrada. Conferindo estado fisico da tabela.';
END;

IF OBJECT_ID(N'dbo.faturas_por_cliente', N'U') IS NULL
BEGIN
    PRINT 'Tabela dbo.faturas_por_cliente nao encontrada. Migracao nao registrada para permitir nova tentativa futura.';
    RETURN;
END;

BEGIN TRY
BEGIN TRANSACTION;

IF COL_LENGTH('dbo.faturas_por_cliente', 'cliente_cnpj') IS NULL
BEGIN
    ALTER TABLE dbo.faturas_por_cliente ADD cliente_cnpj NVARCHAR(14) NULL;
    PRINT 'Coluna dbo.faturas_por_cliente.cliente_cnpj adicionada.';
END;
ELSE
BEGIN
    PRINT 'Coluna dbo.faturas_por_cliente.cliente_cnpj ja existe.';
END;

EXEC sys.sp_executesql N'
;WITH documentos AS (
    SELECT
        unique_id,
        REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            LTRIM(RTRIM(COALESCE(pagador_documento, N''''))),
            N''.'', N''''), N''-'', N''''), N''/'', N''''), N'' '', N''''), CHAR(9), N''''), CHAR(160), N'''') AS documento_limpo
    FROM dbo.faturas_por_cliente
)
UPDATE fpc
SET cliente_cnpj = CASE
    WHEN documentos.documento_limpo NOT LIKE ''%[^0-9]%''
     AND LEN(documentos.documento_limpo) = 14
    THEN documentos.documento_limpo
    ELSE NULL
END
FROM dbo.faturas_por_cliente fpc
INNER JOIN documentos ON documentos.unique_id = fpc.unique_id
WHERE ISNULL(fpc.cliente_cnpj, N'''') <> ISNULL(CASE
    WHEN documentos.documento_limpo NOT LIKE ''%[^0-9]%''
     AND LEN(documentos.documento_limpo) = 14
    THEN documentos.documento_limpo
    ELSE NULL
END, N'''');
';

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_fpc_cliente_cnpj'
      AND object_id = OBJECT_ID(N'dbo.faturas_por_cliente')
)
BEGIN
    EXEC sys.sp_executesql N'
        CREATE INDEX IX_fpc_cliente_cnpj
            ON dbo.faturas_por_cliente(cliente_cnpj)
            WHERE cliente_cnpj IS NOT NULL;
    ';
    PRINT 'Indice IX_fpc_cliente_cnpj criado.';
END;

IF @MigrationJaRegistrada = 0
BEGIN
    INSERT INTO dbo.schema_migrations (migration_id, notes)
    VALUES (
        @MigrationId,
        N'Adiciona coluna persistida cliente_cnpj, populada a partir de fit_pyr_document/pagador_documento quando houver CNPJ de 14 digitos.'
    );
END;

COMMIT TRANSACTION;
PRINT 'Migration 015_adicionar_cliente_cnpj_faturas_por_cliente concluida com sucesso.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
    BEGIN
        ROLLBACK TRANSACTION;
    END;
    THROW;
END CATCH;
GO
