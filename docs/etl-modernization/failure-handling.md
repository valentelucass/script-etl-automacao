# Failure Handling

## Error Taxonomy
- `TRANSIENT_API_ERROR`
- `PERMANENT_VALIDATION_ERROR`
- `DB_CONFLICT`
- `DATA_QUALITY_BREACH`
- `TIMEOUT`
- `SCHEMA_DRIFT`

## Retry Policy
- `ExponentialBackoffRetryPolicy`
- parâmetros configuráveis:
  - `etl.retry.max_tentativas`
  - `etl.retry.delay_base_ms`
  - `etl.retry.multiplicador`
  - `etl.retry.jitter`

## Failure Policy
Modos suportados:
- `ABORT_PIPELINE`
- `CONTINUE_WITH_ALERT`
- `RETRY`
- `DEGRADE`

Configuração por entidade:
- `etl.failure.graphql`
- `etl.failure.dataexport`
- `etl.failure.faturas_graphql`
- `etl.failure.quality`
- `etl.failure.default`

## Circuit Breaker
- aplica por entidade
- abre após `etl.circuit.failure_threshold`
- reabre em half-open após `etl.circuit.open_seconds`

## Idempotência
- `IdempotencyPolicy` com:
  - chave natural
  - janela temporal
  - versão de schema
