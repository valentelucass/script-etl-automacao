/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/pipeline/PipelineReport.java
Classe  : PipelineReport (class)
Pacote  : br.com.extrator.aplicacao.pipeline
Modulo  : Pipeline - Aplicacao

Papel   : Relatorio imutavel de resultado de execucao de pipeline (steps, metricas, qualidade).

Conecta com:
- StepExecutionResult (lista de resultados dos steps)
- DataQualityReport (resultado de qualidade)

Fluxo geral:
1) PipelineOrchestrator constroi report via builder pattern.
2) addResult(), aborted(), metric() adicionam dados.
3) build() retorna PipelineReport imutavel.
4) Use cases consultam: getResultados(), isAborted(), isBemSucedido(), totalSucessos(), totalFalhasExecucao().

Estrutura interna:
Inner class Builder (fluent API):
- addResult(StepExecutionResult): adiciona resultado.
- aborted(boolean), abortedBy(String): marca aborcao.
- generatedAt(LocalDateTime): timestamp do relatorio.
- qualityReport(DataQualityReport): resultado de qualidade.
- metric(key, value): adiciona metrica.
- build(): cria PipelineReport imutavel.
Atributos-chave:
- resultados: List<StepExecutionResult> imutavel.
- aborted, abortedBy: estado de aborcao.
- qualityReport: resultado de data quality.
- metricsSnapshot: Map<String, Double> imutavel.
Metodos principais:
- totalSucessos(), totalFalhasExecucao(): contadores.
- isBemSucedido(): valida steps + quality.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.pipeline;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.extrator.observabilidade.quality.DataQualityReport;
import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;

public final class PipelineReport {
    private final LocalDate dataInicio;
    private final LocalDate dataFim;
    private final LocalDateTime generatedAt;
    private final List<StepExecutionResult> resultados;
    private final boolean aborted;
    private final String abortedBy;
    private final DataQualityReport qualityReport;
    private final Map<String, Double> metricsSnapshot;

    private PipelineReport(final Builder builder) {
        this.dataInicio = builder.dataInicio;
        this.dataFim = builder.dataFim;
        this.generatedAt = builder.generatedAt;
        this.resultados = Collections.unmodifiableList(new ArrayList<>(builder.resultados));
        this.aborted = builder.aborted;
        this.abortedBy = builder.abortedBy;
        this.qualityReport = builder.qualityReport;
        this.metricsSnapshot = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metricsSnapshot));
    }

    public static Builder builder(final LocalDate dataInicio, final LocalDate dataFim) {
        return new Builder(dataInicio, dataFim);
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public List<StepExecutionResult> getResultados() {
        return resultados;
    }

    public boolean isAborted() {
        return aborted;
    }

    public String getAbortedBy() {
        return abortedBy;
    }

    public DataQualityReport getQualityReport() {
        return qualityReport;
    }

    public Map<String, Double> getMetricsSnapshot() {
        return metricsSnapshot;
    }

    public long totalSucessos() {
        return resultados.stream().filter(StepExecutionResult::isSuccess).count();
    }

    public long totalFalhasExecucao() {
        return resultados.stream().filter(StepExecutionResult::isFailed).count();
    }

    public boolean isBemSucedido() {
        final boolean stepsOk = !aborted && totalFalhasExecucao() == 0;
        final boolean qualityOk = qualityReport == null || qualityReport.isAprovado();
        return stepsOk && qualityOk;
    }

    public static final class Builder {
        private final LocalDate dataInicio;
        private final LocalDate dataFim;
        private LocalDateTime generatedAt = LocalDateTime.now();
        private final List<StepExecutionResult> resultados = new ArrayList<>();
        private boolean aborted = false;
        private String abortedBy;
        private DataQualityReport qualityReport;
        private final Map<String, Double> metricsSnapshot = new LinkedHashMap<>();

        private Builder(final LocalDate dataInicio, final LocalDate dataFim) {
            this.dataInicio = dataInicio;
            this.dataFim = dataFim;
        }

        public Builder addResult(final StepExecutionResult result) {
            this.resultados.add(result);
            return this;
        }

        public Builder aborted(final boolean aborted) {
            this.aborted = aborted;
            return this;
        }

        public Builder abortedBy(final String abortedBy) {
            this.abortedBy = abortedBy;
            return this;
        }

        public Builder generatedAt(final LocalDateTime generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public Builder qualityReport(final DataQualityReport qualityReport) {
            this.qualityReport = qualityReport;
            return this;
        }

        public Builder metric(final String key, final double value) {
            this.metricsSnapshot.put(key, value);
            return this;
        }

        public PipelineReport build() {
            return new PipelineReport(this);
        }
    }
}


