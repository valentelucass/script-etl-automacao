/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/dominio/graphql/fretes/nfse/FreteNfsePayload.java
Classe  : FreteNfsePayload (interface)
Pacote  : br.com.extrator.dominio.graphql.fretes.nfse
Modulo  : Dominio - DTO NFS-e
Papel   : Contrato para payload de NFS-e em resposta GraphQL de frete.
Conecta com: Sem dependencia interna
Fluxo geral:
1) Interface define métodos de accessors para campos de NFS-e (id, status, RPS, XML, PDF, etc)
Estrutura interna:
Metodos: getId(), getNumber(), getStatus(), getRpsSeries(), getIssuedAt(), getCancelationReason(), getPdfServiceUrl(), getXmlDocument(), getCorporationId(), getFreightId(), getServiceDescription()
[DOC-FILE-END]============================================================== */
package br.com.extrator.dominio.graphql.fretes.nfse;

public interface FreteNfsePayload {
    String getId();
    Integer getNumber();
    String getStatus();
    String getRpsSeries();
    String getIssuedAt();
    String getCancelationReason();
    String getPdfServiceUrl();
    String getXmlDocument();
    Long getCorporationId();
    Long getFreightId();
    String getServiceDescription();
}
