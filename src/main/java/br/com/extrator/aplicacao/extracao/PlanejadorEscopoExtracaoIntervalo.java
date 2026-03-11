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
            entidadesParaValidar.add(entidadeEspecifica);
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
        if (incluirFaturasGraphQL) {
            entidadesParaValidar.add(ConstantesEntidades.FATURAS_GRAPHQL);
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
            steps.add(new GraphQLPipelineStep(graphQLGateway, "graphql"));
            steps.add(new DataExportPipelineStep(dataExportGateway, "dataexport"));
            if (incluirFaturasGraphQL) {
                steps.add(new GraphQLPipelineStep(graphQLGateway, ConstantesEntidades.FATURAS_GRAPHQL));
            }
            return steps;
        }

        if ("graphql".equalsIgnoreCase(apiEspecifica)) {
            if (entidadeNormalizada == null) {
                steps.add(new GraphQLPipelineStep(graphQLGateway, "graphql"));
                if (incluirFaturasGraphQL) {
                    steps.add(new GraphQLPipelineStep(graphQLGateway, ConstantesEntidades.FATURAS_GRAPHQL));
                }
                return steps;
            }
            steps.add(new GraphQLPipelineStep(graphQLGateway, entidadeNormalizada));
            return steps;
        }

        if ("dataexport".equalsIgnoreCase(apiEspecifica)) {
            steps.add(new DataExportPipelineStep(
                dataExportGateway,
                entidadeNormalizada == null ? "dataexport" : entidadeNormalizada
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
                entidades.add(entidadeNormalizada);
            }
            return entidades;
        }

        final boolean apiTodas = apiEspecifica == null || apiEspecifica.isBlank();
        final boolean apiGraphQL = "graphql".equalsIgnoreCase(apiEspecifica);
        final boolean apiDataExport = "dataexport".equalsIgnoreCase(apiEspecifica);

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

        return valor;
    }
}
