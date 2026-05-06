package br.com.extrator.integracao.mapeamento.raster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.extrator.dominio.raster.RasterParadaDTO;
import br.com.extrator.dominio.raster.RasterViagemDTO;
import br.com.extrator.persistencia.entidade.RasterViagemEntity;
import br.com.extrator.persistencia.entidade.RasterViagemParadaEntity;

class RasterMapperTest {

    @Test
    void deveConverterDatasOffsetENullSentinela() {
        final RasterViagemDTO dto = new RasterViagemDTO();
        dto.setCodSolicitacao(10L);
        dto.setDataHoraPrevIni("2026-05-05T20:30:00.000-03:00");
        dto.setDataHoraRealFim("1900-01-01T00:00:00.000-03:00");
        dto.setTempoTotalViagem(66_451_402);

        final RasterViagemEntity entity = new RasterMapper().toViagemEntity(dto);

        assertNotNull(entity.getDataHoraPrevIni());
        assertEquals(-3 * 60 * 60, entity.getDataHoraPrevIni().getOffset().getTotalSeconds());
        assertNull(entity.getDataHoraRealFim());
        assertNull(entity.getTempoTotalViagemMin());
    }

    @Test
    void deveUsarOrdemFallbackQuandoParadaNaoTrouxerOrdem() {
        final RasterParadaDTO parada = new RasterParadaDTO();
        parada.setTipo("E");
        parada.setDataHoraPrevChegada("2026-05-06T01:30:00.000-03:00");
        final RasterViagemDTO viagem = new RasterViagemDTO();
        viagem.setCodSolicitacao(99L);
        viagem.setColetasEntregas(List.of(parada));

        final List<RasterViagemParadaEntity> entities = new RasterMapper().toParadaEntities(viagem);

        assertEquals(1, entities.size());
        assertEquals(99L, entities.get(0).getCodSolicitacao());
        assertEquals(1, entities.get(0).getOrdem());
        assertNotNull(entities.get(0).getDataHoraPrevChegada());
    }
}
