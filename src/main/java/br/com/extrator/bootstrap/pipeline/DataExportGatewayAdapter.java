/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/bootstrap/pipeline/DataExportGatewayAdapter.java
Classe  : DataExportGatewayAdapter (class)
Pacote  : br.com.extrator.bootstrap.pipeline
Modulo  : Bootstrap - Wiring

Papel   : Adapter que implementa DataExportGateway, invocando o DataExportExtractionService
          e retornando um StepExecutionResult padronizado para o pipeline.

Conecta com:
- DataExportGateway (aplicacao.portas) — interface de porta implementada
- DataExportExtractionService (integracao.dataexport.services) — servico de extracao DataExport
- StepExecutionResult (aplicacao.pipeline.runtime) — resultado padronizado de step
- StepStatus (aplicacao.pipeline.runtime) — enumeracao de status de step

Fluxo geral:
1) Recebe dataInicio, dataFim e nome de entidade (pode ser null/"all").
2) Normaliza o filtro de entidade (null significa todas as entidades).
3) Instancia DataExportExtractionService e invoca executar().
4) Constroi e retorna StepExecutionResult com status SUCCESS.

Estrutura interna:
Metodos principais:
- executar(dataInicio, dataFim, entidade): executa extracao DataExport e retorna resultado.
- normalizeEntityFilter(entidade): normaliza o nome da entidade para null quando representa "todas".
[DOC-FILE-END]============================================================== */
package br.com.extrator.bootstrap.pipeline;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

import br.com.extrator.integracao.dataexport.services.DataExportExtractionService;
import br.com.extrator.aplicacao.portas.DataExportGateway;
import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.aplicacao.pipeline.runtime.StepStatus;

public final class DataExportGatewayAdapter implements DataExportGateway {
    @Override
    public StepExecutionResult executar(final LocalDate dataInicio, final LocalDate dataFim, final String entidade) throws Exception {
        final LocalDateTime inicio = LocalDateTime.now();
        final String filtroEntidade = normalizeEntityFilter(entidade);
        final DataExportExtractionService service = new DataExportExtractionService();
        service.executar(dataInicio, dataFim, filtroEntidade);
        final String entidadeExecucao = filtroEntidade == null ? "dataexport" : filtroEntidade;
        return StepExecutionResult.builder("dataexport:" + entidadeExecucao, entidadeExecucao)
            .status(StepStatus.SUCCESS)
            .startedAt(inicio)
            .finishedAt(LocalDateTime.now())
            .message("DataExport executado com sucesso")
            .metadata("source", "dataexport")
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
            || "dataexport".equals(normalizada)) {
            return null;
        }
        return normalizada;
    }
}

