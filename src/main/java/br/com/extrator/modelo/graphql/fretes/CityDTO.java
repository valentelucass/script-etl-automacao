/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/fretes/CityDTO.java
Classe  : CityDTO (class)
Pacote  : br.com.extrator.modelo.graphql.fretes
Modulo  : DTO/Mapper GraphQL
Papel   : Implementa responsabilidade de city dto.

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
- getState(): expone valor atual do estado interno.
- setState(...1 args): ajusta valor em estado interno.
Atributos-chave:
- name: campo de estado para "name".
- state: campo de estado para "state".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.graphql.fretes;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO aninhado para representar a Cidade (City) de um Endereço.
 * Conforme documentação em docs/descobertas-endpoints/fretes.md linha 90-96.
 */
public class CityDTO {
    @JsonProperty("name")
    private String name;

    @JsonProperty("state")
    private StateDTO state;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public StateDTO getState() {
        return state;
    }

    public void setState(StateDTO state) {
        this.state = state;
    }
}

