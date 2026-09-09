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
- Desde a correção de 08/09/2026, o cliente de `usuarios_sistema` envia `IndividualInput.updatedAt` pelos dias da janela planejada, junto de `enabled: true`. A sobreposição diária é intencional e absorvida pelo upsert; a seleção do node permanece `id`/`name`. Regra `ETL-V1-USUARIOS-001`, contrato em `docs/moderno/extracao/graphql.md`.
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
- `ETL-V1-COMPLETUDE-001`: `usuarios_sistema` não possui mais exceção de sucesso para `LIMITE_PAGINAS`. `ExtractionLogger` preserva `INCOMPLETO_LIMITE`, `apiCompleta=false` e `sucesso=false`; a confirmação da janela permanece bloqueada. Janela vazia com paginação completa continua sendo sucesso.
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

## Correção pontual da paralisação do ETL — 08/09/2026

**Estado:** correção aplicada ao código principal após autorização do usuário, com testes incorporados e `target/extrator.jar` compilado/empacotado em 08/09/2026 às 20:24. Os comentários, logs e contrato GraphQL foram atualizados. As duas expectativas antigas de timeout agora verificam um override explícito de teste, sem alterar os limites operacionais. O usuário subiu o pacote no PM2 às 21:06:50; a verificação posterior pela IA é somente de leitura. Não houve start/restart ou recovery executado pela IA. As alterações anteriores em `.gitignore` e `AGENTS.md` foram preservadas.

**Comportamento anterior comprovado:** `usuarios_sistema`, primeiro step do pipeline, consultava `individual` sem filtro temporal. A API entregava 20 registros por página apesar de `first: 1000`; a execução chegava a 2.000 páginas/40.000 registros e demorava aproximadamente 74 minutos. O limite global do ciclo é 60 minutos, enquanto o timeout de usuários é 120 minutos. O ciclo falhava antes de prosseguir para as outras entidades. Além disso, `ExtractionLogger` convertia indevidamente `LIMITE_PAGINAS` de usuários em `COMPLETO`, permitindo aparência de sucesso e confirmação de uma janela incompleta. As cargas auxiliares D-1 explicam os registros até 12:42:13 de 08/09; seu término deixou visível a paralisação do ciclo principal. Ausência de movimento em fim de semana/feriado não explica esse timeout.

**Solução aplicada:** o cliente envia `params.updatedAt` no formato `YYYY-MM-DD - YYYY-MM-DD`, derivado das datas da janela já planejada, mantendo `enabled: true`, paginação por cursor e upsert. A API aceita `updatedAt` em `IndividualInput`; o campo não existe no objeto de saída `Individual`, portanto não foi adicionado à seleção de `node`. Reconsultar os dias das extremidades é uma sobreposição intencional para não perder alterações dentro do dia. A exceção que transformava limite de páginas em sucesso foi removida, assim como o parâmetro privado `entityName` que ficou sem uso. Limite, erro e interrupção preservam incompletude e impedem confirmação do watermark. Não houve mudança de timeout, jobs, dependências ou schema para esta correção.

**Evidências da investigação anterior à aplicação, em 08/09/2026:**

- Consulta real somente de leitura, janela `2026-09-08 - 2026-09-08`: 97 registros únicos, 5 páginas, paginação completa, 9,903 segundos. O cliente Java candidato, consumindo os registros somente em memória e sem acesso ao banco, obteve os mesmos 97 registros/5 páginas em 9,510 segundos. Controle com janela futura retornou zero registros e paginação completa, sem fallback para consulta global.
- Antes do patch, os cinco testes iniciais reproduziram quatro falhas: filtro ausente e falso status completo. A validação final direcionada executou 73 testes, todos aprovados, e empacotou o JAR candidato. Os seis testes novos incluem a continuidade do pipeline montado pelo `PipelineCompositionRoot`, com cliente real contra HTTP local e gateways seguintes simulados: GraphQL, DataExport, Raster habilitado e Data Quality foram visitados dentro de 10 segundos. Isso valida a orquestração local, não equivale a uma carga completa em produção.
- Suíte geral: 534 testes, 524 aprovados, 2 falhas, 7 erros e 1 ignorado. Os seis testes novos passaram. As duas falhas em `GraphQLPipelineStepTest` e `GraphQLGatewayAdapterTest` esperam 30 minutos, mas a configuração atual retorna 120 minutos para usuários; ambas foram reproduzidas novamente com os fontes originais. Os sete erros são de testes SQL Server sem `DB_URL` de teste disponível; não foram fornecidas credenciais produtivas a testes que executam escrita.
- Empacotamento Maven da cópia candidata concluído. PMD executado; seu guard de regressão reprovou contra `.ci/pmd-baseline.txt`. A comparação executada entre fontes originais e candidatos apresentou os mesmos 76 apontamentos ativos, sem novos apontamentos ativos do candidato. O gate global de análise estática permanece pendente.
- `database/validacao/034_validar_schema_recriacao.sql` executado sem escrita em tabelas persistentes: reprovou por ausência dos registros das migrations `047_criar_tabela_regras_atribuicao_filial` e `048_inserir_regra_frigelar_cwb` em `dbo.schema_migrations`. Nenhuma migration foi aplicada. O script `031_limpar_dados_todas_tabelas.sql` não foi executado, pois contém exclusões físicas e não é uma validação segura.
- Artefatos da investigação preservados: `candidate.patch`, `api-pagination-evidence.json`, `java-live-evidence.json`, `baseline-reports/`, `final-focused-package.log`, `final-focused-package-reports/`, `final-focused-summary.json`, `full-suite-complete-resources-reports/`, `suite-summary.json`, `original-timeout-tests.log`, `pmd-comparison.json`, `pmd-guard.log`, `schema-evidence.json` e `original-hashes.json`, todos sob `.tmp/validacao-contencao-20260908/`. Os testes foram incorporados aos fontes nesta entrega.

**Validação dos fontes aplicados e do pacote final:**

- Suíte completa executada: **534 testes, 526 aprovados, 0 falhas de asserção, 7 erros por ausência de `DB_URL` de teste e 1 ignorado** (`CurlBancoPeriodoFechadoOperationalProofTest`). As duas falhas de timeout da investigação anterior foram resolvidas. Não foram desabilitados testes nem fornecidas credenciais produtivas aos testes SQL que escrevem dados.
- Empacotamento com seleção explícita de 14 classes relacionadas à correção: **77 testes, 0 falhas/erros/ignorados; Maven package concluído**. Os seis testes novos verificam filtro/cursor, janela vazia, erro sem consulta global, completude e continuidade das etapas. O teste do orquestrador usa HTTP local para usuários e simula as demais etapas; não afirma ter extraído todas as tabelas produtivas.
- **Consulta real somente de leitura usando as classes do próprio `target/extrator.jar`: 110 registros únicos em 6 páginas, 11,900 segundos, API completa**, na janela diária de 08/09. O crescimento em relação aos 97 registros da investigação ocorreu entre as consultas. Nenhum registro foi persistido por esse probe.
- Integridade ZIP, manifest/Main-Class e igualdade das quatro classes alteradas no JAR com as classes compiladas/testadas conferidas. Smoke direto do JAR: `--ajuda` e `--auth-info` retornaram zero; SQLite criado apenas na pasta local de teste. O script PowerShell de smoke foi bloqueado por falta de assinatura; sua política de execução permaneceu intacta e os mesmos comandos Java foram validados diretamente.
- PMD: **76 apontamentos ativos, nenhum novo em relação aos fontes anteriores**; o guard global continua reprovando contra o baseline. Guards de `catch Throwable` e duplicação SQL passaram. Permanecem falhas anteriores no guard de versão (README sem metadado esperado), tamanho de classes (arquivos não alterados nesta correção) e mojibake (64 ocorrências em `CompletudeValidator.java`, também não alterado).
- Análise Maven de dependências executada: terminou com avisos de dependências transitivas/não usadas e classificação do SQLite; nenhuma dependência foi modificada. Formatter e scanners locais de vulnerabilidades/segredos não possuem execução configurada nesta entrega. Diff, UTF-8 e whitespace dos arquivos da correção conferidos; a busca pelos valores de credenciais locais nos arquivos alterados e no conteúdo do JAR retornou zero ocorrências. O JAR não contém `.env`. Essa checagem não substitui um scanner de vulnerabilidades/segredos.
- A validação de schema `034_validar_schema_recriacao.sql` foi repetida em modo de leitura e manteve somente as duas migrations 047/048 sem registro. Não houve alteração estrutural nem necessidade de migration para o filtro.
- Pacote: `target/extrator.jar`, **20.799.106 bytes**, SHA-256 **`f2048d627bd9217d468bc32a5d938732d732d27b5234fd8b0c699e5b88f1c80f`**. Backup do JAR anterior: `.tmp/entrega-contencao-20260908/extrator-anterior.jar`, SHA-256 `795db4abe8c428a266eeedf85b7d02dcf1701fb6b6f689e10686a346b4a39ff6`. Restaurar esse backup reintroduz o defeito anterior; ele é somente recurso de recuperação.
- Evidências finais em `.tmp/entrega-contencao-20260908/`: `full-suite-summary.json`, `full-suite-reports/`, `focused-package.log`, `focused-package-summary.json`, `package-evidence.json`, `pmd-evidence.json`, `guards-summary.json`, `dependency-analysis.log`, `schema-evidence.json` e `changes.patch`. A pasta é local/ignorada pelo Git; fontes, testes e contrato corrigidos estão no repositório.

**Verificação após a subida pelo usuário:**

- PM2 `ETL-EXTRACAO-DADOS-LOOP` online, PID `41856`, iniciado em 08/09/2026 às 21:06:50. O SHA-256 do JAR confere com o pacote entregue. O contador de reinícios permaneceu em 29 antes e depois do ciclo, igual à saída enviada pelo usuário.
- **Primeiro ciclo concluído com `SUCCESS`:** `7e94b3ec-5076-42c6-9311-70464941e2ab`; execução `c2bc6998-f581-4a41-b805-ae7464b5d876`, de **21:06:52 a 21:35:59**, duração registrada de **1.746 segundos (aproximadamente 29 minutos)**, abaixo do limite global de 60 minutos. O resumo do daemon registrou 35.592 registros processados em `log_extracoes`, zero warnings e zero errors no resumo do ciclo. Esse total é de processamento auditado, não uma contagem de novas linhas distintas na base.
- Auditoria SQL confirmou usuários `COMPLETO`: 123 registros únicos, 7 páginas, 112 persistidos e 11 no-ops, zero inválidos, `api_completa=1`, de 21:06:55 a 21:07:09 (aproximadamente 14 segundos). O bloqueio original em usuários não se repetiu.
- Coletas também concluiu: 1.141 registros persistidos, 61 páginas, zero inválidos, `api_completa=1`, de 21:07:14 a 21:09:29. O processo avançou para Fretes às 21:09:32.
- Fretes concluiu às 21:20:53: **4.418 registros**, 221 páginas, `COMPLETO`, `api_completa=1`, zero inválidos. As sete entidades Data Export também terminaram completas: **Manifestos 1.582**, **Cotações 1.276**, **Localização de Cargas 4.423**, **Contas a Pagar 591**, **Faturas por Cliente 4.424**, **Inventário 13.054** e **Sinistros 12**, em volumes reportados pela auditoria após deduplicação. Todos os 10 resultados GraphQL/Data Export possuem `api_completa=1` e zero inválidos.
- Raster concluiu às 21:33:41, com **1.001 viagens e 3.547 paradas processadas**, 8 páginas e ambos os logs `COMPLETO`. Assim, **as 12 extrações previstas constam completas em `log_extracoes`** (11 entidades principais e o log adicional de paradas). Consulta direta também confirmou gravações nas duas tabelas Raster desde a subida: 563 linhas em `raster_viagens` e 1.868 em `raster_viagem_paradas`. Os volumes processados podem incluir reconsulta/upsert das mesmas chaves entre dias e não devem ser tratados como quantidade de novas linhas distintas.
- `quality:checks` terminou com `SUCCESS`; o resumo executivo confirmou `validacao_final=true`, `completude_nao_ok=0`, `integridade_falhas=0`, `quality_ok=true` e `quality_falhas=0`. Os 11 watermarks foram atualizados às 21:33:51: usuários para `2026-09-08T21:06:53.0315051`; as demais entidades para `2026-09-09T00:00:00`, conforme suas janelas planejadas.
- **As cinco materializações BI concluíram sem falhas às 21:35:59**, com 4.489 inserções e 135 atualizações reportadas, em 127.755 ms. O daemon passou para `WAITING_NEXT_CYCLE`, sem intervenção manual requerida e com contadores de ciclos sem sucesso/alerta zerados. Próximo ciclo informado pelo daemon: **08/09/2026 às 22:35:59**.
- Evidências: `.tmp/entrega-contencao-20260908/post-deploy-first-cycle.json`, `post-deploy-status.json`, `logs/daemon/ciclos/2026-09-08/extracao_daemon_2026-09-08_21-06-52.log`, `logs/daemon/historico/execucao_daemon_2026_09.csv` e auditoria SQL por `execution_uuid`/`cycle_id`. `sys_execution_history` ainda não apresentou linha para o processo daemon em execução; o fechamento deste ciclo foi comprovado pelo histórico CSV do daemon, pelo estado operacional e pelos resultados em `sys_execution_audit`/`log_extracoes`. Os dados produtivos foram consultados por `SELECT`; a IA não executou extração paralela nem alterou o runtime.

**Governança:** origem = incidente e solicitações do usuário em 08/09/2026; responsável técnico proposto = manutenção do ETL/TI, pessoa a designar; responsável de negócio não informado. Implementação das regras `ETL-V1-USUARIOS-001` e `ETL-V1-COMPLETUDE-001`, incorporação dos testes, geração do pacote, subida pelo usuário e primeiro ciclo completo em produção concluídos. A retomada está comprovada neste ciclo; falta confirmar a repetição no segundo ciclo e tratar separadamente as lacunas históricas/pendências de validação já registradas.

## Tarefas Pendentes

- [ ] **[P0 — ETL-V1-TESTES-001] Concluir a validação SQL pendente.** Os testes de regressão já estão incorporados, as expectativas de timeout corrigidas e a suíte executada. Faltam os sete testes SQL em ambiente de teste autorizado: `ColetaStatusCatalogParityTest`, `ColetaTerminalStatusMergeTest`, `FreteTerminalStatusMergeTest`, `InventarioComprovanteMergeTest` e `ManifestoMergeIntegrityTest`, em `src/test/java/br/com/extrator/persistencia/repositorio/`. Não ocultar os erros nem fornecer banco produtivo aos testes de escrita. Revisão humana do diff ainda não registrada.
- [ ] **[P0 — ETL-V1-OPERACAO-001] Confirmar o segundo ciclo após a subida.** **Primeiro ciclo integral aprovado** às 21:35:59: todas as extrações, Data Quality e cinco materializações passaram em aproximadamente 29 minutos. Falta observar o segundo ciclo, previsto pelo daemon para 22:35:59 de 08/09/2026, e confirmar novamente todas as entidades, auditorias e watermarks, sem timeout ou reinício. Esta verificação não criou agendamento nem monitor persistente. Zero registros é aceitável quando a consulta termina completa; ausência de execução de uma entidade não é.
- [ ] **[P0 — ETL-V1-RECUPERACAO-001] Recuperar as janelas afetadas sem repetir a varredura global.** Identificar a última janela comprovadamente completa por entidade, desconsiderando o falso sucesso de usuários por limite de páginas. Com o cliente corrigido, usar os comandos existentes de extração por intervalo/recovery em janelas controladas, preservando upsert e exclusão lógica; não avançar watermarks manualmente para esconder lacunas. Conferir persistência e materialização das janelas recuperadas. A execução produtiva desta tarefa permanece a cargo da operação; nenhum recovery foi disparado na investigação.
- [ ] **[P1 — ETL-V1-VALIDACAO-001] Resolver as pendências gerais de validação registradas.** Investigar os 76 apontamentos PMD, os guards de versão/tamanho/mojibake e os avisos de dependências já presentes, sem silenciar regras ou ampliar baseline para obter aprovação. Registrar a lacuna de formatter e análise local de vulnerabilidades/segredos. Conferir os objetos/efeitos e a rastreabilidade das migrations 047/048 antes de qualquer regularização do histórico; não reaplicar DDL/DML nem inserir registros de migração às cegas. Esses problemas impedem declarar a validação global aprovada e permanecem separados do patch pontual. Se houver necessidade de alteração estrutural, seguir migration/baseline e validação de schema do repositório.

### Pendências anteriores preservadas

- [Operacional] Conferir o primeiro disparo automático da tarefa `ETL Expurgo Orfaos Noturno` em 31/07/2026 às 03:00. A ação já aponta para o script atual e a execução real manual equivalente terminou com sucesso em 30/07: 14.445 chaves ESL, 127 ausências, 126 exclusões lógicas e 1 candidata. A checagem confirma somente o agendamento automático, não requer nova correção de dados.
- [Negócio] Confirmar com a operação se o status ESL bruto `done` de Coletas deve ser exibido como `Coletada` ou `Finalizada`; há exemplos históricos das duas interpretações e a regra não deve ser alterada sem essa definição.
