/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/fretes/MainAddressDTO.java
Classe  : MainAddressDTO (class)
Pacote  : br.com.extrator.modelo.graphql.fretes
Modulo  : DTO/Mapper GraphQL
Papel   : Implementa responsabilidade de main address dto.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela payloads da API GraphQL.
2) Mapeia estrutura remota para modelo interno.
3) Apoia persistencia e validacao do extrator.

Estrutura interna:
Metodos principais:
- getCity(): expone valor atual do estado interno.
- setCity(...1 args): ajusta valor em estado interno.
Atributos-chave:
- city: campo de estado para "city".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.graphql.fretes;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO aninhado para representar o Endereço Principal (MainAddress) de um Remetente/Destinatário.
 * Conforme documentação em docs/descobertas-endpoints/fretes.md linha 88-97.
 */
public class MainAddressDTO {
    @JsonProperty("city")
    private CityDTO city;

    public CityDTO getCity() {
        return city;
    }

    public void setCity(CityDTO city) {
        this.city = city;
    }
}

