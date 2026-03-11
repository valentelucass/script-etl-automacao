# Runbook Operacional ETL

## Execução diária
1. Rodar fluxo:
```bash
--fluxo-completo
```
2. Verificar resumo executivo no log.
3. Confirmar ausência de alertas de integridade e quality.

## Execução automática de testes (IA)
```bash
./ai-test-control/execute-full-test-suite.sh
```

## Incidentes comuns
- `TIMEOUT`/latência API:
  - validar WireMock/endpoint real.
  - revisar retry e circuit breaker.
- `DATA_QUALITY_BREACH`:
  - revisar freshness e schema.
  - executar replay do intervalo impactado.
- `DB_CONFLICT`:
  - validar chaves naturais e deduplicação.

## Recuperação rápida
```bash
--recovery 2026-01-01 2026-01-02 --api dataexport --entidade manifestos
```

## Ambiente de testes
- Subir:
```bash
docker compose -f test-environment/docker-compose.yml up -d
```
- Derrubar:
```bash
docker compose -f test-environment/docker-compose.yml down -v
```
