package br.com.extrator.integracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import br.com.extrator.aplicacao.pipeline.PipelineStep;
import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.aplicacao.pipeline.runtime.StepStatus;
import br.com.extrator.bootstrap.pipeline.PipelineCompositionRoot;
import br.com.extrator.suporte.concorrencia.OperationTimeoutGuard;

class UsuariosIncrementalContencaoTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<JsonNode> requisicoes = new CopyOnWriteArrayList<>();
    private final Map<String, String> propriedadesAnteriores = new HashMap<>();
    private HttpServer servidor;

    @AfterEach
    void restaurarAmbiente() {
        if (servidor != null) {
            servidor.stop(0);
        }
        propriedadesAnteriores.forEach((chave, valor) -> {
            if (valor == null) {
                System.clearProperty(chave);
            } else {
                System.setProperty(chave, valor);
            }
        });
    }

    @Test
    void devePreservarFiltroDiarioECursorEmTodasAsPaginasComChunks() throws Exception {
        final ClienteApiGraphQL cliente = criarCliente(
            pagina(true, "cursor-1", 1, 20), pagina(false, "cursor-2", 21, 3)
        );
        final List<Long> ids = new ArrayList<>();

        final var resultado = cliente.buscarUsuariosSistema(
            LocalDateTime.of(2026, 9, 8, 12, 30), LocalDateTime.of(2026, 9, 8, 19, 30),
            chunk -> chunk.forEach(usuario -> ids.add(usuario.getId()))
        );

        assertTrue(resultado.isCompleto());
        assertEquals(23, resultado.getRegistrosExtraidos());
        assertEquals(2, resultado.getPaginasProcessadas());
        assertEquals(23, ids.stream().distinct().count());
        assertEquals(2, requisicoes.size());
        validarFiltroEmTodasAsRequisicoes("2026-09-08 - 2026-09-08");
        assertEquals("cursor-1", requisicoes.get(1).at("/variables/after").asText());
    }

    @Test
    void deveConcluirJanelaVaziaSemRecorrerAConsultaGlobal() throws Exception {
        final ClienteApiGraphQL cliente = criarCliente(pagina(false, "empty", 1, 0));

        final var resultado = cliente.buscarUsuariosSistema(
            LocalDateTime.of(2099, 1, 1, 0, 0), LocalDateTime.of(2099, 1, 2, 0, 0)
        );

        assertTrue(resultado.isCompleto());
        assertEquals(0, resultado.getRegistrosExtraidos());
        assertEquals(1, requisicoes.size());
        validarFiltroEmTodasAsRequisicoes("2099-01-01 - 2099-01-02");
    }

    @Test
    void devePreservarFiltroTemporalQuandoApiFalha() throws Exception {
        final ClienteApiGraphQL cliente = criarCliente(
            "{\"errors\":[{\"message\":\"Synthetic contract error\"}]}"
        );

        final var resultado = cliente.buscarUsuariosSistema(
            LocalDateTime.of(2026, 9, 7, 0, 0), LocalDateTime.of(2026, 9, 8, 1, 0)
        );

        assertFalse(resultado.isCompleto());
        assertEquals("ERRO_API", resultado.getMotivoInterrupcao());
        validarFiltroEmTodasAsRequisicoes("2026-09-07 - 2026-09-08");
    }

    @Test
    void deveProsseguirParaDemaisEtapasAposUsuariosIncrementais() throws Exception {
        final ClienteApiGraphQL cliente = criarCliente(pagina(false, "done", 1, 2));
        configurarPropriedade("RASTER_ENABLED", "true");
        final var root = PipelineCompositionRoot.criarPadrao();
        final List<String> etapasVisitadas = new CopyOnWriteArrayList<>();
        final List<PipelineStep> etapas = root.criarStepsFluxoCompleto(true).stream()
            .map(original -> simularEtapa(original, cliente, etapasVisitadas)).toList();
        final LocalDate dia = LocalDate.of(2026, 9, 8);

        final var relatorio = OperationTimeoutGuard.executar(
            "teste-usuarios-incrementais", Duration.ofSeconds(10),
            () -> root.criarOrquestrador().executar(dia, dia, etapas)
        );

        assertFalse(relatorio.isAborted());
        assertEquals(0, relatorio.totalFalhasExecucao());
        assertEquals(
            List.of("usuarios_sistema", "coletas", "fretes", "dataexport", "raster_viagens", "quality"),
            etapasVisitadas
        );
        validarFiltroEmTodasAsRequisicoes("2026-09-08 - 2026-09-08");
    }

    private PipelineStep simularEtapa(final PipelineStep original,
                                     final ClienteApiGraphQL cliente,
                                     final List<String> etapasVisitadas) {
        return new PipelineStep() {
            @Override
            public String obterNomeEtapa() {
                return original.obterNomeEtapa();
            }

            @Override
            public String obterNomeEntidade() {
                return original.obterNomeEntidade();
            }

            @Override
            public StepExecutionResult executar(final LocalDate inicio, final LocalDate fim) {
                final String entidade = obterNomeEntidade();
                if ("usuarios_sistema".equals(entidade)) {
                    final var resultado = cliente.buscarUsuariosSistema(inicio.atStartOfDay(), fim.atTime(20, 0));
                    if (!resultado.isCompleto()) {
                        throw new IllegalStateException("Extração de usuários incompleta no teste");
                    }
                }
                etapasVisitadas.add(entidade);
                final LocalDateTime agora = LocalDateTime.now();
                return StepExecutionResult.builder(obterNomeEtapa(), entidade)
                    .status(StepStatus.SUCCESS).startedAt(agora).finishedAt(agora).build();
            }
        };
    }

    private ClienteApiGraphQL criarCliente(final String... respostas) throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/graphql", exchange -> {
            requisicoes.add(mapper.readTree(exchange.getRequestBody()));
            final String resposta = respostas[Math.min(requisicoes.size() - 1, respostas.length - 1)];
            final byte[] bytes = resposta.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        servidor.start();
        configurarPropriedade("API_BASEURL", "http://127.0.0.1:" + servidor.getAddress().getPort());
        configurarPropriedade("API_GRAPHQL_ENDPOINT", "/graphql");
        configurarPropriedade("API_GRAPHQL_TOKEN", "synthetic-test-token");
        return new ClienteApiGraphQL();
    }

    private String pagina(final boolean temProxima, final String cursor, final int primeiroId, final int quantidade) {
        final var conexao = mapper.createObjectNode();
        final var edges = conexao.putArray("edges");
        for (int i = 0; i < quantidade; i++) {
            edges.addObject().putObject("node").put("id", primeiroId + i).put("name", "Synthetic");
        }
        conexao.putObject("pageInfo").put("hasNextPage", temProxima).put("endCursor", cursor);
        final var resposta = mapper.createObjectNode();
        resposta.putObject("data").set("individual", conexao);
        return resposta.toString();
    }

    private void validarFiltroEmTodasAsRequisicoes(final String intervalo) {
        assertFalse(requisicoes.isEmpty());
        for (final JsonNode requisicao : requisicoes) {
            assertEquals(intervalo, requisicao.at("/variables/params/updatedAt").asText());
            assertTrue(requisicao.at("/variables/params/enabled").asBoolean());
            assertFalse(requisicao.get("query").asText().contains("updatedAt"),
                "updatedAt pertence ao input, não ao node Individual");
        }
    }

    private void configurarPropriedade(final String chave, final String valor) {
        if (!propriedadesAnteriores.containsKey(chave)) {
            propriedadesAnteriores.put(chave, System.getProperty(chave));
        }
        System.setProperty(chave, valor);
    }
}
