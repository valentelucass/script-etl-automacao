package br.com.extrator.comandos.cli.extracao.daemon;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;

import br.com.extrator.comandos.cli.extracao.reconciliacao.LoopReconciliationService;

/**
 * Harness utilitario para executar cenarios de resiliencia sobre o loop daemon
 * sem tocar no estado operacional padrao da aplicacao.
 */
public final class DaemonResilienceHarness {

    public record ProbeResult(
        Path baseDir,
        Path stateFile,
        Path historyDir,
        Path cyclesDir,
        long durationMs,
        Properties finalState,
        int cycleHistoryEntries,
        List<Path> cycleLogs
    ) {
    }

    private DaemonResilienceHarness() {
    }

    public static ProbeResult executar(final Path baseDir,
                                       final LoopDaemonRunHandler.FluxoExecutor fluxoExecutor,
                                       final LoopDaemonRunHandler.CycleWaitStrategy waitStrategy,
                                       final Function<Boolean, Duration> cycleTimeoutProvider) throws Exception {
        Files.createDirectories(baseDir);

        final Path stateFile = baseDir.resolve("loop_daemon.state");
        final Path pidFile = baseDir.resolve("loop_daemon.pid");
        final Path stopFile = baseDir.resolve("loop_daemon.stop");
        final Path forceRunFile = baseDir.resolve("loop_daemon.force_run");
        final Path cyclesDir = baseDir.resolve("ciclos");
        final Path historyDir = baseDir.resolve("history");
        final Path reconciliacaoDir = baseDir.resolve("reconciliacao");
        final Path reconciliationState = baseDir.resolve("loop_reconciliation.state");

        final DaemonStateStore stateStore = new DaemonStateStore(
            baseDir,
            stateFile,
            pidFile,
            stopFile,
            forceRunFile
        );
        final DaemonHistoryWriter historyWriter = new DaemonHistoryWriter(
            baseDir,
            cyclesDir,
            historyDir,
            reconciliacaoDir,
            "extrator.loop.reconciliacao.history.dir",
            false
        );
        final LoopReconciliationService reconciliationService = new LoopReconciliationService(
            reconciliationState,
            Clock.systemDefaultZone(),
            false,
            1,
            0,
            (data, api, entidade, incluirFaturasGraphQL) -> {
                // Reconciliacao desligada no harness.
            }
        );
        final LoopDaemonRunHandler handler = new LoopDaemonRunHandler(
            stateStore,
            historyWriter,
            fluxoExecutor,
            reconciliationService::processarPosCiclo,
            waitStrategy,
            cicloLog -> () -> {
            },
            () -> 7777L,
            30L,
            false,
            cycleTimeoutProvider
        );

        final long inicio = System.nanoTime();
        handler.executar(false);
        final long duracaoMs = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

        return new ProbeResult(
            baseDir,
            stateFile,
            historyDir,
            cyclesDir,
            duracaoMs,
            stateStore.loadState(),
            contarEntradasHistorico(historyDir),
            listarLogsCiclo(cyclesDir)
        );
    }

    private static int contarEntradasHistorico(final Path historyDir) throws Exception {
        if (!Files.exists(historyDir)) {
            return 0;
        }
        try (var stream = Files.walk(historyDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".csv"))
                .mapToInt(DaemonResilienceHarness::contarLinhasSemCabecalho)
                .sum();
        }
    }

    private static int contarLinhasSemCabecalho(final Path path) {
        try {
            final List<String> linhas = Files.readAllLines(path, StandardCharsets.UTF_8);
            return Math.max(0, linhas.size() - 1);
        } catch (final Exception e) {
            return 0;
        }
    }

    private static List<Path> listarLogsCiclo(final Path cyclesDir) throws Exception {
        if (!Files.exists(cyclesDir)) {
            return List.of();
        }
        try (var stream = Files.walk(cyclesDir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".log"))
                .sorted(Comparator.naturalOrder())
                .toList();
        }
    }
}
