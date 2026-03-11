package br.com.extrator.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PipelineIntegrationIT {

    @Test
    void deveTerAmbienteDeTesteProvisionado() {
        assertTrue(Files.exists(Path.of("test-environment/docker-compose.yml")));
        assertTrue(Files.exists(Path.of("test-environment/wiremock/mappings/graphql-fretes-success.json")));
        assertTrue(Files.exists(Path.of("test-environment/datasets/duplicates.json")));
    }
}
