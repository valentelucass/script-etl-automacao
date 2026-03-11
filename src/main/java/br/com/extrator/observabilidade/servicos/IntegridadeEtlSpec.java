package br.com.extrator.observabilidade.servicos;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/servicos/IntegridadeEtlSpec.java
Classe  :  (class)
Pacote  : br.com.extrator.observabilidade.servicos
Modulo  : Observabilidade - Servico
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.util.List;

record IntegridadeEtlSpec(
    String entidade,
    String tabela,
    String colunaTimestamp,
    List<String> chavesUnicas,
    List<String> colunasObrigatorias
) {
    IntegridadeEtlSpec {
        chavesUnicas = List.copyOf(chavesUnicas);
        colunasObrigatorias = List.copyOf(colunasObrigatorias);
    }
}
