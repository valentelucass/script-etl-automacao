package br.com.extrator.aplicacao.extracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ExecutionLockUseCasesTest {

    @Test
    void fluxoCompletoDeveAdquirirLockAntesDeExecutar() {
        final RecordingLockManager lockManager = new RecordingLockManager();
        final FluxoCompletoUseCase useCase = new FluxoCompletoUseCase(new PreBackfillReferencialColetasUseCase(), lockManager);

        assertThrows(Exception.class, () -> useCase.executar(false, false));
        assertEquals(1, lockManager.acquireCount);
    }

    @Test
    void extracaoPorIntervaloDeveAdquirirLockAntesDeExecutar() {
        final RecordingLockManager lockManager = new RecordingLockManager();
        final ExtracaoPorIntervaloUseCase useCase = new ExtracaoPorIntervaloUseCase(
            new PreBackfillReferencialColetasUseCase(),
            new PlanejadorEscopoExtracaoIntervalo(),
            lockManager
        );

        final ExtracaoPorIntervaloRequest request = new ExtracaoPorIntervaloRequest(
            LocalDate.of(2026, 3, 9),
            LocalDate.of(2026, 3, 9),
            null,
            null,
            true,
            false
        );
        assertThrows(Exception.class, () -> useCase.executar(request));
        assertEquals(1, lockManager.acquireCount);
    }

    @Test
    void testeApiDeveAdquirirLockAntesDeExecutar() {
        final RecordingLockManager lockManager = new RecordingLockManager();
        final TesteApiUseCase useCase = new TesteApiUseCase(lockManager);

        assertThrows(Exception.class, () -> useCase.executar(new TesteApiRequest("graphql", null, true)));
        assertEquals(1, lockManager.acquireCount);
    }

    private static final class RecordingLockManager implements ExecutionLockManager {
        private int acquireCount;

        @Override
        public AutoCloseable acquire(final String resourceName) {
            acquireCount++;
            return () -> { };
        }
    }
}
