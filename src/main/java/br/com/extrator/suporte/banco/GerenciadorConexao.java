/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/util/banco/GerenciadorConexao.java
Classe  : GerenciadorConexao (class)
Pacote  : br.com.extrator.suporte.banco
Modulo  : Utilitario compartilhado
Papel   : Implementa responsabilidade de gerenciador conexao.

Conecta com:
- CarregadorConfig (util.configuracao)

Fluxo geral:
1) Centraliza funcoes auxiliares reutilizaveis.
2) Evita repeticao de logica transversal.
3) Apoia configuracao, formatacao e infraestrutura.

Estrutura interna:
Metodos principais:
- GerenciadorConexao(): realiza operacao relacionada a "gerenciador conexao".
- criarDataSource(): instancia ou monta estrutura de dados.
- obterEstatisticasPool(): recupera dados configurados ou calculados.
- isPoolSaudavel(): retorna estado booleano de controle.
- obterUrlComDatabaseName(): recupera dados configurados ou calculados.
- obterConfigInt(...3 args): recupera dados configurados ou calculados.
- obterConfigLong(...3 args): recupera dados configurados ou calculados.
- extrairMensagemMaisInterna(...1 args): realiza operacao relacionada a "extrair mensagem mais interna".
Atributos-chave:
- logger: logger da classe para diagnostico.
- POOL_MAX_SIZE_DEFAULT: campo de estado para "pool max size default".
- POOL_MIN_IDLE_DEFAULT: campo de estado para "pool min idle default".
- POOL_IDLE_TIMEOUT_DEFAULT: campo de estado para "pool idle timeout default".
- POOL_CONNECTION_TIMEOUT_DEFAULT: campo de estado para "pool connection timeout default".
- POOL_MAX_LIFETIME_DEFAULT: campo de estado para "pool max lifetime default".
[DOC-FILE-END]============================================================== */

package br.com.extrator.suporte.banco;

import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import br.com.extrator.suporte.configuracao.ConfigBanco;

/**
 * Classe centralizada para gerenciar e fornecer conexões com o banco de dados.
 * Utiliza HikariCP para pool de conexões de alta performance.
 * 
 * Configurações do pool via config.properties ou variáveis de ambiente:
 * - db.pool.maximum_size (padrão: 10)
 * - db.pool.minimum_idle (padrão: 2)
 * - db.pool.idle_timeout_ms (padrão: 600000 = 10 min)
 * - db.pool.connection_timeout_ms (padrão: 30000 = 30 seg)
 * - db.pool.max_lifetime_ms (padrão: 1800000 = 30 min)
 * 
 * @author Sistema de Extração ESL Cloud
 * @version 2.0 - Migrado para HikariCP
 */
public final class GerenciadorConexao {

    private static final Logger logger = LoggerFactory.getLogger(GerenciadorConexao.class);
    private static volatile HikariDataSource dataSource;

    // Singleton thread-safe usando holder pattern
    private static class DataSourceHolder {
        private static final HikariDataSource INSTANCE = criarDataSource();
    }

    private GerenciadorConexao() {
        // Impede instanciação
    }

    /**
     * Cria e configura o DataSource HikariCP.
     */
    private static HikariDataSource criarDataSource() {
        logger.info("[INFO] Inicializando pool de conexoes HikariCP...");

        // PROBLEMA 3 CORRIGIDO: Usar CarregadorConfig em vez de System.getenv() diretamente
        final String dbUrl = obterUrlComDatabaseName();
        final String dbUser = ConfigBanco.obterUsuarioBancoDados();
        final String dbPassword = ConfigBanco.obterSenhaBancoDados();

        if (dbUrl == null || dbUrl.trim().isEmpty() ||
            dbUser == null || dbUser.trim().isEmpty() ||
            dbPassword == null) {
            
            final String msg = "Variáveis de ambiente do banco (DB_URL, DB_USER, DB_PASSWORD) não estão configuradas.";
            logger.error(msg);
            throw new IllegalStateException(msg);
        }

        final HikariConfig config = new HikariConfig();
        
        // Configurações básicas de conexão
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUser);
        config.setPassword(dbPassword);
        config.setPoolName("ESL-Cloud-Pool");

        // Configurações do pool
        config.setMaximumPoolSize(ConfigBanco.obterPoolMaximumSize());
        config.setMinimumIdle(ConfigBanco.obterPoolMinimumIdle());
        config.setIdleTimeout(ConfigBanco.obterPoolIdleTimeoutMs());
        config.setConnectionTimeout(ConfigBanco.obterPoolConnectionTimeoutMs());
        config.setMaxLifetime(ConfigBanco.obterPoolMaxLifetimeMs());
        config.setInitializationFailTimeout(ConfigBanco.obterPoolInitializationFailTimeoutMs());

        // Configurações de performance para SQL Server
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        // Query de validação para SQL Server
        config.setConnectionTestQuery("SELECT 1");

        final HikariDataSource ds = new HikariDataSource(config);
        try (Connection ignored = ds.getConnection()) {
            dataSource = ds;
            logger.info("[OK] Pool HikariCP inicializado e conexao inicial validada: maxSize={}, minIdle={}",
                config.getMaximumPoolSize(), config.getMinimumIdle());
        } catch (final SQLException e) {
            ds.close();
            logger.error("Falha ao validar conexao inicial do pool HikariCP: {}", extrairMensagemMaisInterna(e));
            throw new IllegalStateException("Falha ao validar conexao inicial do pool HikariCP", e);
        }
        
        // Hook para fechar o pool no shutdown da JVM.
        // Em casos onde o pool é inicializado dentro de um shutdown hook, a JVM já está
        // encerrando e não permite registrar novos hooks.
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(GerenciadorConexao::fecharPool));
        } catch (final IllegalStateException e) {
            logger.warn("JVM em desligamento: hook de fechamento do pool não registrado.");
        }

        return ds;
    }

    /**
     * Obtém uma conexão do pool.
     *
     * @return Uma instância de Connection do pool.
     * @throws SQLException Se não for possível obter uma conexão.
     */
    public static Connection obterConexao() throws SQLException {
        try {
            return DataSourceHolder.INSTANCE.getConnection();
        } catch (final ExceptionInInitializerError | NoClassDefFoundError e) {
            final String msg =
                "Falha ao inicializar pool de conexoes. Verifique DB_URL/DB_USER/DB_PASSWORD e conectividade com o SQL Server.";
            logger.error(msg, e);
            throw new SQLException(msg + " Detalhe: " + extrairMensagemMaisInterna(e), e);
        }
    }

    /**
     * Retorna estatísticas do pool de conexões.
     * Útil para monitoramento e debug.
     * 
     * @return String com estatísticas do pool
     */
    public static String obterEstatisticasPool() {
        final HikariDataSource ds = DataSourceHolder.INSTANCE;
        return String.format(
            "Pool[ativas=%d, ociosas=%d, total=%d, aguardando=%d]",
            ds.getHikariPoolMXBean().getActiveConnections(),
            ds.getHikariPoolMXBean().getIdleConnections(),
            ds.getHikariPoolMXBean().getTotalConnections(),
            ds.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }

    /**
     * Verifica se o pool está saudável.
     * 
     * @return true se o pool está operacional
     */
    public static boolean isPoolSaudavel() {
        try {
            final HikariDataSource ds = DataSourceHolder.INSTANCE;
            if (ds.isClosed()) {
                return false;
            }
            try (Connection ignored = ds.getConnection()) {
                return true;
            }
        } catch (final SQLException e) {
            return false;
        } catch (final RuntimeException | ExceptionInInitializerError | NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * Constrói a URL do banco incluindo o databaseName se necessário.
     * PROBLEMA 3 CORRIGIDO: Usa CarregadorConfig.obterUrlBancoDados() em vez de System.getenv()
     */
    public static void fecharPool() {
        final HikariDataSource ds = dataSource;
        if (ds == null) {
            return;
        }

        synchronized (GerenciadorConexao.class) {
            final HikariDataSource atual = dataSource;
            if (atual == null || atual.isClosed()) {
                return;
            }

            logger.info("[INFO] Fechando pool de conexoes...");
            atual.close();
            logger.info("[OK] Pool HikariCP encerrado.");
        }
    }

    /**
     * ConstrÃ³i a URL do banco incluindo o databaseName se necessÃ¡rio.
     * PROBLEMA 3 CORRIGIDO: Usa CarregadorConfig.obterUrlBancoDados() em vez de System.getenv()
     */
    private static String obterUrlComDatabaseName() {
        String url = ConfigBanco.obterUrlBancoDados();
        if (url == null) {
            return null;
        }
        
        url = url.trim();
        if (url.startsWith("jdbc:sqlserver://")) {
            final boolean temDatabaseName = url.toLowerCase().contains("databasename=");
            final boolean temDatabase = url.toLowerCase().contains("database=");
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

    private static String extrairMensagemMaisInterna(final Throwable throwable) {
        Throwable atual = throwable;
        while (atual.getCause() != null && atual.getCause() != atual) {
            atual = atual.getCause();
        }
        final String mensagem = atual.getMessage();
        return (mensagem == null || mensagem.isBlank()) ? atual.getClass().getSimpleName() : mensagem;
    }
}
