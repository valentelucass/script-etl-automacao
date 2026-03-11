package br.com.extrator.suporte.configuracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/suporte/configuracao/ConfigSeguranca.java
Classe  : ConfigSeguranca (class)
Pacote  : br.com.extrator.suporte.configuracao
Modulo  : Suporte - Config
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


public final class ConfigSeguranca {
    private ConfigSeguranca() {
    }

    public static String obterPepper() {
        return obterTexto("EXTRATOR_AUTH_PEPPER", "security.auth.pepper", "");
    }

    public static int obterMaxTentativasFalhas() {
        return obterInteiro("EXTRATOR_AUTH_MAX_TENTATIVAS", "security.auth.max_tentativas", 3);
    }

    public static int obterMinutosBloqueio() {
        return obterInteiro("EXTRATOR_AUTH_BLOQUEIO_MINUTOS", "security.auth.bloqueio_minutos", 5);
    }

    public static String obterTexto(final String envVar, final String propertyKey, final String defaultValue) {
        final String env = System.getenv(envVar);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        final String property = ConfigSource.obterPropriedade(propertyKey);
        if (property != null && !property.isBlank()) {
            return property.trim();
        }
        return defaultValue;
    }

    public static int obterInteiro(final String envVar, final String propertyKey, final int defaultValue) {
        try {
            return Integer.parseInt(obterTexto(envVar, propertyKey, String.valueOf(defaultValue)));
        } catch (final NumberFormatException e) {
            return defaultValue;
        }
    }
}
