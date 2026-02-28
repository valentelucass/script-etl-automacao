/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/seguranca/AuthCreateUserComando.java
Classe  : AuthCreateUserComando (class)
Pacote  : br.com.extrator.comandos.seguranca
Modulo  : Comando CLI (seguranca)
Papel   : Implementa responsabilidade de auth create user comando.

Conecta com:
- Comando (comandos.base)
- AcaoSeguranca (seguranca)
- PerfilAcesso (seguranca)
- SegurancaConsolePrompt (seguranca)
- SegurancaService (seguranca)

Fluxo geral:
1) Orquestra operacoes de autenticacao/autorizacao.
2) Aplica regras de perfil e ciclo de senha.
3) Registra resultado operacional das acoes de seguranca.

Estrutura interna:
Metodos principais:
- solicitarPerfil(): realiza operacao relacionada a "solicitar perfil".
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.seguranca;

import java.util.Arrays;

import br.com.extrator.comandos.base.Comando;
import br.com.extrator.seguranca.AcaoSeguranca;
import br.com.extrator.seguranca.PerfilAcesso;
import br.com.extrator.seguranca.SegurancaConsolePrompt;
import br.com.extrator.seguranca.SegurancaService;

/**
 * Comando para criar usuarios operacionais.
 */
public class AuthCreateUserComando implements Comando {

    @Override
    public void executar(final String[] args) throws Exception {
        final SegurancaService segurancaService = new SegurancaService();

        final SegurancaConsolePrompt.Credenciais adminCredenciais = SegurancaConsolePrompt.solicitarCredenciais(
            "Autenticacao de ADMIN para criar usuario:"
        );
        try {
            segurancaService.autenticarEAutorizar(
                adminCredenciais.usuario(),
                adminCredenciais.senha(),
                AcaoSeguranca.AUTH_CREATE_USER,
                "Criacao de usuario operacional"
            );
        } finally {
            Arrays.fill(adminCredenciais.senha(), '\0');
        }

        final String novoUsername = (args.length >= 2)
            ? args[1].trim()
            : SegurancaConsolePrompt.solicitarTextoObrigatorio("Novo username: ");
        final PerfilAcesso perfil = (args.length >= 3)
            ? PerfilAcesso.fromString(args[2])
            : solicitarPerfil();
        final String displayName = (args.length >= 4)
            ? args[3].trim()
            : SegurancaConsolePrompt.solicitarTextoOpcional("Nome de exibicao (opcional): ");

        final char[] senha = SegurancaConsolePrompt.solicitarSenhaComConfirmacao(
            "Senha do novo usuario: ",
            "Confirmar senha: "
        );
        try {
            segurancaService.criarUsuario(
                novoUsername,
                displayName,
                perfil,
                senha,
                adminCredenciais.usuario()
            );
        } finally {
            Arrays.fill(senha, '\0');
        }

        System.out.println("Usuario criado com sucesso: " + novoUsername + " (" + perfil + ").");
    }

    private PerfilAcesso solicitarPerfil() {
        while (true) {
            System.out.println("Perfis disponiveis: ADMIN, OPERADOR, VISUALIZADOR");
            final String valor = SegurancaConsolePrompt.solicitarTextoObrigatorio("Perfil: ");
            try {
                return PerfilAcesso.fromString(valor);
            } catch (final IllegalArgumentException e) {
                System.out.println(e.getMessage());
            }
        }
    }
}
