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
