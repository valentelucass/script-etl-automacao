package br.com.extrator.util.configuracao;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.util.banco.GerenciadorConexao;

/**
 * Classe responsÃ¡vel por carregar as configuraÃ§Ãµes do arquivo config.properties.
 * 
 * Thread-safe: utiliza o padrÃ£o Initialization-on-demand holder para
 * garantir carregamento Ãºnico e seguro das propriedades.
 * 
 * @author Sistema de ExtraÃ§Ã£o ESL Cloud
 * @version 2.0 - Thread-safe
 */
public final class CarregadorConfig {
    private static final Logger logger = LoggerFactory.getLogger(CarregadorConfig.class);
    private static final String ARQUIVO_CONFIG = "config.properties";

    /**
     * Holder pattern para carregamento lazy e thread-safe das propriedades.
     * A JVM garante que a classe interna sÃ³ serÃ¡ carregada quando acessada,
     * e o carregamento de classe Ã© thread-safe por especificaÃ§Ã£o.
     */
    private static final class PropertiesHolder {
        private static final Properties INSTANCE = carregarPropriedadesInterno();

        private static Properties carregarPropriedadesInterno() {
            final Properties props = new Properties();
            try (InputStream input = CarregadorConfig.class.getClassLoader().getResourceAsStream(ARQUIVO_CONFIG)) {
                if (input == null) {
                    logger.error("NÃ£o foi possÃ­vel encontrar o arquivo {}", ARQUIVO_CONFIG);
                    throw new RuntimeException("Arquivo de configuraÃ§Ã£o nÃ£o encontrado: " + ARQUIVO_CONFIG);
                }
                props.load(input);
                logger.info("Arquivo de configuraÃ§Ã£o carregado com sucesso (thread-safe)");
            } catch (final IOException ex) {
                logger.error("Erro ao carregar o arquivo de configuraÃ§Ã£o", ex);
                throw new RuntimeException("Erro ao carregar arquivo de configuraÃ§Ã£o", ex);
            }
            return props;
        }
    }

    private CarregadorConfig() {
        // Impede instanciaÃ§Ã£o
    }

    /**
     * Carrega as propriedades do arquivo de configuraÃ§Ã£o.
     * Thread-safe e lazy - carrega apenas no primeiro acesso.
     * 
     * @return Objeto Properties com as configuraÃ§Ãµes carregadas
     */
    public static Properties carregarPropriedades() {
        return PropertiesHolder.INSTANCE;
    }

    /**
     * Valida a conexÃ£o com o banco de dados
     * Testa se Ã© possÃ­vel conectar com as credenciais configuradas
     * 
     * @throws RuntimeException Se nÃ£o conseguir conectar ao banco
     */
    public static void validarConexaoBancoDados() {
        logger.info("Validando conexao com o banco de dados...");

        final String url = obterUrlBancoDados();
        final String usuario = obterUsuarioBancoDados();
        final String senha = obterSenhaBancoDados();

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

        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            // Testa se a conexao e valida
            if (conexao.isValid(5)) { // timeout de 5 segundos
                logger.info("Conexao com banco de dados validada com sucesso (via pool HikariCP)");
            } else {
                logger.error("Conexao com banco de dados invalida (via pool HikariCP)");
                throw new RuntimeException("Falha na validacao: Conexao com banco de dados invalida");
            }
        } catch (final SQLException e) {
            logger.error("Erro ao conectar com o banco de dados: {}", e.getMessage());
            throw criarErroConexaoBanco(e);
        } catch (final Throwable t) {
            logger.error("Falha ao inicializar pool ou obter conexao com o banco: {}", t.getMessage());
            throw criarErroConexaoBanco(t);
        }
    }

    /**
     * Converte diferentes tipos de falha de conexao/pool em RuntimeException consistente.
     */
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

    /**
     * Retorna a mensagem mais interna da cadeia de causas para melhorar diagnostico.
     */
    private static String extrairMensagemMaisInterna(final Throwable throwable) {
        Throwable atual = throwable;
        while (atual.getCause() != null && atual.getCause() != atual) {
            atual = atual.getCause();
        }
        final String mensagem = atual.getMessage();
        return (mensagem == null || mensagem.isBlank()) ? atual.getClass().getSimpleName() : mensagem;
    }

    /**
     * Valida se as tabelas essenciais existem no banco de dados.
     * Verifica: log_extracoes, page_audit, dim_usuarios
     * 
     * @throws IllegalStateException Se alguma tabela essencial nao existir
     */
    
    public static void validarTabelasEssenciais() {
        logger.info("ðŸ” Validando existÃªncia de tabelas essenciais no banco de dados...");
        
        final String[] tabelasEssenciais = {
            "log_extracoes",
            "page_audit",
            "dim_usuarios"
        };
        
        final StringBuilder tabelasFaltando = new StringBuilder();
        
        try (Connection conexao = GerenciadorConexao.obterConexao()) {
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
                    "âŒ ERRO CRÃTICO: As seguintes tabelas nÃ£o existem no banco de dados: %s. " +
                    "Execute 'database/executar_database.bat' antes de rodar a aplicaÃ§Ã£o. " +
                    "Veja database/README.md para instruÃ§Ãµes.",
                    tabelasFaltando.toString()
                );
                logger.error(mensagem);
                throw new IllegalStateException(mensagem);
            }
            
            logger.info("âœ… Todas as tabelas essenciais existem no banco de dados");
            
        } catch (final SQLException e) {
            logger.error("Erro ao validar tabelas essenciais: {}", e.getMessage());
            throw new RuntimeException("Falha ao validar tabelas essenciais", e);
        }
    }
    
    /**
     * Verifica se uma tabela existe no banco de dados.
     * 
     * @param conexao ConexÃ£o com o banco de dados
     * @param nomeTabela Nome da tabela a verificar
     * @return true se a tabela existe, false caso contrÃ¡rio
     * @throws SQLException Se houver erro ao consultar o banco
     */
    private static boolean tabelaExiste(final Connection conexao, final String nomeTabela) throws SQLException {
        final String sql = """
            SELECT COUNT(*) 
            FROM INFORMATION_SCHEMA.TABLES 
            WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = ?
            """;
        
        try (java.sql.PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, nomeTabela);
            try (java.sql.ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * ObtÃ©m uma configuraÃ§Ã£o obrigatÃ³ria exclusivamente de variÃ¡veis de ambiente.
     * Implementa lÃ³gica de fail-fast para dados sensÃ­veis.
     * 
     * @param nomeVariavelAmbiente Nome da variÃ¡vel de ambiente obrigatÃ³ria
     * @return Valor da variÃ¡vel de ambiente
     * @throws IllegalStateException Se a variÃ¡vel de ambiente nÃ£o existir ou estiver vazia
     */
    private static String obterConfiguracaoObrigatoria(final String nomeVariavelAmbiente) {
        final String valor = System.getenv(nomeVariavelAmbiente);
        
        if (valor == null || valor.trim().isEmpty()) {
            final String mensagem = String.format(
                "VariÃ¡vel de ambiente obrigatÃ³ria '%s' nÃ£o encontrada ou estÃ¡ vazia. " +
                "Configure esta variÃ¡vel de ambiente antes de executar a aplicaÃ§Ã£o.",
                nomeVariavelAmbiente
            );
            logger.error(mensagem);
            throw new IllegalStateException(mensagem);
        }
        
        logger.debug("ConfiguraÃ§Ã£o sensÃ­vel '{}' obtida da variÃ¡vel de ambiente", nomeVariavelAmbiente);
        return valor;
    }

    /**
     * ObtÃ©m uma configuraÃ§Ã£o priorizando variÃ¡veis de ambiente sobre o arquivo
     * config.properties. Para configuraÃ§Ãµes nÃ£o-sensÃ­veis.
     * 
     * @param nomeVariavelAmbiente Nome da variÃ¡vel de ambiente
     * @param nomeChaveProperties  Nome da chave no arquivo config.properties
     * @return Valor da configuraÃ§Ã£o (variÃ¡vel de ambiente ou fallback para
     *         properties)
     */
    private static String obterConfiguracao(final String nomeVariavelAmbiente, final String nomeChaveProperties) {
        // Tenta primeiro obter da variÃ¡vel de ambiente
        final String valorAmbiente = System.getenv(nomeVariavelAmbiente);
        if (valorAmbiente != null && !valorAmbiente.trim().isEmpty()) {
            logger.debug("ConfiguraÃ§Ã£o '{}' obtida da variÃ¡vel de ambiente", nomeVariavelAmbiente);
            return valorAmbiente;
        }

        // Fallback para o arquivo config.properties
        final Properties props = carregarPropriedades();
        final String valorProperties = props.getProperty(nomeChaveProperties);
        if (valorProperties == null) {
            logger.warn(
                    "ConfiguraÃ§Ã£o '{}' nÃ£o encontrada nem em variÃ¡vel de ambiente '{}' nem no arquivo de configuraÃ§Ã£o '{}'",
                    nomeChaveProperties, nomeVariavelAmbiente, nomeChaveProperties);
        } else {
            logger.debug("ConfiguraÃ§Ã£o '{}' obtida do arquivo config.properties", nomeChaveProperties);
        }
        return valorProperties;
    }

    /**
     * ObtÃ©m uma propriedade especÃ­fica do arquivo de configuraÃ§Ã£o
     * 
     * @param chave Nome da propriedade
     * @return Valor da propriedade
     */
    public static String obterPropriedade(final String chave) {
        final Properties props = carregarPropriedades();
        final String valor = props.getProperty(chave);
        if (valor == null) {
            logger.warn("Propriedade '{}' nÃ£o encontrada no arquivo de configuraÃ§Ã£o", chave);
        }
        return valor;
    }

    /**
     * ObtÃ©m a URL base da API
     * 
     * @return URL base da API
     */
    public static String obterUrlBaseApi() {
        return obterConfiguracao("API_BASEURL", "api.baseurl");
    }

    /**
     * ObtÃ©m o token de autenticaÃ§Ã£o da API REST
     * 
     * @return Token de autenticaÃ§Ã£o da API REST
     * @throws IllegalStateException Se a variÃ¡vel de ambiente API_REST_TOKEN nÃ£o estiver configurada
     */
    public static String obterTokenApiRest() {
        return obterConfiguracaoObrigatoria("API_REST_TOKEN");
    }

    /**
     * ObtÃ©m o token de autenticaÃ§Ã£o da API GraphQL
     * 
     * @return Token de autenticaÃ§Ã£o da API GraphQL
     * @throws IllegalStateException Se a variÃ¡vel de ambiente API_GRAPHQL_TOKEN nÃ£o estiver configurada
     */
    public static String obterTokenApiGraphQL() {
        return obterConfiguracaoObrigatoria("API_GRAPHQL_TOKEN");
    }

    /**
     * ObtÃ©m o endpoint da API GraphQL.
     * 
     * @return Endpoint da API GraphQL
     */
    public static String obterEndpointGraphQL() {
        return obterConfiguracao("API_GRAPHQL_ENDPOINT", "api.graphql.endpoint");
    }

    /**
     * ObtÃ©m o token da API Data Export.
     * 
     * @return Token da API Data Export
     * @throws IllegalStateException Se a variÃ¡vel de ambiente API_DATAEXPORT_TOKEN nÃ£o estiver configurada
     */
    public static String obterTokenApiDataExport() {
        return obterConfiguracaoObrigatoria("API_DATAEXPORT_TOKEN");
    }

    /**
     * ObtÃ©m a URL de conexÃ£o com o banco de dados
     * 
     * @return URL de conexÃ£o com o banco
     * @throws IllegalStateException Se a variÃ¡vel de ambiente DB_URL nÃ£o estiver configurada
     */
    public static String obterUrlBancoDados() {
        return obterConfiguracaoObrigatoria("DB_URL");
    }

    /**
     * ObtÃ©m o usuÃ¡rio do banco de dados
     * 
     * @return UsuÃ¡rio do banco
     * @throws IllegalStateException Se a variÃ¡vel de ambiente DB_USER nÃ£o estiver configurada
     */
    public static String obterUsuarioBancoDados() {
        return obterConfiguracaoObrigatoria("DB_USER");
    }

    /**
     * ObtÃ©m a senha do banco de dados
     * 
     * @return Senha do banco
     * @throws IllegalStateException Se a variÃ¡vel de ambiente DB_PASSWORD nÃ£o estiver configurada
     */
    public static String obterSenhaBancoDados() {
        return obterConfiguracaoObrigatoria("DB_PASSWORD");
    }
    
    /**
     * ObtÃ©m o nome do banco de dados alvo.
     * Prioriza variÃ¡vel de ambiente DB_NAME; fallback para config.properties (db.name).
     */
    public static String obterNomeBancoDados() {
        final String valor = obterConfiguracao("DB_NAME", "db.name");
        return valor;
    }

    /**
     * ObtÃ©m o tempo de espaÃ§amento padrÃ£o (throttling) entre requisiÃ§Ãµes em
     * milissegundos.
     * 
     * @return O tempo de throttling em ms.
     */
    public static long obterThrottlingPadrao() {
        final String valor = obterConfiguracao("API_THROTTLING_PADRAO_MS", "api.throttling.padrao_ms");
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn(
                    "Propriedade 'api.throttling.padrao_ms' nÃ£o encontrada ou invÃ¡lida. Usando valor padrÃ£o: 2000ms");
            return 2000L; // Valor padrÃ£o de 2 segundos
        }
    }

    /**
     * ObtÃ©m o nÃºmero mÃ¡ximo de tentativas para a lÃ³gica de retry.
     * 
     * @return O nÃºmero mÃ¡ximo de tentativas.
     */
    public static int obterMaxTentativasRetry() {
        final String valor = obterConfiguracao("API_RETRY_MAX_TENTATIVAS", "api.retry.max_tentativas");
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.retry.max_tentativas' nÃ£o encontrada ou invÃ¡lida. Usando valor padrÃ£o: 5");
            return 5; // Valor padrÃ£o
        }
    }

    /**
     * ObtÃ©m o tempo de espera base (em milissegundos) para a lÃ³gica de retry.
     * 
     * @return O tempo de delay base em ms.
     */
    public static long obterDelayBaseRetry() {
        final String valor = obterConfiguracao("API_RETRY_DELAY_BASE_MS", "api.retry.delay_base_ms");
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn(
                    "Propriedade 'api.retry.delay_base_ms' nÃ£o encontrada ou invÃ¡lida. Usando valor padrÃ£o: 2000ms");
            return 2000L; // Valor padrÃ£o de 2 segundos
        }
    }

    /**
     * ObtÃ©m o multiplicador para a estratÃ©gia de backoff exponencial.
     * 
     * @return O multiplicador.
     */
    public static double obterMultiplicadorRetry() {
        final String valor = obterConfiguracao("API_RETRY_MULTIPLICADOR", "api.retry.multiplicador");
        try {
            return Double.parseDouble(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.retry.multiplicador' nÃ£o encontrada ou invÃ¡lida. Usando valor padrÃ£o: 2.0");
            return 2.0; // Valor padrÃ£o
        }
    }

    /**
     * ObtÃ©m o timeout para requisiÃ§Ãµes da API REST em Duration.
     * 
     * @return Duration com o timeout configurado (padrÃ£o: 120 segundos)
     */
    public static java.time.Duration obterTimeoutApiRest() {
        final String valor = obterConfiguracao("API_REST_TIMEOUT_SECONDS", "api.rest.timeout.seconds");
        try {
            final long segundos = Long.parseLong(valor);
            return java.time.Duration.ofSeconds(segundos);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.rest.timeout.seconds' nÃ£o encontrada ou invÃ¡lida. Usando valor padrÃ£o: 120 segundos");
            return java.time.Duration.ofSeconds(120L); // Valor padrÃ£o de 120 segundos
        }
    }

    /**
     * ObtÃ©m o ID da corporaÃ§Ã£o para filtros GraphQL
     * 
     * @return ID da corporaÃ§Ã£o
     */
    public static String obterCorporationId() {
        return obterConfiguracao("API_CORPORATION_ID", "api.corporation.id");
    }

    /**
     * ObtÃ©m o intervalo mÃ­nimo de throttling (padrÃ£o: 2200ms)
     * Usado para evitar erros HTTP 429 (Rate Limit)
     * 
     * @return O intervalo mÃ­nimo de throttling em ms
     */
    public static long obterThrottlingMinimo() {
        final String valor = obterConfiguracao("API_THROTTLING_MINIMO_MS", "api.throttling.minimo_ms");
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.throttling.minimo_ms' nÃ£o encontrada ou invÃ¡lida. Usando valor padrÃ£o: 2200ms");
            return 2200L; // Valor padrÃ£o de 2.2 segundos (10% de margem sobre 2s)
        }
    }

    /**
     * ObtÃ©m o limite mÃ¡ximo de pÃ¡ginas por execuÃ§Ã£o para API REST
     * 
     * @return Limite mÃ¡ximo de pÃ¡ginas (padrÃ£o: 500)
     */
    public static int obterLimitePaginasApiRest() {
        final String valor = obterConfiguracao("API_REST_MAX_PAGINAS", "api.rest.max.paginas");
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.rest.max.paginas' nÃ£o encontrada ou invÃ¡lida. Usando valor padrÃ£o: 500");
            return 500; // Valor padrÃ£o aumentado de 100 para 500
        }
    }

    /**
     * ObtÃ©m o limite mÃ¡ximo de pÃ¡ginas por execuÃ§Ã£o para API GraphQL
     * 
     * @return Limite mÃ¡ximo de pÃ¡ginas (padrÃ£o: 2000)
     */
    public static int obterLimitePaginasApiGraphQL() {
        final String valor = obterConfiguracao("API_GRAPHQL_MAX_PAGINAS", "api.graphql.max.paginas");
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.graphql.max.paginas' nÃ£o encontrada ou invÃ¡lida. Usando valor padrÃ£o: 2000");
            return 2000; // Valor padrÃ£o aumentado de 1000 para 2000
        }
    }

    public static int obterLimitePaginasFaturasGraphQL() {
        final String valor = obterConfiguracao("API_GRAPHQL_FATURAS_MAX_PAGINAS", "api.graphql.faturas.max_paginas");
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.graphql.faturas.max_paginas' nÃ£o encontrada ou invÃ¡lida. Usando valor padrÃ£o: 200");
            return 200;
        }
    }

    public static int obterDiasJanelaFaturasGraphQL() {
        final String valor = obterConfiguracao("API_GRAPHQL_FATURAS_DIAS_JANELA", "api.graphql.faturas.dias_janela");
        try {
            final int dias = Integer.parseInt(valor);
            return dias <= 0 ? 2 : dias;
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.graphql.faturas.dias_janela' nÃ£o encontrada ou invÃ¡lida. Usando valor padrÃ£o: 2");
            return 2;
        }
    }

    /**
     * ObtÃ©m o limite mÃ¡ximo de pÃ¡ginas por execuÃ§Ã£o para API DataExport
     * 
     * @return Limite mÃ¡ximo de pÃ¡ginas (padrÃ£o: 500)
     */
    public static int obterLimitePaginasApiDataExport() {
        final String valor = obterConfiguracao("API_DATAEXPORT_MAX_PAGINAS", "api.dataexport.max.paginas");
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.dataexport.max.paginas' nÃ£o encontrada ou invÃ¡lida. Usando valor padrÃ£o: 500");
            return 500; // Valor padrÃ£o aumentado de 100 para 500
        }
    }

    /**
     * ObtÃ©m o limite mÃ¡ximo de REGISTROS por execuÃ§Ã£o para API GraphQL.
     * PROBLEMA #7 CORRIGIDO: Valor agora configurÃ¡vel em vez de hardcoded.
     * 
     * @return Limite mÃ¡ximo de registros (padrÃ£o: 50000)
     */
    public static int obterMaxRegistrosGraphQL() {
        final String valor = obterConfiguracao("API_GRAPHQL_MAX_REGISTROS", "api.graphql.max.registros.execucao");
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.debug("Propriedade 'api.graphql.max.registros.execucao' nÃ£o encontrada. Usando padrÃ£o: 50000");
            return 50000;
        }
    }

    /**
     * ObtÃ©m o limite mÃ¡ximo de REGISTROS por execuÃ§Ã£o para API DataExport.
     * PROBLEMA #7 CORRIGIDO: Valor agora configurÃ¡vel em vez de hardcoded.
     * 
     * @return Limite mÃ¡ximo de registros (padrÃ£o: 10000)
     */
    public static int obterMaxRegistrosDataExport() {
        final String valor = obterConfiguracao("API_DATAEXPORT_MAX_REGISTROS", "api.dataexport.max.registros.execucao");
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.debug("Propriedade 'api.dataexport.max.registros.execucao' nÃ£o encontrada. Usando padrÃ£o: 10000");
            return 10000;
        }
    }

    // ========== CONFIGURAÃ‡Ã•ES DE BANCO DE DADOS ==========

    /**
     * ObtÃ©m o tamanho do batch para commits no banco de dados.
     * Controla quantos registros sÃ£o processados antes de cada commit.
     * 
     * @return Tamanho do batch (padrÃ£o: 100)
     */
    public static int obterBatchSize() {
        final String valor = obterConfiguracao("DB_BATCH_SIZE", "db.batch.size");
        try {
            final int size = Integer.parseInt(valor);
            return size > 0 ? size : 100;
        } catch (NumberFormatException | NullPointerException e) {
            return 100;
        }
    }

    /**
     * Determina se a extraÃ§Ã£o deve continuar apÃ³s erro em um registro.
     * Se false, para na primeira falha. Se true, continua e loga os erros.
     * 
     * @return true para continuar apÃ³s erros (padrÃ£o: true)
     */
    public static boolean isContinuarAposErro() {
        final String valor = obterConfiguracao("DB_CONTINUE_ON_ERROR", "db.continue.on.error");
        if (valor == null || valor.isEmpty()) {
            return true; // PadrÃ£o: continuar
        }
        return Boolean.parseBoolean(valor);
    }

    /**
     * ObtÃ©m o delay entre extraÃ§Ãµes de entidades em milissegundos.
     * Usado para evitar sobrecarga nas APIs.
     * 
     * @return Delay em ms (padrÃ£o: 2000)
     */
    public static long obterDelayEntreExtracoes() {
        final String valor = obterConfiguracao("EXTRACAO_DELAY_MS", "extracao.delay.ms");
        try {
            final long delay = Long.parseLong(valor);
            return delay > 0 ? delay : 2000L;
        } catch (NumberFormatException | NullPointerException e) {
            return 2000L;
        }
    }

    /**
     * ObtÃ©m o timeout de validaÃ§Ã£o de conexÃ£o em segundos.
     * 
     * @return Timeout em segundos (padrÃ£o: 5)
     */
    public static int obterTimeoutValidacaoConexao() {
        final String valor = obterConfiguracao("DB_VALIDATION_TIMEOUT", "db.validation.timeout");
        try {
            final int timeout = Integer.parseInt(valor);
            return timeout > 0 ? timeout : 5;
        } catch (NumberFormatException | NullPointerException e) {
            return 5;
        }
    }

    // ========== FASE 4: CONFIGURAÃ‡Ã•ES DE ENRIQUECIMENTO DE FATURAS ==========

    /**
     * ObtÃ©m o nÃºmero de threads para processamento paralelo de resultados do enriquecimento.
     * IMPORTANTE: Essas threads sÃ£o para processamento de dados (parsing, mapeamento, salvamento),
     * NÃƒO para requisiÃ§Ãµes HTTP. As requisiÃ§Ãµes HTTP continuam sequenciais com throttling global de 2s.
     * 
     * @return NÃºmero de threads (padrÃ£o: 5)
     */
    public static int obterThreadsProcessamentoFaturas() {
        final String valor = obterConfiguracao("API_ENRIQUECIMENTO_FATURAS_THREADS", "api.enriquecimento.faturas.threads");
        try {
            final int threads = Integer.parseInt(valor);
            return threads > 0 ? threads : 5;
        } catch (NumberFormatException | NullPointerException e) {
            logger.debug("Propriedade 'api.enriquecimento.faturas.threads' nÃ£o encontrada. Usando padrÃ£o: 5");
            return 5;
        }
    }

    /**
     * ObtÃ©m o limite de erros consecutivos antes de aumentar delay.
     * 
     * @return Limite de erros (padrÃ£o: 10)
     */
    public static int obterLimiteErrosConsecutivos() {
        final String valor = obterConfiguracao("API_ENRIQUECIMENTO_ERROS_LIMITE", "api.enriquecimento.erros_consecutivos_limite");
        try {
            final int limite = Integer.parseInt(valor);
            return limite > 0 ? limite : 10;
        } catch (NumberFormatException | NullPointerException e) {
            return 10;
        }
    }

    /**
     * ObtÃ©m o multiplicador de delay quando hÃ¡ muitos erros consecutivos.
     * 
     * @return Multiplicador (padrÃ£o: 2.0)
     */
    public static double obterMultiplicadorDelayErros() {
        final String valor = obterConfiguracao("API_ENRIQUECIMENTO_DELAY_MULTIPLIER", "api.enriquecimento.delay_multiplier_erros");
        try {
            final double multiplicador = Double.parseDouble(valor);
            return multiplicador > 1.0 ? multiplicador : 2.0;
        } catch (NumberFormatException | NullPointerException e) {
            return 2.0;
        }
    }

    /**
     * ObtÃ©m o intervalo (em nÃºmero de faturas) para log de progresso.
     * 
     * @return Intervalo (padrÃ£o: 100)
     */
    public static int obterIntervaloLogProgressoEnriquecimento() {
        final String valor = obterConfiguracao("API_ENRIQUECIMENTO_INTERVALO_LOG", "api.enriquecimento.intervalo_log_progresso");
        try {
            final int intervalo = Integer.parseInt(valor);
            return intervalo > 0 ? intervalo : 100;
        } catch (NumberFormatException | NullPointerException e) {
            return 100;
        }
    }

    /**
     * ObtÃ©m o intervalo (em segundos) para heartbeat durante enriquecimento.
     * 
     * @return Intervalo em segundos (padrÃ£o: 10)
     */
    public static int obterHeartbeatSegundos() {
        final String valor = obterConfiguracao("API_ENRIQUECIMENTO_HEARTBEAT", "api.enriquecimento.heartbeat_segundos");
        try {
            final int segundos = Integer.parseInt(valor);
            return segundos > 0 ? segundos : 10;
        } catch (NumberFormatException | NullPointerException e) {
            return 10;
        }
    }

    /**
     * ObtÃƒÂ©m a quantidade mÃƒÂ¡xima de registros invÃƒÂ¡lidos tolerados por entidade
     * antes de marcar status INCOMPLETO_DADOS.
     *
     * @return Limite absoluto de invÃƒÂ¡lidos tolerados (padrÃƒÂ£o: 500)
     */
    public static int obterMaxInvalidosToleradosPorEntidade() {
        final String valor = obterConfiguracao("ETL_INVALIDOS_QUANTIDADE_MAX", "etl.invalidos.quantidade.max");
        try {
            final int limite = Integer.parseInt(valor);
            return limite >= 0 ? limite : 500;
        } catch (NumberFormatException | NullPointerException e) {
            return 500;
        }
    }

    /**
     * ObtÃƒÂ©m o percentual mÃƒÂ¡ximo de registros invÃƒÂ¡lidos tolerados por entidade
     * antes de marcar status INCOMPLETO_DADOS.
     *
     * @return Limite percentual de invÃƒÂ¡lidos tolerados (padrÃƒÂ£o: 2.5)
     */
    public static double obterPercentualMaxInvalidosToleradosPorEntidade() {
        final String valor = obterConfiguracao("ETL_INVALIDOS_PERCENTUAL_MAX", "etl.invalidos.percentual.max");
        try {
            final double limite = Double.parseDouble(valor);
            return limite >= 0 ? limite : 2.5d;
        } catch (NumberFormatException | NullPointerException e) {
            return 2.5d;
        }
    }

    /**
     * ObtÃƒÂ©m a quantidade mÃƒÂ¡xima de manifestos ÃƒÂ³rfÃƒÂ£os tolerados na validaÃƒÂ§ÃƒÂ£o
     * de integridade referencial.
     *
     * @return Limite absoluto de ÃƒÂ³rfÃƒÂ£os tolerados (padrÃƒÂ£o: 500)
     */
    public static int obterMaxOrfaosManifestosTolerados() {
        final String valor = obterConfiguracao(
            "ETL_REFERENCIAL_MANIFESTOS_ORFAOS_QUANTIDADE_MAX",
            "etl.referencial.manifestos.orfaos.quantidade.max"
        );
        try {
            final int limite = Integer.parseInt(valor);
            return limite >= 0 ? limite : 500;
        } catch (NumberFormatException | NullPointerException e) {
            return 500;
        }
    }

    /**
     * ObtÃƒÂ©m o percentual mÃƒÂ¡ximo de manifestos ÃƒÂ³rfÃƒÂ£os tolerados na validaÃƒÂ§ÃƒÂ£o
     * de integridade referencial.
     *
     * @return Limite percentual de ÃƒÂ³rfÃƒÂ£os tolerados (padrÃƒÂ£o: 35.0)
     */
    public static double obterPercentualMaxOrfaosManifestosTolerados() {
        final String valor = obterConfiguracao(
            "ETL_REFERENCIAL_MANIFESTOS_ORFAOS_PERCENTUAL_MAX",
            "etl.referencial.manifestos.orfaos.percentual.max"
        );
        try {
            final double limite = Double.parseDouble(valor);
            return limite >= 0 ? limite : 35.0d;
        } catch (NumberFormatException | NullPointerException e) {
            return 35.0d;
        }
    }

    /**
     * Number of extra retroactive days used in the pre-backfill of coletas
     * before strict referential validation in full extraction mode.
     *
     * Example: with standard 24h window [D-1, D], value 1 runs a pre-backfill
     * for day D-2 only.
     *
     * @return extra retroactive days for coletas backfill (default: 1)
     */
    public static int obterEtlReferencialColetasBackfillDias() {
        final String valor = obterConfiguracao(
            "ETL_REFERENCIAL_COLETAS_BACKFILL_DIAS",
            "etl.referencial.coletas.backfill.dias"
        );
        try {
            final int dias = Integer.parseInt(valor);
            return Math.max(0, dias);
        } catch (NumberFormatException | NullPointerException e) {
            return 1;
        }
    }

    /**
     * Controls whether automatic reconciliation runs after each daemon cycle.
     *
     * @return true when reconciliation is enabled (default: true)
     */
    public static boolean isLoopReconciliacaoAtiva() {
        final String valor = obterConfiguracao("LOOP_RECONCILIACAO_ATIVA", "loop.reconciliacao.ativa");
        if (valor == null || valor.isBlank()) {
            return true;
        }
        return Boolean.parseBoolean(valor.trim());
    }

    /**
     * Max pending reconciliations executed in each daemon cycle.
     *
     * @return max reconciliations per cycle (default: 2)
     */
    public static int obterLoopReconciliacaoMaxPorCiclo() {
        final String valor = obterConfiguracao("LOOP_RECONCILIACAO_MAX_POR_CICLO", "loop.reconciliacao.max_por_ciclo");
        try {
            final int maximo = Integer.parseInt(valor);
            return maximo > 0 ? maximo : 2;
        } catch (NumberFormatException | NullPointerException e) {
            return 2;
        }
    }

    /**
     * Number of retroactive days added as pending when the main cycle fails.
     *
     * @return retroactive days to schedule (default: 1)
     */
    public static int obterLoopReconciliacaoDiasRetroativosFalha() {
        final String valor = obterConfiguracao(
            "LOOP_RECONCILIACAO_DIAS_RETROATIVOS_FALHA",
            "loop.reconciliacao.dias_retroativos_falha"
        );
        try {
            final int dias = Integer.parseInt(valor);
            return Math.max(0, dias);
        } catch (NumberFormatException | NullPointerException e) {
            return 1;
        }
    }
}


