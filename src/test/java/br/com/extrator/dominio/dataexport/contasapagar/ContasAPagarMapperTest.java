package br.com.extrator.dominio.dataexport.contasapagar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import br.com.extrator.integracao.mapeamento.dataexport.contasapagar.ContasAPagarMapper;
import br.com.extrator.persistencia.entidade.ContasAPagarDataExportEntity;

class ContasAPagarMapperTest {

    @Test
    void deveRetornarNullQuandoValorMonetarioForInvalido() {
        final ContasAPagarDTO dto = criarDtoBase();
        dto.setOriginalValue("valor-invalido");

        final ContasAPagarMapper mapper = new ContasAPagarMapper();
        final ContasAPagarDataExportEntity entity = mapper.toEntity(dto);

        assertNull(entity.getValorOriginal());
    }

    @Test
    void devePreservarZeroQuandoValorMonetarioForZeroValido() {
        final ContasAPagarDTO dto = criarDtoBase();
        dto.setOriginalValue("0.00");

        final ContasAPagarMapper mapper = new ContasAPagarMapper();
        final ContasAPagarDataExportEntity entity = mapper.toEntity(dto);

        assertEquals(0, entity.getValorOriginal().compareTo(BigDecimal.ZERO));
    }

    private ContasAPagarDTO criarDtoBase() {
        final ContasAPagarDTO dto = new ContasAPagarDTO();
        dto.setSequenceCode("123");
        return dto;
    }
}
