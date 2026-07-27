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
- Fluxo completo: planeja janelas, executa pre-backfill de coletas, roda steps `usuarios_sistema`, `coletas`, `fretes`, DataExport, Raster quando habilitado e Data Quality.
- GraphQL ESL usa `API_BASEURL` padrão `https://rodogarcia.eslcloud.com.br`, endpoint `/graphql` e `api.corporation.id=385129`.
- Entidades GraphQL: `usuarios_sistema`, `coletas`, `coletas_referencial` e `fretes`.
- Data Export extrai `manifestos`, `cotacoes`, `localizacao_cargas`, `contas_a_pagar`, `faturas_por_cliente`, `inventario` e `sinistros`.
- Templates DataExport configurados incluem `manifestos=6399`, `localizacao=8656`, `cotacoes=6906`, além dos limites específicos para templates `8656`, `4924`, `6399`, `6389`, `6906` e `8636`.
- Raster, quando habilitado por `RASTER_ENABLED`, consulta viagens e paradas e persiste `dbo.raster_viagens` e `dbo.raster_viagem_paradas`.
- Persistência operacional inclui `dbo.coletas`, `dbo.fretes`, `dbo.manifestos`, `dbo.cotacoes`, `dbo.localizacao_cargas`, `dbo.contas_a_pagar`, `dbo.faturas_por_cliente`, `dbo.inventario`, `dbo.sinistros`, `dbo.dim_usuarios`, `dbo.dim_calendario`, `dbo.dim_regiao_logistica_rules` e tabelas Raster.
- Auditoria e controle incluem `dbo.log_extracoes`, `dbo.page_audit`, `dbo.sys_execution_history`, `dbo.sys_execution_audit`, `dbo.sys_execution_watermark`, `dbo.schema_migrations`, `dbo.etl_invalid_records` e `dbo.sys_reconciliation_quarantine`.
- Procedures de materialização BI: `dbo.sp_carga_fato_gestao_vista_fretes`, `dbo.sp_carga_fato_gestao_vista_coletores`, `dbo.sp_carga_fato_fretes_faturamento`, `dbo.sp_carga_fato_gestao_vista_faturas` e `dbo.sp_carga_fato_gestao_vista_manifestos`.
- Contratos publicados ao Dashboard/Power BI: `dbo.vw_coletas_powerbi`, `dbo.vw_fretes_powerbi`, `dbo.vw_manifestos_powerbi`, `dbo.vw_localizacao_cargas_powerbi`, `dbo.vw_contas_a_pagar_powerbi`, `dbo.vw_cotacoes_powerbi`, `dbo.vw_faturas_por_cliente_powerbi`, `dbo.vw_inventario_powerbi`, `dbo.vw_sinistros_powerbi`, `dbo.vw_fato_manifestos_dash`, `dbo.vw_raster_sm_transit_time` e `dbo.vw_dim_*`. A view de coletas publica `[Região Logística]` com precedência Faixa de CEP -> Cidade/UF -> Cidade - UF.

## Regras de Negócio Consolidadas
- Este projeto é o único dono estrutural de `ETL_SISTEMA`/`esl_cloud`; criação de tabelas, índices, constraints, procedures, fatos e views analíticas deve acontecer aqui.
- O Dashboard é consumidor read-only dos objetos publicados pelo ETL; não deve haver DDL/DML de Dashboard contra `ETL_SISTEMA`.
- Toda mudança estrutural deve atualizar migration e baseline correspondente em `database/tabelas`, `views`, `views-dimensao`, `procedures`, `indices`, `validacao`, README e executor quando aplicável.
- O ciclo recorrente é aditivo: insere/atualiza registros novos ou alterados e não executa `DELETE`/`TRUNCATE` no caminho comum.
- Ausências na origem são tratadas por expurgo lógico noturno (`excluido_na_origem=1`) com metadados como `data_exclusao_origem` e `ultima_reconciliacao_origem_em`; reaparecimento reativa a chave.
- Views operacionais e analíticas devem filtrar `excluido_na_origem=1` por padrão, exceto diagnósticos/auditorias explícitas.
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
- Segurança local SQLite e comandos `--auth-*` não substituem a segurança do banco SQL Server; servem à operação do CLI.
- UTF-8 é obrigatório para Java, SQL, logs e arquivos de configuração; mojibake deve ser corrigido na origem.

## Protocolo de Planejamento de Requisições
- Antes de iniciar qualquer planejamento ou escrita de código, a IA DEVE OBRIGATORIAMENTE ler `AGENTS.md` do projeto local e `CONTEXTO_GLOBAL.md`.
- O `CONTEXTO_GLOBAL.md` dita as regras do ecossistema e o `AGENTS.md` dita as regras locais. Falhar em ler e aplicar essas regras resulta em quebra arquitetural.
- Ao receber uma nova requisição para este projeto, atuar como Arquiteto de Software e usar este `states.md` como ESTADO ATUAL.
- A análise deve respeitar a stack, a arquitetura, as fronteiras de banco e os contratos de dados descritos neste arquivo.
- A resposta de planejamento deve retornar somente o bloco `## Tarefas Pendentes`, formatado em Markdown.
- O bloco deve decompor a requisição em tarefas sequenciais, lógicas e granulares, especificando arquivos exatos, variáveis, tipagens e validações que deverão ser alterados ou criados.
- É proibido incluir saudações, conclusões, explicações fora dos bullets ou reescrever outras seções durante a resposta de planejamento.

## Tarefas Pendentes
# 📋 TAREFA TÉCNICA: Carga de Regras na Dimensão de Regiões Logísticas

**Atenção, Codex:** Atue como Engenheiro de Dados. A estrutura da tabela `dbo.dim_regiao_logistica_rules` já foi criada no repositório `etl-extracao-dados`, mas ela está vazia. Precisamos popular essa tabela com as regras de negócio fornecidas pelo gestor para que a view de Coletas passe a cruzar a informação corretamente.

Antes de prosseguir, certifique-se de estar operando no contexto do repositório `etl-extracao-dados` e respeite as regras de `AGENTS.md` e `CONTEXTO_GLOBAL.md`.

## 🛠️ Escopo da Tarefa

1. **Analisar a Lista de Regras:** Leia a lista de "De/Para" abaixo fornecida pela operação. Identifique o que é regra de Faixa de CEP e o que é regra de fallback por Cidade/UF.

[COLE AQUI A SUA LISTA, TEXTO OU RESUMO DO EXCEL COM AS REGRAS]

2. **Criar Script SQL Idempotente:**
Crie um novo arquivo de script SQL na pasta correspondente de migrações ou scripts de carga (ex: `database/migrations/050_carga_inicial_dim_regiao_logistica.sql` ou similar, seguindo a numeração atual). 

O script deve conter instruções de `INSERT` com verificação de não-existência (idempotente) ou usar `MERGE` para inserir as regras de forma segura, alimentando as colunas corretas:
- `cep_inicio` e `cep_fim` (para faixas de CEP, mantendo apenas números).
- `cidade` e `uf` (para regras de município).
- `regiao_logistica` (A Macro-Região final).

## 🚦 Critérios de Aceite
* O script SQL deve ser gerado com sintaxe válida para SQL Server (T-SQL).
* Valores de CEP devem ser inseridos limpos (sem hífen, formato VARCHAR(8)).
* Nenhuma alteração estrutural (DDL) deve ser feita, apenas inserção de dados (DML).
* Apresente o código SQL final pronto para ser executado no banco `ETL_SISTEMA`.
