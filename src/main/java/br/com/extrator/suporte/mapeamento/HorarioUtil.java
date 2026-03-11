/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/util/mapeamento/HorarioUtil.java
Classe  : HorarioUtil (class)
Pacote  : br.com.extrator.suporte.mapeamento
Modulo  : Utilitario compartilhado
Papel   : Implementa responsabilidade de horario util.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Centraliza funcoes auxiliares reutilizaveis.
2) Evita repeticao de logica transversal.
3) Apoia configuracao, formatacao e infraestrutura.

Estrutura interna:
Metodos principais:
- HorarioUtil(): realiza operacao relacionada a "horario util".
- normalizarHora(...1 args): realiza operacao relacionada a "normalizar hora".
Atributos-chave:
- logger: logger da classe para diagnostico.
- FORMATTER_ISO: campo de estado para "formatter iso".
- FORMATTER_HHMM: campo de estado para "formatter hhmm".
[DOC-FILE-END]============================================================== */

package br.com.extrator.suporte.mapeamento;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilitario centralizado para normalizacao de campos de hora vindos da API.
 *
 * Aceita horas simples, timestamps ISO e casos com sufixos como "Z" ou milissegundos.
 * Valores que chegam apenas como data sao tratados como nulos para evitar truncamento
 * incorreto em colunas TIME no banco.
 * 
 * @since 2.3.2
 */
public final class HorarioUtil {
    private static final Logger logger = LoggerFactory.getLogger(HorarioUtil.class);
    private static final DateTimeFormatter FORMATTER_ISO = DateTimeFormatter.ISO_LOCAL_TIME;
    private static final DateTimeFormatter FORMATTER_HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern PADRAO_DATA_HORA = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[ T](\\d{2}:\\d{2}(?::\\d{2})?)");
    private static final Pattern PADRAO_HORA_COM_PREFIXO = Pattern.compile("^(\\d{2}:\\d{2}(:\\d{2})?)");
    
    private HorarioUtil() {
        // Classe utilitária - construtor privado
    }
    
    /**
     * Normaliza um campo de hora sem alterar o contrato atual dos mapeadores.
     *
     * @param hora string representando horario
     * @return hora valida, hora extraida de timestamp, valor original curto, ou null para lixo/data pura
     */
    public static String normalizarHora(final String hora) {
        if (hora == null) {
            return null;
        }
        final String valor = hora.trim();
        if (valor.isEmpty()) {
            return null;
        }

        if (valor.matches("^\\d{2}:\\d{2}(:\\d{2})?$")) {
            return valor.length() <= 10 ? valor : valor.substring(0, 8);
        }

        final Matcher matcherDataHora = PADRAO_DATA_HORA.matcher(valor);
        if (matcherDataHora.find()) {
            String horaExtraida = matcherDataHora.group(1);
            if (horaExtraida.length() > 8) {
                horaExtraida = horaExtraida.substring(0, 8);
            }
            return horaExtraida;
        }

        if (valor.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            logger.debug("Campo de hora contem apenas data: '{}'. Convertendo para null.", valor);
            return null;
        }

        final Matcher matcherHora = PADRAO_HORA_COM_PREFIXO.matcher(valor);
        if (matcherHora.find()) {
            String horaExtraida = matcherHora.group(1);
            if (horaExtraida.length() > 8) {
                horaExtraida = horaExtraida.substring(0, 8);
            }
            return horaExtraida;
        }

        if ("NULL".equalsIgnoreCase(valor) || valor.length() > 10) {
            return null;
        }

        try {
            final LocalTime time = LocalTime.parse(valor, FORMATTER_ISO);
            return time.format(FORMATTER_HHMM);
        } catch (final Exception e1) {
            try {
                final LocalTime time = LocalTime.parse(valor, FORMATTER_HHMM);
                return time.format(FORMATTER_HHMM);
            } catch (final Exception e2) {
                return valor;
            }
        }
    }
}
