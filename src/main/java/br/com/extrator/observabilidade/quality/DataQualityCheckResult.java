package br.com.extrator.observabilidade.quality;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/quality/DataQualityCheckResult.java
Classe  : DataQualityCheckResult (class)
Pacote  : br.com.extrator.observabilidade.quality
Modulo  : Observabilidade - Quality
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


public final class DataQualityCheckResult {
    private final String entidade;
    private final String checkName;
    private final boolean passed;
    private final double measuredValue;
    private final double threshold;
    private final String details;

    public DataQualityCheckResult(
        final String entidade,
        final String checkName,
        final boolean passed,
        final double measuredValue,
        final double threshold,
        final String details
    ) {
        this.entidade = entidade;
        this.checkName = checkName;
        this.passed = passed;
        this.measuredValue = measuredValue;
        this.threshold = threshold;
        this.details = details;
    }

    public String getEntidade() {
        return entidade;
    }

    public String getCheckName() {
        return checkName;
    }

    public boolean isAprovado() {
        return passed;
    }

    public double getMeasuredValue() {
        return measuredValue;
    }

    public double getThreshold() {
        return threshold;
    }

    public String getDetails() {
        return details;
    }
}


