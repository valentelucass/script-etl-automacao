# Resumo Tecnico Codex (Status Real)

Data de atualizacao: 2026-02-27

## Objetivo
Consolidar o estado real da auditoria em um unico arquivo, sem duplicacoes, com checklist `feito/parcial/pendente`.

## Leitura canonica
- Auditoria detalhada: `docs/analises/AUDITORIA_TECNICA_2026-02-27.md`
- Rotacao de segredo: `docs/SECURITY_ROTATION_REQUIRED.md`

## Status geral
- Fase 1 (seguranca e build): **majoritariamente feita**.
- Fase 2 (refatoracao estrutural): **parcial**.
- Fase 3 (qualidade/CI): **parcial para cobertura total; feito para CI basico e PMD**.

## Checklist consolidado

| Fase | Item | Status | Evidencia |
| --- | --- | --- | --- |
| Fase 1 | Remover credencial exposta do fluxo versionado | FEITO | `.gitignore` contem `database/config.bat`; `config_exemplo.bat` mantido. |
| Fase 1 | Aviso de rotacao de senha exposta | FEITO | `docs/SECURITY_ROTATION_REQUIRED.md` criado. |
| Fase 1 | Corrigir build Maven sem parent Spring desnecessario | FEITO | `pom.xml` sem `spring-boot-starter-parent`. |
| Fase 1 | Alinhar versionamento release (`README` x `pom`) | FEITO | `pom.xml` em `2.3.4`; sem `1.0-SNAPSHOT`. |
| Fase 1 | Corrigir encoding alvo (`CarregadorConfig`/`config.properties`) | FEITO | `CarregadorConfig` limpo (remocao de bloco legado com mojibake) e `config.properties` estabilizado. |
| Fase 1 | Trocar `catch(Throwable)` por tratamento tipado | FEITO | Sem ocorrencias de `catch(Throwable)` em `src/main/java`. |
| Fase 2 | Desacoplar `LoopDaemonComando` em servicos | FEITO | `DaemonLifecycleService`, `DaemonStateStore`, `DaemonHistoryWriter` extraidos. |
| Fase 2 | `Main` como entrypoint minimo via `CommandRegistry` | PARCIAL | Usa `CommandRegistry`, mas ainda concentra historico/shutdown/logging. |
| Fase 2 | Eliminar duplicidade SQL da `vw_dim_usuarios` | FEITO | Script canonico `database/views-dimensao/024_criar_view_dim_usuarios.sql` no bootstrap. |
| Fase 2 | Remover duplicidades de documentacao | PARCIAL | Houve consolidacao, mas ainda existem arvores documentais paralelas em `docs/`. |
| Fase 2 | Refatorar dashboard Python monolitico | PARCIAL | `src/dashboards/aplicacao.py` nao existe no estado atual; sem trilha de refatoracao registrada no repo atual. |
| Fase 2 | Remover codigo morto/dependencias ociosas | FEITO | `OcorrenciaDTO/OcorrenciaMapper` removidos; `poi/poi-ooxml` ausentes no `pom.xml`. |
| Fase 3 | Testes para camadas desacopladas | FEITO | Testes para servicos de daemon em `src/test/java/.../daemon`. |
| Fase 3 | CI basico com testes + analise estatica | FEITO | Workflow em `.github/workflows/ci.yml`. |
| Fase 3 | PMD verde no CI | FEITO | `maven-pmd-plugin` atualizado para `3.28.0` e etapa dedicada no workflow. |
| Fase 3 | Saneamento de encoding em lote (API/Runners) | PARCIAL | Rodada aplicada em `ClienteApiGraphQL`, `ClienteApiDataExport`, `GraphQLQueries` e `ExtractionLogger`; ocorrencias totais reduziram de 382 para 106. |
| Fase 3 | Saneamento global de encoding do projeto | PENDENTE | Ainda ha ocorrencias de mojibake em multiplos arquivos. |

## Pendencias abertas (objetivas)
1. Reduzir acoplamento residual de `Main`.
2. Concluir saneamento global de encoding UTF-8 sem BOM.
3. Consolidar estruturas documentais ainda paralelas em `docs/`.
4. Registrar trilha clara para o modulo dashboard (refatorado ou descontinuado).

## Validacoes desta rodada
```bash
mvn -B -ntp test
mvn -B -ntp -DskipTests=true pmd:pmd
```
Resultado: ambos com `BUILD SUCCESS` no ambiente local.
