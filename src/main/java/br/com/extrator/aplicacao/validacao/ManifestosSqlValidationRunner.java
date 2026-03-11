/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/validacao/ManifestosSqlValidationRunner.java
Classe  : ManifestosSqlValidationRunner (final class)
Pacote  : br.com.extrator.aplicacao.validacao
Modulo  : Use Case - Validacao

Papel   : Executor de 5 testes SQL para validacao de manifestos (duplicados falsos, NULLs, pick_sequence_code, chave composta, resumo).

Conecta com:
- ManifestosQueryResultPrinter (para formatar saida SQL)
- LoggerConsole (suporte.console)

Fluxo geral:
1) executar(Connection) executa 5 testes SQL em sequencia (duplicados, NULL, distribuicao, integridade, resumo).
2) executarTeste() envolve cada teste com titulo e executa query.
3) Usa resultPrinter para exibir resultados em formato tabular.

Estrutura interna:
Atributos-chave:
- log: LoggerConsole (para logging).
- resultPrinter: ManifestosQueryResultPrinter (para formatacao de saida).
Metodos principais:
- executar(Connection): orquestra 5 testes.
- executarTeste(Connection, String, String): executa SQL individual com titulo.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.validacao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import br.com.extrator.suporte.console.LoggerConsole;

/**
 * Executa consultas SQL auxiliares de validacao para manifestos.
 */
final class ManifestosSqlValidationRunner {
    private final LoggerConsole log;
    private final ManifestosQueryResultPrinter resultPrinter;

    ManifestosSqlValidationRunner(final LoggerConsole log) {
        this.log = log;
        this.resultPrinter = new ManifestosQueryResultPrinter(log);
    }

    void executar(final Connection conn) throws SQLException {
        System.out.println("📄 IDENTIFICAR DUPLICADOS FALSOS:");
        System.out.println("(Manifestos com mesma chave de negocio mas identificador_unico diferente)");
        log.console("");

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 """
                    SELECT
                      sequence_code,
                      COALESCE(CAST(pick_sequence_code AS VARCHAR(50)), 'NULL') as pick_sequence_code,
                      COALESCE(CAST(mdfe_number AS VARCHAR(50)), 'NULL') as mdfe_number,
                      COUNT(*) as total_registros,
                      COUNT(DISTINCT identificador_unico) as identificadores_unicos,
                      MIN(data_extracao) as primeira_extracao,
                      MAX(data_extracao) as ultima_extracao
                    FROM manifestos
                    GROUP BY
                      sequence_code,
                      COALESCE(CAST(pick_sequence_code AS VARCHAR(50)), 'NULL'),
                      COALESCE(CAST(mdfe_number AS VARCHAR(50)), 'NULL')
                    HAVING COUNT(*) > 1 AND COUNT(DISTINCT identificador_unico) > 1
                    ORDER BY COUNT(*) DESC""")) {
            resultPrinter.exibirResultado(rs);
        }

        log.console("");
        log.console("===============================================================================");
        log.console("");

        System.out.println("📄 VALIDAÇÃO DA CORREÇÃO DO IDENTIFICADOR ÚNICO:");
        log.console("");

        executarTeste(
            conn,
            "TESTE 1: Verificar duplicados falsos",
            """
               SELECT
                 sequence_code,
                 COALESCE(CAST(pick_sequence_code AS VARCHAR(50)), 'NULL') as pick_sequence_code,
                 COALESCE(CAST(mdfe_number AS VARCHAR(50)), 'NULL') as mdfe_number,
                 COUNT(*) as total
               FROM manifestos
               GROUP BY
                 sequence_code,
                 COALESCE(CAST(pick_sequence_code AS VARCHAR(50)), 'NULL'),
                 COALESCE(CAST(mdfe_number AS VARCHAR(50)), 'NULL')
               HAVING COUNT(*) > 1""");

        executarTeste(
            conn,
            "TESTE 2: Verificar identificadores NULL",
            "SELECT COUNT(*) as total_com_identificador_null FROM manifestos WHERE identificador_unico IS NULL");

        executarTeste(
            conn,
            "TESTE 3: Distribuição de pick_sequence_code",
            """
               SELECT
                 CASE
                   WHEN pick_sequence_code IS NOT NULL THEN 'Com pick_sequence_code'
                   ELSE 'Sem pick_sequence_code (usa mdfe/hash)'
                 END as tipo,
                 COUNT(*) as total,
                 CAST(COUNT(*) * 100.0 / (SELECT COUNT(*) FROM manifestos) AS DECIMAL(5,2)) as percentual
               FROM manifestos
               GROUP BY
                 CASE
                   WHEN pick_sequence_code IS NOT NULL THEN 'Com pick_sequence_code'
                   ELSE 'Sem pick_sequence_code (usa mdfe/hash)'
                 END""");

        executarTeste(
            conn,
            "TESTE 4: Integridade de chave composta",
            """
               SELECT
                 sequence_code,
                 identificador_unico,
                 COUNT(*) as total
               FROM manifestos
               GROUP BY sequence_code, identificador_unico
               HAVING COUNT(*) > 1""");

        executarTeste(
            conn,
            "TESTE 5: Resumo final",
            """
               SELECT
                 'Total de manifestos' as metrica,
                 COUNT(*) as valor
               FROM manifestos
               UNION ALL
               SELECT
                 'Com pick_sequence_code' as metrica,
                 COUNT(*) as valor
               FROM manifestos
               WHERE pick_sequence_code IS NOT NULL
               UNION ALL
               SELECT
                 'Sem pick_sequence_code (usa mdfe/hash)' as metrica,
                 COUNT(*) as valor
               FROM manifestos
               WHERE pick_sequence_code IS NULL
               UNION ALL
               SELECT
                 'Com identificador_unico NULL' as metrica,
                 COUNT(*) as valor
               FROM manifestos
               WHERE identificador_unico IS NULL
               UNION ALL
               SELECT
                 'Duplicados falsos (mesma chave de negocio)' as metrica,
                 COUNT(*) as valor
               FROM (
                 SELECT sequence_code
                 FROM manifestos
                 GROUP BY
                   sequence_code,
                   COALESCE(CAST(pick_sequence_code AS VARCHAR(50)), 'NULL'),
                   COALESCE(CAST(mdfe_number AS VARCHAR(50)), 'NULL')
                 HAVING COUNT(*) > 1
               ) as duplicados""");
    }

    private void executarTeste(
        final Connection conn,
        final String titulo,
        final String sql
    ) throws SQLException {
        System.out.println(titulo);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            resultPrinter.exibirResultado(rs);
        }
        log.console("");
    }
}
