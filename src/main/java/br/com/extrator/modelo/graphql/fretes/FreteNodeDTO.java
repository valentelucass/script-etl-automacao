/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/fretes/FreteNodeDTO.java
Classe  : FreteNodeDTO (class)
Pacote  : br.com.extrator.modelo.graphql.fretes
Modulo  : DTO/Mapper GraphQL
Papel   : Implementa responsabilidade de frete node dto.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela payloads da API GraphQL.
2) Mapeia estrutura remota para modelo interno.
3) Apoia persistencia e validacao do extrator.

Estrutura interna:
Metodos principais:
- add(...2 args): realiza operacao relacionada a "add".
- getId(): expone valor atual do estado interno.
- setId(...1 args): ajusta valor em estado interno.
- getServiceAt(): expone valor atual do estado interno.
- setServiceAt(...1 args): ajusta valor em estado interno.
- getCreatedAt(): expone valor atual do estado interno.
- setCreatedAt(...1 args): ajusta valor em estado interno.
- getStatus(): expone valor atual do estado interno.
- setStatus(...1 args): ajusta valor em estado interno.
- getModal(): expone valor atual do estado interno.
- setModal(...1 args): ajusta valor em estado interno.
- getType(): expone valor atual do estado interno.
- setType(...1 args): ajusta valor em estado interno.
- getAccountingCreditId(): expone valor atual do estado interno.
Atributos-chave:
- id: campo de estado para "id".
- serviceAt: campo de estado para "service at".
- createdAt: campo de estado para "created at".
- status: campo de estado para "status".
- modal: campo de estado para "modal".
- type: campo de estado para "type".
- accountingCreditId: campo de estado para "accounting credit id".
- accountingCreditInstallmentId: campo de estado para "accounting credit installment id".
- totalValue: campo de estado para "total value".
- invoicesValue: campo de estado para "invoices value".
- invoicesWeight: campo de estado para "invoices weight".
- corporationId: campo de estado para "corporation id".
- destinationCityId: campo de estado para "destination city id".
- deliveryPredictionDate: campo de estado para "delivery prediction date".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.graphql.fretes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO (Data Transfer Object) para representar um "node" de Frete,
 * conforme retornado pela API GraphQL. Mapeia os campos essenciais
 * e inclui um contêiner dinâmico para capturar todas as outras
 * propriedades, garantindo resiliência e completude.
 */
public class FreteNodeDTO {

    // --- Campos Essenciais Mapeados ---
    @JsonProperty("id")
    private Long id;

    @JsonProperty("serviceAt")
    private String serviceAt; // Recebe como String para ser convertido para OffsetDateTime

    @JsonProperty("createdAt")
    private String createdAt; // Recebe como String para ser convertido para OffsetDateTime

    @JsonProperty("status")
    private String status;

    @JsonProperty("modal")
    private String modal;

    @JsonProperty("type")
    private String type;

    @JsonProperty("accountingCreditId")
    private Long accountingCreditId;

    @JsonProperty("accountingCreditInstallmentId")
    private Long accountingCreditInstallmentId;

    @JsonProperty("total")
    private BigDecimal totalValue;

    @JsonProperty("invoicesValue")
    private BigDecimal invoicesValue;

    @JsonProperty("invoicesWeight")
    private BigDecimal invoicesWeight;

    @JsonProperty("corporationId")
    private Long corporationId;

    @JsonProperty("destinationCityId")
    private Long destinationCityId;

    @JsonProperty("deliveryPredictionDate")
    private String deliveryPredictionDate; // Recebe como String para ser convertido para LocalDate

    @JsonProperty("serviceDate")
    private String serviceDate; // Recebe como String para ser convertido para LocalDate

    // --- Campos Expandidos (Objetos Aninhados) ---
    @JsonProperty("payer")
    private PayerDTO payer;

    @JsonProperty("sender")
    private SenderDTO sender;

    @JsonProperty("receiver")
    private ReceiverDTO receiver;

    @JsonProperty("corporation")
    private CorporationDTO corporation;

    @JsonProperty("freightInvoices")
    private List<FreightInvoiceDTO> freightInvoices;

    @JsonProperty("customerPriceTable")
    private CustomerPriceTableDTO customerPriceTable;

    @JsonProperty("freightClassification")
    private FreightClassificationDTO freightClassification;

    @JsonProperty("costCenter")
    private CostCenterDTO costCenter;

    @JsonProperty("user")
    private UserDTO user;

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class CteDTO {
        @JsonProperty("key")
        private String key;
        @JsonProperty("number")
        private Integer number;
        @JsonProperty("series")
        private Integer series;

        @JsonProperty("issuedAt")
        private String issuedAt;

        @JsonProperty("emissionType")
        private String emissionType;

        @JsonProperty("id")
        private Long id;

        @JsonProperty("createdAt")
        private String createdAt;

        public String getKey() { return key; }
        public void setKey(final String key) { this.key = key; }
        public Integer getNumber() { return number; }
        public void setNumber(final Integer number) { this.number = number; }
        public Integer getSeries() { return series; }
        public void setSeries(final Integer series) { this.series = series; }

        public String getIssuedAt() { return issuedAt; }
        public void setIssuedAt(final String issuedAt) { this.issuedAt = issuedAt; }
        public String getEmissionType() { return emissionType; }
        public void setEmissionType(final String emissionType) { this.emissionType = emissionType; }
        public Long getId() { return id; }
        public void setId(final Long id) { this.id = id; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(final String createdAt) { this.createdAt = createdAt; }
    }

    @JsonProperty("cte")
    private CteDTO cte;

    @JsonProperty("originCity")
    private CityDTO originCity;

    @JsonProperty("destinationCity")
    private CityDTO destinationCity;

    @JsonProperty("fiscalDetail")
    private FiscalDetailDTO fiscalDetail;

    // --- Campos Adicionais do CSV (22 campos mapeados) ---
    @JsonProperty("referenceNumber")
    private String referenceNumber;

    @JsonProperty("invoicesTotalVolumes")
    private Integer invoicesTotalVolumes;

    @JsonProperty("taxedWeight")
    private BigDecimal taxedWeight;

    @JsonProperty("realWeight")
    private BigDecimal realWeight;

    @JsonProperty("cubagesCubedWeight")
    private BigDecimal cubagesCubedWeight;

    @JsonProperty("totalCubicVolume")
    private BigDecimal totalCubicVolume;

    @JsonProperty("subtotal")
    private BigDecimal subtotal;

    @JsonProperty("freightWeightSubtotal")
    private BigDecimal freightWeightSubtotal;

    @JsonProperty("adValoremSubtotal")
    private BigDecimal adValoremSubtotal;

    @JsonProperty("tollSubtotal")
    private BigDecimal tollSubtotal;

    @JsonProperty("itrSubtotal")
    private BigDecimal itrSubtotal;

    @JsonProperty("serviceType")
    private String serviceType; // API retorna como String (ex: "0")

    @JsonProperty("insuranceEnabled")
    private Boolean insuranceEnabled;

    @JsonProperty("grisSubtotal")
    private BigDecimal grisSubtotal;

    @JsonProperty("tdeSubtotal")
    private BigDecimal tdeSubtotal;

    @JsonProperty("modalCte")
    private String modalCte;

    @JsonProperty("redispatchSubtotal")
    private BigDecimal redispatchSubtotal;

    @JsonProperty("suframaSubtotal")
    private BigDecimal suframaSubtotal;

    @JsonProperty("paymentType")
    private String paymentType;

    @JsonProperty("previousDocumentType")
    private String previousDocumentType;

    @JsonProperty("productsValue")
    private BigDecimal productsValue;

    @JsonProperty("trtSubtotal")
    private BigDecimal trtSubtotal;

    @JsonProperty("nfseSeries")
    private String nfseSeries;

    @JsonProperty("nfseNumber")
    private String nfseNumber; // API retorna como String (pode ser "" ou número como String)

    @JsonProperty("insuranceId")
    private Long insuranceId;

    @JsonProperty("otherFees")
    private BigDecimal otherFees;

    @JsonProperty("km")
    private BigDecimal km;

    @JsonProperty("paymentAccountableType")
    private String paymentAccountableType; // API retorna como String (ex: "0", "1", "3", "4")

    @JsonProperty("insuredValue")
    private BigDecimal insuredValue;

    @JsonProperty("globalized")
    private Boolean globalized;

    @JsonProperty("secCatSubtotal")
    private BigDecimal secCatSubtotal;

    @JsonProperty("globalizedType")
    private String globalizedType;

    @JsonProperty("priceTableAccountableType")
    private String priceTableAccountableType; // API retorna como String (ex: "4")

    @JsonProperty("insuranceAccountableType")
    private String insuranceAccountableType; // API retorna como String (ex: "4", "5")

    // --- Contêiner Dinâmico ("Resto") ---
    private final Map<String, Object> otherProperties = new HashMap<>();

    @JsonAnySetter
    public void add(final String key, final Object value) {
        this.otherProperties.put(key, value);
    }

    // --- Getters e Setters ---

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getServiceAt() {
        return serviceAt;
    }

    public void setServiceAt(final String serviceAt) {
        this.serviceAt = serviceAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final String createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public String getModal() {
        return modal;
    }

    public void setModal(final String modal) {
        this.modal = modal;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public Long getAccountingCreditId() {
        return accountingCreditId;
    }
    public void setAccountingCreditId(final Long accountingCreditId) {
        this.accountingCreditId = accountingCreditId;
    }
    public Long getAccountingCreditInstallmentId() {
        return accountingCreditInstallmentId;
    }
    public void setAccountingCreditInstallmentId(final Long accountingCreditInstallmentId) {
        this.accountingCreditInstallmentId = accountingCreditInstallmentId;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(final BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public BigDecimal getInvoicesValue() {
        return invoicesValue;
    }

    public void setInvoicesValue(final BigDecimal invoicesValue) {
        this.invoicesValue = invoicesValue;
    }

    public BigDecimal getInvoicesWeight() {
        return invoicesWeight;
    }

    public void setInvoicesWeight(final BigDecimal invoicesWeight) {
        this.invoicesWeight = invoicesWeight;
    }

    public Long getCorporationId() {
        return corporationId;
    }

    public void setCorporationId(final Long corporationId) {
        this.corporationId = corporationId;
    }

    public Long getDestinationCityId() {
        return destinationCityId;
    }

    public void setDestinationCityId(final Long destinationCityId) {
        this.destinationCityId = destinationCityId;
    }

    public String getDeliveryPredictionDate() {
        return deliveryPredictionDate;
    }

    public void setDeliveryPredictionDate(final String deliveryPredictionDate) {
        this.deliveryPredictionDate = deliveryPredictionDate;
    }

    public String getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(final String serviceDate) {
        this.serviceDate = serviceDate;
    }

    // --- Getters e Setters para Campos Expandidos ---

    public PayerDTO getPayer() {
        return payer;
    }

    public void setPayer(final PayerDTO payer) {
        this.payer = payer;
    }

    public SenderDTO getSender() {
        return sender;
    }

    public void setSender(final SenderDTO sender) {
        this.sender = sender;
    }

    public ReceiverDTO getReceiver() {
        return receiver;
    }

    public void setReceiver(final ReceiverDTO receiver) {
        this.receiver = receiver;
    }

    public CorporationDTO getCorporation() {
        return corporation;
    }

    public void setCorporation(final CorporationDTO corporation) {
        this.corporation = corporation;
    }

    public List<FreightInvoiceDTO> getFreightInvoices() {
        return freightInvoices;
    }

    public void setFreightInvoices(final List<FreightInvoiceDTO> freightInvoices) {
        this.freightInvoices = freightInvoices;
    }

    public CustomerPriceTableDTO getCustomerPriceTable() {
        return customerPriceTable;
    }

    public void setCustomerPriceTable(final CustomerPriceTableDTO customerPriceTable) {
        this.customerPriceTable = customerPriceTable;
    }

    public FreightClassificationDTO getFreightClassification() {
        return freightClassification;
    }

    public void setFreightClassification(final FreightClassificationDTO freightClassification) {
        this.freightClassification = freightClassification;
    }

    public CostCenterDTO getCostCenter() {
        return costCenter;
    }

    public void setCostCenter(final CostCenterDTO costCenter) {
        this.costCenter = costCenter;
    }

    public UserDTO getUser() {
        return user;
    }

    public void setUser(final UserDTO user) {
        this.user = user;
    }

    public CteDTO getCte() { return cte; }
    public void setCte(final CteDTO cte) { this.cte = cte; }

    public CityDTO getOriginCity() { return originCity; }
    public void setOriginCity(final CityDTO originCity) { this.originCity = originCity; }
    public CityDTO getDestinationCity() { return destinationCity; }
    public void setDestinationCity(final CityDTO destinationCity) { this.destinationCity = destinationCity; }
    public FiscalDetailDTO getFiscalDetail() { return fiscalDetail; }
    public void setFiscalDetail(final FiscalDetailDTO fiscalDetail) { this.fiscalDetail = fiscalDetail; }

    // --- Getters e Setters para Campos Adicionais do CSV ---

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(final String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public Integer getInvoicesTotalVolumes() {
        return invoicesTotalVolumes;
    }

    public void setInvoicesTotalVolumes(final Integer invoicesTotalVolumes) {
        this.invoicesTotalVolumes = invoicesTotalVolumes;
    }

    public BigDecimal getTaxedWeight() {
        return taxedWeight;
    }

    public void setTaxedWeight(final BigDecimal taxedWeight) {
        this.taxedWeight = taxedWeight;
    }

    public BigDecimal getRealWeight() {
        return realWeight;
    }

    public void setRealWeight(final BigDecimal realWeight) {
        this.realWeight = realWeight;
    }

    public BigDecimal getCubagesCubedWeight() { return cubagesCubedWeight; }
    public void setCubagesCubedWeight(final BigDecimal cubagesCubedWeight) { this.cubagesCubedWeight = cubagesCubedWeight; }

    public BigDecimal getTotalCubicVolume() {
        return totalCubicVolume;
    }

    public void setTotalCubicVolume(final BigDecimal totalCubicVolume) {
        this.totalCubicVolume = totalCubicVolume;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(final BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getFreightWeightSubtotal() { return freightWeightSubtotal; }
    public void setFreightWeightSubtotal(final BigDecimal freightWeightSubtotal) { this.freightWeightSubtotal = freightWeightSubtotal; }
    public BigDecimal getAdValoremSubtotal() { return adValoremSubtotal; }
    public void setAdValoremSubtotal(final BigDecimal adValoremSubtotal) { this.adValoremSubtotal = adValoremSubtotal; }
    public BigDecimal getTollSubtotal() { return tollSubtotal; }
    public void setTollSubtotal(final BigDecimal tollSubtotal) { this.tollSubtotal = tollSubtotal; }
    public BigDecimal getItrSubtotal() { return itrSubtotal; }
    public void setItrSubtotal(final BigDecimal itrSubtotal) { this.itrSubtotal = itrSubtotal; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(final String serviceType) { this.serviceType = serviceType; }
    public Boolean getInsuranceEnabled() { return insuranceEnabled; }
    public void setInsuranceEnabled(final Boolean insuranceEnabled) { this.insuranceEnabled = insuranceEnabled; }
    public BigDecimal getGrisSubtotal() { return grisSubtotal; }
    public void setGrisSubtotal(final BigDecimal grisSubtotal) { this.grisSubtotal = grisSubtotal; }
    public BigDecimal getTdeSubtotal() { return tdeSubtotal; }
    public void setTdeSubtotal(final BigDecimal tdeSubtotal) { this.tdeSubtotal = tdeSubtotal; }
    public String getModalCte() { return modalCte; }
    public void setModalCte(final String modalCte) { this.modalCte = modalCte; }
    public BigDecimal getRedispatchSubtotal() { return redispatchSubtotal; }
    public void setRedispatchSubtotal(final BigDecimal redispatchSubtotal) { this.redispatchSubtotal = redispatchSubtotal; }
    public BigDecimal getSuframaSubtotal() { return suframaSubtotal; }
    public void setSuframaSubtotal(final BigDecimal suframaSubtotal) { this.suframaSubtotal = suframaSubtotal; }
    public String getPaymentType() { return paymentType; }
    public void setPaymentType(final String paymentType) { this.paymentType = paymentType; }
    public String getPreviousDocumentType() { return previousDocumentType; }
    public void setPreviousDocumentType(final String previousDocumentType) { this.previousDocumentType = previousDocumentType; }
    public BigDecimal getProductsValue() { return productsValue; }
    public void setProductsValue(final BigDecimal productsValue) { this.productsValue = productsValue; }
    public BigDecimal getTrtSubtotal() { return trtSubtotal; }
    public void setTrtSubtotal(final BigDecimal trtSubtotal) { this.trtSubtotal = trtSubtotal; }
    public String getNfseSeries() { return nfseSeries; }
    public void setNfseSeries(final String nfseSeries) { this.nfseSeries = nfseSeries; }
    public String getNfseNumber() { return nfseNumber; }
    public void setNfseNumber(final String nfseNumber) { this.nfseNumber = nfseNumber; }
    public Long getInsuranceId() { return insuranceId; }
    public void setInsuranceId(final Long insuranceId) { this.insuranceId = insuranceId; }
    public BigDecimal getOtherFees() { return otherFees; }
    public void setOtherFees(final BigDecimal otherFees) { this.otherFees = otherFees; }
    public BigDecimal getKm() { return km; }
    public void setKm(final BigDecimal km) { this.km = km; }
    public String getPaymentAccountableType() { return paymentAccountableType; }
    public void setPaymentAccountableType(final String paymentAccountableType) { this.paymentAccountableType = paymentAccountableType; }
    public BigDecimal getInsuredValue() { return insuredValue; }
    public void setInsuredValue(final BigDecimal insuredValue) { this.insuredValue = insuredValue; }
    public Boolean getGlobalized() { return globalized; }
    public void setGlobalized(final Boolean globalized) { this.globalized = globalized; }
    public BigDecimal getSecCatSubtotal() { return secCatSubtotal; }
    public void setSecCatSubtotal(final BigDecimal secCatSubtotal) { this.secCatSubtotal = secCatSubtotal; }
    public String getGlobalizedType() { return globalizedType; }
    public void setGlobalizedType(final String globalizedType) { this.globalizedType = globalizedType; }
    public String getPriceTableAccountableType() { return priceTableAccountableType; }
    public void setPriceTableAccountableType(final String priceTableAccountableType) { this.priceTableAccountableType = priceTableAccountableType; }
    public String getInsuranceAccountableType() { return insuranceAccountableType; }
    public void setInsuranceAccountableType(final String insuranceAccountableType) { this.insuranceAccountableType = insuranceAccountableType; }

    @JsonAnyGetter
    public Map<String, Object> getOtherProperties() {
        return otherProperties;
    }
}
