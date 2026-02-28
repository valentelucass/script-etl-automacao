/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/fretes/FreightInvoiceDTO.java
Classe  : FreightInvoiceDTO (class)
Pacote  : br.com.extrator.modelo.graphql.fretes
Modulo  : DTO/Mapper GraphQL
Papel   : Implementa responsabilidade de freight invoice dto.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela payloads da API GraphQL.
2) Mapeia estrutura remota para modelo interno.
3) Apoia persistencia e validacao do extrator.

Estrutura interna:
Metodos principais:
- getInvoice(): expone valor atual do estado interno.
- setInvoice(...1 args): ajusta valor em estado interno.
Atributos-chave:
- invoice: campo de estado para "invoice".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.graphql.fretes;

import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonProperty;

public class FreightInvoiceDTO {
    public static class InvoiceDTO {
        @JsonProperty("number")
        private String number;
        @JsonProperty("series")
        private String series;
        @JsonProperty("key")
        private String key;
        @JsonProperty("value")
        private BigDecimal value;
        @JsonProperty("weight")
        private BigDecimal weight;

        public String getNumber() { return number; }
        public void setNumber(final String number) { this.number = number; }
        public String getSeries() { return series; }
        public void setSeries(final String series) { this.series = series; }
        public String getKey() { return key; }
        public void setKey(final String key) { this.key = key; }
        public BigDecimal getValue() { return value; }
        public void setValue(final BigDecimal value) { this.value = value; }
        public BigDecimal getWeight() { return weight; }
        public void setWeight(final BigDecimal weight) { this.weight = weight; }
    }

    @JsonProperty("invoice")
    private InvoiceDTO invoice;

    public InvoiceDTO getInvoice() { return invoice; }
    public void setInvoice(final InvoiceDTO invoice) { this.invoice = invoice; }
}
