/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/fretes/nfse/NfseServiceDTO.java
Classe  : NfseServiceDTO (class)
Pacote  : br.com.extrator.modelo.graphql.fretes.nfse
Modulo  : DTO/Mapper GraphQL
Papel   : Implementa responsabilidade de nfse service dto.

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
- getDescription(): expone valor atual do estado interno.
- setDescription(...1 args): ajusta valor em estado interno.
Atributos-chave:
- id: campo de estado para "id".
- description: campo de estado para "description".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.graphql.fretes.nfse;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NfseServiceDTO {
    @JsonProperty("id")
    private Long id;

    @JsonProperty("description")
    private String description;

    public Long getId() { return id; }
    public void setId(final Long id) { this.id = id; }

    public String getDescription() { return description; }
    public void setDescription(final String description) { this.description = description; }
}
