# TODO Qualidade de Testes (2026-02-28)

## Regra de uso

- Marcar `[x]` somente apos executar e validar a evidencia.
- Sempre anexar comando executado e resultado final (pass/fail).
- Em caso de falha, abrir item corretivo no mesmo arquivo antes de fechar a rodada.

## Baseline obrigatoria (antes de alterar codigo)

- [x] Rodar testes unitarios atuais.
  - Comando: `mvn test`
  - Evidencia: `Tests run: 33, Failures: 0, Errors: 0`
- [x] Rodar validacao 24h detalhada.
  - Comando: `java --enable-native-access=ALL-UNNAMED -jar target/extrator.jar --validar-api-banco-24h-detalhado --sem-faturas-graphql`
  - Evidencia: `RESUMO_API_VS_BANCO_24H_DETALHADO | ok=7 | falhas=0`
- [x] Rodar validacao 24h resumida.
  - Comando: `java --enable-native-access=ALL-UNNAMED -jar target/extrator.jar --validar-api-banco-24h --sem-faturas-graphql`
  - Evidencia: `RESUMO_API_VS_BANCO_24H | ok=7 | falhas=0`

## Robustez de ambiente de testes

- [x] Adicionar override por `system property` para banco de seguranca SQLite.
  - Aceite: testes conseguem isolar banco em pasta temporaria.
- [x] Criar teste automatizado do resolver de caminho de seguranca.
  - Aceite: validacao de prioridade para `extrator.security.db.path`.
- [x] Criar teste automatizado de bootstrap + autenticacao com SQLite real.
  - Aceite: cria banco, usuario ADMIN e audita autenticacao.
- [x] Criar smoke test do JAR empacotado para detectar regressao de classpath/driver.
  - Aceite: `--ajuda` e `--auth-info` funcionando via `target/extrator.jar`.
- [x] Integrar smoke test do JAR no CI.
  - Aceite: workflow roda `package` e smoke script.

## Regressao pos-ajuste

- [x] Rodar suite completa novamente.
  - Comando: `mvn test`
  - Evidencia: `Tests run: 35, Failures: 0, Errors: 0`.
  - Aceite: 100% verde.
- [x] Rodar build de pacote.
  - Comando: `mvn -DskipTests package`
  - Evidencia: `BUILD SUCCESS`.
  - Aceite: `BUILD SUCCESS`.
- [x] Rodar smoke test do JAR local.
  - Comando: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ci/smoke_packaged_jar.ps1`
  - Observacao: host local sem `/bin/bash`; script Bash permanece para CI Linux.
  - Evidencia: `[smoke] OK: JAR empacotado validado com sucesso.`
  - Aceite: script finaliza com `OK`.
- [x] Revalidar 24h (resumo e detalhado) para confirmar nao regressao funcional.
  - Comandos:
    - `java --enable-native-access=ALL-UNNAMED -jar target/extrator.jar --validar-api-banco-24h-detalhado --sem-faturas-graphql`
    - `java --enable-native-access=ALL-UNNAMED -jar target/extrator.jar --validar-api-banco-24h --sem-faturas-graphql`
  - Evidencias:
    - `RESUMO_API_VS_BANCO_24H_DETALHADO | ok=7 | falhas=0` (log: `logs/extracao_dados_2026-02-28_09-58-07.log`)
    - `RESUMO_API_VS_BANCO_24H | ok=7 | falhas=0` (log: `logs/extracao_dados_2026-02-28_10-01-52.log`)
  - Aceite: ambos com `falhas=0`.

## Pendencias abertas

- [ ] Investigar definitivamente a causa ambiente-especifica do erro intermitente `No suitable driver found`.
  - Hipotese atual: mismatch entre artefato executado no host e JAR mais recente.
  - Proxima acao: coletar hash do JAR em producao antes de cada execucao do menu.
- [x] Adicionar script de "rodada consistente" (extracao + validacao 24h em sequencia) para evitar falso erro por drift temporal da API.
  - Evidencia: `scripts/ci/rodada_24h_consistente.ps1`.
