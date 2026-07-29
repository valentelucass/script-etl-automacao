package br.com.extrator.aplicacao.expurgo;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface ColetaReconciliationStore {
    Set<String> buscarIdsAtivos(LocalDate dataInicio, LocalDate dataFim) throws SQLException;

    ColetaMissingRegistration registrarAusencias(
        List<String> idsAusentes,
        String runId,
        int confirmacoesNecessarias,
        int batchSize
    ) throws SQLException;
}
