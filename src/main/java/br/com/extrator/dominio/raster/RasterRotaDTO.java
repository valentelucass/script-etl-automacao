package br.com.extrator.dominio.raster;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RasterRotaDTO {
    @JsonAlias({"CodRota", "codRota"})
    private Long codRota;

    @JsonAlias({"Descricao", "descricao", "RotaDescricao", "DescRota"})
    private String descricao;

    public Long getCodRota() {
        return codRota;
    }

    public void setCodRota(final Long codRota) {
        this.codRota = codRota;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(final String descricao) {
        this.descricao = descricao;
    }
}
