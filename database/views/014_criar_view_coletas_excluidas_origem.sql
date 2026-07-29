IF OBJECT_ID(N'dbo.vw_coletas_excluidas_origem', N'V') IS NOT NULL
    DROP VIEW dbo.vw_coletas_excluidas_origem;
GO

CREATE VIEW dbo.vw_coletas_excluidas_origem AS
SELECT
    c.id AS [ID],
    c.sequence_code AS [Coleta],
    c.request_date AS [Solicitacao],
    c.filial_nome AS [Filial],
    c.status AS [Status ESL bruto],
    COALESCE(status_coleta.rotulo_powerbi, c.status) AS [Status antes da exclusão],
    N'Excluída na origem' AS [Situação de sincronização],
    c.ausente_na_origem_desde AS [Ausente desde],
    c.confirmacoes_ausencia_origem AS [Confirmações de ausência],
    c.data_exclusao_origem AS [Excluída em],
    c.motivo_exclusao_origem AS [Motivo],
    c.reconciliacao_origem_run_id AS [Execução de confirmação],
    c.ultima_reconciliacao_origem_em AS [Última reconciliação]
FROM dbo.coletas c
LEFT JOIN dbo.dim_status_coleta status_coleta
    ON status_coleta.codigo_status = LOWER(LTRIM(RTRIM(c.status)))
   AND status_coleta.ativo = 1
WHERE c.excluido_na_origem = 1;
GO
