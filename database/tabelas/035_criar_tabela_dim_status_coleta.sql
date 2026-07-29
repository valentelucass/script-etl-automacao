-- Catálogo canônico de status retornados pela ESL para Coletas.
-- A aplicação Java deve manter paridade com esta tabela por meio de testes.
IF OBJECT_ID(N'dbo.dim_status_coleta', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.dim_status_coleta (
        codigo_status NVARCHAR(50) NOT NULL,
        rotulo_powerbi NVARCHAR(100) NOT NULL,
        estado_terminal BIT NOT NULL,
        ativo BIT NOT NULL
            CONSTRAINT DF_dim_status_coleta_ativo DEFAULT (1),
        atualizado_em DATETIME2(0) NOT NULL
            CONSTRAINT DF_dim_status_coleta_atualizado_em DEFAULT (SYSUTCDATETIME()),
        CONSTRAINT PK_dim_status_coleta PRIMARY KEY (codigo_status)
    );
    PRINT 'Tabela dbo.dim_status_coleta criada com sucesso.';
END
GO

MERGE dbo.dim_status_coleta AS target
USING (VALUES
    (N'pending', N'Pendente', CAST(0 AS bit)),
    (N'treatment', N'Em tratativa', CAST(0 AS bit)),
    (N'manifested', N'Manifestada', CAST(0 AS bit)),
    (N'in_transit', N'Em trânsito', CAST(0 AS bit)),
    (N'draft', N'Rascunho', CAST(0 AS bit)),
    (N'finished', N'Finalizada', CAST(1 AS bit)),
    (N'done', N'Coletada', CAST(1 AS bit)),
    (N'canceled', N'Cancelada', CAST(1 AS bit)),
    (N'cancelled', N'Cancelada', CAST(1 AS bit))
) AS source (codigo_status, rotulo_powerbi, estado_terminal)
ON target.codigo_status = source.codigo_status
WHEN MATCHED THEN
    UPDATE SET
        rotulo_powerbi = source.rotulo_powerbi,
        estado_terminal = source.estado_terminal,
        ativo = 1,
        atualizado_em = SYSUTCDATETIME()
WHEN NOT MATCHED THEN
    INSERT (codigo_status, rotulo_powerbi, estado_terminal, ativo, atualizado_em)
    VALUES (source.codigo_status, source.rotulo_powerbi, source.estado_terminal, 1, SYSUTCDATETIME());
GO
