-- ============================================
-- View dinamica Raster para Transit Time SM
-- ============================================

CREATE OR ALTER VIEW dbo.vw_raster_sm_transit_time AS
WITH base AS (
    SELECT
        v.cod_solicitacao,
        v.placa_veiculo,
        v.status_viagem,
        v.cnpj_cliente_orig,
        v.cnpj_cliente_dest,
        v.cod_ibge_cidade_orig,
        v.cod_ibge_cidade_dest,
        v.data_hora_prev_ini,
        v.data_hora_prev_fim,
        v.data_hora_real_ini,
        v.data_hora_real_fim,
        v.tempo_total_viagem_min,
        v.dentro_prazo_raster,
        v.percentual_atraso_raster,
        v.cod_rota,
        v.rota_descricao,
        v.link_timeline,
        p.ordem,
        p.tipo,
        p.cod_ibge_cidade AS parada_cod_ibge_cidade,
        p.cnpj_cliente AS parada_cnpj_cliente,
        p.data_hora_prev_chegada,
        p.data_hora_real_chegada,
        p.data_hora_real_saida,
        p.chegou_na_entrega
    FROM dbo.raster_viagens v
    LEFT JOIN dbo.raster_viagem_paradas p
        ON p.cod_solicitacao = v.cod_solicitacao
),
rota AS (
    SELECT
        b.*,
        NULLIF(LTRIM(RTRIM(REPLACE(REPLACE(b.rota_descricao, '/BRASIL', ''), ' ATE ', '|'))), '') AS rota_normalizada
    FROM base b
),
origem_destino AS (
    SELECT
        r.*,
        NULLIF(LTRIM(RTRIM(
            CASE
                WHEN CHARINDEX('|', r.rota_normalizada) > 0
                    THEN LEFT(r.rota_normalizada, CHARINDEX('|', r.rota_normalizada) - 1)
                ELSE NULL
            END
        )), '') AS origem_rota,
        NULLIF(LTRIM(RTRIM(
            CASE
                WHEN CHARINDEX('|', r.rota_normalizada) > 0
                    THEN SUBSTRING(r.rota_normalizada, CHARINDEX('|', r.rota_normalizada) + 1, 500)
                ELSE NULL
            END
        )), '') AS destino_rota
    FROM rota r
),
resolvido AS (
    SELECT
        od.*,
        COALESCE(
            od.origem_rota,
            NULLIF(od.cnpj_cliente_orig, ''),
            CONVERT(NVARCHAR(30), od.cod_ibge_cidade_orig)
        ) AS origem_sm,
        COALESCE(
            CONVERT(NVARCHAR(30), od.parada_cod_ibge_cidade),
            od.destino_rota,
            NULLIF(od.parada_cnpj_cliente, ''),
            NULLIF(od.cnpj_cliente_dest, ''),
            CONVERT(NVARCHAR(30), od.cod_ibge_cidade_dest)
        ) AS destino_sm
    FROM origem_destino od
),
transit AS (
    SELECT
        r.*,
        COALESCE(
            CASE
                WHEN r.tempo_total_viagem_min BETWEEN 0 AND 43200 THEN r.tempo_total_viagem_min
                ELSE NULL
            END,
            CASE
                WHEN r.data_hora_prev_ini IS NOT NULL
                     AND COALESCE(r.data_hora_prev_chegada, r.data_hora_prev_fim) IS NOT NULL
                     AND DATEDIFF(MINUTE, r.data_hora_prev_ini, COALESCE(r.data_hora_prev_chegada, r.data_hora_prev_fim))
                         BETWEEN 0 AND 43200
                    THEN DATEDIFF(MINUTE, r.data_hora_prev_ini, COALESCE(r.data_hora_prev_chegada, r.data_hora_prev_fim))
                ELSE NULL
            END
        ) AS transit_time_min
    FROM resolvido r
)
SELECT
    cod_solicitacao,
    placa_veiculo,
    status_viagem,
    origem_sm AS [ORIGEM - SM],
    destino_sm AS [DESTINO - SM],
    CONCAT(origem_sm, ' x ', destino_sm) AS [Origem x Destino],
    CASE
        WHEN CHARINDEX('/', origem_sm) > 0 THEN LTRIM(RTRIM(LEFT(origem_sm, CHARINDEX('/', origem_sm) - 1)))
        WHEN CHARINDEX('-', origem_sm) > 0 THEN LTRIM(RTRIM(LEFT(origem_sm, CHARINDEX('-', origem_sm) - 1)))
        ELSE origem_sm
    END AS [ORIGEM],
    CASE
        WHEN ordem IS NULL THEN NULL
        ELSE CONCAT(ordem, N'º')
    END AS [ORDEM],
    CASE
        WHEN CHARINDEX('/', destino_sm) > 0 THEN LTRIM(RTRIM(LEFT(destino_sm, CHARINDEX('/', destino_sm) - 1)))
        WHEN CHARINDEX('-', destino_sm) > 0 THEN LTRIM(RTRIM(LEFT(destino_sm, CHARINDEX('-', destino_sm) - 1)))
        ELSE destino_sm
    END AS [DESTINO],
    CASE
        WHEN data_hora_prev_ini IS NULL THEN NULL
        ELSE CONVERT(CHAR(5), CAST(data_hora_prev_ini AS TIME), 108)
    END AS [HORÁRIO CORTE],
    CASE
        WHEN COALESCE(data_hora_prev_chegada, data_hora_prev_fim) IS NULL THEN NULL
        ELSE CONVERT(CHAR(5), CAST(COALESCE(data_hora_prev_chegada, data_hora_prev_fim) AS TIME), 108)
    END AS [PREV. CHEGADA (destino)],
    CASE
        WHEN transit_time_min IS NULL THEN NULL
        ELSE CONCAT(
            CASE
                WHEN transit_time_min / 60 < 100
                    THEN RIGHT('00' + CONVERT(VARCHAR(2), transit_time_min / 60), 2)
                ELSE CONVERT(VARCHAR(10), transit_time_min / 60)
            END,
            ':',
            RIGHT('00' + CONVERT(VARCHAR(2), transit_time_min % 60), 2)
        )
    END AS [TRANSIT TIME],
    ordem AS ordem_parada,
    tipo AS tipo_parada,
    data_hora_prev_ini AS data_hora_prev_ini_raster,
    data_hora_prev_fim AS data_hora_prev_fim_raster,
    data_hora_prev_chegada AS data_hora_prev_chegada_parada,
    data_hora_real_ini,
    data_hora_real_fim,
    data_hora_real_chegada,
    data_hora_real_saida,
    dentro_prazo_raster,
    percentual_atraso_raster,
    cod_rota,
    rota_descricao,
    link_timeline
FROM transit;
GO

PRINT 'View vw_raster_sm_transit_time criada/atualizada com sucesso!';
GO
