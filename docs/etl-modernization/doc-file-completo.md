# DOC-FILE COMPLETO - ETL MODERNIZATION

## 1. Objetivo
Aplicar evolucao arquitetural **no projeto atual** (sem branch/pasta paralela), com foco em:
- baixo acoplamento
- testabilidade
- resiliencia
- observabilidade
- automacao total de testes

## 2. Arquitetura final (resumo)
```text
CLI (Main/CommandRegistry)
   -> ExecutarFluxoCompletoComando
      -> PipelineCompositionRoot
         -> PipelineOrchestrator
            -> Step GraphQL
            -> Step DataExport
            -> Step Faturas GraphQL (opcional)
            -> Step Data Quality
```

Camadas aplicadas:
- `runners/ports`: contratos (ports)
- `runners/pipeline`: orchestrator, steps, composition root (adapters)
- `servicos/policies`: retry/failure/circuit/idempotency/error taxonomy
- `auditoria/quality`: data quality checks e relatorio
- `servicos/observability`: metricas e logger estruturado
- `comandos/extracao/recovery`: replay/backfill use case

## 3. Interfaces principais adicionadas
- `DataExportGateway`
- `GraphQLGateway`
- `EntityRepositoryPort<T>`
- `ExtractionLoggerPort`
- `ClockPort`
- `ConfigPort`

## 4. Orquestrador e execucao por step
Classes principais:
- `PipelineStep`
- `StepExecutionResult`
- `PipelineReport`
- `PipelineOrchestrator`
- `ExtractorRegistry`
- `PipelineCompositionRoot`

Responsabilidades do orquestrador:
- executar steps em sequencia deterministica
- aplicar retry policy
- classificar erro em taxonomy
- aplicar failure policy por entidade
- aplicar circuit breaker
- consolidar `PipelineReport`

## 5. Policies implementadas
- `ExponentialBackoffRetryPolicy` (max tentativas, delay base, multiplicador, jitter)
- `MapFailurePolicy` (`ABORT_PIPELINE`, `CONTINUE_WITH_ALERT`, `RETRY`, `DEGRADE`)
- `CircuitBreaker` (CLOSED/OPEN/HALF_OPEN)
- `IdempotencyPolicy` (chave natural + janela + schema version)
- `ErrorClassifier` e `ErrorTaxonomy`

## 6. Data quality
Checks implementados:
- `UniquenessCheck`
- `CompletenessCheck`
- `FreshnessCheck`
- `ReferentialIntegrityCheck`
- `SchemaValidationCheck`

Servicos:
- `DataQualityService`
- `SqlServerDataQualityQueryAdapter`
- `DataQualityPipelineStep`

## 7. Recovery e backfill
Componente:
- `RecoveryUseCase`
- `RecoveryComando` (`--recovery`)

Uso:
```bash
--recovery YYYY-MM-DD YYYY-MM-DD [--api graphql|dataexport] [--entidade nome] [--sem-faturas-graphql]
```

## 8. Observabilidade
- logger estruturado JSON: `JsonStructuredExtractionLogger`
- metricas em memoria: `InMemoryPipelineMetrics`
- export Prometheus: `PrometheusMetricsExporter`
- bridge OTel: `OpenTelemetryAttributeBridge`

## 9. Estrutura de testes automatizados
Tipos adicionados:
- unit tests (policies, orchestrator, quality, registry)
- integration smoke (`*IT`)
- contract test (`GraphQLResponseContractTest`)
- snapshot test (`ManifestoSnapshotTest`)
- pipeline e2e sintético (`PipelineE2ETest`)
- chaos test (`PipelineChaosTest`)

## 10. Ambiente de teste IA-first
Pasta:
- `test-environment/`

Conteudo:
- `docker-compose.yml` (SQL Server + WireMock)
- mocks WireMock
- datasets sinteticos:
  - duplicatas
  - dados tardios
  - paginacao incompleta
  - schema drift
  - erros de API

## 11. Scripts de execucao automatica
Raiz:
- `run-unit-tests.sh`
- `run-integration-tests.sh`
- `run-pipeline-tests.sh`
- `run-chaos-tests.sh`

Controle para IA:
- `ai-test-control/execute-full-test-suite.sh`
- `ai-test-control/execute-recovery-tests.sh`
- `ai-test-control/execute-chaos-tests.sh`

## 12. CI
Workflow novo:
- `.github/workflows/etl-tests.yml`

Etapas:
1. lint (PMD)
2. build
3. unit tests
4. integration tests
5. contract tests
6. pipeline tests

## 13. Compatibilidade mantida
- fluxo principal continua no comando atual `--fluxo-completo`
- validacoes de completude/integridade existentes foram preservadas
- melhorias foram adicionadas sem substituir o projeto base

## 14. Estado de validacao
Validado com:
```bash
mvn test
```
Resultado: suite passou.
