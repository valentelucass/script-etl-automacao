/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/util/ThreadUtil.java
Classe  : ThreadUtil (class)
Pacote  : br.com.extrator.util
Modulo  : Utilitario compartilhado
Papel   : Implementa responsabilidade de thread util.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Centraliza funcoes auxiliares reutilizaveis.
2) Evita repeticao de logica transversal.
3) Apoia configuracao, formatacao e infraestrutura.

Estrutura interna:
Metodos principais:
- ThreadUtil(): realiza operacao relacionada a "thread util".
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.util;

/**
 * Utilitário centralizado para operações de thread (ex.: pausas).
 * Evita uso direto de {@link Thread#sleep(long)} espalhado pelo código.
 *
 * @author Sistema de Extração ESL Cloud
 * @version 1.0
 */
public final class ThreadUtil {

    /**
     * Aguarda o tempo especificado (delega para {@link Thread#sleep(long)}).
     *
     * @param millis tempo em milissegundos
     * @throws InterruptedException se a thread for interrompida durante a espera
     */
    public static void aguardar(final long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    private ThreadUtil() {
        // Impede instanciação
    }
}
