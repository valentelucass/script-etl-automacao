/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/util/formatacao/FormatadorData.java
Classe  : FormatadorData (class)
Pacote  : br.com.extrator.util.formatacao
Modulo  : Utilitario compartilhado
Papel   : Implementa responsabilidade de formatador data.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Centraliza funcoes auxiliares reutilizaveis.
2) Evita repeticao de logica transversal.
3) Apoia configuracao, formatacao e infraestrutura.

Estrutura interna:
Metodos principais:
- parseLocalDate(...1 args): realiza operacao relacionada a "parse local date".
- parseLocalDate(...2 args): realiza operacao relacionada a "parse local date".
- parseLocalDateTime(...1 args): realiza operacao relacionada a "parse local date time".
- parseOffsetDateTime(...1 args): realiza operacao relacionada a "parse offset date time".
- formatISO(...1 args): realiza operacao relacionada a "format iso".
- formatBR(...1 args): realiza operacao relacionada a "format br".
- formatFileName(...1 args): realiza operacao relacionada a "format file name".
- formatLog(...1 args): realiza operacao relacionada a "format log".
- agoraFormatadoLog(): realiza operacao relacionada a "agora formatado log".
- agoraFormatadoArquivo(): realiza operacao relacionada a "agora formatado arquivo".
- FormatadorData(): realiza operacao relacionada a "formatador data".
Atributos-chave:
- logger: logger da classe para diagnostico.
- ISO_DATE: campo de estado para "iso date".
- ISO_DATE_TIME: campo de estado para "iso date time".
- ISO_OFFSET_DATE_TIME: campo de estado para "iso offset date time".
- BR_DATE: campo de estado para "br date".
- BR_DATE_TIME: campo de estado para "br date time".
- LOG_DATE_TIME: campo de estado para "log date time".
- FILE_NAME: campo de estado para "file name".
- OFFSET_PADRAO_SEM_TIMEZONE: campo de estado para "offset padrao sem timezone".
[DOC-FILE-END]============================================================== */

package br.com.extrator.util.formatacao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classe utilitária centralizada para formatação e parsing de datas.
 * Evita duplicação de DateTimeFormatter espalhados pelo código.
 * 
 * @author Sistema de Extração ESL Cloud
 * @version 1.0
 */
public final class FormatadorData {
    
    private static final Logger logger = LoggerFactory.getLogger(FormatadorData.class);
    
    // ========== FORMATADORES PADRÃO ==========
    
    /** Formato ISO padrão: yyyy-MM-dd */
    public static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    
    /** Formato ISO padrão: yyyy-MM-dd'T'HH:mm:ss */
    public static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    /** Formato ISO com offset: yyyy-MM-dd'T'HH:mm:ssXXX */
    public static final DateTimeFormatter ISO_OFFSET_DATE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    
    /** Formato brasileiro: dd/MM/yyyy */
    public static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    
    /** Formato brasileiro com hora: dd/MM/yyyy HH:mm:ss */
    public static final DateTimeFormatter BR_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    
    /** Formato para logs: yyyy-MM-dd HH:mm:ss */
    public static final DateTimeFormatter LOG_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /** Formato para nomes de arquivo: yyyy-MM-dd_HH-mm-ss */
    public static final DateTimeFormatter FILE_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /** Fallback para datas sem timezone explícito (padrão operacional da integração). */
    private static final ZoneOffset OFFSET_PADRAO_SEM_TIMEZONE = ZoneOffset.of("-03:00");

    /** Formatos comuns de data/hora sem timezone que já apareceram em integrações. */
    private static final DateTimeFormatter[] FORMATOS_DATA_HORA_SEM_OFFSET = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
    };
    
    // ========== MÉTODOS DE PARSING ==========
    
    /**
     * Converte String para LocalDate de forma segura.
     * 
     * @param dateStr String no formato yyyy-MM-dd
     * @return LocalDate ou null se inválido
     */
    public static LocalDate parseLocalDate(final String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim(), ISO_DATE);
        } catch (final DateTimeParseException e) {
            logger.warn("Erro ao parsear LocalDate '{}': {}", dateStr, e.getMessage());
            return null;
        }
    }
    
    /**
     * Converte String para LocalDate com formatador customizado.
     * 
     * @param dateStr String de data
     * @param formatter Formatador a usar
     * @return LocalDate ou null se inválido
     */
    public static LocalDate parseLocalDate(final String dateStr, final DateTimeFormatter formatter) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim(), formatter);
        } catch (final DateTimeParseException e) {
            logger.warn("Erro ao parsear LocalDate '{}': {}", dateStr, e.getMessage());
            return null;
        }
    }
    
    /**
     * Converte String para LocalDateTime de forma segura.
     * 
     * @param dateTimeStr String no formato ISO
     * @return LocalDateTime ou null se inválido
     */
    public static LocalDateTime parseLocalDateTime(final String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr.trim());
        } catch (final DateTimeParseException e) {
            logger.warn("Erro ao parsear LocalDateTime '{}': {}", dateTimeStr, e.getMessage());
            return null;
        }
    }
    
    /**
     * Converte String para OffsetDateTime de forma segura.
     * 
     * @param dateTimeStr String no formato ISO com offset
     * @return OffsetDateTime ou null se inválido
     */
    public static OffsetDateTime parseOffsetDateTime(final String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        final String valor = dateTimeStr.trim();

        // Formato ISO completo com offset (ex.: 2025-10-02T00:00:00.000-03:00)
        try {
            return OffsetDateTime.parse(valor);
        } catch (final DateTimeParseException ignored) {
            // Tentativas de fallback abaixo.
        }

        // ISO local sem offset (ex.: 2025-10-02T00:00:00)
        try {
            return LocalDateTime.parse(valor, ISO_DATE_TIME).atOffset(OFFSET_PADRAO_SEM_TIMEZONE);
        } catch (final DateTimeParseException ignored) {
            // Tentativas de fallback abaixo.
        }

        // Formatos legados sem timezone (ex.: 2025-10-02 00:00:00)
        for (final DateTimeFormatter formatter : FORMATOS_DATA_HORA_SEM_OFFSET) {
            try {
                return LocalDateTime.parse(valor, formatter).atOffset(OFFSET_PADRAO_SEM_TIMEZONE);
            } catch (final DateTimeParseException ignored) {
                // Tenta próximo formatter
            }
        }

        // Apenas data (ex.: 2025-10-07) -> início do dia no offset padrão
        try {
            return LocalDate.parse(valor, ISO_DATE).atStartOfDay().atOffset(OFFSET_PADRAO_SEM_TIMEZONE);
        } catch (final DateTimeParseException e) {
            logger.warn("Erro ao parsear OffsetDateTime '{}': {}", dateTimeStr, e.getMessage());
            return null;
        }
    }
    
    // ========== MÉTODOS DE FORMATAÇÃO ==========
    
    /**
     * Formata LocalDate para String ISO (yyyy-MM-dd).
     * 
     * @param date Data a formatar
     * @return String formatada ou null
     */
    public static String formatISO(final LocalDate date) {
        return date != null ? date.format(ISO_DATE) : null;
    }
    
    /**
     * Formata LocalDateTime para String ISO.
     * 
     * @param dateTime Data/hora a formatar
     * @return String formatada ou null
     */
    public static String formatISO(final LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(ISO_DATE_TIME) : null;
    }
    
    /**
     * Formata LocalDate para formato brasileiro (dd/MM/yyyy).
     * 
     * @param date Data a formatar
     * @return String formatada ou null
     */
    public static String formatBR(final LocalDate date) {
        return date != null ? date.format(BR_DATE) : null;
    }
    
    /**
     * Formata LocalDateTime para formato brasileiro (dd/MM/yyyy HH:mm:ss).
     * 
     * @param dateTime Data/hora a formatar
     * @return String formatada ou null
     */
    public static String formatBR(final LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(BR_DATE_TIME) : null;
    }
    
    /**
     * Formata LocalDateTime para nome de arquivo (yyyy-MM-dd_HH-mm-ss).
     * 
     * @param dateTime Data/hora a formatar
     * @return String formatada ou null
     */
    public static String formatFileName(final LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(FILE_NAME) : null;
    }
    
    /**
     * Formata LocalDateTime para logs (yyyy-MM-dd HH:mm:ss).
     * 
     * @param dateTime Data/hora a formatar
     * @return String formatada ou null
     */
    public static String formatLog(final LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(LOG_DATE_TIME) : null;
    }
    
    /**
     * Retorna a data/hora atual formatada para logs.
     * 
     * @return String formatada
     */
    public static String agoraFormatadoLog() {
        return LocalDateTime.now().format(LOG_DATE_TIME);
    }
    
    /**
     * Retorna a data/hora atual formatada para nome de arquivo.
     * 
     * @return String formatada
     */
    public static String agoraFormatadoArquivo() {
        return LocalDateTime.now().format(FILE_NAME);
    }
    
    private FormatadorData() {
        // Impede instanciação
    }
}
