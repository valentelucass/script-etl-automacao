package br.com.extrator.analises.indicadoresgestao;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class IndicadoresGestaoViewSqlTest {

    @Test
    void fretesPowerBiDeveExporColunasOficiaisDePerformanceECubagem() throws IOException {
        final String sql = lerSql("database/views/012_criar_view_fretes_powerbi.sql");

        assertContem(sql, "[Nº Minuta]");
        assertContem(sql, "[Filial Emissora]");
        assertContem(sql, "[Responsável pela Região de Destino]");
        assertContem(sql, "[Data de Finalização]");
        assertContem(sql, "[Finalização da Performance]");
        assertContem(sql, "[Performance Diferença de Dias]");
        assertContem(sql, "[Performance Status]");
        assertContem(sql, "[Performance Status Dif de Dias]");
        assertContem(sql, "[Performance Status Dif de Dias Oficial]");
        assertContem(sql, "[Peso Real]");
        assertContem(sql, "[Peso Cubado]");
        assertContem(sql, "[Total M3]");
        assertContem(sql, "[Cortesia]");
        assertContem(sql, "[Cortesia Flag]");
        assertContem(sql, "NULLIF(LTRIM(RTRIM(f.nfse_series)), '')");
        assertContem(sql, "LEFT JOIN dbo.localizacao_cargas");
    }

    @Test
    void manifestosPowerBiDeveExporLocalDeDescarregamentoComNomeDoNegocio() throws IOException {
        final String sql = lerSql("database/views/018_criar_view_manifestos_powerbi.sql");

        assertContem(sql, "[Filial Emissora]");
        assertContem(sql, "[Local de Descarregamento]");
    }

    @Test
    void inventarioPowerBiDeveExporFilialEmissoraDoFrete() throws IOException {
        final String sql = lerSql("database/views/020_criar_view_inventario_powerbi.sql");

        assertContem(sql, "[Filial da Ordem de Conferência]");
        assertContem(sql, "[Filial Emissora do Frete]");
        assertContem(sql, "[Data de Finalização]");
        assertContem(sql, "CheckIn::Order::Return");
        assertContem(sql, "'Retorno'");
        assertContem(sql, "LEFT JOIN dbo.fretes");
    }

    @Test
    void localizacaoCargasPowerBiDeveExporResponsavelPelaRegiaoDeDestino() throws IOException {
        final String sql = lerSql("database/views/017_criar_view_localizacao_cargas_powerbi.sql");

        assertContem(sql, "[Responsável pela Região de Destino]");
    }

    private String lerSql(final String caminhoRelativo) throws IOException {
        return Files.readString(Path.of(caminhoRelativo));
    }

    private void assertContem(final String sql, final String trechoEsperado) {
        assertTrue(
            sql.contains(trechoEsperado),
            "Esperado encontrar o trecho '" + trechoEsperado + "' no SQL da view."
        );
    }
}
