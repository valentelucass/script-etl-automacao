/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/extracao/TesteApiRequest.java
Classe  : TesteApiRequest (record)
Pacote  : br.com.extrator.aplicacao.extracao
Modulo  : Use Case - Extracao

Papel   : Requisicao para teste de API (GraphQL ou DataExport) em ultimas 24h.

Conecta com:
- TesteApiUseCase (consume)

Fluxo geral:
1) CLI ou teste cria TesteApiRequest com tipo de API e opcoes.
2) Use case executa pipeline com steps apropiados.
3) Valida status das entidades no log apos execucao.

Estrutura interna:
Campos:
- tipoApi: String (graphql ou dataexport).
- entidade: String (opcional, null para todas).
- incluirFaturasGraphQL: boolean (flag para incluir faturas GraphQL).
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.extracao;

public record TesteApiRequest(
    String tipoApi,
    String entidade,
    boolean incluirFaturasGraphQL
) {
}
