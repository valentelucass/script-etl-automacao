/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/fretes/nfse/NfseNodeDTO.java
Classe  : NfseNodeDTO (class)
Pacote  : br.com.extrator.modelo.graphql.fretes.nfse
Modulo  : DTO/Mapper GraphQL
Papel   : Implementa responsabilidade de nfse node dto.

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
- getNumber(): expone valor atual do estado interno.
- setNumber(...1 args): ajusta valor em estado interno.
- getStatus(): expone valor atual do estado interno.
- setStatus(...1 args): ajusta valor em estado interno.
- getRpsSeries(): expone valor atual do estado interno.
- setRpsSeries(...1 args): ajusta valor em estado interno.
- getIssuedAt(): expone valor atual do estado interno.
- setIssuedAt(...1 args): ajusta valor em estado interno.
- getCancelationReason(): expone valor atual do estado interno.
- setCancelationReason(...1 args): ajusta valor em estado interno.
- getPdfServiceUrl(): expone valor atual do estado interno.
- setPdfServiceUrl(...1 args): ajusta valor em estado interno.
Atributos-chave:
- id: campo de estado para "id".
- number: campo de estado para "number".
- status: campo de estado para "status".
- rpsSeries: campo de estado para "rps series".
- issuedAt: campo de estado para "issued at".
- cancelationReason: campo de estado para "cancelation reason".
- pdfServiceUrl: campo de estado para "pdf service url".
- xmlDocument: campo de estado para "xml document".
- corporationId: campo de estado para "corporation id".
- nfseService: servico de negocio/coordenacao.
- freightId: campo de estado para "freight id".
- freight: campo de estado para "freight".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.graphql.fretes.nfse;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NfseNodeDTO {
    @JsonProperty("id")
    private String id;

    @JsonProperty("number")
    private Integer number;

    @JsonProperty("status")
    private String status;

    @JsonProperty("rpsSeries")
    private String rpsSeries;

    @JsonProperty("issuedAt")
    private String issuedAt;

    @JsonProperty("cancelationReason")
    private String cancelationReason;

    @JsonProperty("pdfServiceUrl")
    private String pdfServiceUrl;

    @JsonProperty("xmlDocument")
    private String xmlDocument;

    @JsonProperty("corporationId")
    private Long corporationId;

    @JsonProperty("nfseService")
    private NfseServiceDTO nfseService;

    @JsonProperty("freightId")
    private Long freightId;

    public static class FreightRef {
        @JsonProperty("id")
        private Long id;
        public Long getId() { return id; }
        public void setId(final Long id) { this.id = id; }
    }

    @JsonProperty("freight")
    private FreightRef freight;

    public String getId() { return id; }
    public void setId(final String id) { this.id = id; }
    public Integer getNumber() { return number; }
    public void setNumber(final Integer number) { this.number = number; }
    public String getStatus() { return status; }
    public void setStatus(final String status) { this.status = status; }
    public String getRpsSeries() { return rpsSeries; }
    public void setRpsSeries(final String rpsSeries) { this.rpsSeries = rpsSeries; }
    public String getIssuedAt() { return issuedAt; }
    public void setIssuedAt(final String issuedAt) { this.issuedAt = issuedAt; }
    public String getCancelationReason() { return cancelationReason; }
    public void setCancelationReason(final String cancelationReason) { this.cancelationReason = cancelationReason; }
    public String getPdfServiceUrl() { return pdfServiceUrl; }
    public void setPdfServiceUrl(final String pdfServiceUrl) { this.pdfServiceUrl = pdfServiceUrl; }
    public String getXmlDocument() { return xmlDocument; }
    public void setXmlDocument(final String xmlDocument) { this.xmlDocument = xmlDocument; }
    public Long getCorporationId() { return corporationId; }
    public void setCorporationId(final Long corporationId) { this.corporationId = corporationId; }
    public NfseServiceDTO getNfseService() { return nfseService; }
    public void setNfseService(final NfseServiceDTO nfseService) { this.nfseService = nfseService; }
    public Long getFreightId() { return freightId != null ? freightId : (freight != null ? freight.getId() : null); }
    public void setFreightId(final Long freightId) { this.freightId = freightId; }
    public FreightRef getFreight() { return freight; }
    public void setFreight(final FreightRef freight) { this.freight = freight; }

    public String getServiceDescription() {
        return nfseService != null ? nfseService.getDescription() : null;
    }

}
