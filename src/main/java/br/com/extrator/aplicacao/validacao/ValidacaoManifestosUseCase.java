/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/validacao/ValidacaoManifestosUseCase.java
Classe  : ValidacaoManifestosUseCase (class)
Pacote  : br.com.extrator.aplicacao.validacao
Modulo  : Use Case - Validacao

Papel   : Valida integridade de manifestos (contagem, duplicados, identificadores, pick_sequence_code).

Conecta com:
- ManifestosSqlValidationRunner, ManifestosIdentificadorUnicoValidation, ManifestosValidationQueries (delegacao)

Fluxo geral:
1) executar() orquestra validacoes profundas de manifestos.
2) Compara API x BD, verifica estrutura e duplicados.
3) Exibe analises detalhadas e resumo.

Estrutura interna:
Delegacao:
- sqlValidationRunner: executa SQLs customizadas.
- identificadorUnicoValidation: valida chave composta.
- queries: helpers para consultas.
Metodos principais:
- executar(), validarManifestos(): orquestra tudo.
- exibir*(): exibicao de resultados.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.validacao;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import br.com.extrator.suporte.banco.GerenciadorConexao;
import br.com.extrator.suporte.console.LoggerConsole;

public class ValidacaoManifestosUseCase {
    private static final LoggerConsole log = LoggerConsole.getLogger(ValidacaoManifestosUseCase.class);
    private final ManifestosSqlValidationRunner sqlValidationRunner = new ManifestosSqlValidationRunner(log);
    private final ManifestosIdentificadorUnicoValidation identificadorUnicoValidation =
        new ManifestosIdentificadorUnicoValidation(log);
    private final ManifestosValidationQueries queries = new ManifestosValidationQueries(log);

    public void executar() throws Exception {
        log.console("===============================================================================");
        log.info("                    VALIDACAO DE MANIFESTOS");
        log.console("===============================================================================");

        try (Connection conn = GerenciadorConexao.obterConexao()) {
            validarManifestos(conn);

            log.console("");
            log.console("===============================================================================");
            log.info("                    EXECUTANDO SQLs DE VALIDACAO");
            log.console("===============================================================================");

            sqlValidationRunner.executar(conn);
        } catch (final SQLException e) {
            log.error("Erro ao validar manifestos: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void validarManifestos(final Connection conn) throws SQLException {
        System.out.println("ULTIMA EXTRACAO:");
        log.console("");

        Integer registrosExtraidos = null;
        String timestampFim = null;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 """
                    SELECT TOP 1 registros_extraidos, status_final,
                    CONVERT(VARCHAR, timestamp_fim, 120) as timestamp_fim,
                    mensagem
                    FROM log_extracoes
                    WHERE entidade = 'manifestos'
                    ORDER BY timestamp_fim DESC""")) {
            if (rs.next()) {
                registrosExtraidos = rs.getInt("registros_extraidos");
                final String statusFinal = rs.getString("status_final");
                timestampFim = rs.getString("timestamp_fim");
                final String mensagem = rs.getString("mensagem");
                System.out.println("Data/Hora fim: " + timestampFim);
                System.out.println("Registros extraidos (API): " + registrosExtraidos);
                System.out.println("Status: " + statusFinal);
                if (mensagem != null && !mensagem.trim().isEmpty()) {
                    System.out.println("Mensagem: " + mensagem);
                }
            } else {
                System.out.println("Nenhuma extracao de manifestos encontrada no log_extracoes.");
            }
        }
        log.console("");

        System.out.println("CONTAGEM NO BANCO:");
        log.console("");

        final int totalBanco = queries.contar(conn, "SELECT COUNT(*) as total FROM manifestos");
        final int totalUltimas24h = queries.contar(
            conn,
            "SELECT COUNT(*) as total FROM manifestos WHERE data_extracao >= DATEADD(HOUR, -24, GETDATE())"
        );
        int totalDesdeUltimaExtracao = timestampFim == null
            ? 0
            : queries.contarDesdeUltimaExtracaoComFallback(conn);

        log.info("Total de registros na tabela (todos): {}", totalBanco);
        log.info("Total de registros (ultimas 24h): {}", totalUltimas24h);
        if (timestampFim != null) {
            totalDesdeUltimaExtracao = queries.contarDesdeUltimaExtracaoComFallback(conn);
            log.info("Total de registros (desde ultima extracao): {}", totalDesdeUltimaExtracao);
        }
        log.console("");

        System.out.println("COMPARACAO:");
        log.console("");

        if (registrosExtraidos != null) {
            exibirComparacao(
                registrosExtraidos,
                timestampFim,
                totalBanco,
                totalUltimas24h,
                totalDesdeUltimaExtracao
            );
        } else {
            System.out.println("Nao foi possivel comparar - nenhuma extracao encontrada no log.");
            System.out.println("Total no banco (ultimas 24h): " + totalUltimas24h);
            System.out.println("Total no banco (todos): " + totalBanco);
        }
        log.console("");

        System.out.println("DUPLICADOS (por sequence_code):");
        log.console("");
        final int duplicadosCount = exibirDuplicadosPorSequenceCode(conn);
        log.console("");

        System.out.println("VERIFICACAO DE ESTRUTURA (identificador_unico):");
        log.console("");

        final boolean temIdentificadorUnico = queries.existeColunaIdentificadorUnico(conn);
        final int registrosSemIdentificador = temIdentificadorUnico
            ? queries.contar(conn, "SELECT COUNT(*) as total FROM manifestos WHERE identificador_unico IS NULL")
            : 0;
        final int identificadoresInvalidos = temIdentificadorUnico
            ? queries.contar(conn, "SELECT COUNT(*) as total FROM manifestos WHERE LEN(identificador_unico) > 100")
            : 0;

        exibirAnaliseEstrutura(conn, temIdentificadorUnico, registrosSemIdentificador, identificadoresInvalidos);
        log.console("");

        System.out.println("ANALISE DE pick_sequence_code:");
        log.console("");

        final int[] pickStats = queries.contarPickSequenceCode(conn);
        final int registrosComPickNull = pickStats[0];
        final int registrosComPickNotNull = pickStats[1];
        System.out.println("Registros com pick_sequence_code NULL: " + registrosComPickNull);
        System.out.println("Registros com pick_sequence_code nao NULL: " + registrosComPickNotNull);
        if (temIdentificadorUnico) {
            log.console("");
            System.out.println(
                "Registros com pick_sequence_code NULL usam sequence_code+mdfe_number quando ha MDF-e; sem MDF-e usam hash do metadata."
            );
            System.out.println(
                "Registros com pick_sequence_code nao NULL usam pick_sequence_code ou pick_sequence_code+mdfe_number."
            );
        }
        log.console("");

        if (registrosExtraidos != null && registrosExtraidos > totalUltimas24h) {
            exibirAnaliseDetalhadaDiferenca(
                conn,
                registrosExtraidos,
                totalUltimas24h,
                temIdentificadorUnico
            );
            log.console("");
        }

        exibirResumoFinal(
            registrosExtraidos,
            timestampFim,
            totalBanco,
            totalUltimas24h,
            totalDesdeUltimaExtracao,
            duplicadosCount,
            temIdentificadorUnico,
            registrosSemIdentificador,
            registrosComPickNull
        );

        identificadorUnicoValidation.executar(
            conn,
            temIdentificadorUnico,
            registrosSemIdentificador,
            registrosComPickNull,
            registrosComPickNotNull,
            totalBanco
        );
    }

    private void exibirComparacao(final int registrosExtraidos,
                                  final String timestampFim,
                                  final int totalBanco,
                                  final int totalUltimas24h,
                                  final int totalDesdeUltimaExtracao) {
        System.out.println("Registros no log_extracoes (ultima execucao): " + registrosExtraidos);
        System.out.println("Registros no banco (ultimas 24h): " + totalUltimas24h);
        if (timestampFim != null) {
            System.out.println("Registros no banco (desde ultima extracao): " + totalDesdeUltimaExtracao);
        }
        System.out.println("Registros no banco (total): " + totalBanco);
        log.console("");
        System.out.println("NOTA: O valor em 'log_extracoes' e da ultima execucao registrada.");
        System.out.println("   - Se for de antes da deduplicacao, pode incluir duplicados da API");
        System.out.println("   - Apos deduplicacao, esse valor deve coincidir com os registros no banco");
        System.out.println("   - Execute uma nova extracao para ver os valores atualizados");
        log.console("");

        final Comparacao comparacao = calcularComparacao(
            registrosExtraidos,
            timestampFim,
            totalUltimas24h,
            totalDesdeUltimaExtracao
        );
        exibirResultadoComparacao(registrosExtraidos, timestampFim, totalUltimas24h, totalDesdeUltimaExtracao, comparacao);
    }

    private Comparacao calcularComparacao(final int registrosExtraidos,
                                          final String timestampFim,
                                          final int totalUltimas24h,
                                          final int totalDesdeUltimaExtracao) {
        if (timestampFim != null) {
            if (totalDesdeUltimaExtracao >= 0) {
                return new Comparacao(registrosExtraidos - totalDesdeUltimaExtracao, "desde ultima extracao");
            }
            return new Comparacao(registrosExtraidos - totalUltimas24h, "ultimas 24h");
        }
        return new Comparacao(registrosExtraidos - totalUltimas24h, "ultimas 24h");
    }

    private void exibirResultadoComparacao(final int registrosExtraidos,
                                           final String timestampFim,
                                           final int totalUltimas24h,
                                           final int totalDesdeUltimaExtracao,
                                           final Comparacao comparacao) {
        final int diferenca = comparacao.diferenca();
        final String tipoComparacao = comparacao.tipo();
        if (diferenca == 0) {
            System.out.println("OK - Numeros coincidem (" + tipoComparacao + ")!");
            System.out.println("   O valor do log_extracoes corresponde aos registros no banco.");
            System.out.println("   Isso indica que a extracao funcionou corretamente.");
            return;
        }

        if (diferenca > 0) {
            System.out.println(
                "DIFERENCA: " + diferenca + " registros a mais no log que no banco (" + tipoComparacao + ")"
            );
            System.out.println("   Valor no log_extracoes: " + registrosExtraidos);
            if (timestampFim != null) {
                System.out.println("   Encontrado no banco (desde ultima extracao): " + totalDesdeUltimaExtracao);
            }
            System.out.println("   Encontrado no banco (ultimas 24h): " + totalUltimas24h);
            log.console("");
            System.out.println("Interpretacao:");
            System.out.println("   - Se o log e antigo: normal, duplicados podem ter sido removidos");
            System.out.println("   - Se o log e recente: pode indicar UPDATEs em vez de INSERTs");
            System.out.println("   - UPDATEs nao adicionam linhas, entao ha menos linhas no banco");
            log.console("");
            System.out.println("Se a diferenca for muito grande, verificar:");
            System.out.println("   - erro durante salvamento");
            System.out.println("   - falha silenciosa na validacao");
            System.out.println("   - problema com chave composta");
            log.console("");
            System.out.println("RECOMENDACAO: Execute uma nova extracao para gerar log atualizado com deduplicacao.");
            return;
        }

        System.out.println("ATENCAO - Ha " + Math.abs(diferenca) + " registros a mais no banco!");
        System.out.println("   Valor no log_extracoes: " + registrosExtraidos);
        if (timestampFim != null) {
            System.out.println("   Encontrado no banco (desde ultima extracao): " + totalDesdeUltimaExtracao);
        }
        System.out.println("   Encontrado no banco (ultimas 24h): " + totalUltimas24h);
        log.console("");
        System.out.println("Possiveis causas:");
        System.out.println("   - execucoes anteriores adicionaram registros");
        System.out.println("   - duplicados naturais estao sendo preservados");
        System.out.println("   - dados de periodos anteriores ainda estao no banco");
        System.out.println("   - o log_extracoes pode estar desatualizado");
    }

    private int exibirDuplicadosPorSequenceCode(final Connection conn) throws SQLException {
        int duplicadosCount = 0;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 """
                    SELECT sequence_code, COUNT(*) as quantidade
                    FROM manifestos
                    GROUP BY sequence_code
                    HAVING COUNT(*) > 1
                    ORDER BY quantidade DESC""")) {
            boolean encontrou = false;
            while (rs.next()) {
                if (!encontrou) {
                    System.out.println("Duplicados encontrados:");
                    encontrou = true;
                }
                System.out.println(
                    "  sequence_code: " + rs.getLong("sequence_code") + " - " + rs.getInt("quantidade") + " registros"
                );
                duplicadosCount++;
            }
            if (!encontrou) {
                System.out.println("Nenhum duplicado encontrado por sequence_code.");
            } else {
                log.console("");
                System.out.println("Total de sequence_codes com duplicados: " + duplicadosCount);
            }
        }
        return duplicadosCount;
    }

    private void exibirAnaliseEstrutura(final Connection conn,
                                        final boolean temIdentificadorUnico,
                                        final int registrosSemIdentificador,
                                        final int identificadoresInvalidos) throws SQLException {
        if (!temIdentificadorUnico) {
            System.out.println("Coluna identificador_unico nao existe ainda (tabela nao migrada).");
            System.out.println("   Isso significa que a tabela ainda usa a estrutura antiga.");
            return;
        }

        System.out.println("Coluna identificador_unico existe.");
        if (registrosSemIdentificador > 0) {
            System.out.println("PROBLEMA: " + registrosSemIdentificador + " registros com identificador_unico NULL!");
        } else {
            System.out.println("Todos os registros tem identificador_unico.");
        }

        if (identificadoresInvalidos > 0) {
            System.out.println(
                "PROBLEMA: " + identificadoresInvalidos + " registros com identificador_unico muito longo (>100 chars)!"
            );
        }

        final int duplicadosChaveComposta = queries.contarDuplicadosChaveComposta(conn);
        if (duplicadosChaveComposta == 0) {
            System.out.println("Nenhum duplicado na chave composta (correto - MERGE esta funcionando).");
        } else {
            System.out.println("PROBLEMA: " + duplicadosChaveComposta + " duplicados na chave composta!");
            System.out.println("   Isso nao deveria acontecer com a constraint UNIQUE.");
        }
    }

    private void exibirAnaliseDetalhadaDiferenca(final Connection conn,
                                                 final int registrosExtraidos,
                                                 final int totalUltimas24h,
                                                 final boolean temIdentificadorUnico) throws SQLException {
        System.out.println("ANALISE DETALHADA DA DIFERENCA:");
        log.console("");
        System.out.println("Faltam " + (registrosExtraidos - totalUltimas24h) + " registros.");
        log.console("");

        exibirAnaliseDataExtracao(conn);
        exibirAlertasIntegridade(conn, temIdentificadorUnico);

        if (temIdentificadorUnico) {
            log.console("");
            System.out.println("Possiveis causas:");
            System.out.println("1. MERGE retornou rowsAffected > 0 mas registro nao foi realmente salvo");
            System.out.println("2. Problema com commit/transacao");
            System.out.println("3. Registro foi inserido mas depois deletado por trigger/constraint");
            System.out.println("4. Problema de timezone entre data_extracao e comparacao");
            System.out.println("5. Um registro foi contado duas vezes no rowsAffected");
        } else {
            log.console("");
            System.out.println("ATENCAO: Tabela nao migrada para chave composta!");
            System.out.println("   A tabela ainda usa sequence_code como PRIMARY KEY.");
            System.out.println("   Duplicados naturais podem estar sendo sobrescritos.");
            log.console("");
            System.out.println("Solucao: Execute a migracao para chave composta.");
        }
        log.console("");
        System.out.println("Acoes recomendadas:");
        System.out.println("1. Verificar logs da ultima extracao");
        System.out.println("2. Verificar triggers na tabela manifestos");
        System.out.println("3. Verificar problemas de timezone em data_extracao");
        System.out.println("4. Considerar logging mais detalhado no MERGE");
    }

    private void exibirAnaliseDataExtracao(final Connection conn) {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 """
                    SELECT
                      MIN(data_extracao) as data_minima,
                      MAX(data_extracao) as data_maxima,
                      COUNT(*) as total
                    FROM manifestos
                    WHERE data_extracao >= DATEADD(HOUR, -24, GETDATE())""")) {
            if (rs.next()) {
                System.out.println("Analise de data_extracao (ultimas 24h):");
                System.out.println("   Data minima: " + valorOuPadrao(rs.getString("data_minima")));
                System.out.println("   Data maxima: " + valorOuPadrao(rs.getString("data_maxima")));
                System.out.println("   Total: " + rs.getInt("total"));
                log.console("");
            }
        } catch (final SQLException e) {
            log.warn("Erro ao analisar data_extracao: {}", e.getMessage());
        }
    }

    private void exibirAlertasIntegridade(final Connection conn, final boolean temIdentificadorUnico) throws SQLException {
        if (temIdentificadorUnico) {
            final int duplicadosExatos = queries.contarDuplicadosUltimas24h(conn);
            if (duplicadosExatos > 0) {
                System.out.println(
                    "ATENCAO: " + duplicadosExatos + " pares (sequence_code, identificador_unico) duplicados!"
                );
                System.out.println("   Isso nao deveria acontecer com a constraint UNIQUE.");
            }
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 """
                    SELECT COUNT(*) as total
                    FROM manifestos
                    WHERE data_extracao IS NULL""")) {
            if (rs.next() && rs.getInt("total") > 0) {
                System.out.println("ATENCAO: " + rs.getInt("total") + " registros com data_extracao NULL!");
            }
        } catch (final SQLException e) {
            log.warn("Erro ao verificar data_extracao NULL: {}", e.getMessage());
        }
    }

    private void exibirResumoFinal(final Integer registrosExtraidos,
                                   final String timestampFim,
                                   final int totalBanco,
                                   final int totalUltimas24h,
                                   final int totalDesdeUltimaExtracao,
                                   final int duplicadosCount,
                                   final boolean temIdentificadorUnico,
                                   final int registrosSemIdentificador,
                                   final int registrosComPickNull) {
        System.out.println("RESUMO FINAL:");
        log.console("");
        System.out.println("Total no banco (ultimas 24h): " + totalUltimas24h);
        if (timestampFim != null && totalDesdeUltimaExtracao > 0) {
            System.out.println("Total no banco (desde ultima extracao): " + totalDesdeUltimaExtracao);
        }
        System.out.println("Total no banco (todos): " + totalBanco);
        if (registrosExtraidos != null) {
            System.out.println("Total extraido (API): " + registrosExtraidos);
            if (timestampFim != null && totalDesdeUltimaExtracao > 0) {
                System.out.println("Diferenca (desde ultima extracao): " + (registrosExtraidos - totalDesdeUltimaExtracao));
            }
            System.out.println("Diferenca (ultimas 24h): " + (registrosExtraidos - totalUltimas24h));
        }
        System.out.println("Duplicados por sequence_code: " + duplicadosCount);
        if (temIdentificadorUnico) {
            System.out.println("Registros sem identificador_unico: " + registrosSemIdentificador);
        }
        System.out.println("Registros com pick_sequence_code NULL: " + registrosComPickNull);
        log.console("");
        log.console("===============================================================================");
        log.console("");
    }

    private String valorOuPadrao(final String valor) {
        return valor != null ? valor : "N/A";
    }

    private record Comparacao(int diferenca, String tipo) {
    }
}
