/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/dataexport/contasapagar/ContasAPagarDTO.java
Classe  : ContasAPagarDTO (class)
Pacote  : br.com.extrator.modelo.dataexport.contasapagar
Modulo  : DTO/Mapper DataExport
Papel   : Implementa responsabilidade de contas apagar dto.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela payloads da API DataExport.
2) Mapeia resposta para entidades internas.
3) Apoia carga e deduplicacao no destino.

Estrutura interna:
Metodos principais:
- ContasAPagarDTO(): realiza operacao relacionada a "contas apagar dto".
- getSequenceCode(): expone valor atual do estado interno.
- setSequenceCode(...1 args): ajusta valor em estado interno.
- getDocumentNumber(): expone valor atual do estado interno.
- setDocumentNumber(...1 args): ajusta valor em estado interno.
- getIssueDate(): expone valor atual do estado interno.
- setIssueDate(...1 args): ajusta valor em estado interno.
- getType(): expone valor atual do estado interno.
- setType(...1 args): ajusta valor em estado interno.
- getOriginalValue(): expone valor atual do estado interno.
- setOriginalValue(...1 args): ajusta valor em estado interno.
- getInterestValue(): expone valor atual do estado interno.
- setInterestValue(...1 args): ajusta valor em estado interno.
- getDiscountValue(): expone valor atual do estado interno.
Atributos-chave:
- sequenceCode: campo de estado para "sequence code".
- documentNumber: campo de estado para "document number".
- issueDate: campo de estado para "issue date".
- type: campo de estado para "type".
- originalValue: campo de estado para "original value".
- interestValue: campo de estado para "interest value".
- discountValue: campo de estado para "discount value".
- valueToPay: campo de estado para "value to pay".
- paid: campo de estado para "paid".
- paidValue: campo de estado para "paid value".
- competenceMonth: campo de estado para "competence month".
- competenceYear: campo de estado para "competence year".
- createdAt: campo de estado para "created at".
- liquidationDate: campo de estado para "liquidation date".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.dataexport.contasapagar;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO para dados de Contas a Pagar retornados pela API Data Export.
 * Template ID: 8636
 * Tabela: accounting_debits
 * Campo de filtro: issue_date
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContasAPagarDTO {
    
    // CHAVE PRIMÁRIA
    @JsonProperty("ant_ils_sequence_code")
    private String sequenceCode;
    
    // DADOS DO DOCUMENTO
    @JsonProperty("document")
    private String documentNumber;
    
    @JsonProperty("issue_date")
    private String issueDate;
    
    @JsonProperty("type")
    private String type;
    
    // VALORES FINANCEIROS
    @JsonProperty("value")
    private String originalValue;
    
    @JsonProperty("interest_value")
    private String interestValue;
    
    @JsonProperty("discount_value")
    private String discountValue;
    
    @JsonProperty("value_to_pay")
    private String valueToPay;
    
    @JsonProperty("paid")
    private Boolean paid;
    
    @JsonProperty("paid_value")
    private String paidValue;
    
    // COMPETÊNCIA
    @JsonProperty("competence_month")
    private String competenceMonth;
    
    @JsonProperty("competence_year")
    private String competenceYear;
    
    // DATAS ADICIONAIS
    @JsonProperty("created_at")
    private String createdAt;
    
    @JsonProperty("ant_ils_atn_liquidation_date")
    private String liquidationDate;
    
    @JsonProperty("ant_ils_atn_transaction_date")
    private String transactionDate;
    
    // FORNECEDOR
    @JsonProperty("ant_rir_name")
    private String providerName;
    
    // FILIAL
    @JsonProperty("ant_crn_psn_nickname")
    private String branchName;
    
    // CENTRO DE CUSTO
    @JsonProperty("ant_ces_acr_name")
    private String costCenterName;
    
    @JsonProperty("ant_ces_value")
    private String costCenterValue;
    
    // CONTA CONTÁBIL
    @JsonProperty("ant_ils_pas_ant_classification")
    private String accountingClassification;
    
    @JsonProperty("ant_ils_pas_ant_name")
    private String accountingDescription;
    
    @JsonProperty("ant_ils_pas_value")
    private String accountingValue;
    
    // ÁREA DE LANÇAMENTO
    @JsonProperty("ant_aln_name")
    private String launchAreaName;
    
    // OBSERVAÇÕES
    @JsonProperty("ant_ils_comments")
    private String comments;
    
    @JsonProperty("ant_ils_expense_description")
    private String expenseDescription;
    
    // USUÁRIO
    @JsonProperty("ant_uer_name")
    private String userName;
    
    // RECONCILIAÇÃO
    @JsonProperty("ant_ils_atn_reconciled")
    private Boolean reconciled;

    private final Map<String, Object> otherProperties = new LinkedHashMap<>();
    
    // CONSTRUTOR VAZIO
    public ContasAPagarDTO() {}
    
    // GETTERS E SETTERS
    
    public String getSequenceCode() {
        return sequenceCode;
    }
    
    public void setSequenceCode(final String sequenceCode) {
        this.sequenceCode = sequenceCode;
    }
    
    public String getDocumentNumber() {
        return documentNumber;
    }
    
    public void setDocumentNumber(final String documentNumber) {
        this.documentNumber = documentNumber;
    }
    
    public String getIssueDate() {
        return issueDate;
    }
    
    public void setIssueDate(final String issueDate) {
        this.issueDate = issueDate;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(final String type) {
        this.type = type;
    }
    
    public String getOriginalValue() {
        return originalValue;
    }
    
    public void setOriginalValue(final String originalValue) {
        this.originalValue = originalValue;
    }
    
    public String getInterestValue() {
        return interestValue;
    }
    
    public void setInterestValue(final String interestValue) {
        this.interestValue = interestValue;
    }
    
    public String getDiscountValue() {
        return discountValue;
    }
    
    public void setDiscountValue(final String discountValue) {
        this.discountValue = discountValue;
    }
    
    public String getValueToPay() {
        return valueToPay;
    }
    
    public void setValueToPay(final String valueToPay) {
        this.valueToPay = valueToPay;
    }
    
    public Boolean getPaid() {
        return paid;
    }
    
    public void setPaid(final Boolean paid) {
        this.paid = paid;
    }
    
    public String getPaidValue() {
        return paidValue;
    }
    
    public void setPaidValue(final String paidValue) {
        this.paidValue = paidValue;
    }
    
    public String getCompetenceMonth() {
        return competenceMonth;
    }
    
    public void setCompetenceMonth(final String competenceMonth) {
        this.competenceMonth = competenceMonth;
    }
    
    public String getCompetenceYear() {
        return competenceYear;
    }
    
    public void setCompetenceYear(final String competenceYear) {
        this.competenceYear = competenceYear;
    }
    
    public String getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(final String createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getLiquidationDate() {
        return liquidationDate;
    }
    
    public void setLiquidationDate(final String liquidationDate) {
        this.liquidationDate = liquidationDate;
    }
    
    public String getTransactionDate() {
        return transactionDate;
    }
    
    public void setTransactionDate(final String transactionDate) {
        this.transactionDate = transactionDate;
    }
    
    public String getProviderName() {
        return providerName;
    }
    
    public void setProviderName(final String providerName) {
        this.providerName = providerName;
    }
    
    public String getBranchName() {
        return branchName;
    }
    
    public void setBranchName(final String branchName) {
        this.branchName = branchName;
    }
    
    public String getCostCenterName() {
        return costCenterName;
    }
    
    public void setCostCenterName(final String costCenterName) {
        this.costCenterName = costCenterName;
    }
    
    public String getCostCenterValue() {
        return costCenterValue;
    }
    
    public void setCostCenterValue(final String costCenterValue) {
        this.costCenterValue = costCenterValue;
    }
    
    public String getAccountingClassification() {
        return accountingClassification;
    }
    
    public void setAccountingClassification(final String accountingClassification) {
        this.accountingClassification = accountingClassification;
    }
    
    public String getAccountingDescription() {
        return accountingDescription;
    }
    
    public void setAccountingDescription(final String accountingDescription) {
        this.accountingDescription = accountingDescription;
    }
    
    public String getAccountingValue() {
        return accountingValue;
    }
    
    public void setAccountingValue(final String accountingValue) {
        this.accountingValue = accountingValue;
    }
    
    public String getLaunchAreaName() {
        return launchAreaName;
    }
    
    public void setLaunchAreaName(final String launchAreaName) {
        this.launchAreaName = launchAreaName;
    }
    
    public String getComments() {
        return comments;
    }
    
    public void setComments(final String comments) {
        this.comments = comments;
    }
    
    public String getExpenseDescription() {
        return expenseDescription;
    }
    
    public void setExpenseDescription(final String expenseDescription) {
        this.expenseDescription = expenseDescription;
    }
    
    public String getUserName() {
        return userName;
    }
    
    public void setUserName(final String userName) {
        this.userName = userName;
    }
    
    public Boolean getReconciled() {
        return reconciled;
    }
    
    public void setReconciled(final Boolean reconciled) {
        this.reconciled = reconciled;
    }

    @JsonAnySetter
    public void add(final String key, final Object value) {
        this.otherProperties.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getAllProperties() {
        final Map<String, Object> all = new LinkedHashMap<>();
        all.put("ant_ils_sequence_code", sequenceCode);
        all.put("document", documentNumber);
        all.put("issue_date", issueDate);
        all.put("type", type);
        all.put("value", originalValue);
        all.put("interest_value", interestValue);
        all.put("discount_value", discountValue);
        all.put("value_to_pay", valueToPay);
        all.put("paid", paid);
        all.put("paid_value", paidValue);
        all.put("competence_month", competenceMonth);
        all.put("competence_year", competenceYear);
        all.put("created_at", createdAt);
        all.put("ant_ils_atn_liquidation_date", liquidationDate);
        all.put("ant_ils_atn_transaction_date", transactionDate);
        all.put("ant_rir_name", providerName);
        all.put("ant_crn_psn_nickname", branchName);
        all.put("ant_ces_acr_name", costCenterName);
        all.put("ant_ces_value", costCenterValue);
        all.put("ant_ils_pas_ant_classification", accountingClassification);
        all.put("ant_ils_pas_ant_name", accountingDescription);
        all.put("ant_ils_pas_value", accountingValue);
        all.put("ant_aln_name", launchAreaName);
        all.put("ant_ils_comments", comments);
        all.put("ant_ils_expense_description", expenseDescription);
        all.put("ant_uer_name", userName);
        all.put("ant_ils_atn_reconciled", reconciled);
        if (otherProperties != null && !otherProperties.isEmpty()) {
            for (final Map.Entry<String, Object> e : otherProperties.entrySet()) {
                all.putIfAbsent(e.getKey(), e.getValue());
            }
        }
        return all;
    }
}
