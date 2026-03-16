package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/GraphQLPaginator.java
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import br.com.extrator.integracao.constantes.ConstantesApiGraphQL;
import br.com.extrator.suporte.configuracao.ConfigApi;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

final class GraphQLPaginator {
    private final Logger logger;
    private final int intervaloLogProgresso;
    private final int maxFalhasConsecutivas;
    private final Duration janelaReaberturaCircuito;
    private final Map<String, Integer> contadorFalhasConsecutivas;
    private final Set<String> entidadesComCircuitAberto;
    private final Map<String, Instant> circuitosAbertosDesde;
    private final GraphQLPageAuditLogger pageAuditLogger;
    private final GraphQLPageFetcher pageFetcher;

    GraphQLPaginator(final Logger logger,
                     final int intervaloLogProgresso,
                     final int maxFalhasConsecutivas,
                     final Duration janelaReaberturaCircuito,
                     final Map<String, Integer> contadorFalhasConsecutivas,
                     final Set<String> entidadesComCircuitAberto,
                     final Map<String, Instant> circuitosAbertosDesde,
                     final GraphQLPageAuditLogger pageAuditLogger,
                     final GraphQLPageFetcher pageFetcher) {
        this.logger = logger;
        this.intervaloLogProgresso = intervaloLogProgresso;
        this.maxFalhasConsecutivas = maxFalhasConsecutivas;
        this.janelaReaberturaCircuito = janelaReaberturaCircuito;
        this.contadorFalhasConsecutivas = contadorFalhasConsecutivas;
        this.entidadesComCircuitAberto = entidadesComCircuitAberto;
        this.circuitosAbertosDesde = circuitosAbertosDesde;
        this.pageAuditLogger = pageAuditLogger;
        this.pageFetcher = pageFetcher;
    }

    <T> ResultadoExtracao<T> executarQueryPaginada(final String executionUuid,
                                                   final String query,
                                                   final String nomeEntidade,
                                                   final Map<String, Object> variaveis,
                                                   final Class<T> tipoClasse) {
        final String chaveEntidade = "GraphQL-" + nomeEntidade;
        if (isCircuitBreakerAtivo(chaveEntidade)) {
            logger.warn("Circuit breaker ativo para entidade {}", nomeEntidade);
            return ResultadoExtracao.incompleto(
                new ArrayList<>(),
                ResultadoExtracao.MotivoInterrupcao.CIRCUIT_BREAKER,
                0,
                0
            );
        }

        logger.info("Executando query GraphQL paginada para entidade: {}", nomeEntidade);

        final List<T> todasEntidades = new ArrayList<>();
        String cursor = null;
        boolean hasNextPage = true;
        int paginaAtual = 1;
        int paginasProcessadas = 0;
        int totalRegistrosProcessados = 0;
        boolean interrompido = false;
        ResultadoExtracao.MotivoInterrupcao motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS;

        final int limitePaginasGeral = ConfigApi.obterLimitePaginasApiGraphQL();
        final String nomeEntidadeFaturasGraphQL = ConstantesApiGraphQL.obterNomeEntidadeApi(ConstantesEntidades.FATURAS_GRAPHQL);
        final String nomeEntidadeUsuarios = ConstantesApiGraphQL.obterNomeEntidadeApi(ConstantesEntidades.USUARIOS_SISTEMA);
        final int limitePaginas = nomeEntidadeFaturasGraphQL.equals(nomeEntidade)
            ? ConfigApi.obterLimitePaginasFaturasGraphQL()
            : nomeEntidadeUsuarios.equals(nomeEntidade)
                ? ConfigApi.obterLimitePaginasUsuariosGraphQL()
            : limitePaginasGeral;
        final int maxRegistros = nomeEntidadeUsuarios.equals(nomeEntidade)
            ? ConfigApi.obterMaxRegistrosUsuariosGraphQL()
            : ConfigApi.obterMaxRegistrosGraphQL();
        final boolean auditar = nomeEntidadeFaturasGraphQL.equals(nomeEntidade);
        final String runUuid = auditar ? java.util.UUID.randomUUID().toString() : null;
        final int perInt = 100;
        Integer tamanhoPaginaEsperado = null;

        LocalDate janelaInicio = null;
        LocalDate janelaFim = null;
        try {
            final Object paramsObj = variaveis != null ? variaveis.get("params") : null;
            if (paramsObj instanceof final Map<?, ?> m) {
                final Object v1 = m.get("issueDate");
                final Object v2 = m.get("dueDate");
                final Object v3 = m.get("originalDueDate");
                final String dataStr = v1 != null ? v1.toString() : (v2 != null ? v2.toString() : (v3 != null ? v3.toString() : null));
                if (dataStr != null && !dataStr.isBlank()) {
                    final LocalDate d = LocalDate.parse(dataStr);
                    janelaInicio = d;
                    janelaFim = d;
                }
            }
        } catch (final RuntimeException ignored) {
            logger.debug("Falha ao inferir janela de auditoria da query GraphQL: {}", ignored.getMessage());
        }

        while (hasNextPage) {
            try {
                if (paginaAtual > limitePaginas) {
                    logger.warn("Limite de paginas atingido para {}: {}", nomeEntidade, limitePaginas);
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS;
                    break;
                }

                if (totalRegistrosProcessados >= maxRegistros) {
                    logger.warn("Limite de registros atingido para {}: {}", nomeEntidade, maxRegistros);
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.LIMITE_REGISTROS;
                    break;
                }

                if (paginaAtual % intervaloLogProgresso == 0) {
                    logger.info(
                        "Progresso GraphQL {}: pagina {} com {} registros processados",
                        nomeEntidade,
                        paginaAtual,
                        totalRegistrosProcessados
                    );
                }

                final Map<String, Object> variaveisComCursor = new java.util.HashMap<>(variaveis);
                if (cursor != null) {
                    variaveisComCursor.put("after", cursor);
                }

                final PaginatedGraphQLResponse<T> resposta =
                    pageFetcher.fetch(query, nomeEntidade, variaveisComCursor, tipoClasse);

                if (resposta.isErroApi()) {
                    logger.error(
                        "Falha de API GraphQL para {} na pagina {}: {}",
                        nomeEntidade,
                        paginaAtual,
                        resposta.getErroDetalhe() == null ? "SEM_DETALHE" : resposta.getErroDetalhe()
                    );
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.ERRO_API;
                    break;
                }

                todasEntidades.addAll(resposta.getEntidades());
                totalRegistrosProcessados += resposta.getEntidades().size();
                paginasProcessadas++;

                if (auditar && executionUuid != null && runUuid != null) {
                    pageAuditLogger.registrarPagina(
                        executionUuid,
                        runUuid,
                        paginaAtual,
                        perInt,
                        janelaInicio,
                        janelaFim,
                        resposta,
                        tipoClasse
                    );
                }

                resetarEstadoFalhas(chaveEntidade);

                final int registrosRecebidos = resposta.getEntidades().size();
                final String novoCursor = resposta.getEndCursor();
                if (resposta.getHasNextPage() && (novoCursor == null || novoCursor.isBlank())) {
                    logger.warn("Paginacao inconsistente em {}: hasNextPage=true sem cursor de continuidade.", nomeEntidade);
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.PAGINACAO_INCONSISTENTE;
                    break;
                }

                if (novoCursor != null && cursor != null && novoCursor.equals(cursor) && resposta.getHasNextPage()) {
                    final int limiarPaginaCurta = tamanhoPaginaEsperado != null ? tamanhoPaginaEsperado : perInt;
                    if (registrosRecebidos < limiarPaginaCurta) {
                        logger.warn(
                            "Cursor repetido em {} com pagina curta ({} < {}). Marcando extracao como inconsistente.",
                            nomeEntidade,
                            registrosRecebidos,
                            limiarPaginaCurta
                        );
                        interrompido = true;
                        motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.PAGINACAO_INCONSISTENTE;
                        break;
                    }
                    logger.warn("Cursor repetido com hasNextPage=true em {}. Interrompendo para evitar loop.", nomeEntidade);
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.LOOP_DETECTADO;
                    break;
                }

                if (resposta.getEntidades().isEmpty() && resposta.getHasNextPage()) {
                    logger.warn("Pagina vazia com hasNextPage=true em {}. Interrompendo por inconsistencia de paginacao.", nomeEntidade);
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.PAGINACAO_INCONSISTENTE;
                    break;
                }

                if (resposta.getHasNextPage() && tamanhoPaginaEsperado != null && registrosRecebidos < tamanhoPaginaEsperado) {
                    logger.warn(
                        "Paginacao inconsistente em {}: pagina {} retornou {} registros (< {}) com hasNextPage=true.",
                        nomeEntidade,
                        paginaAtual,
                        registrosRecebidos,
                        tamanhoPaginaEsperado
                    );
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.PAGINACAO_INCONSISTENTE;
                    break;
                }

                if (resposta.getHasNextPage() && registrosRecebidos > 0) {
                    tamanhoPaginaEsperado = tamanhoPaginaEsperado == null
                        ? registrosRecebidos
                        : Math.max(tamanhoPaginaEsperado, registrosRecebidos);
                }

                hasNextPage = resposta.getHasNextPage();
                cursor = novoCursor;
                paginaAtual++;
            } catch (final RuntimeException e) {
                logger.error(
                    "Erro ao executar query GraphQL para entidade {} pagina {}: {}",
                    nomeEntidade,
                    paginaAtual,
                    e.getMessage(),
                    e
                );
                incrementarContadorFalhas(chaveEntidade, nomeEntidade);
                interrompido = true;
                motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.ERRO_API;
                break;
            }
        }

        if (interrompido) {
            logger.warn(
                "Query GraphQL incompleta para {}: {} registros em {} paginas",
                nomeEntidade,
                totalRegistrosProcessados,
                paginasProcessadas
            );
            return ResultadoExtracao.incompleto(
                todasEntidades,
                motivoInterrupcao,
                paginasProcessadas,
                totalRegistrosProcessados
            );
        }
        if (todasEntidades.isEmpty()) {
            logger.info("Query GraphQL concluida para {} sem registros", nomeEntidade);
        } else {
            logger.info(
                "Query GraphQL completa para {}: {} registros em {} paginas",
                nomeEntidade,
                totalRegistrosProcessados,
                paginasProcessadas
            );
        }
        return ResultadoExtracao.completo(todasEntidades, paginasProcessadas, totalRegistrosProcessados);
    }

    private void incrementarContadorFalhas(final String chaveEntidade, final String nomeEntidade) {
        final int falhas = contadorFalhasConsecutivas.getOrDefault(chaveEntidade, 0) + 1;
        contadorFalhasConsecutivas.put(chaveEntidade, falhas);
        if (falhas >= maxFalhasConsecutivas) {
            entidadesComCircuitAberto.add(chaveEntidade);
            circuitosAbertosDesde.put(chaveEntidade, Instant.now());
            logger.error(
                "ðŸš¨ CIRCUIT BREAKER ATIVADO - Entidade {} ({}): {} falhas consecutivas. Entidade temporariamente desabilitada.",
                chaveEntidade,
                nomeEntidade,
                falhas
            );
        } else {
            logger.warn("âš ï¸ Falha {}/{} para entidade {} ({})", falhas, maxFalhasConsecutivas, chaveEntidade, nomeEntidade);
        }
    }

    private boolean isCircuitBreakerAtivo(final String chaveEntidade) {
        if (!entidadesComCircuitAberto.contains(chaveEntidade)) {
            return false;
        }
        final Instant abertoDesde = circuitosAbertosDesde.get(chaveEntidade);
        if (abertoDesde == null || abertoDesde.plus(janelaReaberturaCircuito).isAfter(Instant.now())) {
            return true;
        }
        logger.info("Circuit breaker reaberto automaticamente para {}", chaveEntidade);
        resetarEstadoFalhas(chaveEntidade);
        return false;
    }

    private void resetarEstadoFalhas(final String chaveEntidade) {
        contadorFalhasConsecutivas.put(chaveEntidade, 0);
        entidadesComCircuitAberto.remove(chaveEntidade);
        circuitosAbertosDesde.remove(chaveEntidade);
    }
}
