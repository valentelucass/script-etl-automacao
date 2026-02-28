/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/entity/LocalizacaoCargaEntity.java
Classe  : LocalizacaoCargaEntity (class)
Pacote  : br.com.extrator.db.entity
Modulo  : Entidade de persistencia
Papel   : Implementa responsabilidade de localizacao carga entity.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Define estrutura de dados persistida no banco.
2) Representa campos de tabela/view no dominio Java.
3) Suporta transporte de dados entre camadas.

Estrutura interna:
Metodos principais:
- getSequenceNumber(): expone valor atual do estado interno.
- setSequenceNumber(...1 args): ajusta valor em estado interno.
- getServiceAt(): expone valor atual do estado interno.
- setServiceAt(...1 args): ajusta valor em estado interno.
- getStatus(): expone valor atual do estado interno.
- setStatus(...1 args): ajusta valor em estado interno.
- getTotalValue(): expone valor atual do estado interno.
- setTotalValue(...1 args): ajusta valor em estado interno.
- getPredictedDeliveryAt(): expone valor atual do estado interno.
- setPredictedDeliveryAt(...1 args): ajusta valor em estado interno.
- getOriginLocationName(): expone valor atual do estado interno.
- setOriginLocationName(...1 args): ajusta valor em estado interno.
- getDestinationLocationName(): expone valor atual do estado interno.
- setDestinationLocationName(...1 args): ajusta valor em estado interno.
Atributos-chave:
- sequenceNumber: campo de estado para "sequence number".
- type: campo de estado para "type".
- serviceAt: campo de estado para "service at".
- invoicesVolumes: campo de estado para "invoices volumes".
- taxedWeight: campo de estado para "taxed weight".
- invoicesValue: campo de estado para "invoices value".
- totalValue: campo de estado para "total value".
- serviceType: campo de estado para "service type".
- branchNickname: campo de estado para "branch nickname".
- predictedDeliveryAt: campo de estado para "predicted delivery at".
- destinationLocationName: campo de estado para "destination location name".
- destinationBranchNickname: campo de estado para "destination branch nickname".
- classification: campo de estado para "classification".
- status: campo de estado para "status".
[DOC-FILE-END]============================================================== */

package br.com.extrator.db.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Entity (Entidade) que representa uma linha na tabela 'localizacao_cargas' do banco de dados.
 * É o "produto final" da transformação, contendo os dados já estruturados e prontos
 * para serem persistidos. A coluna 'metadata' armazena o JSON completo
 * do objeto original para garantir 100% de completude e resiliência.
 */
public class LocalizacaoCargaEntity {

    // --- Coluna de Chave Primária ---
    private Long sequenceNumber;

    // --- Colunas Essenciais para Indexação e Relatórios ---
    // Campos principais conforme docs/descobertas-endpoints/localizacaocarga.md
    private String type; // Tipo
    private OffsetDateTime serviceAt;
    private Integer invoicesVolumes; // Volumes
    private String taxedWeight; // Peso Taxado
    private String invoicesValue; // Valor NF
    private BigDecimal totalValue;
    private String serviceType; // Serviço
    private String branchNickname; // Filial
    private OffsetDateTime predictedDeliveryAt;
    private String destinationLocationName; // Polo de Destino
    private String destinationBranchNickname; // Filial de Destino
    private String classification; // Classificação
    private String status;
    private String statusBranchNickname; // Filial do Status
    private String originLocationName; // Polo de Origem
    private String originBranchNickname; // Filial de Origem
    private String fitFlnClnNickname; // Localização Atual (fit_fln_cln_nickname)

    // --- Coluna de Metadados ---
    private String metadata;

    // --- Getters e Setters ---

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(final Long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public OffsetDateTime getServiceAt() {
        return serviceAt;
    }

    public void setServiceAt(final OffsetDateTime serviceAt) {
        this.serviceAt = serviceAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(final BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public OffsetDateTime getPredictedDeliveryAt() {
        return predictedDeliveryAt;
    }

    public void setPredictedDeliveryAt(final OffsetDateTime predictedDeliveryAt) {
        this.predictedDeliveryAt = predictedDeliveryAt;
    }

    public String getOriginLocationName() {
        return originLocationName;
    }

    public void setOriginLocationName(final String originLocationName) {
        this.originLocationName = originLocationName;
    }

    public String getDestinationLocationName() {
        return destinationLocationName;
    }

    public void setDestinationLocationName(final String destinationLocationName) {
        this.destinationLocationName = destinationLocationName;
    }

    // Getters e Setters para campos adicionais
    
    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public Integer getInvoicesVolumes() {
        return invoicesVolumes;
    }

    public void setInvoicesVolumes(final Integer invoicesVolumes) {
        this.invoicesVolumes = invoicesVolumes;
    }

    public String getTaxedWeight() {
        return taxedWeight;
    }

    public void setTaxedWeight(final String taxedWeight) {
        this.taxedWeight = taxedWeight;
    }

    public String getInvoicesValue() {
        return invoicesValue;
    }

    public void setInvoicesValue(final String invoicesValue) {
        this.invoicesValue = invoicesValue;
    }

    public String getServiceType() {
        return serviceType;
    }

    public void setServiceType(final String serviceType) {
        this.serviceType = serviceType;
    }

    public String getBranchNickname() {
        return branchNickname;
    }

    public void setBranchNickname(final String branchNickname) {
        this.branchNickname = branchNickname;
    }

    public String getDestinationBranchNickname() {
        return destinationBranchNickname;
    }

    public void setDestinationBranchNickname(final String destinationBranchNickname) {
        this.destinationBranchNickname = destinationBranchNickname;
    }

    public String getClassification() {
        return classification;
    }

    public void setClassification(final String classification) {
        this.classification = classification;
    }

    public String getStatusBranchNickname() {
        return statusBranchNickname;
    }

    public void setStatusBranchNickname(final String statusBranchNickname) {
        this.statusBranchNickname = statusBranchNickname;
    }

    public String getOriginBranchNickname() {
        return originBranchNickname;
    }

    public void setOriginBranchNickname(final String originBranchNickname) {
        this.originBranchNickname = originBranchNickname;
    }

    public String getFitFlnClnNickname() {
        return fitFlnClnNickname;
    }

    public void setFitFlnClnNickname(final String fitFlnClnNickname) {
        this.fitFlnClnNickname = fitFlnClnNickname;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(final String metadata) {
        this.metadata = metadata;
    }
}