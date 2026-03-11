/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/cli/utilitarios/TestarApiComando.java
Classe  : TestarApiComando (command), ParsedArgs (record)
Pacote  : br.com.extrator.comandos.cli.utilitarios
Modulo  : CLI - Utilitario
Papel   : Comando para teste de conectividade/resposta de APIs (GraphQL ou DataExport).
Conecta com:
- br.com.extrator.aplicacao.extracao.TesteApiUseCase
- br.com.extrator.aplicacao.extracao.TesteApiRequest
- br.com.extrator.comandos.cli.base.Comando
Fluxo geral:
1) executar() valida args (tipo API obrigatório)
2) parseArgs() extrai entidade e flag --sem-faturas-graphql
3) Delegação a TesteApiUseCase.executar() com TesteApiRequest
Estrutura interna:
Atributos: useCase, FLAG_SEM_FATURAS_GRAPHQL [static]
Metodos: executar(), parseArgs()
[DOC-FILE-END]============================================================== */
package br.com.extrator.comandos.cli.utilitarios;

import java.util.ArrayList;
import java.util.List;

import br.com.extrator.aplicacao.extracao.TesteApiRequest;
import br.com.extrator.aplicacao.extracao.TesteApiUseCase;
import br.com.extrator.comandos.cli.base.Comando;

public class TestarApiComando implements Comando {
    private static final String FLAG_SEM_FATURAS_GRAPHQL = "--sem-faturas-graphql";
    private final TesteApiUseCase useCase;

    public TestarApiComando() {
        this(new TesteApiUseCase());
    }

    TestarApiComando(final TesteApiUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void executar(final String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("ERRO: Tipo de API nao especificado.");
            System.err.println("Uso: --testar-api <tipo> [entidade] [--sem-faturas-graphql]");
            System.err.println("Tipos validos: graphql, dataexport");
            throw new IllegalArgumentException("Tipo de API nao especificado. Tipos validos: graphql, dataexport");
        }

        final String tipoApi = args[1];
        final ParsedArgs parsedArgs = parseArgs(args);

        useCase.executar(
            new TesteApiRequest(
                tipoApi,
                parsedArgs.entidade(),
                parsedArgs.incluirFaturasGraphQL()
            )
        );
    }

    private ParsedArgs parseArgs(final String[] args) {
        boolean incluirFaturasGraphQL = true;
        final List<String> posicionais = new ArrayList<>();

        for (int i = 2; i < args.length; i++) {
            final String arg = args[i];
            if (FLAG_SEM_FATURAS_GRAPHQL.equalsIgnoreCase(arg)) {
                incluirFaturasGraphQL = false;
            } else {
                posicionais.add(arg);
            }
        }

        if (posicionais.size() > 1) {
            throw new IllegalArgumentException(
                "Argumentos invalidos para --testar-api. Uso: --testar-api <tipo> [entidade] [--sem-faturas-graphql]"
            );
        }

        final String entidade = posicionais.isEmpty() ? null : posicionais.get(0);
        return new ParsedArgs(entidade, incluirFaturasGraphQL);
    }

    private record ParsedArgs(String entidade, boolean incluirFaturasGraphQL) {
    }
}
