package br.com.extrator.suporte.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SensitiveDataSanitizerTest {

    @Test
    void deveMascararTokenBearerESenha() {
        final String original = "Authorization: Bearer abc.def.ghi password=123456";
        final String sanitized = SensitiveDataSanitizer.sanitize(original);

        assertTrue(sanitized.toLowerCase().contains("authorization"));
        assertTrue(sanitized.contains("***"));
        assertFalse(sanitized.contains("abc.def.ghi"));
        assertFalse(sanitized.contains("123456"));
    }

    @Test
    void deveMascararCamposJsonESegmentosJdbc() {
        final String original = "{\"token\":\"segredo\",\"senha\":\"123\"} jdbc:sqlserver://localhost;user=sa;password=Senha123;";
        final String sanitized = SensitiveDataSanitizer.sanitize(original);

        assertTrue(sanitized.contains("\"token\":\"***\""));
        assertTrue(sanitized.contains("\"senha\":\"***\""));
        assertTrue(sanitized.contains("user=***"));
        assertTrue(sanitized.contains("password=***"));
        assertFalse(sanitized.contains("segredo"));
        assertFalse(sanitized.contains("Senha123"));
    }

    @Test
    void devePreservarMensagemSemDadoSensivel() {
        final String original = "Processamento concluido com 120 registros";
        final String sanitized = SensitiveDataSanitizer.sanitize(original);

        assertEquals(original, sanitized);
    }

    @Test
    void deveMascararSecretsEmQueryStringEHeaderCustomizado() {
        final String original = "GET /endpoint?token=abc123&x=1\nX-API-KEY: chave-super-secreta";
        final String sanitized = SensitiveDataSanitizer.sanitize(original);

        assertTrue(sanitized.toLowerCase().contains("token="));
        assertTrue(sanitized.toLowerCase().contains("api-key"));
        assertTrue(sanitized.contains("***"));
        assertFalse(sanitized.contains("abc123"));
        assertFalse(sanitized.contains("chave-super-secreta"));
    }

    @Test
    void deveMascararAuthorizationBasic() {
        final String original = "Authorization: Basic YWxhZGRpbjpvcGVuc2VzYW1l";
        final String sanitized = SensitiveDataSanitizer.sanitize(original);

        assertTrue(sanitized.toLowerCase().contains("authorization"));
        assertTrue(sanitized.contains("***"));
        assertFalse(sanitized.contains("YWxhZGRpbjpvcGVuc2VzYW1l"));
    }
}
