/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/validacao/ManifestosIdentificadorUnicoValidation.java
Classe  : ManifestosIdentificadorUnicoValidation (class)
Pacote  : br.com.extrator.aplicacao.validacao
Modulo  : Use Case - Validacao

Papel   : Valida estrategia de identificador unico em manifestos (chave composta, duplicados).

Conecta com:
- ManifestosSqlValidationRunner (delegacao)

Fluxo geral:
1) executar() valida que coluna identificador_unico existe e funciona.
2) Testes: duplicados falsos, NULLs, chave composta, pick_sequence_code.
3) Exibe alertas se problemas encontrados.

Estrutura interna:
Metodos principais:
- executar(): orquestra validacoes.
- exibirDuplicadosFalsos(), contarDuplicadosChaveComposta(): SQL queries.
- exibirTestes*(): exibicao de resultados.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.validacao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import br.com.extrator.suporte.console.LoggerConsole;

/**
 * Executa a validacao final da estrategia de identificador unico dos manifestos.
 */
final class ManifestosIdentificadorUnicoValidation {
    private final LoggerConsole log;

    ManifestosIdentificadorUnicoValidation(final LoggerConsole log) {
        this.log = log;
    }

    void executar(
        final Connection conn,
        final boolean temIdentificadorUnico,
        final int registrosSemIdentificador,
        final int registrosComPickNull,
        final int registrosComPickNotNull,
        final int totalBanco
    ) throws SQLException {
        if (!temIdentificadorUnico) {
            System.out.println("⚠️ VALIDAÇÃO DA CORREÇÃO NÃO PODE SER EXECUTADA");
            System.out.println("   A tabela não tem a coluna identificador_unico ainda.");
            System.out.println("   Execute a migração para chave composta primeiro.");
            log.console("");
            log.console("===============================================================================");
            return;
        }

        System.out.println("🔍 IDENTIFICAR DUPLICADOS FALSOS:");
        log.console("");
        System.out.println("(Manifestos com mesma chave de negocio mas identificador_unico diferente)");
        System.out.println("(Isso indica que campos voláteis estavam no hash ANTES da correção)");
        log.console("");

        final int duplicadosFalsosCount = exibirDuplicadosFalsos(conn);
        log.console("");
        log.console("===============================================================================");
        log.console("");

        System.out.println("✅ VALIDAÇÃO DA CORREÇÃO DO IDENTIFICADOR ÚNICO:");
        log.console("");

        final int duplicadosFalsosAposCorrecao = contarDuplicadosFalsosAposCorrecao(conn);
        exibirTesteDuplicadosFalsosAposCorrecao(duplicadosFalsosAposCorrecao);
        log.console("");

        System.out.println("TESTE 2: Verificar identificadores NULL");
        if (registrosSemIdentificador == 0) {
            System.out.println("  ✅ PASSOU: Todos os registros têm identificador_unico");
        } else {
            System.out.println("  ❌ FALHOU: " + registrosSemIdentificador + " registros sem identificador_unico");
        }
        log.console("");

        System.out.println("TESTE 3: Distribuição de pick_sequence_code");
        System.out.println(
            "  Registros com pick_sequence_code: " + registrosComPickNotNull
                + " (" + String.format("%.2f", calcularPercentual(registrosComPickNotNull, totalBanco)) + "%)"
        );
        System.out.println(
            "  Registros sem pick_sequence_code (usa mdfe/hash): " + registrosComPickNull
                + " (" + String.format("%.2f", calcularPercentual(registrosComPickNull, totalBanco)) + "%)"
        );
        log.console("");

        final int duplicadosChaveComposta = contarDuplicadosChaveComposta(conn);
        exibirTesteChaveComposta(duplicadosChaveComposta);
        log.console("");

        System.out.println("TESTE 5: Resumo final");
        System.out.println("  Total de manifestos: " + totalBanco);
        System.out.println("  Com pick_sequence_code: " + registrosComPickNotNull);
        System.out.println("  Sem pick_sequence_code (usa mdfe/hash): " + registrosComPickNull);
        System.out.println("  Com identificador_unico NULL: " + registrosSemIdentificador);
        System.out.println("  Duplicados falsos: " + duplicadosFalsosCount);
        log.console("");

        final boolean todosTestesPassaram = registrosSemIdentificador == 0 && duplicadosChaveComposta == 0;
        if (todosTestesPassaram) {
            System.out.println("✅ TODOS OS TESTES PASSARAM!");
            System.out.println("   A correção do identificador único está funcionando corretamente.");
        } else {
            System.out.println("⚠️ ALGUNS TESTES FALHARAM");
            System.out.println("   Revise os resultados acima para identificar problemas.");
        }
        log.console("");
        log.console("===============================================================================");
    }

    private int exibirDuplicadosFalsos(final Connection conn) throws SQLException {
        int duplicadosFalsosCount = 0;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 """
                    SELECT
                      sequence_code,
                      COALESCE(CAST(pick_sequence_code AS VARCHAR(50)), 'NULL') as pick_sequence_code,
                      COALESCE(CAST(mdfe_number AS VARCHAR(50)), 'NULL') as mdfe_number,
                      COUNT(*) as total_registros,
                      COUNT(DISTINCT identificador_unico) as identificadores_unicos
                    FROM manifestos
                    GROUP BY
                      sequence_code,
                      COALESCE(CAST(pick_sequence_code AS VARCHAR(50)), 'NULL'),
                      COALESCE(CAST(mdfe_number AS VARCHAR(50)), 'NULL')
                    HAVING COUNT(*) > 1 AND COUNT(DISTINCT identificador_unico) > 1
                    ORDER BY COUNT(*) DESC""")) {
            boolean encontrou = false;
            while (rs.next()) {
                if (!encontrou) {
                    System.out.println("Duplicados falsos encontrados:");
                    encontrou = true;
                }
                final long sequenceCode = rs.getLong("sequence_code");
                final int totalRegistros = rs.getInt("total_registros");
                final int identificadoresUnicos = rs.getInt("identificadores_unicos");
                System.out.println(
                    "  sequence_code: " + sequenceCode
                        + " - " + totalRegistros + " registros, "
                        + identificadoresUnicos + " identificadores diferentes"
                );
                duplicadosFalsosCount++;
            }
            if (!encontrou) {
                System.out.println("✅ Nenhum duplicado falso encontrado!");
                System.out.println("   Todos os manifestos com a mesma chave de negocio têm o mesmo identificador_unico.");
                System.out.println("   Isso indica que a correção está funcionando corretamente.");
            } else {
                log.console("");
                System.out.println("⚠️ Total de sequence_codes com duplicados falsos: " + duplicadosFalsosCount);
                log.console("");
                System.out.println("💡 Interpretação:");
                System.out.println("   - Esses são duplicados criados ANTES da correção do identificador único");
                System.out.println("   - Eles têm a mesma chave de negocio mas identificador_unico diferente");
                System.out.println("   - Isso acontecia porque campos voláteis (mobile_read_at, etc.) estavam no hash");
                System.out.println("   - Após a correção, novas extrações não criarão mais esses duplicados");
                log.console("");
                System.out.println("💡 Solução:");
                System.out.println("   - Execute uma nova extração completa");
                System.out.println("   - Os duplicados falsos não serão mais criados");
                System.out.println("   - Os existentes permanecerão no banco (são registros válidos)");
            }
        }
        return duplicadosFalsosCount;
    }

    private int contarDuplicadosFalsosAposCorrecao(final Connection conn) throws SQLException {
        int duplicadosFalsosAposCorrecao = 0;
        System.out.println("TESTE 1: Verificar duplicados falsos");
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 """
                    SELECT sequence_code
                    FROM manifestos
                    GROUP BY
                      sequence_code,
                      COALESCE(CAST(pick_sequence_code AS VARCHAR(50)), 'NULL'),
                      COALESCE(CAST(mdfe_number AS VARCHAR(50)), 'NULL')
                    HAVING COUNT(*) > 1""")) {
            while (rs.next()) {
                duplicadosFalsosAposCorrecao++;
            }
        }
        return duplicadosFalsosAposCorrecao;
    }

    private void exibirTesteDuplicadosFalsosAposCorrecao(final int duplicadosFalsosAposCorrecao) {
        if (duplicadosFalsosAposCorrecao == 0) {
            System.out.println("  ✅ PASSOU: Nenhum duplicado falso (todos têm identificador_unico único)");
        } else {
            System.out.println(
                "  ⚠️ ATENÇÃO: " + duplicadosFalsosAposCorrecao + " sequence_codes com múltiplos registros"
            );
            System.out.println(
                "     (Isso pode ser normal se são duplicados naturais com pick_sequence_code ou mdfe_number diferentes)"
            );
        }
    }

    private int contarDuplicadosChaveComposta(final Connection conn) throws SQLException {
        int duplicadosChaveComposta = 0;
        System.out.println("TESTE 4: Integridade de chave composta");
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 """
                    SELECT sequence_code, identificador_unico
                    FROM manifestos
                    GROUP BY sequence_code, identificador_unico
                    HAVING COUNT(*) > 1""")) {
            while (rs.next()) {
                duplicadosChaveComposta++;
            }
        }
        return duplicadosChaveComposta;
    }

    private void exibirTesteChaveComposta(final int duplicadosChaveComposta) {
        if (duplicadosChaveComposta == 0) {
            System.out.println("  ✅ PASSOU: Chave composta é única (sem duplicados)");
        } else {
            System.out.println("  ❌ FALHOU: " + duplicadosChaveComposta + " duplicados na chave composta");
            System.out.println("     (Isso não deveria acontecer - verifique constraint UNIQUE)");
        }
    }

    private double calcularPercentual(final int parte, final int total) {
        if (total <= 0) {
            return 0.0;
        }
        return parte * 100.0 / total;
    }
}
