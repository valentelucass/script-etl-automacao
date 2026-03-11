/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/pipeline/GraphQLPipelineStep.java
Classe  : GraphQLPipelineStep (class)
Pacote  : br.com.extrator.aplicacao.pipeline
Modulo  : Pipeline - Aplicacao

Papel   : Step de pipeline que executa extracao via GraphQLGateway para uma entidade.

Conecta com:
- GraphQLGateway (delegacao)
- PipelineStep (interface que implementa)

Fluxo geral:
1) PipelineOrchestrator cria instancia com gateway e nome de entidade.
2) executar(dataInicio, dataFim) delega a gateway.executar().
3) Retorna StepExecutionResult com status e detalhe de execucao.

Estrutura interna:
Atributos-chave:
- gateway: GraphQLGateway (delegacao para HTTP).
- entidade: String (nome da entidade a extrair).
Metodos principais:
- executar(LocalDate, LocalDate): delega a gateway.
- obterNomeEtapa(), obterNomeEntidade(): identificacao.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.pipeline;

import java.time.LocalDate;

import br.com.extrator.aplicacao.portas.GraphQLGateway;
import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;

public final class GraphQLPipelineStep implements PipelineStep {
    private final GraphQLGateway gateway;
    private final String entidade;

    public GraphQLPipelineStep(final GraphQLGateway gateway, final String entidade) {
        this.gateway = gateway;
        this.entidade = entidade;
    }

    @Override
    public StepExecutionResult executar(final LocalDate dataInicio, final LocalDate dataFim) throws Exception {
        return gateway.executar(dataInicio, dataFim, entidade);
    }

    @Override
    public String obterNomeEtapa() {
        return "graphql:" + entidade;
    }

    @Override
    public String obterNomeEntidade() {
        return entidade;
    }
}


