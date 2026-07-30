# Estado Atual do Sistema

## Stack Tecnológica

- Java 17 CLI/daemon com Maven e geração de fat JAR `target/extrator.jar` via `maven-shade-plugin`.
- Jackson Databind/JSR310 para JSON, Microsoft JDBC Driver para SQL Server, HikariCP para pool JDBC, SQLite JDBC para segurança local, SLF4J/Logback para logs e JUnit Jupiter para testes.
- Banco principal SQL Server `ETL_SISTEMA`/`esl_cloud`, propriedade estrutural exclusiva deste projeto.
- SQLite local para autenticação operacional do CLI.
- Scripts SQL/T-SQL versionados em `database/`; automação Windows em `scripts/windows`, `scripts/ci` e `database/executar_database.bat`.
- Integrações externas: ESL Cloud GraphQL, ESL Cloud Data Export e Raster API.

## Arquitetura e Padrões

- Arquitetura limpa/hexagonal para CLI, com composition root manual e sem Spring IoC no runtime principal.
- `bootstrap.Main` é o entrypoint; interpreta comandos, inicializa contexto quando necessário, controla histórico de execução e códigos de saída.
- `comandos/cli` registra comandos no `CommandRegistry` e delega para casos de uso.
- `aplicacao` contém casos de uso de extração, expurgo, reconciliação, políticas, portas e orquestração de pipeline.
- `bootstrap/pipeline/PipelineCompositionRoot` instancia adapters GraphQL, DataExport, Raster, auditoria, completude, integridade e data quality.
- `integracao` contém clientes HTTP, paginadores, extractors e mappers para GraphQL, DataExport e Raster.
- `persistencia` contém repositórios JDBC/SQL Server, watermarks, auditoria e adapters de consultas.
- `features` isola estratégias por domínio (`coletas`, `fretes`, `manifestos`, `localizacao`, `usuarios`).
- `observabilidade` e `plataforma` concentram logs estruturados, auditoria, data quality, validação, métricas e relatórios.
- Banco canônico em `database/tabelas`, `views`, `views-dimensao`, `procedures`, `indices`, `validacao`, `seguranca` e `migrations`; migrations consolidadas ficam em `database/migrations/historico_arquivado`.
- Padrão de dados: carga aditiva, upsert idempotente, expurgo lógico noturno, auditoria por página/execução e materialização SQL de fatos BI.
- Dados extraídos, cadastros de suporte, fatos, auditoria e histórico de BI usam exclusão lógica obrigatória. Hard delete/`DELETE FROM`/`TRUNCATE` em rotinas comuns é proibido; quando houver ausência na origem, use `excluido_na_origem`, `ativo`, `deleted_at` ou vigência, e filtre inativos nas views/materializações por padrão.
- A dimensão governada `dbo.dim_regiao_logistica_rules` resolve macro-regiões de Coletas por faixa de CEP ou Cidade/UF. O baseline usa `database/tabelas/034_criar_tabela_dim_regiao_logistica_rules.sql`, a migration ativa é `database/migrations/049_criar_dim_regiao_logistica_rules.sql` e os índices ficam em `database/indices/003_criar_indices_dim_regiao_logistica.sql`.

## Fluxo de Dados e Integrações

- Comando padrão sem argumentos ou `--fluxo-completo` executa o ciclo intradia planejado por entidade.
- Comandos vigentes incluem `--extracao-intervalo`, `--fechamento-mensal`, `--recovery`, `--expurgo-orfaos`, `--loop`, `--loop-daemon-start`, `--loop-daemon-stop`, `--loop-daemon-status`, `--loop-daemon-run`, `--materializar-fatos-bi`, `--materializar-fatos-bi-scheduler`, validações, auditorias e comandos `--auth-*`.
- A tarefa agendada `ETL Expurgo Orfaos Noturno` executa diariamente às 03:00 o script atual `etl-dash\etl-extracao-dados\scripts\windows\10-expurgo-orfaos-noturno.ps1`. Ela usa o JAR atual para obter um snapshot completo e reconciliar Coletas fora do caminho intradia, importando `API_GRAPHQL_PAGINACAO_ANOMALIA_MAX_TENTATIVAS=2` do ambiente operacional.
- Fluxo completo: planeja janelas, executa pre-backfill de coletas, roda steps `usuarios_sistema`, `coletas`, `fretes`, DataExport, Raster quando habilitado e Data Quality.
- GraphQL ESL usa `API_BASEURL` padrão `https://rodogarcia.eslcloud.com.br`, endpoint `/graphql` e `api.corporation.id=385129`.
- Entidades GraphQL: `usuarios_sistema`, `coletas`, `coletas_referencial` e `fretes`.
- Data Export extrai `manifestos`, `cotacoes`, `localizacao_cargas`, `contas_a_pagar`, `faturas_por_cliente`, `inventario` e `sinistros`.
- Templates DataExport configurados incluem `manifestos=6399`, `localizacao=8656`, `cotacoes=6906`, além dos limites específicos para templates `8656`, `4924`, `6399`, `6389`, `6906` e `8636`.
- Raster, quando habilitado por `RASTER_ENABLED`, consulta viagens e paradas e persiste `dbo.raster_viagens` e `dbo.raster_viagem_paradas`.
- Persistência operacional inclui `dbo.coletas`, `dbo.fretes`, `dbo.manifestos`, `dbo.cotacoes`, `dbo.localizacao_cargas`, `dbo.contas_a_pagar`, `dbo.faturas_por_cliente`, `dbo.inventario`, `dbo.sinistros`, `dbo.dim_usuarios`, `dbo.dim_calendario`, `dbo.dim_regiao_logistica_rules` e tabelas Raster.
- Auditoria e controle incluem `dbo.log_extracoes`, `dbo.page_audit`, `dbo.sys_execution_history`, `dbo.sys_execution_audit`, `dbo.sys_execution_watermark`, `dbo.schema_migrations`, `dbo.etl_invalid_records` e `dbo.sys_reconciliation_quarantine`.
- Procedures de materialização BI: `dbo.sp_carga_fato_gestao_vista_fretes`, `dbo.sp_carga_fato_gestao_vista_coletores`, `dbo.sp_carga_fato_fretes_faturamento`, `dbo.sp_carga_fato_gestao_vista_faturas` e `dbo.sp_carga_fato_gestao_vista_manifestos`.
- Contratos publicados ao Dashboard/Power BI: `dbo.vw_coletas_powerbi`, `dbo.vw_fretes_powerbi`, `dbo.vw_manifestos_powerbi`, `dbo.vw_localizacao_cargas_powerbi`, `dbo.vw_contas_a_pagar_powerbi`, `dbo.vw_cotacoes_powerbi`, `dbo.vw_faturas_por_cliente_powerbi`, `dbo.vw_inventario_powerbi`, `dbo.vw_sinistros_powerbi`, `dbo.vw_fato_manifestos_dash`, `dbo.vw_raster_sm_transit_time` e `dbo.vw_dim_*`.

## Regras de Negócio Consolidadas

- Este projeto é o único dono estrutural de `ETL_SISTEMA`/`esl_cloud`; criação de tabelas, índices, constraints, procedures, fatos e views analíticas deve acontecer aqui.
- O Dashboard é consumidor read-only dos objetos publicados pelo ETL; não deve haver DDL/DML de Dashboard contra `ETL_SISTEMA`.
- Toda mudança estrutural deve atualizar migration e baseline correspondente em `database/tabelas`, `views`, `views-dimensao`, `procedures`, `indices`, `validacao`, README e executor quando aplicável.
- O ciclo recorrente é aditivo: insere/atualiza registros novos ou alterados e não executa `DELETE`/`TRUNCATE` no caminho comum.
- Ausências na origem são tratadas por expurgo lógico noturno (`excluido_na_origem=1`) com metadados como `data_exclusao_origem` e `ultima_reconciliacao_origem_em`; reaparecimento reativa a chave.
- Em Coletas, a primeira ausência em snapshot completo já é publicada como `Excluída`; a segunda ausência consecutiva confirma o expurgo lógico. A linha é preservada para auditoria e reaparece ativa se voltar ao ESL. `vw_coletas_powerbi` mantém esse caso visível com status `Excluída`; indicadores agregados do Dashboard o desconsideram.
- Em 30/07/2026 a reconciliação global real de 90 dias concluiu com 14.445 chaves no ESL: 126 ausências foram confirmadas por segunda passagem e marcadas por exclusão lógica; 1 ausência nova ficou como candidata da primeira passagem. As 127 permanecem visíveis como `Excluída` no contrato publicado.
- Agregações, totalizações, rankings, contagens e cruzamentos de BI devem ser executados no SQL Server, não em memória Java.
- Filtros temporais devem ser sargable, sem funções no lado esquerdo de colunas indexadas.
- Regras pesadas de BI devem ser materializadas durante carga ou em procedures/tabelas fato, não calculadas sob demanda em views de apresentação.
- A atribuição financeira de faturamento usa `dbo.regras_atribuicao_filial` por CNPJ do pagador antes da materialização de `dbo.fato_fretes_faturamento`; a regra ativa de Frigelar Garuva (`92660406007040`) direciona a receita para `CWB - RODOGARCIA` (`cwb - rodogarcia`), mesmo quando a emissão original pertence a NHB.
- A resolução de Região Logística de Coletas deve permanecer no ETL: `dbo.vw_coletas_powerbi` cruza `dbo.dim_regiao_logistica_rules` primeiro por CEP limpo de 8 dígitos entre `cep_inicio`/`cep_fim`, depois por correspondência exata de `cidade`/`uf`, e finalmente preserva `Cidade - UF` para não retornar nulo ao Dashboard.
- O fluxo completo usa lock transacional SQL Server (`sp_getapplock`) para evitar execuções concorrentes.
- GraphQL usa política de falha `ABORT_PIPELINE`; DataExport, Raster e Data Quality podem degradar conforme configuração.
- Data Quality valida unicidade, completude, freshness, integridade referencial e schema.
- Watermarks confirmados só avançam para entidades com auditoria completa e status confirmável.
- Coletas executa pre-backfill referencial e pós-hidratação para reduzir órfãos em manifestos.
- O daemon não deve ativar prune de fretes no caminho crítico; reconciliação histórica fica separada.
- `metadata` preserva payload bruto de origem quando campos não são promovidos a colunas físicas.
- UTF-8 é obrigatório para Java, SQL, logs e arquivos de configuração; mojibake deve ser corrigido na origem.

## Protocolo de Planejamento de Requisições

- Antes de iniciar qualquer planejamento ou escrita de código, a IA deve ler `AGENTS.md` do projeto local e `CONTEXTO_GLOBAL.md`.
- O `CONTEXTO_GLOBAL.md` dita as regras do ecossistema e o `AGENTS.md` dita as regras locais.
- Ao receber uma nova requisição para este projeto, atuar como Arquiteto de Software e usar este `states.md` como estado atual.
- A análise deve respeitar a stack, a arquitetura, as fronteiras de banco e os contratos de dados descritos neste arquivo.
- A resposta de planejamento deve retornar somente o bloco `## Tarefas Pendentes`, formatado em Markdown.
- O bloco deve decompor a requisição em tarefas sequenciais, lógicas e granulares, especificando arquivos exatos, variáveis, tipagens e validações que deverão ser alterados ou criados.

## Regras de Status e Indicadores

- Coletas: estado terminal (`finished`, `done`, `canceled`, `cancelled`) supera estado aberto mesmo com data retroativa. A reextração dos 19 exemplos CPQ corrigiu todos: 16 `finished` e 3 `canceled`; comparação direta ESL x banco: 19 comparados, 0 divergências.
- Fretes: antes de promover uma transição terminal, o staging preserva CT-e/finalizações já conhecidos quando o payload atual os omite. Aplicado e verificado nas minutas 391357 (`done` → `finalizado`) e 403285 (`finished` → `finalizado`).
- Performance: `vw_fretes_powerbi` publica `finished`/`done` como `finalizado` e `canceled`/`cancelled` como `cancelada`; migration 052 publicada. Os aliases da view foram republicados em UTF-8 para preservar o contrato do Dashboard.
- Manifestos: status por `sequence_code` é consolidado com prioridade `closed` > `in_transit` > `pending`; migration 051 publicada e fato rematerializada para 29/04–28/07/2026. Reconciliação: 9.648 manifestos, 0 divergências de status.
- Inventário: comprovante anexado é cumulativo por chave lógica; uma ocorrência posterior não pode remover evidência já observada. A regra protege as próximas cargas; o histórico sem evidência de origem não é inferido artificialmente.
- `data_extracao` confirma consulta do registro, não mudança efetiva do status.

## Artefatos Relevantes

- Persistência: `ColetaRepository`, `FreteRepository`, `InventarioRepository`.
- Publicação SQL: `database/procedures/005_criar_sp_carga_fato_gestao_vista_manifestos.sql`, `database/views/012_criar_view_fretes_powerbi.sql`.
- Contratos: `dbo.vw_coletas_powerbi`, `dbo.vw_fretes_powerbi`, `dbo.vw_manifestos_powerbi`.

## Tarefas Pendentes

- [Operacional] Conferir o primeiro disparo automático da tarefa `ETL Expurgo Orfaos Noturno` em 31/07/2026 às 03:00. A ação já aponta para o script atual e a execução real manual equivalente terminou com sucesso em 30/07: 14.445 chaves ESL, 127 ausências, 126 exclusões lógicas e 1 candidata. A checagem confirma somente o agendamento automático, não requer nova correção de dados.
- [Negócio] Confirmar com a operação se o status ESL bruto `done` de Coletas deve ser exibido como `Coletada` ou `Finalizada`; há exemplos históricos das duas interpretações e a regra não deve ser alterada sem essa definição.
