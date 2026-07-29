-- A view de Coletas é granular por coleta: um manifesto adicional não pode replicar a linha.
SET NOCOUNT ON;

IF OBJECT_ID(N'dbo.vw_coletas_powerbi', N'V') IS NULL
    THROW 51167, 'View dbo.vw_coletas_powerbi ausente.', 1;

IF EXISTS (
    SELECT 1
    FROM dbo.vw_coletas_powerbi
    GROUP BY [ID]
    HAVING COUNT_BIG(*) > 1
)
    THROW 51168, 'A view dbo.vw_coletas_powerbi possui mais de uma linha para a mesma coleta.', 1;

SELECT
    COUNT_BIG(*) AS linhas_view,
    COUNT_BIG(DISTINCT [ID]) AS coletas_distintas
FROM dbo.vw_coletas_powerbi;
GO
