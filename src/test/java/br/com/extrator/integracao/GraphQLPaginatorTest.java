package br.com.extrator.integracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import br.com.extrator.suporte.configuracao.ConfigApi;

class GraphQLPaginatorTest {

    @Test
    void deveMarcarIncompletoQuandoCursorRepeteComPaginaCurta() {
        final AtomicInteger callCount = new AtomicInteger();
        final GraphQLPaginator paginator = new GraphQLPaginator(
            LoggerFactory.getLogger(GraphQLPaginatorTest.class),
            10,
            3,
            Duration.ofMinutes(10),
            new HashMap<>(),
            new HashSet<>(),
            new HashMap<>(),
            new GraphQLPageFetcher() {
                @Override
                public <T> PaginatedGraphQLResponse<T> fetch(
                    final String query,
                    final String nomeEntidade,
                    final Map<String, Object> variaveis,
                    final Class<T> tipoClasse
                ) {
                    if (callCount.getAndIncrement() == 0) {
                        return cast(java.util.stream.IntStream.rangeClosed(1, 20).boxed().toList(), true, "cursor-1");
                    }
                    return cast(List.of(21, 22, 23, 24, 25), true, "cursor-1");
                }
            }
        );

        final ResultadoExtracao<Integer> resultado = paginator.executarQueryPaginada(
            "exec-1",
            "query",
            "freights",
            Map.of("params", Map.of("serviceAt", "2026-03-09 - 2026-03-09")),
            Integer.class
        );

        assertFalse(resultado.isCompleto());
        assertEquals(ResultadoExtracao.MotivoInterrupcao.LOOP_DETECTADO.getCodigo(), resultado.getMotivoInterrupcao());
        assertEquals(1, resultado.getPaginasProcessadas());
        assertEquals(20, resultado.getDados().size());
        assertEquals(
            ConfigApi.obterMaxTentativasAnomaliaPaginacaoGraphQL() + 2,
            callCount.get(),
            "Cursor repetido deve receber as retentativas configuradas antes de marcar incompleto."
        );
    }

    @Test
    void devePermitirPaginaCurtaQuandoCursorContinuaAvancando() {
        final AtomicInteger callCount = new AtomicInteger();
        final GraphQLPaginator paginator = new GraphQLPaginator(
            LoggerFactory.getLogger(GraphQLPaginatorTest.class),
            10,
            3,
            Duration.ofMinutes(10),
            new HashMap<>(),
            new HashSet<>(),
            new HashMap<>(),
            new GraphQLPageFetcher() {
                @Override
                public <T> PaginatedGraphQLResponse<T> fetch(
                    final String query,
                    final String nomeEntidade,
                    final Map<String, Object> variaveis,
                    final Class<T> tipoClasse
                ) {
                    final int chamada = callCount.getAndIncrement();
                    if (chamada == 0) {
                        return cast(java.util.stream.IntStream.rangeClosed(1, 20).boxed().toList(), true, "cursor-2");
                    }
                    if (chamada == 1) {
                        return cast(List.of(21, 22, 23, 24, 25), true, "cursor-3");
                    }
                    return cast(List.of(26, 27, 28), false, "cursor-4");
                }
            }
        );

        final ResultadoExtracao<Integer> resultado = paginator.executarQueryPaginada(
            "exec-2",
            "query",
            "freights",
            Map.of(),
            Integer.class
        );

        assertTrue(resultado.isCompleto());
        assertEquals(3, resultado.getPaginasProcessadas());
        assertEquals(28, resultado.getDados().size());
        assertEquals(3, callCount.get(), "Pagina curta com cursor valido deve permitir continuar a extracao.");
    }

    @Test
    void deveProcessarChunkPorPaginaSemMaterializarResultadoCompleto() {
        final AtomicInteger callCount = new AtomicInteger();
        final List<Integer> salvos = new ArrayList<>();
        final List<List<Integer>> referenciasChunks = new ArrayList<>();
        final GraphQLPaginator paginator = new GraphQLPaginator(
            LoggerFactory.getLogger(GraphQLPaginatorTest.class),
            10,
            3,
            Duration.ofMinutes(10),
            new HashMap<>(),
            new HashSet<>(),
            new HashMap<>(),
            new GraphQLPageFetcher() {
                @Override
                public <T> PaginatedGraphQLResponse<T> fetch(
                    final String query,
                    final String nomeEntidade,
                    final Map<String, Object> variaveis,
                    final Class<T> tipoClasse
                ) {
                    final int chamada = callCount.getAndIncrement();
                    if (chamada == 0) {
                        return cast(new ArrayList<>(List.of(1, 2)), true, "cursor-1");
                    }
                    return cast(new ArrayList<>(List.of(3)), false, "cursor-2");
                }
            }
        );

        final ResultadoExtracao<Integer> resultado = paginator.executarQueryPaginada(
            "exec-chunked",
            "query",
            "freights",
            Map.of(),
            Integer.class,
            chunk -> {
                salvos.addAll(chunk);
                referenciasChunks.add(chunk);
            }
        );

        assertTrue(resultado.isCompleto());
        assertEquals(2, resultado.getPaginasProcessadas());
        assertEquals(3, resultado.getRegistrosExtraidos());
        assertEquals(0, resultado.getDados().size(), "Resultado chunked nao deve reter DTOs acumulados.");
        assertEquals(List.of(1, 2, 3), salvos);
        assertTrue(
            referenciasChunks.stream().allMatch(List::isEmpty),
            "Depois do commit do chunk, o paginador deve limpar a lista da pagina."
        );
    }

    @Test
    void deveRecuperarQuandoAnomaliaDePaginacaoEhTransitoria() {
        final AtomicInteger callCount = new AtomicInteger();
        final GraphQLPaginator paginator = new GraphQLPaginator(
            LoggerFactory.getLogger(GraphQLPaginatorTest.class),
            10,
            3,
            Duration.ofMinutes(10),
            new HashMap<>(),
            new HashSet<>(),
            new HashMap<>(),
            new GraphQLPageFetcher() {
                @Override
                public <T> PaginatedGraphQLResponse<T> fetch(
                    final String query,
                    final String nomeEntidade,
                    final Map<String, Object> variaveis,
                    final Class<T> tipoClasse
                ) {
                    final int chamada = callCount.getAndIncrement();
                    if (chamada == 0) {
                        return cast(java.util.stream.IntStream.rangeClosed(1, 20).boxed().toList(), true, "cursor-1");
                    }
                    if (chamada == 1) {
                        return cast(List.of(21, 22, 23, 24, 25), true, "cursor-1");
                    }
                    return cast(java.util.stream.IntStream.rangeClosed(21, 40).boxed().toList(), false, "cursor-2");
                }
            }
        );

        final ResultadoExtracao<Integer> resultado = paginator.executarQueryPaginada(
            "exec-transient",
            "query",
            "freights",
            Map.of(),
            Integer.class
        );

        assertTrue(resultado.isCompleto());
        assertEquals(2, resultado.getPaginasProcessadas());
        assertEquals(40, resultado.getDados().size());
        assertEquals(3, callCount.get());
    }

    @Test
    void deveAceitarVariaveisNulasSemLancarNpe() {
        final GraphQLPaginator paginator = new GraphQLPaginator(
            LoggerFactory.getLogger(GraphQLPaginatorTest.class),
            10,
            3,
            Duration.ofMinutes(10),
            new HashMap<>(),
            new HashSet<>(),
            new HashMap<>(),
            new GraphQLPageFetcher() {
                @Override
                public <T> PaginatedGraphQLResponse<T> fetch(
                    final String query,
                    final String nomeEntidade,
                    final Map<String, Object> variaveis,
                    final Class<T> tipoClasse
                ) {
                    assertTrue(variaveis.isEmpty());
                    return cast(List.of(1, 2, 3), false, "cursor-final");
                }
            }
        );

        final ResultadoExtracao<Integer> resultado = paginator.executarQueryPaginada(
            "exec-null",
            "query",
            "freights",
            Map.of(),
            Integer.class
        );

        assertTrue(resultado.isCompleto());
        assertEquals(3, resultado.getDados().size());
    }

    @Test
    void deveReabrirCircuitoAposJanelaConfigurada() {
        final Map<String, Instant> circuitosAbertosDesde = new HashMap<>();
        final Set<String> entidadesComCircuitoAberto = new HashSet<>();
        final String chaveEntidade = "GraphQL-freights";
        entidadesComCircuitoAberto.add(chaveEntidade);
        circuitosAbertosDesde.put(chaveEntidade, Instant.now().minus(Duration.ofMinutes(11)));

        final GraphQLPaginator paginator = new GraphQLPaginator(
            LoggerFactory.getLogger(GraphQLPaginatorTest.class),
            10,
            3,
            Duration.ofMinutes(10),
            new HashMap<>(),
            entidadesComCircuitoAberto,
            circuitosAbertosDesde,
            new GraphQLPageFetcher() {
                @Override
                public <T> PaginatedGraphQLResponse<T> fetch(
                    final String query,
                    final String nomeEntidade,
                    final Map<String, Object> variaveis,
                    final Class<T> tipoClasse
                ) {
                    return cast(List.of(1, 2, 3), false, "cursor-final");
                }
            }
        );

        final ResultadoExtracao<Integer> resultado = paginator.executarQueryPaginada(
            "exec-3",
            "query",
            "freights",
            Map.of(),
            Integer.class
        );

        assertTrue(resultado.isCompleto());
        assertEquals(1, resultado.getPaginasProcessadas());
        assertFalse(entidadesComCircuitoAberto.contains(chaveEntidade));
    }

    @Test
    void deveMarcarIncompletoQuandoApiFalhaNoMeioDaPaginacao() {
        final AtomicInteger callCount = new AtomicInteger();
        final GraphQLPaginator paginator = new GraphQLPaginator(
            LoggerFactory.getLogger(GraphQLPaginatorTest.class),
            10,
            3,
            Duration.ofMinutes(10),
            new HashMap<>(),
            new HashSet<>(),
            new HashMap<>(),
            new GraphQLPageFetcher() {
                @Override
                public <T> PaginatedGraphQLResponse<T> fetch(
                    final String query,
                    final String nomeEntidade,
                    final Map<String, Object> variaveis,
                    final Class<T> tipoClasse
                ) {
                    if (callCount.getAndIncrement() == 0) {
                        return cast(java.util.stream.IntStream.rangeClosed(1, 100).boxed().toList(), true, "cursor-ok");
                    }
                    throw new IllegalStateException("api down");
                }
            }
        );

        final ResultadoExtracao<Integer> resultado = paginator.executarQueryPaginada(
            "exec-4",
            "query",
            "freights",
            Map.of(),
            Integer.class
        );

        assertFalse(resultado.isCompleto());
        assertEquals(ResultadoExtracao.MotivoInterrupcao.ERRO_API.getCodigo(), resultado.getMotivoInterrupcao());
        assertEquals(1, resultado.getPaginasProcessadas());
        assertEquals(100, resultado.getDados().size());
    }

    @Test
    void deveAceitarPaginacaoQuandoApiUsaLimiteEfetivoMenorQueFirstSolicitado() {
        final AtomicInteger callCount = new AtomicInteger();
        final GraphQLPaginator paginator = new GraphQLPaginator(
            LoggerFactory.getLogger(GraphQLPaginatorTest.class),
            10,
            3,
            Duration.ofMinutes(10),
            new HashMap<>(),
            new HashSet<>(),
            new HashMap<>(),
            new GraphQLPageFetcher() {
                @Override
                public <T> PaginatedGraphQLResponse<T> fetch(
                    final String query,
                    final String nomeEntidade,
                    final Map<String, Object> variaveis,
                    final Class<T> tipoClasse
                ) {
                    if (callCount.getAndIncrement() == 0) {
                        return cast(java.util.stream.IntStream.rangeClosed(1, 20).boxed().toList(), true, "cursor-20");
                    }
                    return cast(java.util.stream.IntStream.rangeClosed(21, 40).boxed().toList(), false, "cursor-40");
                }
            }
        );

        final ResultadoExtracao<Integer> resultado = paginator.executarQueryPaginada(
            "exec-5",
            "query",
            "pick",
            Map.of(),
            Integer.class
        );

        assertTrue(resultado.isCompleto());
        assertEquals(2, resultado.getPaginasProcessadas());
        assertEquals(40, resultado.getDados().size());
    }

    @Test
    void devePermitirUsuariosSistemaAcimaDoLimiteLegadoDeDezPaginas() {
        final AtomicInteger callCount = new AtomicInteger();
        final GraphQLPaginator paginator = new GraphQLPaginator(
            LoggerFactory.getLogger(GraphQLPaginatorTest.class),
            1000,
            3,
            Duration.ofMinutes(10),
            new HashMap<>(),
            new HashSet<>(),
            new HashMap<>(),
            new GraphQLPageFetcher() {
                @Override
                public <T> PaginatedGraphQLResponse<T> fetch(
                    final String query,
                    final String nomeEntidade,
                    final Map<String, Object> variaveis,
                    final Class<T> tipoClasse
                ) {
                    final int pagina = callCount.incrementAndGet();
                    return cast(List.of(pagina), pagina < 11, "cursor-" + pagina);
                }
            }
        );

        final ResultadoExtracao<Integer> resultado = paginator.executarQueryPaginada(
            "exec-6",
            "query",
            "individual",
            Map.of("params", Map.of("enabled", true)),
            Integer.class
        );

        assertTrue(resultado.isCompleto());
        assertEquals(11, resultado.getPaginasProcessadas());
        assertEquals(11, resultado.getDados().size());
        assertEquals(11, callCount.get());
    }

    @Test
    void deveConcluirUsuariosSistemaQuandoApiTerminaAntesDoHardLimit() {
        final AtomicInteger callCount = new AtomicInteger();
        final GraphQLPaginator paginator = new GraphQLPaginator(
            LoggerFactory.getLogger(GraphQLPaginatorTest.class),
            1000,
            3,
            Duration.ofMinutes(10),
            new HashMap<>(),
            new HashSet<>(),
            new HashMap<>(),
            new GraphQLPageFetcher() {
                @Override
                public <T> PaginatedGraphQLResponse<T> fetch(
                    final String query,
                    final String nomeEntidade,
                    final Map<String, Object> variaveis,
                    final Class<T> tipoClasse
                ) {
                    final int pagina = callCount.incrementAndGet();
                    return cast(java.util.stream.IntStream.rangeClosed((pagina - 1) * 100 + 1, pagina * 100).boxed().toList(),
                        pagina < 3,
                        "cursor-reg-" + pagina);
                }
            }
        );

        final ResultadoExtracao<Integer> resultado = paginator.executarQueryPaginada(
            "exec-7",
            "query",
            "individual",
            Map.of("params", Map.of("enabled", true)),
            Integer.class
        );

        assertTrue(resultado.isCompleto());
        assertEquals(3, resultado.getPaginasProcessadas());
        assertEquals(300, resultado.getDados().size());
    }

    @Test
    void deveInterromperPaginacaoQueNuncaEncerraAoAtingirLimiteDePaginas() {
        final AtomicInteger callCount = new AtomicInteger();
        final GraphQLPaginator paginator = new GraphQLPaginator(
            LoggerFactory.getLogger(GraphQLPaginatorTest.class),
            1000,
            3,
            Duration.ofMinutes(10),
            new HashMap<>(),
            new HashSet<>(),
            new HashMap<>(),
            new GraphQLPageFetcher() {
                @Override
                public <T> PaginatedGraphQLResponse<T> fetch(
                    final String query,
                    final String nomeEntidade,
                    final Map<String, Object> variaveis,
                    final Class<T> tipoClasse
                ) {
                    final int pagina = callCount.incrementAndGet();
                    return cast(List.of(pagina), true, "cursor-" + pagina);
                }
            }
        );

        final ResultadoExtracao<Integer> resultado = paginator.executarQueryPaginada(
            "exec-loop",
            "query",
            "freights",
            Map.of(),
            Integer.class
        );

        assertFalse(resultado.isCompleto());
        assertEquals(ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS.getCodigo(), resultado.getMotivoInterrupcao());
        assertEquals(ConfigApi.obterLimitePaginasApiGraphQL(), resultado.getPaginasProcessadas());
        assertEquals(ConfigApi.obterLimitePaginasApiGraphQL(), resultado.getDados().size());
    }

    @SuppressWarnings("unchecked")
    private static <T> PaginatedGraphQLResponse<T> cast(
        final List<?> dados,
        final boolean hasNextPage,
        final String cursor
    ) {
        return new PaginatedGraphQLResponse<>((List<T>) dados, hasNextPage, cursor, 200, 10, "req", "resp", dados.size());
    }
}
