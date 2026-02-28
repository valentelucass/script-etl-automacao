/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/fretes/CorporationDTO.java
Classe  : CorporationDTO (class)
Pacote  : br.com.extrator.modelo.graphql.fretes
Modulo  : DTO/Mapper GraphQL
Papel   : Implementa responsabilidade de corporation dto.

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
- getId(): expone valor atual do estado interno.
- setId(...1 args): ajusta valor em estado interno.
- getPerson(): expone valor atual do estado interno.
- setPerson(...1 args): ajusta valor em estado interno.
- getNickname(): expone valor atual do estado interno.
- setNickname(...1 args): ajusta valor em estado interno.
- getCnpj(): expone valor atual do estado interno.
- setCnpj(...1 args): ajusta valor em estado interno.
Atributos-chave:
- id: campo de estado para "id".
- name: campo de estado para "name".
- nickname: campo de estado para "nickname".
- cnpj: campo de estado para "cnpj".
- person: campo de estado para "person".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.graphql.fretes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO aninhado para representar a Filial (Corporation) de um Frete.
 * Conforme documentação em docs/descobertas-endpoints/fretes.md linha 152.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CorporationDTO {
    @JsonProperty("id")
    private Long id;
    @JsonProperty("name")
    private String name;

    @JsonProperty("nickname")
    private String nickname;

    @JsonProperty("cnpj")
    private String cnpj;

    @JsonProperty("person")
    private PersonDTO person;

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public void setId(final Long id) { this.id = id; }

    public PersonDTO getPerson() { return person; }
    public void setPerson(final PersonDTO person) { this.person = person; }

    public String getNickname() { return nickname; }
    public void setNickname(final String nickname) { this.nickname = nickname; }
    public String getCnpj() { return cnpj; }
    public void setCnpj(final String cnpj) { this.cnpj = cnpj; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PersonDTO {
        @JsonProperty("nickname")
        private String nickname;

        @JsonProperty("cnpj")
        private String cnpj;

        public String getNickname() { return nickname; }
        public void setNickname(final String nickname) { this.nickname = nickname; }
        public String getCnpj() { return cnpj; }
        public void setCnpj(final String cnpj) { this.cnpj = cnpj; }
    }
}
