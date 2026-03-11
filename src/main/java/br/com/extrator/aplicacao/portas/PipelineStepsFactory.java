/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/portas/PipelineStepsFactory.java
Classe  : PipelineStepsFactory (interface)
Pacote  : br.com.extrator.aplicacao.portas
Modulo  : Porta (Interface)

Papel   : Porta factory para criar lista de steps do pipeline conforme flags e escopo.

Conecta com:
- PipelineCompositionRoot (registra factory em AplicacaoContexto)
- Use cases (obtém steps via factory)

Fluxo geral:
1) criarStepsFluxoCompleto(incluirFaturas, incluirQuality) retorna List<PipelineStep>.
2) Factory abstracta criacao e decisoes de quais steps incluir.

Estrutura interna:
Metodos principais:
- criarStepsFluxoCompleto(boolean, boolean): List<PipelineStep>.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.portas;

import java.util.List;

import br.com.extrator.aplicacao.pipeline.PipelineStep;

public interface PipelineStepsFactory {
    List<PipelineStep> criarStepsFluxoCompleto(boolean incluirFaturasGraphQL, boolean incluirDataQuality);
}
