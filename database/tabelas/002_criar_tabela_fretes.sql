-- ============================================
-- Script de criação da tabela 'fretes'
-- Execute este script UMA VEZ antes de colocar o sistema em produção
-- ============================================

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.fretes') AND type in (N'U'))
BEGIN
    CREATE TABLE dbo.fretes (
        -- Coluna de Chave Primária
        id BIGINT PRIMARY KEY,

        -- Colunas Essenciais "Promovidas" para Indexação e Relatórios
        servico_em DATETIMEOFFSET,
        criado_em DATETIMEOFFSET,
        status NVARCHAR(50),
        cortesia BIT,
        modal NVARCHAR(50),
        tipo_frete NVARCHAR(100),
        accounting_credit_id BIGINT,
        accounting_credit_installment_id BIGINT,
        valor_total DECIMAL(18, 2),
        valor_notas DECIMAL(18, 2),
        peso_notas DECIMAL(18, 3),
        id_corporacao BIGINT,
        id_cidade_destino BIGINT,
        data_previsao_entrega DATE,
        service_date DATE,
        finished_at DATETIMEOFFSET,
        fit_dpn_performance_finished_at DATETIMEOFFSET,
        corporation_sequence_number BIGINT,

        -- Campos Expandidos (22 campos do CSV)
        pagador_id BIGINT,
        pagador_nome NVARCHAR(255),
        remetente_id BIGINT,
        remetente_nome NVARCHAR(255),
        origem_cidade NVARCHAR(255),
        origem_uf NVARCHAR(10),
        destinatario_id BIGINT,
        destinatario_nome NVARCHAR(255),
        destino_cidade NVARCHAR(255),
        destino_uf NVARCHAR(10),
        filial_nome NVARCHAR(255),
        filial_apelido NVARCHAR(255),
        numero_nota_fiscal NVARCHAR(MAX),
        tabela_preco_nome NVARCHAR(255),
        classificacao_nome NVARCHAR(255),
        centro_custo_nome NVARCHAR(255),
        usuario_nome NVARCHAR(255),
        reference_number NVARCHAR(100),
        invoices_total_volumes INT,
        taxed_weight DECIMAL(18, 3),
        real_weight DECIMAL(18, 3),
        total_cubic_volume DECIMAL(18, 6),
        subtotal DECIMAL(18, 2),

        -- CT-e (chave, número, série)
        chave_cte NVARCHAR(100),
        numero_cte INT,
        serie_cte INT,
        cte_id BIGINT,
        cte_emission_type NVARCHAR(50),
        cte_created_at DATETIMEOFFSET,
        cte_issued_at DATETIMEOFFSET,

        service_type INT,
        insurance_enabled BIT,
        gris_subtotal DECIMAL(18, 2),
        tde_subtotal DECIMAL(18, 2),
        modal_cte NVARCHAR(50),
        redispatch_subtotal DECIMAL(18, 2),
        suframa_subtotal DECIMAL(18, 2),
        payment_type NVARCHAR(50),
        previous_document_type NVARCHAR(50),
        products_value DECIMAL(18, 2),
        trt_subtotal DECIMAL(18, 2),
        nfse_series NVARCHAR(50),
        nfse_number INT,
        insurance_id BIGINT,
        other_fees DECIMAL(18, 2),
        km DECIMAL(18, 2),
        payment_accountable_type INT,
        insured_value DECIMAL(18, 2),
        globalized BIT,
        sec_cat_subtotal DECIMAL(18, 2),
        globalized_type NVARCHAR(50),
        price_table_accountable_type INT,
        insurance_accountable_type INT,

        fiscal_calculation_basis DECIMAL(18, 2),
        fiscal_tax_rate DECIMAL(18, 6),
        fiscal_pis_rate DECIMAL(18, 6),
        fiscal_cofins_rate DECIMAL(18, 6),
        fiscal_has_difal BIT,
        fiscal_difal_origin DECIMAL(18, 2),
        fiscal_difal_destination DECIMAL(18, 2),

        fiscal_cst_type NVARCHAR(10),
        fiscal_cfop_code NVARCHAR(10),
        fiscal_tax_value DECIMAL(18, 2),
        fiscal_pis_value DECIMAL(18, 2),
        fiscal_cofins_value DECIMAL(18, 2),

        cubages_cubed_weight DECIMAL(18, 6),
        freight_weight_subtotal DECIMAL(18, 2),
        ad_valorem_subtotal DECIMAL(18, 2),
        toll_subtotal DECIMAL(18, 2),
        itr_subtotal DECIMAL(18, 2),

        pagador_documento NVARCHAR(50),
        remetente_documento NVARCHAR(50),
        destinatario_documento NVARCHAR(50),
        filial_cnpj NVARCHAR(50),

        nfse_integration_id NVARCHAR(50),
        nfse_status NVARCHAR(50),
        nfse_issued_at DATE,
        nfse_cancelation_reason NVARCHAR(255),
        nfse_pdf_service_url NVARCHAR(1000),
        nfse_corporation_id BIGINT,
        nfse_service_description NVARCHAR(500),
        nfse_xml_document NVARCHAR(MAX),

        -- Coluna de Metadados para Resiliência e Completude
        metadata NVARCHAR(MAX),

        -- Coluna de Auditoria
        data_extracao DATETIME2 DEFAULT GETDATE()
    );
    
    PRINT 'Tabela fretes criada com sucesso!';
END
ELSE
BEGIN
    PRINT 'Tabela fretes já existe. Pulando criação.';
END
GO
