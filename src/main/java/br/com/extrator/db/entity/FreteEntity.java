package br.com.extrator.db.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Entity (Entidade) que representa uma linha na tabela 'fretes' do banco de dados.
 * Contém os campos-chave "promovidos" para acesso rápido e indexação,
 * e uma coluna 'metadata' para armazenar o JSON bruto completo, garantindo
 * 100% de completude e resiliência a futuras mudanças na API.
 */
public class FreteEntity {

    // --- Coluna de Chave Primária ---
    private Long id;

    // --- Colunas Essenciais para Indexação e Relatórios ---
    private OffsetDateTime servicoEm;
    private OffsetDateTime criadoEm;
    private String status;
    private String modal;
    private String tipoFrete;
    private BigDecimal valorTotal;
    private BigDecimal valorNotas;
    private BigDecimal pesoNotas;
    private Long idCorporacao;
    private Long idCidadeDestino;
    private LocalDate dataPrevisaoEntrega;

    // --- Campos Expandidos (22 campos do CSV) ---
    private Long pagadorId;
    private String pagadorNome;
    private Long remetenteId;
    private String remetenteNome;
    private String origemCidade;
    private String origemUf;
    private Long destinatarioId;
    private String destinatarioNome;
    private String destinoCidade;
    private String destinoUf;
    private String filialNome;
    private String numeroNotaFiscal;
    private String tabelaPrecoNome;
    private String classificacaoNome;
    private String centroCustoNome;
    private String usuarioNome;
    private String referenceNumber;
    private Integer invoicesTotalVolumes;
    private BigDecimal taxedWeight;
    private BigDecimal realWeight;
    private BigDecimal cubagesCubedWeight;
    private BigDecimal totalCubicVolume;
    private BigDecimal subtotal;

    private String chaveCte;
    private Integer numeroCte;
    private Integer serieCte;
    private OffsetDateTime cteIssuedAt;

    private Integer serviceType;
    private Boolean insuranceEnabled;
    private BigDecimal grisSubtotal;
    private BigDecimal tdeSubtotal;
    private String modalCte;
    private BigDecimal redispatchSubtotal;
    private BigDecimal suframaSubtotal;
    private String paymentType;
    private String previousDocumentType;
    private BigDecimal productsValue;
    private BigDecimal trtSubtotal;
    private BigDecimal freightWeightSubtotal;
    private BigDecimal adValoremSubtotal;
    private BigDecimal tollSubtotal;
    private BigDecimal itrSubtotal;
    private String nfseSeries;
    private Integer nfseNumber;
    private Long insuranceId;
    private BigDecimal otherFees;
    private BigDecimal km;
    private Integer paymentAccountableType;
    private BigDecimal insuredValue;
    private Boolean globalized;
    private BigDecimal secCatSubtotal;
    private String globalizedType;
    private Integer priceTableAccountableType;
    private Integer insuranceAccountableType;

    private String filialCnpj;
    private String remetenteDocumento;
    private String destinatarioDocumento;
    private String pagadorDocumento;

    private String fiscalCstType;
    private String fiscalCfopCode;
    private BigDecimal fiscalTaxValue;
    private BigDecimal fiscalPisValue;
    private BigDecimal fiscalCofinsValue;

    private BigDecimal fiscalCalculationBasis;
    private BigDecimal fiscalTaxRate;
    private BigDecimal fiscalPisRate;
    private BigDecimal fiscalCofinsRate;
    private Boolean fiscalHasDifal;
    private BigDecimal fiscalDifalOrigin;
    private BigDecimal fiscalDifalDestination;

    private Long cteId;
    private String cteEmissionType;
    private OffsetDateTime cteCreatedAt;

    private String filialApelido;

    // --- Coluna de Metadados ---
    private String metadata;

    // --- Getters e Setters ---

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public OffsetDateTime getServicoEm() {
        return servicoEm;
    }

    public void setServicoEm(final OffsetDateTime servicoEm) {
        this.servicoEm = servicoEm;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(final OffsetDateTime criadoEm) {
        this.criadoEm = criadoEm;
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

    public String getTipoFrete() {
        return tipoFrete;
    }

    public void setTipoFrete(final String tipoFrete) {
        this.tipoFrete = tipoFrete;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(final BigDecimal valorTotal) {
        this.valorTotal = valorTotal;
    }

    public BigDecimal getValorNotas() {
        return valorNotas;
    }

    public void setValorNotas(final BigDecimal valorNotas) {
        this.valorNotas = valorNotas;
    }

    public BigDecimal getPesoNotas() {
        return pesoNotas;
    }

    public void setPesoNotas(final BigDecimal pesoNotas) {
        this.pesoNotas = pesoNotas;
    }

    public Long getIdCorporacao() {
        return idCorporacao;
    }

    public void setIdCorporacao(final Long idCorporacao) {
        this.idCorporacao = idCorporacao;
    }

    public Long getIdCidadeDestino() {
        return idCidadeDestino;
    }

    public void setIdCidadeDestino(final Long idCidadeDestino) {
        this.idCidadeDestino = idCidadeDestino;
    }

    public LocalDate getDataPrevisaoEntrega() {
        return dataPrevisaoEntrega;
    }

    public void setDataPrevisaoEntrega(final LocalDate dataPrevisaoEntrega) {
        this.dataPrevisaoEntrega = dataPrevisaoEntrega;
    }

    // --- Getters e Setters para Campos Expandidos ---

    public Long getPagadorId() {
        return pagadorId;
    }

    public void setPagadorId(Long pagadorId) {
        this.pagadorId = pagadorId;
    }

    public String getPagadorNome() {
        return pagadorNome;
    }

    public void setPagadorNome(String pagadorNome) {
        this.pagadorNome = pagadorNome;
    }

    public Long getRemetenteId() {
        return remetenteId;
    }

    public void setRemetenteId(Long remetenteId) {
        this.remetenteId = remetenteId;
    }

    public String getRemetenteNome() {
        return remetenteNome;
    }

    public void setRemetenteNome(String remetenteNome) {
        this.remetenteNome = remetenteNome;
    }

    public String getOrigemCidade() {
        return origemCidade;
    }

    public void setOrigemCidade(String origemCidade) {
        this.origemCidade = origemCidade;
    }

    public String getOrigemUf() {
        return origemUf;
    }

    public void setOrigemUf(String origemUf) {
        this.origemUf = origemUf;
    }

    public Long getDestinatarioId() {
        return destinatarioId;
    }

    public void setDestinatarioId(Long destinatarioId) {
        this.destinatarioId = destinatarioId;
    }

    public String getDestinatarioNome() {
        return destinatarioNome;
    }

    public void setDestinatarioNome(String destinatarioNome) {
        this.destinatarioNome = destinatarioNome;
    }

    public String getDestinoCidade() {
        return destinoCidade;
    }

    public void setDestinoCidade(String destinoCidade) {
        this.destinoCidade = destinoCidade;
    }

    public String getDestinoUf() {
        return destinoUf;
    }

    public void setDestinoUf(String destinoUf) {
        this.destinoUf = destinoUf;
    }

    public String getFilialNome() {
        return filialNome;
    }

    public void setFilialNome(String filialNome) {
        this.filialNome = filialNome;
    }

    public String getNumeroNotaFiscal() {
        return numeroNotaFiscal;
    }

    public void setNumeroNotaFiscal(String numeroNotaFiscal) {
        this.numeroNotaFiscal = numeroNotaFiscal;
    }

    public String getTabelaPrecoNome() {
        return tabelaPrecoNome;
    }

    public void setTabelaPrecoNome(String tabelaPrecoNome) {
        this.tabelaPrecoNome = tabelaPrecoNome;
    }

    public String getClassificacaoNome() {
        return classificacaoNome;
    }

    public void setClassificacaoNome(String classificacaoNome) {
        this.classificacaoNome = classificacaoNome;
    }

    public String getCentroCustoNome() {
        return centroCustoNome;
    }

    public void setCentroCustoNome(String centroCustoNome) {
        this.centroCustoNome = centroCustoNome;
    }

    public String getUsuarioNome() {
        return usuarioNome;
    }

    public void setUsuarioNome(String usuarioNome) {
        this.usuarioNome = usuarioNome;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
    }

    public Integer getInvoicesTotalVolumes() {
        return invoicesTotalVolumes;
    }

    public void setInvoicesTotalVolumes(Integer invoicesTotalVolumes) {
        this.invoicesTotalVolumes = invoicesTotalVolumes;
    }

    public BigDecimal getTaxedWeight() {
        return taxedWeight;
    }

    public void setTaxedWeight(BigDecimal taxedWeight) {
        this.taxedWeight = taxedWeight;
    }

    public BigDecimal getRealWeight() {
        return realWeight;
    }

    public void setRealWeight(BigDecimal realWeight) {
        this.realWeight = realWeight;
    }

    public BigDecimal getCubagesCubedWeight() {
        return cubagesCubedWeight;
    }

    public void setCubagesCubedWeight(BigDecimal cubagesCubedWeight) {
        this.cubagesCubedWeight = cubagesCubedWeight;
    }

    public BigDecimal getTotalCubicVolume() {
        return totalCubicVolume;
    }

    public void setTotalCubicVolume(BigDecimal totalCubicVolume) {
        this.totalCubicVolume = totalCubicVolume;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public String getChaveCte() {
        return chaveCte;
    }

    public void setChaveCte(final String chaveCte) {
        this.chaveCte = chaveCte;
    }

    public Integer getNumeroCte() {
        return numeroCte;
    }

    public void setNumeroCte(final Integer numeroCte) {
        this.numeroCte = numeroCte;
    }

    public Integer getSerieCte() {
        return serieCte;
    }

    public void setSerieCte(final Integer serieCte) {
        this.serieCte = serieCte;
    }

    public OffsetDateTime getCteIssuedAt() { return cteIssuedAt; }
    public void setCteIssuedAt(final OffsetDateTime cteIssuedAt) { this.cteIssuedAt = cteIssuedAt; }

    public Integer getServiceType() { return serviceType; }
    public void setServiceType(final Integer serviceType) { this.serviceType = serviceType; }
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

    public BigDecimal getFreightWeightSubtotal() { return freightWeightSubtotal; }
    public void setFreightWeightSubtotal(final BigDecimal freightWeightSubtotal) { this.freightWeightSubtotal = freightWeightSubtotal; }
    public BigDecimal getAdValoremSubtotal() { return adValoremSubtotal; }
    public void setAdValoremSubtotal(final BigDecimal adValoremSubtotal) { this.adValoremSubtotal = adValoremSubtotal; }
    public BigDecimal getTollSubtotal() { return tollSubtotal; }
    public void setTollSubtotal(final BigDecimal tollSubtotal) { this.tollSubtotal = tollSubtotal; }
    public BigDecimal getItrSubtotal() { return itrSubtotal; }
    public void setItrSubtotal(final BigDecimal itrSubtotal) { this.itrSubtotal = itrSubtotal; }
    public String getNfseSeries() { return nfseSeries; }
    public void setNfseSeries(final String nfseSeries) { this.nfseSeries = nfseSeries; }
    public Integer getNfseNumber() { return nfseNumber; }
    public void setNfseNumber(final Integer nfseNumber) { this.nfseNumber = nfseNumber; }
    public Long getInsuranceId() { return insuranceId; }
    public void setInsuranceId(final Long insuranceId) { this.insuranceId = insuranceId; }
    public BigDecimal getOtherFees() { return otherFees; }
    public void setOtherFees(final BigDecimal otherFees) { this.otherFees = otherFees; }
    public BigDecimal getKm() { return km; }
    public void setKm(final BigDecimal km) { this.km = km; }
    public Integer getPaymentAccountableType() { return paymentAccountableType; }
    public void setPaymentAccountableType(final Integer paymentAccountableType) { this.paymentAccountableType = paymentAccountableType; }
    public BigDecimal getInsuredValue() { return insuredValue; }
    public void setInsuredValue(final BigDecimal insuredValue) { this.insuredValue = insuredValue; }
    public Boolean getGlobalized() { return globalized; }
    public void setGlobalized(final Boolean globalized) { this.globalized = globalized; }
    public BigDecimal getSecCatSubtotal() { return secCatSubtotal; }
    public void setSecCatSubtotal(final BigDecimal secCatSubtotal) { this.secCatSubtotal = secCatSubtotal; }
    public String getGlobalizedType() { return globalizedType; }
    public void setGlobalizedType(final String globalizedType) { this.globalizedType = globalizedType; }
    public Integer getPriceTableAccountableType() { return priceTableAccountableType; }
    public void setPriceTableAccountableType(final Integer priceTableAccountableType) { this.priceTableAccountableType = priceTableAccountableType; }
    public Integer getInsuranceAccountableType() { return insuranceAccountableType; }
    public void setInsuranceAccountableType(final Integer insuranceAccountableType) { this.insuranceAccountableType = insuranceAccountableType; }

    public String getFilialCnpj() { return filialCnpj; }
    public void setFilialCnpj(final String filialCnpj) { this.filialCnpj = filialCnpj; }
    public String getRemetenteDocumento() { return remetenteDocumento; }
    public void setRemetenteDocumento(final String remetenteDocumento) { this.remetenteDocumento = remetenteDocumento; }
    public String getDestinatarioDocumento() { return destinatarioDocumento; }
    public void setDestinatarioDocumento(final String destinatarioDocumento) { this.destinatarioDocumento = destinatarioDocumento; }
    public String getPagadorDocumento() { return pagadorDocumento; }
    public void setPagadorDocumento(final String pagadorDocumento) { this.pagadorDocumento = pagadorDocumento; }

    public String getFiscalCstType() { return fiscalCstType; }
    public void setFiscalCstType(final String fiscalCstType) { this.fiscalCstType = fiscalCstType; }
    public String getFiscalCfopCode() { return fiscalCfopCode; }
    public void setFiscalCfopCode(final String fiscalCfopCode) { this.fiscalCfopCode = fiscalCfopCode; }
    public BigDecimal getFiscalTaxValue() { return fiscalTaxValue; }
    public void setFiscalTaxValue(final BigDecimal fiscalTaxValue) { this.fiscalTaxValue = fiscalTaxValue; }
    public BigDecimal getFiscalPisValue() { return fiscalPisValue; }
    public void setFiscalPisValue(final BigDecimal fiscalPisValue) { this.fiscalPisValue = fiscalPisValue; }
    public BigDecimal getFiscalCofinsValue() { return fiscalCofinsValue; }
    public void setFiscalCofinsValue(final BigDecimal fiscalCofinsValue) { this.fiscalCofinsValue = fiscalCofinsValue; }

    public BigDecimal getFiscalCalculationBasis() { return fiscalCalculationBasis; }
    public void setFiscalCalculationBasis(final BigDecimal fiscalCalculationBasis) { this.fiscalCalculationBasis = fiscalCalculationBasis; }
    public BigDecimal getFiscalTaxRate() { return fiscalTaxRate; }
    public void setFiscalTaxRate(final BigDecimal fiscalTaxRate) { this.fiscalTaxRate = fiscalTaxRate; }
    public BigDecimal getFiscalPisRate() { return fiscalPisRate; }
    public void setFiscalPisRate(final BigDecimal fiscalPisRate) { this.fiscalPisRate = fiscalPisRate; }
    public BigDecimal getFiscalCofinsRate() { return fiscalCofinsRate; }
    public void setFiscalCofinsRate(final BigDecimal fiscalCofinsRate) { this.fiscalCofinsRate = fiscalCofinsRate; }
    public Boolean getFiscalHasDifal() { return fiscalHasDifal; }
    public void setFiscalHasDifal(final Boolean fiscalHasDifal) { this.fiscalHasDifal = fiscalHasDifal; }
    public BigDecimal getFiscalDifalOrigin() { return fiscalDifalOrigin; }
    public void setFiscalDifalOrigin(final BigDecimal fiscalDifalOrigin) { this.fiscalDifalOrigin = fiscalDifalOrigin; }
    public BigDecimal getFiscalDifalDestination() { return fiscalDifalDestination; }
    public void setFiscalDifalDestination(final BigDecimal fiscalDifalDestination) { this.fiscalDifalDestination = fiscalDifalDestination; }

    public Long getCteId() { return cteId; }
    public void setCteId(final Long cteId) { this.cteId = cteId; }
    public String getCteEmissionType() { return cteEmissionType; }
    public void setCteEmissionType(final String cteEmissionType) { this.cteEmissionType = cteEmissionType; }
    public OffsetDateTime getCteCreatedAt() { return cteCreatedAt; }
    public void setCteCreatedAt(final OffsetDateTime cteCreatedAt) { this.cteCreatedAt = cteCreatedAt; }

    public String getFilialApelido() { return filialApelido; }
    public void setFilialApelido(final String filialApelido) { this.filialApelido = filialApelido; }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(final String metadata) {
        this.metadata = metadata;
    }
}