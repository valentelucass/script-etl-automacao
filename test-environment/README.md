# Test Environment

Ambiente automatizado para testes de integração, contrato e caos.

## Serviços
- `sqlserver`: banco efêmero para testes de repositório.
- `wiremock`: mocks das APIs externas (GraphQL/DataExport).

## Subir ambiente
```bash
docker compose -f test-environment/docker-compose.yml up -d
```

## Derrubar ambiente
```bash
docker compose -f test-environment/docker-compose.yml down -v
```

## Datasets sintéticos
Arquivos em `test-environment/datasets/` cobrem:
- duplicatas
- dados tardios
- paginação incompleta
- schema drift
- erros de API
