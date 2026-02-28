package br.com.extrator.comandos.extracao;

import java.util.EnumMap;
import java.util.Map;

import br.com.extrator.comandos.base.Comando;
import br.com.extrator.comandos.extracao.daemon.DaemonHistoryWriter;
import br.com.extrator.comandos.extracao.daemon.DaemonLifecycleService;
import br.com.extrator.comandos.extracao.daemon.DaemonStateStore;
import br.com.extrator.comandos.extracao.daemon.LoopDaemonHandlerSupport;
import br.com.extrator.comandos.extracao.daemon.LoopDaemonModeHandler;
import br.com.extrator.comandos.extracao.daemon.LoopDaemonRunHandler;
import br.com.extrator.comandos.extracao.daemon.LoopDaemonStartHandler;
import br.com.extrator.comandos.extracao.daemon.LoopDaemonStatusHandler;
import br.com.extrator.comandos.extracao.daemon.LoopDaemonStopHandler;

/**
 * Gerencia loop de extracao em segundo plano (daemon).
 */
public class LoopDaemonComando implements Comando {
    public enum Modo {
        START,
        STOP,
        STATUS,
        RUN
    }

    private final Modo modo;
    private final Map<Modo, LoopDaemonModeHandler> handlers;

    public LoopDaemonComando(final Modo modo) {
        this.modo = modo;
        this.handlers = criarHandlersPadrao();
    }

    LoopDaemonComando(final Modo modo,
                      final DaemonStateStore stateStore,
                      final DaemonLifecycleService lifecycleService,
                      final DaemonHistoryWriter historyWriter) {
        this.modo = modo;
        this.handlers = criarHandlers(stateStore, lifecycleService, historyWriter);
    }

    LoopDaemonComando(final Modo modo, final Map<Modo, LoopDaemonModeHandler> handlers) {
        this.modo = modo;
        this.handlers = handlers;
    }

    @Override
    public void executar(final String[] args) throws Exception {
        final boolean incluirFaturasGraphQL = !possuiFlag(args, LoopDaemonHandlerSupport.FLAG_SEM_FATURAS_GRAPHQL);
        final LoopDaemonModeHandler handler = handlers.get(modo);
        if (handler == null) {
            throw new IllegalStateException("Modo de loop daemon nao suportado: " + modo);
        }
        handler.executar(incluirFaturasGraphQL);
    }

    private Map<Modo, LoopDaemonModeHandler> criarHandlersPadrao() {
        final DaemonStateStore stateStore = DaemonStateStore.criarPadrao();
        final DaemonLifecycleService lifecycleService = DaemonLifecycleService.criarPadrao(stateStore);
        final DaemonHistoryWriter historyWriter = DaemonHistoryWriter.criarPadrao();
        return criarHandlers(stateStore, lifecycleService, historyWriter);
    }

    private Map<Modo, LoopDaemonModeHandler> criarHandlers(final DaemonStateStore stateStore,
                                                           final DaemonLifecycleService lifecycleService,
                                                           final DaemonHistoryWriter historyWriter) {
        final Map<Modo, LoopDaemonModeHandler> mapa = new EnumMap<>(Modo.class);
        mapa.put(Modo.START, new LoopDaemonStartHandler(stateStore, lifecycleService, historyWriter));
        mapa.put(Modo.STOP, new LoopDaemonStopHandler(stateStore, lifecycleService, historyWriter));
        mapa.put(Modo.STATUS, new LoopDaemonStatusHandler(stateStore, lifecycleService, historyWriter));
        mapa.put(Modo.RUN, new LoopDaemonRunHandler(stateStore, historyWriter));
        return mapa;
    }

    private boolean possuiFlag(final String[] args, final String flag) {
        if (args == null || flag == null) {
            return false;
        }
        for (final String arg : args) {
            if (arg != null && flag.equalsIgnoreCase(arg.trim())) {
                return true;
            }
        }
        return false;
    }
}
