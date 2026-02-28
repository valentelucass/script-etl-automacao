# Auditoria Tecnica - 2026-02-27

## Resumo executivo tecnico
O projeto e funcional e amplo, mas apresenta riscos estruturais em governanca de build/versionamento, acoplamento em classes centrais, duplicacao documental/SQL e testabilidade. Esses pontos impactam previsibilidade de release, custo de manutencao e velocidade de evolucao.

## Problemas criticos (impacto alto)
1. Inconsistencia de release e build.
2. `LoopDaemonComando` com excesso de responsabilidades.
3. Captura ampla de excecoes (`catch (Throwable)`) em fluxos criticos.
4. Duplicidade SQL no bootstrap para artefato de dimensao.

## Problemas medios
1. Dependencias possivelmente redundantes (`poi`/`poi-ooxml`).
2. Legibilidade degradada por encoding (mojibake).
3. `Main` acumulando orquestracao, historico e lifecycle.
4. DTOs extensos e baixa coesao em pontos especificos.

## Melhorias recomendadas
1. Unificar governanca de versao (fonte unica + sincronizacao automatica).
2. Refatorar daemon por servicos especializados.
3. Remover `catch(Throwable)` onde nao estritamente necessario.
4. Eliminar duplicacoes de SQL e documentacao.
5. Expandir testes em extratores, repositorios e cenarios de falha parcial.
6. Revisar dependencias e adicionar auditoria de vulnerabilidades no pipeline.

## Sugestoes estruturais de refatoracao
1. Arquitetura explicita por camadas: `application`, `domain`, `infrastructure`, `cli`.
2. Configuracao imutavel validada no startup.
3. DTOs compostos por subdominios semanticos.
4. CI minima com build + testes + lint + auditoria de dependencias + guardas de regressao.

## Trechos tecnicos criticos (explicacao)
1. Divergencia de versao em release quebra rastreabilidade de deploy/hotfix.
2. Classe daemon monolitica reduz testabilidade e aumenta risco de regressao.
3. `catch(Throwable)` pode mascarar falhas graves e reduzir observabilidade.
4. SQL duplicado cria multiplas "fontes oficiais" de schema.
5. Mojibake prejudica diagnostico operacional em producao.

## Plano de acao priorizado
### P0 (imediato)
1. Alinhar versao de release (`README`, `pom`, tags/changelog).
2. Remover duplicidade SQL de artefatos canonicos.
3. Trocar `catch(Throwable)` por tratamento tipado.

### P1 (curto prazo)
1. Refatorar `LoopDaemonComando` em componentes especializados.
2. Corrigir encoding UTF-8 de fontes com caracteres corrompidos.
3. Implantar CI minima com validacoes automaticas.

### P2 (medio prazo)
1. Consolidar documentacao canonica e arquivar duplicatas.
2. Expandir cobertura de testes para extracao/persistencia/falhas.
3. Revisar e racionalizar dependencias.

## Pontos corretos (justificativa tecnica)
1. Segredos via variaveis de ambiente reduzem risco de hardcode.
2. Uso de PBKDF2 + salt + comparacao em tempo constante e adequado para autenticacao local/console.
3. Organizacao SQL por tipo de artefato facilita troubleshooting operacional.
