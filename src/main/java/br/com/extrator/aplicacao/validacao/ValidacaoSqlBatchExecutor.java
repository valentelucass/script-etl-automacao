/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/validacao/ValidacaoSqlBatchExecutor.java
Classe  : ValidacaoSqlBatchExecutor (final class)
Pacote  : br.com.extrator.aplicacao.validacao
Modulo  : Use Case - Validacao

Papel   : Executor de scripts SQL T-SQL em batches (split por GO, remove PRINT, processa ResultSets).

Conecta com:
- ValidacaoSqlResultPrinter (para exibicao de resultados)
- LoggerConsole (suporte.console)

Fluxo geral:
1) executar(Connection, String, String) recebe SQL script.
2) Remove linhas PRINT, divide por GO (case-insensitive).
3) Para cada batch: execute() e exibe ResultSet se presente.
4) Com fallback: ignora erros de PRINT/syntax.

Estrutura interna:
Atributos-chave:
- log: LoggerConsole (para warning de erros).
- resultPrinter: ValidacaoSqlResultPrinter (para exibicao).
Metodos principais:
- executar(Connection, String, String): executor principal.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.validacao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import br.com.extrator.suporte.console.LoggerConsole;

final class ValidacaoSqlBatchExecutor {
    private final LoggerConsole log;
    private final ValidacaoSqlResultPrinter resultPrinter;

    ValidacaoSqlBatchExecutor(final LoggerConsole log) {
        this.log = log;
        this.resultPrinter = new ValidacaoSqlResultPrinter(log);
    }

    void executar(final Connection conn, final String sql, final String scriptName) throws SQLException {
        String sqlProcessado = sql.replaceAll("(?i)^\\s*PRINT\\s+.*$", "");
        final String[] batches = sqlProcessado.split("(?i)^\\s*GO\\s*$", -1);

        for (final String batch : batches) {
            final String batchTrimmed = batch.trim();
            if (batchTrimmed.isEmpty()) {
                continue;
            }

            try (Statement stmt = conn.createStatement()) {
                boolean hasResultSet = stmt.execute(batchTrimmed);
                do {
                    if (hasResultSet) {
                        try (ResultSet rs = stmt.getResultSet()) {
                            resultPrinter.exibirResultado(rs);
                        }
                    }
                    hasResultSet = stmt.getMoreResults();
                } while (hasResultSet || stmt.getUpdateCount() != -1);
            } catch (final SQLException e) {
                log.warn("Erro ao executar batch do script {}: {}", scriptName, e.getMessage());
                final String mensagem = e.getMessage().toLowerCase();
                if (!mensagem.contains("print") && !mensagem.contains("incorrect syntax")) {
                    throw e;
                }
            }
        }
    }
}
