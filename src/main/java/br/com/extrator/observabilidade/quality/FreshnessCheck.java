package br.com.extrator.observabilidade.quality;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/quality/FreshnessCheck.java
Classe  : DataQualityCheck (class)
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


import java.time.Duration;
import java.time.LocalDateTime;

public final class FreshnessCheck implements DataQualityCheck {
    @Override
    public String obterNome() {
        return "freshness";
    }

    @Override
    public DataQualityCheckResult executar(final DataQualityContext context) {
        final LocalDateTime latest = context.getQueryPort().buscarTimestampMaisRecente(context.getEntidade());
        if (latest == null) {
            return new DataQualityCheckResult(
                context.getEntidade(),
                obterNome(),
                false,
                Double.MAX_VALUE,
                context.getMaxLagMinutes(),
                "Sem timestamp de referencia"
            );
        }
        final long lagMin = Math.max(0L, Duration.between(latest, context.getNow()).toMinutes());
        final boolean passed = lagMin <= context.getMaxLagMinutes();
        return new DataQualityCheckResult(
            context.getEntidade(),
            obterNome(),
            passed,
            lagMin,
            context.getMaxLagMinutes(),
            passed ? "Freshness dentro do limite" : "Freshness acima do limite"
        );
    }
}


