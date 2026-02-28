/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/coletas/CustomerDTO.java
Classe  : CustomerDTO (class)
Pacote  : br.com.extrator.modelo.graphql.coletas
Modulo  : DTO/Mapper GraphQL
Papel   : Implementa responsabilidade de customer dto.

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
- getCnpj(): expone valor atual do estado interno.
- setCnpj(...1 args): ajusta valor em estado interno.
Atributos-chave:
- id: campo de estado para "id".
- name: campo de estado para "name".
- cnpj: campo de estado para "cnpj".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.graphql.coletas;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO aninhado para representar o Cliente (Customer) de uma Coleta.
 * Conforme documentação em docs/descobertas-endpoints/coletas.md linha 77-80.
 */
public class CustomerDTO {
    @JsonProperty("id")
    private Long id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("cnpj")
    private String cnpj;

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

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }
}
