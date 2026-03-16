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
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import br.com.extrator.aplicacao.contexto.AplicacaoContexto;
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
public class ExtracaoPorIntervaloUseCase {
    private static final String EXECUTION_LOCK_RESOURCE = "etl-global-execution";
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
        final LocalDate dataInicio = request.dataInicio();
        final LocalDate dataFim = request.dataFim();
        final String apiEspecifica = request.apiEspecifica();
        final String entidadeEspecifica = request.entidadeEspecifica();
        final boolean incluirFaturasGraphQL = request.incluirFaturasGraphQL();
        final boolean modoLoopDaemon = request.modoLoopDaemon();

        BannerUtil.exibirBannerExtracaoCompleta();

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
            incluirFaturasGraphQL ? "INCLUIDO" : "DESABILITADO (flag --sem-faturas-graphql)"
        );

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

        executarPreBackfillReferencialColetas(dataInicio, apiEspecifica, entidadeEspecifica, modoLoopDaemon);

        final LocalDateTime inicioExecucao = LocalDateTime.now();
        final PipelineOrchestrator orchestrator = AplicacaoContexto.orchestratorFactory().criar();
        int blocosCompletos = 0;
        int blocosFalhados = 0;
        final List<String> blocosFalhadosLista = new ArrayList<>();

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
                blocoComFalha
            );

            final LocalDateTime fimExecucaoBloco = LocalDateTime.now();
            final List<String> falhasDeVolume = validarResultadosCriticosDoBloco(
                bloco,
                inicioExecucaoBloco,
                fimExecucaoBloco,
                apiEspecifica,
                entidadeEspecifica,
                incluirFaturasGraphQL,
                modoLoopDaemon
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
        log.console("Duracao total: {} minutos", duracaoMinutos);
        log.console("=".repeat(60));

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

    private List<String> validarResultadosCriticosDoBloco(
        final BlocoPeriodo bloco,
        final LocalDateTime inicioExecucaoBloco,
        final LocalDateTime fimExecucaoBloco,
        final String apiEspecifica,
        final String entidadeEspecifica,
        final boolean incluirFaturasGraphQL,
        final boolean modoLoopDaemon
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

        final br.com.extrator.aplicacao.portas.ExtractionLogQueryPort logQueryPort =
            AplicacaoContexto.extractionLogQueryPort();
        for (final String entidade : entidadesObrigatorias) {
            final Optional<LogExtracaoInfo> logOpt = logQueryPort.buscarUltimoLogPorEntidadeNoIntervaloExecucao(
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
        final boolean modoLoopDaemon
    ) {
        if (modoLoopDaemon) {
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
        final boolean houveFalhaNoBloco
    ) {
        if (modoLoopDaemon || houveFalhaNoBloco) {
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

    private static final class BlocoPeriodo {
        private final LocalDate dataInicio;
        private final LocalDate dataFim;

        private BlocoPeriodo(final LocalDate dataInicio, final LocalDate dataFim) {
            this.dataInicio = dataInicio;
            this.dataFim = dataFim;
        }
    }
}
