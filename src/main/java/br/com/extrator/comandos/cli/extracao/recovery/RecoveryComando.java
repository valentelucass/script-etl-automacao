/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/cli/extracao/recovery/RecoveryComando.java
Classe  : RecoveryComando (command)
Pacote  : br.com.extrator.comandos.cli.extracao.recovery
Modulo  : CLI - Recovery
Papel   : Comando para replay de extração em janela de datas com flags de API e entidade.
Conecta com:
- br.com.extrator.aplicacao.extracao.RecoveryUseCase
- br.com.extrator.comandos.cli.base.Comando
- br.com.extrator.suporte.console.LoggerConsole
Fluxo geral:
1) executar() valida args (data início/fim obrigatórias em ISO_DATE)
2) Parse de flags: --api [graphql|dataexport], --entidade, --sem-faturas-graphql
3) Delegação a RecoveryUseCase.executarReplay()
Estrutura interna:
Atributos: log [static], DATE_FORMAT [static]
Metodos: executar(), imprimirUso()
[DOC-FILE-END]============================================================== */
package br.com.extrator.comandos.cli.extracao.recovery;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import br.com.extrator.aplicacao.extracao.RecoveryUseCase;
import br.com.extrator.comandos.cli.base.Comando;
import br.com.extrator.suporte.console.LoggerConsole;

public final class RecoveryComando implements Comando {
    private static final LoggerConsole log = LoggerConsole.getLogger(RecoveryComando.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_DATE;

    @Override
    public void executar(final String[] args) throws Exception {
        if (args == null || args.length < 3) {
            imprimirUso();
            return;
        }

        final LocalDate dataInicio;
        final LocalDate dataFim;
        try {
            dataInicio = LocalDate.parse(args[1], DATE_FORMAT);
            dataFim = LocalDate.parse(args[2], DATE_FORMAT);
        } catch (DateTimeParseException e) {
            log.error("Datas invalidas. Use formato YYYY-MM-DD.");
            imprimirUso();
            return;
        }

        String api = null;
        String entidade = null;
        boolean incluirFaturasGraphQL = true;

        for (int i = 3; i < args.length; i++) {
            final String arg = args[i];
            if ("--sem-faturas-graphql".equalsIgnoreCase(arg)) {
                incluirFaturasGraphQL = false;
                continue;
            }
            if ("--api".equalsIgnoreCase(arg) && i + 1 < args.length) {
                api = args[++i];
                continue;
            }
            if ("--entidade".equalsIgnoreCase(arg) && i + 1 < args.length) {
                entidade = args[++i];
            }
        }

        final RecoveryUseCase useCase = new RecoveryUseCase();
        useCase.executarReplay(dataInicio, dataFim, api, entidade, incluirFaturasGraphQL);
    }

    private void imprimirUso() {
        log.console("Uso:");
        log.console("  --recovery YYYY-MM-DD YYYY-MM-DD [--api graphql|dataexport] [--entidade nome] [--sem-faturas-graphql]");
        log.console("Exemplo:");
        log.console("  --recovery 2026-01-01 2026-01-31 --api graphql --entidade coletas");
    }
}
