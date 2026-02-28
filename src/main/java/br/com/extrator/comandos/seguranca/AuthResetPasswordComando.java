/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/seguranca/AuthResetPasswordComando.java
Classe  : AuthResetPasswordComando (class)
Pacote  : br.com.extrator.comandos.seguranca
Modulo  : Comando CLI (seguranca)
Papel   : Implementa responsabilidade de auth reset password comando.

Conecta com:
- Comando (comandos.base)
- AcaoSeguranca (seguranca)
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
import br.com.extrator.seguranca.AcaoSeguranca;
import br.com.extrator.seguranca.SegurancaConsolePrompt;
import br.com.extrator.seguranca.SegurancaService;

/**
 * Comando para redefinir senha de usuario.
 */
public class AuthResetPasswordComando implements Comando {

    @Override
    public void executar(final String[] args) throws Exception {
        final SegurancaService segurancaService = new SegurancaService();

        final SegurancaConsolePrompt.Credenciais adminCredenciais = SegurancaConsolePrompt.solicitarCredenciais(
            "Autenticacao de ADMIN para redefinir senha:"
        );
        try {
            segurancaService.autenticarEAutorizar(
                adminCredenciais.usuario(),
                adminCredenciais.senha(),
                AcaoSeguranca.AUTH_RESET_PASSWORD,
                "Redefinicao de senha de usuario"
            );
        } finally {
            Arrays.fill(adminCredenciais.senha(), '\0');
        }

        final String usernameAlvo = (args.length >= 2)
            ? args[1].trim()
            : SegurancaConsolePrompt.solicitarTextoObrigatorio("Username do usuario alvo: ");
        final char[] novaSenha = SegurancaConsolePrompt.solicitarSenhaComConfirmacao(
            "Nova senha: ",
            "Confirmar nova senha: "
        );
        try {
            segurancaService.redefinirSenha(usernameAlvo, novaSenha, adminCredenciais.usuario());
        } finally {
            Arrays.fill(novaSenha, '\0');
        }

        System.out.println("Senha redefinida com sucesso para: " + usernameAlvo + ".");
    }
}
