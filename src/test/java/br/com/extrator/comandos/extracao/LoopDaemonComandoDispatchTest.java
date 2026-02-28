package br.com.extrator.comandos.extracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.extrator.comandos.extracao.LoopDaemonComando.Modo;
import br.com.extrator.comandos.extracao.daemon.LoopDaemonModeHandler;

class LoopDaemonComandoDispatchTest {

    @Test
    void deveRoteiarModoStartComFlagSemFaturas() throws Exception {
        final RegistroHandlers registro = new RegistroHandlers();
        final LoopDaemonComando comando = new LoopDaemonComando(Modo.START, registro.handlers);

        comando.executar(new String[] {"--loop-daemon-start", "--sem-faturas-graphql"});

        assertEquals(1, registro.startChamadas);
        assertFalse(registro.startIncluiuFaturasGraphQL);
        assertEquals(0, registro.runChamadas);
    }

    @Test
    void deveRoteiarModoRunComFaturasPorPadrao() throws Exception {
        final RegistroHandlers registro = new RegistroHandlers();
        final LoopDaemonComando comando = new LoopDaemonComando(Modo.RUN, registro.handlers);

        comando.executar(new String[] {"--loop-daemon-run"});

        assertEquals(1, registro.runChamadas);
        assertTrue(registro.runIncluiuFaturasGraphQL);
        assertEquals(0, registro.startChamadas);
    }

    private static final class RegistroHandlers {
        private int startChamadas;
        private int stopChamadas;
        private int statusChamadas;
        private int runChamadas;
        private boolean startIncluiuFaturasGraphQL;
        private boolean runIncluiuFaturasGraphQL;

        private final Map<Modo, LoopDaemonModeHandler> handlers;

        private RegistroHandlers() {
            final Map<Modo, LoopDaemonModeHandler> mapa = new EnumMap<>(Modo.class);
            mapa.put(Modo.START, incluirFaturasGraphQL -> {
                startChamadas++;
                startIncluiuFaturasGraphQL = incluirFaturasGraphQL;
            });
            mapa.put(Modo.STOP, incluirFaturasGraphQL -> stopChamadas++);
            mapa.put(Modo.STATUS, incluirFaturasGraphQL -> statusChamadas++);
            mapa.put(Modo.RUN, incluirFaturasGraphQL -> {
                runChamadas++;
                runIncluiuFaturasGraphQL = incluirFaturasGraphQL;
            });
            this.handlers = mapa;
        }
    }
}
