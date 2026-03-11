/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/contexto/NoOpExtractionLogQueryPort.java
Classe  : NoOpExtractionLogQueryPort (class)
Pacote  : br.com.extrator.aplicacao.contexto
Modulo  : Contexto DI - Aplicacao

Papel   : Implementacao no-op de ExtractionLogQueryPort para fallback quando contexto nao inicializado.

Conecta com:
- ExtractionLogQueryPort (interface que implementa)

Fluxo geral:
1) AplicacaoContexto retorna instancia deste quando extractionLogQueryPort e null.
2) Metodos retornam Optional.empty() para todas as consultas.
3) Impede NullPointerException e falhas cascata em cenarios de inicializacao incompleta.

Estrutura interna:
Metodos principais:
- buscarUltimoLogPorEntidadeNoIntervaloExecucao(): retorna Optional.empty().
- buscarUltimaExtracaoPorPeriodo(): retorna Optional.empty().
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.contexto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import br.com.extrator.aplicacao.extracao.LogExtracaoInfo;
import br.com.extrator.aplicacao.portas.ExtractionLogQueryPort;

/**
 * Implementacao no-op usada quando o contexto nao foi inicializado.
 * Retorna Optional.empty() para todas as consultas.
 */
final class NoOpExtractionLogQueryPort implements ExtractionLogQueryPort {

    @Override
    public Optional<LogExtracaoInfo> buscarUltimoLogPorEntidadeNoIntervaloExecucao(
        final String entidade,
        final LocalDateTime inicio,
        final LocalDateTime fim
    ) {
        return Optional.empty();
    }

    @Override
    public Optional<LogExtracaoInfo> buscarUltimaExtracaoPorPeriodo(
        final String entidade,
        final LocalDate dataInicio,
        final LocalDate dataFim
    ) {
        return Optional.empty();
    }
}
