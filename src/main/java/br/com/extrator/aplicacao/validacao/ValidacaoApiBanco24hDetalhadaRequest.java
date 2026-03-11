/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/validacao/ValidacaoApiBanco24hDetalhadaRequest.java
Classe  : ValidacaoApiBanco24hDetalhadaRequest (record)
Pacote  : br.com.extrator.aplicacao.validacao
Modulo  : Use Case - Validacao

Papel   : Request DTO para validacao API vs Banco 24h (flags de contexto e data).

Conecta com:
- Nenhuma (DTO puro)

Fluxo geral:
1) Record imutavel com 4 campos: incluirFaturasGraphQL, periodoFechado, permitirFallbackJanela, dataReferenciaSistema.
2) Compact constructor valida dataReferenciaSistema nao-null.

Estrutura interna:
Campos:
- incluirFaturasGraphQL: boolean (incluir entidade FATURAS_GRAPHQL).
- periodoFechado: boolean (modo fechado para comparacao em periodos).
- permitirFallbackJanela: boolean (permitir fallback sem filtro periodo em log_extracoes).
- dataReferenciaSistema: LocalDate (data de referencia para buscas).
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.validacao;

import java.time.LocalDate;
import java.util.Objects;

public record ValidacaoApiBanco24hDetalhadaRequest(
    boolean incluirFaturasGraphQL,
    boolean periodoFechado,
    boolean permitirFallbackJanela,
    LocalDate dataReferenciaSistema
) {
    public ValidacaoApiBanco24hDetalhadaRequest {
        Objects.requireNonNull(dataReferenciaSistema, "dataReferenciaSistema");
    }
}
