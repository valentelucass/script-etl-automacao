PRINT 'Migration 053: catálogo canônico e timestamp normalizado de status das Coletas';
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO

DECLARE @MigrationId NVARCHAR(255) = N'053_criar_catalogo_status_e_timestamp_coletas';

IF OBJECT_ID(N'dbo.schema_migrations', N'U') IS NULL
    THROW 51158, 'Tabela dbo.schema_migrations nao encontrada.', 1;

IF EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE migration_id = @MigrationId)
BEGIN
    PRINT 'Migracao 053 ja aplicada. Nenhuma acao necessaria.';
    RETURN;
END;

BEGIN TRY
BEGIN TRANSACTION;

IF OBJECT_ID(N'dbo.dim_status_coleta', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.dim_status_coleta (
        codigo_status NVARCHAR(50) NOT NULL,
        rotulo_powerbi NVARCHAR(100) NOT NULL,
        estado_terminal BIT NOT NULL,
        ativo BIT NOT NULL
            CONSTRAINT DF_dim_status_coleta_ativo DEFAULT (1),
        atualizado_em DATETIME2(0) NOT NULL
            CONSTRAINT DF_dim_status_coleta_atualizado_em DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT PK_dim_status_coleta PRIMARY KEY (codigo_status)
    );
END;

MERGE dbo.dim_status_coleta AS target
USING (VALUES
    (N'pending', N'Pendente', CAST(0 AS bit)),
    (N'treatment', N'Em tratativa', CAST(0 AS bit)),
    (N'manifested', N'Manifestada', CAST(0 AS bit)),
    (N'in_transit', N'Em trânsito', CAST(0 AS bit)),
    (N'draft', N'Rascunho', CAST(0 AS bit)),
    (N'finished', N'Finalizada', CAST(1 AS bit)),
    (N'done', N'Coletada', CAST(1 AS bit)),
    (N'canceled', N'Cancelada', CAST(1 AS bit)),
    (N'cancelled', N'Cancelada', CAST(1 AS bit))
) AS source (codigo_status, rotulo_powerbi, estado_terminal)
ON target.codigo_status = source.codigo_status
WHEN MATCHED THEN
    UPDATE SET
        rotulo_powerbi = source.rotulo_powerbi,
        estado_terminal = source.estado_terminal,
        ativo = 1,
        atualizado_em = SYSUTCDATETIME()
WHEN NOT MATCHED THEN
    INSERT (codigo_status, rotulo_powerbi, estado_terminal, ativo, atualizado_em)
    VALUES (source.codigo_status, source.rotulo_powerbi, source.estado_terminal, 1, SYSUTCDATETIME());

IF COL_LENGTH(N'dbo.coletas', N'status_updated_at_em') IS NULL
BEGIN
    ALTER TABLE dbo.coletas
    ADD status_updated_at_em DATETIMEOFFSET(0) NULL;
END;

EXEC sys.sp_executesql N'
    UPDATE dbo.coletas
       SET status_updated_at_em = COALESCE(
           TRY_CONVERT(DATETIMEOFFSET(0), status_updated_at, 127),
           TODATETIMEOFFSET(TRY_CONVERT(DATETIME2(0), status_updated_at, 103), ''-03:00'')
       )
     WHERE status_updated_at IS NOT NULL
       AND status_updated_at_em IS NULL;
';

INSERT INTO dbo.schema_migrations (migration_id, notes)
VALUES (
    @MigrationId,
    N'Cria catálogo canônico de status de Coletas e coluna DATETIMEOFFSET para a versão do status.'
);

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;

PRINT 'Migration 053 concluida com sucesso.';
GO
