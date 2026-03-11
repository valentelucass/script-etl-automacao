-- ============================================================================
-- CORRECAO CRITICA #4: Alinhar constraint UNIQUE com logica de MERGE
-- ============================================================================
-- Arquivo: 002_corrigir_constraint_manifestos.sql
-- Descricao: Corrige inconsistencias entre constraint UNIQUE e logica de MERGE
-- Data: 04/02/2026
-- Autor: Sistema de Auditoria
--
-- PROBLEMA ORIGINAL:
-- - MERGE usa: (sequence_code, pick_sequence_code, mdfe_number)
-- - Constraint UNIQUE usa: (sequence_code, identificador_unico)
--
-- SOLUCAO:
-- - Alinhar constraint para usar a mesma chave composta do MERGE
-- - Isso permite multiplos MDF-es e coletas para o mesmo sequence_code
-- ============================================================================

SET NOCOUNT ON;

DECLARE @MigrationId NVARCHAR(255) = N'002_corrigir_constraint_manifestos';

IF OBJECT_ID(N'dbo.schema_migrations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.schema_migrations (
        migration_id NVARCHAR(255) NOT NULL,
        applied_at DATETIME2(0) NOT NULL CONSTRAINT DF_schema_migrations_applied_at DEFAULT SYSUTCDATETIME(),
        checksum_sha256 VARCHAR(64) NULL,
        notes NVARCHAR(500) NULL,
        CONSTRAINT PK_schema_migrations PRIMARY KEY (migration_id)
    );
END

IF EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE migration_id = @MigrationId)
BEGIN
    PRINT 'Migracao 002_corrigir_constraint_manifestos ja aplicada. Nenhuma acao necessaria.';
    RETURN;
END

PRINT '[INFO] Iniciando correcao de constraint em MANIFESTOS...';
PRINT '';

IF OBJECT_ID(N'dbo.manifestos', N'U') IS NULL
BEGIN
    PRINT 'Tabela dbo.manifestos nao encontrada. Nada a fazer (sera criada ja com a constraint correta pelo script de tabelas).';
    GOTO RegistrarMigracao;
END

-- ============================================================================
-- PASSO 1: Backup da Constraint Atual
-- ============================================================================

PRINT '[INFO] PASSO 1: Verificando constraint atual...';

IF EXISTS (
    SELECT 1 FROM sys.key_constraints
    WHERE name = 'UQ_manifestos_sequence_identificador'
    AND parent_object_id = OBJECT_ID('dbo.manifestos')
)
BEGIN
    PRINT '  [INFO] Constraint antiga encontrada: UQ_manifestos_sequence_identificador';
    PRINT '         (sequence_code, identificador_unico)';

    PRINT '';
    PRINT '  [INFO] Verificando registros que se tornarao validos...';

    SELECT
        sequence_code,
        COUNT(*) AS total_registros,
        COUNT(DISTINCT pick_sequence_code) AS picks_distintos,
        COUNT(DISTINCT mdfe_number) AS mdfes_distintos
    FROM dbo.manifestos
    GROUP BY sequence_code
    HAVING COUNT(*) > 1;

    IF @@ROWCOUNT > 0
    BEGIN
        PRINT '  [AVISO] Existem manifestos com multiplos picks/MDFes (correto).';
        PRINT '          Estes registros sao duplicados naturais e devem ser preservados.';
    END
    ELSE
    BEGIN
        PRINT '  [OK] Nenhum duplicado natural encontrado.';
    END
END
ELSE
BEGIN
    PRINT '  [AVISO] Constraint UQ_manifestos_sequence_identificador nao encontrada.';
    PRINT '          Tabela pode estar usando estrutura antiga.';
END

PRINT '';

-- ============================================================================
-- PASSO 2: Verificar Duplicados que Violariam Nova Constraint
-- ============================================================================

PRINT '[INFO] PASSO 2: Verificando duplicados que violariam nova constraint...';
PRINT '       (Mesma chave composta: sequence_code, pick_sequence_code, mdfe_number)';
PRINT '';

SELECT
    sequence_code,
    ISNULL(CAST(pick_sequence_code AS VARCHAR), 'NULL') AS pick_seq,
    ISNULL(CAST(mdfe_number AS VARCHAR), 'NULL') AS mdfe_num,
    COUNT(*) AS total_duplicados
FROM dbo.manifestos
GROUP BY sequence_code, pick_sequence_code, mdfe_number
HAVING COUNT(*) > 1;

IF @@ROWCOUNT > 0
BEGIN
    PRINT '';
    PRINT '  [ERRO] Existem duplicados que violariam a nova constraint.';
    PRINT '         Estes sao duplicados falsos e devem ser removidos antes da migracao.';
    PRINT '';
    PRINT '  [INFO] Acoes recomendadas:';
    PRINT '         1. Execute --validar-manifestos para identificar duplicados falsos';
    PRINT '         2. Delete registros duplicados manualmente';
    PRINT '         3. Execute este script novamente';
    PRINT '';
    PRINT '  [ERRO] MIGRACAO ABORTADA.';
    RAISERROR('Duplicados falsos encontrados. Corrija antes de continuar.', 16, 1);
    RETURN;
END
ELSE
BEGIN
    PRINT '  [OK] Nenhum duplicado falso encontrado. Seguro para migracao.';
END

PRINT '';

-- ============================================================================
-- PASSO 3: Remover Constraint Antiga (se existir)
-- ============================================================================

PRINT '[INFO] PASSO 3: Removendo constraint antiga...';

IF EXISTS (
    SELECT 1 FROM sys.key_constraints
    WHERE name = 'UQ_manifestos_sequence_identificador'
    AND parent_object_id = OBJECT_ID('dbo.manifestos')
)
BEGIN
    BEGIN TRY
        ALTER TABLE dbo.manifestos
        DROP CONSTRAINT UQ_manifestos_sequence_identificador;

        PRINT '  [OK] Constraint antiga removida com sucesso.';
    END TRY
    BEGIN CATCH
        PRINT '  [ERRO] Erro ao remover constraint antiga:';
        PRINT '         ' + ERROR_MESSAGE();
        THROW;
    END CATCH
END
ELSE
BEGIN
    PRINT '  [INFO] Constraint antiga nao existe (tabela ja migrada ou estrutura antiga).';
END

PRINT '';

-- ============================================================================
-- PASSO 4: Criar Nova Constraint (Chave Composta)
-- ============================================================================

PRINT '[INFO] PASSO 4: Criando nova constraint alinhada com MERGE...';
PRINT '       Chave: (sequence_code, pick_sequence_code, mdfe_number)';
PRINT '';

IF NOT EXISTS (
    SELECT 1 FROM sys.key_constraints
    WHERE name = 'UQ_manifestos_chave_composta'
    AND parent_object_id = OBJECT_ID('dbo.manifestos')
)
BEGIN
    BEGIN TRY
        IF NOT EXISTS (
            SELECT 1 FROM sys.columns
            WHERE name = 'chave_merge_hash'
            AND object_id = OBJECT_ID('dbo.manifestos')
        )
        BEGIN
            PRINT '  [INFO] Adicionando coluna computada chave_merge_hash...';

            ALTER TABLE dbo.manifestos
            ADD chave_merge_hash AS (
                CAST(sequence_code AS VARCHAR(20)) + '|' +
                ISNULL(CAST(pick_sequence_code AS VARCHAR(20)), '-1') + '|' +
                ISNULL(CAST(mdfe_number AS VARCHAR(20)), '-1')
            ) PERSISTED;

            PRINT '  [OK] Coluna computada criada.';
        END

        ALTER TABLE dbo.manifestos
        ADD CONSTRAINT UQ_manifestos_chave_composta
        UNIQUE (chave_merge_hash);

        PRINT '  [OK] Nova constraint criada com sucesso.';
        PRINT '       Nome: UQ_manifestos_chave_composta';
        PRINT '       Chave: chave_merge_hash (sequence_code|pick|mdfe)';

    END TRY
    BEGIN CATCH
        PRINT '  [ERRO] Erro ao criar nova constraint:';
        PRINT '         ' + ERROR_MESSAGE();
        THROW;
    END CATCH
END
ELSE
BEGIN
    PRINT '  [INFO] Nova constraint ja existe (tabela ja migrada).';
END

PRINT '';

-- ============================================================================
-- PASSO 5: Validacao Final
-- ============================================================================

PRINT '[INFO] PASSO 5: Validacao final...';
PRINT '';

IF EXISTS (
    SELECT 1 FROM sys.key_constraints
    WHERE name = 'UQ_manifestos_chave_composta'
    AND parent_object_id = OBJECT_ID('dbo.manifestos')
)
BEGIN
    PRINT '  [OK] Constraint UQ_manifestos_chave_composta confirmada.';

    DECLARE @total INT, @distintos INT;

    SELECT @total = COUNT(*) FROM dbo.manifestos;
    SELECT @distintos = COUNT(DISTINCT chave_merge_hash) FROM dbo.manifestos;

    PRINT '  [INFO] Estatisticas:';
    PRINT '         Total de registros: ' + CAST(@total AS VARCHAR);
    PRINT '         Chaves unicas: ' + CAST(@distintos AS VARCHAR);

    IF @total = @distintos
    BEGIN
        PRINT '  [OK] Integridade confirmada: nenhum duplicado.';
    END
    ELSE
    BEGIN
        PRINT '  [AVISO] Ainda existem ' + CAST(@total - @distintos AS VARCHAR) + ' duplicados.';
        PRINT '          Isso nao deveria acontecer. Execute validacao manual.';
    END
END
ELSE
BEGIN
    PRINT '  [ERRO] Constraint nao foi criada corretamente.';
    RAISERROR('Falha na validacao da constraint.', 16, 1);
    RETURN;
END

PRINT '';
RegistrarMigracao:

IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE migration_id = @MigrationId)
BEGIN
    INSERT INTO dbo.schema_migrations (migration_id, notes)
    VALUES (@MigrationId, N'Alinhamento da constraint UNIQUE de manifestos com chave de MERGE.');
END

PRINT '';
PRINT '[OK] Migracao concluida com sucesso.';
PRINT '';
PRINT '[INFO] Proximos passos:';
PRINT '       1. Execute --validar-manifestos para verificar integridade';
PRINT '       2. Execute uma extracao de teste';
PRINT '       3. Monitore logs para garantir que MERGE esta funcionando';
PRINT '       4. Considere REBUILD da tabela para otimizar fragmentacao';
PRINT '';

GO
