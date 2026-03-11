/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/extracao/PartialExecutionException.java
Classe  : PartialExecutionException (class)
Pacote  : br.com.extrator.aplicacao.extracao
Modulo  : Use Case - Extracao

Papel   : Excecao para sinalizar execucao parcial (sucesso parcial, nao falha total).

Conecta com:
- ExtracaoPorIntervaloUseCase (lanca)
- FluxoCompletoUseCase (lanca)
- ReconciliacaoUseCase (lanca potencialmente)

Fluxo geral:
1) Use case identifica execucao com falhas parciais (alguns blocos OK, alguns falhos).
2) Lanca PartialExecutionException com detalhe das falhas.
3) Chamador diferencia de RuntimeException pura (erro total) via catch/instanceof.

Estrutura interna:
Construtores:
- PartialExecutionException(String message): construtor basico.
- PartialExecutionException(String message, Throwable cause): com causa.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.extracao;

/**
 * Excecao para sinalizar execucao parcial: parte do fluxo concluiu,
 * mas houve falhas que impedem classificar como sucesso total.
 */
public class PartialExecutionException extends RuntimeException {

    public PartialExecutionException(final String message) {
        super(message);
    }

    public PartialExecutionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
