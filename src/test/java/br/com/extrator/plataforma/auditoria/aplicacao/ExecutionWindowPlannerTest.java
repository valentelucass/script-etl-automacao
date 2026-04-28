package br.com.extrator.plataforma.auditoria.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.extrator.aplicacao.portas.ExecutionAuditPort;
import br.com.extrator.plataforma.auditoria.dominio.ExecutionAuditRecord;
import br.com.extrator.plataforma.auditoria.dominio.ExecutionWindowPlan;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

class ExecutionWindowPlannerTest {

    @Test
    void devePlanejarJanelaComOverlapDe48hParaEntidadesCriticas() {
        final ExecutionWindowPlanner planner = new ExecutionWindowPlanner(new StubExecutionAuditPort());

        final ExecutionWindowPlan planoColetas = planner.planejarEntidade(
            ConstantesEntidades.COLETAS,
            LocalDate.of(2026, 3, 25)
        );
        final ExecutionWindowPlan planoManifestos = planner.planejarEntidade(
            ConstantesEntidades.MANIFESTOS,
            LocalDate.of(2026, 3, 25)
        );
        final ExecutionWindowPlan planoCotacoes = planner.planejarEntidade(
            ConstantesEntidades.COTACOES,
            LocalDate.of(2026, 3, 25)
        );

        assertEquals(LocalDate.of(2026, 3, 19), planoColetas.consultaDataInicio());
        assertEquals(LocalDate.of(2026, 3, 25), planoColetas.consultaDataFim());
        assertEquals(LocalDate.of(2026, 3, 19), planoManifestos.consultaDataInicio());
        assertEquals(LocalDate.of(2026, 3, 25), planoManifestos.consultaDataFim());
        assertEquals(LocalDate.of(2026, 3, 19), planoCotacoes.consultaDataInicio());
    }

    @Test
    void deveUsarWatermarkConfirmadoComoInicioDeConfirmacao() {
        final LocalDateTime watermark = LocalDateTime.of(2026, 3, 24, 5, 30);
        final ExecutionWindowPlanner planner = new ExecutionWindowPlanner(new StubExecutionAuditPort(watermark));

        final ExecutionWindowPlan plano = planner.planejarEntidade(
            ConstantesEntidades.FRETES,
            LocalDate.of(2026, 3, 25)
        );

        assertEquals(watermark, plano.confirmacaoInicio());
        assertEquals(LocalDate.of(2026, 3, 19), plano.consultaDataInicio());
        assertEquals(LocalDateTime.of(2026, 3, 25, LocalTime.MAX.getHour(), LocalTime.MAX.getMinute(), LocalTime.MAX.getSecond(), LocalTime.MAX.getNano()), plano.confirmacaoFim());
    }

    @Test
    void naoDeveIncluirFaturasGraphqlQuandoFlagEstiverDesabilitada() {
        final ExecutionWindowPlanner planner = new ExecutionWindowPlanner(new StubExecutionAuditPort());

        final var planos = planner.planejarFluxoCompleto(LocalDate.of(2026, 3, 25), false);

        assertFalse(planos.containsKey(ConstantesEntidades.FATURAS_GRAPHQL));
    }

    @Test
    void deveIncluirInventarioESinistrosNoFluxoCompleto() {
        final ExecutionWindowPlanner planner = new ExecutionWindowPlanner(new StubExecutionAuditPort());

        final var planos = planner.planejarFluxoCompleto(LocalDate.of(2026, 3, 25), false);

        assertTrue(planos.containsKey(ConstantesEntidades.INVENTARIO));
        assertTrue(planos.containsKey(ConstantesEntidades.SINISTROS));
        assertTrue(planos.containsKey(ConstantesEntidades.CONTAS_A_PAGAR));
        assertTrue(planos.containsKey(ConstantesEntidades.FATURAS_POR_CLIENTE));
    }

    @Test
    void deveUsarWatermarkMaisAntigoQueReplayComoInicioDaConsulta() {
        final LocalDateTime watermark = LocalDateTime.of(2026, 3, 10, 5, 30);
        final ExecutionWindowPlanner planner = new ExecutionWindowPlanner(new StubExecutionAuditPort(watermark));

        final ExecutionWindowPlan plano = planner.planejarEntidade(
            ConstantesEntidades.COLETAS,
            LocalDate.of(2026, 3, 25)
        );

        assertEquals(LocalDate.of(2026, 3, 10), plano.consultaDataInicio());
        assertEquals(watermark, plano.confirmacaoInicio());
    }

    @Test
    void deveLimitarConsultaDeFaturasGraphqlMesmoComWatermarkAntigo() {
        final LocalDateTime watermark = LocalDateTime.of(2026, 3, 1, 5, 30);
        final ExecutionWindowPlanner planner = new ExecutionWindowPlanner(new StubExecutionAuditPort(watermark));

        final ExecutionWindowPlan plano = planner.planejarEntidade(
            ConstantesEntidades.FATURAS_GRAPHQL,
            LocalDate.of(2026, 3, 25)
        );

        assertEquals(LocalDate.of(2026, 3, 24), plano.consultaDataInicio());
        assertEquals(LocalDate.of(2026, 3, 25), plano.consultaDataFim());
        assertEquals(watermark, plano.confirmacaoInicio());
    }

    @Test
    void deveFalharQuandoEntidadeNaoPossuirStrategyRegistrada() {
        final ExecutionWindowPlanner planner = new ExecutionWindowPlanner(new StubExecutionAuditPort());

        assertThrows(
            IllegalArgumentException.class,
            () -> planner.planejarEntidade("entidade_desconhecida", LocalDate.of(2026, 3, 25))
        );
    }

    private static final class StubExecutionAuditPort implements ExecutionAuditPort {
        private final LocalDateTime watermark;

        private StubExecutionAuditPort() {
            this(null);
        }

        private StubExecutionAuditPort(final LocalDateTime watermark) {
            this.watermark = watermark;
        }

        @Override
        public void registrarResultado(final ExecutionAuditRecord record) {
            // no-op
        }

        @Override
        public Optional<ExecutionAuditRecord> buscarResultado(final String executionUuid, final String entidade) {
            return Optional.empty();
        }

        @Override
        public List<ExecutionAuditRecord> listarResultados(final String executionUuid) {
            return List.of();
        }

        @Override
        public Optional<LocalDateTime> buscarWatermarkConfirmado(final String entidade) {
            return Optional.ofNullable(watermark);
        }

        @Override
        public void atualizarWatermarkConfirmado(final String entidade, final LocalDateTime watermarkConfirmado) {
            // no-op
        }
    }
}
