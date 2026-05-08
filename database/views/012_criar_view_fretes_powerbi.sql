-- ============================================
-- Script de criação da view 'vw_fretes_powerbi'
-- Execute este script UMA VEZ antes de colocar o sistema em produção
-- ============================================

CREATE OR ALTER VIEW dbo.vw_fretes_powerbi AS
SELECT

    CAST(f.servico_em AS TIME(0)) AS [Hora (Solicitacao)],

    f.id AS [ID],
    f.corporation_sequence_number AS [Nº Minuta],
    f.chave_cte AS [Chave CT-e],
    f.numero_cte AS [Nº CT-e],
    f.serie_cte AS [Série],
    f.cte_issued_at AS [CT-e Emissão],
    f.cte_emission_type AS [CT-e Tipo Emissão],
    f.cte_id AS [CT-e ID],
    f.cte_created_at AS [CT-e Criado em],
    CASE 
        WHEN f.cte_id IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.chave_cte)), '') IS NOT NULL
          OR f.numero_cte IS NOT NULL
          OR f.serie_cte IS NOT NULL THEN 'CT-e'
        WHEN f.nfse_number IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_series)), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_xml_document)), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_integration_id)), '') IS NOT NULL THEN 'NFS-e'
        ELSE 'Pendente/Não Emitido'
    END AS [Documento Oficial/Tipo],
    CASE 
        WHEN f.cte_id IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.chave_cte)), '') IS NOT NULL THEN f.chave_cte
        ELSE NULL
    END AS [Documento Oficial/Chave],
    CASE 
        WHEN f.cte_id IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.chave_cte)), '') IS NOT NULL
          OR f.numero_cte IS NOT NULL
          OR f.serie_cte IS NOT NULL THEN CONVERT(NVARCHAR(50), f.numero_cte)
        WHEN f.nfse_number IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_series)), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_xml_document)), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_integration_id)), '') IS NOT NULL THEN CONVERT(NVARCHAR(50), f.nfse_number)
        ELSE NULL
    END AS [Documento Oficial/Número],
    CASE 
        WHEN f.cte_id IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.chave_cte)), '') IS NOT NULL
          OR f.numero_cte IS NOT NULL
          OR f.serie_cte IS NOT NULL THEN CONVERT(NVARCHAR(50), f.serie_cte)
        WHEN f.nfse_number IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_series)), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_xml_document)), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_integration_id)), '') IS NOT NULL THEN f.nfse_series
        ELSE NULL
    END AS [Documento Oficial/Série],
    CASE 
        WHEN f.cte_id IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.chave_cte)), '') IS NOT NULL
          OR f.numero_cte IS NOT NULL
          OR f.serie_cte IS NOT NULL THEN NULL
        WHEN f.nfse_number IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_series)), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_xml_document)), '') IS NOT NULL
          OR NULLIF(LTRIM(RTRIM(f.nfse_integration_id)), '') IS NOT NULL THEN f.nfse_xml_document
        ELSE NULL
    END AS [Documento Oficial/XML],
    f.servico_em AS [Data frete],
    f.criado_em AS [Criado em],
    f.valor_total AS [Valor Total do Serviço],
    f.valor_notas AS [Valor NF],
    f.peso_notas AS [Kg NF],
    f.subtotal AS [Valor Frete],
    f.invoices_total_volumes AS [Volumes],
    f.taxed_weight AS [Kg Taxado],
    f.taxed_weight AS [Peso Taxado],
    f.real_weight AS [Kg Real],
    f.real_weight AS [Peso Real],
    f.cubages_cubed_weight AS [Kg Cubado],
    f.cubages_cubed_weight AS [Peso Cubado],
    f.total_cubic_volume AS [M3],
    f.total_cubic_volume AS [Total M3],
    f.pagador_nome AS [Pagador],
    f.pagador_documento AS [Pagador Doc],
    f.pagador_id AS [Pagador ID],
    f.remetente_nome AS [Remetente],
    f.remetente_documento AS [Remetente Doc],
    f.remetente_id AS [Remetente ID],
    f.origem_cidade AS [Origem],
    f.origem_uf AS [UF Origem],
    f.destinatario_nome AS [Destinatario],
    f.destinatario_documento AS [Destinatario Doc],
    f.destinatario_id AS [Destinatario ID],
    f.destino_cidade AS [Destino],
    f.destino_uf AS [UF Destino],
    f.filial_nome AS [Filial],
    f.filial_nome AS [Filial Emissora],
    indicador_base.filial_responsavel_destino AS [Responsável pela Região de Destino],
    f.filial_apelido AS [Filial Apelido],
    f.filial_cnpj AS [Filial CNPJ],
    f.tabela_preco_nome AS [Tabela de Preço],
    f.classificacao_nome AS [Classificação],
    f.centro_custo_nome AS [Centro de Custo],
    f.usuario_nome AS [Usuário],
    f.numero_nota_fiscal AS [NF],
    f.reference_number AS [Referência],
    f.id_corporacao AS [Corp ID],
    f.id_cidade_destino AS [Cidade Destino ID],
    indicador_base.previsao_entrega_oficial AS [Previsão de Entrega],
    f.finished_at AS [Data de Finalização],
    indicador_base.finalizacao_performance_oficial AS [Finalização da Performance],
    indicador_perf.performance_diferenca_dias AS [Performance Diferença de Dias],
    CASE
        WHEN indicador_perf.performance_diferenca_dias IS NULL THEN NULL
        WHEN indicador_perf.performance_diferenca_dias <= 0 THEN 'NO PRAZO'
        ELSE 'FORA DO PRAZO'
    END AS [Performance Status],
    CASE
        WHEN indicador_perf.performance_diferenca_dias IS NULL THEN NULL
        WHEN indicador_perf.performance_diferenca_dias = 0 THEN 'NO PRAZO'
        WHEN indicador_perf.performance_diferenca_dias = 1 THEN '1 DIA DE ATRASO'
        WHEN indicador_perf.performance_diferenca_dias > 1 THEN CONCAT(indicador_perf.performance_diferenca_dias, ' DIAS DE ATRASO')
        WHEN indicador_perf.performance_diferenca_dias = -1 THEN '1 DIA ANTES'
        WHEN indicador_perf.performance_diferenca_dias = -2 THEN '2 DIAS ANTES'
        WHEN indicador_perf.performance_diferenca_dias = -3 THEN '3 DIAS ANTES'
        ELSE 'ACIMA DE 3 DIAS ANTES'
    END AS [Performance Status Dif de Dias],
    CASE
        WHEN indicador_perf.performance_diferenca_dias IS NULL THEN NULL
        WHEN indicador_perf.performance_diferenca_dias = 0 THEN 'NO PRAZO'
        WHEN indicador_perf.performance_diferenca_dias = 1 THEN '1 DIA DE ATRASO'
        WHEN indicador_perf.performance_diferenca_dias > 1 THEN CONCAT(indicador_perf.performance_diferenca_dias, ' DIAS DE ATRASO')
        WHEN indicador_perf.performance_diferenca_dias = -1 THEN '1 DIA ANTES'
        WHEN indicador_perf.performance_diferenca_dias = -2 THEN '2 DIAS ANTES'
        WHEN indicador_perf.performance_diferenca_dias = -3 THEN '3 DIAS ANTES'
        ELSE 'ACIMA DE 3 DIAS ANTES'
    END AS [Performance Status Dif de Dias Oficial],
    f.modal AS [Modal],
    CASE f.status
        WHEN 'pending' THEN 'pendente'
        WHEN 'finished' THEN 'finalizado'
        WHEN 'in_transit' THEN 'em trânsito'
        WHEN 'standby' THEN 'aguardando'
        WHEN 'manifested' THEN 'registrado'
        WHEN 'occurrence_treatment' THEN 'tratamento de ocorrência'
        ELSE f.status
    END AS [Status],
    CASE WHEN f.cortesia = 1 THEN 'Sim'
         WHEN f.cortesia = 0 THEN 'Não'
         ELSE NULL
    END AS [Cortesia],
    f.cortesia AS [Cortesia Flag],
    REPLACE(f.tipo_frete, 'Freight::', '') AS [Tipo Frete],
    f.service_type AS [Service Type],
    CASE WHEN f.insurance_enabled = 1 THEN 'Com seguro'
         WHEN f.insurance_enabled = 0 THEN 'Sem seguro'
         ELSE NULL
    END AS [Seguro Habilitado],
    f.gris_subtotal AS [GRIS],
    f.tde_subtotal AS [TDE],
    f.freight_weight_subtotal AS [Frete Peso],
    f.ad_valorem_subtotal AS [Ad Valorem],
    f.toll_subtotal AS [Pedágio],
    f.itr_subtotal AS [ITR],
    f.modal_cte AS [Modal CT-e],
    f.redispatch_subtotal AS [Redispatch],
    f.suframa_subtotal AS [SUFRAMA],
    CASE f.payment_type
        WHEN 'bill' THEN 'cobrança'
        WHEN 'cash' THEN 'dinheiro'
        ELSE f.payment_type
    END AS [Tipo Pagamento],
    CASE f.previous_document_type
        WHEN 'electronic' THEN 'eletrônico'
        ELSE f.previous_document_type
    END AS [Doc Anterior],
    f.products_value AS [Valor Produtos],
    f.trt_subtotal AS [TRT],
    f.fiscal_cst_type AS [ICMS CST],
    f.fiscal_cfop_code AS [CFOP],
    f.fiscal_tax_value AS [Valor ICMS],
    f.fiscal_pis_value AS [Valor PIS],
    f.fiscal_cofins_value AS [Valor COFINS],
    f.fiscal_calculation_basis AS [Base de Cálculo ICMS],
    f.fiscal_tax_rate AS [Alíquota ICMS %],
    f.fiscal_pis_rate AS [Alíquota PIS %],
    f.fiscal_cofins_rate AS [Alíquota COFINS %],
    CASE WHEN f.fiscal_has_difal = 1 THEN 'possui'
         WHEN f.fiscal_has_difal = 0 THEN 'não possui'
         ELSE NULL
    END AS [Possui DIFAL],
    f.fiscal_difal_origin AS [DIFAL Origem],
    f.fiscal_difal_destination AS [DIFAL Destino],
    f.nfse_series AS [Série NFS-e],
    f.nfse_number AS [Nº NFS-e],
    f.nfse_integration_id AS [NFS-e/ID Integração],
    f.nfse_status AS [NFS-e/Status],
    f.nfse_issued_at AS [NFS-e/Emissão],
    f.nfse_cancelation_reason AS [NFS-e/Cancelamento/Motivo],
    f.nfse_pdf_service_url AS [NFS-e/PDF],
    f.nfse_corporation_id AS [NFS-e/Filial ID],
    f.nfse_service_description AS [NFS-e/Serviço/Descrição],
    f.nfse_xml_document AS [NFS-e/XML],
    f.insurance_id AS [Seguro ID],
    f.other_fees AS [Outras Tarifas],
    f.km AS [KM],
    f.payment_accountable_type AS [Tipo Contábil Pagamento],
    f.insured_value AS [Valor Segurado],
    CASE WHEN f.globalized = 1 THEN 'verdadeiro'
         WHEN f.globalized = 0 THEN 'falso'
         ELSE NULL
    END AS [Globalizado],
    f.sec_cat_subtotal AS [SEC/CAT],
    CASE f.globalized_type
        WHEN 'none' THEN 'nenhum'
        ELSE f.globalized_type
    END AS [Tipo Globalizado],
    f.price_table_accountable_type AS [Tipo Contábil Tabela],
    f.insurance_accountable_type AS [Tipo Contábil Seguro],
    f.metadata AS [Metadata],
    f.data_extracao AS [Data de extracao]
FROM dbo.fretes AS f
LEFT JOIN dbo.localizacao_cargas AS lc
    ON lc.sequence_number = f.corporation_sequence_number
OUTER APPLY (
    SELECT
        COALESCE(CAST(f.data_previsao_entrega AS DATE), CAST(lc.predicted_delivery_at AS DATE)) AS previsao_entrega_oficial,
        COALESCE(f.fit_dpn_performance_finished_at, f.finished_at) AS finalizacao_performance_oficial,
        COALESCE(NULLIF(LTRIM(RTRIM(lc.destination_branch_nickname)), ''), f.filial_nome) AS filial_responsavel_destino
) AS indicador_base
OUTER APPLY (
    SELECT
        CASE
            WHEN indicador_base.previsao_entrega_oficial IS NULL
              OR indicador_base.finalizacao_performance_oficial IS NULL THEN NULL
            ELSE DATEDIFF(
                DAY,
                indicador_base.previsao_entrega_oficial,
                CAST(indicador_base.finalizacao_performance_oficial AS DATE)
            )
        END AS performance_diferenca_dias
) AS indicador_perf;
GO

PRINT 'View vw_fretes_powerbi criada/atualizada com sucesso!';
GO
