-- ============================================
-- Script de criação da tabela 'coletas'
-- Execute este script UMA VEZ antes de colocar o sistema em produção
-- ============================================

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.coletas') AND type in (N'U'))
BEGIN
    CREATE TABLE dbo.coletas (
        -- Coluna de Chave Primária (String, conforme API GraphQL)
        id NVARCHAR(50) PRIMARY KEY,

        -- Colunas Essenciais para Indexação e Relatórios
        sequence_code BIGINT NOT NULL,
        request_date DATE,
        request_hour NVARCHAR(8),
        service_date DATE,
        status NVARCHAR(50),
        total_value DECIMAL(18, 2),
        total_weight DECIMAL(18, 3),
        total_volumes INT,

        -- Campos Expandidos (22 campos do CSV)
        cliente_nome NVARCHAR(255),
        cliente_doc NVARCHAR(50),
        local_coleta NVARCHAR(500),
        numero_coleta NVARCHAR(50),
        complemento_coleta NVARCHAR(255),
        cidade_coleta NVARCHAR(255),
        bairro_coleta NVARCHAR(255),
        uf_coleta NVARCHAR(10),
        cep_coleta NVARCHAR(20),
        filial_id BIGINT,
        filial_nome NVARCHAR(255),
        usuario_nome NVARCHAR(255),
        finish_date DATE,
        manifest_item_pick_id BIGINT,
        pick_items_ids NVARCHAR(MAX),
        vehicle_type_id BIGINT,

        cancellation_reason NVARCHAR(MAX),
        cancellation_user_id BIGINT,
        destroy_reason NVARCHAR(MAX),
        destroy_user_id BIGINT,
        status_updated_at NVARCHAR(50),
        status_updated_at_em DATETIMEOFFSET(0) NULL,
        taxed_weight DECIMAL(18, 3), -- Peso Taxado (node.taxedWeight)
        pick_region NVARCHAR(255), -- Região da Coleta (node.pickAddress.city.name + state.code)
        last_occurrence NVARCHAR(50), -- Última Ocorrência (tradução do status)
        acao_ocorrencia NVARCHAR(255), -- Ação da Ocorrência (lógica De-Para)
        numero_tentativas INT, -- Nº Tentativas (lógica De-Para)

        -- Coluna de Metadados para Resiliência e Completude
        metadata NVARCHAR(MAX),

        -- Coluna de Auditoria
        data_extracao DATETIME2 DEFAULT GETDATE(),
        excluido_na_origem BIT NOT NULL CONSTRAINT DF_coletas_excluido_na_origem DEFAULT (0),
        data_exclusao_origem DATETIME2(0) NULL,
        ausente_na_origem_desde DATETIME2(0) NULL,
        confirmacoes_ausencia_origem SMALLINT NOT NULL
            CONSTRAINT DF_coletas_confirmacoes_ausencia_origem DEFAULT (0),
        ultima_reconciliacao_origem_em DATETIME2(0) NULL,
        reconciliacao_origem_run_id NVARCHAR(36) NULL,
        motivo_exclusao_origem NVARCHAR(255) NULL,
        
        -- Constraint para chave de negócio
        CONSTRAINT UQ_coletas_sequence_code UNIQUE (sequence_code)
    );
    
    PRINT 'Tabela coletas criada com sucesso!';
END
ELSE
BEGIN
    PRINT 'Tabela coletas já existe. Pulando criação.';
END
GO

IF COL_LENGTH(N'dbo.coletas', N'excluido_na_origem') IS NULL
BEGIN
    ALTER TABLE dbo.coletas
    ADD excluido_na_origem BIT NOT NULL
        CONSTRAINT DF_coletas_excluido_na_origem DEFAULT (0) WITH VALUES;
    PRINT 'Coluna coletas.excluido_na_origem adicionada em tabela existente.';
END
GO

IF COL_LENGTH(N'dbo.coletas', N'data_exclusao_origem') IS NULL
BEGIN
    ALTER TABLE dbo.coletas
    ADD data_exclusao_origem DATETIME2(0) NULL;
    PRINT 'Coluna coletas.data_exclusao_origem adicionada em tabela existente.';
END
GO

IF COL_LENGTH(N'dbo.coletas', N'status_updated_at_em') IS NULL
BEGIN
    ALTER TABLE dbo.coletas
    ADD status_updated_at_em DATETIMEOFFSET(0) NULL;
    PRINT 'Coluna coletas.status_updated_at_em adicionada em tabela existente.';
END
GO

IF COL_LENGTH(N'dbo.coletas', N'ausente_na_origem_desde') IS NULL
BEGIN
    ALTER TABLE dbo.coletas ADD ausente_na_origem_desde DATETIME2(0) NULL;
END
GO

IF COL_LENGTH(N'dbo.coletas', N'confirmacoes_ausencia_origem') IS NULL
BEGIN
    ALTER TABLE dbo.coletas
    ADD confirmacoes_ausencia_origem SMALLINT NOT NULL
        CONSTRAINT DF_coletas_confirmacoes_ausencia_origem DEFAULT (0) WITH VALUES;
END
GO

IF COL_LENGTH(N'dbo.coletas', N'ultima_reconciliacao_origem_em') IS NULL
BEGIN
    ALTER TABLE dbo.coletas ADD ultima_reconciliacao_origem_em DATETIME2(0) NULL;
END
GO

IF COL_LENGTH(N'dbo.coletas', N'reconciliacao_origem_run_id') IS NULL
BEGIN
    ALTER TABLE dbo.coletas ADD reconciliacao_origem_run_id NVARCHAR(36) NULL;
END
GO

IF COL_LENGTH(N'dbo.coletas', N'motivo_exclusao_origem') IS NULL
BEGIN
    ALTER TABLE dbo.coletas ADD motivo_exclusao_origem NVARCHAR(255) NULL;
END
GO

IF COL_LENGTH(N'dbo.coletas', N'pick_items_ids') IS NULL
BEGIN
    ALTER TABLE dbo.coletas
    ADD pick_items_ids NVARCHAR(MAX) NULL;
    PRINT 'Coluna coletas.pick_items_ids adicionada em tabela existente.';
END
GO
