-- ============================================================================
-- CORRECAO CRITICA #7: Indices para Melhorar Performance
-- ============================================================================
-- Arquivo: 001_criar_indices_performance.sql
-- Descricao: Cria indices otimizados para queries de auditoria e busca
-- Data: 04/02/2026
-- Autor: Sistema de Auditoria
-- ============================================================================


PRINT 'Iniciando criacao de indices de performance...';

-- ============================================================================
-- MANIFESTOS - Indices para otimizar queries de auditoria e busca
-- ============================================================================

PRINT 'Criando indices para tabela MANIFESTOS...';

-- Indice para queries de auditoria por data_extracao (usado em validacoes)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_manifestos_data_extracao' AND object_id = OBJECT_ID('dbo.manifestos'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_manifestos_data_extracao
    ON dbo.manifestos(data_extracao DESC)
    INCLUDE (sequence_code, status);

    PRINT '  Indice IX_manifestos_data_extracao criado';
END
ELSE
    PRINT '    Indice IX_manifestos_data_extracao ja existe';

-- Indice composto para busca por sequence_code + data
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_manifestos_busca_sequence' AND object_id = OBJECT_ID('dbo.manifestos'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_manifestos_busca_sequence
    ON dbo.manifestos(sequence_code, data_extracao DESC)
    INCLUDE (mdfe_number, pick_sequence_code);

    PRINT '  Indice IX_manifestos_busca_sequence criado';
END
ELSE
    PRINT '    Indice IX_manifestos_busca_sequence ja existe';

-- Indice para busca por created_at (campo de data do negocio)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_manifestos_created_at' AND object_id = OBJECT_ID('dbo.manifestos'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_manifestos_created_at
    ON dbo.manifestos(created_at DESC)
    INCLUDE (sequence_code, status, branch_nickname);

    PRINT '  Indice IX_manifestos_created_at criado';
END
ELSE
    PRINT '    Indice IX_manifestos_created_at ja existe';

-- ============================================================================
-- COTACOES - Indices para otimizar queries
-- ============================================================================

PRINT 'Criando indices para tabela COTACOES...';

-- Indice para queries de auditoria por data_extracao
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_cotacoes_data_extracao' AND object_id = OBJECT_ID('dbo.cotacoes'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_cotacoes_data_extracao
    ON dbo.cotacoes(data_extracao DESC)
    INCLUDE (sequence_code, customer_name, total_value);

    PRINT '  Indice IX_cotacoes_data_extracao criado';
END
ELSE
    PRINT '    Indice IX_cotacoes_data_extracao ja existe';

-- Indice para busca por requested_at (data do negocio)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_cotacoes_requested_at' AND object_id = OBJECT_ID('dbo.cotacoes'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_cotacoes_requested_at
    ON dbo.cotacoes(requested_at DESC)
    INCLUDE (sequence_code, customer_name, branch_nickname);

    PRINT '  Indice IX_cotacoes_requested_at criado';
END
ELSE
    PRINT '    Indice IX_cotacoes_requested_at ja existe';

-- ============================================================================
-- CONTAS A PAGAR - Indices para otimizar queries
-- ============================================================================

PRINT 'Criando indices para tabela CONTAS_A_PAGAR...';

-- Indice para queries de auditoria por issue_date (IMPORTANTE!)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_contas_pagar_issue_date' AND object_id = OBJECT_ID('dbo.contas_a_pagar'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_contas_pagar_issue_date
    ON dbo.contas_a_pagar(issue_date DESC)
    INCLUDE (sequence_code, status_pagamento, valor_a_pagar, nome_fornecedor);

    PRINT '  Indice IX_contas_pagar_issue_date criado';
END
ELSE
    PRINT '    Indice IX_contas_pagar_issue_date ja existe';

-- Indice para busca por status_pagamento
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_contas_pagar_status' AND object_id = OBJECT_ID('dbo.contas_a_pagar'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_contas_pagar_status
    ON dbo.contas_a_pagar(status_pagamento, issue_date DESC);

    PRINT '  Indice IX_contas_pagar_status criado';
END
ELSE
    PRINT '    Indice IX_contas_pagar_status ja existe';

-- Indice para competencia (ano + mes)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_contas_pagar_competencia' AND object_id = OBJECT_ID('dbo.contas_a_pagar'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_contas_pagar_competencia
    ON dbo.contas_a_pagar(ano_competencia DESC, mes_competencia DESC)
    INCLUDE (valor_a_pagar, status_pagamento);

    PRINT '  Indice IX_contas_pagar_competencia criado';
END
ELSE
    PRINT '    Indice IX_contas_pagar_competencia ja existe';

-- ============================================================================
-- COLETAS - Indices para otimizar queries
-- ============================================================================

PRINT 'Criando indices para tabela COLETAS...';

-- Indice para queries de auditoria por data_extracao
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_coletas_data_extracao' AND object_id = OBJECT_ID('dbo.coletas'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_coletas_data_extracao
    ON dbo.coletas(data_extracao DESC)
    INCLUDE (id, sequence_code, status, cliente_nome);

    PRINT '  Indice IX_coletas_data_extracao criado';
END
ELSE
    PRINT '    Indice IX_coletas_data_extracao ja existe';

-- Indice para busca por service_date
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_coletas_service_date' AND object_id = OBJECT_ID('dbo.coletas'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_coletas_service_date
    ON dbo.coletas(service_date DESC)
    INCLUDE (sequence_code, status, cliente_nome);

    PRINT '  Indice IX_coletas_service_date criado';
END
ELSE
    PRINT '    Indice IX_coletas_service_date ja existe';

-- ============================================================================
-- FRETES - Indices para otimizar queries
-- ============================================================================

PRINT 'Criando indices para tabela FRETES...';

-- Indice para queries de auditoria por data_extracao
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_fretes_data_extracao' AND object_id = OBJECT_ID('dbo.fretes'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_fretes_data_extracao
    ON dbo.fretes(data_extracao DESC)
    INCLUDE (id, servico_em, status, valor_total);

    PRINT '  Indice IX_fretes_data_extracao criado';
END
ELSE
    PRINT '    Indice IX_fretes_data_extracao ja existe';

-- Indice para busca por servico_em
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_fretes_servico_em' AND object_id = OBJECT_ID('dbo.fretes'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_fretes_servico_em
    ON dbo.fretes(servico_em DESC)
    INCLUDE (id, status, pagador_nome, valor_total);

    PRINT '  Indice IX_fretes_servico_em criado';
END
ELSE
    PRINT '    Indice IX_fretes_servico_em ja existe';

-- ============================================================================
-- LOCALIZACAO DE CARGAS - Indices para otimizar queries
-- ============================================================================

PRINT 'Criando indices para tabela LOCALIZACAO_CARGAS...';

-- Indice para queries de auditoria por data_extracao
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_localizacao_data_extracao' AND object_id = OBJECT_ID('dbo.localizacao_cargas'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_localizacao_data_extracao
    ON dbo.localizacao_cargas(data_extracao DESC)
    INCLUDE (sequence_number, service_at);

    PRINT '  Indice IX_localizacao_data_extracao criado';
END
ELSE
    PRINT '    Indice IX_localizacao_data_extracao ja existe';

-- Indice para busca por service_at
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_localizacao_service_at' AND object_id = OBJECT_ID('dbo.localizacao_cargas'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_localizacao_service_at
    ON dbo.localizacao_cargas(service_at DESC)
    INCLUDE (sequence_number);

    PRINT '  Indice IX_localizacao_service_at criado';
END
ELSE
    PRINT '    Indice IX_localizacao_service_at ja existe';

-- ============================================================================
-- LOG_EXTRACOES - Indices para otimizar queries de auditoria
-- ============================================================================

PRINT 'Criando indices para tabela LOG_EXTRACOES...';

-- Indice para busca por entidade + timestamp
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_log_extracoes_busca' AND object_id = OBJECT_ID('dbo.log_extracoes'))
BEGIN
    CREATE NONCLUSTERED INDEX IX_log_extracoes_busca
    ON dbo.log_extracoes(entidade, timestamp_fim DESC)
    INCLUDE (status_final, registros_extraidos, paginas_processadas);

    PRINT '  Indice IX_log_extracoes_busca criado';
END
ELSE
    PRINT '    Indice IX_log_extracoes_busca ja existe';

-- ============================================================================
-- ESTATISTICAS DOS INDICES
-- ============================================================================

PRINT '';
PRINT 'Estatisticas de Indices Criados:';

SELECT
    OBJECT_NAME(i.object_id) AS Tabela,
    i.name AS Nome_Indice,
    i.type_desc AS Tipo,
    CAST(ROUND((SUM(a.used_pages) * 8) / 1024.0, 2) AS DECIMAL(10,2)) AS Tamanho_MB
FROM sys.indexes i
INNER JOIN sys.partitions p ON i.object_id = p.object_id AND i.index_id = p.index_id
INNER JOIN sys.allocation_units a ON p.partition_id = a.container_id
WHERE i.name LIKE 'IX_%'
  AND OBJECT_NAME(i.object_id) IN ('manifestos', 'cotacoes', 'contas_a_pagar', 'coletas', 'fretes', 'localizacao_cargas', 'log_extracoes')
GROUP BY i.object_id, i.name, i.type_desc
ORDER BY Tabela, Nome_Indice;

PRINT '';
PRINT 'Script de criacao de indices concluido com sucesso!';
PRINT '';
PRINT 'RECOMENDACOES:';
PRINT '   1. Execute UPDATE STATISTICS apos carga massiva de dados';
PRINT '   2. Configure AUTO_UPDATE_STATISTICS = ON';
PRINT '   3. Monitore fragmentacao dos indices mensalmente';
PRINT '   4. Considere REBUILD se fragmentacao > 30%';

GO
