/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/cli/extracao/ExecutarFluxoCompletoComando.java
Classe  : ExecutarFluxoCompletoComando (public class)
Pacote  : br.com.extrator.comandos.cli.extracao
Modulo  : Comando CLI - Extracao

Papel   : Comando CLI para extracao completa 24h (com pre-backfill, pipeline, validacoes).

Conecta com:
- FluxoCompletoUseCase (aplicacao.extracao) - delegacao
- LoggerConsole (suporte.console)

Fluxo geral:
1) executar(String[] args) detecta flags: --sem-faturas-graphql, --modo-loop-daemon.
2) Delega a FluxoCompletoUseCase.executar(incluirFaturasGraphQL, modoLoopDaemon).

Estrutura interna:
Atributos-chave:
- fluxoCompletoUseCase: delegate para use case.
Metodos principais:
- executar(String[]): flag parser e delegacao.
- possuiFlag(String[], String): helper para deteccao de flags.
[DOC-FILE-END]============================================================== */
package br.com.extrator.comandos.cli.extracao;

import br.com.extrator.aplicacao.extracao.FluxoCompletoUseCase;
import br.com.extrator.comandos.cli.base.Comando;
import br.com.extrator.suporte.console.LoggerConsole;

public class ExecutarFluxoCompletoComando implements Comando {
    private static final LoggerConsole log = LoggerConsole.getLogger(ExecutarFluxoCompletoComando.class);
    private static final String FLAG_SEM_FATURAS_GRAPHQL = "--sem-faturas-graphql";
    private static final String FLAG_MODO_LOOP_DAEMON = "--modo-loop-daemon";

    private final FluxoCompletoUseCase fluxoCompletoUseCase;

    public ExecutarFluxoCompletoComando() {
        this(new FluxoCompletoUseCase());
    }

    ExecutarFluxoCompletoComando(final FluxoCompletoUseCase fluxoCompletoUseCase) {
        this.fluxoCompletoUseCase = fluxoCompletoUseCase;
    }

    @Override
    public void executar(final String[] args) throws Exception {
        final boolean incluirFaturasGraphQL = !possuiFlag(args, FLAG_SEM_FATURAS_GRAPHQL);
        final boolean modoLoopDaemon = possuiFlag(args, FLAG_MODO_LOOP_DAEMON);

        log.debug(
            "Delegando fluxo completo para FluxoCompletoUseCase | incluir_faturas_graphql={} | modo_loop_daemon={}",
            incluirFaturasGraphQL,
            modoLoopDaemon
        );
        fluxoCompletoUseCase.executar(incluirFaturasGraphQL, modoLoopDaemon);
    }

    private boolean possuiFlag(final String[] args, final String flag) {
        if (args == null || flag == null) {
            return false;
        }
        for (final String arg : args) {
            if (flag.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }
}
