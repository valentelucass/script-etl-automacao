/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/cli/validacao/ValidarApiVsBanco24hDetalhadoComando.java
Classe  : ValidarApiVsBanco24hDetalhadoComando (command)
Pacote  : br.com.extrator.comandos.cli.validacao
Modulo  : CLI - Validacao
Papel   : Comando para validação detalhada 24h com suporte a período fechado e fallback de janela.
Conecta com:
- br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaUseCase
- br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaRequest
- br.com.extrator.suporte.tempo.RelogioSistema
Fluxo geral:
1) executar() extrai flags: --sem-faturas-graphql, --periodo-fechado, --permitir-fallback-janela
2) Monta ValidacaoApiBanco24hDetalhadaRequest com flags e data hoje
3) Delegação a useCase.executar()
Estrutura interna:
Atributos: useCase
Metodos: executar(), possuiFlag() [private]
[DOC-FILE-END]============================================================== */
package br.com.extrator.comandos.cli.validacao;

import br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaRequest;
import br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaUseCase;
import br.com.extrator.comandos.cli.base.Comando;
import br.com.extrator.suporte.tempo.RelogioSistema;

public class ValidarApiVsBanco24hDetalhadoComando implements Comando {
    private final ValidacaoApiBanco24hDetalhadaUseCase useCase;

    public ValidarApiVsBanco24hDetalhadoComando() {
        this(new ValidacaoApiBanco24hDetalhadaUseCase());
    }

    ValidarApiVsBanco24hDetalhadoComando(final ValidacaoApiBanco24hDetalhadaUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void executar(final String[] args) throws Exception {
        useCase.executar(
            new ValidacaoApiBanco24hDetalhadaRequest(
                !possuiFlag(args, "--sem-faturas-graphql"),
                possuiFlag(args, "--periodo-fechado"),
                possuiFlag(args, "--permitir-fallback-janela"),
                RelogioSistema.hoje()
            )
        );
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
