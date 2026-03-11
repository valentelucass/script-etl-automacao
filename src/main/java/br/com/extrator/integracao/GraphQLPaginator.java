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
    private final Map<String, Integer> contadorFalhasConsecutivas;
    private final Set<String> entidadesComCircuitAberto;
    private final GraphQLPageAuditLogger pageAuditLogger;
    private final GraphQLHttpExecutor httpExecutor;

    GraphQLPaginator(final Logger logger,
                     final int intervaloLogProgresso,
                     final int maxFalhasConsecutivas,
                     final Map<String, Integer> contadorFalhasConsecutivas,
                     final Set<String> entidadesComCircuitAberto,
                     final GraphQLPageAuditLogger pageAuditLogger,
                     final GraphQLHttpExecutor httpExecutor) {
        this.logger = logger;
        this.intervaloLogProgresso = intervaloLogProgresso;
        this.maxFalhasConsecutivas = maxFalhasConsecutivas;
        this.contadorFalhasConsecutivas = contadorFalhasConsecutivas;
        this.entidadesComCircuitAberto = entidadesComCircuitAberto;
        this.pageAuditLogger = pageAuditLogger;
        this.httpExecutor = httpExecutor;
    }

    <T> ResultadoExtracao<T> executarQueryPaginada(final String executionUuid,
                                                   final String query,
                                                   final String nomeEntidade,
                                                   final Map<String, Object> variaveis,
                                                   final Class<T> tipoClasse) {
        final String chaveEntidade = "GraphQL-" + nomeEntidade;
        if (entidadesComCircuitAberto.contains(chaveEntidade)) {
            logger.warn("Circuit breaker ativo para entidade {}", nomeEntidade);
            return ResultadoExtracao.incompleto(new ArrayList<>(), ResultadoExtracao.MotivoInterrupcao.CIRCUIT_BREAKER, 0, 0);
        }

        logger.info("Executando query GraphQL paginada para entidade: {}", nomeEntidade);

        final List<T> todasEntidades = new ArrayList<>();
        String cursor = null;
        boolean hasNextPage = true;
        int paginaAtual = 1;
        int totalRegistrosProcessados = 0;
        boolean interrompido = false;
        ResultadoExtracao.MotivoInterrupcao motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS;

        final int limitePaginasGeral = ConfigApi.obterLimitePaginasApiGraphQL();
        final String nomeEntidadeFaturasGraphQL = ConstantesApiGraphQL.obterNomeEntidadeApi(ConstantesEntidades.FATURAS_GRAPHQL);
        final int limitePaginas = nomeEntidadeFaturasGraphQL.equals(nomeEntidade)
                ? ConfigApi.obterLimitePaginasFaturasGraphQL()
                : limitePaginasGeral;
        final boolean auditar = nomeEntidadeFaturasGraphQL.equals(nomeEntidade);
        final String runUuid = auditar ? java.util.UUID.randomUUID().toString() : null;
        final int perInt = 100;

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

                final int maxRegistros = ConfigApi.obterMaxRegistrosGraphQL();
                if (totalRegistrosProcessados >= maxRegistros) {
                    logger.warn("Limite de registros atingido para {}: {}", nomeEntidade, maxRegistros);
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.LIMITE_REGISTROS;
                    break;
                }

                if (paginaAtual % intervaloLogProgresso == 0) {
                    logger.info("Progresso GraphQL {}: pagina {} com {} registros processados", nomeEntidade, paginaAtual, totalRegistrosProcessados);
                }

                final Map<String, Object> variaveisComCursor = new java.util.HashMap<>(variaveis);
                if (cursor != null) {
                    variaveisComCursor.put("after", cursor);
                }

                final PaginatedGraphQLResponse<T> resposta =
                    httpExecutor.executarQueryGraphQLTipado(query, nomeEntidade, variaveisComCursor, tipoClasse);

                if (resposta.isErroApi()) {
                    logger.error("Falha de API GraphQL para {} na pagina {}: {}", nomeEntidade, paginaAtual, resposta.getErroDetalhe() == null ? "SEM_DETALHE" : resposta.getErroDetalhe());
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.ERRO_API;
                    break;
                }

                todasEntidades.addAll(resposta.getEntidades());
                totalRegistrosProcessados += resposta.getEntidades().size();

                if (auditar && executionUuid != null && runUuid != null) {
                    pageAuditLogger.registrarPagina(executionUuid, runUuid, paginaAtual, perInt, janelaInicio, janelaFim, resposta, tipoClasse);
                }

                contadorFalhasConsecutivas.put(chaveEntidade, 0);

                final String novoCursor = resposta.getEndCursor();
                if (novoCursor != null && cursor != null && novoCursor.equals(cursor) && resposta.getHasNextPage()) {
                    final int registrosEsperados = perInt;
                    final int registrosRecebidos = resposta.getEntidades().size();
                    if (registrosRecebidos < registrosEsperados) {
                        logger.warn("Cursor repetido em {} com pagina incompleta ({} < {})", nomeEntidade, registrosRecebidos, registrosEsperados);
                        break;
                    }
                    logger.warn("Cursor repetido com hasNextPage=true em {}. Interrompendo para evitar loop.", nomeEntidade);
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.LOOP_DETECTADO;
                    break;
                }

                if (resposta.getEntidades().isEmpty() && resposta.getHasNextPage()) {
                    logger.warn("Pagina vazia com hasNextPage=true em {}. Interrompendo para evitar loop.", nomeEntidade);
                    interrompido = true;
                    motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.PAGINA_VAZIA;
                    break;
                }

                hasNextPage = resposta.getHasNextPage();
                cursor = novoCursor;
                paginaAtual++;
            } catch (final RuntimeException e) {
                logger.error("Erro ao executar query GraphQL para entidade {} pagina {}: {}", nomeEntidade, paginaAtual, e.getMessage(), e);
                incrementarContadorFalhas(chaveEntidade, nomeEntidade);
                interrompido = true;
                motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.ERRO_API;
                break;
            }
        }

        if (interrompido) {
            logger.warn("Query GraphQL incompleta para {}: {} registros em {} paginas", nomeEntidade, totalRegistrosProcessados, paginaAtual - 1);
            return ResultadoExtracao.incompleto(todasEntidades, motivoInterrupcao, paginaAtual - 1, totalRegistrosProcessados);
        }
        if (todasEntidades.isEmpty()) {
            logger.info("Query GraphQL concluida para {} sem registros", nomeEntidade);
        } else {
            logger.info("Query GraphQL completa para {}: {} registros em {} paginas", nomeEntidade, totalRegistrosProcessados, paginaAtual - 1);
        }
        return ResultadoExtracao.completo(todasEntidades, paginaAtual - 1, totalRegistrosProcessados);
    }

    private void incrementarContadorFalhas(final String chaveEntidade, final String nomeEntidade) {
        final int falhas = contadorFalhasConsecutivas.getOrDefault(chaveEntidade, 0) + 1;
        contadorFalhasConsecutivas.put(chaveEntidade, falhas);
        if (falhas >= maxFalhasConsecutivas) {
            entidadesComCircuitAberto.add(chaveEntidade);
            logger.error("🚨 CIRCUIT BREAKER ATIVADO - Entidade {} ({}): {} falhas consecutivas. Entidade temporariamente desabilitada.", chaveEntidade, nomeEntidade, falhas);
        } else {
            logger.warn("⚠️ Falha {}/{} para entidade {} ({})", falhas, maxFalhasConsecutivas, chaveEntidade, nomeEntidade);
        }
    }
}
