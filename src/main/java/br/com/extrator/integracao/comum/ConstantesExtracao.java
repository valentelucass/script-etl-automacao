/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/common/ConstantesExtracao.java
Classe  : ConstantesExtracao (class)
Pacote  : br.com.extrator.integracao.comum
Modulo  : Componente compartilhado de extracao
Papel   : Implementa responsabilidade de constantes extracao.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Disponibiliza contratos e utilitarios transversais.
2) Padroniza resultado, log e comportamento comum.
3) Reduz duplicacao entre GraphQL e DataExport.

Estrutura interna:
Metodos principais:
- ConstantesExtracao(): realiza operacao relacionada a "constantes extracao".
Atributos-chave:
- EMOJI_COLETAS: campo de estado para "emoji coletas".
- EMOJI_FRETES: campo de estado para "emoji fretes".
- EMOJI_FATURAS: campo de estado para "emoji faturas".
- EMOJI_COTACOES: campo de estado para "emoji cotacoes".
- EMOJI_LOCALIZACAO: campo de estado para "emoji localizacao".
- EMOJI_CONTAS_PAGAR: campo de estado para "emoji contas pagar".
- EMOJI_FATURAS_CLIENTE: campo de estado para "emoji faturas cliente".
- EMOJI_USUARIOS: campo de estado para "emoji usuarios".
- MSG_ERRO_EXTRACAO: campo de estado para "msg erro extracao".
- MSG_ERRO_DELAY_INTERROMPIDO: campo de estado para "msg erro delay interrompido".
- MSG_LOG_EXTRAINDO: campo de estado para "msg log extraindo".
- MSG_LOG_EXTRAINDO_COM_MOTIVO: campo de estado para "msg log extraindo com motivo".
- MSG_LOG_EXTRAIDOS: campo de estado para "msg log extraidos".
- MSG_LOG_PROCESSADOS: campo de estado para "msg log processados".
[DOC-FILE-END]============================================================== */

package br.com.extrator.integracao.comum;

/**
 * Constantes centralizadas para extrações.
 * Evita duplicação de strings e valores mágicos nos runners e extractors.
 * 
 * @author Sistema de Extração ESL Cloud
 * @version 1.0
 */
public final class ConstantesExtracao {
    
    private ConstantesExtracao() {
        // Impede instanciação
    }
    
    // ========== EMOJIS POR ENTIDADE ==========
    /** Emoji para Coletas */
    public static final String EMOJI_COLETAS = "📦";
    
    /** Emoji para Fretes */
    public static final String EMOJI_FRETES = "🚛";
    
    /** Emoji para Faturas (GraphQL e Manifestos) */
    public static final String EMOJI_FATURAS = "🧾";
    
    /** Emoji para Cotações */
    public static final String EMOJI_COTACOES = "💹";
    
    /** Emoji para Localização de Cargas */
    public static final String EMOJI_LOCALIZACAO = "📍";
    
    /** Emoji para Contas a Pagar */
    public static final String EMOJI_CONTAS_PAGAR = "💰";
    
    /** Emoji para Faturas por Cliente */
    public static final String EMOJI_FATURAS_CLIENTE = "💸";
    
    /** Emoji para Usuários do Sistema */
    public static final String EMOJI_USUARIOS = "👥";
    
    // ========== MENSAGENS DE ERRO ==========
    /** Template para mensagem de erro de extração */
    public static final String MSG_ERRO_EXTRACAO = "Falha na extração de %s";
    
    /** Mensagem de erro quando delay é interrompido */
    public static final String MSG_ERRO_DELAY_INTERROMPIDO = "Delay interrompido";
    
    // ========== MENSAGENS DE LOG ==========
    /** Template para log de extração iniciada */
    public static final String MSG_LOG_EXTRAINDO = "\n{} Extraindo {}...";
    
    /** Template para log de extração com motivo */
    public static final String MSG_LOG_EXTRAINDO_COM_MOTIVO = "\n{} Extraindo {}{}...";
    
    /** Template para log de registros extraídos */
    public static final String MSG_LOG_EXTRAIDOS = "✓ Extraídos: {} {}{}";
    
    /** Template para log de registros processados (DataExport) */
    public static final String MSG_LOG_PROCESSADOS = "✓ Processados: {}/{} {} (INSERTs + UPDATEs)";
    
    /** Template para log de registros salvos (GraphQL) */
    public static final String MSG_LOG_SALVOS = "✓ Salvos: {}/{} {}";
    
    /** Template para log de duplicados removidos */
    public static final String MSG_LOG_DUPLICADOS_REMOVIDOS = "⚠️ Removidos {} duplicados da resposta da API antes de salvar";
    
    /** Mensagem para usuários do sistema (quando necessário para enriquecer coletas) */
    public static final String MSG_MOTIVO_USUARIOS_COLETAS = " (necessário para enriquecer coletas)";
    
    /** Mensagem para extração de últimas 24h */
    public static final String MSG_ULTIMAS_24H = " (últimas 24h)";
}
