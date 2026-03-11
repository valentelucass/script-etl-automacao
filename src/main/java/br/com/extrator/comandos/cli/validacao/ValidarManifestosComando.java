/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/cli/validacao/ValidarManifestosComando.java
Classe  : ValidarManifestosComando (command)
Pacote  : br.com.extrator.comandos.cli.validacao
Modulo  : CLI - Validacao
Papel   : Comando para validação de integridade/consistência de manifestos em banco.
Conecta com:
- br.com.extrator.aplicacao.validacao.ValidacaoManifestosUseCase
- br.com.extrator.comandos.cli.base.Comando
Fluxo geral:
1) executar() delega diretamente a useCase.executar()
Estrutura interna:
Atributos: useCase
Metodos: executar()
[DOC-FILE-END]============================================================== */
package br.com.extrator.comandos.cli.validacao;

import br.com.extrator.aplicacao.validacao.ValidacaoManifestosUseCase;
import br.com.extrator.comandos.cli.base.Comando;

public class ValidarManifestosComando implements Comando {
    private final ValidacaoManifestosUseCase useCase;

    public ValidarManifestosComando() {
        this(new ValidacaoManifestosUseCase());
    }

    ValidarManifestosComando(final ValidacaoManifestosUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void executar(final String[] args) throws Exception {
        useCase.executar();
    }
}
