/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/seguranca/SegurancaConsolePrompt.java
Classe  : SegurancaConsolePrompt (class)
Pacote  : br.com.extrator.seguranca
Modulo  : Modulo de seguranca
Papel   : Implementa responsabilidade de seguranca console prompt.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela usuarios, perfis e acoes autorizadas.
2) Implementa regras de autenticacao e senha.
3) Gerencia repositorio de seguranca local.

Estrutura interna:
Metodos principais:
- SegurancaConsolePrompt(): realiza operacao relacionada a "seguranca console prompt".
- solicitarCredenciais(...1 args): realiza operacao relacionada a "solicitar credenciais".
- solicitarTextoObrigatorio(...1 args): realiza operacao relacionada a "solicitar texto obrigatorio".
- solicitarTextoOpcional(...1 args): realiza operacao relacionada a "solicitar texto opcional".
- solicitarSenhaObrigatoria(...1 args): realiza operacao relacionada a "solicitar senha obrigatoria".
- solicitarSenhaComConfirmacao(...2 args): realiza operacao relacionada a "solicitar senha com confirmacao".
- solicitarSenha(...1 args): realiza operacao relacionada a "solicitar senha".
- Credenciais(...2 args): realiza operacao relacionada a "credenciais".
Atributos-chave:
- SCANNER: campo de estado para "scanner".
[DOC-FILE-END]============================================================== */

package br.com.extrator.seguranca;

import java.io.Console;
import java.util.Arrays;
import java.util.Scanner;

/**
 * Utilitario de prompts de console para comandos de seguranca.
 */
public final class SegurancaConsolePrompt {
    private static final Scanner SCANNER = new Scanner(System.in);

    private SegurancaConsolePrompt() {
    }

    public static Credenciais solicitarCredenciais(final String titulo) {
        if (titulo != null && !titulo.isBlank()) {
            System.out.println(titulo);
        }
        final String usuario = solicitarTextoObrigatorio("Usuario: ");
        final char[] senha = solicitarSenhaObrigatoria("Senha: ");
        return new Credenciais(usuario, senha);
    }

    public static String solicitarTextoObrigatorio(final String label) {
        while (true) {
            System.out.print(label);
            final String valor = SCANNER.nextLine();
            if (valor != null && !valor.trim().isEmpty()) {
                return valor.trim();
            }
            System.out.println("Valor obrigatorio. Tente novamente.");
        }
    }

    public static String solicitarTextoOpcional(final String label) {
        System.out.print(label);
        final String valor = SCANNER.nextLine();
        return valor == null ? "" : valor.trim();
    }

    public static char[] solicitarSenhaObrigatoria(final String label) {
        while (true) {
            final char[] senha = solicitarSenha(label);
            if (senha.length > 0) {
                return senha;
            }
            System.out.println("Senha obrigatoria. Tente novamente.");
        }
    }

    public static char[] solicitarSenhaComConfirmacao(final String label, final String labelConfirmacao) {
        while (true) {
            final char[] senha = solicitarSenhaObrigatoria(label);
            final char[] confirmacao = solicitarSenhaObrigatoria(labelConfirmacao);
            final boolean iguais = Arrays.equals(senha, confirmacao);
            Arrays.fill(confirmacao, '\0');
            if (iguais) {
                return senha;
            }
            Arrays.fill(senha, '\0');
            System.out.println("Senha e confirmacao nao conferem. Tente novamente.");
        }
    }

    private static char[] solicitarSenha(final String label) {
        final Console console = System.console();
        if (console != null) {
            final char[] senha = console.readPassword(label);
            return senha == null ? new char[0] : senha;
        }
        System.out.print(label);
        final String valor = SCANNER.nextLine();
        return valor == null ? new char[0] : valor.toCharArray();
    }

    public record Credenciais(String usuario, char[] senha) {
    }
}
