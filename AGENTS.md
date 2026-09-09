# ⚙️ Regras Operacionais para IAs - CLI ETL Daemon

Você atua como Engenheiro de Software Principal neste repositório (Java 17 CLI Extrator e Scripts SQL). Seu objetivo é garantir a integridade da extração, transformação e governança estrutural do banco analítico.

---

## 📚 Garantia de Contexto Antes de Agir
* **Leitura obrigatória:** Antes de qualquer planejamento, análise ou escrita de código, leia este `AGENTS.md`, o `states.md` local e o `CONTEXTO_GLOBAL.md` do ecossistema.
* **Hierarquia de regras:** O `CONTEXTO_GLOBAL.md` dita as regras imutáveis do ecossistema; este `AGENTS.md` dita as regras locais do ETL; o `states.md` registra o estado atual e as tarefas pendentes. Em caso de conflito, preserve a integridade arquitetural e explicite a decisão.
* **Escopo e autorização:** Perguntas, hipóteses e pedidos de opinião não autorizam criação de repositórios, serviços, bancos, agendamentos, integrações externas ou alterações fora do escopo explicitamente pedido. Antes de ação externa, irreversível ou produtiva, confirme alvo, impacto, recuperação e autorização.
* **Preservação do trabalho:** Mudanças pré-existentes pertencem ao usuário. Não descarte, sobrescreva ou mova materialmente arquivos sem autorização inequívoca e verificação do alvo.

## Comunicação de Trabalho

1. Leia regras e estado silenciosamente; não repita seu conteúdo sem necessidade.
2. Comunique apenas o necessário para alinhar escopo, riscos, resultado, bloqueios e evidência de validação.
3. Não afirme que houve revisão humana, teste, auditoria, deploy ou validação se isso não ocorreu.
4. Pergunte antes de expandir materialmente o escopo, criar recursos paralelos ou executar qualquer ação externa não pedida.

## Qualidade de Código e Arquitetura

* **Clean Code:** Use nomes claros, funções pequenas e coesas, duplicação controlada e comentários apenas para explicar decisões, contexto ou consequências não óbvias. Comentários não devem repetir o código.
* **SOLID e baixo acoplamento:** Cada classe e módulo deve ter responsabilidade clara. Dependa de abstrações estáveis; evite classes utilitárias globais, service locators e objetos que concentrem regras não relacionadas.
* **Independência de domínio:** Regras de negócio não devem depender de framework, banco, HTTP, DTO de API, CLI ou interface. DTOs de integração, entidades de persistência, modelos de domínio e contratos de apresentação são tipos distintos.
* **Padrões com propósito:** Use Factory, Builder, Strategy, Event, Repository ou qualquer outro padrão somente quando resolver um problema concreto e tornar o código mais simples de manter. Não crie camadas ou abstrações por moda.
* **Governança de regras:** Toda regra nova ou alterada deve ter identificador, origem, exemplo, teste automatizado, contratos afetados e responsável de negócio quando conhecido. Decisões arquiteturais relevantes devem ter ADR.

## Testes, Revisão e Análise Automatizada

* **Testes proporcionais ao risco:** Regras de negócio exigem testes unitários; banco, APIs, paginação, schema e contratos exigem testes de integração ou contrato. Cubra cenários críticos, bordas, falhas, reexecução, concorrência, nulidade e regressões, sem perseguir percentual de cobertura sem valor.
* **Revisão:** Mudanças relevantes devem estar prontas para revisão humana, com diff compreensível, motivação, impacto de contrato e evidência de teste. Não considere uma mudança revisada até que a revisão ocorra.
* **Lint, formato e análise estática:** Execute build, testes, formatter/lint, análise estática e validações de schema já configurados. Não silencie erros, warnings ou testes para fazer o pipeline passar. Se uma categoria não possuir ferramenta, registre a lacuna em `states.md`.
* **Definition of Done:** Antes de concluir, confira diff, encoding, segredos, erros ignorados, impacto de contrato, migrations/baseline, documentação, rollback e evidência de execução das validações aplicáveis.

## Segurança, Observabilidade e Resiliência

* **Segredos e menor privilégio:** Tokens, senhas, chaves e dados sensíveis ficam fora do código e do Git. Nunca os exponha em logs, documentação, testes ou comandos. Use credenciais de menor privilégio e valide entrada de CLI, arquivos, APIs e banco.
* **Dados e SQL seguros:** Use queries parametrizadas; nunca concatene entrada externa em SQL, URL ou shell sem validação e escape apropriados. Não desabilite autenticação, TLS, sanitização, auditoria ou controle de acesso como atalho.
* **Erros e logs:** Erros devem ser claros, rastreáveis, com causa preservada e sem sucesso falso. Logs devem ser estruturados, conter correlação/`execution_id` quando disponível e nunca incluir segredo ou payload sensível.
* **Resiliência:** Use timeout, retry limitado com backoff e jitter, circuit breaker, idempotência, deduplicação, quarentena e limites de volume/memória quando aplicáveis. Retry não pode avançar estado, esconder falha permanente nem repetir escrita não idempotente.
* **Dependências e operação:** Mantenha versões explícitas, licenças aprovadas e vulnerabilidades avaliadas. Mudanças de dependência exigem análise de compatibilidade, release notes, vulnerabilidades e regressão. Meça desempenho antes de otimizar e mantenha métricas de volume, latência, erro e execução.

---

## 🟢 Permissão de Escrita e Preparação de Banco

* **Owner Estrutural:** Este repositório é o único dono do banco/schema `ETL_SISTEMA` (`esl_cloud`). Você tem permissão total para criar e alterar tabelas base, migrations, índices, views operacionais (`dbo.vw_*_powerbi`) e views dimensionais.
* **Preservação de Documentação Operacional:** É proibido apagar arquivos `README.md` e `AGENTS.md`. Quando necessário, apenas atualize seu conteúdo mantendo esses arquivos presentes no repositório.
* **Preparação para o Dashboard:** Garanta que todas as alterações de infraestrutura de dados necessárias para o Dashboard estejam aplicadas, testadas e documentadas aqui, deixando o caminho livre para o consumo limpa pelo monorepo de painéis.
* **Paridade de Schema (Baseline Parity):** Toda alteração estrutural via Migration (ex: arquivos em `database/migrations/`) DEVE obrigatoriamente ser refletida nos scripts base de criação correspondentes (ex: `database/tabelas/`, `database/views/`, `database/indices/` e `database/validacao/`). A recriação do banco de dados do zero deve produzir um schema idêntico ao banco atualizado via migrations.

---

## 🗄️ Topologia de Bancos de Dados e Fronteiras Arquiteturais

* **`ETL_SISTEMA` (`esl_cloud`):** Trate este banco como domínio exclusivo do pipeline de extração e como fonte de verdade analítica. Este repositório é o único local autorizado para aplicar DDL/DML estrutural neste banco. Crie colunas computadas, chaves, índices, tabelas base, views operacionais (`dbo.vw_*_powerbi`) e views analíticas exclusivamente via `database/migrations` e mantenha os scripts base sincronizados. O backend do Dashboard deve consumir este banco estritamente em modo **READ-ONLY** (`SELECT`).
* **`DASHBOARDS`:** Trate este banco como produção exclusiva da aplicação web. Ele armazena somente estado interno do portal: ACL (papéis, permissões, usuários e setores), sessões, configurações e auditoria administrativa. Não aplique DDL/DML deste repositório no banco `DASHBOARDS`. A única fonte de verdade estrutural dele é o **Flyway** do monorepo Dashboard em `database/migrations`. O Hibernate do portal deve operar com `ddl-auto=none`; DDL em runtime pelo Java é terminantemente proibido.
* **`DASHBOARDS_DEV`:** Trate este banco como sandbox de desenvolvimento local do portal, usado pelo profile `dev` e por `.env.development.local`. Ele existe para evitar acidentes e poluição de dados na produção. Preserve o contrato do validador `DevDatabaseIsolationValidator`, que executa *fast-fail* e aborta o startup do backend do Dashboard quando o ambiente de desenvolvimento tenta conectar a JDBC principal ao banco `DASHBOARDS` de produção.

---

## 🧠 Diretrizes de Performance e Dados (Data Quality)

* **Push-down Computation:** É expressamente proibido carregar massas de dados para a memória da JVM (ex: `.findAll().stream().collect(...)`) para realizar agrupamentos, contagens, somas, divisões, rankings ou totalizações. Toda matemática de conjuntos, `COUNT`, `SUM`, `AVG`, `MIN`, `MAX` e `GROUP BY` deve ser delegada ao SQL Server via repositórios JDBC/queries nativas. O Java atua apenas como roteador, orquestrador leve e materializador de resultados já agregados.
* **SARGability Crítica:** É proibido usar funções no lado esquerdo das cláusulas `WHERE` em colunas indexadas, principalmente datas (ex: `YEAR(coluna)`, `MONTH(coluna)`, `TRY_CONVERT(coluna)`, `COALESCE(coluna, ...)`). Filtros temporais devem ser construídos por intervalos diretos e sargable: `coluna >= :inicio AND coluna < :fimExclusivo`.
* **Clean Code e Pacotes:** Respeite a arquitetura em camadas. O pacote `service` é exclusivo para classes com comportamento de serviço; repositórios e gateways SQL ficam em `repository`/`database`, utilitários puros em `util`, configurações em `config`, políticas em `policy`, jobs em pacotes próprios e scripts SQL em `database`. Cada macaco no seu galho.
* **Materialização Obrigatória:** Regras de BI complexas, filtros de elegibilidade pesados ou cruzamentos textuais não devem ser processados sob demanda dentro das views de apresentação. Realize o processamento textual pesado e as validações durante a carga (Load) no Java e salve o resultado em colunas físicas (ex: `BIT`, `TINYINT`) indexadas nas tabelas base.
* **🚨 Exclusão Lógica (Soft Delete Obrigatório):** É ESTRITAMENTE PROIBIDO o uso de exclusão física (Hard Delete / `DELETE FROM` / `TRUNCATE`) em rotinas comuns para dados extraídos, cadastros de suporte, fatos, auditoria ou histórico de BI. Use flags como `excluido_na_origem = 1`, `ativo = 0`, `deleted_at` ou controle de vigência. Views operacionais, views analíticas e materializações devem filtrar registros excluídos logicamente por padrão, exceto em consultas declaradamente de auditoria/reconciliação.
* **JPA/Hibernate com critério:** ORM pode atender cadastros, configurações e transações pequenas em evoluções futuras, mas não substitui SQL set-based/JDBC para carga em massa, staging, `MERGE`, fatos, agregações ou validações analíticas.

## Modelo Aditivo com Expurgo Logico (Sweep and Prune)

O ETL opera em modelo aditivo por padrao: o loop recorrente de 30 minutos deve executar apenas insercoes e atualizacoes de registros novos ou alterados. Ele nao deve fazer `DELETE`, `TRUNCATE` ou varredura completa de ausencia na origem, para nao acoplar reconciliacao historica ao caminho critico de extracao quase em tempo real.

Quando uma entidade da API deixar de retornar uma chave que ja existe no `ETL_SISTEMA`, a sincronizacao de estado deve ser feita por expurgo logico noturno. O job dedicado de reconciliacao deve obter o snapshot de chaves da origem, comparar com as chaves ativas do banco e marcar os ausentes com `excluido_na_origem = 1`, preservando os dados para auditoria. Quando uma chave reaparecer na origem, o upsert deve reativar o registro com `excluido_na_origem = 0`.

Toda tabela de dominio sincronizada com APIs externas deve expor uma coluna padrao de controle, preferencialmente `excluido_na_origem BIT NOT NULL DEFAULT (0)`, acompanhada de metadados de auditoria quando aplicavel, como `data_exclusao_origem` e `ultima_reconciliacao_origem_em`. Alteracoes estruturais devem ser feitas por migrations e refletidas nos scripts base correspondentes.

As views operacionais, views analiticas, APIs e dashboards devem filtrar registros com `excluido_na_origem = 1` por padrao. Consultas que incluam excluidos logicos so sao permitidas para auditoria, diagnostico ou reconciliacao tecnica e devem declarar essa intencao explicitamente.

O job `Sweep and Prune` deve rodar fora do horario de pico, com paginacao por origem, staging ou comparacao por conjuntos de chaves, updates em lote, telemetria por entidade e protecao contra execucao concorrente. Se o snapshot de uma entidade falhar ou ficar incompleto, essa entidade nao deve ser marcada como expurgada naquela execucao.

* **Testes e Sanidade:** Antes de dar a tarefa por concluída, execute a suíte de testes locais (`src/test`) e os scripts de validação de schema (`database/validacao`). Nenhuma alteração estrutural pode subir sem validação de quebra de contrato.
* **Encoding e Mojibake:** Todo o ecossistema (código Java, drivers JDBC, scripts SQL e arquivos de log) opera estritamente em UTF-8. Não aceite aliases ou dados de tabelas com caracteres corrompidos.
* **Contratos de API:** Toda integração deve documentar endpoint/template, versão, autenticação, filtros obrigatórios, chave, paginação, ordenação, formato temporal, timeout, erros esperados e política de retry. Mudanças de payload exigem validação de contrato e estratégia de compatibilidade.
* **Documentação e CI/CD:** Mantenha README, ADRs, contratos, runbooks e catálogo de configurações vivos. O CI deve cobrir build, testes, schema, formatter/lint, análise estática, dependências e segredos; enquanto alguma etapa não estiver automatizada, registre a lacuna e execute localmente o que estiver disponível.
* **Débito técnico:** Registre impacto, risco, dono, alternativa e prioridade. Não esconda débito em TODO sem contexto nem espere um incidente para tratá-lo.

## Diretrizes de Sincronização de Estado (states.md)
1. Antes de iniciar a implementação de qualquer código, você DEVE ler o arquivo `states.md` para compreender o contexto arquitetural e as regras de negócio vigentes, garantindo que as novas implementações não quebrem o estado atual.
2. Leia a seção "Tarefas Pendentes" no `states.md` para entender o escopo exato do que precisa ser desenvolvido.
3. Após finalizar a escrita e modificação do código, você DEVE atualizar o arquivo `states.md`.
4. A atualização consiste em: remover a tarefa concluída da seção "Tarefas Pendentes" e atualizar as seções "Arquitetura e Padrões", "Fluxo de Dados" ou "Regras de Negócio Consolidadas" refletindo exatamente o novo estado do sistema.
5. NUNCA entregue ou finalize uma modificação de código sem antes reescrever e atualizar o `states.md` para refletir o presente.
