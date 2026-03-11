package br.com.extrator.observabilidade.quality;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/quality/DataQualityReport.java
Classe  : DataQualityReport (class)
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


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DataQualityReport {
    private final List<DataQualityCheckResult> results;

    public DataQualityReport(final List<DataQualityCheckResult> results) {
        this.results = Collections.unmodifiableList(new ArrayList<>(results));
    }

    public List<DataQualityCheckResult> obterResultados() {
        return results;
    }

    public boolean isAprovado() {
        return results.stream().allMatch(DataQualityCheckResult::isAprovado);
    }

    public long totalFalhas() {
        return results.stream().filter(r -> !r.isAprovado()).count();
    }
}


