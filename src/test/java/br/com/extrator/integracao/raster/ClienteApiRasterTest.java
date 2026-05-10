package br.com.extrator.integracao.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import br.com.extrator.dominio.raster.RasterViagemDTO;
import br.com.extrator.integracao.ResultadoExtracao;

class ClienteApiRasterTest {

    @AfterEach
    void limparPropriedades() {
        System.clearProperty("RASTER_MAX_DIAS_JANELA");
    }

    @Test
    void deveParsearResultComoArrayComViagens() {
        final String json = """
            {
              "result": [
                {
                  "Viagens": [
                    {
                      "CodSolicitacao": 8577538,
                      "PlacaVeiculo": "ABC1D23",
                      "Rota": {"CodRota": 10, "Descricao": "AGUDOS/SP ATE OSASCO/SP"},
                      "ColetasEntregas": [
                        {"Ordem": 1, "Tipo": "E", "DataHoraPrevChegada": "2026-05-05T01:30:00.000-03:00"}
                      ]
                    }
                  ]
                }
              ]
            }
            """;

        final ResultadoExtracao<RasterViagemDTO> resultado = new ClienteApiRaster().parseResponse(json);

        assertEquals(1, resultado.getDados().size());
        assertEquals(8577538L, resultado.getDados().get(0).getCodSolicitacao());
        assertEquals("AGUDOS/SP ATE OSASCO/SP", resultado.getDados().get(0).getRota().getDescricao());
        assertEquals(1, resultado.getDados().get(0).getColetasEntregas().size());
        assertEquals(1, resultado.getDados().get(0).getColetasEntregas().get(0).getOrdem());
    }

    @Test
    void deveParsearResultComoObjetoUnico() {
        final String json = """
            {
              "result": {
                "Viagens": {
                  "CodSolicitacao": 1,
                  "TempoTotalViagem": 300
                }
              }
            }
            """;

        final ResultadoExtracao<RasterViagemDTO> resultado = new ClienteApiRaster().parseResponse(json);

        assertEquals(1, resultado.getDados().size());
        assertEquals(300, resultado.getDados().get(0).getTempoTotalViagem());
    }

    @Test
    void deveAceitarViagensAusenteComoListaVazia() {
        final ResultadoExtracao<RasterViagemDTO> resultado = new ClienteApiRaster().parseResponse("{\"result\":[{}]}");

        assertEquals(0, resultado.getDados().size());
        assertEquals(1, resultado.getPaginasProcessadas());
    }

    @Test
    void deveMarcarIncompletoQuandoVieremQuinhentasViagens() {
        final StringBuilder viagens = new StringBuilder();
        for (int i = 1; i <= 500; i++) {
            if (i > 1) {
                viagens.append(',');
            }
            viagens.append("{\"CodSolicitacao\":").append(i).append('}');
        }
        final String json = "{\"result\":[{\"Viagens\":[" + viagens + "]}]}";

        final ResultadoExtracao<RasterViagemDTO> resultado = new ClienteApiRaster().parseResponse(json);

        assertFalse(resultado.isCompleto());
        assertEquals("LOTE_RASTER_500_REGISTROS", resultado.getMotivoInterrupcao());
        assertEquals(500, resultado.getDados().size());
    }

    @Test
    void deveParticionarPeriodoQuandoLoteRasterBaterQuinhentos() {
        System.setProperty("RASTER_MAX_DIAS_JANELA", "31");
        final LocalDate inicio = LocalDate.of(2026, 4, 2);
        final LocalDate fim = LocalDate.of(2026, 5, 1);
        final ClienteApiRasterFake cliente = new ClienteApiRasterFake()
            .comResultado(inicio, fim, resultadoIncompletoLimite(1, 500))
            .comResultado(LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 16), resultadoCompleto(1, 280))
            .comResultado(LocalDate.of(2026, 4, 17), LocalDate.of(2026, 5, 1), resultadoCompleto(281, 300));

        final ResultadoExtracao<RasterViagemDTO> resultado = cliente.buscarEventoFimViagem(inicio, fim);

        assertTrue(resultado.isCompleto());
        assertEquals(580, resultado.getDados().size());
        assertEquals(580, resultado.getRegistrosExtraidos());
        assertEquals(2, resultado.getPaginasProcessadas());
        assertEquals(List.of(
            "2026-04-02 a 2026-05-01",
            "2026-04-02 a 2026-04-16",
            "2026-04-17 a 2026-05-01"
        ), cliente.chamadas);
    }

    @Test
    void deveParticionarPeriodoGrandeAntesDaPrimeiraChamadaRaster() {
        System.setProperty("RASTER_MAX_DIAS_JANELA", "15");
        final LocalDate inicio = LocalDate.of(2026, 4, 1);
        final LocalDate fim = LocalDate.of(2026, 4, 30);
        final ClienteApiRasterFake cliente = new ClienteApiRasterFake()
            .comResultado(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 15), resultadoCompleto(1, 250))
            .comResultado(LocalDate.of(2026, 4, 16), LocalDate.of(2026, 4, 30), resultadoCompleto(251, 260));

        final ResultadoExtracao<RasterViagemDTO> resultado = cliente.buscarEventoFimViagem(inicio, fim);

        assertTrue(resultado.isCompleto());
        assertEquals(510, resultado.getDados().size());
        assertEquals(510, resultado.getRegistrosExtraidos());
        assertEquals(2, resultado.getPaginasProcessadas());
        assertEquals(List.of(
            "2026-04-01 a 2026-04-15",
            "2026-04-16 a 2026-04-30"
        ), cliente.chamadas);
    }

    @Test
    void deveUsarJanelasDiariasPorPadraoConformeManualRaster() {
        final LocalDate inicio = LocalDate.of(2026, 4, 1);
        final LocalDate fim = LocalDate.of(2026, 4, 3);
        final ClienteApiRasterFake cliente = new ClienteApiRasterFake()
            .comResultado(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 1), resultadoCompleto(1, 10))
            .comResultado(LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 2), resultadoCompleto(11, 20))
            .comResultado(LocalDate.of(2026, 4, 3), LocalDate.of(2026, 4, 3), resultadoCompleto(31, 30));

        final ResultadoExtracao<RasterViagemDTO> resultado = cliente.buscarEventoFimViagem(inicio, fim);

        assertTrue(resultado.isCompleto());
        assertEquals(60, resultado.getDados().size());
        assertEquals(60, resultado.getRegistrosExtraidos());
        assertEquals(3, resultado.getPaginasProcessadas());
        assertEquals(List.of(
            "2026-04-01 a 2026-04-01",
            "2026-04-02 a 2026-04-02",
            "2026-04-03 a 2026-04-03"
        ), cliente.chamadas);
    }

    @Test
    void deveManterIncompletoQuandoDiaTambemBaterLimiteRaster() {
        final LocalDate dia = LocalDate.of(2026, 4, 15);
        final ClienteApiRasterFake cliente = new ClienteApiRasterFake()
            .comResultado(dia, dia, resultadoIncompletoLimite(1, 500));

        final ResultadoExtracao<RasterViagemDTO> resultado = cliente.buscarEventoFimViagem(dia, dia);

        assertFalse(resultado.isCompleto());
        assertEquals("LOTE_RASTER_500_REGISTROS", resultado.getMotivoInterrupcao());
        assertEquals(500, resultado.getDados().size());
        assertEquals(List.of("2026-04-15 a 2026-04-15"), cliente.chamadas);
    }

    private static ResultadoExtracao<RasterViagemDTO> resultadoCompleto(final int primeiroCodigo,
                                                                        final int quantidade) {
        return ResultadoExtracao.completo(viagens(primeiroCodigo, quantidade), 1, quantidade);
    }

    private static ResultadoExtracao<RasterViagemDTO> resultadoIncompletoLimite(final int primeiroCodigo,
                                                                                final int quantidade) {
        return ResultadoExtracao.incompleto(
            viagens(primeiroCodigo, quantidade),
            "LOTE_RASTER_500_REGISTROS",
            1,
            quantidade
        );
    }

    private static List<RasterViagemDTO> viagens(final int primeiroCodigo, final int quantidade) {
        final List<RasterViagemDTO> viagens = new ArrayList<>();
        for (int i = 0; i < quantidade; i++) {
            final RasterViagemDTO viagem = new RasterViagemDTO();
            viagem.setCodSolicitacao((long) primeiroCodigo + i);
            viagens.add(viagem);
        }
        return viagens;
    }

    private static final class ClienteApiRasterFake extends ClienteApiRaster {
        private final Map<String, ResultadoExtracao<RasterViagemDTO>> resultados = new LinkedHashMap<>();
        private final List<String> chamadas = new ArrayList<>();

        private ClienteApiRasterFake comResultado(final LocalDate inicio,
                                                  final LocalDate fim,
                                                  final ResultadoExtracao<RasterViagemDTO> resultado) {
            resultados.put(chave(inicio, fim), resultado);
            return this;
        }

        @Override
        ResultadoExtracao<RasterViagemDTO> buscarEventoFimViagemUmaJanela(final LocalDate dataInicio,
                                                                          final LocalDate dataFim) {
            final String chave = chave(dataInicio, dataFim);
            chamadas.add(chave.replace("|", " a "));
            final ResultadoExtracao<RasterViagemDTO> resultado = resultados.get(chave);
            if (resultado == null) {
                throw new AssertionError("Janela nao configurada no teste: " + chave);
            }
            return resultado;
        }

        private static String chave(final LocalDate inicio, final LocalDate fim) {
            return inicio + "|" + fim;
        }
    }
}
