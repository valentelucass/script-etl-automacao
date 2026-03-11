/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/pipeline/DataExportPipelineStep.java
Classe  : DataExportPipelineStep (class)
Pacote  : br.com.extrator.aplicacao.pipeline
Modulo  : Pipeline - Aplicacao

Papel   : Step de pipeline que executa extracao via DataExportGateway para uma entidade.

Conecta com:
- DataExportGateway (delegacao)
- PipelineStep (interface que implementa)

Fluxo geral:
1) PipelineOrchestrator cria instancia com gateway e nome de entidade.
2) executar(dataInicio, dataFim) delega a gateway.executar().
3) Retorna StepExecutionResult com status e detalhe de execucao.

Estrutura interna:
Atributos-chave:
- gateway: DataExportGateway (delegacao para HTTP).
- entidade: String (nome da entidade a extrair).
Metodos principais:
- executar(LocalDate, LocalDate): delega a gateway.
- obterNomeEtapa(), obterNomeEntidade(): identificacao.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.pipeline;

import java.time.LocalDate;

import br.com.extrator.aplicacao.portas.DataExportGateway;
import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;

public final class DataExportPipelineStep implements PipelineStep {
    private final DataExportGateway gateway;
    private final String entidade;

    public DataExportPipelineStep(final DataExportGateway gateway, final String entidade) {
        this.gateway = gateway;
        this.entidade = entidade;
    }

    @Override
    public StepExecutionResult executar(final LocalDate dataInicio, final LocalDate dataFim) throws Exception {
        return gateway.executar(dataInicio, dataFim, entidade);
    }

    @Override
    public String obterNomeEtapa() {
        return "dataexport:" + entidade;
    }

    @Override
    public String obterNomeEntidade() {
        return entidade;
    }
}


