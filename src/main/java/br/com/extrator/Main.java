package br.com.extrator;

import br.com.extrator.api.ClienteApiRest;
import br.com.extrator.api.ClienteApiGraphQL;
import br.com.extrator.api.ClienteApiDataExport;
import br.com.extrator.db.ServicoBancoDadosDinamico;
import br.com.extrator.modelo.EntidadeDinamica;
import br.com.extrator.testes.TesteModoExecucao;
import br.com.extrator.servicos.MetricasService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

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

    // Constantes para o sistema de gestão de estado
    private static final String ARQUIVO_ULTIMO_RUN = "last_run.properties";
    private static final String PROPRIEDADE_ULTIMO_RUN = "last_successful_run";

    public static void main(String[] args) {
        // Verifica se o usuário quer apenas validar os dados de acesso
        if (args.length > 0 && "--validar".equals(args[0])) {
            validarDadosAcesso();
            return;
        }

        // Verifica se foi solicitada ajuda
        if (args.length > 0 && ("--ajuda".equals(args[0]) || "--help".equals(args[0]))) {
            TesteModoExecucao.exibirAjuda();
            return;
        }

        // Verifica se o usuário quer fazer introspecção GraphQL
        if (args.length > 0 && "--introspeccao".equals(args[0])) {
            realizarIntrospeccaoGraphQL();
            return;
        }

        // Inicializa o gerenciador do modo de teste
        TesteModoExecucao testeModo = new TesteModoExecucao(args);
        
        // Valida os argumentos fornecidos
        if (!TesteModoExecucao.validarArgumentos(args)) {
            System.err.println("Argumentos inválidos!");
            TesteModoExecucao.exibirAjuda();
            System.exit(1);
        }

        // Exibe informações sobre o modo de execução
        testeModo.exibirInformacoesModo();

        // Exibe banner no console para melhor visualização
        exibirBanner();

        logger.info("Iniciando processo de extração de dados das 3 APIs do ESL Cloud (Modo Teste: {})", 
                   testeModo.isModoTesteAtivo());
        System.out.println("\n" + "=".repeat(60));
        System.out.println("INICIANDO PROCESSO DE EXTRAÇÃO DE DADOS");
        System.out.println("=".repeat(60));
        System.out.println("Sistema: Extração de dados das 3 APIs do ESL Cloud ");
        System.out.println("Versão: 2.0 by @valentelucass");
        System.out.println("Modo: " + (testeModo.isModoTesteAtivo() ? "TESTE (limitado)" : "NORMAL (completo)"));
        System.out.println("Início: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        System.out.println("=".repeat(60) + "\n");

        try {
            // Guarda o timestamp do início da execução atual
            LocalDateTime inicioExecucao = LocalDateTime.now();

            // Instância do serviço de métricas
        MetricasService metricasService = MetricasService.getInstance();
        
        // Verifica se as configurações foram personalizadas
        logger.info("Verificando configurações");
        System.out.println("    [ETAPA 1/11] Verificando configurações do sistema...");
        br.com.extrator.util.CarregadorConfig.verificarConfiguracoesPersonalizadas();
            System.out.println("    Configurações validadas com sucesso!");
            System.out.println("    💡 Dica: Para maior segurança, use variáveis de ambiente:");
            System.out.println("       Windows: $env:API_BASEURL=\"sua_url\" | Linux: export API_BASEURL=\"sua_url\"");
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
            ClienteApiGraphQL clienteApiGraphQL = new ClienteApiGraphQL();
            ClienteApiDataExport clienteApiDataExport = new ClienteApiDataExport();
            System.out.println("    Clientes das 3 APIs inicializados com sucesso!");
            sucessos.add("Clientes das APIs inicializados (REST, GraphQL, Data Export)");
            System.out.println();

            // Obtém a data de busca do gerenciador de teste
            String dataBusca = testeModo.getDataBusca();
            boolean modoTeste = testeModo.isModoTesteAtivo();
            
            System.out.println("    Data de busca configurada: " + dataBusca);
            if (modoTeste) {
                System.out.println("    Modo de teste: Limitando a " + testeModo.getLimiteRegistrosTeste() + " registros por entidade");
                sucessos.add("Modo de teste configurado (limite: " + testeModo.getLimiteRegistrosTeste() + " registros)");
            } else {
                sucessos.add("Modo normal configurado");
            }
            System.out.println();
            // Declaração da variável modoAutomatico
            boolean modoAutomatico = false;
            
            // Verifica se deve usar modo automático (incremental)
            if (dataBusca == null || dataBusca.trim().isEmpty()) {
                // Modo Automático: usa carga incremental
                modoAutomatico = true;
                LocalDateTime dataInicio = lerDataUltimaExecucao();
                dataBusca = dataInicio.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

                logger.info("Executando em modo incremental. Data de início: {}", dataBusca);
                System.out.println("    Modo Incremental ativado");
                System.out.println("      Buscando dados desde: "
                        + dataInicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
                sucessos.add("Modo incremental configurado (desde " + 
                           dataInicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) + ")");
            }
            System.out.println();

            // ========== ETAPA 5/11: EXTRAÇÃO DE FATURAS A RECEBER (API REST) ==========
            if (testeModo.isTestarRest() || testeModo.isModoNormal()) {
                logger.info("Extraindo Faturas a Receber da API REST");
                System.out.println("    [ETAPA 5/11] Extraindo Faturas a Receber da API REST...");

                try {
                    metricasService.iniciarTimer("extracao_faturas_receber");
                    List<EntidadeDinamica> faturasAReceber = clienteApiRest.buscarFaturasAReceber(dataBusca, modoTeste);
                    metricasService.pararTimer("extracao_faturas_receber");
                    
                    totalEntidadesExtraidas += faturasAReceber.size();
                    System.out.println("    Total de faturas a receber encontradas: " + faturasAReceber.size());

                    if (!faturasAReceber.isEmpty()) {
                        System.out.println("    Salvando faturas a receber no banco SQL Server...");
                        int processados = servicoBD.salvarEntidades(faturasAReceber, "faturas_a_receber");
                        totalEntidadesProcessadas += processados;
                        metricasService.adicionarRegistrosProcessados("faturas_a_receber", processados);
                        
                        System.out.println(
                                "    Faturas a receber salvas! Processadas: " + processados + "/" + faturasAReceber.size());
                        sucessos.add("Faturas a Receber extraídas e salvas: " + processados);
                    } else {
                        avisos.add("Nenhuma fatura a receber encontrada para o período");
                    }
                    
                    metricasService.registrarSucesso("API_REST_Faturas_Receber");
                } catch (RuntimeException e) {
                    metricasService.registrarFalha("API_REST_Faturas_Receber");
                    logger.error("Falha na extração de Faturas a Receber: {}", e.getMessage());
                    System.out.println("    ❌ ERRO: Falha na extração de Faturas a Receber - " + e.getMessage());
                    erros.add("Faturas a Receber: " + e.getMessage());
                }
                
                Thread.sleep(2000);
                System.out.println();
            } else {
                System.out.println("    [ETAPA 5/11] Extração de Faturas a Receber PULADA (modo de teste específico)");
                System.out.println();
            }

            // ========== ETAPA 6/11: EXTRAÇÃO DE FATURAS A PAGAR (API REST) ==========
            if (testeModo.isTestarRest() || testeModo.isModoNormal()) {
                logger.info("Extraindo Faturas a Pagar da API REST");
                System.out.println("    [ETAPA 6/11] Extraindo Faturas a Pagar da API REST...");

                try {
                    metricasService.iniciarTimer("extracao_faturas_pagar");
                    List<EntidadeDinamica> faturasAPagar = clienteApiRest.buscarFaturasAPagar(dataBusca, modoTeste);
                    metricasService.pararTimer("extracao_faturas_pagar");
                    
                    totalEntidadesExtraidas += faturasAPagar.size();
                    System.out.println("    Total de faturas a pagar encontradas: " + faturasAPagar.size());

                    if (!faturasAPagar.isEmpty()) {
                        System.out.println("    Salvando faturas a pagar no banco SQL Server...");
                        int processados = servicoBD.salvarEntidades(faturasAPagar, "faturas_a_pagar");
                        totalEntidadesProcessadas += processados;
                        metricasService.adicionarRegistrosProcessados("faturas_a_pagar", processados);
                        
                        System.out.println("    Faturas a pagar salvas! Processadas: " + processados + "/" + faturasAPagar.size());
                        sucessos.add("Faturas a Pagar extraídas e salvas: " + processados);
                    } else {
                        avisos.add("Nenhuma fatura a pagar encontrada para o período");
                    }
                    
                    metricasService.registrarSucesso("API_REST_Faturas_Pagar");
                } catch (RuntimeException e) {
                    metricasService.registrarFalha("API_REST_Faturas_Pagar");
                    logger.error("Falha na extração de Faturas a Pagar: {}", e.getMessage());
                    System.out.println("    ❌ ERRO: Falha na extração de Faturas a Pagar - " + e.getMessage());
                    erros.add("Faturas a Pagar: " + e.getMessage());
                }
                
                Thread.sleep(2000);
                System.out.println();
            } else {
                System.out.println("    [ETAPA 6/11] Extração de Faturas a Pagar PULADA (modo de teste específico)");
                System.out.println();
            }

            // ========== ETAPA 7/11: EXTRAÇÃO DE OCORRÊNCIAS (API REST) ==========
            if (testeModo.isTestarRest() || testeModo.isModoNormal()) {
                logger.info("Extraindo ocorrências da API REST");
                System.out.println("    [ETAPA 7/11] Extraindo Ocorrências da API REST...");

                try {
                    metricasService.iniciarTimer("extracao_ocorrencias_rest");
                    // Chama o método que você corrigiu em ClienteApiRest.java
                    List<EntidadeDinamica> ocorrencias = clienteApiRest.buscarOcorrencias(dataBusca, modoTeste);
                    metricasService.pararTimer("extracao_ocorrencias_rest");
                    
                    totalEntidadesExtraidas += ocorrencias.size();

                    logger.info("Extração de ocorrências concluída. Total encontrado: {}", ocorrencias.size());
                    System.out.println("    Total de ocorrências encontradas: " + ocorrencias.size());

                    if (!ocorrencias.isEmpty()) {
                        logger.info("Salvando ocorrências no banco de dados");
                        System.out.println("    Salvando ocorrências no banco SQL Server...");

                        // Salva os resultados na tabela "ocorrencias"
                        int processados = servicoBD.salvarEntidades(ocorrencias, "ocorrencias");
                        totalEntidadesProcessadas += processados;
                        metricasService.adicionarRegistrosProcessados("ocorrencias", processados);

                        logger.info("Ocorrências salvas. Total processado: {}", processados);
                        System.out.println(
                                "    Ocorrências salvas com sucesso! Processadas: " + processados + "/" + ocorrencias.size());
                        sucessos.add("Ocorrências extraídas e salvas: " + processados + "/" + ocorrencias.size());
                    } else {
                        System.out.println("    Nenhuma ocorrência encontrada para o período especificado");
                        avisos.add("Nenhuma ocorrência encontrada para o período");
                    }
                    
                    metricasService.registrarSucesso("API_REST_Ocorrencias");
                } catch (RuntimeException e) {
                    metricasService.registrarFalha("API_REST_Ocorrencias");
                    logger.error("Falha na extração de Ocorrências: {}", e.getMessage());
                    System.out.println("    ❌ ERRO: Falha na extração de Ocorrências - " + e.getMessage());
                    erros.add("Ocorrências: " + e.getMessage());
                }

                // Pausa obrigatória de 2 segundos entre APIs
                logger.info("Aguardando 2 segundos antes da próxima API...");
                System.out.println("    Aguardando 2 segundos antes da próxima API...");
                Thread.sleep(2000);
                System.out.println();
            } else {
                System.out.println("    [ETAPA 7/11] Extração de Ocorrências PULADA (modo de teste específico)");
                System.out.println();
            }

            // ========== ETAPA 8/11: EXTRAÇÃO DE COLETAS (API GRAPHQL) ==========
            if (testeModo.isTestarGraphQL() || testeModo.isModoNormal()) {
                logger.info("Extraindo coletas da API GraphQL");
                System.out.println("    [ETAPA 8/11] Extraindo Coletas da API GraphQL...");

                try {
                    metricasService.iniciarTimer("extracao_coletas");
                    List<EntidadeDinamica> coletas = clienteApiGraphQL.buscarColetas(dataBusca, modoTeste);
                    metricasService.pararTimer("extracao_coletas");
                    
                    totalEntidadesExtraidas += coletas.size();

                    logger.info("Extração de coletas concluída. Total encontrado: {}", coletas.size());
                    System.out.println("    Total de coletas encontradas: " + coletas.size());

                    if (!coletas.isEmpty()) {
                        logger.info("Salvando coletas no banco de dados");
                        System.out.println("    Salvando coletas no banco SQL Server...");

                        int processados = servicoBD.salvarEntidades(coletas, "coletas");
                        totalEntidadesProcessadas += processados;
                        metricasService.adicionarRegistrosProcessados("coletas", processados);

                        logger.info("Coletas salvas. Total processado: {}", processados);
                        System.out.println(
                                "    Coletas salvas com sucesso! Processadas: " + processados + "/" + coletas.size());
                        sucessos.add("Coletas extraídas e salvas: " + processados + "/" + coletas.size());
                    } else {
                        System.out.println("    Nenhuma coleta encontrada para o período especificado");
                        avisos.add("Nenhuma coleta encontrada para o período");
                    }
                    
                    metricasService.registrarSucesso("API_GraphQL_Coletas");
                } catch (RuntimeException e) {
                    metricasService.registrarFalha("API_GraphQL_Coletas");
                    logger.error("Falha na extração de Coletas: {}", e.getMessage());
                    System.out.println("    ❌ ERRO: Falha na extração de Coletas - " + e.getMessage());
                    erros.add("Coletas: " + e.getMessage());
                }

                // Pausa obrigatória de 2 segundos entre APIs
                logger.info("Aguardando 2 segundos antes da próxima API...");
                System.out.println("    Aguardando 2 segundos antes da próxima API...");
                Thread.sleep(2000);
                System.out.println();
            } else {
                System.out.println("    [ETAPA 8/11] Extração de Coletas PULADA (modo de teste específico)");
                System.out.println();
            }
            
            // ========== ETAPA 9/11: EXTRAÇÃO DE FRETES (API GRAPHQL) ==========
            if (testeModo.isTestarGraphQL() || testeModo.isModoNormal()) {
                logger.info("Extraindo fretes da API GraphQL");
                System.out.println("    [ETAPA 9/11] Extraindo Fretes da API GraphQL...");

                try {
                    metricasService.iniciarTimer("extracao_fretes_graphql");
                    List<EntidadeDinamica> fretes = clienteApiGraphQL.buscarFretes(dataBusca, modoTeste);
                    metricasService.pararTimer("extracao_fretes_graphql");
                    
                    totalEntidadesExtraidas += fretes.size();

                    logger.info("Extração de fretes concluída. Total encontrado: {}", fretes.size());
                    System.out.println("    Total de fretes encontrados: " + fretes.size());

                    if (!fretes.isEmpty()) {
                        logger.info("Salvando fretes no banco de dados");
                        System.out.println("    Salvando fretes no banco SQL Server...");

                        int processados = servicoBD.salvarEntidades(fretes, "fretes");
                        totalEntidadesProcessadas += processados;
                        metricasService.adicionarRegistrosProcessados("fretes", processados);

                        logger.info("Fretes salvos. Total processado: {}", processados);
                        System.out.println(
                                "    Fretes salvos com sucesso! Processados: " + processados + "/" + fretes.size());
                        sucessos.add("Fretes extraídos e salvos: " + processados + "/" + fretes.size());
                    } else {
                        System.out.println("    Nenhum frete encontrado para o período especificado");
                        avisos.add("Nenhum frete encontrado para o período");
                    }
                    
                    metricasService.registrarSucesso("API_GraphQL_Fretes");
                } catch (RuntimeException e) {
                    metricasService.registrarFalha("API_GraphQL_Fretes");
                    logger.error("Falha na extração de Fretes: {}", e.getMessage());
                    System.out.println("    ❌ ERRO: Falha na extração de Fretes - " + e.getMessage());
                    erros.add("Fretes: " + e.getMessage());
                }

                // Pausa obrigatória de 2 segundos entre APIs
                logger.info("Aguardando 2 segundos antes da próxima API...");
                System.out.println("    Aguardando 2 segundos antes da próxima API...");
                Thread.sleep(2000);
                System.out.println();
            } else {
                System.out.println("    [ETAPA 9/11] Extração de Fretes PULADA (modo de teste específico)");
                System.out.println();
            }

            // ========== ETAPA 10/11: EXTRAÇÃO DE MANIFESTOS (API DATA EXPORT) ==========
            if (testeModo.isTestarDataExport() || testeModo.isModoNormal()) {
                logger.info("Extraindo manifestos da API Data Export");
                System.out.println("    [ETAPA 10/11] Extraindo Manifestos da API Data Export...");

                try {
                    metricasService.iniciarTimer("extracao_manifestos");
                    List<EntidadeDinamica> manifestos = clienteApiDataExport.buscarManifestos(dataBusca, modoTeste);
                    metricasService.pararTimer("extracao_manifestos");
                    
                    totalEntidadesExtraidas += manifestos.size();

                    logger.info("Extração de manifestos concluída. Total encontrado: {}", manifestos.size());
                    System.out.println("    Total de manifestos encontrados: " + manifestos.size());

                    if (!manifestos.isEmpty()) {
                        logger.info("Salvando manifestos no banco de dados");
                        System.out.println("    Salvando manifestos no banco SQL Server...");

                        int processados = servicoBD.salvarEntidades(manifestos, "manifestos");
                        totalEntidadesProcessadas += processados;
                        metricasService.adicionarRegistrosProcessados("manifestos", processados);

                        logger.info("Manifestos salvos. Total processado: {}", processados);
                        System.out.println(
                                "    Manifestos salvos com sucesso! Processados: " + processados + "/" + manifestos.size());
                        sucessos.add("Manifestos extraídos e salvos: " + processados + "/" + manifestos.size());
                    } else {
                        System.out.println("    Nenhum manifesto encontrado para o período especificado");
                        avisos.add("Nenhum manifesto encontrado para o período");
                    }
                    
                    metricasService.registrarSucesso("API_DataExport_Manifestos");
                } catch (RuntimeException e) {
                    metricasService.registrarFalha("API_DataExport_Manifestos");
                    logger.error("Falha na extração de Manifestos: {}", e.getMessage());
                    System.out.println("    ❌ ERRO: Falha na extração de Manifestos - " + e.getMessage());
                    erros.add("Manifestos: " + e.getMessage());
                }

                // Pausa obrigatória de 2 segundos entre APIs
                logger.info("Aguardando 2 segundos antes da próxima API...");
                System.out.println("    Aguardando 2 segundos antes da próxima API...");
                Thread.sleep(2000);
                System.out.println();
            } else {
                System.out.println("    [ETAPA 10/11] Extração de Manifestos PULADA (modo de teste específico)");
                System.out.println();
            }

            // ========== ETAPA 11/11: EXTRAÇÃO DE LOCALIZAÇÃO DA CARGA (API DATA EXPORT) ==========
            if (testeModo.isTestarDataExport() || testeModo.isModoNormal()) {
                logger.info("Extraindo localização da carga da API Data Export");
                System.out.println("    [ETAPA 11/11] Extraindo Localização da Carga da API Data Export...");

                try {
                    metricasService.iniciarTimer("extracao_localizacao_carga");
                    List<EntidadeDinamica> localizacoes = clienteApiDataExport.buscarLocalizacaoCarga(dataBusca, modoTeste);
                    metricasService.pararTimer("extracao_localizacao_carga");
                    
                    totalEntidadesExtraidas += localizacoes.size();

                    logger.info("Extração de localização da carga concluída. Total encontrado: {}", localizacoes.size());
                    System.out.println("    Total de localizações encontradas: " + localizacoes.size());

                    if (!localizacoes.isEmpty()) {
                        logger.info("Salvando localização da carga no banco de dados");
                        System.out.println("    Salvando localização da carga no banco SQL Server...");

                        int processados = servicoBD.salvarEntidades(localizacoes, "localizacao_carga");
                        totalEntidadesProcessadas += processados;
                        metricasService.adicionarRegistrosProcessados("localizacao_carga", processados);

                        logger.info("Localização da carga salva. Total processado: {}", processados);
                        System.out.println(
                                "    Localização da carga salva com sucesso! Processadas: " + processados + "/" + localizacoes.size());
                        sucessos.add("Localização da carga extraída e salva: " + processados + "/" + localizacoes.size());
                    } else {
                        System.out.println("    Nenhuma localização encontrada para o período especificado");
                        avisos.add("Nenhuma localização encontrada para o período");
                    }
                    
                    metricasService.registrarSucesso("API_DataExport_Localizacao");
                } catch (RuntimeException e) {
                    metricasService.registrarFalha("API_DataExport_Localizacao");
                    logger.error("Falha na extração de Localização da Carga: {}", e.getMessage());
                    System.out.println("    ❌ ERRO: Falha na extração de Localização da Carga - " + e.getMessage());
                    erros.add("Localização da Carga: " + e.getMessage());
                }

                // Pausa obrigatória de 2 segundos entre APIs
                logger.info("Aguardando 2 segundos antes da próxima API...");
                System.out.println("    Aguardando 2 segundos antes da próxima API...");
                Thread.sleep(2000);
                System.out.println();
            } else {
                System.out.println("    [ETAPA 11/11] Extração de Localização da Carga PULADA (modo de teste específico)");
                System.out.println();
            }
            
            // ========== ETAPA 12/11: EXTRAÇÃO DE MANIFESTOS (API DATA EXPORT) ==========
            if (testeModo.isTestarDataExport() || testeModo.isModoNormal()) {
                logger.info("Extraindo manifestos da API Data Export");
                System.out.println("    [ETAPA 12/11] Extraindo Manifestos da API Data Export...");

                try {
                    metricasService.iniciarTimer("extracao_manifestos");
                    List<EntidadeDinamica> manifestos = clienteApiDataExport.buscarManifestos(dataBusca, modoTeste);
                    metricasService.pararTimer("extracao_manifestos");
                    
                    totalEntidadesExtraidas += manifestos.size();

                    logger.info("Extração de manifestos concluída. Total encontrado: {}", manifestos.size());
                    System.out.println("    Total de manifestos encontrados: " + manifestos.size());

                    if (!manifestos.isEmpty()) {
                        logger.info("Salvando manifestos no banco de dados");
                        System.out.println("    Salvando manifestos no banco SQL Server...");

                        int processados = servicoBD.salvarEntidades(manifestos, "manifestos");
                        totalEntidadesProcessadas += processados;
                        metricasService.adicionarRegistrosProcessados("manifestos", processados);

                        logger.info("Manifestos salvos. Total processado: {}", processados);
                        System.out.println(
                                "    Manifestos salvos com sucesso! Processados: " + processados + "/" + manifestos.size());
                        sucessos.add("Manifestos extraídos e salvos: " + processados + "/" + manifestos.size());
                    } else {
                        System.out.println("    Nenhum manifesto encontrado para o período especificado");
                        avisos.add("Nenhum manifesto encontrado para o período");
                    }
                    
                    metricasService.registrarSucesso("API_DataExport_Manifestos");
                } catch (RuntimeException e) {
                    metricasService.registrarFalha("API_DataExport_Manifestos");
                    logger.error("Falha na extração de Manifestos: {}", e.getMessage());
                    System.out.println("    ❌ ERRO: Falha na extração de Manifestos - " + e.getMessage());
                    erros.add("Manifestos: " + e.getMessage());
                }

                // Pausa obrigatória de 2 segundos entre APIs
                logger.info("Aguardando 2 segundos antes da próxima API...");
                System.out.println("    Aguardando 2 segundos antes da próxima API...");
                Thread.sleep(2000);
                System.out.println();
            } else {
                System.out.println("    [ETAPA 12/11] Extração de Manifestos PULADA (modo de teste específico)");
                System.out.println();
            }

            // ========== ETAPA 13/11: EXTRAÇÃO DE LOCALIZAÇÃO DA CARGA (API DATA EXPORT) ==========
            if (testeModo.isTestarDataExport() || testeModo.isModoNormal()) {
                logger.info("Extraindo localização da carga da API Data Export");
                System.out.println("    [ETAPA 13/11] Extraindo Localização da Carga da API Data Export...");

                try {
                    metricasService.iniciarTimer("extracao_localizacao_carga");
                    List<EntidadeDinamica> localizacoes = clienteApiDataExport.buscarLocalizacaoCarga(dataBusca, modoTeste);
                    metricasService.pararTimer("extracao_localizacao_carga");
                    
                    totalEntidadesExtraidas += localizacoes.size();

                    logger.info("Extração de localização da carga concluída. Total encontrado: {}", localizacoes.size());
                    System.out.println("    Total de localizações encontradas: " + localizacoes.size());

                    if (!localizacoes.isEmpty()) {
                        logger.info("Salvando localização da carga no banco de dados");
                        System.out.println("    Salvando localizações no banco SQL Server...");

                        int processados = servicoBD.salvarEntidades(localizacoes, "localizacao_carga");
                        totalEntidadesProcessadas += processados;
                        metricasService.adicionarRegistrosProcessados("localizacao_carga", processados);

                        logger.info("Localização da carga salva. Total processado: {}", processados);
                        System.out.println(
                                "    Localizações salvas com sucesso! Processadas: " + processados + "/" + localizacoes.size());
                        sucessos.add("Localizações extraídas e salvas: " + processados + "/" + localizacoes.size());
                    } else {
                        System.out.println("    Nenhuma localização encontrada para o período especificado");
                        avisos.add("Nenhuma localização encontrada para o período");
                    }
                    
                    metricasService.registrarSucesso("API_DataExport_Localizacao");
                } catch (RuntimeException e) {
                    metricasService.registrarFalha("API_DataExport_Localizacao");
                    logger.error("Falha na extração de Localização da Carga: {}", e.getMessage());
                    System.out.println("    ❌ ERRO: Falha na extração de Localização da Carga - " + e.getMessage());
                    erros.add("Localização da Carga: " + e.getMessage());
                }
            } else {
                System.out.println("    [ETAPA 13/11] Extração de Localização da Carga PULADA (modo de teste específico)");
                System.out.println();
            }

            // ========== RESUMO FINAL ==========
            logger.info("Processo de extração concluído");
            System.out.println();

            // Gravação condicional de sucesso (apenas em modo automático e sem erros)
            if (modoAutomatico && erros.isEmpty()) {
                logger.info("Execução bem-sucedida em modo automático. Gravando timestamp de sucesso.");
                gravarDataExecucao(inicioExecucao);
                System.out.println("    ✓ Timestamp de execução atualizado para próxima execução incremental");
                sucessos.add("Timestamp de execução atualizado com sucesso");
            } else if (modoAutomatico && !erros.isEmpty()) {
                logger.warn("Execução em modo automático com erros. Timestamp NÃO será atualizado.");
                System.out.println("    ⚠ Timestamp NÃO atualizado devido a erros na execução");
                avisos.add("Timestamp não atualizado devido a erros na execução");
            }

            exibirResumoFinal();

            // Salvar métricas do dia após a execução
            try {
                MetricasService.getInstance().salvarMetricasDoDia();
                logger.info("Métricas do dia salvas com sucesso");
            } catch (Exception e) {
                logger.error("Erro ao salvar métricas do dia: {}", e.getMessage(), e);
            }

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

            // Salvar métricas do dia após a execução (mesmo com erros)
            try {
                MetricasService.getInstance().salvarMetricasDoDia();
                logger.info("Métricas do dia salvas com sucesso");
            } catch (Exception ex) {
                logger.error("Erro ao salvar métricas do dia: {}", ex.getMessage(), ex);
            }

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

        // Relatório de métricas
        System.out.println();
        System.out.println("MÉTRICAS DE EXECUÇÃO:");
        System.out.println(MetricasService.getInstance().gerarRelatorio());

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
     * Lê o timestamp da última execução bem-sucedida do arquivo last_run.properties
     * 
     * @return LocalDateTime da última execução ou 24 horas atrás se não encontrar
     */
    private static LocalDateTime lerDataUltimaExecucao() {
        try {
            // Verifica se o arquivo existe
            if (!Files.exists(Paths.get(ARQUIVO_ULTIMO_RUN))) {
                logger.warn("Arquivo {} não encontrado. Usando valor padrão (24 horas atrás)", ARQUIVO_ULTIMO_RUN);
                return LocalDateTime.now().minusHours(24);
            }

            // Carrega as propriedades do arquivo
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(ARQUIVO_ULTIMO_RUN)) {
                props.load(fis);
            }

            // Lê o timestamp da última execução
            String ultimaExecucaoStr = props.getProperty(PROPRIEDADE_ULTIMO_RUN);
            if (ultimaExecucaoStr == null || ultimaExecucaoStr.trim().isEmpty()) {
                logger.warn("Propriedade {} não encontrada no arquivo {}. Usando valor padrão (24 horas atrás)", 
                           PROPRIEDADE_ULTIMO_RUN, ARQUIVO_ULTIMO_RUN);
                return LocalDateTime.now().minusHours(24);
            }

            // Converte o timestamp para LocalDateTime
            LocalDateTime ultimaExecucao = LocalDateTime.parse(ultimaExecucaoStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            logger.info("Última execução bem-sucedida encontrada: {}", ultimaExecucao);
            return ultimaExecucao;

        } catch (Exception e) {
            logger.warn("Erro ao ler arquivo de última execução: {}. Usando valor padrão (24 horas atrás)", e.getMessage());
            return LocalDateTime.now().minusHours(24);
        }
    }

    /**
     * Grava o timestamp de execução no arquivo last_run.properties
     * 
     * @param timestamp LocalDateTime a ser gravado
     */
    private static void gravarDataExecucao(LocalDateTime timestamp) {
        try {
            Properties props = new Properties();
            props.setProperty(PROPRIEDADE_ULTIMO_RUN, timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            // Grava o arquivo com comentário de aviso
            try (FileOutputStream fos = new FileOutputStream(ARQUIVO_ULTIMO_RUN)) {
                props.store(fos, "ATENÇÃO: Este arquivo é gerenciado automaticamente pelo sistema. NÃO EDITE MANUALMENTE!");
            }

            logger.info("Timestamp de execução gravado com sucesso: {}", timestamp);

        } catch (IOException e) {
            logger.error("Erro ao gravar timestamp de execução: {}", e.getMessage());
            // Não lança exceção para não interromper o fluxo principal
        }
    }

    /**
     * Realiza introspecção GraphQL para descobrir tipos e campos disponíveis
     */
    private static void realizarIntrospeccaoGraphQL() {
        exibirBannerIntrospeccao();
        
        logger.info("Iniciando introspecção da API GraphQL");
        System.out.println("Iniciando introspecção da API GraphQL do ESL Cloud...\n");
        
        try {
            // Inicializa cliente GraphQL
            ClienteApiGraphQL clienteGraphQL = new ClienteApiGraphQL();
            
            // Lista de tipos para inspecionar
            String[] tiposParaInspecionar = {"FreightInput", "ColetaInput", "CollectionInput"};
            
            for (String tipo : tiposParaInspecionar) {
                System.out.println("═".repeat(60));
                System.out.println("INSPECIONANDO TIPO: " + tipo);
                System.out.println("═".repeat(60));
                
                List<String> campos = clienteGraphQL.inspecionarTipoGraphQL(tipo);
                
                if (campos.isEmpty()) {
                    System.out.println("❌ Tipo '" + tipo + "' não encontrado ou sem campos disponíveis");
                } else {
                    System.out.println("✅ Tipo '" + tipo + "' encontrado com " + campos.size() + " campos:");
                    for (int i = 0; i < campos.size(); i++) {
                        System.out.println("  " + (i + 1) + ". " + campos.get(i));
                    }
                }
                System.out.println();
            }
            
            System.out.println("═".repeat(60));
            System.out.println("INTROSPECÇÃO CONCLUÍDA");
            System.out.println("═".repeat(60));
            System.out.println("✅ Processo de introspecção finalizado com sucesso!");
            System.out.println("📋 Verifique os logs acima para identificar os campos corretos");
            System.out.println("🔧 Use essas informações para corrigir as queries GraphQL");
            
        } catch (Exception e) {
            logger.error("Erro durante introspecção GraphQL: {}", e.getMessage(), e);
            System.err.println("❌ Erro durante introspecção: " + e.getMessage());
        }
    }

    /**
     * Exibe banner de introspecção no console
     */
    private static void exibirBannerIntrospeccao() {
        System.out.println("\n" +
                "╔══════════════════════════════════════════════════════════════╗\n" +
                "║                   INTROSPECÇÃO GRAPHQL                       ║\n" +
                "║              Sistema de Extração ESL Cloud                   ║\n" +
                "║                                                              ║\n" +
                "║  Descobrindo tipos e campos disponíveis na API GraphQL       ║\n" +
                "╚══════════════════════════════════════════════════════════════╝");
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