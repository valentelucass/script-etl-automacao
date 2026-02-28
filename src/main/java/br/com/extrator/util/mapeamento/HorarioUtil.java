/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/util/mapeamento/HorarioUtil.java
Classe  : HorarioUtil (class)
Pacote  : br.com.extrator.util.mapeamento
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

package br.com.extrator.util.mapeamento;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilitário centralizado para normalização de horários.
 * 
 * Converte horários em diversos formatos (ISO, HH:mm, HH:mm:ss) para formato padronizado HH:mm.
 * Reutiliza a lógica originalmente em ColetaMapper.
 * 
 * @since 2.3.2
 */
public final class HorarioUtil {
    private static final Logger logger = LoggerFactory.getLogger(HorarioUtil.class);
    private static final DateTimeFormatter FORMATTER_ISO = DateTimeFormatter.ISO_LOCAL_TIME;
    private static final DateTimeFormatter FORMATTER_HHMM = DateTimeFormatter.ofPattern("HH:mm");
    
    private HorarioUtil() {
        // Classe utilitária - construtor privado
    }
    
    /**
     * Normaliza horário para formato HH:mm.
     * 
     * Aceita formatos:
     * - ISO: HH:mm:ss ou HH:mm:ss.SSS
     * - Simples: HH:mm
     * 
     * @param hora String representando horário
     * @return Horário normalizado (HH:mm) ou string original se não conseguir parsear
     */
    public static String normalizarHora(final String hora) {
        if (hora == null || hora.trim().isEmpty()) {
            return null;
        }
        try {
            // Tenta ISO primeiro (HH:mm:ss ou HH:mm:ss.SSS)
            final LocalTime time = LocalTime.parse(hora, FORMATTER_ISO);
            return time.format(FORMATTER_HHMM);
        } catch (final Exception e1) {
            try {
                // Tenta HH:mm
                final LocalTime time = LocalTime.parse(hora, FORMATTER_HHMM);
                return time.format(FORMATTER_HHMM);
            } catch (final Exception e2) {
                logger.warn("Erro ao normalizar hora: {}", hora);
                return hora; // Retorna original se não conseguir parsear
            }
        }
    }
}
