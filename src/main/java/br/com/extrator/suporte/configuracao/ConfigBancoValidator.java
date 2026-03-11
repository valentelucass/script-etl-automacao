package br.com.extrator.suporte.configuracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/suporte/configuracao/ConfigBancoValidator.java
Classe  :  (class)
Pacote  : br.com.extrator.suporte.configuracao
Modulo  : Suporte - Config
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ConfigBancoValidator {
    private static final Logger logger = LoggerFactory.getLogger(ConfigBancoValidator.class);

    private ConfigBancoValidator() {
    }

    static void validarConexaoBancoDados() {
        logger.info("Validando conexao com o banco de dados...");

        final String url = ConfigBanco.obterUrlBancoDados();
        final String usuario = ConfigBanco.obterUsuarioBancoDados();
        final String senha = ConfigBanco.obterSenhaBancoDados();

        if (url == null || url.trim().isEmpty()) {
            logger.error("URL do banco de dados nao configurada");
            throw new RuntimeException("Configuracao invalida: URL do banco de dados nao pode estar vazia");
        }
        if (usuario == null || usuario.trim().isEmpty()) {
            logger.error("Usuario do banco de dados nao configurado");
            throw new RuntimeException("Configuracao invalida: Usuario do banco de dados nao pode estar vazio");
        }
        if (senha == null || senha.trim().isEmpty()) {
            logger.error("Senha do banco de dados nao configurada");
            throw new RuntimeException("Configuracao invalida: Senha do banco de dados nao pode estar vazia");
        }

        final int timeoutSegundos = ConfigBanco.obterTimeoutValidacaoConexao();
        try (Connection conexao = obterConexaoValidacao(url, usuario, senha, timeoutSegundos)) {
            if (conexao.isValid(timeoutSegundos)) {
                logger.info("Conexao com banco de dados validada com sucesso (via JDBC)");
                return;
            }
            logger.error("Conexao com banco de dados invalida (via JDBC)");
            throw new RuntimeException("Falha na validacao: Conexao com banco de dados invalida");
        } catch (final SQLException e) {
            logger.error("Erro ao conectar com o banco de dados: {}", e.getMessage());
            throw criarErroConexaoBanco(e);
        } catch (final RuntimeException t) {
            logger.error("Falha ao validar conexao com o banco: {}", t.getMessage());
            throw criarErroConexaoBanco(t);
        }
    }

    static void validarTabelasEssenciais() {
        logger.info("Validando existencia de tabelas essenciais no banco de dados...");

        final String[] tabelasEssenciais = {
            "log_extracoes",
            "page_audit",
            "dim_usuarios"
        };

        final StringBuilder tabelasFaltando = new StringBuilder();
        try (Connection conexao = obterConexaoValidacao(
                ConfigBanco.obterUrlBancoDados(),
                ConfigBanco.obterUsuarioBancoDados(),
                ConfigBanco.obterSenhaBancoDados(),
                ConfigBanco.obterTimeoutValidacaoConexao())) {
            for (final String tabela : tabelasEssenciais) {
                if (!tabelaExiste(conexao, tabela)) {
                    if (tabelasFaltando.length() > 0) {
                        tabelasFaltando.append(", ");
                    }
                    tabelasFaltando.append(tabela);
                }
            }

            if (tabelasFaltando.length() > 0) {
                final String mensagem = String.format(
                    "ERRO CRITICO: As seguintes tabelas nao existem no banco de dados: %s. Execute 'database/executar_database.bat' antes de rodar a aplicacao. Veja database/README.md para instrucoes.",
                    tabelasFaltando
                );
                logger.error(mensagem);
                throw new IllegalStateException(mensagem);
            }

            logger.info("Todas as tabelas essenciais existem no banco de dados");
        } catch (final SQLException e) {
            logger.error("Erro ao validar tabelas essenciais: {}", e.getMessage());
            throw new RuntimeException("Falha ao validar tabelas essenciais", e);
        }
    }

    private static Connection obterConexaoValidacao(final String url,
                                                    final String usuario,
                                                    final String senha,
                                                    final int timeoutSegundos) throws SQLException {
        DriverManager.setLoginTimeout(Math.max(1, timeoutSegundos));
        return DriverManager.getConnection(obterUrlComDatabaseName(url), usuario, senha);
    }

    private static String obterUrlComDatabaseName(final String urlOriginal) {
        if (urlOriginal == null) {
            return null;
        }

        String url = urlOriginal.trim();
        if (url.startsWith("jdbc:sqlserver://")) {
            final String urlLower = url.toLowerCase();
            final boolean temDatabaseName = urlLower.contains("databasename=");
            final boolean temDatabase = urlLower.contains("database=");
            if (!temDatabaseName && !temDatabase) {
                final String nomeBanco = ConfigBanco.obterNomeBancoDados();
                if (nomeBanco != null && !nomeBanco.trim().isEmpty()) {
                    url = url.endsWith(";")
                        ? url + "databaseName=" + nomeBanco.trim()
                        : url + ";databaseName=" + nomeBanco.trim();
                }
            }
        }
        return url;
    }

    private static boolean tabelaExiste(final Connection conexao, final String nomeTabela) throws SQLException {
        final String sql = """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = ?
            """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, nomeTabela);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static RuntimeException criarErroConexaoBanco(final Throwable causa) {
        final String detalhe = extrairMensagemMaisInterna(causa);
        final String detalheLower = detalhe.toLowerCase();

        String mensagemErro = "Erro de conexao com banco de dados: ";
        if (detalheLower.contains("login failed")) {
            mensagemErro += "Credenciais invalidas (usuario ou senha incorretos)";
        } else if (detalheLower.contains("cannot open database")) {
            mensagemErro += "Banco de dados nao encontrado ou inacessivel";
        } else if (detalheLower.contains("tcp/ip")
                || detalheLower.contains("connection refused")
                || detalheLower.contains("connect timed out")
                || detalheLower.contains("connection reset")) {
            mensagemErro += "Servidor de banco de dados inacessivel (verifique URL, porta, firewall e servico SQL Server)";
        } else {
            mensagemErro += detalhe;
        }

        return new RuntimeException(mensagemErro, causa);
    }

    private static String extrairMensagemMaisInterna(final Throwable throwable) {
        Throwable atual = throwable;
        while (atual.getCause() != null && atual.getCause() != atual) {
            atual = atual.getCause();
        }
        final String mensagem = atual.getMessage();
        return (mensagem == null || mensagem.isBlank()) ? atual.getClass().getSimpleName() : mensagem;
    }
}
