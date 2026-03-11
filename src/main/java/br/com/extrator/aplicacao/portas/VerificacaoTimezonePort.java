/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/portas/VerificacaoTimezonePort.java
Classe  : VerificacaoTimezonePort (interface)
Pacote  : br.com.extrator.aplicacao.portas
Modulo  : Porta (Interface)

Papel   : Porta para verificacao de consistencia de timezone em BD.

Conecta com:
- VerificacaoTimezoneAdapter (implementacao em observabilidade)

Fluxo geral:
1) verificar() valida que timezone BD e aplicacao sao sincronizados.
2) Falha critica se desvios significativos (pode invalidar comparacoes de data).

Estrutura interna:
Metodos principais:
- verificar(): void (lança excecao se falha).
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.portas;

public interface VerificacaoTimezonePort {
    void verificar();
}
