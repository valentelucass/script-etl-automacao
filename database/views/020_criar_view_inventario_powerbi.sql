-- ============================================
-- Script de criação da view 'vw_inventario_powerbi'
-- Execute este script UMA VEZ antes de colocar o sistema em produção
-- ============================================

CREATE OR ALTER VIEW dbo.vw_inventario_powerbi AS
SELECT
    CAST(i.started_at AS TIME(0)) AS [Hora (Início)],
    i.identificador_unico AS [Identificador Único],
    i.numero_minuta AS [Nº Minuta],
    i.pagador_nome AS [Pagador],
    i.remetente_nome AS [Remetente],
    i.origem_cidade AS [Origem],
    i.destinatario_nome AS [Destinatário],
    i.destino_cidade AS [Destino],
    i.regiao_entrega AS [Região Entrega],
    i.filial_entregadora AS [Filial Entregadora],
    i.sequence_code AS [N° Ordem],
    i.branch_nickname AS [Filial],
    i.branch_nickname AS [Filial da Ordem de Conferência],
    COALESCE(f.filial_nome, i.branch_nickname) AS [Filial Emissora do Frete],
    CASE i.type
        WHEN 'CheckIn::Order::Loading' THEN 'Carregamento'
        WHEN 'CheckIn::Order::Unloading' THEN 'Descarregamento'
        WHEN 'CheckIn::Order::Picking' THEN 'Picking'
        WHEN 'CheckIn::Order::Receipt' THEN 'Recebimento'
        WHEN 'CheckIn::Order::Return' THEN 'Retorno'
        ELSE i.type
    END AS [Tipo],
    i.started_at AS [Data/Hora início],
    i.finished_at AS [Data/Hora fim],
    CASE i.status
        WHEN 'pending' THEN 'pendente'
        WHEN 'finished' THEN 'finalizado'
        ELSE i.status
    END AS [Status],
    i.conferente_nome AS [Conferente],
    REPLACE(REPLACE(REPLACE(i.invoices_mapping, '[', ''), ']', ''), '"', '') AS [Notas Fiscais],
    i.invoices_value AS [Valor de NF],
    i.real_weight AS [Peso Real],
    i.total_cubic_volume AS [M³],
    i.taxed_weight AS [Peso Taxado],
    i.invoices_volumes AS [Volumes],
    i.read_volumes AS [Volumes Lidos],
    i.predicted_delivery_at AS [Previsão de Entrega],
    i.performance_finished_at AS [Finalização da Performance],
    i.performance_finished_at AS [Data de Finalização],
    i.ultima_ocorrencia_at AS [Data Última Ocorrência],
    i.ultima_ocorrencia_descricao AS [Ocorrência],
    i.metadata AS [Metadata],
    i.data_extracao AS [Data de extracao]
FROM dbo.inventario AS i
LEFT JOIN dbo.fretes AS f
    ON f.corporation_sequence_number = i.numero_minuta;
GO

PRINT 'View vw_inventario_powerbi criada/atualizada com sucesso!';
GO
