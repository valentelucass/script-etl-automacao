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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import br.com.extrator.suporte.banco.GerenciadorConexao;
import br.com.extrator.suporte.console.LoggerConsole;

public class ValidacaoApiBanco24hDetalhadaUseCase {
    private final LoggerConsole log;
    private final ValidacaoApiBanco24hDetalhadaRepository repository;
    private final ValidacaoApiBanco24hDetalhadaComparator comparator;
    private final ValidacaoApiBanco24hDetalhadaApiCollector apiCollector;
    private final ValidacaoApiBanco24hDetalhadaReporter reporter;

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
        this(log, repository, comparator, apiCollector, new ValidacaoApiBanco24hDetalhadaReporter(log, comparator));
    }

    ValidacaoApiBanco24hDetalhadaUseCase(
        final LoggerConsole log,
        final ValidacaoApiBanco24hDetalhadaRepository repository,
        final ValidacaoApiBanco24hDetalhadaComparator comparator,
        final ValidacaoApiBanco24hDetalhadaApiCollector apiCollector,
        final ValidacaoApiBanco24hDetalhadaReporter reporter
    ) {
        this.log = log;
        this.repository = repository;
        this.comparator = comparator;
        this.apiCollector = apiCollector;
        this.reporter = reporter;
    }

    public void executar(final ValidacaoApiBanco24hDetalhadaRequest request) throws Exception {
        final List<ResultadoComparacao> resultados = new ArrayList<>();

        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            final LocalDate dataReferencia = repository.resolverDataReferenciaLogs(conexao, request.dataReferenciaSistema());
            final LocalDate dataInicio = dataReferencia.minusDays(1);
            final LocalDate dataFim = request.periodoFechado() ? dataReferencia.minusDays(1) : dataReferencia;

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

            for (final EntidadeValidacao entidade : apiCollector.criarEntidades(
                conexao,
                dataReferencia,
                dataInicio,
                dataFim,
                request.incluirFaturasGraphQL(),
                request.permitirFallbackJanela()
            )) {
                final ResultadoApiChaves api = entidade.fornecedor().get();
                resultados.add(
                    comparator.compararEntidade(
                        conexao,
                        entidade.entidade(),
                        api,
                        dataReferencia,
                        dataInicio,
                        dataFim,
                        request.periodoFechado(),
                        request.permitirFallbackJanela()
                    )
                );
            }
        }

        final ResumoExecucao resumo = reporter.reportar(resultados);
        if (resumo.falhas() > 0) {
            throw new RuntimeException(
                "Comparacao detalhada API x Banco reprovada: "
                    + resumo.falhas()
                    + " entidade(s) com divergencia."
            );
        }
    }
}
