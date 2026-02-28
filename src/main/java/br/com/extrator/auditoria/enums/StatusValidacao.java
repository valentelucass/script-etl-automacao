/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/auditoria/enums/StatusValidacao.java
Classe  : StatusValidacao (enum)
Pacote  : br.com.extrator.auditoria.enums
Modulo  : Modulo de auditoria
Papel   : Implementa responsabilidade de status validacao.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela resultados e estado de auditoria.
2) Apoia consolidacao de evidencias operacionais.
3) Suporta emissao de relatorios de conformidade.

Estrutura interna:
Metodos principais:
- StatusValidacao(...2 args): realiza operacao relacionada a "status validacao".
- getDescricao(): expone valor atual do estado interno.
- getIcone(): expone valor atual do estado interno.
- getDescricaoCompleta(): expone valor atual do estado interno.
- isSucesso(): retorna estado booleano de controle.
- temProblema(): verifica presenca/condicao esperada.
- isErroCritico(): retorna estado booleano de controle.
- toString(): realiza operacao relacionada a "to string".
Atributos-chave:
- descricao: campo de estado para "descricao".
- icone: campo de estado para "icone".
[DOC-FILE-END]============================================================== */

package br.com.extrator.auditoria.enums;

/**
 * Enum que representa os possíveis status de validação de uma auditoria.
 */
public enum StatusValidacao {
    /**
     * Validação ainda não foi executada.
     */
    PENDENTE("Pendente", "⏳"),
    
    /**
     * Validação executada com sucesso, dados íntegros.
     */
    OK("OK", "✅"),
    
    /**
     * Validação executada, mas com alertas ou inconsistências menores.
     */
    ALERTA("Alerta", "⚠️"),
    
    /**
     * Erro durante a validação ou problemas críticos encontrados.
     */
    ERRO("Erro", "❌");
    
    private final String descricao;
    private final String icone;
    
    StatusValidacao(final String descricao, final String icone) {
        this.descricao = descricao;
        this.icone = icone;
    }
    
    public String getDescricao() {
        return descricao;
    }
    
    public String getIcone() {
        return icone;
    }
    
    /**
     * Retorna uma representação textual com ícone e descrição.
     * 
     * @return String formatada com ícone e descrição
     */
    public String getDescricaoCompleta() {
        return icone + " " + descricao;
    }
    
    /**
     * Verifica se o status indica sucesso.
     * 
     * @return true se o status for OK, false caso contrário
     */
    public boolean isSucesso() {
        return this == OK;
    }
    
    /**
     * Verifica se o status indica problema (alerta ou erro).
     * 
     * @return true se o status for ALERTA ou ERRO, false caso contrário
     */
    public boolean temProblema() {
        return this == ALERTA || this == ERRO;
    }
    
    /**
     * Verifica se o status indica erro crítico.
     * 
     * @return true se o status for ERRO, false caso contrário
     */
    public boolean isErroCritico() {
        return this == ERRO;
    }
    
    @Override
    public String toString() {
        return getDescricaoCompleta();
    }
}