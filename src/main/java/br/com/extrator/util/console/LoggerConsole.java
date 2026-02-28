/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/util/console/LoggerConsole.java
Classe  : LoggerConsole (class)
Pacote  : br.com.extrator.util.console
Modulo  : Utilitario compartilhado
Papel   : Implementa responsabilidade de logger console.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Centraliza funcoes auxiliares reutilizaveis.
2) Evita repeticao de logica transversal.
3) Apoia configuracao, formatacao e infraestrutura.

Estrutura interna:
Metodos principais:
- LoggerConsole(...1 args): realiza operacao relacionada a "logger console".
- getLogger(...1 args): expone valor atual do estado interno.
- info(...2 args): realiza operacao relacionada a "info".
- warn(...2 args): realiza operacao relacionada a "warn".
- error(...2 args): realiza operacao relacionada a "error".
- debug(...2 args): realiza operacao relacionada a "debug".
- trace(...2 args): realiza operacao relacionada a "trace".
- console(...1 args): realiza operacao relacionada a "console".
- console(...2 args): realiza operacao relacionada a "console".
- getUnderlyingLogger(): expone valor atual do estado interno.
- formatMessage(...2 args): realiza operacao relacionada a "format message".
Atributos-chave:
- logger: logger da classe para diagnostico.
[DOC-FILE-END]============================================================== */

package br.com.extrator.util.console;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilitário para logging duplo: Logger (arquivo) + Console (visual).
 * Mantém o feedback visual para scripts .bat enquanto grava logs estruturados.
 * 
 * @author Sistema de Extração ESL Cloud
 * @version 1.0
 */
public final class LoggerConsole {
    
    private final Logger logger;
    private static final boolean ESPELHAR_NO_CONSOLE =
        !"false".equalsIgnoreCase(System.getProperty("extrator.logger.console.mirror", "true"));
    
    /**
     * Cria uma instância do LoggerConsole para a classe especificada.
     * 
     * @param clazz Classe para o logger
     */
    public LoggerConsole(final Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }
    
    /**
     * Obtém uma instância do LoggerConsole para a classe especificada.
     * 
     * @param clazz Classe para o logger
     * @return Instância de LoggerConsole
     */
    public static LoggerConsole getLogger(final Class<?> clazz) {
        return new LoggerConsole(clazz);
    }
    
    /**
     * Log de informação (info no logger + println no console).
     * 
     * @param message Mensagem com placeholders {}
     * @param args Argumentos para os placeholders
     */
    public void info(final String message, final Object... args) {
        logger.info(message, args);
        if (ESPELHAR_NO_CONSOLE) {
            System.out.println(formatMessage(message, args));
        }
    }
    
    /**
     * Log de aviso (warn no logger + println no console).
     * 
     * @param message Mensagem com placeholders {}
     * @param args Argumentos para os placeholders
     */
    public void warn(final String message, final Object... args) {
        logger.warn(message, args);
        if (ESPELHAR_NO_CONSOLE) {
            System.out.println(formatMessage(message, args));
        }
    }
    
    /**
     * Log de erro (error no logger + println no stderr).
     * 
     * @param message Mensagem com placeholders {}
     * @param args Argumentos para os placeholders
     */
    public void error(final String message, final Object... args) {
        logger.error(message, args);
        if (ESPELHAR_NO_CONSOLE) {
            System.err.println(formatMessage(message, args));
        }
    }
    
    /**
     * Log de debug (apenas logger, não vai para console).
     * 
     * @param message Mensagem com placeholders {}
     * @param args Argumentos para os placeholders
     */
    public void debug(final String message, final Object... args) {
        logger.debug(message, args);
    }
    
    /**
     * Log de trace (apenas logger, não vai para console).
     * 
     * @param message Mensagem com placeholders {}
     * @param args Argumentos para os placeholders
     */
    public void trace(final String message, final Object... args) {
        logger.trace(message, args);
    }
    
    /**
     * Log apenas no console (System.out.println), sem logger.
     * Útil para separadores visuais e banners.
     * 
     * @param message Mensagem
     */
    public void console(final String message) {
        System.out.println(message);
    }
    
    /**
     * Log apenas no console (System.out.println) com formatação.
     * 
     * @param message Mensagem com placeholders {}
     * @param args Argumentos
     */
    public void console(final String message, final Object... args) {
        System.out.println(formatMessage(message, args));
    }
    
    /**
     * Retorna o Logger subjacente para uso direto quando necessário.
     * 
     * @return Logger SLF4J
     */
    public Logger getUnderlyingLogger() {
        return logger;
    }
    
    /**
     * Formata mensagem substituindo {} pelos argumentos.
     */
    private String formatMessage(final String message, final Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        
        String result = message;
        for (final Object arg : args) {
            // Ignora throwables no final (padrão SLF4J)
            if (arg instanceof Throwable) {
                continue;
            }
            final int idx = result.indexOf("{}");
            if (idx >= 0) {
                result = result.substring(0, idx) + String.valueOf(arg) + result.substring(idx + 2);
            }
        }
        return result;
    }
    
}
