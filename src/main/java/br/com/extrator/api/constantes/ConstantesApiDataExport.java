/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/api/constantes/ConstantesApiDataExport.java
Classe  : ConstantesApiDataExport (class)
Pacote  : br.com.extrator.api.constantes
Modulo  : Cliente de integracao API
Papel   : Implementa responsabilidade de constantes api data export.

Conecta com:
- ConstantesEntidades (util.validacao)

Fluxo geral:
1) Monta requisicoes para endpoints externos.
2) Trata autenticacao, timeout e parse de resposta.
3) Entrega dados normalizados para os extractors.

Estrutura interna:
Metodos principais:
- ConstantesApiDataExport(): realiza operacao relacionada a "constantes api data export".
- ConfiguracaoEntidade(...7 args): realiza operacao relacionada a "configuracao entidade".
- obterConfiguracao(...1 args): recupera dados configurados ou calculados.
- possuiConfiguracao(...1 args): realiza operacao relacionada a "possui configuracao".
- obterTemplateId(...1 args): recupera dados configurados ou calculados.
- obterCampoData(...1 args): recupera dados configurados ou calculados.
- obterTabelaApi(...1 args): recupera dados configurados ou calculados.
- obterValorPer(...1 args): recupera dados configurados ou calculados.
- obterTimeout(...1 args): recupera dados configurados ou calculados.
- obterOrderBy(...1 args): recupera dados configurados ou calculados.
- usaSearchNested(...1 args): realiza operacao relacionada a "usa search nested".
- formatarEndpoint(...1 args): realiza operacao relacionada a "formatar endpoint".
- obterCampoIdPrimario(...1 args): recupera dados configurados ou calculados.
Atributos-chave:
- ENDPOINT_DATA_EXPORT: campo de estado para "endpoint data export".
[DOC-FILE-END]============================================================== */

package br.com.extrator.api.constantes;

import java.time.Duration;
import java.util.Map;

import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Constantes centralizadas para a API Data Export do ESL Cloud.
 * Utiliza Record para agrupar todas as configurações de cada entidade,
 * facilitando manutenção e comparação com Insomnia.
 * 
 * Para adicionar nova entidade: apenas adicionar entrada no Map CONFIGURACOES.
 * 
 * @author Sistema de Extração ESL Cloud
 * @version 1.0
 */
public final class ConstantesApiDataExport {

    private ConstantesApiDataExport() {
        // Impede instanciação
    }

    // ========== ENDPOINT ==========
    /**
     * Endpoint base para requisições da API Data Export.
     * Formato: /api/analytics/reports/{templateId}/data
     */
    public static final String ENDPOINT_DATA_EXPORT = "/api/analytics/reports/%d/data";

    // ========== RECORD DE CONFIGURAÇÃO ==========
    /**
     * Record que agrupa todas as configurações de uma entidade da API Data Export.
     * Imutável e type-safe.
     * 
     * @param templateId ID do template na API Data Export
     * @param campoData Campo de data para filtros (ex: "service_date", "requested_at")
     * @param tabelaApi Nome da tabela na API (ex: "manifests", "quotes")
     * @param valorPer Quantidade de registros por página ("100", "1000", "10000")
     * @param timeout Timeout da requisição
     * @param orderBy Campo de ordenação (ex: "sequence_code asc")
     * @param usaSearchNested Se true, usa estrutura aninhada no search (ex: ContasAPagar)
     */
    public record ConfiguracaoEntidade(
        int templateId,
        String campoData,
        String tabelaApi,
        String valorPer,
        Duration timeout,
        String orderBy,
        boolean usaSearchNested
    ) {}

    // ========== MAPA DE CONFIGURAÇÕES ==========
    /**
     * Mapa de configurações por entidade.
     * Chave: ConstantesEntidades.* (ex: "manifestos", "cotacoes")
     * Valor: ConfiguracaoEntidade com todas as configurações
     */
    private static final Map<String, ConfiguracaoEntidade> CONFIGURACOES = Map.of(
        // MANIFESTOS - Template 6399
        // API exige filtro com nome "service_date" (422 se enviar created_at). Ver docs/02-apis/dataexport/manifestos.md
        ConstantesEntidades.MANIFESTOS,
        new ConfiguracaoEntidade(
            6399,                           // templateId
            "service_date",                 // campoData (obrigatório: nome do filtro na API)
            "manifests",                    // tabelaApi
            "10000",                        // valorPer
            Duration.ofSeconds(120),        // timeout (2 min - páginas grandes)
            "sequence_code asc",            // orderBy
            false                           // usaSearchNested
        ),

        // COTACOES - Template 6906
        ConstantesEntidades.COTACOES,
        new ConfiguracaoEntidade(
            6906,                           // templateId
            "requested_at",                 // campoData
            "quotes",                       // tabelaApi
            "1000",                         // valorPer
            Duration.ofSeconds(60),         // timeout
            "sequence_code asc",            // orderBy
            false                           // usaSearchNested
        ),

        // LOCALIZACAO_CARGAS - Template 8656
        ConstantesEntidades.LOCALIZACAO_CARGAS,
        new ConfiguracaoEntidade(
            8656,                           // templateId
            "service_at",                   // campoData
            "freights",                     // tabelaApi
            "10000",                        // valorPer
            Duration.ofSeconds(90),         // timeout
            "sequence_number asc",          // orderBy (diferente!)
            false                           // usaSearchNested
        ),

        // CONTAS_A_PAGAR - Template 8636
        ConstantesEntidades.CONTAS_A_PAGAR,
        new ConfiguracaoEntidade(
            8636,                           // templateId
            "issue_date",                   // campoData
            "accounting_debits",            // tabelaApi
            "100",                          // valorPer
            Duration.ofSeconds(60),         // timeout
            "issue_date desc",              // orderBy (alinhado ao template oficial)
            true                            // usaSearchNested (estrutura diferente!)
        ),

        // FATURAS_POR_CLIENTE - Template 4924
        // API exige search.freights.service_at (422 se enviar outro nome). Ver docs/02-apis/dataexport/faturaporcliente.md
        ConstantesEntidades.FATURAS_POR_CLIENTE,
        new ConfiguracaoEntidade(
            4924,                           // templateId
            "service_at",                   // campoData (obrigatório: nome do filtro na API)
            "freights",                     // tabelaApi
            "100",                          // valorPer (ajustado)
            Duration.ofSeconds(60),         // timeout
            "unique_id asc",                // orderBy (diferente!)
            false                           // usaSearchNested
        )
    );

    // ========== MÉTODO PRINCIPAL ==========
    /**
     * Obtém a configuração completa de uma entidade.
     * 
     * @param entidade Nome da entidade (usar ConstantesEntidades.*)
     * @return ConfiguracaoEntidade com todas as configurações
     * @throws IllegalArgumentException se a entidade não for encontrada
     */
    public static ConfiguracaoEntidade obterConfiguracao(final String entidade) {
        final ConfiguracaoEntidade config = CONFIGURACOES.get(entidade);
        if (config == null) {
            throw new IllegalArgumentException("Entidade não configurada para Data Export: " + entidade);
        }
        return config;
    }

    /**
     * Verifica se uma entidade está configurada para Data Export.
     * 
     * @param entidade Nome da entidade
     * @return true se a entidade possui configuração
     */
    public static boolean possuiConfiguracao(final String entidade) {
        return CONFIGURACOES.containsKey(entidade);
    }

    // ========== MÉTODOS AUXILIARES (CONVENIÊNCIA) ==========
    /**
     * Obtém o Template ID de uma entidade.
     */
    public static int obterTemplateId(final String entidade) {
        return obterConfiguracao(entidade).templateId();
    }

    /**
     * Obtém o campo de data de uma entidade.
     */
    public static String obterCampoData(final String entidade) {
        return obterConfiguracao(entidade).campoData();
    }

    /**
     * Obtém o nome da tabela na API de uma entidade.
     */
    public static String obterTabelaApi(final String entidade) {
        return obterConfiguracao(entidade).tabelaApi();
    }

    /**
     * Obtém o valor de "per" (registros por página) de uma entidade.
     */
    public static String obterValorPer(final String entidade) {
        return obterConfiguracao(entidade).valorPer();
    }

    /**
     * Obtém o timeout de requisição de uma entidade.
     */
    public static Duration obterTimeout(final String entidade) {
        return obterConfiguracao(entidade).timeout();
    }

    /**
     * Obtém o campo de ordenação de uma entidade.
     */
    public static String obterOrderBy(final String entidade) {
        return obterConfiguracao(entidade).orderBy();
    }

    /**
     * Verifica se a entidade usa estrutura aninhada no search.
     */
    public static boolean usaSearchNested(final String entidade) {
        return obterConfiguracao(entidade).usaSearchNested();
    }

    /**
     * Formata a URL completa do endpoint para um template específico.
     * 
     * @param templateId ID do template
     * @return URL formatada (ex: "/api/analytics/reports/6399/data")
     */
    public static String formatarEndpoint(final int templateId) {
        return String.format(ENDPOINT_DATA_EXPORT, templateId);
    }

    /**
     * Obtém o nome do campo de ID primário baseado no orderBy.
     * Extrai o primeiro campo do orderBy (ex: "sequence_code asc" -> "sequence_code").
     * 
     * @param config Configuração da entidade
     * @return Nome do campo de ID primário
     */
    public static String obterCampoIdPrimario(final ConfiguracaoEntidade config) {
        final String orderBy = config.orderBy();
        if (orderBy == null || orderBy.isBlank()) {
            return null;
        }
        // Extrai o primeiro campo (ex: "sequence_code asc" -> "sequence_code")
        return orderBy.split("\\s+")[0];
    }

    /**
     * Obtém o nome do campo de ID primário de uma entidade.
     * 
     * @param entidade Nome da entidade
     * @return Nome do campo de ID primário
     */
    public static String obterCampoIdPrimario(final String entidade) {
        return obterCampoIdPrimario(obterConfiguracao(entidade));
    }
}
