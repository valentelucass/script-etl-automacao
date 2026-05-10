package br.com.extrator.aplicacao.pipeline;

import java.time.Duration;
import java.time.LocalDate;

import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.aplicacao.portas.RasterGateway;
import br.com.extrator.suporte.configuracao.ConfigRaster;

public final class RasterPipelineStep implements PipelineStep {
    private final RasterGateway gateway;
    private final String entidade;

    public RasterPipelineStep(final RasterGateway gateway, final String entidade) {
        this.gateway = gateway;
        this.entidade = entidade;
    }

    @Override
    public StepExecutionResult executar(final LocalDate dataInicio, final LocalDate dataFim) throws Exception {
        return gateway.executar(dataInicio, dataFim, entidade);
    }

    @Override
    public String obterNomeEtapa() {
        return "raster:" + entidade;
    }

    @Override
    public String obterNomeEntidade() {
        return entidade;
    }

    @Override
    public Duration obterTimeoutExecucao() {
        return ConfigRaster.obterTimeoutStep();
    }
}
