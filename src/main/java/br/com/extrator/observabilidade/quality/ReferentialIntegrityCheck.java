package br.com.extrator.observabilidade.quality;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/quality/ReferentialIntegrityCheck.java
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


public final class ReferentialIntegrityCheck implements DataQualityCheck {
    @Override
    public String obterNome() {
        return "referential_integrity";
    }

    @Override
    public DataQualityCheckResult executar(final DataQualityContext context) {
        final long breaks = context.getQueryPort().contarQuebrasReferenciais(context.getEntidade());
        final long threshold = context.getMaxReferentialBreaks();
        final boolean passed = breaks <= threshold;
        final String mensagem = passed
            ? (breaks == 0L ? "Integridade referencial ok"
                            : "Quebras referenciais dentro da tolerancia (" + breaks + " <= " + threshold + ")")
            : "Quebras referenciais acima da tolerancia (" + breaks + " > " + threshold + ")";
        return new DataQualityCheckResult(
            context.getEntidade(),
            obterNome(),
            passed,
            breaks,
            threshold,
            mensagem
        );
    }
}


