package br.com.extrator.persistencia.repositorio;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/persistencia/repositorio/HistoryPersistenceInterruptedException.java
Classe  : RuntimeException (class)
Pacote  : br.com.extrator.persistencia.repositorio
Modulo  : Persistencia - Repositorio
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


/**
 * Signals interruption during retry wait for execution history persistence.
 */
public class HistoryPersistenceInterruptedException extends RuntimeException {

    public HistoryPersistenceInterruptedException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
