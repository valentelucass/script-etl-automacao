package br.com.extrator.comandos;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.auditoria.CompletudeValidator;
import br.com.extrator.db.repository.LogExtracaoRepository;
import br.com.extrator.runners.DataExportRunner;
import br.com.extrator.runners.GraphQLRunner;
import br.com.extrator.util.BannerUtil;

/**
 * Comando responsável por executar o fluxo completo de extração de dados
 * das 3 APIs do ESL Cloud (REST, GraphQL e DataExport).
 */
public class ExecutarFluxoCompletoComando implements Comando {
    private static final Logger logger = LoggerFactory.getLogger(ExecutarFluxoCompletoComando.class);
    
    // Constantes para gravação do timestamp de execução
    private static final String ARQUIVO_ULTIMO_RUN = "last_run.properties";
    private static final String PROPRIEDADE_ULTIMO_RUN = "last_successful_run";
    
    // Número de threads para execução paralela dos runners
    private static final int NUMERO_DE_THREADS = 2;
    
    @Override
    public void executar(final String[] args) throws Exception {
        // Exibe banner inicial de extração completa
        BannerUtil.exibirBannerExtracaoCompleta();
        
        // Define data de hoje para buscar dados do dia atual
        final LocalDate dataHoje = LocalDate.now();
        
        logger.info("Iniciando processo de extração de dados das 2 APIs do ESL Cloud");
        System.out.println("\n" + "=".repeat(60));
        System.out.println("INICIANDO PROCESSO DE EXTRAÇÃO DE DADOS");
        System.out.println("=".repeat(60));
        System.out.println("Modo: DADOS DE HOJE");
        System.out.println("Data de extração: " + dataHoje.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " (dados de hoje)");
        System.out.println("Início: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        System.out.println("=".repeat(60) + "\n");
        
        final LocalDateTime inicioExecucao = LocalDateTime.now();
        
        // ========== EXECUÇÃO PARALELA DOS RUNNERS ==========
        logger.info("Iniciando fluxo ETL em modo paralelo com {} threads", NUMERO_DE_THREADS);
        System.out.println("\n🔄 Iniciando execução paralela dos 2 runners...");
        
        final ExecutorService executor = Executors.newFixedThreadPool(NUMERO_DE_THREADS);
        // Usar LinkedHashMap para manter ordem de inserção e associar explicitamente nome ao Future
        // Isso elimina o risco de desalinhamento entre ordem de submissão e nomes dos runners
        final Map<String, Future<?>> runnersFuturos = new LinkedHashMap<>();
        final List<String> runnersFalhados = new ArrayList<>();
        int totalSucessos = 0;
        int totalFalhas = 0;
        
        try {
            // Submeter todas as tarefas para execução paralela
            // Simplificado: apenas executar o runner, sem try-catch redundante
            // A lógica de sucesso/falha é centralizada no loop de verificação abaixo
            // Usar Map para associar explicitamente nome do runner ao seu Future
            logger.debug("Submetendo GraphQLRunner...");
            System.out.println("🔄 [1/2] Submetendo API GraphQL para execução...");
            runnersFuturos.put("GraphQL", executor.submit(criarCallableRunner(() -> GraphQLRunner.executar(dataHoje))));
            
            logger.debug("Submetendo DataExportRunner...");
            System.out.println("🔄 [2/2] Submetendo API Data Export para execução...");
            runnersFuturos.put("DataExport", executor.submit(criarCallableRunner(() -> DataExportRunner.executar(dataHoje))));
            
            logger.info("Todos os runners foram submetidos. Aguardando conclusão...");
            System.out.println("\n⏳ Aguardando conclusão de todos os runners...\n");
            
            // Aguardar a conclusão e tratar falhas individualmente
            // Centralização da lógica de sucesso/falha aqui
            // Iterar sobre Map.entrySet() para ter acesso simultâneo ao nome e ao Future
            for (final Map.Entry<String, Future<?>> entry : runnersFuturos.entrySet()) {
                final String nomeRunner = entry.getKey();
                final Future<?> futuro = entry.getValue();
                
                try {
                    // .get() é bloqueante - espera a thread daquele runner terminar
                    futuro.get();
                    totalSucessos++;
                    System.out.println("✅ API " + nomeRunner + " concluída com sucesso!");
                    logger.info("✅ Runner {} executado com sucesso", nomeRunner);
                } catch (final ExecutionException e) {
                    // ESTA É A MUDANÇA CRÍTICA: captura exceção, registra erro e continua
                    totalFalhas++;
                    runnersFalhados.add(nomeRunner);
                    
                    final Throwable causa = e.getCause();
                    final String mensagemErro = causa != null ? causa.getMessage() : e.getMessage();
                    System.err.println("❌ API " + nomeRunner + " falhou: " + mensagemErro);
                    logger.error("❌ FALHA ISOLADA NO RUNNER {}: {}. O fluxo principal continuará.", 
                        nomeRunner, mensagemErro, e);
                    System.err.println("⚠️  Runner " + nomeRunner + " falhou, mas outros runners continuarão executando.");
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    totalFalhas++;
                    runnersFalhados.add(nomeRunner);
                    System.err.println("❌ Thread interrompida para runner " + nomeRunner);
                    logger.error("❌ Thread interrompida para runner {}: {}", nomeRunner, e.getMessage(), e);
                }
            }
            
            // Resumo da execução dos runners
            System.out.println("\n" + "=".repeat(60));
            System.out.println("📊 RESUMO DA EXECUÇÃO DOS RUNNERS");
            System.out.println("=".repeat(60));
            System.out.println("✅ Runners bem-sucedidos: " + totalSucessos + "/2");
            if (totalFalhas > 0) {
                System.out.println("❌ Runners com falha: " + totalFalhas + "/2");
                System.out.println("⚠️  Runners falhados: " + String.join(", ", runnersFalhados));
            }
            System.out.println("=".repeat(60) + "\n");
            
            if (totalFalhas > 0) {
                logger.warn("Fluxo ETL concluído com {} falha(s). Runners falhados: {}", 
                    totalFalhas, String.join(", ", runnersFalhados));
            } else {
                logger.info("✅ Fluxo ETL completo. Runners GraphQL e DataExport executados com sucesso.");
            }
            
        } finally {
            // Sempre desligar o pool de threads, caso contrário a aplicação não encerrará
            executor.shutdown();
            logger.debug("ExecutorService encerrado");
        }

        try {
            final LogExtracaoRepository dimRepo = new LogExtracaoRepository();
            dimRepo.criarOuAtualizarViewDimFiliais();
            System.out.println("✅ View vw_dim_filiais criada/atualizada automaticamente");
            logger.info("✅ View vw_dim_filiais criada/atualizada após execução dos runners");
            dimRepo.criarOuAtualizarViewDimPlanoContas();
            System.out.println("✅ View vw_dim_planocontas criada/atualizada automaticamente");
            logger.info("✅ View vw_dim_planocontas criada/atualizada após execução dos runners");
        } catch (final Exception e) {
            System.err.println("⚠️ Não foi possível criar/atualizar vw_dim_filiais/vw_dim_planocontas: " + e.getMessage());
            logger.warn("⚠️ Não foi possível criar/atualizar vw_dim_filiais/vw_dim_planocontas automaticamente: {}", e.getMessage(), e);
        }
        
        // ========== PASSO B: VALIDAÇÃO DE COMPLETUDE ==========
        // Executar validação mesmo se algum runner falhar
        // A validação já trata entidades ausentes (retorna Optional.empty() se API falhar)
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🔍 INICIANDO VALIDAÇÃO DE COMPLETUDE DOS DADOS");
        System.out.println("=".repeat(60));
        
        if (totalFalhas > 0) {
            System.out.println("⚠️  ATENÇÃO: Alguns runners falharam (" + String.join(", ", runnersFalhados) + ")");
            System.out.println("⚠️  A validação pode mostrar dados incompletos para essas entidades.");
            logger.warn("Executando validação de completude com {} runner(s) falhado(s): {}", 
                totalFalhas, String.join(", ", runnersFalhados));
        }
        
        try {
                // Instancia o validador
                final CompletudeValidator validator = new CompletudeValidator();
                
                // Passo B.1: Buscar totais da API ESL Cloud
                System.out.println("🔄 [1/2] Buscando totais nas APIs do ESL Cloud...");
                final LocalDate dataReferencia = LocalDate.now();
                final Optional<Map<String, Integer>> totaisEslCloudOpt = validator.buscarTotaisEslCloud(dataReferencia);
                
                if (totaisEslCloudOpt.isPresent()) {
                    final Map<String, Integer> totaisEslCloud = totaisEslCloudOpt.get();
                    System.out.println("✅ Totais obtidos das APIs com sucesso!");
                    
                    // Passo B.2: Validar completude comparando com o banco de dados
                    System.out.println("🔄 [2/2] Validando completude dos dados extraídos...");
                    final Map<String, CompletudeValidator.StatusValidacao> resultadosValidacao = validator.validarCompletude(totaisEslCloud, dataReferencia);
                    
                    // Determina se a extração está completa (todos os status devem ser OK)
                    final boolean extracaoCompleta = resultadosValidacao.values().stream()
                        .allMatch(status -> status == CompletudeValidator.StatusValidacao.OK);
                    
                    // TÓPICO 4: Validações Avançadas (apenas se a validação básica passou)
                    boolean gapValidationOk = true;
                    boolean temporalValidationOk = true;
                    
                    if (extracaoCompleta) {
                        System.out.println("🔍 [3/4] Executando validação de gaps (IDs sequenciais)...");
                        final CompletudeValidator.StatusValidacao gapStatus = validator.validarGapsOcorrencias(dataReferencia);
                        gapValidationOk = (gapStatus == CompletudeValidator.StatusValidacao.OK);
                        
                        if (gapValidationOk) {
                            System.out.println("✅ Validação de gaps: OK");
                            logger.info("✅ Validação de gaps concluída com sucesso");
                        } else {
                            System.out.println("⚠️ Validação de gaps: " + gapStatus);
                            logger.warn("⚠️ Validação de gaps detectou problemas: {}", gapStatus);
                        }
                        
                        System.out.println("🕐 [4/4] Executando validação de janela temporal...");
                        final Map<String, CompletudeValidator.StatusValidacao> temporalResults = validator.validarJanelaTemporal(dataReferencia);
                        temporalValidationOk = temporalResults.values().stream()
                            .allMatch(status -> status == CompletudeValidator.StatusValidacao.OK);
                        
                        if (temporalValidationOk) {
                            System.out.println("✅ Validação temporal: OK");
                            logger.info("✅ Validação de janela temporal concluída com sucesso");
                        } else {
                            System.out.println("❌ Validação temporal detectou problemas críticos!");
                            logger.error("❌ Validação temporal detectou registros criados durante extração - risco de perda de dados");
                        }
                    }
                    
                    // Determina resultado final considerando todas as validações
                    final boolean validacaoFinalCompleta = extracaoCompleta && gapValidationOk && temporalValidationOk;
                    
                    // Exibe resultado final da validação
                    System.out.println("\n" + "=".repeat(60));
                    if (validacaoFinalCompleta) {
                        System.out.println("🎉 EXTRAÇÃO 100% COMPLETA E VALIDADA!");
                        System.out.println("✅ Todos os dados foram extraídos com sucesso!");
                        System.out.println("✅ Validação de gaps: OK");
                        System.out.println("✅ Validação temporal: OK");
                        logger.info("🎉 EXTRAÇÃO 100% COMPLETA! Todas as validações (básica, gaps e temporal) foram bem-sucedidas.");
                    } else {
                        System.out.println("❌ EXTRAÇÃO COM PROBLEMAS - Verificar logs");
                        if (!extracaoCompleta) {
                            System.out.println("⚠️  Inconsistências na contagem de registros detectadas.");
                        }
                        if (!gapValidationOk) {
                            System.out.println("⚠️  Gaps nos IDs detectados - possível perda de registros específicos.");
                        }
                        if (!temporalValidationOk) {
                            System.out.println("❌ CRÍTICO: Registros criados durante extração - risco de perda de dados!");
                        }
                        System.out.println("💡 Consulte os logs detalhados para identificar os problemas.");
                        logger.error("❌ EXTRAÇÃO COM PROBLEMAS - Básica: {}, Gaps: {}, Temporal: {}", 
                            extracaoCompleta, gapValidationOk, temporalValidationOk);
                        
                        // Nota: Implementação futura de alertas por email/Slack pode ser adicionada aqui
                    }
                    System.out.println("=".repeat(60));
                } else {
                    System.out.println("ℹ️ Continuando sem validação de completude (API indisponível)");
                    logger.info("ℹ️ Continuando sem validação de completude (API indisponível)");
                }
                
            } catch (final Exception e) {
                logger.warn("⚠️ Não foi possível comparar com API ESL Cloud: {}", e.getMessage());
                logger.info("ℹ️ Continuando sem validação de completude (dados extraídos estão salvos)");
                logger.debug("Stack trace completo da falha na validação:", e);
                System.out.println("⚠️ Validação de completude falhou - continuando sem validação");
                System.out.println("ℹ️ Os dados extraídos foram salvos com sucesso no banco de dados");
            }
            
        // Exibe resumo final
        final LocalDateTime fimExecucao = LocalDateTime.now();
        
        // Determinar banner e mensagem final baseado no resultado
        if (totalFalhas == 0) {
            BannerUtil.exibirBannerSucesso();
            System.out.println("📊 RESUMO DA EXTRAÇÃO");
            System.out.println("Início: " + inicioExecucao.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            System.out.println("Fim: " + fimExecucao.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            System.out.println("Duração: " + java.time.Duration.between(inicioExecucao, fimExecucao).toMinutes() + " minutos");
            System.out.println("✅ Todas as APIs foram processadas com sucesso!");
            System.out.println();
            
            // Grava timestamp apenas se todos os runners sucederam
            gravarDataExecucao();
        } else {
            BannerUtil.exibirBannerErro();
            System.out.println("📊 RESUMO DA EXTRAÇÃO");
            System.out.println("Início: " + inicioExecucao.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            System.out.println("Fim: " + fimExecucao.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
            System.out.println("Duração: " + java.time.Duration.between(inicioExecucao, fimExecucao).toMinutes() + " minutos");
            System.out.println("⚠️  Execução concluída com falhas parciais:");
            System.out.println("   ✅ Runners bem-sucedidos: " + totalSucessos + "/2");
            System.out.println("   ❌ Runners com falha: " + totalFalhas + "/2 (" + String.join(", ", runnersFalhados) + ")");
            System.out.println("💡 Dados dos runners bem-sucedidos foram salvos no banco de dados.");
            System.out.println();
            
            logger.warn("Execução concluída com falhas parciais. Runners falhados: {}", 
                String.join(", ", runnersFalhados));
            
            // Não grava timestamp se houver falhas (Opção B do plano)
            logger.info("Timestamp de execução não gravado devido a falhas parciais");
        }
    }
    
    /**
     * Cria um Callable que executa uma tarefa que pode lançar Exception.
     * A exceção será capturada pelo Future.get() no loop de verificação.
     * 
     * @param tarefa Tarefa a ser executada (que pode lançar Exception)
     * @return Callable que executa a tarefa
     */
    private Callable<Void> criarCallableRunner(final ExecutavelComExcecao tarefa) {
        return () -> {
            tarefa.executar();
            return null;
        };
    }
    
    /**
     * Interface funcional para tarefas que podem lançar Exception.
     * Usada para simplificar a criação de Callables.
     */
    @FunctionalInterface
    private interface ExecutavelComExcecao {
        void executar() throws Exception;
    }
    
    /**
     * Grava timestamp da execução bem-sucedida.
     */
    private void gravarDataExecucao() {
        try {
            final Properties props = new Properties();
            props.setProperty(PROPRIEDADE_ULTIMO_RUN, LocalDateTime.now().toString());
            
            try (final FileOutputStream fos = new FileOutputStream(ARQUIVO_ULTIMO_RUN)) {
                props.store(fos, "Última execução bem-sucedida do sistema de extração");
            }
            
            logger.info("Timestamp de execução gravado com sucesso");
        } catch (final IOException e) {
            logger.warn("Não foi possível gravar timestamp de execução: {}", e.getMessage());
        }
    }
}
