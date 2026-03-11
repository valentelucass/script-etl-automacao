/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/portas/GraphQLGateway.java
Classe  : GraphQLGateway (interface)
Pacote  : br.com.extrator.aplicacao.portas
Modulo  : Porta (Interface)

Papel   : Porta gateway para execucao de extracao via API GraphQL.

Conecta com:
- GraphQLGatewayAdapter (implementacao em bootstrap/pipeline)
- GraphQLPipelineStep (consume)

Fluxo geral:
1) executar(dataInicio, dataFim, entidade) chama API GraphQL.
2) Retorna StepExecutionResult (status, timing, mensagem, metadata).

Estrutura interna:
Metodos principais:
- executar(LocalDate, LocalDate, String): StepExecutionResult com resultado da extracao.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.portas;

import java.time.LocalDate;

import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;

public interface GraphQLGateway {
    StepExecutionResult executar(LocalDate dataInicio, LocalDate dataFim, String entidade) throws Exception;
}


