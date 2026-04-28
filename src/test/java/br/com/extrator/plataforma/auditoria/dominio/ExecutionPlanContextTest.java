package br.com.extrator.plataforma.auditoria.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExecutionPlanContextTest {

    @AfterEach
    void cleanup() {
        ExecutionPlanContext.clear();
        limparPropriedades("coletas");
    }

    @Test
    void deveExportarPlanoParaSystemPropertiesDoProcessoFilho() {
        final ExecutionWindowPlan plano = new ExecutionWindowPlan(
            LocalDate.of(2026, 4, 17),
            LocalDate.of(2026, 4, 23),
            LocalDateTime.of(2026, 4, 17, 0, 0),
            LocalDateTime.of(2026, 4, 23, 23, 59, 59)
        );

        ExecutionPlanContext.setPlanos(Map.of("coletas", plano));
        final Map<String, String> propriedades = ExecutionPlanContext.exportarSystemProperties();

        assertEquals("2026-04-17", propriedades.get("etl.execution.plan.coletas.consulta_data_inicio"));
        assertEquals("2026-04-23", propriedades.get("etl.execution.plan.coletas.consulta_data_fim"));
        assertEquals("2026-04-17T00:00", propriedades.get("etl.execution.plan.coletas.confirmacao_inicio"));
        assertEquals("2026-04-23T23:59:59", propriedades.get("etl.execution.plan.coletas.confirmacao_fim"));
    }

    @Test
    void deveLerPlanoViaSystemPropertiesQuandoThreadLocalNaoEstiverDisponivel() {
        System.setProperty("etl.execution.plan.coletas.consulta_data_inicio", "2026-04-17");
        System.setProperty("etl.execution.plan.coletas.consulta_data_fim", "2026-04-23");
        System.setProperty("etl.execution.plan.coletas.confirmacao_inicio", "2026-04-17T00:00");
        System.setProperty("etl.execution.plan.coletas.confirmacao_fim", "2026-04-23T23:59:59");

        final var planoOpt = ExecutionPlanContext.getPlano("coletas");

        assertTrue(planoOpt.isPresent());
        assertEquals(LocalDate.of(2026, 4, 17), planoOpt.get().consultaDataInicio());
        assertEquals(LocalDate.of(2026, 4, 23), planoOpt.get().consultaDataFim());
        assertEquals(LocalDateTime.of(2026, 4, 17, 0, 0), planoOpt.get().confirmacaoInicio());
        assertEquals(LocalDateTime.of(2026, 4, 23, 23, 59, 59), planoOpt.get().confirmacaoFim());
    }

    @Test
    void deveDisponibilizarPlanoParaOutraThreadDaMesmaExecucao() throws InterruptedException {
        final ExecutionWindowPlan plano = new ExecutionWindowPlan(
            LocalDate.of(2026, 4, 22),
            LocalDate.of(2026, 4, 23),
            LocalDateTime.of(2026, 4, 22, 0, 0),
            LocalDateTime.of(2026, 4, 23, 23, 59, 59)
        );
        ExecutionPlanContext.setPlanos(Map.of("faturas_graphql", plano));

        final AtomicReference<ExecutionWindowPlan> capturado = new AtomicReference<>();
        final Thread worker = new Thread(() ->
            capturado.set(ExecutionPlanContext.getPlano("faturas_graphql").orElse(null))
        );
        worker.start();
        worker.join();

        assertEquals(plano, capturado.get());
    }

    private void limparPropriedades(final String entidade) {
        final String prefix = "etl.execution.plan." + entidade + ".";
        System.clearProperty(prefix + "consulta_data_inicio");
        System.clearProperty(prefix + "consulta_data_fim");
        System.clearProperty(prefix + "confirmacao_inicio");
        System.clearProperty(prefix + "confirmacao_fim");
    }
}
