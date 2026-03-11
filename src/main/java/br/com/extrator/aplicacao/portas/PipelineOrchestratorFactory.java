/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/portas/PipelineOrchestratorFactory.java
Classe  : PipelineOrchestratorFactory (interface)
Pacote  : br.com.extrator.aplicacao.portas
Modulo  : Porta (Interface)

Papel   : Porta factory funcional para criar instancias de PipelineOrchestrator.

Conecta com:
- PipelineCompositionRoot (registra factory em AplicacaoContexto)
- Use cases (obtém orquestra via factory)

Fluxo geral:
1) criar() retorna nova instancia de PipelineOrchestrator.
2) Factory abstrai criacao (permite DI ou configuracao).

Estrutura interna:
Metodos principais:
- criar(): PipelineOrchestrator.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.portas;

import br.com.extrator.aplicacao.pipeline.PipelineOrchestrator;

@FunctionalInterface
public interface PipelineOrchestratorFactory {
    PipelineOrchestrator criar();
}
