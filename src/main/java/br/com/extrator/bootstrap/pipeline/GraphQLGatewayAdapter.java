/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/bootstrap/pipeline/GraphQLGatewayAdapter.java
Classe  : GraphQLGatewayAdapter (class)
Pacote  : br.com.extrator.bootstrap.pipeline
Modulo  : Bootstrap - Wiring

Papel   : Adapter que implementa GraphQLGateway, invocando o GraphQLExtractionService
          e retornando um StepExecutionResult padronizado para o pipeline.

Conecta com:
- GraphQLGateway (aplicacao.portas) — interface de porta implementada
- GraphQLExtractionService (integracao.graphql.services) — servico de extracao GraphQL
- StepExecutionResult (aplicacao.pipeline.runtime) — resultado padronizado de step
- StepStatus (aplicacao.pipeline.runtime) — enumeracao de status de step
- ConstantesEntidades (suporte.validacao) — constantes com nomes de entidades ETL

Fluxo geral:
1) Recebe dataInicio, dataFim e nome de entidade (pode ser null/"all").
2) Normaliza o filtro de entidade (null significa todas as entidades GraphQL).
3) Instancia GraphQLExtractionService e invoca executar().
4) Constroi e retorna StepExecutionResult com status SUCCESS, identificando o step pelo nome da entidade.

Estrutura interna:
Metodos principais:
- executar(dataInicio, dataFim, entidade): executa extracao GraphQL e retorna resultado.
- normalizeEntityFilter(entidade): normaliza o nome da entidade para null quando representa "todas".
[DOC-FILE-END]============================================================== */
package br.com.extrator.bootstrap.pipeline;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

import br.com.extrator.integracao.graphql.services.GraphQLExtractionService;
import br.com.extrator.aplicacao.portas.GraphQLGateway;
import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.aplicacao.pipeline.runtime.StepStatus;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public final class GraphQLGatewayAdapter implements GraphQLGateway {
    @Override
    public StepExecutionResult executar(final LocalDate dataInicio, final LocalDate dataFim, final String entidade) throws Exception {
        final LocalDateTime inicio = LocalDateTime.now();
        final String filtroEntidade = normalizeEntityFilter(entidade);
        final GraphQLExtractionService service = new GraphQLExtractionService();
        service.executar(dataInicio, dataFim, filtroEntidade);

        if (filtroEntidade != null && ConstantesEntidades.FATURAS_GRAPHQL.equalsIgnoreCase(filtroEntidade)) {
            return StepExecutionResult.builder("graphql:faturas_graphql", ConstantesEntidades.FATURAS_GRAPHQL)
                .status(StepStatus.SUCCESS)
                .startedAt(inicio)
                .finishedAt(LocalDateTime.now())
                .message("GraphQL faturas executado com sucesso")
                .metadata("source", "graphql")
                .build();
        }

        final String entidadeExecucao = filtroEntidade == null ? "graphql" : filtroEntidade;
        return StepExecutionResult.builder("graphql:" + entidadeExecucao, entidadeExecucao)
            .status(StepStatus.SUCCESS)
            .startedAt(inicio)
            .finishedAt(LocalDateTime.now())
            .message("GraphQL executado com sucesso")
            .metadata("source", "graphql")
            .build();
    }

    private String normalizeEntityFilter(final String entidade) {
        if (entidade == null) {
            return null;
        }
        final String normalizada = entidade.trim().toLowerCase(Locale.ROOT);
        if (normalizada.isBlank()
            || "all".equals(normalizada)
            || "todas".equals(normalizada)
            || "graphql".equals(normalizada)) {
            return null;
        }
        return normalizada;
    }
}

