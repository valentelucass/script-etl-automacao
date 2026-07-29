IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.coletas')
      AND name = N'IX_coletas_reconciliacao_ativas_request_date'
)
BEGIN
    CREATE INDEX IX_coletas_reconciliacao_ativas_request_date
        ON dbo.coletas (request_date, id)
        INCLUDE (confirmacoes_ausencia_origem)
        WHERE excluido_na_origem = 0;
END
GO
