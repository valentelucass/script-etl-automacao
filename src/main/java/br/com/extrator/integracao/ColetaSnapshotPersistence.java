package br.com.extrator.integracao;

import java.sql.SQLException;
import java.util.List;

import br.com.extrator.aplicacao.expurgo.ColetaSnapshotPersistencePort;
import br.com.extrator.dominio.graphql.coletas.ColetaNodeDTO;
import br.com.extrator.integracao.graphql.extractors.ColetaExtractor;
import br.com.extrator.integracao.mapeamento.graphql.coletas.ColetaMapper;
import br.com.extrator.persistencia.repositorio.ColetaRepository;

/** Reusa o mesmo caminho de persistência da carga operacional na reconciliação histórica. */
public final class ColetaSnapshotPersistence implements ColetaSnapshotPersistencePort {
    private final ColetaExtractor extractor;

    public ColetaSnapshotPersistence() {
        this(new ColetaExtractor(new ClienteApiGraphQL(), new ColetaRepository(), new ColetaMapper()));
    }

    ColetaSnapshotPersistence(final ColetaExtractor extractor) {
        this.extractor = extractor;
    }

    @Override
    public int persistir(final List<ColetaNodeDTO> records) throws SQLException {
        return extractor.save(records);
    }
}
