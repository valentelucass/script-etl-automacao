IF OBJECT_ID('dbo.vw_coletas_powerbi', 'V') IS NOT NULL 
    DROP VIEW dbo.vw_coletas_powerbi;
GO

CREATE VIEW dbo.vw_coletas_powerbi AS
SELECT
    c.id AS [ID],
    c.sequence_code AS [Coleta],
    c.request_date AS [Solicitacao],

    -- Hora da solicitacao vinda do campo requestHour da API GraphQL (HH:MM:SS)
    CAST(ISNULL(c.request_hour, '00:00:00') AS TIME(0)) AS [Hora (Solicitacao)],

    c.service_date AS [Agendamento],
    c.finish_date AS [Finalizacao],
    CASE LOWER(c.status)
        WHEN 'pending' THEN N'Pendente'
        WHEN 'done' THEN N'Coletada'
        WHEN 'canceled' THEN N'Cancelada'
        WHEN 'finished' THEN N'Finalizada'
        WHEN 'treatment' THEN N'Em tratativa'
        WHEN 'in_transit' THEN N'Em trânsito'
        WHEN 'manifested' THEN N'Manifestada'
        ELSE c.status
    END AS [Status],
    c.total_volumes AS [Volumes],
    c.total_weight AS [Peso Real],
    c.taxed_weight AS [Peso Taxado],
    c.total_value AS [Valor NF],
    m.sequence_code AS [Numero Manifesto], 
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
    c.last_occurrence AS [Última Ocorrência],
    c.acao_ocorrencia AS [Ação da Ocorrência],
    c.numero_tentativas AS [Nº Tentativas],
    c.metadata AS [Metadata],
    c.data_extracao AS [Data de extracao]
FROM dbo.coletas c
LEFT JOIN dbo.manifestos m ON c.sequence_code = m.pick_sequence_code
LEFT JOIN dbo.dim_usuarios u_cancel ON c.cancellation_user_id = u_cancel.user_id
LEFT JOIN dbo.dim_usuarios u_destroy ON c.destroy_user_id = u_destroy.user_id;
GO