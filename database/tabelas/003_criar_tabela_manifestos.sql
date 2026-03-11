-- ============================================
-- Script de criação da tabela 'manifestos'
-- Execute este script UMA VEZ antes de colocar o sistema em produção
-- ============================================

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.manifestos') AND type in (N'U'))
BEGIN
    CREATE TABLE dbo.manifestos (
        -- Coluna de Chave Primária (Auto-incrementado)
        id BIGINT IDENTITY(1,1) PRIMARY KEY,

        -- Chave Composta (para identificar unicamente)
        sequence_code BIGINT NOT NULL,
        identificador_unico NVARCHAR(100) NOT NULL,

        -- Colunas Essenciais para Indexação e Relatórios
        status NVARCHAR(50),
        created_at DATETIMEOFFSET,
        departured_at DATETIMEOFFSET,
        closed_at DATETIMEOFFSET,
        finished_at DATETIMEOFFSET,
        mdfe_number INT,
        mdfe_key NVARCHAR(100),
        mdfe_status NVARCHAR(50),
        distribution_pole NVARCHAR(255),
        classification NVARCHAR(255),
        vehicle_plate NVARCHAR(10),
        vehicle_type NVARCHAR(255),
        vehicle_owner NVARCHAR(255),
        driver_name NVARCHAR(255),
        branch_nickname NVARCHAR(255),
        vehicle_departure_km INT,
        closing_km INT,
        traveled_km INT,
        invoices_count INT,
        invoices_volumes INT,
        invoices_weight DECIMAL(18, 3),
        total_taxed_weight DECIMAL(18, 3),
        total_cubic_volume DECIMAL(18, 6),
        invoices_value DECIMAL(18, 2),
        manifest_freights_total DECIMAL(18, 2),
        pick_sequence_code BIGINT,
        contract_number NVARCHAR(50),
        contract_type NVARCHAR(50),
        calculation_type NVARCHAR(50),
        cargo_type NVARCHAR(255),
        daily_subtotal DECIMAL(18, 2),
        total_cost DECIMAL(18, 2),
        freight_subtotal DECIMAL(18, 2),
        fuel_subtotal DECIMAL(18, 2),
        toll_subtotal DECIMAL(18, 2),
        driver_services_total DECIMAL(18, 2),
        operational_expenses_total DECIMAL(18, 2),
        inss_value DECIMAL(18, 2),
        sest_senat_value DECIMAL(18, 2),
        ir_value DECIMAL(18, 2),
        paying_total DECIMAL(18, 2),
        manual_km BIT,
        generate_mdfe BIT,
        monitoring_request BIT,
        uniq_destinations_count INT,
        mobile_read_at DATETIMEOFFSET,
        km DECIMAL(18, 2),
        delivery_manifest_items_count INT,
        transfer_manifest_items_count INT,
        pick_manifest_items_count INT,
        dispatch_draft_manifest_items_count INT,
        consolidation_manifest_items_count INT,
        reverse_pick_manifest_items_count INT,
        manifest_items_count INT,
        finalized_manifest_items_count INT,
        calculated_pick_count INT,
        calculated_delivery_count INT,
        calculated_dispatch_count INT,
        calculated_consolidation_count INT,
        calculated_reverse_pick_count INT,
        pick_subtotal DECIMAL(18, 2),
        delivery_subtotal DECIMAL(18, 2),
        dispatch_subtotal DECIMAL(18, 2),
        consolidation_subtotal DECIMAL(18, 2),
        reverse_pick_subtotal DECIMAL(18, 2),
        advance_subtotal DECIMAL(18, 2),
        fleet_costs_subtotal DECIMAL(18, 2),
        additionals_subtotal DECIMAL(18, 2),
        discounts_subtotal DECIMAL(18, 2),
        discount_value DECIMAL(18, 2),
        adjustment_comments NVARCHAR(MAX),
        contract_status NVARCHAR(50),
        iks_id NVARCHAR(100),
        programacao_sequence_code NVARCHAR(50),
        programacao_starting_at DATETIMEOFFSET,
        programacao_ending_at DATETIMEOFFSET,
        trailer1_license_plate NVARCHAR(10),
        trailer1_weight_capacity DECIMAL(18, 2),
        trailer2_license_plate NVARCHAR(10),
        trailer2_weight_capacity DECIMAL(18, 2),
        vehicle_weight_capacity DECIMAL(18, 2),
        vehicle_cubic_weight DECIMAL(18, 2),
        capacidade_kg DECIMAL(18, 2), -- Capacidade do veículo em kg (vehicle.weightCapacity)
        obs_operacional NVARCHAR(MAX), -- Comentários operacionais/liberação (operationalComments)
        obs_financeira NVARCHAR(MAX), -- Comentários financeiros/fechamento (closingComments)
        unloading_recipient_names NVARCHAR(MAX),
        delivery_region_names NVARCHAR(MAX),
        programacao_cliente NVARCHAR(255),
        programacao_tipo_servico NVARCHAR(255),
        creation_user_name NVARCHAR(255),
        adjustment_user_name NVARCHAR(255),

        -- Coluna de Metadados para Resiliência e Completude
        metadata NVARCHAR(MAX),

        -- Coluna computada para deduplicação (alinhada com lógica de MERGE)
        -- Permite múltiplos MDF-es e coletas para o mesmo sequence_code
        chave_merge_hash AS (
            CAST(sequence_code AS VARCHAR(20)) + '|' +
            ISNULL(CAST(pick_sequence_code AS VARCHAR(20)), '-1') + '|' +
            ISNULL(CAST(mdfe_number AS VARCHAR(20)), '-1')
        ) PERSISTED,

        -- Coluna de Auditoria
        data_extracao DATETIME2 DEFAULT GETDATE(),

        -- Constraint UNIQUE alinhada com a chave de MERGE
        CONSTRAINT UQ_manifestos_chave_composta UNIQUE (chave_merge_hash)
    );
    
    PRINT 'Tabela manifestos criada com sucesso!';
END
ELSE
BEGIN
    PRINT 'Tabela manifestos já existe. Pulando criação.';
END
GO

-- Criar índices (idempotente - só cria se não existir)
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_manifestos_sequence_code' AND object_id = OBJECT_ID('dbo.manifestos'))
BEGIN
    CREATE INDEX IX_manifestos_sequence_code ON dbo.manifestos(sequence_code);
    PRINT 'Índice IX_manifestos_sequence_code criado com sucesso!';
END
ELSE
BEGIN
    PRINT 'Índice IX_manifestos_sequence_code já existe. Pulando criação.';
END
GO
