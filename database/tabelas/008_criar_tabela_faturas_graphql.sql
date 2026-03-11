IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.faturas_graphql') AND type in (N'U'))
BEGIN
    CREATE TABLE dbo.faturas_graphql (
        id BIGINT PRIMARY KEY,
        document NVARCHAR(50),
        issue_date DATE,
        due_date DATE,
        original_due_date DATE,
        value DECIMAL(18,2),
        paid_value DECIMAL(18,2),
        value_to_pay DECIMAL(18,2),
        discount_value DECIMAL(18,2),
        interest_value DECIMAL(18,2),
        paid BIT,
        status NVARCHAR(50),
        type NVARCHAR(50),
        comments NVARCHAR(MAX),
        sequence_code INT,
        competence_month INT,
        competence_year INT,
        created_at DATETIMEOFFSET,
        updated_at DATETIMEOFFSET,
        corporation_id BIGINT,
        corporation_name NVARCHAR(255),
        corporation_cnpj NVARCHAR(50),
        nfse_numero VARCHAR(50) NULL,
        carteira_banco VARCHAR(50) NULL,
        instrucao_boleto NVARCHAR(MAX) NULL,
        banco_nome VARCHAR(100) NULL,
        metodo_pagamento VARCHAR(50) NULL,
        metadata NVARCHAR(MAX),
        data_extracao DATETIME2 DEFAULT GETDATE()
    );
    PRINT 'Tabela faturas_graphql criada com sucesso!';
END
ELSE
BEGIN
    PRINT 'Tabela faturas_graphql já existe. Pulando criação.';
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.faturas_graphql') AND name = 'nfse_numero')
    BEGIN
        ALTER TABLE dbo.faturas_graphql ADD nfse_numero VARCHAR(50) NULL;
        PRINT 'Coluna nfse_numero adicionada.';
    END;
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.faturas_graphql') AND name = 'carteira_banco')
    BEGIN
        ALTER TABLE dbo.faturas_graphql ADD carteira_banco VARCHAR(50) NULL;
        PRINT 'Coluna carteira_banco adicionada.';
    END;
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.faturas_graphql') AND name = 'instrucao_boleto')
    BEGIN
        ALTER TABLE dbo.faturas_graphql ADD instrucao_boleto NVARCHAR(MAX) NULL;
        PRINT 'Coluna instrucao_boleto adicionada.';
    END;
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.faturas_graphql') AND name = 'banco_nome')
    BEGIN
        ALTER TABLE dbo.faturas_graphql ADD banco_nome VARCHAR(100) NULL;
        PRINT 'Coluna banco_nome adicionada.';
    END;
    IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID('dbo.faturas_graphql') AND name = 'metodo_pagamento')
    BEGIN
        ALTER TABLE dbo.faturas_graphql ADD metodo_pagamento VARCHAR(50) NULL;
        PRINT 'Coluna metodo_pagamento adicionada.';
    END;
END
GO

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_fg_document' AND object_id = OBJECT_ID('dbo.faturas_graphql'))
    CREATE INDEX IX_fg_document ON dbo.faturas_graphql(document);
GO

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_fg_due_date' AND object_id = OBJECT_ID('dbo.faturas_graphql'))
    CREATE INDEX IX_fg_due_date ON dbo.faturas_graphql(due_date);
GO

IF NOT EXISTS (SELECT * FROM sys.indexes WHERE name = 'IX_fg_corporation_id' AND object_id = OBJECT_ID('dbo.faturas_graphql'))
    CREATE INDEX IX_fg_corporation_id ON dbo.faturas_graphql(corporation_id);
GO
