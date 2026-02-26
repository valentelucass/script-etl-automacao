package br.com.extrator.util.banco;

import java.sql.Connection;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import br.com.extrator.util.configuracao.CarregadorConfig;

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

    // Singleton thread-safe usando holder pattern
    private static class DataSourceHolder {
        private static final HikariDataSource INSTANCE = criarDataSource();
    }

    // Configurações padrão do pool (podem ser sobrescritas via config)
    private static final int POOL_MAX_SIZE_DEFAULT = 10;
    private static final int POOL_MIN_IDLE_DEFAULT = 2;
    private static final long POOL_IDLE_TIMEOUT_DEFAULT = 600_000L;      // 10 minutos
    private static final long POOL_CONNECTION_TIMEOUT_DEFAULT = 30_000L; // 30 segundos
    private static final long POOL_MAX_LIFETIME_DEFAULT = 1_800_000L;    // 30 minutos

    private GerenciadorConexao() {
        // Impede instanciação
    }

    /**
     * Cria e configura o DataSource HikariCP.
     */
    private static HikariDataSource criarDataSource() {
        logger.info("🔧 Inicializando pool de conexões HikariCP...");

        // PROBLEMA 3 CORRIGIDO: Usar CarregadorConfig em vez de System.getenv() diretamente
        final String dbUrl = obterUrlComDatabaseName();
        final String dbUser = CarregadorConfig.obterUsuarioBancoDados();
        final String dbPassword = CarregadorConfig.obterSenhaBancoDados();

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
        config.setMaximumPoolSize(obterConfigInt("DB_POOL_MAX_SIZE", "db.pool.maximum_size", POOL_MAX_SIZE_DEFAULT));
        config.setMinimumIdle(obterConfigInt("DB_POOL_MIN_IDLE", "db.pool.minimum_idle", POOL_MIN_IDLE_DEFAULT));
        config.setIdleTimeout(obterConfigLong("DB_POOL_IDLE_TIMEOUT", "db.pool.idle_timeout_ms", POOL_IDLE_TIMEOUT_DEFAULT));
        config.setConnectionTimeout(obterConfigLong("DB_POOL_CONN_TIMEOUT", "db.pool.connection_timeout_ms", POOL_CONNECTION_TIMEOUT_DEFAULT));
        config.setMaxLifetime(obterConfigLong("DB_POOL_MAX_LIFETIME", "db.pool.max_lifetime_ms", POOL_MAX_LIFETIME_DEFAULT));
        // Permite inicializacao do pool mesmo com banco temporariamente indisponivel.
        // Evita erro definitivo de classe quando ha oscilacao de conectividade.
        config.setInitializationFailTimeout(-1L);

        // Configurações de performance para SQL Server
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        // Query de validação para SQL Server
        config.setConnectionTestQuery("SELECT 1");

        final HikariDataSource ds = new HikariDataSource(config);
        
        logger.info("✅ Pool HikariCP inicializado: maxSize={}, minIdle={}", 
            config.getMaximumPoolSize(), config.getMinimumIdle());
        
        // Hook para fechar o pool no shutdown da JVM.
        // Em casos onde o pool é inicializado dentro de um shutdown hook, a JVM já está
        // encerrando e não permite registrar novos hooks.
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (!ds.isClosed()) {
                    logger.info("🔌 Fechando pool de conexões...");
                    ds.close();
                }
            }));
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
            return !DataSourceHolder.INSTANCE.isClosed() && 
                   DataSourceHolder.INSTANCE.getHikariPoolMXBean().getTotalConnections() > 0;
        } catch (final Exception e) {
            return false;
        }
    }

    /**
     * Constrói a URL do banco incluindo o databaseName se necessário.
     * PROBLEMA 3 CORRIGIDO: Usa CarregadorConfig.obterUrlBancoDados() em vez de System.getenv()
     */
    private static String obterUrlComDatabaseName() {
        String url = CarregadorConfig.obterUrlBancoDados();
        if (url == null) {
            return null;
        }
        
        url = url.trim();
        if (url.startsWith("jdbc:sqlserver://")) {
            final boolean temDatabaseName = url.toLowerCase().contains("databasename=");
            final boolean temDatabase = url.toLowerCase().contains("database=");
            if (!temDatabaseName && !temDatabase) {
                final String nomeBanco = CarregadorConfig.obterNomeBancoDados();
                if (nomeBanco != null && !nomeBanco.trim().isEmpty()) {
                    url = url.endsWith(";") 
                        ? url + "databaseName=" + nomeBanco.trim()
                        : url + ";databaseName=" + nomeBanco.trim();
                }
            }
        }
        return url;
    }

    /**
     * Obtém configuração int com fallback.
     */
    private static int obterConfigInt(final String envVar, final String propKey, final int defaultValue) {
        final String envValue = System.getenv(envVar);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Integer.parseInt(envValue);
            } catch (final NumberFormatException e) {
                logger.warn("Valor inválido para {}: '{}', usando padrão: {}", envVar, envValue, defaultValue);
            }
        }
        
        try {
            final String propValue = CarregadorConfig.obterPropriedade(propKey);
            if (propValue != null && !propValue.isEmpty()) {
                return Integer.parseInt(propValue);
            }
        } catch (final Exception e) {
            // Ignora e usa padrão
        }
        
        return defaultValue;
    }

    /**
     * Obtém configuração long com fallback.
     */
    private static long obterConfigLong(final String envVar, final String propKey, final long defaultValue) {
        final String envValue = System.getenv(envVar);
        if (envValue != null && !envValue.isEmpty()) {
            try {
                return Long.parseLong(envValue);
            } catch (final NumberFormatException e) {
                logger.warn("Valor inválido para {}: '{}', usando padrão: {}", envVar, envValue, defaultValue);
            }
        }
        
        try {
            final String propValue = CarregadorConfig.obterPropriedade(propKey);
            if (propValue != null && !propValue.isEmpty()) {
                return Long.parseLong(propValue);
            }
        } catch (final Exception e) {
            // Ignora e usa padrão
        }
        
        return defaultValue;
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
