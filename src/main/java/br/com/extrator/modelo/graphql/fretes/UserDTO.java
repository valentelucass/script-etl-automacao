/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/fretes/UserDTO.java
Classe  : UserDTO (class)
Pacote  : br.com.extrator.modelo.graphql.fretes
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
- getName(): expone valor atual do estado interno.
- setName(...1 args): ajusta valor em estado interno.
Atributos-chave:
- name: campo de estado para "name".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.graphql.fretes;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO aninhado para representar o Usuário (User) de um Frete.
 * Conforme documentação em docs/descobertas-endpoints/fretes.md linha 173.
 */
public class UserDTO {
    @JsonProperty("name")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

