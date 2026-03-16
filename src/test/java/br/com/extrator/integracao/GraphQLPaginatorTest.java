package br.com.extrator.integracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

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
            null,
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
        assertEquals(ResultadoExtracao.MotivoInterrupcao.PAGINACAO_INCONSISTENTE.getCodigo(), resultado.getMotivoInterrupcao());
        assertEquals(2, resultado.getPaginasProcessadas());
        assertEquals(25, resultado.getDados().size());
    }

    @Test
    void deveMarcarIncompletoQuandoHasNextPageVemComPaginaCurta() {
        final AtomicInteger callCount = new AtomicInteger();
        final GraphQLPaginator paginator = new GraphQLPaginator(
            LoggerFactory.getLogger(GraphQLPaginatorTest.class),
            10,
            3,
            Duration.ofMinutes(10),
            new HashMap<>(),
            new HashSet<>(),
            new HashMap<>(),
            null,
            new GraphQLPageFetcher() {
                @Override
                public <T> PaginatedGraphQLResponse<T> fetch(
                    final String query,
                    final String nomeEntidade,
                    final Map<String, Object> variaveis,
                    final Class<T> tipoClasse
                ) {
                    if (callCount.getAndIncrement() == 0) {
                        return cast(java.util.stream.IntStream.rangeClosed(1, 20).boxed().toList(), true, "cursor-2");
                    }
                    return cast(List.of(21, 22, 23, 24, 25), true, "cursor-3");
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

        assertFalse(resultado.isCompleto());
        assertEquals(ResultadoExtracao.MotivoInterrupcao.PAGINACAO_INCONSISTENTE.getCodigo(), resultado.getMotivoInterrupcao());
        assertEquals(2, resultado.getPaginasProcessadas());
        assertEquals(25, resultado.getDados().size());
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
            null,
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
            null,
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
            null,
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
    void devePermitirMaisDeDuasMilPaginasParaUsuariosSistema() {
        final AtomicInteger callCount = new AtomicInteger();
        final GraphQLPaginator paginator = new GraphQLPaginator(
            LoggerFactory.getLogger(GraphQLPaginatorTest.class),
            1000,
            3,
            Duration.ofMinutes(10),
            new HashMap<>(),
            new HashSet<>(),
            new HashMap<>(),
            null,
            new GraphQLPageFetcher() {
                @Override
                public <T> PaginatedGraphQLResponse<T> fetch(
                    final String query,
                    final String nomeEntidade,
                    final Map<String, Object> variaveis,
                    final Class<T> tipoClasse
                ) {
                    final int pagina = callCount.incrementAndGet();
                    return cast(List.of(pagina), pagina < 2001, "cursor-" + pagina);
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
        assertEquals(2001, resultado.getPaginasProcessadas());
        assertEquals(2001, resultado.getDados().size());
    }

    @Test
    void devePermitirMaisDeCinquentaMilRegistrosParaUsuariosSistema() {
        final AtomicInteger callCount = new AtomicInteger();
        final GraphQLPaginator paginator = new GraphQLPaginator(
            LoggerFactory.getLogger(GraphQLPaginatorTest.class),
            1000,
            3,
            Duration.ofMinutes(10),
            new HashMap<>(),
            new HashSet<>(),
            new HashMap<>(),
            null,
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
                        pagina < 501,
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
        assertEquals(501, resultado.getPaginasProcessadas());
        assertEquals(50100, resultado.getDados().size());
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
