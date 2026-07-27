PRINT 'Migration 050: garantir regra FRIGELAR para CWB';
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO

DECLARE @MigrationId NVARCHAR(255) = N'050_garantir_regra_frigelar_cwb';

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
    PRINT 'Migracao 050_garantir_regra_frigelar_cwb ja aplicada. Nenhuma acao necessaria.';
    RETURN;
END;

IF OBJECT_ID(N'dbo.regras_atribuicao_filial', N'U') IS NULL
    THROW 51150, 'Tabela dbo.regras_atribuicao_filial nao encontrada. Execute o schema-base antes da migration 050.', 1;

BEGIN TRY
BEGIN TRANSACTION;

IF EXISTS (
    SELECT 1
    FROM dbo.regras_atribuicao_filial
    WHERE pagador_documento_key = N'92660406007040'
      AND ativo = 1
)
BEGIN
    UPDATE dbo.regras_atribuicao_filial
       SET filial_destino_nome = N'CWB - RODOGARCIA',
           filial_destino_key = N'cwb - rodogarcia',
           motivo = N'Migracao financeira solicitada pela diretoria (emissao original na NHB)'
     WHERE pagador_documento_key = N'92660406007040'
       AND ativo = 1;
END
ELSE
BEGIN
    INSERT INTO dbo.regras_atribuicao_filial (
        pagador_documento_key,
        filial_destino_nome,
        filial_destino_key,
        ativo,
        motivo
    ) VALUES (
        N'92660406007040',
        N'CWB - RODOGARCIA',
        N'cwb - rodogarcia',
        CAST(1 AS BIT),
        N'Migracao financeira solicitada pela diretoria (emissao original na NHB)'
    );
END;

INSERT INTO dbo.schema_migrations (migration_id, notes)
VALUES (
    @MigrationId,
    N'Garante a atribuicao financeira de Frigelar Garuva (92.660.406/0070-40) para CWB.'
);

COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;

PRINT 'Migration 050_garantir_regra_frigelar_cwb concluida com sucesso.';
GO
