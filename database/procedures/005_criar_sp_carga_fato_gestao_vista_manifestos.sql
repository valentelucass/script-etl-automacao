-- ============================================================================
-- Procedure de carga incremental da fato materializada de Manifestos
-- ============================================================================

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;
GO

CREATE OR ALTER PROCEDURE dbo.sp_carga_fato_gestao_vista_manifestos
    @DataInicio DATE = NULL,
    @DataFimExclusivo DATE = NULL,
    @MarcarAusentesComoExcluidos BIT = 0,
    @SnapshotEm DATETIME2(0) = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    IF OBJECT_ID(N'dbo.fato_gestao_vista_manifestos', N'U') IS NULL
        THROW 51070, 'Tabela dbo.fato_gestao_vista_manifestos nao encontrada. Execute a migration 042 antes da carga.', 1;

    IF OBJECT_ID(N'dbo.manifestos', N'U') IS NULL
        THROW 51071, 'Tabela dbo.manifestos nao encontrada. Carga da fato de manifestos abortada.', 1;

    IF OBJECT_ID(N'dbo.coletas', N'U') IS NULL
        THROW 51072, 'Tabela dbo.coletas nao encontrada. Carga da fato de manifestos abortada.', 1;

    IF OBJECT_ID(N'dbo.fretes', N'U') IS NULL
        THROW 51073, 'Tabela dbo.fretes nao encontrada. Carga da fato de manifestos abortada.', 1;

    SET @DataInicio = COALESCE(@DataInicio, CAST(DATEADD(DAY, -3, SYSUTCDATETIME()) AS DATE));
    SET @DataFimExclusivo = COALESCE(@DataFimExclusivo, CAST(DATEADD(DAY, 1, SYSUTCDATETIME()) AS DATE));

    IF @DataInicio >= @DataFimExclusivo
        THROW 51075, '@DataInicio deve ser menor que @DataFimExclusivo.', 1;

    DECLARE @cargaCompleta BIT = 0;
    SET @SnapshotEm = COALESCE(@SnapshotEm, SYSUTCDATETIME());

    DECLARE @lockResult INT;

    EXEC @lockResult = sys.sp_getapplock
        @Resource = N'dbo.sp_carga_fato_gestao_vista_manifestos',
        @LockMode = N'Exclusive',
        @LockOwner = N'Session',
        @LockTimeout = 0;

    IF @lockResult < 0
        THROW 51076, 'Carga da fato de manifestos ja esta em execucao por outra sessao.', 1;

    DECLARE @resultado TABLE (
        merge_action NVARCHAR(10) NOT NULL
    );

    BEGIN TRY
        ;WITH receita_coletas AS (
            SELECT
                c.sequence_code,
                SUM(COALESCE(f.valor_total, 0)) AS receita_coleta
            FROM dbo.coletas AS c
            CROSS APPLY (
                SELECT DISTINCT pick_items.pick_item_id
                FROM OPENJSON(CASE WHEN ISJSON(c.pick_items_ids) = 1 THEN c.pick_items_ids END)
                WITH (pick_item_id BIGINT '$') AS pick_items
                WHERE pick_items.pick_item_id IS NOT NULL
            ) AS coleta_item
            JOIN dbo.fretes AS f
                ON f.pick_item_id = coleta_item.pick_item_id
               AND COALESCE(f.excluido_na_origem, 0) = 0
            WHERE COALESCE(c.excluido_na_origem, 0) = 0
            GROUP BY c.sequence_code
        ),
        manifestos_linha AS (
            SELECT
                m.*,
                competencia_operacional.data_competencia_operacional,
                proprietario_documento.proprietario_documento,
                tipo_motorista.tipo_motorista
            FROM dbo.manifestos AS m
            CROSS APPLY (
                SELECT COALESCE(m.departured_at, m.created_at) AS data_competencia_operacional
            ) competencia_operacional
            OUTER APPLY OPENJSON(CASE WHEN ISJSON(m.metadata) = 1 THEN m.metadata END)
            WITH (
                mft_vie_onr_document NVARCHAR(255) '$.mft_vie_onr_document',
                mft_vie_onr_cnpj NVARCHAR(255) '$.mft_vie_onr_cnpj',
                vehicle_owner_document NVARCHAR(255) '$.vehicle_owner_document',
                owner_document NVARCHAR(255) '$.owner_document',
                mft_vie_owner_document NVARCHAR(255) '$.mft_vie_owner_document'
            ) metadata_proprietario
            OUTER APPLY (
                SELECT NULLIF(
                    REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                        COALESCE(
                            metadata_proprietario.mft_vie_onr_document,
                            metadata_proprietario.mft_vie_onr_cnpj,
                            metadata_proprietario.vehicle_owner_document,
                            metadata_proprietario.owner_document,
                            metadata_proprietario.mft_vie_owner_document
                        ),
                        '.', ''), '-', ''), '/', ''), ' ', ''), CHAR(9), ''
                    ),
                    ''
                ) AS proprietario_documento
            ) proprietario_documento
            LEFT JOIN dbo.manifestos_frota_propria_cnpjs AS cnpj
                ON cnpj.ativo = 1
               AND cnpj.cnpj = proprietario_documento.proprietario_documento
            OUTER APPLY (
                SELECT CASE
                    WHEN cnpj.cnpj IS NOT NULL
                      OR UPPER(LTRIM(RTRIM(COALESCE(m.vehicle_owner, '')))) COLLATE Latin1_General_CI_AI = N'RODOGARCIA TRANSPORTES RODOVIARIOS LTDA'
                    THEN N'Frota Própria'
                    WHEN LOWER(LTRIM(RTRIM(COALESCE(m.contract_type, '')))) COLLATE Latin1_General_CI_AI IN (
                        N'aggregate',
                        N'aggregated',
                        N'agregado',
                        N'prestador agregado',
                        N'exclusive',
                        N'exclusivo'
                    )
                      OR LOWER(LTRIM(RTRIM(COALESCE(m.contract_type, '')))) COLLATE Latin1_General_CI_AI LIKE N'%agreg%'
                      OR LOWER(LTRIM(RTRIM(COALESCE(m.contract_type, '')))) COLLATE Latin1_General_CI_AI LIKE N'%exclus%'
                    THEN N'Agregado'
                    ELSE N'Terceiro / Autônomo'
                END AS tipo_motorista
            ) tipo_motorista
            WHERE COALESCE(m.excluido_na_origem, 0) = 0
              AND (m.vehicle_plate IS NULL OR m.vehicle_plate <> N'ACM0000')
              AND (
                    @cargaCompleta = 1
                 OR (
                        m.departured_at >= @DataInicio
                    AND m.departured_at < @DataFimExclusivo
                 )
                 OR (
                        m.departured_at IS NULL
                    AND m.created_at >= @DataInicio
                    AND m.created_at < @DataFimExclusivo
                 )
              )
        ),
        manifestos_expurgo AS (
            SELECT DISTINCT
                m.sequence_code
            FROM dbo.manifestos AS m
            WHERE @cargaCompleta = 1
               OR (
                        m.departured_at >= @DataInicio
                    AND m.departured_at < @DataFimExclusivo
                  )
               OR (
                        m.departured_at IS NULL
                    AND m.created_at >= @DataInicio
                    AND m.created_at < @DataFimExclusivo
                  )
        ),
        agregados AS (
            SELECT
                ml.sequence_code,
                ml.sequence_code AS numero_manifesto,
                MAX(ml.identificador_unico) AS identificador_unico,
                MAX(ml.data_competencia_operacional) AS data_criacao,
                CAST(MAX(ml.data_competencia_operacional) AS DATE) AS data_criacao_date,
                CASE
                    WHEN MAX(ml.data_competencia_operacional) IS NULL THEN NULL
                    ELSE YEAR(CAST(MAX(ml.data_competencia_operacional) AS DATE)) * 100 + MONTH(CAST(MAX(ml.data_competencia_operacional) AS DATE))
                END AS data_criacao_yyyymm,
                CAST(MAX(ml.created_at) AS TIME(0)) AS hora_solicitacao,
                CAST(MAX(ml.created_at) AS TIME(0)) AS hora_criacao,
                MAX(ml.departured_at) AS saida,
                MAX(ml.closed_at) AS fechamento,
                MAX(ml.finished_at) AS chegada,
                CASE
                    WHEN MAX(CASE WHEN LOWER(LTRIM(RTRIM(ml.status))) = N'closed' THEN 1 ELSE 0 END) = 1
                        THEN N'closed'
                    WHEN MAX(CASE WHEN LOWER(LTRIM(RTRIM(ml.status))) = N'in_transit' THEN 1 ELSE 0 END) = 1
                        THEN N'in_transit'
                    WHEN MAX(CASE WHEN LOWER(LTRIM(RTRIM(ml.status))) = N'pending' THEN 1 ELSE 0 END) = 1
                        THEN N'pending'
                    ELSE MAX(ml.status)
                END AS status_raw,
                MAX(ml.classification) AS classificacao,
                MAX(ml.branch_nickname) AS filial,
                MAX(ml.branch_nickname) AS filial_emissora,
                NULLIF(LOWER(LTRIM(RTRIM(MAX(ml.branch_nickname)))), N'') AS filial_key,
                MAX(ml.mdfe_number) AS mdfe_number,
                MAX(ml.mdfe_key) AS mdfe_key,
                MAX(ml.mdfe_status) AS mdfe_status_raw,
                MAX(ml.distribution_pole) AS distribution_pole,
                MAX(ml.vehicle_plate) AS placa_veiculo,
                NULLIF(LOWER(LTRIM(RTRIM(MAX(ml.vehicle_plate)))), N'') AS placa_veiculo_key,
                MAX(ml.vehicle_type) AS tipo_veiculo,
                MAX(ml.vehicle_owner) AS proprietario_nome,
                MAX(ml.proprietario_documento) AS proprietario_documento,
                MAX(ml.tipo_motorista) AS tipo_motorista,
                NULLIF(LOWER(LTRIM(RTRIM(MAX(ml.tipo_motorista)))), N'') AS tipo_motorista_key,
                MAX(ml.driver_name) AS motorista,
                NULLIF(LOWER(LTRIM(RTRIM(MAX(ml.driver_name)))), N'') AS motorista_key,
                MAX(ml.vehicle_departure_km) AS km_saida,
                MAX(ml.closing_km) AS km_chegada,
                MAX(ml.traveled_km) AS km_viagem,
                CASE MAX(CAST(ml.manual_km AS INT))
                    WHEN 1 THEN N'é manual'
                    WHEN 0 THEN N'não é manual'
                    ELSE NULL
                END AS km_manual,
                MAX(ml.km) AS km_total,
                MAX(ml.invoices_count) AS qtd_nf,
                MAX(ml.invoices_volumes) AS volumes_nf,
                MAX(ml.invoices_weight) AS peso_nf,
                MAX(ml.total_taxed_weight) AS peso_taxado,
                MAX(ml.total_cubic_volume) AS total_m3,
                MAX(ml.invoices_value) AS valor_nf,
                MAX(ml.manifest_freights_total) AS fretes_total,
                SUM(COALESCE(rc.receita_coleta, 0)) AS coletas_total,
                MAX(COALESCE(ml.manifest_freights_total, 0)) + SUM(COALESCE(rc.receita_coleta, 0)) AS receita_total,
                STRING_AGG(CONVERT(NVARCHAR(MAX), ml.pick_sequence_code), N', ') AS coleta_numeros,
                MAX(ml.contract_number) AS contract_number,
                MAX(ml.contract_type) AS contract_type_vehicle_raw,
                MAX(ml.driver_contract_type) AS contract_type_driver_raw,
                MAX(ml.calculation_type) AS calculation_type_raw,
                MAX(ml.cargo_type) AS cargo_type_raw,
                MAX(ml.daily_subtotal) AS diaria,
                MAX(ml.total_cost) AS custo_total,
                MAX(ml.freight_subtotal) AS valor_frete,
                MAX(ml.fuel_subtotal) AS combustivel,
                MAX(ml.toll_subtotal) AS pedagio,
                MAX(ml.driver_services_total) AS servicos_motorista_total,
                MAX(ml.operational_expenses_total) AS despesa_operacional,
                MAX(ml.inss_value) AS inss_value,
                MAX(ml.sest_senat_value) AS sest_senat_value,
                MAX(ml.ir_value) AS ir_value,
                MAX(ml.paying_total) AS saldo_pagar,
                MAX(ml.uniq_destinations_count) AS uniq_destinations_count,
                CAST(MAX(CAST(ml.generate_mdfe AS INT)) AS BIT) AS gerar_mdfe,
                CAST(MAX(CAST(ml.monitoring_request AS INT)) AS BIT) AS solicitou_monitoramento,
                CASE MAX(CAST(ml.monitoring_request AS INT))
                    WHEN 1 THEN N'sim'
                    WHEN 0 THEN N'não'
                    ELSE NULL
                END AS solicitacao_monitoramento,
                MAX(ml.mobile_read_at) AS leitura_movel_em,
                MAX(ml.delivery_manifest_items_count) AS itens_entrega,
                MAX(ml.transfer_manifest_items_count) AS itens_transferencia,
                MAX(ml.pick_manifest_items_count) AS itens_coleta,
                MAX(ml.dispatch_draft_manifest_items_count) AS itens_despacho_rascunho,
                MAX(ml.consolidation_manifest_items_count) AS itens_consolidacao,
                MAX(ml.reverse_pick_manifest_items_count) AS itens_coleta_reversa,
                MAX(ml.manifest_items_count) AS itens_total,
                MAX(ml.finalized_manifest_items_count) AS itens_finalizados,
                MAX(ml.calculated_pick_count) AS calculado_coleta,
                MAX(ml.calculated_delivery_count) AS calculado_entrega,
                MAX(ml.calculated_dispatch_count) AS calculado_despacho,
                MAX(ml.calculated_consolidation_count) AS calculado_consolidacao,
                MAX(ml.calculated_reverse_pick_count) AS calculado_coleta_reversa,
                MAX(ml.pick_subtotal) AS valor_coletas,
                MAX(ml.delivery_subtotal) AS valor_entregas,
                MAX(ml.dispatch_subtotal) AS despachos,
                MAX(ml.consolidation_subtotal) AS consolidacoes,
                MAX(ml.reverse_pick_subtotal) AS coleta_reversa,
                MAX(ml.advance_subtotal) AS adiantamento,
                MAX(ml.fleet_costs_subtotal) AS custos_frota,
                MAX(ml.additionals_subtotal) AS adicionais,
                MAX(ml.discounts_subtotal) AS descontos,
                MAX(ml.discount_value) AS desconto_valor,
                MAX(ml.adjustment_comments) AS ajuste_comentarios,
                MAX(ml.iks_id) AS iks_id,
                MAX(ml.programacao_sequence_code) AS programacao_sequence_code,
                MAX(ml.programacao_starting_at) AS programacao_inicio,
                MAX(ml.programacao_ending_at) AS programacao_termino,
                MAX(ml.trailer1_license_plate) AS carreta1_placa,
                MAX(ml.trailer1_weight_capacity) AS carreta1_capacidade_peso,
                MAX(ml.trailer2_license_plate) AS carreta2_placa,
                MAX(ml.trailer2_weight_capacity) AS carreta2_capacidade_peso,
                MAX(ml.vehicle_weight_capacity) AS veiculo_capacidade_peso,
                MAX(ml.vehicle_cubic_weight) AS veiculo_peso_cubado,
                (
                    COALESCE(MAX(ml.vehicle_weight_capacity), 0)
                    + COALESCE(MAX(ml.trailer1_weight_capacity), 0)
                    + COALESCE(MAX(ml.trailer2_weight_capacity), 0)
                ) AS capacidade_lotacao_kg,
                MAX(REPLACE(REPLACE(REPLACE(ml.unloading_recipient_names, '[', ''), ']', ''), '"', '')) AS descarregamento_destinatarios,
                MAX(REPLACE(REPLACE(REPLACE(ml.unloading_recipient_names, '[', ''), ']', ''), '"', '')) AS local_descarregamento,
                MAX(REPLACE(REPLACE(REPLACE(ml.delivery_region_names, '[', ''), ']', ''), '"', '')) AS entrega_regioes,
                MAX(ml.programacao_cliente) AS programacao_cliente,
                MAX(ml.programacao_tipo_servico) AS programacao_tipo_servico,
                MAX(ml.creation_user_name) AS usuario_emissor,
                MAX(ml.adjustment_user_name) AS usuario_ajuste,
                MAX(ml.obs_operacional) AS obs_operacional,
                MAX(ml.obs_financeira) AS obs_financeira,
                MAX(ml.metadata) AS metadata,
                MAX(CAST(ml.data_extracao AS DATETIME2(0))) AS data_extracao
            FROM manifestos_linha AS ml
            LEFT JOIN receita_coletas AS rc
                ON rc.sequence_code = ml.pick_sequence_code
            GROUP BY ml.sequence_code
        ),
        origem AS (
            SELECT
                a.sequence_code,
                a.numero_manifesto,
                a.identificador_unico,
                a.data_criacao,
                a.data_criacao_date,
                a.data_criacao_yyyymm,
                a.hora_solicitacao,
                a.hora_criacao,
                a.saida,
                a.fechamento,
                a.chegada,
                status_traduzido.status,
                a.status_raw,
                NULLIF(LOWER(LTRIM(RTRIM(status_traduzido.status))), N'') AS status_key,
                a.classificacao,
                CASE
                    WHEN LOWER(LTRIM(RTRIM(COALESCE(a.classificacao, N'')))) COLLATE Latin1_General_CI_AI LIKE N'%distribu%' THEN N'distribuicao'
                    WHEN LOWER(LTRIM(RTRIM(COALESCE(a.classificacao, N'')))) COLLATE Latin1_General_CI_AI LIKE N'%transfer%' THEN N'transferencia'
                    WHEN LOWER(LTRIM(RTRIM(COALESCE(a.classificacao, N'')))) COLLATE Latin1_General_CI_AI LIKE N'%carga%fechada%'
                      OR LOWER(LTRIM(RTRIM(COALESCE(tipo_carga.tipo_carga, N'')))) COLLATE Latin1_General_CI_AI LIKE N'%carga%fechada%'
                    THEN N'cargaFechada'
                    ELSE N'distribuicao'
                END AS classificacao_bucket,
                a.filial,
                a.filial_emissora,
                a.filial_key,
                a.mdfe_number,
                a.mdfe_key,
                mdfe_status.mdfe_status,
                a.distribution_pole,
                a.placa_veiculo,
                a.placa_veiculo_key,
                a.tipo_veiculo,
                a.proprietario_nome,
                a.proprietario_documento,
                a.tipo_motorista,
                a.tipo_motorista_key,
                a.motorista,
                a.motorista_key,
                a.km_saida,
                a.km_chegada,
                a.km_viagem,
                a.km_manual,
                a.km_total,
                a.qtd_nf,
                a.volumes_nf,
                a.peso_nf,
                a.peso_taxado,
                a.total_m3,
                a.valor_nf,
                a.fretes_total,
                a.coletas_total,
                a.receita_total,
                a.coleta_numeros,
                a.contract_number,
                tipo_contrato_veiculo.tipo_contrato_veiculo,
                NULLIF(LOWER(LTRIM(RTRIM(tipo_contrato_veiculo.tipo_contrato_veiculo))), N'') AS tipo_contrato_veiculo_key,
                tipo_contrato_motorista.tipo_contrato_motorista,
                NULLIF(LOWER(LTRIM(RTRIM(tipo_contrato_motorista.tipo_contrato_motorista))), N'') AS tipo_contrato_motorista_key,
                tipo_contrato.tipo_contrato,
                NULLIF(LOWER(LTRIM(RTRIM(tipo_contrato.tipo_contrato))), N'') AS tipo_contrato_key,
                tipo_calculo.tipo_calculo,
                tipo_carga.tipo_carga,
                NULLIF(LOWER(LTRIM(RTRIM(tipo_carga.tipo_carga))), N'') AS tipo_carga_key,
                a.diaria,
                a.custo_total,
                a.valor_frete,
                a.combustivel,
                a.pedagio,
                a.servicos_motorista_total,
                a.despesa_operacional,
                a.inss_value,
                a.sest_senat_value,
                a.ir_value,
                a.saldo_pagar,
                a.uniq_destinations_count,
                a.gerar_mdfe,
                a.solicitou_monitoramento,
                a.solicitacao_monitoramento,
                a.leitura_movel_em,
                a.itens_entrega,
                a.itens_transferencia,
                a.itens_coleta,
                a.itens_despacho_rascunho,
                a.itens_consolidacao,
                a.itens_coleta_reversa,
                a.itens_total,
                a.itens_finalizados,
                a.calculado_coleta,
                a.calculado_entrega,
                a.calculado_despacho,
                a.calculado_consolidacao,
                a.calculado_coleta_reversa,
                a.valor_coletas,
                a.valor_entregas,
                a.despachos,
                a.consolidacoes,
                a.coleta_reversa,
                a.adiantamento,
                a.custos_frota,
                a.adicionais,
                a.descontos,
                a.desconto_valor,
                a.ajuste_comentarios,
                a.iks_id,
                a.programacao_sequence_code,
                a.programacao_inicio,
                a.programacao_termino,
                a.carreta1_placa,
                a.carreta1_capacidade_peso,
                a.carreta2_placa,
                a.carreta2_capacidade_peso,
                a.veiculo_capacidade_peso,
                a.veiculo_peso_cubado,
                a.capacidade_lotacao_kg,
                a.descarregamento_destinatarios,
                a.local_descarregamento,
                a.entrega_regioes,
                a.programacao_cliente,
                a.programacao_tipo_servico,
                a.usuario_emissor,
                a.usuario_ajuste,
                a.obs_operacional,
                a.obs_financeira,
                a.metadata,
                a.data_extracao,
                CAST(0 AS BIT) AS excluido_na_origem,
                CAST(NULL AS DATETIME2(0)) AS data_exclusao_origem,
                @SnapshotEm AS ultima_reconciliacao_origem_em,
                @SnapshotEm AS snapshot_em,
                CONVERT(BINARY(32), HASHBYTES('SHA2_256', CONCAT_WS(N'|',
                    CONVERT(NVARCHAR(30), a.sequence_code),
                    COALESCE(a.identificador_unico, N'__NULL__'),
                    COALESCE(CONVERT(NVARCHAR(48), a.data_criacao, 127), N'__NULL__'),
                    COALESCE(status_traduzido.status, N'__NULL__'),
                    COALESCE(a.classificacao, N'__NULL__'),
                    COALESCE(a.filial, N'__NULL__'),
                    COALESCE(a.placa_veiculo, N'__NULL__'),
                    COALESCE(a.tipo_veiculo, N'__NULL__'),
                    COALESCE(a.proprietario_nome, N'__NULL__'),
                    COALESCE(a.proprietario_documento, N'__NULL__'),
                    COALESCE(a.tipo_motorista, N'__NULL__'),
                    COALESCE(a.motorista, N'__NULL__'),
                    COALESCE(CONVERT(NVARCHAR(40), a.peso_taxado), N'__NULL__'),
                    COALESCE(CONVERT(NVARCHAR(40), a.total_m3), N'__NULL__'),
                    COALESCE(CONVERT(NVARCHAR(40), a.fretes_total), N'__NULL__'),
                    COALESCE(CONVERT(NVARCHAR(40), a.coletas_total), N'__NULL__'),
                    COALESCE(CONVERT(NVARCHAR(40), a.receita_total), N'__NULL__'),
                    COALESCE(tipo_contrato_veiculo.tipo_contrato_veiculo, N'__NULL__'),
                    COALESCE(tipo_contrato_motorista.tipo_contrato_motorista, N'__NULL__'),
                    COALESCE(tipo_contrato.tipo_contrato, N'__NULL__'),
                    COALESCE(CONVERT(NVARCHAR(40), a.custo_total), N'__NULL__'),
                    COALESCE(CONVERT(NVARCHAR(40), a.valor_frete), N'__NULL__'),
                    COALESCE(CONVERT(NVARCHAR(40), a.combustivel), N'__NULL__'),
                    COALESCE(CONVERT(NVARCHAR(40), a.pedagio), N'__NULL__'),
                    COALESCE(CONVERT(NVARCHAR(40), a.saldo_pagar), N'__NULL__'),
                    COALESCE(CONVERT(NVARCHAR(40), a.km_total), N'__NULL__'),
                    COALESCE(CONVERT(NVARCHAR(40), a.capacidade_lotacao_kg), N'__NULL__'),
                    COALESCE(CONVERT(NVARCHAR(20), a.itens_total), N'__NULL__'),
                    COALESCE(CONVERT(NVARCHAR(20), a.itens_finalizados), N'__NULL__'),
                    COALESCE(a.local_descarregamento, N'__NULL__'),
                    COALESCE(CONVERT(NVARCHAR(30), a.data_extracao, 126), N'__NULL__')
                ))) AS hash_linha
            FROM agregados AS a
            CROSS APPLY (
                SELECT CASE a.status_raw
                    WHEN N'closed' THEN N'encerrado'
                    WHEN N'in_transit' THEN N'em trânsito'
                    WHEN N'pending' THEN N'pendente'
                    ELSE a.status_raw
                END AS status
            ) status_traduzido
            CROSS APPLY (
                SELECT CASE a.mdfe_status_raw
                    WHEN N'pending' THEN N'pendente'
                    WHEN N'closed' THEN N'encerrado'
                    WHEN N'issued' THEN N'emitido'
                    WHEN N'rejected' THEN N'rejeitado'
                    ELSE a.mdfe_status_raw
                END AS mdfe_status
            ) mdfe_status
            CROSS APPLY (
                SELECT CASE a.contract_type_vehicle_raw
                    WHEN N'aggregate' THEN N'Agregado'
                    WHEN N'driver' THEN N'Motorista'
                    ELSE a.contract_type_vehicle_raw
                END AS tipo_contrato_veiculo
            ) tipo_contrato_veiculo
            CROSS APPLY (
                SELECT CASE a.contract_type_driver_raw
                    WHEN N'company' THEN N'Próprio'
                    WHEN N'aggregate' THEN N'Agregado'
                    WHEN N'third_party' THEN N'Terceiro'
                    ELSE a.contract_type_driver_raw
                END AS tipo_contrato_motorista
            ) tipo_contrato_motorista
            CROSS APPLY (
                SELECT CASE
                    WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Agregado'
                     AND a.proprietario_nome COLLATE Latin1_General_CI_AI
                         LIKE N'%LM TRANSPORTES INTERESTADUAIS SERVICOS E COMERCIO S.A%'
                        THEN N'Frota + PX'
                    WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Agregado'
                     AND (
                            tipo_contrato_motorista.tipo_contrato_motorista IS NULL
                         OR LTRIM(RTRIM(tipo_contrato_motorista.tipo_contrato_motorista)) = N''
                         )
                        THEN N'Terceiro'
                    WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Motorista'
                     AND (
                            tipo_contrato_motorista.tipo_contrato_motorista IS NULL
                         OR LTRIM(RTRIM(tipo_contrato_motorista.tipo_contrato_motorista)) = N''
                         )
                        THEN N'Frota + PX'
                    WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Agregado'
                     AND tipo_contrato_motorista.tipo_contrato_motorista = N'Agregado'
                        THEN N'Agregado'
                    WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Agregado'
                     AND tipo_contrato_motorista.tipo_contrato_motorista = N'Terceiro'
                        THEN N'Terceiro'
                    WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Agregado'
                     AND tipo_contrato_motorista.tipo_contrato_motorista = N'Próprio'
                        THEN N'Frota'
                    WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Motorista'
                     AND tipo_contrato_motorista.tipo_contrato_motorista = N'Agregado'
                        THEN N'Frota + PX'
                    WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Motorista'
                     AND tipo_contrato_motorista.tipo_contrato_motorista = N'Terceiro'
                        THEN N'Frota + PX'
                    WHEN tipo_contrato_veiculo.tipo_contrato_veiculo = N'Motorista'
                     AND tipo_contrato_motorista.tipo_contrato_motorista = N'Próprio'
                        THEN N'Frota'
                    ELSE NULL
                END AS tipo_contrato
            ) tipo_contrato
            CROSS APPLY (
                SELECT CASE a.calculation_type_raw
                    WHEN N'price_table' THEN N'tabela de preço'
                    WHEN N'agreed' THEN N'acordado'
                    ELSE a.calculation_type_raw
                END AS tipo_calculo
            ) tipo_calculo
            CROSS APPLY (
                SELECT CASE a.cargo_type_raw
                    WHEN N'fractioned' THEN N'carga fracionada'
                    WHEN N'closed' THEN N'carga fechada'
                    ELSE a.cargo_type_raw
                END AS tipo_carga
            ) tipo_carga
        )
        MERGE dbo.fato_gestao_vista_manifestos WITH (HOLDLOCK) AS target
        USING origem AS source
           ON target.sequence_code = source.sequence_code
        WHEN MATCHED
         AND (
                target.hash_linha IS NULL
             OR source.hash_linha IS NULL
             OR target.hash_linha <> source.hash_linha
             OR target.excluido_na_origem <> source.excluido_na_origem
         )
            THEN UPDATE SET
                numero_manifesto = source.numero_manifesto,
                identificador_unico = source.identificador_unico,
                data_criacao = source.data_criacao,
                data_criacao_date = source.data_criacao_date,
                data_criacao_yyyymm = source.data_criacao_yyyymm,
                hora_solicitacao = source.hora_solicitacao,
                hora_criacao = source.hora_criacao,
                saida = source.saida,
                fechamento = source.fechamento,
                chegada = source.chegada,
                status = source.status,
                status_raw = source.status_raw,
                status_key = source.status_key,
                classificacao = source.classificacao,
                classificacao_bucket = source.classificacao_bucket,
                filial = source.filial,
                filial_emissora = source.filial_emissora,
                filial_key = source.filial_key,
                mdfe_number = source.mdfe_number,
                mdfe_key = source.mdfe_key,
                mdfe_status = source.mdfe_status,
                distribution_pole = source.distribution_pole,
                placa_veiculo = source.placa_veiculo,
                placa_veiculo_key = source.placa_veiculo_key,
                tipo_veiculo = source.tipo_veiculo,
                proprietario_nome = source.proprietario_nome,
                proprietario_documento = source.proprietario_documento,
                tipo_motorista = source.tipo_motorista,
                tipo_motorista_key = source.tipo_motorista_key,
                motorista = source.motorista,
                motorista_key = source.motorista_key,
                km_saida = source.km_saida,
                km_chegada = source.km_chegada,
                km_viagem = source.km_viagem,
                km_manual = source.km_manual,
                km_total = source.km_total,
                qtd_nf = source.qtd_nf,
                volumes_nf = source.volumes_nf,
                peso_nf = source.peso_nf,
                peso_taxado = source.peso_taxado,
                total_m3 = source.total_m3,
                valor_nf = source.valor_nf,
                fretes_total = source.fretes_total,
                coletas_total = source.coletas_total,
                receita_total = source.receita_total,
                coleta_numeros = source.coleta_numeros,
                contract_number = source.contract_number,
                tipo_contrato_veiculo = source.tipo_contrato_veiculo,
                tipo_contrato_veiculo_key = source.tipo_contrato_veiculo_key,
                tipo_contrato_motorista = source.tipo_contrato_motorista,
                tipo_contrato_motorista_key = source.tipo_contrato_motorista_key,
                tipo_contrato = source.tipo_contrato,
                tipo_contrato_key = source.tipo_contrato_key,
                tipo_calculo = source.tipo_calculo,
                tipo_carga = source.tipo_carga,
                tipo_carga_key = source.tipo_carga_key,
                diaria = source.diaria,
                custo_total = source.custo_total,
                valor_frete = source.valor_frete,
                combustivel = source.combustivel,
                pedagio = source.pedagio,
                servicos_motorista_total = source.servicos_motorista_total,
                despesa_operacional = source.despesa_operacional,
                inss_value = source.inss_value,
                sest_senat_value = source.sest_senat_value,
                ir_value = source.ir_value,
                saldo_pagar = source.saldo_pagar,
                uniq_destinations_count = source.uniq_destinations_count,
                gerar_mdfe = source.gerar_mdfe,
                solicitou_monitoramento = source.solicitou_monitoramento,
                solicitacao_monitoramento = source.solicitacao_monitoramento,
                leitura_movel_em = source.leitura_movel_em,
                itens_entrega = source.itens_entrega,
                itens_transferencia = source.itens_transferencia,
                itens_coleta = source.itens_coleta,
                itens_despacho_rascunho = source.itens_despacho_rascunho,
                itens_consolidacao = source.itens_consolidacao,
                itens_coleta_reversa = source.itens_coleta_reversa,
                itens_total = source.itens_total,
                itens_finalizados = source.itens_finalizados,
                calculado_coleta = source.calculado_coleta,
                calculado_entrega = source.calculado_entrega,
                calculado_despacho = source.calculado_despacho,
                calculado_consolidacao = source.calculado_consolidacao,
                calculado_coleta_reversa = source.calculado_coleta_reversa,
                valor_coletas = source.valor_coletas,
                valor_entregas = source.valor_entregas,
                despachos = source.despachos,
                consolidacoes = source.consolidacoes,
                coleta_reversa = source.coleta_reversa,
                adiantamento = source.adiantamento,
                custos_frota = source.custos_frota,
                adicionais = source.adicionais,
                descontos = source.descontos,
                desconto_valor = source.desconto_valor,
                ajuste_comentarios = source.ajuste_comentarios,
                iks_id = source.iks_id,
                programacao_sequence_code = source.programacao_sequence_code,
                programacao_inicio = source.programacao_inicio,
                programacao_termino = source.programacao_termino,
                carreta1_placa = source.carreta1_placa,
                carreta1_capacidade_peso = source.carreta1_capacidade_peso,
                carreta2_placa = source.carreta2_placa,
                carreta2_capacidade_peso = source.carreta2_capacidade_peso,
                veiculo_capacidade_peso = source.veiculo_capacidade_peso,
                veiculo_peso_cubado = source.veiculo_peso_cubado,
                capacidade_lotacao_kg = source.capacidade_lotacao_kg,
                descarregamento_destinatarios = source.descarregamento_destinatarios,
                local_descarregamento = source.local_descarregamento,
                entrega_regioes = source.entrega_regioes,
                programacao_cliente = source.programacao_cliente,
                programacao_tipo_servico = source.programacao_tipo_servico,
                usuario_emissor = source.usuario_emissor,
                usuario_ajuste = source.usuario_ajuste,
                obs_operacional = source.obs_operacional,
                obs_financeira = source.obs_financeira,
                metadata = source.metadata,
                data_extracao = source.data_extracao,
                excluido_na_origem = source.excluido_na_origem,
                data_exclusao_origem = source.data_exclusao_origem,
                ultima_reconciliacao_origem_em = source.ultima_reconciliacao_origem_em,
                snapshot_em = source.snapshot_em,
                hash_linha = source.hash_linha
        WHEN NOT MATCHED BY TARGET
            THEN INSERT (
                sequence_code, numero_manifesto, identificador_unico, data_criacao,
                data_criacao_date, data_criacao_yyyymm, hora_solicitacao, hora_criacao,
                saida, fechamento, chegada, status, status_raw, status_key, classificacao,
                classificacao_bucket, filial, filial_emissora, filial_key, mdfe_number,
                mdfe_key, mdfe_status, distribution_pole, placa_veiculo, placa_veiculo_key,
                tipo_veiculo, proprietario_nome, proprietario_documento, tipo_motorista,
                tipo_motorista_key, motorista, motorista_key, km_saida, km_chegada,
                km_viagem, km_manual, km_total, qtd_nf, volumes_nf, peso_nf, peso_taxado,
                total_m3, valor_nf, fretes_total, coletas_total, receita_total, coleta_numeros,
                contract_number, tipo_contrato_veiculo, tipo_contrato_veiculo_key,
                tipo_contrato_motorista, tipo_contrato_motorista_key, tipo_contrato,
                tipo_contrato_key, tipo_calculo, tipo_carga, tipo_carga_key, diaria,
                custo_total, valor_frete, combustivel, pedagio,
                servicos_motorista_total, despesa_operacional, inss_value, sest_senat_value,
                ir_value, saldo_pagar, uniq_destinations_count, gerar_mdfe, solicitou_monitoramento,
                solicitacao_monitoramento, leitura_movel_em, itens_entrega, itens_transferencia,
                itens_coleta, itens_despacho_rascunho, itens_consolidacao, itens_coleta_reversa,
                itens_total, itens_finalizados, calculado_coleta, calculado_entrega,
                calculado_despacho, calculado_consolidacao, calculado_coleta_reversa,
                valor_coletas, valor_entregas, despachos, consolidacoes, coleta_reversa,
                adiantamento, custos_frota, adicionais, descontos, desconto_valor,
                ajuste_comentarios, iks_id, programacao_sequence_code, programacao_inicio,
                programacao_termino, carreta1_placa, carreta1_capacidade_peso,
                carreta2_placa, carreta2_capacidade_peso, veiculo_capacidade_peso,
                veiculo_peso_cubado, capacidade_lotacao_kg, descarregamento_destinatarios,
                local_descarregamento, entrega_regioes, programacao_cliente,
                programacao_tipo_servico, usuario_emissor, usuario_ajuste, obs_operacional,
                obs_financeira, metadata, data_extracao, excluido_na_origem,
                data_exclusao_origem, ultima_reconciliacao_origem_em, snapshot_em, hash_linha
            )
            VALUES (
                source.sequence_code, source.numero_manifesto, source.identificador_unico, source.data_criacao,
                source.data_criacao_date, source.data_criacao_yyyymm, source.hora_solicitacao, source.hora_criacao,
                source.saida, source.fechamento, source.chegada, source.status, source.status_raw, source.status_key,
                source.classificacao, source.classificacao_bucket, source.filial, source.filial_emissora,
                source.filial_key, source.mdfe_number, source.mdfe_key, source.mdfe_status,
                source.distribution_pole, source.placa_veiculo, source.placa_veiculo_key,
                source.tipo_veiculo, source.proprietario_nome, source.proprietario_documento,
                source.tipo_motorista, source.tipo_motorista_key, source.motorista, source.motorista_key,
                source.km_saida, source.km_chegada, source.km_viagem, source.km_manual, source.km_total,
                source.qtd_nf, source.volumes_nf, source.peso_nf, source.peso_taxado, source.total_m3,
                source.valor_nf, source.fretes_total, source.coletas_total, source.receita_total,
                source.coleta_numeros, source.contract_number, source.tipo_contrato_veiculo,
                source.tipo_contrato_veiculo_key, source.tipo_contrato_motorista,
                source.tipo_contrato_motorista_key, source.tipo_contrato,
                source.tipo_contrato_key, source.tipo_calculo, source.tipo_carga, source.tipo_carga_key,
                source.diaria, source.custo_total, source.valor_frete, source.combustivel,
                source.pedagio, source.servicos_motorista_total, source.despesa_operacional,
                source.inss_value, source.sest_senat_value, source.ir_value, source.saldo_pagar,
                source.uniq_destinations_count, source.gerar_mdfe, source.solicitou_monitoramento,
                source.solicitacao_monitoramento, source.leitura_movel_em, source.itens_entrega,
                source.itens_transferencia, source.itens_coleta, source.itens_despacho_rascunho,
                source.itens_consolidacao, source.itens_coleta_reversa, source.itens_total,
                source.itens_finalizados, source.calculado_coleta, source.calculado_entrega,
                source.calculado_despacho, source.calculado_consolidacao,
                source.calculado_coleta_reversa, source.valor_coletas, source.valor_entregas,
                source.despachos, source.consolidacoes, source.coleta_reversa, source.adiantamento,
                source.custos_frota, source.adicionais, source.descontos, source.desconto_valor,
                source.ajuste_comentarios, source.iks_id, source.programacao_sequence_code,
                source.programacao_inicio, source.programacao_termino, source.carreta1_placa,
                source.carreta1_capacidade_peso, source.carreta2_placa, source.carreta2_capacidade_peso,
                source.veiculo_capacidade_peso, source.veiculo_peso_cubado, source.capacidade_lotacao_kg,
                source.descarregamento_destinatarios, source.local_descarregamento, source.entrega_regioes,
                source.programacao_cliente, source.programacao_tipo_servico, source.usuario_emissor,
                source.usuario_ajuste, source.obs_operacional, source.obs_financeira, source.metadata,
                source.data_extracao, source.excluido_na_origem, source.data_exclusao_origem,
                source.ultima_reconciliacao_origem_em, source.snapshot_em, source.hash_linha
            )
        WHEN NOT MATCHED BY SOURCE
         AND @MarcarAusentesComoExcluidos = 1
         AND target.excluido_na_origem = 0
         AND (
                @cargaCompleta = 1
             OR EXISTS (
                    SELECT 1
                    FROM manifestos_expurgo AS me
                    WHERE me.sequence_code = target.sequence_code
             )
             OR (
                    NOT EXISTS (
                        SELECT 1
                        FROM dbo.manifestos AS m_fallback
                        WHERE m_fallback.sequence_code = target.sequence_code
                    )
                AND target.data_criacao_date >= @DataInicio
                AND target.data_criacao_date < @DataFimExclusivo
             )
         )
            THEN UPDATE SET
                excluido_na_origem = 1,
                data_exclusao_origem = COALESCE(target.data_exclusao_origem, @SnapshotEm),
                ultima_reconciliacao_origem_em = @SnapshotEm,
                snapshot_em = @SnapshotEm,
                hash_linha = NULL
        OUTPUT $action
        INTO @resultado (merge_action);

        EXEC sys.sp_releaseapplock
            @Resource = N'dbo.sp_carga_fato_gestao_vista_manifestos',
            @LockOwner = N'Session';

        SELECT
            SUM(CASE WHEN merge_action = N'INSERT' THEN 1 ELSE 0 END) AS linhas_inseridas,
            SUM(CASE WHEN merge_action = N'UPDATE' THEN 1 ELSE 0 END) AS linhas_atualizadas,
            @SnapshotEm AS snapshot_em
        FROM @resultado;
    END TRY
    BEGIN CATCH
        EXEC sys.sp_releaseapplock
            @Resource = N'dbo.sp_carga_fato_gestao_vista_manifestos',
            @LockOwner = N'Session';
        THROW;
    END CATCH;
END;
GO

PRINT 'Procedure dbo.sp_carga_fato_gestao_vista_manifestos criada/atualizada com sucesso.';
GO
