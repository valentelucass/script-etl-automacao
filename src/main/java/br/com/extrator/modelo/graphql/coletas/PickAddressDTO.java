/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/coletas/PickAddressDTO.java
Classe  : PickAddressDTO (class)
Pacote  : br.com.extrator.modelo.graphql.coletas
Modulo  : DTO/Mapper GraphQL
Papel   : Implementa responsabilidade de pick address dto.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela payloads da API GraphQL.
2) Mapeia estrutura remota para modelo interno.
3) Apoia persistencia e validacao do extrator.

Estrutura interna:
Metodos principais:
- getLine1(): expone valor atual do estado interno.
- setLine1(...1 args): ajusta valor em estado interno.
- getLine2(): expone valor atual do estado interno.
- setLine2(...1 args): ajusta valor em estado interno.
- getNumber(): expone valor atual do estado interno.
- setNumber(...1 args): ajusta valor em estado interno.
- getNeighborhood(): expone valor atual do estado interno.
- setNeighborhood(...1 args): ajusta valor em estado interno.
- getPostalCode(): expone valor atual do estado interno.
- setPostalCode(...1 args): ajusta valor em estado interno.
- getCity(): expone valor atual do estado interno.
- setCity(...1 args): ajusta valor em estado interno.
Atributos-chave:
- line1: campo de estado para "line1".
- line2: campo de estado para "line2".
- number: campo de estado para "number".
- neighborhood: campo de estado para "neighborhood".
- postalCode: campo de estado para "postal code".
- city: campo de estado para "city".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.graphql.coletas;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO aninhado para representar o Endereço de Coleta (PickAddress) de uma Coleta.
 * Conforme documentação em docs/descobertas-endpoints/coletas.md linha 82-90.
 */
public class PickAddressDTO {
    @JsonProperty("line1")
    private String line1;

    @JsonProperty("line2")
    private String line2;

    @JsonProperty("number")
    private String number;

    @JsonProperty("neighborhood")
    private String neighborhood;

    @JsonProperty("postalCode")
    private String postalCode;

    @JsonProperty("city")
    private CityDTO city;

    public String getLine1() {
        return line1;
    }

    public void setLine1(String line1) {
        this.line1 = line1;
    }

    public String getLine2() {
        return line2;
    }

    public void setLine2(String line2) {
        this.line2 = line2;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public void setNeighborhood(String neighborhood) {
        this.neighborhood = neighborhood;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public CityDTO getCity() {
        return city;
    }

    public void setCity(CityDTO city) {
        this.city = city;
    }
}
