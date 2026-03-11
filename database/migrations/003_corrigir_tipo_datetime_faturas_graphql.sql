-- ============================================================================
-- CORRECAO: Alterar tipo de created_at e updated_at em faturas_graphql
-- ============================================================================
-- Arquivo: 003_corrigir_tipo_datetime_faturas_graphql.sql
-- Descricao: Converte DATETIME2 para DATETIMEOFFSET nas colunas created_at e
--            updated_at da tabela faturas_graphql, preservando o fuso horario
--            retornado pela API GraphQL (OffsetDateTime no Java).
--
-- Impacto: Os valores ja armazenados eram UTC sem offset (DATETIME2 gravado
--          via Timestamp.from(instant)). Apos a migracao, esses valores
--          permanecem como UTC (offset +00:00). Dados novos passam a ter o
--          offset original da API preservado.
--
-- Compatibilidade: SQL Server converte DATETIME2 -> DATETIMEOFFSET sem perda
--                  de precisao. Operacao segura mesmo com dados existentes.
-- ============================================================================

SET NOCOUNT ON;

DECLARE @MigrationId NVARCHAR(255) = N'003_corrigir_tipo_datetime_faturas_graphql';

IF OBJECT_ID(N'dbo.schema_migrations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.schema_migrations (
        migration_id    NVARCHAR(255) NOT NULL,
        applied_at      DATETIME2(0)  NOT NULL CONSTRAINT DF_schema_migrations_applied_at DEFAULT SYSUTCDATETIME(),
        checksum_sha256 VARCHAR(64)   NULL,
        notes           NVARCHAR(500) NULL,
        CONSTRAINT PK_schema_migrations PRIMARY KEY (migration_id)
    );
END

IF EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE migration_id = @MigrationId)
BEGIN
    PRINT 'Migracao 003_corrigir_tipo_datetime_faturas_graphql ja aplicada. Nenhuma acao necessaria.';
    RETURN;
END

PRINT 'Iniciando correcao de tipo em faturas_graphql...';

IF OBJECT_ID(N'dbo.faturas_graphql', N'U') IS NULL
BEGIN
    PRINT 'Tabela faturas_graphql nao encontrada. Nada a fazer (sera criada ja com DATETIMEOFFSET).';
    GOTO RegistrarMigracao;
END

-- ============================================================================
-- PASSO 1: Alterar coluna created_at
-- ============================================================================

PRINT 'PASSO 1: Alterando created_at de DATETIME2 para DATETIMEOFFSET...';

IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.faturas_graphql')
      AND name = 'created_at'
      AND system_type_id = TYPE_ID('datetime2')
)
BEGIN
    BEGIN TRY
        ALTER TABLE dbo.faturas_graphql
            ALTER COLUMN created_at DATETIMEOFFSET NULL;
        PRINT '  created_at alterada com sucesso.';
    END TRY
    BEGIN CATCH
        PRINT '  ERRO ao alterar created_at: ' + ERROR_MESSAGE();
        THROW;
    END CATCH
END
ELSE
BEGIN
    PRINT '  created_at ja e DATETIMEOFFSET ou nao existe. Pulando.';
END

-- ============================================================================
-- PASSO 2: Alterar coluna updated_at
-- ============================================================================

PRINT 'PASSO 2: Alterando updated_at de DATETIME2 para DATETIMEOFFSET...';

IF EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.faturas_graphql')
      AND name = 'updated_at'
      AND system_type_id = TYPE_ID('datetime2')
)
BEGIN
    BEGIN TRY
        ALTER TABLE dbo.faturas_graphql
            ALTER COLUMN updated_at DATETIMEOFFSET NULL;
        PRINT '  updated_at alterada com sucesso.';
    END TRY
    BEGIN CATCH
        PRINT '  ERRO ao alterar updated_at: ' + ERROR_MESSAGE();
        THROW;
    END CATCH
END
ELSE
BEGIN
    PRINT '  updated_at ja e DATETIMEOFFSET ou nao existe. Pulando.';
END

-- ============================================================================
-- PASSO 3: Validacao
-- ============================================================================

PRINT 'PASSO 3: Validando tipos atuais...';

SELECT
    name          AS coluna,
    TYPE_NAME(system_type_id) AS tipo_atual,
    is_nullable
FROM sys.columns
WHERE object_id = OBJECT_ID('dbo.faturas_graphql')
  AND name IN ('created_at', 'updated_at');

PRINT 'Correcao concluida. Registros existentes permanecem em UTC (+00:00).';

GOTO RegistrarMigracao;

RegistrarMigracao:

IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE migration_id = @MigrationId)
BEGIN
    INSERT INTO dbo.schema_migrations (migration_id, notes)
    VALUES (@MigrationId, N'Conversao de DATETIME2 para DATETIMEOFFSET em faturas_graphql.created_at e updated_at.');
END

PRINT 'Migracao 003 registrada com sucesso.';

GO
