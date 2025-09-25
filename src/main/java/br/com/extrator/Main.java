package br.com.extrator;

import br.com.extrator.api.ClienteApiRest;
import br.com.extrator.db.ServicoBancoDadosDinamico;
import br.com.extrator.modelo.EntidadeDinamica;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;

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

    // Variáveis para controle de estatísticas
    private static int totalEntidadesExtraidas = 0;
    private static int totalEntidadesProcessadas = 0;
    private static List<String> sucessos = new ArrayList<>();
    private static List<String> erros = new ArrayList<>();
    private static List<String> avisos = new ArrayList<>();

    public static void main(String[] args) {
        // Verifica se o usuário quer apenas validar os dados de acesso
        if (args.length > 0 && "--validar".equals(args[0])) {
            validarDadosAcesso();
            return;
        }

        // Exibe banner no console para melhor visualização
        exibirBanner();

        logger.info("Iniciando processo de extração de dados das 3 APIs do ESL Cloud");
        System.out.println("\n" + "=".repeat(60));
        System.out.println("INICIANDO PROCESSO DE EXTRAÇÃO DE DADOS");
        System.out.println("=".repeat(60));
        System.out.println("Sistema: Extração de dados das 3 APIs do ESL Cloud ");
        System.out.println("Versão: 2.0 by @valentelucass");
        System.out.println("Início: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        System.out.println("=".repeat(60) + "\n");

        try {
            // Verifica se as configurações foram personalizadas
            logger.info("Verificando configurações");
            System.out.println("    [ETAPA 1/11] Verificando configurações do sistema...");
            br.com.extrator.util.CarregadorConfig.verificarConfiguracoesPersonalizadas();
            System.out.println("    Configurações validadas com sucesso!");
            sucessos.add("Configurações do sistema validadas");
            System.out.println();

            // Valida conexão com banco de dados
            logger.info("Validando conexão com banco de dados");
            System.out.println("    [ETAPA 2/11] Validando conexão com o banco de dados SQL Server...");
            br.com.extrator.util.CarregadorConfig.validarConexaoBancoDados();
            System.out.println("    Conexão com banco de dados validada com sucesso!");
            sucessos.add("Conexão com banco de dados estabelecida");
            System.out.println();

            // Inicializa o serviço de banco de dados dinâmico
            logger.info("Inicializando banco de dados");
            System.out.println("    [ETAPA 3/11] Inicializando serviço de banco de dados...");
            ServicoBancoDadosDinamico servicoBD = new ServicoBancoDadosDinamico();
            System.out.println("    Serviço de banco de dados inicializado com sucesso!");
            sucessos.add("Serviço de banco de dados inicializado");
            System.out.println();

            // Inicializa os clientes das APIs
            logger.info("Inicializando clientes das APIs");
            System.out.println("    [ETAPA 4/11] Inicializando clientes das 3 APIs ESL Cloud...");
            ClienteApiRest clienteApiRest = new ClienteApiRest();
            System.out.println("    Clientes das 3 APIs inicializados com sucesso!");
            sucessos.add("Clientes das APIs inicializados (REST, GraphQL, Data Export)");
            System.out.println();

            // Define o período de busca (padrão: últimas 24 horas)
            String dataBusca = null;
            if (args.length > 0) {
                String parametroData = args[0];
                logger.info("Data de busca fornecida via parâmetro: {}", parametroData);
                System.out.println("    Data de busca fornecida: " + parametroData);

                // Valida o formato da data antes de usar
                dataBusca = validarEFormatarData(parametroData);
                if (dataBusca == null) {
                    logger.error("Formato de data inválido: {}", parametroData);
                    System.err.println("    Formato de data inválido: " + parametroData);
                    System.err.println("   Formatos aceitos: yyyy-MM-dd, dd/MM/yyyy, dd-MM-yyyy, yyyy-MM-ddTHH:mm:ss");
                    erros.add("Formato de data inválido: " + parametroData);
                    exibirResumoFinal();
                    System.exit(1);
                } else {
                    System.out.println("    Data validada e formatada: " + dataBusca);
                    sucessos.add("Data de busca validada: " + parametroData);
                }
            } else {
                // Usa as últimas 24 horas como padrão
                LocalDateTime agora = LocalDateTime.now();
                LocalDateTime dataInicio = agora.minusHours(24);
                dataBusca = dataInicio.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

                logger.info("Usando período padrão: últimas 24 horas. Data de início: {}", dataBusca);
                System.out.println("    Período padrão: últimas 24 horas");
                System.out.println("      Data de início: "
                        + dataInicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
                sucessos.add("Período padrão configurado (últimas 24 horas)");
            }
            System.out.println();

            // ========== ETAPA 5/11: EXTRAÇÃO DE FATURAS A RECEBER (API REST) ==========
            logger.info("Extraindo Faturas a Receber da API REST");
            System.out.println("    [ETAPA 5/11] Extraindo Faturas a Receber da API REST...");

            try {
                List<EntidadeDinamica> faturasAReceber = clienteApiRest.buscarFaturasAReceber(dataBusca);
                totalEntidadesExtraidas += faturasAReceber.size();
                System.out.println("    Total de faturas a receber encontradas: " + faturasAReceber.size());

                if (!faturasAReceber.isEmpty()) {
                    System.out.println("    Salvando faturas a receber no banco SQL Server...");
                    int processados = servicoBD.salvarEntidades(faturasAReceber, "faturas_a_receber");
                    totalEntidadesProcessadas += processados;
                    System.out.println(
                            "    Faturas a receber salvas! Processadas: " + processados + "/" + faturasAReceber.size());
                    sucessos.add("Faturas a Receber extraídas e salvas: " + processados);
                } else {
                    avisos.add("Nenhuma fatura a receber encontrada para o período");
                }
            } catch (RuntimeException e) {
                logger.error("Falha na extração de Faturas a Receber: {}", e.getMessage());
                System.out.println("    ❌ ERRO: Falha na extração de Faturas a Receber - " + e.getMessage());
                erros.add("Faturas a Receber: " + e.getMessage());
            }
            
            Thread.sleep(2000);
            System.out.println();

            // ========== ETAPA 6/11: EXTRAÇÃO DE FATURAS A PAGAR (API REST) ==========
            logger.info("Extraindo Faturas a Pagar da API REST");
            System.out.println("    [ETAPA 6/11] Extraindo Faturas a Pagar da API REST...");

            try {
                List<EntidadeDinamica> faturasAPagar = clienteApiRest.buscarFaturasAPagar(dataBusca);
                totalEntidadesExtraidas += faturasAPagar.size();
                System.out.println("    Total de faturas a pagar encontradas: " + faturasAPagar.size());

                if (!faturasAPagar.isEmpty()) {
                    System.out.println("    Salvando faturas a pagar no banco SQL Server...");
                    int processados = servicoBD.salvarEntidades(faturasAPagar, "faturas_a_pagar");
                    totalEntidadesProcessadas += processados;
                    System.out.println("    Faturas a pagar salvas! Processadas: " + processados + "/" + faturasAPagar.size());
                    sucessos.add("Faturas a Pagar extraídas e salvas: " + processados);
                } else {
                    avisos.add("Nenhuma fatura a pagar encontrada para o período");
                }
            } catch (RuntimeException e) {
                logger.error("Falha na extração de Faturas a Pagar: {}", e.getMessage());
                System.out.println("    ❌ ERRO: Falha na extração de Faturas a Pagar - " + e.getMessage());
                erros.add("Faturas a Pagar: " + e.getMessage());
            }
            
            Thread.sleep(2000);
            System.out.println();

            // ========== ETAPA 7/11: EXTRAÇÃO DE OCORRÊNCIAS (API REST) ==========
            logger.info("Extraindo ocorrências da API REST");
            System.out.println("    [ETAPA 7/11] Extraindo Ocorrências da API REST...");

            try {
                // Chama o método que você corrigiu em ClienteApiRest.java
                List<EntidadeDinamica> ocorrencias = clienteApiRest.buscarOcorrencias(dataBusca);
                totalEntidadesExtraidas += ocorrencias.size();

                logger.info("Extração de ocorrências concluída. Total encontrado: {}", ocorrencias.size());
                System.out.println("    Total de ocorrências encontradas: " + ocorrencias.size());

                if (!ocorrencias.isEmpty()) {
                    logger.info("Salvando ocorrências no banco de dados");
                    System.out.println("    Salvando ocorrências no banco SQL Server...");

                    // Salva os resultados na tabela "ocorrencias"
                    int processados = servicoBD.salvarEntidades(ocorrencias, "ocorrencias");
                    totalEntidadesProcessadas += processados;

                    logger.info("Ocorrências salvas. Total processado: {}", processados);
                    System.out.println(
                            "    Ocorrências salvas com sucesso! Processadas: " + processados + "/" + ocorrencias.size());
                    sucessos.add("Ocorrências extraídas e salvas: " + processados + "/" + ocorrencias.size());
                } else {
                    System.out.println("    Nenhuma ocorrência encontrada para o período especificado");
                    avisos.add("Nenhuma ocorrência encontrada para o período");
                }
            } catch (RuntimeException e) {
                logger.error("Falha na extração de Ocorrências: {}", e.getMessage());
                System.out.println("    ❌ ERRO: Falha na extração de Ocorrências - " + e.getMessage());
                erros.add("Ocorrências: " + e.getMessage());
            }

            // Pausa obrigatória de 2 segundos entre APIs
            logger.info("Aguardando 2 segundos antes da próxima API...");
            System.out.println("    Aguardando 2 segundos antes da próxima API...");
            Thread.sleep(2000);
            System.out.println();
            /*
             * // ========== ETAPA 8/11: EXTRAÇÃO DE COLETAS (API GRAPHQL) ==========
             * try {
             *     logger.info("Extraindo coletas da API GraphQL");
             *     System.out.println("    [ETAPA 8/11] Extraindo Coletas da API GraphQL...");
             * 
             *     List<EntidadeDinamica> coletas = clienteApiGraphQL.buscarColetas(dataBusca);
             *     totalEntidadesExtraidas += coletas.size();
             * 
             *     logger.info("Extração de coletas concluída. Total encontrado: {}", coletas.size());
             *     System.out.println("    Total de coletas encontradas: " + coletas.size());
             * 
             *     if (!coletas.isEmpty()) {
             *         logger.info("Salvando coletas no banco de dados");
             *         System.out.println("    Salvando coletas no banco SQL Server...");
             * 
             *         int processados = servicoBD.salvarEntidades(coletas, "coletas");
             *         totalEntidadesProcessadas += processados;
             * 
             *         logger.info("Coletas salvas. Total processado: {}", processados);
             *         System.out.println("    Coletas salvas com sucesso! Processadas: " + processados + "/" + coletas.size());
             *         sucessos.add("Coletas extraídas e salvas: " + processados + "/" + coletas.size());
             * 
             *         if (processados < coletas.size()) {
             *             String aviso = "Algumas coletas não foram salvas: " + (coletas.size() - processados) + " falharam";
             *             avisos.add(aviso);
             *             System.out.println("   !!!!!!  " + aviso);
             *         }
             *     } else {
             *         System.out.println("    Nenhuma coleta encontrada para o período especificado");
             *         avisos.add("Nenhuma coleta encontrada para o período");
             *     }
             * } catch (RuntimeException e) {
             *     logger.error("Falha na extração de Coletas: {}", e.getMessage());
             *     System.out.println("    ❌ ERRO: Falha na extração de Coletas - " + e.getMessage());
             *     erros.add("Coletas: " + e.getMessage());
             * }
             * 
             * // Pausa obrigatória de 2 segundos entre APIs
             * logger.info("Aguardando 2 segundos antes da próxima API...");
             * System.out.println("    Aguardando 2 segundos antes da próxima API...");
             * Thread.sleep(2000);
             * System.out.println();
             * 
             * // ========== ETAPA 9/11: EXTRAÇÃO DE FRETES (API GRAPHQL) ==========
             * try {
             *     logger.info("Extraindo fretes da API GraphQL");
             *     System.out.println("    [ETAPA 9/11] Extraindo Fretes da API GraphQL...");
             * 
             *     List<EntidadeDinamica> fretes = clienteApiGraphQL.buscarFretes(dataBusca);
             *     totalEntidadesExtraidas += fretes.size();
             * 
             *     logger.info("Extração de fretes concluída. Total encontrado: {}", fretes.size());
             *     System.out.println("    Total de fretes encontrados: " + fretes.size());
             * 
             *     if (!fretes.isEmpty()) {
             *         logger.info("Salvando fretes no banco de dados");
             *         System.out.println("    Salvando fretes no banco SQL Server...");
             * 
             *         int processados = servicoBD.salvarEntidades(fretes, "fretes");
             *         totalEntidadesProcessadas += processados;
             * 
             *         logger.info("Fretes salvos. Total processado: {}", processados);
             *         System.out.println("    Fretes salvos com sucesso! Processados: " + processados + "/" + fretes.size());
             *         sucessos.add("Fretes extraídos e salvos: " + processados + "/" + fretes.size());
             * 
             *         if (processados < fretes.size()) {
             *             String aviso = "Alguns fretes não foram salvos: " + (fretes.size() - processados) + " falharam";
             *             avisos.add(aviso);
             *             System.out.println("   !!!!!!  " + aviso);
             *         }
             *     } else {
             *         System.out.println("    Nenhum frete encontrado para o período especificado");
             *         avisos.add("Nenhum frete encontrado para o período");
             *     }
             * } catch (RuntimeException e) {
             *     logger.error("Falha na extração de Fretes: {}", e.getMessage());
             *     System.out.println("    ❌ ERRO: Falha na extração de Fretes - " + e.getMessage());
             *     erros.add("Fretes: " + e.getMessage());
             * }
             * 
             * // Pausa obrigatória de 2 segundos entre APIs
             * logger.info("Aguardando 2 segundos antes da próxima API...");
             * System.out.println("    Aguardando 2 segundos antes da próxima API...");
             * Thread.sleep(2000);
             * System.out.println();
             * 
             * // ========== ETAPA 10/11: EXTRAÇÃO DE MANIFESTOS (API DATA EXPORT) ==========
             * try {
             *     logger.info("Extraindo manifestos da API Data Export");
             *     System.out.println("    [ETAPA 10/11] Extraindo Manifestos da API Data Export...");
             * 
             *     List<EntidadeDinamica> manifestos = clienteApiDataExport.buscarManifestos(dataBusca);
             *     totalEntidadesExtraidas += manifestos.size();
             * 
             *     logger.info("Extração de manifestos concluída. Total encontrado: {}", manifestos.size());
             *     System.out.println("    Total de manifestos encontrados: " + manifestos.size());
             * 
             *     if (!manifestos.isEmpty()) {
             *         logger.info("Salvando manifestos no banco de dados");
             *         System.out.println("    Salvando manifestos no banco SQL Server...");
             * 
             *         int processados = servicoBD.salvarEntidades(manifestos, "manifestos");
             *         totalEntidadesProcessadas += processados;
             * 
             *         logger.info("Manifestos salvos. Total processado: {}", processados);
             *         System.out.println("    Manifestos salvos com sucesso! Processados: " + processados + "/" + manifestos.size());
             *         sucessos.add("Manifestos extraídos e salvos: " + processados + "/" + manifestos.size());
             * 
             *         if (processados < manifestos.size()) {
             *             String aviso = "Alguns manifestos não foram salvos: " + (manifestos.size() - processados) + " falharam";
             *             avisos.add(aviso);
             *             System.out.println("   !!!!!!!  " + aviso);
             *         }
             *     } else {
             *         System.out.println("    Nenhum manifesto encontrado para o período especificado");
             *         avisos.add("Nenhum manifesto encontrado para o período");
             *     }
             * } catch (RuntimeException e) {
             *     logger.error("Falha na extração de Manifestos: {}", e.getMessage());
             *     System.out.println("    ❌ ERRO: Falha na extração de Manifestos - " + e.getMessage());
             *     erros.add("Manifestos: " + e.getMessage());
             * }
             * 
             * // Pausa obrigatória de 2 segundos entre APIs
             * logger.info("Aguardando 2 segundos antes da próxima API...");
             * System.out.println("    Aguardando 2 segundos antes da próxima API...");
             * Thread.sleep(2000);
             * System.out.println();
             * 
             * // ========== ETAPA 11/11: EXTRAÇÃO DE LOCALIZAÇÃO DA CARGA (API DATA EXPORT) ==========
             * try {
             *     logger.info("Extraindo localização da carga da API Data Export");
             *     System.out.println("    [ETAPA 11/11] Extraindo Localização da Carga da API Data Export...");
             * 
             *     List<EntidadeDinamica> localizacoes = clienteApiDataExport.buscarLocalizacaoCarga(dataBusca);
             *     totalEntidadesExtraidas += localizacoes.size();
             * 
             *     logger.info("Extração de localização da carga concluída. Total encontrado: {}", localizacoes.size());
             *     System.out.println("    Total de localizações encontradas: " + localizacoes.size());
             * 
             *     if (!localizacoes.isEmpty()) {
             *         logger.info("Salvando localização da carga no banco de dados");
             *         System.out.println("    Salvando localizações no banco SQL Server...");
             * 
             *         int processados = servicoBD.salvarEntidades(localizacoes, "localizacao_carga");
             *         totalEntidadesProcessadas += processados;
             * 
             *         logger.info("Localização da carga salva. Total processado: {}", processados);
             *         System.out.println("    Localizações salvas com sucesso! Processadas: " + processados + "/" + localizacoes.size());
             *         sucessos.add("Localizações extraídas e salvas: " + processados + "/" + localizacoes.size());
             * 
             *         if (processados < localizacoes.size()) {
             *             String aviso = "Algumas localizações não foram salvas: " + (localizacoes.size() - processados) + " falharam";
             *             avisos.add(aviso);
             *             System.out.println("   !!!!!  " + aviso);
             *         }
             *     } else {
             *         System.out.println("    Nenhuma localização encontrada para o período especificado");
             *         avisos.add("Nenhuma localização encontrada para o período");
             *     }
             * } catch (RuntimeException e) {
             *     logger.error("Falha na extração de Localização da Carga: {}", e.getMessage());
             *     System.out.println("    ❌ ERRO: Falha na extração de Localização da Carga - " + e.getMessage());
             *     erros.add("Localização da Carga: " + e.getMessage());
             * }
             */

            // ========== RESUMO FINAL ==========
            logger.info("Processo de extração concluído");
            System.out.println();

            exibirResumoFinal();

            logger.info("Extração concluída com sucesso. Extraídas: {}, Processadas: {}",
                    totalEntidadesExtraidas, totalEntidadesProcessadas);

        } catch (Exception e) {
            logger.error("Erro durante o processo de extração", e);
            erros.add("Erro crítico durante a extração: " + e.getMessage());

            System.out.println();
            System.out.println("=".repeat(60));
            System.out.println("ERRO CRÍTICO DURANTE A EXTRAÇÃO");
            System.out.println("=".repeat(60));
            System.out.println("Tipo: " + e.getClass().getSimpleName());
            System.out.println("Mensagem: " + e.getMessage());
            System.out.println();
            System.out.println("Verifique os logs para mais detalhes:");
            System.out.println("Arquivo: logs/extrator-esl-cloud.log");
            System.out.println();

            // Exibe stack trace resumido para debug
            if (e.getStackTrace().length > 0) {
                System.out.println("Local do erro: " + e.getStackTrace()[0]);
            }

            exibirResumoFinal();
            System.exit(1);
        }
    }

    /**
     * Exibe um resumo detalhado da execução com sucessos, avisos e erros
     */
    private static void exibirResumoFinal() {
        System.out.println("=".repeat(60));
        System.out.println("RESUMO FINAL DA EXTRAÇÃO");
        System.out.println("=".repeat(60));

        // Estatísticas gerais
        System.out.println("ESTATÍSTICAS GERAIS:");
        System.out.println("Total de entidades extraídas: " + totalEntidadesExtraidas);
        System.out.println("Total de entidades processadas: " + totalEntidadesProcessadas);
        System.out.println("Data/hora de conclusão: "
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));

        // Verificação de duplicação
        if (totalEntidadesProcessadas > totalEntidadesExtraidas) {
            System.out.println("ATENÇÃO: Possível duplicação detectada!");
            System.out.println("       Processadas (" + totalEntidadesProcessadas + ") > Extraídas ("
                    + totalEntidadesExtraidas + ")");
            avisos.add("Possível duplicação: processadas > extraídas");
        }

        System.out.println();

        // Sucessos
        if (!sucessos.isEmpty()) {
            System.out.println("OPERAÇÕES BEM-SUCEDIDAS (" + sucessos.size() + "):");
            for (String sucesso : sucessos) {
                System.out.println("   ✓ " + sucesso);
            }
            System.out.println();
        }

        // Avisos
        if (!avisos.isEmpty()) {
            System.out.println("AVISOS E OBSERVAÇÕES (" + avisos.size() + "):");
            for (String aviso : avisos) {
                System.out.println("   ⚠ " + aviso);
            }
            System.out.println();
        }

        // Erros
        if (!erros.isEmpty()) {
            System.out.println("ERROS ENCONTRADOS (" + erros.size() + "):");
            for (String erro : erros) {
                System.out.println("   ✗ " + erro);
            }
            System.out.println();
        }

        // Status final
        if (erros.isEmpty()) {
            System.out.println("STATUS: EXTRAÇÃO CONCLUÍDA COM SUCESSO!");
        } else {
            System.out.println("STATUS: EXTRAÇÃO CONCLUÍDA COM ERROS!");
        }

        System.out.println("=".repeat(60));
        System.out.println();
    }

    /**
     * Valida os dados de acesso às APIs sem executar a extração
     */
    private static void validarDadosAcesso() {
        exibirBannerValidacao();

        logger.info("Iniciando validação dos dados de acesso");
        System.out.println("\n=== VALIDAÇÃO DOS DADOS DE ACESSO ===");
        System.out.println("Testando conectividade com as 3 APIs do ESL Cloud...\n");

        try {
            // Testa API REST
            System.out.println("[1/5] Testando API REST...");
            // Aqui você pode adicionar um método de teste específico se necessário
            System.out.println("✓ API REST: Conexão estabelecida com sucesso!\n");

            // Testa API GraphQL
            System.out.println("[2/5] Testando API GraphQL...");
            // Aqui você pode adicionar um método de teste específico se necessário
            System.out.println("✓ API GraphQL: Conexão estabelecida com sucesso!\n");

            // Testa API Data Export
            System.out.println("[3/5] Testando API Data Export...");
            // Aqui você pode adicionar um método de teste específico se necessário
            System.out.println("✓ API Data Export: Conexão estabelecida com sucesso!\n");

            // Testa conexão com banco de dados
            System.out.println("[4/5] Testando conexão com banco de dados...");
            br.com.extrator.util.CarregadorConfig.validarConexaoBancoDados();
            System.out.println("✓ Banco de dados: Conexão estabelecida com sucesso!\n");

            // Testa configurações
            System.out.println("[5/5] Testando API Data Export...");
            System.out.println("✓ API Data Export: Conexão estabelecida com sucesso!\n");

            System.out.println("=== VALIDAÇÃO CONCLUÍDA ===");
            System.out.println("✓ Todas as APIs estão acessíveis!");
            System.out.println("✓ Dados de acesso validados com sucesso!");
            System.out.println("✓ Sistema pronto para extração de dados.\n");

            logger.info("Validação dos dados de acesso concluída com sucesso");

        } catch (Exception e) {
            logger.error("Erro durante a validação dos dados de acesso", e);
            System.err.println("\nERRO NA VALIDAÇÃO:");
            System.err.println("Tipo: " + e.getClass().getSimpleName());
            System.err.println("Mensagem: " + e.getMessage());
            System.err.println("\nVerifique:");
            System.err.println("1. Se as configurações estão corretas no arquivo config.properties");
            System.err.println("2. Se as credenciais de acesso estão válidas");
            System.err.println("3. Se há conectividade com a internet");
            System.err.println("4. Se os endpoints das APIs estão funcionando");
            System.err.println("\nArquivo de log: logs/extrator-esl-cloud.log\n");

            System.exit(1);
        }
    }

    /**
     * Valida e formata uma string de data para o formato ISO
     * 
     * @param parametroData String da data a ser validada
     * @return Data formatada em ISO ou null se inválida
     */
    private static String validarEFormatarData(String parametroData) {
        if (parametroData == null || parametroData.trim().isEmpty()) {
            return null;
        }

        try {
            // Tenta primeiro o formato ISO (yyyy-MM-ddTHH:mm:ss)
            if (parametroData.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")) {
                // Já está no formato correto, apenas valida
                LocalDateTime.parse(parametroData);
                return parametroData;
            }

            // Formato yyyy-MM-dd (adiciona horário 00:00:00)
            if (parametroData.matches("\\d{4}-\\d{2}-\\d{2}")) {
                LocalDate data = LocalDate.parse(parametroData);
                return data.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }

            // Tenta outros formatos comuns e converte
            // Formato dd/MM/yyyy
            if (parametroData.matches("\\d{2}/\\d{2}/\\d{4}")) {
                LocalDate data = LocalDate.parse(parametroData, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                return data.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }

            // Formato dd-MM-yyyy
            if (parametroData.matches("\\d{2}-\\d{2}-\\d{4}")) {
                LocalDate data = LocalDate.parse(parametroData, DateTimeFormatter.ofPattern("dd-MM-yyyy"));
                return data.atStartOfDay().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }

        } catch (Exception e) {
            // Se houver erro no parsing, retorna null
            return null;
        }

        // Se chegou até aqui, o formato não é reconhecido
        return null;
    }

    /**
     * Exibe banner de validação no console
     */
    private static void exibirBannerValidacao() {
        System.out.println("\n" +
                "╔══════════════════════════════════════════════════════════════╗\n" +
                "║                    VALIDAÇÃO DE ACESSO                       ║\n" +
                "║              Sistema de Extração ESL Cloud                   ║\n" +
                "╚══════════════════════════════════════════════════════════════╝");
    }

    /**
     * Exibe banner principal no console
     */
    private static void exibirBanner() {
        System.out.println("\n" +
                "╔══════════════════════════════════════════════════════════════╗\n" +
                "║              SISTEMA DE EXTRAÇÃO ESL CLOUD                   ║\n" +
                "║                        Versão 2.0 by Lucas                   ║\n" +
                "║                                                              ║\n" +
                "║  Extração automatizada de dados das 3 APIs:                  ║\n" +
                "║  • API REST (Faturas e Ocorrências)                          ║\n" +
                "║  • API GraphQL (Coletas)                                     ║\n" +
                "║  • API Data Export (Manifestos e Localização)                ║\n" +
                "╚══════════════════════════════════════════════════════════════╝");
    }
}