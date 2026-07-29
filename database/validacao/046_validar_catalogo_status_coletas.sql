-- Validação de contrato: catálogo de status e versão temporal das Coletas.
SET NOCOUNT ON;

IF OBJECT_ID(N'dbo.dim_status_coleta', N'U') IS NULL
    THROW 51160, 'Catálogo dbo.dim_status_coleta ausente.', 1;

IF COL_LENGTH(N'dbo.coletas', N'status_updated_at_em') IS NULL
    THROW 51161, 'Coluna dbo.coletas.status_updated_at_em ausente.', 1;

IF EXISTS (
    SELECT expected.codigo_status
    FROM (VALUES
        (N'pending'), (N'treatment'), (N'manifested'), (N'in_transit'), (N'draft'),
        (N'finished'), (N'done'), (N'canceled'), (N'cancelled')
    ) AS expected(codigo_status)
    EXCEPT
    SELECT codigo_status
    FROM dbo.dim_status_coleta
    WHERE ativo = 1
)
    THROW 51162, 'Catálogo ativo de status de Coletas incompleto.', 1;

DECLARE @StatusDesconhecidos INT = (
    SELECT COUNT(*)
    FROM dbo.coletas c
    LEFT JOIN dbo.dim_status_coleta status_coleta
        ON status_coleta.codigo_status = LOWER(LTRIM(RTRIM(c.status)))
       AND status_coleta.ativo = 1
    WHERE c.status IS NOT NULL
      AND status_coleta.codigo_status IS NULL
);

DECLARE @TimestampsNaoNormalizados INT = (
    SELECT COUNT(*)
    FROM dbo.coletas
    WHERE status_updated_at IS NOT NULL
      AND status_updated_at_em IS NULL
);

SELECT
    @StatusDesconhecidos AS status_desconhecidos,
    @TimestampsNaoNormalizados AS timestamps_nao_normalizados,
    COUNT(*) AS coletas_ativas
FROM dbo.coletas
WHERE COALESCE(excluido_na_origem, 0) = 0;

IF @TimestampsNaoNormalizados > 0
    THROW 51163, 'Existem timestamps de status sem normalização.', 1;
GO
