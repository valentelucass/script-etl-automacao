/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/pipeline/PipelineStep.java
Classe  : PipelineStep (interface)
Pacote  : br.com.extrator.aplicacao.pipeline
Modulo  : Pipeline - Aplicacao

Papel   : Interface contrato para um step de pipeline que executa extracao em um intervalo de datas.

Conecta com:
- GraphQLPipelineStep, DataExportPipelineStep (implementacoes)
- DataQualityPipelineStep (implementacao)

Fluxo geral:
1) PipelineOrchestrator percorre lista de steps e executa cada um.
2) Cada step implementa executar() para delegar ao gateway apropriado.
3) obterNomeEtapa() e obterNomeEntidade() fornecem identificacao para logging.

Estrutura interna:
Metodos principais:
- executar(LocalDate, LocalDate): executa step e retorna StepExecutionResult.
- obterNomeEtapa(): identificador da etapa (ex: graphql:coletas).
- obterNomeEntidade(): nome da entidade processada.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.pipeline;

import java.time.LocalDate;

import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;

public interface PipelineStep {
    StepExecutionResult executar(LocalDate dataInicio, LocalDate dataFim) throws Exception;

    String obterNomeEtapa();

    String obterNomeEntidade();
}


