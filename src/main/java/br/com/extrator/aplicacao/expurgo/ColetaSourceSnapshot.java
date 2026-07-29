package br.com.extrator.aplicacao.expurgo;

import java.util.List;

import br.com.extrator.dominio.graphql.coletas.ColetaNodeDTO;

/** Snapshot completo de Coletas retornado pela GraphQL para uma janela de requestDate. */
public record ColetaSourceSnapshot(
    List<ColetaNodeDTO> records,
    boolean complete,
    int pagesProcessed,
    int rowsRead,
    String interruptionReason
) {
    public ColetaSourceSnapshot {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
