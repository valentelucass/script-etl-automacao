package br.com.extrator.integracao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class DataExportPaginationSupportTest {

    @Test
    void deveRetentarQuandoHouverLacuna422() {
        final DataExportPaginationSupport support = new DataExportPaginationSupport(
            LoggerFactory.getLogger(DataExportPaginationSupportTest.class),
            5,
            Duration.ofMinutes(10),
            new HashMap<>(),
            new HashSet<>(),
            new HashMap<>()
        );

        final ResultadoExtracao<String> resultado = ResultadoExtracao.incompleto(
            java.util.List.of(),
            ResultadoExtracao.MotivoInterrupcao.LACUNA_PAGINACAO_422,
            3,
            300
        );

        assertTrue(support.deveRetentarResultadoIncompleto(resultado));
    }

    @Test
    void naoDeveRetentarQuandoPaginaVaziaFoiFimNatural() {
        final DataExportPaginationSupport support = new DataExportPaginationSupport(
            LoggerFactory.getLogger(DataExportPaginationSupportTest.class),
            5,
            Duration.ofMinutes(10),
            new HashMap<>(),
            new HashSet<>(),
            new HashMap<>()
        );

        final ResultadoExtracao<String> resultado = ResultadoExtracao.incompleto(
            java.util.List.of(),
            ResultadoExtracao.MotivoInterrupcao.PAGINA_VAZIA,
            3,
            300
        );

        assertFalse(support.deveRetentarResultadoIncompleto(resultado));
    }

    @Test
    void deveReabrirCircuitoAposJanela() {
        final HashMap<String, Instant> abertoDesde = new HashMap<>();
        abertoDesde.put("Template-4924", Instant.now().minus(Duration.ofMinutes(11)));
        final HashSet<String> circuitos = new HashSet<>();
        circuitos.add("Template-4924");
        final DataExportPaginationSupport support = new DataExportPaginationSupport(
            LoggerFactory.getLogger(DataExportPaginationSupportTest.class),
            5,
            Duration.ofMinutes(10),
            new HashMap<>(),
            circuitos,
            abertoDesde
        );

        assertFalse(support.isCircuitBreakerAtivo("Template-4924"));
    }
}
