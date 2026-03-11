# Esquema das Fases Finais

Documento de execucao para as fases finais da modernizacao arquitetural.

Objetivo:
- separar as ultimas fases em blocos menores;
- impedir mistura de escopo entre fases;
- deixar claro o que deve entrar, o que nao deve entrar e como validar cada etapa.

Estado atual:
- Fases 1, 2 e 3 estao estabilizadas no caminho operacional.
- Fase 4 esta em andamento e ja teve extracoes seguras em request factory, parser, audit logger e utilitarios de hash.
- As fases abaixo devem comecar somente quando o corte atual da Fase 4 estiver considerado suficiente para sair do caminho critico.

## Ordem obrigatoria

1. Fechar Fase 4 em nivel suficiente para parar de aumentar o escopo dos clientes gigantes.
2. Executar Fase 5 sozinha.
3. Executar Fase 6 sozinha.
4. Executar Fase 7 sozinha.
5. Executar Fase 8 sozinha.
6. Executar Fase 9 sozinha.
7. Executar Fase 10 sozinha.
8. Executar Fase 11 sozinha.

Regra:
- nao misturar remocao de codigo morto com reorganizacao de pacotes;
- nao misturar refatoracao de configuracao com correcao de dependencias;
- nao mover classes de pacote antes de estabilizar responsabilidade e dependencia.

## Bloco A - Consolidacao logica

### FASE 5 - Reducao de god classes

Entrada:
- Fase 4 com clientes de API razoavelmente fatiados nas responsabilidades mais evidentes.

Foco:
- quebrar classes grandes de orquestracao, validacao e services centrais;
- reduzir SRP violado antes de mexer forte em pacotes.

Classes-alvo prioritarias:
- `ValidarApiVsBanco24hDetalhadoComando`
- `CarregadorConfig`
- `GraphQLExtractionService`
- `DataExportExtractionService`
- validadores grandes que ainda misturam leitura, regra e relatorio

Pode entrar:
- extracao de planners;
- extracao de validators;
- extracao de builders;
- extracao de services auxiliares;
- criacao de objetos de resultado para reduzir parametros e branching.

Nao deve entrar:
- renomeacao massiva;
- mudanca de pacote em lote;
- limpeza de codigo morto sem confirmacao;
- alteracao de contrato externo.

Entregaveis:
- classes grandes menores e mais coesas;
- evidencias no `TODO-ARQUITETURAL.md`;
- lista atualizada das classes que ainda continuam acima da meta por razao tecnica.

Gate de saida:
- a classe-alvo nao mistura mais multiplas responsabilidades centrais;
- `mvn -q -DskipTests compile`;
- `mvn -q test`.

### FASE 6 - Correcao de dependencias entre camadas

Entrada:
- classes grandes mais separadas, para que dependencias erradas fiquem visiveis.

Foco:
- corrigir direcao de dependencia;
- quebrar ciclos relevantes;
- aproximar o projeto de `comandos -> aplicacao -> dominio`, com adaptadores externos ao redor.

Alvos prioritarios:
- `runners <-> servicos`
- `api <-> db/modelo/util`
- `util` apontando para integracao
- pontos de instanciacao concreta que atravessam camadas sem necessidade

Pode entrar:
- criacao de portas/adapters onde houver acoplamento real;
- deslocamento de classes pequenas para camada correta se isso for necessario para quebrar ciclo;
- simplificacao de imports cruzados.

Nao deve entrar:
- reorganizacao final de todos os pacotes;
- remocao de classes mortas;
- refatoracao global de configuracao.

Entregaveis:
- mapa de dependencia atualizado;
- lista dos ciclos removidos;
- lista de desvios restantes com justificativa.

Gate de saida:
- ciclos criticos removidos ou reduzidos a casos justificados;
- `mvn -q -DskipTests compile`;
- `mvn -q test`.

## Bloco B - Limpeza estrutural

### FASE 7 - Remocao de codigo morto

Entrada:
- dependencias ja estabilizadas o suficiente para saber o que realmente ficou sem uso.

Foco:
- remover classes, metodos e stubs dormentes;
- diminuir ruído antes da refatoracao de configuracao e da reorganizacao fisica.

Alvos prioritarios:
- `EntityRepositoryPort`
- `OpenTelemetryAttributeBridge`
- `PrometheusMetricsExporter`
- `HorarioUtil`
- `LimpadorTabelas`
- `ComandoProvider`
- metodos sem chamador em `PipelineMetricsPort`

Pode entrar:
- remocao de classes sem referencia;
- eliminacao de imports mortos;
- retirada de wrappers sem uso;
- ajuste de documentacao operacional se alguma ferramenta manual for aposentada.

Nao deve entrar:
- mover classes em massa;
- quebrar `CarregadorConfig`;
- alterar fluxo principal do ETL.

Entregaveis:
- relatorio de remocao com impacto;
- busca de referencia limpa;
- TODO atualizado com os itens efetivamente removidos.

Gate de saida:
- tudo removido ou explicitamente justificado;
- `mvn -q -DskipTests compile`;
- `mvn -q test`.

### FASE 8 - Refatoracao de configuracao

Entrada:
- codigo morto removido e base mais limpa para isolar modulos de configuracao.

Foco:
- quebrar `CarregadorConfig` sem romper bootstrap;
- separar leitura, defaults e validacao por contexto.

Submodulos alvo:
- `ConfigApi`
- `ConfigBanco`
- `ConfigEtl`
- `ConfigSeguranca`
- `ConfigLoop`

Pode entrar:
- fachada de compatibilidade temporaria;
- migracao progressiva de consumidores;
- objetos menores por contexto.

Nao deve entrar:
- reorganizacao definitiva dos pacotes;
- renomeacao ampla;
- troca de contratos de ambiente.

Entregaveis:
- configuracao particionada;
- mapa de consumidores migrados;
- lista de pontos ainda ligados na fachada temporaria.

Gate de saida:
- `CarregadorConfig` deixa de ser o unico agregador central;
- `mvn -q -DskipTests compile`;
- `mvn -q test`.

## Bloco C - Fechamento arquitetural

### FASE 9 - Reorganizacao de pacotes

Entrada:
- responsabilidades e dependencias ja estabilizadas.

Foco:
- refletir a arquitetura final na estrutura fisica do projeto.

Sequencia recomendada:
1. mover `aplicacao` e adaptadores CLI;
2. mover `integracao/graphql` e `integracao/dataexport`;
3. mover `persistencia`;
4. mover `suporte`, `observabilidade` e `seguranca`;
5. limpar bridges temporarias.

Pode entrar:
- movimento incremental de pacotes;
- ajuste pontual de imports;
- remocao de classes-ponte ao final de cada lote.

Nao deve entrar:
- nova refatoracao logica grande;
- mudanca de comportamento funcional;
- renomeacao PT-BR em massa fora do necessario.

Entregaveis:
- arvore de pacotes mais proxima da arquitetura alvo;
- lista de pacotes antigos remanescentes;
- bridges temporarias reduzidas ao minimo.

Gate de saida:
- estrutura fisica consistente com a arquitetura definida;
- `mvn -q -DskipTests compile`;
- `mvn -q test`.

### FASE 10 - Validacao arquitetural

Entrada:
- reorganizacao fisica concluida.

Foco:
- medir o resultado final tecnicamente, sem assumir sucesso por percepcao.

Checklist objetivo:
- regerar relatorio de classes `>500` linhas;
- regerar relatorio de classes com `>10` dependencias;
- regerar detector de ciclos;
- regerar detector de codigo morto;
- regerar detector de duplicacao relevante;
- revisar se comandos CLI ficaram finos.

Entregaveis:
- relatorio comparativo antes/depois;
- lista de pendencias residuais;
- status das fases anteriores com evidencia.

Gate de saida:
- indicadores criticos melhoraram de forma comprovada;
- `mvn -q -DskipTests compile`;
- `mvn -q test`.

### FASE 11 - Auditoria final

Entrada:
- validacao arquitetural concluida e numericamente documentada.

Foco:
- confrontar o repositorio com os objetivos do plano.

Perguntas obrigatorias:
- os casos de uso continuam sendo o centro da aplicacao?
- os comandos CLI estao finos?
- existe um pipeline unico operacional?
- as god classes relevantes foram divididas?
- as dependencias respeitam a direcao esperada?
- o codigo morto conhecido saiu?
- a estrutura fisica final reflete as camadas?

Entregaveis:
- auditoria final de conformidade;
- lista explicita do que nao foi aplicado;
- justificativa tecnica para qualquer desvio remanescente.

Gate de saida:
- documento final de auditoria fechado;
- `mvn -q -DskipTests compile`;
- `mvn -q test`.

## Regra operacional por fase

Cada fase final deve seguir exatamente este ciclo:

1. escolher um lote pequeno dentro da fase;
2. aplicar a menor refatoracao que entregue ganho real;
3. rodar `mvn -q -DskipTests compile`;
4. rodar `mvn -q test`;
5. atualizar `TODO-ARQUITETURAL.md` com evidencia objetiva;
6. so entao abrir o proximo lote.

## Checklist de controle rapido

- [ ] Uma fase por vez
- [ ] Um tipo principal de mudanca por vez
- [ ] Compile verde apos cada lote
- [ ] Testes verdes apos cada lote
- [ ] TODO atualizado apos cada lote
- [ ] Sem reorganizacao fisica antes da estabilizacao logica
- [ ] Sem remocao de codigo sem busca de referencia
