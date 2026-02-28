# Direcionamento

## Objetivo
Centralizar o que guia a execucao tecnica:

- verdades comprovadas (com evidencia)
- plano de acao priorizado
- to-do operacional com criterio de aceite
- comando operacional padrao (diagnostico/parada/validacao)

## Arquivos deste diretorio

- `verdades/VERDADES-OPERACIONAIS.md`: registro vivo de fatos confirmados
- `planos/PLANO-ACAO-DAEMON-RECONCILIACAO-2026-02.md`: plano por prioridade
- `planos/PLANO-ACAO-QUALIDADE-TESTES-2026-02-28.md`: plano de robustez da esteira de testes
- `TODO-EXECUCAO-DAEMON.md`: checklist executavel
- `TODO-QUALIDADE-TESTES-2026-02-28.md`: checklist executavel de qualidade e regressao

## Regra de manutencao

Sempre que uma nova verdade for comprovada por log, codigo ou requisicao real:

1. adicionar entrada em `verdades/VERDADES-OPERACIONAIS.md`
2. atualizar impacto e decisao no plano
3. ajustar checklist no `TODO-EXECUCAO-DAEMON.md`

## Guia rapido operacional

Sempre reportar tempos em minutos.

1. Validar estado do daemon:
   - `java -jar target/extrator.jar --loop-daemon-status`
2. Parar daemon com seguranca:
   - `java -jar target/extrator.jar --loop-daemon-stop`
3. Confirmar parada:
   - repetir `--loop-daemon-status` ate `Estado: STOPPED`.
4. Para falha de reconciliacao por faturas:
   - conferir `status_code` e `reason_code` em `log_extracoes`;
   - validar chamada GraphQL sempre em `/graphql` com header `Authorization: Bearer <token>`;
   - se `faturas_graphql` tiver backfill por ID, validar regra registrada em `VERDADES-OPERACIONAIS.md` antes de concluir que houve falha real de volume.

## Formato minimo de uma verdade

- data/hora
- fato objetivo
- evidencia (arquivo/linha, consulta, ou resposta API)
- impacto no comportamento
- decisao tecnica
