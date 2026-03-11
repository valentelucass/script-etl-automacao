/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/extracao/ReconciliacaoUseCase.java
Classe  : ReconciliacaoUseCase (class)
Pacote  : br.com.extrator.aplicacao.extracao
Modulo  : Use Case - Extracao

Papel   : Executa reconciliacao (re-extracao) para um dia especifico com mesmo fluxo de intervalo.

Conecta com:
- ExtracaoPorIntervaloUseCase (delegacao via composicao)

Fluxo geral:
1) executar(data, incluirFaturasGraphQL) monta ExtracaoPorIntervaloRequest (data = data, modoLoopDaemon = true).
2) Delega a ExtracaoPorIntervaloUseCase.executar() com intervalo de 1 dia.
3) Reusa pipeline, validacoes e integridade do fluxo de intervalo.

Estrutura interna:
Atributos-chave:
- extracaoPorIntervaloUseCase: delegacao para fluxo de intervalo (composicao).
Metodos principais:
- executar(LocalDate, boolean): ponto de entrada, monta request e delega.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.extracao;

import java.time.LocalDate;
import java.util.Objects;

public class ReconciliacaoUseCase {
    private final ExtracaoPorIntervaloUseCase extracaoPorIntervaloUseCase;

    public ReconciliacaoUseCase() {
        this(new ExtracaoPorIntervaloUseCase());
    }

    ReconciliacaoUseCase(final ExtracaoPorIntervaloUseCase extracaoPorIntervaloUseCase) {
        this.extracaoPorIntervaloUseCase = Objects.requireNonNull(
            extracaoPorIntervaloUseCase,
            "extracaoPorIntervaloUseCase nao pode ser null"
        );
    }

    public void executar(final LocalDate data, final boolean incluirFaturasGraphQL) throws Exception {
        final ExtracaoPorIntervaloRequest request = new ExtracaoPorIntervaloRequest(
            data,
            data,
            null,
            null,
            incluirFaturasGraphQL,
            true
        );
        extracaoPorIntervaloUseCase.executar(request);
    }
}
