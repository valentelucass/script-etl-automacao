SET ANSI_NULLS ON;
GO
SET QUOTED_IDENTIFIER ON;
GO

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.faturas_por_cliente') AND type in (N'U'))
BEGIN
    CREATE TABLE dbo.faturas_por_cliente (
        unique_id NVARCHAR(100) PRIMARY KEY,
        valor_frete DECIMAL(18,2),
        valor_fatura DECIMAL(18,2),
        third_party_ctes_value DECIMAL(18,2),
        numero_cte BIGINT,
        chave_cte NVARCHAR(100),
        numero_nfse BIGINT,
        serie_nfse NVARCHAR(50),
        status_cte NVARCHAR(255),
        status_cte_result NVARCHAR(MAX),
        data_emissao_cte DATETIMEOFFSET,
        numero_fatura NVARCHAR(50),
        data_emissao_fatura DATE,
        data_vencimento_fatura DATE,
        data_baixa_fatura DATE,
        fit_ant_ils_original_due_date DATE,
        fit_ant_document NVARCHAR(50),
        fit_ant_issue_date DATE,
        fit_ant_value DECIMAL(18,2),
        filial NVARCHAR(255),
        tipo_frete NVARCHAR(100),
        classificacao NVARCHAR(100),
        estado NVARCHAR(50),
        pagador_nome NVARCHAR(255),
        pagador_documento NVARCHAR(50),
        cliente_cnpj NVARCHAR(14),
        remetente_nome NVARCHAR(255),
        remetente_documento NVARCHAR(50),
        destinatario_nome NVARCHAR(255),
        destinatario_documento NVARCHAR(50),
        vendedor_nome NVARCHAR(255),
        notas_fiscais NVARCHAR(MAX),
        pedidos_cliente NVARCHAR(MAX),
        metadata NVARCHAR(MAX),
        data_extracao DATETIME2 DEFAULT GETDATE()
    );
    PRINT 'Tabela faturas_por_cliente criada com sucesso!';
END
ELSE
BEGIN
    PRINT 'Tabela faturas_por_cliente já existe. Pulando criação.';
END
GO

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_fpc_vencimento' AND object_id = OBJECT_ID('dbo.faturas_por_cliente'))
    CREATE INDEX IX_fpc_vencimento ON dbo.faturas_por_cliente(data_vencimento_fatura);
GO

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_fpc_pagador' AND object_id = OBJECT_ID('dbo.faturas_por_cliente'))
    CREATE INDEX IX_fpc_pagador ON dbo.faturas_por_cliente(pagador_nome);
GO

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_fpc_filial' AND object_id = OBJECT_ID('dbo.faturas_por_cliente'))
    CREATE INDEX IX_fpc_filial ON dbo.faturas_por_cliente(filial);
GO

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_fpc_chave_cte' AND object_id = OBJECT_ID('dbo.faturas_por_cliente'))
    CREATE INDEX IX_fpc_chave_cte ON dbo.faturas_por_cliente(chave_cte);
GO

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_fpc_cliente_cnpj' AND object_id = OBJECT_ID('dbo.faturas_por_cliente'))
    CREATE INDEX IX_fpc_cliente_cnpj
        ON dbo.faturas_por_cliente(cliente_cnpj)
        WHERE cliente_cnpj IS NOT NULL;
GO
