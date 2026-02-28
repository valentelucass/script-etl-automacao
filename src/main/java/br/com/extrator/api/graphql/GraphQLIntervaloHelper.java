/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/api/graphql/GraphQLIntervaloHelper.java
Classe  : GraphQLIntervaloHelper (class)
Pacote  : br.com.extrator.api.graphql
Modulo  : Cliente de integracao API
Papel   : Implementa responsabilidade de graph qlintervalo helper.

Conecta com:
- ResultadoExtracao (api)
- ThreadUtil (util)
- CarregadorConfig (util.configuracao)

Fluxo geral:
1) Monta requisicoes para endpoints externos.
2) Trata autenticacao, timeout e parse de resposta.
3) Entrega dados normalizados para os extractors.

Estrutura interna:
Metodos principais:
- GraphQLIntervaloHelper(): realiza operacao relacionada a "graph qlintervalo helper".
- executarPorDia(...5 args): executa o fluxo principal desta responsabilidade.
- executarPorDia(...6 args): executa o fluxo principal desta responsabilidade.
- executarDiaComRetry(...6 args): executa o fluxo principal desta responsabilidade.
- deveRetentarDia(...1 args): verifica comportamento esperado em teste automatizado.
- aguardarRetry(...3 args): realiza operacao relacionada a "aguardar retry".
- calcularDelay(...3 args): realiza operacao relacionada a "calcular delay".
- selecionarMotivoInterrupcao(...2 args): realiza operacao relacionada a "selecionar motivo interrupcao".
Atributos-chave:
- logger: logger da classe para diagnostico.
[DOC-FILE-END]============================================================== */

package br.com.extrator.api.graphql;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.api.ResultadoExtracao;
import br.com.extrator.util.ThreadUtil;
import br.com.extrator.util.configuracao.CarregadorConfig;

/**
 * Helper para execucao de queries GraphQL por intervalo de datas.
 * Util para entidades que nao suportam intervalo diretamente (ex: coletas, faturas).
 */
public final class GraphQLIntervaloHelper {

    private static final Logger logger = LoggerFactory.getLogger(GraphQLIntervaloHelper.class);

    private GraphQLIntervaloHelper() {
        // Construtor privado para classe utilitaria
    }

    /**
     * Executa uma funcao de extracao para cada dia do intervalo especificado.
     * Consolida os resultados em um unico ResultadoExtracao.
     */
    public static <T> ResultadoExtracao<T> executarPorDia(
            final LocalDate dataInicio,
            final LocalDate dataFim,
            final Function<LocalDate, ResultadoExtracao<T>> executorDia,
            final String nomeEntidade) {

        logger.info("Buscando {} via GraphQL - Periodo: {} a {}", nomeEntidade, dataInicio, dataFim);

        final List<T> todas = new ArrayList<>();
        int totalPaginas = 0;
        boolean todasCompletas = true;
        String motivoInterrupcao = null;

        LocalDate dia = dataInicio;
        int diaAtual = 1;
        final long totalDias = ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;

        while (!dia.isAfter(dataFim)) {
            logger.info("{} - Dia {}/{}: {}", nomeEntidade, diaAtual, totalDias, dia);

            final ResultadoExtracao<T> resultadoDia = executarDiaComRetry(dia, executorDia, nomeEntidade, diaAtual, totalDias);
            todas.addAll(resultadoDia.getDados());
            totalPaginas += resultadoDia.getPaginasProcessadas();

            if (resultadoDia.isCompleto()) {
                logger.info("Dia {}/{} completo: {} registros", diaAtual, totalDias, resultadoDia.getDados().size());
            } else {
                logger.warn("Dia {}/{} incompleto: {} registros", diaAtual, totalDias, resultadoDia.getDados().size());
                todasCompletas = false;
                motivoInterrupcao = selecionarMotivoInterrupcao(motivoInterrupcao, resultadoDia.getMotivoInterrupcao());
            }

            dia = dia.plusDays(1);
            diaAtual++;
        }

        logger.info("Total consolidado: {} {}", todas.size(), nomeEntidade);

        if (todasCompletas) {
            return ResultadoExtracao.completo(todas, totalPaginas, todas.size());
        }

        return ResultadoExtracao.incompleto(
            todas,
            motivoInterrupcao != null ? motivoInterrupcao : ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS.getCodigo(),
            totalPaginas,
            todas.size()
        );
    }

    /**
     * Executa uma funcao de extracao para cada dia de um intervalo,
     * com opcao de log detalhado de progresso.
     */
    public static <T> ResultadoExtracao<T> executarPorDia(
            final LocalDate dataInicio,
            final LocalDate dataFim,
            final Function<LocalDate, ResultadoExtracao<T>> executorDia,
            final String nomeEntidade,
            final boolean logProgresso) {

        if (logProgresso) {
            return executarPorDia(dataInicio, dataFim, executorDia, nomeEntidade);
        }

        final List<T> todas = new ArrayList<>();
        int totalPaginas = 0;
        boolean todasCompletas = true;
        String motivoInterrupcao = null;

        LocalDate dia = dataInicio;
        while (!dia.isAfter(dataFim)) {
            final ResultadoExtracao<T> resultadoDia = executarDiaComRetry(dia, executorDia, nomeEntidade, 0, 0);
            todas.addAll(resultadoDia.getDados());
            totalPaginas += resultadoDia.getPaginasProcessadas();

            if (!resultadoDia.isCompleto()) {
                todasCompletas = false;
                motivoInterrupcao = selecionarMotivoInterrupcao(motivoInterrupcao, resultadoDia.getMotivoInterrupcao());
            }

            dia = dia.plusDays(1);
        }

        if (todasCompletas) {
            return ResultadoExtracao.completo(todas, totalPaginas, todas.size());
        }

        return ResultadoExtracao.incompleto(
            todas,
            motivoInterrupcao != null ? motivoInterrupcao : ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS.getCodigo(),
            totalPaginas,
            todas.size()
        );
    }

    private static <T> ResultadoExtracao<T> executarDiaComRetry(
            final LocalDate dia,
            final Function<LocalDate, ResultadoExtracao<T>> executorDia,
            final String nomeEntidade,
            final int diaAtual,
            final long totalDias) {
        final int tentativasConfiguradas = Math.max(1, CarregadorConfig.obterMaxTentativasRetry());
        final int maxTentativas = Math.min(3, tentativasConfiguradas);
        final long delayBaseMs = Math.max(500L, CarregadorConfig.obterDelayBaseRetry());
        final double multiplicador = Math.max(1.0d, CarregadorConfig.obterMultiplicadorRetry());
        ResultadoExtracao<T> ultimoResultado = null;

        for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {
            try {
                ultimoResultado = executorDia.apply(dia);
            } catch (final RuntimeException e) {
                logger.warn(
                    "Falha ao extrair {} no dia {} (tentativa {}/{}): {}",
                    nomeEntidade,
                    dia,
                    tentativa,
                    maxTentativas,
                    e.getMessage()
                );
                if (tentativa < maxTentativas) {
                    aguardarRetry(delayBaseMs, multiplicador, tentativa);
                    continue;
                }
                throw e;
            }

            if (!deveRetentarDia(ultimoResultado) || tentativa == maxTentativas) {
                return ultimoResultado;
            }

            final long delayMs = calcularDelay(delayBaseMs, multiplicador, tentativa);
            if (diaAtual > 0 && totalDias > 0) {
                logger.warn(
                    "{} - Dia {}/{} apresentou erro de API (motivo={}): tentativa {}/{}. Repetindo em {}ms...",
                    nomeEntidade,
                    diaAtual,
                    totalDias,
                    ultimoResultado.getMotivoInterrupcao(),
                    tentativa,
                    maxTentativas,
                    delayMs
                );
            } else {
                logger.warn(
                    "{} - Dia {} apresentou erro de API (motivo={}): tentativa {}/{}. Repetindo em {}ms...",
                    nomeEntidade,
                    dia,
                    ultimoResultado.getMotivoInterrupcao(),
                    tentativa,
                    maxTentativas,
                    delayMs
                );
            }
            aguardarRetry(delayBaseMs, multiplicador, tentativa);
        }

        return ultimoResultado;
    }

    private static <T> boolean deveRetentarDia(final ResultadoExtracao<T> resultado) {
        if (resultado == null || resultado.isCompleto()) {
            return false;
        }
        final String motivo = resultado.getMotivoInterrupcao();
        return ResultadoExtracao.MotivoInterrupcao.ERRO_API.getCodigo().equals(motivo)
            || ResultadoExtracao.MotivoInterrupcao.CIRCUIT_BREAKER.getCodigo().equals(motivo);
    }

    private static void aguardarRetry(final long delayBaseMs, final double multiplicador, final int tentativa) {
        final long delayMs = calcularDelay(delayBaseMs, multiplicador, tentativa);
        try {
            ThreadUtil.aguardar(delayMs);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrompida durante retry de extração GraphQL por dia", e);
        }
    }

    private static long calcularDelay(final long delayBaseMs, final double multiplicador, final int tentativa) {
        final double fator = Math.pow(multiplicador, Math.max(0, tentativa - 1));
        final long delayCalculado = Math.round(delayBaseMs * fator);
        return Math.min(delayCalculado, 30_000L);
    }

    private static String selecionarMotivoInterrupcao(final String atual, final String candidato) {
        if (candidato == null || candidato.isBlank()) {
            return atual;
        }
        if (ResultadoExtracao.MotivoInterrupcao.ERRO_API.getCodigo().equals(candidato)
            || ResultadoExtracao.MotivoInterrupcao.CIRCUIT_BREAKER.getCodigo().equals(candidato)) {
            return candidato;
        }
        if (atual == null || atual.isBlank()) {
            return candidato;
        }
        return atual;
    }
}
