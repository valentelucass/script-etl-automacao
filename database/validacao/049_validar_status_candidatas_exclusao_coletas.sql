-- Candidatas da primeira confirmação continuam auditáveis, mas a visão operacional deve informar a exclusão na origem.
SET NOCOUNT ON;

IF OBJECT_ID(N'dbo.vw_coletas_powerbi', N'V') IS NULL
    THROW 51169, 'View dbo.vw_coletas_powerbi ausente.', 1;

IF EXISTS (
    SELECT 1
    FROM dbo.coletas c
    LEFT JOIN dbo.vw_coletas_powerbi v
        ON v.[ID] = c.id
    WHERE COALESCE(c.excluido_na_origem, 0) = 0
      AND COALESCE(c.confirmacoes_ausencia_origem, 0) >= 1
      AND (v.[ID] IS NULL OR v.[Status] <> N'Excluída')
)
    THROW 51170, 'Candidata de exclusão não está identificada como Excluída na view de Coletas.', 1;

IF EXISTS (
    SELECT 1
    FROM dbo.coletas c
    LEFT JOIN dbo.vw_coletas_powerbi v
        ON v.[ID] = c.id
    WHERE COALESCE(c.excluido_na_origem, 0) = 1
      AND (v.[ID] IS NULL OR v.[Status] <> N'Excluída' OR v.[Excluída na Origem] <> 1)
)
    THROW 51171, 'Coleta com exclusão lógica confirmada não está visível como Excluída na view de Coletas.', 1;

IF EXISTS (
    SELECT 1
    FROM dbo.coletas c
    JOIN dbo.vw_coletas_powerbi v
        ON v.[ID] = c.id
    WHERE COALESCE(c.excluido_na_origem, 0) = 0
      AND COALESCE(c.confirmacoes_ausencia_origem, 0) = 0
      AND LOWER(LTRIM(RTRIM(COALESCE(c.status, N'')))) NOT IN (N'excluded', N'excluída')
      AND (v.[Status] = N'Excluída' OR v.[Excluída na Origem] <> 0)
)
    THROW 51172, 'Coleta ativa sem ausência confirmada foi exibida indevidamente como Excluída.', 1;

SELECT
    SUM(CASE WHEN COALESCE(c.excluido_na_origem, 0) = 0 AND COALESCE(c.confirmacoes_ausencia_origem, 0) >= 1 THEN 1 ELSE 0 END) AS candidatas_exibidas_excluidas,
    SUM(CASE WHEN COALESCE(c.excluido_na_origem, 0) = 1 THEN 1 ELSE 0 END) AS exclusoes_logicas_exibidas,
    SUM(CASE WHEN COALESCE(c.excluido_na_origem, 0) = 0 AND COALESCE(c.confirmacoes_ausencia_origem, 0) = 0 THEN 1 ELSE 0 END) AS ativas_sem_ausencia
FROM dbo.coletas c;
GO
