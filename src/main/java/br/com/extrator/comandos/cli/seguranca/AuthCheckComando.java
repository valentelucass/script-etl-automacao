/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/seguranca/AuthCheckComando.java
Classe  : AuthCheckComando (class)
Pacote  : br.com.extrator.comandos.cli.seguranca
Modulo  : Comando CLI (seguranca)
Papel   : Implementa responsabilidade de auth check comando.

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
- construirDetalhe(...1 args): realiza operacao relacionada a "construir detalhe".
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.cli.seguranca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import br.com.extrator.comandos.cli.base.Comando;
import br.com.extrator.seguranca.AcaoSeguranca;
import br.com.extrator.seguranca.SegurancaConsolePrompt;
import br.com.extrator.seguranca.SegurancaService;
import br.com.extrator.seguranca.UsuarioSeguranca;

/**
 * Comando para autenticar usuario e autorizar acao sensivel.
 */
public class AuthCheckComando implements Comando {
    private static final String ENV_AUTH_CONTEXT_FILE = "EXTRATOR_AUTH_CONTEXT_FILE";

    @Override
    public void executar(final String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Uso: --auth-check <ACAO_SEGURANCA> [detalhe]");
        }

        final AcaoSeguranca acao = AcaoSeguranca.fromToken(args[1]);
        final String detalhe = construirDetalhe(args);
        final SegurancaConsolePrompt.Credenciais credenciais = SegurancaConsolePrompt.solicitarCredenciais(
            "Autenticacao obrigatoria: " + acao.getDescricao()
        );
        final SegurancaService segurancaService = new SegurancaService();
        try {
            final UsuarioSeguranca usuario = segurancaService.autenticarEAutorizar(
                credenciais.usuario(),
                credenciais.senha(),
                acao,
                detalhe
            );
            gravarContextoSessao(usuario);
        } finally {
            Arrays.fill(credenciais.senha(), '\0');
        }
        System.out.println("Acesso autorizado para " + acao.name() + ".");
    }

    private String construirDetalhe(final String[] args) {
        if (args.length < 3) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    private void gravarContextoSessao(final UsuarioSeguranca usuario) {
        final String caminho = System.getenv(ENV_AUTH_CONTEXT_FILE);
        if (caminho == null || caminho.isBlank()) {
            return;
        }

        final String acoesPermitidas = Arrays.stream(AcaoSeguranca.values())
            .filter(acao -> acao.permite(usuario.perfilAcesso()))
            .map(AcaoSeguranca::name)
            .collect(Collectors.joining(";"));

        final String conteudo = "username=" + limparValorContexto(usuario.username()) + System.lineSeparator()
            + "role=" + usuario.perfilAcesso().name() + System.lineSeparator()
            + "actions=" + acoesPermitidas + System.lineSeparator();

        try {
            final Path arquivo = Path.of(caminho).toAbsolutePath().normalize();
            final Path diretorio = arquivo.getParent();
            if (diretorio != null) {
                Files.createDirectories(diretorio);
            }
            Files.writeString(arquivo, conteudo, StandardCharsets.UTF_8);
        } catch (final IOException | RuntimeException e) {
            System.err.println("Aviso: nao foi possivel gravar contexto de sessao: " + e.getMessage());
        }
    }

    private String limparValorContexto(final String valor) {
        if (valor == null) {
            return "";
        }
        return valor
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('=', '_')
            .replace(';', '_')
            .trim();
    }
}
