package br.com.extrator.integracao.graphql.extractors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import br.com.extrator.dominio.dataexport.fretes.FreteIndicadorDTO;
import br.com.extrator.dominio.graphql.fretes.FreteNodeDTO;
import br.com.extrator.integracao.ResultadoExtracao;
import br.com.extrator.integracao.comum.EntityExtractor;
import br.com.extrator.integracao.mapeamento.graphql.fretes.FreteMapper;
import br.com.extrator.persistencia.entidade.FreteEntity;
import br.com.extrator.persistencia.repositorio.FreteRepository;

class FreteExtractorTest {

    @AfterEach
    void limparFlagDePrune() {
        System.clearProperty("ETL_FRETES_PRUNE_AUSENTES");
        System.clearProperty("ETL_FRETES_PERFORMANCE_LOOKBACK_MODO");
        System.clearProperty("ETL_FRETES_PERFORMANCE_LOOKBACK_NORMAL_DIAS");
        System.clearProperty("ETL_FRETES_PERFORMANCE_LOOKBACK_RECONCILIACAO_DIAS");
        System.clearProperty("ETL_FRETES_PERFORMANCE_LOOKBACK_BACKFILL_DIAS");
        System.clearProperty("ETL_FRETES_PERFORMANCE_LOOKBACK_INTERVALO_DIAS");
        System.clearProperty("ETL_FRETES_PERFORMANCE_LOOKBACK_DIAS");
        System.clearProperty("etl.fretes.performance.lookback.modo");
        System.clearProperty("etl.fretes.performance.lookback.normal.dias");
        System.clearProperty("etl.fretes.performance.lookback.reconciliacao.dias");
        System.clearProperty("etl.fretes.performance.lookback.backfill.dias");
        System.clearProperty("etl.fretes.performance.lookback.intervalo.dias");
        System.clearProperty("etl.fretes.performance.lookback.dias");
        System.clearProperty("api.dataexport.timezone");
    }

    @Test
    void fluxoNormalNaoDeveExpandirJanelaMesmoComChaveLegadaConfigurada() {
        System.setProperty("etl.fretes.performance.lookback.modo", "normal");
        System.setProperty("etl.fretes.performance.lookback.dias", "30");

        assertEquals(
            LocalDate.of(2026, 3, 1),
            FreteExtractor.calcularDataInicioConsulta(LocalDate.of(2026, 3, 1))
        );
    }

    @Test
    void reconciliacaoPodeUsarLookbackLegadoDePerformance() {
        System.setProperty("etl.fretes.performance.lookback.modo", "reconciliacao");
        System.setProperty("etl.fretes.performance.lookback.dias", "30");

        assertEquals(
            LocalDate.of(2026, 1, 30),
            FreteExtractor.calcularDataInicioConsulta(LocalDate.of(2026, 3, 1))
        );
    }

    @Test
    void deveDividirConsultaDeFretesEmBlocosDeNoMaximoTrintaDias() {
        final List<String> janelasChamadas = new ArrayList<>();
        final FreteExtractor extractor = new FreteExtractor(
            (dataInicio, dataFim) -> {
                janelasChamadas.add(dataInicio + ".." + dataFim);
                return ResultadoExtracao.completo(List.of(), 1, 0);
            },
            new FakeFreteRepository(),
            new FreteMapper(),
            null,
            true
        );

        final ResultadoExtracao<FreteNodeDTO> resultado = extractor.extract(
            LocalDate.of(2026, 3, 1),
            LocalDate.of(2026, 4, 5)
        );

        assertTrue(resultado.isCompleto());
        assertEquals(2, resultado.getPaginasProcessadas());
        assertEquals(
            List.of("2026-03-01..2026-03-30", "2026-03-31..2026-04-05"),
            janelasChamadas
        );
    }

    @Test
    void dataExport6389DeveUsarMesmaJanelaEfetivaDaExtracaoNormal() throws SQLException {
        System.setProperty("etl.fretes.performance.lookback.modo", "normal");
        System.setProperty("etl.fretes.performance.lookback.dias", "30");
        System.setProperty("api.dataexport.timezone", "America/Sao_Paulo");
        final FakeFreteRepository repository = new FakeFreteRepository();
        final AtomicReference<LocalDate> inicioIndicadores = new AtomicReference<>();
        final AtomicReference<LocalDate> fimIndicadores = new AtomicReference<>();
        final FreteExtractor extractor = new FreteExtractor(
            (dataInicio, dataFim) -> ResultadoExtracao.completo(
                List.of(criarFrete(47882015L, 456L, "2026-04-24T20:11:00-03:00")),
                1,
                1
            ),
            repository,
            new FreteMapper(),
            (dataInicio, dataFim) -> {
                inicioIndicadores.set(dataInicio);
                fimIndicadores.set(dataFim);
                return ResultadoExtracao.completo(
                    List.of(criarIndicador(456L, "04/24/2026 18:15:00")),
                    1,
                    1
                );
            },
            true
        );

        final ResultadoExtracao<FreteNodeDTO> resultado = extractor.extract(
            LocalDate.of(2026, 4, 24),
            LocalDate.of(2026, 4, 30)
        );
        extractor.saveWithMetrics(resultado.getDados());

        assertEquals(LocalDate.of(2026, 4, 24), inicioIndicadores.get());
        assertEquals(LocalDate.of(2026, 4, 30), fimIndicadores.get());
    }

    @Test
    void deveRemoverAusentesQuandoPruneEstaAtivoEExtracaoFoiCompleta() throws SQLException {
        System.setProperty("ETL_FRETES_PRUNE_AUSENTES", "true");
        final FakeFreteRepository repository = new FakeFreteRepository();
        final FreteExtractor extractor = new FreteExtractor(null, repository, new FreteMapper());
        extractor.registrarUltimaExtracaoParaTeste(LocalDate.of(2026, 3, 25), LocalDate.of(2026, 3, 25), true);

        final EntityExtractor.SaveMetrics metrics = extractor.saveWithMetrics(List.of(criarFrete(47882015L)));

        assertEquals(1, metrics.getRegistrosSalvos());
        assertTrue(repository.remocaoExecutada);
        assertEquals(LocalDate.of(2026, 3, 25), repository.dataInicioRemocao);
        assertEquals(LocalDate.of(2026, 3, 25), repository.dataFimRemocao);
        assertEquals(List.of(47882015L), repository.idsPresentesRemocao);
    }

    @Test
    void naoDeveRemoverAusentesQuandoExtracaoNaoFoiCompleta() throws SQLException {
        System.setProperty("ETL_FRETES_PRUNE_AUSENTES", "true");
        final FakeFreteRepository repository = new FakeFreteRepository();
        final FreteExtractor extractor = new FreteExtractor(null, repository, new FreteMapper());
        extractor.registrarUltimaExtracaoParaTeste(LocalDate.of(2026, 3, 25), LocalDate.of(2026, 3, 25), false);

        extractor.saveWithMetrics(List.of(criarFrete(47882015L)));

        assertFalse(repository.remocaoExecutada);
    }

    @Test
    void deveEnriquecerPerformanceOficialComDataExport6389() throws SQLException {
        System.setProperty("api.dataexport.timezone", "America/Sao_Paulo");
        final FakeFreteRepository repository = new FakeFreteRepository();
        final FreteExtractor extractor = new FreteExtractor(
            null,
            repository,
            new FreteMapper(),
            (dataInicio, dataFim) -> ResultadoExtracao.completo(
                List.of(criarIndicador(456L, "03/25/2026 18:15:00")),
                1,
                1
            )
        );
        extractor.registrarUltimaExtracaoParaTeste(LocalDate.of(2026, 3, 25), LocalDate.of(2026, 3, 25), true);

        extractor.saveWithMetrics(List.of(criarFrete(47882015L, 456L)));

        assertEquals(1, repository.entidadesSalvas.size());
        assertEquals(
            OffsetDateTime.parse("2026-03-25T18:15:00-03:00"),
            repository.entidadesSalvas.get(0).getFitDpnPerformanceFinishedAt()
        );
    }

    @Test
    void deveCorrigirDataAmbiguaDdMmQuandoParserPadraoGerarDataAnteriorAoFrete() throws SQLException {
        System.setProperty("api.dataexport.timezone", "America/Sao_Paulo");
        final FakeFreteRepository repository = new FakeFreteRepository();
        final FreteExtractor extractor = new FreteExtractor(
            null,
            repository,
            new FreteMapper(),
            (dataInicio, dataFim) -> ResultadoExtracao.completo(
                List.of(criarIndicador(789L, "02/04/2026 19:34:00")),
                1,
                1
            )
        );
        extractor.registrarUltimaExtracaoParaTeste(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 3), true);

        extractor.saveWithMetrics(List.of(criarFrete(47882016L, 789L, "2026-04-01T00:07:00-03:00")));

        assertEquals(
            OffsetDateTime.parse("2026-04-02T19:34:00-03:00"),
            repository.entidadesSalvas.get(0).getFitDpnPerformanceFinishedAt()
        );
    }

    private static FreteNodeDTO criarFrete(final long id) {
        return criarFrete(id, null, "2026-03-25T20:11:00-03:00");
    }

    private static FreteNodeDTO criarFrete(final long id, final Long corporationSequenceNumber) {
        return criarFrete(id, corporationSequenceNumber, "2026-03-25T20:11:00-03:00");
    }

    private static FreteNodeDTO criarFrete(final long id,
                                           final Long corporationSequenceNumber,
                                           final String serviceAt) {
        final FreteNodeDTO dto = new FreteNodeDTO();
        dto.setId(id);
        dto.setServiceAt(serviceAt);
        dto.setCreatedAt("2026-03-25T20:24:02-03:00");
        dto.setStatus("pending");
        dto.setCorporationSequenceNumber(corporationSequenceNumber);
        return dto;
    }

    private static FreteIndicadorDTO criarIndicador(final long corporationSequenceNumber,
                                                    final String performanceFinishedAt) {
        final FreteIndicadorDTO dto = new FreteIndicadorDTO();
        dto.setCorporationSequenceNumber(corporationSequenceNumber);
        dto.setPerformanceFinishedAt(performanceFinishedAt);
        return dto;
    }

    private static final class FakeFreteRepository extends FreteRepository {
        private boolean remocaoExecutada;
        private LocalDate dataInicioRemocao;
        private LocalDate dataFimRemocao;
        private List<Long> idsPresentesRemocao = List.of();
        private List<FreteEntity> entidadesSalvas = List.of();

        @Override
        public int salvar(final List<FreteEntity> entidades) {
            this.entidadesSalvas = entidades == null ? List.of() : new ArrayList<>(entidades);
            return entidades == null ? 0 : entidades.size();
        }

        @Override
        public SaveSummary getUltimoResumoSalvamento() {
            return new SaveSummary(1, 1, 0, 1, 0);
        }

        @Override
        public int removerAusentesNoPeriodo(final LocalDate dataInicio,
                                            final LocalDate dataFim,
                                            final Collection<Long> idsPresentes) {
            this.remocaoExecutada = true;
            this.dataInicioRemocao = dataInicio;
            this.dataFimRemocao = dataFim;
            this.idsPresentesRemocao = new ArrayList<>(idsPresentes);
            return 1;
        }
    }
}
