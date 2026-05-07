package br.com.extrator.integracao.raster;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import br.com.extrator.dominio.raster.RasterViagemDTO;
import br.com.extrator.integracao.ResultadoExtracao;
import br.com.extrator.integracao.comum.EntityExtractor;
import br.com.extrator.integracao.mapeamento.raster.RasterMapper;
import br.com.extrator.persistencia.entidade.RasterViagemEntity;
import br.com.extrator.persistencia.entidade.RasterViagemParadaEntity;
import br.com.extrator.persistencia.repositorio.AbstractRepository;
import br.com.extrator.persistencia.repositorio.RasterViagemParadaRepository;
import br.com.extrator.persistencia.repositorio.RasterViagemRepository;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public class RasterViagemExtractor implements EntityExtractor<RasterViagemDTO> {
    private final ClienteApiRaster apiClient;
    private final RasterViagemRepository viagemRepository;
    private final RasterViagemParadaRepository paradaRepository;
    private final RasterMapper mapper;
    private int ultimaQuantidadeParadas;
    private int ultimasParadasSalvas;
    private int ultimasParadasPersistidas;
    private int ultimasParadasNoOpIdempotente;

    public RasterViagemExtractor() {
        this(
            new ClienteApiRaster(),
            new RasterViagemRepository(),
            new RasterViagemParadaRepository(),
            new RasterMapper()
        );
    }

    public RasterViagemExtractor(final ClienteApiRaster apiClient,
                                 final RasterViagemRepository viagemRepository,
                                 final RasterViagemParadaRepository paradaRepository,
                                 final RasterMapper mapper) {
        this.apiClient = apiClient;
        this.viagemRepository = viagemRepository;
        this.paradaRepository = paradaRepository;
        this.mapper = mapper;
    }

    @Override
    public ResultadoExtracao<RasterViagemDTO> extract(final LocalDate dataInicio, final LocalDate dataFim) {
        resetarMetricasParadas();
        return apiClient.buscarEventoFimViagem(dataInicio, dataFim);
    }

    @Override
    public int save(final List<RasterViagemDTO> dtos) throws SQLException {
        return saveWithMetrics(dtos).getRegistrosSalvos();
    }

    @Override
    public SaveMetrics saveWithMetrics(final List<RasterViagemDTO> dtos) throws SQLException {
        resetarMetricasParadas();
        final List<RasterViagemEntity> viagens = new ArrayList<>();
        final List<RasterViagemParadaEntity> paradas = new ArrayList<>();
        int invalidos = 0;

        if (dtos != null) {
            for (final RasterViagemDTO dto : dtos) {
                if (dto == null || dto.getCodSolicitacao() == null) {
                    invalidos++;
                    continue;
                }
                final RasterViagemEntity viagem = mapper.toViagemEntity(dto);
                if (viagem == null) {
                    invalidos++;
                    continue;
                }
                viagens.add(viagem);
                paradas.addAll(mapper.toParadaEntities(dto));
            }
        }

        final int registrosSalvos = viagens.isEmpty() ? 0 : viagemRepository.salvar(viagens);
        ultimaQuantidadeParadas = paradas.size();
        if (!paradas.isEmpty()) {
            ultimasParadasSalvas = paradaRepository.salvar(paradas);
            final AbstractRepository.SaveSummary resumoParadas = paradaRepository.getUltimoResumoSalvamento();
            ultimasParadasPersistidas = resumoParadas.getRegistrosPersistidos();
            ultimasParadasNoOpIdempotente = resumoParadas.getRegistrosNoOpIdempotente();
        }
        final AbstractRepository.SaveSummary resumo = viagemRepository.getUltimoResumoSalvamento();
        return new SaveMetrics(
            registrosSalvos,
            viagens.size(),
            invalidos,
            resumo.getRegistrosPersistidos(),
            resumo.getRegistrosNoOpIdempotente()
        );
    }

    @Override
    public String getEntityName() {
        return ConstantesEntidades.RASTER_VIAGENS;
    }

    @Override
    public String getEmoji() {
        return "[RASTER]";
    }

    public int getUltimaQuantidadeParadas() {
        return ultimaQuantidadeParadas;
    }

    public int getUltimasParadasSalvas() {
        return ultimasParadasSalvas;
    }

    public int getUltimasParadasPersistidas() {
        return ultimasParadasPersistidas;
    }

    public int getUltimasParadasNoOpIdempotente() {
        return ultimasParadasNoOpIdempotente;
    }

    private void resetarMetricasParadas() {
        ultimaQuantidadeParadas = 0;
        ultimasParadasSalvas = 0;
        ultimasParadasPersistidas = 0;
        ultimasParadasNoOpIdempotente = 0;
    }
}
