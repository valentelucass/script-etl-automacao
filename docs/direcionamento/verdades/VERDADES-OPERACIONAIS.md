# Verdades Operacionais

## Convencao

- ID: `V-YYYYMMDD-XX`
- Status: `ATIVA` ou `SUPERADA`

---

### V-20260221-01 | ATIVA
- Fato: endpoint GraphQL operacional e `/graphql`.
- Evidencia: requisicao real retornando `{"data":{"__typename":"Query"}}`.
- Impacto: configuracao com `/api/graphql` gera falha (404/400) e mascara diagnostico.
- Decisao: padrao operacional deve usar `/graphql`.

### V-20260221-02 | ATIVA
- Fato: `PickInput` nao possui `serviceDate` nem `sequenceCode`; possui `requestDate`.
- Evidencia: introspection GraphQL de `PickInput`.
- Impacto: nao e possivel buscar coletas diretamente por `serviceDate` ou `pick_sequence_code`.
- Decisao: reconciliacao de manifestos nao pode depender desses filtros na API atual.

### V-20260221-03 | ATIVA
- Fato: `CreditCustomerBillingInput` nao aceita `issueDate`.
- Evidencia: resposta GraphQL com erro `argumentNotAccepted`.
- Impacto: fallback por `issueDate` para faturas nao e viavel.
- Decisao: remover/evitar estrategia baseada em `issueDate`.

### V-20260221-04 | ATIVA
- Fato: filtro por `dueDate` nao cobre os IDs de `freight.accountingCreditId` do mesmo dia de servico.
- Evidencia: para `2026-02-19`, `freight_accountingCreditId_unique=375` e cobertura por `dueDate=0`.
- Impacto: integridade referencial frete x fatura falha de forma persistente.
- Decisao: reconciliacao deve incluir backfill por chave (`accounting_credit_id` -> `creditCustomerBilling.id`).

### V-20260221-05 | ATIVA
- Fato: consulta direta de fatura por `id` retorna dados para IDs ausentes na busca por data.
- Evidencia: IDs `4025482`, `4025586`, `4025597`, `4025711`, `4025800` encontrados via `params: { id }`.
- Impacto: problema e de criterio de filtro temporal, nao de inexistencia da fatura.
- Decisao: usar busca por ID durante reconciliacao antes da validacao final.

### V-20260221-06 | ATIVA
- Fato: tratamento atual pode marcar erro GraphQL como execucao completa com `0` registros.
- Evidencia: log historico com `QueryCanceled` seguido de status `COMPLETO` em ciclo.
- Impacto: falso positivo de saude e risco de dados faltantes.
- Decisao: erro GraphQL deve resultar em `ERRO_API`/incompleto.

### V-20260221-07 | ATIVA
- Fato: teste `LoopDaemonComandoReconciliacaoHistoryTest` remove CSV real em `logs/daemon/reconciliacao`.
- Evidencia: codigo do teste remove arquivo fixo em caminho de runtime.
- Impacto: observabilidade e historico ficam contaminados.
- Decisao: isolar teste para caminho temporario sem tocar logs operacionais.

### V-20260221-08 | ATIVA
- Fato: reconciliacao automatica agora executa extracao por intervalo em modo loop (`--modo-loop-daemon`).
- Evidencia: `LoopReconciliationService.executarReconciliacaoPadrao` adiciona a flag em `args` antes de chamar `ExecutarExtracaoPorIntervaloComando`.
- Impacto: falhas conhecidas de manifestos x coletas por limitacao de schema (sem `serviceDate`) deixam de derrubar a reconciliacao automatica como erro bloqueante.
- Decisao: manter reconciliacao automatica em modo loop para classificar essa classe de ocorrencia como alerta operacional, nao como falha terminal.

### V-20260221-09 | ATIVA
- Fato: validacao de integridade no comando por intervalo agora respeita modo loop.
- Evidencia: `ExecutarExtracaoPorIntervaloComando` passa `modoLoopDaemon` para `IntegridadeEtlValidator.validarExecucao(..., modoLoopDaemon)`.
- Impacto: reconciliacao deixa de ficar presa indefinidamente em `pending_dates` por limitacao de origem ja confirmada.
- Decisao: usar `--modo-loop-daemon` em reconciliacao automatica e manter validacao estrita fora do loop.

### V-20260221-10 | ATIVA
- Fato: endpoint invalido (`/api/graphql`) nao e mais tratado como sucesso silencioso.
- Evidencia: execucao real em `2026-02-21 12:31` com `API_GRAPHQL_ENDPOINT=/api/graphql` gerou `ETL_DIAG status_code=ERRO_API`, `STATUS_NAO_COMPLETO`, `Status final: PARTIAL` e `EXIT_CODE=2` em `target/validation_negative.log`.
- Impacto: erro de origem agora derruba a completude do ciclo e evita falso positivo com `0` registros.
- Decisao: manter regra de erro hard (`ERRO_API`) quando GraphQL falhar.

### V-20260221-11 | ATIVA
- Fato: endpoint correto (`/graphql`) conclui o mesmo fluxo como completo.
- Evidencia: execucao real em `2026-02-21 12:31-12:32` sem override retornou `ETL_DIAG status_code=COMPLETO`, `INTEGRIDADE_ETL ... CONTAGEM_OK`, `Status final: SUCCESS` e `EXIT_CODE=0` em `target/validation_positive.log`.
- Impacto: comportamento esperado ficou reproduzivel ponta a ponta.
- Decisao: usar `/graphql` como padrao operacional unico.

### V-20260221-12 | ATIVA
- Fato: arquivo local de segredo ainda aponta endpoint antigo.
- Evidencia: `src/main/resources/config.properties` usa `api.graphql.endpoint=/graphql`, enquanto `docs/08-arquivos-secretos/esl_api_local.json` esta com `\"graphqlEndpoint\": \"/api/graphql\"`.
- Impacto: validacoes manuais podem falhar por configuracao divergente do runtime.
- Decisao: alinhar o endpoint em artefatos auxiliares para reduzir erro operacional.

### V-20260221-13 | ATIVA
- Fato: cenario positivo ficou estavel em repeticao curta.
- Evidencia: 3 execucoes consecutivas em `target/validation_positive_repeat_1.log`, `target/validation_positive_repeat_2.log` e `target/validation_positive_repeat_3.log` com `EXIT_CODE=0`, `status_code=COMPLETO`, `CONTAGEM_OK` para `coletas` (258/258) e `usuarios_sistema` (20/20).
- Impacto: reduz risco de resultado pontual/nao reproduzivel na validacao manual.
- Decisao: considerar o fluxo validado para cenarios equivalentes de intervalo unico.

### V-20260221-14 | ATIVA
- Fato: `faturas_graphql` podia cair em `INCOMPLETO_DB` mesmo com dado correto apos backfill.
- Evidencia: `api_count=154` e `db_upserts=520` no mesmo ciclo; o volume salvo era maior por complemento de IDs orfaos, nao por perda de dados.
- Impacto: reconciliacao automatica podia falhar por falso negativo de consistencia.
- Decisao: ajustar comparacao de consistencia pos-salvamento para considerar volume final salvo quando houver expansao valida por backfill.

### V-20260221-15 | ATIVA
- Fato: busca de fatura por ID pode falhar transitoriamente e recuperar na tentativa seguinte.
- Evidencia: ID `4025751` apareceu como nao encontrado em tentativa anterior, mas retornou normalmente em nova requisicao direta.
- Impacto: uma unica tentativa por ID gera risco de manter orfao falso e travar `pending_dates`.
- Decisao: usar retentativa curta com backoff no backfill por `creditCustomerBilling(id)` antes de concluir ausencia real.

### V-20260221-16 | ATIVA
- Fato: parada por comando foi validada em execucao real.
- Evidencia: `--loop-daemon-stop` retornou `Loop daemon parado.` e status seguinte registrou `Estado: STOPPED` com `Processo vivo: NAO`.
- Impacto: comando de encerramento esta funcional para operacao de producao.
- Decisao: manter `--loop-daemon-stop` como procedimento oficial de encerramento.

### V-20260221-17 | ATIVA
- Fato: autenticacao GraphQL exige header com prefixo `Bearer`.
- Evidencia: `query { __typename }` em `/graphql` retornou `401` com token puro e sucesso com `Authorization: Bearer <token>`.
- Impacto: requisicoes de validacao podem falhar mesmo com token valido se o formato do header estiver incorreto.
- Decisao: padrao operacional de requisicao GraphQL deve usar `Bearer`.

### V-20260221-18 | ATIVA
- Fato: consulta de fatura por ID `4025751` retornou em runtime quando executada no formato correto.
- Evidencia: query paginada de `creditCustomerBilling(params:{id:'4025751'})` em `/graphql` retornou `graphql_records=1`.
- Impacto: confirma que o orfao pode ser efeito de falha transitoria/forma de consulta, nao necessariamente inexistencia da fatura.
- Decisao: manter backfill por ID com retentativa e validar endpoint/header antes de concluir ausencia real.

### V-20260228-01 | ATIVA
- Fato: o modulo de seguranca agora aceita override de caminho do SQLite via `-Dextrator.security.db.path`.
- Evidencia: `CaminhoBancoSegurancaResolver` passou a priorizar `system property` antes de variaveis de ambiente.
- Impacto: testes e smoke podem isolar banco de seguranca em pasta temporaria sem afetar ambiente operacional.
- Decisao: padrao de testes automatizados deve usar o override por `system property`.

### V-20260228-02 | ATIVA
- Fato: existe smoke test automatizado do JAR empacotado cobrindo inicializacao de seguranca/SQLite.
- Evidencia: script `scripts/ci/smoke_packaged_jar.sh`, integrado no workflow `.github/workflows/ci.yml`.
- Impacto: regressao de classpath/driver no artefato final tende a ser detectada antes do uso em producao.
- Decisao: manter o smoke do JAR como gate obrigatorio de CI.

### V-20260228-03 | ATIVA
- Fato: a validacao API x banco 24h pode falhar por drift temporal se a extracao de referencia estiver antiga.
- Evidencia: comparacao detalhada com janela antiga falhou (`ok=3 | falhas=4`) e, apos extracao fresca, passou (`ok=7 | falhas=0`).
- Impacto: rodada de validacao sem extracao previa pode gerar falso negativo operacional.
- Decisao: executar extracao fresca antes da validacao 24h, com script de rodada consistente (`scripts/ci/rodada_24h_consistente.ps1`).
