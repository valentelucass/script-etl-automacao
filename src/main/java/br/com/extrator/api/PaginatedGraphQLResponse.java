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
