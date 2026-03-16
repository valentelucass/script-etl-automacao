/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/extracao/FluxoCompletoUseCase.java
Classe  : FluxoCompletoUseCase (class)
Pacote  : br.com.extrator.aplicacao.extracao
Modulo  : Use Case - Extracao

Papel   : Orquestra fluxo ETL completo da janela principal D-1..D: extracao, validacao de completude e integridade, data quality.

Conecta com:
- PreBackfillReferencialColetasUseCase (pre-backfill orfaos)
- PipelineOrchestrator (executa steps)
- CompletudePort (valida completude origem x destino)
- IntegridadeEtlPort (valida integridade ETL)
- AplicacaoContexto (obtem portas)
- RelogioSistema (marca tempo de execucao)

Fluxo geral:
1) executar() orquestra extracao completa da janela principal diaria D-1..D.
2) Executa pre-backfill de referencial de coletas (orfaos dinamicos).
3) Orquestra pipeline (GraphQL + DataExport + Data Quality).
4) Valida completude (origem x destino), integridade e data quality.
5) Grava timestamp de ultima execucao bem-sucedida se tudo OK.
6) Retorna status (SUCCESS, PARTIAL, ERROR, SUCCESS_WITH_ALERT em modo daemon).

Estrutura interna:
Metodos principais:
- executar(): ponto de entrada, orquestra fluxo completo.
- determinarStatusExecutivo(): classifica resultado em SUCCESS/PARTIAL/ERROR/ALERT.
- gravarDataExecucao(): persiste timestamp de execucao bem-sucedida.
- nomeRunnerParaResultado(): mapeia step para nome amigavel (GraphQL/DataExport/Faturas).
Atributos-chave:
- ARQUIVO_ULTIMO_RUN: caminho properties com timestamp ultima execucao.
- preBackfillReferencialColetasUseCase: executa pre-backfill.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.extracao;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import br.com.extrator.aplicacao.contexto.AplicacaoContexto;
import br.com.extrator.aplicacao.pipeline.PipelineOrchestrator;
import br.com.extrator.aplicacao.portas.CompletudePort;
import br.com.extrator.aplicacao.portas.IntegridadeEtlPort;
import br.com.extrator.aplicacao.pipeline.PipelineReport;
import br.com.extrator.aplicacao.pipeline.PipelineStep;
import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.aplicacao.pipeline.runtime.StepStatus;
import br.com.extrator.suporte.configuracao.ConfigEtl;
import br.com.extrator.suporte.banco.SqlServerExecutionLockManager;
import br.com.extrator.suporte.console.BannerUtil;
import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.formatacao.FormatadorData;
import br.com.extrator.suporte.tempo.RelogioSistema;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public class FluxoCompletoUseCase {
    private static final String EXECUTION_LOCK_RESOURCE = "etl-global-execution";
    private static final LoggerConsole log = LoggerConsole.getLogger(FluxoCompletoUseCase.class);
    private static final String ARQUIVO_ULTIMO_RUN = "runtime/state/last_run.properties";
    private static final String PROPRIEDADE_ULTIMO_RUN = "last_successful_run";
    private final PreBackfillReferencialColetasUseCase preBackfillReferencialColetasUseCase;
    private final ExecutionLockManager executionLockManager;

    public FluxoCompletoUseCase() {
        this(new PreBackfillReferencialColetasUseCase(), new SqlServerExecutionLockManager());
    }

    FluxoCompletoUseCase(final PreBackfillReferencialColetasUseCase preBackfillReferencialColetasUseCase) {
        this(preBackfillReferencialColetasUseCase, new SqlServerExecutionLockManager());
    }

    FluxoCompletoUseCase(
        final PreBackfillReferencialColetasUseCase preBackfillReferencialColetasUseCase,
        final ExecutionLockManager executionLockManager
    ) {
        this.preBackfillReferencialColetasUseCase = preBackfillReferencialColetasUseCase;
        this.executionLockManager = executionLockManager;
    }

    public void executar(final boolean incluirFaturasGraphQL, final boolean modoLoopDaemon) throws Exception {
        try (AutoCloseable ignored = executionLockManager.acquire(EXECUTION_LOCK_RESOURCE)) {
        BannerUtil.exibirBannerExtracaoCompleta();

        final LocalDate dataFim = RelogioSistema.hoje();
        final LocalDate dataInicio = dataFim.minusDays(1);

        log.info("Iniciando processo de extracao de dados das APIs do ESL Cloud");
        log.console("\n" + "=".repeat(60));
        log.console("INICIANDO PROCESSO DE EXTRACAO DE DADOS");
        log.console("=".repeat(60));
        log.console("Modo: JANELA PRINCIPAL DIARIA D-1..D");
        log.console("Observacao: a janela principal nao representa 24h corridas.");
        log.console("Observacao: coletas executa pre-backfill e pos-hidratacao referencial fora da janela principal.");
        if (modoLoopDaemon) {
            log.console("Contexto: LOOP DAEMON (integridade final nao bloqueante)");
        }
        log.console(
            "Faturas GraphQL: {}",
            incluirFaturasGraphQL ? "INCLUIDO" : "DESABILITADO (flag --sem-faturas-graphql)"
        );
        log.console("Periodo de extracao: {} a {}", FormatadorData.formatBR(dataInicio), FormatadorData.formatBR(dataFim));
        log.console("Inicio: {}", FormatadorData.formatBR(RelogioSistema.agora()));
        log.console("=".repeat(60) + "\n");

        executarPreBackfillReferencialColetas(dataInicio, modoLoopDaemon);

        final LocalDateTime inicioExecucao = RelogioSistema.agora();
        boolean validacaoFinalCompleta = true;
        String detalheFalhaValidacao = null;
        int completudeEntidadesTotal = -1;
        int completudeEntidadesNaoOk = -1;
        int integridadeFalhas = -1;
        boolean qualidadeDadosAprovada = true;
        String detalheQualidadeDados = null;
        long qualidadeChecksFalhas = 0L;

        final List<String> runnersFalhados = new ArrayList<>();
        int totalSucessos = 0;
        int totalFalhas = 0;

        final PipelineOrchestrator orchestrator = AplicacaoContexto.orchestratorFactory().criar();
        final List<PipelineStep> steps = AplicacaoContexto.stepsFactory().criarStepsFluxoCompleto(incluirFaturasGraphQL, true);
        log.info("Iniciando fluxo ETL via PipelineOrchestrator com {} steps", steps.size());

        final PipelineReport pipelineReport = orchestrator.executar(dataInicio, dataFim, steps);
        for (final StepExecutionResult result : pipelineReport.getResultados()) {
            if ("quality".equalsIgnoreCase(result.obterNomeEntidade())) {
                qualidadeDadosAprovada = result.getStatus() == StepStatus.SUCCESS;
                final Object checksFalhas = result.getMetadata().get("checks_failed");
                if (checksFalhas instanceof Number numeroFalhas) {
                    qualidadeChecksFalhas = numeroFalhas.longValue();
                }
                if (!qualidadeDadosAprovada) {
                    detalheQualidadeDados = result.getMessage();
                }
                continue;
            }

            if (result.getStatus() == StepStatus.SUCCESS || result.getStatus() == StepStatus.DEGRADED) {
                totalSucessos++;
                if (result.getStatus() == StepStatus.DEGRADED) {
                    log.warn("Step {} concluido em modo degradado: {}", result.obterNomeEtapa(), result.getMessage());
                } else {
                    log.info("Step {} concluido com sucesso", result.obterNomeEtapa());
                }
                continue;
            }

            totalFalhas++;
            final String nomeRunner = nomeRunnerParaResultado(result);
            runnersFalhados.add(nomeRunner);
            log.error("Falha no runner {}: {}", nomeRunner, result.getMessage());
        }

        if (pipelineReport.isAborted()) {
            totalFalhas++;
            final String abortadoPor = pipelineReport.getAbortedBy() == null ? "desconhecido" : pipelineReport.getAbortedBy();
            runnersFalhados.add("ABORTED:" + abortadoPor);
            log.error("Pipeline abortado por failure policy no step {}", abortadoPor);
        }

        log.console("\n" + "=".repeat(60));
        log.info("RESUMO DA EXECUCAO DOS RUNNERS");
        log.console("=".repeat(60));
        log.info("Runners bem-sucedidos: {}", totalSucessos);
        if (totalFalhas > 0) {
            log.warn("Runners com falha: {} - {}", totalFalhas, String.join(", ", runnersFalhados));
        }
        if (qualidadeDadosAprovada) {
            log.info("Data quality checks aprovados");
        } else {
            log.warn("Data quality checks reprovados: {} falhas", qualidadeChecksFalhas);
        }
        log.console("=".repeat(60) + "\n");

        executarPosHidratacaoReferencialColetas(dataInicio, dataFim, modoLoopDaemon, totalFalhas > 0);

        log.console("\n" + "=".repeat(60));
        log.info("INICIANDO VALIDACAO DE COMPLETUDE DOS DADOS");
        log.console("=".repeat(60));

        if (totalFalhas > 0) {
            log.warn("ATENCAO: Runners falhados ({}) - validacao pode estar incompleta", String.join(", ", runnersFalhados));
        }

        try {
            final CompletudePort completudePort = AplicacaoContexto.completudePort();
            log.info("Validando completude (origem x destino) com base nos logs da execucao");
            final Map<String, CompletudePort.StatusCompletude> resultadosValidacao =
                completudePort.validarCompletudePorLogs(dataFim);

            if (!incluirFaturasGraphQL) {
                resultadosValidacao.remove(ConstantesEntidades.FATURAS_GRAPHQL);
                log.info("Validacao de completude sem {}", ConstantesEntidades.FATURAS_GRAPHQL);
            }

            final boolean extracaoCompleta = resultadosValidacao.values().stream()
                .allMatch(status -> status == CompletudePort.StatusCompletude.OK);
            completudeEntidadesTotal = resultadosValidacao.size();
            completudeEntidadesNaoOk = (int) resultadosValidacao.values().stream()
                .filter(status -> status != CompletudePort.StatusCompletude.OK)
                .count();

            if (!extracaoCompleta) {
                resultadosValidacao.forEach((entidade, status) -> {
                    if (status != CompletudePort.StatusCompletude.OK) {
                        if (modoLoopDaemon) {
                            log.warn(
                                "INTEGRIDADE_ETL | resultado=ALERTA_LOOP | codigo=COMPLETUDE | entidade={} | status={}",
                                entidade,
                                status
                            );
                        } else {
                            log.error(
                                "INTEGRIDADE_ETL | resultado=FALHA | codigo=COMPLETUDE | entidade={} | status={}",
                                entidade,
                                status
                            );
                        }
                    }
                });
            }

            log.info("Executando validacao estrita de integridade ETL");
            final IntegridadeEtlPort integridadePort = AplicacaoContexto.integridadeEtlPort();
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

            final IntegridadeEtlPort.ResultadoIntegridade resultadoIntegridade =
                integridadePort.validarExecucao(inicioExecucao, RelogioSistema.agora(), entidadesEsperadas, modoLoopDaemon);

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

            validacaoFinalCompleta = extracaoCompleta && resultadoIntegridade.isValido() && qualidadeDadosAprovada;
            if (!qualidadeDadosAprovada && (detalheQualidadeDados == null || detalheQualidadeDados.isBlank())) {
                detalheQualidadeDados = "Data quality reprovada";
            }

            log.console("\n" + "=".repeat(60));
            if (validacaoFinalCompleta) {
                log.info("EXTRACAO COMPLETA E VALIDADA");
            } else {
                final List<String> motivos = new ArrayList<>();
                if (!extracaoCompleta || !resultadoIntegridade.isValido()) {
                    motivos.add("Validacao de integridade reprovada (completude/schema/chaves/referencial)");
                }
                if (!qualidadeDadosAprovada) {
                    motivos.add(detalheQualidadeDados == null ? "Data quality reprovada" : detalheQualidadeDados);
                }
                detalheFalhaValidacao = String.join(" | ", motivos);
                if (modoLoopDaemon) {
                    log.warn("EXTRACAO CONCLUIDA COM ALERTA DE INTEGRIDADE (modo loop daemon)");
                } else {
                    log.error("EXTRACAO COM PROBLEMAS - Verificar logs");
                }
            }
            log.console("=".repeat(60));

        } catch (final Exception e) {
            validacaoFinalCompleta = false;
            detalheFalhaValidacao = "Falha ao executar validacoes finais: " + e.getMessage();
            log.error("Falha na validacao final de integridade: {}", e.getMessage());
            log.debug("Stack trace completo da falha na validacao:", e);
        }

        final LocalDateTime fimExecucao = RelogioSistema.agora();
        final long duracaoMinutos = Duration.between(inicioExecucao, fimExecucao).toMinutes();
        final long duracaoSegundos = Duration.between(inicioExecucao, fimExecucao).getSeconds();
        final boolean falhaSomenteValidacao = totalFalhas == 0 && !validacaoFinalCompleta;
        final String statusExecutivo = determinarStatusExecutivo(
            totalFalhas,
            validacaoFinalCompleta,
            modoLoopDaemon,
            falhaSomenteValidacao
        );

        log.info(
            "RESUMO_EXECUTIVO | status={} | inicio={} | fim={} | duracao_seg={} | duracao_min={} | runners_ok={} | runners_falha={} | validacao_final={} | completude_total={} | completude_nao_ok={} | integridade_falhas={} | quality_ok={} | quality_falhas={} | modo_loop_daemon={} | faturas_graphql={}",
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
            qualidadeDadosAprovada,
            qualidadeChecksFalhas,
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
            log.info(
                "Inicio: {} | Fim: {} | Duracao: {} minutos",
                FormatadorData.formatBR(inicioExecucao),
                FormatadorData.formatBR(fimExecucao),
                duracaoMinutos
            );
            log.info("Todas as APIs foram processadas com sucesso.");
            gravarDataExecucao();
            return;
        }

        if (modoLoopDaemon && falhaSomenteValidacao) {
            BannerUtil.exibirBannerSucesso();
            log.warn("RESUMO DA EXTRACAO (com alerta de integridade no loop)");
            log.info(
                "Inicio: {} | Fim: {} | Duracao: {} minutos",
                FormatadorData.formatBR(inicioExecucao),
                FormatadorData.formatBR(fimExecucao),
                duracaoMinutos
            );
            log.warn(
                "Validacao final reprovada: {}",
                detalheFalhaValidacao != null ? detalheFalhaValidacao : "divergencia de integridade"
            );
            log.info("Timestamp nao gravado devido a alerta de integridade (modo loop daemon)");
            return;
        }

        BannerUtil.exibirBannerErro();
        log.warn("RESUMO DA EXTRACAO (com falhas)");
        log.info(
            "Inicio: {} | Fim: {} | Duracao: {} minutos",
            FormatadorData.formatBR(inicioExecucao),
            FormatadorData.formatBR(fimExecucao),
            duracaoMinutos
        );
        if (totalFalhas > 0) {
            log.warn("Execucao com falhas parciais: runners falhados: {}", String.join(", ", runnersFalhados));
        }
        if (!validacaoFinalCompleta) {
            log.error(
                "Validacao final reprovada: {}",
                detalheFalhaValidacao != null ? detalheFalhaValidacao : "divergencia de integridade"
            );
        }
        log.info("Timestamp nao gravado devido a falhas");

        if (!validacaoFinalCompleta) {
            throw new RuntimeException(
                "Fluxo completo interrompido por falha de integridade. "
                    + (detalheFalhaValidacao != null
                        ? detalheFalhaValidacao
                        : "Verifique os logs estruturados de validacao.")
            );
        }

        throw new PartialExecutionException(
            "Fluxo completo concluido com falhas parciais. Runners falhados: " + String.join(", ", runnersFalhados)
        );
        }
    }

    private void gravarDataExecucao() {
        try {
            final Properties props = new Properties();
            props.setProperty(PROPRIEDADE_ULTIMO_RUN, RelogioSistema.agora().toString());
            final Path arquivoUltimoRun = Path.of(ARQUIVO_ULTIMO_RUN);
            Files.createDirectories(arquivoUltimoRun.getParent());

            try (FileOutputStream fos = new FileOutputStream(arquivoUltimoRun.toFile())) {
                props.store(fos, "Ultima execucao bem-sucedida do sistema de extracao");
            }

            log.debug("Timestamp de execucao gravado com sucesso");
        } catch (final IOException e) {
            log.warn("Nao foi possivel gravar timestamp de execucao: {}", e.getMessage());
        }
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

    private String nomeRunnerParaResultado(final StepExecutionResult result) {
        final String step = result.obterNomeEtapa() == null ? "" : result.obterNomeEtapa().toLowerCase(Locale.ROOT);
        if (step.contains(ConstantesEntidades.FATURAS_GRAPHQL)) {
            return "FaturasGraphQL";
        }
        if (step.startsWith("graphql:")) {
            return "GraphQL";
        }
        if (step.startsWith("dataexport:")) {
            return "DataExport";
        }
        final String entidade = result.obterNomeEntidade();
        return entidade == null || entidade.isBlank() ? "desconhecido" : entidade;
    }

    private void executarPreBackfillReferencialColetas(final LocalDate dataInicio, final boolean modoLoopDaemon) {
        if (modoLoopDaemon) {
            return;
        }

        final int diasRetroativos = ConfigEtl.obterEtlReferencialColetasBackfillDias();
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
            preBackfillReferencialColetasUseCase.executar(backfillInicio, backfillFim);
            log.info("Pre-backfill referencial de coletas concluido.");
        } catch (final Exception e) {
            log.warn(
                "Pre-backfill referencial de coletas falhou: {}. Fluxo principal seguira normalmente.",
                e.getMessage()
            );
            log.debug("Detalhes da falha no pre-backfill referencial de coletas:", e);
        }
    }

    private void executarPosHidratacaoReferencialColetas(
        final LocalDate dataInicio,
        final LocalDate dataFim,
        final boolean modoLoopDaemon,
        final boolean houveFalhaNosRunners
    ) {
        if (modoLoopDaemon || houveFalhaNosRunners) {
            return;
        }

        final int lookaheadDias = ConfigEtl.obterEtlReferencialColetasLookaheadDias();
        if (lookaheadDias <= 0) {
            return;
        }

        log.console("\n" + "=".repeat(60));
        log.info(
            "POS-HIDRATACAO REFERENCIAL DE COLETAS | periodo={} a {} | lookahead_dias={}",
            FormatadorData.formatBR(dataInicio),
            FormatadorData.formatBR(dataFim),
            lookaheadDias
        );
        log.console("=".repeat(60));

        try {
            preBackfillReferencialColetasUseCase.executarPosExtracao(dataInicio, dataFim);
            log.info("Pos-hidratacao referencial de coletas concluida.");
        } catch (final Exception e) {
            log.warn(
                "Pos-hidratacao referencial de coletas falhou: {}. Validacao final seguira normalmente.",
                e.getMessage()
            );
            log.debug("Detalhes da falha na pos-hidratacao referencial de coletas:", e);
        }
    }
}
