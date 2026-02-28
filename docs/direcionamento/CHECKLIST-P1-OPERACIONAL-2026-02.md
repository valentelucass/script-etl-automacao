# Checklist P1 Operacional - 2026-02

Data de consolidacao: 2026-02-28

## Escopo P1
- mirror/cache Maven
- consistencia de versao automatizada
- decomposicao final de `LoopDaemonComando`
- higiene documental residual

## Status consolidado

1. Mirror/cache Maven na CI
- [x] `actions/setup-java` com `cache: maven`
- [x] warmup de dependencias/plugins com `mvn dependency:go-offline`
- [x] suporte opcional a mirror por secrets:
  - `MAVEN_MIRROR_URL`
  - `MAVEN_MIRROR_USERNAME`
  - `MAVEN_MIRROR_PASSWORD`

2. Governanca de versao (guard automatizado)
- [x] guard `scripts/ci/check_version_consistency.py`
- [x] etapa adicionada no workflow CI
- [x] validacao cruzada:
  - `pom.xml`
  - `README_RESUMIDO.md`
  - `docs/README.md`
- [x] exigencia de secao `Novidades X.Y.Z` no `README_RESUMIDO.md`

3. Higiene documental residual
- [x] metadata de versao do `docs/README.md` alinhada para `2.3.4`
- [x] guia de Maven/troubleshooting reescrito para fluxo atual de CI e mirror
- [x] template de settings para mirror adicionado em `.ci/maven-settings-template.xml`

4. Decomposicao final do daemon
- [x] `LoopDaemonComando` fatiado por modo operacional (`start`, `stop`, `status`, `run`) via handlers dedicados
- [x] loop/run isolado em handler dedicado com testes focados em:
  - parada graciosa
  - force run
  - reconciliacao com falha parcial
- [x] `LoopDaemonComando` reduzido para roteamento de modo (classe curta)

## Criterio de saida P1
P1 concluido:
- workflow CI estiver verde com guard de versao ativo
- mirror opcional estiver funcional via secrets no runner
- `LoopDaemonComando` estiver decomposto com cobertura de regressao nos cenarios criticos
