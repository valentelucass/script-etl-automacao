/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/portas/LimpezaBancoPort.java
Classe  : LimpezaBancoPort (interface)
Pacote  : br.com.extrator.aplicacao.portas
Modulo  : Porta (Interface)

Papel   : Porta para limpeza completa do banco de dados (utilitario administrativo).

Conecta com:
- LimpezaBancoAdapter (implementacao em persistencia)

Fluxo geral:
1) limparTodasTabelas() deleta todos os dados do banco.
2) Uso: resetting BD para testes, recovery, disaster recovery.

Estrutura interna:
Metodos principais:
- limparTodasTabelas(): void (truncate/delete all).
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.portas;

public interface LimpezaBancoPort {
    void limparTodasTabelas();
}
