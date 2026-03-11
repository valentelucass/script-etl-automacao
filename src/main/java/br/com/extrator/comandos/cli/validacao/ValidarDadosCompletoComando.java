/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/cli/validacao/ValidarDadosCompletoComando.java
Classe  : ValidarDadosCompletoComando (command)
Pacote  : br.com.extrator.comandos.cli.validacao
Modulo  : CLI - Validacao
Papel   : Comando para validação completa de dados (completude, integridade, qualidade).
Conecta com:
- br.com.extrator.aplicacao.validacao.ValidacaoDadosCompletoUseCase
- br.com.extrator.comandos.cli.base.Comando
Fluxo geral:
1) executar() delega diretamente a useCase.executar()
Estrutura interna:
Atributos: useCase
Metodos: executar()
[DOC-FILE-END]============================================================== */
package br.com.extrator.comandos.cli.validacao;

import br.com.extrator.aplicacao.validacao.ValidacaoDadosCompletoUseCase;
import br.com.extrator.comandos.cli.base.Comando;

public class ValidarDadosCompletoComando implements Comando {
    private final ValidacaoDadosCompletoUseCase useCase;

    public ValidarDadosCompletoComando() {
        this(new ValidacaoDadosCompletoUseCase());
    }

    ValidarDadosCompletoComando(final ValidacaoDadosCompletoUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void executar(final String[] args) throws Exception {
        useCase.executar();
    }
}
