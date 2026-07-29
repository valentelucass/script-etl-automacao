package br.com.extrator.aplicacao.expurgo;

/** Resultado da confirmação de ausências, separado de exclusões efetivadas. */
public record ColetaMissingRegistration(int markedMissing, int logicallyExcluded) {
}
