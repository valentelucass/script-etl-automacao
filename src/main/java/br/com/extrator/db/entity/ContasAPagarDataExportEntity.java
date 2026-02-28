/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/entity/ContasAPagarDataExportEntity.java
Classe  : ContasAPagarDataExportEntity (class)
Pacote  : br.com.extrator.db.entity
Modulo  : Entidade de persistencia
Papel   : Implementa responsabilidade de contas apagar data export entity.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Define estrutura de dados persistida no banco.
2) Representa campos de tabela/view no dominio Java.
3) Suporta transporte de dados entre camadas.

Estrutura interna:
Metodos principais:
- ContasAPagarDataExportEntity(): realiza operacao relacionada a "contas apagar data export entity".
- getSequenceCode(): expone valor atual do estado interno.
- setSequenceCode(...1 args): ajusta valor em estado interno.
- getDocumentNumber(): expone valor atual do estado interno.
- setDocumentNumber(...1 args): ajusta valor em estado interno.
- getIssueDate(): expone valor atual do estado interno.
- setIssueDate(...1 args): ajusta valor em estado interno.
- getTipoLancamento(): expone valor atual do estado interno.
- setTipoLancamento(...1 args): ajusta valor em estado interno.
- getValorOriginal(): expone valor atual do estado interno.
- setValorOriginal(...1 args): ajusta valor em estado interno.
- getValorJuros(): expone valor atual do estado interno.
- setValorJuros(...1 args): ajusta valor em estado interno.
- getValorDesconto(): expone valor atual do estado interno.
Atributos-chave:
- sequenceCode: campo de estado para "sequence code".
- documentNumber: campo de estado para "document number".
- issueDate: campo de estado para "issue date".
- tipoLancamento: campo de estado para "tipo lancamento".
- valorOriginal: campo de estado para "valor original".
- valorJuros: campo de estado para "valor juros".
- valorDesconto: campo de estado para "valor desconto".
- valorAPagar: campo de estado para "valor apagar".
- valorPago: campo de estado para "valor pago".
- statusPagamento: campo de estado para "status pagamento".
- mesCompetencia: campo de estado para "mes competencia".
- anoCompetencia: campo de estado para "ano competencia".
- dataCriacao: campo de estado para "data criacao".
- dataLiquidacao: campo de estado para "data liquidacao".
[DOC-FILE-END]============================================================== */

package br.com.extrator.db.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Entidade que representa um registro de Fatura a Pagar (Data Export) na tabela faturas_a_pagar_data_export.
 * Template ID: 8636
 */
public class ContasAPagarDataExportEntity {
    
    // CHAVE PRIMÁRIA
    private Long sequenceCode;
    
    // DADOS DO DOCUMENTO
    private String documentNumber;
    private LocalDate issueDate;
    private String tipoLancamento; // Limpo (ex: "Manual" em vez de "Accounting::Debit::Manual")
    
    // VALORES FINANCEIROS (DECIMAL para análises)
    private BigDecimal valorOriginal;
    private BigDecimal valorJuros;
    private BigDecimal valorDesconto;
    private BigDecimal valorAPagar;
    private BigDecimal valorPago;
    
    // STATUS
    private String statusPagamento; // "PAGO" ou "ABERTO"
    
    // COMPETÊNCIA
    private Integer mesCompetencia;
    private Integer anoCompetencia;
    
    // DATAS
    private OffsetDateTime dataCriacao;
    private LocalDate dataLiquidacao;
    private LocalDate dataTransacao;
    
    // FORNECEDOR
    private String nomeFornecedor;
    
    // FILIAL
    private String nomeFilial;
    
    // CENTRO DE CUSTO
    private String nomeCentroCusto;
    private BigDecimal valorCentroCusto;
    
    // CONTA CONTÁBIL
    private String classificacaoContabil;
    private String descricaoContabil;
    private BigDecimal valorContabil;
    
    // ÁREA DE LANÇAMENTO
    private String areaLancamento;
    
    // OBSERVAÇÕES
    private String observacoes;
    private String descricaoDespesa;
    
    // USUÁRIO
    private String nomeUsuario;
    
    // RECONCILIAÇÃO
    private Boolean reconciliado;
    
    // METADATA (JSON completo)
    private String metadata;
    
    // DATA DE EXTRAÇÃO
    private LocalDateTime dataExtracao;
    
    // CONSTRUTOR VAZIO
    public ContasAPagarDataExportEntity() {}
    
    // GETTERS E SETTERS
    
    public Long getSequenceCode() {
        return sequenceCode;
    }
    
    public void setSequenceCode(final Long sequenceCode) {
        this.sequenceCode = sequenceCode;
    }
    
    public String getDocumentNumber() {
        return documentNumber;
    }
    
    public void setDocumentNumber(final String documentNumber) {
        this.documentNumber = documentNumber;
    }
    
    public LocalDate getIssueDate() {
        return issueDate;
    }
    
    public void setIssueDate(final LocalDate issueDate) {
        this.issueDate = issueDate;
    }
    
    public String getTipoLancamento() {
        return tipoLancamento;
    }
    
    public void setTipoLancamento(final String tipoLancamento) {
        this.tipoLancamento = tipoLancamento;
    }
    
    public BigDecimal getValorOriginal() {
        return valorOriginal;
    }
    
    public void setValorOriginal(final BigDecimal valorOriginal) {
        this.valorOriginal = valorOriginal;
    }
    
    public BigDecimal getValorJuros() {
        return valorJuros;
    }
    
    public void setValorJuros(final BigDecimal valorJuros) {
        this.valorJuros = valorJuros;
    }
    
    public BigDecimal getValorDesconto() {
        return valorDesconto;
    }
    
    public void setValorDesconto(final BigDecimal valorDesconto) {
        this.valorDesconto = valorDesconto;
    }
    
    public BigDecimal getValorAPagar() {
        return valorAPagar;
    }
    
    public void setValorAPagar(final BigDecimal valorAPagar) {
        this.valorAPagar = valorAPagar;
    }
    
    public BigDecimal getValorPago() {
        return valorPago;
    }
    
    public void setValorPago(final BigDecimal valorPago) {
        this.valorPago = valorPago;
    }
    
    public String getStatusPagamento() {
        return statusPagamento;
    }
    
    public void setStatusPagamento(final String statusPagamento) {
        this.statusPagamento = statusPagamento;
    }
    
    public Integer getMesCompetencia() {
        return mesCompetencia;
    }
    
    public void setMesCompetencia(final Integer mesCompetencia) {
        this.mesCompetencia = mesCompetencia;
    }
    
    public Integer getAnoCompetencia() {
        return anoCompetencia;
    }
    
    public void setAnoCompetencia(final Integer anoCompetencia) {
        this.anoCompetencia = anoCompetencia;
    }
    
    public OffsetDateTime getDataCriacao() {
        return dataCriacao;
    }
    
    public void setDataCriacao(final OffsetDateTime dataCriacao) {
        this.dataCriacao = dataCriacao;
    }
    
    public LocalDate getDataLiquidacao() {
        return dataLiquidacao;
    }
    
    public void setDataLiquidacao(final LocalDate dataLiquidacao) {
        this.dataLiquidacao = dataLiquidacao;
    }
    
    public LocalDate getDataTransacao() {
        return dataTransacao;
    }
    
    public void setDataTransacao(final LocalDate dataTransacao) {
        this.dataTransacao = dataTransacao;
    }
    
    public String getNomeFornecedor() {
        return nomeFornecedor;
    }
    
    public void setNomeFornecedor(final String nomeFornecedor) {
        this.nomeFornecedor = nomeFornecedor;
    }
    
    public String getNomeFilial() {
        return nomeFilial;
    }
    
    public void setNomeFilial(final String nomeFilial) {
        this.nomeFilial = nomeFilial;
    }
    
    public String getNomeCentroCusto() {
        return nomeCentroCusto;
    }
    
    public void setNomeCentroCusto(final String nomeCentroCusto) {
        this.nomeCentroCusto = nomeCentroCusto;
    }
    
    public BigDecimal getValorCentroCusto() {
        return valorCentroCusto;
    }
    
    public void setValorCentroCusto(final BigDecimal valorCentroCusto) {
        this.valorCentroCusto = valorCentroCusto;
    }
    
    public String getClassificacaoContabil() {
        return classificacaoContabil;
    }
    
    public void setClassificacaoContabil(final String classificacaoContabil) {
        this.classificacaoContabil = classificacaoContabil;
    }
    
    public String getDescricaoContabil() {
        return descricaoContabil;
    }
    
    public void setDescricaoContabil(final String descricaoContabil) {
        this.descricaoContabil = descricaoContabil;
    }
    
    public BigDecimal getValorContabil() {
        return valorContabil;
    }
    
    public void setValorContabil(final BigDecimal valorContabil) {
        this.valorContabil = valorContabil;
    }
    
    public String getAreaLancamento() {
        return areaLancamento;
    }
    
    public void setAreaLancamento(final String areaLancamento) {
        this.areaLancamento = areaLancamento;
    }
    
    public String getObservacoes() {
        return observacoes;
    }
    
    public void setObservacoes(final String observacoes) {
        this.observacoes = observacoes;
    }
    
    public String getDescricaoDespesa() {
        return descricaoDespesa;
    }
    
    public void setDescricaoDespesa(final String descricaoDespesa) {
        this.descricaoDespesa = descricaoDespesa;
    }
    
    public String getNomeUsuario() {
        return nomeUsuario;
    }
    
    public void setNomeUsuario(final String nomeUsuario) {
        this.nomeUsuario = nomeUsuario;
    }
    
    public Boolean getReconciliado() {
        return reconciliado;
    }
    
    public void setReconciliado(final Boolean reconciliado) {
        this.reconciliado = reconciliado;
    }
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(final String metadata) {
        this.metadata = metadata;
    }
    
    public LocalDateTime getDataExtracao() {
        return dataExtracao;
    }
    
    public void setDataExtracao(final LocalDateTime dataExtracao) {
        this.dataExtracao = dataExtracao;
    }
}