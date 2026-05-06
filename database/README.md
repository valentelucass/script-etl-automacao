# Banco de dados do projeto

Este README documenta as **tabelas** do projeto como um catálogo de pesquisa. O foco aqui é a estrutura persistida, a origem de cada carga e o significado das colunas. As **views ficaram de fora de propósito**, porque são derivadas dessas tabelas.

Fontes usadas para montar este documento:

- `database/tabelas/*.sql`
- `database/migrations/*.sql`
- `database/security_sqlite/*.sql`
- repositórios Java com DDL idempotente de fallback para estruturas técnicas

## Convenções gerais

- Quase todas as tabelas operacionais têm `metadata`, que guarda o **payload bruto completo** da origem.
- Quase todas as tabelas de extração têm `data_extracao`, que representa **quando o ETL gravou ou atualizou** aquela linha.
- Nem todo campo lido da API é promovido a coluna física. Quando isso acontece, o dado continua disponível em `metadata`.
- `faturas_por_cliente.serie_nfse` existe fisicamente, mas costuma ser preenchida na etapa de **enriquecimento por tabela ponte** com `fretes` e `faturas_graphql`.
- A fonte canônica para recriação do schema fica em `database/*.sql`, mesmo quando o Java também mantém DDL defensivo de fallback.
- Divergências mapeadas nesta conferência estão resumidas em `database/DIVERGENCIAS_CODIGO_SCHEMA.md`.

## Regra obrigatória para migrations

Sempre que uma migration alterar estrutura, contrato de leitura ou comportamento de recriação do banco, o espelhamento em `./database` deve ser feito na mesma entrega.

Isso significa atualizar, quando aplicável:

- `database/tabelas/` para que um banco recriado do zero já nasça no estado final correto;
- `database/views/` e `database/views-dimensao/` quando a mudança afetar colunas, joins, chaves ou semântica exposta em view;
- `database/indices/` quando a mudança exigir novo índice, remoção ou ajuste de índice;
- `database/validacao/` quando a mudança alterar critério de conferência, integridade ou diagnóstico operacional;
- `database/README.md` quando a estrutura documentada mudar;
- `database/executar_database.bat` quando entrar nova migration ou quando a ordem de execução precisar mudar.

Regra prática:

- migration sem reflexo no baseline é considerada entrega incompleta;
- recriar o banco com `database/executar_database.bat --recriar` deve levar ao mesmo estado estrutural esperado de um banco antigo que recebeu todas as migrations.

Checklist mínimo por mudança estrutural:

1. alterar a migration;
2. refletir a mudança no script-base correspondente em `tabelas/views/indices/validacao`;
3. incluir a migration nova em `database/executar_database.bat`;
4. revisar este `README` se o catálogo ou a regra operacional mudou.

Exemplo de pesquisa em `metadata`:

```sql
SELECT TOP 50
    id,
    JSON_VALUE(metadata, '$.requester') AS requester,
    JSON_VALUE(metadata, '$.comments')  AS comments
FROM dbo.coletas
ORDER BY data_extracao DESC;
```

## Mapa rápido das tabelas

### SQL Server

| Tabela | Grupo | Origem | Grão | Chave principal |
| --- | --- | --- | --- | --- |
| `dbo.coletas` | Negócio | GraphQL `pick` | 1 linha por coleta | `id` |
| `dbo.fretes` | Negócio | GraphQL `freight` + enriquecimento NFSe | 1 linha por frete | `id` |
| `dbo.manifestos` | Negócio | Data Export | 1 linha por combinação operacional de manifesto | `id` + unicidade por `chave_merge_hash` |
| `dbo.cotacoes` | Negócio | Data Export | 1 linha por cotação | `sequence_code` |
| `dbo.localizacao_cargas` | Negócio | Data Export | 1 linha por minuta/carga | `sequence_number` |
| `dbo.contas_a_pagar` | Negócio | Data Export | 1 linha por lançamento | `sequence_code` |
| `dbo.faturas_por_cliente` | Negócio | Data Export + enriquecimento | 1 linha por vínculo de faturamento | `unique_id` |
| `dbo.inventario` | Negócio | Data Export | 1 linha por ordem/minuta consolidada por chave técnica | `identificador_unico` |
| `dbo.sinistros` | Negócio | Data Export | 1 linha por sinistro + NF consolidada por chave técnica | `identificador_unico` |
| `dbo.faturas_graphql` | Negócio | GraphQL `creditCustomerBilling` | 1 linha por billing | `id` |
| `dbo.raster_viagens` | Negócio | Raster `getEventoFimViagem` | 1 linha por viagem/SM | `cod_solicitacao` |
| `dbo.raster_viagem_paradas` | Negócio | Raster `getEventoFimViagem.ColetasEntregas` | 1 linha por parada da viagem | `cod_solicitacao`, `ordem` |
| `dbo.dim_usuarios` | Referência | GraphQL `individual` | 1 linha por usuário | `user_id` |
| `dbo.dim_usuarios_historico` | Auditoria | Snapshot de usuários | 1 linha por mudança de estado | `id` |
| `dbo.log_extracoes` | Auditoria | Runtime ETL | 1 linha por execução de entidade | `id` |
| `dbo.page_audit` | Auditoria | Runtime ETL | 1 linha por página requisitada | `id` |
| `dbo.sys_execution_history` | Auditoria | Runtime ETL | 1 linha por execução do pipeline | `id` |
| `dbo.sys_auditoria_temp` | Auditoria | Runtime ETL | 1 linha por campo detectado em auditoria | sem PK declarada |
| `dbo.sys_execution_audit` | Controle | Runtime ETL | 1 linha por `execution_uuid` + entidade | `execution_uuid`, `entidade` |
| `dbo.sys_execution_watermark` | Controle | Runtime ETL | 1 linha por entidade | `entidade` |
| `dbo.schema_migrations` | Controle | Migrations SQL | 1 linha por migration | `migration_id` |
| `dbo.etl_invalid_records` | Auditoria | Runtime ETL | 1 linha por descarte inválido | `id` |

### SQLite local de autenticação

| Tabela | Grupo | Origem | Grão | Chave principal |
| --- | --- | --- | --- | --- |
| `users` | Segurança | SQLite local | 1 linha por usuário local | `id` |
| `auth_audit` | Segurança | SQLite local | 1 linha por evento de autenticação | `id` |

## Relações úteis para pesquisa

- `dbo.manifestos.pick_sequence_code = dbo.coletas.sequence_code`
  - relação já endurecida no schema por FK seletiva `FK_manifestos_pick_sequence_code_coletas` quando não há órfãos
- `dbo.fretes.accounting_credit_id = dbo.faturas_graphql.id`
- `dbo.inventario.numero_minuta = dbo.fretes.corporation_sequence_number`
- `dbo.sinistros.corporation_sequence_number = dbo.fretes.corporation_sequence_number`
- `dbo.faturas_graphql.document = dbo.faturas_por_cliente.fit_ant_document`
- `dbo.fretes.nfse_number` e `dbo.fretes.nfse_series` alimentam `dbo.faturas_por_cliente.numero_nfse` e `dbo.faturas_por_cliente.serie_nfse`
- `dbo.coletas.cancellation_user_id` e `dbo.coletas.destroy_user_id` podem ser ligados a `dbo.dim_usuarios.user_id`
- `dbo.raster_viagem_paradas.cod_solicitacao = dbo.raster_viagens.cod_solicitacao`
- `dbo.dim_usuarios_historico.user_id = dbo.dim_usuarios.user_id`
- `dbo.page_audit.execution_uuid` conversa com `dbo.sys_execution_audit.execution_uuid`
- `dbo.sys_execution_audit.entidade = dbo.sys_execution_watermark.entidade`

## Tabelas de negócio e referência

### `dbo.coletas`

- Fonte: GraphQL `pick`
- Grão: 1 linha por coleta
- Chaves: PK `id`; `sequence_code` é `NOT NULL` e `UNIQUE`
- Observação importante: o ETL lê mais campos do que a tabela promove. Hoje ficam só em `metadata` campos como `requester`, `comments`, `agentId`, `serviceStartHour`, `serviceEndHour`, `cargoClassificationId`, `costCenterId`, `invoicesCubedWeight`, `lunchBreakEndHour`, `lunchBreakStartHour`, `notificationEmail`, `notificationPhone`, `pickTypeId`, `pickupLocationId`, além de IDs e documentos que aparecem na entidade como `clienteId`, `usuarioId` e `filialCnpj` (payloads `customer.id`, `user.id` e `corporation.person.cnpj`).

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `id` | `NVARCHAR(50)` | ID técnico GraphQL da coleta. |
| `sequence_code` | `BIGINT NOT NULL` | Número operacional da coleta/minuta; chave candidata usada pela FK de manifestos. |
| `request_date` | `DATE` | Data de solicitação da coleta. |
| `request_hour` | `NVARCHAR(8)` | Hora da solicitação (`HH:MM:SS`). |
| `service_date` | `DATE` | Data de execução/serviço da coleta. |
| `status` | `NVARCHAR(50)` | Status bruto da API (`finished`, `canceled`, `pending`, etc.). |
| `total_value` | `DECIMAL(18,2)` | Valor total associado à coleta. |
| `total_weight` | `DECIMAL(18,3)` | Peso total informado para a coleta. |
| `total_volumes` | `INT` | Quantidade total de volumes. |
| `cliente_nome` | `NVARCHAR(255)` | Nome do cliente vinculado à coleta. |
| `cliente_doc` | `NVARCHAR(50)` | Documento do cliente, normalmente CNPJ. |
| `local_coleta` | `NVARCHAR(500)` | Logradouro da coleta (`pickAddress.line1`). |
| `numero_coleta` | `NVARCHAR(50)` | Número do endereço da coleta. |
| `complemento_coleta` | `NVARCHAR(255)` | Complemento do endereço (`pickAddress.line2`). |
| `cidade_coleta` | `NVARCHAR(255)` | Cidade da coleta. |
| `bairro_coleta` | `NVARCHAR(255)` | Bairro da coleta. |
| `uf_coleta` | `NVARCHAR(10)` | UF da coleta. |
| `cep_coleta` | `NVARCHAR(20)` | CEP da coleta. |
| `filial_id` | `BIGINT` | ID da corporação/filial emissora. |
| `filial_nome` | `NVARCHAR(255)` | Apelido da filial emissora. |
| `usuario_nome` | `NVARCHAR(255)` | Nome do usuário associado à coleta no payload. |
| `finish_date` | `DATE` | Data de finalização da coleta. |
| `manifest_item_pick_id` | `BIGINT` | ID do item de manifesto relacionado à coleta. |
| `vehicle_type_id` | `BIGINT` | ID do tipo de veículo solicitado. |
| `cancellation_reason` | `NVARCHAR(MAX)` | Motivo de cancelamento, quando houver. |
| `cancellation_user_id` | `BIGINT` | Usuário que cancelou a coleta. |
| `destroy_reason` | `NVARCHAR(MAX)` | Motivo de destruição/invalidação do registro. |
| `destroy_user_id` | `BIGINT` | Usuário que destruiu/inativou a coleta. |
| `status_updated_at` | `NVARCHAR(50)` | Data/hora bruta da última atualização de status. |
| `taxed_weight` | `DECIMAL(18,3)` | Peso taxado da coleta. |
| `pick_region` | `NVARCHAR(255)` | Região derivada da coleta, normalmente `Cidade - UF`. |
| `last_occurrence` | `NVARCHAR(50)` | Tradução amigável do status. |
| `acao_ocorrencia` | `NVARCHAR(255)` | Ação/resultado derivado da ocorrência. |
| `numero_tentativas` | `INT` | Número de tentativas derivado pela regra de negócio. |
| `metadata` | `NVARCHAR(MAX)` | JSON bruto da coleta. |
| `data_extracao` | `DATETIME2` | Momento da gravação/atualização no banco. |

### `dbo.fretes`

- Fonte: GraphQL `freight`
- Grão: 1 linha por frete
- Chaves: PK `id`
- Observação importante: as colunas `nfse_integration_id`, `nfse_status`, `nfse_issued_at`, `nfse_cancelation_reason`, `nfse_pdf_service_url`, `nfse_corporation_id`, `nfse_service_description` e `nfse_xml_document` são tipicamente preenchidas por **enriquecimento posterior de NFSe**, não pela consulta principal de fretes.

#### Identificação, datas e operação

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `id` | `BIGINT` | ID técnico do frete. |
| `servico_em` | `DATETIMEOFFSET` | Data/hora do serviço (`serviceAt`). |
| `criado_em` | `DATETIMEOFFSET` | Data/hora de criação do frete. |
| `status` | `NVARCHAR(50)` | Status operacional do frete. |
| `modal` | `NVARCHAR(50)` | Modal logístico do frete. |
| `tipo_frete` | `NVARCHAR(100)` | Tipo de frete retornado pela API. |
| `accounting_credit_id` | `BIGINT` | ID do crédito contábil/faturamento ligado ao frete. |
| `accounting_credit_installment_id` | `BIGINT` | ID da parcela do crédito contábil. |
| `id_corporacao` | `BIGINT` | ID da corporação/filial dona do frete. |
| `id_cidade_destino` | `BIGINT` | ID da cidade de destino. |
| `data_previsao_entrega` | `DATE` | Data prevista de entrega. |
| `service_date` | `DATE` | Data operacional do frete. |
| `reference_number` | `NVARCHAR(100)` | Referência externa/comercial do frete. |
| `service_type` | `INT` | Tipo de serviço em código numérico. |
| `modal_cte` | `NVARCHAR(50)` | Modal fiscal do CT-e. |
| `payment_type` | `NVARCHAR(50)` | Tipo de pagamento do frete. |
| `previous_document_type` | `NVARCHAR(50)` | Tipo de documento anterior relacionado. |
| `globalized` | `BIT` | Indica frete globalizado. |
| `globalized_type` | `NVARCHAR(50)` | Tipo/classificação da globalização. |

#### Envolvidos, origem e destino

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `pagador_id` | `BIGINT` | ID do pagador. |
| `pagador_nome` | `NVARCHAR(255)` | Nome do pagador. |
| `pagador_documento` | `NVARCHAR(50)` | CNPJ/CPF do pagador. |
| `remetente_id` | `BIGINT` | ID do remetente. |
| `remetente_nome` | `NVARCHAR(255)` | Nome do remetente. |
| `remetente_documento` | `NVARCHAR(50)` | CNPJ/CPF do remetente. |
| `origem_cidade` | `NVARCHAR(255)` | Cidade de origem. |
| `origem_uf` | `NVARCHAR(10)` | UF de origem. |
| `destinatario_id` | `BIGINT` | ID do destinatário. |
| `destinatario_nome` | `NVARCHAR(255)` | Nome do destinatário. |
| `destinatario_documento` | `NVARCHAR(50)` | CNPJ/CPF do destinatário. |
| `destino_cidade` | `NVARCHAR(255)` | Cidade de destino. |
| `destino_uf` | `NVARCHAR(10)` | UF de destino. |
| `filial_nome` | `NVARCHAR(255)` | Nome/apelido principal da filial. |
| `filial_apelido` | `NVARCHAR(255)` | Apelido consolidado da filial. |
| `filial_cnpj` | `NVARCHAR(50)` | CNPJ da filial emissora. |
| `tabela_preco_nome` | `NVARCHAR(255)` | Nome da tabela de preço. |
| `classificacao_nome` | `NVARCHAR(255)` | Classificação comercial/logística do frete. |
| `centro_custo_nome` | `NVARCHAR(255)` | Centro de custo associado. |
| `usuario_nome` | `NVARCHAR(255)` | Usuário responsável/associado ao frete. |

#### Volume, peso e valores principais

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `valor_total` | `DECIMAL(18,2)` | Valor total do frete. |
| `valor_notas` | `DECIMAL(18,2)` | Valor somado das notas fiscais. |
| `peso_notas` | `DECIMAL(18,3)` | Peso somado das notas. |
| `invoices_total_volumes` | `INT` | Total de volumes das notas. |
| `taxed_weight` | `DECIMAL(18,3)` | Peso taxado. |
| `real_weight` | `DECIMAL(18,3)` | Peso real. |
| `cubages_cubed_weight` | `DECIMAL(18,3)` | Peso cubado calculado. |
| `total_cubic_volume` | `DECIMAL(18,3)` | Cubagem total. |
| `subtotal` | `DECIMAL(18,2)` | Subtotal base do frete. |
| `products_value` | `DECIMAL(18,2)` | Valor dos produtos. |
| `km` | `DECIMAL(18,2)` | Quilometragem do frete. |
| `numero_nota_fiscal` | `NVARCHAR(MAX)` | Lista textual de números de nota fiscal vinculados. |

#### CT-e, NFS-e e cobrança

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `chave_cte` | `NVARCHAR(100)` | Chave do CT-e. |
| `numero_cte` | `INT` | Número do CT-e. |
| `serie_cte` | `INT` | Série do CT-e. |
| `cte_id` | `BIGINT` | ID técnico do CT-e na API. |
| `cte_emission_type` | `NVARCHAR(50)` | Tipo de emissão do CT-e. |
| `cte_created_at` | `DATETIMEOFFSET` | Data/hora de criação do CT-e. |
| `cte_issued_at` | `DATETIMEOFFSET` | Data/hora de emissão do CT-e. |
| `nfse_series` | `NVARCHAR(50)` | Série da NFS-e. |
| `nfse_number` | `INT` | Número da NFS-e. |
| `nfse_integration_id` | `NVARCHAR(50)` | ID de integração da NFS-e. |
| `nfse_status` | `NVARCHAR(50)` | Status da NFS-e. |
| `nfse_issued_at` | `DATE` | Data de emissão da NFS-e enriquecida. |
| `nfse_cancelation_reason` | `NVARCHAR(255)` | Motivo de cancelamento da NFS-e. |
| `nfse_pdf_service_url` | `NVARCHAR(1000)` | URL do PDF/serviço da NFS-e. |
| `nfse_corporation_id` | `BIGINT` | Corporação emissora da NFS-e. |
| `nfse_service_description` | `NVARCHAR(500)` | Descrição do serviço na NFS-e. |
| `nfse_xml_document` | `NVARCHAR(MAX)` | XML completo da NFS-e. |

#### Custos, seguros e fiscais

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `insurance_enabled` | `BIT` | Indica se há seguro habilitado. |
| `insurance_id` | `BIGINT` | ID do seguro. |
| `insurance_accountable_type` | `INT` | Tipo contábil do seguro. |
| `insured_value` | `DECIMAL(18,2)` | Valor segurado. |
| `gris_subtotal` | `DECIMAL(18,2)` | Subtotal de GRIS. |
| `tde_subtotal` | `DECIMAL(18,2)` | Subtotal de TDE. |
| `redispatch_subtotal` | `DECIMAL(18,2)` | Subtotal de redespacho. |
| `suframa_subtotal` | `DECIMAL(18,2)` | Subtotal de SUFRAMA. |
| `trt_subtotal` | `DECIMAL(18,2)` | Subtotal de TRT. |
| `other_fees` | `DECIMAL(18,2)` | Outras taxas. |
| `payment_accountable_type` | `INT` | Tipo contábil do pagamento. |
| `sec_cat_subtotal` | `DECIMAL(18,2)` | Subtotal de categoria SEC/CAT. |
| `price_table_accountable_type` | `INT` | Tipo contábil da tabela de preço. |
| `freight_weight_subtotal` | `DECIMAL(18,2)` | Subtotal por peso de frete. |
| `ad_valorem_subtotal` | `DECIMAL(18,2)` | Subtotal de ad valorem. |
| `toll_subtotal` | `DECIMAL(18,2)` | Subtotal de pedágio. |
| `itr_subtotal` | `DECIMAL(18,2)` | Subtotal de ITR. |
| `fiscal_calculation_basis` | `DECIMAL(18,2)` | Base de cálculo fiscal. |
| `fiscal_tax_rate` | `DECIMAL(18,6)` | Alíquota principal do imposto. |
| `fiscal_pis_rate` | `DECIMAL(18,6)` | Alíquota de PIS. |
| `fiscal_cofins_rate` | `DECIMAL(18,6)` | Alíquota de COFINS. |
| `fiscal_has_difal` | `BIT` | Indica se há DIFAL. |
| `fiscal_difal_origin` | `DECIMAL(18,2)` | Valor do DIFAL de origem. |
| `fiscal_difal_destination` | `DECIMAL(18,2)` | Valor do DIFAL de destino. |
| `fiscal_cst_type` | `NVARCHAR(10)` | CST fiscal. |
| `fiscal_cfop_code` | `NVARCHAR(10)` | Código CFOP. |
| `fiscal_tax_value` | `DECIMAL(18,2)` | Valor do imposto principal. |
| `fiscal_pis_value` | `DECIMAL(18,2)` | Valor de PIS. |
| `fiscal_cofins_value` | `DECIMAL(18,2)` | Valor de COFINS. |

#### Backup de payload

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `metadata` | `NVARCHAR(MAX)` | JSON bruto do frete. |
| `data_extracao` | `DATETIME2` | Momento da gravação/atualização no banco. |

### `dbo.manifestos`

- Fonte: Data Export
- Grão: 1 linha por combinação operacional de manifesto
- Chaves:
  - PK física: `id`
  - Identidade operacional de apoio: `identificador_unico`
  - Unicidade real do merge: `chave_merge_hash`
- Observação importante: a mesma `sequence_code` pode aparecer mais de uma vez quando muda `pick_sequence_code` ou `mdfe_number`. Isso é comportamento esperado.

#### Identidade e datas

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `id` | `BIGINT IDENTITY` | PK técnica da tabela. |
| `sequence_code` | `BIGINT` | Código sequencial do manifesto. |
| `identificador_unico` | `NVARCHAR(100)` | Identidade de negócio calculada pela aplicação. |
| `status` | `NVARCHAR(50)` | Status operacional do manifesto. |
| `created_at` | `DATETIMEOFFSET` | Criação do manifesto. |
| `departured_at` | `DATETIMEOFFSET` | Saída/partida do manifesto. |
| `closed_at` | `DATETIMEOFFSET` | Fechamento do manifesto. |
| `finished_at` | `DATETIMEOFFSET` | Finalização do manifesto. |
| `mobile_read_at` | `DATETIMEOFFSET` | Momento de leitura/atualização mobile. |
| `data_extracao` | `DATETIME2` | Momento da gravação/atualização no banco. |
| `chave_merge_hash` | `Coluna computada persistida` | Concatenação `sequence_code|coalesce(pick_sequence_code, identificador_unico)|mdfe_number`, base da `UNIQUE`. |

#### MDF-e, contrato e programação

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `mdfe_number` | `INT` | Número do MDF-e. |
| `mdfe_key` | `NVARCHAR(100)` | Chave do MDF-e. |
| `mdfe_status` | `NVARCHAR(50)` | Status do MDF-e. |
| `distribution_pole` | `NVARCHAR(255)` | Polo de distribuição. |
| `classification` | `NVARCHAR(255)` | Classificação do manifesto. |
| `pick_sequence_code` | `BIGINT` | `sequence_code` da coleta associada. |
| `contract_number` | `NVARCHAR(50)` | Número do contrato. |
| `contract_type` | `NVARCHAR(50)` | Tipo do contrato. |
| `calculation_type` | `NVARCHAR(50)` | Tipo de cálculo aplicado. |
| `cargo_type` | `NVARCHAR(255)` | Tipo de carga. |
| `contract_status` | `NVARCHAR(50)` | Status do contrato. |
| `iks_id` | `NVARCHAR(100)` | Identificador externo/IKS. |
| `programacao_sequence_code` | `NVARCHAR(50)` | Código da programação logística. |
| `programacao_starting_at` | `DATETIMEOFFSET` | Início da programação. |
| `programacao_ending_at` | `DATETIMEOFFSET` | Fim da programação. |
| `programacao_cliente` | `NVARCHAR(255)` | Cliente ligado à programação. |
| `programacao_tipo_servico` | `NVARCHAR(255)` | Tipo de serviço da programação. |
| `manual_km` | `BIT` | Indica quilometragem manual. |
| `generate_mdfe` | `BIT` | Indica se o processo gera MDF-e. |
| `monitoring_request` | `BIT` | Indica se há solicitação de monitoramento. |
| `uniq_destinations_count` | `INT` | Quantidade de destinos únicos. |

#### Veículo, motorista e responsáveis

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `vehicle_plate` | `NVARCHAR(10)` | Placa do veículo principal. |
| `vehicle_type` | `NVARCHAR(255)` | Tipo do veículo. |
| `vehicle_owner` | `NVARCHAR(255)` | Proprietário do veículo. |
| `driver_name` | `NVARCHAR(255)` | Nome do motorista. |
| `branch_nickname` | `NVARCHAR(255)` | Filial/apelido responsável pelo manifesto. |
| `vehicle_departure_km` | `INT` | KM de saída do veículo. |
| `closing_km` | `INT` | KM de fechamento. |
| `traveled_km` | `INT` | KM percorrido. |
| `km` | `DECIMAL(18,2)` | Quilometragem total consolidada. |
| `trailer1_license_plate` | `NVARCHAR(10)` | Placa do primeiro reboque. |
| `trailer1_weight_capacity` | `DECIMAL(18,2)` | Capacidade do primeiro reboque. |
| `trailer2_license_plate` | `NVARCHAR(10)` | Placa do segundo reboque. |
| `trailer2_weight_capacity` | `DECIMAL(18,2)` | Capacidade do segundo reboque. |
| `vehicle_weight_capacity` | `DECIMAL(18,2)` | Capacidade de peso do veículo. |
| `vehicle_cubic_weight` | `DECIMAL(18,2)` | Capacidade/carga cúbica do veículo. |
| `capacidade_kg` | `DECIMAL(18,2)` | Capacidade do veículo em kg, promovida para pesquisa direta. |
| `creation_user_name` | `NVARCHAR(255)` | Usuário criador do manifesto. |
| `adjustment_user_name` | `NVARCHAR(255)` | Usuário que fez ajuste posterior. |

#### Volumes, itens e abrangência operacional

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `invoices_count` | `INT` | Quantidade de notas fiscais. |
| `invoices_volumes` | `INT` | Quantidade de volumes das notas. |
| `invoices_weight` | `DECIMAL(18,3)` | Peso das notas. |
| `total_taxed_weight` | `DECIMAL(18,3)` | Peso taxado total. |
| `total_cubic_volume` | `DECIMAL(18,6)` | Cubagem total. |
| `invoices_value` | `DECIMAL(18,2)` | Valor total das notas. |
| `manifest_freights_total` | `DECIMAL(18,2)` | Total de fretes do manifesto. |
| `delivery_manifest_items_count` | `INT` | Quantidade de itens de entrega. |
| `transfer_manifest_items_count` | `INT` | Quantidade de itens de transferência. |
| `pick_manifest_items_count` | `INT` | Quantidade de itens de coleta. |
| `dispatch_draft_manifest_items_count` | `INT` | Quantidade de itens de despacho rascunho. |
| `consolidation_manifest_items_count` | `INT` | Quantidade de itens de consolidação. |
| `reverse_pick_manifest_items_count` | `INT` | Quantidade de itens de coleta reversa. |
| `manifest_items_count` | `INT` | Total geral de itens do manifesto. |
| `finalized_manifest_items_count` | `INT` | Itens finalizados. |
| `calculated_pick_count` | `INT` | Contagem calculada de coletas. |
| `calculated_delivery_count` | `INT` | Contagem calculada de entregas. |
| `calculated_dispatch_count` | `INT` | Contagem calculada de despachos. |
| `calculated_consolidation_count` | `INT` | Contagem calculada de consolidações. |
| `calculated_reverse_pick_count` | `INT` | Contagem calculada de coletas reversas. |
| `unloading_recipient_names` | `NVARCHAR(MAX)` | Lista serializada de destinatários de descarga. |
| `delivery_region_names` | `NVARCHAR(MAX)` | Lista serializada de regiões de entrega. |

#### Custos, totais e pagamentos

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `daily_subtotal` | `DECIMAL(18,2)` | Subtotal diário. |
| `total_cost` | `DECIMAL(18,2)` | Custo total. |
| `freight_subtotal` | `DECIMAL(18,2)` | Subtotal de frete. |
| `fuel_subtotal` | `DECIMAL(18,2)` | Subtotal de combustível. |
| `toll_subtotal` | `DECIMAL(18,2)` | Subtotal de pedágio. |
| `driver_services_total` | `DECIMAL(18,2)` | Total de serviços do motorista. |
| `operational_expenses_total` | `DECIMAL(18,2)` | Total de despesas operacionais. |
| `inss_value` | `DECIMAL(18,2)` | Valor de INSS. |
| `sest_senat_value` | `DECIMAL(18,2)` | Valor de SEST/SENAT. |
| `ir_value` | `DECIMAL(18,2)` | Valor de imposto de renda. |
| `paying_total` | `DECIMAL(18,2)` | Total a pagar. |
| `pick_subtotal` | `DECIMAL(18,2)` | Subtotal de coletas. |
| `delivery_subtotal` | `DECIMAL(18,2)` | Subtotal de entregas. |
| `dispatch_subtotal` | `DECIMAL(18,2)` | Subtotal de despachos. |
| `consolidation_subtotal` | `DECIMAL(18,2)` | Subtotal de consolidação. |
| `reverse_pick_subtotal` | `DECIMAL(18,2)` | Subtotal de coleta reversa. |
| `advance_subtotal` | `DECIMAL(18,2)` | Subtotal de adiantamento. |
| `fleet_costs_subtotal` | `DECIMAL(18,2)` | Subtotal de custos de frota. |
| `additionals_subtotal` | `DECIMAL(18,2)` | Subtotal de adicionais. |
| `discounts_subtotal` | `DECIMAL(18,2)` | Subtotal de descontos. |
| `discount_value` | `DECIMAL(18,2)` | Valor final de desconto. |

#### Observações e backup de payload

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `adjustment_comments` | `NVARCHAR(MAX)` | Comentários de ajuste. |
| `obs_operacional` | `NVARCHAR(MAX)` | Comentários operacionais/liberação. |
| `obs_financeira` | `NVARCHAR(MAX)` | Comentários financeiros/fechamento. |
| `metadata` | `NVARCHAR(MAX)` | JSON bruto do manifesto. |

### `dbo.cotacoes`

- Fonte: Data Export
- Grão: 1 linha por cotação
- Chaves: PK `sequence_code`

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `sequence_code` | `BIGINT` | Código sequencial da cotação. |
| `requested_at` | `DATETIMEOFFSET` | Data/hora da solicitação da cotação. |
| `operation_type` | `NVARCHAR(100)` | Tipo de operação solicitada. |
| `customer_doc` | `NVARCHAR(14)` | Documento do cliente. |
| `customer_name` | `NVARCHAR(255)` | Nome do cliente. |
| `origin_city` | `NVARCHAR(100)` | Cidade de origem. |
| `origin_state` | `NVARCHAR(2)` | UF de origem. |
| `destination_city` | `NVARCHAR(100)` | Cidade de destino. |
| `destination_state` | `NVARCHAR(2)` | UF de destino. |
| `price_table` | `NVARCHAR(255)` | Tabela de preço aplicada. |
| `volumes` | `INT` | Quantidade de volumes. |
| `taxed_weight` | `DECIMAL(18,3)` | Peso taxado. |
| `invoices_value` | `DECIMAL(18,2)` | Valor das notas. |
| `total_value` | `DECIMAL(18,2)` | Valor total cotado. |
| `user_name` | `NVARCHAR(255)` | Usuário que gerou a cotação. |
| `branch_nickname` | `NVARCHAR(255)` | Filial vinculada à cotação. |
| `company_name` | `NVARCHAR(255)` | Nome da empresa/corporação. |
| `requester_name` | `NVARCHAR(255)` | Solicitante da cotação. |
| `real_weight` | `NVARCHAR(50)` | Peso real em formato textual da origem. |
| `origin_postal_code` | `NVARCHAR(10)` | CEP de origem. |
| `destination_postal_code` | `NVARCHAR(10)` | CEP de destino. |
| `customer_nickname` | `NVARCHAR(255)` | Apelido do cliente. |
| `sender_document` | `NVARCHAR(14)` | Documento do remetente. |
| `sender_nickname` | `NVARCHAR(255)` | Apelido do remetente. |
| `receiver_document` | `NVARCHAR(14)` | Documento do destinatário. |
| `receiver_nickname` | `NVARCHAR(255)` | Apelido do destinatário. |
| `disapprove_comments` | `NVARCHAR(MAX)` | Comentários de reprovação/desaprovação. |
| `freight_comments` | `NVARCHAR(MAX)` | Comentários livres do frete/cotação. |
| `discount_subtotal` | `DECIMAL(18,6)` | Subtotal de desconto. |
| `itr_subtotal` | `DECIMAL(18,6)` | Subtotal de ITR. |
| `tde_subtotal` | `DECIMAL(18,6)` | Subtotal de TDE. |
| `collect_subtotal` | `DECIMAL(18,6)` | Subtotal de coleta. |
| `delivery_subtotal` | `DECIMAL(18,6)` | Subtotal de entrega. |
| `other_fees` | `DECIMAL(18,6)` | Outras taxas. |
| `cte_issued_at` | `DATETIMEOFFSET` | Data/hora de emissão do CT-e ligado à cotação. |
| `nfse_issued_at` | `DATETIMEOFFSET` | Data/hora de emissão da NFS-e ligada à cotação. |
| `metadata` | `NVARCHAR(MAX)` | JSON bruto da cotação. |
| `data_extracao` | `DATETIME2` | Momento da gravação/atualização no banco. |

### `dbo.localizacao_cargas`

- Fonte: Data Export
- Grão: 1 linha por carga/minuta
- Chaves: PK `sequence_number`
- Observação importante: `taxed_weight` e `invoices_value` ficam como texto porque a origem pode variar de formato.

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `sequence_number` | `BIGINT` | Número da minuta/carga. |
| `type` | `NVARCHAR(100)` | Tipo da carga. |
| `service_at` | `DATETIMEOFFSET` | Data/hora do frete/serviço. |
| `invoices_volumes` | `INT` | Quantidade de volumes. |
| `taxed_weight` | `NVARCHAR(50)` | Peso taxado em formato textual da origem. |
| `invoices_value` | `NVARCHAR(50)` | Valor das notas em formato textual da origem. |
| `total_value` | `DECIMAL(18,2)` | Valor do frete. |
| `service_type` | `NVARCHAR(50)` | Tipo de serviço. |
| `branch_nickname` | `NVARCHAR(255)` | Filial emissora. |
| `predicted_delivery_at` | `DATETIMEOFFSET` | Previsão de entrega. |
| `destination_location_name` | `NVARCHAR(255)` | Região/localização de destino. |
| `destination_branch_nickname` | `NVARCHAR(255)` | Filial de destino. |
| `classification` | `NVARCHAR(255)` | Classificação da carga. |
| `status` | `NVARCHAR(50)` | Status atual da carga. |
| `status_branch_nickname` | `NVARCHAR(255)` | Filial atual segundo o status. |
| `origin_location_name` | `NVARCHAR(255)` | Região/localização de origem. |
| `origin_branch_nickname` | `NVARCHAR(255)` | Filial de origem. |
| `fit_fln_cln_nickname` | `NVARCHAR(255)` | Localização atual consolidada da carga. |
| `metadata` | `NVARCHAR(MAX)` | JSON bruto da localização de carga. |
| `data_extracao` | `DATETIME2` | Momento da gravação/atualização no banco. |

### `dbo.contas_a_pagar`

- Fonte: Data Export
- Grão: 1 linha por lançamento/conta a pagar
- Chaves: PK `sequence_code`
- Observação importante: `status_pagamento` é normalizado para `PAGO` ou `ABERTO`; `tipo_lancamento` e `classificacao_contabil` são traduzidos para termos mais legíveis.

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `sequence_code` | `BIGINT` | Código sequencial da conta a pagar. |
| `document_number` | `VARCHAR(100)` | Número do documento financeiro. |
| `issue_date` | `DATE` | Data de emissão do documento. |
| `tipo_lancamento` | `NVARCHAR(100)` | Tipo de lançamento traduzido/normalizado. |
| `valor_original` | `DECIMAL(18,2)` | Valor original. |
| `valor_juros` | `DECIMAL(18,2)` | Valor de juros. |
| `valor_desconto` | `DECIMAL(18,2)` | Valor de desconto. |
| `valor_a_pagar` | `DECIMAL(18,2)` | Valor em aberto para pagamento. |
| `valor_pago` | `DECIMAL(18,2)` | Valor efetivamente pago. |
| `status_pagamento` | `NVARCHAR(50)` | Status consolidado do pagamento. |
| `mes_competencia` | `INT` | Mês de competência. |
| `ano_competencia` | `INT` | Ano de competência. |
| `data_criacao` | `DATETIMEOFFSET` | Data/hora de criação do lançamento. |
| `data_liquidacao` | `DATE` | Data de liquidação. |
| `data_transacao` | `DATE` | Data da transação. |
| `nome_fornecedor` | `NVARCHAR(255)` | Nome do fornecedor. |
| `nome_filial` | `NVARCHAR(255)` | Nome da filial. |
| `nome_centro_custo` | `NVARCHAR(255)` | Centro de custo. |
| `valor_centro_custo` | `DECIMAL(18,2)` | Valor atribuído ao centro de custo. |
| `classificacao_contabil` | `NVARCHAR(100)` | Classificação contábil traduzida. |
| `descricao_contabil` | `NVARCHAR(255)` | Descrição da conta contábil. |
| `valor_contabil` | `DECIMAL(18,2)` | Valor contábil. |
| `area_lancamento` | `NVARCHAR(255)` | Área responsável pelo lançamento. |
| `observacoes` | `NVARCHAR(MAX)` | Observações do lançamento. |
| `descricao_despesa` | `NVARCHAR(MAX)` | Descrição textual da despesa. |
| `nome_usuario` | `NVARCHAR(255)` | Usuário responsável/associado. |
| `reconciliado` | `BIT` | Indica se o lançamento foi reconciliado. |
| `metadata` | `NVARCHAR(MAX)` | JSON bruto da conta a pagar. |
| `data_extracao` | `DATETIME2` | Momento da gravação/atualização no banco. |

### `dbo.faturas_por_cliente`

- Fonte: Data Export, com enriquecimento posterior
- Grão: 1 linha por vínculo de faturamento consolidado
- Chaves: PK `unique_id`
- Observações importantes:
  - `unique_id` é um identificador canônico calculado pela aplicação, normalmente baseado em hash.
  - `numero_nfse` e `serie_nfse` podem ser enriquecidos depois cruzando `faturas_graphql` e `fretes`.

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `unique_id` | `NVARCHAR(100)` | Identificador único canônico do registro de faturamento. |
| `valor_frete` | `DECIMAL(18,2)` | Valor do frete. |
| `valor_fatura` | `DECIMAL(18,2)` | Valor da fatura. |
| `third_party_ctes_value` | `DECIMAL(18,2)` | Valor de CT-es de terceiros vinculados. |
| `numero_cte` | `BIGINT` | Número do CT-e. |
| `chave_cte` | `NVARCHAR(100)` | Chave do CT-e. |
| `numero_nfse` | `BIGINT` | Número da NFS-e. |
| `serie_nfse` | `NVARCHAR(50)` | Série da NFS-e, geralmente enriquecida depois. |
| `status_cte` | `NVARCHAR(255)` | Status traduzido do CT-e. |
| `status_cte_result` | `NVARCHAR(MAX)` | Texto/resultados detalhados do status do CT-e. |
| `data_emissao_cte` | `DATETIMEOFFSET` | Data/hora de emissão do CT-e. |
| `numero_fatura` | `NVARCHAR(50)` | Número/documento da fatura. |
| `data_emissao_fatura` | `DATE` | Data de emissão da fatura. |
| `data_vencimento_fatura` | `DATE` | Data de vencimento da fatura. |
| `data_baixa_fatura` | `DATE` | Data de baixa/liquidação da fatura. |
| `fit_ant_ils_original_due_date` | `DATE` | Vencimento original legado da fatura. |
| `fit_ant_document` | `NVARCHAR(50)` | Documento legado da fatura, usado como ponte para `faturas_graphql.document`. |
| `fit_ant_issue_date` | `DATE` | Data de emissão legada da fatura. |
| `fit_ant_value` | `DECIMAL(18,2)` | Valor legado da fatura. |
| `filial` | `NVARCHAR(255)` | Filial do faturamento. |
| `tipo_frete` | `NVARCHAR(100)` | Tipo de frete consolidado. |
| `classificacao` | `NVARCHAR(100)` | Classificação do registro. |
| `estado` | `NVARCHAR(50)` | Estado/UF ou estado operacional recebido no relatório. |
| `pagador_nome` | `NVARCHAR(255)` | Nome do pagador. |
| `pagador_documento` | `NVARCHAR(50)` | Documento do pagador. |
| `remetente_nome` | `NVARCHAR(255)` | Nome do remetente. |
| `remetente_documento` | `NVARCHAR(50)` | Documento do remetente. |
| `destinatario_nome` | `NVARCHAR(255)` | Nome do destinatário. |
| `destinatario_documento` | `NVARCHAR(50)` | Documento do destinatário. |
| `vendedor_nome` | `NVARCHAR(255)` | Nome do vendedor/comercial. |
| `notas_fiscais` | `NVARCHAR(MAX)` | Lista textual de notas fiscais. |
| `pedidos_cliente` | `NVARCHAR(MAX)` | Lista textual de pedidos do cliente. |
| `metadata` | `NVARCHAR(MAX)` | JSON bruto do relatório de faturamento. |
| `data_extracao` | `DATETIME2` | Momento da gravação/atualização no banco. |

### `dbo.faturas_graphql`

- Fonte: GraphQL `creditCustomerBilling`
- Grão: 1 linha por billing
- Chaves: PK `id`
- Observação importante: `status`, `original_due_date`, `nfse_numero`, `carteira_banco`, `instrucao_boleto`, `banco_nome` e `metodo_pagamento` são lidos da **primeira parcela** (`installments[0]`) quando existe.

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `id` | `BIGINT` | ID técnico do billing. |
| `document` | `NVARCHAR(50)` | Documento/número principal da cobrança. |
| `issue_date` | `DATE` | Data de emissão. |
| `due_date` | `DATE` | Data de vencimento atual. |
| `original_due_date` | `DATE` | Vencimento original da primeira parcela. |
| `value` | `DECIMAL(18,2)` | Valor nominal do billing. |
| `paid_value` | `DECIMAL(18,2)` | Valor pago. |
| `value_to_pay` | `DECIMAL(18,2)` | Valor ainda a pagar. |
| `discount_value` | `DECIMAL(18,2)` | Valor de desconto. |
| `interest_value` | `DECIMAL(18,2)` | Valor de juros. |
| `paid` | `BIT` | Flag de pagamento. |
| `status` | `NVARCHAR(50)` | Status da primeira parcela. |
| `type` | `NVARCHAR(50)` | Tipo do billing. |
| `comments` | `NVARCHAR(MAX)` | Comentários livres da cobrança. |
| `sequence_code` | `INT` | Código sequencial da cobrança. |
| `competence_month` | `INT` | Mês de competência. |
| `competence_year` | `INT` | Ano de competência. |
| `created_at` | `DATETIMEOFFSET` | Data/hora de criação. |
| `updated_at` | `DATETIMEOFFSET` | Data/hora de atualização. |
| `corporation_id` | `BIGINT` | ID da corporação. |
| `corporation_name` | `NVARCHAR(255)` | Nome/apelido da corporação. |
| `corporation_cnpj` | `NVARCHAR(50)` | CNPJ da corporação. |
| `nfse_numero` | `VARCHAR(50)` | Documento da NFS-e da primeira parcela (`accountingCredit.document`). |
| `carteira_banco` | `VARCHAR(50)` | Carteira/variação da conta bancária. |
| `instrucao_boleto` | `NVARCHAR(MAX)` | Instrução customizada de boleto. |
| `banco_nome` | `VARCHAR(100)` | Nome do banco da cobrança. |
| `metodo_pagamento` | `VARCHAR(50)` | Método de pagamento da primeira parcela. |
| `metadata` | `NVARCHAR(MAX)` | JSON bruto do billing GraphQL. |
| `data_extracao` | `DATETIME2` | Momento da gravação/atualização no banco. |

### `dbo.raster_viagens`

- Fonte: Raster `getEventoFimViagem`
- Grão: 1 linha por viagem/SM
- Chaves: PK `cod_solicitacao`
- Observação importante: a Raster entra como domínio separado das tabelas ESL. A view `dbo.vw_raster_sm_transit_time` deriva as colunas operacionais da planilha a partir desta tabela e de `dbo.raster_viagem_paradas`.

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `cod_solicitacao` | `BIGINT` | Código da solicitação/SM na Raster. |
| `sequencial` | `BIGINT` | Sequencial adicional recebido da Raster, quando disponível. |
| `cod_filial` | `INT` | Código da filial informado pela Raster. |
| `status_viagem` | `NVARCHAR(10)` | Status da viagem consultado no endpoint. |
| `placa_veiculo` | `NVARCHAR(20)` | Placa principal da viagem. |
| `placa_carreta1` | `NVARCHAR(20)` | Placa da primeira carreta. |
| `placa_carreta2` | `NVARCHAR(20)` | Placa da segunda carreta. |
| `placa_carreta3` | `NVARCHAR(20)` | Placa da terceira carreta. |
| `cpf_motorista1` | `NVARCHAR(20)` | CPF do motorista principal. |
| `cpf_motorista2` | `NVARCHAR(20)` | CPF do segundo motorista. |
| `cnpj_cliente_orig` | `NVARCHAR(20)` | CNPJ do cliente de origem. |
| `cnpj_cliente_dest` | `NVARCHAR(20)` | CNPJ do cliente de destino. |
| `cod_ibge_cidade_orig` | `BIGINT` | Código IBGE da cidade de origem. |
| `cod_ibge_cidade_dest` | `BIGINT` | Código IBGE da cidade de destino. |
| `data_hora_prev_ini` | `DATETIMEOFFSET(3)` | Data/hora prevista de início da viagem. |
| `data_hora_prev_fim` | `DATETIMEOFFSET(3)` | Data/hora prevista de fim da viagem. |
| `data_hora_real_ini` | `DATETIMEOFFSET(3)` | Data/hora real de início da viagem. |
| `data_hora_real_fim` | `DATETIMEOFFSET(3)` | Data/hora real de fim da viagem. |
| `data_hora_identificou_fim_viagem` | `DATETIMEOFFSET(3)` | Momento em que a Raster identificou o fim da viagem. |
| `tempo_total_viagem_min` | `INT` | Tempo total da viagem em minutos. |
| `dentro_prazo_raster` | `NVARCHAR(5)` | Indicador de cumprimento do prazo pela Raster. |
| `percentual_atraso_raster` | `DECIMAL(10,2)` | Percentual de atraso calculado pela Raster. |
| `rodou_fora_horario` | `NVARCHAR(5)` | Indicador de operação fora do horário. |
| `velocidade_media` | `DECIMAL(10,2)` | Velocidade média da viagem. |
| `eventos_velocidade` | `INT` | Quantidade de eventos de velocidade. |
| `desvios_de_rota` | `INT` | Quantidade de desvios de rota. |
| `cod_rota` | `BIGINT` | Código da rota Raster. |
| `rota_descricao` | `NVARCHAR(500)` | Descrição textual da rota. |
| `link_timeline` | `NVARCHAR(1000)` | Link da timeline Raster. |
| `metadata` | `NVARCHAR(MAX)` | JSON bruto da viagem. |
| `data_extracao` | `DATETIME2(3)` | Momento da gravação/atualização no banco. |

### `dbo.raster_viagem_paradas`

- Fonte: Raster `getEventoFimViagem.ColetasEntregas`
- Grão: 1 linha por parada da viagem
- Chaves: PK composta `cod_solicitacao`, `ordem`; FK `cod_solicitacao` para `dbo.raster_viagens`
- Observação importante: documentos, pernoites, temperaturas e demais arrays ainda ficam preservados em `metadata` na v1.

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `cod_solicitacao` | `BIGINT` | Código da solicitação/SM da viagem. |
| `ordem` | `INT` | Ordem da parada dentro da viagem. |
| `tipo` | `NVARCHAR(5)` | Tipo da parada, conforme payload Raster. |
| `cod_ibge_cidade` | `BIGINT` | Código IBGE da cidade da parada. |
| `cnpj_cliente` | `NVARCHAR(20)` | CNPJ do cliente da parada. |
| `codigo_cliente` | `NVARCHAR(50)` | Código do cliente na Raster. |
| `data_hora_prev_chegada` | `DATETIMEOFFSET(3)` | Data/hora prevista de chegada na parada. |
| `data_hora_prev_saida` | `DATETIMEOFFSET(3)` | Data/hora prevista de saída da parada. |
| `data_hora_real_chegada` | `DATETIMEOFFSET(3)` | Data/hora real de chegada na parada. |
| `data_hora_real_saida` | `DATETIMEOFFSET(3)` | Data/hora real de saída da parada. |
| `latitude` | `DECIMAL(18,8)` | Latitude planejada/associada à parada. |
| `longitude` | `DECIMAL(18,8)` | Longitude planejada/associada à parada. |
| `dentro_prazo_raster` | `NVARCHAR(5)` | Indicador de cumprimento do prazo na parada. |
| `diferenca_tempo_raster` | `NVARCHAR(80)` | Diferença de tempo textual retornada pela Raster. |
| `km_percorrido_entrega` | `DECIMAL(18,3)` | Quilometragem percorrida até a entrega. |
| `km_restante_entrega` | `DECIMAL(18,3)` | Quilometragem restante até a entrega. |
| `chegou_na_entrega` | `NVARCHAR(5)` | Indicador de chegada na entrega. |
| `data_hora_ultima_posicao` | `DATETIMEOFFSET(3)` | Data/hora da última posição associada. |
| `latitude_ultima_posicao` | `DECIMAL(18,8)` | Latitude da última posição. |
| `longitude_ultima_posicao` | `DECIMAL(18,8)` | Longitude da última posição. |
| `referencia_ultima_posicao` | `NVARCHAR(500)` | Referência textual da última posição. |
| `metadata` | `NVARCHAR(MAX)` | JSON bruto da parada. |
| `data_extracao` | `DATETIME2(3)` | Momento da gravação/atualização no banco. |

### `dbo.dim_usuarios`

- Fonte: GraphQL `individual`
- Grão: 1 linha por usuário
- Chaves: PK `user_id`
- Observação importante: a estrutura final da tabela é a soma de `011_criar_tabela_dim_usuarios.sql` com `016_alter_tabela_dim_usuarios_estado.sql`.
- Observação importante: hoje existem dois caminhos de escrita. `UsuarioSistemaRepository` mantém o subconjunto legado (`user_id`, `nome`, `data_atualizacao`), enquanto `SqlServerUsuariosEstadoRepository` preenche também `ativo`, `origem_atualizado_em`, `ultima_extracao_em` e alimenta `dim_usuarios_historico`. Para pesquisa, considerar a estrutura completa abaixo.

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `user_id` | `BIGINT` | ID do usuário no sistema origem. |
| `nome` | `NVARCHAR(255)` | Nome do usuário. |
| `data_atualizacao` | `DATETIME` | Momento em que o snapshot do usuário foi atualizado. |
| `ativo` | `BIT` | Indica se o usuário está ativo no snapshot atual. |
| `origem_atualizado_em` | `DATETIME2` | Data/hora de atualização vinda da origem, quando disponível. |
| `ultima_extracao_em` | `DATETIME2` | Último momento em que o usuário apareceu na sincronização. |

## Tabelas técnicas, auditoria e controle

### `dbo.log_extracoes`

- Papel: log resumido por extração de entidade

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `id` | `BIGINT IDENTITY` | PK técnica. |
| `entidade` | `NVARCHAR(50)` | Nome da entidade extraída. |
| `timestamp_inicio` | `DATETIME2` | Início da execução. |
| `timestamp_fim` | `DATETIME2` | Fim da execução. |
| `status_final` | `NVARCHAR(20)` | Status final resumido da extração. |
| `registros_extraidos` | `INT` | Quantidade de registros extraídos. |
| `paginas_processadas` | `INT` | Quantidade de páginas processadas. |
| `mensagem` | `NVARCHAR(MAX)` | Mensagem textual complementar. |

### `dbo.page_audit`

- Papel: trilha por página requisitada na API
- Observação importante: existe índice `UNIQUE` em `(run_uuid, template_id, page)`

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `id` | `BIGINT IDENTITY` | PK técnica. |
| `execution_uuid` | `NVARCHAR(36)` | UUID da execução macro do pipeline. |
| `run_uuid` | `NVARCHAR(36)` | UUID da execução da entidade/runner. |
| `template_id` | `INT` | ID do template/endereço consultado. |
| `page` | `INT` | Número da página requisitada. |
| `per` | `INT` | Tamanho da página. |
| `janela_inicio` | `DATE` | Início da janela consultada. |
| `janela_fim` | `DATE` | Fim da janela consultada. |
| `req_hash` | `CHAR(64)` | Hash SHA-256 da requisição. |
| `resp_hash` | `CHAR(64)` | Hash SHA-256 da resposta. |
| `total_itens` | `INT` | Total de itens retornados na página. |
| `id_key` | `NVARCHAR(50)` | Nome lógico do campo de identidade analisado. |
| `id_min_num` | `BIGINT` | Menor ID numérico visto na página. |
| `id_max_num` | `BIGINT` | Maior ID numérico visto na página. |
| `id_min_str` | `NVARCHAR(80)` | Menor ID textual visto na página. |
| `id_max_str` | `NVARCHAR(80)` | Maior ID textual visto na página. |
| `status_code` | `INT` | Status HTTP da chamada. |
| `duracao_ms` | `INT` | Duração da requisição em milissegundos. |
| `timestamp` | `DATETIME2` | Momento de gravação da auditoria de página. |

### `dbo.sys_execution_history`

- Papel: resumo histórico de execuções do pipeline
- Observação importante: o contrato atual é o mesmo usado pelo runtime e pela migration `005`, com índice em `start_time` e coluna técnica `created_at`.

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `id` | `BIGINT IDENTITY` | PK técnica. |
| `start_time` | `DATETIME2` | Início da execução. |
| `end_time` | `DATETIME2` | Fim da execução. |
| `duration_seconds` | `INT` | Duração total em segundos. |
| `status` | `VARCHAR(20)` | Status final da execução. |
| `total_records` | `INT` | Quantidade total de registros tratados. Default `0`. |
| `error_category` | `VARCHAR(50)` | Categoria de erro, quando existir. |
| `error_message` | `VARCHAR(500)` | Mensagem resumida do erro. |
| `created_at` | `DATETIME2` | Momento em que a linha de histórico foi gravada. |

### `dbo.sys_auditoria_temp`

- Papel: auditoria temporária de campos descobertos na API

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `entidade` | `NVARCHAR(100)` | Entidade auditada. |
| `campo_api` | `NVARCHAR(400)` | Nome/caminho do campo encontrado. |
| `data_auditoria` | `DATETIME2` | Momento da auditoria do campo. |

### `dbo.sys_execution_audit`

- Papel: trilha estruturada autorizativa por execução e entidade
- Chave composta: `execution_uuid`, `entidade`

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `execution_uuid` | `NVARCHAR(36)` | UUID da execução. |
| `entidade` | `NVARCHAR(50)` | Entidade auditada. |
| `janela_consulta_inicio` | `DATETIME2` | Início da janela originalmente consultada. |
| `janela_consulta_fim` | `DATETIME2` | Fim da janela originalmente consultada. |
| `janela_confirmacao_inicio` | `DATETIME2` | Início da janela efetivamente confirmada. |
| `janela_confirmacao_fim` | `DATETIME2` | Fim da janela efetivamente confirmada. |
| `status_execucao` | `NVARCHAR(30)` | Status estruturado da execução. |
| `api_total_bruto` | `INT` | Total bruto retornado pela API. |
| `api_total_unico` | `INT` | Total único após deduplicação. |
| `db_upserts` | `INT` | Quantidade de upserts executados. |
| `db_persistidos` | `INT` | Quantidade confirmada como persistida. |
| `api_completa` | `BIT` | Indica se a leitura da API foi considerada completa. |
| `motivo_incompletude` | `NVARCHAR(200)` | Motivo da incompletude, quando houver. |
| `paginas_processadas` | `INT` | Total de páginas processadas. |
| `noop_count` | `INT` | Quantidade de merges sem alteração efetiva. |
| `invalid_count` | `INT` | Quantidade de registros inválidos descartados. |
| `started_at` | `DATETIME2` | Início da execução da entidade. |
| `finished_at` | `DATETIME2` | Fim da execução da entidade. |
| `command_name` | `NVARCHAR(100)` | Comando/runner que executou a carga. |
| `cycle_id` | `NVARCHAR(100)` | Identificador do ciclo/loop. |
| `detalhe` | `NVARCHAR(MAX)` | Detalhamento livre da execução. |
| `updated_at` | `DATETIME2` | Última atualização da linha de auditoria. |

### `dbo.sys_execution_watermark`

- Papel: watermark confirmado por entidade

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `entidade` | `NVARCHAR(50)` | Nome da entidade. |
| `watermark_confirmado` | `DATETIME2` | Último marco temporal confirmado como persistido. |
| `updated_at` | `DATETIME2` | Última atualização do watermark. |

### `dbo.dim_usuarios_historico`

- Papel: histórico simples de mudança do snapshot de usuários

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `id` | `BIGINT IDENTITY` | PK técnica. |
| `execution_uuid` | `NVARCHAR(36)` | UUID da execução que observou a mudança. |
| `user_id` | `BIGINT` | ID do usuário. |
| `nome` | `NVARCHAR(255)` | Nome observado naquele momento. |
| `ativo` | `BIT` | Estado ativo/inativo no momento da observação. |
| `origem_atualizado_em` | `DATETIME2` | Carimbo de atualização vindo da origem, quando houver. |
| `observado_em` | `DATETIME2` | Momento em que a mudança foi observada. |
| `tipo_alteracao` | `NVARCHAR(30)` | Tipo da mudança (`INSERTED`, `UPDATED`, `REACTIVATED`, `DEACTIVATED`). |

### `dbo.schema_migrations`

- Papel: controle de migrations aplicadas
- Observação importante: também possui script-base em `database/tabelas/018_criar_tabela_schema_migrations.sql` para cenários de recriação manual do schema sem depender da migration `001`

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `migration_id` | `NVARCHAR(255)` | Identificador único da migration. |
| `applied_at` | `DATETIME2(0)` | Momento de aplicação da migration. |
| `checksum_sha256` | `VARCHAR(64)` | Hash SHA-256 opcional do script. |
| `notes` | `NVARCHAR(500)` | Observações livres sobre a migration. |

### `dbo.etl_invalid_records`

- Papel: trilha de payloads descartados por invalidade
- Observação importante: possui script-base em `database/tabelas/019_criar_tabela_etl_invalid_records.sql` e também fallback idempotente no runtime

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `id` | `BIGINT IDENTITY` | PK técnica. |
| `created_at` | `DATETIME2` | Momento do descarte do registro inválido. |
| `entidade` | `NVARCHAR(80)` | Entidade de origem do payload inválido. |
| `reason_code` | `NVARCHAR(50)` | Código resumido do motivo do descarte. |
| `detalhe` | `NVARCHAR(500)` | Detalhe textual do problema. |
| `chave_referencia` | `NVARCHAR(150)` | Chave ou referência do registro inválido. |
| `payload_json` | `NVARCHAR(MAX)` | Payload bruto descartado. |

## SQLite local de segurança

### `users`

- Banco: SQLite local
- Papel: usuários locais para autenticação operacional

| Coluna | Tipo SQLite | Descrição |
| --- | --- | --- |
| `id` | `INTEGER` | PK autoincremental. |
| `username` | `TEXT` | Login do usuário, `UNIQUE`, case-insensitive. |
| `display_name` | `TEXT` | Nome de exibição. |
| `password_hash` | `TEXT` | Hash da senha. |
| `password_salt` | `TEXT` | Salt da senha. |
| `role` | `TEXT` | Papel/perfil de acesso. |
| `active` | `INTEGER` | Flag de usuário ativo. |
| `failed_attempts` | `INTEGER` | Contador de tentativas de login falhas. |
| `blocked_until` | `TEXT` | Data/hora até quando o usuário fica bloqueado. |
| `created_at` | `TEXT` | Criação do usuário. |
| `updated_at` | `TEXT` | Última atualização do usuário. |
| `last_login_at` | `TEXT` | Último login realizado. |

### `auth_audit`

- Banco: SQLite local
- Papel: trilha de autenticação local

| Coluna | Tipo SQLite | Descrição |
| --- | --- | --- |
| `id` | `INTEGER` | PK autoincremental. |
| `occurred_at` | `TEXT` | Momento do evento de autenticação. |
| `username` | `TEXT` | Usuário envolvido no evento. |
| `action` | `TEXT` | Ação executada (`login`, `logout`, bloqueio, etc.). |
| `success` | `INTEGER` | Indica sucesso ou falha do evento. |
| `detail` | `TEXT` | Detalhe textual complementar. |
| `host` | `TEXT` | Host/origem do evento. |

## Onde os scripts vivem

- SQL Server base: `database/tabelas/`
- Ajustes estruturais: `database/migrations/`
- Segurança SQLite local: `database/security_sqlite/`
- Execução dos scripts SQL Server: `database/executar_database.bat`

## Observações finais

- Este documento descreve a **estrutura efetiva de pesquisa** do projeto, não apenas a ordem de execução dos scripts.
- Se você precisar de um campo que a API entrega, mas que não aparece como coluna física, o primeiro lugar para olhar é `metadata`.
- Para análises de execução, cruzar `sys_execution_audit`, `page_audit`, `log_extracoes` e `etl_invalid_records` costuma dar a trilha mais completa.
