/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/auditoria/enums/StatusAuditoria.java
Classe  : StatusAuditoria (enum)
Pacote  : br.com.extrator.observabilidade.enums
Modulo  : Modulo de auditoria
Papel   : Implementa responsabilidade de status auditoria.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela resultados e estado de auditoria.
2) Apoia consolidacao de evidencias operacionais.
3) Suporta emissao de relatorios de conformidade.

Estrutura interna:
Metodos principais:
- StatusAuditoria(...2 args): realiza operacao relacionada a "status auditoria".
- getDescricao(): expone valor atual do estado interno.
- getIcone(): expone valor atual do estado interno.
- getStatusFormatado(): expone valor atual do estado interno.
- toString(): realiza operacao relacionada a "to string".
Atributos-chave:
- descricao: campo de estado para "descricao".
- icone: campo de estado para "icone".
[DOC-FILE-END]============================================================== */

package br.com.extrator.observabilidade.enums;

/**
 * Enum que representa os possíveis status de uma auditoria.
 * Define os estados pelos quais uma auditoria pode passar durante sua execução.
 */
public enum StatusAuditoria {
    /**
     * Auditoria ainda não foi iniciada ou está aguardando execução
     */
    PENDENTE("Auditoria pendente", "⏳"),
    
    /**
     * Auditoria está sendo executada no momento
     */
    EM_ANDAMENTO("Auditoria em andamento", "🔄"),
    
    /**
     * Auditoria foi concluída com sucesso
     */
    CONCLUIDA("Auditoria concluída com sucesso", "✅"),
    
    /**
     * Auditoria falhou durante a execução
     */
    FALHA("Auditoria falhou", "❌"),
    
    /**
     * Auditoria foi concluída mas com alertas
     */
    CONCLUIDA_COM_ALERTAS("Auditoria concluída com alertas", "⚠️"),
    
    /**
     * Erro durante auditoria
     */
    ERRO("Erro durante auditoria", "🚨");

    private final String descricao;
    private final String icone;

    /**
     * Construtor do enum
     * @param descricao Descrição textual do status
     * @param icone Ícone representativo do status
     */
    StatusAuditoria(String descricao, String icone) {
        this.descricao = descricao;
        this.icone = icone;
    }

    /**
     * Retorna a descrição textual do status
     * @return Descrição do status
     */
    public String getDescricao() {
        return descricao;
    }

    /**
     * Retorna o ícone representativo do status
     * @return Ícone do status
     */
    public String getIcone() {
        return icone;
    }

    /**
     * Retorna uma representação formatada do status com ícone e descrição
     * @return Status formatado
     */
    public String getStatusFormatado() {
        return icone + " " + descricao;
    }

    @Override
    public String toString() {
        return getStatusFormatado();
    }
}