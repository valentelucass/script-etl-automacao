package br.com.extrator.testes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// mvn clean compile assembly:single
// java -jar target/extrator-esl-cloud-1.0-SNAPSHOT-jar-with-dependencies.jar --validar
// java -jar target/script-automacao-1.0-SNAPSHOT-jar-with-dependencies.jar --teste 2024-01-01


/**
 * Classe responsável por gerenciar o Modo de Teste do sistema de extração.
 * 
 * O Modo de Teste permite executar o fluxo completo de ponta a ponta (end-to-end)
 * limitando a extração a um número muito pequeno de registros (5) por entidade,
 * permitindo validar se todas as tabelas são criadas e populadas corretamente
 * com consumo mínimo da quota da API.
 */
public class TesteModoExecucao {
    
    private static final Logger logger = LoggerFactory.getLogger(TesteModoExecucao.class);
    
    // Configurações do modo de teste
    private static final int LIMITE_REGISTROS_TESTE = 5;
    private static final String ARGUMENTO_MODO_TESTE = "--teste";
    private static final String ARGUMENTO_TESTE_REST = "--teste-rest";
    private static final String ARGUMENTO_TESTE_GRAPHQL = "--teste-graphql";
    private static final String ARGUMENTO_TESTE_DATA_EXPORT = "--teste-data-export";
    
    // Flags específicas para cada tipo de teste
    private boolean testarRest = false;
    private boolean testarGraphQL = false;
    private boolean testarDataExport = false;
    private final String dataBusca;
    
    /**
     * Construtor que analisa os argumentos da linha de comando
     * 
     * @param args Argumentos da linha de comando
     */
    public TesteModoExecucao(String[] args) {
        if (args.length == 0) {
            // Modo normal sem argumentos
            this.dataBusca = obterDataAtual();
        } else if (ARGUMENTO_MODO_TESTE.equals(args[0])) {
            // Modo de teste completo (todas as APIs)
            this.testarRest = true;
            this.testarGraphQL = true;
            this.testarDataExport = true;
            
            // Se foi fornecida uma data específica para o teste
            if (args.length > 1 && !args[1].trim().isEmpty()) {
                this.dataBusca = args[1];
                logger.info("Modo de Teste COMPLETO ATIVADO com data específica: {}", this.dataBusca);
            } else {
                this.dataBusca = obterDataAtual();
                logger.info("Modo de Teste COMPLETO ATIVADO com data atual: {}", this.dataBusca);
            }
            
            logger.warn("=== MODO DE TESTE COMPLETO ATIVO ===");
            logger.warn("Limitando extração a {} registros por entidade", LIMITE_REGISTROS_TESTE);
            logger.warn("Testando todas as APIs: REST, GraphQL e Data Export");
            logger.warn("===============================");
            
        } else if (ARGUMENTO_TESTE_REST.equals(args[0])) {
            // Modo de teste apenas para API REST
            this.testarRest = true;
            
            if (args.length > 1 && !args[1].trim().isEmpty()) {
                this.dataBusca = args[1];
                logger.info("Modo de Teste REST ATIVADO com data específica: {}", this.dataBusca);
            } else {
                this.dataBusca = obterDataAtual();
                logger.info("Modo de Teste REST ATIVADO com data atual: {}", this.dataBusca);
            }
            
            logger.warn("=== MODO DE TESTE REST ATIVO ===");
            logger.warn("Testando apenas API REST (Faturas e Ocorrências)");
            logger.warn("Limitando extração a {} registros por entidade", LIMITE_REGISTROS_TESTE);
            logger.warn("===============================");
            
        } else if (ARGUMENTO_TESTE_GRAPHQL.equals(args[0])) {
            // Modo de teste apenas para API GraphQL
            this.testarGraphQL = true;
            
            if (args.length > 1 && !args[1].trim().isEmpty()) {
                this.dataBusca = args[1];
                logger.info("Modo de Teste GraphQL ATIVADO com data específica: {}", this.dataBusca);
            } else {
                this.dataBusca = obterDataAtual();
                logger.info("Modo de Teste GraphQL ATIVADO com data atual: {}", this.dataBusca);
            }
            
            logger.warn("=== MODO DE TESTE GRAPHQL ATIVO ===");
            logger.warn("Testando apenas API GraphQL (Coletas e Fretes)");
            logger.warn("Limitando extração a {} registros por entidade", LIMITE_REGISTROS_TESTE);
            logger.warn("===============================");
            
        } else if (ARGUMENTO_TESTE_DATA_EXPORT.equals(args[0])) {
            // Modo de teste apenas para API Data Export
            this.testarDataExport = true;
            
            if (args.length > 1 && !args[1].trim().isEmpty()) {
                this.dataBusca = args[1];
                logger.info("Modo de Teste Data Export ATIVADO com data específica: {}", this.dataBusca);
            } else {
                this.dataBusca = obterDataAtual();
                logger.info("Modo de Teste Data Export ATIVADO com data atual: {}", this.dataBusca);
            }
            
            logger.warn("=== MODO DE TESTE DATA EXPORT ATIVO ===");
            logger.warn("Testando apenas API Data Export (Manifestos e Localização)");
            logger.warn("Limitando extração a {} registros por entidade", LIMITE_REGISTROS_TESTE);
            logger.warn("===============================");
            
        } else {
            // Modo normal - primeiro argumento é a data
            this.dataBusca = args[0];
            logger.info("Modo normal ativado com data: {}", this.dataBusca);
        }
    }
    
    /**
     * Verifica se o modo de teste para API REST está ativo.
     * @return true se o teste da API REST deve ser executado
     */
    public boolean isTestarRest() {
        return testarRest;
    }

    /**
     * Verifica se o modo de teste para API GraphQL está ativo.
     * @return true se o teste da API GraphQL deve ser executado
     */
    public boolean isTestarGraphQL() {
        return testarGraphQL;
    }

    /**
     * Verifica se o modo de teste para API Data Export está ativo.
     * @return true se o teste da API Data Export deve ser executado
     */
    public boolean isTestarDataExport() {
        return testarDataExport;
    }

    /**
     * Verifica se está em modo normal (não está em nenhum modo de teste específico).
     * @return true se nenhuma flag de teste específica estiver ativa
     */
    public boolean isModoNormal() {
        return !testarRest && !testarGraphQL && !testarDataExport;
    }

    /**
     * Verifica se algum modo de teste está ativo.
     * @return true se pelo menos uma flag de teste estiver ativa
     */
    public boolean isModoTesteAtivo() {
        return testarRest || testarGraphQL || testarDataExport;
    }
    
    /**
     * Obtém a data de busca configurada
     * 
     * @return Data de busca no formato ISO
     */
    public String getDataBusca() {
        return dataBusca;
    }
    
    /**
     * Obtém o limite de registros para o modo de teste
     * 
     * @return Número máximo de registros por entidade no modo de teste
     */
    public int getLimiteRegistrosTeste() {
        return LIMITE_REGISTROS_TESTE;
    }
    
    /**
     * Exibe informações sobre o modo de execução atual
     */
    public void exibirInformacoesModo() {
        if (isModoTesteAtivo()) {
            System.out.println("=== MODO DE TESTE ATIVO ===");
            System.out.println("Data de busca: " + dataBusca);
            System.out.println("Limite por entidade: " + LIMITE_REGISTROS_TESTE + " registros");
            
            if (testarRest) {
                System.out.println("Testando: API REST (Faturas e Ocorrências)");
            }
            if (testarGraphQL) {
                System.out.println("Testando: API GraphQL (Coletas e Fretes)");
            }
            if (testarDataExport) {
                System.out.println("Testando: API Data Export (Manifestos e Localização)");
            }
            
            System.out.println("===============================");
        } else {
            System.out.println("=== MODO NORMAL ===");
            System.out.println("Data de busca: " + dataBusca);
            System.out.println("Extração completa habilitada");
            System.out.println("===================");
        }
    }
    
    /**
     * Valida se os argumentos fornecidos são válidos
     * 
     * @param args Argumentos da linha de comando
     * @return true se os argumentos são válidos
     */
    public static boolean validarArgumentos(String[] args) {
        if (args.length == 0) {
            return true; // Modo normal sem data específica
        }
        
        if (ARGUMENTO_MODO_TESTE.equals(args[0])) {
            return true; // Modo de teste válido
        }
        
        if (ARGUMENTO_TESTE_REST.equals(args[0])) {
            return true; // Modo de teste REST válido
        }
        
        if (ARGUMENTO_TESTE_GRAPHQL.equals(args[0])) {
            return true; // Modo de teste GraphQL válido
        }
        
        if (ARGUMENTO_TESTE_DATA_EXPORT.equals(args[0])) {
            return true; // Modo de teste Data Export válido
        }
        
        if ("--ajuda".equals(args[0]) || "--help".equals(args[0])) {
            return true; // Comando de ajuda válido
        }
        
        if (args.length == 1) {
            return true; // Modo normal com data específica
        }
        
        return false; // Argumentos inválidos
    }
    
    /**
     * Exibe a ajuda sobre como usar o modo de teste
     */
    public static void exibirAjuda() {
        System.out.println("=== EXTRATOR ESL CLOUD ===");
        System.out.println();
        System.out.println("Uso:");
        System.out.println("  java -jar extrator.jar                         # Modo normal com data atual");
        System.out.println("  java -jar extrator.jar \"2025-10-01\"            # Modo normal com data específica");
        System.out.println();
        System.out.println("Modos de Teste:");
        System.out.println("  java -jar extrator.jar --teste                 # Teste completo (todas as APIs)");
        System.out.println("  java -jar extrator.jar --teste \"2025-10-01\"    # Teste completo com data específica");
        System.out.println("  java -jar extrator.jar --teste-rest            # Teste apenas API REST");
        System.out.println("  java -jar extrator.jar --teste-rest \"2025-10-01\" # Teste API REST com data específica");
        System.out.println("  java -jar extrator.jar --teste-graphql         # Teste apenas API GraphQL");
        System.out.println("  java -jar extrator.jar --teste-graphql \"2025-10-01\" # Teste API GraphQL com data específica");
        System.out.println("  java -jar extrator.jar --teste-data-export     # Teste apenas API Data Export");
        System.out.println("  java -jar extrator.jar --teste-data-export \"2025-10-01\" # Teste API Data Export com data específica");
        System.out.println();
        System.out.println("Detalhes dos Modos de Teste:");
        System.out.println("  --teste           : Testa todas as APIs (REST + GraphQL + Data Export)");
        System.out.println("  --teste-rest      : Testa apenas Faturas a Receber/Pagar e Ocorrências");
        System.out.println("  --teste-graphql   : Testa apenas Coletas e Fretes");
        System.out.println("  --teste-data-export : Testa apenas Manifestos e Localização da Carga");
        System.out.println();
        System.out.println("Características dos Testes:");
        System.out.println("  - Limita extração a " + LIMITE_REGISTROS_TESTE + " registros por entidade");
        System.out.println("  - Ideal para validar funcionamento e economizar quota da API");
        System.out.println("  - Permite desenvolvimento e depuração mais eficientes");
        System.out.println();
        System.out.println("Configuração:");
        System.out.println("  O sistema prioriza variáveis de ambiente para maior segurança:");
        System.out.println("  - ESL_API_REST_URL, ESL_API_REST_TOKEN");
        System.out.println("  - ESL_API_GRAPHQL_URL, ESL_API_GRAPHQL_TOKEN");
        System.out.println("  - ESL_API_DATA_EXPORT_URL, ESL_API_DATA_EXPORT_TOKEN");
        System.out.println("  - ESL_DB_URL, ESL_DB_USERNAME, ESL_DB_PASSWORD");
        System.out.println("  Fallback: config.properties (para desenvolvimento local)");
        System.out.println();
        System.out.println("Logs e Monitoramento:");
        System.out.println("  - Arquivo de log: logs/extrator-esl.log");
        System.out.println("  - Níveis: INFO (operações), WARN (avisos), ERROR (erros)");
        System.out.println("  - Rotação automática por tamanho e data");
        System.out.println("  - Logs incluem timestamps, threads e contexto detalhado");
        System.out.println();
    }
    
    /**
     * Obtém a data atual no formato ISO
     * 
     * @return Data atual formatada
     */
    private String obterDataAtual() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}