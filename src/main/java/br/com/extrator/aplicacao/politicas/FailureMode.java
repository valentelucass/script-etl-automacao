/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/politicas/FailureMode.java
Classe  : FailureMode (enum)
Pacote  : br.com.extrator.aplicacao.politicas
Modulo  : Politicas - Resiliencia

Papel   : Enum que especifica comportamento quando um step falha (abortar, continuar com alerta, retry, degradar).

Conecta com:
- FailurePolicy (usa modes)
- PipelineOrchestrator (interpreta mode)

Fluxo geral:
1) FailurePolicy.resolver() retorna FailureMode baseado em erro e entidade.
2) Orquestra aplicaMode para decidir proximo passo.
3) ABORT_PIPELINE: para tudo; CONTINUE_WITH_ALERT: aviso; RETRY: tenta novamente; DEGRADE: continua degradado.

Estrutura interna:
Valores:
- ABORT_PIPELINE: para pipeline imediatamente (falha critica).
- CONTINUE_WITH_ALERT: continua mas registra alerta (aviso, nao fatal).
- RETRY: tenta executar novamente (erro transiente).
- DEGRADE: continua mas marca resultado como degradado (partial success).
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.politicas;

public enum FailureMode {
    ABORT_PIPELINE,
    CONTINUE_WITH_ALERT,
    RETRY,
    DEGRADE
}

