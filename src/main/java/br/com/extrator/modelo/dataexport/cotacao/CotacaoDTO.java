/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/dataexport/cotacao/CotacaoDTO.java
Classe  : CotacaoDTO (class)
Pacote  : br.com.extrator.modelo.dataexport.cotacao
Modulo  : DTO/Mapper DataExport
Papel   : Implementa responsabilidade de cotacao dto.

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
- getRequestedAt(): expone valor atual do estado interno.
- setRequestedAt(...1 args): ajusta valor em estado interno.
- getTotalValue(): expone valor atual do estado interno.
- setTotalValue(...1 args): ajusta valor em estado interno.
- getTaxedWeight(): expone valor atual do estado interno.
- setTaxedWeight(...1 args): ajusta valor em estado interno.
- getInvoicesValue(): expone valor atual do estado interno.
- setInvoicesValue(...1 args): ajusta valor em estado interno.
- getOriginCity(): expone valor atual do estado interno.
- setOriginCity(...1 args): ajusta valor em estado interno.
Atributos-chave:
- requestedAt: campo de estado para "requested at".
- sequenceCode: campo de estado para "sequence code".
- operationType: campo de estado para "operation type".
- customerDocument: campo de estado para "customer document".
- customerName: campo de estado para "customer name".
- originCity: campo de estado para "origin city".
- originState: campo de estado para "origin state".
- destinationCity: campo de estado para "destination city".
- destinationState: campo de estado para "destination state".
- priceTable: campo de estado para "price table".
- volumes: campo de estado para "volumes".
- taxedWeight: campo de estado para "taxed weight".
- invoicesValue: campo de estado para "invoices value".
- totalValue: campo de estado para "total value".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.dataexport.cotacao;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO (Data Transfer Object) para representar um registro de Cotação
 * vindo da API Data Export. Mapeia campos essenciais para colunas dedicadas
 * e captura dinamicamente todos os outros campos do template em um mapa,
 * garantindo resiliência contra futuras alterações no template.
 */
public class CotacaoDTO {

    // --- Todos os 37 campos do CSV mapeados explicitamente conforme docs/descobertas-endpoints/cotacoes.md ---
    
    // 1. Data Cotação
    @JsonProperty("requested_at")
    private String requestedAt;
    
    // 2. N° Cotação
    @JsonProperty("sequence_code")
    private Long sequenceCode;
    
    // 3. Tipo de operação
    @JsonProperty("qoe_qes_fon_name")
    private String operationType;
    
    // 4. CNPJ/CPF Cliente
    @JsonProperty("qoe_cor_document")
    private String customerDocument;
    
    // 5. Cliente Pagador
    @JsonProperty("qoe_cor_name")
    private String customerName;
    
    // 6. Cidade Origem
    @JsonProperty("qoe_qes_ony_name")
    private String originCity;
    
    // 7. UF Origem
    @JsonProperty("qoe_qes_ony_sae_code")
    private String originState;
    
    // 8. Cidade Destino
    @JsonProperty("qoe_qes_diy_name")
    private String destinationCity;
    
    // 9. UF Destino
    @JsonProperty("qoe_qes_diy_sae_code")
    private String destinationState;
    
    // 10. Tabela
    @JsonProperty("qoe_qes_cre_name")
    private String priceTable;
    
    // 11. Volume
    @JsonProperty("qoe_qes_invoices_volumes")
    private Integer volumes;
    
    // 12. Peso taxado
    @JsonProperty("qoe_qes_taxed_weight")
    private String taxedWeight;
    
    // 13. Valor NF
    @JsonProperty("qoe_qes_invoices_value")
    private String invoicesValue;
    
    // 14. Valor frete
    @JsonProperty("qoe_qes_total")
    private String totalValue;
    
    // 15. CT-e/Data de emissão
    @JsonProperty("qoe_qes_fit_fhe_cte_issued_at")
    private String cteIssuedAt;
    
    // 16. Nfse/Data de emissão
    @JsonProperty("qoe_qes_fit_nse_issued_at")
    private String nfseIssuedAt;
    
    // 17. Usuário
    @JsonProperty("qoe_uer_name")
    private String userName;
    
    // 18. Filial
    @JsonProperty("qoe_crn_psn_nickname")
    private String branchNickname;
    
    // 19. Remetente/CNPJ
    @JsonProperty("qoe_qes_sdr_document")
    private String senderDocument;
    
    // 20. Remetente/Nome fantasia
    @JsonProperty("qoe_qes_sdr_nickname")
    private String senderNickname;
    
    // 21. Destinatário/CNPJ
    @JsonProperty("qoe_qes_rpt_document")
    private String receiverDocument;
    
    // 22. Destinatário/Nome fantasia
    @JsonProperty("qoe_qes_rpt_nickname")
    private String receiverNickname;
    
    // 23. Pagador/Nome fantasia (mesmo que qoe_cor_name - campo 5)
    // @JsonProperty("qoe_cor_name") - já mapeado acima como customerName
    
    // 24. CEP Origem
    @JsonProperty("qoe_qes_origin_postal_code")
    private String originPostalCode;
    
    // 25. CEP Destino
    @JsonProperty("qoe_qes_destination_postal_code")
    private String destinationPostalCode;
    
    // 26. Peso real
    @JsonProperty("qoe_qes_real_weight")
    private String realWeight;
    
    // 27. Observações
    @JsonProperty("qoe_qes_disapprove_comments")
    private String disapproveComments;
    
    // 28. Observações para o frete
    @JsonProperty("qoe_qes_freight_comments")
    private String freightComments;
    
    // 29. Descontos/Subtotal parcelas
    @JsonProperty("qoe_qes_fit_fdt_subtotal")
    private String discountSubtotal;
    
    // 30. Solicitante
    @JsonProperty("requester_name")
    private String requesterName;
    
    // 31. Trechos/ITR
    @JsonProperty("qoe_qes_itr_subtotal")
    private String itrSubtotal;
    
    // 32. Trechos/TDE
    @JsonProperty("qoe_qes_tde_subtotal")
    private String tdeSubtotal;
    
    // 33. Trechos/Coleta
    @JsonProperty("qoe_qes_collect_subtotal")
    private String collectSubtotal;
    
    // 34. Trechos/Entrega
    @JsonProperty("qoe_qes_delivery_subtotal")
    private String deliverySubtotal;
    
    // 35. Trechos/Outros valores
    @JsonProperty("qoe_qes_other_fees")
    private String otherFees;
    
    // 36. Empresa
    @JsonProperty("qoe_crn_psn_name")
    private String companyName;
    
    // 37. Apelido/Nickname do Cliente
    @JsonProperty("qoe_cor_nickname")
    private String customerNickname;

    // --- Contêiner Dinâmico ("Resto") ---
    private final Map<String, Object> otherProperties = new HashMap<>();

    @JsonAnySetter
    public void add(final String key, final Object value) {
        this.otherProperties.put(key, value);
    }

    /**
     * Retorna um mapa contendo todas as propriedades do DTO, combinando
     * os 37 campos mapeados explicitamente conforme docs/descobertas-endpoints/cotacoes.md
     * com os campos capturados dinamicamente.
     * Essencial para a serialização completa no campo de metadados.
     * @return Um mapa com todos os dados da cotação.
     */
    public Map<String, Object> getAllProperties() {
        final Map<String, Object> allProps = new LinkedHashMap<>();
        // Adiciona todos os 37 campos explícitos ao mapa conforme documentação
        allProps.put("requested_at", requestedAt);
        allProps.put("sequence_code", sequenceCode);
        allProps.put("qoe_qes_fon_name", operationType);
        allProps.put("qoe_cor_document", customerDocument);
        allProps.put("qoe_cor_name", customerName);
        allProps.put("qoe_cor_nickname", customerNickname);
        allProps.put("qoe_qes_ony_name", originCity);
        allProps.put("qoe_qes_ony_sae_code", originState);
        allProps.put("qoe_qes_diy_name", destinationCity);
        allProps.put("qoe_qes_diy_sae_code", destinationState);
        allProps.put("qoe_qes_cre_name", priceTable);
        allProps.put("qoe_qes_invoices_volumes", volumes);
        allProps.put("qoe_qes_taxed_weight", taxedWeight);
        allProps.put("qoe_qes_invoices_value", invoicesValue);
        allProps.put("qoe_qes_total", totalValue);
        allProps.put("qoe_qes_fit_fhe_cte_issued_at", cteIssuedAt);
        allProps.put("qoe_qes_fit_nse_issued_at", nfseIssuedAt);
        allProps.put("qoe_uer_name", userName);
        allProps.put("qoe_crn_psn_nickname", branchNickname);
        allProps.put("qoe_crn_psn_name", companyName);
        allProps.put("qoe_qes_sdr_document", senderDocument);
        allProps.put("qoe_qes_sdr_nickname", senderNickname);
        allProps.put("qoe_qes_rpt_document", receiverDocument);
        allProps.put("qoe_qes_rpt_nickname", receiverNickname);
        allProps.put("qoe_qes_origin_postal_code", originPostalCode);
        allProps.put("qoe_qes_destination_postal_code", destinationPostalCode);
        allProps.put("qoe_qes_real_weight", realWeight);
        allProps.put("qoe_qes_disapprove_comments", disapproveComments);
        allProps.put("qoe_qes_freight_comments", freightComments);
        allProps.put("qoe_qes_fit_fdt_subtotal", discountSubtotal);
        allProps.put("requester_name", requesterName);
        allProps.put("qoe_qes_itr_subtotal", itrSubtotal);
        allProps.put("qoe_qes_tde_subtotal", tdeSubtotal);
        allProps.put("qoe_qes_collect_subtotal", collectSubtotal);
        allProps.put("qoe_qes_delivery_subtotal", deliverySubtotal);
        allProps.put("qoe_qes_other_fees", otherFees);
        // Adiciona todos os outros campos capturados dinamicamente
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

    public String getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(final String requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(final String totalValue) {
        this.totalValue = totalValue;
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

    public String getOriginCity() {
        return originCity;
    }

    public void setOriginCity(final String originCity) {
        this.originCity = originCity;
    }

    public String getOriginState() {
        return originState;
    }

    public void setOriginState(final String originState) {
        this.originState = originState;
    }

    public String getDestinationCity() {
        return destinationCity;
    }

    public void setDestinationCity(final String destinationCity) {
        this.destinationCity = destinationCity;
    }

    public String getDestinationState() {
        return destinationState;
    }

    public void setDestinationState(final String destinationState) {
        this.destinationState = destinationState;
    }

    public String getCustomerDocument() {
        return customerDocument;
    }

    public void setCustomerDocument(final String customerDocument) {
        this.customerDocument = customerDocument;
    }

    // Getters e Setters para todos os 36 campos conforme documentação
    
    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(final String operationType) {
        this.operationType = operationType;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(final String customerName) {
        this.customerName = customerName;
    }

    public String getPriceTable() {
        return priceTable;
    }

    public void setPriceTable(final String priceTable) {
        this.priceTable = priceTable;
    }

    public Integer getVolumes() {
        return volumes;
    }

    public void setVolumes(final Integer volumes) {
        this.volumes = volumes;
    }

    public String getCteIssuedAt() {
        return cteIssuedAt;
    }

    public void setCteIssuedAt(final String cteIssuedAt) {
        this.cteIssuedAt = cteIssuedAt;
    }

    public String getNfseIssuedAt() {
        return nfseIssuedAt;
    }

    public void setNfseIssuedAt(final String nfseIssuedAt) {
        this.nfseIssuedAt = nfseIssuedAt;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(final String userName) {
        this.userName = userName;
    }

    public String getBranchNickname() {
        return branchNickname;
    }

    public void setBranchNickname(final String branchNickname) {
        this.branchNickname = branchNickname;
    }

    public String getSenderDocument() {
        return senderDocument;
    }

    public void setSenderDocument(final String senderDocument) {
        this.senderDocument = senderDocument;
    }

    public String getSenderNickname() {
        return senderNickname;
    }

    public void setSenderNickname(final String senderNickname) {
        this.senderNickname = senderNickname;
    }

    public String getReceiverDocument() {
        return receiverDocument;
    }

    public void setReceiverDocument(final String receiverDocument) {
        this.receiverDocument = receiverDocument;
    }

    public String getReceiverNickname() {
        return receiverNickname;
    }

    public void setReceiverNickname(final String receiverNickname) {
        this.receiverNickname = receiverNickname;
    }

    public String getOriginPostalCode() {
        return originPostalCode;
    }

    public void setOriginPostalCode(final String originPostalCode) {
        this.originPostalCode = originPostalCode;
    }

    public String getDestinationPostalCode() {
        return destinationPostalCode;
    }

    public void setDestinationPostalCode(final String destinationPostalCode) {
        this.destinationPostalCode = destinationPostalCode;
    }

    public String getRealWeight() {
        return realWeight;
    }

    public void setRealWeight(final String realWeight) {
        this.realWeight = realWeight;
    }

    public String getDisapproveComments() {
        return disapproveComments;
    }

    public void setDisapproveComments(final String disapproveComments) {
        this.disapproveComments = disapproveComments;
    }

    public String getFreightComments() {
        return freightComments;
    }

    public void setFreightComments(final String freightComments) {
        this.freightComments = freightComments;
    }

    public String getDiscountSubtotal() {
        return discountSubtotal;
    }

    public void setDiscountSubtotal(final String discountSubtotal) {
        this.discountSubtotal = discountSubtotal;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public void setRequesterName(final String requesterName) {
        this.requesterName = requesterName;
    }

    public String getItrSubtotal() {
        return itrSubtotal;
    }

    public void setItrSubtotal(final String itrSubtotal) {
        this.itrSubtotal = itrSubtotal;
    }

    public String getTdeSubtotal() {
        return tdeSubtotal;
    }

    public void setTdeSubtotal(final String tdeSubtotal) {
        this.tdeSubtotal = tdeSubtotal;
    }

    public String getCollectSubtotal() {
        return collectSubtotal;
    }

    public void setCollectSubtotal(final String collectSubtotal) {
        this.collectSubtotal = collectSubtotal;
    }

    public String getDeliverySubtotal() {
        return deliverySubtotal;
    }

    public void setDeliverySubtotal(final String deliverySubtotal) {
        this.deliverySubtotal = deliverySubtotal;
    }

    public String getOtherFees() {
        return otherFees;
    }

    public void setOtherFees(final String otherFees) {
        this.otherFees = otherFees;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(final String companyName) {
        this.companyName = companyName;
    }

    public String getCustomerNickname() {
        return customerNickname;
    }

    public void setCustomerNickname(final String customerNickname) {
        this.customerNickname = customerNickname;
    }

    @JsonAnyGetter
    public Map<String, Object> getOtherProperties() {
        return otherProperties;
    }
}
