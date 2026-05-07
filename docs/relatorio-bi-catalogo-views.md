# Catálogo Técnico das Views de BI

Fonte: scripts SQL do projeto em `Database/views`, `Database/views-dimensao` e `Database/tabelas`.

Observações:
- Tipos são inferidos a partir do DDL das tabelas ou da expressão SQL da view.
- Classificações usam as categorias solicitadas: `Dimensão`, `Métrica`, `Chave`, `Data`.
- Campos técnicos como `Metadata` foram mantidos porque impactam auditoria, rastreabilidade e depuração do ETL.

## vw_bi_monitoramento

- Dependências: `sys_execution_history`
- Total de campos: 9

| Campo | Expressão / Origem | Tipo inferido | Classificação | Descrição inferida |
| --- | --- | --- | --- | --- |
| Id | `id` | BIGINT | Chave | Identificador de negócio para Id. |
| Inicio | `start_time` | DATETIME | Data | Campo temporal associado a Inicio. |
| Fim | `end_time` | DATETIME | Data | Campo temporal associado a Fim. |
| Duracao (s) | `duration_seconds` | INT | Métrica | Indicador quantitativo relacionado a Duracao (s). |
| Data | `CAST(start_time AS DATE)` | DATE | Data | Data de referência para Data. |
| Status | `status` | VARCHAR(20) | Dimensão | Estado operacional/financeiro de Status. |
| Total Registros | `total_records` | INT | Métrica | Métrica operacional/financeira de Total Registros. |
| Categoria Erro | `error_category` | VARCHAR(50) | Dimensão | Atributo descritivo de Categoria Erro. |
| Mensagem Erro | `error_message` | VARCHAR(500) | Dimensão | Atributo descritivo de Mensagem Erro. |

## vw_coletas_powerbi

- Dependências: `coletas`, `manifestos`, `dim_usuarios`
- Total de campos: 38

| Campo | Expressão / Origem | Tipo inferido | Classificação | Descrição inferida |
| --- | --- | --- | --- | --- |
| ID | `c.id` | NVARCHAR(50) | Chave | Identificador de negócio para ID. |
| Coleta | `c.sequence_code` | BIGINT | Dimensão | Atributo descritivo de Coleta. |
| Solicitacao | `c.request_date` | DATE | Data | Campo temporal associado a Solicitacao. |
| Hora (Solicitacao) | `CAST(ISNULL(c.request_hour, '00:00:00') AS TIME(0))` | TIME(0) | Data | Horário operacional associado a Hora (Solicitacao). |
| Agendamento | `c.service_date` | DATE | Data | Campo temporal associado a Agendamento. |
| Finalizacao | `c.finish_date` | DATE | Data | Campo temporal associado a Finalizacao. |
| Status | `CASE LOWER(c.status) WHEN 'pending' THEN N'Pendente' WHEN 'done' THEN N'Coletada' WHEN 'canceled' THEN N'Cancelada' WHEN 'finished' THEN N'Finalizada' WHEN 'treatment' THEN N'Em tratativa' WHEN 'in_transit' THEN N'Em trânsito' WHEN 'manifested' THEN N'Manifestada' ELSE c.status END` | NVARCHAR | Dimensão | Estado operacional/financeiro de Status. |
| Volumes | `c.total_volumes` | INT | Métrica | Indicador quantitativo relacionado a Volumes. |
| Peso Real | `c.total_weight` | DECIMAL(18,3) | Métrica | Indicador quantitativo relacionado a Peso Real. |
| Peso Taxado | `c.taxed_weight` | DECIMAL(18,3) | Métrica | Indicador quantitativo relacionado a Peso Taxado. |
| Valor NF | `c.total_value` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor NF. |
| Numero Manifesto | `m.sequence_code` | BIGINT | Chave | Identificador de negócio para Numero Manifesto. |
| Veiculo | `c.vehicle_type_id` | BIGINT | Dimensão | Atributo descritivo usado para segmentação em Veiculo. |
| Cliente | `c.cliente_nome` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Cliente. |
| Cliente Doc | `c.cliente_doc` | NVARCHAR(50) | Dimensão | Atributo descritivo usado para segmentação em Cliente Doc. |
| Local da Coleta | `c.local_coleta` | NVARCHAR(500) | Dimensão | Atributo descritivo de Local da Coleta. |
| Numero | `c.numero_coleta` | NVARCHAR(50) | Chave | Identificador de negócio para Numero. |
| Complemento | `c.complemento_coleta` | NVARCHAR(255) | Dimensão | Atributo descritivo de Complemento. |
| Cidade | `c.cidade_coleta` | NVARCHAR(255) | Dimensão | Identificador de negócio para Cidade. |
| Bairro | `c.bairro_coleta` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Bairro. |
| UF | `c.uf_coleta` | NVARCHAR(10) | Dimensão | Atributo descritivo usado para segmentação em UF. |
| CEP | `c.cep_coleta` | NVARCHAR(20) | Dimensão | Atributo descritivo usado para segmentação em CEP. |
| Região da Coleta | `c.pick_region` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Região da Coleta. |
| Filial ID | `c.filial_id` | BIGINT | Chave | Identificador de negócio para Filial ID. |
| Filial | `c.filial_nome` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Filial. |
| Usuario | `c.usuario_nome` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Usuario. |
| Motivo Cancel. | `c.cancellation_reason` | NVARCHAR(MAX) | Dimensão | Atributo descritivo de Motivo Cancel.. |
| Usuario Cancel. ID | `c.cancellation_user_id` | BIGINT | Chave | Identificador de negócio para Usuario Cancel. ID. |
| Usuario Cancel. Nome | `COALESCE(u_cancel.nome, CAST(c.cancellation_user_id AS VARCHAR(20)))` | VARCHAR(20) | Dimensão | Atributo descritivo usado para segmentação em Usuario Cancel. Nome. |
| Motivo Exclusao | `c.destroy_reason` | NVARCHAR(MAX) | Dimensão | Atributo descritivo de Motivo Exclusao. |
| Usuario Exclusao ID | `c.destroy_user_id` | BIGINT | Chave | Identificador de negócio para Usuario Exclusao ID. |
| Usuario Exclusao Nome | `COALESCE(u_destroy.nome, CAST(c.destroy_user_id AS VARCHAR(20)))` | VARCHAR(20) | Dimensão | Atributo descritivo usado para segmentação em Usuario Exclusao Nome. |
| Status Atualizado Em | `c.status_updated_at` | NVARCHAR(50) | Dimensão | Estado operacional/financeiro de Status Atualizado Em. |
| Última Ocorrência | `c.last_occurrence` | NVARCHAR(50) | Dimensão | Atributo descritivo de Última Ocorrência. |
| Ação da Ocorrência | `c.acao_ocorrencia` | NVARCHAR(255) | Dimensão | Atributo descritivo de Ação da Ocorrência. |
| Nº Tentativas | `c.numero_tentativas` | INT | Métrica | Indicador quantitativo relacionado a Nº Tentativas. |
| Metadata | `c.metadata` | NVARCHAR(MAX) | Dimensão | Payload técnico bruto associado a Metadata para auditoria e rastreabilidade. |
| Data de extracao | `c.data_extracao` | DATETIME2 | Data | Data de referência para Data de extracao. |

## vw_contas_a_pagar_powerbi

- Dependências: `contas_a_pagar`
- Total de campos: 31

| Campo | Expressão / Origem | Tipo inferido | Classificação | Descrição inferida |
| --- | --- | --- | --- | --- |
| Hora (Solicitacao) | `CAST(ISNULL(data_criacao, '1900-01-01T00:00:00+00:00') AS TIME(0))` | TIME(0) | Data | Horário operacional associado a Hora (Solicitacao). |
| Lançamento a Pagar/N° | `sequence_code` | BIGINT | Dimensão | Atributo descritivo de Lançamento a Pagar/N°. |
| N° Documento | `document_number` | VARCHAR(100) | Chave | Identificador de negócio para N° Documento. |
| Emissão | `issue_date` | DATE | Data | Campo temporal associado a Emissão. |
| Tipo | `tipo_lancamento` | NVARCHAR(100) | Dimensão | Atributo descritivo usado para segmentação em Tipo. |
| Valor | `valor_original` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor. |
| Juros | `valor_juros` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Juros. |
| Desconto | `valor_desconto` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Desconto. |
| Valor a pagar | `valor_a_pagar` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor a pagar. |
| Pago | `CASE WHEN status_pagamento = 'PAGO' THEN 'Sim' ELSE 'Não' END` | NVARCHAR | Dimensão | Atributo descritivo de Pago. |
| Valor pago | `valor_pago` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor pago. |
| Fornecedor/Nome | `nome_fornecedor` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Fornecedor/Nome. |
| Filial | `nome_filial` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Filial. |
| Conta Contábil/Classificação | `classificacao_contabil` | NVARCHAR(100) | Dimensão | Atributo descritivo usado para segmentação em Conta Contábil/Classificação. |
| Conta Contábil/Descrição | `descricao_contabil` | NVARCHAR(255) | Dimensão | Atributo descritivo de Conta Contábil/Descrição. |
| Conta Contábil/Valor | `valor_contabil` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Conta Contábil/Valor. |
| Centro de custo/Nome | `nome_centro_custo` | NVARCHAR(255) | Métrica | Valor monetário ou indicador financeiro de Centro de custo/Nome. |
| Centro de custo/Valor | `valor_centro_custo` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Centro de custo/Valor. |
| Área de Lançamento | `area_lancamento` | NVARCHAR(255) | Dimensão | Atributo descritivo de Área de Lançamento. |
| Mês de Competência | `mes_competencia` | INT | Data | Campo temporal associado a Mês de Competência. |
| Ano de Competência | `ano_competencia` | INT | Data | Campo temporal associado a Ano de Competência. |
| Data criação | `data_criacao` | DATETIMEOFFSET | Data | Data de referência para Data criação. |
| Observações | `observacoes` | NVARCHAR(MAX) | Dimensão | Atributo descritivo de Observações. |
| Descrição da despesa | `descricao_despesa` | NVARCHAR(MAX) | Dimensão | Atributo descritivo de Descrição da despesa. |
| Baixa/Data liquidação | `data_liquidacao` | DATE | Data | Data de referência para Baixa/Data liquidação. |
| Data transação | `data_transacao` | DATE | Data | Data de referência para Data transação. |
| Usuário/Nome | `nome_usuario` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Usuário/Nome. |
| Status Pagamento | `status_pagamento` | NVARCHAR(50) | Dimensão | Estado operacional/financeiro de Status Pagamento. |
| Conciliado | `CASE WHEN reconciliado = 1 THEN 'Conciliado' WHEN reconciliado = 0 THEN 'Não conciliado' ELSE NULL END` | NVARCHAR | Dimensão | Atributo descritivo de Conciliado. |
| Metadata | `metadata` | NVARCHAR(MAX) | Dimensão | Payload técnico bruto associado a Metadata para auditoria e rastreabilidade. |
| Data de extracao | `data_extracao` | DATETIME2 | Data | Data de referência para Data de extracao. |

## vw_cotacoes_powerbi

- Dependências: `cotacoes`
- Total de campos: 53

| Campo | Expressão / Origem | Tipo inferido | Classificação | Descrição inferida |
| --- | --- | --- | --- | --- |
| N° Cotação | `sequence_code` | BIGINT | Dimensão | Atributo descritivo de N° Cotação. |
| Data Cotação | `requested_at` | DATETIMEOFFSET | Data | Data de referência para Data Cotação. |
| Hora (Solicitacao) | `CAST(requested_at AS TIME(0))` | TIME(0) | Data | Horário operacional associado a Hora (Solicitacao). |
| Data de extracao | `data_extracao` | DATETIME2 | Data | Data de referência para Data de extracao. |
| Filial | `branch_nickname` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Filial. |
| Unidade | `branch_nickname` | NVARCHAR(255) | Dimensão | Identificador de negócio para Unidade. |
| Unidade_Origem | `branch_nickname` | NVARCHAR(255) | Dimensão | Identificador de negócio para Unidade_Origem. |
| Solicitante | `requester_name` | NVARCHAR(255) | Dimensão | Atributo descritivo de Solicitante. |
| Usuário | `user_name` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Usuário. |
| Setor_Usuario | `user_name` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Setor_Usuario. |
| Empresa | `company_name` | NVARCHAR(255) | Dimensão | Atributo descritivo de Empresa. |
| Cliente Pagador | `customer_name` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Cliente Pagador. |
| CNPJ/CPF Cliente | `customer_doc` | NVARCHAR(14) | Chave | Identificador de negócio para CNPJ/CPF Cliente. |
| Pagador/Nome fantasia | `customer_nickname` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Pagador/Nome fantasia. |
| Cliente Grupo | `customer_name` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Cliente Grupo. |
| Cliente | `COALESCE(customer_nickname, customer_name)` | NVARCHAR | Dimensão | Atributo descritivo usado para segmentação em Cliente. |
| Cidade Origem | `origin_city` | NVARCHAR(100) | Dimensão | Identificador de negócio para Cidade Origem. |
| UF Origem | `origin_state` | NVARCHAR(2) | Dimensão | Atributo descritivo usado para segmentação em UF Origem. |
| CEP Origem | `origin_postal_code` | NVARCHAR(10) | Dimensão | Atributo descritivo usado para segmentação em CEP Origem. |
| Origem | `CONCAT(origin_city, ' - ', origin_state)` | NVARCHAR | Dimensão | Atributo descritivo usado para segmentação em Origem. |
| Cidade Destino | `destination_city` | NVARCHAR(100) | Dimensão | Identificador de negócio para Cidade Destino. |
| UF Destino | `destination_state` | NVARCHAR(2) | Dimensão | Atributo descritivo usado para segmentação em UF Destino. |
| CEP Destino | `destination_postal_code` | NVARCHAR(10) | Dimensão | Atributo descritivo usado para segmentação em CEP Destino. |
| Destino | `CONCAT(destination_city, ' - ', destination_state)` | NVARCHAR | Dimensão | Atributo descritivo usado para segmentação em Destino. |
| Trecho | `CONCAT(origin_city, ' - ', origin_state, ' x ', destination_city, ' - ', destination_state)` | NVARCHAR | Dimensão | Atributo descritivo de Trecho. |
| Volume | `volumes` | INT | Métrica | Indicador quantitativo relacionado a Volume. |
| Peso real | `real_weight` | NVARCHAR(50) | Métrica | Indicador quantitativo relacionado a Peso real. |
| Peso taxado | `taxed_weight` | DECIMAL(18,3) | Métrica | Indicador quantitativo relacionado a Peso taxado. |
| Valor NF | `invoices_value` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor NF. |
| Valor frete | `total_value` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor frete. |
| Min. Frete/KG | `CASE WHEN taxed_weight > 0 THEN CAST(total_value AS DECIMAL(18,2)) / CAST(taxed_weight AS DECIMAL(18,2)) ELSE 0 END` | SQL_VARIANT | Métrica | Indicador quantitativo relacionado a Min. Frete/KG. |
| Tabela | `price_table` | NVARCHAR(255) | Dimensão | Atributo descritivo de Tabela. |
| Tipo de operação | `operation_type` | NVARCHAR(100) | Dimensão | Atributo descritivo usado para segmentação em Tipo de operação. |
| Metadata | `metadata` | NVARCHAR(MAX) | Dimensão | Payload técnico bruto associado a Metadata para auditoria e rastreabilidade. |
| Status Conversão | `CASE WHEN cte_issued_at IS NOT NULL OR nfse_issued_at IS NOT NULL THEN 'Convertida' WHEN disapprove_comments IS NOT NULL AND LEN(disapprove_comments) > 0 THEN 'Reprovada' ELSE 'Pendente' END` | NVARCHAR | Dimensão | Estado operacional/financeiro de Status Conversão. |
| Status_Sistema | `CASE WHEN cte_issued_at IS NOT NULL OR nfse_issued_at IS NOT NULL THEN 'Convertida' WHEN disapprove_comments IS NOT NULL AND LEN(disapprove_comments) > 0 THEN 'Reprovada' ELSE 'Pendente' END` | NVARCHAR | Dimensão | Estado operacional/financeiro de Status_Sistema. |
| Status_Sistema_CTe | `CASE WHEN cte_issued_at IS NOT NULL THEN 'Emitido' ELSE 'Pendente' END` | NVARCHAR | Dimensão | Estado operacional/financeiro de Status_Sistema_CTe. |
| Status_Sistema_NFSe | `CASE WHEN nfse_issued_at IS NOT NULL THEN 'Emitida' ELSE 'Pendente' END` | NVARCHAR | Dimensão | Estado operacional/financeiro de Status_Sistema_NFSe. |
| Refino_CTe | `CASE WHEN cte_issued_at IS NOT NULL THEN 'Sim' ELSE 'Não' END` | NVARCHAR | Dimensão | Atributo descritivo de Refino_CTe. |
| Motivo Perda | `disapprove_comments` | NVARCHAR(MAX) | Dimensão | Atributo descritivo de Motivo Perda. |
| Observações para o frete | `freight_comments` | NVARCHAR(MAX) | Dimensão | Atributo descritivo de Observações para o frete. |
| CT-e/Data de emissão | `cte_issued_at` | DATETIMEOFFSET | Data | Data de referência para CT-e/Data de emissão. |
| Nfse/Data de emissão | `nfse_issued_at` | DATETIMEOFFSET | Data | Data de referência para Nfse/Data de emissão. |
| Remetente/CNPJ | `sender_document` | NVARCHAR(14) | Chave | Identificador de negócio para Remetente/CNPJ. |
| Remetente/Nome fantasia | `sender_nickname` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Remetente/Nome fantasia. |
| Destinatário/CNPJ | `receiver_document` | NVARCHAR(14) | Chave | Identificador de negócio para Destinatário/CNPJ. |
| Destinatário/Nome fantasia | `receiver_nickname` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Destinatário/Nome fantasia. |
| Descontos/Subtotal parcelas | `discount_subtotal` | DECIMAL(18,6) | Métrica | Valor monetário ou indicador financeiro de Descontos/Subtotal parcelas. |
| Trechos/ITR | `itr_subtotal` | DECIMAL(18,6) | Dimensão | Atributo descritivo de Trechos/ITR. |
| Trechos/TDE | `tde_subtotal` | DECIMAL(18,6) | Dimensão | Atributo descritivo de Trechos/TDE. |
| Trechos/Coleta | `collect_subtotal` | DECIMAL(18,6) | Dimensão | Atributo descritivo de Trechos/Coleta. |
| Trechos/Entrega | `delivery_subtotal` | DECIMAL(18,6) | Dimensão | Atributo descritivo de Trechos/Entrega. |
| Trechos/Outros valores | `other_fees` | DECIMAL(18,6) | Dimensão | Valor monetário ou indicador financeiro de Trechos/Outros valores. |

## vw_dim_clientes

- Dependências: `fretes`, `coletas`, `faturas_por_cliente`
- Total de campos: 1

| Campo | Expressão / Origem | Tipo inferido | Classificação | Descrição inferida |
| --- | --- | --- | --- | --- |
| Nome | `DISTINCT UPPER(LTRIM(RTRIM(Nome)))` | INFERIDO | Dimensão | Atributo descritivo usado para segmentação em Nome. |

## vw_dim_filiais

- Dependências: `vw_fretes_powerbi`, `vw_manifestos_powerbi`, `vw_contas_a_pagar_powerbi`, `vw_faturas_por_cliente_powerbi`
- Total de campos: 2

| Campo | Expressão / Origem | Tipo inferido | Classificação | Descrição inferida |
| --- | --- | --- | --- | --- |
| [NomeFilial] | `[NomeFilial]` | NVARCHAR | Dimensão | Atributo descritivo usado para segmentação em [NomeFilial]. |
| Hora (Solicitacao) | `CAST('00:00:00' AS TIME(0))` | TIME(0) | Data | Horário operacional associado a Hora (Solicitacao). |

## vw_dim_motoristas

- Dependências: `vw_manifestos_powerbi`
- Total de campos: 1

| Campo | Expressão / Origem | Tipo inferido | Classificação | Descrição inferida |
| --- | --- | --- | --- | --- |
| NomeMotorista | `DISTINCT UPPER(LTRIM(RTRIM([Motorista])))` | INFERIDO | Dimensão | Atributo descritivo usado para segmentação em NomeMotorista. |

## vw_dim_planocontas

- Dependências: `vw_contas_a_pagar_powerbi`
- Total de campos: 3

| Campo | Expressão / Origem | Tipo inferido | Classificação | Descrição inferida |
| --- | --- | --- | --- | --- |
| Descricao | `UPPER(LTRIM(RTRIM([Conta Contábil/Descrição])))` | NVARCHAR | Dimensão | Atributo descritivo de Descricao. |
| Classificacao | `ISNULL(MAX([Conta Contábil/Classificação]), 'OUTROS / NÃO CLASSIFICADO')` | NVARCHAR | Dimensão | Atributo descritivo usado para segmentação em Classificacao. |
| Hora (Solicitacao) | `CAST('00:00:00' AS TIME(0))` | TIME(0) | Data | Horário operacional associado a Hora (Solicitacao). |

## vw_dim_usuarios

- Dependências: `dim_usuarios`
- Total de campos: 3

| Campo | Expressão / Origem | Tipo inferido | Classificação | Descrição inferida |
| --- | --- | --- | --- | --- |
| User ID | `[user_id]` | NVARCHAR | Chave | Identificador de negócio para User ID. |
| Nome | `LTRIM(RTRIM([nome]))` | NVARCHAR | Dimensão | Atributo descritivo usado para segmentação em Nome. |
| Data Atualizacao | `[data_atualizacao]` | NVARCHAR | Data | Data de referência para Data Atualizacao. |

## vw_dim_veiculos

- Dependências: `vw_manifestos_powerbi`
- Total de campos: 3

| Campo | Expressão / Origem | Tipo inferido | Classificação | Descrição inferida |
| --- | --- | --- | --- | --- |
| Placa | `UPPER(LTRIM(RTRIM([Veículo/Placa])))` | NVARCHAR | Chave | Identificador de negócio para Placa. |
| TipoVeiculo | `MAX(UPPER(LTRIM(RTRIM([Tipo Veículo]))))` | NVARCHAR | Dimensão | Atributo descritivo usado para segmentação em TipoVeiculo. |
| Proprietario | `MAX(UPPER(LTRIM(RTRIM([Proprietário/Nome]))))` | NVARCHAR | Dimensão | Atributo descritivo de Proprietario. |

## vw_faturas_graphql_powerbi

- Dependências: `faturas_graphql`
- Total de campos: 29

| Campo | Expressão / Origem | Tipo inferido | Classificação | Descrição inferida |
| --- | --- | --- | --- | --- |
| ID | `id` | BIGINT | Chave | Identificador de negócio para ID. |
| Fatura/N° Documento | `document` | NVARCHAR(50) | Chave | Identificador de negócio para Fatura/N° Documento. |
| Emissão | `issue_date` | DATE | Data | Campo temporal associado a Emissão. |
| Vencimento | `due_date` | DATE | Data | Campo temporal associado a Vencimento. |
| Vencimento Original | `original_due_date` | DATE | Data | Campo temporal associado a Vencimento Original. |
| Valor | `value` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor. |
| Valor Pago | `paid_value` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor Pago. |
| Valor a Pagar | `value_to_pay` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor a Pagar. |
| Valor Desconto | `discount_value` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor Desconto. |
| Valor Juros | `interest_value` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor Juros. |
| Pago | `CASE WHEN paid = 1 THEN N'Pago' WHEN paid = 0 THEN N'Não Pago' ELSE N'Indefinido' END` | NVARCHAR | Dimensão | Atributo descritivo de Pago. |
| Status | `CASE LOWER(status) WHEN 'paid' THEN N'Pago' WHEN 'pending' THEN N'Pendente' ELSE CONCAT(N'Não mapeado: ', ISNULL(status, 'NULL')) END` | NVARCHAR | Dimensão | Estado operacional/financeiro de Status. |
| Tipo | `CASE type WHEN 'Accounting::Credit::CustomerBilling' THEN N'Fatura de cliente' ELSE CONCAT(N'Não mapeado: ', type) END` | NVARCHAR | Dimensão | Atributo descritivo usado para segmentação em Tipo. |
| Observações | `comments` | NVARCHAR(MAX) | Dimensão | Atributo descritivo de Observações. |
| Código Sequencial | `sequence_code` | INT | Chave | Identificador de negócio para Código Sequencial. |
| Mês Competência | `competence_month` | INT | Data | Campo temporal associado a Mês Competência. |
| Ano Competência | `competence_year` | INT | Data | Campo temporal associado a Ano Competência. |
| Data Criação | `created_at` | DATETIMEOFFSET | Data | Data de referência para Data Criação. |
| Data Atualização | `updated_at` | DATETIMEOFFSET | Data | Data de referência para Data Atualização. |
| Filial/ID | `corporation_id` | BIGINT | Chave | Identificador de negócio para Filial/ID. |
| Filial/Nome | `corporation_name` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Filial/Nome. |
| Filial/CNPJ | `corporation_cnpj` | NVARCHAR(50) | Chave | Identificador de negócio para Filial/CNPJ. |
| NFS-e/Número | `nfse_numero` | VARCHAR(50) | Chave | Identificador de negócio para NFS-e/Número. |
| Banco/Carteira | `carteira_banco` | VARCHAR(50) | Dimensão | Atributo descritivo de Banco/Carteira. |
| Banco/Instrução Boleto | `instrucao_boleto` | NVARCHAR(MAX) | Dimensão | Atributo descritivo de Banco/Instrução Boleto. |
| Banco/Nome | `banco_nome` | VARCHAR(100) | Dimensão | Atributo descritivo usado para segmentação em Banco/Nome. |
| Método Pagamento | `CASE metodo_pagamento WHEN 'credit_in_account' THEN N'Crédito em conta' ELSE CONCAT(N'Não mapeado: ', metodo_pagamento) END` | NVARCHAR | Dimensão | Atributo descritivo de Método Pagamento. |
| Metadata | `metadata` | NVARCHAR(MAX) | Dimensão | Payload técnico bruto associado a Metadata para auditoria e rastreabilidade. |
| Data de extracao | `data_extracao` | DATETIME2 | Data | Data de referência para Data de extracao. |

## vw_faturas_por_cliente_powerbi

- Dependências: `faturas_por_cliente`, `faturas_graphql`
- Total de campos: 42

| Campo | Expressão / Origem | Tipo inferido | Classificação | Descrição inferida |
| --- | --- | --- | --- | --- |
| Hora (Solicitacao) | `CAST(fpc.data_emissao_cte AS TIME(0))` | TIME(0) | Data | Horário operacional associado a Hora (Solicitacao). |
| ID Único | `fpc.unique_id` | NVARCHAR(100) | Chave | Identificador de negócio para ID Único. |
| Filial | `fpc.filial` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Filial. |
| Estado | `fpc.estado` | NVARCHAR(50) | Dimensão | Atributo descritivo de Estado. |
| CT-e/Número | `fpc.numero_cte` | BIGINT | Chave | Identificador de negócio para CT-e/Número. |
| Número do Documento | `CASE WHEN fpc.numero_cte IS NOT NULL THEN CONVERT(NVARCHAR(50), fpc.numero_cte) WHEN fpc.numero_nfse IS NOT NULL THEN CONVERT(NVARCHAR(50), fpc.numero_nfse) ELSE NULL END` | SQL_VARIANT | Chave | Identificador de negócio para Número do Documento. |
| CT-e/Chave | `fpc.chave_cte` | NVARCHAR(100) | Chave | Identificador de negócio para CT-e/Chave. |
| CT-e/Data de emissão | `fpc.data_emissao_cte` | DATETIMEOFFSET | Data | Data de referência para CT-e/Data de emissão. |
| Frete/Valor dos CT-es | `fpc.valor_frete` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Frete/Valor dos CT-es. |
| Terceiros/Valor CT-es | `fpc.third_party_ctes_value` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Terceiros/Valor CT-es. |
| CT-e/Status | `fpc.status_cte` | NVARCHAR(255) | Dimensão | Estado operacional/financeiro de CT-e/Status. |
| CT-e/Resultado | `fpc.status_cte_result` | NVARCHAR(MAX) | Dimensão | Atributo descritivo de CT-e/Resultado. |
| Tipo | `fpc.tipo_frete` | NVARCHAR(100) | Dimensão | Atributo descritivo usado para segmentação em Tipo. |
| Classificação | `fpc.classificacao` | NVARCHAR(100) | Dimensão | Atributo descritivo usado para segmentação em Classificação. |
| Pagador do frete/Nome | `fpc.pagador_nome` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Pagador do frete/Nome. |
| Pagador do frete/Documento | `fpc.pagador_documento` | NVARCHAR(50) | Chave | Identificador de negócio para Pagador do frete/Documento. |
| Cliente/CNPJ | `fpc.cliente_cnpj` | NVARCHAR(14) | Chave | CNPJ do cliente pagador vindo da própria requisição `faturas_por_cliente`: API `fit_pyr_document`, persistido em `pagador_documento` e materializado em `cliente_cnpj`. |
| Remetente/Nome | `fpc.remetente_nome` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Remetente/Nome. |
| Remetente/Documento | `fpc.remetente_documento` | NVARCHAR(50) | Chave | Identificador de negócio para Remetente/Documento. |
| Destinatário/Nome | `fpc.destinatario_nome` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Destinatário/Nome. |
| Destinatário/Documento | `fpc.destinatario_documento` | NVARCHAR(50) | Chave | Identificador de negócio para Destinatário/Documento. |
| Vendedor/Nome | `fpc.vendedor_nome` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Vendedor/Nome. |
| NFS-e/Número | `fpc.numero_nfse` | BIGINT | Chave | Identificador de negócio para NFS-e/Número. |
| NFS-e/Série | `fpc.serie_nfse` | NVARCHAR(50) | Dimensão | Atributo descritivo de NFS-e/Série. |
| fit_nse_number | `fpc.numero_nfse` | BIGINT | Dimensão | Atributo descritivo de fit_nse_number. |
| N° NFS-e | `fg.nfse_numero` | VARCHAR(50) | Dimensão | Atributo descritivo de N° NFS-e. |
| Carteira/Descrição | `fg.carteira_banco` | VARCHAR(50) | Dimensão | Atributo descritivo de Carteira/Descrição. |
| Instrução Customizada | `fg.instrucao_boleto` | NVARCHAR(MAX) | Dimensão | Valor monetário ou indicador financeiro de Instrução Customizada. |
| Status do Processo | `CASE WHEN fpc.fit_ant_document IS NOT NULL THEN 'Faturado' ELSE 'Aguardando Faturamento' END` | NVARCHAR | Dimensão | Estado operacional/financeiro de Status do Processo. |
| Fatura/N° Documento | `fpc.fit_ant_document` | NVARCHAR(50) | Chave | Identificador de negócio para Fatura/N° Documento. |
| Fatura/Emissão | `fpc.fit_ant_issue_date` | DATE | Data | Campo temporal associado a Fatura/Emissão. |
| Fatura/Valor | `fpc.fit_ant_value` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Fatura/Valor. |
| Fatura/Valor Total | `fpc.valor_fatura` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Fatura/Valor Total. |
| Fatura/Número | `fpc.numero_fatura` | NVARCHAR(50) | Chave | Identificador de negócio para Fatura/Número. |
| Fatura/Emissão Fatura | `fpc.data_emissao_fatura` | DATE | Data | Campo temporal associado a Fatura/Emissão Fatura. |
| Parcelas/Vencimento | `fpc.data_vencimento_fatura` | DATE | Data | Campo temporal associado a Parcelas/Vencimento. |
| Fatura/Baixa | `fpc.data_baixa_fatura` | DATE | Data | Campo temporal associado a Fatura/Baixa. |
| Fatura/Data Vencimento Original | `fpc.fit_ant_ils_original_due_date` | DATE | Data | Data de referência para Fatura/Data Vencimento Original. |
| Notas Fiscais | `fpc.notas_fiscais` | NVARCHAR(MAX) | Dimensão | Atributo descritivo de Notas Fiscais. |
| Pedidos/Cliente | `fpc.pedidos_cliente` | NVARCHAR(MAX) | Dimensão | Identificador de negócio para Pedidos/Cliente. |
| Metadata | `fpc.metadata` | NVARCHAR(MAX) | Dimensão | Payload técnico bruto associado a Metadata para auditoria e rastreabilidade. |
| Data da Última Atualização | `fpc.data_extracao` | DATETIME2 | Data | Data de referência para Data da Última Atualização. |

## vw_fretes_powerbi

- Dependências: `fretes`
- Total de campos: 102

| Campo | Expressão / Origem | Tipo inferido | Classificação | Descrição inferida |
| --- | --- | --- | --- | --- |
| Hora (Solicitacao) | `CAST(servico_em AS TIME(0))` | TIME(0) | Data | Horário operacional associado a Hora (Solicitacao). |
| ID | `id` | BIGINT | Chave | Identificador de negócio para ID. |
| Chave CT-e | `chave_cte` | NVARCHAR(100) | Chave | Identificador de negócio para Chave CT-e. |
| Nº CT-e | `numero_cte` | INT | Dimensão | Atributo descritivo de Nº CT-e. |
| Série | `serie_cte` | INT | Dimensão | Atributo descritivo de Série. |
| CT-e Emissão | `cte_issued_at` | DATETIMEOFFSET | Data | Campo temporal associado a CT-e Emissão. |
| CT-e Tipo Emissão | `cte_emission_type` | NVARCHAR(50) | Data | Atributo descritivo usado para segmentação em CT-e Tipo Emissão. |
| CT-e ID | `cte_id` | BIGINT | Chave | Identificador de negócio para CT-e ID. |
| CT-e Criado em | `cte_created_at` | DATETIMEOFFSET | Data | Campo temporal associado a CT-e Criado em. |
| Documento Oficial/Tipo | `CASE WHEN cte_id IS NOT NULL OR chave_cte IS NOT NULL OR numero_cte IS NOT NULL OR serie_cte IS NOT NULL THEN 'CT-e' WHEN nfse_number IS NOT NULL OR nfse_series IS NOT NULL OR nfse_xml_document IS NOT NULL OR nfse_integration_id IS NOT NULL THEN 'NFS-e' ELSE 'Pendente/Não Emitido' END` | NVARCHAR | Chave | Identificador de negócio para Documento Oficial/Tipo. |
| Documento Oficial/Chave | `CASE WHEN cte_id IS NOT NULL OR chave_cte IS NOT NULL THEN chave_cte ELSE NULL END` | SQL_VARIANT | Chave | Identificador de negócio para Documento Oficial/Chave. |
| Documento Oficial/Número | `CASE WHEN cte_id IS NOT NULL OR chave_cte IS NOT NULL THEN CONVERT(NVARCHAR(50), numero_cte) ELSE CONVERT(NVARCHAR(50), nfse_number) END` | SQL_VARIANT | Chave | Identificador de negócio para Documento Oficial/Número. |
| Documento Oficial/Série | `CASE WHEN cte_id IS NOT NULL OR chave_cte IS NOT NULL THEN CONVERT(NVARCHAR(50), serie_cte) ELSE nfse_series END` | SQL_VARIANT | Chave | Identificador de negócio para Documento Oficial/Série. |
| Documento Oficial/XML | `CASE WHEN cte_id IS NOT NULL OR chave_cte IS NOT NULL THEN NULL ELSE nfse_xml_document END` | SQL_VARIANT | Chave | Identificador de negócio para Documento Oficial/XML. |
| Data frete | `servico_em` | DATETIMEOFFSET | Data | Data de referência para Data frete. |
| Criado em | `criado_em` | DATETIMEOFFSET | Data | Campo temporal associado a Criado em. |
| Valor Total do Serviço | `valor_total` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor Total do Serviço. |
| Valor NF | `valor_notas` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor NF. |
| Kg NF | `peso_notas` | DECIMAL(18,3) | Métrica | Indicador quantitativo relacionado a Kg NF. |
| Valor Frete | `subtotal` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor Frete. |
| Volumes | `invoices_total_volumes` | INT | Métrica | Indicador quantitativo relacionado a Volumes. |
| Kg Taxado | `taxed_weight` | DECIMAL(18,3) | Métrica | Indicador quantitativo relacionado a Kg Taxado. |
| Kg Real | `real_weight` | DECIMAL(18,3) | Métrica | Indicador quantitativo relacionado a Kg Real. |
| Kg Cubado | `cubages_cubed_weight` | DECIMAL(18,3) | Métrica | Indicador quantitativo relacionado a Kg Cubado. |
| M3 | `total_cubic_volume` | DECIMAL(18,3) | Métrica | Indicador quantitativo relacionado a M3. |
| Pagador | `pagador_nome` | NVARCHAR(255) | Dimensão | Atributo descritivo de Pagador. |
| Pagador Doc | `pagador_documento` | NVARCHAR(50) | Dimensão | Atributo descritivo de Pagador Doc. |
| Pagador ID | `pagador_id` | BIGINT | Chave | Identificador de negócio para Pagador ID. |
| Remetente | `remetente_nome` | NVARCHAR(255) | Dimensão | Atributo descritivo de Remetente. |
| Remetente Doc | `remetente_documento` | NVARCHAR(50) | Dimensão | Atributo descritivo de Remetente Doc. |
| Remetente ID | `remetente_id` | BIGINT | Chave | Identificador de negócio para Remetente ID. |
| Origem | `origem_cidade` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Origem. |
| UF Origem | `origem_uf` | NVARCHAR(10) | Dimensão | Atributo descritivo usado para segmentação em UF Origem. |
| Destinatario | `destinatario_nome` | NVARCHAR(255) | Dimensão | Atributo descritivo de Destinatario. |
| Destinatario Doc | `destinatario_documento` | NVARCHAR(50) | Dimensão | Atributo descritivo de Destinatario Doc. |
| Destinatario ID | `destinatario_id` | BIGINT | Chave | Identificador de negócio para Destinatario ID. |
| Destino | `destino_cidade` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Destino. |
| UF Destino | `destino_uf` | NVARCHAR(10) | Dimensão | Atributo descritivo usado para segmentação em UF Destino. |
| Filial | `filial_nome` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Filial. |
| Filial Apelido | `filial_apelido` | NVARCHAR(255) | Dimensão | Identificador de negócio para Filial Apelido. |
| Filial CNPJ | `filial_cnpj` | NVARCHAR(50) | Chave | Identificador de negócio para Filial CNPJ. |
| Tabela de Preço | `tabela_preco_nome` | NVARCHAR(255) | Dimensão | Atributo descritivo de Tabela de Preço. |
| Classificação | `classificacao_nome` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Classificação. |
| Centro de Custo | `centro_custo_nome` | NVARCHAR(255) | Métrica | Valor monetário ou indicador financeiro de Centro de Custo. |
| Usuário | `usuario_nome` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Usuário. |
| NF | `numero_nota_fiscal` | NVARCHAR(MAX) | Dimensão | Atributo descritivo de NF. |
| Referência | `reference_number` | NVARCHAR(100) | Chave | Chave ou código usado para identificar Referência. |
| Corp ID | `id_corporacao` | BIGINT | Chave | Identificador de negócio para Corp ID. |
| Cidade Destino ID | `id_cidade_destino` | BIGINT | Chave | Identificador de negócio para Cidade Destino ID. |
| Previsão de Entrega | `data_previsao_entrega` | DATE | Data | Campo temporal associado a Previsão de Entrega. |
| Modal | `modal` | NVARCHAR(50) | Dimensão | Atributo descritivo de Modal. |
| Status | `CASE status WHEN 'pending' THEN 'pendente' WHEN 'finished' THEN 'finalizado' WHEN 'in_transit' THEN 'em trânsito' WHEN 'standby' THEN 'aguardando' WHEN 'manifested' THEN 'registrado' WHEN 'occurrence_treatment' THEN 'tratamento de ocorrência' ELSE status END` | NVARCHAR | Dimensão | Estado operacional/financeiro de Status. |
| Tipo Frete | `REPLACE(tipo_frete, 'Freight::', '')` | NVARCHAR | Dimensão | Atributo descritivo usado para segmentação em Tipo Frete. |
| Service Type | `service_type` | INT | Dimensão | Atributo descritivo de Service Type. |
| Seguro Habilitado | `CASE WHEN insurance_enabled = 1 THEN 'Com seguro' WHEN insurance_enabled = 0 THEN 'Sem seguro' ELSE NULL END` | NVARCHAR | Dimensão | Atributo descritivo de Seguro Habilitado. |
| GRIS | `gris_subtotal` | DECIMAL(18,2) | Dimensão | Atributo descritivo de GRIS. |
| TDE | `tde_subtotal` | DECIMAL(18,2) | Dimensão | Atributo descritivo de TDE. |
| Frete Peso | `freight_weight_subtotal` | DECIMAL(18,2) | Métrica | Indicador quantitativo relacionado a Frete Peso. |
| Ad Valorem | `ad_valorem_subtotal` | DECIMAL(18,2) | Dimensão | Valor monetário ou indicador financeiro de Ad Valorem. |
| Pedágio | `toll_subtotal` | DECIMAL(18,2) | Dimensão | Atributo descritivo de Pedágio. |
| ITR | `itr_subtotal` | DECIMAL(18,2) | Dimensão | Atributo descritivo de ITR. |
| Modal CT-e | `modal_cte` | NVARCHAR(50) | Dimensão | Atributo descritivo de Modal CT-e. |
| Redispatch | `redispatch_subtotal` | DECIMAL(18,2) | Dimensão | Atributo descritivo de Redispatch. |
| SUFRAMA | `suframa_subtotal` | DECIMAL(18,2) | Dimensão | Atributo descritivo usado para segmentação em SUFRAMA. |
| Tipo Pagamento | `CASE payment_type WHEN 'bill' THEN 'cobrança' WHEN 'cash' THEN 'dinheiro' ELSE payment_type END` | NVARCHAR | Dimensão | Atributo descritivo usado para segmentação em Tipo Pagamento. |
| Doc Anterior | `CASE previous_document_type WHEN 'electronic' THEN 'eletrônico' ELSE previous_document_type END` | NVARCHAR | Dimensão | Atributo descritivo de Doc Anterior. |
| Valor Produtos | `products_value` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor Produtos. |
| TRT | `trt_subtotal` | DECIMAL(18,2) | Dimensão | Atributo descritivo de TRT. |
| ICMS CST | `fiscal_cst_type` | NVARCHAR(10) | Dimensão | Atributo descritivo de ICMS CST. |
| CFOP | `fiscal_cfop_code` | NVARCHAR(10) | Dimensão | Atributo descritivo de CFOP. |
| Valor ICMS | `fiscal_tax_value` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor ICMS. |
| Valor PIS | `fiscal_pis_value` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor PIS. |
| Valor COFINS | `fiscal_cofins_value` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor COFINS. |
| Base de Cálculo ICMS | `fiscal_calculation_basis` | DECIMAL(18,2) | Dimensão | Atributo descritivo de Base de Cálculo ICMS. |
| Alíquota ICMS % | `fiscal_tax_rate` | DECIMAL(18,6) | Métrica | Métrica operacional/financeira de Alíquota ICMS %. |
| Alíquota PIS % | `fiscal_pis_rate` | DECIMAL(18,6) | Métrica | Métrica operacional/financeira de Alíquota PIS %. |
| Alíquota COFINS % | `fiscal_cofins_rate` | DECIMAL(18,6) | Métrica | Métrica operacional/financeira de Alíquota COFINS %. |
| Possui DIFAL | `CASE WHEN fiscal_has_difal = 1 THEN 'possui' WHEN fiscal_has_difal = 0 THEN 'não possui' ELSE NULL END` | NVARCHAR | Métrica | Métrica operacional/financeira de Possui DIFAL. |
| DIFAL Origem | `fiscal_difal_origin` | DECIMAL(18,2) | Métrica | Atributo descritivo usado para segmentação em DIFAL Origem. |
| DIFAL Destino | `fiscal_difal_destination` | DECIMAL(18,2) | Métrica | Atributo descritivo usado para segmentação em DIFAL Destino. |
| Série NFS-e | `nfse_series` | NVARCHAR(50) | Dimensão | Atributo descritivo de Série NFS-e. |
| Nº NFS-e | `nfse_number` | INT | Dimensão | Atributo descritivo de Nº NFS-e. |
| NFS-e/ID Integração | `nfse_integration_id` | NVARCHAR(50) | Chave | Identificador de negócio para NFS-e/ID Integração. |
| NFS-e/Status | `nfse_status` | NVARCHAR(50) | Dimensão | Estado operacional/financeiro de NFS-e/Status. |
| NFS-e/Emissão | `nfse_issued_at` | DATE | Data | Campo temporal associado a NFS-e/Emissão. |
| NFS-e/Cancelamento/Motivo | `nfse_cancelation_reason` | NVARCHAR(255) | Dimensão | Atributo descritivo de NFS-e/Cancelamento/Motivo. |
| NFS-e/PDF | `nfse_pdf_service_url` | NVARCHAR(1000) | Dimensão | Atributo descritivo de NFS-e/PDF. |
| NFS-e/Filial ID | `nfse_corporation_id` | BIGINT | Chave | Identificador de negócio para NFS-e/Filial ID. |
| NFS-e/Serviço/Descrição | `nfse_service_description` | NVARCHAR(500) | Dimensão | Atributo descritivo de NFS-e/Serviço/Descrição. |
| NFS-e/XML | `nfse_xml_document` | NVARCHAR(MAX) | Dimensão | Atributo descritivo de NFS-e/XML. |
| Seguro ID | `insurance_id` | BIGINT | Chave | Identificador de negócio para Seguro ID. |
| Outras Tarifas | `other_fees` | DECIMAL(18,2) | Dimensão | Atributo descritivo de Outras Tarifas. |
| KM | `km` | DECIMAL(18,2) | Métrica | Indicador quantitativo relacionado a KM. |
| Tipo Contábil Pagamento | `payment_accountable_type` | INT | Dimensão | Atributo descritivo usado para segmentação em Tipo Contábil Pagamento. |
| Valor Segurado | `insured_value` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor Segurado. |
| Globalizado | `CASE WHEN globalized = 1 THEN 'verdadeiro' WHEN globalized = 0 THEN 'falso' ELSE NULL END` | NVARCHAR | Dimensão | Atributo descritivo de Globalizado. |
| SEC/CAT | `sec_cat_subtotal` | DECIMAL(18,2) | Dimensão | Atributo descritivo de SEC/CAT. |
| Tipo Globalizado | `CASE globalized_type WHEN 'none' THEN 'nenhum' ELSE globalized_type END` | NVARCHAR | Dimensão | Atributo descritivo usado para segmentação em Tipo Globalizado. |
| Tipo Contábil Tabela | `price_table_accountable_type` | INT | Dimensão | Atributo descritivo usado para segmentação em Tipo Contábil Tabela. |
| Tipo Contábil Seguro | `insurance_accountable_type` | INT | Dimensão | Atributo descritivo usado para segmentação em Tipo Contábil Seguro. |
| Metadata | `metadata` | NVARCHAR(MAX) | Dimensão | Payload técnico bruto associado a Metadata para auditoria e rastreabilidade. |
| Data de extracao | `data_extracao` | DATETIME2 | Data | Data de referência para Data de extracao. |

## vw_localizacao_cargas_powerbi

- Dependências: `localizacao_cargas`
- Total de campos: 21

| Campo | Expressão / Origem | Tipo inferido | Classificação | Descrição inferida |
| --- | --- | --- | --- | --- |
| Hora (Solicitacao) | `CAST(service_at AS TIME(0))` | TIME(0) | Data | Horário operacional associado a Hora (Solicitacao). |
| N° Minuta | `sequence_number` | BIGINT | Dimensão | Atributo descritivo de N° Minuta. |
| Tipo | `REPLACE(type, 'Freight::', '')` | NVARCHAR | Dimensão | Atributo descritivo usado para segmentação em Tipo. |
| Data do frete | `service_at` | DATETIMEOFFSET | Data | Data de referência para Data do frete. |
| Volumes | `invoices_volumes` | INT | Métrica | Indicador quantitativo relacionado a Volumes. |
| Peso Taxado | `taxed_weight` | NVARCHAR(50) | Métrica | Indicador quantitativo relacionado a Peso Taxado. |
| Valor NF | `invoices_value` | NVARCHAR(50) | Métrica | Valor monetário ou indicador financeiro de Valor NF. |
| Valor Frete | `total_value` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor Frete. |
| Tipo Serviço | `service_type` | NVARCHAR(50) | Dimensão | Atributo descritivo usado para segmentação em Tipo Serviço. |
| Filial Emissora | `branch_nickname` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Filial Emissora. |
| Previsão Entrega/Previsão de entrega | `predicted_delivery_at` | DATETIMEOFFSET | Data | Campo temporal associado a Previsão Entrega/Previsão de entrega. |
| Região Destino | `destination_location_name` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Região Destino. |
| Filial Destino | `destination_branch_nickname` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Filial Destino. |
| Classificação | `classification` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Classificação. |
| Status Carga | `CASE status WHEN 'pending' THEN 'Pendente' WHEN 'delivering' THEN 'Em entrega' WHEN 'in_warehouse' THEN 'Em armazém' WHEN 'in_transfer' THEN 'Em transferência' WHEN 'manifested' THEN 'Manifestado' WHEN 'finished' THEN 'Finalizado' ELSE status END` | NVARCHAR | Dimensão | Estado operacional/financeiro de Status Carga. |
| Filial Atual | `status_branch_nickname` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Filial Atual. |
| Região Origem | `origin_location_name` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Região Origem. |
| Filial Origem | `origin_branch_nickname` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Filial Origem. |
| Localização Atual | `fit_fln_cln_nickname` | NVARCHAR(255) | Dimensão | Atributo descritivo de Localização Atual. |
| Metadata | `metadata` | NVARCHAR(MAX) | Dimensão | Payload técnico bruto associado a Metadata para auditoria e rastreabilidade. |
| Data de extracao | `data_extracao` | DATETIME2 | Data | Data de referência para Data de extracao. |

## vw_manifestos_powerbi

- Dependências: `manifestos`
- Total de campos: 97

| Campo | Expressão / Origem | Tipo inferido | Classificação | Descrição inferida |
| --- | --- | --- | --- | --- |
| Hora (Solicitacao) | `CAST(created_at AS TIME(0))` | TIME(0) | Data | Horário operacional associado a Hora (Solicitacao). |
| Hora (Criação) | `CAST(created_at AS TIME(0))` | TIME(0) | Data | Horário operacional associado a Hora (Criação). |
| Número | `sequence_code` | BIGINT | Chave | Identificador de negócio para Número. |
| Identificador Único | `identificador_unico` | NVARCHAR(100) | Dimensão | Identificador de negócio para Identificador Único. |
| Status | `CASE status WHEN 'closed' THEN 'encerrado' WHEN 'in_transit' THEN 'em trânsito' WHEN 'pending' THEN 'pendente' ELSE status END` | NVARCHAR | Dimensão | Estado operacional/financeiro de Status. |
| Classificação | `classification` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Classificação. |
| Filial | `branch_nickname` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Filial. |
| Data criação | `created_at` | DATETIMEOFFSET | Data | Data de referência para Data criação. |
| Saída | `departured_at` | DATETIMEOFFSET | Data | Campo temporal associado a Saída. |
| Fechamento | `closed_at` | DATETIMEOFFSET | Data | Campo temporal associado a Fechamento. |
| Chegada | `finished_at` | DATETIMEOFFSET | Data | Campo temporal associado a Chegada. |
| MDFe | `mdfe_number` | INT | Dimensão | Atributo descritivo de MDFe. |
| MDF-es/Chave | `mdfe_key` | NVARCHAR(100) | Chave | Identificador de negócio para MDF-es/Chave. |
| MDFe/Status | `CASE mdfe_status WHEN 'pending' THEN 'pendente' WHEN 'closed' THEN 'encerrado' WHEN 'issued' THEN 'emitido' WHEN 'rejected' THEN 'rejeitado' ELSE mdfe_status END` | NVARCHAR | Dimensão | Estado operacional/financeiro de MDFe/Status. |
| Polo de distribuição | `distribution_pole` | NVARCHAR(255) | Dimensão | Atributo descritivo de Polo de distribuição. |
| Veículo/Placa | `vehicle_plate` | NVARCHAR(10) | Chave | Identificador de negócio para Veículo/Placa. |
| Tipo Veículo | `vehicle_type` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Tipo Veículo. |
| Proprietário/Nome | `vehicle_owner` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Proprietário/Nome. |
| Motorista | `driver_name` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Motorista. |
| Km saída | `vehicle_departure_km` | INT | Data | Indicador quantitativo relacionado a Km saída. |
| Km chegada | `closing_km` | INT | Data | Indicador quantitativo relacionado a Km chegada. |
| KM viagem | `traveled_km` | INT | Métrica | Indicador quantitativo relacionado a KM viagem. |
| Km manual | `CASE WHEN manual_km = 1 THEN 'é manual' WHEN manual_km = 0 THEN 'não é manual' ELSE NULL END` | NVARCHAR | Métrica | Indicador quantitativo relacionado a Km manual. |
| Qtd NF | `invoices_count` | INT | Métrica | Indicador quantitativo relacionado a Qtd NF. |
| Volumes NF | `invoices_volumes` | INT | Métrica | Indicador quantitativo relacionado a Volumes NF. |
| Peso NF | `invoices_weight` | DECIMAL(18,3) | Métrica | Indicador quantitativo relacionado a Peso NF. |
| Total peso taxado | `total_taxed_weight` | DECIMAL(18,3) | Métrica | Indicador quantitativo relacionado a Total peso taxado. |
| Total M3 | `total_cubic_volume` | DECIMAL(18,6) | Métrica | Indicador quantitativo relacionado a Total M3. |
| Valor NF | `invoices_value` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor NF. |
| Fretes/Total | `manifest_freights_total` | DECIMAL(18,2) | Métrica | Métrica operacional/financeira de Fretes/Total. |
| Coleta/Número | `pick_sequence_code` | BIGINT | Chave | Identificador de negócio para Coleta/Número. |
| CIOT/Número | `contract_number` | NVARCHAR(50) | Chave | Identificador de negócio para CIOT/Número. |
| Tipo de contrato | `CASE contract_type WHEN 'aggregate' THEN 'prestador agregado' WHEN 'driver' THEN 'motorista autônomo' ELSE contract_type END` | NVARCHAR | Dimensão | Atributo descritivo usado para segmentação em Tipo de contrato. |
| Tipo de cálculo | `CASE calculation_type WHEN 'price_table' THEN 'tabela de preço' WHEN 'agreed' THEN 'acordado' ELSE calculation_type END` | NVARCHAR | Dimensão | Atributo descritivo usado para segmentação em Tipo de cálculo. |
| Tipo de carga | `CASE cargo_type WHEN 'fractioned' THEN 'carga fracionada' WHEN 'closed' THEN 'carga fechada' ELSE cargo_type END` | NVARCHAR | Dimensão | Atributo descritivo usado para segmentação em Tipo de carga. |
| Diária | `daily_subtotal` | DECIMAL(18,2) | Dimensão | Atributo descritivo de Diária. |
| Custo total | `total_cost` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Custo total. |
| Valor frete | `freight_subtotal` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor frete. |
| Combustível | `fuel_subtotal` | DECIMAL(18,2) | Dimensão | Atributo descritivo de Combustível. |
| Pedágio | `toll_subtotal` | DECIMAL(18,2) | Dimensão | Atributo descritivo de Pedágio. |
| Serviços motorista/Total | `driver_services_total` | DECIMAL(18,2) | Métrica | Atributo descritivo usado para segmentação em Serviços motorista/Total. |
| Despesa operacional | `operational_expenses_total` | DECIMAL(18,2) | Dimensão | Atributo descritivo de Despesa operacional. |
| Dados do agregado/INSS | `inss_value` | DECIMAL(18,2) | Dimensão | Atributo descritivo de Dados do agregado/INSS. |
| Dados do agregado/SEST/SENAT | `sest_senat_value` | DECIMAL(18,2) | Dimensão | Atributo descritivo de Dados do agregado/SEST/SENAT. |
| Dados do agregado/IR | `ir_value` | DECIMAL(18,2) | Dimensão | Atributo descritivo de Dados do agregado/IR. |
| Saldo a pagar | `paying_total` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Saldo a pagar. |
| Destinos únicos/Qtd | `uniq_destinations_count` | INT | Métrica | Indicador quantitativo relacionado a Destinos únicos/Qtd. |
| Gerar MDF-e | `generate_mdfe` | BIT | Dimensão | Atributo descritivo de Gerar MDF-e. |
| Solicitou Monitoramento | `monitoring_request` | BIT | Dimensão | Atributo descritivo de Solicitou Monitoramento. |
| Solicitação Monitoramento | `CASE WHEN monitoring_request = 1 THEN 'sim' WHEN monitoring_request = 0 THEN 'não' ELSE NULL END` | NVARCHAR | Dimensão | Atributo descritivo de Solicitação Monitoramento. |
| Leitura Móvel/Em | `mobile_read_at` | DATETIMEOFFSET | Data | Campo temporal associado a Leitura Móvel/Em. |
| KM Total | `km` | DECIMAL(18,2) | Métrica | Indicador quantitativo relacionado a KM Total. |
| Itens/Entrega | `delivery_manifest_items_count` | INT | Dimensão | Atributo descritivo de Itens/Entrega. |
| Itens/Transferência | `transfer_manifest_items_count` | INT | Dimensão | Atributo descritivo de Itens/Transferência. |
| Itens/Coleta | `pick_manifest_items_count` | INT | Dimensão | Atributo descritivo de Itens/Coleta. |
| Itens/Despacho Rascunho | `dispatch_draft_manifest_items_count` | INT | Dimensão | Atributo descritivo de Itens/Despacho Rascunho. |
| Itens/Consolidação | `consolidation_manifest_items_count` | INT | Dimensão | Identificador de negócio para Itens/Consolidação. |
| Itens/Coleta Reversa | `reverse_pick_manifest_items_count` | INT | Dimensão | Atributo descritivo de Itens/Coleta Reversa. |
| Itens/Total | `manifest_items_count` | INT | Métrica | Métrica operacional/financeira de Itens/Total. |
| Itens/Finalizados | `finalized_manifest_items_count` | INT | Dimensão | Atributo descritivo de Itens/Finalizados. |
| Calculado/Coleta | `calculated_pick_count` | INT | Dimensão | Atributo descritivo de Calculado/Coleta. |
| Calculado/Entrega | `calculated_delivery_count` | INT | Dimensão | Atributo descritivo de Calculado/Entrega. |
| Calculado/Despacho | `calculated_dispatch_count` | INT | Dimensão | Atributo descritivo de Calculado/Despacho. |
| Calculado/Consolidação | `calculated_consolidation_count` | INT | Dimensão | Identificador de negócio para Calculado/Consolidação. |
| Calculado/Coleta Reversa | `calculated_reverse_pick_count` | INT | Dimensão | Atributo descritivo de Calculado/Coleta Reversa. |
| Valor/Coletas | `pick_subtotal` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor/Coletas. |
| Valor/Entregas | `delivery_subtotal` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Valor/Entregas. |
| Despachos | `dispatch_subtotal` | DECIMAL(18,2) | Dimensão | Atributo descritivo de Despachos. |
| Consolidações | `consolidation_subtotal` | DECIMAL(18,2) | Dimensão | Identificador de negócio para Consolidações. |
| Coleta Reversa | `reverse_pick_subtotal` | DECIMAL(18,2) | Dimensão | Atributo descritivo de Coleta Reversa. |
| Adiantamento | `advance_subtotal` | DECIMAL(18,2) | Dimensão | Atributo descritivo de Adiantamento. |
| Custos Frota | `fleet_costs_subtotal` | DECIMAL(18,2) | Dimensão | Valor monetário ou indicador financeiro de Custos Frota. |
| Adicionais | `additionals_subtotal` | DECIMAL(18,2) | Dimensão | Atributo descritivo de Adicionais. |
| Descontos | `discounts_subtotal` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Descontos. |
| Desconto/Valor | `discount_value` | DECIMAL(18,2) | Métrica | Valor monetário ou indicador financeiro de Desconto/Valor. |
| Liberação de Custo de Agregado/Comentários | `adjustment_comments` | NVARCHAR(MAX) | Métrica | Valor monetário ou indicador financeiro de Liberação de Custo de Agregado/Comentários. |
| IKS ID | `iks_id` | NVARCHAR(100) | Chave | Identificador de negócio para IKS ID. |
| Programação/Número | `programacao_sequence_code` | NVARCHAR(50) | Chave | Identificador de negócio para Programação/Número. |
| Programação/Início | `programacao_starting_at` | DATETIMEOFFSET | Data | Campo temporal associado a Programação/Início. |
| Programação/Término | `programacao_ending_at` | DATETIMEOFFSET | Data | Campo temporal associado a Programação/Término. |
| Carreta 1/Placa | `trailer1_license_plate` | NVARCHAR(10) | Chave | Identificador de negócio para Carreta 1/Placa. |
| Carreta 1/Capacidade Peso | `trailer1_weight_capacity` | DECIMAL(18,2) | Métrica | Indicador quantitativo relacionado a Carreta 1/Capacidade Peso. |
| Carreta 2/Placa | `trailer2_license_plate` | NVARCHAR(10) | Chave | Identificador de negócio para Carreta 2/Placa. |
| Carreta 2/Capacidade Peso | `trailer2_weight_capacity` | DECIMAL(18,2) | Métrica | Indicador quantitativo relacionado a Carreta 2/Capacidade Peso. |
| Veículo/Capacidade Peso | `vehicle_weight_capacity` | DECIMAL(18,2) | Métrica | Indicador quantitativo relacionado a Veículo/Capacidade Peso. |
| Veículo/Peso Cubado | `vehicle_cubic_weight` | DECIMAL(18,2) | Métrica | Indicador quantitativo relacionado a Veículo/Peso Cubado. |
| Capacidade Lotação Kg | `capacidade_kg` | DECIMAL(18,2) | Métrica | Indicador quantitativo relacionado a Capacidade Lotação Kg. |
| Descarregamento/Destinatários | `REPLACE(REPLACE(unloading_recipient_names, '[', ''), ']', '')` | NVARCHAR | Dimensão | Atributo descritivo de Descarregamento/Destinatários. |
| Entrega/Regiões | `REPLACE(REPLACE(REPLACE(delivery_region_names, '[', ''), ']', ''), '"', '')` | NVARCHAR | Dimensão | Atributo descritivo de Entrega/Regiões. |
| Programação/Cliente | `programacao_cliente` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Programação/Cliente. |
| Programação/Tipo Serviço | `programacao_tipo_servico` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Programação/Tipo Serviço. |
| Usuário/Emissor | `creation_user_name` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Usuário/Emissor. |
| Usuário/Ajuste | `adjustment_user_name` | NVARCHAR(255) | Dimensão | Atributo descritivo usado para segmentação em Usuário/Ajuste. |
| Liberação/Comentários Operacionais | `obs_operacional` | NVARCHAR(MAX) | Dimensão | Atributo descritivo de Liberação/Comentários Operacionais. |
| Comentários Fechamento | `obs_financeira` | NVARCHAR(MAX) | Data | Campo temporal associado a Comentários Fechamento. |
| Metadata | `metadata` | NVARCHAR(MAX) | Dimensão | Payload técnico bruto associado a Metadata para auditoria e rastreabilidade. |
| Data de extracao | `data_extracao` | DATETIME2 | Data | Data de referência para Data de extracao. |


