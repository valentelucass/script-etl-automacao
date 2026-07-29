package br.com.extrator.aplicacao.expurgo;

import java.time.Duration;
import java.time.LocalDate;

public record ColetaHistoricalReconciliationReport(
    String runId,
    LocalDate dataInicio,
    LocalDate dataFim,
    boolean dryRun,
    int sourceRows,
    int sourceKeys,
    int dbActiveKeys,
    int recordsPersisted,
    int missingCandidates,
    int logicallyExcluded,
    int pagesProcessed,
    Duration duration
) {
}
