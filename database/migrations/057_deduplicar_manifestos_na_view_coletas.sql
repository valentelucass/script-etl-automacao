PRINT 'Migration 057: garantir uma linha por coleta na view Power BI';
GO

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

CREATE OR ALTER VIEW dbo.vw_coletas_powerbi AS
SELECT
    c.id AS [ID],
    c.sequence_code AS [Coleta],
    c.request_date AS [Solicitacao],
    CAST(ISNULL(c.request_hour, '00:00:00') AS TIME(0)) AS [Hora (Solicitacao)],
    c.service_date AS [Agendamento],
    c.finish_date AS [Finalizacao],
    COALESCE(status_coleta.rotulo_powerbi, c.status) AS [Status],
    c.total_volumes AS [Volumes],
    c.total_weight AS [Peso Real],
    c.taxed_weight AS [Peso Taxado],
    c.total_value AS [Valor NF],
    manifesto_canonico.sequence_code AS [Numero Manifesto],
    c.vehicle_type_id AS [Veiculo],
    c.cliente_nome AS [Cliente],
    c.cliente_doc AS [Cliente Doc],
    c.local_coleta AS [Local da Coleta],
    c.numero_coleta AS [Numero],
    c.complemento_coleta AS [Complemento],
    c.cidade_coleta AS [Cidade],
    c.bairro_coleta AS [Bairro],
    c.uf_coleta AS [UF],
    c.cep_coleta AS [CEP],
    c.pick_region AS [Região da Coleta],
    COALESCE(
        regra_cep.regiao_logistica,
        regra_cidade.regiao_logistica,
        CONCAT(COALESCE(coleta_local.cidade_limpa, N'Sem cidade'), N' - ', COALESCE(coleta_local.uf_limpa, N'Sem UF'))
    ) AS [Região Logística],
    c.filial_id AS [Filial ID],
    c.filial_nome AS [Filial],
    c.usuario_nome AS [Usuario],
    c.cancellation_reason AS [Motivo Cancel.],
    c.cancellation_user_id AS [Usuario Cancel. ID],
    COALESCE(u_cancel.nome, CAST(c.cancellation_user_id AS VARCHAR(20))) AS [Usuario Cancel. Nome],
    c.destroy_reason AS [Motivo Exclusao],
    c.destroy_user_id AS [Usuario Exclusao ID],
    COALESCE(u_destroy.nome, CAST(c.destroy_user_id AS VARCHAR(20))) AS [Usuario Exclusao Nome],
    c.status_updated_at AS [Status Atualizado Em],
    c.status_updated_at_em AS [Status Atualizado Em (normalizado)],
    c.last_occurrence AS [Última Ocorrência],
    c.acao_ocorrencia AS [Ação da Ocorrência],
    c.numero_tentativas AS [Nº Tentativas],
    c.metadata AS [Metadata],
    c.data_extracao AS [Data de extracao]
FROM dbo.coletas c
LEFT JOIN dbo.dim_status_coleta status_coleta
    ON status_coleta.codigo_status = LOWER(LTRIM(RTRIM(c.status)))
   AND status_coleta.ativo = 1
OUTER APPLY (
    SELECT TOP (1)
        m.sequence_code
    FROM dbo.manifestos m
    WHERE m.pick_sequence_code = c.sequence_code
      AND COALESCE(m.excluido_na_origem, 0) = 0
    ORDER BY m.sequence_code DESC, m.id DESC
) manifesto_canonico
LEFT JOIN dbo.dim_usuarios u_cancel
    ON c.cancellation_user_id = u_cancel.user_id
   AND COALESCE(u_cancel.excluido_na_origem, 0) = 0
LEFT JOIN dbo.dim_usuarios u_destroy
    ON c.destroy_user_id = u_destroy.user_id
   AND COALESCE(u_destroy.excluido_na_origem, 0) = 0
OUTER APPLY (
    SELECT
        NULLIF(REPLACE(REPLACE(REPLACE(LTRIM(RTRIM(CONVERT(VARCHAR(20), c.cep_coleta))), '-', ''), '.', ''), ' ', ''), '') AS cep_limpo,
        NULLIF(LTRIM(RTRIM(CONVERT(NVARCHAR(255), c.cidade_coleta))), N'') AS cidade_limpa,
        NULLIF(UPPER(LTRIM(RTRIM(CONVERT(VARCHAR(2), c.uf_coleta)))), '') AS uf_limpa
) coleta_local
OUTER APPLY (
    SELECT TOP (1) r.regiao_logistica
    FROM dbo.dim_regiao_logistica_rules r
    WHERE coleta_local.cep_limpo IS NOT NULL
      AND LEN(coleta_local.cep_limpo) = 8
      AND coleta_local.cep_limpo NOT LIKE '%[^0-9]%'
      AND r.cep_inicio IS NOT NULL
      AND r.cep_fim IS NOT NULL
      AND coleta_local.cep_limpo BETWEEN r.cep_inicio AND r.cep_fim
    ORDER BY r.cep_inicio DESC, r.cep_fim ASC, r.id ASC
) regra_cep
OUTER APPLY (
    SELECT TOP (1) r.regiao_logistica
    FROM dbo.dim_regiao_logistica_rules r
    WHERE regra_cep.regiao_logistica IS NULL
      AND coleta_local.cidade_limpa IS NOT NULL
      AND coleta_local.uf_limpa IS NOT NULL
      AND r.cidade = coleta_local.cidade_limpa
      AND r.uf = coleta_local.uf_limpa
    ORDER BY r.id ASC
) regra_cidade
WHERE COALESCE(c.excluido_na_origem, 0) = 0;
GO

IF NOT EXISTS (
    SELECT 1
    FROM dbo.schema_migrations
    WHERE migration_id = N'057_deduplicar_manifestos_na_view_coletas'
)
BEGIN
    INSERT INTO dbo.schema_migrations (migration_id, notes)
    VALUES (
        N'057_deduplicar_manifestos_na_view_coletas',
        N'Publica apenas o manifesto ativo de maior sequence_code por coleta para impedir multiplicação de linhas na view.'
    );
END;
GO
