PRINT 'Migration 055: confirmação segura de ausência na origem para Coletas';
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO

DECLARE @MigrationId NVARCHAR(255) = N'055_confirmar_ausencia_origem_coletas';

IF EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE migration_id = @MigrationId)
BEGIN
    PRINT 'Migracao 055 ja aplicada. Nenhuma acao necessaria.';
    RETURN;
END;

BEGIN TRY
BEGIN TRANSACTION;

IF COL_LENGTH(N'dbo.coletas', N'ausente_na_origem_desde') IS NULL
    ALTER TABLE dbo.coletas ADD ausente_na_origem_desde DATETIME2(0) NULL;

IF COL_LENGTH(N'dbo.coletas', N'confirmacoes_ausencia_origem') IS NULL
    ALTER TABLE dbo.coletas
    ADD confirmacoes_ausencia_origem SMALLINT NOT NULL
        CONSTRAINT DF_coletas_confirmacoes_ausencia_origem DEFAULT (0) WITH VALUES;

IF COL_LENGTH(N'dbo.coletas', N'ultima_reconciliacao_origem_em') IS NULL
    ALTER TABLE dbo.coletas ADD ultima_reconciliacao_origem_em DATETIME2(0) NULL;

IF COL_LENGTH(N'dbo.coletas', N'reconciliacao_origem_run_id') IS NULL
    ALTER TABLE dbo.coletas ADD reconciliacao_origem_run_id NVARCHAR(36) NULL;

IF COL_LENGTH(N'dbo.coletas', N'motivo_exclusao_origem') IS NULL
    ALTER TABLE dbo.coletas ADD motivo_exclusao_origem NVARCHAR(255) NULL;

EXEC sys.sp_executesql N'
    UPDATE dbo.coletas
       SET confirmacoes_ausencia_origem = CASE WHEN excluido_na_origem = 1 THEN 2 ELSE 0 END,
           ausente_na_origem_desde = CASE
               WHEN excluido_na_origem = 1 THEN COALESCE(ausente_na_origem_desde, data_exclusao_origem)
               ELSE NULL
           END
     WHERE confirmacoes_ausencia_origem IS NULL
        OR (excluido_na_origem = 1 AND confirmacoes_ausencia_origem < 2);
';

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.coletas')
      AND name = N'IX_coletas_reconciliacao_ativas_request_date'
)
BEGIN
    EXEC sys.sp_executesql N'
        CREATE INDEX IX_coletas_reconciliacao_ativas_request_date
            ON dbo.coletas (request_date, id)
            INCLUDE (confirmacoes_ausencia_origem)
            WHERE excluido_na_origem = 0;
    ';
END;

INSERT INTO dbo.schema_migrations (migration_id, notes)
VALUES (
    @MigrationId,
    N'Adiciona confirmação consecutiva, trilha de auditoria e índice para reconciliação segura de ausências de Coletas.'
);

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;

PRINT 'Migration 055 concluida com sucesso.';
GO
