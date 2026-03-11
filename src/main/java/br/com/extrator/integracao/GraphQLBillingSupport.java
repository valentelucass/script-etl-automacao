package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/GraphQLBillingSupport.java
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import br.com.extrator.integracao.constantes.ConstantesApiGraphQL;
import br.com.extrator.integracao.graphql.GraphQLQueries;
import br.com.extrator.dominio.graphql.faturas.CreditCustomerBillingNodeDTO;
import br.com.extrator.suporte.configuracao.ConfigApi;
import br.com.extrator.suporte.formatacao.FormatadorData;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

final class GraphQLBillingSupport {
    private final Logger logger;
    private final GraphQLSchemaInspector schemaInspector;
    private final GraphQLPaginator paginator;

    GraphQLBillingSupport(final Logger logger,
                         final GraphQLSchemaInspector schemaInspector,
                         final GraphQLPaginator paginator) {
        this.logger = logger;
        this.schemaInspector = schemaInspector;
        this.paginator = paginator;
    }

    ResultadoExtracao<CreditCustomerBillingNodeDTO> buscarCapaFaturas(final String executionUuid, final LocalDate dataReferencia) {
        final int diasJanela = ConfigApi.obterDiasJanelaFaturasGraphQL();
        final LocalDate dataInicio = dataReferencia.minusDays(Math.max(0, diasJanela - 1));
        return buscarCapaFaturas(executionUuid, dataInicio, dataReferencia);
    }

    ResultadoExtracao<CreditCustomerBillingNodeDTO> buscarCapaFaturas(final String executionUuid,
                                                                      final LocalDate dataInicio,
                                                                      final LocalDate dataFim) {
        return GraphQLIntervaloHelper.executarPorDia(
            dataInicio,
            dataFim,
            data -> buscarCapaFaturasDia(executionUuid, data),
            "Capa Faturas"
        );
    }

    private ResultadoExtracao<CreditCustomerBillingNodeDTO> buscarCapaFaturasDia(final String executionUuid,
                                                                                  final LocalDate data) {
        final Set<String> camposDisponiveis = schemaInspector.listarCamposInput(
            GraphQLQueries.INTROSPECTION_CREDIT_CUSTOMER_BILLING,
            "GraphQL-Introspection",
            "Falha ao introspectar CreditCustomerBillingInput"
        );
        final List<String> camposFiltro = resolverCamposFiltroFaturas(camposDisponiveis);
        ResultadoExtracao<CreditCustomerBillingNodeDTO> ultimoResultado = null;

        for (int indice = 0; indice < camposFiltro.size(); indice++) {
            final String campoFiltro = camposFiltro.get(indice);
            final Map<String, Object> variaveis = Map.of("params", montarParametrosCapaFatura(campoFiltro, data, camposDisponiveis));
            final ResultadoExtracao<CreditCustomerBillingNodeDTO> resultado = paginator.executarQueryPaginada(
                executionUuid,
                GraphQLQueries.QUERY_FATURAS,
                ConstantesApiGraphQL.obterNomeEntidadeApi(ConstantesEntidades.FATURAS_GRAPHQL),
                variaveis,
                CreditCustomerBillingNodeDTO.class
            );

            ultimoResultado = resultado;
            if (!deveRetentarResultadoFaturas(resultado) || indice == camposFiltro.size() - 1) {
                return resultado;
            }

            logger.warn(
                "Falha transit?ria ao buscar Capa Faturas em {} usando filtro '{}'. Tentando filtro alternativo...",
                data,
                campoFiltro
            );
        }

        return ultimoResultado != null ? ultimoResultado : ResultadoExtracao.completo(new ArrayList<>(), 0, 0);
    }

    private List<String> resolverCamposFiltroFaturas(final Set<String> camposDisponiveis) {
        final LinkedHashSet<String> camposOrdenados = new LinkedHashSet<>();
        if (camposDisponiveis == null || camposDisponiveis.isEmpty()) {
            camposOrdenados.add("dueDate");
            return new ArrayList<>(camposOrdenados);
        }
        if (camposDisponiveis.contains("dueDate")) {
            camposOrdenados.add("dueDate");
        }
        if (camposDisponiveis.contains("originalDueDate")) {
            camposOrdenados.add("originalDueDate");
        }
        if (camposDisponiveis.contains("issueDate")) {
            camposOrdenados.add("issueDate");
        }
        if (camposOrdenados.isEmpty()) {
            camposOrdenados.add("dueDate");
        }
        return new ArrayList<>(camposOrdenados);
    }

    private Map<String, Object> montarParametrosCapaFatura(final String campoFiltro,
                                                           final LocalDate data,
                                                           final Set<String> camposDisponiveis) {
        final Map<String, Object> params = new HashMap<>();
        params.put(campoFiltro, data.format(FormatadorData.ISO_DATE));

        final String corpId = ConfigApi.obterCorporationId();
        if (corpId != null && !corpId.isBlank()) {
            if (camposDisponiveis != null && camposDisponiveis.contains("corporationId")) {
                params.put("corporationId", corpId);
            } else if (camposDisponiveis != null && camposDisponiveis.contains("corporation")) {
                params.put("corporation", Map.of("id", corpId));
            }
        }
        return params;
    }

    private boolean deveRetentarResultadoFaturas(final ResultadoExtracao<CreditCustomerBillingNodeDTO> resultado) {
        if (resultado == null || resultado.isCompleto()) {
            return false;
        }
        final String motivo = resultado.getMotivoInterrupcao();
        final boolean erroApi = ResultadoExtracao.MotivoInterrupcao.ERRO_API.getCodigo().equals(motivo)
            || ResultadoExtracao.MotivoInterrupcao.CIRCUIT_BREAKER.getCodigo().equals(motivo);
        final boolean semDados = resultado.getRegistrosExtraidos() == 0
            && (resultado.getDados() == null || resultado.getDados().isEmpty());
        return erroApi && semDados;
    }
}
