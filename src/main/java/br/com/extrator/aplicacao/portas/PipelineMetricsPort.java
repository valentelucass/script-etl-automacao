/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/portas/PipelineMetricsPort.java
Classe  : PipelineMetricsPort (interface)
Pacote  : br.com.extrator.aplicacao.portas
Modulo  : Porta (Interface)

Papel   : Porta para registro de metricas de execucao do pipeline (duracao, sucesso, falha).

Conecta com:
- PipelineMetricsAdapter (implementacao em observabilidade)

Fluxo geral:
1) registrarDuracaoEntidade(entidade, ms): registra tempo de execucao.
2) incrementarSucesso/Falha(entidade): contadores.
3) obterSnapshot(): Map com metricas atuais (ex: {coletas: 1234.5}).

Estrutura interna:
Metodos principais:
- registrarDuracaoEntidade(String, long): registra duracao em ms.
- incrementarSucesso(String): +1 contador sucesso.
- incrementarFalha(String): +1 contador falha.
- obterSnapshot(): Map<String, Double> com metricas.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.portas;

import java.util.Map;

public interface PipelineMetricsPort {
    void registrarDuracaoEntidade(String entidade, long durationMillis);

    void incrementarSucesso(String entidade);

    void incrementarFalha(String entidade);

    Map<String, Double> obterSnapshot();
}


