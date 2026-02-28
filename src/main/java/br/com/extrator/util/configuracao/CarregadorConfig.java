/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/util/configuracao/CarregadorConfig.java
Classe  : CarregadorConfig (class)
Pacote  : br.com.extrator.util.configuracao
Modulo  : Utilitario compartilhado
Papel   : Implementa responsabilidade de carregador config.

Conecta com:
- GerenciadorConexao (util.banco)

Fluxo geral:
1) Centraliza funcoes auxiliares reutilizaveis.
2) Evita repeticao de logica transversal.
3) Apoia configuracao, formatacao e infraestrutura.

Estrutura interna:
Metodos principais:
- CarregadorConfig(): realiza operacao relacionada a "carregador config".
- carregarPropriedades(): realiza operacao relacionada a "carregar propriedades".
- validarConexaoBancoDados(): aplica regras de validacao e consistencia.
- criarErroConexaoBanco(...1 args): instancia ou monta estrutura de dados.
- extrairMensagemMaisInterna(...1 args): realiza operacao relacionada a "extrair mensagem mais interna".
- validarTabelasEssenciais(): aplica regras de validacao e consistencia.
- obterConfiguracaoObrigatoria(...1 args): recupera dados configurados ou calculados.
- obterConfiguracao(...2 args): recupera dados configurados ou calculados.
- obterPropriedade(...1 args): recupera dados configurados ou calculados.
- obterUrlBaseApi(): recupera dados configurados ou calculados.
- obterTokenApiRest(): recupera dados configurados ou calculados.
- obterTokenApiGraphQL(): recupera dados configurados ou calculados.
- obterEndpointGraphQL(): recupera dados configurados ou calculados.
- obterTokenApiDataExport(): recupera dados configurados ou calculados.
Atributos-chave:
- logger: logger da classe para diagnostico.
- ARQUIVO_CONFIG: configuracao aplicada no fluxo.
[DOC-FILE-END]============================================================== */

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
 * Classe responsavel por carregar as configuracoes do arquivo config.properties.
 * 
 * Thread-safe: utiliza o padrao Initialization-on-demand holder para
 * garantir carregamento unico e seguro das propriedades.
 * 
 * @author Sistema de Extracao ESL Cloud
 * @version 2.0 - Thread-safe
 */
public final class CarregadorConfig {
    private static final Logger logger = LoggerFactory.getLogger(CarregadorConfig.class);
    private static final String ARQUIVO_CONFIG = "config.properties";

    /**
     * Holder pattern para carregamento lazy e thread-safe das propriedades.
     * A JVM garante que a classe interna so sera carregada quando acessada,
     * e o carregamento de classe e thread-safe por especificacao.
     */
    private static final class PropertiesHolder {
        private static final Properties INSTANCE = carregarPropriedadesInterno();

        private static Properties carregarPropriedadesInterno() {
            final Properties props = new Properties();
            try (InputStream input = CarregadorConfig.class.getClassLoader().getResourceAsStream(ARQUIVO_CONFIG)) {
                if (input == null) {
                    logger.error("Nao foi possivel encontrar o arquivo {}", ARQUIVO_CONFIG);
                    throw new RuntimeException("Arquivo de configuracao nao encontrado: " + ARQUIVO_CONFIG);
                }
                props.load(input);
                logger.info("Arquivo de configuracao carregado com sucesso (thread-safe)");
            } catch (final IOException ex) {
                logger.error("Erro ao carregar o arquivo de configuracao", ex);
                throw new RuntimeException("Erro ao carregar arquivo de configuracao", ex);
            }
            return props;
        }
    }

    private CarregadorConfig() {
        // Impede instanciacao
    }

    /**
     * Carrega as propriedades do arquivo de configuracao.
     * Thread-safe e lazy - carrega apenas no primeiro acesso.
     * 
     * @return Objeto Properties com as configuracoes carregadas
     */
    public static Properties carregarPropriedades() {
        return PropertiesHolder.INSTANCE;
    }

    /**
     * Valida a conexao com o banco de dados
     * Testa se e possivel conectar com as credenciais configuradas
     * 
     * @throws RuntimeException Se nao conseguir conectar ao banco
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
        } catch (final RuntimeException t) {
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
        logger.info("Validando existencia de tabelas essenciais no banco de dados...");

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
                    "ERRO CRITICO: As seguintes tabelas nao existem no banco de dados: %s. "
                        + "Execute 'database/executar_database.bat' antes de rodar a aplicacao. "
                        + "Veja database/README.md para instrucoes.",
                    tabelasFaltando.toString()
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
    
    /**
     * Verifica se uma tabela existe no banco de dados.
     * 
     * @param conexao Conexao com o banco de dados
     * @param nomeTabela Nome da tabela a verificar
     * @return true se a tabela existe, false caso contrario
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
     * Obtem uma configuracao obrigatoria exclusivamente de variaveis de ambiente.
     * Implementa logica de fail-fast para dados sensiveis.
     * 
     * @param nomeVariavelAmbiente Nome da variavel de ambiente obrigatoria
     * @return Valor da variavel de ambiente
     * @throws IllegalStateException Se a variavel de ambiente nao existir ou estiver vazia
     */
    private static String obterConfiguracaoObrigatoria(final String nomeVariavelAmbiente) {
        final String valor = System.getenv(nomeVariavelAmbiente);
        
        if (valor == null || valor.trim().isEmpty()) {
            final String mensagem = String.format(
                "Variavel de ambiente obrigatoria '%s' nao encontrada ou esta vazia. " +
                "Configure esta variavel de ambiente antes de executar a aplicacao.",
                nomeVariavelAmbiente
            );
            logger.error(mensagem);
            throw new IllegalStateException(mensagem);
        }
        
        logger.debug("Configuracao sensivel '{}' obtida da variavel de ambiente", nomeVariavelAmbiente);
        return valor;
    }

    /**
     * Obtem uma configuracao priorizando variaveis de ambiente sobre o arquivo
     * config.properties. Para configuracoes nao-sensiveis.
     * 
     * @param nomeVariavelAmbiente Nome da variavel de ambiente
     * @param nomeChaveProperties  Nome da chave no arquivo config.properties
     * @return Valor da configuracao (variavel de ambiente ou fallback para
     *         properties)
     */
    private static String obterConfiguracao(final String nomeVariavelAmbiente, final String nomeChaveProperties) {
        // Tenta primeiro obter da variavel de ambiente
        final String valorAmbiente = System.getenv(nomeVariavelAmbiente);
        if (valorAmbiente != null && !valorAmbiente.trim().isEmpty()) {
            logger.debug("Configuracao '{}' obtida da variavel de ambiente", nomeVariavelAmbiente);
            return valorAmbiente;
        }

        // Fallback para o arquivo config.properties
        final Properties props = carregarPropriedades();
        final String valorProperties = props.getProperty(nomeChaveProperties);
        if (valorProperties == null) {
            logger.warn(
                    "Configuracao '{}' nao encontrada nem em variavel de ambiente '{}' nem no arquivo de configuracao '{}'",
                    nomeChaveProperties, nomeVariavelAmbiente, nomeChaveProperties);
        } else {
            logger.debug("Configuracao '{}' obtida do arquivo config.properties", nomeChaveProperties);
        }
        return valorProperties;
    }

    /**
     * Obtem uma propriedade especifica do arquivo de configuracao
     * 
     * @param chave Nome da propriedade
     * @return Valor da propriedade
     */
    public static String obterPropriedade(final String chave) {
        final Properties props = carregarPropriedades();
        final String valor = props.getProperty(chave);
        if (valor == null) {
            logger.warn("Propriedade '{}' nao encontrada no arquivo de configuracao", chave);
        }
        return valor;
    }

    /**
     * Obtem a URL base da API
     * 
     * @return URL base da API
     */
    public static String obterUrlBaseApi() {
        return obterConfiguracao("API_BASEURL", "api.baseurl");
    }

    /**
     * Obtem o token de autenticacao da API REST
     * 
     * @return Token de autenticacao da API REST
     * @throws IllegalStateException Se a variavel de ambiente API_REST_TOKEN nao estiver configurada
     */
    public static String obterTokenApiRest() {
        return obterConfiguracaoObrigatoria("API_REST_TOKEN");
    }

    /**
     * Obtem o token de autenticacao da API GraphQL
     * 
     * @return Token de autenticacao da API GraphQL
     * @throws IllegalStateException Se a variavel de ambiente API_GRAPHQL_TOKEN nao estiver configurada
     */
    public static String obterTokenApiGraphQL() {
        return obterConfiguracaoObrigatoria("API_GRAPHQL_TOKEN");
    }

    /**
     * Obtem o endpoint da API GraphQL.
     * 
     * @return Endpoint da API GraphQL
     */
    public static String obterEndpointGraphQL() {
        return obterConfiguracao("API_GRAPHQL_ENDPOINT", "api.graphql.endpoint");
    }

    /**
     * Obtem o token da API Data Export.
     * 
     * @return Token da API Data Export
     * @throws IllegalStateException Se a variavel de ambiente API_DATAEXPORT_TOKEN nao estiver configurada
     */
    public static String obterTokenApiDataExport() {
        return obterConfiguracaoObrigatoria("API_DATAEXPORT_TOKEN");
    }

    /**
     * Obtem a URL de conexao com o banco de dados
     * 
     * @return URL de conexao com o banco
     * @throws IllegalStateException Se a variavel de ambiente DB_URL nao estiver configurada
     */
    public static String obterUrlBancoDados() {
        return obterConfiguracaoObrigatoria("DB_URL");
    }

    /**
     * Obtem o usuario do banco de dados
     * 
     * @return Usuario do banco
     * @throws IllegalStateException Se a variavel de ambiente DB_USER nao estiver configurada
     */
    public static String obterUsuarioBancoDados() {
        return obterConfiguracaoObrigatoria("DB_USER");
    }

    /**
     * Obtem a senha do banco de dados
     * 
     * @return Senha do banco
     * @throws IllegalStateException Se a variavel de ambiente DB_PASSWORD nao estiver configurada
     */
    public static String obterSenhaBancoDados() {
        return obterConfiguracaoObrigatoria("DB_PASSWORD");
    }
    
    /**
     * Obtem o nome do banco de dados alvo.
     * Prioriza variavel de ambiente DB_NAME; fallback para config.properties (db.name).
     */
    public static String obterNomeBancoDados() {
        final String valor = obterConfiguracao("DB_NAME", "db.name");
        return valor;
    }

    /**
     * Obtem o tempo de espacamento padrao (throttling) entre requisicoes em
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
                    "Propriedade 'api.throttling.padrao_ms' nao encontrada ou invalida. Usando valor padrao: 2000ms");
            return 2000L; // Valor padrao de 2 segundos
        }
    }

    /**
     * Obtem o numero maximo de tentativas para a logica de retry.
     * 
     * @return O numero maximo de tentativas.
     */
    public static int obterMaxTentativasRetry() {
        final String valor = obterConfiguracao("API_RETRY_MAX_TENTATIVAS", "api.retry.max_tentativas");
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.retry.max_tentativas' nao encontrada ou invalida. Usando valor padrao: 5");
            return 5; // Valor padrao
        }
    }

    /**
     * Obtem o tempo de espera base (em milissegundos) para a logica de retry.
     * 
     * @return O tempo de delay base em ms.
     */
    public static long obterDelayBaseRetry() {
        final String valor = obterConfiguracao("API_RETRY_DELAY_BASE_MS", "api.retry.delay_base_ms");
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn(
                    "Propriedade 'api.retry.delay_base_ms' nao encontrada ou invalida. Usando valor padrao: 2000ms");
            return 2000L; // Valor padrao de 2 segundos
        }
    }

    /**
     * Obtem o multiplicador para a estrategia de backoff exponencial.
     * 
     * @return O multiplicador.
     */
    public static double obterMultiplicadorRetry() {
        final String valor = obterConfiguracao("API_RETRY_MULTIPLICADOR", "api.retry.multiplicador");
        try {
            return Double.parseDouble(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.retry.multiplicador' nao encontrada ou invalida. Usando valor padrao: 2.0");
            return 2.0; // Valor padrao
        }
    }

    /**
     * Obtem o timeout para requisicoes da API REST em Duration.
     * 
     * @return Duration com o timeout configurado (padrao: 120 segundos)
     */
    public static java.time.Duration obterTimeoutApiRest() {
        final String valor = obterConfiguracao("API_REST_TIMEOUT_SECONDS", "api.rest.timeout.seconds");
        try {
            final long segundos = Long.parseLong(valor);
            return java.time.Duration.ofSeconds(segundos);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.rest.timeout.seconds' nao encontrada ou invalida. Usando valor padrao: 120 segundos");
            return java.time.Duration.ofSeconds(120L); // Valor padrao de 120 segundos
        }
    }

    /**
     * Obtem o ID da corporacao para filtros GraphQL
     * 
     * @return ID da corporacao
     */
    public static String obterCorporationId() {
        return obterConfiguracao("API_CORPORATION_ID", "api.corporation.id");
    }

    /**
     * Obtem o intervalo minimo de throttling (padrao: 2200ms)
     * Usado para evitar erros HTTP 429 (Rate Limit)
     * 
     * @return O intervalo minimo de throttling em ms
     */
    public static long obterThrottlingMinimo() {
        final String valor = obterConfiguracao("API_THROTTLING_MINIMO_MS", "api.throttling.minimo_ms");
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.throttling.minimo_ms' nao encontrada ou invalida. Usando valor padrao: 2200ms");
            return 2200L; // Valor padrao de 2.2 segundos (10% de margem sobre 2s)
        }
    }

    /**
     * Obtem o limite maximo de paginas por execucao para API REST
     * 
     * @return Limite maximo de paginas (padrao: 500)
     */
    public static int obterLimitePaginasApiRest() {
        final String valor = obterConfiguracao("API_REST_MAX_PAGINAS", "api.rest.max.paginas");
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.rest.max.paginas' nao encontrada ou invalida. Usando valor padrao: 500");
            return 500; // Valor padrao aumentado de 100 para 500
        }
    }

    /**
     * Obtem o limite maximo de paginas por execucao para API GraphQL
     * 
     * @return Limite maximo de paginas (padrao: 2000)
     */
    public static int obterLimitePaginasApiGraphQL() {
        final String valor = obterConfiguracao("API_GRAPHQL_MAX_PAGINAS", "api.graphql.max.paginas");
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.graphql.max.paginas' nao encontrada ou invalida. Usando valor padrao: 2000");
            return 2000; // Valor padrao aumentado de 1000 para 2000
        }
    }

    public static int obterLimitePaginasFaturasGraphQL() {
        final String valor = obterConfiguracao("API_GRAPHQL_FATURAS_MAX_PAGINAS", "api.graphql.faturas.max_paginas");
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.graphql.faturas.max_paginas' nao encontrada ou invalida. Usando valor padrao: 200");
            return 200;
        }
    }

    public static int obterDiasJanelaFaturasGraphQL() {
        final String valor = obterConfiguracao("API_GRAPHQL_FATURAS_DIAS_JANELA", "api.graphql.faturas.dias_janela");
        try {
            final int dias = Integer.parseInt(valor);
            return dias <= 0 ? 2 : dias;
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.graphql.faturas.dias_janela' nao encontrada ou invalida. Usando valor padrao: 2");
            return 2;
        }
    }

    /**
     * Obtem o limite maximo de paginas por execucao para API DataExport
     * 
     * @return Limite maximo de paginas (padrao: 500)
     */
    public static int obterLimitePaginasApiDataExport() {
        final String valor = obterConfiguracao("API_DATAEXPORT_MAX_PAGINAS", "api.dataexport.max.paginas");
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.warn("Propriedade 'api.dataexport.max.paginas' nao encontrada ou invalida. Usando valor padrao: 500");
            return 500; // Valor padrao aumentado de 100 para 500
        }
    }

    /**
     * Obtem o limite maximo de REGISTROS por execucao para API GraphQL.
     * PROBLEMA #7 CORRIGIDO: Valor agora configuravel em vez de hardcoded.
     * 
     * @return Limite maximo de registros (padrao: 50000)
     */
    public static int obterMaxRegistrosGraphQL() {
        final String valor = obterConfiguracao("API_GRAPHQL_MAX_REGISTROS", "api.graphql.max.registros.execucao");
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.debug("Propriedade 'api.graphql.max.registros.execucao' nao encontrada. Usando padrao: 50000");
            return 50000;
        }
    }

    /**
     * Obtem o limite maximo de REGISTROS por execucao para API DataExport.
     * PROBLEMA #7 CORRIGIDO: Valor agora configuravel em vez de hardcoded.
     * 
     * @return Limite maximo de registros (padrao: 10000)
     */
    public static int obterMaxRegistrosDataExport() {
        final String valor = obterConfiguracao("API_DATAEXPORT_MAX_REGISTROS", "api.dataexport.max.registros.execucao");
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException | NullPointerException e) {
            logger.debug("Propriedade 'api.dataexport.max.registros.execucao' nao encontrada. Usando padrao: 10000");
            return 10000;
        }
    }

    // ========== CONFIGURACOES DE BANCO DE DADOS ==========

    /**
     * Obtem o tamanho do batch para commits no banco de dados.
     * Controla quantos registros sao processados antes de cada commit.
     * 
     * @return Tamanho do batch (padrao: 100)
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
     * Determina se a extracao deve continuar apos erro em um registro.
     * Se false, para na primeira falha. Se true, continua e loga os erros.
     * 
     * @return true para continuar apos erros (padrao: true)
     */
    public static boolean isContinuarAposErro() {
        final String valor = obterConfiguracao("DB_CONTINUE_ON_ERROR", "db.continue.on.error");
        if (valor == null || valor.isEmpty()) {
            return true; // Padrao: continuar
        }
        return Boolean.parseBoolean(valor);
    }

    /**
     * Obtem o delay entre extracoes de entidades em milissegundos.
     * Usado para evitar sobrecarga nas APIs.
     * 
     * @return Delay em ms (padrao: 2000)
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
     * Obtem o timeout de validacao de conexao em segundos.
     * 
     * @return Timeout em segundos (padrao: 5)
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

    // ========== FASE 4: CONFIGURACOES DE ENRIQUECIMENTO DE FATURAS ==========

    /**
     * Obtem o numero de threads para processamento paralelo de resultados do enriquecimento.
     * IMPORTANTE: Essas threads sao para processamento de dados (parsing, mapeamento, salvamento),
     * NAO para requisicoes HTTP. As requisicoes HTTP continuam sequenciais com throttling global de 2s.
     * 
     * @return Numero de threads (padrao: 5)
     */
    public static int obterThreadsProcessamentoFaturas() {
        final String valor = obterConfiguracao("API_ENRIQUECIMENTO_FATURAS_THREADS", "api.enriquecimento.faturas.threads");
        try {
            final int threads = Integer.parseInt(valor);
            return threads > 0 ? threads : 5;
        } catch (NumberFormatException | NullPointerException e) {
            logger.debug("Propriedade 'api.enriquecimento.faturas.threads' nao encontrada. Usando padrao: 5");
            return 5;
        }
    }

    /**
     * Obtem o limite de erros consecutivos antes de aumentar delay.
     * 
     * @return Limite de erros (padrao: 10)
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
     * Obtem o multiplicador de delay quando ha muitos erros consecutivos.
     * 
     * @return Multiplicador (padrao: 2.0)
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
     * Obtem o intervalo (em numero de faturas) para log de progresso.
     * 
     * @return Intervalo (padrao: 100)
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
     * Obtem o intervalo (em segundos) para heartbeat durante enriquecimento.
     * 
     * @return Intervalo em segundos (padrao: 10)
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
     * Obtem a quantidade maxima de registros invalidos tolerados por entidade
     * antes de marcar status INCOMPLETO_DADOS.
     *
     * @return Limite absoluto de invalidos tolerados (padrao: 500)
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
     * Obtem o percentual maximo de registros invalidos tolerados por entidade
     * antes de marcar status INCOMPLETO_DADOS.
     *
     * @return Limite percentual de invalidos tolerados (padrao: 2.5)
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
     * Obtem a quantidade maxima de manifestos orfaos tolerados na validacao
     * de integridade referencial.
     *
     * @return Limite absoluto de orfaos tolerados (padrao: 500)
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
     * Obtem o percentual maximo de manifestos orfaos tolerados na validacao
     * de integridade referencial.
     *
     * @return Limite percentual de orfaos tolerados (padrao: 35.0)
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



