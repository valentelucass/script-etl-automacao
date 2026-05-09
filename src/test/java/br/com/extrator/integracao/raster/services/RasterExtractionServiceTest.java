package br.com.extrator.integracao.raster.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.extrator.aplicacao.portas.ExecutionAuditPort;
import br.com.extrator.integracao.ResultadoExtracao;
import br.com.extrator.integracao.comum.EntityExtractor;
import br.com.extrator.integracao.comum.ExtractionLogger;
import br.com.extrator.integracao.raster.RasterViagemExtractor;
import br.com.extrator.dominio.raster.RasterViagemDTO;
import br.com.extrator.persistencia.entidade.LogExtracaoEntity;
import br.com.extrator.persistencia.repositorio.LogExtracaoRepository;
import br.com.extrator.plataforma.auditoria.dominio.ExecutionAuditRecord;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

class RasterExtractionServiceTest {

    @Test
    void deveRegistrarLogParaViagensEParadas() {
        final RecordingRasterViagemExtractor extractor = new RecordingRasterViagemExtractor();
        final RecordingLogExtracaoRepository logRepository = new RecordingLogExtracaoRepository();
        final RasterExtractionService service = new RasterExtractionService(
            extractor,
            logRepository,
            new NoOpExecutionAuditPort(),
            new ExtractionLogger(RasterExtractionService.class)
        );

        service.executar(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1));

        assertEquals(2, logRepository.logs.size());
        assertEquals(ConstantesEntidades.RASTER_VIAGENS, logRepository.logs.get(0).getEntidade());
        assertEquals(ConstantesEntidades.RASTER_VIAGEM_PARADAS, logRepository.logs.get(1).getEntidade());
        assertEquals(1, logRepository.logs.get(0).getRegistrosExtraidos());
        assertEquals(3, logRepository.logs.get(1).getRegistrosExtraidos());
    }

    @Test
    void deveRegistrarRasterVazioComoCompletoComZeroRegistros() {
        final EmptyRasterViagemExtractor extractor = new EmptyRasterViagemExtractor();
        final RecordingLogExtracaoRepository logRepository = new RecordingLogExtracaoRepository();
        final RasterExtractionService service = new RasterExtractionService(
            extractor,
            logRepository,
            new NoOpExecutionAuditPort(),
            new ExtractionLogger(RasterExtractionService.class)
        );

        service.executar(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1));

        assertEquals(2, logRepository.logs.size());
        assertEquals(LogExtracaoEntity.StatusExtracao.COMPLETO, logRepository.logs.get(0).getStatusFinal());
        assertEquals(LogExtracaoEntity.StatusExtracao.COMPLETO, logRepository.logs.get(1).getStatusFinal());
        assertEquals(0, logRepository.logs.get(0).getRegistrosExtraidos());
        assertEquals(0, logRepository.logs.get(1).getRegistrosExtraidos());
    }

    private static final class RecordingRasterViagemExtractor extends RasterViagemExtractor {
        @Override
        public ResultadoExtracao<RasterViagemDTO> extract(final LocalDate dataInicio, final LocalDate dataFim) {
            return ResultadoExtracao.completo(List.of(new RasterViagemDTO()), 1, 1);
        }

        @Override
        public EntityExtractor.SaveMetrics saveWithMetrics(final List<RasterViagemDTO> dtos) throws SQLException {
            return new EntityExtractor.SaveMetrics(1, 1, 0, 1, 0);
        }

        @Override
        public int getUltimaQuantidadeParadas() {
            return 3;
        }

        @Override
        public int getUltimasParadasSalvas() {
            return 3;
        }

        @Override
        public int getUltimasParadasPersistidas() {
            return 2;
        }

        @Override
        public int getUltimasParadasNoOpIdempotente() {
            return 1;
        }
    }

    private static final class EmptyRasterViagemExtractor extends RasterViagemExtractor {
        @Override
        public ResultadoExtracao<RasterViagemDTO> extract(final LocalDate dataInicio, final LocalDate dataFim) {
            return ResultadoExtracao.completo(List.of(), 1, 0);
        }
    }

    private static final class RecordingLogExtracaoRepository extends LogExtracaoRepository {
        private final List<LogExtracaoEntity> logs = new ArrayList<>();

        @Override
        public void gravarLogExtracao(final LogExtracaoEntity logExtracao) {
            logs.add(logExtracao);
        }
    }

    private static final class NoOpExecutionAuditPort implements ExecutionAuditPort {
        @Override
        public void registrarResultado(final ExecutionAuditRecord record) {
            // no-op
        }

        @Override
        public Optional<ExecutionAuditRecord> buscarResultado(final String executionUuid, final String entidade) {
            return Optional.empty();
        }

        @Override
        public List<ExecutionAuditRecord> listarResultados(final String executionUuid) {
            return List.of();
        }

        @Override
        public Optional<LocalDateTime> buscarWatermarkConfirmado(final String entidade) {
            return Optional.empty();
        }

        @Override
        public void atualizarWatermarkConfirmado(final String entidade, final LocalDateTime watermarkConfirmado) {
            // no-op
        }
    }
}
