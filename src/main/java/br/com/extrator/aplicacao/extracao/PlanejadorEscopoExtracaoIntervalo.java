/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/extracao/PlanejadorEscopoExtracaoIntervalo.java
Classe  : PlanejadorEscopoExtracaoIntervalo (class)
Pacote  : br.com.extrator.aplicacao.extracao
Modulo  : Use Case - Extracao

Papel   : Planeja escopo (steps e entidades) de extracao conforme filtros (API, entidade, flags).

Conecta com:
- ExtracaoPorIntervaloUseCase (consulta para planejar steps e entidades)
- GraphQLPipelineStep, DataExportPipelineStep (cria instancias)
- AplicacaoContexto (obtem gateways)

Fluxo geral:
1) Use case passa filtros (apiEspecifica, entidadeEspecifica, incluirFaturasGraphQL).
2) Planejar determina quais entidades sao afetadas (para validacao e integridade).
3) Planejar cria lista de PipelineSteps a orquestrar.
4) Retorna escopo: passos, entidades para volume, entidades para integridade.

Estrutura interna:
Metodos principais:
- criarSteps(): retorna List<PipelineStep> conforme filtros (GraphQL, DataExport, Faturas).
- determinarEntidadesParaLimite(): quais entidades validar limite de extracao.
- determinarEntidadesObrigatoriasParaVolume(): entidades criticas para validacao de volume.
- determinarEntidadesEsperadasParaIntegridade(): entidades esperadas no schema/chaves.
- normalizarEntidade(): mapeia sinonimos para constantes (coleta, frete, manifesto, etc).
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.extracao;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import br.com.extrator.aplicacao.contexto.AplicacaoContexto;
import br.com.extrator.aplicacao.pipeline.DataExportPipelineStep;
import br.com.extrator.aplicacao.pipeline.GraphQLPipelineStep;
import br.com.extrator.aplicacao.pipeline.PipelineStep;
import br.com.extrator.aplicacao.pipeline.RasterPipelineStep;
import br.com.extrator.aplicacao.portas.DataExportGateway;
import br.com.extrator.aplicacao.portas.GraphQLGateway;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

final class PlanejadorEscopoExtracaoIntervalo {

    List<String> determinarEntidadesParaLimite(
        final String apiEspecifica,
        final String entidadeEspecifica,
        final boolean incluirFaturasGraphQL
    ) {
        final List<String> entidadesParaValidar = new ArrayList<>();

        if (entidadeEspecifica != null && !entidadeEspecifica.isEmpty()) {
            final String entidadeNormalizada = normalizarEntidade(entidadeEspecifica);
            entidadesParaValidar.add(entidadeNormalizada == null ? entidadeEspecifica : entidadeNormalizada);
            return entidadesParaValidar;
        }

        if (apiEspecifica != null && !apiEspecifica.isEmpty()) {
            if ("graphql".equalsIgnoreCase(apiEspecifica)) {
                entidadesParaValidar.add(ConstantesEntidades.COLETAS);
                entidadesParaValidar.add(ConstantesEntidades.FRETES);
                if (incluirFaturasGraphQL) {
                    entidadesParaValidar.add(ConstantesEntidades.FATURAS_GRAPHQL);
                }
            } else if ("dataexport".equalsIgnoreCase(apiEspecifica)) {
                entidadesParaValidar.add(ConstantesEntidades.MANIFESTOS);
                entidadesParaValidar.add(ConstantesEntidades.COTACOES);
                entidadesParaValidar.add(ConstantesEntidades.LOCALIZACAO_CARGAS);
                entidadesParaValidar.add(ConstantesEntidades.CONTAS_A_PAGAR);
                entidadesParaValidar.add(ConstantesEntidades.FATURAS_POR_CLIENTE);
                entidadesParaValidar.add(ConstantesEntidades.INVENTARIO);
                entidadesParaValidar.add(ConstantesEntidades.SINISTROS);
            } else if (ConstantesEntidades.RASTER.equalsIgnoreCase(apiEspecifica)) {
                entidadesParaValidar.add(ConstantesEntidades.RASTER_VIAGENS);
            }
            return entidadesParaValidar;
        }

        entidadesParaValidar.add(ConstantesEntidades.COLETAS);
        entidadesParaValidar.add(ConstantesEntidades.FRETES);
        entidadesParaValidar.add(ConstantesEntidades.MANIFESTOS);
        entidadesParaValidar.add(ConstantesEntidades.COTACOES);
        entidadesParaValidar.add(ConstantesEntidades.LOCALIZACAO_CARGAS);
        entidadesParaValidar.add(ConstantesEntidades.CONTAS_A_PAGAR);
        entidadesParaValidar.add(ConstantesEntidades.FATURAS_POR_CLIENTE);
        entidadesParaValidar.add(ConstantesEntidades.INVENTARIO);
        entidadesParaValidar.add(ConstantesEntidades.SINISTROS);
        if (incluirFaturasGraphQL) {
            entidadesParaValidar.add(ConstantesEntidades.FATURAS_GRAPHQL);
        }
        if (AplicacaoContexto.rasterHabilitadoParaExecucao()) {
            entidadesParaValidar.add(ConstantesEntidades.RASTER_VIAGENS);
        }

        return entidadesParaValidar;
    }

    List<PipelineStep> criarSteps(
        final String apiEspecifica,
        final String entidadeEspecifica,
        final boolean incluirFaturasGraphQL
    ) {
        final String entidadeNormalizada = normalizarEntidade(entidadeEspecifica);
        final List<PipelineStep> steps = new ArrayList<>();
        final GraphQLGateway graphQLGateway = AplicacaoContexto.graphQLGateway();
        final DataExportGateway dataExportGateway = AplicacaoContexto.dataExportGateway();

        if (apiEspecifica == null || apiEspecifica.isBlank()) {
            adicionarStepsGraphQLGranulares(steps, graphQLGateway, incluirFaturasGraphQL);
            adicionarStepsDataExportGranulares(steps, dataExportGateway);
            if (AplicacaoContexto.rasterHabilitadoParaExecucao()) {
                steps.add(new RasterPipelineStep(AplicacaoContexto.rasterGateway(), ConstantesEntidades.RASTER_VIAGENS));
            }
            return steps;
        }

        if ("graphql".equalsIgnoreCase(apiEspecifica)) {
            if (entidadeNormalizada == null) {
                adicionarStepsGraphQLGranulares(steps, graphQLGateway, incluirFaturasGraphQL);
                return steps;
            }
            steps.add(new GraphQLPipelineStep(graphQLGateway, entidadeNormalizada));
            return steps;
        }

        if ("dataexport".equalsIgnoreCase(apiEspecifica)) {
            if (entidadeNormalizada == null) {
                adicionarStepsDataExportGranulares(steps, dataExportGateway);
                return steps;
            }
            steps.add(new DataExportPipelineStep(dataExportGateway, entidadeNormalizada));
            return steps;
        }

        if (ConstantesEntidades.RASTER.equalsIgnoreCase(apiEspecifica)) {
            steps.add(new RasterPipelineStep(
                AplicacaoContexto.rasterGateway(),
                entidadeNormalizada == null ? ConstantesEntidades.RASTER_VIAGENS : entidadeNormalizada
            ));
        }

        return steps;
    }

    Set<String> determinarEntidadesObrigatoriasParaVolume(
        final String apiEspecifica,
        final String entidadeEspecifica,
        final boolean incluirFaturasGraphQL
    ) {
        final Set<String> entidades = new LinkedHashSet<>();

        if (entidadeEspecifica != null && !entidadeEspecifica.isBlank()) {
            final String entidadeNormalizada = normalizarEntidade(entidadeEspecifica);
            if (entidadeNormalizada != null) {
                adicionarEntidadesObrigatoriasParaVolume(entidades, entidadeNormalizada);
            }
            return entidades;
        }

        final boolean apiTodas = apiEspecifica == null || apiEspecifica.isBlank();
        final boolean apiGraphQL = "graphql".equalsIgnoreCase(apiEspecifica);
        final boolean apiDataExport = "dataexport".equalsIgnoreCase(apiEspecifica);
        final boolean apiRaster = ConstantesEntidades.RASTER.equalsIgnoreCase(apiEspecifica);

        if (apiTodas || apiGraphQL) {
            entidades.add(ConstantesEntidades.COLETAS);
            entidades.add(ConstantesEntidades.FRETES);
            if (incluirFaturasGraphQL) {
                entidades.add(ConstantesEntidades.FATURAS_GRAPHQL);
            }
        }

        if (apiTodas || apiDataExport) {
            entidades.add(ConstantesEntidades.MANIFESTOS);
            entidades.add(ConstantesEntidades.COTACOES);
            entidades.add(ConstantesEntidades.LOCALIZACAO_CARGAS);
            entidades.add(ConstantesEntidades.INVENTARIO);
            entidades.add(ConstantesEntidades.SINISTROS);
        }

        if (apiRaster || (apiTodas && AplicacaoContexto.rasterHabilitadoParaExecucao())) {
            adicionarEntidadesObrigatoriasParaVolume(entidades, ConstantesEntidades.RASTER_VIAGENS);
        }

        return entidades;
    }

    Set<String> determinarEntidadesParaResumo(
        final String apiEspecifica,
        final String entidadeEspecifica,
        final boolean incluirFaturasGraphQL
    ) {
        final Set<String> entidades = new LinkedHashSet<>();

        if (entidadeEspecifica != null && !entidadeEspecifica.isBlank()) {
            final String entidadeNormalizada = normalizarEntidade(entidadeEspecifica);
            if (entidadeNormalizada != null) {
                adicionarEntidadeResumo(entidades, entidadeNormalizada);
            }
            return entidades;
        }

        final boolean apiTodas = apiEspecifica == null || apiEspecifica.isBlank();
        final boolean apiGraphQL = "graphql".equalsIgnoreCase(apiEspecifica);
        final boolean apiDataExport = "dataexport".equalsIgnoreCase(apiEspecifica);
        final boolean apiRaster = ConstantesEntidades.RASTER.equalsIgnoreCase(apiEspecifica);

        if (apiTodas || apiGraphQL) {
            entidades.add(ConstantesEntidades.USUARIOS_SISTEMA);
            entidades.add(ConstantesEntidades.COLETAS);
            entidades.add(ConstantesEntidades.FRETES);
            if (incluirFaturasGraphQL) {
                entidades.add(ConstantesEntidades.FATURAS_GRAPHQL);
            }
        }

        if (apiTodas || apiDataExport) {
            entidades.add(ConstantesEntidades.MANIFESTOS);
            entidades.add(ConstantesEntidades.COTACOES);
            entidades.add(ConstantesEntidades.LOCALIZACAO_CARGAS);
            entidades.add(ConstantesEntidades.CONTAS_A_PAGAR);
            entidades.add(ConstantesEntidades.FATURAS_POR_CLIENTE);
            entidades.add(ConstantesEntidades.INVENTARIO);
            entidades.add(ConstantesEntidades.SINISTROS);
        }

        if (apiRaster || (apiTodas && AplicacaoContexto.rasterHabilitadoParaExecucao())) {
            adicionarEntidadeResumo(entidades, ConstantesEntidades.RASTER_VIAGENS);
        }

        return entidades;
    }

    Set<String> determinarEntidadesEsperadasParaIntegridade(
        final String apiEspecifica,
        final String entidadeEspecifica,
        final boolean incluirFaturasGraphQL
    ) {
        final Set<String> entidades = new LinkedHashSet<>();

        if (entidadeEspecifica != null && !entidadeEspecifica.isBlank()) {
            final String entidadeNormalizada = normalizarEntidade(entidadeEspecifica);
            if (entidadeNormalizada != null) {
                if (ConstantesEntidades.RASTER_VIAGENS.equals(entidadeNormalizada)) {
                    return entidades;
                }
                entidades.add(entidadeNormalizada);
                if (ConstantesEntidades.COLETAS.equals(entidadeNormalizada)) {
                    entidades.add(ConstantesEntidades.USUARIOS_SISTEMA);
                }
            }
            return entidades;
        }

        final boolean apiTodas = apiEspecifica == null || apiEspecifica.isBlank();
        final boolean apiGraphQL = "graphql".equalsIgnoreCase(apiEspecifica);
        final boolean apiDataExport = "dataexport".equalsIgnoreCase(apiEspecifica);

        if (apiTodas || apiGraphQL) {
            entidades.add(ConstantesEntidades.USUARIOS_SISTEMA);
            entidades.add(ConstantesEntidades.COLETAS);
            entidades.add(ConstantesEntidades.FRETES);
            if (incluirFaturasGraphQL) {
                entidades.add(ConstantesEntidades.FATURAS_GRAPHQL);
            }
        }

        if (apiTodas || apiDataExport) {
            entidades.add(ConstantesEntidades.MANIFESTOS);
            entidades.add(ConstantesEntidades.COTACOES);
            entidades.add(ConstantesEntidades.LOCALIZACAO_CARGAS);
            entidades.add(ConstantesEntidades.CONTAS_A_PAGAR);
            entidades.add(ConstantesEntidades.FATURAS_POR_CLIENTE);
            entidades.add(ConstantesEntidades.INVENTARIO);
            entidades.add(ConstantesEntidades.SINISTROS);
        }

        return entidades;
    }

    boolean isEntidadeFaturasGraphQL(final String entidadeEspecifica) {
        if (entidadeEspecifica == null || entidadeEspecifica.isBlank()) {
            return false;
        }
        return ConstantesEntidades.FATURAS_GRAPHQL.equalsIgnoreCase(entidadeEspecifica)
            || "faturas".equalsIgnoreCase(entidadeEspecifica)
            || "faturasgraphql".equalsIgnoreCase(entidadeEspecifica);
    }

    String normalizarEntidade(final String entidade) {
        if (entidade == null || entidade.isBlank()) {
            return null;
        }
        final String valor = entidade.trim().toLowerCase(Locale.ROOT);

        if (ConstantesEntidades.COLETAS.equals(valor)) {
            return ConstantesEntidades.COLETAS;
        }
        if (ConstantesEntidades.FRETES.equals(valor)) {
            return ConstantesEntidades.FRETES;
        }
        if (ConstantesEntidades.FATURAS_GRAPHQL.equals(valor)
            || "faturas".equals(valor)
            || "faturasgraphql".equals(valor)) {
            return ConstantesEntidades.FATURAS_GRAPHQL;
        }
        if (ConstantesEntidades.USUARIOS_SISTEMA.equals(valor)) {
            return ConstantesEntidades.USUARIOS_SISTEMA;
        }
        if (ConstantesEntidades.MANIFESTOS.equals(valor)) {
            return ConstantesEntidades.MANIFESTOS;
        }
        if (ConstantesEntidades.COTACOES.equals(valor) || "cotacao".equals(valor)) {
            return ConstantesEntidades.COTACOES;
        }
        if (ConstantesEntidades.LOCALIZACAO_CARGAS.equals(valor)
            || "localizacao_carga".equals(valor)
            || "localizacao_de_carga".equals(valor)
            || "localizacao-carga".equals(valor)
            || "localizacao de carga".equals(valor)) {
            return ConstantesEntidades.LOCALIZACAO_CARGAS;
        }
        if (ConstantesEntidades.CONTAS_A_PAGAR.equals(valor)
            || "contasapagar".equals(valor)
            || "contas a pagar".equals(valor)
            || "contas-a-pagar".equals(valor)) {
            return ConstantesEntidades.CONTAS_A_PAGAR;
        }
        if (ConstantesEntidades.FATURAS_POR_CLIENTE.equals(valor)
            || "faturasporcliente".equals(valor)
            || "faturas por cliente".equals(valor)
            || "faturas-por-cliente".equals(valor)) {
            return ConstantesEntidades.FATURAS_POR_CLIENTE;
        }
        if (ConstantesEntidades.INVENTARIO.equals(valor) || "inventário".equals(valor)) {
            return ConstantesEntidades.INVENTARIO;
        }
        if (ConstantesEntidades.SINISTROS.equals(valor) || "sinistro".equals(valor)) {
            return ConstantesEntidades.SINISTROS;
        }
        if (ConstantesEntidades.RASTER.equals(valor)
            || ConstantesEntidades.RASTER_VIAGENS.equals(valor)
            || ConstantesEntidades.RASTER_VIAGEM_PARADAS.equals(valor)
            || "paradas_raster".equals(valor)
            || "viagens_raster".equals(valor)) {
            return ConstantesEntidades.RASTER_VIAGENS;
        }

        return valor;
    }

    private void adicionarEntidadesObrigatoriasParaVolume(final Set<String> entidades,
                                                          final String entidadeNormalizada) {
        entidades.add(entidadeNormalizada);
        if (ConstantesEntidades.RASTER_VIAGENS.equals(entidadeNormalizada)) {
            entidades.add(ConstantesEntidades.RASTER_VIAGEM_PARADAS);
        }
    }

    private void adicionarEntidadeResumo(final Set<String> entidades,
                                         final String entidadeNormalizada) {
        entidades.add(entidadeNormalizada);
        if (ConstantesEntidades.RASTER_VIAGENS.equals(entidadeNormalizada)) {
            entidades.add(ConstantesEntidades.RASTER_VIAGEM_PARADAS);
        }
    }

    private void adicionarStepsGraphQLGranulares(final List<PipelineStep> steps,
                                                 final GraphQLGateway graphQLGateway,
                                                 final boolean incluirFaturasGraphQL) {
        steps.add(new GraphQLPipelineStep(graphQLGateway, ConstantesEntidades.USUARIOS_SISTEMA));
        steps.add(new GraphQLPipelineStep(graphQLGateway, ConstantesEntidades.COLETAS));
        steps.add(new GraphQLPipelineStep(graphQLGateway, ConstantesEntidades.FRETES));
        if (incluirFaturasGraphQL) {
            steps.add(new GraphQLPipelineStep(graphQLGateway, ConstantesEntidades.FATURAS_GRAPHQL));
        }
    }

    private void adicionarStepsDataExportGranulares(final List<PipelineStep> steps,
                                                    final DataExportGateway dataExportGateway) {
        steps.add(new DataExportPipelineStep(dataExportGateway, ConstantesEntidades.MANIFESTOS));
        steps.add(new DataExportPipelineStep(dataExportGateway, ConstantesEntidades.COTACOES));
        steps.add(new DataExportPipelineStep(dataExportGateway, ConstantesEntidades.LOCALIZACAO_CARGAS));
        steps.add(new DataExportPipelineStep(dataExportGateway, ConstantesEntidades.CONTAS_A_PAGAR));
        steps.add(new DataExportPipelineStep(dataExportGateway, ConstantesEntidades.FATURAS_POR_CLIENTE));
        steps.add(new DataExportPipelineStep(dataExportGateway, ConstantesEntidades.INVENTARIO));
        steps.add(new DataExportPipelineStep(dataExportGateway, ConstantesEntidades.SINISTROS));
    }
}
