package br.com.extrator.aplicacao.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import br.com.extrator.aplicacao.portas.RasterGateway;

class RasterPipelineStepTest {

    @AfterEach
    void limparPropriedades() {
        System.clearProperty("RASTER_TIMEOUT_SECONDS");
        System.clearProperty("RASTER_STEP_TIMEOUT_SECONDS");
    }

    @Test
    void deveUsarTimeoutDeStepSeparadoDoTimeoutHttpRaster() {
        System.setProperty("RASTER_TIMEOUT_SECONDS", "120");
        System.setProperty("RASTER_STEP_TIMEOUT_SECONDS", "900");
        final RasterGateway gateway = (dataInicio, dataFim, entidade) -> null;

        final RasterPipelineStep step = new RasterPipelineStep(gateway, "raster_viagens");

        assertEquals(Duration.ofSeconds(900), step.obterTimeoutExecucao());
    }
}
