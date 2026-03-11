# Recovery Procedures

## Replay por intervalo
Comando:
```bash
--recovery YYYY-MM-DD YYYY-MM-DD [--api graphql|dataexport] [--entidade nome]
```

Exemplo:
```bash
--recovery 2026-01-01 2026-01-31 --api graphql --entidade coletas
```

## Backfill histórico
Use o mesmo comando `--recovery` sem filtro de API/entidade para cobrir todas as entidades.

## Comportamento idempotente
- `RecoveryUseCase` gera `idempotency_key`.
- Persistência atual já opera com upsert/deduplicação por entidade.
- Reexecuções do mesmo intervalo não devem gerar duplicação lógica.

## Estratégia recomendada
1. Rodar `--recovery` para janela mínima afetada.
2. Rodar `--validar-api-banco-24h` ou validações de integridade.
3. Em caso de drift, ampliar janela de replay incrementalmente.
