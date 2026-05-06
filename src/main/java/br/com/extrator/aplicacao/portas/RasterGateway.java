package br.com.extrator.aplicacao.portas;

import java.time.LocalDate;

import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;

public interface RasterGateway {
    StepExecutionResult executar(LocalDate dataInicio, LocalDate dataFim, String entidade) throws Exception;
}
