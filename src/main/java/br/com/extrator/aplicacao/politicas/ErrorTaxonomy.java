/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/politicas/ErrorTaxonomy.java
Classe  : ErrorTaxonomy (enum)
Pacote  : br.com.extrator.aplicacao.politicas
Modulo  : Politicas - Resiliencia

Papel   : Enum que classifica tipos de erros para decisoes de retry e fallback.

Conecta com:
- ErrorClassifier (produz taxonomia)
- RetryPolicy, FailurePolicy (consomem para decidir comportamento)

Fluxo geral:
1) ErrorClassifier.classificar() retorna ErrorTaxonomy.
2) Retry/failure policies consultam tipo para decidir comportamento.
3) TRANSIENT => retry faz sentido; PERMANENT => falhar rapido.

Estrutura interna:
Valores:
- TRANSIENT_API_ERROR: timeout, conexao, erro transiente (retry).
- PERMANENT_VALIDATION_ERROR: erro de validacao (nao retry).
- DB_CONFLICT: conflito BD (retry com idempotencia).
- DATA_QUALITY_BREACH: violacao qualidade (critico).
- TIMEOUT: timeout API (retry com backoff).
- SCHEMA_DRIFT: desvio schema (falha definitiva).
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.politicas;

public enum ErrorTaxonomy {
    TRANSIENT_API_ERROR,
    PERMANENT_VALIDATION_ERROR,
    DB_CONFLICT,
    DATA_QUALITY_BREACH,
    TIMEOUT,
    SCHEMA_DRIFT
}

