/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/portas/VerificacaoTimestampPort.java
Classe  : VerificacaoTimestampPort (interface)
Pacote  : br.com.extrator.aplicacao.portas
Modulo  : Porta (Interface)

Papel   : Porta para verificacao de consistencia de timestamps em BD.

Conecta com:
- VerificacaoTimestampAdapter (implementacao em observabilidade)

Fluxo geral:
1) verificar() valida que timestamps sao monotonicos ou consistentes.
2) Falha critica se desvios significativos.

Estrutura interna:
Metodos principais:
- verificar(): void (lança excecao se falha).
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.portas;

public interface VerificacaoTimestampPort {
    void verificar();
}
