/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/validacao/ValidacaoApiBanco24hDetalhadaRepository.java
Classe  : ValidacaoApiBanco24hDetalhadaRepository (final class)
Pacote  : br.com.extrator.aplicacao.validacao
Modulo  : Use Case - Validacao

Papel   : Acesso a dados do banco para validacao API 24h (resolucao de janela COMPLETA, carregamento de chaves/hashes, faturas orfaas).

Conecta com:
- ValidacaoApiBanco24hDetalhadaMetadataHasher (para hash de metadados)
- ConstantesEntidades (suporte.validacao)
- LoggerConsole (suporte.console)

Fluxo geral:
1) resolverDataReferenciaLogs(): busca log_extracoes COMPLETO 24h, com fallback para dia anterior ou sem filtro periodo.
2) carregarChavesBancoNaJanela(): SELECT chaves por entidade na janela de execucao.
3) carregarHashesMetadataBancoNaJanela(): SELECT chaves + metadata, hasheia cada um.
4) listarAccountingCreditIdsFretes(): busca IDs de fretes com accounting_credit_id (para faturas orfaas).

Estrutura interna:
Atributos-chave:
- log: LoggerConsole (para warning em fallbacks).
- metadataHasher: ValidacaoApiBanco24hDetalhadaMetadataHasher (para hashing).
Metodos principais:
- resolverDataReferenciaLogs(): resolucao inteligente com fallbacks.
- buscarUltimaJanelaCompletaDoDia(): busca janela na log_extracoes com filtro de periodo ou fallback.
- carregarChavesBancoNaJanela(), carregarHashesMetadataBancoNaJanela(): switch de entidade para queries.
- listarAccountingCreditIdsFretes(): busca IDs de fretes para correlacao com faturas.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.validacao;

import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.JanelaExecucao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import br.com.extrator.suporte.mapeamento.MapperUtil;
import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

final class ValidacaoApiBanco24hDetalhadaRepository {
    private final LoggerConsole log;
    private final ValidacaoApiBanco24hDetalhadaMetadataHasher metadataHasher;

    ValidacaoApiBanco24hDetalhadaRepository(
        final LoggerConsole log,
        final ValidacaoApiBanco24hDetalhadaMetadataHasher metadataHasher
    ) {
        this.log = log;
        this.metadataHasher = metadataHasher;
    }

    LocalDate resolverDataReferenciaLogs(final Connection conexao, final LocalDate dataPreferida) throws SQLException {
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

    List<Long> listarAccountingCreditIdsFretes(
        final Connection conexao,
        final JanelaExecucao janela,
        final int limite
    ) throws SQLException {
        final String sql = """
            SELECT DISTINCT TOP (?) CAST(f.accounting_credit_id AS BIGINT) AS accounting_credit_id
            FROM dbo.fretes f
            WHERE f.accounting_credit_id IS NOT NULL
              AND f.data_extracao >= ?
              AND f.data_extracao <= ?
            ORDER BY CAST(f.accounting_credit_id AS BIGINT)
            """;

        final java.util.ArrayList<Long> ids = new java.util.ArrayList<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setInt(1, limite);
            stmt.setTimestamp(2, Timestamp.valueOf(janela.inicio()));
            stmt.setTimestamp(3, Timestamp.valueOf(janela.fim()));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final long id = rs.getLong("accounting_credit_id");
                    if (!rs.wasNull()) {
                        ids.add(id);
                    }
                }
            }
        }
        return ids;
    }

    Optional<JanelaExecucao> buscarUltimaJanelaCompletaDoDia(
        final Connection conexao,
        final String entidade,
        final LocalDate dataReferencia,
        final LocalDate periodoInicio,
        final LocalDate periodoFim,
        final boolean permitirFallbackJanela
    ) throws SQLException {
        final String sqlComPeriodo = """
            SELECT TOP 1 timestamp_inicio, timestamp_fim
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
                    final java.time.LocalDateTime inicio = rs.getTimestamp("timestamp_inicio").toLocalDateTime();
                    final java.time.LocalDateTime fim = rs.getTimestamp("timestamp_fim").toLocalDateTime();
                    return Optional.of(new JanelaExecucao(inicio, fim, true));
                }
            }
        }

        if (!permitirFallbackJanela) {
            return Optional.empty();
        }

        final String sqlFallback = """
            SELECT TOP 1 timestamp_inicio, timestamp_fim
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
                    final java.time.LocalDateTime inicio = rs.getTimestamp("timestamp_inicio").toLocalDateTime();
                    final java.time.LocalDateTime fim = rs.getTimestamp("timestamp_fim").toLocalDateTime();
                    return Optional.of(new JanelaExecucao(inicio, fim, false));
                }
            }
        }

        return Optional.empty();
    }

    Set<String> carregarChavesBancoNaJanela(
        final Connection conexao,
        final String entidade,
        final JanelaExecucao janela
    ) throws SQLException {
        final String sql = switch (entidade) {
            case ConstantesEntidades.MANIFESTOS ->
                """
                SELECT CONCAT(
                    CAST(sequence_code AS VARCHAR(50)),
                    '|',
                    COALESCE(CAST(pick_sequence_code AS VARCHAR(50)), '-1'),
                    '|',
                    COALESCE(CAST(mdfe_number AS VARCHAR(50)), '-1')
                ) AS chave
                FROM dbo.manifestos
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_code IS NOT NULL
                """;
            case ConstantesEntidades.COTACOES ->
                """
                SELECT CAST(sequence_code AS VARCHAR(50)) AS chave
                FROM dbo.cotacoes
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_code IS NOT NULL
                """;
            case ConstantesEntidades.LOCALIZACAO_CARGAS ->
                """
                SELECT CAST(sequence_number AS VARCHAR(50)) AS chave
                FROM dbo.localizacao_cargas
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_number IS NOT NULL
                """;
            case ConstantesEntidades.CONTAS_A_PAGAR ->
                """
                SELECT CAST(sequence_code AS VARCHAR(50)) AS chave
                FROM dbo.contas_a_pagar
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_code IS NOT NULL
                """;
            case ConstantesEntidades.FATURAS_POR_CLIENTE ->
                """
                SELECT unique_id AS chave
                FROM dbo.faturas_por_cliente
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND unique_id IS NOT NULL
                """;
            case ConstantesEntidades.FRETES ->
                """
                SELECT CAST(id AS VARCHAR(50)) AS chave
                FROM dbo.fretes
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND id IS NOT NULL
                """;
            case ConstantesEntidades.COLETAS ->
                """
                SELECT id AS chave
                FROM dbo.coletas
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND id IS NOT NULL
                """;
            case ConstantesEntidades.FATURAS_GRAPHQL ->
                """
                SELECT CAST(id AS VARCHAR(50)) AS chave
                FROM dbo.faturas_graphql
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND id IS NOT NULL
                """;
            case ConstantesEntidades.USUARIOS_SISTEMA ->
                """
                SELECT CAST(user_id AS VARCHAR(50)) AS chave
                FROM dbo.dim_usuarios
                WHERE data_atualizacao >= ? AND data_atualizacao <= ?
                  AND user_id IS NOT NULL
                """;
            default -> throw new IllegalArgumentException("Entidade nao suportada na comparacao detalhada: " + entidade);
        };

        final Set<String> chaves = new HashSet<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(janela.inicio()));
            stmt.setTimestamp(2, Timestamp.valueOf(janela.fim()));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final String chave = rs.getString("chave");
                    if (chave != null && !chave.isBlank()) {
                        chaves.add(chave.trim());
                    }
                }
            }
        }
        return chaves;
    }

    Map<String, String> carregarHashesMetadataBancoNaJanela(
        final Connection conexao,
        final String entidade,
        final JanelaExecucao janela
    ) throws SQLException {
        final String sql = switch (entidade) {
            case ConstantesEntidades.MANIFESTOS ->
                """
                SELECT CONCAT(
                    CAST(sequence_code AS VARCHAR(50)),
                    '|',
                    COALESCE(CAST(pick_sequence_code AS VARCHAR(50)), '-1'),
                    '|',
                    COALESCE(CAST(mdfe_number AS VARCHAR(50)), '-1')
                ) AS chave,
                metadata
                FROM dbo.manifestos
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_code IS NOT NULL
                """;
            case ConstantesEntidades.COTACOES ->
                """
                SELECT CAST(sequence_code AS VARCHAR(50)) AS chave, metadata
                FROM dbo.cotacoes
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_code IS NOT NULL
                """;
            case ConstantesEntidades.LOCALIZACAO_CARGAS ->
                """
                SELECT CAST(sequence_number AS VARCHAR(50)) AS chave, metadata
                FROM dbo.localizacao_cargas
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_number IS NOT NULL
                """;
            case ConstantesEntidades.CONTAS_A_PAGAR ->
                """
                SELECT CAST(sequence_code AS VARCHAR(50)) AS chave, metadata
                FROM dbo.contas_a_pagar
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_code IS NOT NULL
                """;
            case ConstantesEntidades.FATURAS_POR_CLIENTE ->
                """
                SELECT unique_id AS chave, metadata
                FROM dbo.faturas_por_cliente
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND unique_id IS NOT NULL
                """;
            case ConstantesEntidades.FRETES ->
                """
                SELECT CAST(id AS VARCHAR(50)) AS chave, metadata
                FROM dbo.fretes
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND id IS NOT NULL
                """;
            case ConstantesEntidades.COLETAS ->
                """
                SELECT id AS chave, metadata
                FROM dbo.coletas
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND id IS NOT NULL
                """;
            case ConstantesEntidades.FATURAS_GRAPHQL ->
                """
                SELECT CAST(id AS VARCHAR(50)) AS chave, metadata
                FROM dbo.faturas_graphql
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND id IS NOT NULL
                """;
            case ConstantesEntidades.USUARIOS_SISTEMA ->
                """
                SELECT CAST(user_id AS VARCHAR(50)) AS chave, nome, data_atualizacao
                FROM dbo.dim_usuarios
                WHERE data_atualizacao >= ? AND data_atualizacao <= ?
                  AND user_id IS NOT NULL
                """;
            default -> throw new IllegalArgumentException("Entidade nao suportada na comparacao detalhada: " + entidade);
        };

        final Map<String, String> hashesPorChave = new LinkedHashMap<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(janela.inicio()));
            stmt.setTimestamp(2, Timestamp.valueOf(janela.fim()));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final String chave = rs.getString("chave");
                    if (chave == null || chave.isBlank()) {
                        continue;
                    }
                    final String metadata;
                    if (ConstantesEntidades.USUARIOS_SISTEMA.equals(entidade)) {
                        final Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("id", rs.getLong("chave"));
                        payload.put("name", rs.getString("nome"));
                        metadata = MapperUtil.toJson(payload);
                    } else {
                        metadata = rs.getString("metadata");
                    }
                    hashesPorChave.put(
                        chave.trim(),
                        metadataHasher.hashMetadata(entidade, metadata)
                    );
                }
            }
        }
        return hashesPorChave;
    }

    Map<String, String> carregarMetadataBrutaBancoNaJanela(
        final Connection conexao,
        final String entidade,
        final JanelaExecucao janela
    ) throws SQLException {
        final String sql = switch (entidade) {
            case ConstantesEntidades.MANIFESTOS ->
                """
                SELECT CONCAT(
                    CAST(sequence_code AS VARCHAR(50)),
                    '|',
                    COALESCE(CAST(pick_sequence_code AS VARCHAR(50)), '-1'),
                    '|',
                    COALESCE(CAST(mdfe_number AS VARCHAR(50)), '-1')
                ) AS chave,
                metadata
                FROM dbo.manifestos
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_code IS NOT NULL
                """;
            case ConstantesEntidades.COTACOES ->
                """
                SELECT CAST(sequence_code AS VARCHAR(50)) AS chave, metadata
                FROM dbo.cotacoes
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_code IS NOT NULL
                """;
            case ConstantesEntidades.LOCALIZACAO_CARGAS ->
                """
                SELECT CAST(sequence_number AS VARCHAR(50)) AS chave, metadata
                FROM dbo.localizacao_cargas
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_number IS NOT NULL
                """;
            case ConstantesEntidades.CONTAS_A_PAGAR ->
                """
                SELECT CAST(sequence_code AS VARCHAR(50)) AS chave, metadata
                FROM dbo.contas_a_pagar
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_code IS NOT NULL
                """;
            case ConstantesEntidades.FATURAS_POR_CLIENTE ->
                """
                SELECT unique_id AS chave, metadata
                FROM dbo.faturas_por_cliente
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND unique_id IS NOT NULL
                """;
            case ConstantesEntidades.FRETES ->
                """
                SELECT CAST(id AS VARCHAR(50)) AS chave, metadata
                FROM dbo.fretes
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND id IS NOT NULL
                """;
            case ConstantesEntidades.COLETAS ->
                """
                SELECT id AS chave, metadata
                FROM dbo.coletas
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND id IS NOT NULL
                """;
            case ConstantesEntidades.FATURAS_GRAPHQL ->
                """
                SELECT CAST(id AS VARCHAR(50)) AS chave, metadata
                FROM dbo.faturas_graphql
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND id IS NOT NULL
                """;
            case ConstantesEntidades.USUARIOS_SISTEMA ->
                """
                SELECT CAST(user_id AS VARCHAR(50)) AS chave, nome, data_atualizacao
                FROM dbo.dim_usuarios
                WHERE data_atualizacao >= ? AND data_atualizacao <= ?
                  AND user_id IS NOT NULL
                """;
            default -> throw new IllegalArgumentException("Entidade nao suportada na comparacao detalhada: " + entidade);
        };

        final Map<String, String> metadataPorChave = new LinkedHashMap<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(janela.inicio()));
            stmt.setTimestamp(2, Timestamp.valueOf(janela.fim()));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final String chave = rs.getString("chave");
                    if (chave == null || chave.isBlank()) {
                        continue;
                    }
                    final String metadata;
                    if (ConstantesEntidades.USUARIOS_SISTEMA.equals(entidade)) {
                        final Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("id", rs.getLong("chave"));
                        payload.put("name", rs.getString("nome"));
                        metadata = MapperUtil.toJson(payload);
                    } else {
                        metadata = rs.getString("metadata");
                    }
                    metadataPorChave.put(chave.trim(), metadata);
                }
            }
        }
        return metadataPorChave;
    }

    private boolean existeLogCompleto24hNaData(final Connection conexao, final LocalDate data) throws SQLException {
        final LocalDate dataInicio = data.minusDays(1);
        final String sql = """
            SELECT TOP 1 1
            FROM dbo.log_extracoes
            WHERE status_final = 'COMPLETO'
              AND CAST(timestamp_inicio AS DATE) = ?
              AND (mensagem LIKE ? OR mensagem LIKE ?)
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setDate(1, java.sql.Date.valueOf(data));
            stmt.setString(2, "%" + dataInicio + " a " + data + "%");
            stmt.setString(3, "%" + "Data: " + dataInicio + "%");
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
}
