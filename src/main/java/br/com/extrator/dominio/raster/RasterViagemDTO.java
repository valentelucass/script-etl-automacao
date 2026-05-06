package br.com.extrator.dominio.raster;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RasterViagemDTO {
    @JsonAlias({"CodSolicitacao", "codSolicitacao"})
    private Long codSolicitacao;

    @JsonAlias({"Sequencial", "sequencial"})
    private Long sequencial;

    @JsonAlias({"CodFilial", "codFilial"})
    private Integer codFilial;

    @JsonAlias({"StatusViagem", "statusViagem"})
    private String statusViagem;

    @JsonAlias({"PlacaVeiculo", "placaVeiculo"})
    private String placaVeiculo;

    @JsonAlias({"PlacaCarreta1", "placaCarreta1"})
    private String placaCarreta1;

    @JsonAlias({"PlacaCarreta02", "PlacaCarreta2", "placaCarreta2"})
    private String placaCarreta2;

    @JsonAlias({"PlacaCarreta3", "placaCarreta3"})
    private String placaCarreta3;

    @JsonAlias({"CPFMotorista1", "CpfMotorista1", "cpfMotorista1"})
    private String cpfMotorista1;

    @JsonAlias({"CPFMotorista2", "CpfMotorista2", "cpfMotorista2"})
    private String cpfMotorista2;

    @JsonAlias({"CNPJClienteOrig", "CnpjClienteOrig", "cnpjClienteOrig"})
    private String cnpjClienteOrig;

    @JsonAlias({"CNPJClienteDest", "CnpjClienteDest", "cnpjClienteDest"})
    private String cnpjClienteDest;

    @JsonAlias({"CodIBGECidadeOrig", "CodIbgeCidadeOrig", "codIbgeCidadeOrig"})
    private Long codIbgeCidadeOrig;

    @JsonAlias({"CodIBGECidadeDest", "CodIbgeCidadeDest", "codIbgeCidadeDest"})
    private Long codIbgeCidadeDest;

    @JsonAlias({"DataHoraPrevIni", "dataHoraPrevIni"})
    private String dataHoraPrevIni;

    @JsonAlias({"DataHoraPrevFim", "dataHoraPrevFim"})
    private String dataHoraPrevFim;

    @JsonAlias({"DataHoraRealIni", "dataHoraRealIni"})
    private String dataHoraRealIni;

    @JsonAlias({"DataHoraRealFim", "dataHoraRealFim"})
    private String dataHoraRealFim;

    @JsonAlias({"DataHoraIdentificouFimViagem", "dataHoraIdentificouFimViagem"})
    private String dataHoraIdentificouFimViagem;

    @JsonAlias({"TempoTotalViagem", "tempoTotalViagem"})
    private Integer tempoTotalViagem;

    @JsonAlias({"DentroPrazo", "dentroPrazo"})
    private String dentroPrazo;

    @JsonAlias({"PercentualAtraso", "percentualAtraso"})
    private BigDecimal percentualAtraso;

    @JsonAlias({"RodouForaHorario", "rodouForaHorario"})
    private String rodouForaHorario;

    @JsonAlias({"VelocidadeMedia", "velocidadeMedia"})
    private BigDecimal velocidadeMedia;

    @JsonAlias({"EventosVelocidade", "eventosVelocidade"})
    private Integer eventosVelocidade;

    @JsonAlias({"DesviosDeRota", "desviosDeRota"})
    private Integer desviosDeRota;

    @JsonAlias({"LinkTimeLine", "LinkTimeline", "linkTimeLine", "linkTimeline"})
    private String linkTimeline;

    @JsonAlias({"Rota", "rota"})
    private RasterRotaDTO rota;

    @JsonAlias({"ColetasEntregas", "coletasEntregas"})
    private List<RasterParadaDTO> coletasEntregas = new ArrayList<>();

    @JsonIgnore
    private String metadata;

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

    public String getDataHoraPrevIni() {
        return dataHoraPrevIni;
    }

    public void setDataHoraPrevIni(final String dataHoraPrevIni) {
        this.dataHoraPrevIni = dataHoraPrevIni;
    }

    public String getDataHoraPrevFim() {
        return dataHoraPrevFim;
    }

    public void setDataHoraPrevFim(final String dataHoraPrevFim) {
        this.dataHoraPrevFim = dataHoraPrevFim;
    }

    public String getDataHoraRealIni() {
        return dataHoraRealIni;
    }

    public void setDataHoraRealIni(final String dataHoraRealIni) {
        this.dataHoraRealIni = dataHoraRealIni;
    }

    public String getDataHoraRealFim() {
        return dataHoraRealFim;
    }

    public void setDataHoraRealFim(final String dataHoraRealFim) {
        this.dataHoraRealFim = dataHoraRealFim;
    }

    public String getDataHoraIdentificouFimViagem() {
        return dataHoraIdentificouFimViagem;
    }

    public void setDataHoraIdentificouFimViagem(final String dataHoraIdentificouFimViagem) {
        this.dataHoraIdentificouFimViagem = dataHoraIdentificouFimViagem;
    }

    public Integer getTempoTotalViagem() {
        return tempoTotalViagem;
    }

    public void setTempoTotalViagem(final Integer tempoTotalViagem) {
        this.tempoTotalViagem = tempoTotalViagem;
    }

    public String getDentroPrazo() {
        return dentroPrazo;
    }

    public void setDentroPrazo(final String dentroPrazo) {
        this.dentroPrazo = dentroPrazo;
    }

    public BigDecimal getPercentualAtraso() {
        return percentualAtraso;
    }

    public void setPercentualAtraso(final BigDecimal percentualAtraso) {
        this.percentualAtraso = percentualAtraso;
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

    public String getLinkTimeline() {
        return linkTimeline;
    }

    public void setLinkTimeline(final String linkTimeline) {
        this.linkTimeline = linkTimeline;
    }

    public RasterRotaDTO getRota() {
        return rota;
    }

    public void setRota(final RasterRotaDTO rota) {
        this.rota = rota;
    }

    public List<RasterParadaDTO> getColetasEntregas() {
        return coletasEntregas == null ? List.of() : coletasEntregas;
    }

    public void setColetasEntregas(final List<RasterParadaDTO> coletasEntregas) {
        this.coletasEntregas = coletasEntregas == null ? new ArrayList<>() : coletasEntregas;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(final String metadata) {
        this.metadata = metadata;
    }
}
