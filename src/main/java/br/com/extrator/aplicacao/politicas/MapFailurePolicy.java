/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/politicas/MapFailurePolicy.java
Classe  : MapFailurePolicy (class)
Pacote  : br.com.extrator.aplicacao.politicas
Modulo  : Politicas - Resiliencia

Papel   : Implementa FailurePolicy baseada em map (por entidade) com fallback.

Conecta com:
- FailurePolicy (interface que implementa)
- FailureMode (valores retornados)

Fluxo geral:
1) Construtor recebe map (entidade -> FailureMode) e modoPadrao.
2) Normaliza chaves para lowercase (insensitivo a case).
3) resolver(entidade, taxonomy) consulta map ou retorna modoPadrao.

Estrutura interna:
Atributos-chave:
- porEntidade: Map<String, FailureMode> imutavel (chaves normalized).
- modoPadrao: FailureMode (fallback se entidade nao esta no map).
Metodos principais:
- resolver(entidade, taxonomy): retorna FailureMode por entidade (case-insensitive).
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.politicas;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class MapFailurePolicy implements FailurePolicy {
    private final Map<String, FailureMode> porEntidade;
    private final FailureMode modoPadrao;

    public MapFailurePolicy(final Map<String, FailureMode> porEntidade, final FailureMode modoPadrao) {
        final Map<String, FailureMode> normalizado = new LinkedHashMap<>();
        if (porEntidade != null) {
            for (Map.Entry<String, FailureMode> entry : porEntidade.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                normalizado.put(entry.getKey().trim().toLowerCase(Locale.ROOT), entry.getValue());
            }
        }
        this.porEntidade = Collections.unmodifiableMap(normalizado);
        this.modoPadrao = modoPadrao == null ? FailureMode.ABORT_PIPELINE : modoPadrao;
    }

    @Override
    public FailureMode resolver(final String entidade, final ErrorTaxonomy taxonomy) {
        final String chave = entidade == null ? "" : entidade.trim().toLowerCase(Locale.ROOT);
        return porEntidade.getOrDefault(chave, modoPadrao);
    }
}


