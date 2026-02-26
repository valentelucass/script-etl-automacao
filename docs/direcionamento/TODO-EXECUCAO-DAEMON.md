# TODO Execucao - Daemon/Reconciliacao

## Regra de uso

- Marcar cada item com `[x]` somente apos validar o criterio de aceite.
- Medir e registrar tempo sempre em minutos.
- Toda nova verdade comprovada deve entrar em `docs/direcionamento/verdades/VERDADES-OPERACIONAIS.md`.
- Ao fim de cada fase, atualizar status no plano: `docs/direcionamento/planos/PLANO-ACAO-DAEMON-RECONCILIACAO-2026-02.md`.

## Baseline (antes de codar)

- [x] Capturar baseline de comportamento atual (erros GraphQL, reconciliacao 2026-02-19, pending_dates).
  - Aceite: baseline documentado com evidencias (arquivo/linha de log ou resposta de requisicao).
- [x] Rodar baseline de testes locais.
  - Comando: `mvn -DfailIfNoTests=false test`.
  - Aceite: resultado salvo para comparacao pos-correcao.

## P0 - Confiabilidade (estimado: 525 min)

- [x] P0.1 Corrigir semantica de erro GraphQL para nunca marcar sucesso quando houver `errors`.
  - Aceite: ciclo com erro GraphQL termina como `ERRO_API` ou `INCOMPLETO` (nunca `COMPLETO`).
- [x] P0.2 Implementar backfill por ID em reconciliacao frete x fatura.
  - Aceite: IDs orfaos em `fretes.accounting_credit_id` sao buscados por `creditCustomerBilling(id)` antes da validacao final.
- [x] P0.3 Isolar `LoopDaemonComandoReconciliacaoHistoryTest` para caminho temporario.
  - Aceite: nenhum teste escreve/remove arquivo em `logs/daemon/reconciliacao`.

## Gate de testes pesados P0

- [x] Executar suite de testes com foco em reconciliacao e historico.
  - Comando: `mvn -Dtest=LoopDaemonComandoReconciliacaoHistoryTest,LoopReconciliationServiceTest,LoopReconciliationServiceStressTest test`.
  - Aceite: 100% verde.
- [x] Executar teste completo do projeto.
  - Comando: `mvn -DfailIfNoTests=false clean test`.
  - Aceite: build e testes verdes sem regressao.

## P1 - Reducao de reincidencia (estimado: 360 min)

- [x] P1.1 Ajustar reconciliacao manifesto x coleta para limitacao de schema sem `serviceDate`.
  - Aceite: pendencia deixa de ficar presa indefinidamente por limitacao de origem.
- [x] P1.2 Melhorar reason-codes e observabilidade de erros de origem.
  - Aceite: logs distinguem claramente erro de API vs ausencia valida de dado.
- [x] P1.3 Corrigir falso `INCOMPLETO_DB` de `faturas_graphql` apos backfill por ID.
  - Aceite: quando backfill aumenta `db_upserts`, consistencia final considera o volume salvo valido e nao marca falha falsa.
- [x] P1.4 Adicionar retentativa curta no backfill por `creditCustomerBilling(id)`.
  - Aceite: falha transitoria de consulta por ID nao gera orfao falso sem nova tentativa.

## Gate de testes pesados P1

- [x] Reexecutar testes focados em reconciliacao.
  - Comando: `mvn -Dtest=LoopReconciliationServiceTest,LoopReconciliationServiceStressTest test`.
  - Aceite: sem falhas e sem aumento de tempo fora do esperado.
- [x] Validar ciclo real controlado (1 ciclo completo).
  - Aceite: `pending_dates` nao aumenta indevidamente e integridade final nao piora.

## P2 - Governanca e fechamento (estimado: 180 min)

- [ ] P2.1 Consolidar runbook de investigacao com requisicoes reprodutiveis.
  - Aceite: passo a passo executavel documentado.
- [x] P2.2 Atualizar documentacao final tecnica e operacional.
  - Aceite: plano, TODO e verdades atualizados com estado final.

## Encerramento obrigatorio

- [ ] Gerar relatorio final da execucao com:
  - tempo total em minutos
  - problemas resolvidos
  - pendencias remanescentes
  - riscos operacionais
- [x] Registrar verdades finais comprovadas em `docs/direcionamento/verdades/VERDADES-OPERACIONAIS.md`.
- [ ] Atualizar indice de documentacao se surgirem novos artefatos.
