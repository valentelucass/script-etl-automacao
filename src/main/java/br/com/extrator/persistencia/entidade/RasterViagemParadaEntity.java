package br.com.extrator.persistencia.entidade;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

public class RasterViagemParadaEntity {
    private Long codSolicitacao;
    private Integer ordem;
    private String tipo;
    private Long codIbgeCidade;
    private String cnpjCliente;
    private String codigoCliente;
    private OffsetDateTime dataHoraPrevChegada;
    private OffsetDateTime dataHoraPrevSaida;
    private OffsetDateTime dataHoraRealChegada;
    private OffsetDateTime dataHoraRealSaida;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String dentroPrazoRaster;
    private String diferencaTempoRaster;
    private BigDecimal kmPercorridoEntrega;
    private BigDecimal kmRestanteEntrega;
    private String chegouNaEntrega;
    private OffsetDateTime dataHoraUltimaPosicao;
    private BigDecimal latitudeUltimaPosicao;
    private BigDecimal longitudeUltimaPosicao;
    private String referenciaUltimaPosicao;
    private String metadata;
    private Instant dataExtracao;

    public Long getCodSolicitacao() {
        return codSolicitacao;
    }

    public void setCodSolicitacao(final Long codSolicitacao) {
        this.codSolicitacao = codSolicitacao;
    }

    public Integer getOrdem() {
        return ordem;
    }

    public void setOrdem(final Integer ordem) {
        this.ordem = ordem;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(final String tipo) {
        this.tipo = tipo;
    }

    public Long getCodIbgeCidade() {
        return codIbgeCidade;
    }

    public void setCodIbgeCidade(final Long codIbgeCidade) {
        this.codIbgeCidade = codIbgeCidade;
    }

    public String getCnpjCliente() {
        return cnpjCliente;
    }

    public void setCnpjCliente(final String cnpjCliente) {
        this.cnpjCliente = cnpjCliente;
    }

    public String getCodigoCliente() {
        return codigoCliente;
    }

    public void setCodigoCliente(final String codigoCliente) {
        this.codigoCliente = codigoCliente;
    }

    public OffsetDateTime getDataHoraPrevChegada() {
        return dataHoraPrevChegada;
    }

    public void setDataHoraPrevChegada(final OffsetDateTime dataHoraPrevChegada) {
        this.dataHoraPrevChegada = dataHoraPrevChegada;
    }

    public OffsetDateTime getDataHoraPrevSaida() {
        return dataHoraPrevSaida;
    }

    public void setDataHoraPrevSaida(final OffsetDateTime dataHoraPrevSaida) {
        this.dataHoraPrevSaida = dataHoraPrevSaida;
    }

    public OffsetDateTime getDataHoraRealChegada() {
        return dataHoraRealChegada;
    }

    public void setDataHoraRealChegada(final OffsetDateTime dataHoraRealChegada) {
        this.dataHoraRealChegada = dataHoraRealChegada;
    }

    public OffsetDateTime getDataHoraRealSaida() {
        return dataHoraRealSaida;
    }

    public void setDataHoraRealSaida(final OffsetDateTime dataHoraRealSaida) {
        this.dataHoraRealSaida = dataHoraRealSaida;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(final BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(final BigDecimal longitude) {
        this.longitude = longitude;
    }

    public String getDentroPrazoRaster() {
        return dentroPrazoRaster;
    }

    public void setDentroPrazoRaster(final String dentroPrazoRaster) {
        this.dentroPrazoRaster = dentroPrazoRaster;
    }

    public String getDiferencaTempoRaster() {
        return diferencaTempoRaster;
    }

    public void setDiferencaTempoRaster(final String diferencaTempoRaster) {
        this.diferencaTempoRaster = diferencaTempoRaster;
    }

    public BigDecimal getKmPercorridoEntrega() {
        return kmPercorridoEntrega;
    }

    public void setKmPercorridoEntrega(final BigDecimal kmPercorridoEntrega) {
        this.kmPercorridoEntrega = kmPercorridoEntrega;
    }

    public BigDecimal getKmRestanteEntrega() {
        return kmRestanteEntrega;
    }

    public void setKmRestanteEntrega(final BigDecimal kmRestanteEntrega) {
        this.kmRestanteEntrega = kmRestanteEntrega;
    }

    public String getChegouNaEntrega() {
        return chegouNaEntrega;
    }

    public void setChegouNaEntrega(final String chegouNaEntrega) {
        this.chegouNaEntrega = chegouNaEntrega;
    }

    public OffsetDateTime getDataHoraUltimaPosicao() {
        return dataHoraUltimaPosicao;
    }

    public void setDataHoraUltimaPosicao(final OffsetDateTime dataHoraUltimaPosicao) {
        this.dataHoraUltimaPosicao = dataHoraUltimaPosicao;
    }

    public BigDecimal getLatitudeUltimaPosicao() {
        return latitudeUltimaPosicao;
    }

    public void setLatitudeUltimaPosicao(final BigDecimal latitudeUltimaPosicao) {
        this.latitudeUltimaPosicao = latitudeUltimaPosicao;
    }

    public BigDecimal getLongitudeUltimaPosicao() {
        return longitudeUltimaPosicao;
    }

    public void setLongitudeUltimaPosicao(final BigDecimal longitudeUltimaPosicao) {
        this.longitudeUltimaPosicao = longitudeUltimaPosicao;
    }

    public String getReferenciaUltimaPosicao() {
        return referenciaUltimaPosicao;
    }

    public void setReferenciaUltimaPosicao(final String referenciaUltimaPosicao) {
        this.referenciaUltimaPosicao = referenciaUltimaPosicao;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(final String metadata) {
        this.metadata = metadata;
    }

    public Instant getDataExtracao() {
        return dataExtracao;
    }

    public void setDataExtracao(final Instant dataExtracao) {
        this.dataExtracao = dataExtracao;
    }
}
