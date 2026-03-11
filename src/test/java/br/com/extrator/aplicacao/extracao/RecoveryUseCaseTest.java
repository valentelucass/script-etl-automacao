package br.com.extrator.aplicacao.extracao;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class RecoveryUseCaseTest {

    @Test
    void deveFalharQuandoPeriodoInvalido() {
        final RecoveryUseCase useCase = new RecoveryUseCase();
        assertThrows(
            IllegalArgumentException.class,
            () -> useCase.executarReplay(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1), null, null, true)
        );
    }
}
