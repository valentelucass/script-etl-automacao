package br.com.extrator.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Classe responsável por carregar as configurações do arquivo config.properties
 */
public class CarregadorConfig {
    private static final Logger logger = LoggerFactory.getLogger(CarregadorConfig.class);
    private static final String ARQUIVO_CONFIG = "config.properties";
    private static Properties propriedades = null;

    /**
     * Carrega as propriedades do arquivo de configuração
     * 
     * @return Objeto Properties com as configurações carregadas
     */
    public static Properties carregarPropriedades() {
        if (propriedades == null) {
            propriedades = new Properties();
            try (InputStream input = CarregadorConfig.class.getClassLoader().getResourceAsStream(ARQUIVO_CONFIG)) {
                if (input == null) {
                    logger.error("Não foi possível encontrar o arquivo {}", ARQUIVO_CONFIG);
                    throw new RuntimeException("Arquivo de configuração não encontrado: " + ARQUIVO_CONFIG);
                }
                propriedades.load(input);
                logger.info("Arquivo de configuração carregado com sucesso");
            } catch (IOException ex) {
                logger.error("Erro ao carregar o arquivo de configuração", ex);
                throw new RuntimeException("Erro ao carregar arquivo de configuração", ex);
            }
        }
        return propriedades;
    }

    /**
     * Valida a conexão com o banco de dados
     * Testa se é possível conectar com as credenciais configuradas
     * 
     * @throws RuntimeException Se não conseguir conectar ao banco
     */
    public static void validarConexaoBancoDados() {
        logger.info("Validando conexão com o banco de dados...");

        String url = obterUrlBancoDados();
        String usuario = obterUsuarioBancoDados();
        String senha = obterSenhaBancoDados();

        if (url == null || url.trim().isEmpty()) {
            logger.error("URL do banco de dados não configurada");
            throw new RuntimeException("Configuração inválida: URL do banco de dados não pode estar vazia");
        }

        if (usuario == null || usuario.trim().isEmpty()) {
            logger.error("Usuário do banco de dados não configurado");
            throw new RuntimeException("Configuração inválida: Usuário do banco de dados não pode estar vazio");
        }

        if (senha == null || senha.trim().isEmpty()) {
            logger.error("Senha do banco de dados não configurada");
            throw new RuntimeException("Configuração inválida: Senha do banco de dados não pode estar vazia");
        }

        try (Connection conexao = DriverManager.getConnection(url, usuario, senha)) {
            // Testa se a conexão é válida
            if (conexao.isValid(5)) { // timeout de 5 segundos
                logger.info("✓ Conexão com banco de dados validada com sucesso");
            } else {
                logger.error("Conexão com banco de dados inválida");
                throw new RuntimeException("Falha na validação: Conexão com banco de dados inválida");
            }
        } catch (SQLException e) {
            logger.error("Erro ao conectar com o banco de dados: {}", e.getMessage());

            // Mensagens de erro mais específicas baseadas no código de erro
            String mensagemErro = "Erro de conexão com banco de dados: ";
            if (e.getMessage().contains("Login failed")) {
                mensagemErro += "Credenciais inválidas (usuário ou senha incorretos)";
            } else if (e.getMessage().contains("Cannot open database")) {
                mensagemErro += "Banco de dados não encontrado ou inacessível";
            } else if (e.getMessage().contains("The TCP/IP connection")) {
                mensagemErro += "Servidor de banco de dados inacessível (verifique URL e conectividade)";
            } else {
                mensagemErro += e.getMessage();
            }

            throw new RuntimeException(mensagemErro, e);
        }
    }

    /**
     * Obtém uma configuração obrigatória exclusivamente de variáveis de ambiente.
     * Implementa lógica de fail-fast para dados sensíveis.
     * 
     * @param nomeVariavelAmbiente Nome da variável de ambiente obrigatória
     * @return Valor da variável de ambiente
     * @throws IllegalStateException Se a variável de ambiente não existir ou estiver vazia
     */
    private static String obterConfiguracaoObrigatoria(String nomeVariavelAmbiente) {
        String valor = System.getenv(nomeVariavelAmbiente);
        
        if (valor == null || valor.trim().isEmpty()) {
            String mensagem = String.format(
                "Variável de ambiente obrigatória '%s' não encontrada ou está vazia. " +
                "Configure esta variável de ambiente antes de executar a aplicação.",
                nomeVariavelAmbiente
            );
            logger.error(mensagem);
            throw new IllegalStateException(mensagem);
        }
        
        logger.debug("Configuração sensível '{}' obtida da variável de ambiente", nomeVariavelAmbiente);
        return valor;
    }

    /**
     * Obtém uma configuração priorizando variáveis de ambiente sobre o arquivo
     * config.properties. Para configurações não-sensíveis.
     * 
     * @param nomeVariavelAmbiente Nome da variável de ambiente
     * @param nomeChaveProperties  Nome da chave no arquivo config.properties
     * @return Valor da configuração (variável de ambiente ou fallback para
     *         properties)
     */
    private static String obterConfiguracao(String nomeVariavelAmbiente, String nomeChaveProperties) {
        // Tenta primeiro obter da variável de ambiente
        String valorAmbiente = System.getenv(nomeVariavelAmbiente);
        if (valorAmbiente != null && !valorAmbiente.trim().isEmpty()) {
            logger.debug("Configuração '{}' obtida da variável de ambiente", nomeVariavelAmbiente);
            return valorAmbiente;
        }

        // Fallback para o arquivo config.properties
        Properties props = carregarPropriedades();
        String valorProperties = props.getProperty(nomeChaveProperties);
        if (valorProperties == null) {
            logger.warn(
                    "Configuração '{}' não encontrada nem em variável de ambiente '{}' nem no arquivo de configuração '{}'",
                    nomeChaveProperties, nomeVariavelAmbiente, nomeChaveProperties);
        } else {
            logger.debug("Configuração '{}' obtida do arquivo config.properties", nomeChaveProperties);
        }
        return valorProperties;
    }

    /**
     * Obtém uma propriedade específica do arquivo de configuração
     * 
     * @param chave Nome da propriedade
     * @return Valor da propriedade
     */
    public static String obterPropriedade(String chave) {
        Properties props = carregarPropriedades();
        String valor = props.getProperty(chave);
        if (valor == null) {
            logger.warn("Propriedade '{}' não encontrada no arquivo de configuração", chave);
        }
        return valor;
    }

    /**
     * Obtém a URL base da API
     * 
     * @return URL base da API
     */
    public static String obterUrlBaseApi() {
        return obterConfiguracao("API_BASEURL", "api.baseurl");
    }

    /**
     * Obtém o token de autenticação da API REST
     * 
     * @return Token de autenticação da API REST
     * @throws IllegalStateException Se a variável de ambiente API_REST_TOKEN não estiver configurada
     */
    public static String obterTokenApiRest() {
        return obterConfiguracaoObrigatoria("API_REST_TOKEN");
    }

    /**
     * Obtém o token de autenticação da API GraphQL
     * 
     * @return Token de autenticação da API GraphQL
     * @throws IllegalStateException Se a variável de ambiente API_GRAPHQL_TOKEN não estiver configurada
     */
    public static String obterTokenApiGraphQL() {
        return obterConfiguracaoObrigatoria("API_GRAPHQL_TOKEN");
    }

    /**
     * Obtém o endpoint da API GraphQL.
     * 
     * @return Endpoint da API GraphQL
     */
    public static String obterEndpointGraphQL() {
        return obterConfiguracao("API_GRAPHQL_ENDPOINT", "api.graphql.endpoint");
    }

    /**
     * Obtém o token da API Data Export.
     * 
     * @return Token da API Data Export
     * @throws IllegalStateException Se a variável de ambiente API_DATAEXPORT_TOKEN não estiver configurada
     */
    public static String obterTokenApiDataExport() {
        return obterConfiguracaoObrigatoria("API_DATAEXPORT_TOKEN");
    }

    /**
     * Obtém a URL de conexão com o banco de dados
     * 
     * @return URL de conexão com o banco
     * @throws IllegalStateException Se a variável de ambiente DB_URL não estiver configurada
     */
    public static String obterUrlBancoDados() {
        return obterConfiguracaoObrigatoria("DB_URL");
    }

    /**
     * Obtém o usuário do banco de dados
     * 
     * @return Usuário do banco
     * @throws IllegalStateException Se a variável de ambiente DB_USER não estiver configurada
     */
    public static String obterUsuarioBancoDados() {
        return obterConfiguracaoObrigatoria("DB_USER");
    }

    /**
     * Obtém a senha do banco de dados
     * 
     * @return Senha do banco
     * @throws IllegalStateException Se a variável de ambiente DB_PASSWORD não estiver configurada
     */
    public static String obterSenhaBancoDados() {
        return obterConfiguracaoObrigatoria("DB_PASSWORD");
    }

    /**
     * Obtém o tempo de espaçamento padrão (throttling) entre requisições em
     * milissegundos.
     * 
     * @return O tempo de throttling em ms.
     */
    public static long obterThrottlingPadrao() {
        String valor = obterConfiguracao("API_THROTTLING_PADRAO_MS", "api.throttling.padrao_ms");
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn(
                    "Propriedade 'api.throttling.padrao_ms' não encontrada ou inválida. Usando valor padrão: 2000ms");
            return 2000L; // Valor padrão de 2 segundos
        }
    }

    /**
     * Obtém o número máximo de tentativas para a lógica de retry.
     * 
     * @return O número máximo de tentativas.
     */
    public static int obterMaxTentativasRetry() {
        String valor = obterConfiguracao("API_RETRY_MAX_TENTATIVAS", "api.retry.max_tentativas");
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.retry.max_tentativas' não encontrada ou inválida. Usando valor padrão: 5");
            return 5; // Valor padrão
        }
    }

    /**
     * Obtém o tempo de espera base (em milissegundos) para a lógica de retry.
     * 
     * @return O tempo de delay base em ms.
     */
    public static long obterDelayBaseRetry() {
        String valor = obterConfiguracao("API_RETRY_DELAY_BASE_MS", "api.retry.delay_base_ms");
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn(
                    "Propriedade 'api.retry.delay_base_ms' não encontrada ou inválida. Usando valor padrão: 2000ms");
            return 2000L; // Valor padrão de 2 segundos
        }
    }

    /**
     * Obtém o multiplicador para a estratégia de backoff exponencial.
     * 
     * @return O multiplicador.
     */
    public static double obterMultiplicadorRetry() {
        String valor = obterConfiguracao("API_RETRY_MULTIPLICADOR", "api.retry.multiplicador");
        try {
            return Double.parseDouble(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.retry.multiplicador' não encontrada ou inválida. Usando valor padrão: 2.0");
            return 2.0; // Valor padrão
        }
    }

    /**
     * Obtém o ID da corporação para filtros GraphQL
     * 
     * @return ID da corporação
     */
    public static String obterCorporationId() {
        return obterConfiguracao("API_CORPORATION_ID", "api.corporation.id");
    }
}