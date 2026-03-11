/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/validacao/ValidacaoApiBanco24hRequest.java
Classe  : ValidacaoApiBanco24hRequest (record)
Pacote  : br.com.extrator.aplicacao.validacao
Modulo  : Use Case - Validacao

Papel   : Request DTO simplificado para validacao 24h (sem modo fechado, com fallback).

Conecta com:
- Nenhuma (DTO puro)

Fluxo geral:
1) Record com 3 campos: incluirFaturasGraphQL, permitirFallbackJanela, dataReferenciaSistema.
2) Compact constructor valida dataReferenciaSistema nao-null.

Estrutura interna:
Campos:
- incluirFaturasGraphQL: boolean (incluir FATURAS_GRAPHQL).
- permitirFallbackJanela: boolean (permitir fallback sem filtro periodo).
- dataReferenciaSistema: LocalDate (data de referencia).
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.validacao;

import java.time.LocalDate;
import java.util.Objects;

public record ValidacaoApiBanco24hRequest(
    boolean incluirFaturasGraphQL,
    boolean permitirFallbackJanela,
    LocalDate dataReferenciaSistema
) {
    public ValidacaoApiBanco24hRequest {
        Objects.requireNonNull(dataReferenciaSistema, "dataReferenciaSistema");
    }
}
