package br.com.extrator;

import br.com.extrator.api.ClienteApiRest;
import br.com.extrator.api.ClienteApiGraphQL;
import br.com.extrator.api.ClienteApiDataExport;
import br.com.extrator.db.ServicoBancoDadosDinamico;
import br.com.extrator.modelo.EntidadeDinamica;
import br.com.extrator.util.TerminalCores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Sistema de Extração de Dados do ESL Cloud
 * 
 * Este sistema orquestra a extração de dados das 3 APIs do ESL Cloud:
 * - API REST: Faturas e Ocorrências
 * - API GraphQL: Coletas
 * - API Data Export: Manifestos e Localização da Carga
 * 
 * O sistema processa cada API com intervalos de 2 segundos entre elas,
 * salvando todos os dados no banco SQL Server local.
 * 
 * @author Sistema de Extração ESL Cloud
 * @version 2.0
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // Verifica se o usuário quer apenas validar os dados de acesso
        if (args.length > 0 && "--validar".equals(args[0])) {
            validarDadosAcesso();
            return;
        }
        // Exibe banner no console para melhor visualização
        exibirBanner();
        
        logger.info("Iniciando processo de extração de dados das 3 APIs do ESL Cloud");
        System.out.println(TerminalCores.titulo("[INICIANDO] Processo de extração de dados das 3 APIs do ESL Cloud"));
        
        try {
            // Verifica se as configurações foram personalizadas
            logger.info("Verificando configurações");
            System.out.println(TerminalCores.info("[ETAPA 1/6] Verificando configurações..."));
            br.com.extrator.util.CarregadorConfig.verificarConfiguracoesPersonalizadas();
            System.out.println(TerminalCores.sucesso("✓ Configurações validadas com sucesso!"));
            
            // Inicializa o serviço de banco de dados dinâmico
            logger.info("Inicializando banco de dados");
            System.out.println(TerminalCores.info("[ETAPA 2/6] Inicializando conexão com o banco de dados SQL Server..."));
            ServicoBancoDadosDinamico servicoBD = new ServicoBancoDadosDinamico();
            System.out.println(TerminalCores.sucesso("✓ Conexão com banco de dados estabelecida com sucesso!"));
            
            // Inicializa os clientes das APIs
            logger.info("Inicializando clientes das APIs");
            System.out.println(TerminalCores.info("[ETAPA 3/6] Inicializando clientes das 3 APIs ESL Cloud..."));
            ClienteApiRest clienteApiRest = new ClienteApiRest();
            // TEMPORARIAMENTE COMENTADO - APIs não utilizadas no teste focado
            // ClienteApiGraphQL clienteApiGraphQL = new ClienteApiGraphQL();
            // ClienteApiDataExport clienteApiDataExport = new ClienteApiDataExport();
            System.out.println(TerminalCores.sucesso("✓ Clientes das 3 APIs inicializados com sucesso!"));
            
            // Define o período de busca (padrão: últimas 24 horas)
            String dataBusca = null;
            if (args.length > 0) {
                dataBusca = args[0];
                logger.info("Data de busca fornecida via parâmetro: {}", dataBusca);
                System.out.println(TerminalCores.info("→ Data de busca fornecida: ") + TerminalCores.destaque(dataBusca));
            } else {
                LocalDateTime dataInicio = LocalDateTime.now().minusHours(24);
                dataBusca = dataInicio.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                logger.info("Usando data de busca padrão (últimas 24 horas): {}", dataBusca);
                System.out.println(TerminalCores.info("→ Usando data de busca padrão (últimas 24 horas): ") + TerminalCores.destaque(dataBusca));
            }
            
            int totalEntidadesExtraidas = 0;
            int totalEntidadesProcessadas = 0;
            
            // ========== ETAPA 4/9: EXTRAÇÃO DE FATURAS (API REST) ==========
            logger.info("Extraindo faturas da API REST");
            System.out.println(TerminalCores.info("\n[ETAPA 4/9] Extraindo Faturas da API REST..."));
            
            List<EntidadeDinamica> faturas = clienteApiRest.buscarFaturas(dataBusca);
            totalEntidadesExtraidas += faturas.size();
            
            logger.info("Extração de faturas concluída. Total encontrado: {}", faturas.size());
            System.out.println(TerminalCores.sucesso("✓ Extração de faturas concluída! Total: ") + TerminalCores.destaque(String.valueOf(faturas.size())));
            
            if (!faturas.isEmpty()) {
                logger.info("Salvando faturas no banco de dados");
                System.out.println(TerminalCores.info("→ Salvando faturas no banco SQL Server..."));
                
                int processados = servicoBD.salvarEntidades(faturas, "faturas");
                totalEntidadesProcessadas += processados;
                
                logger.info("Faturas salvas. Total processado: {}", processados);
                System.out.println(TerminalCores.sucesso("✓ Faturas salvas com sucesso! Total: ") + TerminalCores.destaque(String.valueOf(processados)));
            }
            
            /* TEMPORARIAMENTE COMENTADO PARA TESTE FOCADO EM FATURAS
            // Pausa obrigatória de 2 segundos entre APIs
            logger.info("Aguardando 2 segundos antes da próxima API...");
            System.out.println(TerminalCores.info("→ Aguardando 2 segundos..."));
            Thread.sleep(2000);
            
            // ========== ETAPA 5/9: EXTRAÇÃO DE OCORRÊNCIAS (API REST) ==========
            logger.info("Extraindo ocorrências da API REST");
            System.out.println(TerminalCores.info("\n[ETAPA 5/9] Extraindo Ocorrências da API REST..."));
            
            List<EntidadeDinamica> ocorrencias = clienteApiRest.buscarOcorrencias(dataBusca);
            totalEntidadesExtraidas += ocorrencias.size();
            
            logger.info("Extração de ocorrências concluída. Total encontrado: {}", ocorrencias.size());
            System.out.println(TerminalCores.sucesso("✓ Extração de ocorrências concluída! Total: ") + TerminalCores.destaque(String.valueOf(ocorrencias.size())));
            
            if (!ocorrencias.isEmpty()) {
                logger.info("Salvando ocorrências no banco de dados");
                System.out.println(TerminalCores.info("→ Salvando ocorrências no banco SQL Server..."));
                
                int processados = servicoBD.salvarEntidades(ocorrencias, "ocorrencias");
                totalEntidadesProcessadas += processados;
                
                logger.info("Ocorrências salvas. Total processado: {}", processados);
                System.out.println(TerminalCores.sucesso("✓ Ocorrências salvas com sucesso! Total: ") + TerminalCores.destaque(String.valueOf(processados)));
            }
            
            // Pausa obrigatória de 2 segundos entre APIs
            logger.info("Aguardando 2 segundos antes da próxima API...");
            System.out.println(TerminalCores.info("→ Aguardando 2 segundos..."));
            Thread.sleep(2000);
            
            // ========== ETAPA 6/9: EXTRAÇÃO DE COLETAS (API GRAPHQL) ==========
            logger.info("Extraindo coletas da API GraphQL");
            System.out.println(TerminalCores.info("\n[ETAPA 6/9] Extraindo Coletas da API GraphQL..."));
            
            List<EntidadeDinamica> coletas = clienteApiGraphQL.buscarColetas(dataBusca);
            totalEntidadesExtraidas += coletas.size();
            
            logger.info("Extração de coletas concluída. Total encontrado: {}", coletas.size());
            System.out.println(TerminalCores.sucesso("✓ Extração de coletas concluída! Total: ") + TerminalCores.destaque(String.valueOf(coletas.size())));
            
            if (!coletas.isEmpty()) {
                logger.info("Salvando coletas no banco de dados");
                System.out.println(TerminalCores.info("→ Salvando coletas no banco SQL Server..."));
                
                int processados = servicoBD.salvarEntidades(coletas, "coletas");
                totalEntidadesProcessadas += processados;
                
                logger.info("Coletas salvas. Total processado: {}", processados);
                System.out.println(TerminalCores.sucesso("✓ Coletas salvas com sucesso! Total: ") + TerminalCores.destaque(String.valueOf(processados)));
            }
            
            // Pausa obrigatória de 2 segundos entre APIs
            logger.info("Aguardando 2 segundos antes da próxima API...");
            System.out.println(TerminalCores.info("→ Aguardando 2 segundos..."));
            Thread.sleep(2000);
            
            // ========== ETAPA 7/9: EXTRAÇÃO DE MANIFESTOS (API DATA EXPORT) ==========
            logger.info("Extraindo manifestos da API Data Export");
            System.out.println(TerminalCores.info("\n[ETAPA 7/9] Extraindo Manifestos da API Data Export..."));
            
            List<EntidadeDinamica> manifestos = clienteApiDataExport.buscarManifestos(LocalDateTime.parse(dataBusca));
            totalEntidadesExtraidas += manifestos.size();
            
            logger.info("Extração de manifestos concluída. Total encontrado: {}", manifestos.size());
            System.out.println(TerminalCores.sucesso("✓ Extração de manifestos concluída! Total: ") + TerminalCores.destaque(String.valueOf(manifestos.size())));
            
            if (!manifestos.isEmpty()) {
                logger.info("Salvando manifestos no banco de dados");
                System.out.println(TerminalCores.info("→ Salvando manifestos no banco SQL Server..."));
                
                int processados = servicoBD.salvarEntidades(manifestos, "manifestos");
                totalEntidadesProcessadas += processados;
                
                logger.info("Manifestos salvos. Total processado: {}", processados);
                System.out.println(TerminalCores.sucesso("✓ Manifestos salvos com sucesso! Total: ") + TerminalCores.destaque(String.valueOf(processados)));
            }
            
            // ========== ETAPA 8/9: EXTRAÇÃO DE LOCALIZAÇÃO DA CARGA (API DATA EXPORT) ==========
            logger.info("Extraindo localização da carga da API Data Export");
            System.out.println(TerminalCores.info("\n[ETAPA 8/9] Extraindo Localização da Carga da API Data Export..."));
            
            List<EntidadeDinamica> localizacoes = clienteApiDataExport.buscarLocalizacaoCarga(LocalDateTime.parse(dataBusca));
            totalEntidadesExtraidas += localizacoes.size();
            
            logger.info("Extração de localização da carga concluída. Total encontrado: {}", localizacoes.size());
            System.out.println(TerminalCores.sucesso("✓ Extração de localização da carga concluída! Total: ") + TerminalCores.destaque(String.valueOf(localizacoes.size())));
            
            if (!localizacoes.isEmpty()) {
                logger.info("Salvando localização da carga no banco de dados");
                System.out.println(TerminalCores.info("→ Salvando localização da carga no banco SQL Server..."));
                
                int processados = servicoBD.salvarEntidades(localizacoes, "localizacao_carga");
                totalEntidadesProcessadas += processados;
                
                logger.info("Localização da carga salva. Total processado: {}", processados);
                System.out.println(TerminalCores.sucesso("✓ Localização da carga salva com sucesso! Total: ") + TerminalCores.destaque(String.valueOf(processados)));
            }
            FIM DO COMENTÁRIO TEMPORÁRIO */

            // ========== ETAPA 9/9: RESUMO FINAL ==========
            System.out.println(TerminalCores.info("\n[ETAPA 9/9] Gerando resumo final..."));
            
            // Resumo final
            if (totalEntidadesExtraidas == 0) {
                logger.info("Nenhuma entidade encontrada para o período especificado. Encerrando processo.");
                System.out.println("\n" + TerminalCores.aviso("[AVISO] Nenhuma entidade encontrada para o período especificado."));
                System.out.println("\n" + TerminalCores.info("[CONCLUÍDO] Processo finalizado sem erros, mas nenhum dado foi processado."));
                return;
            }
            
            logger.info("Processo de ETL das 3 APIs concluído com sucesso!");
            System.out.println("\n" + TerminalCores.FUNDO_VERDE + TerminalCores.NEGRITO + "[SUCESSO] Processo de ETL das 3 APIs concluído com sucesso!" + TerminalCores.RESET);
            System.out.println(TerminalCores.info("→ Total de entidades extraídas: ") + TerminalCores.destaque(String.valueOf(totalEntidadesExtraidas)));
            System.out.println(TerminalCores.info("→ Total de entidades processadas: ") + TerminalCores.destaque(String.valueOf(totalEntidadesProcessadas)));
            System.out.println(TerminalCores.info("→ APIs processadas: ") + TerminalCores.destaque("REST (Faturas + Ocorrências), GraphQL (Coletas), Data Export (Manifestos + Localização)"));
            System.out.println(TerminalCores.info("→ Logs detalhados disponíveis em: ") + TerminalCores.destaque("logs/extrator-esl.log"));
            
        } catch (Exception e) {
            logger.error("Erro durante o processo de ETL", e);
            System.out.println("\n" + TerminalCores.NEGRITO + "========== ERRO DE EXECUÇÃO ===========" + TerminalCores.RESET);
            System.out.println(TerminalCores.NEGRITO + "Problema: " + TerminalCores.RESET + TerminalCores.erro(e.getMessage()));
            
            // Verifica se é um erro de configuração não personalizada
            if (e.getMessage() != null && e.getMessage().contains("Configuração não personalizada")) {
                System.out.println("\n" + TerminalCores.NEGRITO + "SOLUÇÃO:" + TerminalCores.RESET);
                System.out.println("1. Abra o arquivo " + TerminalCores.destaque("src/main/resources/config.properties"));
                System.out.println("2. Substitua os valores entre colchetes pelos dados reais:");
                System.out.println("   - " + TerminalCores.destaque("[subdominio]") + " → Seu subdomínio na ESL Cloud");
                System.out.println("   - " + TerminalCores.destaque("[seu_bearer_token]") + " → Token de autenticação da API");
                System.out.println("   - " + TerminalCores.destaque("[servidor]") + " → Endereço do servidor SQL Server");
                System.out.println("   - " + TerminalCores.destaque("[nome_banco]") + " → Nome do banco de dados");
                System.out.println("   - " + TerminalCores.destaque("[usuario_banco]") + " → Usuário do banco de dados");
                System.out.println("   - " + TerminalCores.destaque("[senha_banco]") + " → Senha do banco de dados");
                System.out.println("\n3. Execute novamente o programa após configurar corretamente");
            }
            // Verifica se é um erro de conexão com o banco
            else if (e.getCause() != null && e.getCause().toString().contains("SQLServerException")) {
                System.out.println("\n" + TerminalCores.NEGRITO + "SOLUÇÃO:" + TerminalCores.RESET);
                System.out.println("1. Verifique se o servidor SQL Server está acessível");
                System.out.println("2. Confirme se as credenciais do banco de dados estão corretas");
                System.out.println("3. Verifique se o firewall permite conexões na porta 1433");
                System.out.println("4. Certifique-se que o formato da URL de conexão está correto");
            }
            // Erro genérico
            else {
                System.out.println("\n" + TerminalCores.NEGRITO + "DETALHES:" + TerminalCores.RESET);
                System.out.println("Tipo de erro: " + e.getClass().getSimpleName());
                if (e.getCause() != null) {
                    System.out.println("Causa: " + e.getCause().getMessage());
                }
            }
            
            System.out.println("\n" + TerminalCores.NEGRITO + "INFORMAÇÕES ADICIONAIS:" + TerminalCores.RESET);
            System.out.println("→ Consulte os logs para mais detalhes: " + TerminalCores.destaque("logs/extrator-esl.log"));
            System.out.println("→ Verifique o arquivo " + TerminalCores.destaque("INSTRUCOES.md") + " para instruções detalhadas");
            System.out.println(TerminalCores.NEGRITO + "=====================================" + TerminalCores.RESET);
            System.exit(1);
        }
    }

    /**
     * Valida apenas os dados de acesso à API ESL Cloud
     */
    private static void validarDadosAcesso() {
        exibirBannerValidacao();

        logger.info("Iniciando validação dos dados de acesso às 3 APIs do ESL Cloud");
        System.out.println(TerminalCores.titulo("[VALIDAÇÃO] Testando dados de acesso às 3 APIs do ESL Cloud"));

        try {
            // Verifica configurações
            logger.info("Verificando configurações");
            System.out.println(TerminalCores.info("[ETAPA 1/4] Verificando configurações..."));
            br.com.extrator.util.CarregadorConfig.verificarConfiguracoesPersonalizadas();
            System.out.println(TerminalCores.sucesso("✓ Configurações carregadas com sucesso!"));

            // Exibe informações das configurações (sem mostrar tokens completos por segurança)
            String urlBase = br.com.extrator.util.CarregadorConfig.obterUrlBaseApi();
            String tokenRest = br.com.extrator.util.CarregadorConfig.obterTokenApiRest();
            String tokenGraphQL = br.com.extrator.util.CarregadorConfig.obterTokenApiGraphQL();
            String tokenDataExport = br.com.extrator.util.CarregadorConfig.obterTokenApiDataExport();
            System.out.println(TerminalCores.info("→ Tenant: ") + TerminalCores.destaque(urlBase));
            System.out.println(TerminalCores.info("→ Token REST: ") + TerminalCores.destaque(tokenRest.substring(0, 10) + "..." + tokenRest.substring(tokenRest.length() - 5)));
            System.out.println(TerminalCores.info("→ Token GraphQL: ") + TerminalCores.destaque(tokenGraphQL.substring(0, 10) + "..." + tokenGraphQL.substring(tokenGraphQL.length() - 5)));
            System.out.println(TerminalCores.info("→ Token Data Export: ") + TerminalCores.destaque(tokenDataExport.substring(0, 10) + "..." + tokenDataExport.substring(tokenDataExport.length() - 5)));

            // Testa o acesso à API REST
            System.out.println(TerminalCores.info("[ETAPA 2/5] Testando acesso à API REST..."));
            ClienteApiRest clienteApiRest = new ClienteApiRest();
            boolean acessoRestValido = clienteApiRest.validarAcessoApi();

            if (acessoRestValido) {
                System.out.println(TerminalCores.sucesso("✓ API REST validada com sucesso!"));
            } else {
                System.out.println(TerminalCores.erro("✗ Falha na validação da API REST!"));
            }

            // Testa o acesso à API GraphQL
            System.out.println(TerminalCores.info("[ETAPA 3/5] Testando acesso à API GraphQL..."));
            ClienteApiGraphQL clienteApiGraphQL = new ClienteApiGraphQL();
            boolean acessoGraphQLValido = clienteApiGraphQL.validarAcessoApi();

            if (acessoGraphQLValido) {
                System.out.println(TerminalCores.sucesso("✓ API GraphQL validada com sucesso!"));
            } else {
                System.out.println(TerminalCores.erro("✗ Falha na validação da API GraphQL!"));
            }

            // Testa o acesso à API Data Export
            System.out.println(TerminalCores.info("[ETAPA 4/5] Testando acesso à API Data Export..."));
            ClienteApiDataExport clienteApiDataExport = new ClienteApiDataExport();
            boolean acessoDataExportValido = clienteApiDataExport.validarAcessoApi();

            if (acessoDataExportValido) {
                System.out.println(TerminalCores.sucesso("✓ API Data Export validada com sucesso!"));
            } else {
                System.out.println(TerminalCores.erro("✗ Falha na validação da API Data Export!"));
            }

            // Resumo da validação
            System.out.println(TerminalCores.info("[ETAPA 5/5] Resumo da validação..."));
            
            if (acessoRestValido && acessoGraphQLValido && acessoDataExportValido) {
                System.out.println(TerminalCores.sucesso("✓ Todas as validações foram bem-sucedidas!"));
                System.out.println("\n" + TerminalCores.FUNDO_VERDE + TerminalCores.NEGRITO + "[SUCESSO] Dados de acesso das 3 APIs validados com sucesso!" + TerminalCores.RESET);
                System.out.println(TerminalCores.info("→ Tenant: ") + TerminalCores.destaque("Acessível"));
                System.out.println(TerminalCores.info("→ API REST: ") + TerminalCores.destaque("Válida e autorizada (Faturas + Ocorrências)"));
                System.out.println(TerminalCores.info("→ API GraphQL: ") + TerminalCores.destaque("Válida e autorizada (Coletas)"));
                System.out.println(TerminalCores.info("→ API Data Export: ") + TerminalCores.destaque("Válida e autorizada (Manifestos + Localização)"));
                System.out.println(TerminalCores.info("→ Status: ") + TerminalCores.destaque("Pronto para extração de dados"));
            } else {
                System.out.println(TerminalCores.erro("✗ Algumas validações falharam!"));
                System.out.println("\n" + TerminalCores.NEGRITO + "========== ERRO DE VALIDAÇÃO ===========" + TerminalCores.RESET);
                System.out.println(TerminalCores.erro("Nem todas as APIs puderam ser acessadas"));

                System.out.println("\n" + TerminalCores.NEGRITO + "POSSÍVEIS CAUSAS:" + TerminalCores.RESET);
                System.out.println("1. " + TerminalCores.destaque("Tokens inválidos ou expirados"));
                System.out.println("2. " + TerminalCores.destaque("URL do tenant incorreta"));
                System.out.println("3. " + TerminalCores.destaque("Problemas de conectividade com a internet"));
                System.out.println("4. " + TerminalCores.destaque("Tokens sem permissões adequadas para as respectivas APIs"));

                System.out.println("\n" + TerminalCores.NEGRITO + "SOLUÇÃO:" + TerminalCores.RESET);
                System.out.println("1. Verifique se os tokens estão corretos no arquivo config.properties");
                System.out.println("2. Confirme se a URL do tenant está no formato correto");
                System.out.println("3. Verifique se cada token tem as permissões adequadas:");
                System.out.println("   - Token REST: Acesso a faturas e ocorrências");
                System.out.println("   - Token GraphQL: Acesso a coletas via GraphQL");
                System.out.println("   - Token Data Export: Acesso a manifestos e localização da carga");
                System.out.println("4. Entre em contato com o suporte da ESL Cloud se necessário");
                System.exit(1);
            }

        } catch (Exception e) {
            logger.error("Erro durante a validação dos dados de acesso", e);
            System.out.println("\n" + TerminalCores.NEGRITO + "========== ERRO DE VALIDAÇÃO ===========" + TerminalCores.RESET);
            System.out.println(TerminalCores.erro("Erro durante a validação: " + e.getMessage()));
            System.out.println("\n" + TerminalCores.NEGRITO + "INFORMAÇÕES ADICIONAIS:" + TerminalCores.RESET);
            System.out.println("→ Consulte os logs para mais detalhes: " + TerminalCores.destaque("logs/extrator-esl.log"));
            System.exit(1);
        }
    }

    /**
     * Exibe um banner específico para validação
     */
    private static void exibirBannerValidacao() {
        System.out.println("\n" + TerminalCores.NEGRITO + "=====================================" + TerminalCores.RESET);
        System.out.println(TerminalCores.NEGRITO + "   VALIDADOR DE ACESSO - ESL CLOUD   " + TerminalCores.RESET);
        System.out.println(TerminalCores.NEGRITO + "=====================================" + TerminalCores.RESET);

        // Exibe data e hora atual
        LocalDateTime agora = LocalDateTime.now();
        DateTimeFormatter formatador = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        System.out.println("Data/Hora: " + TerminalCores.destaque(agora.format(formatador)));

        // Exibe versão do sistema
        System.out.println("Versão: " + TerminalCores.destaque("1.0.0"));
        System.out.println(TerminalCores.NEGRITO + "=====================================" + TerminalCores.RESET + "\n");
    }

    /**
     * Exibe um banner de boas-vindas no console
     */
    private static void exibirBanner() {
        System.out.println("\n" + TerminalCores.NEGRITO + "=====================================" + TerminalCores.RESET);
        System.out.println(TerminalCores.NEGRITO + "    EXTRATOR DE DADOS - ESL CLOUD    " + TerminalCores.RESET);
        System.out.println(TerminalCores.NEGRITO + "=====================================" + TerminalCores.RESET);
        
        // Exibe data e hora atual
        LocalDateTime agora = LocalDateTime.now();
        DateTimeFormatter formatador = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        System.out.println("Data/Hora: " + TerminalCores.destaque(agora.format(formatador)));
        
        // Exibe versão do sistema
        System.out.println("Versão: " + TerminalCores.destaque("1.0.0"));
        System.out.println(TerminalCores.NEGRITO + "=====================================" + TerminalCores.RESET + "\n");
    }
}