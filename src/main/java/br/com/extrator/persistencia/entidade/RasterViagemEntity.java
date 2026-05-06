package br.com.extrator.persistencia.entidade;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;

public class RasterViagemEntity {
    private Long codSolicitacao;
    private Long sequencial;
    private Integer codFilial;
    private String statusViagem;
    private String placaVeiculo;
    private String placaCarreta1;
    private String placaCarreta2;
    private String placaCarreta3;
    private String cpfMotorista1;
    private String cpfMotorista2;
    private String cnpjClienteOrig;
    private String cnpjClienteDest;
    private Long codIbgeCidadeOrig;
    private Long codIbgeCidadeDest;
    private OffsetDateTime dataHoraPrevIni;
    private OffsetDateTime dataHoraPrevFim;
    private OffsetDateTime dataHoraRealIni;
    private OffsetDateTime dataHoraRealFim;
    private OffsetDateTime dataHoraIdentificouFimViagem;
    private Integer tempoTotalViagemMin;
    private String dentroPrazoRaster;
    private BigDecimal percentualAtrasoRaster;
    private String rodouForaHorario;
    private BigDecimal velocidadeMedia;
    private Integer eventosVelocidade;
    private Integer desviosDeRota;
    private Long codRota;
    private String rotaDescricao;
    private String linkTimeline;
    private String metadata;
    private Instant dataExtracao;

    public Long getCodSolicitacao() {
        return codSolicitacao;
    }

    public void setCodSolicitacao(final Long codSolicitacao) {
        this.codSolicitacao = codSolicitacao;
    }

    public Long getSequencial() {
        return sequencial;
    }

    public void setSequencial(final Long sequencial) {
        this.sequencial = sequencial;
    }

    public Integer getCodFilial() {
        return codFilial;
    }

    public void setCodFilial(final Integer codFilial) {
        this.codFilial = codFilial;
    }

    public String getStatusViagem() {
        return statusViagem;
    }

    public void setStatusViagem(final String statusViagem) {
        this.statusViagem = statusViagem;
    }

    public String getPlacaVeiculo() {
        return placaVeiculo;
    }

    public void setPlacaVeiculo(final String placaVeiculo) {
        this.placaVeiculo = placaVeiculo;
    }

    public String getPlacaCarreta1() {
        return placaCarreta1;
    }

    public void setPlacaCarreta1(final String placaCarreta1) {
        this.placaCarreta1 = placaCarreta1;
    }

    public String getPlacaCarreta2() {
        return placaCarreta2;
    }

    public void setPlacaCarreta2(final String placaCarreta2) {
        this.placaCarreta2 = placaCarreta2;
    }

    public String getPlacaCarreta3() {
        return placaCarreta3;
    }

    public void setPlacaCarreta3(final String placaCarreta3) {
        this.placaCarreta3 = placaCarreta3;
    }

    public String getCpfMotorista1() {
        return cpfMotorista1;
    }

    public void setCpfMotorista1(final String cpfMotorista1) {
        this.cpfMotorista1 = cpfMotorista1;
    }

    public String getCpfMotorista2() {
        return cpfMotorista2;
    }

    public void setCpfMotorista2(final String cpfMotorista2) {
        this.cpfMotorista2 = cpfMotorista2;
    }

    public String getCnpjClienteOrig() {
        return cnpjClienteOrig;
    }

    public void setCnpjClienteOrig(final String cnpjClienteOrig) {
        this.cnpjClienteOrig = cnpjClienteOrig;
    }

    public String getCnpjClienteDest() {
        return cnpjClienteDest;
    }

    public void setCnpjClienteDest(final String cnpjClienteDest) {
        this.cnpjClienteDest = cnpjClienteDest;
    }

    public Long getCodIbgeCidadeOrig() {
        return codIbgeCidadeOrig;
    }

    public void setCodIbgeCidadeOrig(final Long codIbgeCidadeOrig) {
        this.codIbgeCidadeOrig = codIbgeCidadeOrig;
    }

    public Long getCodIbgeCidadeDest() {
        return codIbgeCidadeDest;
    }

    public void setCodIbgeCidadeDest(final Long codIbgeCidadeDest) {
        this.codIbgeCidadeDest = codIbgeCidadeDest;
    }

    public OffsetDateTime getDataHoraPrevIni() {
        return dataHoraPrevIni;
    }

    public void setDataHoraPrevIni(final OffsetDateTime dataHoraPrevIni) {
        this.dataHoraPrevIni = dataHoraPrevIni;
    }

    public OffsetDateTime getDataHoraPrevFim() {
        return dataHoraPrevFim;
    }

    public void setDataHoraPrevFim(final OffsetDateTime dataHoraPrevFim) {
        this.dataHoraPrevFim = dataHoraPrevFim;
    }

    public OffsetDateTime getDataHoraRealIni() {
        return dataHoraRealIni;
    }

    public void setDataHoraRealIni(final OffsetDateTime dataHoraRealIni) {
        this.dataHoraRealIni = dataHoraRealIni;
    }

    public OffsetDateTime getDataHoraRealFim() {
        return dataHoraRealFim;
    }

    public void setDataHoraRealFim(final OffsetDateTime dataHoraRealFim) {
        this.dataHoraRealFim = dataHoraRealFim;
    }

    public OffsetDateTime getDataHoraIdentificouFimViagem() {
        return dataHoraIdentificouFimViagem;
    }

    public void setDataHoraIdentificouFimViagem(final OffsetDateTime dataHoraIdentificouFimViagem) {
        this.dataHoraIdentificouFimViagem = dataHoraIdentificouFimViagem;
    }

    public Integer getTempoTotalViagemMin() {
        return tempoTotalViagemMin;
    }

    public void setTempoTotalViagemMin(final Integer tempoTotalViagemMin) {
        this.tempoTotalViagemMin = tempoTotalViagemMin;
    }

    public String getDentroPrazoRaster() {
        return dentroPrazoRaster;
    }

    public void setDentroPrazoRaster(final String dentroPrazoRaster) {
        this.dentroPrazoRaster = dentroPrazoRaster;
    }

    public BigDecimal getPercentualAtrasoRaster() {
        return percentualAtrasoRaster;
    }

    public void setPercentualAtrasoRaster(final BigDecimal percentualAtrasoRaster) {
        this.percentualAtrasoRaster = percentualAtrasoRaster;
    }

    public String getRodouForaHorario() {
        return rodouForaHorario;
    }

    public void setRodouForaHorario(final String rodouForaHorario) {
        this.rodouForaHorario = rodouForaHorario;
    }

    public BigDecimal getVelocidadeMedia() {
        return velocidadeMedia;
    }

    public void setVelocidadeMedia(final BigDecimal velocidadeMedia) {
        this.velocidadeMedia = velocidadeMedia;
    }

    public Integer getEventosVelocidade() {
        return eventosVelocidade;
    }

    public void setEventosVelocidade(final Integer eventosVelocidade) {
        this.eventosVelocidade = eventosVelocidade;
    }

    public Integer getDesviosDeRota() {
        return desviosDeRota;
    }

    public void setDesviosDeRota(final Integer desviosDeRota) {
        this.desviosDeRota = desviosDeRota;
    }

    public Long getCodRota() {
        return codRota;
    }

    public void setCodRota(final Long codRota) {
        this.codRota = codRota;
    }

    public String getRotaDescricao() {
        return rotaDescricao;
    }

    public void setRotaDescricao(final String rotaDescricao) {
        this.rotaDescricao = rotaDescricao;
    }

    public String getLinkTimeline() {
        return linkTimeline;
    }

    public void setLinkTimeline(final String linkTimeline) {
        this.linkTimeline = linkTimeline;
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
