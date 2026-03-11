package br.com.extrator.observabilidade.quality;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/quality/SchemaValidationCheck.java
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


public final class SchemaValidationCheck implements DataQualityCheck {
    @Override
    public String obterNome() {
        return "schema_validation";
    }

    @Override
    public DataQualityCheckResult executar(final DataQualityContext context) {
        final String actual = context.getQueryPort().detectarVersaoSchema(context.getEntidade());
        final String expected = context.getExpectedSchemaVersion();
        final boolean passed = expected != null && expected.equalsIgnoreCase(actual);
        return new DataQualityCheckResult(
            context.getEntidade(),
            obterNome(),
            passed,
            passed ? 1.0d : 0.0d,
            1.0d,
            "expected=" + expected + ", actual=" + actual
        );
    }
}


