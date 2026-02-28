/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/seguranca/SegurancaService.java
Classe  : SegurancaService (class)
Pacote  : br.com.extrator.seguranca
Modulo  : Modulo de seguranca
Papel   : Implementa responsabilidade de seguranca service.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela usuarios, perfis e acoes autorizadas.
2) Implementa regras de autenticacao e senha.
3) Gerencia repositorio de seguranca local.

Estrutura interna:
Metodos principais:
- SegurancaService(): realiza operacao relacionada a "seguranca service".
- autenticarEAutorizar(...4 args): realiza operacao relacionada a "autenticar eautorizar".
- bootstrapAdmin(...3 args): realiza operacao relacionada a "bootstrap admin".
- criarUsuario(...5 args): instancia ou monta estrutura de dados.
- redefinirSenha(...3 args): realiza operacao relacionada a "redefinir senha".
- desativarUsuario(...2 args): realiza operacao relacionada a "desativar usuario".
- obterResumo(): recupera dados configurados ou calculados.
- tratarFalhaSenha(...2 args): realiza operacao relacionada a "tratar falha senha".
- registrarAuditoria(...4 args): grava informacoes de auditoria/log.
- validarPoliticaSenha(...1 args): aplica regras de validacao e consistencia.
- lerIntEnv(...2 args): realiza operacao relacionada a "ler int env".
- normalizarUsername(...1 args): realiza operacao relacionada a "normalizar username".
- resolverHostname(): realiza operacao relacionada a "resolver hostname".
- ResumoSeguranca(...3 args): realiza operacao relacionada a "resumo seguranca".
Atributos-chave:
- repository: dependencia de acesso a banco.
- pepper: campo de estado para "pepper".
- maxTentativasFalhas: campo de estado para "max tentativas falhas".
- minutosBloqueio: campo de estado para "minutos bloqueio".
[DOC-FILE-END]============================================================== */

package br.com.extrator.seguranca;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Regras de autenticacao/autorizacao para operacao do sistema.
 */
public class SegurancaService {
    private final SegurancaRepository repository;
    private final String pepper;
    private final int maxTentativasFalhas;
    private final int minutosBloqueio;

    public SegurancaService() {
        this.repository = new SegurancaRepository();
        this.pepper = Optional.ofNullable(System.getenv("EXTRATOR_AUTH_PEPPER")).orElse("");
        this.maxTentativasFalhas = lerIntEnv("EXTRATOR_AUTH_MAX_TENTATIVAS", 3);
        this.minutosBloqueio = lerIntEnv("EXTRATOR_AUTH_BLOQUEIO_MINUTOS", 5);
    }

    public void autenticarEAutorizar(
        final String usernameRaw,
        final char[] senha,
        final AcaoSeguranca acao,
        final String detalhe
    ) {
        if (!repository.existeQualquerUsuarioAtivo()) {
            throw new IllegalStateException(
                "Nenhum usuario ativo encontrado. Execute primeiro: java -jar target\\extrator.jar --auth-bootstrap"
            );
        }

        final String username = normalizarUsername(usernameRaw);
        final Optional<UsuarioSeguranca> userOpt = repository.buscarPorUsername(username);
        if (userOpt.isEmpty()) {
            registrarAuditoria(username, acao.name(), false, "Usuario nao encontrado: " + username);
            throw new IllegalArgumentException("Usuario ou senha invalidos.");
        }

        final UsuarioSeguranca usuario = userOpt.get();
        if (!usuario.ativo()) {
            registrarAuditoria(username, acao.name(), false, "Usuario inativo");
            throw new IllegalStateException("Usuario inativo: " + username);
        }

        final LocalDateTime agora = LocalDateTime.now();
        if (usuario.bloqueadoAte() != null && usuario.bloqueadoAte().isAfter(agora)) {
            registrarAuditoria(
                username,
                acao.name(),
                false,
                "Usuario bloqueado ate " + usuario.bloqueadoAte()
            );
            throw new IllegalStateException("Usuario bloqueado ate " + usuario.bloqueadoAte() + ".");
        }

        final boolean senhaValida = SenhaSeguraUtil.validarSenha(
            senha,
            usuario.senhaHashBase64(),
            usuario.senhaSaltBase64(),
            pepper
        );

        if (!senhaValida) {
            tratarFalhaSenha(usuario, acao);
        }

        repository.registrarLoginSucesso(usuario.id());

        if (!acao.permite(usuario.perfilAcesso())) {
            registrarAuditoria(
                username,
                acao.name(),
                false,
                "Perfil sem permissao para acao. Perfil: " + usuario.perfilAcesso()
            );
            throw new IllegalStateException("Perfil sem permissao para acao: " + acao.name());
        }

        registrarAuditoria(
            username,
            acao.name(),
            true,
            detalhe == null ? acao.getDescricao() : detalhe
        );
    }

    public void bootstrapAdmin(final String usernameRaw, final String displayName, final char[] senha) {
        if (repository.existeUsuarioAdminAtivo()) {
            throw new IllegalStateException("Ja existe usuario ADMIN ativo. Use os comandos de gerenciamento de usuarios.");
        }

        validarPoliticaSenha(senha);
        final String username = normalizarUsername(usernameRaw);

        final String saltBase64 = SenhaSeguraUtil.gerarSaltBase64();
        final String hashBase64 = SenhaSeguraUtil.gerarHashBase64(senha, saltBase64, pepper);
        repository.inserirUsuario(username, displayName, PerfilAcesso.ADMIN, hashBase64, saltBase64);
        registrarAuditoria(username, "AUTH_BOOTSTRAP", true, "Primeiro administrador criado.");
    }

    public void criarUsuario(
        final String usernameRaw,
        final String displayName,
        final PerfilAcesso perfilAcesso,
        final char[] senha,
        final String usuarioOperador
    ) {
        validarPoliticaSenha(senha);
        final String username = normalizarUsername(usernameRaw);
        final String saltBase64 = SenhaSeguraUtil.gerarSaltBase64();
        final String hashBase64 = SenhaSeguraUtil.gerarHashBase64(senha, saltBase64, pepper);
        repository.inserirUsuario(username, displayName, perfilAcesso, hashBase64, saltBase64);
        registrarAuditoria(usuarioOperador, "AUTH_CREATE_USER", true, "Usuario criado: " + username + " (" + perfilAcesso + ")");
    }

    public void redefinirSenha(final String usernameRaw, final char[] novaSenha, final String usuarioOperador) {
        validarPoliticaSenha(novaSenha);
        final String username = normalizarUsername(usernameRaw);
        final String saltBase64 = SenhaSeguraUtil.gerarSaltBase64();
        final String hashBase64 = SenhaSeguraUtil.gerarHashBase64(novaSenha, saltBase64, pepper);
        repository.redefinirSenha(username, hashBase64, saltBase64);
        registrarAuditoria(usuarioOperador, "AUTH_RESET_PASSWORD", true, "Senha redefinida para usuario: " + username);
    }

    public void desativarUsuario(final String usernameRaw, final String usuarioOperador) {
        final String username = normalizarUsername(usernameRaw);
        final String operador = normalizarUsername(usuarioOperador);
        if (username.equals(operador)) {
            throw new IllegalArgumentException("Nao e permitido desativar o proprio usuario.");
        }
        repository.desativarUsuario(username);
        registrarAuditoria(usuarioOperador, "AUTH_DISABLE_USER", true, "Usuario desativado: " + username);
    }

    public ResumoSeguranca obterResumo() {
        return new ResumoSeguranca(
            repository.getDbPath(),
            repository.contarUsuariosAtivos(),
            repository.contarEventosAuditoria()
        );
    }

    private void tratarFalhaSenha(final UsuarioSeguranca usuario, final AcaoSeguranca acao) {
        final int tentativasAtualizadas = usuario.tentativasFalhas() + 1;
        LocalDateTime bloqueadoAte = null;
        int valorTentativasSalvo = tentativasAtualizadas;
        if (tentativasAtualizadas >= maxTentativasFalhas) {
            bloqueadoAte = LocalDateTime.now().plusMinutes(minutosBloqueio);
            valorTentativasSalvo = 0;
        }

        repository.atualizarFalhaLogin(usuario.id(), valorTentativasSalvo, bloqueadoAte);

        if (bloqueadoAte != null) {
            registrarAuditoria(
                usuario.username(),
                acao.name(),
                false,
                "Senha invalida. Usuario bloqueado ate " + bloqueadoAte
            );
            throw new IllegalStateException("Senha invalida. Usuario bloqueado por " + minutosBloqueio + " minuto(s).");
        }

        final int restantes = Math.max(0, maxTentativasFalhas - tentativasAtualizadas);
        registrarAuditoria(
            usuario.username(),
            acao.name(),
            false,
            "Senha invalida. Tentativas restantes: " + restantes
        );
        throw new IllegalArgumentException("Usuario ou senha invalidos.");
    }

    private void registrarAuditoria(final String username, final String acao, final boolean sucesso, final String detalhe) {
        final String host = resolverHostname();
        repository.registrarAuditoria(username, acao, sucesso, detalhe, host);
    }

    private void validarPoliticaSenha(final char[] senha) {
        if (!SenhaSeguraUtil.senhaAtendePolitica(senha)) {
            throw new IllegalArgumentException(
                "Senha invalida. Requisitos: minimo 8 caracteres, com pelo menos 1 letra e 1 numero."
            );
        }
    }

    private int lerIntEnv(final String nome, final int valorPadrao) {
        final String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            return valorPadrao;
        }
        try {
            final int parsed = Integer.parseInt(valor.trim());
            return parsed > 0 ? parsed : valorPadrao;
        } catch (final NumberFormatException e) {
            return valorPadrao;
        }
    }

    private String normalizarUsername(final String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username obrigatorio.");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String resolverHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (final UnknownHostException e) {
            return "host-desconhecido";
        }
    }

    public record ResumoSeguranca(Path dbPath, long usuariosAtivos, long eventosAuditoria) {
    }
}
