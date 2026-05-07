package br.com.extrator.integracao.raster.services;

import java.time.LocalDate;

import br.com.extrator.aplicacao.contexto.AplicacaoContexto;
import br.com.extrator.aplicacao.portas.ExecutionAuditPort;
import br.com.extrator.integracao.comum.ExtractionLogger;
import br.com.extrator.integracao.comum.ExtractionResult;
import br.com.extrator.integracao.raster.RasterViagemExtractor;
import br.com.extrator.persistencia.entidade.LogExtracaoEntity;
import br.com.extrator.persistencia.repositorio.LogExtracaoRepository;
import br.com.extrator.plataforma.auditoria.aplicacao.ExecutionAuditRecorder;
import br.com.extrator.suporte.configuracao.ConfigRaster;
import br.com.extrator.suporte.tempo.RelogioSistema;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public class RasterExtractionService {
    private final RasterViagemExtractor extractor;
    private final LogExtracaoRepository logRepository;
    private final ExecutionAuditPort executionAuditPort;
    private final ExtractionLogger logger;

    public RasterExtractionService() {
        this(
            new RasterViagemExtractor(),
            new LogExtracaoRepository(),
            AplicacaoContexto.executionAuditPort(),
            new ExtractionLogger(RasterExtractionService.class)
        );
    }

    RasterExtractionService(final RasterViagemExtractor extractor,
                            final LogExtracaoRepository logRepository,
                            final ExecutionAuditPort executionAuditPort,
                            final ExtractionLogger logger) {
        this.extractor = extractor;
        this.logRepository = logRepository;
        this.executionAuditPort = executionAuditPort;
        this.logger = logger;
    }

    public ExtractionResult executar(final LocalDate dataInicio, final LocalDate dataFim) {
        final LocalDate fim = dataFim != null ? dataFim : RelogioSistema.hoje();
        final LocalDate inicio = dataInicio != null ? dataInicio : fim.minusDays(ConfigRaster.obterLookbackDays());
        final ExtractionResult result = logger.executeWithLogging(extractor, inicio, fim, extractor.getEmoji());
        registrarLogExtracao(result);
        return result;
    }

    private void registrarLogExtracao(final ExtractionResult result) {
        if (result == null) {
            return;
        }
        logRepository.gravarLogExtracao(result.toLogEntity());
        logRepository.gravarLogExtracao(criarLogParadas(result));
        ExecutionAuditRecorder.registrar(executionAuditPort, result);
    }

    private LogExtracaoEntity criarLogParadas(final ExtractionResult result) {
        return new LogExtracaoEntity(
            ConstantesEntidades.RASTER_VIAGEM_PARADAS,
            result.getInicio(),
            result.getFim(),
            result.getStatus(),
            extractor.getUltimaQuantidadeParadas(),
            result.getPaginasProcessadas(),
            montarMensagemParadas(result)
        );
    }

    private String montarMensagemParadas(final ExtractionResult result) {
        return "Paradas derivadas da extracao Raster "
            + ConstantesEntidades.RASTER_VIAGENS
            + " | API/paradas mapeadas: " + extractor.getUltimaQuantidadeParadas()
            + " | DB/paradas operacoes: " + extractor.getUltimasParadasSalvas()
            + " | Persistidos: " + extractor.getUltimasParadasPersistidas()
            + " | No-op: " + extractor.getUltimasParadasNoOpIdempotente()
            + " | " + result.getMensagem();
    }
}
