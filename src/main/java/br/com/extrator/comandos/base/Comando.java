/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/base/Comando.java
Classe  : Comando (interface)
Pacote  : br.com.extrator.comandos.base
Modulo  : Componente Java
Papel   : Implementa comportamento de comando.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Define comportamento principal deste modulo.
2) Interage com camadas relacionadas do sistema.
3) Entrega resultado para o fluxo chamador.

Estrutura interna:
Metodos principais:
- Metodos nao mapeados automaticamente; consulte a implementacao abaixo.
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.base;

/**
 * Interface que define o contrato comum para todos os comandos do sistema.
 * 
 * Implementa o padrão Command, permitindo que a classe Main seja um dispatcher
 * puro que delega a execução para classes especializadas.
 */
public interface Comando {
    
    /**
     * Executa o comando com os argumentos fornecidos.
     * 
     * @param args Array completo de argumentos da linha de comando
     * @throws Exception Se ocorrer algum erro durante a execução
     */
    void executar(String[] args) throws Exception;
}