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
        sequence_code BIGINT,
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
        vehicle_type_id BIGINT,

        cancellation_reason NVARCHAR(MAX),
        cancellation_user_id BIGINT,
        destroy_reason NVARCHAR(MAX),
        destroy_user_id BIGINT,
        status_updated_at NVARCHAR(50),
        taxed_weight DECIMAL(18, 3), -- Peso Taxado (node.taxedWeight)
        pick_region NVARCHAR(255), -- Região da Coleta (node.pickAddress.city.name + state.code)
        last_occurrence NVARCHAR(50), -- Última Ocorrência (tradução do status)
        acao_ocorrencia NVARCHAR(255), -- Ação da Ocorrência (lógica De-Para)
        numero_tentativas INT, -- Nº Tentativas (lógica De-Para)

        -- Coluna de Metadados para Resiliência e Completude
        metadata NVARCHAR(MAX),

        -- Coluna de Auditoria
        data_extracao DATETIME2 DEFAULT GETDATE(),
        
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
