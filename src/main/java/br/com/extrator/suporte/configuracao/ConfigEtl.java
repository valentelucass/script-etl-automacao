package br.com.extrator.suporte.configuracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/suporte/configuracao/ConfigEtl.java
Classe  : ConfigEtl (class)
Pacote  : br.com.extrator.suporte.configuracao
Modulo  : Suporte - Config
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.time.Duration;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.suporte.validacao.ConstantesEntidades;

public final class ConfigEtl {
    private static final Logger logger = LoggerFactory.getLogger(ConfigEtl.class);
    private static final int FRETES_LOOKBACK_MAX_DIAS = 30;
    private static final String FRETES_LOOKBACK_MODO_NORMAL = "normal";
    private static final String FRETES_LOOKBACK_MODO_RECONCILIACAO = "reconciliacao";
    private static final String FRETES_LOOKBACK_MODO_BACKFILL = "backfill";
    private static final String FRETES_LOOKBACK_MODO_INTERVALO = "intervalo";

    private ConfigEtl() {
    }

    public static long obterDelayEntreExtracoes() {
        return ConfigValueParser.parseLong(
            ConfigSource.obterConfiguracao("EXTRACAO_DELAY_MS", "extracao.delay.ms"),
            2000L,
            value -> value > 0L,
            null,
            null,
            null
        );
    }

    public static int obterThreadsProcessamentoFaturas() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_ENRIQUECIMENTO_FATURAS_THREADS", "api.enriquecimento.faturas.threads"),
            5,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static int obterLimiteErrosConsecutivos() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_ENRIQUECIMENTO_ERROS_LIMITE", "api.enriquecimento.erros_consecutivos_limite"),
            10,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static double obterMultiplicadorDelayErros() {
        return ConfigValueParser.parseDouble(
            ConfigSource.obterConfiguracao("API_ENRIQUECIMENTO_DELAY_MULTIPLIER", "api.enriquecimento.delay_multiplier_erros"),
            2.0d,
            value -> value > 1.0d,
            null,
            null,
            null
        );
    }

    public static int obterIntervaloLogProgressoEnriquecimento() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_ENRIQUECIMENTO_INTERVALO_LOG", "api.enriquecimento.intervalo_log_progresso"),
            100,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static int obterHeartbeatSegundos() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("API_ENRIQUECIMENTO_HEARTBEAT", "api.enriquecimento.heartbeat_segundos"),
            10,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static int obterMaxInvalidosToleradosPorEntidade() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("ETL_INVALIDOS_QUANTIDADE_MAX", "etl.invalidos.quantidade.max"),
            500,
            value -> value >= 0,
            null,
            null,
            null
        );
    }

    public static double obterPercentualMaxInvalidosToleradosPorEntidade() {
        return ConfigValueParser.parseDouble(
            ConfigSource.obterConfiguracao("ETL_INVALIDOS_PERCENTUAL_MAX", "etl.invalidos.percentual.max"),
            2.5d,
            value -> value >= 0.0d,
            null,
            null,
            null
        );
    }

    public static CarregadorConfig.EtlIntegridadeModo obterModoIntegridadeEtl() {
        final String valor = ConfigSource.obterConfiguracao("ETL_INTEGRIDADE_MODO", "etl.integridade.modo");
        if (valor == null || valor.isBlank()) {
            return CarregadorConfig.EtlIntegridadeModo.STRICT_INTEGRITY;
        }
        try {
            return CarregadorConfig.EtlIntegridadeModo.valueOf(valor.trim().toUpperCase());
        } catch (final IllegalArgumentException ex) {
            logger.warn("Modo de integridade ETL invalido '{}'. Usando STRICT_INTEGRITY.", valor);
            return CarregadorConfig.EtlIntegridadeModo.STRICT_INTEGRITY;
        }
    }

    public static boolean isModoIntegridadeEstrito() {
        return CarregadorConfig.EtlIntegridadeModo.STRICT_INTEGRITY.equals(obterModoIntegridadeEtl());
    }

    public static int obterMaxOrfaosManifestosTolerados() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "ETL_REFERENCIAL_MANIFESTOS_ORFAOS_QUANTIDADE_MAX",
                "etl.referencial.manifestos.orfaos.quantidade.max"
            ),
            500,
            value -> value >= 0,
            null,
            null,
            null
        );
    }

    public static double obterPercentualMaxOrfaosManifestosTolerados() {
        return ConfigValueParser.parseDouble(
            ConfigSource.obterConfiguracao(
                "ETL_REFERENCIAL_MANIFESTOS_ORFAOS_PERCENTUAL_MAX",
                "etl.referencial.manifestos.orfaos.percentual.max"
            ),
            35.0d,
            value -> value >= 0.0d,
            null,
            null,
            null
        );
    }

    public static int obterEtlReferencialColetasBackfillDias() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "ETL_REFERENCIAL_COLETAS_BACKFILL_DIAS",
                "etl.referencial.coletas.backfill.dias"
            ),
            1,
            value -> value >= 0,
            null,
            null,
            null
        );
    }

    public static int obterEtlReferencialColetasBackfillBufferDias() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "ETL_REFERENCIAL_COLETAS_BACKFILL_BUFFER_DIAS",
                "etl.referencial.coletas.backfill.buffer_dias"
            ),
            7,
            value -> value >= 0,
            null,
            null,
            null
        );
    }

    public static int obterEtlReferencialColetasBackfillMaxExpansaoDias() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "ETL_REFERENCIAL_COLETAS_BACKFILL_MAX_EXPANSAO_DIAS",
                "etl.referencial.coletas.backfill.max_expansao_dias"
            ),
            30,
            value -> value >= 0,
            null,
            null,
            null
        );
    }

    public static int obterEtlReferencialColetasBackfillMaxExpansaoDiasIntervalo() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "ETL_REFERENCIAL_COLETAS_BACKFILL_MAX_EXPANSAO_DIAS_INTERVALO",
                "etl.referencial.coletas.backfill.max_expansao_dias.intervalo"
            ),
            400,
            value -> value >= 0,
            null,
            null,
            null
        );
    }

    public static int obterEtlReferencialColetasLookaheadDias() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "ETL_REFERENCIAL_COLETAS_LOOKAHEAD_DIAS",
                "etl.referencial.coletas.lookahead.dias"
            ),
            1,
            value -> value >= 0,
            null,
            null,
            null
        );
    }

    public static boolean isPruneAusentesFretesAtivo() {
        final String valor = ConfigSource.obterConfiguracao(
            "ETL_FRETES_PRUNE_AUSENTES",
            "etl.fretes.prune.ausentes"
        );
        return valor != null && Boolean.parseBoolean(valor.trim().toLowerCase(Locale.ROOT));
    }

    public static int obterFretesPerformanceLookbackDias() {
        return obterFretesPerformanceLookbackDiasReconciliacao();
    }

    public static int obterFretesPerformanceLookbackDiasEfetivo() {
        return switch (obterFretesPerformanceLookbackModo()) {
            case FRETES_LOOKBACK_MODO_RECONCILIACAO -> obterFretesPerformanceLookbackDiasReconciliacao();
            case FRETES_LOOKBACK_MODO_BACKFILL -> obterFretesPerformanceLookbackDiasBackfill();
            case FRETES_LOOKBACK_MODO_INTERVALO -> obterFretesPerformanceLookbackDiasIntervalo();
            default -> obterFretesPerformanceLookbackDiasNormal();
        };
    }

    public static String obterFretesPerformanceLookbackModo() {
        final String valor = ConfigSource.obterConfiguracao(
            "ETL_FRETES_PERFORMANCE_LOOKBACK_MODO",
            "etl.fretes.performance.lookback.modo"
        );
        if (valor == null || valor.isBlank()) {
            return FRETES_LOOKBACK_MODO_NORMAL;
        }
        final String normalizado = valor.trim().toLowerCase(Locale.ROOT);
        return switch (normalizado) {
            case FRETES_LOOKBACK_MODO_NORMAL,
                FRETES_LOOKBACK_MODO_RECONCILIACAO,
                FRETES_LOOKBACK_MODO_BACKFILL,
                FRETES_LOOKBACK_MODO_INTERVALO -> normalizado;
            default -> {
                logger.warn(
                    "Modo de lookback de fretes invalido '{}'. Usando normal sem lookback implicito.",
                    valor
                );
                yield FRETES_LOOKBACK_MODO_NORMAL;
            }
        };
    }

    public static int obterFretesPerformanceLookbackDiasNormal() {
        return obterFretesLookbackDias(
            ConfigSource.obterConfiguracao(
                "ETL_FRETES_PERFORMANCE_LOOKBACK_NORMAL_DIAS",
                "etl.fretes.performance.lookback.normal.dias"
            ),
            0,
            "etl.fretes.performance.lookback.normal.dias"
        );
    }

    public static int obterFretesPerformanceLookbackDiasReconciliacao() {
        String valor = ConfigSource.obterConfiguracao(
            "ETL_FRETES_PERFORMANCE_LOOKBACK_RECONCILIACAO_DIAS",
            "etl.fretes.performance.lookback.reconciliacao.dias"
        );
        if (valor == null || valor.isBlank()) {
            valor = ConfigSource.obterConfiguracao(
                "ETL_FRETES_PERFORMANCE_LOOKBACK_DIAS",
                "etl.fretes.performance.lookback.dias"
            );
        }
        return obterFretesLookbackDias(
            valor,
            30,
            "etl.fretes.performance.lookback.reconciliacao.dias"
        );
    }

    public static int obterFretesPerformanceLookbackDiasBackfill() {
        return obterFretesLookbackDias(
            ConfigSource.obterConfiguracao(
                "ETL_FRETES_PERFORMANCE_LOOKBACK_BACKFILL_DIAS",
                "etl.fretes.performance.lookback.backfill.dias"
            ),
            0,
            "etl.fretes.performance.lookback.backfill.dias"
        );
    }

    public static int obterFretesPerformanceLookbackDiasIntervalo() {
        return obterFretesLookbackDias(
            ConfigSource.obterConfiguracao(
                "ETL_FRETES_PERFORMANCE_LOOKBACK_INTERVALO_DIAS",
                "etl.fretes.performance.lookback.intervalo.dias"
            ),
            0,
            "etl.fretes.performance.lookback.intervalo.dias"
        );
    }

    private static int obterFretesLookbackDias(final String valor,
                                               final int valorPadrao,
                                               final String chaveLog) {
        return ConfigValueParser.parseInt(
            valor,
            valorPadrao,
            value -> value >= 0 && value <= FRETES_LOOKBACK_MAX_DIAS,
            logger,
            chaveLog,
            "0 a " + FRETES_LOOKBACK_MAX_DIAS + " dias"
        );
    }

    public static boolean isFretePruneGuardrailAtivo() {
        return obterBooleanComFallback(
            "ETL_FRETES_PRUNE_GUARDRAIL_ENABLED",
            "etl.fretes.prune.guardrail.enabled",
            true
        );
    }

    public static int obterFretePruneBaselineMinRegistros() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "ETL_FRETES_PRUNE_GUARDRAIL_BASELINE_MIN_REGISTROS",
                "etl.fretes.prune.guardrail.baseline.min_registros"
            ),
            100,
            value -> value >= 0,
            null,
            null,
            null
        );
    }

    public static double obterFretePruneGuardrailMinRatio() {
        return ConfigValueParser.parseDouble(
            ConfigSource.obterConfiguracao(
                "ETL_FRETES_PRUNE_GUARDRAIL_MIN_RATIO",
                "etl.fretes.prune.guardrail.min_ratio"
            ),
            0.30d,
            value -> value >= 0.0d && value <= 1.0d,
            null,
            null,
            null
        );
    }

    public static int obterFretePruneHistoricoExecucoes() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "ETL_FRETES_PRUNE_GUARDRAIL_HISTORICO_EXECUCOES",
                "etl.fretes.prune.guardrail.historico_execucoes"
            ),
            7,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static int obterFretePruneMinAusenciasConsecutivas() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "ETL_FRETES_PRUNE_MIN_AUSENCIAS_CONSECUTIVAS",
                "etl.fretes.prune.min_ausencias_consecutivas"
            ),
            2,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static int obterTimeoutLockExecucaoMs() {
        return ConfigValueParser.parseInt(
            System.getProperty("etl.execution.lock.timeout.ms") != null
                ? System.getProperty("etl.execution.lock.timeout.ms")
                : ConfigSource.obterConfiguracao(
                    "ETL_EXECUTION_LOCK_TIMEOUT_MS",
                    "etl.execution.lock.timeout.ms"
                ),
            30_000,
            value -> value >= 0,
            null,
            null,
            null
        );
    }

    public static Duration obterTimeoutStepPadrao() {
        return Duration.ofMillis(obterLongComFallback(
            "ETL_PIPELINE_TIMEOUT_STEP_PADRAO_MS",
            "etl.pipeline.timeout.step.padrao.ms",
            900_000L
        ));
    }

    public static Duration obterTimeoutStepGraphQL() {
        return Duration.ofMillis(obterLongComFallback(
            "ETL_PIPELINE_TIMEOUT_STEP_GRAPHQL_MS",
            "etl.pipeline.timeout.step.graphql.ms",
            1_200_000L
        ));
    }

    public static Duration obterTimeoutStepGraphQLCompleto() {
        final long margemMs = obterLongComFallback(
            "ETL_PIPELINE_TIMEOUT_STEP_GRAPHQL_COMPLETO_MARGEM_MS",
            "etl.pipeline.timeout.step.graphql_completo.margem.ms",
            120_000L
        );
        final long totalMs = somarDuracoesComSaturacao(
            obterTimeoutEntidadeGraphQL(ConstantesEntidades.USUARIOS_SISTEMA).toMillis(),
            obterTimeoutEntidadeGraphQL(ConstantesEntidades.COLETAS).toMillis(),
            obterTimeoutEntidadeGraphQL(ConstantesEntidades.FRETES).toMillis(),
            margemMs
        );
        return Duration.ofMillis(totalMs);
    }

    public static Duration obterTimeoutStepDataExport() {
        return Duration.ofMillis(obterLongComFallback(
            "ETL_PIPELINE_TIMEOUT_STEP_DATAEXPORT_MS",
            "etl.pipeline.timeout.step.dataexport.ms",
            3_600_000L
        ));
    }

    public static Duration obterTimeoutStepQuality() {
        return Duration.ofMillis(obterLongComFallback(
            "ETL_PIPELINE_TIMEOUT_STEP_QUALITY_MS",
            "etl.pipeline.timeout.step.quality.ms",
            300_000L
        ));
    }

    public static Duration obterTimeoutStepFaturasGraphQL() {
        return Duration.ofMillis(obterLongComFallback(
            "ETL_PIPELINE_TIMEOUT_STEP_FATURAS_GRAPHQL_MS",
            "etl.pipeline.timeout.step.faturas_graphql.ms",
            7_200_000L
        ));
    }

    public static Duration obterTimeoutEntidadeGraphQLColetasIntervalo() {
        return Duration.ofMillis(obterLongComFallback(
            "ETL_GRAPHQL_TIMEOUT_ENTIDADE_COLETAS_INTERVALO_MS",
            "etl.graphql.timeout.entidade.coletas.intervalo.ms",
            1_800_000L
        ));
    }

    public static Duration obterTimeoutEntidadeGraphQL(final String entidade) {
        final String chave = normalizarChaveEntidade(entidade);
        final long padrao;
        if ("USUARIOS_SISTEMA".equals(chave)) {
            padrao = 1_800_000L;
        } else if ("FRETES".equals(chave)) {
            padrao = 900_000L;
        } else if ("FATURAS_GRAPHQL".equals(chave)) {
            padrao = 7_200_000L;
        } else {
            padrao = 600_000L;
        }
        return Duration.ofMillis(obterLongComFallback(
            "ETL_GRAPHQL_TIMEOUT_ENTIDADE_" + chave + "_MS",
            "etl.graphql.timeout.entidade." + chave.toLowerCase(Locale.ROOT) + ".ms",
            padrao
        ));
    }

    public static Duration obterTimeoutEntidadeGraphQLColetasReferencial() {
        final long padrao = Math.max(
            obterTimeoutEntidadeGraphQL(ConstantesEntidades.COLETAS).toMillis(),
            1_800_000L
        );
        return Duration.ofMillis(obterLongComFallback(
            "ETL_GRAPHQL_TIMEOUT_ENTIDADE_COLETAS_REFERENCIAL_MS",
            "etl.graphql.timeout.entidade.coletas_referencial.ms",
            padrao
        ));
    }

    public static int obterEtlIntervaloColetasMaxConsecutiveFailures() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "ETL_INTERVALO_COLETAS_MAX_CONSECUTIVE_FAILURES",
                "etl.intervalo.coletas.max_consecutive_failures"
            ),
            2,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static Duration obterTimeoutEntidadeDataExport(final String entidade) {
        final String chave = normalizarChaveEntidade(entidade);
        return Duration.ofMillis(obterLongComFallback(
            "ETL_DATAEXPORT_TIMEOUT_ENTIDADE_" + chave + "_MS",
            "etl.dataexport.timeout.entidade." + chave.toLowerCase(Locale.ROOT) + ".ms",
            900_000L
        ));
    }

    public static Duration obterTimeoutCicloDaemon(final boolean incluirFaturasGraphQL) {
        if (incluirFaturasGraphQL) {
            return Duration.ofMillis(obterLongComFallback(
                "ETL_DAEMON_CYCLE_TIMEOUT_COM_FATURAS_MS",
                "etl.daemon.cycle.timeout.com_faturas.ms",
                14_400_000L
            ));
        }
        return Duration.ofMillis(obterLongComFallback(
            "ETL_DAEMON_CYCLE_TIMEOUT_MS",
            "etl.daemon.cycle.timeout.ms",
            3_600_000L
        ));
    }

    public static int obterLoopDaemonMaxConsecutiveAlertCycles() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "LOOP_DAEMON_MAX_CONSECUTIVE_ALERT_CYCLES",
                "loop.daemon.max_consecutive_alert_cycles"
            ),
            3,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static Duration obterRecoveryReplayIdempotencyTtl() {
        return Duration.ofHours(ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "ETL_RECOVERY_REPLAY_IDEMPOTENCY_TTL_HOURS",
                "etl.recovery.replay.idempotency.ttl.hours"
            ),
            12,
            value -> value > 0,
            null,
            null,
            null
        ));
    }

    public static boolean isLateDataAutoReplayAtivo() {
        final String valor = ConfigSource.obterConfiguracao(
            "ETL_LATE_DATA_AUTO_REPLAY_ENABLED",
            "etl.late_data.auto_replay.enabled"
        );
        return valor == null || valor.isBlank() || Boolean.parseBoolean(valor.trim());
    }

    public static int obterLateDataAutoReplayMaxAttempts() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "ETL_LATE_DATA_AUTO_REPLAY_MAX_ATTEMPTS",
                "etl.late_data.auto_replay.max_attempts"
            ),
            1,
            value -> value >= 0 && value <= 3,
            null,
            null,
            null
        );
    }

    public static long obterLateDataAutoReplayDelayMs() {
        return obterLongComFallback(
            "ETL_LATE_DATA_AUTO_REPLAY_DELAY_MS",
            "etl.late_data.auto_replay.delay.ms",
            0L
        );
    }

    public static int obterTimeoutLockReplayMs() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "ETL_RECOVERY_REPLAY_LOCK_TIMEOUT_MS",
                "etl.recovery.replay.lock.timeout.ms"
            ),
            30_000,
            value -> value >= 0,
            null,
            null,
            null
        );
    }

    public static long obterTimeoutParallelGraceMs() {
        return obterLongComFallback(
            "ETL_PIPELINE_TIMEOUT_PARALLEL_GRACE_MS",
            "etl.pipeline.timeout.parallel.grace.ms",
            5_000L
        );
    }

    public static long obterTimeoutShutdownExecutorMs() {
        return obterLongComFallback(
            "ETL_PIPELINE_SHUTDOWN_TIMEOUT_MS",
            "etl.pipeline.shutdown.timeout.ms",
            5_000L
        );
    }

    public static long obterTimeoutThreadLeakGraceMs() {
        return obterLongComFallback(
            "ETL_THREAD_LEAK_GRACE_MS",
            "etl.thread.leak.grace.ms",
            500L
        );
    }

    public static boolean isFalharAoDetectarThreadLeak() {
        return obterBooleanComFallback(
            "ETL_THREAD_LEAK_FAIL_ON_DETECTION",
            "etl.thread.leak.fail_on_detection",
            false
        );
    }

    public static boolean isIsolamentoProcessoAtivo() {
        return obterBooleanComFallback(
            "ETL_PROCESS_ISOLATION_ENABLED",
            "etl.process.isolation.enabled",
            true
        );
    }

    public static long obterTimeoutDestruicaoProcessoIsoladoMs() {
        return obterLongComFallback(
            "ETL_PROCESS_ISOLATION_DESTROY_TIMEOUT_MS",
            "etl.process.isolation.destroy_timeout.ms",
            1_500L
        );
    }

    public static boolean isProcessoFilhoIsolado() {
        return obterBooleanComFallback(
            "ETL_PROCESS_ISOLATED_CHILD",
            "etl.process.isolated.child",
            false
        );
    }

    public static boolean isExecucaoManualStepIsoladoPermitida() {
        return obterBooleanComFallback(
            "ETL_PROCESS_ISOLATED_MANUAL_ALLOW",
            "etl.process.isolated.manual.allow",
            false
        );
    }

    private static long obterLongComFallback(final String envKey,
                                             final String propertyKey,
                                             final long valorPadrao) {
        return ConfigValueParser.parseLong(
            ConfigSource.obterConfiguracao(envKey, propertyKey),
            valorPadrao,
            value -> value > 0L,
            null,
            null,
            null
        );
    }

    private static boolean obterBooleanComFallback(final String envKey,
                                                   final String propertyKey,
                                                   final boolean valorPadrao) {
        final String valor = ConfigSource.obterConfiguracao(envKey, propertyKey);
        if (valor == null || valor.isBlank()) {
            return valorPadrao;
        }
        return Boolean.parseBoolean(valor.trim());
    }

    private static long somarDuracoesComSaturacao(final long... valores) {
        long total = 0L;
        for (final long valor : valores) {
            if (valor <= 0L) {
                continue;
            }
            if (Long.MAX_VALUE - total < valor) {
                return Long.MAX_VALUE;
            }
            total += valor;
        }
        return total;
    }

    private static String normalizarChaveEntidade(final String entidade) {
        final String valor = entidade == null || entidade.isBlank()
            ? ConstantesEntidades.COLETAS
            : entidade.trim();
        return valor
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
    }
}
