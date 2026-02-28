/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/seguranca/SegurancaRepository.java
Classe  : SegurancaRepository (class)
Pacote  : br.com.extrator.seguranca
Modulo  : Modulo de seguranca
Papel   : Implementa responsabilidade de seguranca repository.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela usuarios, perfis e acoes autorizadas.
2) Implementa regras de autenticacao e senha.
3) Gerencia repositorio de seguranca local.

Estrutura interna:
Metodos principais:
- SegurancaRepository(): realiza operacao relacionada a "seguranca repository".
- getDbPath(): expone valor atual do estado interno.
- existeQualquerUsuarioAtivo(): realiza operacao relacionada a "existe qualquer usuario ativo".
- existeUsuarioAdminAtivo(): realiza operacao relacionada a "existe usuario admin ativo".
- buscarPorUsername(...1 args): consulta e retorna dados conforme criterio.
- inserirUsuario(...5 args): inclui registros no destino configurado.
- atualizarFalhaLogin(...3 args): altera estado/registros existentes.
- registrarLoginSucesso(...1 args): grava informacoes de auditoria/log.
- redefinirSenha(...3 args): realiza operacao relacionada a "redefinir senha".
- desativarUsuario(...1 args): realiza operacao relacionada a "desativar usuario".
- contarUsuariosAtivos(): realiza operacao relacionada a "contar usuarios ativos".
- contarEventosAuditoria(): realiza operacao relacionada a "contar eventos auditoria".
- registrarAuditoria(...5 args): grava informacoes de auditoria/log.
- garantirDriverSqlite(): realiza operacao relacionada a "garantir driver sqlite".
Atributos-chave:
- dbPath: campo de estado para "db path".
- jdbcUrl: campo de estado para "jdbc url".
[DOC-FILE-END]============================================================== */

package br.com.extrator.seguranca;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

/**
 * Repositorio SQLite para usuarios e auditoria de seguranca.
 */
public class SegurancaRepository {
    private final Path dbPath;
    private final String jdbcUrl;

    public SegurancaRepository() {
        this.dbPath = CaminhoBancoSegurancaResolver.resolver();
        this.jdbcUrl = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        garantirDriverSqlite();
        inicializarSchema();
    }

    public Path getDbPath() {
        return dbPath;
    }

    public boolean existeQualquerUsuarioAtivo() {
        final String sql = "SELECT COUNT(*) FROM users WHERE active = 1";
        try (Connection conn = abrirConexao();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (final SQLException e) {
            throw new RuntimeException("Falha ao consultar usuarios ativos.", e);
        }
    }

    public boolean existeUsuarioAdminAtivo() {
        final String sql = "SELECT COUNT(*) FROM users WHERE active = 1 AND role = 'ADMIN'";
        try (Connection conn = abrirConexao();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (final SQLException e) {
            throw new RuntimeException("Falha ao consultar administradores ativos.", e);
        }
    }

    public Optional<UsuarioSeguranca> buscarPorUsername(final String usernameRaw) {
        final String username = normalizarUsername(usernameRaw);
        final String sql = """
            SELECT id, username, display_name, password_hash, password_salt, role, active, failed_attempts, blocked_until
            FROM users
            WHERE username = ?
            """;
        try (Connection conn = abrirConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                final LocalDateTime bloqueadoAte = parseDataHoraNullable(rs.getString("blocked_until"));
                final UsuarioSeguranca usuario = new UsuarioSeguranca(
                    rs.getLong("id"),
                    rs.getString("username"),
                    rs.getString("display_name"),
                    PerfilAcesso.fromString(rs.getString("role")),
                    rs.getInt("active") == 1,
                    rs.getInt("failed_attempts"),
                    bloqueadoAte,
                    rs.getString("password_hash"),
                    rs.getString("password_salt")
                );
                return Optional.of(usuario);
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Falha ao buscar usuario por username.", e);
        }
    }

    public void inserirUsuario(
        final String usernameRaw,
        final String displayName,
        final PerfilAcesso perfilAcesso,
        final String hashBase64,
        final String saltBase64
    ) {
        final String username = normalizarUsername(usernameRaw);
        final String sql = """
            INSERT INTO users (
                username, display_name, password_hash, password_salt, role, active,
                failed_attempts, blocked_until, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, 1, 0, NULL, ?, ?)
            """;
        final String agora = LocalDateTime.now().toString();
        try (Connection conn = abrirConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, (displayName == null || displayName.isBlank()) ? username : displayName.trim());
            stmt.setString(3, hashBase64);
            stmt.setString(4, saltBase64);
            stmt.setString(5, perfilAcesso.name());
            stmt.setString(6, agora);
            stmt.setString(7, agora);
            stmt.executeUpdate();
        } catch (final SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("unique")) {
                throw new IllegalStateException("Usuario ja existe: " + username);
            }
            throw new RuntimeException("Falha ao inserir usuario.", e);
        }
    }

    public void atualizarFalhaLogin(final long userId, final int tentativasFalhas, final LocalDateTime bloqueadoAte) {
        final String sql = """
            UPDATE users
            SET failed_attempts = ?, blocked_until = ?, updated_at = ?
            WHERE id = ?
            """;
        try (Connection conn = abrirConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, tentativasFalhas);
            stmt.setString(2, bloqueadoAte == null ? null : bloqueadoAte.toString());
            stmt.setString(3, LocalDateTime.now().toString());
            stmt.setLong(4, userId);
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Falha ao atualizar estado de falha de login.", e);
        }
    }

    public void registrarLoginSucesso(final long userId) {
        final String sql = """
            UPDATE users
            SET failed_attempts = 0, blocked_until = NULL, last_login_at = ?, updated_at = ?
            WHERE id = ?
            """;
        final String agora = LocalDateTime.now().toString();
        try (Connection conn = abrirConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, agora);
            stmt.setString(2, agora);
            stmt.setLong(3, userId);
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Falha ao registrar login bem-sucedido.", e);
        }
    }

    public void redefinirSenha(final String usernameRaw, final String novoHashBase64, final String novoSaltBase64) {
        final String username = normalizarUsername(usernameRaw);
        final String sql = """
            UPDATE users
            SET password_hash = ?, password_salt = ?, failed_attempts = 0, blocked_until = NULL, active = 1, updated_at = ?
            WHERE username = ?
            """;
        try (Connection conn = abrirConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, novoHashBase64);
            stmt.setString(2, novoSaltBase64);
            stmt.setString(3, LocalDateTime.now().toString());
            stmt.setString(4, username);
            final int atualizados = stmt.executeUpdate();
            if (atualizados == 0) {
                throw new IllegalStateException("Usuario nao encontrado: " + username);
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Falha ao redefinir senha.", e);
        }
    }

    public void desativarUsuario(final String usernameRaw) {
        final String username = normalizarUsername(usernameRaw);
        final String sql = """
            UPDATE users
            SET active = 0, updated_at = ?
            WHERE username = ?
            """;
        try (Connection conn = abrirConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, LocalDateTime.now().toString());
            stmt.setString(2, username);
            final int atualizados = stmt.executeUpdate();
            if (atualizados == 0) {
                throw new IllegalStateException("Usuario nao encontrado: " + username);
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Falha ao desativar usuario.", e);
        }
    }

    public long contarUsuariosAtivos() {
        final String sql = "SELECT COUNT(*) FROM users WHERE active = 1";
        try (Connection conn = abrirConexao();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (final SQLException e) {
            throw new RuntimeException("Falha ao contar usuarios ativos.", e);
        }
    }

    public long contarEventosAuditoria() {
        final String sql = "SELECT COUNT(*) FROM auth_audit";
        try (Connection conn = abrirConexao();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (final SQLException e) {
            throw new RuntimeException("Falha ao contar eventos de auditoria.", e);
        }
    }

    public void registrarAuditoria(
        final String username,
        final String acao,
        final boolean sucesso,
        final String detalhe,
        final String host
    ) {
        final String sql = """
            INSERT INTO auth_audit (occurred_at, username, action, success, detail, host)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (Connection conn = abrirConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, LocalDateTime.now().toString());
            stmt.setString(2, username);
            stmt.setString(3, acao);
            stmt.setInt(4, sucesso ? 1 : 0);
            stmt.setString(5, detalhe);
            stmt.setString(6, host);
            stmt.executeUpdate();
        } catch (final SQLException e) {
            throw new RuntimeException("Falha ao registrar auditoria de seguranca.", e);
        }
    }

    private Connection abrirConexao() throws SQLException {
        final Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        return conn;
    }

    private void garantirDriverSqlite() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (final ClassNotFoundException e) {
            throw new IllegalStateException("Driver SQLite nao encontrado no classpath.", e);
        }
    }

    private void inicializarSchema() {
        final String[] comandos = new String[] {
            """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                display_name TEXT,
                password_hash TEXT NOT NULL,
                password_salt TEXT NOT NULL,
                role TEXT NOT NULL,
                active INTEGER NOT NULL DEFAULT 1,
                failed_attempts INTEGER NOT NULL DEFAULT 0,
                blocked_until TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL,
                last_login_at TEXT
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS auth_audit (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                occurred_at TEXT NOT NULL,
                username TEXT,
                action TEXT NOT NULL,
                success INTEGER NOT NULL,
                detail TEXT,
                host TEXT
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_users_username ON users(username)",
            "CREATE INDEX IF NOT EXISTS idx_auth_audit_occurred_at ON auth_audit(occurred_at)"
        };

        try (Connection conn = abrirConexao();
             Statement stmt = conn.createStatement()) {
            for (final String comando : comandos) {
                stmt.execute(comando);
            }
        } catch (final SQLException e) {
            throw new RuntimeException("Falha ao inicializar schema de seguranca.", e);
        }
    }

    private String normalizarUsername(final String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username obrigatorio.");
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private LocalDateTime parseDataHoraNullable(final String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(valor);
    }
}
