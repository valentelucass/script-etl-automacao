/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/api/constantes/ConstantesViewsPowerBI.java
Classe  : ConstantesViewsPowerBI (class)
Pacote  : br.com.extrator.api.constantes
Modulo  : Cliente de integracao API
Papel   : Implementa responsabilidade de constantes views power bi.

Conecta com:
- ConstantesEntidades (util.validacao)

Fluxo geral:
1) Monta requisicoes para endpoints externos.
2) Trata autenticacao, timeout e parse de resposta.
3) Entrega dados normalizados para os extractors.

Estrutura interna:
Metodos principais:
- ConstantesViewsPowerBI(): realiza operacao relacionada a "constantes views power bi".
- possuiView(...1 args): realiza operacao relacionada a "possui view".
- obterNomeView(...1 args): recupera dados configurados ou calculados.
- obterMapaViews(): recupera dados configurados ou calculados.
Atributos-chave:
- VIEWS: campo de estado para "views".
[DOC-FILE-END]============================================================== */

package br.com.extrator.api.constantes;

import java.util.HashMap;
import java.util.Map;

import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Constantes centralizadas para nomes das Views do Power BI.
 * 
 * IMPORTANTE: As views SQL estão em scripts separados (database/views/).
 * Esta classe apenas mapeia entidades para nomes de views, sem duplicar SQL.
 * 
 * As views são criadas/atualizadas pelos scripts SQL executados via executar_database.bat.
 * 
 * @author Sistema de Extração ESL Cloud
 * @version 2.0 - Refatorado para remover SQL duplicado (agora apenas mapeamento de nomes)
 */
public final class ConstantesViewsPowerBI {

    private ConstantesViewsPowerBI() {
        // Impede instanciação
    }

    // ========== MAPA DE NOMES DE VIEWS ==========
    /**
     * Mapa de nomes de views por entidade.
     * Chave: ConstantesEntidades.* (ex: "fretes", "coletas")
     * Valor: Nome da view (ex: "vw_fretes_powerbi")
     */
    private static final Map<String, String> VIEWS;
    
    static {
        VIEWS = new HashMap<>();
        VIEWS.put(ConstantesEntidades.FATURAS_POR_CLIENTE, "vw_faturas_por_cliente_powerbi");
        VIEWS.put(ConstantesEntidades.FRETES, "vw_fretes_powerbi");
        VIEWS.put(ConstantesEntidades.COLETAS, "vw_coletas_powerbi");
        VIEWS.put(ConstantesEntidades.FATURAS_GRAPHQL, "vw_faturas_graphql_powerbi");
        VIEWS.put(ConstantesEntidades.COTACOES, "vw_cotacoes_powerbi");
        VIEWS.put(ConstantesEntidades.CONTAS_A_PAGAR, "vw_contas_a_pagar_powerbi");
        VIEWS.put(ConstantesEntidades.LOCALIZACAO_CARGAS, "vw_localizacao_cargas_powerbi");
        VIEWS.put(ConstantesEntidades.MANIFESTOS, "vw_manifestos_powerbi");
    }

    // ========== MÉTODOS PRINCIPAIS ==========
    /**
     * Verifica se uma entidade possui view Power BI.
     * 
     * @param entidade Nome da entidade (usar ConstantesEntidades.*)
     * @return true se a entidade possui view
     */
    public static boolean possuiView(final String entidade) {
        return VIEWS.containsKey(entidade);
    }

    /**
     * Obtém o nome da view de uma entidade.
     * 
     * @param entidade Nome da entidade (usar ConstantesEntidades.*)
     * @return Nome da view (ex: "vw_fretes_powerbi")
     * @throws IllegalArgumentException se a entidade não possuir view
     */
    public static String obterNomeView(final String entidade) {
        final String nomeView = VIEWS.get(entidade);
        if (nomeView == null) {
            throw new IllegalArgumentException("Entidade não possui view Power BI: " + entidade);
        }
        return nomeView;
    }

    /**
     * Obtém o mapa completo de views por entidade.
     * 
     * @return Map imutável com entidade -> nome da view
     */
    public static Map<String, String> obterMapaViews() {
        return Map.copyOf(VIEWS);
    }
}
