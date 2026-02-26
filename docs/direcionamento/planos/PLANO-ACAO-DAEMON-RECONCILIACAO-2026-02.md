# Plano de Acao - Daemon/Reconciliacao (2026-02)

## Objetivo
Eliminar falso positivo de sucesso, destravar `pending_dates`, e restaurar confiabilidade de integridade referencial.

## Escopo

- GraphQL: semantica de erro e completude
- Reconciliacao frete x fatura por chave
- Estrategia para manifestos x coletas com limitacao de schema
- Observabilidade e seguranca de testes

## Prioridades

## P0 (bloqueadores de confiabilidade)

1. Corrigir semantica de erro GraphQL
- Resultado esperado: `errors` em GraphQL sempre geram `ERRO_API`/incompleto.
- Locais alvo: `ClienteApiGraphQL`, `GraphQLIntervaloHelper`, propagacao em `ExtractionLogger`.
- Estimativa: `180 min`.

2. Implementar backfill por ID na reconciliacao frete x fatura
- Resultado esperado: para IDs orfaos em `fretes.accounting_credit_id`, consultar `creditCustomerBilling(id)` e persistir antes da validacao referencial.
- Locais alvo: comando de reconciliacao por intervalo + servico/repositorio de apoio.
- Estimativa: `300 min`.

3. Corrigir teste que apaga CSV real
- Resultado esperado: `LoopDaemonComandoReconciliacaoHistoryTest` usa so path temporario.
- Estimativa: `45 min`.

Tempo P0 estimado: `525 min`.

## P1 (reduzir reincidencia e ruido operacional)

1. Ajustar regra de reconciliacao manifestos x coletas sem `serviceDate`
- Resultado esperado: nao travar pendencia indefinidamente por limitacao de API.
- Abordagem: classificar caso como `limitacao_origem_confirmada` com politica de retentativa controlada e alerta explicito.
- Estimativa: `240 min`.

2. Melhorar rastreabilidade de erro/reason-code
- Resultado esperado: diferenciar claramente erro de origem vs ausencia valida de dados.
- Estimativa: `120 min`.

Tempo P1 estimado: `360 min`.

## P2 (padronizacao e governanca tecnica)

1. Consolidar runbook de investigacao com Postman CLI
- Resultado esperado: procedimento padrao reprodutivel para validacao profunda.
- Estimativa: `90 min`.

2. Atualizar documentacao tecnica final
- Resultado esperado: docs alinhadas ao comportamento novo + decisoes registradas.
- Estimativa: `90 min`.

Tempo P2 estimado: `180 min`.

## Ordem de Execucao

1. P0.1
2. P0.2
3. P0.3
4. Testes pesados P0
5. P1
6. Testes pesados P1
7. P2
8. Atualizacao final de documentacao

## Status de Execucao (2026-02-21)

- P0.1: concluido.
- P0.2: concluido.
- P0.3: concluido.
- Gate P0: concluido (`mvn -Dtest=GraphQLIntervaloHelperTest,LoopDaemonComandoReconciliacaoHistoryTest,LoopReconciliationServiceTest,LoopReconciliationServiceStressTest test` e `mvn -DfailIfNoTests=false clean test`).
- P1.1: concluido (reconciliacao por intervalo executa em `--modo-loop-daemon`, evitando bloqueio por limitacao conhecida de manifestos/coletas sem `serviceDate`).
- P1.2: concluido (consistencia pos-backfill de `faturas_graphql` normalizada para evitar falso `INCOMPLETO_DB`; retentativa por ID aplicada no backfill de faturas).
- Gate P1: concluido (testes focados e validacoes reais sem regressao de consistencia).
- P2.1: pendente (runbook operacional reproduzivel com foco em investigacao API).
- P2.2: concluido (docs de direcionamento atualizados com verdades operacionais e guia rapido de parada/diagnostico).

## Criterio de Pronto (DoD)

- Sem falso `COMPLETO` quando GraphQL retorna `errors`.
- Reconciliacao de `2026-02-19` deixa de falhar por falta de faturas ja existentes por ID.
- Nenhum teste manipula `logs/daemon/reconciliacao` real.
- Testes pesados verdes.
- Documentacao final atualizada.
- Nova verdade descoberta registrada em `verdades/VERDADES-OPERACIONAIS.md`.

## Janela total estimada

- P0 + P1 + P2 = `1065 min` (aprox. `17.75 h` de trabalho tecnico efetivo).
