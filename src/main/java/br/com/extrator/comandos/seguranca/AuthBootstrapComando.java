/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/seguranca/AuthBootstrapComando.java
Classe  : AuthBootstrapComando (class)
Pacote  : br.com.extrator.comandos.seguranca
Modulo  : Comando CLI (seguranca)
Papel   : Implementa responsabilidade de auth bootstrap comando.

Conecta com:
- Comando (comandos.base)
- SegurancaConsolePrompt (seguranca)
- SegurancaService (seguranca)

Fluxo geral:
1) Orquestra operacoes de autenticacao/autorizacao.
2) Aplica regras de perfil e ciclo de senha.
3) Registra resultado operacional das acoes de seguranca.

Estrutura interna:
Metodos principais:
- Metodos nao mapeados automaticamente; consulte a implementacao abaixo.
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.seguranca;

import java.util.Arrays;

import br.com.extrator.comandos.base.Comando;
import br.com.extrator.seguranca.SegurancaConsolePrompt;
import br.com.extrator.seguranca.SegurancaService;

/**
 * Comando para criar o primeiro usuario administrador.
 */
public class AuthBootstrapComando implements Comando {

    @Override
    public void executar(final String[] args) throws Exception {
        final SegurancaService segurancaService = new SegurancaService();

        final String username = (args.length >= 2)
            ? args[1].trim()
            : SegurancaConsolePrompt.solicitarTextoObrigatorio("Username do ADMIN: ");
        final String displayName = (args.length >= 3)
            ? args[2].trim()
            : SegurancaConsolePrompt.solicitarTextoOpcional("Nome de exibicao (opcional): ");

        final char[] senha = SegurancaConsolePrompt.solicitarSenhaComConfirmacao(
            "Senha do ADMIN: ",
            "Confirmar senha: "
        );
        try {
            segurancaService.bootstrapAdmin(username, displayName, senha);
        } finally {
            Arrays.fill(senha, '\0');
        }

        final SegurancaService.ResumoSeguranca resumo = segurancaService.obterResumo();
        System.out.println("Bootstrap concluido com sucesso.");
        System.out.println("Banco de seguranca: " + resumo.dbPath());
        System.out.println("Usuarios ativos: " + resumo.usuariosAtivos());
    }
}
