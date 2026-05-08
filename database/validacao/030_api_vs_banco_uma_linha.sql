-- ==============================================================================
-- UMA QUERY: API vs BANCO (última extração vs contagem no banco)
-- Compara registros_extraidos (log da última execução) com contagem nas tabelas.
-- Execute no SSMS ou: sqlcmd -S servidor -d banco -i 030_api_vs_banco_uma_linha.sql
--
-- Sobre "poucos fretes" (ex: 3): a extração GraphQL de fretes filtra por serviceAt
-- (data do serviço). Em modo "dados de hoje" só entram fretes com data de hoje;
-- é normal ter 0, 3 ou qualquer número conforme o volume do dia.
-- ==============================================================================
--
-- ONELINER (copie e cole em uma linha para verificação rápida):
-- WITH L AS (SELECT entidade, registros_extraidos, ROW_NUMBER() OVER (PARTITION BY entidade ORDER BY timestamp_fim DESC) rn FROM log_extracoes), B AS (SELECT 'usuarios_sistema' e, COUNT(*) c FROM dim_usuarios WHERE data_atualizacao >= DATEADD(HOUR,-24,GETDATE()) UNION ALL SELECT 'coletas', COUNT(*) FROM coletas WHERE data_extracao >= DATEADD(HOUR,-24,GETDATE()) UNION ALL SELECT 'fretes', COUNT(*) FROM fretes WHERE data_extracao >= DATEADD(HOUR,-24,GETDATE()) UNION ALL SELECT 'manifestos', COUNT(*) FROM manifestos WHERE data_extracao >= DATEADD(HOUR,-24,GETDATE()) UNION ALL SELECT 'cotacoes', COUNT(*) FROM cotacoes WHERE data_extracao >= DATEADD(HOUR,-24,GETDATE()) UNION ALL SELECT 'localizacao_cargas', COUNT(*) FROM localizacao_cargas WHERE data_extracao >= DATEADD(HOUR,-24,GETDATE()) UNION ALL SELECT 'contas_a_pagar', COUNT(*) FROM contas_a_pagar WHERE data_extracao >= DATEADD(HOUR,-24,GETDATE()) UNION ALL SELECT 'faturas_por_cliente', COUNT(*) FROM faturas_por_cliente WHERE data_extracao >= DATEADD(HOUR,-24,GETDATE()) UNION ALL SELECT 'faturas_graphql', COUNT(*) FROM faturas_graphql WHERE data_extracao >= DATEADD(HOUR,-24,GETDATE()) UNION ALL SELECT 'inventario', COUNT(*) FROM inventario WHERE data_extracao >= DATEADD(HOUR,-24,GETDATE()) UNION ALL SELECT 'sinistros', COUNT(*) FROM sinistros WHERE data_extracao >= DATEADD(HOUR,-24,GETDATE()) UNION ALL SELECT 'raster_viagens', COUNT(*) FROM raster_viagens WHERE data_extracao >= DATEADD(HOUR,-24,GETDATE()) UNION ALL SELECT 'raster_viagem_paradas', COUNT(*) FROM raster_viagem_paradas WHERE data_extracao >= DATEADD(HOUR,-24,GETDATE())) SELECT COALESCE(l.entidade,b.e) AS entidade, l.registros_extraidos AS api, b.c AS banco, COALESCE(l.registros_extraidos,0)-COALESCE(b.c,0) AS diff, CASE WHEN l.registros_extraidos IS NULL THEN 'SEM_LOG' WHEN b.c IS NULL THEN 'SEM_BANCO' WHEN l.registros_extraidos=b.c THEN 'OK' WHEN ABS(l.registros_extraidos-b.c)<=5 THEN 'DIF_PEQ' ELSE 'DIF_GRANDE' END AS status FROM (SELECT * FROM L WHERE rn=1) l FULL OUTER JOIN B ON l.entidade=b.e ORDER BY status, entidade;
--
-- Versão formatada (legível):
WITH LogUltima AS (
    SELECT entidade, registros_extraidos, timestamp_fim,
           ROW_NUMBER() OVER (PARTITION BY entidade ORDER BY timestamp_fim DESC) AS rn
    FROM log_extracoes
),
Banco AS (
    SELECT 'usuarios_sistema' AS e, COUNT(*) AS c
    FROM dim_usuarios
    WHERE data_atualizacao >= DATEADD(HOUR, -24, GETDATE())
    UNION ALL SELECT 'coletas', COUNT(*) FROM coletas WHERE data_extracao >= DATEADD(HOUR, -24, GETDATE())
    UNION ALL SELECT 'fretes', COUNT(*) FROM fretes WHERE data_extracao >= DATEADD(HOUR, -24, GETDATE())
    UNION ALL SELECT 'manifestos', COUNT(*) FROM manifestos WHERE data_extracao >= DATEADD(HOUR, -24, GETDATE())
    UNION ALL SELECT 'cotacoes', COUNT(*) FROM cotacoes WHERE data_extracao >= DATEADD(HOUR, -24, GETDATE())
    UNION ALL SELECT 'localizacao_cargas', COUNT(*) FROM localizacao_cargas WHERE data_extracao >= DATEADD(HOUR, -24, GETDATE())
    UNION ALL SELECT 'contas_a_pagar', COUNT(*) FROM contas_a_pagar WHERE data_extracao >= DATEADD(HOUR, -24, GETDATE())
    UNION ALL SELECT 'faturas_por_cliente', COUNT(*) FROM faturas_por_cliente WHERE data_extracao >= DATEADD(HOUR, -24, GETDATE())
    UNION ALL SELECT 'faturas_graphql', COUNT(*) FROM faturas_graphql WHERE data_extracao >= DATEADD(HOUR, -24, GETDATE())
    UNION ALL SELECT 'inventario', COUNT(*) FROM inventario WHERE data_extracao >= DATEADD(HOUR, -24, GETDATE())
    UNION ALL SELECT 'sinistros', COUNT(*) FROM sinistros WHERE data_extracao >= DATEADD(HOUR, -24, GETDATE())
    UNION ALL SELECT 'raster_viagens', COUNT(*) FROM raster_viagens WHERE data_extracao >= DATEADD(HOUR, -24, GETDATE())
    UNION ALL SELECT 'raster_viagem_paradas', COUNT(*) FROM raster_viagem_paradas WHERE data_extracao >= DATEADD(HOUR, -24, GETDATE())
)
SELECT
    COALESCE(l.entidade, b.e) AS entidade,
    l.registros_extraidos AS api,
    b.c AS banco,
    COALESCE(l.registros_extraidos, 0) - COALESCE(b.c, 0) AS diff,
    CASE
        WHEN l.registros_extraidos IS NULL THEN 'SEM_LOG'
        WHEN b.c IS NULL THEN 'SEM_BANCO'
        WHEN l.registros_extraidos = b.c THEN 'OK'
        WHEN ABS(l.registros_extraidos - b.c) <= 5 THEN 'DIF_PEQ'
        ELSE 'DIF_GRANDE'
    END AS status,
    CONVERT(VARCHAR(19), l.timestamp_fim, 120) AS ultima_extracao
FROM (SELECT * FROM LogUltima WHERE rn = 1) l
FULL OUTER JOIN Banco b ON l.entidade = b.e
ORDER BY
    CASE WHEN l.registros_extraidos IS NULL THEN 2 WHEN b.c IS NULL THEN 2 WHEN l.registros_extraidos = b.c THEN 0 ELSE 1 END,
    COALESCE(l.entidade, b.e);
