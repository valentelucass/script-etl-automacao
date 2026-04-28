package br.com.extrator.plataforma.auditoria.aplicacao;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.com.extrator.aplicacao.portas.ExecutionAuditPort;
import br.com.extrator.plataforma.auditoria.dominio.ExecutionWindowPlan;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public final class ExecutionWindowPlanner {
    private static final int REPLAY_MINIMO_DIAS = 7;
    private final ExecutionAuditPort executionAuditPort;
    private final Map<String, FeatureExecutionWindowStrategy> strategies;

    public ExecutionWindowPlanner(final ExecutionAuditPort executionAuditPort) {
        this.executionAuditPort = executionAuditPort;
        this.strategies = registrarStrategies();
    }

    public Map<String, ExecutionWindowPlan> planejarFluxoCompleto(final LocalDate dataReferenciaFim,
                                                                  final boolean incluirFaturasGraphQL) {
        final Map<String, ExecutionWindowPlan> planos = new LinkedHashMap<>();
        for (final String entidade : entidadesPadrao(incluirFaturasGraphQL)) {
            planos.put(entidade, planejarEntidade(entidade, dataReferenciaFim));
        }
        return Map.copyOf(planos);
    }

    public ExecutionWindowPlan planejarEntidade(final String entidade, final LocalDate dataReferenciaFim) {
        final FeatureExecutionWindowStrategy strategy = strategies.get(entidade);
        if (strategy == null) {
            throw new IllegalArgumentException("Nenhuma strategy de janela registrada para a entidade '" + entidade + "'.");
        }
        return strategy.planejar(
            dataReferenciaFim,
            executionAuditPort.buscarWatermarkConfirmado(entidade)
        );
    }

    private Map<String, FeatureExecutionWindowStrategy> registrarStrategies() {
        final Map<String, FeatureExecutionWindowStrategy> registradas = new LinkedHashMap<>();
        registrarReplay(registradas, ConstantesEntidades.COLETAS);
        registrarReplay(registradas, ConstantesEntidades.MANIFESTOS);
        registrarReplay(registradas, ConstantesEntidades.FRETES);
        registrarReplay(registradas, ConstantesEntidades.COTACOES);
        registrarReplay(registradas, ConstantesEntidades.LOCALIZACAO_CARGAS);
        registrarReplay(registradas, ConstantesEntidades.FATURAS_POR_CLIENTE);

        registrarJanelaDiaria(registradas, ConstantesEntidades.USUARIOS_SISTEMA);
        registrarJanelaDiaria(registradas, ConstantesEntidades.CONTAS_A_PAGAR);
        registrarJanelaDiaria(registradas, ConstantesEntidades.INVENTARIO);
        registrarJanelaDiaria(registradas, ConstantesEntidades.SINISTROS);
        registrarJanelaDiariaSemReplayRetroativo(registradas, ConstantesEntidades.FATURAS_GRAPHQL);
        return Map.copyOf(registradas);
    }

    private void registrarReplay(final Map<String, FeatureExecutionWindowStrategy> registradas,
                                 final String entidade) {
        registradas.put(entidade, new RegisteredExecutionWindowStrategy(entidade, REPLAY_MINIMO_DIAS, true));
    }

    private void registrarJanelaDiaria(final Map<String, FeatureExecutionWindowStrategy> registradas,
                                       final String entidade) {
        registradas.put(entidade, new RegisteredExecutionWindowStrategy(entidade, 2, true));
    }

    private void registrarJanelaDiariaSemReplayRetroativo(final Map<String, FeatureExecutionWindowStrategy> registradas,
                                                          final String entidade) {
        registradas.put(entidade, new RegisteredExecutionWindowStrategy(entidade, 2, false));
    }

    private List<String> entidadesPadrao(final boolean incluirFaturasGraphQL) {
        final List<String> entidades = new java.util.ArrayList<>(List.of(
            ConstantesEntidades.USUARIOS_SISTEMA,
            ConstantesEntidades.COLETAS,
            ConstantesEntidades.FRETES,
            ConstantesEntidades.MANIFESTOS,
            ConstantesEntidades.COTACOES,
            ConstantesEntidades.LOCALIZACAO_CARGAS,
            ConstantesEntidades.INVENTARIO,
            ConstantesEntidades.SINISTROS,
            ConstantesEntidades.CONTAS_A_PAGAR,
            ConstantesEntidades.FATURAS_POR_CLIENTE
        ));
        if (incluirFaturasGraphQL) {
            entidades.add(ConstantesEntidades.FATURAS_GRAPHQL);
        }
        return List.copyOf(entidades);
    }

    private static final class RegisteredExecutionWindowStrategy implements FeatureExecutionWindowStrategy {
        private final String entidade;
        private final int consultaDiasMinimos;
        private final boolean expandirConsultaAteWatermark;

        private RegisteredExecutionWindowStrategy(final String entidade,
                                                  final int consultaDiasMinimos,
                                                  final boolean expandirConsultaAteWatermark) {
            this.entidade = entidade;
            this.consultaDiasMinimos = Math.max(1, consultaDiasMinimos);
            this.expandirConsultaAteWatermark = expandirConsultaAteWatermark;
        }

        @Override
        public String entidade() {
            return entidade;
        }

        @Override
        public ExecutionWindowPlan planejar(final LocalDate dataReferenciaFim,
                                            final Optional<java.time.LocalDateTime> watermarkConfirmado) {
            final LocalDate consultaMinima = dataReferenciaFim.minusDays(consultaDiasMinimos - 1L);
            final LocalDate consultaInicio = expandirConsultaAteWatermark
                ? watermarkConfirmado
                    .map(java.time.LocalDateTime::toLocalDate)
                    .map(data -> data.isBefore(consultaMinima) ? data : consultaMinima)
                    .orElse(consultaMinima)
                : consultaMinima;
            final java.time.LocalDateTime confirmacaoInicio = watermarkConfirmado.orElse(consultaInicio.atStartOfDay());
            return new ExecutionWindowPlan(
                consultaInicio,
                dataReferenciaFim,
                confirmacaoInicio,
                dataReferenciaFim.atTime(LocalTime.MAX)
            );
        }
    }
}
