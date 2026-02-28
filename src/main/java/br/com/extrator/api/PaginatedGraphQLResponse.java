/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/api/PaginatedGraphQLResponse.java
Classe  : PaginatedGraphQLResponse (class)
Pacote  : br.com.extrator.api
Modulo  : Cliente de integracao API
Papel   : Implementa responsabilidade de paginated graph qlresponse.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Monta requisicoes para endpoints externos.
2) Trata autenticacao, timeout e parse de resposta.
3) Entrega dados normalizados para os extractors.

Estrutura interna:
Metodos principais:
- PaginatedGraphQLResponse(...8 args): realiza operacao relacionada a "paginated graph qlresponse".
- PaginatedGraphQLResponse(...10 args): realiza operacao relacionada a "paginated graph qlresponse".
- getEntidades(): expone valor atual do estado interno.
- getHasNextPage(): expone valor atual do estado interno.
- getEndCursor(): expone valor atual do estado interno.
- getStatusCode(): expone valor atual do estado interno.
- getDuracaoMs(): expone valor atual do estado interno.
- getReqHash(): expone valor atual do estado interno.
- getRespHash(): expone valor atual do estado interno.
- getTotalItens(): expone valor atual do estado interno.
- isErroApi(): retorna estado booleano de controle.
- getErroDetalhe(): expone valor atual do estado interno.
Atributos-chave:
- entidades: campo de estado para "entidades".
- hasNextPage: campo de estado para "has next page".
- endCursor: campo de estado para "end cursor".
- statusCode: campo de estado para "status code".
- duracaoMs: campo de estado para "duracao ms".
- reqHash: campo de estado para "req hash".
- respHash: campo de estado para "resp hash".
- totalItens: campo de estado para "total itens".
- erroApi: campo de estado para "erro api".
- erroDetalhe: campo de estado para "erro detalhe".
[DOC-FILE-END]============================================================== */

package br.com.extrator.api;

import java.util.List;

/**
 * Classe auxiliar para encapsular a resposta paginada de uma query GraphQL.
 * Contém os dados da página atual e as informações de paginação necessárias
 * para continuar buscando as próximas páginas.
 */
public class PaginatedGraphQLResponse<T> {
    
    private final List<T> entidades;
    private final boolean hasNextPage;
    private final String endCursor;
    private final int statusCode;
    private final int duracaoMs;
    private final String reqHash;
    private final String respHash;
    private final int totalItens;
    private final boolean erroApi;
    private final String erroDetalhe;
    
    /**
     * Construtor da resposta paginada
     * 
     * @param entidades Lista de entidades da página atual
     * @param hasNextPage Indica se há próxima página disponível
     * @param endCursor Cursor para buscar a próxima página
     */
    public PaginatedGraphQLResponse(List<T> entidades, boolean hasNextPage, String endCursor, int statusCode, int duracaoMs, String reqHash, String respHash, int totalItens) {
        this(entidades, hasNextPage, endCursor, statusCode, duracaoMs, reqHash, respHash, totalItens, false, null);
    }

    public PaginatedGraphQLResponse(List<T> entidades,
                                    boolean hasNextPage,
                                    String endCursor,
                                    int statusCode,
                                    int duracaoMs,
                                    String reqHash,
                                    String respHash,
                                    int totalItens,
                                    boolean erroApi,
                                    String erroDetalhe) {
        this.entidades = entidades;
        this.hasNextPage = hasNextPage;
        this.endCursor = endCursor;
        this.statusCode = statusCode;
        this.duracaoMs = duracaoMs;
        this.reqHash = reqHash;
        this.respHash = respHash;
        this.totalItens = totalItens;
        this.erroApi = erroApi;
        this.erroDetalhe = erroDetalhe;
    }
    
    /**
     * @return Lista de entidades da página atual
     */
    public List<T> getEntidades() {
        return entidades;
    }
    
    /**
     * @return true se há próxima página disponível, false caso contrário
     */
    public boolean getHasNextPage() {
        return hasNextPage;
    }
    
    /**
     * @return Cursor para buscar a próxima página
     */
    public String getEndCursor() {
        return endCursor;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public int getDuracaoMs() {
        return duracaoMs;
    }
    
    public String getReqHash() {
        return reqHash;
    }
    
    public String getRespHash() {
        return respHash;
    }
    
    public int getTotalItens() {
        return totalItens;
    }

    public boolean isErroApi() {
        return erroApi;
    }

    public String getErroDetalhe() {
        return erroDetalhe;
    }
}
