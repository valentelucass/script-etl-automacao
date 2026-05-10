package br.com.extrator.aplicacao.extracao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.extrator.aplicacao.contexto.AplicacaoContexto;
import br.com.extrator.aplicacao.pipeline.PipelineStep;
import br.com.extrator.aplicacao.pipeline.runtime.StepExecutionResult;
import br.com.extrator.aplicacao.portas.DataExportGateway;
import br.com.extrator.aplicacao.portas.GraphQLGateway;
import br.com.extrator.aplicacao.portas.RasterGateway;

class PlanejadorEscopoExtracaoIntervaloTest {

    private Object gatewayGraphqlAnterior;
    private Object gatewayDataExportAnterior;
    private Object gatewayRasterAnterior;
    private Object rasterHabilitadoAnterior;
    private String rasterEnabledAnterior;

    @BeforeEach
    void prepararContexto() throws Exception {
        gatewayGraphqlAnterior = lerCampoContexto("graphQLGateway");
        gatewayDataExportAnterior = lerCampoContexto("dataExportGateway");
        gatewayRasterAnterior = lerCampoContexto("rasterGateway");
        rasterHabilitadoAnterior = lerCampoContexto("rasterHabilitadoParaExecucao");
        rasterEnabledAnterior = System.getProperty("RASTER_ENABLED");
        System.setProperty("RASTER_ENABLED", "false");
        AplicacaoContexto.registrar((GraphQLGateway) (dataInicio, dataFim, entidade) ->
            StepExecutionResult.builder("graphql:" + entidade, entidade).build()
        );
        AplicacaoContexto.registrar((DataExportGateway) (dataInicio, dataFim, entidade) ->
            StepExecutionResult.builder("dataexport:" + entidade, entidade).build()
        );
        AplicacaoContexto.registrar((RasterGateway) (dataInicio, dataFim, entidade) ->
            StepExecutionResult.builder("raster:" + entidade, entidade).build()
        );
    }

    @AfterEach
    void restaurarContexto() throws Exception {
        escreverCampoContexto("graphQLGateway", gatewayGraphqlAnterior);
        escreverCampoContexto("dataExportGateway", gatewayDataExportAnterior);
        escreverCampoContexto("rasterGateway", gatewayRasterAnterior);
        escreverCampoContexto("rasterHabilitadoParaExecucao", rasterHabilitadoAnterior);
        if (rasterEnabledAnterior == null) {
            System.clearProperty("RASTER_ENABLED");
        } else {
            System.setProperty("RASTER_ENABLED", rasterEnabledAnterior);
        }
    }

    @Test
    void deveCriarStepsGranularesParaEscopoCompletoSemFaturasGraphql() {
        final PlanejadorEscopoExtracaoIntervalo planejador = new PlanejadorEscopoExtracaoIntervalo();

        final List<String> steps = planejador.criarSteps(null, null, false)
            .stream()
            .map(PipelineStep::obterNomeEtapa)
            .toList();

        assertEquals(
            List.of(
                "graphql:usuarios_sistema",
                "graphql:coletas",
                "graphql:fretes",
                "dataexport:manifestos",
                "dataexport:cotacoes",
                "dataexport:localizacao_cargas",
                "dataexport:contas_a_pagar",
                "dataexport:faturas_por_cliente",
                "dataexport:inventario",
                "dataexport:sinistros"
            ),
            steps
        );
    }

    @Test
    void deveCriarStepsGranularesParaApiGraphqlComFaturasQuandoNaoHouverEntidadeEspecifica() {
        final PlanejadorEscopoExtracaoIntervalo planejador = new PlanejadorEscopoExtracaoIntervalo();

        final List<String> steps = planejador.criarSteps("graphql", null, true)
            .stream()
            .map(PipelineStep::obterNomeEtapa)
            .toList();

        assertEquals(
            List.of(
                "graphql:usuarios_sistema",
                "graphql:coletas",
                "graphql:fretes",
                "graphql:faturas_graphql"
            ),
            steps
        );
    }

    @Test
    void deveCriarStepRasterQuandoApiRasterForInformada() {
        final PlanejadorEscopoExtracaoIntervalo planejador = new PlanejadorEscopoExtracaoIntervalo();

        final List<String> steps = planejador.criarSteps("raster", null, false)
            .stream()
            .map(PipelineStep::obterNomeEtapa)
            .toList();

        assertEquals(List.of("raster:raster_viagens"), steps);
    }

    @Test
    void deveCriarStepRasterSemExigirGatewaysGraphqlOuDataExport() throws Exception {
        escreverCampoContexto("graphQLGateway", null);
        escreverCampoContexto("dataExportGateway", null);
        final PlanejadorEscopoExtracaoIntervalo planejador = new PlanejadorEscopoExtracaoIntervalo();

        final List<String> steps = planejador.criarSteps("raster", null, false)
            .stream()
            .map(PipelineStep::obterNomeEtapa)
            .toList();

        assertEquals(List.of("raster:raster_viagens"), steps);
    }

    @Test
    void deveCriarStepDataExportSemExigirGatewayGraphql() throws Exception {
        escreverCampoContexto("graphQLGateway", null);
        final PlanejadorEscopoExtracaoIntervalo planejador = new PlanejadorEscopoExtracaoIntervalo();

        final List<String> steps = planejador.criarSteps("dataexport", "sinistros", false)
            .stream()
            .map(PipelineStep::obterNomeEtapa)
            .toList();

        assertEquals(List.of("dataexport:sinistros"), steps);
    }

    @Test
    void deveCriarStepGraphqlSemExigirGatewayDataExport() throws Exception {
        escreverCampoContexto("dataExportGateway", null);
        final PlanejadorEscopoExtracaoIntervalo planejador = new PlanejadorEscopoExtracaoIntervalo();

        final List<String> steps = planejador.criarSteps("graphql", "coletas", false)
            .stream()
            .map(PipelineStep::obterNomeEtapa)
            .toList();

        assertEquals(List.of("graphql:coletas"), steps);
    }

    @Test
    void deveIncluirRasterNoEscopoCompletoQuandoHabilitado() {
        AplicacaoContexto.registrarRasterHabilitadoParaExecucao(true);
        final PlanejadorEscopoExtracaoIntervalo planejador = new PlanejadorEscopoExtracaoIntervalo();

        final List<String> steps = planejador.criarSteps(null, null, false)
            .stream()
            .map(PipelineStep::obterNomeEtapa)
            .toList();

        assertEquals(
            List.of(
                "graphql:usuarios_sistema",
                "graphql:coletas",
                "graphql:fretes",
                "dataexport:manifestos",
                "dataexport:cotacoes",
                "dataexport:localizacao_cargas",
                "dataexport:contas_a_pagar",
                "dataexport:faturas_por_cliente",
                "dataexport:inventario",
                "dataexport:sinistros",
                "raster:raster_viagens"
            ),
            steps
        );
    }

    @Test
    void deveValidarLogsDasDuasTabelasRasterNoEscopoRaster() {
        final PlanejadorEscopoExtracaoIntervalo planejador = new PlanejadorEscopoExtracaoIntervalo();

        assertEquals(
            java.util.Set.of("raster_viagens", "raster_viagem_paradas"),
            planejador.determinarEntidadesObrigatoriasParaVolume("raster", null, false)
        );
    }

    private Object lerCampoContexto(final String nomeCampo) throws Exception {
        final Field campo = AplicacaoContexto.class.getDeclaredField(nomeCampo);
        campo.setAccessible(true);
        return campo.get(null);
    }

    private void escreverCampoContexto(final String nomeCampo, final Object valor) throws Exception {
        final Field campo = AplicacaoContexto.class.getDeclaredField(nomeCampo);
        campo.setAccessible(true);
        campo.set(null, valor);
    }
}
