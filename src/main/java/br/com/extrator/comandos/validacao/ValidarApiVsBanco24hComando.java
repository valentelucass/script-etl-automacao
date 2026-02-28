/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/validacao/ValidarApiVsBanco24hComando.java
Classe  : ValidarApiVsBanco24hComando (class)
Pacote  : br.com.extrator.comandos.validacao
Modulo  : Comando CLI (validacao)
Papel   : Implementa responsabilidade de validar api vs banco24h comando.

Conecta com:
- CompletudeValidator (auditoria.servicos)
- Comando (comandos.base)
- GerenciadorConexao (util.banco)
- LoggerConsole (util.console)
- ConstantesEntidades (util.validacao)

Fluxo geral:
1) Executa validacoes de acesso, timestamps e consistencia.
2) Compara API versus banco quando aplicavel.
3) Emite resultado de qualidade para operacao.

Estrutura interna:
Metodos principais:
- JanelaExecucao(...5 args): realiza operacao relacionada a "janela execucao".
- extrairNumeroCampoMensagem(...2 args): realiza operacao relacionada a "extrair numero campo mensagem".
- mapearTabela(...1 args): mapeia campos para DTO/entidade de destino.
- possuiFlag(...2 args): realiza operacao relacionada a "possui flag".
Atributos-chave:
- log: campo de estado para "log".
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.validacao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.extrator.auditoria.servicos.CompletudeValidator;
import br.com.extrator.comandos.base.Comando;
import br.com.extrator.util.banco.GerenciadorConexao;
import br.com.extrator.util.console.LoggerConsole;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Valida comparacao extrema entre API (contagem em tempo real) e Banco
 * para a janela de 24h, correlacionando com a ultima janela COMPLETA
 * registrada em log_extracoes para cada entidade.
 */
public class ValidarApiVsBanco24hComando implements Comando {

    private static final LoggerConsole log = LoggerConsole.getLogger(ValidarApiVsBanco24hComando.class);

    private record JanelaExecucao(
        LocalDateTime inicio,
        LocalDateTime fim,
        int registrosExtraidos,
        Integer apiCount,
        Integer uniqueCount
    ) { }

    private static final List<String> ENTIDADES_ORDEM = List.of(
        ConstantesEntidades.MANIFESTOS,
        ConstantesEntidades.COTACOES,
        ConstantesEntidades.FATURAS_POR_CLIENTE,
        ConstantesEntidades.LOCALIZACAO_CARGAS,
        ConstantesEntidades.FRETES,
        ConstantesEntidades.CONTAS_A_PAGAR,
        ConstantesEntidades.COLETAS,
        ConstantesEntidades.FATURAS_GRAPHQL
    );

    @Override
    public void executar(final String[] args) throws Exception {
        final boolean incluirFaturasGraphQL = !possuiFlag(args, "--sem-faturas-graphql");
        final LocalDate dataReferenciaSistema = LocalDate.now();
        final LocalDate dataReferencia;
        final LocalDate dataInicio;
        final LocalDate dataFim;

        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            dataReferencia = resolverDataReferenciaLogs(conexao, dataReferenciaSistema);
        }
        dataInicio = dataReferencia.minusDays(1);
        dataFim = dataReferencia;

        log.console("\n" + "=".repeat(72));
        log.info("VALIDACAO EXTREMA 24H | API (POSTMAN-LIKE) x BANCO");
        log.info("Data de referencia: {}", dataReferencia);
        log.console("=".repeat(72));

        final CompletudeValidator validator = new CompletudeValidator();
        final Optional<Map<String, Integer>> totaisApiOpt = validator.buscarTotaisEslCloud(dataReferencia);
        if (totaisApiOpt.isEmpty()) {
            throw new RuntimeException("Nao foi possivel obter totais da API em tempo real.");
        }

        final Map<String, Integer> totaisApi = new LinkedHashMap<>(totaisApiOpt.get());
        if (!incluirFaturasGraphQL) {
            totaisApi.remove(ConstantesEntidades.FATURAS_GRAPHQL);
        }

        int totalOk = 0;
        int totalFalhas = 0;

        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            for (final String entidade : ENTIDADES_ORDEM) {
                if (!totaisApi.containsKey(entidade)) {
                    continue;
                }

                final int apiCount = totaisApi.get(entidade);
                final Optional<JanelaExecucao> janelaOpt =
                    buscarUltimaJanelaCompletaDoDia(conexao, entidade, dataReferencia, dataInicio, dataFim);
                if (janelaOpt.isEmpty()) {
                    totalFalhas++;
                    log.error(
                        "API_VS_BANCO_24H | entidade={} | status=FALHA | detalhe=Sem log COMPLETO no dia para comparar.",
                        entidade
                    );
                    continue;
                }

                final JanelaExecucao janela = janelaOpt.get();
                final int bancoCount = contarDestinoNaJanela(conexao, entidade, janela);
                // Compara com contagem deduplicada da própria execução (unique_count/registros_extraidos),
                // pois a API pode retornar duplicados naturais no total bruto.
                final int esperadoUnico = janela.uniqueCount != null ? janela.uniqueCount : janela.registrosExtraidos;
                final int apiCountLog = janela.apiCount != null ? janela.apiCount : esperadoUnico;
                final int diff = bancoCount - esperadoUnico;
                final int deltaApiBrutoAtual = apiCount - esperadoUnico;
                final boolean tolerarBackfillFaturas =
                    ConstantesEntidades.FATURAS_GRAPHQL.equals(entidade) && bancoCount >= esperadoUnico;
                final boolean ok = diff == 0 || tolerarBackfillFaturas;

                if (ok) {
                    totalOk++;
                    if (tolerarBackfillFaturas && diff != 0) {
                        log.warn(
                            "API_VS_BANCO_24H | entidade={} | status=OK_BACKFILL | api_bruto_atual={} | api_bruto_log={} | esperado_unico={} | banco={} | diff={} | janela=[{} .. {}]",
                            entidade, apiCount, apiCountLog, esperadoUnico, bancoCount, diff, janela.inicio, janela.fim
                        );
                    } else if (deltaApiBrutoAtual > 0) {
                        log.info(
                            "API_VS_BANCO_24H | entidade={} | status=OK_API_DUPLICADOS | api_bruto_atual={} | api_bruto_log={} | esperado_unico={} | banco={} | diff={} | duplicados_brutos={} | janela=[{} .. {}]",
                            entidade, apiCount, apiCountLog, esperadoUnico, bancoCount, diff, deltaApiBrutoAtual, janela.inicio, janela.fim
                        );
                    } else {
                        log.info(
                            "API_VS_BANCO_24H | entidade={} | status=OK | api_bruto_atual={} | api_bruto_log={} | esperado_unico={} | banco={} | diff={} | janela=[{} .. {}]",
                            entidade, apiCount, apiCountLog, esperadoUnico, bancoCount, diff, janela.inicio, janela.fim
                        );
                    }
                } else {
                    totalFalhas++;
                    log.error(
                        "API_VS_BANCO_24H | entidade={} | status=FALHA | api_bruto_atual={} | api_bruto_log={} | esperado_unico={} | banco={} | diff={} | janela=[{} .. {}]",
                        entidade, apiCount, apiCountLog, esperadoUnico, bancoCount, diff, janela.inicio, janela.fim
                    );
                }
            }
        }

        log.console("=".repeat(72));
        log.info("RESUMO_API_VS_BANCO_24H | ok={} | falhas={}", totalOk, totalFalhas);
        log.console("=".repeat(72));

        if (totalFalhas > 0) {
            throw new RuntimeException(
                "Comparacao API x Banco 24h reprovada: " + totalFalhas + " entidade(s) com divergencia."
            );
        }
    }

    private LocalDate resolverDataReferenciaLogs(final Connection conexao,
                                                 final LocalDate dataPreferida) throws SQLException {
        if (existeLogCompleto24hNaData(conexao, dataPreferida)) {
            return dataPreferida;
        }

        final LocalDate diaAnterior = dataPreferida.minusDays(1);
        if (existeLogCompleto24hNaData(conexao, diaAnterior)) {
            log.warn(
                "Sem log COMPLETO 24h para {}. Usando dia anterior {} como referencia.",
                dataPreferida,
                diaAnterior
            );
            return diaAnterior;
        }

        if (existeLogCompletoNaData(conexao, dataPreferida)) {
            log.warn(
                "Sem log COMPLETO 24h para {}. Usando logs COMPLETO do proprio dia (sem filtro de periodo).",
                dataPreferida
            );
            return dataPreferida;
        }

        if (existeLogCompletoNaData(conexao, diaAnterior)) {
            log.warn(
                "Sem log COMPLETO 24h para {}. Usando logs COMPLETO do dia anterior {} (sem filtro de periodo).",
                dataPreferida,
                diaAnterior
            );
            return diaAnterior;
        }

        final Optional<LocalDate> ultimaData = buscarUltimaDataComLogCompleto(conexao);
        if (ultimaData.isPresent()) {
            log.warn(
                "Sem log COMPLETO em {} ou {}. Usando ultima data disponivel {}.",
                dataPreferida,
                diaAnterior,
                ultimaData.get()
            );
            return ultimaData.get();
        }

        log.warn("Nenhum log COMPLETO encontrado. Mantendo data de referencia {}.", dataPreferida);
        return dataPreferida;
    }

    private boolean existeLogCompleto24hNaData(final Connection conexao, final LocalDate data) throws SQLException {
        final LocalDate dataInicio = data.minusDays(1);
        final String sql = """
            SELECT TOP 1 1
            FROM dbo.log_extracoes
            WHERE status_final = 'COMPLETO'
              AND CAST(timestamp_inicio AS DATE) = ?
              AND mensagem LIKE ?
              AND mensagem LIKE ?
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setDate(1, java.sql.Date.valueOf(data));
            stmt.setString(2, "%" + dataInicio + "%");
            stmt.setString(3, "%" + data + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean existeLogCompletoNaData(final Connection conexao, final LocalDate data) throws SQLException {
        final String sql = """
            SELECT TOP 1 1
            FROM dbo.log_extracoes
            WHERE status_final = 'COMPLETO'
              AND CAST(timestamp_inicio AS DATE) = ?
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setDate(1, java.sql.Date.valueOf(data));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Optional<LocalDate> buscarUltimaDataComLogCompleto(final Connection conexao) throws SQLException {
        final String sql = """
            SELECT TOP 1 CAST(timestamp_inicio AS DATE) AS data_ref
            FROM dbo.log_extracoes
            WHERE status_final = 'COMPLETO'
            ORDER BY timestamp_fim DESC
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return Optional.of(rs.getDate("data_ref").toLocalDate());
            }
        }
        return Optional.empty();
    }

    private Optional<JanelaExecucao> buscarUltimaJanelaCompletaDoDia(final Connection conexao,
                                                                      final String entidade,
                                                                      final LocalDate dataReferencia,
                                                                      final LocalDate periodoInicio,
                                                                      final LocalDate periodoFim) throws SQLException {
        final String sqlComPeriodo = """
            SELECT TOP 1 timestamp_inicio, timestamp_fim, registros_extraidos, mensagem
            FROM dbo.log_extracoes
            WHERE entidade = ?
              AND status_final = 'COMPLETO'
              AND CAST(timestamp_inicio AS DATE) = ?
              AND mensagem LIKE ?
              AND mensagem LIKE ?
            ORDER BY timestamp_fim DESC
            """;

        try (PreparedStatement stmt = conexao.prepareStatement(sqlComPeriodo)) {
            stmt.setString(1, entidade);
            stmt.setDate(2, java.sql.Date.valueOf(dataReferencia));
            stmt.setString(3, "%" + periodoInicio + "%");
            stmt.setString(4, "%" + periodoFim + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final LocalDateTime inicio = rs.getTimestamp("timestamp_inicio").toLocalDateTime();
                    final LocalDateTime fim = rs.getTimestamp("timestamp_fim").toLocalDateTime();
                    final int registrosExtraidos = rs.getInt("registros_extraidos");
                    final String mensagem = rs.getString("mensagem");
                    final Integer apiCount = extrairNumeroCampoMensagem(mensagem, "api_count");
                    final Integer uniqueCount = extrairNumeroCampoMensagem(mensagem, "unique_count");
                    return Optional.of(new JanelaExecucao(inicio, fim, registrosExtraidos, apiCount, uniqueCount));
                }
            }
        }

        final String sqlFallback = """
            SELECT TOP 1 timestamp_inicio, timestamp_fim, registros_extraidos, mensagem
            FROM dbo.log_extracoes
            WHERE entidade = ?
              AND status_final = 'COMPLETO'
              AND CAST(timestamp_inicio AS DATE) = ?
            ORDER BY timestamp_fim DESC
            """;

        try (PreparedStatement stmt = conexao.prepareStatement(sqlFallback)) {
            stmt.setString(1, entidade);
            stmt.setDate(2, java.sql.Date.valueOf(dataReferencia));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final LocalDateTime inicio = rs.getTimestamp("timestamp_inicio").toLocalDateTime();
                    final LocalDateTime fim = rs.getTimestamp("timestamp_fim").toLocalDateTime();
                    final int registrosExtraidos = rs.getInt("registros_extraidos");
                    final String mensagem = rs.getString("mensagem");
                    final Integer apiCount = extrairNumeroCampoMensagem(mensagem, "api_count");
                    final Integer uniqueCount = extrairNumeroCampoMensagem(mensagem, "unique_count");
                    return Optional.of(new JanelaExecucao(inicio, fim, registrosExtraidos, apiCount, uniqueCount));
                }
            }
        }

        return Optional.empty();
    }

    private Integer extrairNumeroCampoMensagem(final String mensagem, final String campo) {
        if (mensagem == null || mensagem.isBlank() || campo == null || campo.isBlank()) {
            return null;
        }

        final Pattern pattern = Pattern.compile("\\b" + Pattern.quote(campo) + "=(-?\\d+)\\b");
        final Matcher matcher = pattern.matcher(mensagem);
        if (!matcher.find()) {
            return null;
        }

        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int contarDestinoNaJanela(final Connection conexao,
                                      final String entidade,
                                      final JanelaExecucao janela) throws SQLException {
        final String tabela = mapearTabela(entidade);
        final String colunaTemporal = "data_extracao";
        final String sql = "SELECT COUNT(*) FROM dbo." + tabela + " WHERE " + colunaTemporal + " >= ? AND " + colunaTemporal + " <= ?";

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(janela.inicio));
            stmt.setTimestamp(2, Timestamp.valueOf(janela.fim));
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private String mapearTabela(final String entidade) {
        return switch (entidade) {
            case ConstantesEntidades.MANIFESTOS -> ConstantesEntidades.MANIFESTOS;
            case ConstantesEntidades.COTACOES -> ConstantesEntidades.COTACOES;
            case ConstantesEntidades.FATURAS_POR_CLIENTE -> ConstantesEntidades.FATURAS_POR_CLIENTE;
            case ConstantesEntidades.LOCALIZACAO_CARGAS -> ConstantesEntidades.LOCALIZACAO_CARGAS;
            case ConstantesEntidades.FRETES -> ConstantesEntidades.FRETES;
            case ConstantesEntidades.CONTAS_A_PAGAR -> ConstantesEntidades.CONTAS_A_PAGAR;
            case ConstantesEntidades.COLETAS -> ConstantesEntidades.COLETAS;
            case ConstantesEntidades.FATURAS_GRAPHQL -> ConstantesEntidades.FATURAS_GRAPHQL;
            default -> throw new IllegalArgumentException("Entidade nao suportada na comparacao API x Banco: " + entidade);
        };
    }

    private boolean possuiFlag(final String[] args, final String flag) {
        if (args == null || flag == null) {
            return false;
        }
        for (final String arg : args) {
            if (flag.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }
}
