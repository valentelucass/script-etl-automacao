/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/entity/FaturaPorClienteEntity.java
Classe  : FaturaPorClienteEntity (class)
Pacote  : br.com.extrator.db.entity
Modulo  : Entidade de persistencia
Papel   : Implementa responsabilidade de fatura por cliente entity.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Define estrutura de dados persistida no banco.
2) Representa campos de tabela/view no dominio Java.
3) Suporta transporte de dados entre camadas.

Estrutura interna:
Metodos principais:
- getUniqueId(): expone valor atual do estado interno.
- setUniqueId(...1 args): ajusta valor em estado interno.
- getValorFrete(): expone valor atual do estado interno.
- setValorFrete(...1 args): ajusta valor em estado interno.
- getValorFatura(): expone valor atual do estado interno.
- setValorFatura(...1 args): ajusta valor em estado interno.
- getThirdPartyCtesValue(): expone valor atual do estado interno.
- setThirdPartyCtesValue(...1 args): ajusta valor em estado interno.
- getNumeroCte(): expone valor atual do estado interno.
- setNumeroCte(...1 args): ajusta valor em estado interno.
- getChaveCte(): expone valor atual do estado interno.
- setChaveCte(...1 args): ajusta valor em estado interno.
- getNumeroNfse(): expone valor atual do estado interno.
- setNumeroNfse(...1 args): ajusta valor em estado interno.
Atributos-chave:
- uniqueId: campo de estado para "unique id".
- valorFrete: campo de estado para "valor frete".
- valorFatura: campo de estado para "valor fatura".
- thirdPartyCtesValue: campo de estado para "third party ctes value".
- numeroCte: campo de estado para "numero cte".
- chaveCte: campo de estado para "chave cte".
- numeroNfse: campo de estado para "numero nfse".
- statusCte: campo de estado para "status cte".
- statusCteResult: campo de estado para "status cte result".
- dataEmissaoCte: campo de estado para "data emissao cte".
- numeroFatura: campo de estado para "numero fatura".
- dataEmissaoFatura: campo de estado para "data emissao fatura".
- dataVencimentoFatura: campo de estado para "data vencimento fatura".
- dataBaixaFatura: campo de estado para "data baixa fatura".
[DOC-FILE-END]============================================================== */

package br.com.extrator.db.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Entity que representa a tabela faturas_por_cliente no banco.
 * Armazena dados híbridos de CT-e e NFS-e com informações de faturamento.
 * 
 * @author Sistema de Extração ESL Cloud
 * @version 1.0
 */
public class FaturaPorClienteEntity {

    // Chave Primária
    private String uniqueId;

    // Valores
    private BigDecimal valorFrete;
    private BigDecimal valorFatura;
    private BigDecimal thirdPartyCtesValue;

    // Documentos Fiscais
    private Long numeroCte;
    private String chaveCte;
    private Long numeroNfse;
    private String statusCte;
    private String statusCteResult;
    private OffsetDateTime dataEmissaoCte;

    // Dados da Fatura (Cobrança)
    private String numeroFatura;
    private LocalDate dataEmissaoFatura;
    private LocalDate dataVencimentoFatura;
    private LocalDate dataBaixaFatura;
    private LocalDate fitAntOriginalDueDate;

    private String fitAntDocument;
    private LocalDate fitAntIssueDate;
    private BigDecimal fitAntValue;

    // Classificação Operacional
    private String filial;
    private String tipoFrete;
    private String classificacao;
    private String estado;

    // Envolvidos
    private String pagadorNome;
    private String pagadorDocumento;
    private String remetenteNome;
    private String remetenteDocumento;
    private String destinatarioNome;
    private String destinatarioDocumento;
    private String vendedorNome;

    // Listas (convertidas para texto)
    private String notasFiscais;
    private String pedidosCliente;

    // Sistema
    private String metadata;

    // Getters e Setters
    public String getUniqueId() {
        return uniqueId;
    }

    public void setUniqueId(final String uniqueId) {
        this.uniqueId = uniqueId;
    }

    public BigDecimal getValorFrete() {
        return valorFrete;
    }

    public void setValorFrete(final BigDecimal valorFrete) {
        this.valorFrete = valorFrete;
    }

    public BigDecimal getValorFatura() {
        return valorFatura;
    }

    public void setValorFatura(final BigDecimal valorFatura) {
        this.valorFatura = valorFatura;
    }

    public BigDecimal getThirdPartyCtesValue() {
        return thirdPartyCtesValue;
    }

    public void setThirdPartyCtesValue(final BigDecimal thirdPartyCtesValue) {
        this.thirdPartyCtesValue = thirdPartyCtesValue;
    }

    public Long getNumeroCte() {
        return numeroCte;
    }

    public void setNumeroCte(final Long numeroCte) {
        this.numeroCte = numeroCte;
    }

    public String getChaveCte() {
        return chaveCte;
    }

    public void setChaveCte(final String chaveCte) {
        this.chaveCte = chaveCte;
    }

    public Long getNumeroNfse() {
        return numeroNfse;
    }

    public void setNumeroNfse(final Long numeroNfse) {
        this.numeroNfse = numeroNfse;
    }

    public String getStatusCte() {
        return statusCte;
    }

    public void setStatusCte(final String statusCte) {
        this.statusCte = statusCte;
    }

    public String getStatusCteResult() {
        return statusCteResult;
    }

    public void setStatusCteResult(final String statusCteResult) {
        this.statusCteResult = statusCteResult;
    }

    public OffsetDateTime getDataEmissaoCte() {
        return dataEmissaoCte;
    }

    public void setDataEmissaoCte(final OffsetDateTime dataEmissaoCte) {
        this.dataEmissaoCte = dataEmissaoCte;
    }

    public String getNumeroFatura() {
        return numeroFatura;
    }

    public void setNumeroFatura(final String numeroFatura) {
        this.numeroFatura = numeroFatura;
    }

    public LocalDate getDataEmissaoFatura() {
        return dataEmissaoFatura;
    }

    public void setDataEmissaoFatura(final LocalDate dataEmissaoFatura) {
        this.dataEmissaoFatura = dataEmissaoFatura;
    }

    public LocalDate getDataVencimentoFatura() {
        return dataVencimentoFatura;
    }

    public void setDataVencimentoFatura(final LocalDate dataVencimentoFatura) {
        this.dataVencimentoFatura = dataVencimentoFatura;
    }

    public LocalDate getDataBaixaFatura() {
        return dataBaixaFatura;
    }

    public void setDataBaixaFatura(final LocalDate dataBaixaFatura) {
        this.dataBaixaFatura = dataBaixaFatura;
    }

    public LocalDate getFitAntOriginalDueDate() {
        return fitAntOriginalDueDate;
    }

    public void setFitAntOriginalDueDate(final LocalDate fitAntOriginalDueDate) {
        this.fitAntOriginalDueDate = fitAntOriginalDueDate;
    }

    public String getFitAntDocument() {
        return fitAntDocument;
    }

    public void setFitAntDocument(final String fitAntDocument) {
        this.fitAntDocument = fitAntDocument;
    }

    public LocalDate getFitAntIssueDate() {
        return fitAntIssueDate;
    }

    public void setFitAntIssueDate(final LocalDate fitAntIssueDate) {
        this.fitAntIssueDate = fitAntIssueDate;
    }

    public BigDecimal getFitAntValue() {
        return fitAntValue;
    }

    public void setFitAntValue(final BigDecimal fitAntValue) {
        this.fitAntValue = fitAntValue;
    }

    public String getFilial() {
        return filial;
    }

    public void setFilial(final String filial) {
        this.filial = filial;
    }

    public String getTipoFrete() {
        return tipoFrete;
    }

    public void setTipoFrete(final String tipoFrete) {
        this.tipoFrete = tipoFrete;
    }

    public String getClassificacao() {
        return classificacao;
    }

    public void setClassificacao(final String classificacao) {
        this.classificacao = classificacao;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(final String estado) {
        this.estado = estado;
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

    public String getNotasFiscais() {
        return notasFiscais;
    }

    public void setNotasFiscais(final String notasFiscais) {
        this.notasFiscais = notasFiscais;
    }

    public String getPedidosCliente() {
        return pedidosCliente;
    }

    public void setPedidosCliente(final String pedidosCliente) {
        this.pedidosCliente = pedidosCliente;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(final String metadata) {
        this.metadata = metadata;
    }

    public boolean temFaturaConsolidada() {
        if (numeroFatura != null && dataEmissaoFatura != null && valorFatura != null) {
            return true;
        }
        return fitAntDocument != null && fitAntIssueDate != null && fitAntValue != null;
    }
}
