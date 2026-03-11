package br.com.extrator.aplicacao.politicas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import br.com.extrator.aplicacao.portas.ClockPort;

class ExponentialBackoffRetryPolicyTest {

    @Test
    void deveExecutarComRetryAteSucesso() throws Exception {
        final FakeClock clock = new FakeClock();
        final ExponentialBackoffRetryPolicy policy = new ExponentialBackoffRetryPolicy(
            3,
            10L,
            2.0d,
            0.0d,
            clock
        );
        final AtomicInteger tentativas = new AtomicInteger();

        final String resultado = policy.executar(() -> {
            if (tentativas.incrementAndGet() < 3) {
                throw new IllegalStateException("falha transiente");
            }
            return "ok";
        }, "op");

        assertEquals("ok", resultado);
        assertEquals(List.of(10L, 20L), clock.sleepsMs);
    }

    @Test
    void deveLancarErroQuandoAtingeLimiteDeTentativas() {
        final FakeClock clock = new FakeClock();
        final ExponentialBackoffRetryPolicy policy = new ExponentialBackoffRetryPolicy(
            2,
            5L,
            2.0d,
            0.0d,
            clock
        );

        assertThrows(IllegalArgumentException.class, () -> policy.executar(() -> {
            throw new IllegalArgumentException("erro permanente");
        }, "op"));
        assertEquals(List.of(5L), clock.sleepsMs);
    }

    private static final class FakeClock implements ClockPort {
        private final List<Long> sleepsMs = new ArrayList<>();

        @Override
        public LocalDate hoje() {
            return LocalDate.now();
        }

        @Override
        public LocalDateTime agora() {
            return LocalDateTime.now();
        }

        @Override
        public void dormir(final Duration duration) {
            sleepsMs.add(duration.toMillis());
        }
    }
}


