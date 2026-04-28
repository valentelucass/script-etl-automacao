package br.com.extrator.aplicacao.validacao;

import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.EntidadeValidacao;
import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.JanelaExecucao;
import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.PeriodoConsulta;
import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.ResultadoApiChaves;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import br.com.extrator.dominio.graphql.usuarios.IndividualNodeDTO;
import br.com.extrator.integracao.ClienteApiDataExport;
import br.com.extrator.integracao.ClienteApiGraphQL;
import br.com.extrator.integracao.ResultadoExtracao;
import br.com.extrator.integracao.mapeamento.dataexport.contasapagar.ContasAPagarMapper;
import br.com.extrator.integracao.mapeamento.dataexport.cotacao.CotacaoMapper;
import br.com.extrator.integracao.mapeamento.dataexport.faturaporcliente.FaturaPorClienteMapper;
import br.com.extrator.integracao.mapeamento.dataexport.inventario.InventarioMapper;
import br.com.extrator.integracao.mapeamento.dataexport.localizacaocarga.LocalizacaoCargaMapper;
import br.com.extrator.integracao.mapeamento.dataexport.manifestos.ManifestoMapper;
import br.com.extrator.integracao.mapeamento.dataexport.sinistros.SinistroMapper;
import br.com.extrator.integracao.mapeamento.graphql.coletas.ColetaMapper;
import br.com.extrator.integracao.mapeamento.graphql.fretes.FreteMapper;
import br.com.extrator.integracao.mapeamento.graphql.usuarios.UsuarioSistemaMapper;
import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

class ValidacaoApiBanco24hDetalhadaApiCollectorTest {

    @Test
    void deveUsarConsultaIncrementalDeUsuariosMesmoQuandoDimUsuariosEstiverVazia() throws Exception {
        final RecordingClienteApiGraphQL clienteGraphQL = new RecordingClienteApiGraphQL();
        final ValidacaoApiBanco24hDetalhadaApiCollector collector = novoCollector(clienteGraphQL);
        final Connection conexao = conexaoQueFalhaSeConsultarDimUsuarios();
        final LocalDate dataInicio = LocalDate.of(2026, 3, 26);
        final LocalDate dataFim = LocalDate.of(2026, 3, 27);

        final List<EntidadeValidacao> entidades = collector.criarEntidades(
            conexao,
            dataFim,
            dataInicio,
            dataFim,
            List.of(ConstantesEntidades.USUARIOS_SISTEMA),
            false,
            Map.of()
        );
        final var resultado = entidades.get(0).fornecedor().get();

        assertEquals(1, entidades.size());
        assertTrue(clienteGraphQL.incrementalChamado);
        assertFalse(clienteGraphQL.fullLoadChamado);
        assertEquals(dataInicio, clienteGraphQL.dataInicioRecebida);
        assertEquals(dataFim, clienteGraphQL.dataFimRecebida);
        assertEquals(1, resultado.apiBruto());
        assertEquals(1, resultado.apiUnico());
        assertTrue(resultado.extracaoCompleta());
    }

    @Test
    void devePriorizarPeriodoDaExecucaoAncoradaQuandoDisponivel() throws Exception {
        final RecordingClienteApiGraphQL clienteGraphQL = new RecordingClienteApiGraphQL();
        final ValidacaoApiBanco24hDetalhadaApiCollector collector = novoCollector(clienteGraphQL);

        collector.criarEntidades(
            conexaoQueFalhaSeConsultarDimUsuarios(),
            LocalDate.of(2026, 4, 15),
            LocalDate.of(2026, 4, 13),
            LocalDate.of(2026, 4, 13),
            List.of(ConstantesEntidades.USUARIOS_SISTEMA),
            false,
            Map.of(
                ConstantesEntidades.USUARIOS_SISTEMA,
                new PeriodoConsulta(LocalDate.of(2026, 4, 14), LocalDate.of(2026, 4, 15))
            )
        ).get(0).fornecedor().get();

        assertEquals(LocalDate.of(2026, 4, 14), clienteGraphQL.dataInicioRecebida);
        assertEquals(LocalDate.of(2026, 4, 15), clienteGraphQL.dataFimRecebida);
    }

    @Test
    void deveUsarJanelaDeFretesDaExecucaoAncoradaParaToleranciaReferencialDeFaturasGraphQL() throws Exception {
        final RecordingClienteApiGraphQL clienteGraphQL = new RecordingClienteApiGraphQL();
        final AtomicBoolean buscouJanelaAncorada = new AtomicBoolean();
        final AtomicBoolean consultouFallback = new AtomicBoolean();
        final LocalDateTime inicioJanela = LocalDateTime.of(2026, 4, 15, 8, 59, 33);
        final LocalDateTime fimJanela = LocalDateTime.of(2026, 4, 15, 9, 2, 24);
        final ValidacaoApiBanco24hDetalhadaRepository repository =
            new ValidacaoApiBanco24hDetalhadaRepository(
                LoggerConsole.getLogger(ValidacaoApiBanco24hDetalhadaApiCollectorTest.class),
                new ValidacaoApiBanco24hDetalhadaMetadataHasher()
            ) {
                @Override
                Optional<JanelaExecucao> buscarJanelaEstruturadaDaExecucao(
                    final Connection conexao,
                    final String executionUuid,
                    final String entidade
                ) {
                    buscouJanelaAncorada.set(true);
                    assertEquals("exec-ancora", executionUuid);
                    assertEquals(ConstantesEntidades.FRETES, entidade);
                    return Optional.of(new JanelaExecucao(inicioJanela, fimJanela, true));
                }

                @Override
                Optional<JanelaExecucao> buscarUltimaJanelaCompletaDoDia(
                    final Connection conexao,
                    final String entidade,
                    final LocalDate dataReferencia,
                    final LocalDate periodoInicio,
                    final LocalDate periodoFim,
                    final boolean permitirFallbackJanela
                ) {
                    consultouFallback.set(true);
                    return Optional.empty();
                }

                @Override
                List<Long> listarAccountingCreditIdsFretes(
                    final Connection conexao,
                    final JanelaExecucao janela,
                    final int limite
                ) {
                    assertEquals(inicioJanela, janela.inicio());
                    assertEquals(fimJanela, janela.fim());
                    return List.of(4217411L, 4217413L);
                }
            };
        final ValidacaoApiBanco24hDetalhadaApiCollector collector = novoCollector(clienteGraphQL, repository);

        final ResultadoApiChaves resultado = collector.criarEntidades(
            conexaoQueFalhaSeConsultarDimUsuarios(),
            LocalDate.of(2026, 4, 15),
            LocalDate.of(2026, 4, 13),
            LocalDate.of(2026, 4, 13),
            List.of(ConstantesEntidades.FATURAS_GRAPHQL),
            false,
            Map.of(),
            Optional.of("exec-ancora")
        ).get(0).fornecedor().get();

        assertTrue(buscouJanelaAncorada.get());
        assertFalse(consultouFallback.get());
        assertEquals(Set.of("4217411", "4217413"), resultado.chavesToleradasNoBanco());
        assertTrue(resultado.detalhe().contains("ids_fretes_janela=2"));
        assertTrue(resultado.detalhe().contains("fretes_origem_janela=EXECUTION_AUDIT"));
    }

    @Test
    void deveIncluirInventarioESinistrosNaListaPadraoDeEntidadesDetalhadas() {
        final RecordingClienteApiGraphQL clienteGraphQL = new RecordingClienteApiGraphQL();
        final ValidacaoApiBanco24hDetalhadaApiCollector collector = novoCollector(clienteGraphQL);

        final List<EntidadeValidacao> entidades = collector.criarEntidades(
            conexaoQueFalhaSeConsultarDimUsuarios(),
            LocalDate.of(2026, 4, 23),
            LocalDate.of(2026, 4, 22),
            LocalDate.of(2026, 4, 23),
            true,
            false,
            Map.of()
        );

        final List<String> nomes = entidades.stream().map(EntidadeValidacao::entidade).toList();
        assertTrue(nomes.contains(ConstantesEntidades.INVENTARIO));
        assertTrue(nomes.contains(ConstantesEntidades.SINISTROS));
    }

    private ValidacaoApiBanco24hDetalhadaApiCollector novoCollector(final RecordingClienteApiGraphQL clienteGraphQL) {
        return novoCollector(
            clienteGraphQL,
            new ValidacaoApiBanco24hDetalhadaRepository(
                LoggerConsole.getLogger(ValidacaoApiBanco24hDetalhadaApiCollectorTest.class),
                new ValidacaoApiBanco24hDetalhadaMetadataHasher()
            )
        );
    }

    private ValidacaoApiBanco24hDetalhadaApiCollector novoCollector(
        final RecordingClienteApiGraphQL clienteGraphQL,
        final ValidacaoApiBanco24hDetalhadaRepository repository
    ) {
        return new ValidacaoApiBanco24hDetalhadaApiCollector(
            new ClienteApiDataExport(),
            clienteGraphQL,
            new ManifestoMapper(),
            new CotacaoMapper(),
            new LocalizacaoCargaMapper(),
            new ContasAPagarMapper(),
            new FaturaPorClienteMapper(),
            new InventarioMapper(),
            new SinistroMapper(),
            new FreteMapper(),
            new ColetaMapper(),
            new UsuarioSistemaMapper(),
            new ValidacaoApiBanco24hDetalhadaMetadataHasher(),
            repository
        );
    }

    private Connection conexaoQueFalhaSeConsultarDimUsuarios() {
        return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class},
            (proxy, method, args) -> {
                if ("prepareStatement".equals(method.getName())) {
                    throw new AssertionError("A validacao de usuarios nao deve consultar dim_usuarios para decidir full load.");
                }
                if ("close".equals(method.getName())) {
                    return null;
                }
                if ("isClosed".equals(method.getName())) {
                    return false;
                }
                return valorPadrao(method.getReturnType());
            }
        );
    }

    private Object valorPadrao(final Class<?> tipoRetorno) {
        if (tipoRetorno == boolean.class) {
            return false;
        }
        if (tipoRetorno == int.class) {
            return 0;
        }
        if (tipoRetorno == long.class) {
            return 0L;
        }
        if (tipoRetorno == double.class) {
            return 0d;
        }
        return null;
    }

    private static final class RecordingClienteApiGraphQL extends ClienteApiGraphQL {
        private boolean incrementalChamado;
        private boolean fullLoadChamado;
        private LocalDate dataInicioRecebida;
        private LocalDate dataFimRecebida;

        @Override
        public ResultadoExtracao<IndividualNodeDTO> buscarUsuariosSistema(final LocalDate dataInicio, final LocalDate dataFim) {
            incrementalChamado = true;
            dataInicioRecebida = dataInicio;
            dataFimRecebida = dataFim;
            return ResultadoExtracao.completo(List.of(usuarioDto(10L, "Ana")), 1, 1);
        }

        @Override
        public ResultadoExtracao<IndividualNodeDTO> buscarUsuariosSistema() {
            fullLoadChamado = true;
            return ResultadoExtracao.completo(List.of(), 0, 0);
        }

        @Override
        public ResultadoExtracao<br.com.extrator.dominio.graphql.faturas.CreditCustomerBillingNodeDTO> buscarCapaFaturas(
            final LocalDate dataInicio,
            final LocalDate dataFim
        ) {
            return ResultadoExtracao.completo(List.of(), 0, 0);
        }

        private static IndividualNodeDTO usuarioDto(final Long id, final String nome) {
            final IndividualNodeDTO dto = new IndividualNodeDTO();
            dto.setId(id);
            dto.setName(nome);
            return dto;
        }
    }
}
