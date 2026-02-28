/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/usuarios/IndividualNodeDTO.java
Classe  : IndividualNodeDTO (class)
Pacote  : br.com.extrator.modelo.graphql.usuarios
Modulo  : DTO/Mapper GraphQL
Papel   : Implementa responsabilidade de individual node dto.

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
- getName(): expone valor atual do estado interno.
- setName(...1 args): ajusta valor em estado interno.
Atributos-chave:
- id: campo de estado para "id".
- name: campo de estado para "name".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.graphql.usuarios;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO (Data Transfer Object) para representar um "node" de Individual (Usuário do Sistema),
 * conforme retornado pela API GraphQL.
 */
public class IndividualNodeDTO {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("name")
    private String name;

    public Long getId() {
        return id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }
}
