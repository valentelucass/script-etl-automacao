/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/utilitarios/ExportarCsvComando.java
Classe  : ExportarCsvComando (class)
Pacote  : br.com.extrator.comandos.utilitarios
Modulo  : Componente Java
Papel   : Implementa comportamento de exportar csv comando.

Conecta com:
- Comando (comandos.base)
- ExportadorCSV (util.formatacao)

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

package br.com.extrator.comandos.utilitarios;

import br.com.extrator.comandos.base.Comando;
import br.com.extrator.util.formatacao.ExportadorCSV;

/**
 * Comando responsável por exportar dados para CSV.
 * Permite exportar todas as tabelas ou uma tabela específica.
 */
public class ExportarCsvComando implements Comando {
    
    @Override
    public void executar(final String[] args) throws Exception {
        // Se houver argumento, é o nome da tabela específica
        final String tabelaEspecifica = (args != null && args.length > 1) ? args[1].trim() : null;
        
        // Chama o método main do ExportadorCSV diretamente
        if (tabelaEspecifica != null && !tabelaEspecifica.isEmpty()) {
            ExportadorCSV.main(new String[] { tabelaEspecifica });
        } else {
            ExportadorCSV.main(new String[0]);
        }
    }
}

