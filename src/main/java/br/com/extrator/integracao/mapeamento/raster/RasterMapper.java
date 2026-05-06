package br.com.extrator.integracao.mapeamento.raster;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import br.com.extrator.dominio.raster.RasterParadaDTO;
import br.com.extrator.dominio.raster.RasterRotaDTO;
import br.com.extrator.dominio.raster.RasterViagemDTO;
import br.com.extrator.persistencia.entidade.RasterViagemEntity;
import br.com.extrator.persistencia.entidade.RasterViagemParadaEntity;

public class RasterMapper {
    private static final int TEMPO_TOTAL_MAXIMO_VALIDO_MIN = 30 * 24 * 60;
    private static final ZoneId ZONE_ID_PADRAO = ZoneId.systemDefault();
    private static final DateTimeFormatter[] FORMATOS_DATA_HORA_SEM_OFFSET = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
    };

    public RasterViagemEntity toViagemEntity(final RasterViagemDTO dto) {
        if (dto == null) {
            return null;
        }
        final RasterViagemEntity entity = new RasterViagemEntity();
        entity.setCodSolicitacao(dto.getCodSolicitacao());
        entity.setSequencial(dto.getSequencial());
        entity.setCodFilial(dto.getCodFilial());
        entity.setStatusViagem(trim(dto.getStatusViagem()));
        entity.setPlacaVeiculo(trim(dto.getPlacaVeiculo()));
        entity.setPlacaCarreta1(trim(dto.getPlacaCarreta1()));
        entity.setPlacaCarreta2(trim(dto.getPlacaCarreta2()));
        entity.setPlacaCarreta3(trim(dto.getPlacaCarreta3()));
        entity.setCpfMotorista1(trim(dto.getCpfMotorista1()));
        entity.setCpfMotorista2(trim(dto.getCpfMotorista2()));
        entity.setCnpjClienteOrig(trim(dto.getCnpjClienteOrig()));
        entity.setCnpjClienteDest(trim(dto.getCnpjClienteDest()));
        entity.setCodIbgeCidadeOrig(dto.getCodIbgeCidadeOrig());
        entity.setCodIbgeCidadeDest(dto.getCodIbgeCidadeDest());
        entity.setDataHoraPrevIni(parseRasterDateTime(dto.getDataHoraPrevIni()));
        entity.setDataHoraPrevFim(parseRasterDateTime(dto.getDataHoraPrevFim()));
        entity.setDataHoraRealIni(parseRasterDateTime(dto.getDataHoraRealIni()));
        entity.setDataHoraRealFim(parseRasterDateTime(dto.getDataHoraRealFim()));
        entity.setDataHoraIdentificouFimViagem(parseRasterDateTime(dto.getDataHoraIdentificouFimViagem()));
        entity.setTempoTotalViagemMin(normalizarTempoTotalViagemMin(dto.getTempoTotalViagem()));
        entity.setDentroPrazoRaster(trim(dto.getDentroPrazo()));
        entity.setPercentualAtrasoRaster(dto.getPercentualAtraso());
        entity.setRodouForaHorario(trim(dto.getRodouForaHorario()));
        entity.setVelocidadeMedia(dto.getVelocidadeMedia());
        entity.setEventosVelocidade(dto.getEventosVelocidade());
        entity.setDesviosDeRota(dto.getDesviosDeRota());
        final RasterRotaDTO rota = dto.getRota();
        if (rota != null) {
            entity.setCodRota(rota.getCodRota());
            entity.setRotaDescricao(trim(rota.getDescricao()));
        }
        entity.setLinkTimeline(trim(dto.getLinkTimeline()));
        entity.setMetadata(dto.getMetadata());
        entity.setDataExtracao(Instant.now());
        return entity;
    }

    public List<RasterViagemParadaEntity> toParadaEntities(final RasterViagemDTO viagem) {
        final List<RasterViagemParadaEntity> entities = new ArrayList<>();
        if (viagem == null || viagem.getCodSolicitacao() == null) {
            return entities;
        }
        final List<RasterParadaDTO> paradas = viagem.getColetasEntregas();
        for (int i = 0; i < paradas.size(); i++) {
            final RasterViagemParadaEntity entity = toParadaEntity(viagem.getCodSolicitacao(), paradas.get(i), i + 1);
            if (entity != null) {
                entities.add(entity);
            }
        }
        return entities;
    }

    public RasterViagemParadaEntity toParadaEntity(final Long codSolicitacao,
                                                   final RasterParadaDTO dto,
                                                   final int ordemFallback) {
        if (codSolicitacao == null || dto == null) {
            return null;
        }
        final RasterViagemParadaEntity entity = new RasterViagemParadaEntity();
        entity.setCodSolicitacao(codSolicitacao);
        entity.setOrdem(dto.getOrdem() != null ? dto.getOrdem() : ordemFallback);
        entity.setTipo(trim(dto.getTipo()));
        entity.setCodIbgeCidade(dto.getCodIbgeCidade());
        entity.setCnpjCliente(trim(dto.getCnpjCliente()));
        entity.setCodigoCliente(trim(dto.getCodigoCliente()));
        entity.setDataHoraPrevChegada(parseRasterDateTime(dto.getDataHoraPrevChegada()));
        entity.setDataHoraPrevSaida(parseRasterDateTime(dto.getDataHoraPrevSaida()));
        entity.setDataHoraRealChegada(parseRasterDateTime(dto.getDataHoraRealChegada()));
        entity.setDataHoraRealSaida(parseRasterDateTime(dto.getDataHoraRealSaida()));
        entity.setLatitude(dto.getLatitude());
        entity.setLongitude(dto.getLongitude());
        entity.setDentroPrazoRaster(trim(dto.getDentroPrazo()));
        entity.setDiferencaTempoRaster(trim(dto.getDiferencaTempo()));
        entity.setKmPercorridoEntrega(dto.getKmPercorridoEntrega());
        entity.setKmRestanteEntrega(dto.getKmRestanteEntrega());
        entity.setChegouNaEntrega(trim(dto.getChegouNaEntrega()));
        entity.setDataHoraUltimaPosicao(parseRasterDateTime(dto.getDataHoraUltimaPosicao()));
        entity.setLatitudeUltimaPosicao(dto.getLatitudeUltimaPosicao());
        entity.setLongitudeUltimaPosicao(dto.getLongitudeUltimaPosicao());
        entity.setReferenciaUltimaPosicao(trim(dto.getReferenciaUltimaPosicao()));
        entity.setMetadata(dto.getMetadata());
        entity.setDataExtracao(Instant.now());
        return entity;
    }

    static OffsetDateTime parseRasterDateTime(final String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        final String normalizado = value.trim();
        if (normalizado.startsWith("1900-01-01")) {
            return null;
        }
        try {
            return OffsetDateTime.parse(normalizado);
        } catch (final DateTimeParseException ignored) {
            // Fallbacks abaixo.
        }
        for (final DateTimeFormatter formatter : FORMATOS_DATA_HORA_SEM_OFFSET) {
            try {
                return LocalDateTime.parse(normalizado, formatter).atZone(ZONE_ID_PADRAO).toOffsetDateTime();
            } catch (final DateTimeParseException ignored) {
                // Tenta o proximo formato.
            }
        }
        try {
            return LocalDate.parse(normalizado).atStartOfDay(ZONE_ID_PADRAO).toOffsetDateTime();
        } catch (final DateTimeParseException ignored) {
            return null;
        }
    }

    private String trim(final String value) {
        return value == null ? null : value.trim();
    }

    private Integer normalizarTempoTotalViagemMin(final Integer value) {
        if (value == null || value < 0 || value > TEMPO_TOTAL_MAXIMO_VALIDO_MIN) {
            return null;
        }
        return value;
    }
}
