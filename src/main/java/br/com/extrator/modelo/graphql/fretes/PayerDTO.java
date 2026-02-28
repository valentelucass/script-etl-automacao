/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/fretes/PayerDTO.java
Classe  : PayerDTO (class)
Pacote  : br.com.extrator.modelo.graphql.fretes
Modulo  : DTO/Mapper GraphQL
Papel   : Implementa responsabilidade de payer dto.

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
- getCpf(): expone valor atual do estado interno.
- setCpf(...1 args): ajusta valor em estado interno.
Atributos-chave:
- id: campo de estado para "id".
- name: campo de estado para "name".
- cnpj: campo de estado para "cnpj".
- cpf: campo de estado para "cpf".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.graphql.fretes;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO aninhado para representar o Pagador (Payer) de um Frete.
 * Conforme documentação em docs/descobertas-endpoints/fretes.md linha 81-84.
 */
public class PayerDTO {
    @JsonProperty("id")
    private Long id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("cnpj")
    private String cnpj;

    @JsonProperty("cpf")
    private String cpf;

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

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }
}
