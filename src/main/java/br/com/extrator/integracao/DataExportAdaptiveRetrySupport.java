package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/DataExportAdaptiveRetrySupport.java
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


import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;

import org.slf4j.Logger;

import br.com.extrator.integracao.constantes.ConstantesApiDataExport.ConfiguracaoEntidade;

final class DataExportAdaptiveRetrySupport {
    @FunctionalInterface
    interface SearchExecutor<T> {
        ResultadoExtracao<T> executar(ConfiguracaoEntidade configTentativa);
    }

    @FunctionalInterface
    interface RetryStateAction {
        void executar(String chaveTemplate);
    }

    private final Logger logger;

    DataExportAdaptiveRetrySupport(final Logger logger) {
        this.logger = logger;
    }

    <T> ResultadoExtracao<T> executar(
            final String nomeOperacao,
            final String chaveTemplate,
            final List<ConfiguracaoEntidade> tentativas,
            final SearchExecutor<T> executor,
            final Predicate<ResultadoExtracao<?>> deveRetentarResultadoIncompleto,
            final BinaryOperator<ResultadoExtracao<T>> selecionarMelhorResultadoParcial,
            final Predicate<RuntimeException> ehErroTimeoutOu422,
            final RetryStateAction aoIniciarTentativa,
            final RetryStateAction aoRetentarPorResultadoIncompleto,
            final RetryStateAction aoRetentarPorErro) {
        RuntimeException ultimoErro = null;
        ResultadoExtracao<T> melhorParcial = null;

        for (int tentativa = 0; tentativa < tentativas.size(); tentativa++) {
            final ConfiguracaoEntidade configTentativa = tentativas.get(tentativa);
            executarAcao(aoIniciarTentativa, chaveTemplate);

            if (tentativa > 0) {
                logger.warn(
                    "Retry {} apos timeout/422 | tentativa={} | per={} | timeout={}s | order_by={}",
                    nomeOperacao,
                    tentativa + 1,
                    configTentativa.valorPer(),
                    configTentativa.timeout().getSeconds(),
                    formatarOrderBy(configTentativa.orderBy())
                );
            }

            try {
                final ResultadoExtracao<T> resultado = executor.executar(configTentativa);
                if (resultado.isCompleto()) {
                    return resultado;
                }

                melhorParcial = selecionarMelhorResultadoParcial.apply(melhorParcial, resultado);
                final boolean retentavel = deveRetentarResultadoIncompleto.test(resultado);
                final boolean ultimaTentativa = tentativa == tentativas.size() - 1;
                if (!retentavel || ultimaTentativa) {
                    return melhorParcial != null ? melhorParcial : resultado;
                }

                executarAcao(aoRetentarPorResultadoIncompleto, chaveTemplate);
                logger.warn(
                    "{} retornou resultado incompleto (motivo={}) na tentativa {}. Nova tentativa com payload mais leve.",
                    nomeOperacao,
                    resultado.getMotivoInterrupcao(),
                    tentativa + 1
                );
            } catch (final RuntimeException e) {
                ultimoErro = e;
                final boolean timeoutOu422 = ehErroTimeoutOu422.test(e);
                final boolean ultimaTentativa = tentativa == tentativas.size() - 1;
                if (!timeoutOu422 || ultimaTentativa) {
                    if (melhorParcial != null) {
                        logger.warn(
                            "Falha final em {} apos {} tentativa(s). Retornando melhor resultado parcial coletado (registros={}, paginas={}, motivo={}).",
                            nomeOperacao,
                            tentativa + 1,
                            melhorParcial.getRegistrosExtraidos(),
                            melhorParcial.getPaginasProcessadas(),
                            melhorParcial.getMotivoInterrupcao()
                        );
                        return melhorParcial;
                    }
                    throw e;
                }

                executarAcao(aoRetentarPorErro, chaveTemplate);
                logger.warn(
                    "Falha {} com timeout/422. Nova tentativa sera executada com payload mais leve. erro={}",
                    nomeOperacao,
                    e.getMessage()
                );
            }
        }

        if (melhorParcial != null) {
            return melhorParcial;
        }
        throw ultimoErro != null ? ultimoErro : new RuntimeException("Falha inesperada ao extrair " + nomeOperacao);
    }

    private void executarAcao(final RetryStateAction acao, final String chaveTemplate) {
        if (acao != null) {
            acao.executar(chaveTemplate);
        }
    }

    private String formatarOrderBy(final String orderBy) {
        return orderBy == null || orderBy.isBlank() ? "<sem-order-by>" : orderBy;
    }
}
