package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/DataExportCsvCountSupport.java
Classe  :  (class)
Pacote  : br.com.extrator.integracao
Modulo  : Integracao HTTP
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.slf4j.Logger;

import br.com.extrator.integracao.constantes.ConstantesApiDataExport;

final class DataExportCsvCountSupport {
    @FunctionalInterface
    interface CsvExecutor {
        HttpResponse<String> executar(String url, String corpoJson, Duration timeout, String operationId);
    }

    @FunctionalInterface
    interface CircuitBreakerVerifier {
        boolean isAtivo(String chaveTemplate);
    }

    @FunctionalInterface
    interface CircuitBreakerReset {
        void resetar(String chaveTemplate);
    }

    @FunctionalInterface
    interface CircuitBreakerIncrement {
        void incrementar(String chaveTemplate, String tipoAmigavel);
    }

    private final Logger logger;
    private final String urlBase;
    private final Duration timeoutRequisicao;
    private final DataExportRequestBodyFactory requestBodyFactory;
    private final CsvExecutor csvExecutor;
    private final CircuitBreakerVerifier circuitBreakerVerifier;
    private final CircuitBreakerReset circuitBreakerReset;
    private final CircuitBreakerIncrement circuitBreakerIncrement;

    DataExportCsvCountSupport(final Logger logger,
                              final String urlBase,
                              final Duration timeoutRequisicao,
                              final DataExportRequestBodyFactory requestBodyFactory,
                              final CsvExecutor csvExecutor,
                              final CircuitBreakerVerifier circuitBreakerVerifier,
                              final CircuitBreakerReset circuitBreakerReset,
                              final CircuitBreakerIncrement circuitBreakerIncrement) {
        this.logger = logger;
        this.urlBase = urlBase;
        this.timeoutRequisicao = timeoutRequisicao;
        this.requestBodyFactory = requestBodyFactory;
        this.csvExecutor = csvExecutor;
        this.circuitBreakerVerifier = circuitBreakerVerifier;
        this.circuitBreakerReset = circuitBreakerReset;
        this.circuitBreakerIncrement = circuitBreakerIncrement;
    }

    int obterContagemGenericaCsv(final int templateId,
                                 final String nomeTabela,
                                 final String campoData,
                                 final LocalDate dataReferencia,
                                 final String tipoAmigavel) {
        final String chaveTemplate = "Template-" + templateId;
        if (circuitBreakerVerifier.isAtivo(chaveTemplate)) {
            logger.warn("CIRCUIT BREAKER ATIVO - Template {} ({}) temporariamente desabilitado para contagem",
                templateId,
                tipoAmigavel);
            return 0;
        }

        logger.info("Obtendo contagem de {} via CSV - Template: {}, Data: {}",
            tipoAmigavel,
            templateId,
            dataReferencia);

        try {
            final Instant dataInicio = dataReferencia.atStartOfDay().atZone(java.time.ZoneOffset.UTC).toInstant();
            final Instant dataFim = dataReferencia.plusDays(1).atStartOfDay().atZone(java.time.ZoneOffset.UTC).toInstant();
            final String url = urlBase + ConstantesApiDataExport.formatarEndpoint(templateId);
            final String corpoJson = requestBodyFactory.construirCorpoRequisicaoCsv(
                nomeTabela,
                campoData,
                dataInicio,
                dataFim
            );

            logger.debug("Baixando CSV para contagem via URL: {} com corpo: {}", url, corpoJson);

            final long inicioMs = System.currentTimeMillis();
            final HttpResponse<String> resposta = csvExecutor.executar(
                url,
                corpoJson,
                timeoutRequisicao,
                "contagem-csv-" + tipoAmigavel.replace(" ", "-")
            );
            final long duracaoMs = System.currentTimeMillis() - inicioMs;

            if (resposta == null) {
                logger.error("Erro: resposta nula ao baixar CSV para contagem de {}", tipoAmigavel);
                throw new RuntimeException("Falha na requisicao CSV: resposta e null");
            }

            if (resposta.statusCode() != 200) {
                final String mensagemErro = String.format(
                    "Erro ao baixar CSV para contagem de %s. Status: %d",
                    tipoAmigavel,
                    resposta.statusCode()
                );
                logger.error("{} ({} ms) Body: {}", mensagemErro, duracaoMs, resposta.body());
                throw new RuntimeException(mensagemErro);
            }

            final long totalLinhas = resposta.body().lines().count();
            final int contagem = Math.max(0, (int) (totalLinhas - 1));

            circuitBreakerReset.resetar(chaveTemplate);
            logger.info("Contagem de {} obtida com sucesso via CSV: {} registros ({} ms)",
                tipoAmigavel,
                contagem,
                duracaoMs);
            return contagem;
        } catch (final RuntimeException e) {
            logger.error("Erro de runtime ao obter contagem de {} via CSV: {}", tipoAmigavel, e.getMessage(), e);
            circuitBreakerIncrement.incrementar(chaveTemplate, tipoAmigavel);
            throw e;
        } catch (final Exception e) {
            logger.error("Erro inesperado ao obter contagem de {} via CSV: {}", tipoAmigavel, e.getMessage(), e);
            circuitBreakerIncrement.incrementar(chaveTemplate, tipoAmigavel);
            throw new RuntimeException("Erro inesperado ao processar contagem de " + tipoAmigavel + " via CSV", e);
        }
    }
}
