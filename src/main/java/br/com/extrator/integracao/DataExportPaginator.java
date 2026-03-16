package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/DataExportPaginator.java
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
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import br.com.extrator.integracao.constantes.ConstantesApiDataExport;
import br.com.extrator.integracao.constantes.ConstantesApiDataExport.ConfiguracaoEntidade;
import br.com.extrator.suporte.ThreadUtil;
import br.com.extrator.suporte.configuracao.ConfigApi;
import br.com.extrator.suporte.mapeamento.MapperUtil;

final class DataExportPaginator {
    private final Logger logger;
    private final String urlBase;
    private final DataExportRequestBodyFactory requestBodyFactory;
    private final DataExportPageAuditLogger pageAuditLogger;
    private final DataExportHttpExecutor httpExecutor;
    private final int maxTentativasTimeoutPorPagina;
    private final int maxTentativasTimeoutPaginaUm;
    private final int intervaloLogProgresso;
    private final DataExportPaginationSupport paginationSupport;
    private final DataExportTimeWindowSupport timeWindowSupport;
    private final DataExportTimeout422Probe timeout422Probe;

    DataExportPaginator(final Logger logger,
                        final String urlBase,
                        final DataExportRequestBodyFactory requestBodyFactory,
                        final DataExportPageAuditLogger pageAuditLogger,
                        final DataExportHttpExecutor httpExecutor,
                        final int maxTentativasTimeoutPorPagina,
                        final int maxTentativasTimeoutPaginaUm,
                        final int intervaloLogProgresso,
                        final DataExportPaginationSupport paginationSupport,
                        final DataExportTimeWindowSupport timeWindowSupport) {
        this.logger = logger;
        this.urlBase = urlBase;
        this.requestBodyFactory = requestBodyFactory;
        this.pageAuditLogger = pageAuditLogger;
        this.httpExecutor = httpExecutor;
        this.maxTentativasTimeoutPorPagina = maxTentativasTimeoutPorPagina;
        this.maxTentativasTimeoutPaginaUm = maxTentativasTimeoutPaginaUm;
        this.intervaloLogProgresso = intervaloLogProgresso;
        this.paginationSupport = paginationSupport;
        this.timeWindowSupport = timeWindowSupport;
        this.timeout422Probe = new DataExportTimeout422Probe(logger, requestBodyFactory, httpExecutor);
    }

    <T> ResultadoExtracao<T> buscarDadosGenericos(final String executionUuid,
                                                  final int templateId,
                                                  final String nomeTabela,
                                                  final String campoData,
                                                  final TypeReference<List<T>> typeReference,
                                                  final Instant dataInicio,
                                                  final Instant dataFim,
                                                  final ConfiguracaoEntidade config) {
        return buscarDadosGenericos(
            executionUuid,
            templateId,
            nomeTabela,
            campoData,
            typeReference,
            dataInicio,
            dataFim,
            config,
            true
        );
    }

    <T> ResultadoExtracao<T> buscarDadosGenericos(final String executionUuid,
                                                  final int templateId,
                                                  final String nomeTabela,
                                                  final String campoData,
                                                  final TypeReference<List<T>> typeReference,
                                                  final Instant dataInicio,
                                                  final Instant dataFim,
                                                  final ConfiguracaoEntidade config,
                                                  final boolean permitirParticionamento) {
        final String tipoAmigavel = obterNomeAmigavelTipo(templateId, nomeTabela);
        final String chaveTemplate = "Template-" + templateId;
        final String executionId = (executionUuid == null || executionUuid.isBlank())
            ? java.util.UUID.randomUUID().toString()
            : executionUuid;
        final String runUuid = java.util.UUID.randomUUID().toString();

        if (paginationSupport.isCircuitBreakerAtivo(chaveTemplate)) {
            logger.warn(
                "âš ï¸ CIRCUIT BREAKER ATIVO - Template {} ({}) temporariamente desabilitado devido a falhas consecutivas",
                templateId,
                tipoAmigavel
            );
            return ResultadoExtracao.incompleto(
                new ArrayList<>(),
                ResultadoExtracao.MotivoInterrupcao.CIRCUIT_BREAKER,
                0,
                0
            );
        }

        final String valorPer = config.valorPer();
        final Duration timeout = config.timeout();
        final int perInt;
        try {
            perInt = Integer.parseInt(valorPer);
        } catch (final NumberFormatException e) {
            return ResultadoExtracao.incompleto(
                new ArrayList<>(),
                ResultadoExtracao.MotivoInterrupcao.ERRO_API,
                0,
                0
            );
        }

        final LocalDate janelaInicio = timeWindowSupport.toLocalDate(dataInicio);
        final LocalDate janelaFim = timeWindowSupport.toLocalDate(dataFim);

        if (permitirParticionamento && ConfigApi.isParticionamentoJanelaDataExportAtivo() && janelaInicio.isBefore(janelaFim)) {
            logger.info(
                "Particionamento automatico DataExport ativo para template {} ({}): {} ate {} (sub-janelas diarias)",
                templateId,
                tipoAmigavel,
                janelaInicio,
                janelaFim
            );

            final List<T> consolidados = new ArrayList<>();
            int paginasConsolidadas = 0;
            String motivoInterrupcaoConsolidado = null;
            boolean completo = true;

            LocalDate dia = janelaInicio;
            while (!dia.isAfter(janelaFim)) {
                final Instant inicioDia = timeWindowSupport.inicioDoDia(dia);
                final Instant fimDia = timeWindowSupport.fimDoDia(dia);

                final ResultadoExtracao<T> resultadoDia = buscarDadosGenericos(
                    executionId,
                    templateId,
                    nomeTabela,
                    campoData,
                    typeReference,
                    inicioDia,
                    fimDia,
                    config,
                    false
                );
                consolidados.addAll(resultadoDia.getDados());
                paginasConsolidadas += resultadoDia.getPaginasProcessadas();

                if (!resultadoDia.isCompleto()) {
                    completo = false;
                    motivoInterrupcaoConsolidado = selecionarMotivoInterrupcao(
                        motivoInterrupcaoConsolidado,
                        resultadoDia.getMotivoInterrupcao()
                    );
                }
                dia = dia.plusDays(1);
            }

            return completo
                ? ResultadoExtracao.completo(consolidados, paginasConsolidadas, consolidados.size())
                : ResultadoExtracao.incompleto(
                    consolidados,
                    motivoInterrupcaoConsolidado != null
                        ? motivoInterrupcaoConsolidado
                        : ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS.getCodigo(),
                    paginasConsolidadas,
                    consolidados.size()
                );
        }

        logger.info("INICIANDO EXTRACAO: Template {} - {}", templateId, tipoAmigavel);
        logger.info("Periodo: {} ate {}", janelaInicio, janelaFim);
        logger.info("Valor 'per': {}", valorPer);
        logger.info("Timeout: {} segundos", timeout.getSeconds());

        final List<T> resultadosFinais = new ArrayList<>();
        int paginaAtual = 1;
        int totalPaginas = 0;
        int totalRegistrosProcessados = 0;
        int paginasTimeout422ComLacuna = 0;
        boolean interrompido = false;
        ResultadoExtracao.MotivoInterrupcao motivoInterrupcao = null;

        final int limitePaginas = ConfigApi.obterLimitePaginasApiDataExportPorTemplate(templateId);
        final int maxRegistros = ConfigApi.obterMaxRegistrosDataExportPorTemplate(templateId);

        try {
            while (true) {
                if (paginaAtual > limitePaginas) {
                    logger.warn(
                        "PROTECAO ATIVADA - Template {} ({}): Limite de {} paginas atingido. Interrompendo busca para evitar loop infinito.",
                        templateId,
                        tipoAmigavel,
                        limitePaginas
                    );
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS;
                    break;
                }
                if (totalRegistrosProcessados >= maxRegistros) {
                    logger.warn(
                        "PROTECAO ATIVADA - Template {} ({}): Limite de {} registros atingido. Interrompendo busca para evitar sobrecarga.",
                        templateId,
                        tipoAmigavel,
                        maxRegistros
                    );
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.LIMITE_REGISTROS;
                    break;
                }

                logger.info("-> Requisitando pagina {}...", paginaAtual);
                final String url = urlBase + ConstantesApiDataExport.formatarEndpoint(templateId);
                final String corpoJson = requestBodyFactory.construirCorpoRequisicao(
                    nomeTabela,
                    campoData,
                    dataInicio,
                    dataFim,
                    paginaAtual,
                    config
                );
                final String reqHash = PayloadHashUtil.sha256Hex(corpoJson);

                HttpResponse<String> resposta = null;
                long duracaoMs = 0L;
                int tentativaTimeoutPagina = 1;
                final int maxTentativasTimeoutPaginaAtual = paginaAtual == 1
                    ? Math.min(maxTentativasTimeoutPorPagina, maxTentativasTimeoutPaginaUm)
                    : maxTentativasTimeoutPorPagina;

                while (tentativaTimeoutPagina <= maxTentativasTimeoutPaginaAtual) {
                    final long tempoInicio = System.currentTimeMillis();
                    resposta = httpExecutor.executarRequisicaoDataExportJson(
                        url,
                        corpoJson,
                        timeout,
                        "DataExport-Template-" + templateId + "-Page-" + paginaAtual
                    );
                    duracaoMs = System.currentTimeMillis() - tempoInicio;

                    if (!httpExecutor.ehRespostaTimeout422(resposta)
                        || tentativaTimeoutPagina == maxTentativasTimeoutPaginaAtual) {
                        break;
                    }

                    final long atrasoRetryTimeoutMs = httpExecutor.calcularAtrasoRetryTimeoutPagina(tentativaTimeoutPagina);
                    logger.warn(
                        "Timeout 422 em {} pagina {}. Retentativa {}/{} em {}ms (backoff exponencial+jitter).",
                        tipoAmigavel,
                        paginaAtual,
                        tentativaTimeoutPagina + 1,
                        maxTentativasTimeoutPaginaAtual,
                        atrasoRetryTimeoutMs
                    );
                    try {
                        ThreadUtil.aguardar(atrasoRetryTimeoutMs);
                    } catch (final InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(
                            "Thread interrompida durante retentativa de timeout da pagina " + paginaAtual,
                            ex
                        );
                    }
                    tentativaTimeoutPagina++;
                }

                if (resposta == null) {
                    throw new RuntimeException("Resposta nula na paginacao - pagina " + paginaAtual);
                }

                logger.info("Resposta recebida: Status {}, Tempo: {}ms", resposta.statusCode(), duracaoMs);
                final String respHash = PayloadHashUtil.sha256Hex(resposta.body());

                if (resposta.statusCode() != 200) {
                    if (httpExecutor.ehRespostaTimeout422(resposta) && paginaAtual > 1) {
                        final DataExportTimeout422Probe.ResultadoSondaTimeout422 resultadoSonda = timeout422Probe.sondarPaginaTimeout422(
                            url,
                            nomeTabela,
                            campoData,
                            dataInicio,
                            dataFim,
                            paginaAtual,
                            config,
                            timeout,
                            templateId
                        );

                        paginasTimeout422ComLacuna++;
                        logger.warn(
                            "Timeout 422 na pagina {} de {}. Encerrando como resultado parcial com lacuna explicita; resultado_sonda={}. Nenhuma pagina sera assumida como final e nenhuma pagina sera pulada silenciosamente.",
                            paginaAtual,
                            tipoAmigavel,
                            resultadoSonda
                        );
                        interrompido = true;
                        motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.LACUNA_PAGINACAO_422;
                        totalPaginas = paginaAtual - 1;
                        break;
                    }
                    throw new RuntimeException("Erro HTTP " + resposta.statusCode() + " na pagina " + paginaAtual);
                }

                final List<T> registrosPagina;
                try {
                    final JsonNode raizJson = MapperUtil.sharedJson().readTree(resposta.body());
                    final JsonNode dadosNode = raizJson.has("data") ? raizJson.get("data") : raizJson;
                    final String idKey = ConstantesApiDataExport.obterCampoIdPrimario(config);

                    if (dadosNode != null && dadosNode.isArray()) {
                        if (dadosNode.size() == 0) {
                            pageAuditLogger.registrarPaginaVazia(
                                executionId,
                                runUuid,
                                templateId,
                                paginaAtual,
                                perInt,
                                janelaInicio,
                                janelaFim,
                                reqHash,
                                respHash,
                                idKey,
                                resposta.statusCode(),
                                (int) duracaoMs
                            );
                            logger.info("Fim da paginacao (pagina vazia)");
                            totalPaginas = paginaAtual - 1;
                            break;
                        }

                        pageAuditLogger.registrarPaginaComDados(
                            executionId,
                            runUuid,
                            templateId,
                            paginaAtual,
                            perInt,
                            janelaInicio,
                            janelaFim,
                            reqHash,
                            respHash,
                            idKey,
                            resposta.statusCode(),
                            (int) duracaoMs,
                            dadosNode
                        );
                        registrosPagina = MapperUtil.sharedJson().convertValue(dadosNode, typeReference);
                    } else {
                        pageAuditLogger.registrarPayloadInvalido(
                            executionId,
                            runUuid,
                            templateId,
                            paginaAtual,
                            perInt,
                            janelaInicio,
                            janelaFim,
                            reqHash,
                            respHash,
                            resposta.statusCode(),
                            (int) duracaoMs
                        );
                        final String tipoPayload = dadosNode == null ? "null" : dadosNode.getNodeType().name();
                        final String amostraPayload = httpExecutor.extrairAmostraPayload(resposta.body(), 400);
                        throw new IllegalStateException(
                            "Payload invalido na pagina " + paginaAtual
                                + ": esperado array, recebido " + tipoPayload
                                + " | resp_hash=" + respHash
                                + " | amostra=" + amostraPayload
                        );
                    }
                } catch (final Exception e) {
                    if (e instanceof final IllegalStateException illegalStateException) {
                        throw illegalStateException;
                    }
                    throw new RuntimeException("Erro ao parsear pagina " + paginaAtual, e);
                }

                logger.info("Pagina {}: {} registros parseados", paginaAtual, registrosPagina.size());
                resultadosFinais.addAll(registrosPagina);
                totalRegistrosProcessados += registrosPagina.size();
                paginationSupport.resetarEstadoFalhasTemplate(chaveTemplate);

                logger.info("Total acumulado: {} registros", totalRegistrosProcessados);
                if (paginaAtual % intervaloLogProgresso == 0) {
                    logger.info("Progresso: {} paginas processadas, {} registros", paginaAtual, totalRegistrosProcessados);
                }
                paginaAtual++;
            }

            paginationSupport.resetarEstadoFalhasTemplate(chaveTemplate);
            if (paginasTimeout422ComLacuna > 0) {
                logger.warn(
                    "Extracao de {} concluiu com {} lacuna(s) de paginacao por timeout 422.",
                    tipoAmigavel,
                    paginasTimeout422ComLacuna
                );
            }

            return interrompido
                ? ResultadoExtracao.incompleto(
                    resultadosFinais,
                    motivoInterrupcao != null ? motivoInterrupcao : ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS,
                    totalPaginas > 0 ? totalPaginas : (paginaAtual - 1),
                    totalRegistrosProcessados
                )
                : ResultadoExtracao.completo(
                    resultadosFinais,
                    totalPaginas > 0 ? totalPaginas : (paginaAtual - 1),
                    totalRegistrosProcessados
                );
        } catch (final RuntimeException e) {
            logger.error("ERRO CRITICO na extracao de {}: {}", tipoAmigavel, e.getMessage(), e);
            paginationSupport.incrementarContadorFalhas(chaveTemplate, tipoAmigavel);
            throw new RuntimeException("Falha na extracao de " + tipoAmigavel, e);
        }
    }

    private String obterNomeAmigavelTipo(final int templateId, final String nomeTabela) {
        return switch (templateId) {
            case 6399 -> "Manifestos";
            case 6906 -> "Cotacoes";
            case 8656 -> "Localizacoes de Carga / Fretes";
            case 8636 -> "Contas a Pagar";
            case 4924 -> "Faturas por Cliente";
            default -> switch (nomeTabela) {
                case "manifests" -> "Manifestos";
                case "quotes" -> "Cotacoes";
                case "freights" -> "Localizacoes de Carga / Fretes";
                case "accounting_debits" -> "Contas a Pagar";
                default -> "Dados";
            };
        };
    }

    private String selecionarMotivoInterrupcao(final String atual, final String candidato) {
        if (candidato == null || candidato.isBlank()) {
            return atual;
        }
        if (ResultadoExtracao.MotivoInterrupcao.ERRO_API.getCodigo().equals(candidato)
            || ResultadoExtracao.MotivoInterrupcao.CIRCUIT_BREAKER.getCodigo().equals(candidato)) {
            return candidato;
        }
        return (atual == null || atual.isBlank()) ? candidato : atual;
    }

}
