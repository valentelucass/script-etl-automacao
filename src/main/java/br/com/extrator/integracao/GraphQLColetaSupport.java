package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/GraphQLColetaSupport.java
Classe  :  (class)
Pacote  : br.com.extrator.integracao
Modulo  : Integracao HTTP
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import br.com.extrator.integracao.constantes.ConstantesApiGraphQL;
import br.com.extrator.integracao.graphql.GraphQLQueries;
import br.com.extrator.dominio.graphql.coletas.ColetaNodeDTO;
import br.com.extrator.suporte.formatacao.FormatadorData;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

final class GraphQLColetaSupport {
    private final Logger logger;
    private final GraphQLSchemaInspector schemaInspector;
    private final GraphQLPaginator paginator;
    private volatile Set<String> camposPickInputCache;

    GraphQLColetaSupport(final Logger logger,
                        final GraphQLSchemaInspector schemaInspector,
                        final GraphQLPaginator paginator) {
        this.logger = logger;
        this.schemaInspector = schemaInspector;
        this.paginator = paginator;
    }

    ResultadoExtracao<ColetaNodeDTO> buscarColetas(final String executionUuid,
                                                   final LocalDate dataInicio,
                                                   final LocalDate dataFim) {
        if (suportaFiltroPick("serviceDate")) {
            logger.info("🔍 Coletas: usando filtros combinados requestDate + serviceDate para reduzir perdas referenciais.");
            return buscarColetasComFiltrosCombinados(executionUuid, dataInicio, dataFim);
        }
        logger.info("ℹ️ Coletas: filtro serviceDate não disponível no schema atual, usando requestDate.");
        return GraphQLIntervaloHelper.executarPorDia(dataInicio, dataFim, data -> buscarColetasDia(executionUuid, data), "Coletas");
    }

    private ResultadoExtracao<ColetaNodeDTO> buscarColetasComFiltrosCombinados(final String executionUuid,
                                                                                final LocalDate dataInicio,
                                                                                final LocalDate dataFim) {
        final List<ColetaNodeDTO> acumulado = new ArrayList<>();
        int totalPaginas = 0;
        boolean completo = true;
        String motivoInterrupcao = null;

        LocalDate dataAtual = dataInicio;
        while (!dataAtual.isAfter(dataFim)) {
            final ResultadoExtracao<ColetaNodeDTO> porRequestDate = buscarColetasDiaComCampo(executionUuid, dataAtual, "requestDate");
            final ResultadoExtracao<ColetaNodeDTO> porServiceDate = buscarColetasDiaComCampo(executionUuid, dataAtual, "serviceDate");

            acumulado.addAll(porRequestDate.getDados());
            acumulado.addAll(porServiceDate.getDados());
            totalPaginas += porRequestDate.getPaginasProcessadas();
            totalPaginas += porServiceDate.getPaginasProcessadas();

            if (!porRequestDate.isCompleto() || !porServiceDate.isCompleto()) {
                completo = false;
                motivoInterrupcao = selecionarMotivoInterrupcao(motivoInterrupcao, porRequestDate.getMotivoInterrupcao());
                motivoInterrupcao = selecionarMotivoInterrupcao(motivoInterrupcao, porServiceDate.getMotivoInterrupcao());
            }
            dataAtual = dataAtual.plusDays(1);
        }

        final List<ColetaNodeDTO> deduplicado = deduplicarColetasPorId(acumulado);
        final int duplicadosRemovidos = acumulado.size() - deduplicado.size();
        if (duplicadosRemovidos > 0) {
            logger.info("ℹ️ Coletas combinadas: {} duplicado(s) removido(s) por id/sequenceCode.", duplicadosRemovidos);
        }

        if (completo) {
            return ResultadoExtracao.completo(deduplicado, totalPaginas, deduplicado.size());
        }
        return ResultadoExtracao.incompleto(
            deduplicado,
            motivoInterrupcao != null ? motivoInterrupcao : ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS.getCodigo(),
            totalPaginas,
            deduplicado.size()
        );
    }

    private ResultadoExtracao<ColetaNodeDTO> buscarColetasDia(final String executionUuid, final LocalDate data) {
        return buscarColetasDiaComCampo(executionUuid, data, "requestDate");
    }

    private ResultadoExtracao<ColetaNodeDTO> buscarColetasDiaComCampo(final String executionUuid,
                                                                      final LocalDate data,
                                                                      final String campoData) {
        final Map<String, Object> variaveis = Map.of("params", Map.of(campoData, data.format(FormatadorData.ISO_DATE)));
        return paginator.executarQueryPaginada(
            executionUuid,
            GraphQLQueries.QUERY_COLETAS,
            ConstantesApiGraphQL.obterNomeEntidadeApi(ConstantesEntidades.COLETAS),
            variaveis,
            ColetaNodeDTO.class
        );
    }

    private boolean suportaFiltroPick(final String campo) {
        return listarCamposInputPick().contains(campo);
    }

    private Set<String> listarCamposInputPick() {
        if (camposPickInputCache == null) {
            camposPickInputCache = Set.copyOf(
                schemaInspector.listarCamposInput(
                    GraphQLQueries.INTROSPECTION_PICK_INPUT,
                    "GraphQL-Introspection-PickInput",
                    "Falha ao introspectar PickInput. Seguira com requestDate apenas"
                )
            );
        }
        return camposPickInputCache;
    }

    private String selecionarMotivoInterrupcao(final String atual, final String candidato) {
        if (candidato == null || candidato.isBlank()) {
            return atual;
        }
        if (ResultadoExtracao.MotivoInterrupcao.ERRO_API.getCodigo().equals(candidato)
            || ResultadoExtracao.MotivoInterrupcao.CIRCUIT_BREAKER.getCodigo().equals(candidato)) {
            return candidato;
        }
        return (atual == null || atual.isBlank()) ? candidato : atual;
    }

    private List<ColetaNodeDTO> deduplicarColetasPorId(final List<ColetaNodeDTO> coletas) {
        final Map<String, ColetaNodeDTO> unicos = new LinkedHashMap<>();
        for (final ColetaNodeDTO coleta : coletas) {
            if (coleta == null) {
                continue;
            }
            final String chave = coleta.getId() != null && !coleta.getId().isBlank()
                ? coleta.getId()
                : String.valueOf(coleta.getSequenceCode());
            unicos.put(chave, coleta);
        }
        return new ArrayList<>(unicos.values());
    }
}
