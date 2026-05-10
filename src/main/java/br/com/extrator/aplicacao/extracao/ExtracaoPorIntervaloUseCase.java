/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/extracao/ExtracaoPorIntervaloUseCase.java
Classe  : ExtracaoPorIntervaloUseCase (class)
Pacote  : br.com.extrator.aplicacao.extracao
Modulo  : Use Case - Extracao

Papel   : Orquestra extracao de dados em intervalo de datas, dividindo em blocos de 30 dias.

Conecta com:
- PreBackfillReferencialColetasUseCase (executa pre-backfill)
- PlanejadorEscopoExtracaoIntervalo (planeja steps)
- PipelineOrchestrator (executa pipeline)
- IntegridadeEtlPort (valida integridade)
- AplicacaoContexto (obtem portas)

Fluxo geral:
1) executar() recebe request (datas, filtros, flags).
2) Valida limites de extracao por entidade e divide periodo em blocos (30 dias).
3) Para cada bloco: cria steps, orquestra pipeline, valida volume e integridade.
4) Coleta falhas e lanca PartialExecutionException se houver falhas parciais.

Estrutura interna:
Metodos principais:
- executar(ExtracaoPorIntervaloRequest): ponto de entrada, orquestra blocos.
- dividirEmBlocos(): subdivide intervalo em blocos de ate 30 dias.
- validarLimitesParaBloco(): verifica limite de extracao (horas) por entidade.
- registrarResultadosBloco(): coleta status e falhas do pipeline.
- validarResultadosCriticosDoBloco(): valida volume e integridade apos execucao.
Atributos-chave:
- TAMANHO_BLOCO_DIAS: constante 30 (tamanho de bloco).
- preBackfillReferencialColetasUseCase: executa backfill de orfaos.
- planejadorEscopo: planeja steps conforme filtros de requisicao.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.extracao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import br.com.extrator.aplicacao.contexto.AplicacaoContexto;
import br.com.extrator.aplicacao.portas.ExtractionLogQueryPort;
import br.com.extrator.aplicacao.portas.IntegridadeEtlPort;
import br.com.extrator.aplicacao.pipeline.PipelineOrchestrator;
import br.com.extrator.aplicacao.pipeline.PipelineReport;
import br.com.extrator.aplicacao.pipeline.PipelineStep;
import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.aplicacao.pipeline.runtime.StepStatus;
import br.com.extrator.suporte.configuracao.ConfigEtl;
import br.com.extrator.suporte.banco.SqlServerExecutionLockManager;
import br.com.extrator.suporte.console.BannerUtil;
import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.formatacao.FormatadorData;
import br.com.extrator.suporte.configuracao.ScopedSystemPropertyOverride;
import br.com.extrator.suporte.observabilidade.ExecutionContext;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public class ExtracaoPorIntervaloUseCase {
    private static final String EXECUTION_LOCK_RESOURCE = "etl-global-execution";
    private static final String PROP_PRUNE_AUSENTES_FRETES = "ETL_FRETES_PRUNE_AUSENTES";
    private static final String PROP_TIMEOUT_FRETES = "ETL_GRAPHQL_TIMEOUT_ENTIDADE_FRETES_MS";
    private static final String PROP_TIMEOUT_COLETAS = "ETL_GRAPHQL_TIMEOUT_ENTIDADE_COLETAS_MS";
    private static final String PROP_BACKFILL_MAX_EXPANSAO_COLETAS =
        "ETL_REFERENCIAL_COLETAS_BACKFILL_MAX_EXPANSAO_DIAS";
    private static final String PROP_BACKFILL_DIAS_COLETAS =
        "ETL_REFERENCIAL_COLETAS_BACKFILL_DIAS";
    private static final String PROP_LOOKAHEAD_DIAS_COLETAS =
        "ETL_REFERENCIAL_COLETAS_LOOKAHEAD_DIAS";
    private static final String PROP_LOOKBACK_MODO_FRETES =
        "ETL_FRETES_PERFORMANCE_LOOKBACK_MODO";
    private static final long TIMEOUT_FRETES_INTERVALO_MS = 10_800_000L;
    private static final LoggerConsole log = LoggerConsole.getLogger(ExtracaoPorIntervaloUseCase.class);
    private static final int TAMANHO_BLOCO_DIAS = 30;
    private final PreBackfillReferencialColetasUseCase preBackfillReferencialColetasUseCase;
    private final PlanejadorEscopoExtracaoIntervalo planejadorEscopo;
    private final ExecutionLockManager executionLockManager;

    public ExtracaoPorIntervaloUseCase() {
        this(
            new PreBackfillReferencialColetasUseCase(),
            new PlanejadorEscopoExtracaoIntervalo(),
            new SqlServerExecutionLockManager()
        );
    }

    ExtracaoPorIntervaloUseCase(
        final PreBackfillReferencialColetasUseCase preBackfillReferencialColetasUseCase,
        final PlanejadorEscopoExtracaoIntervalo planejadorEscopo
    ) {
        this(preBackfillReferencialColetasUseCase, planejadorEscopo, new SqlServerExecutionLockManager());
    }

    ExtracaoPorIntervaloUseCase(
        final PreBackfillReferencialColetasUseCase preBackfillReferencialColetasUseCase,
        final PlanejadorEscopoExtracaoIntervalo planejadorEscopo,
        final ExecutionLockManager executionLockManager
    ) {
        this.preBackfillReferencialColetasUseCase = preBackfillReferencialColetasUseCase;
        this.planejadorEscopo = planejadorEscopo;
        this.executionLockManager = executionLockManager;
    }

    public void executar(final ExtracaoPorIntervaloRequest request) throws Exception {
        try (AutoCloseable ignored = executionLockManager.acquire(EXECUTION_LOCK_RESOURCE)) {
        final boolean modoRapido24h = request.modoRapido24h();
        final Map<String, String> overridesTemporarios = new LinkedHashMap<>();
        overridesTemporarios.put(PROP_PRUNE_AUSENTES_FRETES, Boolean.TRUE.toString());
        overridesTemporarios.put(PROP_LOOKBACK_MODO_FRETES, resolverModoLookbackFretes(request));
        overridesTemporarios.put(PROP_TIMEOUT_FRETES, resolverTimeoutMinimo(PROP_TIMEOUT_FRETES, TIMEOUT_FRETES_INTERVALO_MS));
        overridesTemporarios.put(
            PROP_TIMEOUT_COLETAS,
            resolverTimeoutMinimo(
                PROP_TIMEOUT_COLETAS,
                ConfigEtl.obterTimeoutEntidadeGraphQLColetasIntervalo().toMillis()
            )
        );
        overridesTemporarios.put(
            PROP_BACKFILL_MAX_EXPANSAO_COLETAS,
            resolverInteiroMinimo(
                PROP_BACKFILL_MAX_EXPANSAO_COLETAS,
                ConfigEtl.obterEtlReferencialColetasBackfillMaxExpansaoDiasIntervalo()
            )
        );
        if (modoRapido24h) {
            overridesTemporarios.put(PROP_BACKFILL_DIAS_COLETAS, "0");
            overridesTemporarios.put(PROP_LOOKAHEAD_DIAS_COLETAS, "0");
        }
        try (ScopedSystemPropertyOverride scopedOverride = ScopedSystemPropertyOverride.apply(overridesTemporarios)) {
        final LocalDate dataInicio = request.dataInicio();
        final LocalDate dataFim = request.dataFim();
        final String apiEspecifica = request.apiEspecifica();
        final String entidadeEspecifica = request.entidadeEspecifica();
        final boolean incluirFaturasGraphQL = request.incluirFaturasGraphQL();
        final boolean modoLoopDaemon = request.modoLoopDaemon();

        BannerUtil.exibirBannerExtracaoPorIntervalo();

        log.console("\n" + "=".repeat(60));
        log.console("EXTRACAO POR INTERVALO DE DATAS");
        log.console("=".repeat(60));
        log.console("Periodo solicitado: {} a {}", FormatadorData.formatBR(dataInicio), FormatadorData.formatBR(dataFim));

        if (apiEspecifica != null && !apiEspecifica.isEmpty()) {
            log.console("API: {}", apiEspecifica.toUpperCase(Locale.ROOT));
            if (entidadeEspecifica != null && !entidadeEspecifica.isEmpty()) {
                log.console("Entidade: {}", entidadeEspecifica);
            } else {
                log.console("Entidade: TODAS");
            }
        } else {
            log.console("API: TODAS");
            log.console("Entidade: TODAS");
        }
        log.console(
            "Faturas GraphQL: {}",
            descreverFaturasGraphQL(apiEspecifica, incluirFaturasGraphQL)
        );
        if (modoRapido24h) {
            log.console("Modo rapido 24h: ATIVO (sem pre-backfill e sem pos-hidratacao referencial de coletas)");
        }

        final ValidadorLimiteExtracao validador = new ValidadorLimiteExtracao();
        final long diasPeriodo = validador.calcularDuracaoPeriodo(dataInicio, dataFim);
        log.console("Duracao: {} dias", diasPeriodo);

        final int limiteHorasPeriodoTotal = validador.obterLimiteHoras(diasPeriodo);
        if (limiteHorasPeriodoTotal == 0) {
            log.console("Regra de limitacao: SEM LIMITE (periodo < 31 dias)");
        } else if (limiteHorasPeriodoTotal == 1) {
            log.console("Regra de limitacao: 1 HORA entre extracoes (periodo de 31 dias a 6 meses)");
        } else {
            log.console("Regra de limitacao: 12 HORAS entre extracoes (periodo > 6 meses)");
        }
        log.console("Nota: Validacao sera feita por BLOCO (30 dias = sem limite), nao pelo periodo total");

        final List<BlocoPeriodo> blocos = dividirEmBlocos(dataInicio, dataFim);
        log.console("Total de blocos: {}", blocos.size());

        if (blocos.size() > 1) {
            log.console("\nPeriodo sera dividido em {} blocos de ate {} dias:", blocos.size(), TAMANHO_BLOCO_DIAS);
            for (int i = 0; i < blocos.size(); i++) {
                final BlocoPeriodo bloco = blocos.get(i);
                log.console(
                    "  Bloco {}/{}: {} a {} ({} dias)",
                    i + 1,
                    blocos.size(),
                    FormatadorData.formatBR(bloco.dataInicio),
                    FormatadorData.formatBR(bloco.dataFim),
                    validador.calcularDuracaoPeriodo(bloco.dataInicio, bloco.dataFim)
                );
            }
        }

        log.console("=".repeat(60) + "\n");

        executarPreBackfillReferencialColetas(
            dataInicio,
            apiEspecifica,
            entidadeEspecifica,
            modoLoopDaemon,
            modoRapido24h
        );

        final LocalDateTime inicioExecucao = LocalDateTime.now();
        int blocosCompletos = 0;
        int blocosFalhados = 0;
        int falhasCriticasConsecutivasColetas = 0;
        final int limiteFalhasCriticasColetas = ConfigEtl.obterEtlIntervaloColetasMaxConsecutiveFailures();
        boolean abortadoPorFalhasCriticasColetas = false;
        String motivoAbortoColetas = null;
        final List<String> blocosFalhadosLista = new ArrayList<>();
        final List<ResumoEntidadeBloco> resumosEntidades = new ArrayList<>();

        for (int i = 0; i < blocos.size(); i++) {
            final BlocoPeriodo bloco = blocos.get(i);
            final int numeroBloco = i + 1;
            final int totalBlocos = blocos.size();

            log.console("\n" + "=".repeat(60));
            log.console(
                "BLOCO {}/{}: {} a {}",
                numeroBloco,
                totalBlocos,
                FormatadorData.formatBR(bloco.dataInicio),
                FormatadorData.formatBR(bloco.dataFim)
            );
            log.console("=".repeat(60));

            final boolean podeExecutar = validarLimitesParaBloco(
                bloco,
                validador,
                apiEspecifica,
                entidadeEspecifica,
                incluirFaturasGraphQL
            );

            if (!podeExecutar) {
                log.warn("Bloco {}/{} bloqueado pelas regras de limitacao. Pulando...", numeroBloco, totalBlocos);
                blocosFalhados++;
                blocosFalhadosLista.add("Bloco " + numeroBloco);
                continue;
            }

            log.info("Iniciando extracao do bloco {}/{}...", numeroBloco, totalBlocos);
            final LocalDateTime inicioExecucaoBloco = LocalDateTime.now();
            boolean blocoComFalha = false;
            final List<String> falhasBloco = new ArrayList<>();
            final PipelineOrchestrator orchestrator = AplicacaoContexto.orchestratorFactory().criar();
            final List<PipelineStep> steps = planejadorEscopo.criarSteps(
                apiEspecifica,
                entidadeEspecifica,
                incluirFaturasGraphQL
            );
            final PipelineReport pipelineReport = orchestrator.executar(bloco.dataInicio, bloco.dataFim, steps);
            blocoComFalha = registrarResultadosBloco(numeroBloco, totalBlocos, pipelineReport, falhasBloco);

            if (!blocoComFalha && !steps.isEmpty()) {
                log.info("Bloco {}/{} concluido com sucesso via PipelineOrchestrator!", numeroBloco, totalBlocos);
            }

            executarPosHidratacaoReferencialColetas(
                bloco.dataInicio,
                bloco.dataFim,
                apiEspecifica,
                entidadeEspecifica,
                modoLoopDaemon,
                blocoComFalha,
                modoRapido24h
            );

            final LocalDateTime fimExecucaoBloco = LocalDateTime.now();
            final Map<String, Optional<LogExtracaoInfo>> logsBloco = buscarLogsBloco(
                inicioExecucaoBloco,
                fimExecucaoBloco,
                apiEspecifica,
                entidadeEspecifica,
                incluirFaturasGraphQL
            );
            resumosEntidades.addAll(montarResumoBloco(
                bloco,
                numeroBloco,
                totalBlocos,
                logsBloco
            ));
            final List<String> falhasDeVolume = validarResultadosCriticosDoBloco(
                bloco,
                inicioExecucaoBloco,
                fimExecucaoBloco,
                apiEspecifica,
                entidadeEspecifica,
                incluirFaturasGraphQL,
                modoLoopDaemon,
                logsBloco
            );
            if (!falhasDeVolume.isEmpty()) {
                blocoComFalha = true;
                falhasBloco.addAll(falhasDeVolume);
                log.warn(
                    "Bloco {}/{} apresentou inconsistencias de volume em entidades criticas:",
                    numeroBloco,
                    totalBlocos
                );
                for (final String falhaVolume : falhasDeVolume) {
                    log.warn("   - {}", falhaVolume);
                }
            }

            final boolean falhaCriticaColetas = houveFalhaCriticaColetas(pipelineReport, falhasBloco);
            if (falhaCriticaColetas) {
                falhasCriticasConsecutivasColetas++;
                log.warn(
                    "Bloco {}/{} contabilizado como falha critica de coletas (sequencia atual: {}/{}).",
                    numeroBloco,
                    totalBlocos,
                    falhasCriticasConsecutivasColetas,
                    limiteFalhasCriticasColetas
                );
            } else {
                falhasCriticasConsecutivasColetas = 0;
            }

            if (blocoComFalha) {
                blocosFalhados++;
                if (falhasBloco.isEmpty()) {
                    blocosFalhadosLista.add("Bloco " + numeroBloco);
                } else {
                    blocosFalhadosLista.add("Bloco " + numeroBloco + " (" + String.join(" | ", falhasBloco) + ")");
                }
            } else {
                blocosCompletos++;
            }

            if (falhaCriticaColetas && falhasCriticasConsecutivasColetas >= limiteFalhasCriticasColetas) {
                abortadoPorFalhasCriticasColetas = true;
                motivoAbortoColetas = String.format(
                    "Extracao por intervalo abortada apos %d falhas criticas consecutivas de coletas. "
                        + "Ultimo bloco processado: %d/%d (%s a %s). Blocos falhados acumulados: %s",
                    falhasCriticasConsecutivasColetas,
                    numeroBloco,
                    totalBlocos,
                    FormatadorData.formatBR(bloco.dataInicio),
                    FormatadorData.formatBR(bloco.dataFim),
                    String.join(", ", blocosFalhadosLista)
                );
                log.error("{}", motivoAbortoColetas);
                break;
            }
        }

        final LocalDateTime fimExecucao = LocalDateTime.now();
        final long duracaoMinutos = java.time.Duration.between(inicioExecucao, fimExecucao).toMinutes();

        log.console("\n" + "=".repeat(60));
        log.console("RESUMO DA EXTRACAO POR INTERVALO");
        log.console("=".repeat(60));
        log.console("Periodo: {} a {}", FormatadorData.formatBR(dataInicio), FormatadorData.formatBR(dataFim));
        log.console("Total de blocos: {}", blocos.size());
        log.console("Blocos completos: {}", blocosCompletos);
        if (blocosFalhados > 0) {
            log.warn("Blocos falhados: {} - {}", blocosFalhados, String.join(", ", blocosFalhadosLista));
        }
        imprimirResumoDetalhado(resumosEntidades);
        log.console("Duracao total: {} minutos", duracaoMinutos);
        log.console("=".repeat(60));

        if (abortadoPorFalhasCriticasColetas) {
            BannerUtil.exibirBannerErro();
            throw new PartialExecutionException(motivoAbortoColetas);
        }

        if (blocosFalhados == 0) {
            BannerUtil.exibirBannerSucesso();
            return;
        }

        BannerUtil.exibirBannerErro();
        throw new PartialExecutionException(
            "Extracao por intervalo concluida com falhas parciais. Blocos falhados: "
                + blocosFalhados
                + " - "
                + String.join(", ", blocosFalhadosLista)
        );
        }
        }
    }

    private String resolverTimeoutMinimo(final String propriedade, final long timeoutMinimoMs) {
        final String atual = System.getProperty(propriedade);
        if (atual != null) {
            try {
                if (Long.parseLong(atual) >= timeoutMinimoMs) {
                    return atual;
                }
            } catch (final NumberFormatException ignored) {
                // Valor invalido atual sera substituido pelo minimo seguro para backfill.
            }
        }
        return Long.toString(timeoutMinimoMs);
    }

    private String resolverModoLookbackFretes(final ExtracaoPorIntervaloRequest request) {
        if (request != null && request.modoLoopDaemon()) {
            return "reconciliacao";
        }
        if ("--recovery".equalsIgnoreCase(ExecutionContext.currentCommand())) {
            return "backfill";
        }
        return "intervalo";
    }

    private String resolverInteiroMinimo(final String propriedade, final int valorMinimo) {
        final String atual = System.getProperty(propriedade);
        if (atual != null) {
            try {
                if (Integer.parseInt(atual) >= valorMinimo) {
                    return atual;
                }
            } catch (final NumberFormatException ignored) {
                // Valor invalido atual sera substituido pelo minimo seguro para backfill.
            }
        }
        return Integer.toString(valorMinimo);
    }

    private List<BlocoPeriodo> dividirEmBlocos(final LocalDate dataInicio, final LocalDate dataFim) {
        final List<BlocoPeriodo> blocos = new ArrayList<>();
        LocalDate inicioBloco = dataInicio;

        while (!inicioBloco.isAfter(dataFim)) {
            final LocalDate fimBloco = inicioBloco.plusDays(TAMANHO_BLOCO_DIAS - 1);
            final LocalDate fimReal = fimBloco.isAfter(dataFim) ? dataFim : fimBloco;

            blocos.add(new BlocoPeriodo(inicioBloco, fimReal));
            inicioBloco = fimReal.plusDays(1);
        }

        return blocos;
    }

    private boolean validarLimitesParaBloco(
        final BlocoPeriodo bloco,
        final ValidadorLimiteExtracao validador,
        final String apiEspecifica,
        final String entidadeEspecifica,
        final boolean incluirFaturasGraphQL
    ) {
        boolean todasPermitidas = true;
        for (final String entidade : planejadorEscopo.determinarEntidadesParaLimite(
            apiEspecifica,
            entidadeEspecifica,
            incluirFaturasGraphQL
        )) {
            final ValidadorLimiteExtracao.ResultadoValidacao resultado =
                validador.validarLimiteExtracao(entidade, bloco.dataInicio, bloco.dataFim);

            if (!resultado.isPermitido()) {
                log.warn("{}: {}", entidade, resultado.getMotivo());
                log.console("   Aguarde {} hora(s) antes de tentar novamente", resultado.getHorasRestantes());
                todasPermitidas = false;
            }
        }

        return todasPermitidas;
    }

    private boolean registrarResultadosBloco(
        final int numeroBloco,
        final int totalBlocos,
        final PipelineReport pipelineReport,
        final List<String> falhasBloco
    ) {
        boolean blocoComFalha = false;
        for (final StepExecutionResult result : pipelineReport.getResultados()) {
            if (result.getStatus() == StepStatus.SUCCESS || result.getStatus() == StepStatus.DEGRADED) {
                if (result.getStatus() == StepStatus.DEGRADED) {
                    log.warn(
                        "Step {} do bloco {}/{} concluiu em modo degradado: {}",
                        result.obterNomeEtapa(),
                        numeroBloco,
                        totalBlocos,
                        result.getMessage()
                    );
                }
                continue;
            }

            blocoComFalha = true;
            final String falha = (result.obterNomeEntidade() == null ? result.obterNomeEtapa() : result.obterNomeEntidade())
                + ": "
                + (result.getMessage() == null ? "falha sem mensagem" : result.getMessage());
            falhasBloco.add(falha);
            log.error(
                "Falha no step {} do bloco {}/{}: {}",
                result.obterNomeEtapa(),
                numeroBloco,
                totalBlocos,
                result.getMessage()
            );
        }

        if (pipelineReport.isAborted()) {
            blocoComFalha = true;
            final String abortadoPor = pipelineReport.getAbortedBy() == null ? "desconhecido" : pipelineReport.getAbortedBy();
            falhasBloco.add("ABORTED:" + abortadoPor);
            log.error("Pipeline do bloco {}/{} abortado pelo step {}", numeroBloco, totalBlocos, abortadoPor);
        }
        return blocoComFalha;
    }

    private boolean houveFalhaCriticaColetas(
        final PipelineReport pipelineReport,
        final List<String> falhasBloco
    ) {
        final boolean falhaDiretaColetas = pipelineReport.getResultados().stream()
            .anyMatch(this::isFalhaDiretaColetas);
        if (falhaDiretaColetas) {
            return true;
        }
        if (pipelineReport.isAborted() && referenciaColetas(pipelineReport.getAbortedBy())) {
            return true;
        }
        return falhasBloco.stream().anyMatch(this::isFalhaCriticaColetas);
    }

    private boolean isFalhaDiretaColetas(final StepExecutionResult result) {
        return result != null
            && result.getStatus() == StepStatus.FAILED
            && (referenciaColetas(result.obterNomeEntidade()) || referenciaColetas(result.obterNomeEtapa()));
    }

    private boolean isFalhaCriticaColetas(final String falha) {
        final String falhaNormalizada = falha == null ? "" : falha.toLowerCase(Locale.ROOT);
        if (falhaNormalizada.contains("audit_ausente") && falhaNormalizada.contains("coletas")) {
            return true;
        }
        if (falhaNormalizada.contains("coletas sem log")) {
            return true;
        }
        return falhaNormalizada.contains("integridade_referencial_manifestos")
            && falhaNormalizada.contains("contexto_coletas={sem_auditoria}");
    }

    private boolean referenciaColetas(final String valor) {
        return valor != null && valor.toLowerCase(Locale.ROOT).contains("coletas");
    }

    private Map<String, Optional<LogExtracaoInfo>> buscarLogsBloco(
        final LocalDateTime inicioExecucaoBloco,
        final LocalDateTime fimExecucaoBloco,
        final String apiEspecifica,
        final String entidadeEspecifica,
        final boolean incluirFaturasGraphQL
    ) {
        final Map<String, Optional<LogExtracaoInfo>> logsBloco = new LinkedHashMap<>();
        final Set<String> entidadesResumo = planejadorEscopo.determinarEntidadesParaResumo(
            apiEspecifica,
            entidadeEspecifica,
            incluirFaturasGraphQL
        );
        if (entidadesResumo.isEmpty()) {
            return logsBloco;
        }

        final ExtractionLogQueryPort logQueryPort = AplicacaoContexto.extractionLogQueryPort();
        for (final String entidade : entidadesResumo) {
            logsBloco.put(
                entidade,
                logQueryPort.buscarUltimoLogPorEntidadeNoIntervaloExecucao(
                    entidade,
                    inicioExecucaoBloco,
                    fimExecucaoBloco
                )
            );
        }
        return logsBloco;
    }

    private List<ResumoEntidadeBloco> montarResumoBloco(
        final BlocoPeriodo bloco,
        final int numeroBloco,
        final int totalBlocos,
        final Map<String, Optional<LogExtracaoInfo>> logsBloco
    ) {
        final List<ResumoEntidadeBloco> resumos = new ArrayList<>();
        for (final Map.Entry<String, Optional<LogExtracaoInfo>> entry : logsBloco.entrySet()) {
            resumos.add(criarResumoEntidade(
                bloco,
                numeroBloco,
                totalBlocos,
                entry.getKey(),
                entry.getValue()
            ));
        }
        return resumos;
    }

    private ResumoEntidadeBloco criarResumoEntidade(
        final BlocoPeriodo bloco,
        final int numeroBloco,
        final int totalBlocos,
        final String entidade,
        final Optional<LogExtracaoInfo> logOpt
    ) {
        if (logOpt == null || logOpt.isEmpty()) {
            return new ResumoEntidadeBloco(
                numeroBloco,
                totalBlocos,
                bloco.dataInicio,
                bloco.dataFim,
                entidade,
                "SEM_LOG",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "SEM_LOG",
                "nao gravou log_extracoes nesta janela"
            );
        }

        final LogExtracaoInfo logEntidade = logOpt.get();
        final String mensagem = logEntidade.getMensagem();
        final boolean resumoParadas = ConstantesEntidades.RASTER_VIAGEM_PARADAS.equals(entidade);
        final Integer apiCount = resumoParadas
            ? primeiroNaoNulo(extrairNumeroAposRotulo(mensagem, "API/paradas mapeadas"), logEntidade.getRegistrosExtraidos())
            : primeiroNaoNulo(extrairMetricaKeyValue(mensagem, "api_count"), logEntidade.getRegistrosExtraidos());
        final Integer uniqueCount = resumoParadas
            ? apiCount
            : extrairMetricaKeyValue(mensagem, "unique_count");
        final Integer dbUpserts = resumoParadas
            ? extrairNumeroAposRotulo(mensagem, "DB/paradas operacoes")
            : extrairMetricaKeyValue(mensagem, "db_upserts");
        final Integer dbPersisted = resumoParadas
            ? extrairNumeroAposRotulo(mensagem, "Persistidos")
            : extrairMetricaKeyValue(mensagem, "db_persisted");
        final Integer noopCount = resumoParadas
            ? extrairNumeroAposRotulo(mensagem, "No-op")
            : extrairMetricaKeyValue(mensagem, "noop_count");
        final Integer invalidCount = extrairMetricaKeyValue(mensagem, "invalid_count");
        final String status = logEntidade.getStatusFinal() == null
            ? "DESCONHECIDO"
            : logEntidade.getStatusFinal().name();
        final String reasonCode = primeiroNaoNulo(
            extrairTextoKeyValue(mensagem, "reason_code"),
            resolverReasonCodeResumo(status, apiCount, mensagem)
        );

        return new ResumoEntidadeBloco(
            numeroBloco,
            totalBlocos,
            bloco.dataInicio,
            bloco.dataFim,
            entidade,
            status,
            apiCount,
            uniqueCount,
            dbUpserts,
            dbPersisted,
            noopCount,
            invalidCount,
            logEntidade.getPaginasProcessadas(),
            reasonCode,
            observacaoResumo(status, apiCount, dbUpserts, dbPersisted, noopCount, invalidCount, reasonCode)
        );
    }

    private void imprimirResumoDetalhado(final List<ResumoEntidadeBloco> resumosEntidades) {
        if (resumosEntidades == null || resumosEntidades.isEmpty()) {
            log.console("Detalhe por tabela/bloco: nenhum log de entidade encontrado nesta execucao.");
            return;
        }

        log.console("");
        log.console("DETALHE POR TABELA/BLOCO (API x DB)");
        log.console("Legenda: API=retorno da fonte; unicos=apos deduplicacao; DB ops=INSERT/UPDATE/no-op; persistidos=linhas alteradas.");

        int blocoAtual = -1;
        for (final ResumoEntidadeBloco resumo : resumosEntidades) {
            if (resumo.numeroBloco != blocoAtual) {
                blocoAtual = resumo.numeroBloco;
                log.console(
                    "Bloco {}/{}: {} a {}",
                    resumo.numeroBloco,
                    resumo.totalBlocos,
                    FormatadorData.formatBR(resumo.dataInicio),
                    FormatadorData.formatBR(resumo.dataFim)
                );
            }
            log.console(resumo.formatarLinha());
        }
    }

    private String observacaoResumo(final String status,
                                    final Integer apiCount,
                                    final Integer dbUpserts,
                                    final Integer dbPersisted,
                                    final Integer noopCount,
                                    final Integer invalidCount,
                                    final String reasonCode) {
        if ("SEM_LOG".equals(status)) {
            return "sem log gravado";
        }
        if (!LogExtracaoInfo.StatusExtracao.COMPLETO.name().equals(status)) {
            return "verificar status/motivo";
        }
        if (valorOuZero(apiCount) == 0 && valorOuZero(dbUpserts) == 0) {
            return "sem dados retornados no periodo";
        }
        if (valorOuZero(apiCount) > 0 && valorOuZero(dbPersisted) == 0 && valorOuZero(noopCount) > 0) {
            return "somente no-op idempotente";
        }
        if (valorOuZero(apiCount) > 0 && valorOuZero(dbUpserts) == 0) {
            return "API retornou dados, mas DB nao registrou operacoes";
        }
        if (valorOuZero(invalidCount) > 0) {
            return "concluido com invalidos descartados";
        }
        if (reasonCode != null && !"OK".equalsIgnoreCase(reasonCode) && !"SEM_DADOS_PERIODO".equalsIgnoreCase(reasonCode)) {
            return "conferir motivo";
        }
        return "ok";
    }

    private Integer extrairMetricaKeyValue(final String mensagem, final String chave) {
        if (mensagem == null || chave == null) {
            return null;
        }
        final String marcador = chave + "=";
        final int inicioMarcador = mensagem.indexOf(marcador);
        if (inicioMarcador < 0) {
            return null;
        }
        return lerInteiroEm(mensagem, inicioMarcador + marcador.length());
    }

    private String extrairTextoKeyValue(final String mensagem, final String chave) {
        if (mensagem == null || chave == null) {
            return null;
        }
        final String marcador = chave + "=";
        final int inicioMarcador = mensagem.indexOf(marcador);
        if (inicioMarcador < 0) {
            return null;
        }
        int pos = inicioMarcador + marcador.length();
        final StringBuilder valor = new StringBuilder();
        while (pos < mensagem.length()) {
            final char caractere = mensagem.charAt(pos);
            if (Character.isWhitespace(caractere) || caractere == '|') {
                break;
            }
            valor.append(caractere);
            pos++;
        }
        return valor.length() == 0 ? null : valor.toString();
    }

    private Integer extrairNumeroAposRotulo(final String mensagem, final String rotulo) {
        if (mensagem == null || rotulo == null) {
            return null;
        }
        final int inicioRotulo = mensagem.indexOf(rotulo);
        if (inicioRotulo < 0) {
            return null;
        }
        int pos = inicioRotulo + rotulo.length();
        while (pos < mensagem.length() && (mensagem.charAt(pos) == ':' || Character.isWhitespace(mensagem.charAt(pos)))) {
            pos++;
        }
        return lerInteiroEm(mensagem, pos);
    }

    private Integer lerInteiroEm(final String texto, final int inicio) {
        if (texto == null || inicio < 0 || inicio >= texto.length()) {
            return null;
        }
        int pos = inicio;
        final StringBuilder digitos = new StringBuilder();
        while (pos < texto.length()) {
            final char caractere = texto.charAt(pos);
            if (Character.isDigit(caractere)) {
                digitos.append(caractere);
            } else if (caractere != ',' && caractere != '.') {
                break;
            }
            pos++;
        }
        if (digitos.length() == 0) {
            return null;
        }
        try {
            return Integer.parseInt(digitos.toString());
        } catch (final NumberFormatException ignored) {
            return null;
        }
    }

    private int valorOuZero(final Integer valor) {
        return valor == null ? 0 : valor;
    }

    static String resolverReasonCodeResumo(final String status,
                                           final Integer apiCount,
                                           final String mensagem) {
        if (ConstantesEntidades.STATUS_ERRO_API.equalsIgnoreCase(status)) {
            final String normalizada = mensagem == null ? "" : mensagem.toLowerCase(Locale.ROOT);
            if (normalizada.contains("timeout") || normalizada.contains("interrompida")) {
                return "TIMEOUT";
            }
            return ConstantesEntidades.STATUS_ERRO_API;
        }
        if (apiCount != null && apiCount == 0) {
            return "SEM_DADOS_PERIODO";
        }
        return "OK";
    }

    static String descreverFaturasGraphQL(final String apiEspecifica,
                                          final boolean incluirFaturasGraphQL) {
        if ("dataexport".equalsIgnoreCase(apiEspecifica)
            || ConstantesEntidades.RASTER.equalsIgnoreCase(apiEspecifica)) {
            return "NAO SE APLICA";
        }
        return incluirFaturasGraphQL ? "INCLUIDO" : "DESABILITADO (flag --sem-faturas-graphql)";
    }

    private Integer primeiroNaoNulo(final Integer primeiro, final Integer segundo) {
        return primeiro != null ? primeiro : segundo;
    }

    private String primeiroNaoNulo(final String primeiro, final String segundo) {
        return primeiro != null && !primeiro.isBlank() ? primeiro : segundo;
    }

    private List<String> validarResultadosCriticosDoBloco(
        final BlocoPeriodo bloco,
        final LocalDateTime inicioExecucaoBloco,
        final LocalDateTime fimExecucaoBloco,
        final String apiEspecifica,
        final String entidadeEspecifica,
        final boolean incluirFaturasGraphQL,
        final boolean modoLoopDaemon,
        final Map<String, Optional<LogExtracaoInfo>> logsBloco
    ) {
        final List<String> falhas = new ArrayList<>();
        final Set<String> entidadesObrigatorias = planejadorEscopo.determinarEntidadesObrigatoriasParaVolume(
            apiEspecifica,
            entidadeEspecifica,
            incluirFaturasGraphQL
        );

        if (entidadesObrigatorias.isEmpty()) {
            return falhas;
        }

        final ExtractionLogQueryPort logQueryPort = AplicacaoContexto.extractionLogQueryPort();
        for (final String entidade : entidadesObrigatorias) {
            final Optional<LogExtracaoInfo> logOpt = logsBloco != null && logsBloco.containsKey(entidade)
                ? logsBloco.get(entidade)
                : logQueryPort.buscarUltimoLogPorEntidadeNoIntervaloExecucao(
                    entidade,
                    inicioExecucaoBloco,
                    fimExecucaoBloco
                );

            if (logOpt.isEmpty()) {
                falhas.add(String.format(
                    "%s sem log no bloco %s a %s (possivel nao execucao)",
                    entidade,
                    bloco.dataInicio,
                    bloco.dataFim
                ));
                continue;
            }

            final LogExtracaoInfo logEntidade = logOpt.get();
            if (logEntidade.getStatusFinal() != LogExtracaoInfo.StatusExtracao.COMPLETO) {
                falhas.add(String.format(
                    "%s com status %s no bloco %s a %s",
                    entidade,
                    logEntidade.getStatusFinal(),
                    bloco.dataInicio,
                    bloco.dataFim
                ));
                continue;
            }

            final Integer registrosExtraidos = logEntidade.getRegistrosExtraidos();
            if (registrosExtraidos == null) {
                falhas.add(String.format(
                    "%s sem contagem de registros no bloco %s a %s",
                    entidade,
                    bloco.dataInicio,
                    bloco.dataFim
                ));
                continue;
            }

            if (registrosExtraidos == 0) {
                log.info(
                    "{} com 0 registros no bloco {} a {} (considerado valido quando integridade tambem estiver OK).",
                    entidade,
                    bloco.dataInicio,
                    bloco.dataFim
                );
            }
        }

        final Set<String> entidadesIntegridade = planejadorEscopo.determinarEntidadesEsperadasParaIntegridade(
            apiEspecifica,
            entidadeEspecifica,
            incluirFaturasGraphQL
        );
        if (!entidadesIntegridade.isEmpty()) {
            final IntegridadeEtlPort integridadePort = AplicacaoContexto.integridadeEtlPort();
            final IntegridadeEtlPort.ResultadoIntegridade resultadoIntegridade =
                integridadePort.validarExecucao(inicioExecucaoBloco, fimExecucaoBloco, entidadesIntegridade, modoLoopDaemon);
            if (!resultadoIntegridade.isValido()) {
                falhas.addAll(resultadoIntegridade.getFalhas());
            }
        }

        return falhas;
    }

    private void executarPreBackfillReferencialColetas(
        final LocalDate dataInicio,
        final String apiEspecifica,
        final String entidadeEspecifica,
        final boolean modoLoopDaemon,
        final boolean modoRapido24h
    ) {
        if (modoLoopDaemon) {
            return;
        }
        if (modoRapido24h) {
            log.console("Pre-backfill referencial de coletas: ignorado no modo rapido 24h.");
            return;
        }

        final boolean escopoCompleto =
            (apiEspecifica == null || apiEspecifica.isBlank())
                && (entidadeEspecifica == null || entidadeEspecifica.isBlank());
        if (!escopoCompleto) {
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
            "PRE-BACKFILL REFERENCIAL DE COLETAS (INTERVALO) | periodo={} a {} | dias_retroativos={}",
            FormatadorData.formatBR(backfillInicio),
            FormatadorData.formatBR(backfillFim),
            diasRetroativos
        );
        log.console("=".repeat(60));

        try {
            preBackfillReferencialColetasUseCase.executar(backfillInicio, backfillFim);
            log.info("Pre-backfill referencial de coletas (intervalo) concluido.");
        } catch (final Exception e) {
            log.warn(
                "Pre-backfill referencial de coletas (intervalo) falhou: {}. Fluxo principal seguira normalmente.",
                e.getMessage()
            );
            log.debug("Detalhes da falha no pre-backfill referencial de coletas (intervalo):", e);
        }
    }

    private void executarPosHidratacaoReferencialColetas(
        final LocalDate dataInicio,
        final LocalDate dataFim,
        final String apiEspecifica,
        final String entidadeEspecifica,
        final boolean modoLoopDaemon,
        final boolean houveFalhaNoBloco,
        final boolean modoRapido24h
    ) {
        if (modoLoopDaemon || houveFalhaNoBloco) {
            return;
        }
        if (modoRapido24h) {
            log.console("Pos-hidratacao referencial de coletas: ignorada no modo rapido 24h.");
            return;
        }

        final boolean escopoCompleto =
            (apiEspecifica == null || apiEspecifica.isBlank())
                && (entidadeEspecifica == null || entidadeEspecifica.isBlank());
        if (!escopoCompleto) {
            return;
        }

        final int lookaheadDias = ConfigEtl.obterEtlReferencialColetasLookaheadDias();
        if (lookaheadDias <= 0) {
            return;
        }

        log.console("\n" + "=".repeat(60));
        log.info(
            "POS-HIDRATACAO REFERENCIAL DE COLETAS (INTERVALO) | periodo={} a {} | lookahead_dias={}",
            FormatadorData.formatBR(dataInicio),
            FormatadorData.formatBR(dataFim),
            lookaheadDias
        );
        log.console("=".repeat(60));

        try {
            preBackfillReferencialColetasUseCase.executarPosExtracao(dataInicio, dataFim);
            log.info("Pos-hidratacao referencial de coletas (intervalo) concluida.");
        } catch (final Exception e) {
            log.warn(
                "Pos-hidratacao referencial de coletas (intervalo) falhou: {}. Validacao final seguira normalmente.",
                e.getMessage()
            );
            log.debug("Detalhes da falha na pos-hidratacao referencial de coletas (intervalo):", e);
        }
    }

    private static final class ResumoEntidadeBloco {
        private final int numeroBloco;
        private final int totalBlocos;
        private final LocalDate dataInicio;
        private final LocalDate dataFim;
        private final String entidade;
        private final String status;
        private final Integer apiCount;
        private final Integer uniqueCount;
        private final Integer dbUpserts;
        private final Integer dbPersisted;
        private final Integer noopCount;
        private final Integer invalidCount;
        private final Integer paginasProcessadas;
        private final String reasonCode;
        private final String observacao;

        private ResumoEntidadeBloco(final int numeroBloco,
                                    final int totalBlocos,
                                    final LocalDate dataInicio,
                                    final LocalDate dataFim,
                                    final String entidade,
                                    final String status,
                                    final Integer apiCount,
                                    final Integer uniqueCount,
                                    final Integer dbUpserts,
                                    final Integer dbPersisted,
                                    final Integer noopCount,
                                    final Integer invalidCount,
                                    final Integer paginasProcessadas,
                                    final String reasonCode,
                                    final String observacao) {
            this.numeroBloco = numeroBloco;
            this.totalBlocos = totalBlocos;
            this.dataInicio = dataInicio;
            this.dataFim = dataFim;
            this.entidade = entidade;
            this.status = status;
            this.apiCount = apiCount;
            this.uniqueCount = uniqueCount;
            this.dbUpserts = dbUpserts;
            this.dbPersisted = dbPersisted;
            this.noopCount = noopCount;
            this.invalidCount = invalidCount;
            this.paginasProcessadas = paginasProcessadas;
            this.reasonCode = reasonCode;
            this.observacao = observacao;
        }

        private String formatarLinha() {
            return String.format(
                Locale.ROOT,
                "  %-28s status=%-18s api=%8s unicos=%8s db_ops=%8s persistidos=%8s noop=%8s invalidos=%6s pags=%4s motivo=%-22s %s",
                entidade,
                texto(status),
                numero(apiCount),
                numero(uniqueCount),
                numero(dbUpserts),
                numero(dbPersisted),
                numero(noopCount),
                numero(invalidCount),
                numero(paginasProcessadas),
                texto(reasonCode),
                texto(observacao)
            );
        }

        private static String numero(final Integer valor) {
            return valor == null ? "-" : String.format(Locale.ROOT, "%,d", valor);
        }

        private static String texto(final String valor) {
            return valor == null || valor.isBlank() ? "-" : valor;
        }
    }

    private static final class BlocoPeriodo {
        private final LocalDate dataInicio;
        private final LocalDate dataFim;

        private BlocoPeriodo(final LocalDate dataInicio, final LocalDate dataFim) {
            this.dataInicio = dataInicio;
            this.dataFim = dataFim;
        }
    }
}
