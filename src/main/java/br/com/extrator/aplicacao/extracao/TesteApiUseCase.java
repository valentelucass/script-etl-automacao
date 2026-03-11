/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/extracao/TesteApiUseCase.java
Classe  : TesteApiUseCase (class)
Pacote  : br.com.extrator.aplicacao.extracao
Modulo  : Use Case - Extracao

Papel   : Testa funcionalidade de um endpoint API (GraphQL ou DataExport) em ultimas 24h.

Conecta com:
- PipelineOrchestrator (executa steps)
- GraphQLPipelineStep, DataExportPipelineStep (steps de teste)
- ExtractionLogQueryPort (valida status final das entidades)
- AplicacaoContexto (obtem portas)

Fluxo geral:
1) executar(TesteApiRequest) valida request e exibe banner da API.
2) Cria steps conforme tipo e entidade.
3) Executa pipeline com PipelineOrchestrator.
4) Valida status das entidades no log dentro de janela de tempo.
5) Lanca IllegalStateException se falhas ou status nao COMPLETO.

Estrutura interna:
Metodos principais:
- executar(TesteApiRequest): ponto de entrada, orquestra teste.
- executarApi(): cria e executa steps conforme tipo de API.
- executarGraphQL(), executarDataExport(): especificos por tipo.
- executarPipeline(): executa pipeline e coleta falhas.
- validarStatusDasEntidadesExecutadas(): verifica logs finais.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.extracao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.aplicacao.contexto.AplicacaoContexto;
import br.com.extrator.aplicacao.pipeline.DataExportPipelineStep;
import br.com.extrator.aplicacao.pipeline.GraphQLPipelineStep;
import br.com.extrator.aplicacao.pipeline.PipelineOrchestrator;
import br.com.extrator.aplicacao.portas.ExtractionLogQueryPort;
import br.com.extrator.aplicacao.pipeline.PipelineReport;
import br.com.extrator.aplicacao.pipeline.PipelineStep;
import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.aplicacao.pipeline.runtime.StepStatus;
import br.com.extrator.suporte.console.BannerUtil;
import br.com.extrator.suporte.tempo.RelogioSistema;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public class TesteApiUseCase {
    private static final Logger logger = LoggerFactory.getLogger(TesteApiUseCase.class);
    private static final String FLAG_SEM_FATURAS_GRAPHQL = "--sem-faturas-graphql";

    public void executar(final TesteApiRequest request) throws Exception {
        final String tipoApi = request.tipoApi();
        boolean incluirFaturasGraphQL = request.incluirFaturasGraphQL();
        final boolean somenteFaturasGraphQL = isEntidadeFaturasGraphQL(request.entidade());

        if (somenteFaturasGraphQL && !incluirFaturasGraphQL) {
            logger.warn(
                "Flag {} ignorada porque a entidade solicitada e explicitamente faturas_graphql.",
                FLAG_SEM_FATURAS_GRAPHQL
            );
            incluirFaturasGraphQL = true;
        }

        final LocalDate dataFim = RelogioSistema.hoje();
        final LocalDate dataInicio = dataFim.minusDays(1);

        exibirBannerTipoApi(tipoApi);

        System.out.println(
            "Periodo de teste: "
                + dataInicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " a "
                + dataFim.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " (ultimas 24h)"
        );
        if ("graphql".equalsIgnoreCase(tipoApi) && request.entidade() == null) {
            System.out.println(
                "Faturas GraphQL: "
                    + (incluirFaturasGraphQL
                        ? "INCLUIDO (fase final)"
                        : "DESABILITADO (" + FLAG_SEM_FATURAS_GRAPHQL + ")")
            );
        }
        System.out.println();

        try {
            final LocalDateTime inicioExecucao = RelogioSistema.agora();
            executarApi(tipoApi, dataInicio, dataFim, request.entidade(), incluirFaturasGraphQL, somenteFaturasGraphQL);
            validarStatusDasEntidadesExecutadas(
                inicioExecucao,
                tipoApi,
                request.entidade(),
                incluirFaturasGraphQL,
                somenteFaturasGraphQL
            );

            BannerUtil.exibirBannerSucesso();
            System.out.println("Teste da API " + tipoApi.toUpperCase() + " concluido com sucesso!");
        } catch (final Exception e) {
            BannerUtil.exibirBannerErro();
            System.err.println("Erro durante execucao: " + e.getMessage());
            logger.error("Erro durante execucao: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void exibirBannerTipoApi(final String tipoApi) {
        switch (tipoApi == null ? "" : tipoApi.toLowerCase()) {
            case "graphql" -> BannerUtil.exibirBannerApiGraphQL();
            case "dataexport" -> BannerUtil.exibirBannerApiDataExport();
            default -> {
                System.err.println("ERRO: Tipo de API invalido: " + tipoApi);
                System.err.println("Tipos validos: graphql, dataexport");
                throw new IllegalArgumentException("Tipo de API invalido: " + tipoApi);
            }
        }
    }

    private void executarApi(final String tipoApi,
                             final LocalDate dataInicio,
                             final LocalDate dataFim,
                             final String entidade,
                             final boolean incluirFaturasGraphQL,
                             final boolean somenteFaturasGraphQL) throws Exception {
        switch (tipoApi == null ? "" : tipoApi.toLowerCase()) {
            case "graphql" -> executarGraphQL(
                dataInicio,
                dataFim,
                entidade,
                incluirFaturasGraphQL,
                somenteFaturasGraphQL
            );
            case "dataexport" -> executarDataExport(dataInicio, dataFim, entidade, incluirFaturasGraphQL);
            default -> {
                System.err.println("ERRO: Tipo de API invalido: " + tipoApi);
                System.err.println("Tipos validos: graphql, dataexport");
                throw new IllegalArgumentException("Tipo de API invalido: " + tipoApi);
            }
        }
    }

    private void executarGraphQL(final LocalDate dataInicio,
                                 final LocalDate dataFim,
                                 final String entidade,
                                 final boolean incluirFaturasGraphQL,
                                 final boolean somenteFaturasGraphQL) throws Exception {
        final List<PipelineStep> steps = new ArrayList<>();

        if (somenteFaturasGraphQL) {
            logger.info("Executando somente Faturas GraphQL para o periodo {} a {}", dataInicio, dataFim);
            steps.add(new GraphQLPipelineStep(AplicacaoContexto.graphQLGateway(), ConstantesEntidades.FATURAS_GRAPHQL));
        } else if (entidade != null && !entidade.isBlank()) {
            steps.add(new GraphQLPipelineStep(AplicacaoContexto.graphQLGateway(), normalizarEntidadeGraphQL(entidade)));
        } else {
            steps.add(new GraphQLPipelineStep(AplicacaoContexto.graphQLGateway(), "graphql"));
        }

        if (!somenteFaturasGraphQL && (entidade == null || entidade.isBlank()) && incluirFaturasGraphQL) {
            logger.info("[FASE 3] Executando Faturas GraphQL por ultimo no teste de API...");
            steps.add(new GraphQLPipelineStep(AplicacaoContexto.graphQLGateway(), ConstantesEntidades.FATURAS_GRAPHQL));
        } else if (!somenteFaturasGraphQL && (entidade == null || entidade.isBlank())) {
            logger.info("[FASE 3] Faturas GraphQL desabilitado no teste de API (flag {}).", FLAG_SEM_FATURAS_GRAPHQL);
        }

        executarPipeline(dataInicio, dataFim, steps, "teste GraphQL");
    }

    private void executarDataExport(final LocalDate dataInicio,
                                    final LocalDate dataFim,
                                    final String entidade,
                                    final boolean incluirFaturasGraphQL) {
        final List<PipelineStep> steps = new ArrayList<>();

        if (!incluirFaturasGraphQL) {
            logger.info("Flag {} ignorada para DataExport.", FLAG_SEM_FATURAS_GRAPHQL);
        }

        if (entidade != null && !entidade.isBlank()) {
            steps.add(new DataExportPipelineStep(AplicacaoContexto.dataExportGateway(), normalizarEntidadeDataExport(entidade)));
        } else {
            steps.add(new DataExportPipelineStep(AplicacaoContexto.dataExportGateway(), "dataexport"));
        }

        executarPipeline(dataInicio, dataFim, steps, "teste DataExport");
    }

    private void executarPipeline(final LocalDate dataInicio,
                                  final LocalDate dataFim,
                                  final List<PipelineStep> steps,
                                  final String contexto) {
        final PipelineOrchestrator orchestrator = AplicacaoContexto.orchestratorFactory().criar();
        final PipelineReport report = orchestrator.executar(dataInicio, dataFim, steps);
        final List<String> falhas = new ArrayList<>();

        for (final StepExecutionResult result : report.getResultados()) {
            if (result.getStatus() == StepStatus.SUCCESS || result.getStatus() == StepStatus.DEGRADED) {
                continue;
            }

            final String nomeEtapa = result.obterNomeEtapa() == null ? "desconhecido" : result.obterNomeEtapa();
            final String mensagem = result.getMessage() == null ? "falha sem mensagem" : result.getMessage();
            falhas.add(nomeEtapa + ": " + mensagem);
        }

        if (report.isAborted()) {
            final String abortadoPor = report.getAbortedBy() == null ? "desconhecido" : report.getAbortedBy();
            falhas.add("pipeline abortado por " + abortadoPor);
        }

        if (!falhas.isEmpty()) {
            throw new IllegalStateException("Falha no " + contexto + ": " + String.join(" | ", falhas));
        }
    }

    private void validarStatusDasEntidadesExecutadas(final LocalDateTime inicioExecucao,
                                                     final String tipoApi,
                                                     final String entidade,
                                                     final boolean incluirFaturasGraphQL,
                                                     final boolean somenteFaturasGraphQL) {
        final List<String> esperadas = obterEntidadesEsperadas(
            tipoApi,
            entidade,
            incluirFaturasGraphQL,
            somenteFaturasGraphQL
        );
        if (esperadas.isEmpty()) {
            return;
        }

        final ExtractionLogQueryPort logQueryPort = AplicacaoContexto.extractionLogQueryPort();
        final LocalDateTime inicioJanela = inicioExecucao.minusSeconds(5);
        final LocalDateTime fimJanela = RelogioSistema.agora().plusSeconds(5);
        final List<String> semLog = new ArrayList<>();
        final List<String> naoCompletas = new ArrayList<>();

        for (final String entidadeEsperada : esperadas) {
            final Optional<LogExtracaoInfo> optLog =
                logQueryPort.buscarUltimoLogPorEntidadeNoIntervaloExecucao(entidadeEsperada, inicioJanela, fimJanela);

            if (optLog.isEmpty()) {
                semLog.add(entidadeEsperada);
                continue;
            }

            final LogExtracaoInfo log = optLog.get();
            if (log.getStatusFinal() != LogExtracaoInfo.StatusExtracao.COMPLETO) {
                naoCompletas.add(entidadeEsperada + "=" + log.getStatusFinal().name());
            }
        }

        if (!semLog.isEmpty() || !naoCompletas.isEmpty()) {
            final StringBuilder detalhe = new StringBuilder("Teste de API reprovado na validacao de status.");
            if (!semLog.isEmpty()) {
                detalhe.append(" Sem log na janela para: ").append(String.join(", ", semLog)).append(".");
            }
            if (!naoCompletas.isEmpty()) {
                detalhe.append(" Status nao COMPLETO: ").append(String.join(", ", naoCompletas)).append(".");
            }
            throw new IllegalStateException(detalhe.toString());
        }
    }

    private List<String> obterEntidadesEsperadas(final String tipoApi,
                                                 final String entidade,
                                                 final boolean incluirFaturasGraphQL,
                                                 final boolean somenteFaturasGraphQL) {
        final Set<String> entidades = new LinkedHashSet<>();
        final String tipo = tipoApi == null ? "" : tipoApi.toLowerCase();

        if ("graphql".equals(tipo)) {
            if (somenteFaturasGraphQL) {
                entidades.add(ConstantesEntidades.FATURAS_GRAPHQL);
                return new ArrayList<>(entidades);
            }

            if (entidade != null && !entidade.isBlank()) {
                entidades.add(normalizarEntidadeGraphQL(entidade));
                return new ArrayList<>(entidades);
            }

            entidades.add(ConstantesEntidades.USUARIOS_SISTEMA);
            entidades.add(ConstantesEntidades.COLETAS);
            entidades.add(ConstantesEntidades.FRETES);
            if (incluirFaturasGraphQL) {
                entidades.add(ConstantesEntidades.FATURAS_GRAPHQL);
            }
            return new ArrayList<>(entidades);
        }

        if ("dataexport".equals(tipo)) {
            if (entidade != null && !entidade.isBlank()) {
                entidades.add(normalizarEntidadeDataExport(entidade));
                return new ArrayList<>(entidades);
            }
            entidades.add(ConstantesEntidades.MANIFESTOS);
            entidades.add(ConstantesEntidades.COTACOES);
            entidades.add(ConstantesEntidades.LOCALIZACAO_CARGAS);
            entidades.add(ConstantesEntidades.CONTAS_A_PAGAR);
            entidades.add(ConstantesEntidades.FATURAS_POR_CLIENTE);
        }

        return new ArrayList<>(entidades);
    }

    private boolean isEntidadeFaturasGraphQL(final String entidade) {
        if (entidade == null || entidade.isBlank()) {
            return false;
        }
        return "faturas_graphql".equalsIgnoreCase(entidade)
            || "faturas".equalsIgnoreCase(entidade)
            || "faturasgraphql".equalsIgnoreCase(entidade);
    }

    private String normalizarEntidadeGraphQL(final String entidade) {
        final String valor = entidade == null ? "" : entidade.trim().toLowerCase();
        return switch (valor) {
            case "faturas", "faturasgraphql" -> ConstantesEntidades.FATURAS_GRAPHQL;
            case "usuarios" -> ConstantesEntidades.USUARIOS_SISTEMA;
            default -> valor;
        };
    }

    private String normalizarEntidadeDataExport(final String entidade) {
        final String valor = entidade == null ? "" : entidade.trim().toLowerCase();
        return switch (valor) {
            case "localizacao_carga", "localizacao_de_carga" -> ConstantesEntidades.LOCALIZACAO_CARGAS;
            case "contasapagar" -> ConstantesEntidades.CONTAS_A_PAGAR;
            case "faturasporcliente" -> ConstantesEntidades.FATURAS_POR_CLIENTE;
            default -> valor;
        };
    }
}
