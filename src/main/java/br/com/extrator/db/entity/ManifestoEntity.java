/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/entity/ManifestoEntity.java
Classe  : ManifestoEntity (class)
Pacote  : br.com.extrator.db.entity
Modulo  : Entidade de persistencia
Papel   : Implementa responsabilidade de manifesto entity.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Define estrutura de dados persistida no banco.
2) Representa campos de tabela/view no dominio Java.
3) Suporta transporte de dados entre camadas.

Estrutura interna:
Metodos principais:
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
- getTraveledKm(): expone valor atual do estado interno.
- setTraveledKm(...1 args): ajusta valor em estado interno.
Atributos-chave:
- logger: logger da classe para diagnostico.
- objectMapper: apoio de mapeamento de dados.
- sequenceCode: campo de estado para "sequence code".
- status: campo de estado para "status".
- createdAt: campo de estado para "created at".
- departuredAt: campo de estado para "departured at".
- closedAt: campo de estado para "closed at".
- finishedAt: campo de estado para "finished at".
- mdfeNumber: campo de estado para "mdfe number".
- mdfeKey: campo de estado para "mdfe key".
- mdfeStatus: campo de estado para "mdfe status".
- distributionPole: campo de estado para "distribution pole".
- classification: campo de estado para "classification".
- vehiclePlate: campo de estado para "vehicle plate".
[DOC-FILE-END]============================================================== */

package br.com.extrator.db.entity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Entity (Entidade) que representa uma linha na tabela 'manifestos' do banco de dados.
 * É o "produto final" da transformação, contendo os dados já estruturados e prontos
 * para serem persistidos. A coluna 'metadata' armazena o JSON completo
 * do objeto original para garantir 100% de completude e resiliência.
 */
public class ManifestoEntity {

    private static final Logger logger = LoggerFactory.getLogger(ManifestoEntity.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // --- Coluna de Chave Primária ---
    private Long sequenceCode;

    // --- Colunas Essenciais para Indexação e Relatórios ---
    // Campos principais conforme docs/descobertas-endpoints/manifestos.md
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime departuredAt;
    private OffsetDateTime closedAt; // Data Fechamento
    private OffsetDateTime finishedAt;
    private Integer mdfeNumber; // MDF-e
    private String mdfeKey; // Chave MDF-e
    private String mdfeStatus;
    private String distributionPole; // Polo de Distribuição
    private String classification; // Classificação
    private String vehiclePlate;
    private String vehicleType; // Tipo Veículo
    private String vehicleOwner; // Proprietário
    private String driverName;
    private String branchNickname; // Filial (Apelido)
    private Integer vehicleDepartureKm; // Km Saída
    private Integer closingKm; // Km Fechamento
    private Integer traveledKm;
    private Integer invoicesCount; // Total Notas
    private Integer invoicesVolumes; // Total Volumes
    private BigDecimal invoicesWeight; // Peso Real
    private BigDecimal totalTaxedWeight; // Peso Taxado
    private BigDecimal totalCubicVolume; // Cubagem
    private BigDecimal invoicesValue; // Valor Notas
    private BigDecimal manifestFreightsTotal; // Valor Fretes
    private Long pickSequenceCode; // Coleta (Item)
    private String contractNumber; // Contrato
    private BigDecimal dailySubtotal; // Diárias
    private BigDecimal totalCost;
    private BigDecimal operationalExpensesTotal; // Desp. Operacionais
    private BigDecimal inssValue; // INSS
    private BigDecimal sestSenatValue; // SEST/SENAT
    private BigDecimal irValue; // IR
    private BigDecimal payingTotal; // Valor a Pagar
    private String creationUserName; // Usuário (Criação)
    private String adjustmentUserName; // Usuário do Acerto

    private String contractType;
    private String calculationType;
    private String cargoType;
    private BigDecimal freightSubtotal;
    private BigDecimal fuelSubtotal;
    private BigDecimal tollSubtotal;
    private BigDecimal driverServicesTotal;
    private Boolean manualKm;
    private Boolean generateMdfe;
    private Boolean monitoringRequest;
    private Integer uniqDestinationsCount;

    private OffsetDateTime mobileReadAt;
    private BigDecimal km;
    private Integer deliveryManifestItemsCount;
    private Integer transferManifestItemsCount;
    private Integer pickManifestItemsCount;
    private Integer dispatchDraftManifestItemsCount;
    private Integer consolidationManifestItemsCount;
    private Integer reversePickManifestItemsCount;
    private Integer manifestItemsCount;
    private Integer finalizedManifestItemsCount;
    private Integer calculatedPickCount;
    private Integer calculatedDeliveryCount;
    private Integer calculatedDispatchCount;
    private Integer calculatedConsolidationCount;
    private Integer calculatedReversePickCount;
    private BigDecimal pickSubtotal;
    private BigDecimal deliverySubtotal;
    private BigDecimal dispatchSubtotal;
    private BigDecimal consolidationSubtotal;
    private BigDecimal reversePickSubtotal;
    private BigDecimal advanceSubtotal;
    private BigDecimal fleetCostsSubtotal;
    private BigDecimal additionalsSubtotal;
    private BigDecimal discountsSubtotal;
    private BigDecimal discountValue;
    private String adjustmentComments;
    private String contractStatus;
    private String iksId;
    private String programacaoSequenceCode;
    private OffsetDateTime programacaoStartingAt;
    private OffsetDateTime programacaoEndingAt;
    private String trailer1LicensePlate;
    private BigDecimal trailer1WeightCapacity;
    private String trailer2LicensePlate;
    private BigDecimal trailer2WeightCapacity;
    private BigDecimal vehicleWeightCapacity;
    private BigDecimal vehicleCubicWeight;
    private String unloadingRecipientNames;
    private String deliveryRegionNames;
    private String programacaoCliente;
    private String programacaoTipoServico;
    
    // Novos campos de veículo e comentários (descobertos via CTE)
    private BigDecimal capacidadeKg; // vehicle.weightCapacity (capacidade em kg)
    private String obsOperacional; // operationalComments (comentários de liberação)
    private String obsFinanceira; // closingComments (comentários financeiros)

    // --- Coluna de Metadados ---
    private String metadata;

    // --- Coluna de Identificador Único (para chave composta) ---
    private String identificadorUnico;

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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getDeparturedAt() {
        return departuredAt;
    }

    public void setDeparturedAt(final OffsetDateTime departuredAt) {
        this.departuredAt = departuredAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(final OffsetDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public BigDecimal getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(final BigDecimal totalCost) {
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

    // Getters e Setters para campos adicionais
    
    public OffsetDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(final OffsetDateTime closedAt) {
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

    public String getBranchNickname() {
        return branchNickname;
    }

    public void setBranchNickname(final String branchNickname) {
        this.branchNickname = branchNickname;
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

    public BigDecimal getInvoicesWeight() {
        return invoicesWeight;
    }

    public void setInvoicesWeight(final BigDecimal invoicesWeight) {
        this.invoicesWeight = invoicesWeight;
    }

    public BigDecimal getTotalTaxedWeight() {
        return totalTaxedWeight;
    }

    public void setTotalTaxedWeight(final BigDecimal totalTaxedWeight) {
        this.totalTaxedWeight = totalTaxedWeight;
    }

    public BigDecimal getTotalCubicVolume() {
        return totalCubicVolume;
    }

    public void setTotalCubicVolume(final BigDecimal totalCubicVolume) {
        this.totalCubicVolume = totalCubicVolume;
    }

    public BigDecimal getInvoicesValue() {
        return invoicesValue;
    }

    public void setInvoicesValue(final BigDecimal invoicesValue) {
        this.invoicesValue = invoicesValue;
    }

    public BigDecimal getManifestFreightsTotal() {
        return manifestFreightsTotal;
    }

    public void setManifestFreightsTotal(final BigDecimal manifestFreightsTotal) {
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

    public BigDecimal getDailySubtotal() {
        return dailySubtotal;
    }

    public void setDailySubtotal(final BigDecimal dailySubtotal) {
        this.dailySubtotal = dailySubtotal;
    }

    public BigDecimal getOperationalExpensesTotal() {
        return operationalExpensesTotal;
    }

    public void setOperationalExpensesTotal(final BigDecimal operationalExpensesTotal) {
        this.operationalExpensesTotal = operationalExpensesTotal;
    }

    public BigDecimal getInssValue() {
        return inssValue;
    }

    public void setInssValue(final BigDecimal inssValue) {
        this.inssValue = inssValue;
    }

    public BigDecimal getSestSenatValue() {
        return sestSenatValue;
    }

    public void setSestSenatValue(final BigDecimal sestSenatValue) {
        this.sestSenatValue = sestSenatValue;
    }

    public BigDecimal getIrValue() {
        return irValue;
    }

    public void setIrValue(final BigDecimal irValue) {
        this.irValue = irValue;
    }

    public BigDecimal getPayingTotal() {
        return payingTotal;
    }

    public void setPayingTotal(final BigDecimal payingTotal) {
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

    public BigDecimal getFreightSubtotal() {
        return freightSubtotal;
    }

    public void setFreightSubtotal(final BigDecimal freightSubtotal) {
        this.freightSubtotal = freightSubtotal;
    }

    public BigDecimal getFuelSubtotal() {
        return fuelSubtotal;
    }

    public void setFuelSubtotal(final BigDecimal fuelSubtotal) {
        this.fuelSubtotal = fuelSubtotal;
    }

    public BigDecimal getTollSubtotal() {
        return tollSubtotal;
    }

    public void setTollSubtotal(final BigDecimal tollSubtotal) {
        this.tollSubtotal = tollSubtotal;
    }

    public BigDecimal getDriverServicesTotal() {
        return driverServicesTotal;
    }

    public void setDriverServicesTotal(final BigDecimal driverServicesTotal) {
        this.driverServicesTotal = driverServicesTotal;
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

    public Integer getUniqDestinationsCount() {
        return uniqDestinationsCount;
    }

    public void setUniqDestinationsCount(final Integer uniqDestinationsCount) {
        this.uniqDestinationsCount = uniqDestinationsCount;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(final String metadata) {
        this.metadata = metadata;
    }

    public String getIdentificadorUnico() {
        return identificadorUnico;
    }

    public void setIdentificadorUnico(final String identificadorUnico) {
        this.identificadorUnico = identificadorUnico;
    }

    public OffsetDateTime getMobileReadAt() {
        return mobileReadAt;
    }

    public void setMobileReadAt(final OffsetDateTime mobileReadAt) {
        this.mobileReadAt = mobileReadAt;
    }

    public BigDecimal getKm() {
        return km;
    }

    public void setKm(final BigDecimal km) {
        this.km = km;
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

    public BigDecimal getPickSubtotal() {
        return pickSubtotal;
    }

    public void setPickSubtotal(final BigDecimal pickSubtotal) {
        this.pickSubtotal = pickSubtotal;
    }

    public BigDecimal getDeliverySubtotal() {
        return deliverySubtotal;
    }

    public void setDeliverySubtotal(final BigDecimal deliverySubtotal) {
        this.deliverySubtotal = deliverySubtotal;
    }

    public BigDecimal getDispatchSubtotal() {
        return dispatchSubtotal;
    }

    public void setDispatchSubtotal(final BigDecimal dispatchSubtotal) {
        this.dispatchSubtotal = dispatchSubtotal;
    }

    public BigDecimal getConsolidationSubtotal() {
        return consolidationSubtotal;
    }

    public void setConsolidationSubtotal(final BigDecimal consolidationSubtotal) {
        this.consolidationSubtotal = consolidationSubtotal;
    }

    public BigDecimal getReversePickSubtotal() {
        return reversePickSubtotal;
    }

    public void setReversePickSubtotal(final BigDecimal reversePickSubtotal) {
        this.reversePickSubtotal = reversePickSubtotal;
    }

    public BigDecimal getAdvanceSubtotal() {
        return advanceSubtotal;
    }

    public void setAdvanceSubtotal(final BigDecimal advanceSubtotal) {
        this.advanceSubtotal = advanceSubtotal;
    }

    public BigDecimal getFleetCostsSubtotal() {
        return fleetCostsSubtotal;
    }

    public void setFleetCostsSubtotal(final BigDecimal fleetCostsSubtotal) {
        this.fleetCostsSubtotal = fleetCostsSubtotal;
    }

    public BigDecimal getAdditionalsSubtotal() {
        return additionalsSubtotal;
    }

    public void setAdditionalsSubtotal(final BigDecimal additionalsSubtotal) {
        this.additionalsSubtotal = additionalsSubtotal;
    }

    public BigDecimal getDiscountsSubtotal() {
        return discountsSubtotal;
    }

    public void setDiscountsSubtotal(final BigDecimal discountsSubtotal) {
        this.discountsSubtotal = discountsSubtotal;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(final BigDecimal discountValue) {
        this.discountValue = discountValue;
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

    public OffsetDateTime getProgramacaoStartingAt() {
        return programacaoStartingAt;
    }

    public void setProgramacaoStartingAt(final OffsetDateTime programacaoStartingAt) {
        this.programacaoStartingAt = programacaoStartingAt;
    }

    public OffsetDateTime getProgramacaoEndingAt() {
        return programacaoEndingAt;
    }

    public void setProgramacaoEndingAt(final OffsetDateTime programacaoEndingAt) {
        this.programacaoEndingAt = programacaoEndingAt;
    }

    public String getTrailer1LicensePlate() {
        return trailer1LicensePlate;
    }

    public void setTrailer1LicensePlate(final String trailer1LicensePlate) {
        this.trailer1LicensePlate = trailer1LicensePlate;
    }

    public BigDecimal getTrailer1WeightCapacity() {
        return trailer1WeightCapacity;
    }

    public void setTrailer1WeightCapacity(final BigDecimal trailer1WeightCapacity) {
        this.trailer1WeightCapacity = trailer1WeightCapacity;
    }

    public String getTrailer2LicensePlate() {
        return trailer2LicensePlate;
    }

    public void setTrailer2LicensePlate(final String trailer2LicensePlate) {
        this.trailer2LicensePlate = trailer2LicensePlate;
    }

    public BigDecimal getTrailer2WeightCapacity() {
        return trailer2WeightCapacity;
    }

    public void setTrailer2WeightCapacity(final BigDecimal trailer2WeightCapacity) {
        this.trailer2WeightCapacity = trailer2WeightCapacity;
    }

    public BigDecimal getVehicleWeightCapacity() {
        return vehicleWeightCapacity;
    }

    public void setVehicleWeightCapacity(final BigDecimal vehicleWeightCapacity) {
        this.vehicleWeightCapacity = vehicleWeightCapacity;
    }

    public BigDecimal getVehicleCubicWeight() {
        return vehicleCubicWeight;
    }

    public void setVehicleCubicWeight(final BigDecimal vehicleCubicWeight) {
        this.vehicleCubicWeight = vehicleCubicWeight;
    }

    public String getUnloadingRecipientNames() {
        return unloadingRecipientNames;
    }

    public void setUnloadingRecipientNames(final String unloadingRecipientNames) {
        this.unloadingRecipientNames = unloadingRecipientNames;
    }

    public String getDeliveryRegionNames() {
        return deliveryRegionNames;
    }

    public void setDeliveryRegionNames(final String deliveryRegionNames) {
        this.deliveryRegionNames = deliveryRegionNames;
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

    public BigDecimal getCapacidadeKg() {
        return capacidadeKg;
    }

    public void setCapacidadeKg(final BigDecimal capacidadeKg) {
        this.capacidadeKg = capacidadeKg;
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

    /**
     * Calcula o identificador único para este manifesto.
     * Este método deve ser chamado DEPOIS que o metadata e mdfe_number forem definidos.
     * 
     * Prioridade 1: pick_sequence_code + mdfe_number (quando AMBOS estão disponíveis)
     *   - CRÍTICO: Incluir mdfe_number para diferenciar múltiplos MDF-es com mesmo pick
     * Prioridade 2: pick_sequence_code (quando apenas pick está disponível)
     * Prioridade 3: sequence_code + mdfe_number (quando apenas mdfe_number está disponível)
     * Prioridade 4: hash SHA-256 do metadata completo (quando ambos são NULL)
     * 
     * IMPORTANTE: Múltiplos MDF-es do mesmo manifesto devem ser preservados como registros distintos.
     * Por isso, mdfe_number é SEMPRE incluído no identificador quando disponível, mesmo se pick_sequence_code também estiver.
     */
    public void calcularIdentificadorUnico() {
        // CASO 1: AMBOS pick_sequence_code E mdfe_number preenchidos
        // CRÍTICO: Incluir mdfe_number para diferenciar múltiplos MDF-es com mesmo pick
        if (this.pickSequenceCode != null && this.mdfeNumber != null) {
            this.identificadorUnico = this.pickSequenceCode + "_MDFE_" + this.mdfeNumber;
        }
        // CASO 2: Apenas pick_sequence_code preenchido (mdfe_number é NULL)
        else if (this.pickSequenceCode != null) {
            this.identificadorUnico = String.valueOf(this.pickSequenceCode);
        }
        // CASO 3: Apenas mdfe_number preenchido (pick_sequence_code é NULL)
        else if (this.mdfeNumber != null) {
            // Usar sequence_code + mdfe_number quando não há pick mas há MDF-e
            // Isso garante que múltiplos MDF-es do mesmo manifesto sejam preservados
            this.identificadorUnico = this.sequenceCode + "_MDFE_" + this.mdfeNumber;
        }
        // CASO 4: Ambos NULL - usar hash do metadata
        else {
            // Calcular hash do metadata quando não há pick nem MDF-e
            this.identificadorUnico = calcularHashMetadata(this.metadata);
        }
    }

    /**
     * Calcula hash SHA-256 do metadata JSON.
     * IMPORTANTE: Exclui campos voláteis que podem mudar durante a extração
     * para garantir que o mesmo manifesto tenha o mesmo identificador único
     * mesmo que alguns campos sejam atualizados durante a operação.
     * 
     * Usado quando pick_sequence_code não está disponível para diferenciar
     * duplicados naturais que têm mesmo sequence_code mas metadata diferentes.
     * 
     * @param metadata String JSON do metadata
     * @return String hexadecimal do hash SHA-256 (64 caracteres) ou fallback se metadata estiver vazio
     */
    private String calcularHashMetadata(final String metadata) {
        if (metadata == null || metadata.trim().isEmpty()) {
            // Fallback: usar hash do sequence_code se metadata estiver vazio
            return "NULL_METADATA_" + (this.sequenceCode != null ? this.sequenceCode : "UNKNOWN");
        }
        try {
            // Parse do JSON para Map
            @SuppressWarnings("unchecked")
            final
            Map<String, Object> metadataMap = (Map<String, Object>) objectMapper.readValue(metadata, Map.class);
            
            // Criar cópia do metadata para não modificar o original
            final Map<String, Object> metadataParaHash = new LinkedHashMap<>(metadataMap);
            
            // Lista COMPLETA de campos voláteis que DEVEM ser excluídos do hash
            // IMPORTANTE: Campos que podem mudar DURANTE a extração não devem fazer parte do hash
            final List<String> camposVolateis = Arrays.asList(
                // Campos de timestamp que mudam durante operação
                "mobile_read_at",
                "departured_at", 
                "closed_at",
                "finished_at",
                
                // Campos de quilometragem que mudam durante viagem
                "vehicle_departure_km",
                "closing_km",
                "traveled_km",
                
                // Contadores que mudam durante operação
                "finalized_manifest_items_count",
                
                // Campos do MDF-e (Manifesto Eletrônico) que podem mudar durante extração
                // Novo MDF-e pode ser emitido/cancelado/corrigido enquanto extração ocorre
                "mft_mfs_number",      // Número do MDF-e
                "mft_mfs_key",         // Chave do MDF-e (44 dígitos)
                "mdfe_status",         // Status do MDF-e
                
                // Campos de ajustes posteriores que podem ser preenchidos depois
                "mft_aoe_comments",    // Comentários de ajuste
                "mft_aoe_rer_name"     // Nome do ajustador
            );
            
            // Remover campos voláteis
            camposVolateis.forEach(metadataParaHash::remove);
            
            // Gerar hash apenas dos campos estáveis
            final String metadataJson = objectMapper.writeValueAsString(metadataParaHash);
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(metadataJson.getBytes(StandardCharsets.UTF_8));
            final String hashHex = bytesToHex(hash);
            
            logger.debug("Identificador único gerado: {} (após remover {} campos voláteis)", 
                         hashHex.substring(0, 8), camposVolateis.size());
            
            return hashHex;
            
        } catch (final JsonProcessingException e) {
            logger.error("Erro ao processar metadata para hash: {}", e.getMessage(), e);
            // Fallback: calcular hash do metadata original (sem remover campos voláteis)
            try {
                final MessageDigest digest = MessageDigest.getInstance("SHA-256");
                final byte[] hash = digest.digest(metadata.getBytes(StandardCharsets.UTF_8));
                return bytesToHex(hash);
            } catch (final NoSuchAlgorithmException ex) {
                throw new RuntimeException("Erro ao calcular hash SHA-256 do metadata", ex);
            }
        } catch (final NoSuchAlgorithmException e) {
            // Fallback: usar hash simples se SHA-256 não disponível (não deve acontecer)
            throw new RuntimeException("Erro ao calcular hash SHA-256 do metadata", e);
        }
    }

    /**
     * Converte array de bytes para string hexadecimal.
     * 
     * @param bytes Array de bytes a ser convertido
     * @return String hexadecimal (2 caracteres por byte)
     */
    private String bytesToHex(final byte[] bytes) {
        final StringBuilder result = new StringBuilder();
        for (final byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
