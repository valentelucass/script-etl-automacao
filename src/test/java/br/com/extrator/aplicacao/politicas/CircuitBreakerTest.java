package br.com.extrator.aplicacao.politicas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import br.com.extrator.aplicacao.portas.ClockPort;

class CircuitBreakerTest {

    @Test
    void deveAbrirCircuitoEAceitarAposJanela() {
        final MutableClock clock = new MutableClock(LocalDateTime.of(2026, 1, 1, 10, 0));
        final CircuitBreaker breaker = new CircuitBreaker(2, Duration.ofSeconds(60), clock);

        assertTrue(breaker.permite("graphql"));
        breaker.registrarFalha("graphql");
        assertEquals(CircuitBreaker.State.CLOSED, breaker.estadoDe("graphql"));

        breaker.registrarFalha("graphql");
        assertEquals(CircuitBreaker.State.OPEN, breaker.estadoDe("graphql"));
        assertFalse(breaker.permite("graphql"));

        clock.advanceSeconds(61);
        assertTrue(breaker.permite("graphql"));
        assertEquals(CircuitBreaker.State.HALF_OPEN, breaker.estadoDe("graphql"));

        breaker.registrarSucesso("graphql");
        assertEquals(CircuitBreaker.State.CLOSED, breaker.estadoDe("graphql"));
    }

    private static final class MutableClock implements ClockPort {
        private LocalDateTime current;

        private MutableClock(final LocalDateTime initial) {
            this.current = initial;
        }

        private void advanceSeconds(final long seconds) {
            current = current.plusSeconds(seconds);
        }

        @Override
        public LocalDate hoje() {
            return current.toLocalDate();
        }

        @Override
        public LocalDateTime agora() {
            return current;
        }

        @Override
        public void dormir(final Duration duration) {
            current = current.plus(duration);
        }
    }
}


