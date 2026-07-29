package br.com.extrator.aplicacao.expurgo;

import java.sql.SQLException;
import java.util.List;

import br.com.extrator.dominio.graphql.coletas.ColetaNodeDTO;

public interface ColetaSnapshotPersistencePort {
    int persistir(List<ColetaNodeDTO> records) throws SQLException;
}
