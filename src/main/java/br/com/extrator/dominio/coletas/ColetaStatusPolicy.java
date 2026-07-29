package br.com.extrator.dominio.coletas;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Contrato canônico dos status de Coletas publicados pela ESL.
 *
 * <p>O catálogo equivalente no SQL Server é {@code dbo.dim_status_coleta}.
 * Alterações neste contrato exigem atualizar a migration e o teste de paridade
 * com o catálogo persistido.</p>
 */
public final class ColetaStatusPolicy {
    private static final Map<String, Definition> DEFINITIONS = Map.ofEntries(
        Map.entry("pending", new Definition("Pendente", false)),
        Map.entry("treatment", new Definition("Em tratativa", false)),
        Map.entry("manifested", new Definition("Manifestada", false)),
        Map.entry("in_transit", new Definition("Em trânsito", false)),
        Map.entry("draft", new Definition("Rascunho", false)),
        Map.entry("finished", new Definition("Finalizada", true)),
        Map.entry("done", new Definition("Coletada", true)),
        Map.entry("canceled", new Definition("Cancelada", true)),
        Map.entry("cancelled", new Definition("Cancelada", true))
    );

    private ColetaStatusPolicy() {
    }

    public static String normalize(final String rawStatus) {
        if (rawStatus == null) {
            return null;
        }
        final String normalized = rawStatus.trim()
            .toLowerCase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_');
        return normalized.isEmpty() ? null : normalized;
    }

    public static boolean isKnown(final String status) {
        return DEFINITIONS.containsKey(normalize(status));
    }

    public static boolean isTerminal(final String status) {
        final Definition definition = DEFINITIONS.get(normalize(status));
        return definition != null && definition.terminal();
    }

    public static Optional<String> displayName(final String status) {
        final Definition definition = DEFINITIONS.get(normalize(status));
        return definition == null ? Optional.empty() : Optional.of(definition.displayName());
    }

    public static Set<String> knownCodes() {
        return DEFINITIONS.keySet();
    }

    private record Definition(String displayName, boolean terminal) {
    }
}
