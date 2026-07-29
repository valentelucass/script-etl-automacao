package br.com.extrator.integracao;

import java.time.LocalDate;

import br.com.extrator.aplicacao.expurgo.ColetaSourceSnapshot;
import br.com.extrator.aplicacao.expurgo.ColetaSourceSnapshotPort;
import br.com.extrator.dominio.graphql.coletas.ColetaNodeDTO;

/** Adaptador de snapshot GraphQL; não persiste nem permite exclusão por conta própria. */
public final class GraphQLColetaKeySnapshotClient implements ColetaSourceSnapshotPort {
    private final ClienteApiGraphQL client;

    public GraphQLColetaKeySnapshotClient() {
        this(new ClienteApiGraphQL());
    }

    GraphQLColetaKeySnapshotClient(final ClienteApiGraphQL client) {
        this.client = client;
    }

    @Override
    public ColetaSourceSnapshot carregar(final LocalDate dataInicio, final LocalDate dataFim) {
        final ResultadoExtracao<ColetaNodeDTO> result = client.buscarColetas(dataInicio, dataFim);
        return new ColetaSourceSnapshot(
            result.getDados(),
            result.isCompleto(),
            result.getPaginasProcessadas(),
            result.getRegistrosExtraidos(),
            result.getMotivoInterrupcao()
        );
    }
}
