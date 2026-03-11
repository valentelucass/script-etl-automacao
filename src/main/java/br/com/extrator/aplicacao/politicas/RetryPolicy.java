/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/politicas/RetryPolicy.java
Classe  : RetryPolicy (interface)
Pacote  : br.com.extrator.aplicacao.politicas
Modulo  : Politicas - Resiliencia

Papel   : Interface funcional que define politica de retry (executar com re-tentativas automaticas).

Conecta com:
- ExponentialBackoffRetryPolicy (implementacao)
- CheckedSupplier (funcao a executar com retry)

Fluxo geral:
1) Cliente passa lambda/supplier (operacao a executar) e nome da operacao.
2) executar() aplica retry logic: tentativa inicial, catch, espera, retry com backoff.
3) Retorna resultado de T ou lança excecao se todos os retries falharem.

Estrutura interna:
Inner interface CheckedSupplier<T>:
- get(): operacao que pode lancar exception.
Metodos principais:
- executar(CheckedSupplier<T>, String operationName): executa supplier com retry.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.politicas;

@FunctionalInterface
public interface RetryPolicy {
    <T> T executar(CheckedSupplier<T> supplier, String operationName) throws Exception;

    @FunctionalInterface
    interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}


