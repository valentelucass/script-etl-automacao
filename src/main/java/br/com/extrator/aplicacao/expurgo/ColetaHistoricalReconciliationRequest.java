package br.com.extrator.aplicacao.expurgo;

import java.time.LocalDate;
import java.util.Objects;

public record ColetaHistoricalReconciliationRequest(
    LocalDate dataInicio,
    LocalDate dataFim,
    boolean dryRun,
    int batchSize,
    int confirmacoesAusenciaNecessarias
) {
    public ColetaHistoricalReconciliationRequest {
        dataInicio = Objects.requireNonNull(dataInicio, "dataInicio");
        dataFim = Objects.requireNonNull(dataFim, "dataFim");
        if (dataFim.isBefore(dataInicio)) {
            throw new IllegalArgumentException("dataFim nao pode ser anterior a dataInicio");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize deve ser maior que zero");
        }
        if (confirmacoesAusenciaNecessarias < 2) {
            throw new IllegalArgumentException("confirmacoesAusenciaNecessarias deve ser no minimo 2");
        }
    }
}
