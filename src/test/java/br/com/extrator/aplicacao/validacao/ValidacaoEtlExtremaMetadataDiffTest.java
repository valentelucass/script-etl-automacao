package br.com.extrator.aplicacao.validacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ValidacaoEtlExtremaMetadataDiffTest {

    private final ValidacaoEtlExtremaMetadataDiff diff = new ValidacaoEtlExtremaMetadataDiff();

    @Test
    void deveDetectarTimezoneSemMarcarComoDivergenciaQuandoInstanteForIgual() {
        final var resultado = diff.comparar(
            "{\"issuedAt\":\"2026-03-08T12:00:00Z\"}",
            "{\"issuedAt\":\"2026-03-08T09:00:00-03:00\"}"
        );

        assertEquals(0, resultado.divergentFields());
        assertEquals(1, resultado.timezoneDrifts());
    }

    @Test
    void deveDetectarTruncamentoECampoAusente() {
        final var resultado = diff.comparar(
            "{\"document\":\"ABC123456789\",\"nested\":{\"status\":\"OK\"}}",
            "{\"document\":\"ABC123\",\"nested\":{}}"
        );

        assertEquals(1, resultado.divergentFields());
        assertEquals(1, resultado.truncatedFields());
        assertTrue(resultado.apiOnlyFields().contains("nested.status"));
    }
}
