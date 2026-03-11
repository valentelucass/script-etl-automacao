# TODO Arquitetural

Data de fechamento: `2026-03-07`

Validacoes executadas:

- `mvn -q -DskipTests package`
- `mvn -q -DskipTests compile`
- `mvn -q test`

Resultado validado:

- producao: `290` classes Java
- testes: `30` classes Java
- suite Maven: `125` testes, `0` falhas, `0` erros, `0` ignorados
- ciclos entre pacotes raiz: `0`
- diretoria final principal materializada em `src/main/java/br/com/extrator`

## Checklist final

- [x] Estrutura fisica final aplicada:
  - `bootstrap`
  - `aplicacao`
  - `dominio`
  - `integracao`
  - `persistencia`
  - `observabilidade`
  - `seguranca`
  - `suporte`
  - `comandos/cli`
- [x] Pacotes legados removidos do `src/main/java`:
  - `api`
  - `auditoria`
  - `db`
  - `modelo`
  - `runners`
  - `servicos`
  - `util`
- [x] `Main` movido para `br.com.extrator.bootstrap.Main`
- [x] `pom.xml` alinhado ao novo ponto de entrada
- [x] `README.md` e auditoria sincronizados com a arquitetura final
- [x] `config/.env.example` criado como fonte versionada de configuracao
- [x] CLI preservada como adaptador de entrada
- [x] runners legados removidos da estrutura principal
- [x] dominio sem dependencias para outras camadas
- [x] build Maven e suite de testes validados apos a reorganizacao

## Extracoes estruturais desta rodada

- [x] `LoopDaemonRunHandler` reduzido para `304` linhas com extração de `DaemonCycleTee`
- [x] `LoopReconciliationService` reduzido para `282` linhas com extração de `LoopReconciliationStateStore`
- [x] `DaemonLifecycleService` estabilizado em `350` linhas
- [x] `ValidacaoManifestosUseCase` reduzido para `456` linhas com extração de `ManifestosValidationQueries`

## Pendencias bloqueantes

- [x] Nenhuma

## Debito tecnico residual nao bloqueante

- DTOs e entidades grandes ainda concentram volume de linhas por representarem payloads extensos.
- Hotspots de acoplamento restantes ficaram limitados a casos de validacao detalhada e servicos de extracao.

## Classificacao

- `ARQUITETURA FINALIZADA`
