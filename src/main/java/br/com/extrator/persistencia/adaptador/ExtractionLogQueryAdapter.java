package br.com.extrator.persistencia.adaptador;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/persistencia/adaptador/ExtractionLogQueryAdapter.java
Classe  : ExtractionLogQueryPort (class)
Pacote  : br.com.extrator.persistencia.adaptador
Modulo  : Persistencia - Adaptador
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import br.com.extrator.aplicacao.extracao.LogExtracaoInfo;
import br.com.extrator.aplicacao.portas.ExtractionLogQueryPort;
import br.com.extrator.persistencia.entidade.LogExtracaoEntity;
import br.com.extrator.persistencia.repositorio.LogExtracaoRepository;

/**
 * Adapter que implementa ExtractionLogQueryPort usando LogExtracaoRepository.
 */
public class ExtractionLogQueryAdapter implements ExtractionLogQueryPort {

    private final LogExtracaoRepository repository;

    public ExtractionLogQueryAdapter() {
        this.repository = new LogExtracaoRepository();
    }

    @Override
    public Optional<LogExtracaoInfo> buscarUltimoLogPorEntidadeNoIntervaloExecucao(
        final String entidade,
        final LocalDateTime inicio,
        final LocalDateTime fim
    ) {
        return repository.buscarUltimoLogPorEntidadeNoIntervaloExecucao(entidade, inicio, fim)
            .map(this::toInfo);
    }

    @Override
    public Optional<LogExtracaoInfo> buscarUltimaExtracaoPorPeriodo(
        final String entidade,
        final LocalDate dataInicio,
        final LocalDate dataFim
    ) {
        return repository.buscarUltimaExtracaoPorPeriodo(entidade, dataInicio, dataFim)
            .map(this::toInfo);
    }

    private LogExtracaoInfo toInfo(final LogExtracaoEntity entity) {
        return new LogExtracaoInfo(
            mapStatus(entity.getStatusFinal()),
            entity.getTimestampFim(),
            entity.getRegistrosExtraidos()
        );
    }

    private LogExtracaoInfo.StatusExtracao mapStatus(final LogExtracaoEntity.StatusExtracao status) {
        if (status == null) {
            return LogExtracaoInfo.StatusExtracao.ERRO_API;
        }
        return switch (status) {
            case COMPLETO -> LogExtracaoInfo.StatusExtracao.COMPLETO;
            case INCOMPLETO -> LogExtracaoInfo.StatusExtracao.INCOMPLETO;
            case INCOMPLETO_LIMITE -> LogExtracaoInfo.StatusExtracao.INCOMPLETO_LIMITE;
            case INCOMPLETO_DADOS -> LogExtracaoInfo.StatusExtracao.INCOMPLETO_DADOS;
            case INCOMPLETO_DB -> LogExtracaoInfo.StatusExtracao.INCOMPLETO_DB;
            case ERRO_API -> LogExtracaoInfo.StatusExtracao.ERRO_API;
        };
    }
}
