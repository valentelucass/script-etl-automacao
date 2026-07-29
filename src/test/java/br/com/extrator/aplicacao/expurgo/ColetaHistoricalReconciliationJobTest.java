package br.com.extrator.aplicacao.expurgo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.extrator.aplicacao.extracao.ExecutionLockManager;
import br.com.extrator.dominio.graphql.coletas.ColetaNodeDTO;

class ColetaHistoricalReconciliationJobTest {

    @Test
    void deveAbortarAntesDePersistirQuandoSnapshotForIncompleto() {
        final FakePersistence persistence = new FakePersistence();
        final FakeStore store = new FakeStore(Set.of("1"));
        final ColetaHistoricalReconciliationJob job = new ColetaHistoricalReconciliationJob(
            (inicio, fim) -> new ColetaSourceSnapshot(List.of(coleta("1")), false, 1, 1, "ERRO_API"),
            persistence,
            store,
            lockManager()
        );

        assertThrows(IllegalStateException.class, () -> job.executar(request(false)));
        assertEquals(0, persistence.calls);
        assertEquals(0, store.registerCalls);
    }

    @Test
    void devePersistirSnapshotCompletoERegistrarApenasAusencias() throws Exception {
        final FakePersistence persistence = new FakePersistence();
        final FakeStore store = new FakeStore(Set.of("1", "2", "3"));
        final ColetaHistoricalReconciliationJob job = new ColetaHistoricalReconciliationJob(
            (inicio, fim) -> new ColetaSourceSnapshot(List.of(coleta("1"), coleta("3")), true, 2, 2, null),
            persistence,
            store,
            lockManager()
        );

        final ColetaHistoricalReconciliationReport report = job.executar(request(false));

        assertEquals(1, persistence.calls);
        assertEquals(List.of("2"), store.lastMissingIds);
        assertEquals(1, report.missingCandidates());
        assertEquals(1, report.logicallyExcluded());
    }

    @Test
    void dryRunNaoPersisteNemConfirmaAusencias() throws Exception {
        final FakePersistence persistence = new FakePersistence();
        final FakeStore store = new FakeStore(Set.of("1", "2"));
        final ColetaHistoricalReconciliationJob job = new ColetaHistoricalReconciliationJob(
            (inicio, fim) -> new ColetaSourceSnapshot(List.of(coleta("1")), true, 1, 1, null),
            persistence,
            store,
            lockManager()
        );

        final ColetaHistoricalReconciliationReport report = job.executar(request(true));

        assertEquals(0, persistence.calls);
        assertEquals(0, store.registerCalls);
        assertEquals(1, report.missingCandidates());
    }

    private static ColetaHistoricalReconciliationRequest request(final boolean dryRun) {
        return new ColetaHistoricalReconciliationRequest(
            LocalDate.of(2026, 4, 29),
            LocalDate.of(2026, 7, 28),
            dryRun,
            500,
            2
        );
    }

    private static ColetaNodeDTO coleta(final String id) {
        final ColetaNodeDTO dto = new ColetaNodeDTO();
        dto.setId(id);
        return dto;
    }

    private static ExecutionLockManager lockManager() {
        return ignored -> () -> {
        };
    }

    private static final class FakePersistence implements ColetaSnapshotPersistencePort {
        private int calls;

        @Override
        public int persistir(final List<ColetaNodeDTO> records) {
            calls++;
            return records.size();
        }
    }

    private static final class FakeStore implements ColetaReconciliationStore {
        private final Set<String> activeIds;
        private int registerCalls;
        private List<String> lastMissingIds = List.of();

        private FakeStore(final Set<String> activeIds) {
            this.activeIds = activeIds;
        }

        @Override
        public Set<String> buscarIdsAtivos(final LocalDate dataInicio, final LocalDate dataFim) {
            return activeIds;
        }

        @Override
        public ColetaMissingRegistration registrarAusencias(final List<String> idsAusentes,
                                                            final String runId,
                                                            final int confirmacoesNecessarias,
                                                            final int batchSize) {
            registerCalls++;
            lastMissingIds = idsAusentes;
            return new ColetaMissingRegistration(idsAusentes.size(), idsAusentes.size());
        }
    }
}
