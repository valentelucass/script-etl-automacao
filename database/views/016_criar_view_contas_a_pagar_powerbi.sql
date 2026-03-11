-- ============================================
-- Script de criação da view 'vw_contas_a_pagar_powerbi'
-- Execute este script UMA VEZ antes de colocar o sistema em produção
-- ============================================

CREATE OR ALTER VIEW dbo.vw_contas_a_pagar_powerbi AS
SELECT
    CAST(ISNULL(data_criacao, '1900-01-01T00:00:00+00:00') AS TIME(0)) AS [Hora (Solicitacao)],
    sequence_code AS [Lançamento a Pagar/N°],
    document_number AS [N° Documento],
    issue_date AS [Emissão],
    tipo_lancamento AS [Tipo],
    valor_original AS [Valor],
    valor_juros AS [Juros],
    valor_desconto AS [Desconto],
    valor_a_pagar AS [Valor a pagar],
    CASE WHEN status_pagamento = 'PAGO' THEN 'Sim' ELSE 'Não' END AS [Pago],
    valor_pago AS [Valor pago],
    nome_fornecedor AS [Fornecedor/Nome],
    nome_filial AS [Filial],
    classificacao_contabil AS [Conta Contábil/Classificação],
    descricao_contabil AS [Conta Contábil/Descrição],
    valor_contabil AS [Conta Contábil/Valor],
    nome_centro_custo AS [Centro de custo/Nome],
    valor_centro_custo AS [Centro de custo/Valor],
    area_lancamento AS [Área de Lançamento],
    mes_competencia AS [Mês de Competência],
    ano_competencia AS [Ano de Competência],
    data_criacao AS [Data criação],
    observacoes AS [Observações],
    descricao_despesa AS [Descrição da despesa],
    data_liquidacao AS [Baixa/Data liquidação],
    data_transacao AS [Data transação],
    nome_usuario AS [Usuário/Nome],
    status_pagamento AS [Status Pagamento],
    CASE WHEN reconciliado = 1 THEN 'Conciliado'
         WHEN reconciliado = 0 THEN 'Não conciliado'
         ELSE NULL
    END AS [Conciliado],
    metadata AS [Metadata],
    data_extracao AS [Data de extracao]
FROM dbo.contas_a_pagar;
GO

PRINT 'View vw_contas_a_pagar_powerbi criada/atualizada com sucesso!';
GO
