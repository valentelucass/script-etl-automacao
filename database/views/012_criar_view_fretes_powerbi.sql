-- ============================================
-- Script de criação da view 'vw_fretes_powerbi'
-- Execute este script UMA VEZ antes de colocar o sistema em produção
-- ============================================

CREATE OR ALTER VIEW dbo.vw_fretes_powerbi AS
SELECT

    CAST(servico_em AS TIME(0)) AS [Hora (Solicitacao)],

    id AS [ID],
    chave_cte AS [Chave CT-e],
    numero_cte AS [Nº CT-e],
    serie_cte AS [Série],
    cte_issued_at AS [CT-e Emissão],
    cte_emission_type AS [CT-e Tipo Emissão],
    cte_id AS [CT-e ID],
    cte_created_at AS [CT-e Criado em],
    CASE 
        WHEN cte_id IS NOT NULL OR chave_cte IS NOT NULL OR numero_cte IS NOT NULL OR serie_cte IS NOT NULL THEN 'CT-e'
        WHEN nfse_number IS NOT NULL OR nfse_series IS NOT NULL OR nfse_xml_document IS NOT NULL OR nfse_integration_id IS NOT NULL THEN 'NFS-e'
        ELSE 'Pendente/Não Emitido'
    END AS [Documento Oficial/Tipo],
    CASE 
        WHEN cte_id IS NOT NULL OR chave_cte IS NOT NULL THEN chave_cte
        ELSE NULL
    END AS [Documento Oficial/Chave],
    CASE 
        WHEN cte_id IS NOT NULL OR chave_cte IS NOT NULL THEN CONVERT(NVARCHAR(50), numero_cte)
        ELSE CONVERT(NVARCHAR(50), nfse_number)
    END AS [Documento Oficial/Número],
    CASE 
        WHEN cte_id IS NOT NULL OR chave_cte IS NOT NULL THEN CONVERT(NVARCHAR(50), serie_cte)
        ELSE nfse_series
    END AS [Documento Oficial/Série],
    CASE 
        WHEN cte_id IS NOT NULL OR chave_cte IS NOT NULL THEN NULL
        ELSE nfse_xml_document
    END AS [Documento Oficial/XML],
    servico_em AS [Data frete],
    criado_em AS [Criado em],
    valor_total AS [Valor Total do Serviço],
    valor_notas AS [Valor NF],
    peso_notas AS [Kg NF],
    subtotal AS [Valor Frete],
    invoices_total_volumes AS [Volumes],
    taxed_weight AS [Kg Taxado],
    real_weight AS [Kg Real],
    cubages_cubed_weight AS [Kg Cubado],
    total_cubic_volume AS [M3],
    pagador_nome AS [Pagador],
    pagador_documento AS [Pagador Doc],
    pagador_id AS [Pagador ID],
    remetente_nome AS [Remetente],
    remetente_documento AS [Remetente Doc],
    remetente_id AS [Remetente ID],
    origem_cidade AS [Origem],
    origem_uf AS [UF Origem],
    destinatario_nome AS [Destinatario],
    destinatario_documento AS [Destinatario Doc],
    destinatario_id AS [Destinatario ID],
    destino_cidade AS [Destino],
    destino_uf AS [UF Destino],
    filial_nome AS [Filial],
    filial_apelido AS [Filial Apelido],
    filial_cnpj AS [Filial CNPJ],
    tabela_preco_nome AS [Tabela de Preço],
    classificacao_nome AS [Classificação],
    centro_custo_nome AS [Centro de Custo],
    usuario_nome AS [Usuário],
    numero_nota_fiscal AS [NF],
    reference_number AS [Referência],
    id_corporacao AS [Corp ID],
    id_cidade_destino AS [Cidade Destino ID],
    data_previsao_entrega AS [Previsão de Entrega],
    modal AS [Modal],
    CASE status
        WHEN 'pending' THEN 'pendente'
        WHEN 'finished' THEN 'finalizado'
        WHEN 'in_transit' THEN 'em trânsito'
        WHEN 'standby' THEN 'aguardando'
        WHEN 'manifested' THEN 'registrado'
        WHEN 'occurrence_treatment' THEN 'tratamento de ocorrência'
        ELSE status
    END AS [Status],
    REPLACE(tipo_frete, 'Freight::', '') AS [Tipo Frete],
    service_type AS [Service Type],
    CASE WHEN insurance_enabled = 1 THEN 'Com seguro'
         WHEN insurance_enabled = 0 THEN 'Sem seguro'
         ELSE NULL
    END AS [Seguro Habilitado],
    gris_subtotal AS [GRIS],
    tde_subtotal AS [TDE],
    freight_weight_subtotal AS [Frete Peso],
    ad_valorem_subtotal AS [Ad Valorem],
    toll_subtotal AS [Pedágio],
    itr_subtotal AS [ITR],
    modal_cte AS [Modal CT-e],
    redispatch_subtotal AS [Redispatch],
    suframa_subtotal AS [SUFRAMA],
    CASE payment_type
        WHEN 'bill' THEN 'cobrança'
        WHEN 'cash' THEN 'dinheiro'
        ELSE payment_type
    END AS [Tipo Pagamento],
    CASE previous_document_type
        WHEN 'electronic' THEN 'eletrônico'
        ELSE previous_document_type
    END AS [Doc Anterior],
    products_value AS [Valor Produtos],
    trt_subtotal AS [TRT],
    fiscal_cst_type AS [ICMS CST],
    fiscal_cfop_code AS [CFOP],
    fiscal_tax_value AS [Valor ICMS],
    fiscal_pis_value AS [Valor PIS],
    fiscal_cofins_value AS [Valor COFINS],
    fiscal_calculation_basis AS [Base de Cálculo ICMS],
    fiscal_tax_rate AS [Alíquota ICMS %],
    fiscal_pis_rate AS [Alíquota PIS %],
    fiscal_cofins_rate AS [Alíquota COFINS %],
    CASE WHEN fiscal_has_difal = 1 THEN 'possui'
         WHEN fiscal_has_difal = 0 THEN 'não possui'
         ELSE NULL
    END AS [Possui DIFAL],
    fiscal_difal_origin AS [DIFAL Origem],
    fiscal_difal_destination AS [DIFAL Destino],
    nfse_series AS [Série NFS-e],
    nfse_number AS [Nº NFS-e],
    nfse_integration_id AS [NFS-e/ID Integração],
    nfse_status AS [NFS-e/Status],
    nfse_issued_at AS [NFS-e/Emissão],
    nfse_cancelation_reason AS [NFS-e/Cancelamento/Motivo],
    nfse_pdf_service_url AS [NFS-e/PDF],
    nfse_corporation_id AS [NFS-e/Filial ID],
    nfse_service_description AS [NFS-e/Serviço/Descrição],
    nfse_xml_document AS [NFS-e/XML],
    insurance_id AS [Seguro ID],
    other_fees AS [Outras Tarifas],
    km AS [KM],
    payment_accountable_type AS [Tipo Contábil Pagamento],
    insured_value AS [Valor Segurado],
    CASE WHEN globalized = 1 THEN 'verdadeiro'
         WHEN globalized = 0 THEN 'falso'
         ELSE NULL
    END AS [Globalizado],
    sec_cat_subtotal AS [SEC/CAT],
    CASE globalized_type
        WHEN 'none' THEN 'nenhum'
        ELSE globalized_type
    END AS [Tipo Globalizado],
    price_table_accountable_type AS [Tipo Contábil Tabela],
    insurance_accountable_type AS [Tipo Contábil Seguro],
    metadata AS [Metadata],
    data_extracao AS [Data de extracao]
FROM dbo.fretes;
GO

PRINT 'View vw_fretes_powerbi criada/atualizada com sucesso!';
GO
