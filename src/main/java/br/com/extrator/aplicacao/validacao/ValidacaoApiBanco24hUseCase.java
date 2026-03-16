/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/validacao/ValidacaoApiBanco24hUseCase.java
Classe  : ValidacaoApiBanco24hUseCase (public class)
Pacote  : br.com.extrator.aplicacao.validacao
Modulo  : Use Case - Validacao

Papel   : Validacao simplificada API vs Banco 24h (consulta totais da API em tempo real, compara com log de extracao + contagem destino).

Conecta com:
- CompletudeValidator (observabilidade.servicos) - para buscarTotaisEslCloud
- GerenciadorConexao (suporte.banco)
- LoggerConsole (suporte.console)
- ConstantesEntidades (suporte.validacao)

Fluxo geral:
1) executar(ValidacaoApiBanco24hRequest) resolve data referencia (fallback inteligente com log_extracoes).
2) Consulta totais API em tempo real via CompletudeValidator.
3) Para cada entidade: busca ultima janela COMPLETA do dia, extrai counts de mensagem ou tabela.
4) Compara: banco vs esperado, registra OK/FALHA/BACKFILL com logging.

Estrutura interna:
Atributos-chave:
- log: LoggerConsole statico.
- ENTIDADES_ORDEM: List de 8 entidades (ordem fixa).
Inner record JanelaExecucao: inicio, fim, registrosExtraidos, apiCount, uniqueCount, dbUpserts.
Metodos principais:
- executar(ValidacaoApiBanco24hRequest): orquestra validacao com resolve de data.
- resolverDataReferenciaLogs(): fallback inteligente (24h -> dia anterior -> sem filtro -> ultima data).
- buscarUltimaJanelaCompletaDoDia(): busca log com periodo ou fallback.
- extrairNumeroCampoMensagem(String, String): regex extrator de campos (api_count, unique_count, db_upserts).
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.validacao;

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

import br.com.extrator.observabilidade.servicos.CompletudeValidator;
import br.com.extrator.suporte.banco.GerenciadorConexao;
import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public class ValidacaoApiBanco24hUseCase {
    private static final LoggerConsole log = LoggerConsole.getLogger(ValidacaoApiBanco24hUseCase.class);
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

    private record JanelaExecucao(
        LocalDateTime inicio,
        LocalDateTime fim,
        int registrosExtraidos,
        Integer apiCount,
        Integer uniqueCount,
        Integer dbUpserts,
        Integer dbPersisted
    ) {
    }

    public void executar(final ValidacaoApiBanco24hRequest request) throws Exception {
        final LocalDate dataReferencia;
        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            dataReferencia = resolverDataReferenciaLogs(conexao, request.dataReferenciaSistema());
        }

        final LocalDate dataInicio = dataReferencia.minusDays(1);
        final LocalDate dataFim = dataReferencia;

        log.console("\n" + "=".repeat(72));
        log.info("VALIDACAO RAPIDA | JANELA OPERACIONAL RECENTE | LOG_EXTRACOES x BANCO + TELEMETRIA API ATUAL");
        log.info("Data de referencia: {}", dataReferencia);
        log.info("Janela principal considerada: {} a {} (D-1..D)", dataInicio, dataFim);
        log.warn(
            "Modo rapido: o criterio de aprovacao usa a ultima janela COMPLETA registrada em log_extracoes; api_bruto_atual entra apenas como telemetria operacional e nao representa necessariamente 24h corridas."
        );
        log.info(
            "Faturas GraphQL: {}",
            request.incluirFaturasGraphQL() ? "INCLUIDO" : "DESABILITADO (--sem-faturas-graphql)"
        );
        log.info(
            "Fallback de janela sem periodo: {}",
            request.permitirFallbackJanela() ? "ATIVADO" : "DESATIVADO"
        );
        log.console("=".repeat(72));

        final CompletudeValidator validator = new CompletudeValidator();
        final Optional<Map<String, Integer>> totaisApiOpt = validator.buscarTotaisEslCloudJanelaPrincipal(
            dataReferencia,
            request.incluirFaturasGraphQL()
        );
        if (totaisApiOpt.isEmpty()) {
            throw new RuntimeException("Nao foi possivel obter totais da API em tempo real.");
        }

        final Map<String, Integer> totaisApi = new LinkedHashMap<>(totaisApiOpt.get());
        if (!request.incluirFaturasGraphQL()) {
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
                final Optional<JanelaExecucao> janelaOpt = buscarUltimaJanelaCompletaDoDia(
                    conexao,
                    entidade,
                    dataReferencia,
                    dataInicio,
                    dataFim,
                    request.permitirFallbackJanela()
                );
                if (janelaOpt.isEmpty()) {
                    totalFalhas++;
                    log.warn(
                        "API_VS_BANCO | entidade={} | status=INCONCLUSIVO | detalhe=Sem janela COMPLETA compativel para comparacao{}.",
                        entidade,
                        request.permitirFallbackJanela()
                            ? " mesmo com fallback"
                            : " (use --permitir-fallback-janela para liberar fallback aberto)"
                    );
                    continue;
                }

                final JanelaExecucao janela = janelaOpt.get();
                final int bancoCount = contarDestinoNaJanela(conexao, entidade, janela);
                final int esperadoUnico = janela.uniqueCount != null ? janela.uniqueCount : janela.registrosExtraidos;
                final int esperadoPersistido = janela.dbPersisted != null
                    ? janela.dbPersisted
                    : janela.dbUpserts != null ? janela.dbUpserts : esperadoUnico;
                final int apiCountLog = janela.apiCount != null ? janela.apiCount : esperadoUnico;
                final int diff = bancoCount - esperadoPersistido;
                final int deltaApiBrutoAtual = apiCount - esperadoUnico;
                final boolean tolerarBackfillFaturas =
                    ConstantesEntidades.FATURAS_GRAPHQL.equals(entidade) && bancoCount >= esperadoPersistido;

                if (diff == 0 || tolerarBackfillFaturas) {
                    totalOk++;
                    registrarSucesso(
                        entidade,
                        apiCount,
                        apiCountLog,
                        esperadoUnico,
                        esperadoPersistido,
                        bancoCount,
                        diff,
                        deltaApiBrutoAtual,
                        janela,
                        tolerarBackfillFaturas
                    );
                    continue;
                }

                totalFalhas++;
                log.error(
                    "API_VS_BANCO | entidade={} | status=FALHA | api_bruto_atual={} | api_bruto_log={} | api_unico_log={} | esperado_persistido={} | banco={} | diff={} | janela=[{} .. {}]",
                    entidade,
                    apiCount,
                    apiCountLog,
                    esperadoUnico,
                    esperadoPersistido,
                    bancoCount,
                    diff,
                    janela.inicio,
                    janela.fim
                );
            }
        }

        log.console("=".repeat(72));
        log.info("RESUMO_API_VS_BANCO | ok={} | falhas={}", totalOk, totalFalhas);
        log.console("=".repeat(72));

        if (totalFalhas > 0) {
            throw new RuntimeException(
                "Comparacao API x Banco reprovada: " + totalFalhas + " entidade(s) com divergencia."
            );
        }
    }

    private void registrarSucesso(final String entidade,
                                  final int apiCount,
                                  final int apiCountLog,
                                  final int esperadoUnico,
                                  final int esperadoPersistido,
                                  final int bancoCount,
                                  final int diff,
                                  final int deltaApiBrutoAtual,
                                  final JanelaExecucao janela,
                                  final boolean tolerarBackfillFaturas) {
        if (tolerarBackfillFaturas && diff != 0) {
            log.warn(
                "API_VS_BANCO | entidade={} | status=OK_BACKFILL | api_bruto_atual={} | api_bruto_log={} | api_unico_log={} | esperado_persistido={} | banco={} | diff={} | janela=[{} .. {}]",
                entidade,
                apiCount,
                apiCountLog,
                esperadoUnico,
                esperadoPersistido,
                bancoCount,
                diff,
                janela.inicio,
                janela.fim
            );
            return;
        }

        if (deltaApiBrutoAtual > 0) {
            log.info(
                "API_VS_BANCO | entidade={} | status=OK_API_DUPLICADOS | api_bruto_atual={} | api_bruto_log={} | api_unico_log={} | esperado_persistido={} | banco={} | diff={} | duplicados_brutos={} | janela=[{} .. {}]",
                entidade,
                apiCount,
                apiCountLog,
                esperadoUnico,
                esperadoPersistido,
                bancoCount,
                diff,
                deltaApiBrutoAtual,
                janela.inicio,
                janela.fim
            );
            return;
        }

        log.info(
            "API_VS_BANCO | entidade={} | status=OK | api_bruto_atual={} | api_bruto_log={} | api_unico_log={} | esperado_persistido={} | banco={} | diff={} | janela=[{} .. {}]",
            entidade,
            apiCount,
            apiCountLog,
            esperadoUnico,
            esperadoPersistido,
            bancoCount,
            diff,
            janela.inicio,
            janela.fim
        );
    }

    private LocalDate resolverDataReferenciaLogs(final Connection conexao, final LocalDate dataPreferida) throws SQLException {
        if (existeLogCompleto24hNaData(conexao, dataPreferida)) {
            return dataPreferida;
        }

        final LocalDate diaAnterior = dataPreferida.minusDays(1);
        if (existeLogCompleto24hNaData(conexao, diaAnterior)) {
            log.warn(
                "Sem log COMPLETO da janela operacional recente para {}. Usando dia anterior {} como referencia.",
                dataPreferida,
                diaAnterior
            );
            return diaAnterior;
        }

        if (existeLogCompletoNaData(conexao, dataPreferida)) {
            log.warn(
                "Sem log COMPLETO da janela operacional recente para {}. Usando logs COMPLETO do proprio dia (sem filtro de periodo).",
                dataPreferida
            );
            return dataPreferida;
        }

        if (existeLogCompletoNaData(conexao, diaAnterior)) {
            log.warn(
                "Sem log COMPLETO da janela operacional recente para {}. Usando logs COMPLETO do dia anterior {} (sem filtro de periodo).",
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
                                                                     final LocalDate periodoFim,
                                                                     final boolean permitirFallbackJanela) throws SQLException {
        final String sqlComPeriodo = """
            SELECT TOP 1 timestamp_inicio, timestamp_fim, registros_extraidos, mensagem
            FROM dbo.log_extracoes
            WHERE entidade = ?
              AND status_final = 'COMPLETO'
              AND CAST(timestamp_inicio AS DATE) = ?
              AND (mensagem LIKE ? OR mensagem LIKE ?)
            ORDER BY timestamp_fim DESC
            """;

        try (PreparedStatement stmt = conexao.prepareStatement(sqlComPeriodo)) {
            stmt.setString(1, entidade);
            stmt.setDate(2, java.sql.Date.valueOf(dataReferencia));
            stmt.setString(3, "%" + periodoInicio + " a " + periodoFim + "%");
            stmt.setString(4, "%" + "Data: " + periodoInicio + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(criarJanelaExecucao(rs));
                }
            }
        }

        if (!permitirFallbackJanela) {
            return Optional.empty();
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
                    return Optional.of(criarJanelaExecucao(rs));
                }
            }
        }

        return Optional.empty();
    }

    private JanelaExecucao criarJanelaExecucao(final ResultSet rs) throws SQLException {
        final LocalDateTime inicio = rs.getTimestamp("timestamp_inicio").toLocalDateTime();
        final LocalDateTime fim = rs.getTimestamp("timestamp_fim").toLocalDateTime();
        final int registrosExtraidos = rs.getInt("registros_extraidos");
        final String mensagem = rs.getString("mensagem");
        final Integer apiCount = extrairNumeroCampoMensagem(mensagem, "api_count");
        final Integer uniqueCount = extrairNumeroCampoMensagem(mensagem, "unique_count");
        final Integer dbUpserts = extrairNumeroCampoMensagem(mensagem, "db_upserts");
        final Integer dbPersisted = extrairNumeroCampoMensagem(mensagem, "db_persisted");
        return new JanelaExecucao(inicio, fim, registrosExtraidos, apiCount, uniqueCount, dbUpserts, dbPersisted);
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
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private int contarDestinoNaJanela(final Connection conexao,
                                      final String entidade,
                                      final JanelaExecucao janela) throws SQLException {
        final String tabela = mapearTabela(entidade);
        final String sql = "SELECT COUNT(*) FROM dbo." + tabela + " WHERE data_extracao >= ? AND data_extracao <= ?";

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
}
