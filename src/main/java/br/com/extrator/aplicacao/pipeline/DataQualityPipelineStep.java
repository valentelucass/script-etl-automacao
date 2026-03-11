/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/pipeline/DataQualityPipelineStep.java
Classe  : DataQualityPipelineStep (class)
Pacote  : br.com.extrator.aplicacao.pipeline
Modulo  : Pipeline - Aplicacao

Papel   : Step de pipeline que executa validacao de qualidade dos dados extraidos (5 checks).

Conecta com:
- DataQualityService (delegacao para validacao)
- PipelineStep (interface que implementa)

Fluxo geral:
1) executar(dataInicio, dataFim) delega a qualityService.avaliar().
2) Retorna StepExecutionResult com status (SUCCESS | FAILED) e metadata (checks_total, checks_failed).
3) Se aprovado: SUCCESS; senao: FAILED com ErrorTaxonomy.DATA_QUALITY_BREACH.

Estrutura interna:
Atributos-chave:
- qualityService: DataQualityService (validador).
- entidades: List<String> (entidades a validar).
Metodos principais:
- executar(): executa checks de qualidade.
- obterNomeEtapa(), obterNomeEntidade(): identificacao.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.pipeline;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import br.com.extrator.observabilidade.quality.DataQualityReport;
import br.com.extrator.observabilidade.quality.DataQualityService;
import br.com.extrator.observabilidade.quality.DataQualityCheckResult;
import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.aplicacao.pipeline.runtime.StepStatus;
import br.com.extrator.aplicacao.politicas.ErrorTaxonomy;
import br.com.extrator.suporte.console.LoggerConsole;

public final class DataQualityPipelineStep implements PipelineStep {
    private static final LoggerConsole log = LoggerConsole.getLogger(DataQualityPipelineStep.class);
    private final DataQualityService qualityService;
    private final List<String> entidades;

    public DataQualityPipelineStep(final DataQualityService qualityService, final List<String> entidades) {
        this.qualityService = qualityService;
        this.entidades = entidades;
    }

    @Override
    public StepExecutionResult executar(final LocalDate dataInicio, final LocalDate dataFim) {
        final LocalDateTime inicio = LocalDateTime.now();
        final DataQualityReport report = qualityService.avaliar(dataInicio, dataFim, entidades);
        final boolean ok = report.isAprovado();
        final List<DataQualityCheckResult> falhas = report.obterResultados().stream()
            .filter(resultado -> !resultado.isAprovado())
            .toList();
        final String resumoFalhas = resumirFalhas(falhas);
        final StepExecutionResult.Builder builder = StepExecutionResult.builder(obterNomeEtapa(), obterNomeEntidade())
            .status(ok ? StepStatus.SUCCESS : StepStatus.FAILED)
            .startedAt(inicio)
            .finishedAt(LocalDateTime.now())
            .message("checks_failed=" + report.totalFalhas() + (resumoFalhas.isBlank() ? "" : " | " + resumoFalhas))
            .metadata("checks_total", report.obterResultados().size())
            .metadata("checks_failed", report.totalFalhas())
            .metadata("checks_failed_detail", resumoFalhas);
        if (!ok) {
            for (final DataQualityCheckResult falha : falhas) {
                log.warn(
                    "DATA_QUALITY_FALHA | entidade={} | check={} | measured={} | threshold={} | detalhe={}",
                    falha.getEntidade(),
                    falha.getCheckName(),
                    falha.getMeasuredValue(),
                    falha.getThreshold(),
                    falha.getDetails()
                );
            }
            builder.errorTaxonomy(ErrorTaxonomy.DATA_QUALITY_BREACH);
        }
        return builder.build();
    }

    @Override
    public String obterNomeEtapa() {
        return "quality:checks";
    }

    @Override
    public String obterNomeEntidade() {
        return "quality";
    }

    private String resumirFalhas(final List<DataQualityCheckResult> falhas) {
        if (falhas == null || falhas.isEmpty()) {
            return "";
        }
        return falhas.stream()
            .limit(5)
            .map(falha -> {
                final String detalhe = falha.getDetails() == null ? "" : falha.getDetails().trim();
                return detalhe.isEmpty()
                    ? falha.getEntidade() + ":" + falha.getCheckName()
                    : falha.getEntidade() + ":" + falha.getCheckName() + "(" + detalhe + ")";
            })
            .collect(Collectors.joining(" | "));
    }
}

