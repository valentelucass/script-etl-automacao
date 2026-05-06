package br.com.extrator.suporte.log;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/suporte/log/SensitiveDataSanitizer.java
Classe  : SensitiveDataSanitizer (class)
Pacote  : br.com.extrator.suporte.log
Modulo  : Suporte - Log
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.util.regex.Pattern;

/**
 * Sanitizes sensitive fragments before logging or persisting operational errors.
 */
public final class SensitiveDataSanitizer {

    private static final String MASK = "***";

    private static final Pattern BEARER_PATTERN =
        Pattern.compile("(?i)\\bBearer\\s+([A-Za-z0-9\\-._~+/]+=*)");
    private static final Pattern BASIC_AUTH_PATTERN =
        Pattern.compile("(?i)\\bBasic\\s+([A-Za-z0-9+/=]{8,})");
    private static final Pattern KEY_VALUE_PATTERN =
        Pattern.compile("(?i)\\b(password|senha|token|api[_-]?key|secret|client[_-]?secret|authorization|login)\\b\\s*[:=]\\s*([^,;\\s]+)");
    private static final Pattern JSON_KEY_PATTERN =
        Pattern.compile("(?i)\"(password|senha|token|api[_-]?key|secret|client[_-]?secret|authorization|login)\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern URL_QUERY_SECRET_PATTERN =
        Pattern.compile("(?i)([?&](?:access_token|token|api[_-]?key|apikey|password|secret|client[_-]?secret)=)([^&#\\s]+)");
    private static final Pattern HEADER_SECRET_PATTERN =
        Pattern.compile("(?im)\\b(authorization|x-api-key|api-key|x-auth-token|x-access-token)\\b\\s*:\\s*([^\\r\\n]+)");
    private static final Pattern JDBC_PASSWORD_PATTERN =
        Pattern.compile("(?i)(password\\s*=\\s*)([^;\\s]+)");
    private static final Pattern JDBC_USER_PATTERN =
        Pattern.compile("(?i)(user(?:name|id)?\\s*=\\s*)([^;\\s]+)");
    private static final Pattern CPF_KEY_VALUE_PATTERN =
        Pattern.compile("(?i)\\b(cpf|cpf_motorista\\d*|cpfMotorista\\d*)\\b\\s*[:=]\\s*([0-9.\\-]{11,14})");
    private static final Pattern CNPJ_KEY_VALUE_PATTERN =
        Pattern.compile("(?i)\\b(cnpj|cnpj_cliente\\w*|cnpjCliente\\w*|documento)\\b\\s*[:=]\\s*([0-9./\\-]{14,18})");
    private static final Pattern CPF_JSON_PATTERN =
        Pattern.compile("(?i)\"(cpf|cpf_motorista\\d*|cpfMotorista\\d*)\"\\s*:\\s*\"([0-9.\\-]{11,14})\"");
    private static final Pattern CNPJ_JSON_PATTERN =
        Pattern.compile("(?i)\"(cnpj|cnpj_cliente\\w*|cnpjCliente\\w*|documento)\"\\s*:\\s*\"([0-9./\\-]{14,18})\"");
    private static final Pattern CPF_FORMATTED_PATTERN =
        Pattern.compile("\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b");
    private static final Pattern CNPJ_FORMATTED_PATTERN =
        Pattern.compile("\\b\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}\\b");

    private SensitiveDataSanitizer() {
        // utility
    }

    public static String sanitize(final String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String sanitized = value;
        sanitized = BEARER_PATTERN.matcher(sanitized).replaceAll("Bearer " + MASK);
        sanitized = BASIC_AUTH_PATTERN.matcher(sanitized).replaceAll("Basic " + MASK);
        sanitized = HEADER_SECRET_PATTERN.matcher(sanitized).replaceAll("$1: " + MASK);
        sanitized = URL_QUERY_SECRET_PATTERN.matcher(sanitized).replaceAll("$1" + MASK);
        sanitized = KEY_VALUE_PATTERN.matcher(sanitized).replaceAll("$1=" + MASK);
        sanitized = JSON_KEY_PATTERN.matcher(sanitized).replaceAll("\"$1\":\"" + MASK + "\"");
        sanitized = CPF_KEY_VALUE_PATTERN.matcher(sanitized).replaceAll("$1=" + MASK);
        sanitized = CNPJ_KEY_VALUE_PATTERN.matcher(sanitized).replaceAll("$1=" + MASK);
        sanitized = CPF_JSON_PATTERN.matcher(sanitized).replaceAll("\"$1\":\"" + MASK + "\"");
        sanitized = CNPJ_JSON_PATTERN.matcher(sanitized).replaceAll("\"$1\":\"" + MASK + "\"");
        sanitized = CPF_FORMATTED_PATTERN.matcher(sanitized).replaceAll(MASK);
        sanitized = CNPJ_FORMATTED_PATTERN.matcher(sanitized).replaceAll(MASK);
        sanitized = JDBC_PASSWORD_PATTERN.matcher(sanitized).replaceAll("$1" + MASK);
        sanitized = JDBC_USER_PATTERN.matcher(sanitized).replaceAll("$1" + MASK);
        return sanitized;
    }
}
