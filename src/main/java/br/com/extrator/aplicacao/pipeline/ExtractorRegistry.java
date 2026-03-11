/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/pipeline/ExtractorRegistry.java
Classe  : ExtractorRegistry (class)
Pacote  : br.com.extrator.aplicacao.pipeline
Modulo  : Pipeline - Aplicacao

Papel   : Registro de extractors (steps) por entidade (registry pattern para lookup dinamico).

Conecta com:
- PipelineStep (supplier fornece instancias)

Fluxo geral:
1) registrar(entidade, stepSupplier) registra factory para entidade.
2) get(entidade) retorna Optional<PipelineStep>.
3) listarTodos() retorna todos os steps.
4) listarPorEntidades(list) retorna steps para lista específica.

Estrutura interna:
Atributos-chave:
- stepsPorEntidade: Map<String, Supplier<PipelineStep>> (chave normalized, case-insensitive).
Metodos principais:
- registrar(entidade, supplier): registra step.
- get(entidade): Optional<PipelineStep>.
- listarTodos(): List<PipelineStep> imutavel.
- listarPorEntidades(list): List<PipelineStep> filtrado.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.pipeline;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class ExtractorRegistry {
    private final Map<String, Supplier<PipelineStep>> stepsPorEntidade = new LinkedHashMap<>();

    public void registrar(final String entidade, final Supplier<PipelineStep> stepSupplier) {
        final String chave = normalize(entidade);
        if (chave.isBlank()) {
            throw new IllegalArgumentException("entidade nao pode ser vazia");
        }
        stepsPorEntidade.put(chave, stepSupplier);
    }

    public Optional<PipelineStep> get(final String entidade) {
        final Supplier<PipelineStep> supplier = stepsPorEntidade.get(normalize(entidade));
        if (supplier == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(supplier.get());
    }

    public List<PipelineStep> listarTodos() {
        final List<PipelineStep> steps = new ArrayList<>();
        for (Supplier<PipelineStep> supplier : stepsPorEntidade.values()) {
            steps.add(supplier.get());
        }
        return Collections.unmodifiableList(steps);
    }

    public List<PipelineStep> listarPorEntidades(final List<String> entidades) {
        final List<PipelineStep> steps = new ArrayList<>();
        for (String entidade : entidades) {
            get(entidade).ifPresent(steps::add);
        }
        return Collections.unmodifiableList(steps);
    }

    private String normalize(final String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}


