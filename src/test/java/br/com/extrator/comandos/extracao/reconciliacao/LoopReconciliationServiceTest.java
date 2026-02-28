/* ==[DOC-FILE]===============================================================
Arquivo : src/test/java/br/com/extrator/comandos/extracao/reconciliacao/LoopReconciliationServiceTest.java
Classe  : LoopReconciliationServiceTest (class)
Pacote  : br.com.extrator.comandos.extracao.reconciliacao
Modulo  : Teste automatizado
Papel   : Valida comportamento da unidade LoopReconciliationService.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Prepara cenarios e dados de teste.
2) Executa casos para validar comportamento de LoopReconciliationService.
3) Assegura regressao controlada nas regras principais.

Estrutura interna:
Metodos principais:
- setUp(): ajusta valor em estado interno.
- deveExecutarReconciliacaoDiariaDeOntem(): verifica comportamento esperado em teste automatizado.
- deveManterPendenciaQuandoReconciliacaoFalhaEExecutarNaRetentativa(): verifica comportamento esperado em teste automatizado.
- deveRespeitarLimiteMaximoPorCiclo(): verifica comportamento esperado em teste automatizado.
- deveAgendarDiasRetroativosEmFalhaDoCiclo(): verifica comportamento esperado em teste automatizado.
- deveRetornarResumoInativoQuandoFeatureDesativada(): verifica comportamento esperado em teste automatizado.
- salvarEstadoInicial(...2 args): persiste dados em armazenamento.
- carregarEstado(...1 args): realiza operacao relacionada a "carregar estado".
Atributos-chave:
- HOJE: campo de estado para "hoje".
- ONTEM: campo de estado para "ontem".
- stateFile: campo de estado para "state file".
- clock: campo de estado para "clock".
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.extracao.reconciliacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoopReconciliationServiceTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 2, 20);
    private static final LocalDate ONTEM = HOJE.minusDays(1);

    @TempDir
    Path tempDir;

    private Path stateFile;
    private Clock clock;

    @BeforeEach
    void setUp() {
        this.stateFile = tempDir.resolve("loop_reconciliation.state");
        this.clock = Clock.fixed(
            LocalDateTime.of(2026, 2, 20, 10, 0).atZone(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault()
        );
    }

    @Test
    void deveExecutarReconciliacaoDiariaDeOntem() {
        final List<LocalDate> datasExecutadas = new ArrayList<>();
        final LoopReconciliationService service = new LoopReconciliationService(
            stateFile,
            clock,
            true,
            3,
            1,
            (data, incluirFaturasGraphQL) -> datasExecutadas.add(data)
        );

        final var resumo = service.processarPosCiclo(
            LocalDateTime.of(2026, 2, 20, 9, 0),
            LocalDateTime.of(2026, 2, 20, 9, 30),
            true,
            true
        );

        assertTrue(resumo.isAtivo());
        assertEquals(1, resumo.getReconciliacoesExecutadas());
        assertEquals(0, resumo.getFalhas());
        assertEquals(List.of(ONTEM), datasExecutadas);
        assertTrue(resumo.getPendenciasRestantes().isEmpty());
        assertTrue(resumo.isAgendouReconciliacaoDiaria());
        assertFalse(resumo.isPendenciaPorFalha());

        final Properties estado = carregarEstado(stateFile);
        assertEquals(ONTEM.toString(), estado.getProperty("last_daily_scheduled_date"));
        assertEquals(ONTEM.toString(), estado.getProperty("last_successful_reconciliation_date"));
        assertEquals("", estado.getProperty("pending_dates"));
    }

    @Test
    void deveManterPendenciaQuandoReconciliacaoFalhaEExecutarNaRetentativa() {
        salvarEstadoInicial(ONTEM.toString(), "");

        final AtomicInteger tentativas = new AtomicInteger(0);
        final LoopReconciliationService service = new LoopReconciliationService(
            stateFile,
            clock,
            true,
            1,
            0,
            (data, incluirFaturasGraphQL) -> {
                if (tentativas.incrementAndGet() == 1) {
                    throw new IllegalStateException("falha simulada");
                }
            }
        );

        final var primeiroResumo = service.processarPosCiclo(
            LocalDateTime.of(2026, 2, 20, 10, 0),
            LocalDateTime.of(2026, 2, 20, 10, 30),
            false,
            true
        );

        assertEquals(0, primeiroResumo.getReconciliacoesExecutadas());
        assertEquals(1, primeiroResumo.getFalhas());
        assertEquals(List.of(HOJE), primeiroResumo.getPendenciasRestantes());
        assertTrue(primeiroResumo.isPendenciaPorFalha());

        final var segundoResumo = service.processarPosCiclo(
            LocalDateTime.of(2026, 2, 20, 11, 0),
            LocalDateTime.of(2026, 2, 20, 11, 30),
            true,
            true
        );

        assertEquals(1, segundoResumo.getReconciliacoesExecutadas());
        assertEquals(0, segundoResumo.getFalhas());
        assertTrue(segundoResumo.getPendenciasRestantes().isEmpty());

        final Properties estado = carregarEstado(stateFile);
        assertEquals("", estado.getProperty("pending_dates"));
        assertEquals(HOJE.toString(), estado.getProperty("last_successful_reconciliation_date"));
    }

    @Test
    void deveRespeitarLimiteMaximoPorCiclo() {
        salvarEstadoInicial(ONTEM.toString(), "2026-02-17,2026-02-18,2026-02-19");

        final List<LocalDate> datasExecutadas = new ArrayList<>();
        final LoopReconciliationService service = new LoopReconciliationService(
            stateFile,
            clock,
            true,
            2,
            0,
            (data, incluirFaturasGraphQL) -> datasExecutadas.add(data)
        );

        final var resumo = service.processarPosCiclo(
            LocalDateTime.of(2026, 2, 20, 12, 0),
            LocalDateTime.of(2026, 2, 20, 12, 30),
            true,
            false
        );

        assertEquals(List.of(LocalDate.of(2026, 2, 17), LocalDate.of(2026, 2, 18)), datasExecutadas);
        assertEquals(2, resumo.getReconciliacoesExecutadas());
        assertEquals(0, resumo.getFalhas());
        assertEquals(List.of(LocalDate.of(2026, 2, 19)), resumo.getPendenciasRestantes());
    }

    @Test
    void deveAgendarDiasRetroativosEmFalhaDoCiclo() {
        salvarEstadoInicial(ONTEM.toString(), "");

        final LoopReconciliationService service = new LoopReconciliationService(
            stateFile,
            clock,
            true,
            1,
            2,
            (data, incluirFaturasGraphQL) -> {
                throw new IllegalStateException("falha simulada");
            }
        );

        final var resumo = service.processarPosCiclo(
            LocalDateTime.of(2026, 2, 20, 0, 5),
            LocalDateTime.of(2026, 2, 20, 0, 35),
            false,
            true
        );

        assertEquals(0, resumo.getReconciliacoesExecutadas());
        assertEquals(1, resumo.getFalhas());
        assertTrue(resumo.isPendenciaPorFalha());
        assertEquals(
            List.of(LocalDate.of(2026, 2, 18), LocalDate.of(2026, 2, 19), LocalDate.of(2026, 2, 20)),
            resumo.getPendenciasRestantes()
        );
    }

    @Test
    void deveRetornarResumoInativoQuandoFeatureDesativada() {
        final LoopReconciliationService service = new LoopReconciliationService(
            stateFile,
            clock,
            false,
            2,
            1,
            (data, incluirFaturasGraphQL) -> {
                throw new IllegalStateException("nao deveria executar");
            }
        );

        final var resumo = service.processarPosCiclo(
            LocalDateTime.of(2026, 2, 20, 9, 0),
            LocalDateTime.of(2026, 2, 20, 9, 30),
            false,
            true
        );

        assertFalse(resumo.isAtivo());
        assertEquals(0, resumo.getReconciliacoesExecutadas());
        assertEquals(0, resumo.getFalhas());
        assertTrue(resumo.getPendenciasRestantes().isEmpty());
        assertFalse(Files.exists(stateFile));
    }

    private void salvarEstadoInicial(final String lastDailyScheduledDate, final String pendingDates) {
        final Properties properties = new Properties();
        properties.setProperty("last_daily_scheduled_date", lastDailyScheduledDate == null ? "" : lastDailyScheduledDate);
        properties.setProperty("last_successful_reconciliation_date", "");
        properties.setProperty("pending_dates", pendingDates == null ? "" : pendingDates);
        properties.setProperty("last_error", "");
        properties.setProperty("updated_at", "2026-02-20T00:00:00");

        try {
            final Path parent = stateFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(stateFile)) {
                properties.store(out, "test-state");
            }
        } catch (final IOException e) {
            throw new RuntimeException("Falha ao preparar estado inicial para teste", e);
        }
    }

    private Properties carregarEstado(final Path path) {
        final Properties properties = new Properties();
        try (var in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (final IOException e) {
            throw new RuntimeException("Falha ao carregar estado para teste", e);
        }
        return properties;
    }
}
