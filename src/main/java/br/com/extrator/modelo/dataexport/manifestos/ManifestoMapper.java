/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/dataexport/manifestos/ManifestoMapper.java
Classe  : ManifestoMapper (class)
Pacote  : br.com.extrator.modelo.dataexport.manifestos
Modulo  : DTO/Mapper DataExport
Papel   : Implementa responsabilidade de manifesto mapper.

Conecta com:
- ManifestoEntity (db.entity)
- ValidadorDTO (util.validacao)
- ResultadoValidacao (util.validacao.ValidadorDTO)
- MapperUtil (util.mapeamento)

Fluxo geral:
1) Modela payloads da API DataExport.
2) Mapeia resposta para entidades internas.
3) Apoia carga e deduplicacao no destino.

Estrutura interna:
Metodos principais:
- ManifestoMapper(): realiza operacao relacionada a "manifesto mapper".
- toEntity(...1 args): realiza operacao relacionada a "to entity".
- converterParaBigDecimal(...3 args): transforma dados entre formatos/modelos.
Atributos-chave:
- logger: logger da classe para diagnostico.
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.dataexport.manifestos;

import br.com.extrator.db.entity.ManifestoEntity;
import br.com.extrator.util.validacao.ValidadorDTO;
import br.com.extrator.util.validacao.ValidadorDTO.ResultadoValidacao;
import br.com.extrator.util.mapeamento.MapperUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * Mapper (Tradutor) que transforma o ManifestoDTO (dados brutos do Data Export)
 * em uma ManifestoEntity (pronta para o banco de dados).
 * É responsável pela conversão de tipos e pela serialização de todos os
 * dados na coluna de metadados.
 */
public class ManifestoMapper {

    private static final Logger logger = LoggerFactory.getLogger(ManifestoMapper.class);

    public ManifestoMapper() {
        // Usando MapperUtil para ObjectMapper compartilhado
    }

    /**
     * Converte o DTO de Manifesto em uma Entidade.
     * PROBLEMA #6 CORRIGIDO: Adicionada validação de campos críticos.
     * 
     * @param dto O objeto DTO com os dados do manifesto.
     * @return Um objeto ManifestoEntity pronto para ser salvo.
     * @throws IllegalArgumentException se campos críticos forem inválidos
     */
    public ManifestoEntity toEntity(final ManifestoDTO dto) {
        if (dto == null) {
            return null;
        }

        // PROBLEMA #6: Validação de campos críticos
        final ResultadoValidacao validacao = ValidadorDTO.criarValidacao("Manifesto");
        ValidadorDTO.validarId(validacao, "sequence_code", dto.getSequenceCode());
        
        if (!validacao.isValido()) {
            validacao.logErros();
            throw new IllegalArgumentException("Manifesto inválido: sequence_code é obrigatório. Erros: " + validacao.getErros());
        }

        final ManifestoEntity entity = new ManifestoEntity();

        // 1. Mapeamento dos campos essenciais conforme docs/descobertas-endpoints/manifestos.md
        entity.setSequenceCode(dto.getSequenceCode());
        entity.setStatus(dto.getStatus());
        entity.setBranchNickname(dto.getBranchNickname());
        entity.setMdfeNumber(dto.getMdfeNumber());
        entity.setMdfeKey(dto.getMdfeKey());
        entity.setMdfeStatus(dto.getMdfeStatus());
        entity.setDistributionPole(dto.getDistributionPole());
        entity.setClassification(dto.getClassification());
        entity.setVehiclePlate(dto.getVehiclePlate());
        entity.setVehicleType(dto.getVehicleType());
        entity.setVehicleOwner(dto.getVehicleOwner());
        entity.setDriverName(dto.getDriverName());
        entity.setVehicleDepartureKm(dto.getVehicleDepartureKm());
        entity.setClosingKm(dto.getClosingKm());
        entity.setTraveledKm(dto.getTraveledKm());
        entity.setInvoicesCount(dto.getInvoicesCount());
        entity.setInvoicesVolumes(dto.getInvoicesVolumes());
        entity.setDeliveryManifestItemsCount(dto.getDeliveryManifestItemsCount());
        entity.setTransferManifestItemsCount(dto.getTransferManifestItemsCount());
        entity.setPickManifestItemsCount(dto.getPickManifestItemsCount());
        entity.setDispatchDraftManifestItemsCount(dto.getDispatchDraftManifestItemsCount());
        entity.setConsolidationManifestItemsCount(dto.getConsolidationManifestItemsCount());
        entity.setReversePickManifestItemsCount(dto.getReversePickManifestItemsCount());
        entity.setManifestItemsCount(dto.getManifestItemsCount());
        entity.setFinalizedManifestItemsCount(dto.getFinalizedManifestItemsCount());
        entity.setCalculatedPickCount(dto.getCalculatedPickCount());
        entity.setCalculatedDeliveryCount(dto.getCalculatedDeliveryCount());
        entity.setCalculatedDispatchCount(dto.getCalculatedDispatchCount());
        entity.setCalculatedConsolidationCount(dto.getCalculatedConsolidationCount());
        entity.setCalculatedReversePickCount(dto.getCalculatedReversePickCount());
        
        // Converter campos numéricos de String para BigDecimal
        entity.setInvoicesWeight(converterParaBigDecimal(dto.getInvoicesWeight(), "invoices_weight", dto.getSequenceCode()));
        entity.setTotalTaxedWeight(converterParaBigDecimal(dto.getTotalTaxedWeight(), "total_taxed_weight", dto.getSequenceCode()));
        entity.setTotalCubicVolume(converterParaBigDecimal(dto.getTotalCubicVolume(), "total_cubic_volume", dto.getSequenceCode()));
        entity.setInvoicesValue(converterParaBigDecimal(dto.getInvoicesValue(), "invoices_value", dto.getSequenceCode()));
        entity.setManifestFreightsTotal(converterParaBigDecimal(dto.getManifestFreightsTotal(), "manifest_freights_total", dto.getSequenceCode()));
        entity.setPickSubtotal(converterParaBigDecimal(dto.getPickSubtotal(), "pick_subtotal", dto.getSequenceCode()));
        entity.setDeliverySubtotal(converterParaBigDecimal(dto.getDeliverySubtotal(), "delivery_subtotal", dto.getSequenceCode()));
        entity.setDispatchSubtotal(converterParaBigDecimal(dto.getDispatchSubtotal(), "dispatch_subtotal", dto.getSequenceCode()));
        entity.setConsolidationSubtotal(converterParaBigDecimal(dto.getConsolidationSubtotal(), "consolidation_subtotal", dto.getSequenceCode()));
        entity.setReversePickSubtotal(converterParaBigDecimal(dto.getReversePickSubtotal(), "reverse_pick_subtotal", dto.getSequenceCode()));
        entity.setAdvanceSubtotal(converterParaBigDecimal(dto.getAdvanceSubtotal(), "advance_subtotal", dto.getSequenceCode()));
        entity.setFleetCostsSubtotal(converterParaBigDecimal(dto.getFleetCostsSubtotal(), "fleet_costs_subtotal", dto.getSequenceCode()));
        entity.setAdditionalsSubtotal(converterParaBigDecimal(dto.getAdditionalsSubtotal(), "additionals_subtotal", dto.getSequenceCode()));
        entity.setDiscountsSubtotal(converterParaBigDecimal(dto.getDiscountsSubtotal(), "discounts_subtotal", dto.getSequenceCode()));
        entity.setDiscountValue(converterParaBigDecimal(dto.getDiscountValue(), "discount_value", dto.getSequenceCode()));
        
        entity.setPickSequenceCode(dto.getPickSequenceCode());
        entity.setContractNumber(dto.getContractNumber());
        entity.setContractType(dto.getContractType());
        entity.setCalculationType(dto.getCalculationType());
        entity.setCargoType(dto.getCargoType());
        
        entity.setDailySubtotal(converterParaBigDecimal(dto.getDailySubtotal(), "daily_subtotal", dto.getSequenceCode()));
        entity.setOperationalExpensesTotal(converterParaBigDecimal(dto.getOperationalExpensesTotal(), "operational_expenses_total", dto.getSequenceCode()));
        entity.setInssValue(converterParaBigDecimal(dto.getInssValue(), "inss_value", dto.getSequenceCode()));
        entity.setSestSenatValue(converterParaBigDecimal(dto.getSestSenatValue(), "sest_senat_value", dto.getSequenceCode()));
        entity.setIrValue(converterParaBigDecimal(dto.getIrValue(), "ir_value", dto.getSequenceCode()));
        entity.setPayingTotal(converterParaBigDecimal(dto.getPayingTotal(), "paying_total", dto.getSequenceCode()));
        entity.setFreightSubtotal(converterParaBigDecimal(dto.getFreightSubtotal(), "freight_subtotal", dto.getSequenceCode()));
        entity.setFuelSubtotal(converterParaBigDecimal(dto.getFuelSubtotal(), "fuel_subtotal", dto.getSequenceCode()));
        entity.setTollSubtotal(converterParaBigDecimal(dto.getTollSubtotal(), "toll_subtotal", dto.getSequenceCode()));
        entity.setDriverServicesTotal(converterParaBigDecimal(dto.getDriverServicesTotal(), "driver_services_total", dto.getSequenceCode()));
        entity.setManualKm(dto.getManualKm());
        entity.setGenerateMdfe(dto.getGenerateMdfe());
        entity.setMonitoringRequest(dto.getMonitoringRequest());
        entity.setUniqDestinationsCount(dto.getUniqDestinationsCount());
        entity.setCreationUserName(dto.getCreationUserName());
        entity.setAdjustmentUserName(dto.getAdjustmentUserName());
        entity.setAdjustmentComments(dto.getAdjustmentComments());
        entity.setContractStatus(dto.getContractStatus());
        entity.setIksId(dto.getIksId());
        entity.setProgramacaoSequenceCode(dto.getProgramacaoSequenceCode());
        entity.setProgramacaoCliente(dto.getProgramacaoCliente());
        entity.setProgramacaoTipoServico(dto.getProgramacaoTipoServico());

        // 2. Conversão segura de tipos de dados
        try {
            if (dto.getCreatedAt() != null && !dto.getCreatedAt().trim().isEmpty()) {
                entity.setCreatedAt(OffsetDateTime.parse(dto.getCreatedAt()));
            }
            if (dto.getDeparturedAt() != null && !dto.getDeparturedAt().trim().isEmpty()) {
                entity.setDeparturedAt(OffsetDateTime.parse(dto.getDeparturedAt()));
            }
            if (dto.getClosedAt() != null && !dto.getClosedAt().trim().isEmpty()) {
                entity.setClosedAt(OffsetDateTime.parse(dto.getClosedAt()));
            }
            if (dto.getFinishedAt() != null && !dto.getFinishedAt().trim().isEmpty()) {
                entity.setFinishedAt(OffsetDateTime.parse(dto.getFinishedAt()));
            }
            if (dto.getMobileReadAt() != null && !dto.getMobileReadAt().trim().isEmpty()) {
                entity.setMobileReadAt(OffsetDateTime.parse(dto.getMobileReadAt()));
            }
            if (dto.getProgramacaoStartingAt() != null && !dto.getProgramacaoStartingAt().trim().isEmpty()) {
                entity.setProgramacaoStartingAt(OffsetDateTime.parse(dto.getProgramacaoStartingAt()));
            }
            if (dto.getProgramacaoEndingAt() != null && !dto.getProgramacaoEndingAt().trim().isEmpty()) {
                entity.setProgramacaoEndingAt(OffsetDateTime.parse(dto.getProgramacaoEndingAt()));
            }
        } catch (final DateTimeParseException e) {
            logger.error("❌ Erro ao converter datas para manifesto {}: createdAt='{}', departuredAt='{}', closedAt='{}', finishedAt='{}' - {}", 
                dto.getSequenceCode(), dto.getCreatedAt(), dto.getDeparturedAt(), dto.getClosedAt(), dto.getFinishedAt(), e.getMessage());
            logger.debug("Stack trace completo:", e);
        }
        
        // Converter totalCost usando o método auxiliar
        entity.setTotalCost(converterParaBigDecimal(dto.getTotalCost(), "total_cost", dto.getSequenceCode()));
        entity.setKm(converterParaBigDecimal(dto.getKm(), "km", dto.getSequenceCode()));
        entity.setTrailer1LicensePlate(dto.getTrailer1LicensePlate());
        entity.setTrailer1WeightCapacity(converterParaBigDecimal(dto.getTrailer1WeightCapacity(), "mft_tl1_weight_capacity", dto.getSequenceCode()));
        entity.setTrailer2LicensePlate(dto.getTrailer2LicensePlate());
        entity.setTrailer2WeightCapacity(converterParaBigDecimal(dto.getTrailer2WeightCapacity(), "mft_tl2_weight_capacity", dto.getSequenceCode()));
        entity.setVehicleWeightCapacity(converterParaBigDecimal(dto.getVehicleWeightCapacity(), "mft_vie_weight_capacity", dto.getSequenceCode()));
        entity.setVehicleCubicWeight(converterParaBigDecimal(dto.getVehicleCubicWeight(), "mft_vie_cubic_weight", dto.getSequenceCode()));
        
        // Novos campos de veículo e comentários
        // capacidade_kg: usar vehicleWeightCapacity (mesmo campo JSON "mft_vie_weight_capacity")
        entity.setCapacidadeKg(converterParaBigDecimal(dto.getVehicleWeightCapacity(), "capacidade_kg", dto.getSequenceCode()));
        
        // obs_operacional: comentários operacionais (liberação)
        entity.setObsOperacional(dto.getObsOperacional());
        
        // obs_financeira: comentários financeiros (fechamento)
        entity.setObsFinanceira(dto.getObsFinanceira());

        // 3. Empacotamento de todos os metadados
        final String metadata = MapperUtil.toJson(dto.getAllProperties());
        entity.setMetadata(metadata);
        
        if (dto.getOtherProperties() != null) {
            final Object ur = dto.getOtherProperties().get("mft_mte_unloading_recipient_names");
            if (ur != null) {
                entity.setUnloadingRecipientNames(MapperUtil.toJson(ur));
            }
            final Object dr = dto.getOtherProperties().get("mft_mte_delivery_region_names");
            if (dr != null) {
                entity.setDeliveryRegionNames(MapperUtil.toJson(dr));
            }
        }
        
        // 4. Calcular identificador único APÓS definir metadata
        // Isso permite usar pick_sequence_code ou hash do metadata
        entity.calcularIdentificadorUnico();

        return entity;
    }
    
    /**
     * Converte uma String para BigDecimal de forma segura.
     * Retorna null se a string for null, vazia ou não puder ser convertida.
     * 
     * @param valor String a ser convertida
     * @param nomeCampo Nome do campo (para logs de erro)
     * @param sequenceCode Código sequencial do manifesto (para logs de erro)
     * @return BigDecimal ou null se não puder converter
     */
    private BigDecimal converterParaBigDecimal(final String valor, final String nomeCampo, final Long sequenceCode) {
        if (valor == null || valor.trim().isEmpty()) {
            return null;
        }
        
        try {
            return new BigDecimal(valor.trim());
        } catch (final NumberFormatException e) {
            logger.warn("⚠️ Erro ao converter campo '{}' para BigDecimal no manifesto {}: valor='{}'. Definindo como NULL.", 
                nomeCampo, sequenceCode, valor);
            return null;
        }
    }
}
