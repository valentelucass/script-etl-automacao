/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/politicas/FailurePolicy.java
Classe  : FailurePolicy (interface)
Pacote  : br.com.extrator.aplicacao.politicas
Modulo  : Politicas - Resiliencia

Papel   : Interface que define comportamento ao falha de um step (retorna FailureMode).

Conecta com:
- FailureMode (tipo de resposta)
- ErrorTaxonomy (condicao de entrada)

Fluxo geral:
1) PipelineOrchestrator consulta politica quando step falha.
2) resolver(entidade, taxonomy) retorna FailureMode.
3) Orquestra aplicaMode para decidir: abortar, continuar com alerta, retry, degradar.

Estrutura interna:
Metodos principais:
- resolver(String entidade, ErrorTaxonomy taxonomy): retorna FailureMode baseado em erro e entidade.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.politicas;

public interface FailurePolicy {
    FailureMode resolver(String entidade, ErrorTaxonomy taxonomy);
}


