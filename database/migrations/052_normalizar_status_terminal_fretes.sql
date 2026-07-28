PRINT 'Migration 052: normalizar status terminal na view de fretes';
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO

DECLARE @MigrationId NVARCHAR(255) = N'052_normalizar_status_terminal_fretes';

IF OBJECT_ID(N'dbo.schema_migrations', N'U') IS NULL
    THROW 51154, 'Tabela dbo.schema_migrations nao encontrada.', 1;

IF EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE migration_id = @MigrationId)
BEGIN
    PRINT 'Migracao 052_normalizar_status_terminal_fretes ja aplicada. Nenhuma acao necessaria.';
    RETURN;
END;

IF OBJECT_ID(N'dbo.vw_fretes_powerbi', N'V') IS NULL
    THROW 51155, 'View dbo.vw_fretes_powerbi nao encontrada.', 1;

DECLARE @Definition NVARCHAR(MAX) = OBJECT_DEFINITION(OBJECT_ID(N'dbo.vw_fretes_powerbi', N'V'));
DECLARE @Legado NVARCHAR(MAX) = N'WHEN ''finished'' THEN ''finalizado''';
DECLARE @Corrigido NVARCHAR(MAX) = N'WHEN ''finished'' THEN ''finalizado''
        WHEN ''done'' THEN ''finalizado''
        WHEN ''canceled'' THEN ''cancelada''
        WHEN ''cancelled'' THEN ''cancelada''';

IF @Definition IS NULL
    THROW 51156, 'Nao foi possivel ler a definicao da view de fretes.', 1;

BEGIN TRY
BEGIN TRANSACTION;

IF CHARINDEX(@Legado, @Definition) > 0
BEGIN
    SET @Definition = REPLACE(@Definition, @Legado, @Corrigido);
    SET @Definition = REPLACE(@Definition, N'CREATE OR ALTER VIEW', N'ALTER VIEW');
    SET @Definition = REPLACE(@Definition, N'CREATE   VIEW', N'ALTER VIEW');
    SET @Definition = REPLACE(@Definition, N'CREATE VIEW', N'ALTER VIEW');
    EXEC sys.sp_executesql @Definition;
END
ELSE IF CHARINDEX(N'WHEN ''done'' THEN ''finalizado''', @Definition) = 0
BEGIN
    THROW 51157, 'Definicao inesperada da view de fretes; publicacao manual necessaria.', 1;
END;

INSERT INTO dbo.schema_migrations (migration_id, notes)
VALUES (
    @MigrationId,
    N'Normaliza done como finalizado e canceled/cancelled como cancelada na view de Fretes.'
);

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;

PRINT 'Migration 052_normalizar_status_terminal_fretes concluida com sucesso.';
GO
