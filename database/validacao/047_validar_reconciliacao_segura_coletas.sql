-- Validação de contrato da reconciliação histórica e exclusão lógica de Coletas.
SET NOCOUNT ON;

IF OBJECT_ID(N'dbo.vw_coletas_excluidas_origem', N'V') IS NULL
    THROW 51164, 'View de auditoria dbo.vw_coletas_excluidas_origem ausente.', 1;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID(N'dbo.coletas')
      AND name = N'IX_coletas_reconciliacao_ativas_request_date'
)
    THROW 51165, 'Índice de reconciliação de Coletas ausente.', 1;

IF EXISTS (
    SELECT 1
    FROM dbo.coletas
    WHERE excluido_na_origem = 1
      AND confirmacoes_ausencia_origem < 2
)
    THROW 51166, 'Há Coletas excluídas sem duas confirmações de ausência.', 1;

SELECT
    SUM(CASE WHEN excluido_na_origem = 0 AND confirmacoes_ausencia_origem > 0 THEN 1 ELSE 0 END) AS candidatas_aguardando_confirmacao,
    SUM(CASE WHEN excluido_na_origem = 1 THEN 1 ELSE 0 END) AS excluidas_confirmadas,
    SUM(CASE WHEN excluido_na_origem = 1 AND motivo_exclusao_origem = N'AUSENTE_EM_SNAPSHOT_GRAPHQL_COMPLETO' THEN 1 ELSE 0 END) AS excluidas_por_snapshot_graphql
FROM dbo.coletas;
GO
