/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/util/mapeamento/NumeroUtil.java
Classe  : NumeroUtil (class)
Pacote  : br.com.extrator.util.mapeamento
Modulo  : Utilitario compartilhado
Papel   : Implementa responsabilidade de numero util.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Centraliza funcoes auxiliares reutilizaveis.
2) Evita repeticao de logica transversal.
3) Apoia configuracao, formatacao e infraestrutura.

Estrutura interna:
Metodos principais:
- NumeroUtil(): realiza operacao relacionada a "numero util".
- createDecimalFormatUS(): realiza operacao relacionada a "create decimal format us".
- parseBigDecimalUS(...1 args): realiza operacao relacionada a "parse big decimal us".
- parseIntegerOrNull(...1 args): realiza operacao relacionada a "parse integer or null".
Atributos-chave:
- logger: logger da classe para diagnostico.
- DECIMAL_FORMAT_US: campo de estado para "decimal format us".
[DOC-FILE-END]============================================================== */

package br.com.extrator.util.mapeamento;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilitário centralizado para parsing de números.
 * 
 * Unifica a lógica de conversão de strings para BigDecimal com locale US,
 * eliminando duplicação entre ContasAPagarMapper e FaturaPorClienteMapper.
 * 
 * @since 2.3.2
 */
public final class NumeroUtil {
    private static final Logger logger = LoggerFactory.getLogger(NumeroUtil.class);
    private static final DecimalFormat DECIMAL_FORMAT_US = createDecimalFormatUS();
    
    private NumeroUtil() {
        // Classe utilitária - construtor privado
    }
    
    /**
     * Cria DecimalFormat configurado para locale US.
     */
    private static DecimalFormat createDecimalFormatUS() {
        final DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        final DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        format.setParseBigDecimal(true);
        return format;
    }
    
    /**
     * Converte string para BigDecimal usando locale US (ponto como separador decimal).
     * 
     * Remove vírgulas antes de parsear (ex: "1,234.56" -> "1234.56").
     * 
     * @param valor String representando número
     * @return BigDecimal ou null se inválido/vazio
     */
    public static BigDecimal parseBigDecimalUS(final String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return null;
        }
        try {
            // Remove vírgulas (separador de milhares) antes de parsear
            final String valorLimpo = valor.replace(",", "");
            return (BigDecimal) DECIMAL_FORMAT_US.parse(valorLimpo);
        } catch (final Exception e) {
            logger.warn("Erro ao parsear BigDecimal (US locale): {}", valor);
            return null;
        }
    }
    
    /**
     * Converte string para Integer de forma segura.
     * 
     * @param valor String representando número inteiro
     * @return Integer ou null se inválido/vazio
     * @since 2.3.2
     */
    public static Integer parseIntegerOrNull(final String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(valor.trim());
        } catch (final NumberFormatException e) {
            logger.warn("Erro ao converter '{}' para Integer: {}", valor, e.getMessage());
            return null;
        }
    }
}
