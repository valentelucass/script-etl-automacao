/* ==[DOC-FILE]===============================================================
Arquivo : src/test/java/br/com/extrator/comandos/extracao/LoopDaemonComandoReconciliacaoHistoryTest.java
Classe  : LoopDaemonComandoReconciliacaoHistoryTest (class)
Pacote  : br.com.extrator.comandos.extracao
Modulo  : Teste automatizado
Papel   : Valida comportamento da unidade LoopDaemonComandoReconciliacaoHistory.

Conecta com:
- DaemonHistoryWriter (comandos.extracao.daemon)
- LoopReconciliationService (comandos.extracao.reconciliacao)
- ReconciliationSummary (comandos.extracao.reconciliacao.LoopReconciliationService)

Fluxo geral:
1) Prepara cenarios e dados de teste.
2) Executa casos para validar comportamento de LoopDaemonComandoReconciliacaoHistory.
3) Assegura regressao controlada nas regras principais.

Estrutura interna:
Metodos principais:
- Metodos nao mapeados automaticamente; consulte a implementacao abaixo.
Atributos-chave:
- HISTORY_OVERRIDE_KEY: campo de estado para "history override key".
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.extracao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import br.com.extrator.comandos.extracao.daemon.DaemonHistoryWriter;
import br.com.extrator.comandos.extracao.reconciliacao.LoopReconciliationService;
import br.com.extrator.comandos.extracao.reconciliacao.LoopReconciliationService.ReconciliationSummary;

class LoopDaemonComandoReconciliacaoHistoryTest {

    private static final String HISTORY_OVERRIDE_KEY = "extrator.loop.reconciliacao.history.dir";

    @TempDir
    Path tempDir;

    @Test
    void deveRegistrarHistoricoCsvDeReconciliacao() throws Exception {
        final LocalDateTime inicio = LocalDateTime.of(2026, 2, 20, 11, 0);
        final LocalDateTime fimExtracao = LocalDateTime.of(2026, 2, 20, 11, 30);

        final Path pastaReconciliacao = tempDir.resolve("reconciliacao-history");
        Files.createDirectories(pastaReconciliacao);
        final Path csvEsperado = pastaReconciliacao.resolve("reconciliacao_daemon_2026_02.csv");
        final String overrideAnterior = System.getProperty(HISTORY_OVERRIDE_KEY);
        System.setProperty(HISTORY_OVERRIDE_KEY, pastaReconciliacao.toString());

        final Path cicloLog = tempDir.resolve("ciclo.log");
        Files.writeString(cicloLog, "log-ciclo-teste", StandardCharsets.UTF_8);

        try {
            final LoopReconciliationService service = new LoopReconciliationService(
                tempDir.resolve("state.properties"),
                Clock.fixed(inicio.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()),
                true,
                2,
                0,
                (data, incluirFaturasGraphQL) -> {
                    // no-op
                }
            );
            final ReconciliationSummary resumo = service.processarPosCiclo(inicio, fimExtracao, true, true);

            final DaemonHistoryWriter writer = new DaemonHistoryWriter(
                tempDir,
                tempDir.resolve("ciclos"),
                tempDir.resolve("history"),
                tempDir.resolve("default-reconciliacao"),
                HISTORY_OVERRIDE_KEY
            );
            writer.ensureDirectories();
            writer.registerReconciliationHistory(inicio, fimExtracao, true, resumo, cicloLog);

            assertTrue(Files.exists(csvEsperado), "CSV de reconciliacao deve ser criado");
            final List<String> linhas = Files.readAllLines(csvEsperado, StandardCharsets.UTF_8);
            assertFalse(linhas.isEmpty(), "CSV deve conter pelo menos cabecalho");
            assertTrue(
                linhas.stream().anyMatch(l -> l.contains("STATUS_RECONCILIACAO") && l.contains("EXECUTADAS")),
                "CSV deve conter cabecalho de reconciliacao"
            );
            assertTrue(
                linhas.stream().anyMatch(l -> l.contains(";EXECUTADA;") && l.contains(";true;")),
                "CSV deve conter registro com status da reconciliacao executada"
            );
        } finally {
            if (overrideAnterior == null) {
                System.clearProperty(HISTORY_OVERRIDE_KEY);
            } else {
                System.setProperty(HISTORY_OVERRIDE_KEY, overrideAnterior);
            }
        }
    }
}
