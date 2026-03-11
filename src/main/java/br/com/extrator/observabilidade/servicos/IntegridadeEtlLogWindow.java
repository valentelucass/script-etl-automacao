package br.com.extrator.observabilidade.servicos;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/servicos/IntegridadeEtlLogWindow.java
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


import java.time.LocalDateTime;

record IntegridadeEtlLogWindow(
    String status,
    int registrosExtraidos,
    LocalDateTime inicio,
    LocalDateTime fim,
    String mensagem
) {
}
