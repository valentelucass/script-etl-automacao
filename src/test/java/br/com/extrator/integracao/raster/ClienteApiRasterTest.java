package br.com.extrator.integracao.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import br.com.extrator.dominio.raster.RasterViagemDTO;
import br.com.extrator.integracao.ResultadoExtracao;

class ClienteApiRasterTest {

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
}
