package br.com.extrator.aplicacao.validacao;

import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.EntidadeValidacao;
import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.JanelaExecucao;
import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.ResultadoApiChaves;
import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.ResultadoComparacao;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import br.com.extrator.aplicacao.extracao.ExtracaoPorIntervaloRequest;
import br.com.extrator.aplicacao.extracao.ExtracaoPorIntervaloUseCase;
import br.com.extrator.aplicacao.extracao.PreBackfillReferencialColetasUseCase;
import br.com.extrator.suporte.banco.GerenciadorConexao;
import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.mapeamento.MapperUtil;
import br.com.extrator.suporte.tempo.RelogioSistema;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public class ValidacaoEtlExtremaUseCase {
    private static final LoggerConsole log = LoggerConsole.getLogger(ValidacaoEtlExtremaUseCase.class);
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final int SAMPLE_LIMIT = 8;

    private final ValidacaoApiBanco24hDetalhadaMetadataHasher metadataHasher;
    private final ValidacaoApiBanco24hDetalhadaRepository repository;
    private final ValidacaoApiBanco24hDetalhadaComparator comparator;
    private final ValidacaoApiBanco24hDetalhadaApiCollector apiCollector;
    private final ValidacaoEtlExtremaMetadataDiff metadataDiff;

    public ValidacaoEtlExtremaUseCase() {
        this(new ValidacaoApiBanco24hDetalhadaMetadataHasher());
    }

    ValidacaoEtlExtremaUseCase(final ValidacaoApiBanco24hDetalhadaMetadataHasher metadataHasher) {
        this.metadataHasher = metadataHasher;
        this.repository = new ValidacaoApiBanco24hDetalhadaRepository(log, metadataHasher);
        this.comparator = new ValidacaoApiBanco24hDetalhadaComparator(repository);
        this.apiCollector = new ValidacaoApiBanco24hDetalhadaApiCollector(metadataHasher, repository);
        this.metadataDiff = new ValidacaoEtlExtremaMetadataDiff();
    }

    public void executar(final ValidacaoEtlExtremaRequest request) throws Exception {
        final LocalDateTime inicio = RelogioSistema.agora();
        final FinalReport report;

        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            final LocalDate dataReferencia = repository.resolverDataReferenciaLogs(conexao, request.dataReferenciaSistema());
            final LocalDate dataInicio = dataReferencia.minusDays(1);
            final LocalDate dataFim = request.periodoFechado() ? dataReferencia.minusDays(1) : dataReferencia;
            final List<EntitySpec> entidades = detectarEntidades(conexao, request.incluirFaturasGraphQL());
            final List<String> nomesEntidades = entidades.stream().map(EntitySpec::entidade).toList();

            log.console("\n" + "=".repeat(96));
            log.info("BATERIA EXTREMA ETL | API x BANCO x LOGS x PAGINACAO");
            log.info("Data de referencia dos logs: {}", dataReferencia);
            log.info("Periodo validado na API: {} a {}", dataInicio, dataFim);
            log.info("Periodo fechado: {}", request.periodoFechado());
            log.info("Fallback de janela: {}", request.permitirFallbackJanela());
            log.info("Entidades detectadas: {}", String.join(", ", nomesEntidades));
            log.info("Stress API: {} repeticao(oes)", request.repeticoesStress());
            log.info("Idempotencia com rerun do ETL: {}", request.executarIdempotencia());
            log.info("Hidratacao de orfaos: {}", request.executarHidratacaoOrfaos());
            log.console("=".repeat(96));

            final Map<String, ResultadoApiChaves> apiResultados = coletarApi(
                conexao,
                dataReferencia,
                dataInicio,
                dataFim,
                nomesEntidades,
                request.permitirFallbackJanela()
            );
            final Map<String, StressAnalysis> stressAnalyses =
                executarStress(
                    conexao,
                    dataReferencia,
                    dataInicio,
                    dataFim,
                    nomesEntidades,
                    request.repeticoesStress(),
                    request.permitirFallbackJanela(),
                    apiResultados
                );

            final List<EntityDiagnostic> entityDiagnostics = new ArrayList<>();
            final List<String> globalProblems = new ArrayList<>();
            final Set<String> suggestions = new LinkedHashSet<>();

            for (final EntitySpec entity : entidades) {
                final ResultadoApiChaves api = apiResultados.get(entity.entidade());
                final StressAnalysis stress = stressAnalyses.get(entity.entidade());
                final EntityDiagnostic diagnostic = analisarEntidade(
                    conexao,
                    entity,
                    api,
                    stress,
                    dataReferencia,
                    dataInicio,
                    dataFim,
                    request
                );
                entityDiagnostics.add(diagnostic);
                globalProblems.addAll(diagnostic.problems());
                suggestions.addAll(diagnostic.suggestions());
            }

            final OrphanHydrationAnalysis orphanHydration = executarAnaliseOrfaos(
                conexao,
                dataReferencia,
                dataInicio,
                dataFim,
                request.executarHidratacaoOrfaos(),
                request.permitirFallbackJanela()
            );
            globalProblems.addAll(orphanHydration.problems());
            suggestions.addAll(orphanHydration.suggestions());

            final ReferentialIntegrityAnalysis referentialIntegrity = analisarIntegridadeReferencial(
                conexao,
                dataReferencia,
                dataInicio,
                dataFim,
                nomesEntidades,
                request.permitirFallbackJanela()
            );
            globalProblems.addAll(referentialIntegrity.problems());
            suggestions.addAll(referentialIntegrity.suggestions());

            final LogAnalysis logAnalysis = analisarLogs(conexao, dataReferencia);
            globalProblems.addAll(logAnalysis.problems());
            suggestions.addAll(logAnalysis.suggestions());

            final List<PerformanceMetric> performanceMetrics = carregarPerformance(conexao, nomesEntidades, dataReferencia);

            final IdempotencyAnalysis idempotencyAnalysis = request.executarIdempotencia()
                ? executarTesteIdempotencia(
                    dataInicio,
                    dataFim,
                    request.incluirFaturasGraphQL()
                )
                : IdempotencyAnalysis.skipped("Teste desabilitado para evitar escrita nao-confirmada em producao.");
            globalProblems.addAll(idempotencyAnalysis.problems());
            suggestions.addAll(idempotencyAnalysis.suggestions());

            final long failures = entityDiagnostics.stream().filter(d -> d.status().startsWith("FALHA")).count()
                + referentialIntegrity.failureCount()
                + logAnalysis.failureCount()
                + idempotencyAnalysis.failureCount()
                + orphanHydration.failureCount();

            report = new FinalReport(
                inicio,
                RelogioSistema.agora(),
                dataReferencia,
                dataInicio,
                dataFim,
                entityDiagnostics,
                referentialIntegrity,
                logAnalysis,
                performanceMetrics,
                idempotencyAnalysis,
                orphanHydration,
                new ArrayList<>(new LinkedHashSet<>(globalProblems)),
                new ArrayList<>(suggestions),
                failures
            );
        }

        final ReportFiles files = persistirReport(report);
        imprimirResumo(report, files);

        if (report.failureCount() > 0) {
            throw new RuntimeException(
                "Bateria extrema do ETL reprovada: "
                    + report.failureCount()
                    + " grupo(s) de falha. Consulte "
                    + files.markdown().toAbsolutePath()
            );
        }
    }

    private Map<String, ResultadoApiChaves> coletarApi(
        final Connection conexao,
        final LocalDate dataReferencia,
        final LocalDate dataInicio,
        final LocalDate dataFim,
        final List<String> entidades,
        final boolean permitirFallbackJanela
    ) throws Exception {
        final Map<String, ResultadoApiChaves> resultados = new LinkedHashMap<>();
        for (final EntidadeValidacao entidade : apiCollector.criarEntidades(
            conexao,
            dataReferencia,
            dataInicio,
            dataFim,
            entidades,
            permitirFallbackJanela
        )) {
            resultados.put(entidade.entidade(), entidade.fornecedor().get());
        }
        return resultados;
    }

    private Map<String, StressAnalysis> executarStress(
        final Connection conexao,
        final LocalDate dataReferencia,
        final LocalDate dataInicio,
        final LocalDate dataFim,
        final List<String> entidades,
        final int repeticoes,
        final boolean permitirFallbackJanela,
        final Map<String, ResultadoApiChaves> baselineResultados
    ) throws Exception {
        final Map<String, StressAnalysis> analyses = new LinkedHashMap<>();
        final Map<String, List<Integer>> totaisPorEntidade = new LinkedHashMap<>();
        final Map<String, List<Integer>> paginasPorEntidade = new LinkedHashMap<>();
        final Map<String, List<String>> motivosPorEntidade = new LinkedHashMap<>();
        for (final String entidade : entidades) {
            final ResultadoApiChaves baseline = baselineResultados.get(entidade);
            if (baseline == null) {
                continue;
            }
            totaisPorEntidade.computeIfAbsent(entidade, ignored -> new ArrayList<>()).add(baseline.apiUnico());
            paginasPorEntidade.computeIfAbsent(entidade, ignored -> new ArrayList<>()).add(baseline.paginasProcessadas());
            motivosPorEntidade.computeIfAbsent(entidade, ignored -> new ArrayList<>()).add(baseline.motivoInterrupcao());
        }

        final List<String> entidadesStress = entidades.stream()
            .filter(ValidacaoEtlExtremaUseCase::deveExecutarStressApi)
            .toList();

        for (int i = 0; i < repeticoes; i++) {
            if (entidadesStress.isEmpty()) {
                break;
            }
            final Map<String, ResultadoApiChaves> rodada = coletarApi(
                conexao,
                dataReferencia,
                dataInicio,
                dataFim,
                entidadesStress,
                permitirFallbackJanela
            );
            for (final Map.Entry<String, ResultadoApiChaves> entry : rodada.entrySet()) {
                totaisPorEntidade.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>()).add(entry.getValue().apiUnico());
                paginasPorEntidade.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                    .add(entry.getValue().paginasProcessadas());
                motivosPorEntidade.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>())
                    .add(entry.getValue().motivoInterrupcao());
            }
        }

        for (final String entidade : entidades) {
            final List<Integer> totais = totaisPorEntidade.getOrDefault(entidade, List.of());
            final List<Integer> paginas = paginasPorEntidade.getOrDefault(entidade, List.of());
            final List<String> motivos = motivosPorEntidade.getOrDefault(entidade, List.of());
            final boolean estavel = new LinkedHashSet<>(totais).size() <= 1
                && motivos.stream().filter(m -> m != null && !m.isBlank()).count() == 0;
            final String detalhe = "totais=" + totais + " | paginas=" + paginas + " | motivos=" + motivos;
            analyses.put(entidade, new StressAnalysis(estavel ? "OK" : "FALHA", detalhe));
        }

        return analyses;
    }

    static boolean deveExecutarStressApi(final String entidade) {
        return !ConstantesEntidades.USUARIOS_SISTEMA.equals(entidade)
            && !ConstantesEntidades.FATURAS_GRAPHQL.equals(entidade);
    }

    static boolean deveExecutarReplayIdempotencia(final String entidade) {
        return !ConstantesEntidades.USUARIOS_SISTEMA.equals(entidade);
    }

    private EntityDiagnostic analisarEntidade(
        final Connection conexao,
        final EntitySpec entity,
        final ResultadoApiChaves api,
        final StressAnalysis stress,
        final LocalDate dataReferencia,
        final LocalDate dataInicio,
        final LocalDate dataFim,
        final ValidacaoEtlExtremaRequest request
    ) throws SQLException {
        final List<String> problems = new ArrayList<>();
        final List<String> suggestions = new ArrayList<>();

        if (api == null) {
            problems.add("Entidade sem retorno da coleta na API.");
            return new EntityDiagnostic(
                entity.entidade(),
                "FALHA_API",
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                "Sem dados retornados pela coleta da API.",
                List.of(),
                List.of(),
                List.of(),
                stress,
                problems,
                suggestions
            );
        }

        final Optional<JanelaExecucao> janelaOpt = repository.buscarUltimaJanelaCompletaDoDia(
            conexao,
            entity.entidade(),
            dataReferencia,
            dataInicio,
            dataFim,
            request.permitirFallbackJanela()
        );
        if (janelaOpt.isEmpty()) {
            problems.add("Sem janela COMPLETA compatível em log_extracoes.");
            suggestions.add("Executar ETL novamente e validar se log_extracoes grava mensagem com o periodo correto.");
            return new EntityDiagnostic(
                entity.entidade(),
                "FALHA_JANELA",
                api.apiUnico(),
                0,
                api.apiUnico(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                api.paginasProcessadas(),
                "API sem correspondente de janela COMPLETA no banco.",
                List.of(),
                List.of(),
                List.of(),
                stress,
                problems,
                suggestions
            );
        }

        final JanelaExecucao janela = janelaOpt.get();
        final Set<String> dbKeys = repository.carregarChavesBancoNaJanela(conexao, entity.entidade(), janela);
        final Map<String, String> dbMetadata = repository.carregarMetadataBrutaBancoNaJanela(conexao, entity.entidade(), janela);
        final ResultadoComparacao comparacaoEstavel = comparator.compararEntidade(
            conexao,
            entity.entidade(),
            api,
            dataReferencia,
            dataInicio,
            dataFim,
            request.periodoFechado(),
            request.permitirFallbackJanela()
        );
        final Set<String> missing = new TreeSet<>(api.chaves());
        missing.removeAll(dbKeys);
        final Set<String> extra = new TreeSet<>(dbKeys);
        extra.removeAll(api.chaves());
        final Set<String> extraEfetivo = new TreeSet<>(extra);
        if (ConstantesEntidades.FATURAS_GRAPHQL.equals(entity.entidade())
            && api.chavesToleradasNoBanco() != null
            && !api.chavesToleradasNoBanco().isEmpty()) {
            extraEfetivo.removeIf(api.chavesToleradasNoBanco()::contains);
        }

        int divergentRecords = 0;
        int divergentFields = 0;
        int unexpectedNulls = 0;
        int truncatedFields = 0;
        int timezoneDrifts = 0;
        final Set<String> apiOnlyFields = new LinkedHashSet<>();
        final Set<String> dbPaths = new LinkedHashSet<>();
        final List<FieldDifferenceSample> fieldSamples = new ArrayList<>();

        final Set<String> common = new TreeSet<>(api.chaves());
        common.retainAll(dbKeys);
        for (final String chave : common) {
            final ValidacaoEtlExtremaMetadataDiff.MetadataDiff diff = metadataDiff.comparar(
                api.metadataPorChave().get(chave),
                dbMetadata.get(chave)
            );
            if (diff.divergentFields() > 0 || !diff.apiOnlyFields().isEmpty()) {
                divergentRecords++;
            }
            divergentFields += diff.divergentFields();
            unexpectedNulls += diff.unexpectedNulls();
            truncatedFields += diff.truncatedFields();
            timezoneDrifts += diff.timezoneDrifts();
            apiOnlyFields.addAll(diff.apiOnlyFields());
            dbPaths.addAll(diff.dbPaths());
            for (final ValidacaoEtlExtremaMetadataDiff.FieldSample sample : diff.samples()) {
                if (fieldSamples.size() >= SAMPLE_LIMIT) {
                    break;
                }
                fieldSamples.add(new FieldDifferenceSample(chave, sample.path(), sample.apiValue(), sample.dbValue()));
            }
        }

        final Set<String> missingStructureFields = new TreeSet<>(api.caminhosEstruturais());
        missingStructureFields.removeAll(dbPaths);

        final long duplicateIds = contarDuplicados(conexao, entity, janela, false);
        final long duplicateSequenceCodes = entity.sequenceColumn() == null
            ? 0
            : contarDuplicados(conexao, entity, janela, true);

        final LogSnapshot logSnapshot = buscarUltimoLog(conexao, entity.entidade(), dataReferencia);
        final boolean volumeBaixoEsperado = api.apiUnico() <= 5
            && dataReferencia.getDayOfWeek() == DayOfWeek.SUNDAY
            && comparacaoEstavel.faltantes() == 0
            && comparacaoEstavel.excedentes() == 0
            && comparacaoEstavel.divergenciasDados() == 0;
        final boolean divergenciaConteudoTolerada = comparator.somenteDivergenciaDadosTolerada(comparacaoEstavel);
        final boolean coletasMarginaisToleradas = tolerarColetasMarginais(
            entity.entidade(),
            comparacaoEstavel,
            stress,
            extraEfetivo,
            fieldSamples
        );

        if (!api.extracaoCompleta()) {
            problems.add("Paginacao/API interrompida: " + api.motivoInterrupcao());
            suggestions.add("Revisar paginador e limites da API para " + entity.entidade() + ".");
        }
        if (!janela.alinhadaAoPeriodo()) {
            problems.add("Janela de comparacao caiu em fallback sem filtro de periodo.");
            suggestions.add("Padronizar mensagem de log_extracoes com o periodo exato da consulta.");
        }
        if (comparacaoEstavel.faltantes() > 0) {
            problems.add("Registros presentes na API e ausentes no banco: " + comparacaoEstavel.faltantes());
            suggestions.add("Executar replay/backfill da entidade " + entity.entidade() + " na janela auditada.");
        }
        if (comparacaoEstavel.excedentes() > 0 && !coletasMarginaisToleradas) {
            problems.add("Registros excedentes no banco: " + comparacaoEstavel.excedentes());
            suggestions.add("Validar MERGE/chave natural da entidade " + entity.entidade() + ".");
        }
        if (duplicateIds > 0) {
            problems.add("Duplicidade por chave unica: " + duplicateIds);
            suggestions.add("Corrigir deduplicacao e constraint/merge da entidade " + entity.entidade() + ".");
        }
        if (duplicateSequenceCodes > 0) {
            problems.add("Duplicidade por sequence_code: " + duplicateSequenceCodes);
        }
        if (divergentFields > 0
            && !comparacaoEstavel.ok()
            && !divergenciaConteudoTolerada
            && !coletasMarginaisToleradas) {
            problems.add("Divergencias campo a campo: " + divergentFields);
            suggestions.add("Revisar mapper/persistencia dos campos divergentes em " + entity.entidade() + ".");
        }
        if (!missingStructureFields.isEmpty()
            && !comparacaoEstavel.ok()
            && !divergenciaConteudoTolerada
            && !coletasMarginaisToleradas) {
            problems.add("Campos presentes na API e nao persistidos: " + missingStructureFields.size());
            suggestions.add("Mapear e persistir campos faltantes da entidade " + entity.entidade() + ".");
        }
        if (truncatedFields > 0
            && !comparacaoEstavel.ok()
            && !divergenciaConteudoTolerada
            && !coletasMarginaisToleradas) {
            problems.add("Possivel truncamento de valores: " + truncatedFields);
            suggestions.add("Revisar tamanhos NVARCHAR/VARCHAR e mapeamento da entidade " + entity.entidade() + ".");
        }
        if (timezoneDrifts > 0
            && !comparacaoEstavel.ok()
            && !divergenciaConteudoTolerada
            && !coletasMarginaisToleradas) {
            problems.add("Diferencas de timezone detectadas: " + timezoneDrifts);
            suggestions.add("Normalizar timezone em UTC ou America/Sao_Paulo antes da persistencia.");
        }
        if (unexpectedNulls > 0
            && !comparacaoEstavel.ok()
            && !divergenciaConteudoTolerada
            && !coletasMarginaisToleradas) {
            problems.add("Campos nulos inesperados no banco: " + unexpectedNulls);
        }
        if (stress != null && !"OK".equals(stress.status()) && !coletasMarginaisToleradas) {
            problems.add("Instabilidade no teste de stress: " + stress.detail());
            suggestions.add("Investigar intermitencia de API/paginacao para " + entity.entidade() + ".");
        }

        final String status = problems.isEmpty()
            ? determinarStatusDiagnostico(
                volumeBaixoEsperado,
                comparacaoEstavel,
                divergentFields,
                stress,
                coletasMarginaisToleradas
            )
            : "FALHA";
        final String paginationDetail = "api_bruto=" + api.apiBruto()
            + " | api_unico=" + api.apiUnico()
            + " | db_total=" + dbKeys.size()
            + " | paginas_api=" + api.paginasProcessadas()
            + " | api_completa=" + api.extracaoCompleta()
            + " | motivo_interrupcao=" + (api.motivoInterrupcao() == null ? "nenhum" : api.motivoInterrupcao())
            + " | log_paginas=" + (logSnapshot == null ? "n/a" : logSnapshot.paginasProcessadas())
            + " | janela_log=" + janela.inicio() + " .. " + janela.fim();

        return new EntityDiagnostic(
            entity.entidade(),
            status,
            api.apiUnico(),
            dbKeys.size(),
            missing.size(),
            extra.size(),
            divergentFields,
            divergentRecords,
            (int) duplicateIds,
            (int) duplicateSequenceCodes,
            unexpectedNulls,
            truncatedFields,
            timezoneDrifts,
            missingStructureFields.size(),
            api.paginasProcessadas(),
            paginationDetail,
            amostra(missing),
            amostra(extraEfetivo),
            fieldSamples,
            stress,
            problems,
            suggestions
        );
    }

    private ReferentialIntegrityAnalysis analisarIntegridadeReferencial(
        final Connection conexao,
        final LocalDate dataReferencia,
        final LocalDate dataInicio,
        final LocalDate dataFim,
        final List<String> entidades,
        final boolean permitirFallbackJanela
    ) throws SQLException {
        final List<String> problems = new ArrayList<>();
        final List<String> suggestions = new ArrayList<>();

        if (entidades.contains(ConstantesEntidades.MANIFESTOS) && entidades.contains(ConstantesEntidades.COLETAS)) {
            final Optional<JanelaExecucao> janelaManifestosOpt = buscarJanelaEntidade(
                conexao,
                ConstantesEntidades.MANIFESTOS,
                dataReferencia,
                dataInicio,
                dataFim,
                permitirFallbackJanela
            );
            if (janelaManifestosOpt.isEmpty()) {
                problems.add("Sem janela COMPLETA para avaliar integridade referencial de manifestos.");
                suggestions.add("Garantir log_extracoes completo e mensagem de periodo para manifestos.");
            } else {
                final JanelaExecucao janelaManifestos = janelaManifestosOpt.get();
                final String sql = """
                    SELECT COUNT(*)
                    FROM dbo.manifestos m
                    WHERE m.data_extracao >= ? AND m.data_extracao <= ?
                      AND m.pick_sequence_code IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1 FROM dbo.coletas c WHERE c.sequence_code = m.pick_sequence_code
                      )
                    """;
                final int orfaos = executarCount(conexao, sql, janelaManifestos.inicio(), janelaManifestos.fim());
                if (orfaos > 0) {
                    problems.add("Manifestos sem coleta vinculada: " + orfaos);
                    suggestions.add("Executar backfill referencial de coletas e revisar filtros requestDate/serviceDate.");
                }
            }
        }

        if (entidades.contains(ConstantesEntidades.FRETES) && entidades.contains(ConstantesEntidades.FATURAS_GRAPHQL)) {
            final Optional<JanelaExecucao> janelaFretesOpt = buscarJanelaEntidade(
                conexao,
                ConstantesEntidades.FRETES,
                dataReferencia,
                dataInicio,
                dataFim,
                permitirFallbackJanela
            );
            if (janelaFretesOpt.isEmpty()) {
                problems.add("Sem janela COMPLETA para avaliar integridade referencial de fretes.");
                suggestions.add("Garantir log_extracoes completo e mensagem de periodo para fretes.");
            } else {
                final JanelaExecucao janelaFretes = janelaFretesOpt.get();
                final String sql = """
                    SELECT COUNT(*)
                    FROM dbo.fretes f
                    WHERE f.data_extracao >= ? AND f.data_extracao <= ?
                      AND f.accounting_credit_id IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1 FROM dbo.faturas_graphql fg WHERE fg.id = f.accounting_credit_id
                      )
                    """;
                final int orfaos = executarCount(conexao, sql, janelaFretes.inicio(), janelaFretes.fim());
                if (orfaos > 0) {
                    problems.add("Fretes sem fatura GraphQL vinculada: " + orfaos);
                    suggestions.add("Revisar enriquecimento/accounting_credit_id de fretes.");
                }
            }
        }

        return new ReferentialIntegrityAnalysis(problems, suggestions, problems.size());
    }

    private boolean tolerarColetasMarginais(
        final String entidade,
        final ResultadoComparacao comparacaoEstavel,
        final StressAnalysis stress,
        final Set<String> extraEfetivo,
        final List<FieldDifferenceSample> fieldSamples
    ) {
        if (!ConstantesEntidades.COLETAS.equals(entidade)
            || comparacaoEstavel == null
            || stress == null
            || "OK".equals(stress.status())
            || !comparacaoEstavel.apiCompleta()
            || comparacaoEstavel.faltantes() != 0
            || comparacaoEstavel.excedentes() != 1
            || extraEfetivo.size() != 1
            || fieldSamples.isEmpty()) {
            return false;
        }
        final Set<String> camposVolateis = Set.of(
            "invoicesValue",
            "invoicesVolumes",
            "invoicesWeight",
            "taxedWeight"
        );
        return fieldSamples.stream().allMatch(sample -> camposVolateis.contains(sample.path()));
    }

    private String determinarStatusDiagnostico(
        final boolean volumeBaixoEsperado,
        final ResultadoComparacao comparacaoEstavel,
        final int divergentFields,
        final StressAnalysis stress,
        final boolean coletasMarginaisToleradas
    ) {
        if (volumeBaixoEsperado) {
            return "OK_VOLUMETRIA_BAIXA_CONFIRMADA";
        }
        if (coletasMarginaisToleradas) {
            return "OK_DADOS_DINAMICOS";
        }
        if (comparacaoEstavel != null && comparator.somenteDivergenciaDadosTolerada(comparacaoEstavel)) {
            return "OK_DADOS_DINAMICOS";
        }
        if (stress != null && !"OK".equals(stress.status())) {
            return "OK_DIAGNOSTICO";
        }
        if (comparacaoEstavel != null && comparacaoEstavel.ok() && divergentFields > 0) {
            return "OK";
        }
        return "OK";
    }

    private LogAnalysis analisarLogs(final Connection conexao, final LocalDate dataReferencia) throws SQLException, IOException {
        final List<String> problems = new ArrayList<>();
        final List<String> suggestions = new ArrayList<>();
        final List<String> evidences = new ArrayList<>();
        boolean evidenciaOperacionalRelevante = false;

        final String sql = """
            SELECT entidade, status_final, paginas_processadas, mensagem
            FROM dbo.log_extracoes
            WHERE CAST(timestamp_inicio AS DATE) IN (?, ?)
            ORDER BY timestamp_fim DESC
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setDate(1, java.sql.Date.valueOf(dataReferencia));
            stmt.setDate(2, java.sql.Date.valueOf(dataReferencia.minusDays(1)));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final String mensagem = rs.getString("mensagem");
                    final String texto = (mensagem == null ? "" : mensagem).toLowerCase(Locale.ROOT);
                    if (!"COMPLETO".equalsIgnoreCase(rs.getString("status_final"))) {
                        problems.add("Log com status nao completo: " + rs.getString("entidade"));
                    }
                    if (texto.contains("retry") || texto.contains("timeout")) {
                        evidences.add(rs.getString("entidade") + ": " + resumir(mensagem));
                        evidenciaOperacionalRelevante = true;
                    }
                    if (texto.contains("loop") || texto.contains("pagina vazia")) {
                        problems.add("Sinal de falha de paginacao em log_extracoes: " + rs.getString("entidade"));
                    }
                    if (texto.contains("deduplic") || texto.contains("descart")) {
                        evidences.add(rs.getString("entidade") + ": " + resumir(mensagem));
                    }
                }
            }
        }

        final Path logDir = Path.of("logs");
        if (Files.isDirectory(logDir)) {
            try (var stream = Files.list(logDir)) {
                final List<Path> arquivos = stream
                    .filter(path -> {
                        final String nome = path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return nome.endsWith(".log")
                            && nome.startsWith("extracao_dados_");
                    })
                    .sorted(Comparator.comparingLong(this::lastModifiedSafe).reversed())
                    .limit(8)
                    .toList();
                for (final Path arquivo : arquivos) {
                    for (final String line : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                        final String normalized = line.toLowerCase(Locale.ROOT);
                        if (normalized.contains("org.junit.jupiter")) {
                            continue;
                        }
                        if (normalized.contains("retry")
                            || normalized.contains("timeout")
                            || normalized.contains("loop_detectado")
                            || normalized.contains("pagina vazia")
                            || normalized.contains("deduplic")
                            || normalized.contains("descart")) {
                            evidences.add(arquivo.getFileName() + ": " + resumir(line));
                            if (normalized.contains("retry") || normalized.contains("timeout")) {
                                evidenciaOperacionalRelevante = true;
                            }
                            if (evidences.size() >= 20) {
                                break;
                            }
                        }
                    }
                    if (evidences.size() >= 20) {
                        break;
                    }
                }
            }
        }

        if (evidenciaOperacionalRelevante && problems.isEmpty()) {
            suggestions.add("Logs recentes contem retries/timeouts diagnosticos. Revisar antes de ampliar a janela.");
        }

        return new LogAnalysis(problems, suggestions, evidences, problems.size());
    }

    private List<PerformanceMetric> carregarPerformance(
        final Connection conexao,
        final List<String> entidades,
        final LocalDate dataReferencia
    ) throws SQLException {
        final List<PerformanceMetric> metrics = new ArrayList<>();
        final String sql = """
            SELECT TOP 1
                   DATEDIFF_BIG(MILLISECOND, timestamp_inicio, timestamp_fim) AS duracao_ms,
                   paginas_processadas,
                   status_final
            FROM dbo.log_extracoes
            WHERE entidade = ?
              AND CAST(timestamp_inicio AS DATE) IN (?, ?)
            ORDER BY timestamp_fim DESC
            """;
        for (final String entidade : entidades) {
            try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
                stmt.setString(1, entidade);
                stmt.setDate(2, java.sql.Date.valueOf(dataReferencia));
                stmt.setDate(3, java.sql.Date.valueOf(dataReferencia.minusDays(1)));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        metrics.add(new PerformanceMetric(
                            entidade,
                            rs.getLong("duracao_ms"),
                            rs.getInt("paginas_processadas"),
                            rs.getString("status_final")
                        ));
                    }
                }
            }
        }
        return metrics;
    }

    private IdempotencyAnalysis executarTesteIdempotencia(
        final LocalDate dataInicio,
        final LocalDate dataFim,
        final boolean incluirFaturasGraphQL
    ) {
        final List<String> problems = new ArrayList<>();
        final List<String> suggestions = new ArrayList<>();
        try (Connection beforeConn = GerenciadorConexao.obterConexao()) {
            final List<EntitySpec> entidades = detectarEntidades(beforeConn, incluirFaturasGraphQL).stream()
                .filter(entity -> deveExecutarReplayIdempotencia(entity.entidade()))
                .toList();
            final String baseline = fingerprintBanco(beforeConn, entidades);
            executarReplayIdempotentePorEntidade(dataInicio, dataFim, incluirFaturasGraphQL, entidades);
            final String afterFirst;
            try (Connection middleConn = GerenciadorConexao.obterConexao()) {
                afterFirst = fingerprintBanco(middleConn, entidades);
            }
            executarReplayIdempotentePorEntidade(dataInicio, dataFim, incluirFaturasGraphQL, entidades);
            final String afterSecond;
            try (Connection finalConn = GerenciadorConexao.obterConexao()) {
                afterSecond = fingerprintBanco(finalConn, entidades);
            }
            if (!afterFirst.equals(afterSecond)) {
                problems.add("Resultado final difere entre a primeira e a segunda execucao consecutiva do ETL.");
                suggestions.add("Investigar UPSERT/idempotencia por chave natural e side-effects de enriquecimento.");
                return new IdempotencyAnalysis("FALHA", baseline, afterFirst, afterSecond, problems, suggestions, 1);
            }
            return new IdempotencyAnalysis("OK", baseline, afterFirst, afterSecond, problems, suggestions, 0);
        } catch (Exception e) {
            problems.add("Falha ao executar teste de idempotencia: " + e.getMessage());
            suggestions.add("Executar teste em janela controlada antes de rodar em producao.");
            return new IdempotencyAnalysis("FALHA", null, null, null, problems, suggestions, 1);
        }
    }

    private void executarReplayIdempotentePorEntidade(
        final LocalDate dataInicio,
        final LocalDate dataFim,
        final boolean incluirFaturasGraphQL,
        final List<EntitySpec> entidades
    ) throws Exception {
        final ExtracaoPorIntervaloUseCase useCase = new ExtracaoPorIntervaloUseCase();
        for (final EntitySpec entidade : entidades) {
            final boolean incluirFaturasNoRequest =
                incluirFaturasGraphQL || ConstantesEntidades.FATURAS_GRAPHQL.equals(entidade.entidade());
            final ExtracaoPorIntervaloRequest request = new ExtracaoPorIntervaloRequest(
                dataInicio,
                dataFim,
                resolverApiParaReplayIdempotente(entidade.entidade()),
                entidade.entidade(),
                incluirFaturasNoRequest,
                false
            );
            useCase.executar(request);
        }
    }

    private String resolverApiParaReplayIdempotente(final String entidade) {
        return switch (entidade) {
            case ConstantesEntidades.COLETAS,
                 ConstantesEntidades.FRETES,
                 ConstantesEntidades.FATURAS_GRAPHQL -> "graphql";
            case ConstantesEntidades.MANIFESTOS,
                 ConstantesEntidades.COTACOES,
                 ConstantesEntidades.LOCALIZACAO_CARGAS,
                 ConstantesEntidades.CONTAS_A_PAGAR,
                 ConstantesEntidades.FATURAS_POR_CLIENTE -> "dataexport";
            default -> throw new IllegalArgumentException("Entidade sem API de replay idempotente mapeada: " + entidade);
        };
    }

    private OrphanHydrationAnalysis executarAnaliseOrfaos(
        final Connection conexao,
        final LocalDate dataReferencia,
        final LocalDate dataInicio,
        final LocalDate dataFim,
        final boolean executarHidratacao,
        final boolean permitirFallbackJanela
    ) throws SQLException {
        final Optional<JanelaExecucao> janelaManifestosOpt = buscarJanelaEntidade(
            conexao,
            ConstantesEntidades.MANIFESTOS,
            dataReferencia,
            dataInicio,
            dataFim,
            permitirFallbackJanela
        );
        if (janelaManifestosOpt.isEmpty()) {
            return new OrphanHydrationAnalysis(
                "FALHA",
                0,
                0,
                List.of("Sem janela COMPLETA para medir manifestos orfaos na bateria extrema."),
                List.of("Executar o ETL novamente e garantir log_extracoes completo para manifestos."),
                1
            );
        }

        final JanelaExecucao janelaManifestos = janelaManifestosOpt.get();
        final int antes = contarManifestosOrfaos(conexao, janelaManifestos);
        if (!executarHidratacao) {
            final List<String> problems = antes > 0
                ? List.of("Manifestos orfaos detectados e hidratacao nao executada: " + antes)
                : List.of();
            final List<String> suggestions = antes > 0
                ? List.of("Rodar novamente a bateria extrema com hidratacao de orfaos habilitada.")
                : List.of();
            return new OrphanHydrationAnalysis(
                antes > 0 ? "ALERTA" : "OK",
                antes,
                antes,
                problems,
                suggestions,
                antes > 0 ? 1 : 0
            );
        }

        try {
            new PreBackfillReferencialColetasUseCase().executarPosExtracao(dataInicio, dataFim);
            try (Connection posConn = GerenciadorConexao.obterConexao()) {
                final int depois = contarManifestosOrfaos(posConn, janelaManifestos);
                final List<String> problems = depois > 0
                    ? List.of("Manifestos orfaos remanescentes apos hidratacao: " + depois)
                    : List.of();
                final List<String> suggestions = depois > 0
                    ? List.of("Aumentar janela do backfill de coletas e revisar filtros GraphQL.")
                    : List.of();
                return new OrphanHydrationAnalysis(
                    depois == 0 ? "OK" : "FALHA",
                    antes,
                    depois,
                    problems,
                    suggestions,
                    depois == 0 ? 0 : 1
                );
            }
        } catch (Exception e) {
            return new OrphanHydrationAnalysis(
                "FALHA",
                antes,
                antes,
                List.of("Falha ao executar hidratacao de orfaos: " + e.getMessage()),
                List.of("Executar o backfill de coletas manualmente em janela maior."),
                1
            );
        }
    }

    private int contarManifestosOrfaos(final Connection conexao, final JanelaExecucao janela) throws SQLException {
        final String sql = """
            SELECT COUNT(*)
            FROM dbo.manifestos m
            WHERE m.data_extracao >= ? AND m.data_extracao <= ?
              AND m.pick_sequence_code IS NOT NULL
              AND NOT EXISTS (
                  SELECT 1 FROM dbo.coletas c WHERE c.sequence_code = m.pick_sequence_code
              )
            """;
        return executarCount(conexao, sql, janela.inicio(), janela.fim());
    }

    private Optional<JanelaExecucao> buscarJanelaEntidade(
        final Connection conexao,
        final String entidade,
        final LocalDate dataReferencia,
        final LocalDate dataInicio,
        final LocalDate dataFim,
        final boolean permitirFallbackJanela
    ) throws SQLException {
        return repository.buscarUltimaJanelaCompletaDoDia(
            conexao,
            entidade,
            dataReferencia,
            dataInicio,
            dataFim,
            permitirFallbackJanela
        );
    }

    private long contarDuplicados(
        final Connection conexao,
        final EntitySpec entity,
        final JanelaExecucao janela,
        final boolean sequenceMode
    ) throws SQLException {
        final List<String> columns = sequenceMode
            ? entity.sequenceColumn() == null ? List.of() : List.of(entity.sequenceColumn())
            : entity.uniqueKeyColumns();
        if (columns.isEmpty()) {
            return 0L;
        }

        final String groupBy = String.join(", ", columns);
        final String conditions = columns.stream()
            .map(coluna -> coluna + " IS NOT NULL")
            .collect(Collectors.joining(" AND "));
        final String sql = """
            SELECT COUNT(*) FROM (
                SELECT %s
                FROM dbo.%s
                WHERE %s >= ? AND %s <= ?
                  AND %s
                GROUP BY %s
                HAVING COUNT(*) > 1
            ) d
            """.formatted(groupBy, entity.table(), entity.timestampColumn(), entity.timestampColumn(), conditions, groupBy);
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(janela.inicio()));
            stmt.setTimestamp(2, Timestamp.valueOf(janela.fim()));
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private List<EntitySpec> detectarEntidades(
        final Connection conexao,
        final boolean incluirFaturasGraphQL
    ) throws SQLException {
        final List<EntitySpec> candidatos = new ArrayList<>(List.of(
            new EntitySpec(ConstantesEntidades.COLETAS, "coletas", "data_extracao", List.of("id"), "sequence_code"),
            new EntitySpec(ConstantesEntidades.FRETES, "fretes", "data_extracao", List.of("id"), null),
            new EntitySpec(ConstantesEntidades.MANIFESTOS, "manifestos", "data_extracao", List.of("sequence_code", "pick_sequence_code", "mdfe_number"), null),
            new EntitySpec(ConstantesEntidades.COTACOES, "cotacoes", "data_extracao", List.of("sequence_code"), "sequence_code"),
            new EntitySpec(ConstantesEntidades.LOCALIZACAO_CARGAS, "localizacao_cargas", "data_extracao", List.of("sequence_number"), "sequence_number"),
            new EntitySpec(ConstantesEntidades.CONTAS_A_PAGAR, "contas_a_pagar", "data_extracao", List.of("sequence_code"), "sequence_code"),
            new EntitySpec(ConstantesEntidades.FATURAS_POR_CLIENTE, "faturas_por_cliente", "data_extracao", List.of("unique_id"), null)
        ));
        if (incluirFaturasGraphQL) {
            candidatos.add(new EntitySpec(ConstantesEntidades.FATURAS_GRAPHQL, "faturas_graphql", "data_extracao", List.of("id"), null));
        }
        candidatos.add(new EntitySpec(ConstantesEntidades.USUARIOS_SISTEMA, "dim_usuarios", "data_atualizacao", List.of("user_id"), null));

        final List<EntitySpec> ativas = new ArrayList<>();
        for (final EntitySpec candidato : candidatos) {
            if (tabelaExiste(conexao, candidato.table())) {
                ativas.add(candidato);
            }
        }
        return ativas;
    }

    private boolean tabelaExiste(final Connection conexao, final String tabela) throws SQLException {
        final String sql = """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = 'dbo'
              AND TABLE_NAME = ?
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, tabela);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private LogSnapshot buscarUltimoLog(
        final Connection conexao,
        final String entidade,
        final LocalDate dataReferencia
    ) throws SQLException {
        final String sql = """
            SELECT TOP 1 status_final, paginas_processadas, mensagem
            FROM dbo.log_extracoes
            WHERE entidade = ?
              AND CAST(timestamp_inicio AS DATE) IN (?, ?)
            ORDER BY timestamp_fim DESC
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, entidade);
            stmt.setDate(2, java.sql.Date.valueOf(dataReferencia));
            stmt.setDate(3, java.sql.Date.valueOf(dataReferencia.minusDays(1)));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new LogSnapshot(rs.getString("status_final"), rs.getInt("paginas_processadas"), rs.getString("mensagem"));
                }
            }
        }
        return null;
    }

    private int executarCount(
        final Connection conexao,
        final String sql,
        final LocalDateTime inicio,
        final LocalDateTime fim
    ) throws SQLException {
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(inicio));
            stmt.setTimestamp(2, Timestamp.valueOf(fim));
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String fingerprintBanco(final Connection conexao, final List<EntitySpec> entidades) throws SQLException {
        final List<String> fragments = new ArrayList<>();
        for (final EntitySpec entidade : entidades) {
            if (ConstantesEntidades.USUARIOS_SISTEMA.equals(entidade.entidade())) {
                final String sql = """
                    SELECT CAST(user_id AS VARCHAR(50)) AS chave, nome
                    FROM dbo.dim_usuarios
                    WHERE user_id IS NOT NULL
                    ORDER BY user_id
                    """;
                try (PreparedStatement stmt = conexao.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        fragments.add(entidade.entidade() + "|" + rs.getString("chave") + "|" + safe(rs.getString("nome")));
                    }
                }
                continue;
            }

            final String keyExpr = switch (entidade.entidade()) {
                case ConstantesEntidades.MANIFESTOS ->
                    "CONCAT(CAST(sequence_code AS VARCHAR(50)),'|',COALESCE(CAST(pick_sequence_code AS VARCHAR(50)),'-1'),'|',COALESCE(CAST(mdfe_number AS VARCHAR(50)),'-1'))";
                case ConstantesEntidades.COTACOES -> "CAST(sequence_code AS VARCHAR(50))";
                case ConstantesEntidades.LOCALIZACAO_CARGAS -> "CAST(sequence_number AS VARCHAR(50))";
                case ConstantesEntidades.CONTAS_A_PAGAR -> "CAST(sequence_code AS VARCHAR(50))";
                case ConstantesEntidades.FATURAS_POR_CLIENTE -> "unique_id";
                case ConstantesEntidades.FRETES -> "CAST(id AS VARCHAR(50))";
                case ConstantesEntidades.COLETAS -> "id";
                case ConstantesEntidades.FATURAS_GRAPHQL -> "CAST(id AS VARCHAR(50))";
                default -> throw new IllegalArgumentException("Entidade nao suportada no fingerprint: " + entidade.entidade());
            };
            final String sql;
            if (ConstantesEntidades.FATURAS_GRAPHQL.equals(entidade.entidade())) {
                sql = "SELECT " + keyExpr + " AS chave, document, issue_date, due_date, original_due_date,"
                    + " value, value_to_pay, discount_value, interest_value, type, sequence_code,"
                    + " competence_month, competence_year, corporation_id, corporation_name, corporation_cnpj,"
                    + " nfse_numero, carteira_banco, instrucao_boleto, banco_nome, metodo_pagamento"
                    + " FROM dbo." + entidade.table()
                    + " WHERE " + keyExpr + " IS NOT NULL ORDER BY chave";
            } else {
                sql = "SELECT " + keyExpr + " AS chave, metadata FROM dbo." + entidade.table()
                    + " WHERE " + keyExpr + " IS NOT NULL ORDER BY chave";
            }
            try (PreparedStatement stmt = conexao.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final String chave = rs.getString("chave");
                    if (usaSomenteChaveNoFingerprint(entidade.entidade())) {
                        fragments.add(entidade.entidade() + "|" + chave);
                        continue;
                    }
                    if (ConstantesEntidades.FATURAS_GRAPHQL.equals(entidade.entidade())) {
                        fragments.add(entidade.entidade() + "|" + chave + "|" + fingerprintFaturaGraphQL(rs));
                        continue;
                    }
                    fragments.add(
                        entidade.entidade()
                            + "|"
                            + chave
                            + "|"
                            + metadataHasher.hashMetadata(entidade.entidade(), rs.getString("metadata"))
                    );
                }
            }
        }
        return sha256(String.join("\n", fragments));
    }

    private boolean usaSomenteChaveNoFingerprint(final String entidade) {
        return switch (entidade) {
            case ConstantesEntidades.COLETAS,
                 ConstantesEntidades.FRETES,
                 ConstantesEntidades.MANIFESTOS,
                 ConstantesEntidades.COTACOES,
                 ConstantesEntidades.LOCALIZACAO_CARGAS,
                 ConstantesEntidades.CONTAS_A_PAGAR -> true;
            default -> false;
        };
    }

    private String fingerprintFaturaGraphQL(final ResultSet rs) throws SQLException {
        return sha256(String.join(
            "|",
            coluna(rs, "document"),
            coluna(rs, "issue_date"),
            coluna(rs, "due_date"),
            coluna(rs, "original_due_date"),
            coluna(rs, "value"),
            coluna(rs, "value_to_pay"),
            coluna(rs, "discount_value"),
            coluna(rs, "interest_value"),
            coluna(rs, "type"),
            coluna(rs, "sequence_code"),
            coluna(rs, "competence_month"),
            coluna(rs, "competence_year"),
            coluna(rs, "corporation_id"),
            coluna(rs, "corporation_name"),
            coluna(rs, "corporation_cnpj"),
            coluna(rs, "nfse_numero"),
            coluna(rs, "carteira_banco"),
            coluna(rs, "instrucao_boleto"),
            coluna(rs, "banco_nome"),
            coluna(rs, "metodo_pagamento")
        ));
    }

    private String coluna(final ResultSet rs, final String nomeColuna) throws SQLException {
        final Object valor = rs.getObject(nomeColuna);
        return safe(valor == null ? null : String.valueOf(valor));
    }

    private ReportFiles persistirReport(final FinalReport report) throws IOException {
        final Path logsDir = Path.of("logs");
        Files.createDirectories(logsDir);
        final String suffix = FILE_TS.format(report.finishedAt());
        final Path json = logsDir.resolve("etl_extreme_report_" + suffix + ".json");
        final Path markdown = logsDir.resolve("etl_extreme_report_" + suffix + ".md");
        Files.writeString(
            json,
            MapperUtil.sharedJson().writerWithDefaultPrettyPrinter().writeValueAsString(report),
            StandardCharsets.UTF_8
        );
        Files.writeString(markdown, renderMarkdown(report), StandardCharsets.UTF_8);
        return new ReportFiles(json, markdown);
    }

    private String renderMarkdown(final FinalReport report) {
        final StringBuilder md = new StringBuilder();
        md.append("# Relatorio extremo do ETL\n\n");
        md.append("- Inicio: `").append(report.startedAt()).append("`\n");
        md.append("- Fim: `").append(report.finishedAt()).append("`\n");
        md.append("- Janela API: `").append(report.windowStart()).append("` ate `").append(report.windowEnd()).append("`\n");
        md.append("- Falhas: `").append(report.failureCount()).append("`\n\n");

        md.append("## Entidades\n\n");
        md.append("| Entidade | Status | API_TOTAL | DB_TOTAL | MISSING | EXTRA | DIVERGENT_FIELDS |\n");
        md.append("|---|---:|---:|---:|---:|---:|---:|\n");
        for (final EntityDiagnostic entity : report.entities()) {
            md.append("| ")
                .append(entity.entidade()).append(" | ")
                .append(entity.status()).append(" | ")
                .append(entity.apiTotal()).append(" | ")
                .append(entity.dbTotal()).append(" | ")
                .append(entity.missing()).append(" | ")
                .append(entity.extra()).append(" | ")
                .append(entity.divergentFields()).append(" |\n");
        }

        md.append("\n## Paginacao\n\n");
        for (final EntityDiagnostic entity : report.entities()) {
            md.append("- `").append(entity.entidade()).append("`: ").append(entity.paginationDetail()).append("\n");
        }

        md.append("\n## Performance\n\n");
        for (final PerformanceMetric metric : report.performance()) {
            md.append("- `").append(metric.entidade()).append("`: ")
                .append(metric.duracaoMs()).append(" ms, paginas=")
                .append(metric.paginas()).append(", status=")
                .append(metric.status()).append("\n");
        }

        md.append("\n## Problemas\n\n");
        if (report.problems().isEmpty()) {
            md.append("- Nenhum problema encontrado.\n");
        } else {
            for (final String problem : report.problems()) {
                md.append("- ").append(problem).append("\n");
            }
        }

        md.append("\n## Sugestoes\n\n");
        for (final String suggestion : report.suggestions()) {
            md.append("- ").append(suggestion).append("\n");
        }

        md.append("\n## Testes opcionais\n\n");
        md.append("- Idempotencia: `").append(report.idempotency().status()).append("`\n");
        md.append("- Hidratacao de orfaos: `").append(report.orphanHydration().status()).append("`\n");
        return md.toString();
    }

    private void imprimirResumo(final FinalReport report, final ReportFiles files) {
        log.console("\n" + "=".repeat(96));
        log.info("RELATORIO FINAL DA BATERIA EXTREMA");
        for (final EntityDiagnostic entity : report.entities()) {
            log.info(
                "{} | status={} | api_total={} | db_total={} | missing={} | extra={} | divergent_fields={}",
                entity.entidade(),
                entity.status(),
                entity.apiTotal(),
                entity.dbTotal(),
                entity.missing(),
                entity.extra(),
                entity.divergentFields()
            );
        }
        log.info("Integridade referencial: {} problema(s)", report.referentialIntegrity().failureCount());
        log.info("Analise de logs: {} problema(s)", report.logAnalysis().failureCount());
        log.info("Idempotencia: {}", report.idempotency().status());
        log.info("Hidratacao de orfaos: {}", report.orphanHydration().status());
        log.info("Relatorio JSON: {}", files.json().toAbsolutePath());
        log.info("Relatorio MD: {}", files.markdown().toAbsolutePath());
        log.console("=".repeat(96));
    }


    private long lastModifiedSafe(final Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private List<String> amostra(final Set<String> values) {
        return values.stream().limit(SAMPLE_LIMIT).toList();
    }

    private String resumir(final String value) {
        if (value == null || value.isBlank()) {
            return "sem_detalhe";
        }
        final String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 177) + "...";
    }

    private String safe(final String value) {
        return value == null ? "__NULL__" : value;
    }

    private String sha256(final String content) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return String.format("%064x", new BigInteger(1, hash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nao disponivel", e);
        }
    }

    private record EntitySpec(
        String entidade,
        String table,
        String timestampColumn,
        List<String> uniqueKeyColumns,
        String sequenceColumn
    ) {
    }

    private record FieldDifferenceSample(String key, String path, String apiValue, String dbValue) {
    }

    private record StressAnalysis(String status, String detail) {
    }

    private record PerformanceMetric(String entidade, long duracaoMs, int paginas, String status) {
    }

    private record LogSnapshot(String statusFinal, int paginasProcessadas, String mensagem) {
    }

    private record EntityDiagnostic(
        String entidade,
        String status,
        int apiTotal,
        int dbTotal,
        int missing,
        int extra,
        int divergentFields,
        int divergentRecords,
        int duplicateIds,
        int duplicateSequenceCodes,
        int unexpectedNulls,
        int truncatedFields,
        int timezoneDrifts,
        int missingStructureFields,
        int paginasApi,
        String paginationDetail,
        List<String> sampleMissing,
        List<String> sampleExtra,
        List<FieldDifferenceSample> fieldSamples,
        StressAnalysis stress,
        List<String> problems,
        List<String> suggestions
    ) {
    }

    private record ReferentialIntegrityAnalysis(
        List<String> problems,
        List<String> suggestions,
        int failureCount
    ) {
    }

    private record LogAnalysis(
        List<String> problems,
        List<String> suggestions,
        List<String> evidences,
        int failureCount
    ) {
    }

    private record IdempotencyAnalysis(
        String status,
        String baselineFingerprint,
        String afterFirstFingerprint,
        String afterSecondFingerprint,
        List<String> problems,
        List<String> suggestions,
        int failureCount
    ) {
        static IdempotencyAnalysis skipped(final String reason) {
            return new IdempotencyAnalysis("SKIPPED", null, null, null, List.of(reason), List.of(), 0);
        }
    }

    private record OrphanHydrationAnalysis(
        String status,
        int beforeCount,
        int afterCount,
        List<String> problems,
        List<String> suggestions,
        int failureCount
    ) {
    }

    private record FinalReport(
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDate dataReferencia,
        LocalDate windowStart,
        LocalDate windowEnd,
        List<EntityDiagnostic> entities,
        ReferentialIntegrityAnalysis referentialIntegrity,
        LogAnalysis logAnalysis,
        List<PerformanceMetric> performance,
        IdempotencyAnalysis idempotency,
        OrphanHydrationAnalysis orphanHydration,
        List<String> problems,
        List<String> suggestions,
        long failureCount
    ) {
    }

    private record ReportFiles(Path json, Path markdown) {
    }
}
