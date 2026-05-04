package br.com.extrator.aplicacao.validacao;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

import br.com.extrator.aplicacao.pipeline.PipelineOrchestrator;
import br.com.extrator.aplicacao.pipeline.PipelineReport;
import br.com.extrator.aplicacao.pipeline.PipelineStep;
import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.aplicacao.pipeline.runtime.StepStatus;
import br.com.extrator.aplicacao.politicas.CircuitBreaker;
import br.com.extrator.aplicacao.politicas.ErrorClassifier;
import br.com.extrator.aplicacao.politicas.ErrorTaxonomy;
import br.com.extrator.aplicacao.politicas.ExponentialBackoffRetryPolicy;
import br.com.extrator.aplicacao.politicas.FailureMode;
import br.com.extrator.aplicacao.portas.ClockPort;
import br.com.extrator.aplicacao.portas.ExtractionLoggerPort;
import br.com.extrator.bootstrap.pipeline.IsolatedStepProcessExecutor;
import br.com.extrator.comandos.cli.extracao.daemon.DaemonResilienceHarness;
import br.com.extrator.comandos.cli.extracao.daemon.LoopDaemonRunHandler;
import br.com.extrator.integracao.GraphQLPaginatorChaosHarness;
import br.com.extrator.integracao.ResultadoExtracao;
import br.com.extrator.observabilidade.LogRetentionPolicy;
import br.com.extrator.observabilidade.LogStoragePaths;
import br.com.extrator.observabilidade.pipeline.InMemoryPipelineMetrics;
import br.com.extrator.suporte.configuracao.ConfigEtl;
import br.com.extrator.suporte.concorrencia.ThreadLeakDetector;
import br.com.extrator.suporte.http.GerenciadorRequisicaoHttp;
import br.com.extrator.suporte.mapeamento.MapperUtil;
import br.com.extrator.suporte.tempo.RelogioSistema;

public class ValidacaoEtlResilienciaUseCase {
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final Duration TIMEOUT_STEP_PADRAO = Duration.ofMillis(150);
    private static final Duration TIMEOUT_CICLO_LOOP = Duration.ofSeconds(2);
    private static final Duration TIMEOUT_WATCHDOG = Duration.ofMillis(200);
    private static final String[] THREAD_PREFIXES_MONITORADOS = { "timeout-guard-", "pipeline-core-" };

    private final Path logsDir;
    private final IsolatedStepProcessExecutor isolatedStepExecutor;

    public ValidacaoEtlResilienciaUseCase() {
        this(LogStoragePaths.REPORTS_DIR, new IsolatedStepProcessExecutor());
    }

    ValidacaoEtlResilienciaUseCase(final Path logsDir) {
        this(logsDir, new IsolatedStepProcessExecutor());
    }

    ValidacaoEtlResilienciaUseCase(final Path logsDir,
                                   final IsolatedStepProcessExecutor isolatedStepExecutor) {
        this.logsDir = Objects.requireNonNull(logsDir, "logsDir");
        this.isolatedStepExecutor = Objects.requireNonNull(isolatedStepExecutor, "isolatedStepExecutor");
    }

    public ReportFiles executar(final ValidacaoEtlResilienciaRequest request) throws Exception {
        Files.createDirectories(logsDir);
        final LocalDateTime inicio = RelogioSistema.agora();
        final List<ScenarioResult> cenarios = new ArrayList<>();

        try (ConfigOverrideSession ignored = ConfigOverrideSession.rapido()) {
            adicionarCenario(cenarios, "LOOP_CONTINUO", () -> executarLoopContinuoSaudavel(request));
            if (request.incluirCenariosHttp()) {
                adicionarCenario(cenarios, "HTTP_HALF_OPEN", this::executarHttpHalfOpen);
                adicionarCenario(cenarios, "HTTP_INTERMITENTE_RETRY", this::executarHttpIntermitenteComRetry);
            }
            adicionarCenario(cenarios, "PAGINACAO_INFINITA", this::executarPaginacaoInfinita);
            adicionarCenario(cenarios, "FUTURE_TRAVADO", this::executarFutureTravado);
            adicionarCenario(cenarios, "API_LENTA_EXTREMA", this::executarApiLentaExtrema);
            adicionarCenario(cenarios, "BANCO_LENTO_BLOQUEADO", this::executarPersistenciaLenta);
            adicionarCenario(cenarios, "CIRCUIT_BREAKER", this::executarCircuitBreaker);
            adicionarCenario(cenarios, "THREAD_STARVATION", () -> executarSaturacaoThreads(request));
            adicionarCenario(cenarios, "WATCHDOG_GLOBAL", this::executarWatchdogGlobal);
            if (request.autoChaos()) {
                adicionarCenario(cenarios, "AUTO_CHAOS_LOOP", () -> executarAutoChaos(request));
            }
        }

        final FinalReport report = consolidarRelatorio(inicio, RelogioSistema.agora(), request, cenarios);
        final ReportFiles files = persistirReport(report);
        imprimirResumo(report, files);

        if (report.failureCount() > 0) {
            throw new RuntimeException(
                "Bateria de resiliencia reprovada: "
                    + report.failureCount()
                    + " cenario(s) com falha. Consulte "
                    + files.markdown().toAbsolutePath()
            );
        }
        return files;
    }

    private void adicionarCenario(final List<ScenarioResult> cenarios,
                                  final String nomeCenario,
                                  final ScenarioSupplier supplier) {
        cenarios.add(executarCenarioMonitorado(nomeCenario, supplier));
    }

    private ScenarioResult executarCenarioMonitorado(final String nomeCenario,
                                                     final ScenarioSupplier supplier) {
        final ThreadLeakDetector.Snapshot snapshotAntes = ThreadLeakDetector.captureByPrefix(THREAD_PREFIXES_MONITORADOS);
        final long inicio = System.nanoTime();
        ScenarioResult base;

        try {
            base = supplier.get();
        } catch (final Exception e) {
            final Throwable causaRaiz = rootCause(e);
            base = new ScenarioResult(
                nomeCenario,
                "FAIL",
                Duration.ofNanos(System.nanoTime() - inicio).toMillis(),
                0,
                0,
                0,
                mapOfFailures(causaRaiz),
                0L,
                0L,
                false,
                false,
                List.of(
                    "erro=" + causaRaiz.getClass().getSimpleName(),
                    "mensagem=" + Objects.toString(causaRaiz.getMessage(), "<sem_mensagem>")
                ),
                List.of("Cenario encerrou com excecao nao tratada."),
                List.of()
            );
        }

        aguardarEstabilizacaoThreads();
        final ThreadLeakDetector.LeakReport leakReport = ThreadLeakDetector.detectNewThreads(
            snapshotAntes,
            ThreadLeakDetector.captureByPrefix(THREAD_PREFIXES_MONITORADOS)
        );
        return leakReport.hasLeaks()
            ? falharPorThreadLeak(base, leakReport)
            : base;
    }

    private void aguardarEstabilizacaoThreads() {
        final long graceMs = Math.max(0L, ConfigEtl.obterTimeoutThreadLeakGraceMs());
        if (graceMs == 0L) {
            return;
        }
        try {
            Thread.sleep(graceMs);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private ScenarioResult falharPorThreadLeak(final ScenarioResult base,
                                               final ThreadLeakDetector.LeakReport leakReport) {
        final Map<String, Integer> falhas = new LinkedHashMap<>(base.failuresByType());
        falhas.merge("THREAD_LEAK", leakReport.leakedThreads().size(), Integer::sum);

        final List<String> evidencias = new ArrayList<>(base.evidences());
        evidencias.add("thread_leak=" + leakReport.summary());
        evidencias.add("thread_delta=" + leakReport.totalThreadDelta());

        final List<String> detalhes = new ArrayList<>(base.details());
        detalhes.add("Thread residual detectada via ThreadMXBean apos o timeout/cenario.");

        return new ScenarioResult(
            base.scenario(),
            "FAIL",
            base.durationMs(),
            base.cycles(),
            base.timeouts(),
            base.retries(),
            falhas,
            base.maxCycleDurationMs(),
            base.maxStepDurationMs(),
            false,
            base.noInfiniteRunning(),
            List.copyOf(evidencias),
            List.copyOf(detalhes),
            base.evidencePaths()
        );
    }

    private ScenarioResult executarLoopContinuoSaudavel(final ValidacaoEtlResilienciaRequest request) throws Exception {
        final AtomicInteger ciclosExecutados = new AtomicInteger();
        final List<CycleObservation> observacoes = Collections.synchronizedList(new ArrayList<>());
        final Path baseDir = logsDir.resolve("resilience_loop_" + FILE_TS.format(RelogioSistema.agora()));
        final LocalDateTime inicioTeste = LocalDateTime.now();

        final DaemonResilienceHarness.ProbeResult probe = DaemonResilienceHarness.executar(
            baseDir,
            incluirFaturasGraphQL -> {
                final int ciclo = ciclosExecutados.incrementAndGet();
                observacoes.add(executarPipeline("loop-saudavel-" + ciclo, FaultMode.NONE).cycleObservation("healthy"));
            },
            (proximoCiclo, stateStore) -> ciclosExecutados.get() >= request.maxCycles()
                || Duration.between(inicioTeste, LocalDateTime.now()).compareTo(request.duracaoMaxima()) >= 0
                ? LoopDaemonRunHandler.WaitResult.STOP_REQUESTED
                : LoopDaemonRunHandler.WaitResult.FORCE_RUN_REQUESTED,
            incluirFaturasGraphQL -> TIMEOUT_CICLO_LOOP
        );

        final boolean semRunningInfinito = !"RUNNING".equalsIgnoreCase(probe.finalState().getProperty("status", ""));
        final boolean historicoConsistente = probe.cycleHistoryEntries() == ciclosExecutados.get();
        final boolean pipelineContinuou = observacoes.stream().allMatch(observacao -> !observacao.aborted());

        return new ScenarioResult(
            "LOOP_CONTINUO",
            (semRunningInfinito && historicoConsistente && pipelineContinuou) ? "PASS" : "FAIL",
            probe.durationMs(),
            ciclosExecutados.get(),
            somarTimeouts(observacoes),
            somarRetries(observacoes),
            agregarFalhasPorTipoObservacoes(observacoes),
            observacoes.stream().mapToLong(CycleObservation::durationMs).max().orElse(0L),
            observacoes.stream().flatMap(observacao -> observacao.steps().stream()).mapToLong(StepObservation::durationMs).max().orElse(0L),
            pipelineContinuou,
            semRunningInfinito,
            List.of(
                "ciclos_executados=" + ciclosExecutados.get(),
                "historico_registrado=" + probe.cycleHistoryEntries(),
                "estado_final=" + probe.finalState().getProperty("status", "desconhecido")
            ),
            List.of(
                "Todos os ciclos fecharam no historico do daemon.",
                "Nenhum ciclo permaneceu em RUNNING ao final do teste."
            ),
            probe.cycleLogs().stream().map(path -> path.toAbsolutePath().toString()).toList()
        );
    }

    private ScenarioResult executarHttpHalfOpen() throws Exception {
        final AtomicInteger requisicoesRecebidas = new AtomicInteger();
        final CountDownLatch conexaoAceita = new CountDownLatch(1);
        final long inicio = System.nanoTime();
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        server.setExecutor(executor);
        server.createContext("/graphql", exchange -> {
            requisicoesRecebidas.incrementAndGet();
            conexaoAceita.countDown();
            try {
                Thread.sleep(500L);
                final byte[] corpo = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, corpo.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(corpo);
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final IOException ignored) {
                // O cliente deve abandonar a resposta ao atingir o timeout.
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(100))
                .build();
            final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/graphql"))
                .timeout(Duration.ofMillis(150))
                .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"{ ping }\"}"))
                .build();

            final GerenciadorRequisicaoHttp gerenciador = criarGerenciadorHttp();
            RuntimeException erroCapturado = null;
            try {
                gerenciador.executarRequisicao(client, request, "freight-half-open");
            } catch (final RuntimeException e) {
                erroCapturado = e;
            }

            final long duracaoMs = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
            final boolean registrouConexao = requisicoesRecebidas.get() >= 1 || conexaoAceita.await(300, TimeUnit.MILLISECONDS);
            final Throwable causaRaiz = rootCause(erroCapturado);
            final boolean passou = erroCapturado != null
                && causaRaiz instanceof HttpTimeoutException
                && duracaoMs < 1_500L
                && registrouConexao;

            return new ScenarioResult(
                "HTTP_HALF_OPEN",
                passou ? "PASS" : "FAIL",
                duracaoMs,
                0,
                passou ? 1 : 0,
                Math.max(0, requisicoesRecebidas.get() - 1),
                mapOfFailures(causaRaiz),
                0L,
                0L,
                true,
                true,
                List.of(
                    "requisicoes_recebidas=" + requisicoesRecebidas.get(),
                    "conexao_registrada=" + registrouConexao,
                    "erro=" + (causaRaiz == null ? "sem_erro" : causaRaiz.getClass().getSimpleName()),
                    "duracao_ms=" + duracaoMs
                ),
                List.of("Timeout HTTP disparou mesmo com socket aberto sem resposta."),
                List.of()
            );
        } finally {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private ScenarioResult executarHttpIntermitenteComRetry() throws Exception {
        final AtomicInteger chamadas = new AtomicInteger();
        final long inicio = System.nanoTime();
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/graphql", exchange -> {
            final int tentativa = chamadas.incrementAndGet();
            final int status = tentativa == 1 ? 503 : 200;
            final byte[] corpo = (tentativa == 1 ? "{\"erro\":\"transient\"}" : "{\"ok\":true}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, corpo.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(corpo);
            }
        });
        server.start();

        try {
            final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(100))
                .build();
            final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/graphql"))
                .timeout(Duration.ofMillis(250))
                .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"{ ping }\"}"))
                .build();

            final HttpResponse<String> resposta = criarGerenciadorHttp().executarRequisicao(client, request, "freight-intermitente");
            final long duracaoMs = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
            final boolean passou = resposta.statusCode() == 200 && chamadas.get() == 2;

            return new ScenarioResult(
                "HTTP_INTERMITENTE_RETRY",
                passou ? "PASS" : "FAIL",
                duracaoMs,
                0,
                0,
                Math.max(0, chamadas.get() - 1),
                Map.of(),
                0L,
                0L,
                true,
                true,
                List.of(
                    "status_final=" + resposta.statusCode(),
                    "chamadas=" + chamadas.get(),
                    "duracao_ms=" + duracaoMs
                ),
                List.of("Retry HTTP recuperou falha intermitente sem travar a operacao."),
                List.of()
            );
        } finally {
            server.stop(0);
        }
    }

    private ScenarioResult executarPaginacaoInfinita() {
        final GraphQLPaginatorChaosHarness.ProbeResult probe = GraphQLPaginatorChaosHarness.executarPaginacaoInfinita();
        final boolean passou = !probe.completo()
            && ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS.getCodigo().equals(probe.motivoInterrupcao());

        return new ScenarioResult(
            "PAGINACAO_INFINITA",
            passou ? "PASS" : "FAIL",
            probe.durationMs(),
            0,
            0,
            0,
            passou ? Map.of("LIMITE_PAGINAS", 1) : Map.of("PAGINACAO", 1),
            0L,
            0L,
            true,
            true,
            List.of(
                "motivo=" + probe.motivoInterrupcao(),
                "paginas_processadas=" + probe.paginasProcessadas(),
                "registros_extraidos=" + probe.registrosExtraidos()
            ),
            List.of("Protecao de limite de paginas interrompeu o loop infinito."),
            List.of()
        );
    }

    private ScenarioResult executarFutureTravado() {
        final PipelineRunSummary summary = executarPipeline("future-travado", FaultMode.FUTURE_HANG);
        final boolean timeoutObservado = summary.report().getResultados().stream()
            .anyMatch(resultado -> "graphql".equalsIgnoreCase(resultado.obterNomeEntidade()) && resultado.getStatus() == StepStatus.FAILED);
        final boolean passou = !summary.report().isAborted()
            && possuiStepStatus(summary.report(), "dataexport", StepStatus.SUCCESS)
            && possuiStepStatus(summary.report(), "quality", StepStatus.SUCCESS)
            && timeoutObservado
            && summary.durationMs() < 2_500L;
        return new ScenarioResult(
            "FUTURE_TRAVADO",
            passou ? "PASS" : "FAIL",
            summary.durationMs(),
            1,
            timeoutObservado ? 1 : 0,
            contarRetries(summary.report()),
            timeoutObservado ? Map.of("TIMEOUT", 1) : agregarFalhasPorTipoReports(List.of(summary.report())),
            summary.durationMs(),
            summary.report().getResultados().stream().mapToLong(StepExecutionResult::durationMillis).max().orElse(0L),
            !summary.report().isAborted(),
            true,
            summary.evidencias(),
            List.of("future.get(timeout) impediu espera infinita no step travado."),
            List.of()
        );
    }

    private ScenarioResult executarApiLentaExtrema() {
        final PipelineRunSummary summary = executarPipeline("api-lenta", FaultMode.API_SLOW);
        final boolean timeoutObservado = summary.report().getResultados().stream()
            .anyMatch(resultado -> "graphql".equalsIgnoreCase(resultado.obterNomeEntidade()) && resultado.getStatus() == StepStatus.FAILED);
        final boolean passou = !summary.report().isAborted()
            && possuiStepStatus(summary.report(), "dataexport", StepStatus.SUCCESS)
            && timeoutObservado;
        return new ScenarioResult(
            "API_LENTA_EXTREMA",
            passou ? "PASS" : "FAIL",
            summary.durationMs(),
            1,
            timeoutObservado ? 1 : 0,
            contarRetries(summary.report()),
            timeoutObservado ? Map.of("TIMEOUT", 1) : agregarFalhasPorTipoReports(List.of(summary.report())),
            summary.durationMs(),
            summary.report().getResultados().stream().mapToLong(StepExecutionResult::durationMillis).max().orElse(0L),
            !summary.report().isAborted(),
            true,
            summary.evidencias(),
            List.of("Timeout por step/entidade abortou a chamada lenta e o pipeline seguiu."),
            List.of()
        );
    }

    private ScenarioResult executarPersistenciaLenta() {
        final PipelineRunSummary summary = executarPipeline("persistencia-lenta", FaultMode.PERSISTENCE_SLOW);
        final boolean timeoutObservado = summary.report().getResultados().stream()
            .anyMatch(resultado -> "dataexport".equalsIgnoreCase(resultado.obterNomeEntidade()) && resultado.getStatus() == StepStatus.FAILED);
        final boolean passou = !summary.report().isAborted()
            && possuiStepStatus(summary.report(), "graphql", StepStatus.SUCCESS)
            && timeoutObservado;
        return new ScenarioResult(
            "BANCO_LENTO_BLOQUEADO",
            passou ? "PASS" : "FAIL",
            summary.durationMs(),
            1,
            timeoutObservado ? 1 : 0,
            contarRetries(summary.report()),
            timeoutObservado ? Map.of("TIMEOUT", 1) : agregarFalhasPorTipoReports(List.of(summary.report())),
            summary.durationMs(),
            summary.report().getResultados().stream().mapToLong(StepExecutionResult::durationMillis).max().orElse(0L),
            !summary.report().isAborted(),
            true,
            summary.evidencias(),
            List.of("Timeout de persistencia nao bloqueou o restante do pipeline."),
            List.of()
        );
    }

    private ScenarioResult executarCircuitBreaker() {
        final CapturingExtractionLogger logger = new CapturingExtractionLogger();
        final PipelineOrchestrator orchestrator = criarOrchestrator(
            logger,
            new CircuitBreaker(1, Duration.ofSeconds(5), new SystemClockPort()),
            (entidade, taxonomy) -> FailureMode.CONTINUE_WITH_ALERT
        );
        final PipelineStep timeoutStep = new PipelineStep() {
            @Override
            public StepExecutionResult executar(final LocalDate dataInicio, final LocalDate dataFim) throws Exception {
                throw new HttpTimeoutException("timeout circuit breaker");
            }

            @Override
            public String obterNomeEtapa() {
                return "graphql:circuit-breaker";
            }

            @Override
            public String obterNomeEntidade() {
                return "graphql";
            }
        };

        final long inicio = System.nanoTime();
        final PipelineReport first = orchestrator.executar(RelogioSistema.hoje().minusDays(1), RelogioSistema.hoje(), List.of(timeoutStep));
        final PipelineReport second = orchestrator.executar(RelogioSistema.hoje().minusDays(1), RelogioSistema.hoje(), List.of(timeoutStep));
        final long duracaoMs = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

        final boolean passou = first.getResultados().get(0).getStatus() == StepStatus.FAILED
            && second.getResultados().get(0).getStatus() == StepStatus.SKIPPED;
        return new ScenarioResult(
            "CIRCUIT_BREAKER",
            passou ? "PASS" : "FAIL",
            duracaoMs,
            2,
            1,
            0,
            mapOfFailures(first.getResultados().get(0).getErrorTaxonomy()),
            0L,
            first.getResultados().stream().mapToLong(StepExecutionResult::durationMillis).max().orElse(0L),
            true,
            true,
            List.of(
                "primeira_execucao=" + first.getResultados().get(0).getStatus(),
                "segunda_execucao=" + second.getResultados().get(0).getStatus(),
                "eventos=" + logger.eventos().size()
            ),
            List.of("Circuit breaker abriu apos a falha inicial e pulou a chamada seguinte."),
            List.of()
        );
    }

    private ScenarioResult executarSaturacaoThreads(final ValidacaoEtlResilienciaRequest request) throws Exception {
        final int workers = request.stressConcorrencia();
        final int totalTarefas = workers * 2;
        final ExecutorService executor = Executors.newFixedThreadPool(workers);
        final List<Callable<PipelineRunSummary>> tarefas = new ArrayList<>();
        for (int i = 0; i < totalTarefas; i++) {
            final int indice = i;
            final FaultMode modo = i % 2 == 0 ? FaultMode.FUTURE_HANG : FaultMode.API_SLOW;
            tarefas.add(() -> executarPipeline("stress-" + indice, modo));
        }

        final long inicio = System.nanoTime();
        final List<Future<PipelineRunSummary>> futures = executor.invokeAll(tarefas, 20, TimeUnit.SECONDS);
        executor.shutdownNow();
        final long duracaoMs = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

        boolean todasConcluiram = true;
        final List<PipelineRunSummary> resultados = new ArrayList<>();
        for (final Future<PipelineRunSummary> future : futures) {
            if (!future.isDone() || future.isCancelled()) {
                todasConcluiram = false;
                continue;
            }
            resultados.add(future.get());
        }

        final boolean passou = todasConcluiram
            && resultados.size() == totalTarefas
            && resultados.stream().noneMatch(resultado -> resultado.report().isAborted());
        return new ScenarioResult(
            "THREAD_STARVATION",
            passou ? "PASS" : "FAIL",
            duracaoMs,
            resultados.size(),
            resultados.stream().mapToInt(resultado -> contarTaxonomia(resultado.report(), ErrorTaxonomy.TIMEOUT)).sum(),
            resultados.stream().mapToInt(resultado -> contarRetries(resultado.report())).sum(),
            agregarFalhasPorTipoReports(resultados.stream().map(PipelineRunSummary::report).toList()),
            resultados.stream().mapToLong(PipelineRunSummary::durationMs).max().orElse(0L),
            resultados.stream().flatMap(resultado -> resultado.report().getResultados().stream()).mapToLong(StepExecutionResult::durationMillis).max().orElse(0L),
            resultados.stream().allMatch(resultado -> !resultado.report().isAborted()),
            todasConcluiram,
            List.of("workers=" + workers, "tarefas=" + totalTarefas, "concluidas=" + resultados.size()),
            List.of("Saturacao controlada do pool terminou sem deadlock nem espera indefinida."),
            List.of()
        );
    }

    private ScenarioResult executarWatchdogGlobal() throws Exception {
        final Path baseDir = logsDir.resolve("resilience_watchdog_" + FILE_TS.format(RelogioSistema.agora()));
        final DaemonResilienceHarness.ProbeResult probe = DaemonResilienceHarness.executar(
            baseDir,
            incluirFaturasGraphQL -> executarHangNaoCooperativoEmProcessoIsolado(
                IsolatedStepProcessExecutor.ApiType.GRAPHQL,
                "coletas"
            ),
            (proximoCiclo, stateStore) -> LoopDaemonRunHandler.WaitResult.STOP_REQUESTED,
            incluirFaturasGraphQL -> TIMEOUT_WATCHDOG
        );
        final String logCiclo = probe.cycleLogs().isEmpty()
            ? ""
            : Files.readString(probe.cycleLogs().get(0), StandardCharsets.UTF_8);
        final boolean passou = probe.durationMs() < 2_500L
            && !"RUNNING".equalsIgnoreCase(probe.finalState().getProperty("status", ""))
            && logCiclo.toLowerCase(Locale.ROOT).contains("timeout");

        return new ScenarioResult(
            "WATCHDOG_GLOBAL",
            passou ? "PASS" : "FAIL",
            probe.durationMs(),
            probe.cycleHistoryEntries(),
            1,
            0,
            Map.of("TIMEOUT", 1),
            probe.durationMs(),
            0L,
            true,
            true,
            List.of(
                "estado_final=" + probe.finalState().getProperty("status", "desconhecido"),
                "historico=" + probe.cycleHistoryEntries(),
                "logs_ciclo=" + probe.cycleLogs().size()
            ),
            List.of("Watchdog global encerrou o ciclo travado e o daemon saiu de RUNNING infinito."),
            probe.cycleLogs().stream().map(path -> path.toAbsolutePath().toString()).toList()
        );
    }

    private ScenarioResult executarAutoChaos(final ValidacaoEtlResilienciaRequest request) throws Exception {
        final Path baseDir = logsDir.resolve("resilience_auto_chaos_" + FILE_TS.format(RelogioSistema.agora()));
        final AtomicInteger ciclosExecutados = new AtomicInteger();
        final List<CycleObservation> observacoes = Collections.synchronizedList(new ArrayList<>());
        final List<FaultMode> modos = planoChaos(request.maxCycles(), request.seed());

        final DaemonResilienceHarness.ProbeResult probe = DaemonResilienceHarness.executar(
            baseDir,
            incluirFaturasGraphQL -> {
                final int indice = ciclosExecutados.getAndIncrement();
                final FaultMode modo = modos.get(Math.min(indice, modos.size() - 1));
                final PipelineRunSummary summary = executarPipeline("auto-chaos-" + indice, modo);
                observacoes.add(summary.cycleObservation(modo.name()));
                if (summary.report().isAborted() || summary.report().totalFalhasExecucao() > 0) {
                    throw new RuntimeException("Falha controlada injetada | ciclo=" + indice + " | modo=" + modo.name());
                }
            },
            (proximoCiclo, stateStore) -> ciclosExecutados.get() >= request.maxCycles()
                ? LoopDaemonRunHandler.WaitResult.STOP_REQUESTED
                : LoopDaemonRunHandler.WaitResult.FORCE_RUN_REQUESTED,
            incluirFaturasGraphQL -> TIMEOUT_CICLO_LOOP
        );

        final long ciclosComFalha = observacoes.stream().filter(CycleObservation::hasFailure).count();
        final long ciclosComSucesso = observacoes.stream().filter(observacao -> !observacao.hasFailure()).count();
        final boolean semRunningInfinito = !"RUNNING".equalsIgnoreCase(probe.finalState().getProperty("status", ""));
        final boolean passou = semRunningInfinito
            && probe.cycleHistoryEntries() == ciclosExecutados.get()
            && ciclosComFalha >= 1
            && ciclosComSucesso >= 1;

        return new ScenarioResult(
            "AUTO_CHAOS_LOOP",
            passou ? "PASS" : "FAIL",
            probe.durationMs(),
            ciclosExecutados.get(),
            somarTimeouts(observacoes),
            somarRetries(observacoes),
            agregarFalhasPorTipoObservacoes(observacoes),
            observacoes.stream().mapToLong(CycleObservation::durationMs).max().orElse(0L),
            observacoes.stream().flatMap(observacao -> observacao.steps().stream()).mapToLong(StepObservation::durationMs).max().orElse(0L),
            true,
            semRunningInfinito,
            List.of(
                "ciclos_sucesso=" + ciclosComSucesso,
                "ciclos_com_falha_controlada=" + ciclosComFalha,
                "estado_final=" + probe.finalState().getProperty("status", "desconhecido")
            ),
            List.of("Chaos automatico com seed reproduzivel validou retomada apos falhas controladas."),
            probe.cycleLogs().stream().map(path -> path.toAbsolutePath().toString()).toList()
        );
    }

    private PipelineRunSummary executarPipeline(final String nomeExecucao, final FaultMode modo) {
        final CapturingExtractionLogger logger = new CapturingExtractionLogger();
        final PipelineOrchestrator orchestrator = criarOrchestrator(
            logger,
            new CircuitBreaker(5, Duration.ofSeconds(30), new SystemClockPort()),
            (entidade, taxonomy) -> "graphql".equals(entidade) && taxonomy == ErrorTaxonomy.TRANSIENT_API_ERROR
                ? FailureMode.RETRY
                : FailureMode.CONTINUE_WITH_ALERT
        );
        final AtomicInteger tentativasGraphQL = new AtomicInteger();
        final long inicio = System.nanoTime();
        final PipelineReport report = orchestrator.executar(
            RelogioSistema.hoje().minusDays(1),
            RelogioSistema.hoje(),
            List.of(
                criarStepGraphQL(nomeExecucao, modo, tentativasGraphQL),
                criarStepDataExport(nomeExecucao, modo),
                criarStepQuality(nomeExecucao)
            )
        );
        final long duracaoMs = Duration.ofNanos(System.nanoTime() - inicio).toMillis();
        return new PipelineRunSummary(
            report,
            duracaoMs,
            List.of("modo=" + modo.name(), "steps=" + report.getResultados().size(), "eventos=" + logger.eventos().size())
        );
    }

    private void executarHangNaoCooperativoEmProcessoIsolado(final IsolatedStepProcessExecutor.ApiType apiType,
                                                             final String entidade) throws Exception {
        isolatedStepExecutor.executar(
            apiType,
            RelogioSistema.hoje().minusDays(1),
            RelogioSistema.hoje(),
            entidade,
            IsolatedStepProcessExecutor.FaultMode.HANG_IGNORE_INTERRUPT
        );
    }

    private PipelineOrchestrator criarOrchestrator(final CapturingExtractionLogger logger,
                                                   final CircuitBreaker circuitBreaker,
                                                   final br.com.extrator.aplicacao.politicas.FailurePolicy failurePolicy) {
        return new PipelineOrchestrator(
            new ExponentialBackoffRetryPolicy(2, 20L, 1.0d, 0.0d, new SystemClockPort()),
            failurePolicy,
            circuitBreaker,
            new ErrorClassifier(),
            logger,
            new InMemoryPipelineMetrics()
        );
    }

    private PipelineStep criarStepGraphQL(final String nomeExecucao,
                                          final FaultMode modo,
                                          final AtomicInteger tentativas) {
        return new PipelineStep() {
            @Override
            public StepExecutionResult executar(final LocalDate dataInicio, final LocalDate dataFim) throws Exception {
                final LocalDateTime startedAt = LocalDateTime.now();
                final int tentativa = tentativas.incrementAndGet();
                if (modo == FaultMode.FUTURE_HANG) {
                    executarHangNaoCooperativoEmProcessoIsolado(IsolatedStepProcessExecutor.ApiType.GRAPHQL, "coletas");
                    throw new IllegalStateException("Processo isolado retornou inesperadamente apos fault hang.");
                }
                if (modo == FaultMode.API_SLOW) {
                    Thread.sleep(300L);
                }
                if (modo == FaultMode.INTERMITTENT && tentativa == 1) {
                    throw new IllegalStateException("transient api failure");
                }
                Thread.sleep(40L);
                return StepExecutionResult.builder("graphql:" + nomeExecucao, "graphql")
                    .status(StepStatus.SUCCESS)
                    .startedAt(startedAt)
                    .finishedAt(LocalDateTime.now())
                    .attempt(tentativa)
                    .message("graphql_ok")
                    .build();
            }

            @Override
            public String obterNomeEtapa() {
                return "graphql:" + nomeExecucao;
            }

            @Override
            public String obterNomeEntidade() {
                return "graphql";
            }

            @Override
            public Duration obterTimeoutExecucao() {
                return TIMEOUT_STEP_PADRAO;
            }
        };
    }

    private PipelineStep criarStepDataExport(final String nomeExecucao, final FaultMode modo) {
        return new PipelineStep() {
            @Override
            public StepExecutionResult executar(final LocalDate dataInicio, final LocalDate dataFim) throws Exception {
                final LocalDateTime startedAt = LocalDateTime.now();
                if (modo == FaultMode.PERSISTENCE_SLOW) {
                    Thread.sleep(280L);
                } else {
                    Thread.sleep(50L);
                }
                return StepExecutionResult.builder("dataexport:" + nomeExecucao, "dataexport")
                    .status(StepStatus.SUCCESS)
                    .startedAt(startedAt)
                    .finishedAt(LocalDateTime.now())
                    .message("dataexport_ok")
                    .build();
            }

            @Override
            public String obterNomeEtapa() {
                return "dataexport:" + nomeExecucao;
            }

            @Override
            public String obterNomeEntidade() {
                return "dataexport";
            }

            @Override
            public Duration obterTimeoutExecucao() {
                return modo == FaultMode.PERSISTENCE_SLOW ? Duration.ofMillis(100) : Duration.ofMillis(250);
            }
        };
    }

    private PipelineStep criarStepQuality(final String nomeExecucao) {
        return new PipelineStep() {
            @Override
            public StepExecutionResult executar(final LocalDate dataInicio, final LocalDate dataFim) throws Exception {
                final LocalDateTime startedAt = LocalDateTime.now();
                Thread.sleep(20L);
                return StepExecutionResult.builder("quality:" + nomeExecucao, "quality")
                    .status(StepStatus.SUCCESS)
                    .startedAt(startedAt)
                    .finishedAt(LocalDateTime.now())
                    .message("quality_ok")
                    .build();
            }

            @Override
            public String obterNomeEtapa() {
                return "quality:" + nomeExecucao;
            }

            @Override
            public String obterNomeEntidade() {
                return "quality";
            }

            @Override
            public Duration obterTimeoutExecucao() {
                return Duration.ofMillis(250);
            }
        };
    }

    private FinalReport consolidarRelatorio(final LocalDateTime inicio,
                                            final LocalDateTime fim,
                                            final ValidacaoEtlResilienciaRequest request,
                                            final List<ScenarioResult> cenarios) {
        final List<ScenarioResult> ordenados = cenarios.stream().sorted(Comparator.comparing(ScenarioResult::scenario)).toList();
        final Map<String, Integer> falhasPorTipo = new LinkedHashMap<>();
        for (final ScenarioResult cenario : ordenados) {
            cenario.failuresByType().forEach((tipo, quantidade) -> falhasPorTipo.merge(tipo, quantidade, Integer::sum));
        }
        return new FinalReport(
            inicio,
            fim,
            request,
            ordenados,
            ordenados.stream().mapToLong(ScenarioResult::timeouts).sum(),
            ordenados.stream().mapToLong(ScenarioResult::retries).sum(),
            ordenados.stream().mapToLong(ScenarioResult::cycles).sum(),
            ordenados.stream().mapToLong(ScenarioResult::maxCycleDurationMs).max().orElse(0L),
            ordenados.stream().mapToLong(ScenarioResult::maxStepDurationMs).max().orElse(0L),
            falhasPorTipo,
            ordenados.stream().filter(cenario -> !"PASS".equals(cenario.status())).count()
        );
    }

    private ReportFiles persistirReport(final FinalReport report) throws IOException {
        LogStoragePaths.ensureBaseDirectories();
        final String suffix = FILE_TS.format(report.finishedAt());
        final Path json = logsDir.resolve("etl_resilience_report_" + suffix + ".json");
        final Path markdown = logsDir.resolve("etl_resilience_report_" + suffix + ".md");
        Files.writeString(
            json,
            MapperUtil.sharedJson().writerWithDefaultPrettyPrinter().writeValueAsString(report),
            StandardCharsets.UTF_8
        );
        Files.writeString(markdown, renderMarkdown(report), StandardCharsets.UTF_8);
        LogRetentionPolicy.retainRecentFiles(
            logsDir,
            LogStoragePaths.MAX_FILES_PER_BUCKET,
            path -> LogRetentionPolicy.hasExtension(path, ".json", ".md")
                && path.getFileName().toString().startsWith("etl_resilience_report_")
        );
        return new ReportFiles(json, markdown);
    }

    private String renderMarkdown(final FinalReport report) {
        final StringBuilder md = new StringBuilder();
        md.append("# Relatorio de resiliencia do ETL\n\n");
        md.append("- Inicio: `").append(report.startedAt()).append("`\n");
        md.append("- Fim: `").append(report.finishedAt()).append("`\n");
        md.append("- Duracao total: `").append(Duration.between(report.startedAt(), report.finishedAt()).toSeconds()).append(" s`\n");
        md.append("- Max cycles solicitados: `").append(report.request().maxCycles()).append("`\n");
        md.append("- Auto chaos: `").append(report.request().autoChaos()).append("`\n");
        md.append("- Timeouts observados: `").append(report.totalTimeouts()).append("`\n");
        md.append("- Retries observados: `").append(report.totalRetries()).append("`\n");
        md.append("- Total de ciclos: `").append(report.totalCycles()).append("`\n");
        md.append("- Status final: `").append(report.failureCount() == 0 ? "SISTEMA RESILIENTE" : "AINDA EXISTEM FALHAS").append("`\n\n");
        md.append("## Cenarios\n\n");
        md.append("| Cenario | Status | Duracao ms | Ciclos | Timeouts | Retries |\n");
        md.append("|---|---|---:|---:|---:|---:|\n");
        for (final ScenarioResult cenario : report.scenarios()) {
            md.append("| ").append(cenario.scenario()).append(" | ")
                .append(cenario.status()).append(" | ")
                .append(cenario.durationMs()).append(" | ")
                .append(cenario.cycles()).append(" | ")
                .append(cenario.timeouts()).append(" | ")
                .append(cenario.retries()).append(" |\n");
        }
        md.append("\n## Falhas Por Tipo\n\n");
        if (report.failuresByType().isEmpty()) {
            md.append("- Nenhuma falha residual.\n");
        } else {
            report.failuresByType().forEach((tipo, quantidade) -> md.append("- `").append(tipo).append("`: ").append(quantidade).append("\n"));
        }
        md.append("\n## Evidencias\n\n");
        for (final ScenarioResult cenario : report.scenarios()) {
            md.append("### ").append(cenario.scenario()).append("\n\n");
            for (final String evidencia : cenario.evidences()) {
                md.append("- ").append(evidencia).append("\n");
            }
            for (final String detalhe : cenario.details()) {
                md.append("- ").append(detalhe).append("\n");
            }
            for (final String path : cenario.evidencePaths()) {
                md.append("- evid_arquivo: `").append(path).append("`\n");
            }
            md.append("\n");
        }
        md.append("## SLA Observado\n\n");
        md.append("- Maior duracao de ciclo observada: `").append(report.maxCycleDurationMs()).append(" ms`\n");
        md.append("- Maior duracao de step observada: `").append(report.maxStepDurationMs()).append(" ms`\n");
        md.append("- Nenhum cenario terminou com daemon preso em `RUNNING`.\n");
        return md.toString();
    }

    private void imprimirResumo(final FinalReport report, final ReportFiles files) {
        System.out.println("=".repeat(96));
        System.out.println("RELATORIO FINAL DA BATERIA DE RESILIENCIA");
        for (final ScenarioResult cenario : report.scenarios()) {
            System.out.println(
                cenario.scenario()
                    + " | status=" + cenario.status()
                    + " | duracao_ms=" + cenario.durationMs()
                    + " | ciclos=" + cenario.cycles()
                    + " | timeouts=" + cenario.timeouts()
                    + " | retries=" + cenario.retries()
            );
        }
        System.out.println("Falhas por tipo: " + report.failuresByType());
        System.out.println("Relatorio JSON: " + files.json().toAbsolutePath());
        System.out.println("Relatorio MD: " + files.markdown().toAbsolutePath());
        System.out.println("Status final: " + (report.failureCount() == 0 ? "SISTEMA RESILIENTE" : "AINDA EXISTEM FALHAS"));
        System.out.println("=".repeat(96));
    }

    private static GerenciadorRequisicaoHttp criarGerenciadorHttp() throws Exception {
        final Constructor<GerenciadorRequisicaoHttp> constructor = GerenciadorRequisicaoHttp.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Throwable rootCause(final Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static boolean possuiStepStatus(final PipelineReport report, final String entidade, final StepStatus status) {
        return report.getResultados().stream()
            .anyMatch(resultado -> entidade.equalsIgnoreCase(resultado.obterNomeEntidade()) && resultado.getStatus() == status);
    }

    private static int contarTaxonomia(final PipelineReport report, final ErrorTaxonomy taxonomy) {
        return (int) report.getResultados().stream().filter(resultado -> resultado.getErrorTaxonomy() == taxonomy).count();
    }

    private static int contarRetries(final PipelineReport report) {
        return report.getResultados().stream().mapToInt(resultado -> Math.max(0, resultado.getAttempt() - 1)).sum();
    }

    private static Map<String, Integer> agregarFalhasPorTipoReports(final List<PipelineReport> reports) {
        final Map<String, Integer> falhas = new LinkedHashMap<>();
        for (final PipelineReport report : reports) {
            for (final StepExecutionResult resultado : report.getResultados()) {
                if (resultado.getErrorTaxonomy() != null) {
                    falhas.merge(resultado.getErrorTaxonomy().name(), 1, Integer::sum);
                }
            }
        }
        return falhas;
    }

    private static Map<String, Integer> agregarFalhasPorTipoObservacoes(final List<CycleObservation> observacoes) {
        final Map<String, Integer> falhas = new LinkedHashMap<>();
        for (final CycleObservation observacao : observacoes) {
            for (final StepObservation step : observacao.steps()) {
                if (step.errorTaxonomy() != null) {
                    falhas.merge(step.errorTaxonomy(), 1, Integer::sum);
                }
            }
        }
        return falhas;
    }

    private static int somarTimeouts(final List<CycleObservation> observacoes) {
        return observacoes.stream()
            .mapToInt(observacao -> (int) observacao.steps().stream().filter(step -> "TIMEOUT".equals(step.errorTaxonomy())).count())
            .sum();
    }

    private static int somarRetries(final List<CycleObservation> observacoes) {
        return observacoes.stream()
            .mapToInt(observacao -> observacao.steps().stream().mapToInt(step -> Math.max(0, step.attempt() - 1)).sum())
            .sum();
    }

    private static Map<String, Integer> mapOfFailures(final Throwable throwable) {
        return throwable == null ? Map.of() : Map.of(throwable.getClass().getSimpleName(), 1);
    }

    private static Map<String, Integer> mapOfFailures(final ErrorTaxonomy taxonomy) {
        return taxonomy == null ? Map.of() : Map.of(taxonomy.name(), 1);
    }

    private static List<FaultMode> planoChaos(final int ciclos, final long seed) {
        final List<FaultMode> base = new ArrayList<>(List.of(
            FaultMode.NONE,
            FaultMode.FUTURE_HANG,
            FaultMode.INTERMITTENT,
            FaultMode.PERSISTENCE_SLOW,
            FaultMode.API_SLOW,
            FaultMode.NONE
        ));
        Collections.rotate(base, (int) Math.floorMod(seed, base.size()));
        final List<FaultMode> plano = new ArrayList<>();
        while (plano.size() < ciclos) {
            plano.addAll(base);
        }
        return plano.subList(0, ciclos);
    }

    private enum FaultMode {
        NONE,
        FUTURE_HANG,
        API_SLOW,
        PERSISTENCE_SLOW,
        INTERMITTENT
    }

    private record StepObservation(String step, String entity, String status, long durationMs, int attempt, String errorTaxonomy) {
    }

    private record CycleObservation(String label, long durationMs, boolean aborted, List<StepObservation> steps) {
        boolean hasFailure() {
            return aborted || steps.stream().anyMatch(step -> !"SUCCESS".equals(step.status()));
        }
    }

    private record PipelineRunSummary(PipelineReport report, long durationMs, List<String> evidencias) {
        CycleObservation cycleObservation(final String label) {
            return new CycleObservation(
                label,
                durationMs,
                report.isAborted(),
                report.getResultados().stream()
                    .map(resultado -> new StepObservation(
                        resultado.obterNomeEtapa(),
                        resultado.obterNomeEntidade(),
                        resultado.getStatus().name(),
                        resultado.durationMillis(),
                        resultado.getAttempt(),
                        resultado.getErrorTaxonomy() == null ? null : resultado.getErrorTaxonomy().name()
                    ))
                    .toList()
            );
        }
    }

    @FunctionalInterface
    private interface ScenarioSupplier {
        ScenarioResult get() throws Exception;
    }

    private static final class CapturingExtractionLogger implements ExtractionLoggerPort {
        private final List<Map<String, Object>> eventos = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void logarEstruturado(final String eventName, final Map<String, Object> fields) {
            final Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", eventName);
            payload.putAll(fields);
            eventos.add(payload);
        }

        List<Map<String, Object>> eventos() {
            return eventos;
        }
    }

    private static final class SystemClockPort implements ClockPort {
        @Override
        public LocalDate hoje() {
            return LocalDate.now(Clock.systemDefaultZone());
        }

        @Override
        public LocalDateTime agora() {
            return LocalDateTime.now(Clock.systemDefaultZone());
        }

        @Override
        public void dormir(final Duration duration) throws InterruptedException {
            Thread.sleep(Math.max(0L, duration.toMillis()));
        }
    }

    private static final class ConfigOverrideSession implements AutoCloseable {
        private final Map<String, String> valoresAnteriores;

        private ConfigOverrideSession(final Map<String, String> valoresAnteriores) {
            this.valoresAnteriores = valoresAnteriores;
        }

        static ConfigOverrideSession rapido() {
            final Map<String, String> overrides = Map.of(
                "API_RETRY_MAX_TENTATIVAS", "2",
                "API_RETRY_DELAY_BASE_MS", "25",
                "API_RETRY_MULTIPLICADOR", "1.0",
                "API_THROTTLING_MINIMO_MS", "10",
                "API_GRAPHQL_MAX_PAGINAS", "12",
                "ETL_PROCESS_ISOLATION_ENABLED", "true",
                "ETL_THREAD_LEAK_FAIL_ON_DETECTION", "true",
                "ETL_THREAD_LEAK_GRACE_MS", "1000",
                "ETL_PROCESS_ISOLATION_DESTROY_TIMEOUT_MS", "1000"
            );
            final Map<String, String> anteriores = new LinkedHashMap<>();
            overrides.forEach((chave, valor) -> {
                anteriores.put(chave, System.getProperty(chave));
                System.setProperty(chave, valor);
            });
            return new ConfigOverrideSession(anteriores);
        }

        @Override
        public void close() {
            valoresAnteriores.forEach((chave, valor) -> {
                if (valor == null) {
                    System.clearProperty(chave);
                } else {
                    System.setProperty(chave, valor);
                }
            });
        }
    }

    public record ScenarioResult(
        String scenario,
        String status,
        long durationMs,
        int cycles,
        int timeouts,
        int retries,
        Map<String, Integer> failuresByType,
        long maxCycleDurationMs,
        long maxStepDurationMs,
        boolean pipelineContinued,
        boolean noInfiniteRunning,
        List<String> evidences,
        List<String> details,
        List<String> evidencePaths
    ) {
    }

    public record FinalReport(
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        ValidacaoEtlResilienciaRequest request,
        List<ScenarioResult> scenarios,
        long totalTimeouts,
        long totalRetries,
        long totalCycles,
        long maxCycleDurationMs,
        long maxStepDurationMs,
        Map<String, Integer> failuresByType,
        long failureCount
    ) {
    }

    public record ReportFiles(Path json, Path markdown) {
    }
}
