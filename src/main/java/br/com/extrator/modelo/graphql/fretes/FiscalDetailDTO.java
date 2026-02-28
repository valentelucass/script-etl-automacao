/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/fretes/FiscalDetailDTO.java
Classe  : FiscalDetailDTO (class)
Pacote  : br.com.extrator.modelo.graphql.fretes
Modulo  : DTO/Mapper GraphQL
Papel   : Implementa responsabilidade de fiscal detail dto.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela payloads da API GraphQL.
2) Mapeia estrutura remota para modelo interno.
3) Apoia persistencia e validacao do extrator.

Estrutura interna:
Metodos principais:
- getCstType(): expone valor atual do estado interno.
- setCstType(...1 args): ajusta valor em estado interno.
- getTaxValue(): expone valor atual do estado interno.
- setTaxValue(...1 args): ajusta valor em estado interno.
- getPisValue(): expone valor atual do estado interno.
- setPisValue(...1 args): ajusta valor em estado interno.
- getCofinsValue(): expone valor atual do estado interno.
- setCofinsValue(...1 args): ajusta valor em estado interno.
- getCfopCode(): expone valor atual do estado interno.
- setCfopCode(...1 args): ajusta valor em estado interno.
- getCalculationBasis(): expone valor atual do estado interno.
- setCalculationBasis(...1 args): ajusta valor em estado interno.
- getTaxRate(): expone valor atual do estado interno.
- setTaxRate(...1 args): ajusta valor em estado interno.
Atributos-chave:
- cstType: campo de estado para "cst type".
- taxValue: campo de estado para "tax value".
- pisValue: campo de estado para "pis value".
- cofinsValue: campo de estado para "cofins value".
- cfopCode: campo de estado para "cfop code".
- calculationBasis: campo de estado para "calculation basis".
- taxRate: campo de estado para "tax rate".
- pisRate: campo de estado para "pis rate".
- cofinsRate: campo de estado para "cofins rate".
- hasDifal: campo de estado para "has difal".
- difalTaxValueOrigin: campo de estado para "difal tax value origin".
- difalTaxValueDestination: campo de estado para "difal tax value destination".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.graphql.fretes;

import com.fasterxml.jackson.annotation.JsonProperty;

public class FiscalDetailDTO {
    @JsonProperty("cstType")
    private String cstType;

    @JsonProperty("taxValue")
    private Double taxValue;

    @JsonProperty("pisValue")
    private Double pisValue;

    @JsonProperty("cofinsValue")
    private Double cofinsValue;

    @JsonProperty("cfopCode")
    private String cfopCode;

    @JsonProperty("calculationBasis")
    private Double calculationBasis;

    @JsonProperty("taxRate")
    private Double taxRate;

    @JsonProperty("pisRate")
    private Double pisRate;

    @JsonProperty("cofinsRate")
    private Double cofinsRate;

    @JsonProperty("hasDifal")
    private Boolean hasDifal;

    @JsonProperty("difalTaxValueOrigin")
    private Double difalTaxValueOrigin;

    @JsonProperty("difalTaxValueDestination")
    private Double difalTaxValueDestination;

    public String getCstType() { return cstType; }
    public void setCstType(final String cstType) { this.cstType = cstType; }
    public Double getTaxValue() { return taxValue; }
    public void setTaxValue(final Double taxValue) { this.taxValue = taxValue; }
    public Double getPisValue() { return pisValue; }
    public void setPisValue(final Double pisValue) { this.pisValue = pisValue; }
    public Double getCofinsValue() { return cofinsValue; }
    public void setCofinsValue(final Double cofinsValue) { this.cofinsValue = cofinsValue; }
    public String getCfopCode() { return cfopCode; }
    public void setCfopCode(final String cfopCode) { this.cfopCode = cfopCode; }
    public Double getCalculationBasis() { return calculationBasis; }
    public void setCalculationBasis(final Double calculationBasis) { this.calculationBasis = calculationBasis; }
    public Double getTaxRate() { return taxRate; }
    public void setTaxRate(final Double taxRate) { this.taxRate = taxRate; }
    public Double getPisRate() { return pisRate; }
    public void setPisRate(final Double pisRate) { this.pisRate = pisRate; }
    public Double getCofinsRate() { return cofinsRate; }
    public void setCofinsRate(final Double cofinsRate) { this.cofinsRate = cofinsRate; }
    public Boolean getHasDifal() { return hasDifal; }
    public void setHasDifal(final Boolean hasDifal) { this.hasDifal = hasDifal; }
    public Double getDifalTaxValueOrigin() { return difalTaxValueOrigin; }
    public void setDifalTaxValueOrigin(final Double difalTaxValueOrigin) { this.difalTaxValueOrigin = difalTaxValueOrigin; }
    public Double getDifalTaxValueDestination() { return difalTaxValueDestination; }
    public void setDifalTaxValueDestination(final Double difalTaxValueDestination) { this.difalTaxValueDestination = difalTaxValueDestination; }
}
