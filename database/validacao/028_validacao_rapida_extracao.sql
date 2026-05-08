-- ============================================
-- Script de Validação Rápida: Verificar Dados Extraídos
-- Executa validações rápidas para confirmar que os dados foram extraídos corretamente
-- ============================================

PRINT '╔══════════════════════════════════════════════════════════════════════════════╗';
PRINT '║                    📊 VALIDAÇÃO RÁPIDA DE EXTRAÇÃO                           ║';
PRINT '╚══════════════════════════════════════════════════════════════════════════════╝';
PRINT '';

-- 1. Contagem geral por entidade (últimas 24 horas)
PRINT '1. CONTAGEM DE REGISTROS POR ENTIDADE (Últimas 24 horas):';
PRINT '';

SELECT
    'usuarios_sistema' AS entidade,
    COUNT(*) AS total_registros,
    MAX(data_atualizacao) AS ultima_extracao
FROM dbo.dim_usuarios
WHERE data_atualizacao >= DATEADD(hour, -24, GETDATE())

UNION ALL

SELECT
    'coletas' AS entidade,
    COUNT(*) AS total_registros,
    MAX(data_extracao) AS ultima_extracao
FROM dbo.coletas
WHERE data_extracao >= DATEADD(hour, -24, GETDATE())

UNION ALL

SELECT
    'fretes' AS entidade,
    COUNT(*) AS total_registros,
    MAX(data_extracao) AS ultima_extracao
FROM dbo.fretes
WHERE data_extracao >= DATEADD(hour, -24, GETDATE())

UNION ALL

SELECT
    'faturas_graphql' AS entidade,
    COUNT(*) AS total_registros,
    MAX(data_extracao) AS ultima_extracao
FROM dbo.faturas_graphql
WHERE data_extracao >= DATEADD(hour, -24, GETDATE())

UNION ALL

SELECT
    'manifestos' AS entidade,
    COUNT(*) AS total_registros,
    MAX(data_extracao) AS ultima_extracao
FROM dbo.manifestos
WHERE data_extracao >= DATEADD(hour, -24, GETDATE())

UNION ALL

SELECT
    'cotacoes' AS entidade,
    COUNT(*) AS total_registros,
    MAX(data_extracao) AS ultima_extracao
FROM dbo.cotacoes
WHERE data_extracao >= DATEADD(hour, -24, GETDATE())

UNION ALL

SELECT
    'localizacao_cargas' AS entidade,
    COUNT(*) AS total_registros,
    MAX(data_extracao) AS ultima_extracao
FROM dbo.localizacao_cargas
WHERE data_extracao >= DATEADD(hour, -24, GETDATE())

UNION ALL

SELECT
    'contas_a_pagar' AS entidade,
    COUNT(*) AS total_registros,
    MAX(data_extracao) AS ultima_extracao
FROM dbo.contas_a_pagar
WHERE data_extracao >= DATEADD(hour, -24, GETDATE())

UNION ALL

SELECT
    'faturas_por_cliente' AS entidade,
    COUNT(*) AS total_registros,
    MAX(data_extracao) AS ultima_extracao
FROM dbo.faturas_por_cliente
WHERE data_extracao >= DATEADD(hour, -24, GETDATE())

UNION ALL

SELECT
    'inventario' AS entidade,
    COUNT(*) AS total_registros,
    MAX(data_extracao) AS ultima_extracao
FROM dbo.inventario
WHERE data_extracao >= DATEADD(hour, -24, GETDATE())

UNION ALL

SELECT
    'sinistros' AS entidade,
    COUNT(*) AS total_registros,
    MAX(data_extracao) AS ultima_extracao
FROM dbo.sinistros
WHERE data_extracao >= DATEADD(hour, -24, GETDATE())

UNION ALL

SELECT
    'raster_viagens' AS entidade,
    COUNT(*) AS total_registros,
    MAX(data_extracao) AS ultima_extracao
FROM dbo.raster_viagens
WHERE data_extracao >= DATEADD(hour, -24, GETDATE())

UNION ALL

SELECT
    'raster_viagem_paradas' AS entidade,
    COUNT(*) AS total_registros,
    MAX(data_extracao) AS ultima_extracao
FROM dbo.raster_viagem_paradas
WHERE data_extracao >= DATEADD(hour, -24, GETDATE())

ORDER BY entidade;

PRINT '';
PRINT '2. VERIFICANDO LOGS DE EXTRAÇÃO (Últimas 24 horas):';
PRINT '';

SELECT TOP 20
    entidade,
    status_final,
    registros_extraidos,
    paginas_processadas,
    timestamp_inicio,
    timestamp_fim,
    DATEDIFF(second, timestamp_inicio, timestamp_fim) AS duracao_segundos,
    mensagem
FROM dbo.log_extracoes
WHERE timestamp_inicio >= DATEADD(hour, -24, GETDATE())
ORDER BY timestamp_inicio DESC;

PRINT '';
PRINT '3. VERIFICANDO PAGE_AUDIT (Últimas 24 horas):';
PRINT '';

SELECT
    template_id,
    COUNT(DISTINCT execution_uuid) AS total_execucoes,
    COUNT(*) AS total_paginas,
    MIN(page) AS primeira_pagina,
    MAX(page) AS ultima_pagina,
    MAX(timestamp) AS ultima_auditoria
FROM dbo.page_audit
WHERE timestamp >= DATEADD(hour, -24, GETDATE())
GROUP BY template_id
ORDER BY template_id;

PRINT '';
PRINT '4. VERIFICANDO VIEWS POWERBI (Existência):';
PRINT '';

SELECT
    TABLE_NAME AS view_name,
    'EXISTE' AS status
FROM INFORMATION_SCHEMA.VIEWS
WHERE TABLE_SCHEMA = 'dbo'
  AND TABLE_NAME LIKE 'vw_%_powerbi'
ORDER BY TABLE_NAME;

PRINT '';
PRINT '5. VERIFICANDO VIEWS DIMENSÃO (Existência):';
PRINT '';

SELECT
    TABLE_NAME AS view_name,
    'EXISTE' AS status
FROM INFORMATION_SCHEMA.VIEWS
WHERE TABLE_SCHEMA = 'dbo'
  AND TABLE_NAME LIKE 'vw_dim_%'
ORDER BY TABLE_NAME;

PRINT '';
PRINT '6. VERIFICANDO DADOS RECENTES EM MANIFESTOS (Campos novos):';
PRINT '';

SELECT TOP 10
    sequence_code,
    vehicle_plate,
    vehicle_type,
    capacidade_kg,
    CASE
        WHEN obs_operacional IS NOT NULL THEN 'SIM'
        ELSE 'NULL'
    END AS tem_obs_operacional,
    CASE
        WHEN obs_financeira IS NOT NULL THEN 'SIM'
        ELSE 'NULL'
    END AS tem_obs_financeira,
    data_extracao
FROM dbo.manifestos
WHERE data_extracao >= DATEADD(hour, -24, GETDATE())
ORDER BY data_extracao DESC;

PRINT '';
PRINT '7. VERIFICANDO ENRIQUECIMENTO DE USUÁRIOS (dim_usuarios):';
PRINT '';

SELECT
    COUNT(*) AS total_usuarios,
    COUNT(DISTINCT user_id) AS usuarios_unicos,
    MAX(data_atualizacao) AS ultima_extracao
FROM dbo.dim_usuarios;

PRINT '';
PRINT '8. VERIFICANDO JOIN COM dim_usuarios EM COLETAS:';
PRINT '';

SELECT
    COUNT(*) AS total_coletas,
    COUNT(cancellation_user_id) AS coletas_com_cancellation_user_id,
    COUNT(destroy_user_id) AS coletas_com_destroy_user_id,
    COUNT(DISTINCT u_cancel.user_id) AS usuarios_cancelamento_encontrados,
    COUNT(DISTINCT u_destroy.user_id) AS usuarios_destruicao_encontrados
FROM dbo.coletas c
LEFT JOIN dbo.dim_usuarios u_cancel ON c.cancellation_user_id = u_cancel.user_id
LEFT JOIN dbo.dim_usuarios u_destroy ON c.destroy_user_id = u_destroy.user_id
WHERE c.data_extracao >= DATEADD(hour, -24, GETDATE());

PRINT '';
PRINT '╔══════════════════════════════════════════════════════════════════════════════╗';
PRINT '║                    ✅ VALIDAÇÃO RÁPIDA CONCLUÍDA                              ║';
PRINT '╚══════════════════════════════════════════════════════════════════════════════╝';
PRINT '';
