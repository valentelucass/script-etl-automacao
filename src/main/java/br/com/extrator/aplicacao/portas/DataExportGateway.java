/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/portas/DataExportGateway.java
Classe  : DataExportGateway (interface)
Pacote  : br.com.extrator.aplicacao.portas
Modulo  : Porta (Interface)

Papel   : Porta gateway para execucao de extracao via API DataExport.

Conecta com:
- DataExportGatewayAdapter (implementacao em bootstrap/pipeline)
- DataExportPipelineStep (consume)

Fluxo geral:
1) executar(dataInicio, dataFim, entidade) chama API DataExport.
2) Retorna StepExecutionResult (status, timing, mensagem, metadata).

Estrutura interna:
Metodos principais:
- executar(LocalDate, LocalDate, String): StepExecutionResult com resultado da extracao.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.portas;

import java.time.LocalDate;

import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;

public interface DataExportGateway {
    StepExecutionResult executar(LocalDate dataInicio, LocalDate dataFim, String entidade) throws Exception;
}


