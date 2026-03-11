/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/extracao/LoopDaemonComando.java
Classe  : LoopDaemonComando (class)
Pacote  : br.com.extrator.comandos.cli.extracao
Modulo  : Comando CLI (daemon)
Papel   : Faz dispatch do modo de operacao do loop daemon.

Conecta com:
- Comando (comandos.base)
- LoopDaemonModeHandler (comandos.extracao.daemon)
- DaemonStateStore (comandos.extracao.daemon)
- DaemonLifecycleService (comandos.extracao.daemon)
- DaemonHistoryWriter (comandos.extracao.daemon)

Fluxo geral:
1) Resolve modo solicitado (start/stop/status/run).
2) Inicializa handlers padrao quando necessario.
3) Encaminha execucao para o handler correspondente.

Estrutura interna:
Metodos principais:
- executar(...1 args): dispara modo selecionado.
- criarHandlersPadrao(): cria dependencias para handlers.
- criarHandlers(...3 args): monta mapa de handlers por modo.
Atributos-chave:
- modo: modo atual de operacao.
- handlers: mapa de modo para handler especializado.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.cli.extracao;

import java.util.EnumMap;
import java.util.Map;

import br.com.extrator.comandos.cli.base.Comando;
import br.com.extrator.comandos.cli.extracao.daemon.DaemonHistoryWriter;
import br.com.extrator.comandos.cli.extracao.daemon.DaemonLifecycleService;
import br.com.extrator.comandos.cli.extracao.daemon.DaemonStateStore;
import br.com.extrator.comandos.cli.extracao.daemon.LoopDaemonHandlerSupport;
import br.com.extrator.comandos.cli.extracao.daemon.LoopDaemonModeHandler;
import br.com.extrator.comandos.cli.extracao.daemon.LoopDaemonRunHandler;
import br.com.extrator.comandos.cli.extracao.daemon.LoopDaemonStartHandler;
import br.com.extrator.comandos.cli.extracao.daemon.LoopDaemonStatusHandler;
import br.com.extrator.comandos.cli.extracao.daemon.LoopDaemonStopHandler;

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
