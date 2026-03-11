package br.com.extrator.suporte.configuracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/suporte/configuracao/ConfigValueParser.java
Classe  :  (class)
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


import java.util.function.DoublePredicate;
import java.util.function.IntPredicate;
import java.util.function.LongPredicate;

import org.slf4j.Logger;

final class ConfigValueParser {
    private ConfigValueParser() {
    }

    static int parseInt(final String valor,
                        final int valorPadrao,
                        final IntPredicate valido,
                        final Logger logger,
                        final String chaveLog,
                        final String mensagemPadrao) {
        try {
            final int convertido = Integer.parseInt(valor);
            return valido == null || valido.test(convertido) ? convertido : valorPadrao;
        } catch (final NumberFormatException | NullPointerException e) {
            if (logger != null && chaveLog != null && mensagemPadrao != null) {
                logger.warn("Propriedade '{}' nao encontrada ou invalida. Usando valor padrao: {}", chaveLog, mensagemPadrao);
            }
            return valorPadrao;
        }
    }

    static long parseLong(final String valor,
                          final long valorPadrao,
                          final LongPredicate valido,
                          final Logger logger,
                          final String chaveLog,
                          final String mensagemPadrao) {
        try {
            final long convertido = Long.parseLong(valor);
            return valido == null || valido.test(convertido) ? convertido : valorPadrao;
        } catch (final NumberFormatException | NullPointerException e) {
            if (logger != null && chaveLog != null && mensagemPadrao != null) {
                logger.warn("Propriedade '{}' nao encontrada ou invalida. Usando valor padrao: {}", chaveLog, mensagemPadrao);
            }
            return valorPadrao;
        }
    }

    static double parseDouble(final String valor,
                              final double valorPadrao,
                              final DoublePredicate valido,
                              final Logger logger,
                              final String chaveLog,
                              final String mensagemPadrao) {
        try {
            final double convertido = Double.parseDouble(valor);
            return valido == null || valido.test(convertido) ? convertido : valorPadrao;
        } catch (final NumberFormatException | NullPointerException e) {
            if (logger != null && chaveLog != null && mensagemPadrao != null) {
                logger.warn("Propriedade '{}' nao encontrada ou invalida. Usando valor padrao: {}", chaveLog, mensagemPadrao);
            }
            return valorPadrao;
        }
    }
}
