/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/coletas/UserDTO.java
Classe  : UserDTO (class)
Pacote  : br.com.extrator.modelo.graphql.coletas
Modulo  : DTO/Mapper GraphQL
Papel   : Implementa responsabilidade de user dto.

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

package br.com.extrator.modelo.graphql.coletas;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO aninhado para representar o Usuário/Motorista (User) de uma Coleta.
 * Conforme documentação em docs/descobertas-endpoints/coletas.md linha 92-95.
 */
public class UserDTO {
    @JsonProperty("id")
    private Long id;

    @JsonProperty("name")
    private String name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

