/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/faturas/CreditCustomerBillingNodeDTO.java
Classe  : CreditCustomerBillingNodeDTO (class)
Pacote  : br.com.extrator.modelo.graphql.faturas
Modulo  : DTO/Mapper GraphQL
Papel   : Implementa responsabilidade de credit customer billing node dto.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela payloads da API GraphQL.
2) Mapeia estrutura remota para modelo interno.
3) Apoia persistencia e validacao do extrator.

Estrutura interna:
Metodos principais:
- getId(): expone valor atual do estado interno.
- setId(...1 args): ajusta valor em estado interno.
- getDocument(): expone valor atual do estado interno.
- setDocument(...1 args): ajusta valor em estado interno.
- getIssueDate(): expone valor atual do estado interno.
- setIssueDate(...1 args): ajusta valor em estado interno.
- getDueDate(): expone valor atual do estado interno.
- setDueDate(...1 args): ajusta valor em estado interno.
- getValue(): expone valor atual do estado interno.
- setValue(...1 args): ajusta valor em estado interno.
- getPaidValue(): expone valor atual do estado interno.
- setPaidValue(...1 args): ajusta valor em estado interno.
- getValueToPay(): expone valor atual do estado interno.
- setValueToPay(...1 args): ajusta valor em estado interno.
Atributos-chave:
- id: campo de estado para "id".
- document: campo de estado para "document".
- issueDate: campo de estado para "issue date".
- dueDate: campo de estado para "due date".
- value: campo de estado para "value".
- paidValue: campo de estado para "paid value".
- valueToPay: campo de estado para "value to pay".
- discountValue: campo de estado para "discount value".
- interestValue: campo de estado para "interest value".
- paid: campo de estado para "paid".
- type: campo de estado para "type".
- comments: campo de estado para "comments".
- sequenceCode: campo de estado para "sequence code".
- competenceMonth: campo de estado para "competence month".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.graphql.faturas;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CreditCustomerBillingNodeDTO {
    @JsonProperty("id")
    private Long id;

    @JsonProperty("document")
    private String document;

    @JsonProperty("issueDate")
    private String issueDate;

    @JsonProperty("dueDate")
    private String dueDate;

    @JsonProperty("value")
    private BigDecimal value;

    @JsonProperty("paidValue")
    private BigDecimal paidValue;

    @JsonProperty("valueToPay")
    private BigDecimal valueToPay;

    @JsonProperty("discountValue")
    private BigDecimal discountValue;

    @JsonProperty("interestValue")
    private BigDecimal interestValue;

    @JsonProperty("paid")
    private Boolean paid;

    @JsonProperty("type")
    private String type;

    @JsonProperty("comments")
    private String comments;

    @JsonProperty("sequenceCode")
    private Integer sequenceCode;

    @JsonProperty("competenceMonth")
    private Integer competenceMonth;

    @JsonProperty("competenceYear")
    private Integer competenceYear;
    
    @JsonProperty("ticketAccountId")
    private Integer ticketAccountId;

    public static class CustomerDTO {
        @JsonProperty("id")
        private String id;
        
        @JsonProperty("nickname")
        private String nickname;
        
        public static class PersonDTO {
            @JsonProperty("name")
            private String name;
            
            @JsonProperty("cnpj")
            private String cnpj;
            
            public String getName() { return name; }
            public void setName(final String name) { this.name = name; }
            public String getCnpj() { return cnpj; }
            public void setCnpj(final String cnpj) { this.cnpj = cnpj; }
        }
        
        @JsonProperty("person")
        private PersonDTO person;
        
        @JsonProperty("name")
        private String name;
        
        @JsonProperty("cnpj")
        private String cnpj;
        
        public String getId() { return id; }
        public void setId(final String id) { this.id = id; }
        public String getNickname() { return nickname; }
        public void setNickname(final String nickname) { this.nickname = nickname; }
        public PersonDTO getPerson() { return person; }
        public void setPerson(final PersonDTO person) { this.person = person; }
        public String getName() { return name; }
        public void setName(final String name) { this.name = name; }
        public String getCnpj() { return cnpj; }
        public void setCnpj(final String cnpj) { this.cnpj = cnpj; }
    }

    public static class CorporationDTO {
        @JsonProperty("id")
        private String id;
        
        public static class PersonDTO {
            @JsonProperty("nickname")
            private String nickname;
            
            @JsonProperty("cnpj")
            private String cnpj;
            
            public String getNickname() { return nickname; }
            public void setNickname(final String nickname) { this.nickname = nickname; }
            public String getCnpj() { return cnpj; }
            public void setCnpj(final String cnpj) { this.cnpj = cnpj; }
        }
        
        @JsonProperty("person")
        private PersonDTO person;
        
        public String getId() { return id; }
        public void setId(final String id) { this.id = id; }
        public PersonDTO getPerson() { return person; }
        public void setPerson(final PersonDTO person) { this.person = person; }
    }

    public static class InstallmentDTO {
        @JsonProperty("id")
        private Long id;
        
        @JsonProperty("position")
        private Integer position;
        
        @JsonProperty("sequenceCode")
        private Integer sequenceCode;
        
        @JsonProperty("value")
        private BigDecimal value;
        
        @JsonProperty("valueToPay")
        private BigDecimal valueToPay;
        
        @JsonProperty("dueDate")
        private String dueDate;
        
        @JsonProperty("originalDueDate")
        private String originalDueDate;
        
        @JsonProperty("status")
        private String status;
        
        @JsonProperty("paymentMethod")
        private String paymentMethod;
        
        @JsonProperty("accountingCredit")
        private AccountingCreditDTO accountingCredit;
        
        @JsonProperty("accountingBankAccount")
        private AccountingBankAccountDTO accountingBankAccount;
        
        public Long getId() { return id; }
        public void setId(final Long id) { this.id = id; }
        public Integer getPosition() { return position; }
        public void setPosition(final Integer position) { this.position = position; }
        public Integer getSequenceCode() { return sequenceCode; }
        public void setSequenceCode(final Integer sequenceCode) { this.sequenceCode = sequenceCode; }
        public BigDecimal getValue() { return value; }
        public void setValue(final BigDecimal value) { this.value = value; }
        public BigDecimal getValueToPay() { return valueToPay; }
        public void setValueToPay(final BigDecimal valueToPay) { this.valueToPay = valueToPay; }
        public String getDueDate() { return dueDate; }
        public void setDueDate(final String dueDate) { this.dueDate = dueDate; }
        public String getOriginalDueDate() { return originalDueDate; }
        public void setOriginalDueDate(final String originalDueDate) { this.originalDueDate = originalDueDate; }
        public String getStatus() { return status; }
        public void setStatus(final String status) { this.status = status; }
        public String getPaymentMethod() { return paymentMethod; }
        public void setPaymentMethod(final String paymentMethod) { this.paymentMethod = paymentMethod; }
        public AccountingCreditDTO getAccountingCredit() { return accountingCredit; }
        public void setAccountingCredit(final AccountingCreditDTO accountingCredit) { this.accountingCredit = accountingCredit; }
        public AccountingBankAccountDTO getAccountingBankAccount() { return accountingBankAccount; }
        public void setAccountingBankAccount(final AccountingBankAccountDTO accountingBankAccount) { this.accountingBankAccount = accountingBankAccount; }
    }
    
    /**
     * DTO para AccountingCredit (dados fiscais da parcela).
     * Contém o número da NFS-e (document).
     */
    public static class AccountingCreditDTO {
        @JsonProperty("document")
        private String document;
        
        public String getDocument() { return document; }
        public void setDocument(final String document) { this.document = document; }
    }
    
    /**
     * DTO para AccountingBankAccount (dados bancários da parcela).
     * Contém carteira (portfolioVariation) e instrução customizada (customInstruction).
     */
    public static class AccountingBankAccountDTO {
        @JsonProperty("bankName")
        private String bankName;
        
        @JsonProperty("portfolioVariation")
        private String portfolioVariation;
        
        @JsonProperty("customInstruction")
        private String customInstruction;
        
        public String getBankName() { return bankName; }
        public void setBankName(final String bankName) { this.bankName = bankName; }
        public String getPortfolioVariation() { return portfolioVariation; }
        public void setPortfolioVariation(final String portfolioVariation) { this.portfolioVariation = portfolioVariation; }
        public String getCustomInstruction() { return customInstruction; }
        public void setCustomInstruction(final String customInstruction) { this.customInstruction = customInstruction; }
    }

    @JsonProperty("customer")
    private CustomerDTO customer;

    @JsonProperty("corporation")
    private CorporationDTO corporation;

    @JsonProperty("installments")
    private List<InstallmentDTO> installments;

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(final Long id) { this.id = id; }
    
    public String getDocument() { return document; }
    public void setDocument(final String document) { this.document = document; }
    
    public String getIssueDate() { return issueDate; }
    public void setIssueDate(final String issueDate) { this.issueDate = issueDate; }
    
    public String getDueDate() { return dueDate; }
    public void setDueDate(final String dueDate) { this.dueDate = dueDate; }
    
    public BigDecimal getValue() { return value; }
    public void setValue(final BigDecimal value) { this.value = value; }
    
    public BigDecimal getPaidValue() { return paidValue; }
    public void setPaidValue(final BigDecimal paidValue) { this.paidValue = paidValue; }
    
    public BigDecimal getValueToPay() { return valueToPay; }
    public void setValueToPay(final BigDecimal valueToPay) { this.valueToPay = valueToPay; }
    
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(final BigDecimal discountValue) { this.discountValue = discountValue; }
    
    public BigDecimal getInterestValue() { return interestValue; }
    public void setInterestValue(final BigDecimal interestValue) { this.interestValue = interestValue; }
    
    public Boolean getPaid() { return paid; }
    public void setPaid(final Boolean paid) { this.paid = paid; }
    
    public String getType() { return type; }
    public void setType(final String type) { this.type = type; }
    
    public String getComments() { return comments; }
    public void setComments(final String comments) { this.comments = comments; }
    
    public Integer getSequenceCode() { return sequenceCode; }
    public void setSequenceCode(final Integer sequenceCode) { this.sequenceCode = sequenceCode; }
    
    public Integer getCompetenceMonth() { return competenceMonth; }
    public void setCompetenceMonth(final Integer competenceMonth) { this.competenceMonth = competenceMonth; }
    
    public Integer getCompetenceYear() { return competenceYear; }
    public void setCompetenceYear(final Integer competenceYear) { this.competenceYear = competenceYear; }
    
    public Integer getTicketAccountId() { return ticketAccountId; }
    public void setTicketAccountId(final Integer ticketAccountId) { this.ticketAccountId = ticketAccountId; }
    
    public CustomerDTO getCustomer() { return customer; }
    public void setCustomer(final CustomerDTO customer) { this.customer = customer; }
    
    public CorporationDTO getCorporation() { return corporation; }
    public void setCorporation(final CorporationDTO corporation) { this.corporation = corporation; }
    
    public List<InstallmentDTO> getInstallments() { return installments; }
    public void setInstallments(final List<InstallmentDTO> installments) { this.installments = installments; }
}
