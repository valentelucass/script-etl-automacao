/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/extracao/ExtracaoPorIntervaloRequest.java
Classe  : ExtracaoPorIntervaloRequest (record)
Pacote  : br.com.extrator.aplicacao.extracao
Modulo  : Use Case - Extracao

Papel   : Requisicao para execucao de extracao em um intervalo de datas especifico.

Conecta com:
- ExtracaoPorIntervaloUseCase (consume)

Fluxo geral:
1) Cliente monta request com datas inicio/fim e filtros opcionais (API, entidade).
2) Compact constructor valida e normaliza campos (null-check, trim).
3) Use case recebe request e a decompoe em blocos de extracao.

Estrutura interna:
Campos:
- dataInicio, dataFim: LocalDate (obrigatorios, validados).
- apiEspecifica: String (opcional, normalizado para null se blank).
- entidadeEspecifica: String (opcional, normalizado para null se blank).
- incluirFaturasGraphQL, modoLoopDaemon: boolean (flags de comportamento).
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.extracao;

import java.time.LocalDate;
import java.util.Objects;

public record ExtracaoPorIntervaloRequest(
    LocalDate dataInicio,
    LocalDate dataFim,
    String apiEspecifica,
    String entidadeEspecifica,
    boolean incluirFaturasGraphQL,
    boolean modoLoopDaemon
) {
    public ExtracaoPorIntervaloRequest {
        Objects.requireNonNull(dataInicio, "dataInicio nao pode ser null");
        Objects.requireNonNull(dataFim, "dataFim nao pode ser null");
        apiEspecifica = normalizar(apiEspecifica);
        entidadeEspecifica = normalizar(entidadeEspecifica);
    }

    private static String normalizar(final String valor) {
        if (valor == null) {
            return null;
        }
        final String limpo = valor.trim();
        return limpo.isEmpty() ? null : limpo;
    }
}
