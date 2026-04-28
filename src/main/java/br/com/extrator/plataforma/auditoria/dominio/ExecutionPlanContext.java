package br.com.extrator.plataforma.auditoria.dominio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;

/**
 * Contexto thread-local com as janelas planejadas por entidade.
 */
public final class ExecutionPlanContext {
    private static final String SYSTEM_PROPERTY_PREFIX = "etl.execution.plan.";
    private static volatile Map<String, ExecutionWindowPlan> PLANOS_COMPARTILHADOS = Map.of();
    private static final ThreadLocal<Map<String, ExecutionWindowPlan>> PLANOS =
        ThreadLocal.withInitial(Map::of);

    private ExecutionPlanContext() {
    }

    public static void setPlanos(final Map<String, ExecutionWindowPlan> planos) {
        if (planos == null || planos.isEmpty()) {
            PLANOS.set(Map.of());
            PLANOS_COMPARTILHADOS = Map.of();
            return;
        }
        final Map<String, ExecutionWindowPlan> copia = new LinkedHashMap<>(planos);
        PLANOS.set(copia);
        PLANOS_COMPARTILHADOS = Map.copyOf(copia);
    }

    public static Optional<ExecutionWindowPlan> getPlano(final String entidade) {
        if (entidade == null || entidade.isBlank()) {
            return Optional.empty();
        }
        final ExecutionWindowPlan planoThreadLocal = PLANOS.get().get(entidade);
        if (planoThreadLocal != null) {
            return Optional.of(planoThreadLocal);
        }
        final ExecutionWindowPlan planoCompartilhado = PLANOS_COMPARTILHADOS.get(entidade);
        if (planoCompartilhado != null) {
            return Optional.of(planoCompartilhado);
        }
        return resolverPlanoPorSystemProperty(entidade);
    }

    public static Map<String, String> exportarSystemProperties() {
        final Map<String, ExecutionWindowPlan> origem = !PLANOS.get().isEmpty() ? PLANOS.get() : PLANOS_COMPARTILHADOS;
        if (origem.isEmpty()) {
            return Map.of();
        }

        final Map<String, String> propriedades = new LinkedHashMap<>();
        for (final Map.Entry<String, ExecutionWindowPlan> entry : origem.entrySet()) {
            final ExecutionWindowPlan plano = entry.getValue();
            if (plano == null) {
                continue;
            }
            final String prefix = prefixoSystemProperty(entry.getKey());
            propriedades.put(prefix + "consulta_data_inicio", plano.consultaDataInicio().toString());
            propriedades.put(prefix + "consulta_data_fim", plano.consultaDataFim().toString());
            propriedades.put(prefix + "confirmacao_inicio", plano.confirmacaoInicio().toString());
            propriedades.put(prefix + "confirmacao_fim", plano.confirmacaoFim().toString());
        }
        return Map.copyOf(propriedades);
    }

    public static void clear() {
        PLANOS.remove();
        PLANOS_COMPARTILHADOS = Map.of();
    }

    private static Optional<ExecutionWindowPlan> resolverPlanoPorSystemProperty(final String entidade) {
        final String prefix = prefixoSystemProperty(entidade);
        final String consultaInicio = System.getProperty(prefix + "consulta_data_inicio");
        final String consultaFim = System.getProperty(prefix + "consulta_data_fim");
        final String confirmacaoInicio = System.getProperty(prefix + "confirmacao_inicio");
        final String confirmacaoFim = System.getProperty(prefix + "confirmacao_fim");

        if (consultaInicio == null || consultaFim == null || confirmacaoInicio == null || confirmacaoFim == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(new ExecutionWindowPlan(
                LocalDate.parse(consultaInicio),
                LocalDate.parse(consultaFim),
                LocalDateTime.parse(confirmacaoInicio),
                LocalDateTime.parse(confirmacaoFim)
            ));
        } catch (final DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    private static String prefixoSystemProperty(final String entidade) {
        return SYSTEM_PROPERTY_PREFIX + normalizarChaveEntidade(entidade) + ".";
    }

    private static String normalizarChaveEntidade(final String entidade) {
        return entidade == null
            ? ""
            : entidade.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
