/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/politicas/CircuitBreaker.java
Classe  : CircuitBreaker (class)
Pacote  : br.com.extrator.aplicacao.politicas
Modulo  : Politicas - Resiliencia

Papel   : Implementa padrao Circuit Breaker (CLOSED/OPEN/HALF_OPEN) para controlar acesso a recursos frágeis.

Conecta com:
- ClockPort (obtem tempo atual)
- State enum (CLOSED, OPEN, HALF_OPEN)

Fluxo geral:
1) Estado inicia CLOSED (permitir requisicoes).
2) permite() retorna true se CLOSED ou HALF_OPEN (retry).
3) registrarFalha() incrementa contador; se >= threshold, abre (OPEN).
4) Em OPEN, rejeita ate passar openDuration, entao tenta HALF_OPEN.
5) registrarSucesso() resetar contador e fecha (volta CLOSED).

Estrutura interna:
Enum State:
- CLOSED: operacao normal.
- OPEN: rejeitando (falhas detectadas).
- HALF_OPEN: testando se servico recuperou.
Inner class InternalState:
- failureCount, state, openedAt (timestamp quando abriu).
Metodos principais:
- permite(key): verifica se operacao e permitida.
- registrarFalha(key): incrementa contador, abre se atinge threshold.
- registrarSucesso(key): reseta contador, fecha circuit.
- estadoDe(key): retorna estado atual.
Atributos-chave:
- failureThreshold: quantas falhas ate abrir.
- openDuration: quanto tempo fico aberto antes testar HALF_OPEN.
- states: Map<String, InternalState> (por-chave/recurso).
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.politicas;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import br.com.extrator.aplicacao.portas.ClockPort;

public class CircuitBreaker {
    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final Duration openDuration;
    private final ClockPort clock;
    private final Map<String, InternalState> states = new ConcurrentHashMap<>();

    public CircuitBreaker(final int failureThreshold, final Duration openDuration, final ClockPort clock) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDuration = openDuration == null ? Duration.ofSeconds(60) : openDuration;
        this.clock = clock;
    }

    public boolean permite(final String key) {
        final InternalState state = states.computeIfAbsent(normalize(key), ignored -> new InternalState());
        if (state.state == State.CLOSED) {
            return true;
        }
        if (state.state == State.OPEN) {
            final LocalDateTime agora = clock.agora();
            if (state.openedAt == null || Duration.between(state.openedAt, agora).compareTo(openDuration) >= 0) {
                state.state = State.HALF_OPEN;
                return true;
            }
            return false;
        }
        return true;
    }

    public void registrarSucesso(final String key) {
        final InternalState state = states.computeIfAbsent(normalize(key), ignored -> new InternalState());
        state.failureCount = 0;
        state.state = State.CLOSED;
        state.openedAt = null;
    }

    public void registrarFalha(final String key) {
        final InternalState state = states.computeIfAbsent(normalize(key), ignored -> new InternalState());
        state.failureCount++;
        if (state.failureCount >= failureThreshold) {
            state.state = State.OPEN;
            state.openedAt = clock.agora();
        }
    }

    public State estadoDe(final String key) {
        return states.getOrDefault(normalize(key), new InternalState()).state;
    }

    private String normalize(final String key) {
        return key == null ? "" : key.trim().toLowerCase();
    }

    private static final class InternalState {
        private int failureCount = 0;
        private LocalDateTime openedAt;
        private State state = State.CLOSED;
    }
}



