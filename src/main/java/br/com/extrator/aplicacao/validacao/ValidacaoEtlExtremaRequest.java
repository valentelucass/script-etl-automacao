package br.com.extrator.aplicacao.validacao;

import java.time.LocalDate;
import java.util.Objects;

public record ValidacaoEtlExtremaRequest(
    boolean incluirFaturasGraphQL,
    boolean periodoFechado,
    boolean permitirFallbackJanela,
    int repeticoesStress,
    boolean executarIdempotencia,
    boolean executarHidratacaoOrfaos,
    LocalDate dataReferenciaSistema
) {
    public ValidacaoEtlExtremaRequest {
        Objects.requireNonNull(dataReferenciaSistema, "dataReferenciaSistema");
        repeticoesStress = Math.max(1, repeticoesStress);
    }
}
