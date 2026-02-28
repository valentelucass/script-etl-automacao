/* ==[DOC-FILE]===============================================================
Arquivo : src/test/java/br/com/extrator/comandos/extracao/reconciliacao/LoopReconciliationServiceStressTest.java
Classe  : LoopReconciliationServiceStressTest (class)
Pacote  : br.com.extrator.comandos.extracao.reconciliacao
Modulo  : Teste automatizado
Papel   : Valida comportamento da unidade LoopReconciliationServiceStress.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Prepara cenarios e dados de teste.
2) Executa casos para validar comportamento de LoopReconciliationServiceStress.
3) Assegura regressao controlada nas regras principais.

Estrutura interna:
Metodos principais:
- setUp(): ajusta valor em estado interno.
- deveDrenarBacklogGrandeComFalhasIntermitentesSemPerda(): verifica comportamento esperado em teste automatizado.
- deveSuportarMuitosDiasComReconciliacaoDiariaSemAcumularPendencias(): verifica comportamento esperado em teste automatizado.
- intervalo(...2 args): realiza operacao relacionada a "intervalo".
- salvarEstadoInicial(...2 args): persiste dados em armazenamento.
- carregarEstado(...1 args): realiza operacao relacionada a "carregar estado".
Atributos-chave:
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoopReconciliationServiceStressTest {

    @TempDir
    Path tempDir;

    private Path stateFile;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        this.stateFile = tempDir.resolve("loop_reconciliation_stress.state");
        this.clock = new MutableClock(
            LocalDateTime.of(2026, 2, 20, 10, 0).atZone(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault()
        );
    }

    @Test
    void deveDrenarBacklogGrandeComFalhasIntermitentesSemPerda() {
        final LocalDate hoje = LocalDate.of(2026, 2, 20);
        final LocalDate ontem = hoje.minusDays(1);
        final List<LocalDate> backlog = intervalo(LocalDate.of(2025, 1, 1), ontem);

        salvarEstadoInicial(ontem, backlog);

        final Set<LocalDate> concluidas = new LinkedHashSet<>();
        final Map<LocalDate, AtomicInteger> tentativasPorData = new HashMap<>();
        final AtomicInteger falhasControladas = new AtomicInteger(0);
        final AtomicInteger totalExecucoes = new AtomicInteger(0);

        final LoopReconciliationService service = new LoopReconciliationService(
            stateFile,
            clock,
            true,
            15,
            1,
            (data, incluirFaturasGraphQL) -> {
                totalExecucoes.incrementAndGet();
                final int tentativaAtual = tentativasPorData.computeIfAbsent(data, d -> new AtomicInteger(0)).incrementAndGet();

                // Falha apenas na primeira tentativa de um subconjunto previsivel de datas.
                if (tentativaAtual == 1 && data.getDayOfMonth() % 7 == 0) {
                    falhasControladas.incrementAndGet();
                    throw new IllegalStateException("falha intermitente controlada");
                }
                concluidas.add(data);
            }
        );

        int ciclos = 0;
        while (ciclos < 1200) {
            final var resumo = service.processarPosCiclo(
                LocalDateTime.of(2026, 2, 20, 10, 0),
                LocalDateTime.of(2026, 2, 20, 10, 30),
                true,
                true
            );
            if (resumo.getPendenciasRestantes().isEmpty()) {
                break;
            }
            ciclos++;
        }

        assertTrue(ciclos < 1200, "Backlog nao foi drenado dentro do limite de ciclos");
        assertEquals(backlog.size(), concluidas.size(), "Todas as datas do backlog devem reconciliar com sucesso");
        assertTrue(falhasControladas.get() > 0, "Teste precisa executar falhas controladas para validar retentativa");
        assertTrue(totalExecucoes.get() > backlog.size(), "Deve haver mais execucoes do que datas por causa das retentativas");

        final Properties estadoFinal = carregarEstado(stateFile);
        assertEquals("", estadoFinal.getProperty("pending_dates", ""));
        assertEquals(ontem.toString(), estadoFinal.getProperty("last_successful_reconciliation_date"));
    }

    @Test
    void deveSuportarMuitosDiasComReconciliacaoDiariaSemAcumularPendencias() {
        final LocalDate dataInicial = LocalDate.of(2026, 3, 1);
        clock.setInstant(dataInicial.atTime(1, 0).atZone(ZoneId.systemDefault()).toInstant());

        final Set<LocalDate> conciliadas = new LinkedHashSet<>();
        final LoopReconciliationService service = new LoopReconciliationService(
            stateFile,
            clock,
            true,
            5,
            0,
            (data, incluirFaturasGraphQL) -> conciliadas.add(data)
        );

        for (int dia = 0; dia < 90; dia++) {
            final LocalDate dataAtual = dataInicial.plusDays(dia);
            clock.setInstant(dataAtual.atTime(1, 0).atZone(ZoneId.systemDefault()).toInstant());

            final var resumo = service.processarPosCiclo(
                dataAtual.atStartOfDay(),
                dataAtual.atTime(0, 30),
                true,
                true
            );

            assertTrue(resumo.isAtivo());
            assertEquals(0, resumo.getFalhas());
            assertTrue(
                resumo.getPendenciasRestantes().size() <= 1,
                "Pendencias nao devem acumular em operacao normal diaria"
            );
        }

        // Em 90 dias, o processo deve ter reconciliado ao menos 89 "D-1" distintos.
        assertTrue(conciliadas.size() >= 89);

        final Properties estadoFinal = carregarEstado(stateFile);
        assertFalse(estadoFinal.getProperty("last_daily_scheduled_date", "").isBlank());
        assertFalse(estadoFinal.getProperty("last_successful_reconciliation_date", "").isBlank());
    }

    private List<LocalDate> intervalo(final LocalDate inicio, final LocalDate fim) {
        final List<LocalDate> datas = new ArrayList<>();
        LocalDate atual = inicio;
        while (!atual.isAfter(fim)) {
            datas.add(atual);
            atual = atual.plusDays(1);
        }
        return datas;
    }

    private void salvarEstadoInicial(final LocalDate lastDailyScheduledDate, final List<LocalDate> pendingDates) {
        final Properties properties = new Properties();
        properties.setProperty("last_daily_scheduled_date", lastDailyScheduledDate == null ? "" : lastDailyScheduledDate.toString());
        properties.setProperty("last_successful_reconciliation_date", "");
        properties.setProperty(
            "pending_dates",
            pendingDates == null
                ? ""
                : pendingDates.stream().map(LocalDate::toString).reduce((a, b) -> a + "," + b).orElse("")
        );
        properties.setProperty("last_error", "");
        properties.setProperty("updated_at", "2026-02-20T00:00:00");

        try {
            final Path parent = stateFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(stateFile)) {
                properties.store(out, "stress-test-state");
            }
        } catch (final IOException e) {
            throw new RuntimeException("Falha ao preparar estado de stress", e);
        }
    }

    private Properties carregarEstado(final Path path) {
        final Properties properties = new Properties();
        try (var in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (final IOException e) {
            throw new RuntimeException("Falha ao carregar estado final de stress", e);
        }
        return properties;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(final Instant instant, final ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void setInstant(final Instant instant) {
            this.instant = instant;
        }
    }
}
