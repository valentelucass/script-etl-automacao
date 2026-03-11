package br.com.extrator.aplicacao.validacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.extrator.suporte.mapeamento.MapperUtil;

final class ValidacaoEtlExtremaMetadataDiff {
    private static final int SAMPLE_LIMIT = 8;

    MetadataDiff comparar(final String apiMetadata, final String dbMetadata) {
        final Map<String, String> apiFlat = achatar(apiMetadata);
        final Map<String, String> dbFlat = achatar(dbMetadata);

        int divergentFields = 0;
        int unexpectedNulls = 0;
        int truncatedFields = 0;
        int timezoneDrifts = 0;
        final Set<String> apiOnlyFields = new LinkedHashSet<>();
        final List<FieldSample> samples = new ArrayList<>();

        for (final Map.Entry<String, String> entry : apiFlat.entrySet()) {
            final String path = entry.getKey();
            final String apiValue = entry.getValue();
            if (!dbFlat.containsKey(path)) {
                apiOnlyFields.add(path);
                continue;
            }

            final String dbValue = dbFlat.get(path);
            final boolean timezoneDrift = hasTimezoneDrift(apiValue, dbValue);
            if (timezoneDrift) {
                timezoneDrifts++;
            }
            if (isEquivalent(apiValue, dbValue)) {
                continue;
            }

            divergentFields++;
            if (apiValue != null && dbValue == null) {
                unexpectedNulls++;
            }
            if (isTruncated(apiValue, dbValue)) {
                truncatedFields++;
            }
            if (samples.size() < SAMPLE_LIMIT) {
                samples.add(new FieldSample(path, apiValue, dbValue));
            }
        }

        return new MetadataDiff(
            divergentFields,
            unexpectedNulls,
            truncatedFields,
            timezoneDrifts,
            apiOnlyFields,
            samples,
            apiFlat.keySet(),
            dbFlat.keySet()
        );
    }

    Set<String> extrairPaths(final String metadata) {
        return achatar(metadata).keySet();
    }

    private Map<String, String> achatar(final String metadata) {
        final Map<String, String> flattened = new LinkedHashMap<>();
        if (metadata == null || metadata.isBlank()) {
            return flattened;
        }
        try {
            final JsonNode root = MapperUtil.sharedJson().readTree(metadata);
            coletar(root, "", flattened);
            return flattened;
        } catch (Exception ignored) {
            flattened.put("$raw", metadata.trim());
            return flattened;
        }
    }

    private void coletar(final JsonNode node, final String path, final Map<String, String> flattened) {
        final String currentPath = path.isBlank() ? "$" : path;
        if (node == null || node.isNull()) {
            flattened.put(currentPath, null);
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                final String childPath = path.isBlank() ? entry.getKey() : path + "." + entry.getKey();
                coletar(entry.getValue(), childPath, flattened);
            });
            return;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                final String childPath = currentPath + "[" + i + "]";
                coletar(node.get(i), childPath, flattened);
            }
            if (node.isEmpty()) {
                flattened.put(currentPath, "[]");
            }
            return;
        }
        flattened.put(currentPath, node.asText());
    }

    private boolean isEquivalent(final String apiValue, final String dbValue) {
        if (apiValue == null && dbValue == null) {
            return true;
        }
        if (apiValue == null || dbValue == null) {
            return false;
        }

        final String apiTrim = apiValue.trim();
        final String dbTrim = dbValue.trim();
        if (apiTrim.equals(dbTrim)) {
            return true;
        }

        final String apiNumber = normalizeNumber(apiTrim);
        final String dbNumber = normalizeNumber(dbTrim);
        if (apiNumber != null && apiNumber.equals(dbNumber)) {
            return true;
        }

        final Instant apiInstant = parseInstant(apiTrim);
        final Instant dbInstant = parseInstant(dbTrim);
        if (apiInstant != null && dbInstant != null && apiInstant.equals(dbInstant)) {
            return true;
        }

        final LocalDate apiDate = parseDate(apiTrim);
        final LocalDate dbDate = parseDate(dbTrim);
        return apiDate != null && dbDate != null && apiDate.equals(dbDate);
    }

    private boolean hasTimezoneDrift(final String apiValue, final String dbValue) {
        if (apiValue == null || dbValue == null) {
            return false;
        }
        final Instant apiInstant = parseInstant(apiValue.trim());
        final Instant dbInstant = parseInstant(dbValue.trim());
        if (apiInstant == null || dbInstant == null) {
            return false;
        }
        if (apiInstant.equals(dbInstant) && !apiValue.trim().equals(dbValue.trim())) {
            return true;
        }
        final long diffSeconds = Math.abs(apiInstant.getEpochSecond() - dbInstant.getEpochSecond());
        return diffSeconds > 0 && diffSeconds <= 18_000 && diffSeconds % 3_600 == 0;
    }

    private boolean isTruncated(final String apiValue, final String dbValue) {
        if (apiValue == null || dbValue == null) {
            return false;
        }
        final String apiTrim = apiValue.trim();
        final String dbTrim = dbValue.trim();
        if (dbTrim.length() < 6 || apiTrim.length() <= dbTrim.length()) {
            return false;
        }
        return apiTrim.startsWith(dbTrim);
    }

    private String normalizeNumber(final String value) {
        try {
            return new BigDecimal(value).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Instant parseInstant(final String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value).atOffset(java.time.ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private LocalDate parseDate(final String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    record MetadataDiff(
        int divergentFields,
        int unexpectedNulls,
        int truncatedFields,
        int timezoneDrifts,
        Set<String> apiOnlyFields,
        List<FieldSample> samples,
        Set<String> apiPaths,
        Set<String> dbPaths
    ) {
    }

    record FieldSample(String path, String apiValue, String dbValue) {
    }
}
