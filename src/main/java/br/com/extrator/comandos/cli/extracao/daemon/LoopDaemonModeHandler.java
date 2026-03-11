/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/extracao/daemon/LoopDaemonModeHandler.java
Classe  : LoopDaemonModeHandler (interface)
Pacote  : br.com.extrator.comandos.cli.extracao.daemon
Modulo  : Comando CLI (daemon)
Papel   : Contrato unico para handlers de modo do loop daemon.

Conecta com:
- LoopDaemonComando
- LoopDaemonStartHandler
- LoopDaemonStopHandler
- LoopDaemonStatusHandler
- LoopDaemonRunHandler

Fluxo geral:
1) Recebe parametro de modo de faturas.
2) Executa acao especifica do modo.
3) Propaga excecoes de execucao ao chamador.

Estrutura interna:
Metodos principais:
- executar(...1 args): executa modo de operacao selecionado.
Atributos-chave:
- Sem atributos (interface funcional).
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.cli.extracao.daemon;

@FunctionalInterface
public interface LoopDaemonModeHandler {
    void executar(boolean incluirFaturasGraphQL) throws Exception;
}
