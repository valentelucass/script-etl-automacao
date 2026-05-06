package br.com.extrator.dominio.raster;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RasterParadaDTO {
    @JsonAlias({"Ordem", "ordem"})
    private Integer ordem;

    @JsonAlias({"Tipo", "tipo"})
    private String tipo;

    @JsonAlias({"CodIBGECidade", "codIBGECidade", "CodIbgeCidade"})
    private Long codIbgeCidade;

    @JsonAlias({"CNPJCliente", "cnpjCliente", "CnpjCliente"})
    private String cnpjCliente;

    @JsonAlias({"CodigoCliente", "codigoCliente"})
    private String codigoCliente;

    @JsonAlias({"DataHoraPrevChegada", "dataHoraPrevChegada"})
    private String dataHoraPrevChegada;

    @JsonAlias({"DataHoraPrevSaida", "dataHoraPrevSaida"})
    private String dataHoraPrevSaida;

    @JsonAlias({"DataHoraRealChegada", "dataHoraRealChegada"})
    private String dataHoraRealChegada;

    @JsonAlias({"DataHoraRealSaida", "dataHoraRealSaida"})
    private String dataHoraRealSaida;

    @JsonAlias({"Latitude", "latitude"})
    private BigDecimal latitude;

    @JsonAlias({"Longitude", "longitude"})
    private BigDecimal longitude;

    @JsonAlias({"DentroPrazo", "dentroPrazo"})
    private String dentroPrazo;

    @JsonAlias({"DiferencaTempo", "diferencaTempo"})
    private String diferencaTempo;

    @JsonAlias({"KmPercorridoEntrega", "kmPercorridoEntrega"})
    private BigDecimal kmPercorridoEntrega;

    @JsonAlias({"KmRestanteEntrega", "kmRestanteEntrega"})
    private BigDecimal kmRestanteEntrega;

    @JsonAlias({"ChegouNaEntrega", "chegouNaEntrega"})
    private String chegouNaEntrega;

    @JsonAlias({"DataHoraUltimaPosicao", "dataHoraUltimaPosicao"})
    private String dataHoraUltimaPosicao;

    @JsonAlias({"LatitudeUltimaPosicao", "latitudeUltimaPosicao"})
    private BigDecimal latitudeUltimaPosicao;

    @JsonAlias({"LongitudeUltimaPosicao", "longitudeUltimaPosicao"})
    private BigDecimal longitudeUltimaPosicao;

    @JsonAlias({"ReferenciaUltimaPosicao", "referenciaUltimaPosicao"})
    private String referenciaUltimaPosicao;

    @JsonIgnore
    private String metadata;

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

    public String getDataHoraPrevChegada() {
        return dataHoraPrevChegada;
    }

    public void setDataHoraPrevChegada(final String dataHoraPrevChegada) {
        this.dataHoraPrevChegada = dataHoraPrevChegada;
    }

    public String getDataHoraPrevSaida() {
        return dataHoraPrevSaida;
    }

    public void setDataHoraPrevSaida(final String dataHoraPrevSaida) {
        this.dataHoraPrevSaida = dataHoraPrevSaida;
    }

    public String getDataHoraRealChegada() {
        return dataHoraRealChegada;
    }

    public void setDataHoraRealChegada(final String dataHoraRealChegada) {
        this.dataHoraRealChegada = dataHoraRealChegada;
    }

    public String getDataHoraRealSaida() {
        return dataHoraRealSaida;
    }

    public void setDataHoraRealSaida(final String dataHoraRealSaida) {
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

    public String getDentroPrazo() {
        return dentroPrazo;
    }

    public void setDentroPrazo(final String dentroPrazo) {
        this.dentroPrazo = dentroPrazo;
    }

    public String getDiferencaTempo() {
        return diferencaTempo;
    }

    public void setDiferencaTempo(final String diferencaTempo) {
        this.diferencaTempo = diferencaTempo;
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

    public String getDataHoraUltimaPosicao() {
        return dataHoraUltimaPosicao;
    }

    public void setDataHoraUltimaPosicao(final String dataHoraUltimaPosicao) {
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
}
