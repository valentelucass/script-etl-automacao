package br.com.extrator.comandos.extracao.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.com.extrator.comandos.extracao.reconciliacao.LoopReconciliationService;
import br.com.extrator.comandos.extracao.reconciliacao.LoopReconciliationService.ReconciliationSummary;

class LoopDaemonRunHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void deveExecutarNovoCicloQuandoReceberForceRun() throws Exception {
        final DaemonStateStore stateStore = novoStore();
        final DaemonHistoryWriter historyWriter = novoHistoryWriter();
        final AtomicInteger ciclosExecutados = new AtomicInteger();
        final AtomicInteger chamadaEspera = new AtomicInteger();

        final LoopDaemonRunHandler handler = new LoopDaemonRunHandler(
            stateStore,
            historyWriter,
            incluirFaturas -> ciclosExecutados.incrementAndGet(),
            (inicio, fimExtracao, sucesso, incluirFaturas) -> null,
            (proximoCiclo, store) -> chamadaEspera.getAndIncrement() == 0
                ? LoopDaemonRunHandler.WaitResult.FORCE_RUN_REQUESTED
                : LoopDaemonRunHandler.WaitResult.STOP_REQUESTED,
            cicloLog -> () -> { },
            () -> 9876L,
            30L,
            false
        );

        handler.executar(true);

        assertEquals(2, ciclosExecutados.get(), "Force run deve disparar um segundo ciclo imediato");
        assertFalse(Files.exists(stateStore.getPidFile()), "PID deve ser limpo ao finalizar");
        assertFalse(Files.exists(stateStore.getStopFile()), "Stop file deve ser limpo ao finalizar");
        assertFalse(Files.exists(stateStore.getForceRunFile()), "Force run file deve ser limpo ao finalizar");
        assertEquals("STOPPED", stateStore.loadState().getProperty("status"), "Estado final deve ser STOPPED");
    }

    @Test
    void deveTratarFalhaDeIntegridadeSemInterromperLoop() throws Exception {
        final DaemonStateStore stateStore = novoStore();
        final DaemonHistoryWriter historyWriter = novoHistoryWriter();

        final LoopDaemonRunHandler handler = new LoopDaemonRunHandler(
            stateStore,
            historyWriter,
            incluirFaturas -> {
                throw new RuntimeException(LoopDaemonHandlerSupport.MENSAGEM_FALHA_INTEGRIDADE + " em teste");
            },
            (inicio, fimExtracao, sucesso, incluirFaturas) -> null,
            (proximoCiclo, store) -> LoopDaemonRunHandler.WaitResult.STOP_REQUESTED,
            cicloLog -> () -> { },
            () -> 4321L,
            30L,
            false
        );

        handler.executar(true);

        final Path logCiclo = localizarPrimeiroLogCiclo(tempDir.resolve("daemon").resolve("ciclos"));
        final String conteudo = Files.readString(logCiclo, StandardCharsets.UTF_8);
        assertTrue(
            conteudo.contains("Ciclo concluido com alerta de integridade"),
            "Resumo do ciclo deve registrar alerta de integridade"
        );
    }

    @Test
    void deveClassificarStatusComFalhaDeReconciliacao() {
        final LoopReconciliationService service = new LoopReconciliationService(
            tempDir.resolve("reconciliacao.state"),
            Clock.systemDefaultZone(),
            true,
            1,
            0,
            (data, incluirFaturas) -> {
                throw new IllegalStateException("falha simulada");
            }
        );

        final ReconciliationSummary resumoComFalha = service.processarPosCiclo(
            LocalDateTime.now().minusMinutes(10),
            LocalDateTime.now(),
            true,
            true
        );

        assertEquals(
            "WAITING_NEXT_CYCLE_WITH_ERROR",
            LoopDaemonRunHandler.determinarStatusDaemon(true, resumoComFalha)
        );
        assertEquals("WAITING_NEXT_CYCLE", LoopDaemonRunHandler.determinarStatusDaemon(true, null));
    }

    private DaemonStateStore novoStore() {
        return new DaemonStateStore(
            tempDir.resolve("daemon"),
            tempDir.resolve("daemon").resolve("loop_daemon.state"),
            tempDir.resolve("daemon").resolve("loop_daemon.pid"),
            tempDir.resolve("daemon").resolve("loop_daemon.stop"),
            tempDir.resolve("daemon").resolve("loop_daemon.force_run")
        );
    }

    private DaemonHistoryWriter novoHistoryWriter() {
        return new DaemonHistoryWriter(
            tempDir.resolve("daemon"),
            tempDir.resolve("daemon").resolve("ciclos"),
            tempDir.resolve("daemon").resolve("history"),
            tempDir.resolve("daemon").resolve("reconciliacao"),
            "extrator.loop.reconciliacao.history.dir"
        );
    }

    private Path localizarPrimeiroLogCiclo(final Path cyclesDir) throws Exception {
        try (var stream = Files.walk(cyclesDir)) {
            final List<Path> logs = stream
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".log"))
                .toList();
            assertFalse(logs.isEmpty(), "Deve existir pelo menos um log de ciclo");
            return logs.get(0);
        }
    }
}
