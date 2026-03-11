/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/portas/ExtractionLogQueryPort.java
Classe  : ExtractionLogQueryPort (interface)
Pacote  : br.com.extrator.aplicacao.portas
Modulo  : Porta (Interface)

Papel   : Porta para consulta de logs de extracao (historico de execucoes).

Conecta com:
- ExtractionLogQueryAdapter (implementacao em persistencia/adaptador)
- NoOpExtractionLogQueryPort (fallback no-op)
- ValidadorLimiteExtracao, TesteApiUseCase (consomem)

Fluxo geral:
1) buscarUltimoLogPorEntidadeNoIntervaloExecucao(): consulta executando no intervalo.
2) buscarUltimaExtracaoPorPeriodo(): consulta ultima extracao naquele period.
3) Retorna LogExtracaoInfo (status, timestamp, volume) ou Optional.empty().

Estrutura interna:
Metodos principais:
- buscarUltimoLogPorEntidadeNoIntervaloExecucao(String, LocalDateTime, LocalDateTime): Optional<LogExtracaoInfo>.
- buscarUltimaExtracaoPorPeriodo(String, LocalDate, LocalDate): Optional<LogExtracaoInfo>.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.portas;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import br.com.extrator.aplicacao.extracao.LogExtracaoInfo;

public interface ExtractionLogQueryPort {
    Optional<LogExtracaoInfo> buscarUltimoLogPorEntidadeNoIntervaloExecucao(
        String entidade,
        LocalDateTime inicio,
        LocalDateTime fim
    );

    Optional<LogExtracaoInfo> buscarUltimaExtracaoPorPeriodo(
        String entidade,
        LocalDate dataInicio,
        LocalDate dataFim
    );
}
