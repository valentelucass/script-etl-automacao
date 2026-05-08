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
        v.data_extracao AS viagem_data_extracao,
        p.ordem,
        p.tipo,
        p.cod_ibge_cidade AS parada_cod_ibge_cidade,
        p.cnpj_cliente AS parada_cnpj_cliente,
        p.data_hora_prev_chegada,
        p.data_hora_real_chegada,
        p.data_hora_real_saida,
        p.chegou_na_entrega,
        p.data_extracao AS parada_data_extracao
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
),
apresentacao AS (
    SELECT
        t.*,
        CONCAT(t.origem_sm, ' x ', t.destino_sm) AS origem_destino,
        CASE
            WHEN CHARINDEX('/', t.origem_sm) > 0 THEN LTRIM(RTRIM(LEFT(t.origem_sm, CHARINDEX('/', t.origem_sm) - 1)))
            WHEN CHARINDEX('-', t.origem_sm) > 0 THEN LTRIM(RTRIM(LEFT(t.origem_sm, CHARINDEX('-', t.origem_sm) - 1)))
            ELSE t.origem_sm
        END AS origem_nome,
        CASE
            WHEN t.ordem IS NULL THEN NULL
            ELSE CONCAT(t.ordem, N'º')
        END AS ordem_parada_label,
        CASE
            WHEN CHARINDEX('/', t.destino_sm) > 0 THEN LTRIM(RTRIM(LEFT(t.destino_sm, CHARINDEX('/', t.destino_sm) - 1)))
            WHEN CHARINDEX('-', t.destino_sm) > 0 THEN LTRIM(RTRIM(LEFT(t.destino_sm, CHARINDEX('-', t.destino_sm) - 1)))
            ELSE t.destino_sm
        END AS destino_nome,
        CASE
            WHEN t.data_hora_prev_ini IS NULL THEN NULL
            ELSE CONVERT(CHAR(5), CAST(t.data_hora_prev_ini AS TIME), 108)
        END AS horario_corte_texto,
        CASE
            WHEN COALESCE(t.data_hora_prev_chegada, t.data_hora_prev_fim) IS NULL THEN NULL
            ELSE CONVERT(CHAR(5), CAST(COALESCE(t.data_hora_prev_chegada, t.data_hora_prev_fim) AS TIME), 108)
        END AS previsao_chegada_destino,
        CASE
            WHEN t.transit_time_min IS NULL THEN NULL
            ELSE CONCAT(
                CASE
                    WHEN t.transit_time_min / 60 < 100
                        THEN RIGHT('00' + CONVERT(VARCHAR(2), t.transit_time_min / 60), 2)
                    ELSE CONVERT(VARCHAR(10), t.transit_time_min / 60)
                END,
                ':',
                RIGHT('00' + CONVERT(VARCHAR(2), t.transit_time_min % 60), 2)
            )
        END AS transit_time_texto,
        CASE
            WHEN t.parada_data_extracao IS NULL THEN t.viagem_data_extracao
            WHEN t.viagem_data_extracao IS NULL THEN t.parada_data_extracao
            WHEN t.parada_data_extracao > t.viagem_data_extracao THEN t.parada_data_extracao
            ELSE t.viagem_data_extracao
        END AS data_extracao_raster
    FROM transit t
)
SELECT
    cod_solicitacao,
    placa_veiculo,
    status_viagem,
    origem_sm AS [ORIGEM - SM],
    destino_sm AS [DESTINO - SM],
    origem_destino AS [Origem x Destino],
    origem_nome AS [ORIGEM],
    ordem_parada_label AS [ORDEM],
    destino_nome AS [DESTINO],
    horario_corte_texto AS [HORÁRIO CORTE],
    previsao_chegada_destino AS [PREV. CHEGADA (destino)],
    transit_time_texto AS [TRANSIT TIME],
    origem_sm,
    destino_sm,
    origem_destino,
    origem_nome,
    ordem_parada_label,
    destino_nome,
    horario_corte_texto,
    previsao_chegada_destino,
    transit_time_texto,
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
    link_timeline,
    data_extracao_raster AS [Data de extracao],
    data_extracao_raster
FROM apresentacao;
GO

PRINT 'View vw_raster_sm_transit_time criada/atualizada com sucesso!';
GO
