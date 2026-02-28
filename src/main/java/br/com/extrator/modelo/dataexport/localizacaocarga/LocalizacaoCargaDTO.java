/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/dataexport/localizacaocarga/LocalizacaoCargaDTO.java
Classe  : LocalizacaoCargaDTO (class)
Pacote  : br.com.extrator.modelo.dataexport.localizacaocarga
Modulo  : DTO/Mapper DataExport
Papel   : Implementa responsabilidade de localizacao carga dto.

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

package br.com.extrator.modelo.dataexport.localizacaocarga;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO (Data Transfer Object) para representar um registro de Localização de Carga
 * vindo do template da API Data Export. Mapeia campos essenciais para colunas dedicadas
 * e captura dinamicamente todos os outros campos em um mapa, garantindo
 * resiliência contra futuras alterações no template.
 */
public class LocalizacaoCargaDTO {

    // --- Todos os 17 campos do CSV mapeados explicitamente conforme docs/descobertas-endpoints/localizacaocarga.md ---
    
    // 1. Doc/Minuta
    @JsonProperty("corporation_sequence_number")
    @JsonAlias("sequence_number")
    private Long sequenceNumber;
    
    // 2. Tipo
    @JsonProperty("type")
    private String type;
    
    // 3. Data Frete
    @JsonProperty("service_at")
    private String serviceAt;
    
    // 4. Volumes
    @JsonProperty("invoices_volumes")
    private Integer invoicesVolumes;
    
    // 5. Peso Taxado
    @JsonProperty("taxed_weight")
    private String taxedWeight;
    
    // 6. Valor NF
    @JsonProperty("invoices_value")
    private String invoicesValue;
    
    // 7. Valor Total do Serviço
    @JsonProperty("total")
    private String totalValue;
    
    // 8. Serviço
    @JsonProperty("service_type")
    private String serviceType;
    
    // 9. Filial
    @JsonProperty("fit_crn_psn_nickname")
    private String branchNickname;
    
    // 10. Previsão de Entrega
    @JsonProperty("fit_dpn_delivery_prediction_at")
    private String predictedDeliveryAt;
    
    // 11. Polo de Destino
    @JsonProperty("fit_dyn_name")
    private String destinationLocationName;
    
    // 12. Filial de Destino
    @JsonProperty("fit_dyn_drt_nickname")
    private String destinationBranchNickname;
    
    // 13. Classificação
    @JsonProperty("fit_fsn_name")
    private String classification;
    
    // 14. Status
    @JsonProperty("fit_fln_status")
    private String status;
    
    // 15. Filial do Status (status_branch_nickname no banco)
    // Nota: Este campo pode vir de outro campo JSON ou ser null
    private String statusBranchNickname;
    
    // 15.5. Localização Atual (fit_fln_cln_nickname no banco)
    @JsonProperty("fit_fln_cln_nickname")
    private String fitFlnClnNickname;
    
    // 16. Polo de Origem
    @JsonProperty("fit_o_n_name")
    private String originLocationName;
    
    // 17. Filial de Origem
    @JsonProperty("fit_o_n_drt_nickname")
    private String originBranchNickname;

    // --- Contêiner Dinâmico ("Resto") ---
    private final Map<String, Object> otherProperties = new HashMap<>();

    @JsonAnySetter
    public void add(final String key, final Object value) {
        this.otherProperties.put(key, value);
    }

    /**
     * Retorna um mapa contendo todas as propriedades do DTO, combinando
     * os 17 campos mapeados explicitamente conforme docs/descobertas-endpoints/localizacaocarga.md
     * com os campos capturados dinamicamente.
     * @return Um mapa com todos os dados do registro.
     */
    public Map<String, Object> getAllProperties() {
        final Map<String, Object> allProps = new LinkedHashMap<>();
        // Adiciona todos os 17 campos explícitos ao mapa conforme documentação
        allProps.put("corporation_sequence_number", sequenceNumber);
        allProps.put("sequence_number", sequenceNumber);
        allProps.put("type", type);
        allProps.put("service_at", serviceAt);
        allProps.put("invoices_volumes", invoicesVolumes);
        allProps.put("taxed_weight", taxedWeight);
        allProps.put("invoices_value", invoicesValue);
        allProps.put("total", totalValue);
        allProps.put("service_type", serviceType);
        allProps.put("fit_crn_psn_nickname", branchNickname);
        allProps.put("fit_dpn_delivery_prediction_at", predictedDeliveryAt);
        allProps.put("fit_dyn_name", destinationLocationName);
        allProps.put("fit_dyn_drt_nickname", destinationBranchNickname);
        allProps.put("fit_fsn_name", classification);
        allProps.put("fit_fln_status", status);
        allProps.put("status_branch_nickname", statusBranchNickname);
        allProps.put("fit_fln_cln_nickname", fitFlnClnNickname);
        allProps.put("fit_o_n_name", originLocationName);
        allProps.put("fit_o_n_drt_nickname", originBranchNickname);
        // Adiciona todos os outros campos capturados dinamicamente
        allProps.putAll(otherProperties);
        return allProps;
    }

    // --- Getters e Setters ---

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(final Long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getServiceAt() {
        return serviceAt;
    }

    public void setServiceAt(final String serviceAt) {
        this.serviceAt = serviceAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(final String status) {
        this.status = status;
    }

    public String getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(final String totalValue) {
        this.totalValue = totalValue;
    }

    public String getPredictedDeliveryAt() {
        return predictedDeliveryAt;
    }

    public void setPredictedDeliveryAt(final String predictedDeliveryAt) {
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

    // Getters e Setters para todos os 17 campos conforme documentação
    
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

    @JsonAnyGetter
    public Map<String, Object> getOtherProperties() {
        return otherProperties;
    }
}
