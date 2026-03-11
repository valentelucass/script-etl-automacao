package br.com.extrator.snapshots;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

class ManifestoSnapshotTest {

    @Test
    void deveManterSnapshotDaTransformacaoDeManifesto() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();

        final Map<String, Object> input;
        final Map<String, Object> expected;
        try (InputStream in = getClass().getResourceAsStream("/snapshots/manifesto-input.json");
             InputStream out = getClass().getResourceAsStream("/snapshots/manifesto-output.snapshot.json")) {
            input = mapper.readValue(in, new TypeReference<Map<String, Object>>() { });
            expected = mapper.readValue(out, new TypeReference<Map<String, Object>>() { });
        }

        final Map<String, Object> transformed = transformarManifesto(input);
        assertEquals(expected, transformed);
    }

    private Map<String, Object> transformarManifesto(final Map<String, Object> input) {
        final Map<String, Object> output = new LinkedHashMap<>();
        output.put("manifesto_id", String.valueOf(input.get("manifesto_id")).toUpperCase());
        output.put("numero", String.valueOf(input.get("numero")));
        output.put("data", String.valueOf(input.get("data")));
        output.put("schema_version", "v2");
        return output;
    }
}
