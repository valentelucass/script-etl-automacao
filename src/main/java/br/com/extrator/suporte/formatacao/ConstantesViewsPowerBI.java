package br.com.extrator.suporte.formatacao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/suporte/formatacao/ConstantesViewsPowerBI.java
Classe  : ConstantesViewsPowerBI (class)
Pacote  : br.com.extrator.suporte.formatacao
Modulo  : Suporte - Formatacao
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.util.HashMap;
import java.util.Map;

/**
 * Constantes centralizadas para nomes das views Power BI usadas na exportacao.
 */
public final class ConstantesViewsPowerBI {

    private static final Map<String, String> VIEWS;

    static {
        VIEWS = new HashMap<>();
        VIEWS.put("faturas_por_cliente", "vw_faturas_por_cliente_powerbi");
        VIEWS.put("fretes", "vw_fretes_powerbi");
        VIEWS.put("coletas", "vw_coletas_powerbi");
        VIEWS.put("faturas_graphql", "vw_faturas_graphql_powerbi");
        VIEWS.put("cotacoes", "vw_cotacoes_powerbi");
        VIEWS.put("contas_a_pagar", "vw_contas_a_pagar_powerbi");
        VIEWS.put("localizacao_cargas", "vw_localizacao_cargas_powerbi");
        VIEWS.put("manifestos", "vw_manifestos_powerbi");
    }

    private ConstantesViewsPowerBI() {
    }

    public static boolean possuiView(final String entidade) {
        return VIEWS.containsKey(entidade);
    }

    public static String obterNomeView(final String entidade) {
        final String nomeView = VIEWS.get(entidade);
        if (nomeView == null) {
            throw new IllegalArgumentException("Entidade nao possui view Power BI: " + entidade);
        }
        return nomeView;
    }

    public static Map<String, String> obterMapaViews() {
        return Map.copyOf(VIEWS);
    }
}
