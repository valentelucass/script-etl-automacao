/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/PayloadHashUtil.java
Classe  : PayloadHashUtil (class)
Pacote  : br.com.extrator.integracao
Modulo  : Integracao HTTP

Papel   : Utilitario para calculo de SHA256 hex de payloads (para deduplicacao).

Conecta com:
- Nenhuma (utilidade pura)

Fluxo geral:
1) sha256Hex(payload) calcula hash SHA256.
2) Retorna hex string (minuscula).
3) Null-safe: null -> "".

Estrutura interna:
Metodos principais:
- sha256Hex(String): SHA256 hex string.
[DOC-FILE-END]============================================================== */
package br.com.extrator.integracao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class PayloadHashUtil {
    private PayloadHashUtil() {
    }

    static String sha256Hex(final String payload) {
        final String valorSeguro = payload == null ? "" : payload;
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(valorSeguro.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hash = new StringBuilder(digest.length * 2);
            for (final byte item : digest) {
                hash.append(String.format("%02x", item));
            }
            return hash.toString();
        } catch (final NoSuchAlgorithmException ex) {
            return "";
        }
    }
}
