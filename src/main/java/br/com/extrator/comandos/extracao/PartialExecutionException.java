/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/extracao/PartialExecutionException.java
Classe  : PartialExecutionException (class)
Pacote  : br.com.extrator.comandos.extracao
Modulo  : Comando CLI (extracao)
Papel   : Implementa responsabilidade de partial execution exception.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Interpreta parametros e escopo de extracao.
2) Dispara runners/extratores conforme alvo.
3) Consolida status final e tratamento de falhas.

Estrutura interna:
Metodos principais:
- PartialExecutionException(...1 args): realiza operacao relacionada a "partial execution exception".
- PartialExecutionException(...2 args): realiza operacao relacionada a "partial execution exception".
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.extracao;

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

