/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/dataexport/faturaporcliente/FaturaPorClienteDTO.java
Classe  : FaturaPorClienteDTO (class)
Pacote  : br.com.extrator.modelo.dataexport.faturaporcliente
Modulo  : DTO/Mapper DataExport
Papel   : Implementa responsabilidade de fatura por cliente dto.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela payloads da API DataExport.
2) Mapeia resposta para entidades internas.
3) Apoia carga e deduplicacao no destino.

Estrutura interna:
Metodos principais:
- getNfseNumber(): expone valor atual do estado interno.
- setNfseNumber(...1 args): ajusta valor em estado interno.
- getCteNumber(): expone valor atual do estado interno.
- setCteNumber(...1 args): ajusta valor em estado interno.
- getCteIssuedAt(): expone valor atual do estado interno.
- setCteIssuedAt(...1 args): ajusta valor em estado interno.
- getCteKey(): expone valor atual do estado interno.
- setCteKey(...1 args): ajusta valor em estado interno.
- getCteStatusResult(): expone valor atual do estado interno.
- setCteStatusResult(...1 args): ajusta valor em estado interno.
- getCteStatus(): expone valor atual do estado interno.
- setCteStatus(...1 args): ajusta valor em estado interno.
- getFaturaDocument(): expone valor atual do estado interno.
- setFaturaDocument(...1 args): ajusta valor em estado interno.
Atributos-chave:
- nfseNumber: campo de estado para "nfse number".
- cteNumber: campo de estado para "cte number".
- cteIssuedAt: campo de estado para "cte issued at".
- cteKey: campo de estado para "cte key".
- cteStatusResult: campo de estado para "cte status result".
- cteStatus: campo de estado para "cte status".
- faturaDocument: campo de estado para "fatura document".
- faturaIssueDate: campo de estado para "fatura issue date".
- faturaValue: campo de estado para "fatura value".
- faturaDueDate: campo de estado para "fatura due date".
- faturaBaixaDate: campo de estado para "fatura baixa date".
- faturaOriginalDueDate: campo de estado para "fatura original due date".
- valorFrete: campo de estado para "valor frete".
- thirdPartyCtesValue: campo de estado para "third party ctes value".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.dataexport.faturaporcliente;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO para dados de Faturas por Cliente da API Data Export (Template 4924).
 * Representa a resposta JSON da API contendo informações híbridas de CT-e e NFS-e.
 * 
 * @author Sistema de Extração ESL Cloud
 * @version 1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FaturaPorClienteDTO {

    // Documentos Fiscais
    @JsonProperty("fit_nse_number")
    @JsonAlias({"nfse_number"})
    private Long nfseNumber;

    @JsonProperty("fit_fhe_cte_number")
    private Long cteNumber;

    @JsonProperty("fit_fhe_cte_issued_at")
    private String cteIssuedAt;

    @JsonProperty("fit_fhe_cte_key")
    private String cteKey;

    @JsonProperty("fit_fhe_cte_status_result")
    private String cteStatusResult;

    @JsonProperty("fit_fhe_cte_status")
    private String cteStatus;

    // Dados da Fatura (Cobrança)
    @JsonProperty("fit_ant_document")
    private String faturaDocument;

    @JsonProperty("fit_ant_issue_date")
    private String faturaIssueDate;

    @JsonProperty("fit_ant_value")
    private String faturaValue;

    @JsonProperty("fit_ant_ils_due_date")
    private String faturaDueDate;

    @JsonProperty("fit_ant_ils_atn_transaction_date")
    private String faturaBaixaDate;

    @JsonProperty("fit_ant_ils_original_due_date")
    private String faturaOriginalDueDate;

    // Valores
    @JsonProperty("total")
    private String valorFrete;

    @JsonProperty("third_party_ctes_value")
    private String thirdPartyCtesValue;

    @JsonProperty("type")
    private String tipoFrete;

    // Classificação Operacional
    @JsonProperty("fit_crn_psn_nickname")
    private String filial;

    @JsonProperty("fit_diy_sae_name")
    private String estado;

    @JsonProperty("fit_fsn_name")
    private String classificacao;

    // Envolvidos
    @JsonProperty("fit_pyr_name")
    private String pagadorNome;

    @JsonProperty("fit_pyr_document")
    private String pagadorDocumento;

    @JsonProperty("fit_rpt_name")
    private String remetenteNome;

    @JsonProperty("fit_rpt_document")
    private String remetenteDocumento;

    @JsonProperty("fit_sdr_name")
    private String destinatarioNome;

    @JsonProperty("fit_sdr_document")
    private String destinatarioDocumento;

    @JsonProperty("fit_sps_slr_psn_name")
    private String vendedorNome;

    // Listas (Arrays)
    @JsonProperty("invoices_mapping")
    private List<String> notasFiscais;

    @JsonProperty("fit_fte_invoices_order_number")
    private List<String> pedidosCliente;

    // Campo para armazenar ID da cobrança (creditCustomerBilling) - pode estar no metadata
    // Usado para enriquecimento via GraphQL
    private String billingId;

    private final Map<String, Object> otherProperties = new LinkedHashMap<>();

    // Getters e Setters
    public Long getNfseNumber() {
        return nfseNumber;
    }

    public void setNfseNumber(final Long nfseNumber) {
        this.nfseNumber = nfseNumber;
    }

    public Long getCteNumber() {
        return cteNumber;
    }

    public void setCteNumber(final Long cteNumber) {
        this.cteNumber = cteNumber;
    }

    public String getCteIssuedAt() {
        return cteIssuedAt;
    }

    public void setCteIssuedAt(final String cteIssuedAt) {
        this.cteIssuedAt = cteIssuedAt;
    }

    public String getCteKey() {
        return cteKey;
    }

    public void setCteKey(final String cteKey) {
        this.cteKey = cteKey;
    }

    public String getCteStatusResult() {
        return cteStatusResult;
    }

    public void setCteStatusResult(final String cteStatusResult) {
        this.cteStatusResult = cteStatusResult;
    }

    public String getCteStatus() {
        return cteStatus;
    }

    public void setCteStatus(final String cteStatus) {
        this.cteStatus = cteStatus;
    }

    public String getFaturaDocument() {
        return faturaDocument;
    }

    public void setFaturaDocument(final String faturaDocument) {
        this.faturaDocument = faturaDocument;
    }

    public String getFaturaIssueDate() {
        return faturaIssueDate;
    }

    public void setFaturaIssueDate(final String faturaIssueDate) {
        this.faturaIssueDate = faturaIssueDate;
    }

    public String getFaturaValue() {
        return faturaValue;
    }

    public void setFaturaValue(final String faturaValue) {
        this.faturaValue = faturaValue;
    }

    public String getFaturaDueDate() {
        return faturaDueDate;
    }

    public void setFaturaDueDate(final String faturaDueDate) {
        this.faturaDueDate = faturaDueDate;
    }

    public String getFaturaBaixaDate() {
        return faturaBaixaDate;
    }

    public void setFaturaBaixaDate(final String faturaBaixaDate) {
        this.faturaBaixaDate = faturaBaixaDate;
    }

    public String getFaturaOriginalDueDate() {
        return faturaOriginalDueDate;
    }

    public void setFaturaOriginalDueDate(final String faturaOriginalDueDate) {
        this.faturaOriginalDueDate = faturaOriginalDueDate;
    }

    public String getValorFrete() {
        return valorFrete;
    }

    public void setValorFrete(final String valorFrete) {
        this.valorFrete = valorFrete;
    }

    public String getThirdPartyCtesValue() {
        return thirdPartyCtesValue;
    }

    public void setThirdPartyCtesValue(final String thirdPartyCtesValue) {
        this.thirdPartyCtesValue = thirdPartyCtesValue;
    }

    public String getTipoFrete() {
        return tipoFrete;
    }

    public void setTipoFrete(final String tipoFrete) {
        this.tipoFrete = tipoFrete;
    }

    public String getFilial() {
        return filial;
    }

    public void setFilial(final String filial) {
        this.filial = filial;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(final String estado) {
        this.estado = estado;
    }

    public String getClassificacao() {
        return classificacao;
    }

    public void setClassificacao(final String classificacao) {
        this.classificacao = classificacao;
    }

    public String getPagadorNome() {
        return pagadorNome;
    }

    public void setPagadorNome(final String pagadorNome) {
        this.pagadorNome = pagadorNome;
    }

    public String getPagadorDocumento() {
        return pagadorDocumento;
    }

    public void setPagadorDocumento(final String pagadorDocumento) {
        this.pagadorDocumento = pagadorDocumento;
    }

    public String getRemetenteNome() {
        return remetenteNome;
    }

    public void setRemetenteNome(final String remetenteNome) {
        this.remetenteNome = remetenteNome;
    }

    public String getRemetenteDocumento() {
        return remetenteDocumento;
    }

    public void setRemetenteDocumento(final String remetenteDocumento) {
        this.remetenteDocumento = remetenteDocumento;
    }

    public String getDestinatarioNome() {
        return destinatarioNome;
    }

    public void setDestinatarioNome(final String destinatarioNome) {
        this.destinatarioNome = destinatarioNome;
    }

    public String getDestinatarioDocumento() {
        return destinatarioDocumento;
    }

    public void setDestinatarioDocumento(final String destinatarioDocumento) {
        this.destinatarioDocumento = destinatarioDocumento;
    }

    public String getVendedorNome() {
        return vendedorNome;
    }

    public void setVendedorNome(final String vendedorNome) {
        this.vendedorNome = vendedorNome;
    }

    public List<String> getNotasFiscais() {
        return notasFiscais;
    }

    public void setNotasFiscais(final List<String> notasFiscais) {
        this.notasFiscais = notasFiscais;
    }

    public List<String> getPedidosCliente() {
        return pedidosCliente;
    }

    public void setPedidosCliente(final List<String> pedidosCliente) {
        this.pedidosCliente = pedidosCliente;
    }

    public String getBillingId() {
        return billingId;
    }

    public void setBillingId(final String billingId) {
        this.billingId = billingId;
    }

    @JsonAnySetter
    public void setOtherProperty(final String name, final Object value) {
        otherProperties.put(name, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getOtherProperties() {
        return otherProperties;
    }
}
