package br.com.extrator.dominio.graphql.fretes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import br.com.extrator.integracao.mapeamento.graphql.fretes.FreteMapper;

class FreteMapperTest {

    @Test
    void deveConverterDatasValidasMesmoQuandoOutroCampoDeDataForInvalido() {
        final FreteNodeDTO dto = new FreteNodeDTO();
        dto.setId(123L);
        dto.setServiceAt("data-invalida");
        dto.setCreatedAt("2026-03-01T10:15:30Z");
        dto.setDeliveryPredictionDate("outra-data-invalida");
        dto.setServiceDate("2026-03-01");

        final FreteMapper mapper = new FreteMapper();
        final var entity = mapper.toEntity(dto);

        assertNotNull(entity);
        assertNull(entity.getServicoEm());
        assertEquals(OffsetDateTime.parse("2026-03-01T10:15:30Z"), entity.getCriadoEm());
        assertNull(entity.getDataPrevisaoEntrega());
        assertEquals(LocalDate.of(2026, 3, 1), entity.getServiceDate());
    }
}
