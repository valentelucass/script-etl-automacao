/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/bancos/BankAccountNodeDTO.java
Classe  : BankAccountNodeDTO (class)
Pacote  : br.com.extrator.modelo.graphql.bancos
Modulo  : DTO/Mapper GraphQL
Papel   : Implementa responsabilidade de bank account node dto.

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
- getBankName(): expone valor atual do estado interno.
- setBankName(...1 args): ajusta valor em estado interno.
- getPortfolioVariation(): expone valor atual do estado interno.
- setPortfolioVariation(...1 args): ajusta valor em estado interno.
- getCustomInstruction(): expone valor atual do estado interno.
- setCustomInstruction(...1 args): ajusta valor em estado interno.
Atributos-chave:
- id: campo de estado para "id".
- bankName: campo de estado para "bank name".
- portfolioVariation: campo de estado para "portfolio variation".
- customInstruction: campo de estado para "custom instruction".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.graphql.bancos;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO para representar dados de conta bancária via GraphQL.
 * Usado para resolver detalhes do banco via ticketAccountId.
 */
public class BankAccountNodeDTO {
    
    @JsonProperty("id")
    private Integer id;
    
    @JsonProperty("bankName")
    private String bankName;
    
    @JsonProperty("portfolioVariation")
    private String portfolioVariation;
    
    @JsonProperty("customInstruction")
    private String customInstruction;
    
    // Getters e Setters
    public Integer getId() { return id; }
    public void setId(final Integer id) { this.id = id; }
    
    public String getBankName() { return bankName; }
    public void setBankName(final String bankName) { this.bankName = bankName; }
    
    public String getPortfolioVariation() { return portfolioVariation; }
    public void setPortfolioVariation(final String portfolioVariation) { this.portfolioVariation = portfolioVariation; }
    
    public String getCustomInstruction() { return customInstruction; }
    public void setCustomInstruction(final String customInstruction) { this.customInstruction = customInstruction; }
}
