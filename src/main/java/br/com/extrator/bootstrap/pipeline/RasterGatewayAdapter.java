package br.com.extrator.bootstrap.pipeline;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.aplicacao.pipeline.runtime.StepStatus;
import br.com.extrator.aplicacao.portas.RasterGateway;
import br.com.extrator.integracao.comum.ExtractionResult;
import br.com.extrator.integracao.raster.services.RasterExtractionService;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public final class RasterGatewayAdapter implements RasterGateway {
    private final RasterExtractionService service;

    public RasterGatewayAdapter() {
        this(new RasterExtractionService());
    }

    RasterGatewayAdapter(final RasterExtractionService service) {
        this.service = service;
    }

    @Override
    public StepExecutionResult executar(final LocalDate dataInicio,
                                        final LocalDate dataFim,
                                        final String entidade) {
        final LocalDateTime inicio = LocalDateTime.now();
        final String entidadeExecucao = normalizarEntidade(entidade);
        final ExtractionResult result = service.executar(dataInicio, dataFim);
        final boolean completo = result != null && ConstantesEntidades.STATUS_COMPLETO.equals(result.getStatus());
        final String mensagem = result == null
            ? "Raster sem resultado operacional"
            : "Raster finalizado com status " + result.getStatus() + ": " + result.getMensagem();

        return StepExecutionResult.builder("raster:" + entidadeExecucao, entidadeExecucao)
            .status(completo ? StepStatus.SUCCESS : StepStatus.DEGRADED)
            .startedAt(inicio)
            .finishedAt(LocalDateTime.now())
            .message(mensagem)
            .metadata("source", "raster")
            .metadata("status_extracao", result == null ? null : result.getStatus())
            .metadata("registros_extraidos", result == null ? 0 : result.getRegistrosExtraidos())
            .metadata("registros_salvos", result == null ? 0 : result.getRegistrosSalvos())
            .build();
    }

    private String normalizarEntidade(final String entidade) {
        if (entidade == null || entidade.isBlank()) {
            return ConstantesEntidades.RASTER_VIAGENS;
        }
        final String normalizada = entidade.trim().toLowerCase(Locale.ROOT);
        if (ConstantesEntidades.RASTER.equals(normalizada)
            || ConstantesEntidades.RASTER_VIAGENS.equals(normalizada)
            || "all".equals(normalizada)
            || "todas".equals(normalizada)
            || "viagens_raster".equals(normalizada)) {
            return ConstantesEntidades.RASTER_VIAGENS;
        }
        throw new IllegalArgumentException("Entidade Raster invalida para v1: " + entidade);
    }
}
