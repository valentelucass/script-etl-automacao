package br.com.extrator.dominio.graphql.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import br.com.extrator.integracao.mapeamento.graphql.coletas.ColetaMapper;

class ColetaMapperTest {

    @Test
    void deveSerializarIdsDosPickItemsSemDuplicidade() {
        final ColetaNodeDTO dto = new ColetaNodeDTO();
        dto.setId("104279");
        dto.setSequenceCode(104279L);

        final PickItemDTO primeiro = new PickItemDTO();
        primeiro.setId(4717944L);
        final PickItemDTO duplicado = new PickItemDTO();
        duplicado.setId(4717944L);
        final PickItemDTO segundo = new PickItemDTO();
        segundo.setId(4717950L);
        final PickItemDTO semId = new PickItemDTO();

        dto.setPickItems(Arrays.asList(primeiro, duplicado, segundo, semId, null));

        final ColetaMapper mapper = new ColetaMapper();
        final var entity = mapper.toEntity(dto);

        assertNotNull(entity);
        assertEquals("[4717944,4717950]", entity.getPickItemsIds());
    }

    @Test
    void deveNormalizarStatusEVersionarTimestampDaEsl() {
        final ColetaNodeDTO dto = new ColetaNodeDTO();
        dto.setId("104203");
        dto.setStatus(" Finished ");
        dto.setStatusUpdatedAt("11/06/2026 00:27:00");

        final var entity = new ColetaMapper().toEntity(dto);

        assertEquals("finished", entity.getStatus());
        assertEquals("Finalizada", entity.getLastOccurrence());
        assertEquals("2026-06-11T00:27-03:00", entity.getStatusUpdatedAtEm().toString());
    }
}
