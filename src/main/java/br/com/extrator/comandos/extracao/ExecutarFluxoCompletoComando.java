/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/extracao/ExecutarFluxoCompletoComando.java
Classe  : ExecutarFluxoCompletoComando (class)
Pacote  : br.com.extrator.comandos.extracao
Modulo  : Comando CLI (extracao)
Papel   : Implementa responsabilidade de executar fluxo completo comando.

Conecta com:
- CompletudeValidator (auditoria.servicos)
- IntegridadeEtlValidator (auditoria.servicos)
- Comando (comandos.base)
- LoggerConsole (util.console)
- DataExportRunner (runners.dataexport)
- GraphQLRunner (runners.graphql)
- BannerUtil (util.console)
- CarregadorConfig (util.configuracao)

Fluxo geral:
1) Interpreta parametros e escopo de extracao.
2) Dispara runners/extratores conforme alvo.
3) Consolida status final e tratamento de falhas.

Estrutura interna:
Metodos principais:
- criarCallableRunner(...1 args): instancia ou monta estrutura de dados.
- gravarDataExecucao(): realiza operacao relacionada a "gravar data execucao".
- possuiFlag(...2 args): realiza operacao relacionada a "possui flag".
- determinarStatusExecutivo(...4 args): realiza operacao relacionada a "determinar status executivo".
- executarPreBackfillReferencialColetas(...2 args): executa o fluxo principal desta responsabilidade.
Atributos-chave:
- log: campo de estado para "log".
- FLAG_SEM_FATURAS_GRAPHQL: campo de estado para "flag sem faturas graphql".
- FLAG_MODO_LOOP_DAEMON: campo de estado para "flag modo loop daemon".
- ARQUIVO_ULTIMO_RUN: campo de estado para "arquivo ultimo run".
- PROPRIEDADE_ULTIMO_RUN: campo de estado para "propriedade ultimo run".
- NUMERO_DE_THREADS: campo de estado para "numero de threads".
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.extracao;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import br.com.extrator.auditoria.servicos.CompletudeValidator;
import br.com.extrator.auditoria.servicos.IntegridadeEtlValidator;
import br.com.extrator.comandos.base.Comando;
import br.com.extrator.util.console.LoggerConsole;
import br.com.extrator.runners.dataexport.DataExportRunner;
import br.com.extrator.runners.graphql.GraphQLRunner;
import br.com.extrator.util.console.BannerUtil;
import br.com.extrator.util.configuracao.CarregadorConfig;
import br.com.extrator.util.formatacao.FormatadorData;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Comando responsável por executar o fluxo completo de extração de dados
 * das 3 APIs do ESL Cloud (REST, GraphQL e DataExport).
 */
public class ExecutarFluxoCompletoComando implements Comando {
    // PROBLEMA #9 CORRIGIDO: Usar LoggerConsole para log duplo (arquivo + console)
    private static final LoggerConsole log = LoggerConsole.getLogger(ExecutarFluxoCompletoComando.class);
    private static final String FLAG_SEM_FATURAS_GRAPHQL = "--sem-faturas-graphql";
    private static final String FLAG_MODO_LOOP_DAEMON = "--modo-loop-daemon";
    
    // Constantes para gravação do timestamp de execução
    private static final String ARQUIVO_ULTIMO_RUN = "last_run.properties";
    private static final String PROPRIEDADE_ULTIMO_RUN = "last_successful_run";
    
    // Número de threads para execução paralela dos runners
    private static final int NUMERO_DE_THREADS = 2;
    
    @Override
    public void executar(final String[] args) throws Exception {
        final boolean incluirFaturasGraphQL = !possuiFlag(args, FLAG_SEM_FATURAS_GRAPHQL);
        final boolean modoLoopDaemon = possuiFlag(args, FLAG_MODO_LOOP_DAEMON);

        // Exibe banner inicial de extração completa
        BannerUtil.exibirBannerExtracaoCompleta();
        
        // Janela padrão da extração completa: últimas 24h (ontem -> hoje)
        final LocalDate dataFim = LocalDate.now();
        final LocalDate dataInicio = dataFim.minusDays(1);
        
        // PROBLEMA #9 CORRIGIDO: Usar LoggerConsole para log duplo
        log.info("Iniciando processo de extração de dados das 2 APIs do ESL Cloud");
        log.console("\n" + "=".repeat(60));
        log.console("INICIANDO PROCESSO DE EXTRAÇÃO DE DADOS");
        log.console("=".repeat(60));
        log.console("Modo: ULTIMAS 24H");
        if (modoLoopDaemon) {
            log.console("Contexto: LOOP DAEMON (integridade final nao bloqueante)");
        }
        log.console("Faturas GraphQL: {}", incluirFaturasGraphQL ? "INCLUIDO" : "DESABILITADO (flag --sem-faturas-graphql)");
        // PROBLEMA 13 CORRIGIDO: Usar FormatadorData em vez de criar formatters inline
        log.console("Periodo de extração: {} a {}", FormatadorData.formatBR(dataInicio), FormatadorData.formatBR(dataFim));
        log.console("Início: {}", FormatadorData.formatBR(LocalDateTime.now()));
        log.console("=".repeat(60) + "\n");
        
        // Mitigacao referencial: preenche coletas de dias retroativos para reduzir
        // manifestos orfaos em base recem-limpa. Essa etapa ocorre ANTES da
        // janela oficial de validacao desta execucao.
        executarPreBackfillReferencialColetas(dataInicio, modoLoopDaemon);
        
        final LocalDateTime inicioExecucao = LocalDateTime.now();
        boolean validacaoFinalCompleta = true;
        String detalheFalhaValidacao = null;
        int completudeEntidadesTotal = -1;
        int completudeEntidadesNaoOk = -1;
        int integridadeFalhas = -1;
        
        // ========== EXECUÇÃO PARALELA DOS RUNNERS ==========
        log.info("🔄 Iniciando fluxo ETL em modo paralelo com {} threads", NUMERO_DE_THREADS);
        
        final ExecutorService executor = Executors.newFixedThreadPool(NUMERO_DE_THREADS);
        // Usar LinkedHashMap para manter ordem de inserção e associar explicitamente nome ao Future
        // Isso elimina o risco de desalinhamento entre ordem de submissão e nomes dos runners
        final Map<String, Future<?>> runnersFuturos = new LinkedHashMap<>();
        final List<String> runnersFalhados = new ArrayList<>();
        int totalSucessos = 0;
        int totalFalhas = 0;
        
        try {
            // Submeter todas as tarefas para execução paralela
            log.info("🔄 [1/2] Submetendo API GraphQL para execução...");
            runnersFuturos.put("GraphQL", executor.submit(criarCallableRunner(() -> GraphQLRunner.executarPorIntervalo(dataInicio, dataFim))));
            
            log.info("🔄 [2/2] Submetendo API Data Export para execução...");
            runnersFuturos.put("DataExport", executor.submit(criarCallableRunner(() -> DataExportRunner.executarPorIntervalo(dataInicio, dataFim))));
            
            log.info("⏳ Aguardando conclusão de todos os runners...");
            
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
                    log.info("✅ API {} concluída com sucesso!", nomeRunner);
                } catch (final ExecutionException e) {
                    // Captura exceção, registra erro e continua
                    totalFalhas++;
                    runnersFalhados.add(nomeRunner);
                    
                    final Throwable causa = e.getCause();
                    final String mensagemErro = causa != null ? causa.getMessage() : e.getMessage();
                    log.error("❌ FALHA NO RUNNER {}: {}. O fluxo continuará.", nomeRunner, mensagemErro, e);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    totalFalhas++;
                    runnersFalhados.add(nomeRunner);
                    log.error("❌ Thread interrompida para runner {}: {}", nomeRunner, e.getMessage(), e);
                }
            }
            
            // Resumo da execução dos runners
            log.console("\n" + "=".repeat(60));
            log.info("📊 RESUMO DA EXECUÇÃO DOS RUNNERS (APIs principais)");
            log.console("=".repeat(60));
            log.info("✅ Runners bem-sucedidos: {}/2", totalSucessos);
            if (totalFalhas > 0) {
                log.warn("❌ Runners com falha: {}/2 - {}", totalFalhas, String.join(", ", runnersFalhados));
            }
            log.console("=".repeat(60) + "\n");
            
        } finally {
            executor.shutdown();
            log.debug("ExecutorService encerrado");
        }
        
        if (incluirFaturasGraphQL) {
            // ========== FASE 3: EXTRAÇÃO DE FATURAS GRAPHQL POR ÚLTIMO ==========
            // Motivo: O enriquecimento de faturas_graphql é muito demorado (50+ minutos),
            // então as outras entidades são priorizadas para garantir dados parciais atualizados no BI.
            log.console("\n" + "=".repeat(60));
            log.info("🔄 [FASE 3] EXECUTANDO FATURAS GRAPHQL POR ÚLTIMO");
            log.console("=".repeat(60));
            log.info("ℹ️ Todas as outras entidades já foram extraídas.");
            log.info("ℹ️ Faturas GraphQL é executado por último devido ao processo de enriquecimento demorado.");
            
            try {
                GraphQLRunner.executarFaturasGraphQLPorIntervalo(dataInicio, dataFim);
                log.info("✅ Faturas GraphQL concluídas com sucesso!");
                totalSucessos++;
            } catch (final Exception e) {
                log.error("❌ Falha na extração de Faturas GraphQL: {}. Dados já extraídos das outras entidades foram preservados.", e.getMessage(), e);
                totalFalhas++;
                runnersFalhados.add("FaturasGraphQL");
            }
            log.console("=".repeat(60) + "\n");
        } else {
            log.console("\n" + "=".repeat(60));
            log.warn("⚠️ [FASE 3] FATURAS GRAPHQL DESABILITADO POR OPÇÃO DO OPERADOR");
            log.info("ℹ️ Flag detectada: {}", FLAG_SEM_FATURAS_GRAPHQL);
            log.console("=".repeat(60) + "\n");
        }

        
        // ========== PASSO B: VALIDAÇÃO DE COMPLETUDE ==========
        log.console("\n" + "=".repeat(60));
        log.info("🔍 INICIANDO VALIDAÇÃO DE COMPLETUDE DOS DADOS");
        log.console("=".repeat(60));
        
        if (totalFalhas > 0) {
            log.warn("⚠️ ATENÇÃO: Runners falhados ({}) - validação pode estar incompleta", String.join(", ", runnersFalhados));
        }
        
        try {
            final CompletudeValidator validator = new CompletudeValidator();
            
            // Usa a data de referência congelada no início da execução para evitar
            // falso ERRO quando o fluxo atravessa meia-noite.
            final LocalDate dataReferencia = dataFim;
            log.info("🔄 [1/2] Validando completude (contagem origem x destino) com base nos logs da execução...");
            final Map<String, CompletudeValidator.StatusValidacao> resultadosValidacao =
                validator.validarCompletudePorLogs(dataReferencia);
            if (!incluirFaturasGraphQL) {
                resultadosValidacao.remove(ConstantesEntidades.FATURAS_GRAPHQL);
                log.info("ℹ️ Validação de completude: {} foi desconsiderada por opção do operador.", ConstantesEntidades.FATURAS_GRAPHQL);
            }

            final boolean extracaoCompleta = resultadosValidacao.values().stream()
                .allMatch(status -> status == CompletudeValidator.StatusValidacao.OK);
            completudeEntidadesTotal = resultadosValidacao.size();
            completudeEntidadesNaoOk = (int) resultadosValidacao.values().stream()
                .filter(status -> status != CompletudeValidator.StatusValidacao.OK)
                .count();

            if (!extracaoCompleta) {
                resultadosValidacao.forEach((entidade, status) -> {
                    if (status != CompletudeValidator.StatusValidacao.OK) {
                        if (modoLoopDaemon) {
                            log.warn("INTEGRIDADE_ETL | resultado=ALERTA_LOOP | codigo=COMPLETUDE | entidade={} | status={}", entidade, status);
                        } else {
                            log.error("INTEGRIDADE_ETL | resultado=FALHA | codigo=COMPLETUDE | entidade={} | status={}", entidade, status);
                        }
                    }
                });
            }

            log.info("🔄 [2/2] Executando validação estrita de integridade ETL...");
            final IntegridadeEtlValidator integridadeValidator = new IntegridadeEtlValidator();
            final Set<String> entidadesEsperadas = new LinkedHashSet<>(List.of(
                ConstantesEntidades.USUARIOS_SISTEMA,
                ConstantesEntidades.COLETAS,
                ConstantesEntidades.FRETES,
                ConstantesEntidades.MANIFESTOS,
                ConstantesEntidades.COTACOES,
                ConstantesEntidades.LOCALIZACAO_CARGAS,
                ConstantesEntidades.CONTAS_A_PAGAR,
                ConstantesEntidades.FATURAS_POR_CLIENTE,
                ConstantesEntidades.FATURAS_GRAPHQL
            ));
            if (!incluirFaturasGraphQL) {
                entidadesEsperadas.remove(ConstantesEntidades.FATURAS_GRAPHQL);
            }

            final IntegridadeEtlValidator.ResultadoValidacao resultadoIntegridade =
                integridadeValidator.validarExecucao(inicioExecucao, LocalDateTime.now(), entidadesEsperadas, modoLoopDaemon);

            if (!resultadoIntegridade.isValido()) {
                if (modoLoopDaemon) {
                    resultadoIntegridade.getFalhas().forEach(falha ->
                        log.warn("INTEGRIDADE_ETL | resultado=ALERTA_LOOP | detalhe={}", falha)
                    );
                } else {
                    resultadoIntegridade.getFalhas().forEach(falha ->
                        log.error("INTEGRIDADE_ETL | resultado=FALHA | detalhe={}", falha)
                    );
                }
            }
            integridadeFalhas = resultadoIntegridade.getFalhas().size();

            validacaoFinalCompleta = extracaoCompleta && resultadoIntegridade.isValido();

            log.console("\n" + "=".repeat(60));
            if (validacaoFinalCompleta) {
                log.info("🎉 EXTRAÇÃO 100% COMPLETA E VALIDADA!");
                log.info("✅ Todos os dados foram extraídos com sucesso!");
            } else {
                detalheFalhaValidacao = "Validacao de integridade reprovada (completude/schema/chaves/referencial).";
                if (modoLoopDaemon) {
                    log.warn("EXTRACAO CONCLUIDA COM ALERTA DE INTEGRIDADE (modo loop daemon)");
                    log.warn("Carga nao foi interrompida; o loop seguira no proximo ciclo.");
                } else {
                    log.error("EXTRACAO COM PROBLEMAS - Verificar logs");
                    log.error("Carga interrompida por divergencia entre origem e destino.");
                }
            }
            log.console("=".repeat(60));
            
        } catch (final Exception e) {
            validacaoFinalCompleta = false;
            detalheFalhaValidacao = "Falha ao executar validações finais: " + e.getMessage();
            log.error("❌ Falha na validação final de integridade: {}", e.getMessage());
            log.debug("Stack trace completo da falha na validação:", e);
        }
            
        // Exibe resumo final
        final LocalDateTime fimExecucao = LocalDateTime.now();
        final long duracaoMinutos = java.time.Duration.between(inicioExecucao, fimExecucao).toMinutes();
        final long duracaoSegundos = java.time.Duration.between(inicioExecucao, fimExecucao).getSeconds();
        final boolean falhaSomenteValidacao = totalFalhas == 0 && !validacaoFinalCompleta;
        final String statusExecutivo = determinarStatusExecutivo(totalFalhas, validacaoFinalCompleta, modoLoopDaemon, falhaSomenteValidacao);

        log.info(
            "RESUMO_EXECUTIVO | status={} | inicio={} | fim={} | duracao_seg={} | duracao_min={} | runners_ok={} | runners_falha={} | validacao_final={} | completude_total={} | completude_nao_ok={} | integridade_falhas={} | modo_loop_daemon={} | faturas_graphql={}",
            statusExecutivo,
            FormatadorData.formatBR(inicioExecucao),
            FormatadorData.formatBR(fimExecucao),
            duracaoSegundos,
            duracaoMinutos,
            totalSucessos,
            totalFalhas,
            validacaoFinalCompleta,
            completudeEntidadesTotal,
            completudeEntidadesNaoOk,
            integridadeFalhas,
            modoLoopDaemon,
            incluirFaturasGraphQL
        );
        if (!runnersFalhados.isEmpty()) {
            log.warn("RESUMO_EXECUTIVO | runners_falhados={}", String.join(", ", runnersFalhados));
        }
        if (detalheFalhaValidacao != null && !detalheFalhaValidacao.isBlank()) {
            log.warn("RESUMO_EXECUTIVO | detalhe_validacao={}", detalheFalhaValidacao);
        }

        if (totalFalhas == 0 && validacaoFinalCompleta) {
            BannerUtil.exibirBannerSucesso();
            log.info("RESUMO DA EXTRACAO");
            log.info("Inicio: {} | Fim: {} | Duracao: {} minutos", 
                FormatadorData.formatBR(inicioExecucao), FormatadorData.formatBR(fimExecucao), duracaoMinutos);
            log.info("Todas as APIs foram processadas com sucesso.");
            gravarDataExecucao();
        } else if (modoLoopDaemon && falhaSomenteValidacao) {
            BannerUtil.exibirBannerSucesso();
            log.warn("RESUMO DA EXTRACAO (com alerta de integridade no loop)");
            log.info("Inicio: {} | Fim: {} | Duracao: {} minutos", 
                FormatadorData.formatBR(inicioExecucao), FormatadorData.formatBR(fimExecucao), duracaoMinutos);
            log.warn("Validacao final reprovada: {}", detalheFalhaValidacao != null ? detalheFalhaValidacao : "divergencia de integridade");
            log.info("Timestamp nao gravado devido a alerta de integridade (modo loop daemon)");
        } else {
            BannerUtil.exibirBannerErro();
            log.warn("📊 RESUMO DA EXTRAÇÃO (com falhas)");
            log.info("Início: {} | Fim: {} | Duração: {} minutos", 
                FormatadorData.formatBR(inicioExecucao), FormatadorData.formatBR(fimExecucao), duracaoMinutos);
            if (totalFalhas > 0) {
                log.warn("⚠️ Execução com falhas parciais: {}/2 runners OK, falhados: {}", 
                    totalSucessos, String.join(", ", runnersFalhados));
            }
            if (!validacaoFinalCompleta) {
                log.error("❌ Validação final reprovada: {}", detalheFalhaValidacao != null ? detalheFalhaValidacao : "divergência de integridade");
            }
            log.info("Timestamp não gravado devido a falhas parciais");
            if (!validacaoFinalCompleta) {
                throw new RuntimeException(
                    "Fluxo completo interrompido por falha de integridade. " +
                    (detalheFalhaValidacao != null ? detalheFalhaValidacao : "Verifique os logs estruturados de validação.")
                );
            }
            throw new PartialExecutionException(
                "Fluxo completo concluído com falhas parciais. Runners falhados: " + String.join(", ", runnersFalhados)
            );
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
            
            log.debug("Timestamp de execução gravado com sucesso");
        } catch (final IOException e) {
            log.warn("Não foi possível gravar timestamp de execução: {}", e.getMessage());
        }
    }

    private boolean possuiFlag(final String[] args, final String flag) {
        if (args == null || flag == null) {
            return false;
        }
        for (final String arg : args) {
            if (flag.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    private String determinarStatusExecutivo(
        final int totalFalhasRunners,
        final boolean validacaoFinalCompleta,
        final boolean modoLoopDaemon,
        final boolean falhaSomenteValidacao
    ) {
        if (totalFalhasRunners == 0 && validacaoFinalCompleta) {
            return "SUCCESS";
        }
        if (modoLoopDaemon && falhaSomenteValidacao) {
            return "SUCCESS_WITH_ALERT";
        }
        if (totalFalhasRunners > 0 && validacaoFinalCompleta) {
            return "PARTIAL";
        }
        return "ERROR";
    }

    private void executarPreBackfillReferencialColetas(final LocalDate dataInicio, final boolean modoLoopDaemon) {
        if (modoLoopDaemon) {
            return;
        }

        final int diasRetroativos = CarregadorConfig.obterEtlReferencialColetasBackfillDias();
        if (diasRetroativos <= 0) {
            log.info("Pre-backfill referencial de coletas desabilitado (etl.referencial.coletas.backfill.dias=0).");
            return;
        }

        final LocalDate backfillInicio = dataInicio.minusDays(diasRetroativos);
        final LocalDate backfillFim = dataInicio.minusDays(1);
        if (backfillInicio.isAfter(backfillFim)) {
            return;
        }

        log.console("\n" + "=".repeat(60));
        log.info(
            "PRE-BACKFILL REFERENCIAL DE COLETAS | periodo={} a {} | dias_retroativos={}",
            FormatadorData.formatBR(backfillInicio),
            FormatadorData.formatBR(backfillFim),
            diasRetroativos
        );
        log.console("=".repeat(60));

        try {
            GraphQLRunner.executarPorIntervalo(backfillInicio, backfillFim, ConstantesEntidades.COLETAS);
            log.info("Pre-backfill referencial de coletas concluido.");
        } catch (final Exception e) {
            log.warn(
                "Pre-backfill referencial de coletas falhou: {}. Fluxo principal seguira normalmente.",
                e.getMessage()
            );
            log.debug("Detalhes da falha no pre-backfill referencial de coletas:", e);
        }
    }
}

