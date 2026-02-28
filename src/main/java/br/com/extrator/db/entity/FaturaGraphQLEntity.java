/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/entity/FaturaGraphQLEntity.java
Classe  : FaturaGraphQLEntity (class)
Pacote  : br.com.extrator.db.entity
Modulo  : Entidade de persistencia
Papel   : Implementa responsabilidade de fatura graph qlentity.

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
- getDocument(): expone valor atual do estado interno.
- setDocument(...1 args): ajusta valor em estado interno.
- getIssueDate(): expone valor atual do estado interno.
- setIssueDate(...1 args): ajusta valor em estado interno.
- getDueDate(): expone valor atual do estado interno.
- setDueDate(...1 args): ajusta valor em estado interno.
- getOriginalDueDate(): expone valor atual do estado interno.
- setOriginalDueDate(...1 args): ajusta valor em estado interno.
- getValue(): expone valor atual do estado interno.
- setValue(...1 args): ajusta valor em estado interno.
- getPaidValue(): expone valor atual do estado interno.
- setPaidValue(...1 args): ajusta valor em estado interno.
Atributos-chave:
- id: campo de estado para "id".
- document: campo de estado para "document".
- issueDate: campo de estado para "issue date".
- dueDate: campo de estado para "due date".
- originalDueDate: campo de estado para "original due date".
- value: campo de estado para "value".
- paidValue: campo de estado para "paid value".
- valueToPay: campo de estado para "value to pay".
- discountValue: campo de estado para "discount value".
- interestValue: campo de estado para "interest value".
- paid: campo de estado para "paid".
- status: campo de estado para "status".
- type: campo de estado para "type".
- comments: campo de estado para "comments".
[DOC-FILE-END]============================================================== */

package br.com.extrator.db.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public class FaturaGraphQLEntity {
    private Long id;
    private String document;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private LocalDate originalDueDate;
    private BigDecimal value;
    private BigDecimal paidValue;
    private BigDecimal valueToPay;
    private BigDecimal discountValue;
    private BigDecimal interestValue;
    private Boolean paid;
    private String status;
    private String type;
    private String comments;
    private Integer sequenceCode;
    private Integer competenceMonth;
    private Integer competenceYear;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    
    // Relacionamentos
    private Long corporationId;
    private String corporationName;
    private String corporationCnpj;
    
    // Campos enriquecidos da primeira parcela (installments[0])
    private String nfseNumero;           // N° NFS-e (accountingCredit.document)
    private String carteiraBanco;         // Carteira/Descrição (accountingBankAccount.portfolioVariation)
    private String instrucaoBoleto;      // Instrução Customizada (accountingBankAccount.customInstruction)
    private String bancoNome;            // Nome do Banco (via bankAccount.bankName)
    private String metodoPagamento;      // Método de Pagamento (installments[0].paymentMethod)
    
    // Metadata (JSON completo)
    private String metadata;

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(final Long id) { this.id = id; }
    
    public String getDocument() { return document; }
    public void setDocument(final String document) { this.document = document; }
    
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(final LocalDate issueDate) { this.issueDate = issueDate; }
    
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(final LocalDate dueDate) { this.dueDate = dueDate; }
    
    public LocalDate getOriginalDueDate() { return originalDueDate; }
    public void setOriginalDueDate(final LocalDate originalDueDate) { this.originalDueDate = originalDueDate; }
    
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
    
    public String getStatus() { return status; }
    public void setStatus(final String status) { this.status = status; }
    
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
    
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(final OffsetDateTime createdAt) { this.createdAt = createdAt; }
    
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(final OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    
    public Long getCorporationId() { return corporationId; }
    public void setCorporationId(final Long corporationId) { this.corporationId = corporationId; }
    
    public String getCorporationName() { return corporationName; }
    public void setCorporationName(final String corporationName) { this.corporationName = corporationName; }
    
    public String getCorporationCnpj() { return corporationCnpj; }
    public void setCorporationCnpj(final String corporationCnpj) { this.corporationCnpj = corporationCnpj; }
    
    public String getNfseNumero() { return nfseNumero; }
    public void setNfseNumero(final String nfseNumero) { this.nfseNumero = nfseNumero; }
    
    public String getCarteiraBanco() { return carteiraBanco; }
    public void setCarteiraBanco(final String carteiraBanco) { this.carteiraBanco = carteiraBanco; }
    
    public String getInstrucaoBoleto() { return instrucaoBoleto; }
    public void setInstrucaoBoleto(final String instrucaoBoleto) { this.instrucaoBoleto = instrucaoBoleto; }
    
    public String getBancoNome() { return bancoNome; }
    public void setBancoNome(final String bancoNome) { this.bancoNome = bancoNome; }
    
    public String getMetodoPagamento() { return metodoPagamento; }
    public void setMetodoPagamento(final String metodoPagamento) { this.metodoPagamento = metodoPagamento; }
    
    public String getMetadata() { return metadata; }
    public void setMetadata(final String metadata) { this.metadata = metadata; }
}
