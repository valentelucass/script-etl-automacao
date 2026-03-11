/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/validacao/ManifestosQueryResultPrinter.java
Classe  : ManifestosQueryResultPrinter (final class)
Pacote  : br.com.extrator.aplicacao.validacao
Modulo  : Use Case - Validacao

Papel   : Utilitario para formatar e exibir resultados de SQL queries em saida tabular (manifesto validation).

Conecta com:
- LoggerConsole (suporte.console)

Fluxo geral:
1) exibirResultado(ResultSet) le dados da query.
2) Formata como tabela com headers, separadores e dados.
3) Escreve em System.out com contagem final de linhas.

Estrutura interna:
Atributos-chave:
- log: LoggerConsole (para logging em console).
Metodos principais:
- exibirResultado(ResultSet): formata e exibe tabela com metadados de coluna.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.validacao;

import java.sql.ResultSet;
import java.sql.SQLException;

import br.com.extrator.suporte.console.LoggerConsole;

/**
 * Formata e imprime ResultSet de consultas de validacao de manifestos.
 */
final class ManifestosQueryResultPrinter {
    private final LoggerConsole log;

    ManifestosQueryResultPrinter(final LoggerConsole log) {
        this.log = log;
    }

    void exibirResultado(final ResultSet rs) throws SQLException {
        final java.sql.ResultSetMetaData metaData = rs.getMetaData();
        final int columnCount = metaData.getColumnCount();

        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) {
                System.out.print(" | ");
            }
            System.out.print(metaData.getColumnName(i));
        }
        log.console("");

        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) {
                System.out.print("-+-");
            }
            for (int j = 0; j < metaData.getColumnName(i).length(); j++) {
                System.out.print("-");
            }
        }
        log.console("");

        int rowCount = 0;
        while (rs.next()) {
            rowCount++;
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) {
                    System.out.print(" | ");
                }
                final Object value = rs.getObject(i);
                System.out.print(value != null ? value.toString() : "NULL");
            }
            log.console("");
        }

        if (rowCount == 0) {
            System.out.println("(0 linhas)");
        } else {
            log.console("");
            System.out.println("Total: " + rowCount + " linha(s)");
        }
    }
}
