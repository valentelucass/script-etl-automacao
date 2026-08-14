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
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import br.com.extrator.integracao.constantes.ConstantesApiGraphQL;
import br.com.extrator.suporte.ThreadUtil;
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
    private final GraphQLPageFetcher pageFetcher;

    GraphQLPaginator(final Logger logger,
                     final int intervaloLogProgresso,
                     final int maxFalhasConsecutivas,
                     final Duration janelaReaberturaCircuito,
                     final Map<String, Integer> contadorFalhasConsecutivas,
                     final Set<String> entidadesComCircuitAberto,
                     final Map<String, Instant> circuitosAbertosDesde,
                     final GraphQLPageFetcher pageFetcher) {
        this.logger = logger;
        this.intervaloLogProgresso = intervaloLogProgresso;
        this.maxFalhasConsecutivas = maxFalhasConsecutivas;
        this.janelaReaberturaCircuito = janelaReaberturaCircuito;
        this.contadorFalhasConsecutivas = contadorFalhasConsecutivas;
        this.entidadesComCircuitAberto = entidadesComCircuitAberto;
        this.circuitosAbertosDesde = circuitosAbertosDesde;
        this.pageFetcher = pageFetcher;
    }

    <T> ResultadoExtracao<T> executarQueryPaginada(final String executionUuid,
                                                   final String query,
                                                   final String nomeEntidade,
                                                   final Map<String, Object> variaveis,
                                                   final Class<T> tipoClasse) {
        return executarQueryPaginada(executionUuid, query, nomeEntidade, variaveis, tipoClasse, null);
    }

    <T> ResultadoExtracao<T> executarQueryPaginada(final String executionUuid,
                                                   final String query,
                                                   final String nomeEntidade,
                                                   final Map<String, Object> variaveis,
                                                   final Class<T> tipoClasse,
                                                   final PageChunkConsumer<T> chunkConsumer) {
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

        final List<T> todasEntidades = chunkConsumer == null ? new ArrayList<>() : new ArrayList<>(0);
        String cursor = null;
        boolean hasNextPage = true;
        int paginaAtual = 1;
        int paginasProcessadas = 0;
        int totalRegistrosProcessados = 0;
        boolean interrompido = false;
        ResultadoExtracao.MotivoInterrupcao motivoInterrupcao = ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS;

        final int limitePaginasGeral = ConfigApi.obterLimitePaginasApiGraphQL();
        final String nomeEntidadeUsuarios = ConstantesApiGraphQL.obterNomeEntidadeApi(ConstantesEntidades.USUARIOS_SISTEMA);
        final boolean entidadeUsuarios = nomeEntidadeUsuarios.equals(nomeEntidade);
        final int limitePaginas = entidadeUsuarios
            ? Math.min(ConfigApi.obterLimitePaginasUsuariosGraphQL(), limitePaginasGeral)
            : limitePaginasGeral;
        final int maxRegistros = nomeEntidadeUsuarios.equals(nomeEntidade)
            ? ConfigApi.obterMaxRegistrosUsuariosGraphQL()
            : ConfigApi.obterMaxRegistrosGraphQL();
        if (entidadeUsuarios) {
            logger.info(
                "Limite configurado GraphQL aplicado para {}: max {} paginas por execucao",
                nomeEntidade,
                limitePaginas
            );
        }
        final int perInt = 100;
        Integer tamanhoPaginaEsperado = null;
        int tentativasAnomaliaPagina = 0;
        final int maxTentativasAnomalia = ConfigApi.obterMaxTentativasAnomaliaPaginacaoGraphQL();

        while (hasNextPage) {
            try {
                verificarInterrupcaoCooperativa(nomeEntidade, paginaAtual);
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

                final Map<String, Object> variaveisComCursor = new HashMap<>(
                    variaveis == null ? Map.of() : variaveis
                );
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

                final int registrosRecebidos = resposta.getEntidades().size();
                final String novoCursor = resposta.getEndCursor();
                final AnomaliaPaginacao anomalia = detectarAnomaliaPaginacao(
                    nomeEntidade,
                    paginaAtual,
                    cursor,
                    novoCursor,
                    resposta.getHasNextPage(),
                    registrosRecebidos,
                    tamanhoPaginaEsperado,
                    perInt
                );
                if (anomalia != null) {
                    if (tentativasAnomaliaPagina < maxTentativasAnomalia) {
                        tentativasAnomaliaPagina++;
                        logger.warn(
                            "Anomalia de paginacao em {} na pagina {}. Retentativa curta {}/{} antes de marcar incompleto. detalhe={}",
                            nomeEntidade,
                            paginaAtual,
                            tentativasAnomaliaPagina,
                            maxTentativasAnomalia,
                            anomalia.mensagem()
                        );
                        aguardarRetentativaAnomalia();
                        continue;
                    }
                    interrompido = true;
                    motivoInterrupcao = anomalia.motivo();
                    logger.warn(
                        "Anomalia de paginacao confirmada em {} na pagina {} apos {} retentativa(s). detalhe={}",
                        nomeEntidade,
                        paginaAtual,
                        tentativasAnomaliaPagina,
                        anomalia.mensagem()
                    );
                    break;
                }
                tentativasAnomaliaPagina = 0;

                if (resposta.getHasNextPage() && registrosRecebidos > 0) {
                    tamanhoPaginaEsperado = tamanhoPaginaEsperado == null
                        ? registrosRecebidos
                        : Math.max(tamanhoPaginaEsperado, registrosRecebidos);
                }
                registrarAnomaliaObservavelPaginaCurta(
                    nomeEntidade,
                    paginaAtual,
                    registrosRecebidos,
                    tamanhoPaginaEsperado,
                    resposta.getHasNextPage()
                );

                if (chunkConsumer == null) {
                    todasEntidades.addAll(resposta.getEntidades());
                } else {
                    processarChunkPagina(nomeEntidade, paginaAtual, resposta.getEntidades(), chunkConsumer);
                }
                totalRegistrosProcessados += registrosRecebidos;
                paginasProcessadas++;

                resetarEstadoFalhas(chaveEntidade);

                hasNextPage = resposta.getHasNextPage();
                cursor = novoCursor;
                paginaAtual++;
            } catch (final PageChunkProcessingException e) {
                throw e;
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

    private AnomaliaPaginacao detectarAnomaliaPaginacao(final String nomeEntidade,
                                                        final int paginaAtual,
                                                        final String cursorAtual,
                                                        final String novoCursor,
                                                        final boolean hasNextPage,
                                                        final int registrosRecebidos,
                                                        final Integer tamanhoPaginaEsperado,
                                                        final int perInt) {
        if (hasNextPage && (novoCursor == null || novoCursor.isBlank())) {
            return new AnomaliaPaginacao(
                ResultadoExtracao.MotivoInterrupcao.PAGINACAO_INCONSISTENTE,
                "hasNextPage=true sem cursor de continuidade"
            );
        }

        if (novoCursor != null && cursorAtual != null && novoCursor.equals(cursorAtual) && hasNextPage) {
            return new AnomaliaPaginacao(
                ResultadoExtracao.MotivoInterrupcao.LOOP_DETECTADO,
                "cursor repetido com hasNextPage=true"
            );
        }

        if (registrosRecebidos == 0 && hasNextPage) {
            return new AnomaliaPaginacao(
                ResultadoExtracao.MotivoInterrupcao.PAGINACAO_INCONSISTENTE,
                "pagina vazia com hasNextPage=true"
            );
        }

        return null;
    }

    private <T> void processarChunkPagina(final String nomeEntidade,
                                          final int paginaAtual,
                                          final List<T> registrosPagina,
                                          final PageChunkConsumer<T> chunkConsumer) {
        if (registrosPagina == null || registrosPagina.isEmpty()) {
            return;
        }
        try {
            chunkConsumer.process(registrosPagina);
        } catch (final Exception e) {
            throw new PageChunkProcessingException(
                "Falha ao processar chunk GraphQL de " + nomeEntidade + " na pagina " + paginaAtual,
                e
            );
        } finally {
            limparReferenciasPagina(registrosPagina);
        }
    }

    private void limparReferenciasPagina(final List<?> registrosPagina) {
        try {
            registrosPagina.clear();
        } catch (final UnsupportedOperationException ignored) {
            logger.debug("Lista de pagina GraphQL imutavel; referencias serao liberadas pelo escopo local.");
        }
    }

    private void aguardarRetentativaAnomalia() {
        final long delayMs = ConfigApi.obterDelayRetryAnomaliaPaginacaoGraphQLMs();
        if (delayMs <= 0L) {
            return;
        }
        try {
            ThreadUtil.aguardar(delayMs);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread interrompida durante retentativa de anomalia de paginacao GraphQL", e);
        }
    }

    private void registrarAnomaliaObservavelPaginaCurta(final String nomeEntidade,
                                                        final int paginaAtual,
                                                        final int registrosRecebidos,
                                                        final Integer tamanhoPaginaEsperado,
                                                        final boolean hasNextPage) {
        if (!hasNextPage || tamanhoPaginaEsperado == null || registrosRecebidos >= tamanhoPaginaEsperado) {
            return;
        }
        logger.warn(
            "Anomalia observavel de paginacao GraphQL em {} pagina {}: pagina curta {} (< {}) com hasNextPage=true. Extracao seguira porque cursor continua avancando.",
            nomeEntidade,
            paginaAtual,
            registrosRecebidos,
            tamanhoPaginaEsperado
        );
    }

    private void verificarInterrupcaoCooperativa(final String nomeEntidade, final int paginaAtual) {
        if (!Thread.currentThread().isInterrupted()) {
            return;
        }
        throw new IllegalStateException(
            "Thread interrompida durante paginacao GraphQL de " + nomeEntidade + " na pagina " + paginaAtual
        );
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

    private record AnomaliaPaginacao(ResultadoExtracao.MotivoInterrupcao motivo, String mensagem) {
    }
}
