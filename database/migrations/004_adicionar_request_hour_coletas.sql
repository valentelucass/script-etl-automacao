-- ============================================================================
-- MIGRACAO: Adicionar coluna request_hour na tabela coletas
-- ============================================================================
-- Arquivo: 004_adicionar_request_hour_coletas.sql
-- Descricao: Adiciona a coluna request_hour (NVARCHAR(8)) na tabela coletas
--            para armazenar o horario de solicitacao fornecido separadamente
--            pela API GraphQL (campo requestHour, formato HH:MM:SS).
--
-- Motivacao: A API retorna requestDate (DATE) e requestHour (STRING) como
--            campos distintos. O ColetaEntity ja possui o campo requestHour,
--            mas a coluna nao existia na tabela, causando descarte do dado.
--            Sem a coluna, a view vw_coletas_powerbi nao consegue exibir
--            a Hora (Solicitacao) e sempre mostra 00:00:00.
-- ============================================================================

SET NOCOUNT ON;

DECLARE @MigrationId NVARCHAR(255) = N'004_adicionar_request_hour_coletas';

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
    PRINT 'Migracao 004_adicionar_request_hour_coletas ja aplicada. Nenhuma acao necessaria.';
    RETURN;
END

PRINT 'Iniciando adicao de request_hour na tabela coletas...';

IF OBJECT_ID(N'dbo.coletas', N'U') IS NULL
BEGIN
    PRINT 'Tabela coletas nao encontrada. Sera criada ja com request_hour via script de tabelas.';
    GOTO RegistrarMigracao;
END

-- ============================================================================
-- PASSO 1: Adicionar coluna request_hour
-- ============================================================================

PRINT 'PASSO 1: Adicionando coluna request_hour...';

IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.coletas')
      AND name = 'request_hour'
)
BEGIN
    BEGIN TRY
        ALTER TABLE dbo.coletas
            ADD request_hour NVARCHAR(8) NULL;
        PRINT '  Coluna request_hour adicionada com sucesso.';
    END TRY
    BEGIN CATCH
        PRINT '  ERRO ao adicionar request_hour: ' + ERROR_MESSAGE();
        THROW;
    END CATCH
END
ELSE
BEGIN
    PRINT '  Coluna request_hour ja existe. Pulando.';
END

-- ============================================================================
-- PASSO 2: Validacao
-- ============================================================================

PRINT 'PASSO 2: Validando coluna adicionada...';

SELECT
    name       AS coluna,
    TYPE_NAME(system_type_id) AS tipo,
    max_length,
    is_nullable
FROM sys.columns
WHERE object_id = OBJECT_ID('dbo.coletas')
  AND name = 'request_hour';

PRINT 'Coluna request_hour adicionada. Reexecute o ETL de coletas para popular os dados.';

GOTO RegistrarMigracao;

RegistrarMigracao:

IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE migration_id = @MigrationId)
BEGIN
    INSERT INTO dbo.schema_migrations (migration_id, notes)
    VALUES (@MigrationId, N'Adiciona coluna request_hour NVARCHAR(8) na tabela coletas para armazenar horario de solicitacao da API GraphQL.');
END

PRINT 'Migracao 004 registrada com sucesso.';

GO
