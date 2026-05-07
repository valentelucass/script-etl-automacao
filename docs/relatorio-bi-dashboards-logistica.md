# Relatório Estratégico de BI e Dashboards de Logística

## 1. Escopo e método

Este relatório foi produzido a partir dos scripts SQL versionados no projeto, usando como fonte principal:

- `Database/views`
- `Database/views-dimensao`
- `Database/tabelas`
- scripts de validação e observabilidade do ETL em `Database/validacao` e `src/main/java/.../observabilidade`

Premissas adotadas:

- A fonte de verdade usada aqui é o schema definido no repositório, não uma instância live do SQL Server.
- Os tipos de cada campo das views foram inferidos a partir do DDL das tabelas e das expressões SQL das próprias views.
- O inventário campo a campo de todas as views foi separado no apêndice `docs/relatorio-bi-catalogo-views.md`.
- Sempre que um KPI ou relacionamento não é suportado integralmente pelo schema atual, isso é explicitado como limitação ou risco.

## 2. Inventário executivo das views

| View | Domínio | Campos | Grain analítico pretendido | Dependências principais |
| --- | --- | ---: | --- | --- |
| `vw_coletas_powerbi` | Coletas | 38 | 1 linha por coleta | `coletas`, `manifestos`, `dim_usuarios` |
| `vw_manifestos_powerbi` | Manifestos | 97 | 1 linha por manifesto natural | `manifestos` |
| `vw_fretes_powerbi` | Fretes | 102 | 1 linha por frete | `fretes` |
| `vw_faturas_por_cliente_powerbi` | Faturamento cliente | 42 | 1 linha por registro canônico de fatura | `faturas_por_cliente`, `faturas_graphql` |
| `vw_faturas_graphql_powerbi` | Cobrança / contas a receber | 29 | 1 linha por título GraphQL | `faturas_graphql` |
| `vw_contas_a_pagar_powerbi` | Financeiro / AP | 31 | 1 linha por lançamento a pagar | `contas_a_pagar` |
| `vw_cotacoes_powerbi` | Comercial / pricing | 53 | 1 linha por cotação | `cotacoes` |
| `vw_localizacao_cargas_powerbi` | Tracking | 21 | 1 linha por minuta rastreada | `localizacao_cargas` |
| `vw_bi_monitoramento` | Operação ETL | 9 | 1 linha por execução do ETL | `sys_execution_history` |
| `vw_dim_clientes` | Dimensão | 1 | 1 linha por nome normalizado | `fretes`, `coletas`, `faturas_por_cliente` |
| `vw_dim_filiais` | Dimensão | 2 | 1 linha por filial normalizada | views Power BI |
| `vw_dim_veiculos` | Dimensão | 3 | 1 linha por placa | `vw_manifestos_powerbi` |
| `vw_dim_motoristas` | Dimensão | 1 | 1 linha por motorista normalizado | `vw_manifestos_powerbi` |
| `vw_dim_planocontas` | Dimensão | 3 | 1 linha por descrição contábil normalizada | `vw_contas_a_pagar_powerbi` |
| `vw_dim_usuarios` | Dimensão | 3 | 1 linha por usuário | `dim_usuarios` |

Leitura crítica:

- O projeto já separa razoavelmente bem fatos operacionais e dimensões.
- O schema favorece dashboards analíticos por domínio, mas ainda não está pronto para um Star Schema robusto sem camada semântica adicional.
- As maiores fragilidades hoje estão nos joins textuais, no tratamento de rateios financeiros e em alguns tipos armazenados como string.

## 3. Modelagem relacional identificada

### 3.1 Chaves primárias e chaves de negócio

| Entidade base | PK física | Chave de negócio / observação |
| --- | --- | --- |
| `coletas` | `id` | `sequence_code` é único e funciona como chave operacional |
| `manifestos` | `id` | unicidade operacional via `chave_merge_hash`; visão analítica deve usar `sequence_code + identificador_unico` |
| `fretes` | `id` | `id` é a chave operacional do frete |
| `cotacoes` | `sequence_code` | chave natural da cotação |
| `localizacao_cargas` | `sequence_number` | chave natural da minuta |
| `contas_a_pagar` | `sequence_code` | atual PK física; há risco de perder rateios da origem |
| `faturas_por_cliente` | `unique_id` | hash canônico calculado a partir de atributos estáveis de negócio |
| `faturas_graphql` | `id` | título/lançamento da trilha GraphQL |
| `dim_usuarios` | `user_id` | dimensão técnica de usuário |

### 3.2 Relacionamentos explícitos e inferidos

| Origem | Campo | Destino | Campo | Cardinalidade esperada | Observação |
| --- | --- | --- | --- | --- | --- |
| `manifestos` | `pick_sequence_code` | `coletas` | `sequence_code` | `N:1` | relação operacional crítica do ETL |
| `coletas` | `cancellation_user_id` | `dim_usuarios` | `user_id` | `N:1` | enriquecimento do usuário que cancelou |
| `coletas` | `destroy_user_id` | `dim_usuarios` | `user_id` | `N:1` | enriquecimento do usuário que excluiu |
| `fretes` | `accounting_credit_id` | `faturas_graphql` | `id` | `N:1` | ponte entre logística e cobrança |
| `faturas_por_cliente` | `fit_ant_document` + `fit_ant_issue_date` | `faturas_graphql` | `document` + `issue_date` | `N:0..1` na view | enriquecimento usa escolha determinística para não multiplicar linhas |
| fatos diversos | nomes de filial | `vw_dim_filiais` | `NomeFilial` | `N:1` | dimensão textual normalizada |
| fatos diversos | nomes/documentos de clientes | `vw_dim_clientes` | `Nome` | `N:1` conceitual | risco de colapsar homônimos |
| `manifestos` | `vehicle_plate` | `vw_dim_veiculos` | `Placa` | `N:1` | melhor dimensão operacional hoje |
| `manifestos` | `driver_name` | `vw_dim_motoristas` | `NomeMotorista` | `N:1` | risco de colisão por homônimos |
| `contas_a_pagar` | `descricao_contabil` | `vw_dim_planocontas` | `Descricao` | `N:1` | hoje sem chave contábil formal |

### 3.3 Dependências entre views

- `vw_coletas_powerbi` depende de `manifestos` e `dim_usuarios`.
- `vw_faturas_por_cliente_powerbi` depende de `faturas_graphql`.
- `vw_dim_filiais` depende de várias views Power BI.
- `vw_dim_veiculos` e `vw_dim_motoristas` dependem de `vw_manifestos_powerbi`.
- `vw_dim_planocontas` depende de `vw_contas_a_pagar_powerbi`.

### 3.4 Interpretação de cardinalidade por domínio

- Coletas x Manifestos: uma coleta pode alimentar zero, um ou vários manifestos ao longo do ciclo operacional.
- Fretes x Faturas GraphQL: vários fretes podem apontar para o mesmo crédito/título financeiro.
- Faturas por cliente x Faturas GraphQL: a view mantém grain 1 linha por `faturas_por_cliente` e escolhe no máximo um título GraphQL, priorizando `document + issue_date`.
- Tracking (`localizacao_cargas`) está isolado como fato operacional independente; o schema atual não fornece FK explícita para `fretes`.

## 4. Modelo analítico recomendado

### 4.1 Estratégia recomendada

A melhor abordagem para a aplicação analítica é um Star Schema semântico sobre as tabelas/views atuais, mantendo as views operacionais como camada de ingestão e criando fatos derivados por domínio.

### 4.2 Fatos recomendados

| Fato | Grain recomendado | Fonte principal |
| --- | --- | --- |
| `fato_coletas` | 1 coleta (`id`) | `vw_coletas_powerbi` |
| `fato_manifestos` | 1 manifesto natural (`Número + Identificador Único`) | `vw_manifestos_powerbi` |
| `fato_fretes` | 1 frete (`ID`) | `vw_fretes_powerbi` |
| `fato_cotacoes` | 1 cotação (`N° Cotação`) | `vw_cotacoes_powerbi` |
| `fato_tracking_cargas` | 1 minuta (`N° Minuta`) | `vw_localizacao_cargas_powerbi` |
| `fato_faturas_cliente` | 1 registro de faturamento (`ID Único`) | `vw_faturas_por_cliente_powerbi` |
| `fato_titulos_receber` | 1 título (`ID`) | `vw_faturas_graphql_powerbi` |
| `fato_contas_pagar` | idealmente 1 linha por rateio contábil; no estado atual 1 linha por `sequence_code` | `vw_contas_a_pagar_powerbi` |
| `fato_etl_execucao` | 1 execução | `vw_bi_monitoramento` |

### 4.3 Dimensões conformadas

| Dimensão | Origem sugerida | Observação |
| --- | --- | --- |
| `dim_data` | derivada | obrigatória para todos os eixos temporais |
| `dim_tempo` | derivada | útil para hora de solicitação e distribuição intradiária |
| `dim_filial` | `vw_dim_filiais` | preferir incluir id/cnpj no futuro |
| `dim_cliente` | derivada a partir de nome + documento | não depender apenas de nome normalizado |
| `dim_usuario` | `vw_dim_usuarios` + nomes textuais | suportar papéis múltiplos |
| `dim_motorista` | `vw_dim_motoristas` | idealmente com documento/ID no futuro |
| `dim_veiculo` | `vw_dim_veiculos` | manter placa como chave natural |
| `dim_plano_contas` | `vw_dim_planocontas` | hoje ainda textual |
| `dim_fornecedor` | derivada de `contas_a_pagar` | importante para AP |
| `dim_regiao` | derivada de origem/destino/filial atual | útil para logística e SLA |
| `dim_status` | derivada | padronizar status de cada domínio |
| `dim_tipo_servico` | derivada | fretes, manifestos, cotações e tracking |

### 4.4 Role-playing dimensions

O modelo analítico deve prever papéis distintos para dimensões compartilhadas:

- cliente pagador
- cliente remetente
- cliente destinatário
- cliente da coleta
- filial emissora
- filial origem
- filial destino
- filial atual
- usuário emissor
- usuário cancelador
- usuário excluidor
- usuário de lançamento financeiro

### 4.5 Quando considerar Snowflake

Snowflake só é justificável se o produto evoluir para:

- hierarquia formal de filiais por corporação/região
- cadastro mestre de clientes separado por grupo econômico
- plano de contas com níveis contábeis e agregações oficiais

No estágio atual, a melhor relação custo-benefício continua sendo Star Schema com dimensões denormalizadas.

## 5. Principais inconsistências e riscos do schema atual

### 5.1 Riscos críticos

1. `vw_coletas_powerbi` faz `LEFT JOIN` direto com `manifestos` por `c.sequence_code = m.pick_sequence_code`.
Isso pode duplicar linhas de coleta quando houver múltiplos manifestos para a mesma coleta, inflando KPIs de volume, peso e quantidade.

2. `vw_faturas_por_cliente_powerbi` depende da qualidade de `document + issue_date` para escolher o título GraphQL de enriquecimento.
Quando houver múltiplos títulos iguais também por data, a view mantém apenas um por ordem determinística para preservar o grain.

3. `contas_a_pagar` usa `sequence_code` como PK física e o deduplicador mantém apenas o último registro.
A documentação legado do Data Export mostra casos reais de múltiplas linhas para o mesmo `ant_ils_sequence_code`, representando rateios contábeis ou centro de custo. Isso compromete KPIs de despesas por conta, centro de custo, fornecedor e filial.

### 5.2 Riscos médios

1. `localizacao_cargas.taxed_weight` e `localizacao_cargas.invoices_value` estão armazenados como `NVARCHAR(50)`.
Isso dificulta agregações numéricas seguras no BI.

2. `cotacoes.real_weight` também está como `NVARCHAR(50)`, impedindo cálculo nativo confiável de densidade, cubagem e conversão por peso real.

3. `coletas.status_updated_at` é `NVARCHAR(50)` em vez de tipo temporal.
Isso limita análises de aging e throughput por status.

4. `vw_dim_clientes`, `vw_dim_motoristas` e `vw_dim_planocontas` usam chaves textuais normalizadas.
Essas views garantem unicidade técnica, mas não garantem unicidade semântica.

### 5.3 Impacto nos KPIs

- Margem real por frete ainda não é suportada de forma nativa.
- OTIF por entrega não pode ser calculado com rigor sem timestamp real de entrega por frete.
- Aging de contas a pagar é possível por emissão/criação, mas atraso financeiro exato não é possível sem data de vencimento do título.

## 6. Dashboards por domínio

### 6.1 Coletas

**Página:** `Operações / Coletas`

**Objetivo:** monitorar entrada operacional de coleta, cumprimento do agendamento, tentativas, cancelamentos e qualidade do atendimento por região, cliente e filial.

**Views principais:** `vw_coletas_powerbi`, `vw_dim_usuarios`, `vw_dim_filiais`, `vw_dim_clientes`

**KPIs recomendados:**

- total de coletas
- coletas finalizadas
- taxa de sucesso de coleta = finalizadas / total
- taxa de cancelamento
- SLA de coleta no dia agendado = `finish_date <= service_date`
- lead time de coleta = `request_date -> finish_date`
- média de tentativas por coleta
- volume, peso real, peso taxado e valor NF por coleta

**Filtros principais:**

- período por solicitação
- período por agendamento
- filial
- cliente
- região da coleta
- status
- usuário emissor
- usuário de cancelamento/exclusão

**Layout sugerido:**

- primeira dobra com `KpiCard` em grid `4x2` no desktop e `2x4` no mobile
- segunda dobra com linha temporal de solicitações, barras por status e mapa/tabela por região
- terceira dobra com tabela analítica de coletas com drill para tentativas, cancelamento e usuário

**Componentes React sugeridos:**

- `FilterBar`
- `DateRangePicker`
- `KpiCard`
- `ChartCard`
- `StatusBadge`
- `DataTable`
- `EntityDrawer` para detalhes da coleta

**Visualizações obrigatórias:**

| Métrica / pergunta | Tipo | Justificativa | Estrutura esperada |
| --- | --- | --- | --- |
| coletas por dia | `LineChart` | tendência temporal diária | `{ date: string; total: number; finalizadas: number; canceladas: number }[]` |
| distribuição por status | `BarChart` | comparar carga operacional em aberto vs concluída | `{ status: string; total: number }[]` |
| SLA por filial | `BarChart` horizontal | ranking operacional | `{ filial: string; slaPct: number; total: number }[]` |
| região x volume/peso | `ComposedChart` | combinar quantidade e massa coletada | `{ regiao: string; totalColetas: number; pesoTaxado: number; volumes: number }[]` |
| aging das coletas abertas | `Heatmap` ou tabela | monitorar backlog por faixa de idade | `{ faixaAging: string; status: string; total: number }[]` |

**Contrato TypeScript sugerido:**

```ts
export interface ColetasOverview {
  updatedAt: string;
  totalColetas: number;
  finalizadas: number;
  taxaSucesso: number;
  taxaCancelamento: number;
  slaNoAgendamento: number;
  leadTimeMedioDias: number;
  tentativasMedias: number;
  pesoTaxadoTotal: number;
  valorNfTotal: number;
}

export interface ColetasTrendPoint {
  date: string;
  total: number;
  finalizadas: number;
  canceladas: number;
  emTratativa: number;
}
```

**Insight cruzado prioritário:**

- `coletas x manifestos`: quais coletas geram mais reprocesso ou múltiplos manifestos
- views: `vw_coletas_powerbi`, `vw_manifestos_powerbi`
- campos: `Coleta`, `Numero Manifesto`, `Coleta/Número`
- pergunta: quais coletas geram mais desdobramentos operacionais e podem estar pressionando custo e lead time

### 6.2 Manifestos

**Página:** `Operações / Manifestos`

**Objetivo:** acompanhar ciclo do manifesto, capacidade do veículo, custos de viagem, uso de motorista/frota e produtividade da malha.

**Views principais:** `vw_manifestos_powerbi`, `vw_dim_motoristas`, `vw_dim_veiculos`, `vw_dim_filiais`

**KPIs recomendados:**

- total de manifestos
- manifestos em trânsito e encerrados
- tempo médio de ciclo do manifesto = `created_at -> finished_at` ou `closed_at`
- km total rodado
- custo total
- custo por km
- peso taxado total
- volume total em m3
- taxa média de ocupação por peso = `total_taxed_weight / capacidade_kg`
- taxa média de ocupação por cubagem = `total_cubic_volume / vehicle_cubic_weight`

**Filtros principais:**

- período de criação
- período de saída
- período de fechamento/chegada
- filial
- status
- motorista
- veículo
- tipo de carga
- tipo de contrato
- tipo de cálculo

**Layout sugerido:**

- cards superiores com desempenho de frota e custo
- gráficos centrais de ocupação, km, custo e ranking de motoristas/veículos
- tabela inferior com manifesto, MDF-e, motorista, veículo, custo e indicadores de ocupação

**Componentes React sugeridos:**

- `KpiCard`
- `ChartCard`
- `EntityTable`
- `PercentBar`
- `CapacityGauge`
- `DriverRankingTable`

**Visualizações obrigatórias:**

| Métrica / pergunta | Tipo | Justificativa | Estrutura esperada |
| --- | --- | --- | --- |
| manifestos por dia e status | `AreaChart` empilhado | visão de throughput da malha | `{ date: string; encerrado: number; emTransito: number; pendente: number }[]` |
| custo por filial | `BarChart` | comparar centros operacionais | `{ filial: string; custoTotal: number; km: number; custoPorKm: number }[]` |
| ocupação de carga por manifesto | `ScatterChart` | relacionar capacidade vs uso | `{ manifesto: string; ocupacaoPesoPct: number; ocupacaoCubagemPct: number; custoTotal: number }[]` |
| ranking de motoristas | `BarChart` horizontal | comparar produtividade | `{ motorista: string; manifestos: number; km: number; custoTotal: number }[]` |
| composição de custos | `PieChart` ou `Treemap` | mostrar peso relativo de frete, combustível, pedágio, diárias e adicionais | `{ categoria: string; valor: number }[]` |

**Contrato TypeScript sugerido:**

```ts
export interface ManifestosOverview {
  updatedAt: string;
  totalManifestos: number;
  emTransito: number;
  encerrados: number;
  kmTotal: number;
  custoTotal: number;
  custoPorKm: number;
  ocupacaoPesoMediaPct: number;
  ocupacaoCubagemMediaPct: number;
}
```

**Insight cruzado prioritário:**

- `manifestos x coletas x veículos`
- views: `vw_manifestos_powerbi`, `vw_coletas_powerbi`, `vw_dim_veiculos`
- campos: `Coleta/Número`, `Número`, `Veículo/Placa`, `Capacidade Lotação Kg`
- pergunta: quais veículos concentram coletas de baixa ocupação ou manifestos muito fragmentados

### 6.3 Fretes

**Página:** `Receita / Fretes`

**Objetivo:** analisar receita operacional, documentação fiscal, peso/volume transportado, carteira de clientes e risco de vencimento de entrega.

**Views principais:** `vw_fretes_powerbi`, `vw_dim_clientes`, `vw_dim_filiais`

**KPIs recomendados:**

- total de fretes
- receita bruta do serviço = `Valor Total do Serviço`
- receita de frete = `Valor Frete`
- ticket médio
- volumes totais
- peso taxado e peso real
- taxa de emissão CT-e
- taxa de emissão NFS-e
- fretes pendentes com previsão vencida
- receita por cliente pagador

**Limitação importante:** custo direto por frete não existe nesta view. Margem por frete só pode ser estimada com modelo de alocação externo usando manifesto, despesas ou centro de custo.

**Filtros principais:**

- data do frete
- previsão de entrega
- filial
- pagador
- remetente
- destinatário
- UF origem / UF destino
- status
- tipo de frete
- modal
- seguro

**Layout sugerido:**

- cards de receita e compliance documental
- gráficos de tendência, carteira por cliente e mapa origem-destino
- tabela analítica com drill para impostos, adicionais e documento oficial

**Componentes React sugeridos:**

- `FilterBar`
- `MetricSwitcher`
- `KpiCard`
- `ChartCard`
- `RouteBadge`
- `DocumentStatusChip`
- `DataTable`

**Visualizações obrigatórias:**

| Métrica / pergunta | Tipo | Justificativa | Estrutura esperada |
| --- | --- | --- | --- |
| receita por dia | `LineChart` | tendência temporal de faturamento operacional | `{ date: string; receitaBruta: number; valorFrete: number; fretes: number }[]` |
| receita por cliente pagador | `BarChart` horizontal | ranking de carteira e concentração | `{ cliente: string; receita: number; fretes: number; ticketMedio: number }[]` |
| previsão vencida por status | `BarChart` empilhado | risco operacional imediato | `{ status: string; vencidos: number; noPrazo: number }[]` |
| mix documental | `PieChart` | CT-e, NFS-e e pendente | `{ tipoDocumento: string; total: number }[]` |
| origem x destino x receita | `Sankey` ou tabela matricial | visão de corredor logístico | `{ origemUf: string; destinoUf: string; receita: number; fretes: number }[]` |

**Contrato TypeScript sugerido:**

```ts
export interface FretesOverview {
  updatedAt: string;
  totalFretes: number;
  receitaBruta: number;
  valorFrete: number;
  ticketMedio: number;
  pesoTaxadoTotal: number;
  volumesTotais: number;
  pctCteEmitido: number;
  pctNfseEmitida: number;
  fretesPrevisaoVencida: number;
}
```

**Insight cruzado prioritário:**

- `fretes x faturas_graphql`
- views: `vw_fretes_powerbi`, `vw_faturas_graphql_powerbi`
- campos: `ID`, `Corp ID`, `Fatura/N° Documento`, `Filial/ID`, `Valor`
- pergunta: quanto da receita operacional já está efetivamente materializada em título financeiro

### 6.4 Faturas

**Página:** `Financeiro / Faturas`

**Objetivo:** controlar faturamento ao cliente, títulos emitidos, recebimento, aging de cobrança e reconciliação entre visão operacional e visão financeira.

**Views principais:** `vw_faturas_por_cliente_powerbi`, `vw_faturas_graphql_powerbi`, `vw_dim_filiais`, `vw_dim_clientes`

**Grains coexistentes:**

- `vw_faturas_por_cliente_powerbi`: visão operacional de faturamento e CT-e/NFS-e por cliente
- `vw_faturas_graphql_powerbi`: visão financeira de título/cobrança

**KPIs recomendados:**

- valor total faturado
- valor total a receber
- valor já pago
- saldo aberto
- taxa de adimplência
- DSO aproximado = média `issue_date -> paid/baixa`
- aging por faixa de vencimento
- concentração de faturamento por cliente pagador
- share de títulos em atraso

**Filtros principais:**

- emissão de CT-e
- emissão da fatura
- vencimento
- filial
- cliente pagador
- status do processo
- pago / não pago

**Layout sugerido:**

- topo com indicadores de faturamento, cobrança e aging
- meio com curvas de emissão x recebimento e ranking de clientes
- base com tabela conciliada entre documento operacional e título financeiro

**Componentes React sugeridos:**

- `KpiCard`
- `AgingBuckets`
- `CustomerConcentrationChart`
- `ReceivablesTable`
- `ReconciliationBadge`

**Visualizações obrigatórias:**

| Métrica / pergunta | Tipo | Justificativa | Estrutura esperada |
| --- | --- | --- | --- |
| faturado x pago por mês | `ComposedChart` | comparar geração de receita e caixa | `{ month: string; faturado: number; pago: number; saldoAberto: number }[]` |
| aging de recebíveis | `BarChart` | bucketização financeira clássica | `{ faixa: string; valor: number; titulos: number }[]` |
| top clientes por faturamento | `BarChart` horizontal | concentração e dependência comercial | `{ cliente: string; faturado: number; saldoAberto: number }[]` |
| status do processo de faturamento | `DonutChart` | operação vs cobrança | `{ statusProcesso: string; total: number }[]` |
| reconciliação entre fatura operacional e título financeiro | `Tabela` | evitar erro de join e auditoria | `{ documento: string; emissao: string; valorOperacional: number; valorFinanceiro: number; status: string }[]` |

**Contrato TypeScript sugerido:**

```ts
export interface FaturasOverview {
  updatedAt: string;
  valorFaturado: number;
  valorRecebido: number;
  saldoAberto: number;
  taxaAdimplencia: number;
  dsoMedioDias: number;
  titulosEmAtraso: number;
  clientesAtivos: number;
}
```

**Insight cruzado prioritário:**

- `faturas por cliente x fretes`
- views: `vw_faturas_por_cliente_powerbi`, `vw_fretes_powerbi`
- campos: `Pagador do frete/Nome`, `Frete/Valor dos CT-es`, `Valor Total do Serviço`, `CT-e/Chave`
- pergunta: o valor faturado por cliente acompanha o volume/valor operacional efetivamente transportado

### 6.5 Contas a pagar

**Página:** `Financeiro / Contas a Pagar`

**Objetivo:** monitorar saída de caixa, composição por conta contábil, centro de custo, fornecedor e filial.

**Views principais:** `vw_contas_a_pagar_powerbi`, `vw_dim_planocontas`, `vw_dim_filiais`

**KPIs recomendados:**

- valor total a pagar
- valor pago
- saldo em aberto
- taxa de liquidação
- lead time médio de liquidação = `issue_date -> data_liquidacao`
- despesas por classificação contábil
- despesas por centro de custo
- despesas por fornecedor
- percentual conciliado

**Limitação importante:** sem data de vencimento na estrutura atual, não existe cálculo rigoroso de atraso financeiro. O melhor proxy disponível é aging por emissão/criação.

**Filtros principais:**

- emissão
- data de criação
- data de liquidação
- filial
- fornecedor
- conta contábil
- centro de custo
- tipo de lançamento
- pago / não pago
- conciliado

**Layout sugerido:**

- topo financeiro com KPIs de caixa
- meio com composição por conta, centro de custo e fornecedor
- base com tabela de lançamentos e trilha de liquidação

**Componentes React sugeridos:**

- `KpiCard`
- `ChartCard`
- `AgingBuckets`
- `SupplierTable`
- `ReconciliationBadge`
- `PlanAccountTag`

**Visualizações obrigatórias:**

| Métrica / pergunta | Tipo | Justificativa | Estrutura esperada |
| --- | --- | --- | --- |
| pagos x abertos por mês de competência | `BarChart` empilhado | leitura contábil mensal | `{ month: string; pago: number; aberto: number }[]` |
| despesas por plano de contas | `Treemap` | distribuição por classe contábil | `{ conta: string; valor: number; classificacao: string }[]` |
| despesas por fornecedor | `BarChart` horizontal | concentração de fornecedores | `{ fornecedor: string; valor: number; titulos: number }[]` |
| despesas por centro de custo | `BarChart` | apoiar gestão interna | `{ centroCusto: string; valor: number }[]` |
| conciliação | `DonutChart` | leitura rápida de higiene financeira | `{ status: string; total: number; valor: number }[]` |

**Contrato TypeScript sugerido:**

```ts
export interface ContasAPagarOverview {
  updatedAt: string;
  valorAPagar: number;
  valorPago: number;
  saldoAberto: number;
  taxaLiquidacao: number;
  leadTimeLiquidacaoDias: number;
  pctConciliado: number;
}
```

**Insight cruzado prioritário:**

- `contas a pagar x manifestos`
- views: `vw_contas_a_pagar_powerbi`, `vw_manifestos_powerbi`
- campos: `Filial`, `Conta Contábil/Classificação`, `Custo total`, `Combustível`, `Pedágio`
- pergunta: o perfil de despesas financeiras acompanha a estrutura de custos operacionais da malha

### 6.6 Cotações

**Página:** `Comercial / Cotações`

**Objetivo:** medir geração de demanda, taxa de conversão, perda comercial, corredores mais valiosos e eficiência de precificação.

**Views principais:** `vw_cotacoes_powerbi`, `vw_dim_clientes`, `vw_dim_filiais`

**KPIs recomendados:**

- total de cotações
- valor potencial cotado
- frete médio cotado
- frete por kg
- taxa de conversão para CT-e
- taxa de conversão para NFS-e
- taxa de reprovação
- tempo médio até emissão
- principais motivos de perda

**Filtros principais:**

- data da cotação
- filial
- cliente pagador
- origem
- destino
- trecho
- status de conversão
- tabela de preço
- tipo de operação

**Layout sugerido:**

- cards comerciais no topo
- linha intermediária com funil de conversão e ranking de corredores
- base com tabela comercial de cotações, motivos de perda e observações

**Componentes React sugeridos:**

- `KpiCard`
- `FunnelChartCard`
- `RouteRankingTable`
- `FilterBar`
- `ChartCard`
- `DataTable`

**Visualizações obrigatórias:**

| Métrica / pergunta | Tipo | Justificativa | Estrutura esperada |
| --- | --- | --- | --- |
| cotações por dia e conversão | `LineChart` | acompanhar demanda e fechamento | `{ date: string; cotacoes: number; convertidas: number; reprovadas: number }[]` |
| funil comercial | `FunnelChart` | leitura clara do pipeline | `{ etapa: string; total: number }[]` |
| corredores mais valiosos | `BarChart` horizontal | origem-destino é dimensão central do pricing | `{ trecho: string; valorCotado: number; cotacoes: number; freteKg: number }[]` |
| motivos de perda | `BarChart` | identificar entraves comerciais | `{ motivo: string; total: number }[]` |
| tabela de preço x conversão | `ScatterChart` | comparar pricing e fechamento | `{ tabela: string; taxaConversao: number; freteMedio: number; cotacoes: number }[]` |

**Contrato TypeScript sugerido:**

```ts
export interface CotacoesOverview {
  updatedAt: string;
  totalCotacoes: number;
  valorPotencial: number;
  freteMedio: number;
  freteKgMedio: number;
  taxaConversaoCte: number;
  taxaConversaoNfse: number;
  taxaReprovacao: number;
  tempoMedioConversaoHoras: number;
}
```

**Insight cruzado prioritário:**

- `cotações x fretes`
- views: `vw_cotacoes_powerbi`, `vw_fretes_powerbi`
- campos: `Cliente`, `Trecho`, `Valor frete`, `Pagador`, `Origem`, `Destino`
- pergunta: quais cotações efetivamente viram operação e em quais corredores o preço estimado diverge do preço realizado

### 6.7 Localização de cargas

**Página:** `Tracking / Localização de Cargas`

**Objetivo:** monitorar carteira em trânsito, risco de atraso, distribuição por status e posição da carga na rede.

**Views principais:** `vw_localizacao_cargas_powerbi`, `vw_dim_filiais`

**KPIs recomendados:**

- total de cargas monitoradas
- cargas por status
- cargas com previsão vencida
- valor total em trânsito
- peso taxado em trânsito
- cargas por filial atual
- cargas por região de destino
- percentual finalizado

**Filtros principais:**

- data do frete
- previsão de entrega
- filial emissora
- filial atual
- filial destino
- região origem
- região destino
- status da carga
- tipo
- classificação

**Layout sugerido:**

- cards superiores de risco e volume em trânsito
- mapa/grade central por origem, destino e posição atual
- tabela operacional com foco em cargas vencidas e em transferência

**Componentes React sugeridos:**

- `KpiCard`
- `TrackingMapCard`
- `AgingBuckets`
- `StatusTimeline`
- `DataTable`
- `LastUpdatedBadge`

**Visualizações obrigatórias:**

| Métrica / pergunta | Tipo | Justificativa | Estrutura esperada |
| --- | --- | --- | --- |
| status da carga | `DonutChart` | visão imediata do portfólio em trânsito | `{ status: string; total: number; valorFrete: number }[]` |
| previsão vencida por filial atual | `BarChart` | atacar gargalos na rede | `{ filialAtual: string; vencidas: number; total: number }[]` |
| origem x destino | `Tabela` ou `Heatmap` | leitura de malha e deslocamento | `{ origem: string; destino: string; cargas: number; valorFrete: number }[]` |
| valor em trânsito por região | `BarChart` | exposição financeira da carteira em aberto | `{ regiaoDestino: string; valorFrete: number; cargas: number }[]` |
| linha temporal por status | `AreaChart` | tendência operacional ao longo do tempo | `{ date: string; pendente: number; emEntrega: number; emTransferencia: number; finalizado: number }[]` |

**Contrato TypeScript sugerido:**

```ts
export interface TrackingOverview {
  updatedAt: string;
  totalCargas: number;
  emTransito: number;
  previsaoVencida: number;
  valorFreteEmCarteira: number;
  pesoTaxadoTotal: number;
  pctFinalizado: number;
}
```

**Insight cruzado prioritário:**

- `tracking x fretes`
- views: `vw_localizacao_cargas_powerbi`, `vw_fretes_powerbi`
- campos: `Filial Emissora`, `Filial Destino`, `Região Destino`, `Valor Frete`
- pergunta: quais corredores apresentam maior carteira em trânsito e maior risco financeiro de atraso

## 7. Dashboards transversais recomendados

### 7.1 Executivo

Objetivo:

- consolidar receita, operação, faturamento, cobrança e risco em uma única visão de diretoria

KPIs sugeridos:

- receita operacional
- valor faturado
- saldo a receber
- saldo a pagar
- backlog de coletas
- cargas com previsão vencida
- ocupação média dos manifestos

### 7.2 Saúde do ETL

Fonte:

- `vw_bi_monitoramento`

KPIs:

- tempo médio de execução
- execuções com erro
- volume processado por execução
- categoria de erro

## 8. Estrutura recomendada para React + Next.js

### 8.1 Organização de pastas

```txt
app/
  (dashboards)/
    coletas/page.tsx
    manifestos/page.tsx
    fretes/page.tsx
    faturas/page.tsx
    contas-a-pagar/page.tsx
    cotacoes/page.tsx
    tracking/page.tsx
components/
  dashboard/
    filter-bar.tsx
    kpi-card.tsx
    chart-card.tsx
    data-table.tsx
    empty-state.tsx
    last-updated.tsx
  domain/
    coletas/
    manifestos/
    fretes/
    financeiro/
lib/
  dashboard/
    formatters.ts
    chart-colors.ts
    filters.ts
    mappers.ts
types/
  dashboards/
```

### 8.2 Componentes compartilhados

- `KpiCard`
- `KpiDeltaCard`
- `ChartCard`
- `FilterBar`
- `DateRangePicker`
- `AsyncSelect`
- `DataTable`
- `StatusBadge`
- `LastUpdated`
- `EmptyState`
- `ExportButton`

### 8.3 Boas práticas de frontend

- responsividade mobile-first com breakpoints reais de operação
- cards de KPI em coluna única no mobile e grid no desktop
- dark/light mode com tokens CSS e sem lógica duplicada no componente
- separação entre fetch/normalização e renderização
- acessibilidade com `aria-label`, foco visível, navegação por teclado e texto alternativo
- tabelas com colunas congeladas para IDs e documentos críticos
- estados de loading, empty, partial error e stale data explícitos
- uso de `Suspense` e streaming no App Router para páginas pesadas
- filtros persistidos em URL (`searchParams`) para compartilhamento e rastreabilidade

## 9. Melhorias recomendadas no schema

Prioridade alta:

1. Corrigir `vw_coletas_powerbi` para não duplicar coletas quando houver múltiplos manifestos por `pick_sequence_code`.
2. Monitorar duplicidades reais em `faturas_graphql` por `document + issue_date` e evoluir para uma chave financeira estável se a API expuser esse vínculo.
3. Redesenhar `contas_a_pagar` para suportar grain por rateio, com chave composta ou surrogate key.
4. Converter `localizacao_cargas.taxed_weight` e `localizacao_cargas.invoices_value` para `DECIMAL`.
5. Converter `cotacoes.real_weight` para `DECIMAL`.

Prioridade média:

1. Adicionar dimensão de cliente baseada em documento e não apenas em nome normalizado.
2. Adicionar chave formal para plano de contas.
3. Transformar `status_updated_at` de coletas em tipo temporal real.
4. Expor `data_entrega_real` por frete se a origem suportar isso.

## 10. Métricas avançadas possíveis

### OTIF

Hoje só é possível calcular um proxy parcial.
Para OTIF verdadeiro por entrega, o produto precisa de:

- data/hora real de entrega por frete
- compromisso de entrega por frete
- evidência de integridade do volume/documento entregue

### Lead Time logístico

Já é viável calcular:

- cotação -> emissão (`requested_at -> cte_issued_at/nfse_issued_at`)
- coleta solicitada -> finalizada (`request_date -> finish_date`)
- manifesto criado -> concluído (`created_at -> finished_at/closed_at`)
- faturamento emitido -> baixa/pagamento

### SLA operacional

Já é viável medir:

- coleta no dia agendado
- cotação convertida em até X horas
- manifesto encerrado em até X horas
- carga com previsão ainda não vencida

## 11. Entregáveis gerados

- relatório estratégico: `docs/relatorio-bi-dashboards-logistica.md`
- catálogo técnico das views: `docs/relatorio-bi-catalogo-views.md`
