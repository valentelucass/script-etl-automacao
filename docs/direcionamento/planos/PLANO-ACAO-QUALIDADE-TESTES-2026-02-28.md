# Plano de Acao - Qualidade de Testes (2026-02-28)

## Objetivo

Elevar previsibilidade dos testes, reduzir regressao de producao e garantir deteccao precoce de problemas de empacotamento do JAR.

## Escopo

- Cobertura automatizada para modulo de seguranca (SQLite/auth).
- Smoke test do artefato empacotado (`target/extrator.jar`).
- Checklist operacional de execucao repetivel para validacoes 24h.

## Fases

## Fase 1 - Baseline e diagnostico

1. Executar suite atual e registrar linha de base.
2. Reproduzir validacoes 24h (resumo e detalhado).
3. Isolar risco principal: autenticacao falhando por inicializacao SQLite em producao.

Status: concluida.

## Fase 2 - Endurecimento da esteira

1. Tornar caminho do banco de seguranca configuravel via `system property` para testes deterministas.
2. Adicionar testes de unidade/integracao leve para o fluxo de seguranca.
3. Adicionar smoke test do JAR empacotado para pegar erro de classpath/driver antes de liberar.
4. Integrar smoke test ao workflow CI.

Status: concluida.

## Fase 3 - Regressao ampliada

1. Reexecutar `mvn test`.
2. Reexecutar `mvn -DskipTests package`.
3. Rodar smoke local `scripts/ci/smoke_packaged_jar.sh`.
4. Rodar validacoes de negocio 24h (resumo + detalhado).
5. Consolidar inconsistencias encontradas e tratar imediatamente.

Status: concluida.

## Riscos e mitigacoes

1. Risco: divergencia entre JAR local e JAR realmente executado em producao.
   - Mitigacao: incluir verificacao de artefato no processo de liberacao (hash + timestamp).
2. Risco: dependencia de variavel de ambiente da maquina para testes.
   - Mitigacao: usar `-Dextrator.security.db.path` nos testes e no smoke de CI.
3. Risco: regressao silenciosa em comandos interativos.
   - Mitigacao: manter smoke de comandos nao interativos (`--ajuda`, `--auth-info`) e suite de unidade do dominio.
4. Risco: falso negativo de validacao 24h quando API muda entre extracao e comparacao.
   - Mitigacao: executar extracao fresca imediatamente antes da validacao detalhada/comparativa.

## Criterio de pronto

- Testes automatizados verdes.
- Smoke do JAR empacotado verde no CI.
- Validacao 24h resumida e detalhada sem falhas.
- TODO de qualidade atualizado com evidencias de execucao.
