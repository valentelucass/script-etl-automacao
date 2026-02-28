/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/dataexport/manifestos/ManifestoDTO.java
Classe  : ManifestoDTO (class)
Pacote  : br.com.extrator.modelo.dataexport.manifestos
Modulo  : DTO/Mapper DataExport
Papel   : Implementa responsabilidade de manifesto dto.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela payloads da API DataExport.
2) Mapeia resposta para entidades internas.
3) Apoia carga e deduplicacao no destino.

Estrutura interna:
Metodos principais:
- add(...2 args): realiza operacao relacionada a "add".
- getAllProperties(): expone valor atual do estado interno.
- getSequenceCode(): expone valor atual do estado interno.
- setSequenceCode(...1 args): ajusta valor em estado interno.
- getStatus(): expone valor atual do estado interno.
- setStatus(...1 args): ajusta valor em estado interno.
- getCreatedAt(): expone valor atual do estado interno.
- setCreatedAt(...1 args): ajusta valor em estado interno.
- getDeparturedAt(): expone valor atual do estado interno.
- setDeparturedAt(...1 args): ajusta valor em estado interno.
- getFinishedAt(): expone valor atual do estado interno.
- setFinishedAt(...1 args): ajusta valor em estado interno.
- getTotalCost(): expone valor atual do estado interno.
- setTotalCost(...1 args): ajusta valor em estado interno.
Atributos-chave:
- sequenceCode: campo de estado para "sequence code".
- branchNickname: campo de estado para "branch nickname".
- createdAt: campo de estado para "created at".
- departuredAt: campo de estado para "departured at".
- closedAt: campo de estado para "closed at".
- finishedAt: campo de estado para "finished at".
- status: campo de estado para "status".
- mdfeNumber: campo de estado para "mdfe number".
- mdfeKey: campo de estado para "mdfe key".
- mdfeStatus: campo de estado para "mdfe status".
- distributionPole: campo de estado para "distribution pole".
- classification: campo de estado para "classification".
- vehiclePlate: campo de estado para "vehicle plate".
- vehicleType: campo de estado para "vehicle type".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.dataexport.manifestos;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO (Data Transfer Object) para representar um registro de Manifesto
 * vindo do template da API Data Export. Mapeia campos essenciais para colunas dedicadas
 * e captura dinamicamente todos os outros campos em um mapa, garantindo
 * resiliência contra futuras alterações no template.
 */
public class ManifestoDTO {

    // --- Todos os 87 campos da amostra principal mapeados explicitamente conforme docs/descobertas-endpoints/manifestos.md ---
    
    // 1. Nº Manifesto
    @JsonProperty("sequence_code")
    private Long sequenceCode;
    
    // 2. Filial (Apelido)
    @JsonProperty("mft_crn_psn_nickname")
    private String branchNickname;
    
    // 3. Data Emissão
    @JsonProperty("created_at")
    private String createdAt;
    
    // 4. Data Saída
    @JsonProperty("departured_at")
    private String departuredAt;
    
    // 5. Data Fechamento
    @JsonProperty("closed_at")
    private String closedAt;
    
    // 6. Data Finalização
    @JsonProperty("finished_at")
    private String finishedAt;
    
    // 7. Status
    @JsonProperty("status")
    private String status;
    
    // 8. MDF-e
    @JsonProperty("mft_mfs_number")
    private Integer mdfeNumber;
    
    // 9. Chave MDF-e
    @JsonProperty("mft_mfs_key")
    private String mdfeKey;
    
    // 10. Status MDF-e
    @JsonProperty("mdfe_status")
    private String mdfeStatus;
    
    // 11. Polo de Distribuição
    @JsonProperty("mft_ape_name")
    private String distributionPole;
    
    // 12. Classificação
    @JsonProperty("mft_man_name")
    private String classification;
    
    // 13. Placa
    @JsonProperty("mft_vie_license_plate")
    private String vehiclePlate;
    
    // 14. Tipo Veículo
    @JsonProperty("mft_vie_vee_name")
    private String vehicleType;
    
    // 15. Proprietário
    @JsonProperty("mft_vie_onr_name")
    private String vehicleOwner;
    
    // 16. Motorista
    @JsonProperty("mft_mdr_iil_name")
    private String driverName;
    
    // 17. Km Saída
    @JsonProperty("vehicle_departure_km")
    private Integer vehicleDepartureKm;
    
    // 18. Km Fechamento
    @JsonProperty("closing_km")
    private Integer closingKm;
    
    // 19. Km Rodado
    @JsonProperty("traveled_km")
    private Integer traveledKm;
    
    // 20. Total Notas
    @JsonProperty("invoices_count")
    private Integer invoicesCount;
    
    // 21. Total Volumes
    @JsonProperty("invoices_volumes")
    private Integer invoicesVolumes;
    
    // 22. Peso Real
    @JsonProperty("invoices_weight")
    private String invoicesWeight;
    
    // 23. Peso Taxado
    @JsonProperty("total_taxed_weight")
    private String totalTaxedWeight;
    
    // 24. Cubagem
    @JsonProperty("total_cubic_volume")
    private String totalCubicVolume;
    
    // 25. Valor Notas
    @JsonProperty("invoices_value")
    private String invoicesValue;
    
    // 26. Valor Fretes
    @JsonProperty("manifest_freights_total")
    private String manifestFreightsTotal;
    
    // 27. Coleta (Item)
    @JsonProperty("mft_pfs_pck_sequence_code")
    private Long pickSequenceCode;
    
    // 28. Contrato
    @JsonProperty("mft_cat_cot_number")
    private String contractNumber;
    
    // 29. Diárias
    @JsonProperty("daily_subtotal")
    private String dailySubtotal;
    
    // 30. Custo Total
    @JsonProperty("total_cost")
    private String totalCost;
    
    // 31. Desp. Operacionais
    @JsonProperty("operational_expenses_total")
    private String operationalExpensesTotal;
    
    // 32. INSS
    @JsonProperty("mft_a_t_inss_value")
    private String inssValue;
    
    // 33. SEST/SENAT
    @JsonProperty("mft_a_t_sest_senat_value")
    private String sestSenatValue;
    
    // 34. IR
    @JsonProperty("mft_a_t_ir_value")
    private String irValue;
    
    // 35. Valor a Pagar
    @JsonProperty("paying_total")
    private String payingTotal;
    
    // 36. Usuário (Criação)
    @JsonProperty("mft_uer_name")
    private String creationUserName;
    
    // 37. Usuário do Acerto
    @JsonProperty("mft_aoe_rer_name")
    private String adjustmentUserName;
    
    // 38. Adiantamento
    @JsonProperty("advance_subtotal")
    private String advanceSubtotal;
    
    // 39. Pedágio
    @JsonProperty("toll_subtotal")
    private String tollSubtotal;
    
    // 40. Custos Frota
    @JsonProperty("fleet_costs_subtotal")
    private String fleetCostsSubtotal;
    
    // 41. Programação/Cliente
    @JsonProperty("mft_s_n_svs_sge_pyr_nickname")
    private String programacaoCliente;
    
    // 42. Programação/Tipo de Serviço
    @JsonProperty("mft_s_n_svs_sge_sse_name")
    private String programacaoTipoServico;
    
    // 43. Data/hora de leitura mobile
    @JsonProperty("mobile_read_at")
    private String mobileReadAt;
    
    // 44. Quilometragem
    @JsonProperty("km")
    private String km;
    
    // 45. Quilometragem manual
    @JsonProperty("manual_km")
    private Boolean manualKm;
    
    // 46. Gerar MDF-e
    @JsonProperty("generate_mdfe")
    private Boolean generateMdfe;
    
    // 47. Solicitação de monitoramento
    @JsonProperty("monitoring_request")
    private Boolean monitoringRequest;
    
    // 48. Contagem de itens de entrega
    @JsonProperty("delivery_manifest_items_count")
    private Integer deliveryManifestItemsCount;
    
    // 49. Contagem de itens de transferência
    @JsonProperty("transfer_manifest_items_count")
    private Integer transferManifestItemsCount;
    
    // 50. Contagem de itens de coleta
    @JsonProperty("pick_manifest_items_count")
    private Integer pickManifestItemsCount;
    
    // 51. Contagem de itens de despacho rascunho
    @JsonProperty("dispatch_draft_manifest_items_count")
    private Integer dispatchDraftManifestItemsCount;
    
    // 52. Contagem de itens de consolidação
    @JsonProperty("consolidation_manifest_items_count")
    private Integer consolidationManifestItemsCount;
    
    // 53. Contagem de itens de coleta reversa
    @JsonProperty("reverse_pick_manifest_items_count")
    private Integer reversePickManifestItemsCount;
    
    // 54. Contagem total de itens
    @JsonProperty("manifest_items_count")
    private Integer manifestItemsCount;
    
    // 55. Contagem de itens finalizados
    @JsonProperty("finalized_manifest_items_count")
    private Integer finalizedManifestItemsCount;
    
    // 56. Contagem de destinos únicos
    @JsonProperty("uniq_destinations_count")
    private Integer uniqDestinationsCount;
    
    // 57. Tipo de contrato
    @JsonProperty("contract_type")
    private String contractType;
    
    // 58. Tipo de cálculo
    @JsonProperty("calculation_type")
    private String calculationType;
    
    // 59. Tipo de carga
    @JsonProperty("cargo_type")
    private String cargoType;
    
    // 60. Contagem calculada de coletas
    @JsonProperty("calculated_pick_count")
    private Integer calculatedPickCount;
    
    // 61. Contagem calculada de entregas
    @JsonProperty("calculated_delivery_count")
    private Integer calculatedDeliveryCount;
    
    // 62. Contagem calculada de despachos
    @JsonProperty("calculated_dispatch_count")
    private Integer calculatedDispatchCount;
    
    // 63. Contagem calculada de consolidações
    @JsonProperty("calculated_consolidation_count")
    private Integer calculatedConsolidationCount;
    
    // 64. Contagem calculada de coletas reversas
    @JsonProperty("calculated_reverse_pick_count")
    private Integer calculatedReversePickCount;
    
    // 65. Subtotal de frete
    @JsonProperty("freight_subtotal")
    private String freightSubtotal;
    
    // 66. Subtotal de combustível
    @JsonProperty("fuel_subtotal")
    private String fuelSubtotal;
    
    // 67. Subtotal de coleta
    @JsonProperty("pick_subtotal")
    private String pickSubtotal;
    
    // 68. Subtotal de entrega
    @JsonProperty("delivery_subtotal")
    private String deliverySubtotal;
    
    // 69. Subtotal de despacho
    @JsonProperty("dispatch_subtotal")
    private String dispatchSubtotal;
    
    // 70. Subtotal de consolidação
    @JsonProperty("consolidation_subtotal")
    private String consolidationSubtotal;
    
    // 71. Subtotal de coleta reversa
    @JsonProperty("reverse_pick_subtotal")
    private String reversePickSubtotal;
    
    // 72. Subtotal de adicionais
    @JsonProperty("additionals_subtotal")
    private String additionalsSubtotal;
    
    // 73. Subtotal de descontos
    @JsonProperty("discounts_subtotal")
    private String discountsSubtotal;
    
    // 74. Valor do desconto
    @JsonProperty("discount_value")
    private String discountValue;
    
    // 75. Total de serviços do motorista
    @JsonProperty("driver_services_total")
    private String driverServicesTotal;
    
    // 76. Comentários do acerto
    @JsonProperty("mft_aoe_comments")
    private String adjustmentComments;
    
    // 77. Status do contrato/cotação
    @JsonProperty("mft_cat_cot_status")
    private String contractStatus;
    
    // 78. ID do IKS
    @JsonProperty("mft_iks_id")
    private String iksId;
    
    // 79. Código de sequência da programação
    @JsonProperty("mft_s_n_sequence_code")
    private String programacaoSequenceCode;
    
    // 80. Data de início da programação
    @JsonProperty("mft_s_n_starting_at")
    private String programacaoStartingAt;
    
    // 81. Data de fim da programação
    @JsonProperty("mft_s_n_ending_at")
    private String programacaoEndingAt;
    
    // 82. Placa do trailer 1
    @JsonProperty("mft_tl1_license_plate")
    private String trailer1LicensePlate;
    
    // 83. Capacidade de peso do trailer 1
    @JsonProperty("mft_tl1_weight_capacity")
    private String trailer1WeightCapacity;
    
    // 84. Placa do trailer 2
    @JsonProperty("mft_tl2_license_plate")
    private String trailer2LicensePlate;
    
    // 85. Capacidade de peso do trailer 2
    @JsonProperty("mft_tl2_weight_capacity")
    private String trailer2WeightCapacity;
    
    // 86. Capacidade de peso do veículo
    @JsonProperty("mft_vie_weight_capacity")
    private String vehicleWeightCapacity;
    
    // 87. Peso cúbico do veículo
    @JsonProperty("mft_vie_cubic_weight")
    private String vehicleCubicWeight;
    
    // 88. Comentários operacionais (operationalComments - Liberação)
    @JsonProperty("operational_comments")
    private String obsOperacional;
    
    // 89. Comentários financeiros (closingComments)
    @JsonProperty("closing_comments")
    private String obsFinanceira;
    
    // NOTA: capacidadeKg não é um campo separado no DTO.
    // O campo JSON "mft_vie_weight_capacity" já é mapeado para vehicleWeightCapacity.
    // No Mapper, vehicleWeightCapacity será usado para preencher capacidadeKg na Entity.
    
    // Nota: Campos de arrays (mft_mte_unloading_recipient_names, mft_mte_delivery_region_names) 
    // são capturados via @JsonAnySetter como Object para manter flexibilidade

    // --- Contêiner Dinâmico ("Resto") ---
    private final Map<String, Object> otherProperties = new HashMap<>();

    @JsonAnySetter
    public void add(final String key, final Object value) {
        this.otherProperties.put(key, value);
    }

    /**
     * Retorna um mapa contendo todas as propriedades do DTO, combinando
     * os 87 campos mapeados explicitamente conforme docs/descobertas-endpoints/manifestos.md
     * com os campos capturados dinamicamente.
     * @return Um mapa com todos os dados do manifesto.
     */
    public Map<String, Object> getAllProperties() {
        final Map<String, Object> allProps = new LinkedHashMap<>();
        // Adiciona todos os 87 campos explícitos ao mapa conforme documentação
        allProps.put("sequence_code", sequenceCode);
        allProps.put("mft_crn_psn_nickname", branchNickname);
        allProps.put("created_at", createdAt);
        allProps.put("departured_at", departuredAt);
        allProps.put("closed_at", closedAt);
        allProps.put("finished_at", finishedAt);
        allProps.put("status", status);
        allProps.put("mft_mfs_number", mdfeNumber);
        allProps.put("mft_mfs_key", mdfeKey);
        allProps.put("mdfe_status", mdfeStatus);
        allProps.put("mft_ape_name", distributionPole);
        allProps.put("mft_man_name", classification);
        allProps.put("mft_vie_license_plate", vehiclePlate);
        allProps.put("mft_vie_vee_name", vehicleType);
        allProps.put("mft_vie_onr_name", vehicleOwner);
        allProps.put("mft_mdr_iil_name", driverName);
        allProps.put("vehicle_departure_km", vehicleDepartureKm);
        allProps.put("closing_km", closingKm);
        allProps.put("traveled_km", traveledKm);
        allProps.put("invoices_count", invoicesCount);
        allProps.put("invoices_volumes", invoicesVolumes);
        allProps.put("invoices_weight", invoicesWeight);
        allProps.put("total_taxed_weight", totalTaxedWeight);
        allProps.put("total_cubic_volume", totalCubicVolume);
        allProps.put("invoices_value", invoicesValue);
        allProps.put("manifest_freights_total", manifestFreightsTotal);
        allProps.put("mft_pfs_pck_sequence_code", pickSequenceCode);
        allProps.put("mft_cat_cot_number", contractNumber);
        allProps.put("daily_subtotal", dailySubtotal);
        allProps.put("total_cost", totalCost);
        allProps.put("operational_expenses_total", operationalExpensesTotal);
        allProps.put("mft_a_t_inss_value", inssValue);
        allProps.put("mft_a_t_sest_senat_value", sestSenatValue);
        allProps.put("mft_a_t_ir_value", irValue);
        allProps.put("paying_total", payingTotal);
        allProps.put("mft_uer_name", creationUserName);
        allProps.put("mft_aoe_rer_name", adjustmentUserName);
        allProps.put("advance_subtotal", advanceSubtotal);
        allProps.put("toll_subtotal", tollSubtotal);
        allProps.put("fleet_costs_subtotal", fleetCostsSubtotal);
        allProps.put("mft_s_n_svs_sge_pyr_nickname", programacaoCliente);
        allProps.put("mft_s_n_svs_sge_sse_name", programacaoTipoServico);
        allProps.put("mobile_read_at", mobileReadAt);
        allProps.put("km", km);
        allProps.put("manual_km", manualKm);
        allProps.put("generate_mdfe", generateMdfe);
        allProps.put("monitoring_request", monitoringRequest);
        allProps.put("delivery_manifest_items_count", deliveryManifestItemsCount);
        allProps.put("transfer_manifest_items_count", transferManifestItemsCount);
        allProps.put("pick_manifest_items_count", pickManifestItemsCount);
        allProps.put("dispatch_draft_manifest_items_count", dispatchDraftManifestItemsCount);
        allProps.put("consolidation_manifest_items_count", consolidationManifestItemsCount);
        allProps.put("reverse_pick_manifest_items_count", reversePickManifestItemsCount);
        allProps.put("manifest_items_count", manifestItemsCount);
        allProps.put("finalized_manifest_items_count", finalizedManifestItemsCount);
        allProps.put("uniq_destinations_count", uniqDestinationsCount);
        allProps.put("contract_type", contractType);
        allProps.put("calculation_type", calculationType);
        allProps.put("cargo_type", cargoType);
        allProps.put("calculated_pick_count", calculatedPickCount);
        allProps.put("calculated_delivery_count", calculatedDeliveryCount);
        allProps.put("calculated_dispatch_count", calculatedDispatchCount);
        allProps.put("calculated_consolidation_count", calculatedConsolidationCount);
        allProps.put("calculated_reverse_pick_count", calculatedReversePickCount);
        allProps.put("freight_subtotal", freightSubtotal);
        allProps.put("fuel_subtotal", fuelSubtotal);
        allProps.put("pick_subtotal", pickSubtotal);
        allProps.put("delivery_subtotal", deliverySubtotal);
        allProps.put("dispatch_subtotal", dispatchSubtotal);
        allProps.put("consolidation_subtotal", consolidationSubtotal);
        allProps.put("reverse_pick_subtotal", reversePickSubtotal);
        allProps.put("additionals_subtotal", additionalsSubtotal);
        allProps.put("discounts_subtotal", discountsSubtotal);
        allProps.put("discount_value", discountValue);
        allProps.put("driver_services_total", driverServicesTotal);
        allProps.put("mft_aoe_comments", adjustmentComments);
        allProps.put("mft_cat_cot_status", contractStatus);
        allProps.put("mft_iks_id", iksId);
        allProps.put("mft_s_n_sequence_code", programacaoSequenceCode);
        allProps.put("mft_s_n_starting_at", programacaoStartingAt);
        allProps.put("mft_s_n_ending_at", programacaoEndingAt);
        allProps.put("mft_tl1_license_plate", trailer1LicensePlate);
        allProps.put("mft_tl1_weight_capacity", trailer1WeightCapacity);
        allProps.put("mft_tl2_license_plate", trailer2LicensePlate);
        allProps.put("mft_tl2_weight_capacity", trailer2WeightCapacity);
        allProps.put("mft_vie_weight_capacity", vehicleWeightCapacity);
        allProps.put("mft_vie_cubic_weight", vehicleCubicWeight);
        // capacidade_kg: usar vehicleWeightCapacity (mesmo campo JSON)
        allProps.put("capacidade_kg", vehicleWeightCapacity);
        allProps.put("operational_comments", obsOperacional);
        allProps.put("closing_comments", obsFinanceira);
        // Adiciona todos os outros campos capturados dinamicamente (incluindo arrays)
        allProps.putAll(otherProperties);
        return allProps;
    }

    // --- Getters e Setters ---

    public Long getSequenceCode() {
        return sequenceCode;
    }

    public void setSequenceCode(final Long sequenceCode) {
        this.sequenceCode = sequenceCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final String createdAt) {
        this.createdAt = createdAt;
    }

    public String getDeparturedAt() {
        return departuredAt;
    }

    public void setDeparturedAt(final String departuredAt) {
        this.departuredAt = departuredAt;
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(final String finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(final String totalCost) {
        this.totalCost = totalCost;
    }

    public Integer getTraveledKm() {
        return traveledKm;
    }

    public void setTraveledKm(final Integer traveledKm) {
        this.traveledKm = traveledKm;
    }

    public String getVehiclePlate() {
        return vehiclePlate;
    }

    public void setVehiclePlate(final String vehiclePlate) {
        this.vehiclePlate = vehiclePlate;
    }

    public String getDriverName() {
        return driverName;
    }

    public void setDriverName(final String driverName) {
        this.driverName = driverName;
    }

    // Getters e Setters para todos os 87 campos conforme documentação
    
    public String getBranchNickname() {
        return branchNickname;
    }

    public void setBranchNickname(final String branchNickname) {
        this.branchNickname = branchNickname;
    }

    public String getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(final String closedAt) {
        this.closedAt = closedAt;
    }

    public Integer getMdfeNumber() {
        return mdfeNumber;
    }

    public void setMdfeNumber(final Integer mdfeNumber) {
        this.mdfeNumber = mdfeNumber;
    }

    public String getMdfeKey() {
        return mdfeKey;
    }

    public void setMdfeKey(final String mdfeKey) {
        this.mdfeKey = mdfeKey;
    }

    public String getMdfeStatus() {
        return mdfeStatus;
    }

    public void setMdfeStatus(final String mdfeStatus) {
        this.mdfeStatus = mdfeStatus;
    }

    public String getDistributionPole() {
        return distributionPole;
    }

    public void setDistributionPole(final String distributionPole) {
        this.distributionPole = distributionPole;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(final String classification) {
        this.classification = classification;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public void setVehicleType(final String vehicleType) {
        this.vehicleType = vehicleType;
    }

    public String getVehicleOwner() {
        return vehicleOwner;
    }

    public void setVehicleOwner(final String vehicleOwner) {
        this.vehicleOwner = vehicleOwner;
    }

    public Integer getVehicleDepartureKm() {
        return vehicleDepartureKm;
    }

    public void setVehicleDepartureKm(final Integer vehicleDepartureKm) {
        this.vehicleDepartureKm = vehicleDepartureKm;
    }

    public Integer getClosingKm() {
        return closingKm;
    }

    public void setClosingKm(final Integer closingKm) {
        this.closingKm = closingKm;
    }

    public Integer getInvoicesCount() {
        return invoicesCount;
    }

    public void setInvoicesCount(final Integer invoicesCount) {
        this.invoicesCount = invoicesCount;
    }

    public Integer getInvoicesVolumes() {
        return invoicesVolumes;
    }

    public void setInvoicesVolumes(final Integer invoicesVolumes) {
        this.invoicesVolumes = invoicesVolumes;
    }

    public String getInvoicesWeight() {
        return invoicesWeight;
    }

    public void setInvoicesWeight(final String invoicesWeight) {
        this.invoicesWeight = invoicesWeight;
    }

    public String getTotalTaxedWeight() {
        return totalTaxedWeight;
    }

    public void setTotalTaxedWeight(final String totalTaxedWeight) {
        this.totalTaxedWeight = totalTaxedWeight;
    }

    public String getTotalCubicVolume() {
        return totalCubicVolume;
    }

    public void setTotalCubicVolume(final String totalCubicVolume) {
        this.totalCubicVolume = totalCubicVolume;
    }

    public String getInvoicesValue() {
        return invoicesValue;
    }

    public void setInvoicesValue(final String invoicesValue) {
        this.invoicesValue = invoicesValue;
    }

    public String getManifestFreightsTotal() {
        return manifestFreightsTotal;
    }

    public void setManifestFreightsTotal(final String manifestFreightsTotal) {
        this.manifestFreightsTotal = manifestFreightsTotal;
    }

    public Long getPickSequenceCode() {
        return pickSequenceCode;
    }

    public void setPickSequenceCode(final Long pickSequenceCode) {
        this.pickSequenceCode = pickSequenceCode;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(final String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public String getDailySubtotal() {
        return dailySubtotal;
    }

    public void setDailySubtotal(final String dailySubtotal) {
        this.dailySubtotal = dailySubtotal;
    }

    public String getOperationalExpensesTotal() {
        return operationalExpensesTotal;
    }

    public void setOperationalExpensesTotal(final String operationalExpensesTotal) {
        this.operationalExpensesTotal = operationalExpensesTotal;
    }

    public String getInssValue() {
        return inssValue;
    }

    public void setInssValue(final String inssValue) {
        this.inssValue = inssValue;
    }

    public String getSestSenatValue() {
        return sestSenatValue;
    }

    public void setSestSenatValue(final String sestSenatValue) {
        this.sestSenatValue = sestSenatValue;
    }

    public String getIrValue() {
        return irValue;
    }

    public void setIrValue(final String irValue) {
        this.irValue = irValue;
    }

    public String getPayingTotal() {
        return payingTotal;
    }

    public void setPayingTotal(final String payingTotal) {
        this.payingTotal = payingTotal;
    }

    public String getCreationUserName() {
        return creationUserName;
    }

    public void setCreationUserName(final String creationUserName) {
        this.creationUserName = creationUserName;
    }

    public String getAdjustmentUserName() {
        return adjustmentUserName;
    }

    public void setAdjustmentUserName(final String adjustmentUserName) {
        this.adjustmentUserName = adjustmentUserName;
    }

    public String getAdvanceSubtotal() {
        return advanceSubtotal;
    }

    public void setAdvanceSubtotal(final String advanceSubtotal) {
        this.advanceSubtotal = advanceSubtotal;
    }

    public String getTollSubtotal() {
        return tollSubtotal;
    }

    public void setTollSubtotal(final String tollSubtotal) {
        this.tollSubtotal = tollSubtotal;
    }

    public String getFleetCostsSubtotal() {
        return fleetCostsSubtotal;
    }

    public void setFleetCostsSubtotal(final String fleetCostsSubtotal) {
        this.fleetCostsSubtotal = fleetCostsSubtotal;
    }

    public String getProgramacaoCliente() {
        return programacaoCliente;
    }

    public void setProgramacaoCliente(final String programacaoCliente) {
        this.programacaoCliente = programacaoCliente;
    }

    public String getProgramacaoTipoServico() {
        return programacaoTipoServico;
    }

    public void setProgramacaoTipoServico(final String programacaoTipoServico) {
        this.programacaoTipoServico = programacaoTipoServico;
    }

    public String getMobileReadAt() {
        return mobileReadAt;
    }

    public void setMobileReadAt(final String mobileReadAt) {
        this.mobileReadAt = mobileReadAt;
    }

    public String getKm() {
        return km;
    }

    public void setKm(final String km) {
        this.km = km;
    }

    public Boolean getManualKm() {
        return manualKm;
    }

    public void setManualKm(final Boolean manualKm) {
        this.manualKm = manualKm;
    }

    public Boolean getGenerateMdfe() {
        return generateMdfe;
    }

    public void setGenerateMdfe(final Boolean generateMdfe) {
        this.generateMdfe = generateMdfe;
    }

    public Boolean getMonitoringRequest() {
        return monitoringRequest;
    }

    public void setMonitoringRequest(final Boolean monitoringRequest) {
        this.monitoringRequest = monitoringRequest;
    }

    public Integer getDeliveryManifestItemsCount() {
        return deliveryManifestItemsCount;
    }

    public void setDeliveryManifestItemsCount(final Integer deliveryManifestItemsCount) {
        this.deliveryManifestItemsCount = deliveryManifestItemsCount;
    }

    public Integer getTransferManifestItemsCount() {
        return transferManifestItemsCount;
    }

    public void setTransferManifestItemsCount(final Integer transferManifestItemsCount) {
        this.transferManifestItemsCount = transferManifestItemsCount;
    }

    public Integer getPickManifestItemsCount() {
        return pickManifestItemsCount;
    }

    public void setPickManifestItemsCount(final Integer pickManifestItemsCount) {
        this.pickManifestItemsCount = pickManifestItemsCount;
    }

    public Integer getDispatchDraftManifestItemsCount() {
        return dispatchDraftManifestItemsCount;
    }

    public void setDispatchDraftManifestItemsCount(final Integer dispatchDraftManifestItemsCount) {
        this.dispatchDraftManifestItemsCount = dispatchDraftManifestItemsCount;
    }

    public Integer getConsolidationManifestItemsCount() {
        return consolidationManifestItemsCount;
    }

    public void setConsolidationManifestItemsCount(final Integer consolidationManifestItemsCount) {
        this.consolidationManifestItemsCount = consolidationManifestItemsCount;
    }

    public Integer getReversePickManifestItemsCount() {
        return reversePickManifestItemsCount;
    }

    public void setReversePickManifestItemsCount(final Integer reversePickManifestItemsCount) {
        this.reversePickManifestItemsCount = reversePickManifestItemsCount;
    }

    public Integer getManifestItemsCount() {
        return manifestItemsCount;
    }

    public void setManifestItemsCount(final Integer manifestItemsCount) {
        this.manifestItemsCount = manifestItemsCount;
    }

    public Integer getFinalizedManifestItemsCount() {
        return finalizedManifestItemsCount;
    }

    public void setFinalizedManifestItemsCount(final Integer finalizedManifestItemsCount) {
        this.finalizedManifestItemsCount = finalizedManifestItemsCount;
    }

    public Integer getUniqDestinationsCount() {
        return uniqDestinationsCount;
    }

    public void setUniqDestinationsCount(final Integer uniqDestinationsCount) {
        this.uniqDestinationsCount = uniqDestinationsCount;
    }

    public String getContractType() {
        return contractType;
    }

    public void setContractType(final String contractType) {
        this.contractType = contractType;
    }

    public String getCalculationType() {
        return calculationType;
    }

    public void setCalculationType(final String calculationType) {
        this.calculationType = calculationType;
    }

    public String getCargoType() {
        return cargoType;
    }

    public void setCargoType(final String cargoType) {
        this.cargoType = cargoType;
    }

    public Integer getCalculatedPickCount() {
        return calculatedPickCount;
    }

    public void setCalculatedPickCount(final Integer calculatedPickCount) {
        this.calculatedPickCount = calculatedPickCount;
    }

    public Integer getCalculatedDeliveryCount() {
        return calculatedDeliveryCount;
    }

    public void setCalculatedDeliveryCount(final Integer calculatedDeliveryCount) {
        this.calculatedDeliveryCount = calculatedDeliveryCount;
    }

    public Integer getCalculatedDispatchCount() {
        return calculatedDispatchCount;
    }

    public void setCalculatedDispatchCount(final Integer calculatedDispatchCount) {
        this.calculatedDispatchCount = calculatedDispatchCount;
    }

    public Integer getCalculatedConsolidationCount() {
        return calculatedConsolidationCount;
    }

    public void setCalculatedConsolidationCount(final Integer calculatedConsolidationCount) {
        this.calculatedConsolidationCount = calculatedConsolidationCount;
    }

    public Integer getCalculatedReversePickCount() {
        return calculatedReversePickCount;
    }

    public void setCalculatedReversePickCount(final Integer calculatedReversePickCount) {
        this.calculatedReversePickCount = calculatedReversePickCount;
    }

    public String getFreightSubtotal() {
        return freightSubtotal;
    }

    public void setFreightSubtotal(final String freightSubtotal) {
        this.freightSubtotal = freightSubtotal;
    }

    public String getFuelSubtotal() {
        return fuelSubtotal;
    }

    public void setFuelSubtotal(final String fuelSubtotal) {
        this.fuelSubtotal = fuelSubtotal;
    }

    public String getPickSubtotal() {
        return pickSubtotal;
    }

    public void setPickSubtotal(final String pickSubtotal) {
        this.pickSubtotal = pickSubtotal;
    }

    public String getDeliverySubtotal() {
        return deliverySubtotal;
    }

    public void setDeliverySubtotal(final String deliverySubtotal) {
        this.deliverySubtotal = deliverySubtotal;
    }

    public String getDispatchSubtotal() {
        return dispatchSubtotal;
    }

    public void setDispatchSubtotal(final String dispatchSubtotal) {
        this.dispatchSubtotal = dispatchSubtotal;
    }

    public String getConsolidationSubtotal() {
        return consolidationSubtotal;
    }

    public void setConsolidationSubtotal(final String consolidationSubtotal) {
        this.consolidationSubtotal = consolidationSubtotal;
    }

    public String getReversePickSubtotal() {
        return reversePickSubtotal;
    }

    public void setReversePickSubtotal(final String reversePickSubtotal) {
        this.reversePickSubtotal = reversePickSubtotal;
    }

    public String getAdditionalsSubtotal() {
        return additionalsSubtotal;
    }

    public void setAdditionalsSubtotal(final String additionalsSubtotal) {
        this.additionalsSubtotal = additionalsSubtotal;
    }

    public String getDiscountsSubtotal() {
        return discountsSubtotal;
    }

    public void setDiscountsSubtotal(final String discountsSubtotal) {
        this.discountsSubtotal = discountsSubtotal;
    }

    public String getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(final String discountValue) {
        this.discountValue = discountValue;
    }

    public String getDriverServicesTotal() {
        return driverServicesTotal;
    }

    public void setDriverServicesTotal(final String driverServicesTotal) {
        this.driverServicesTotal = driverServicesTotal;
    }

    public String getAdjustmentComments() {
        return adjustmentComments;
    }

    public void setAdjustmentComments(final String adjustmentComments) {
        this.adjustmentComments = adjustmentComments;
    }

    public String getContractStatus() {
        return contractStatus;
    }

    public void setContractStatus(final String contractStatus) {
        this.contractStatus = contractStatus;
    }

    public String getIksId() {
        return iksId;
    }

    public void setIksId(final String iksId) {
        this.iksId = iksId;
    }

    public String getProgramacaoSequenceCode() {
        return programacaoSequenceCode;
    }

    public void setProgramacaoSequenceCode(final String programacaoSequenceCode) {
        this.programacaoSequenceCode = programacaoSequenceCode;
    }

    public String getProgramacaoStartingAt() {
        return programacaoStartingAt;
    }

    public void setProgramacaoStartingAt(final String programacaoStartingAt) {
        this.programacaoStartingAt = programacaoStartingAt;
    }

    public String getProgramacaoEndingAt() {
        return programacaoEndingAt;
    }

    public void setProgramacaoEndingAt(final String programacaoEndingAt) {
        this.programacaoEndingAt = programacaoEndingAt;
    }

    public String getTrailer1LicensePlate() {
        return trailer1LicensePlate;
    }

    public void setTrailer1LicensePlate(final String trailer1LicensePlate) {
        this.trailer1LicensePlate = trailer1LicensePlate;
    }

    public String getTrailer1WeightCapacity() {
        return trailer1WeightCapacity;
    }

    public void setTrailer1WeightCapacity(final String trailer1WeightCapacity) {
        this.trailer1WeightCapacity = trailer1WeightCapacity;
    }

    public String getTrailer2LicensePlate() {
        return trailer2LicensePlate;
    }

    public void setTrailer2LicensePlate(final String trailer2LicensePlate) {
        this.trailer2LicensePlate = trailer2LicensePlate;
    }

    public String getTrailer2WeightCapacity() {
        return trailer2WeightCapacity;
    }

    public void setTrailer2WeightCapacity(final String trailer2WeightCapacity) {
        this.trailer2WeightCapacity = trailer2WeightCapacity;
    }

    public String getVehicleWeightCapacity() {
        return vehicleWeightCapacity;
    }

    public void setVehicleWeightCapacity(final String vehicleWeightCapacity) {
        this.vehicleWeightCapacity = vehicleWeightCapacity;
    }

    public String getVehicleCubicWeight() {
        return vehicleCubicWeight;
    }

    public void setVehicleCubicWeight(final String vehicleCubicWeight) {
        this.vehicleCubicWeight = vehicleCubicWeight;
    }

    public String getObsOperacional() {
        return obsOperacional;
    }

    public void setObsOperacional(final String obsOperacional) {
        this.obsOperacional = obsOperacional;
    }

    public String getObsFinanceira() {
        return obsFinanceira;
    }

    public void setObsFinanceira(final String obsFinanceira) {
        this.obsFinanceira = obsFinanceira;
    }

    @JsonAnyGetter
    public Map<String, Object> getOtherProperties() {
        return otherProperties;
    }
}
