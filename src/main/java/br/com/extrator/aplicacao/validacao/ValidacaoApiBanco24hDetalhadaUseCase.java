/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/validacao/ValidacaoApiBanco24hDetalhadaUseCase.java
Classe  : ValidacaoApiBanco24hDetalhadaUseCase (class)
Pacote  : br.com.extrator.aplicacao.validacao
Modulo  : Use Case - Validacao

Papel   : Valida dados da janela operacional recente: compara API vs Banco chave-a-chave (POSTMAN-like).

Conecta com:
- ValidacaoApiBanco24hDetalhadaRepository, Comparator, ApiCollector, Reporter (delegacao)

Fluxo geral:
1) executar(request) orquestra validacao detalhada.
2) Coletaentidades da API e compara contra BD.
3) Retorna resumo de falhas (ou lança excecao).

Estrutura interna:
Composicao com builders:
- repository: persistencia e queries.
- comparator: compara resultados API x BD.
- apiCollector: coleta dados da API.
- reporter: exibe relatorio.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.validacao;

import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.EntidadeValidacao;
import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.ResumoExecucao;
import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.ResultadoApiChaves;
import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.ResultadoComparacao;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.PeriodoConsulta;
import br.com.extrator.aplicacao.extracao.ExtracaoPorIntervaloRequest;
import br.com.extrator.aplicacao.extracao.ExtracaoPorIntervaloUseCase;
import br.com.extrator.suporte.banco.GerenciadorConexao;
import br.com.extrator.suporte.configuracao.ConfigEtl;
import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.tempo.RelogioSistema;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public class ValidacaoApiBanco24hDetalhadaUseCase {
    private final LoggerConsole log;
    private final ValidacaoApiBanco24hDetalhadaRepository repository;
    private final ValidacaoApiBanco24hDetalhadaComparator comparator;
    private final ValidacaoApiBanco24hDetalhadaApiCollector apiCollector;
    private final ValidacaoApiBanco24hDetalhadaReporter reporter;
    private final LateDataReplayExecutor lateDataReplayExecutor;

    public ValidacaoApiBanco24hDetalhadaUseCase() {
        this(
            LoggerConsole.getLogger(ValidacaoApiBanco24hDetalhadaUseCase.class),
            new ValidacaoApiBanco24hDetalhadaMetadataHasher()
        );
    }

    private ValidacaoApiBanco24hDetalhadaUseCase(
        final LoggerConsole log,
        final ValidacaoApiBanco24hDetalhadaMetadataHasher metadataHasher
    ) {
        this(
            log,
            new ValidacaoApiBanco24hDetalhadaRepository(log, metadataHasher),
            metadataHasher
        );
    }

    private ValidacaoApiBanco24hDetalhadaUseCase(
        final LoggerConsole log,
        final ValidacaoApiBanco24hDetalhadaRepository repository,
        final ValidacaoApiBanco24hDetalhadaMetadataHasher metadataHasher
    ) {
        this(
            log,
            repository,
            new ValidacaoApiBanco24hDetalhadaComparator(repository),
            new ValidacaoApiBanco24hDetalhadaApiCollector(metadataHasher, repository)
        );
    }

    private ValidacaoApiBanco24hDetalhadaUseCase(
        final LoggerConsole log,
        final ValidacaoApiBanco24hDetalhadaRepository repository,
        final ValidacaoApiBanco24hDetalhadaComparator comparator,
        final ValidacaoApiBanco24hDetalhadaApiCollector apiCollector
    ) {
        this(
            log,
            repository,
            comparator,
            apiCollector,
            new ValidacaoApiBanco24hDetalhadaReporter(log, comparator),
            (dataInicio, dataFim, incluirFaturasGraphQL) -> new ExtracaoPorIntervaloUseCase().executar(
                new ExtracaoPorIntervaloRequest(
                    dataInicio,
                    dataFim,
                    null,
                    null,
                    incluirFaturasGraphQL,
                    false
                )
            )
        );
    }

    ValidacaoApiBanco24hDetalhadaUseCase(
        final LoggerConsole log,
        final ValidacaoApiBanco24hDetalhadaRepository repository,
        final ValidacaoApiBanco24hDetalhadaComparator comparator,
        final ValidacaoApiBanco24hDetalhadaApiCollector apiCollector,
        final ValidacaoApiBanco24hDetalhadaReporter reporter
    ) {
        this(
            log,
            repository,
            comparator,
            apiCollector,
            reporter,
            (dataInicio, dataFim, incluirFaturasGraphQL) -> new ExtracaoPorIntervaloUseCase().executar(
                new ExtracaoPorIntervaloRequest(
                    dataInicio,
                    dataFim,
                    null,
                    null,
                    incluirFaturasGraphQL,
                    false
                )
            )
        );
    }

    ValidacaoApiBanco24hDetalhadaUseCase(
        final LoggerConsole log,
        final ValidacaoApiBanco24hDetalhadaRepository repository,
        final ValidacaoApiBanco24hDetalhadaComparator comparator,
        final ValidacaoApiBanco24hDetalhadaApiCollector apiCollector,
        final ValidacaoApiBanco24hDetalhadaReporter reporter,
        final LateDataReplayExecutor lateDataReplayExecutor
    ) {
        this.log = log;
        this.repository = repository;
        this.comparator = comparator;
        this.apiCollector = apiCollector;
        this.reporter = reporter;
        this.lateDataReplayExecutor = lateDataReplayExecutor;
    }

    public void executar(final ValidacaoApiBanco24hDetalhadaRequest request) throws Exception {
        ExecucaoDetalhada execucao = executarComparacao(request);
        ResumoExecucao resumo = reporter.reportar(execucao.resultados());
        int tentativas = 0;
        while (resumo.falhas() > 0 && deveExecutarReplayDadoTardio(request, execucao.resultados(), tentativas)) {
            tentativas++;
            final long delayMs = ConfigEtl.obterLateDataAutoReplayDelayMs();
            log.warn(
                "LATE_DATA_AUTO_REPLAY | tentativa={}/{} | periodo={} a {} | motivo=faltantes_api_pos_extracao",
                tentativas,
                ConfigEtl.obterLateDataAutoReplayMaxAttempts(),
                execucao.dataInicio(),
                execucao.dataFim()
            );
            if (delayMs > 0) {
                pausarAntesDoReplay(delayMs);
            }
            lateDataReplayExecutor.replay(
                execucao.dataInicio(),
                execucao.dataFim(),
                request.incluirFaturasGraphQL()
            );
            execucao = executarComparacao(request);
            resumo = reporter.reportar(execucao.resultados());
        }

        if (resumo.falhas() > 0) {
            throw new RuntimeException(
                "Comparacao detalhada API x Banco reprovada: "
                    + resumo.falhas()
                    + " entidade(s) com divergencia."
            );
        }
    }

    private ExecucaoDetalhada executarComparacao(final ValidacaoApiBanco24hDetalhadaRequest request) throws Exception {
        final List<ResultadoComparacao> resultados = new ArrayList<>();
        comparator.definirPeriodoFechado(request.periodoFechado());
        final LocalDateTime inicioValidacao = RelogioSistema.agora();
        LocalDate dataInicioResultado = null;
        LocalDate dataFimResultado = null;

        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            final LocalDate dataReferencia = repository.resolverDataReferenciaLogs(conexao, request.dataReferenciaSistema());
            final LocalDate dataInicio = dataReferencia.minusDays(1);
            final LocalDate dataFim = request.periodoFechado() ? dataReferencia.minusDays(1) : dataReferencia;
            dataInicioResultado = dataInicio;
            dataFimResultado = dataFim;
            final Set<String> entidadesSolicitadas = entidadesSolicitadas(request.incluirFaturasGraphQL());

            log.console("\n" + "=".repeat(88));
            log.info("VALIDACAO DETALHADA | JANELA OPERACIONAL RECENTE | API (POSTMAN-LIKE) x BANCO | COMPARACAO CHAVE A CHAVE");
            log.info("Periodo API: {} a {}", dataInicio, dataFim);
            if (request.periodoFechado()) {
                log.info("Modo: PERIODO FECHADO (sem dia em andamento)");
            }
            log.info(
                "Fallback de janela sem periodo: {}",
                request.permitirFallbackJanela() ? "ATIVADO" : "DESATIVADO"
            );
            log.info("Data de referencia dos logs: {}", dataReferencia);
            log.console("=".repeat(88));

            Optional<String> executionUuidAncora = request.executionUuidAncoraOpt();
            executionUuidAncora.ifPresent(executionUuid ->
                log.info("Execution UUID informado via CLI para validacao detalhada: {}", executionUuid)
            );
            if (executionUuidAncora.isEmpty()) {
                executionUuidAncora = repository.resolverExecutionUuidAncora(
                    conexao,
                    entidadesSolicitadas,
                    dataInicio,
                    dataFim,
                    inicioValidacao
                );
            }
            if (executionUuidAncora.isEmpty()) {
                executionUuidAncora = repository.resolverExecutionUuidAncoraRecente(
                    conexao,
                    entidadesSolicitadas,
                    inicioValidacao
                );
            }
            final Map<String, PeriodoConsulta> periodosPorEntidade =
                resolverPeriodosPorEntidade(conexao, executionUuidAncora, entidadesSolicitadas);
            if (!periodosPorEntidade.isEmpty()) {
                log.info("Periodos da execucao ancorada reaproveitados para validacao: {}", periodosPorEntidade.size());
            }
            executionUuidAncora.ifPresent(executionUuid ->
                log.info("Execution UUID ancora da validacao detalhada: {}", executionUuid)
            );
            final List<EntidadeValidacao> entidadesValidacao = apiCollector.criarEntidades(
                conexao,
                dataReferencia,
                dataInicio,
                dataFim,
                request.incluirFaturasGraphQL(),
                request.permitirFallbackJanela(),
                periodosPorEntidade,
                executionUuidAncora
            );

            for (final EntidadeValidacao entidade : entidadesValidacao) {
                final PeriodoConsulta periodoEntidade = periodosPorEntidade.getOrDefault(
                    entidade.entidade(),
                    new PeriodoConsulta(dataInicio, dataFim)
                );
                final ResultadoApiChaves api = entidade.fornecedor().get();
                resultados.add(
                    comparator.compararEntidade(
                        conexao,
                        entidade.entidade(),
                        api,
                        dataReferencia,
                        periodoEntidade.inicio(),
                        periodoEntidade.fim(),
                        request.periodoFechado(),
                        request.permitirFallbackJanela(),
                        executionUuidAncora
                    )
                );
            }
        }

        return new ExecucaoDetalhada(dataInicioResultado, dataFimResultado, resultados);
    }

    boolean deveExecutarReplayDadoTardio(final ValidacaoApiBanco24hDetalhadaRequest request,
                                         final List<ResultadoComparacao> resultados,
                                         final int tentativasExecutadas) {
        if (!request.periodoFechado()
            || !ConfigEtl.isLateDataAutoReplayAtivo()
            || tentativasExecutadas >= ConfigEtl.obterLateDataAutoReplayMaxAttempts()) {
            return false;
        }
        return somenteFaltantesPorDadoTardio(resultados);
    }

    boolean somenteFaltantesPorDadoTardio(final List<ResultadoComparacao> resultados) {
        if (resultados == null || resultados.isEmpty()) {
            return false;
        }

        boolean encontrouFalhaTardia = false;
        for (final ResultadoComparacao resultado : resultados) {
            if (resultado == null || resultado.ok()) {
                continue;
            }
            final boolean inconclusivo = resultado.detalhe() != null && resultado.detalhe().startsWith("INCONCLUSIVO:");
            if (!resultado.apiCompleta()
                || inconclusivo
                || resultado.faltantes() <= 0
                || resultado.excedentes() != 0
                || resultado.divergenciasDados() != 0) {
                return false;
            }
            encontrouFalhaTardia = true;
        }
        return encontrouFalhaTardia;
    }

    private void pausarAntesDoReplay(final long delayMs) {
        try {
            Thread.sleep(delayMs);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrompido antes do replay automatico de dados tardios.", e);
        }
    }

    private Map<String, PeriodoConsulta> resolverPeriodosPorEntidade(final Connection conexao,
                                                                     final Optional<String> executionUuidAncora,
                                                                     final Set<String> entidadesSolicitadas)
        throws Exception {
        if (executionUuidAncora.isEmpty() || entidadesSolicitadas == null || entidadesSolicitadas.isEmpty()) {
            return Map.of();
        }

        final Map<String, PeriodoConsulta> periodos = new LinkedHashMap<>();
        for (final String entidade : entidadesSolicitadas) {
            repository.buscarPeriodoConsultaDaExecucao(conexao, executionUuidAncora.get(), entidade)
                .ifPresent(periodo -> periodos.put(entidade, periodo));
        }
        return periodos.isEmpty() ? Map.of() : Map.copyOf(periodos);
    }

    private Set<String> entidadesSolicitadas(final boolean incluirFaturasGraphQL) {
        final Set<String> entidades = new LinkedHashSet<>(List.of(
            ConstantesEntidades.FRETES,
            ConstantesEntidades.COLETAS,
            ConstantesEntidades.MANIFESTOS,
            ConstantesEntidades.COTACOES,
            ConstantesEntidades.LOCALIZACAO_CARGAS,
            ConstantesEntidades.CONTAS_A_PAGAR,
            ConstantesEntidades.FATURAS_POR_CLIENTE,
            ConstantesEntidades.INVENTARIO,
            ConstantesEntidades.SINISTROS,
            ConstantesEntidades.USUARIOS_SISTEMA
        ));
        if (incluirFaturasGraphQL) {
            entidades.add(ConstantesEntidades.FATURAS_GRAPHQL);
        }
        return entidades;
    }

    @FunctionalInterface
    interface LateDataReplayExecutor {
        void replay(LocalDate dataInicio, LocalDate dataFim, boolean incluirFaturasGraphQL) throws Exception;
    }

    private record ExecucaoDetalhada(
        LocalDate dataInicio,
        LocalDate dataFim,
        List<ResultadoComparacao> resultados
    ) { }
}
