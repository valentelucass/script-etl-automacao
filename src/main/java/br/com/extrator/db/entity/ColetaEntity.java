/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/entity/ColetaEntity.java
Classe  : ColetaEntity (class)
Pacote  : br.com.extrator.db.entity
Modulo  : Entidade de persistencia
Papel   : Implementa responsabilidade de coleta entity.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Define estrutura de dados persistida no banco.
2) Representa campos de tabela/view no dominio Java.
3) Suporta transporte de dados entre camadas.

Estrutura interna:
Metodos principais:
- getId(): expone valor atual do estado interno.
- setId(...1 args): ajusta valor em estado interno.
- getSequenceCode(): expone valor atual do estado interno.
- setSequenceCode(...1 args): ajusta valor em estado interno.
- getRequestDate(): expone valor atual do estado interno.
- setRequestDate(...1 args): ajusta valor em estado interno.
- getServiceDate(): expone valor atual do estado interno.
- setServiceDate(...1 args): ajusta valor em estado interno.
- getStatus(): expone valor atual do estado interno.
- setStatus(...1 args): ajusta valor em estado interno.
- getTotalValue(): expone valor atual do estado interno.
- setTotalValue(...1 args): ajusta valor em estado interno.
- getTotalWeight(): expone valor atual do estado interno.
- setTotalWeight(...1 args): ajusta valor em estado interno.
Atributos-chave:
- id: campo de estado para "id".
- sequenceCode: campo de estado para "sequence code".
- requestDate: campo de estado para "request date".
- serviceDate: campo de estado para "service date".
- status: campo de estado para "status".
- totalValue: campo de estado para "total value".
- totalWeight: campo de estado para "total weight".
- totalVolumes: campo de estado para "total volumes".
- clienteId: campo de estado para "cliente id".
- clienteNome: campo de estado para "cliente nome".
- clienteDoc: campo de estado para "cliente doc".
- localColeta: campo de estado para "local coleta".
- numeroColeta: campo de estado para "numero coleta".
- complementoColeta: campo de estado para "complemento coleta".
[DOC-FILE-END]============================================================== */

package br.com.extrator.db.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Entity (Entidade) que representa uma linha na tabela 'coletas' do banco de dados.
 * É o produto final da transformação, contendo os dados já estruturados e prontos
 * para serem persistidos. A coluna 'metadata' armazena o JSON completo
 * do objeto original para garantir 100% de completude e resiliência.
 */
public class ColetaEntity {

    // --- Colunas de Chave ---
    private String id; // Chave Primária (VARCHAR)

    // --- Colunas Essenciais para Indexação e Relatórios ---
    private Long sequenceCode;
    private LocalDate requestDate;
    private LocalDate serviceDate;
    private String status;
    private BigDecimal totalValue;
    private BigDecimal totalWeight;
    private Integer totalVolumes;

    // --- Campos Expandidos (22 campos do CSV) ---
    private Long clienteId;
    private String clienteNome;
    private String clienteDoc;
    private String localColeta;
    private String numeroColeta;
    private String complementoColeta;
    private String cidadeColeta;
    private String bairroColeta;
    private String ufColeta;
    private String cepColeta;
    private Long filialId;
    private String filialNome;
    private String filialCnpj;
    private Long usuarioId;
    private String usuarioNome;
    private String requestHour;
    private String serviceStartHour;
    private LocalDate finishDate;
    private String serviceEndHour;
    private String requester;
    private String comments;
    private Long agentId;
    private Long manifestItemPickId;
    private Long vehicleTypeId;

    private String cancellationReason;
    private Long cancellationUserId;
    private Long cargoClassificationId;
    private Long costCenterId;
    private String destroyReason;
    private Long destroyUserId;
    private BigDecimal invoicesCubedWeight;
    private String lunchBreakEndHour;
    private String lunchBreakStartHour;
    private String notificationEmail;
    private String notificationPhone;
    private Long pickTypeId;
    private Long pickupLocationId;
    private String statusUpdatedAt;
    private BigDecimal taxedWeight; // Peso Taxado (node.taxedWeight)
    private String pickRegion; // Região da Coleta (node.pickAddress.city.name + state.code)
    private String lastOccurrence; // Última Ocorrência (tradução do status)
    private String acaoOcorrencia; // Ação da Ocorrência (lógica De-Para)
    private Integer numeroTentativas; // Nº Tentativas (lógica De-Para)

    // --- Coluna de Metadados ---
    private String metadata;

    // --- Getters e Setters ---

    public String getId() {
        return id;
    }

    public void setId(final String id) {
        this.id = id;
    }



    public Long getSequenceCode() {
        return sequenceCode;
    }

    public void setSequenceCode(final Long sequenceCode) {
        this.sequenceCode = sequenceCode;
    }

    public LocalDate getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(final LocalDate requestDate) {
        this.requestDate = requestDate;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public void setServiceDate(final LocalDate serviceDate) {
        this.serviceDate = serviceDate;
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

    public BigDecimal getTotalWeight() {
        return totalWeight;
    }

    public void setTotalWeight(final BigDecimal totalWeight) {
        this.totalWeight = totalWeight;
    }

    public Integer getTotalVolumes() {
        return totalVolumes;
    }

    public void setTotalVolumes(final Integer totalVolumes) {
        this.totalVolumes = totalVolumes;
    }

    // --- Getters e Setters para Campos Expandidos ---

    public Long getClienteId() {
        return clienteId;
    }

    public void setClienteId(final Long clienteId) {
        this.clienteId = clienteId;
    }

    public String getClienteNome() {
        return clienteNome;
    }

    public void setClienteNome(final String clienteNome) {
        this.clienteNome = clienteNome;
    }

    public String getClienteDoc() {
        return clienteDoc;
    }

    public void setClienteDoc(final String clienteDoc) {
        this.clienteDoc = clienteDoc;
    }

    public String getLocalColeta() {
        return localColeta;
    }

    public void setLocalColeta(final String localColeta) {
        this.localColeta = localColeta;
    }

    public String getNumeroColeta() {
        return numeroColeta;
    }

    public void setNumeroColeta(final String numeroColeta) {
        this.numeroColeta = numeroColeta;
    }

    public String getComplementoColeta() {
        return complementoColeta;
    }

    public void setComplementoColeta(final String complementoColeta) {
        this.complementoColeta = complementoColeta;
    }

    public String getCidadeColeta() {
        return cidadeColeta;
    }

    public void setCidadeColeta(final String cidadeColeta) {
        this.cidadeColeta = cidadeColeta;
    }

    public String getBairroColeta() {
        return bairroColeta;
    }

    public void setBairroColeta(final String bairroColeta) {
        this.bairroColeta = bairroColeta;
    }

    public String getUfColeta() {
        return ufColeta;
    }

    public void setUfColeta(final String ufColeta) {
        this.ufColeta = ufColeta;
    }

    public String getCepColeta() {
        return cepColeta;
    }

    public void setCepColeta(final String cepColeta) {
        this.cepColeta = cepColeta;
    }

    public Long getFilialId() {
        return filialId;
    }

    public void setFilialId(final Long filialId) {
        this.filialId = filialId;
    }

    public String getFilialNome() {
        return filialNome;
    }

    public void setFilialNome(final String filialNome) {
        this.filialNome = filialNome;
    }

    public String getFilialCnpj() {
        return filialCnpj;
    }

    public void setFilialCnpj(final String filialCnpj) {
        this.filialCnpj = filialCnpj;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(final Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getUsuarioNome() {
        return usuarioNome;
    }

    public void setUsuarioNome(final String usuarioNome) {
        this.usuarioNome = usuarioNome;
    }

    public String getRequestHour() {
        return requestHour;
    }

    public void setRequestHour(final String requestHour) {
        this.requestHour = requestHour;
    }

    public String getServiceStartHour() {
        return serviceStartHour;
    }

    public void setServiceStartHour(final String serviceStartHour) {
        this.serviceStartHour = serviceStartHour;
    }

    public LocalDate getFinishDate() {
        return finishDate;
    }

    public void setFinishDate(final LocalDate finishDate) {
        this.finishDate = finishDate;
    }

    public String getServiceEndHour() {
        return serviceEndHour;
    }

    public void setServiceEndHour(final String serviceEndHour) {
        this.serviceEndHour = serviceEndHour;
    }

    public String getRequester() {
        return requester;
    }

    public void setRequester(final String requester) {
        this.requester = requester;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(final String comments) {
        this.comments = comments;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(final Long agentId) {
        this.agentId = agentId;
    }

    public Long getManifestItemPickId() {
        return manifestItemPickId;
    }

    public void setManifestItemPickId(final Long manifestItemPickId) {
        this.manifestItemPickId = manifestItemPickId;
    }

    public Long getVehicleTypeId() {
        return vehicleTypeId;
    }

    public void setVehicleTypeId(final Long vehicleTypeId) {
        this.vehicleTypeId = vehicleTypeId;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(final String metadata) {
        this.metadata = metadata;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(final String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public Long getCancellationUserId() {
        return cancellationUserId;
    }

    public void setCancellationUserId(final Long cancellationUserId) {
        this.cancellationUserId = cancellationUserId;
    }

    public Long getCargoClassificationId() {
        return cargoClassificationId;
    }

    public void setCargoClassificationId(final Long cargoClassificationId) {
        this.cargoClassificationId = cargoClassificationId;
    }

    public Long getCostCenterId() {
        return costCenterId;
    }

    public void setCostCenterId(final Long costCenterId) {
        this.costCenterId = costCenterId;
    }

    public String getDestroyReason() {
        return destroyReason;
    }

    public void setDestroyReason(final String destroyReason) {
        this.destroyReason = destroyReason;
    }

    public Long getDestroyUserId() {
        return destroyUserId;
    }

    public void setDestroyUserId(final Long destroyUserId) {
        this.destroyUserId = destroyUserId;
    }

    public BigDecimal getInvoicesCubedWeight() {
        return invoicesCubedWeight;
    }

    public void setInvoicesCubedWeight(final BigDecimal invoicesCubedWeight) {
        this.invoicesCubedWeight = invoicesCubedWeight;
    }

    public String getLunchBreakEndHour() {
        return lunchBreakEndHour;
    }

    public void setLunchBreakEndHour(final String lunchBreakEndHour) {
        this.lunchBreakEndHour = lunchBreakEndHour;
    }

    public String getLunchBreakStartHour() {
        return lunchBreakStartHour;
    }

    public void setLunchBreakStartHour(final String lunchBreakStartHour) {
        this.lunchBreakStartHour = lunchBreakStartHour;
    }

    public String getNotificationEmail() {
        return notificationEmail;
    }

    public void setNotificationEmail(final String notificationEmail) {
        this.notificationEmail = notificationEmail;
    }

    public String getNotificationPhone() {
        return notificationPhone;
    }

    public void setNotificationPhone(final String notificationPhone) {
        this.notificationPhone = notificationPhone;
    }

    public Long getPickTypeId() {
        return pickTypeId;
    }

    public void setPickTypeId(final Long pickTypeId) {
        this.pickTypeId = pickTypeId;
    }

    public Long getPickupLocationId() {
        return pickupLocationId;
    }

    public void setPickupLocationId(final Long pickupLocationId) {
        this.pickupLocationId = pickupLocationId;
    }

    public String getStatusUpdatedAt() {
        return statusUpdatedAt;
    }

    public void setStatusUpdatedAt(final String statusUpdatedAt) {
        this.statusUpdatedAt = statusUpdatedAt;
    }

    public BigDecimal getTaxedWeight() {
        return taxedWeight;
    }

    public void setTaxedWeight(final BigDecimal taxedWeight) {
        this.taxedWeight = taxedWeight;
    }

    public String getPickRegion() {
        return pickRegion;
    }

    public void setPickRegion(final String pickRegion) {
        this.pickRegion = pickRegion;
    }

    public String getLastOccurrence() {
        return lastOccurrence;
    }

    public void setLastOccurrence(final String lastOccurrence) {
        this.lastOccurrence = lastOccurrence;
    }

    public String getAcaoOcorrencia() {
        return acaoOcorrencia;
    }

    public void setAcaoOcorrencia(final String acaoOcorrencia) {
        this.acaoOcorrencia = acaoOcorrencia;
    }

    public Integer getNumeroTentativas() {
        return numeroTentativas;
    }

    public void setNumeroTentativas(final Integer numeroTentativas) {
        this.numeroTentativas = numeroTentativas;
    }
}
