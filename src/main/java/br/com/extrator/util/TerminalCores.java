package br.com.extrator.util;

/**
 * Classe utilitária para formatação de texto colorido no terminal
 * Utiliza códigos ANSI para aplicar cores e estilos quando suportado
 */
public class TerminalCores {
    // Verifica se o terminal suporta cores ANSI
    private static final boolean SUPORTA_CORES = false; // Desativado por padrão para evitar problemas de codificação

    // Cores de texto
    public static final String RESET = SUPORTA_CORES ? "\u001B[0m" : "";
    public static final String PRETO = SUPORTA_CORES ? "\u001B[30m" : "";
    public static final String VERMELHO = SUPORTA_CORES ? "\u001B[31m" : "";
    public static final String VERDE = SUPORTA_CORES ? "\u001B[32m" : "";
    public static final String AMARELO = SUPORTA_CORES ? "\u001B[33m" : "";
    public static final String AZUL = SUPORTA_CORES ? "\u001B[34m" : "";
    public static final String ROXO = SUPORTA_CORES ? "\u001B[35m" : "";
    public static final String CIANO = SUPORTA_CORES ? "\u001B[36m" : "";
    public static final String BRANCO = SUPORTA_CORES ? "\u001B[37m" : "";

    // Cores de fundo
    public static final String FUNDO_PRETO = SUPORTA_CORES ? "\u001B[40m" : "";
    public static final String FUNDO_VERMELHO = SUPORTA_CORES ? "\u001B[41m" : "";
    public static final String FUNDO_VERDE = SUPORTA_CORES ? "\u001B[42m" : "";
    public static final String FUNDO_AMARELO = SUPORTA_CORES ? "\u001B[43m" : "";
    public static final String FUNDO_AZUL = SUPORTA_CORES ? "\u001B[44m" : "";
    public static final String FUNDO_ROXO = SUPORTA_CORES ? "\u001B[45m" : "";
    public static final String FUNDO_CIANO = SUPORTA_CORES ? "\u001B[46m" : "";
    public static final String FUNDO_BRANCO = SUPORTA_CORES ? "\u001B[47m" : "";

    // Estilos
    public static final String NEGRITO = SUPORTA_CORES ? "\u001B[1m" : "";
    public static final String SUBLINHADO = SUPORTA_CORES ? "\u001B[4m" : "";
    public static final String PISCANDO = SUPORTA_CORES ? "\u001B[5m" : "";
    public static final String INVERTIDO = SUPORTA_CORES ? "\u001B[7m" : "";

    // Método detectarSuporteCores removido por não ser utilizado

    /**
     * Formata texto para sucesso (verde)
     * 
     * @param texto Texto a ser formatado
     * @return Texto formatado
     */
    public static String sucesso(String texto) {
        return VERDE + texto + RESET;
    }

    /**
     * Formata texto para aviso (amarelo)
     * 
     * @param texto Texto a ser formatado
     * @return Texto formatado
     */
    public static String aviso(String texto) {
        return AMARELO + texto + RESET;
    }

    /**
     * Formata texto para erro (vermelho)
     * 
     * @param texto Texto a ser formatado
     * @return Texto formatado
     */
    public static String erro(String texto) {
        return VERMELHO + texto + RESET;
    }

    /**
     * Formata texto para informação (azul)
     * 
     * @param texto Texto a ser formatado
     * @return Texto formatado
     */
    public static String info(String texto) {
        return AZUL + texto + RESET;
    }

    /**
     * Formata texto para destaque (negrito)
     * 
     * @param texto Texto a ser formatado
     * @return Texto formatado
     */
    public static String destaque(String texto) {
        return NEGRITO + texto + RESET;
    }

    /**
     * Formata texto para título (ciano e negrito)
     * 
     * @param texto Texto a ser formatado
     * @return Texto formatado
     */
    public static String titulo(String texto) {
        return NEGRITO + CIANO + texto + RESET;
    }
}