package br.com.extrator.analises.indicadoresgestao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.extrator.dominio.dataexport.localizacaocarga.LocalizacaoCargaDTO;
import br.com.extrator.dominio.graphql.fretes.FreteNodeDTO;
import br.com.extrator.integracao.mapeamento.dataexport.localizacaocarga.LocalizacaoCargaMapper;
import br.com.extrator.integracao.mapeamento.graphql.fretes.FreteMapper;
import br.com.extrator.persistencia.entidade.FreteEntity;
import br.com.extrator.persistencia.entidade.LocalizacaoCargaEntity;
import br.com.extrator.suporte.mapeamento.MapperUtil;

class IndicadoresGestaoArchitectureValidationTest {

    @Test
    void freteMapperDevePreservarCamposFuturosNoMetadataSemPerderMapeamentoAtual() throws Exception {
        final String json = """
            {
              "id": 332352,
              "serviceAt": "2026-03-01T08:00:00-03:00",
              "createdAt": "2026-03-01T07:00:00-03:00",
              "status": "finished",
              "courtesy": true,
              "corporationId": 385129,
              "destinationCityId": 9988,
              "deliveryPredictionDate": "2026-03-05",
              "serviceDate": "2026-03-01",
              "finishedAt": "2026-03-03T23:02:00-03:00",
              "corporationSequenceNumber": 332352
            }
            """;

        final FreteNodeDTO dto = MapperUtil.sharedJson().readValue(json, FreteNodeDTO.class);
        final FreteEntity entity = new FreteMapper().toEntity(dto);

        assertNotNull(entity);
        assertEquals(LocalDate.of(2026, 3, 5), entity.getDataPrevisaoEntrega());
        assertEquals(LocalDate.of(2026, 3, 1), entity.getServiceDate());
        assertEquals(OffsetDateTime.parse("2026-03-03T23:02:00-03:00"), entity.getFinishedAt());
        assertEquals(332352L, entity.getCorporationSequenceNumber());
        assertEquals(Boolean.TRUE, entity.getCortesia());

        final JsonNode metadata = MapperUtil.sharedJson().readTree(entity.getMetadata());
        assertEquals("2026-03-03T23:02:00-03:00", metadata.get("finishedAt").asText());
        assertEquals(332352L, metadata.get("corporationSequenceNumber").asLong());
        assertEquals(true, metadata.get("courtesy").asBoolean());
    }

    @Test
    void localizacaoCargaMapperDeveAceitarCorporationSequenceNumberComoPonteDoIndicador1() throws Exception {
        final String json = """
            {
              "corporation_sequence_number": 332352,
              "service_at": "2026-03-01T08:00:00-03:00",
              "fit_dpn_delivery_prediction_at": "2026-03-05T18:00:00-03:00",
              "fit_dyn_drt_nickname": "CPQ - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
              "fit_crn_psn_nickname": "SPO - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
              "fit_dyn_name": "CAMPINAS - POLO",
              "fit_fln_status": "in_transit",
              "total": "1500.25"
            }
            """;

        final LocalizacaoCargaDTO dto = MapperUtil.sharedJson().readValue(json, LocalizacaoCargaDTO.class);
        final LocalizacaoCargaEntity entity = new LocalizacaoCargaMapper().toEntity(dto);

        assertNotNull(entity);
        assertEquals(332352L, entity.getSequenceNumber());
        assertEquals("CPQ - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA", entity.getDestinationBranchNickname());
        assertEquals("SPO - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA", entity.getBranchNickname());
        assertEquals(OffsetDateTime.parse("2026-03-05T18:00:00-03:00"), entity.getPredictedDeliveryAt());
    }

    @Test
    void localizacaoCargaDtoDeveAceitarAliasSequenceNumberUsadoNaTrilhaAtual() throws Exception {
        final String json = """
            {
              "sequence_number": 346841,
              "fit_dyn_drt_nickname": "SPO - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
            }
            """;

        final LocalizacaoCargaDTO dto = MapperUtil.sharedJson().readValue(json, LocalizacaoCargaDTO.class);

        assertEquals(346841L, dto.getSequenceNumber());
        assertEquals("SPO - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA", dto.getDestinationBranchNickname());
    }
}
