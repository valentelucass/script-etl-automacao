package br.com.extrator.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
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
     * Verifica se as configurações ainda estão com valores padrão
     * Lança exceção se encontrar valores que precisam ser personalizados
     */
    public static void verificarConfiguracoesPersonalizadas() {
        // Garante que as propriedades foram carregadas
        if (propriedades == null) {
            carregarPropriedades();
        }

        // Verifica configurações da API REST
        String tokenRest = propriedades.getProperty("api.rest.token");
        if (tokenRest != null && tokenRest.contains("[seu_bearer_token]")) {
            logger.error("Configuração da API REST não personalizada: api.rest.token");
            throw new RuntimeException(
                    "Configuração não personalizada: Substitua [seu_bearer_token] no arquivo config.properties");
        }

        // Verifica configurações da API GraphQL
        String tokenGraphQL = propriedades.getProperty("api.graphql.token");
        if (tokenGraphQL != null && tokenGraphQL.contains("[seu_bearer_token]")) {
            logger.error("Configuração da API GraphQL não personalizada: api.graphql.token");
            throw new RuntimeException(
                    "Configuração não personalizada: Substitua [seu_bearer_token] no arquivo config.properties");
        }

        // Verifica configurações da API Data Export
        String tokenDataExport = propriedades.getProperty("api.dataexport.token");
        if (tokenDataExport != null && tokenDataExport.contains("[seu_bearer_token]")) {
            logger.error("Configuração da API Data Export não personalizada: api.dataexport.token");
            throw new RuntimeException(
                    "Configuração não personalizada: Substitua [seu_bearer_token] no arquivo config.properties");
        }

        // Verifica configurações do banco de dados
        String dbUrl = propriedades.getProperty("db.url");
        if (dbUrl != null && (dbUrl.contains("[servidor]") || dbUrl.contains("[nome_banco]"))) {
            logger.error("Configuração do banco de dados não personalizada: db.url");
            throw new RuntimeException(
                    "Configuração não personalizada: Substitua [servidor] e [nome_banco] no arquivo config.properties");
        }

        String dbUser = propriedades.getProperty("db.user");
        if (dbUser != null && dbUser.contains("[usuario_banco]")) {
            logger.error("Configuração do banco de dados não personalizada: db.user");
            throw new RuntimeException(
                    "Configuração não personalizada: Substitua [usuario_banco] no arquivo config.properties");
        }

        String dbPassword = propriedades.getProperty("db.password");
        if (dbPassword != null && dbPassword.contains("[senha_banco]")) {
            logger.error("Configuração do banco de dados não personalizada: db.password");
            throw new RuntimeException(
                    "Configuração não personalizada: Substitua [senha_banco] no arquivo config.properties");
        }
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
        return obterPropriedade("api.baseurl");
    }

    /**
     * Obtém o token de autenticação da API REST
     * 
     * @return Token de autenticação da API REST
     */
    public static String obterTokenApiRest() {
        return obterPropriedade("api.rest.token");
    }

    /**
     * Obtém o token de autenticação da API GraphQL
     * 
     * @return Token de autenticação da API GraphQL
     */
    public static String obterTokenApiGraphQL() {
        return obterPropriedade("api.graphql.token");
    }

    /**
     * Obtém o endpoint da API GraphQL.
     * 
     * @return Endpoint da API GraphQL
     */
    public static String obterEndpointGraphQL() {
        return obterPropriedade("api.graphql.endpoint");
    }

    /**
     * Obtém o token da API Data Export.
     * 
     * @return Token da API Data Export
     */
    public static String obterTokenApiDataExport() {
        return obterPropriedade("api.dataexport.token");
    }

    /**
     * Obtém a URL de conexão com o banco de dados
     * 
     * @return URL de conexão com o banco
     */
    public static String obterUrlBancoDados() {
        return obterPropriedade("db.url");
    }

    /**
     * Obtém o usuário do banco de dados
     * 
     * @return Usuário do banco
     */
    public static String obterUsuarioBancoDados() {
        return obterPropriedade("db.user");
    }

    /**
     * Obtém a senha do banco de dados
     * 
     * @return Senha do banco
     */
    public static String obterSenhaBancoDados() {
        return obterPropriedade("db.password");
    }
}