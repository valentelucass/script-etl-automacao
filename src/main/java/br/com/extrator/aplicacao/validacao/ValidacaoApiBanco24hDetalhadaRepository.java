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
import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.PeriodoConsulta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.extrator.suporte.mapeamento.MapperUtil;
import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.tempo.RelogioSistema;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

class ValidacaoApiBanco24hDetalhadaRepository {
    private static final String METADATA_PICK_SEQUENCE_CODE = "mft_pfs_pck_sequence_code";
    private static final String METADATA_MDFE_NUMBER = "mft_mfs_number";
    private final LoggerConsole log;
    private final ValidacaoApiBanco24hDetalhadaMetadataHasher metadataHasher;
    private final JanelaAbertaCeilingProvider janelaAbertaCeilingProvider;

    ValidacaoApiBanco24hDetalhadaRepository(
        final LoggerConsole log,
        final ValidacaoApiBanco24hDetalhadaMetadataHasher metadataHasher
    ) {
        this(
            log,
            metadataHasher,
            criarAnchorValidacaoAtual()
        );
    }

    ValidacaoApiBanco24hDetalhadaRepository(
        final LoggerConsole log,
        final ValidacaoApiBanco24hDetalhadaMetadataHasher metadataHasher,
        final JanelaAbertaCeilingProvider janelaAbertaCeilingProvider
    ) {
        this.log = log;
        this.metadataHasher = metadataHasher;
        this.janelaAbertaCeilingProvider = janelaAbertaCeilingProvider;
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
        final Optional<LocalDateTime> tetoJanelaAberta =
            resolverTetoValidacaoAberta(dataReferencia, periodoInicio, periodoFim);

        final Optional<JanelaExecucao> janelaComPeriodo = buscarJanelaPorPeriodo(
            conexao,
            entidade,
            dataReferencia,
            periodoInicio,
            periodoFim,
            tetoJanelaAberta
        );
        if (janelaComPeriodo.isPresent()) {
            return janelaComPeriodo;
        }

        if (!permitirFallbackJanela) {
            return Optional.empty();
        }

        return buscarJanelaFallback(conexao, entidade, dataReferencia, tetoJanelaAberta);
    }

    Optional<String> resolverExecutionUuidAncora(final Connection conexao,
                                                 final Set<String> entidades,
                                                 final LocalDate periodoInicio,
                                                 final LocalDate periodoFim,
                                                 final LocalDateTime validacaoIniciadaEm) throws SQLException {
        if (conexao == null
            || entidades == null
            || entidades.isEmpty()
            || periodoInicio == null
            || periodoFim == null
            || validacaoIniciadaEm == null) {
            return Optional.empty();
        }
        if (!tabelaExiste(conexao, "sys_execution_audit")) {
            return Optional.empty();
        }

        final LocalDateTime janelaConsultaInicio = periodoInicio.atStartOfDay();
        final LocalDateTime janelaConsultaFim = periodoFim.plusDays(1).atStartOfDay();
        final String placeholders = String.join(",", Collections.nCopies(entidades.size(), "?"));
        final String sql = """
            WITH candidatos AS (
                SELECT
                    execution_uuid,
                    COUNT(DISTINCT entidade) AS entidades_cobertas,
                    MAX(finished_at) AS ultimo_fim
                FROM dbo.sys_execution_audit
                WHERE finished_at IS NOT NULL
                  AND finished_at <= ?
                  AND status_execucao IN ('COMPLETO', 'RECONCILIADO', 'RECONCILED')
                  AND api_completa = 1
                  AND command_name IN ('--fluxo-completo', '--extracao-intervalo', '--executar-step-isolado', '--loop-daemon-run', '--recovery')
                  AND janela_consulta_inicio = ?
                  AND janela_consulta_fim = ?
                  AND entidade IN (%s)
                GROUP BY execution_uuid
            )
            SELECT TOP 1 execution_uuid
            FROM candidatos
            ORDER BY entidades_cobertas DESC, ultimo_fim DESC
            """.formatted(placeholders);

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            int parametro = 1;
            stmt.setTimestamp(parametro++, Timestamp.valueOf(validacaoIniciadaEm));
            stmt.setTimestamp(parametro++, Timestamp.valueOf(janelaConsultaInicio));
            stmt.setTimestamp(parametro++, Timestamp.valueOf(janelaConsultaFim));
            for (final String entidade : entidades) {
                stmt.setString(parametro++, entidade);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final String executionUuid = rs.getString("execution_uuid");
                    return executionUuid == null || executionUuid.isBlank()
                        ? Optional.empty()
                        : Optional.of(executionUuid.trim());
                }
            }
        }

        return Optional.empty();
    }

    Optional<String> resolverExecutionUuidAncoraRecente(final Connection conexao,
                                                        final Set<String> entidades,
                                                        final LocalDateTime validacaoIniciadaEm) throws SQLException {
        if (conexao == null
            || entidades == null
            || entidades.isEmpty()
            || validacaoIniciadaEm == null
            || !tabelaExiste(conexao, "sys_execution_audit")) {
            return Optional.empty();
        }

        final String placeholders = String.join(",", Collections.nCopies(entidades.size(), "?"));
        final String sql = """
            WITH candidatos AS (
                SELECT
                    execution_uuid,
                    COUNT(DISTINCT entidade) AS entidades_cobertas,
                    MAX(finished_at) AS ultimo_fim
                FROM dbo.sys_execution_audit
                WHERE finished_at IS NOT NULL
                  AND finished_at <= ?
                  AND status_execucao IN ('COMPLETO', 'RECONCILIADO', 'RECONCILED')
                  AND api_completa = 1
                  AND command_name IN ('--fluxo-completo', '--extracao-intervalo', '--executar-step-isolado', '--loop-daemon-run', '--recovery')
                  AND entidade IN (%s)
                GROUP BY execution_uuid
            )
            SELECT TOP 1 execution_uuid
            FROM candidatos
            ORDER BY entidades_cobertas DESC, ultimo_fim DESC
            """.formatted(placeholders);

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            int parametro = 1;
            stmt.setTimestamp(parametro++, Timestamp.valueOf(validacaoIniciadaEm));
            for (final String entidade : entidades) {
                stmt.setString(parametro++, entidade);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final String executionUuid = rs.getString("execution_uuid");
                    return executionUuid == null || executionUuid.isBlank()
                        ? Optional.empty()
                        : Optional.of(executionUuid.trim());
                }
            }
        }

        return Optional.empty();
    }

    Optional<JanelaExecucao> buscarJanelaEstruturadaDaExecucao(final Connection conexao,
                                                               final String executionUuid,
                                                               final String entidade) throws SQLException {
        if (conexao == null
            || executionUuid == null
            || executionUuid.isBlank()
            || entidade == null
            || entidade.isBlank()
            || !tabelaExiste(conexao, "sys_execution_audit")) {
            return Optional.empty();
        }

        final String sql = """
            SELECT TOP 1 started_at, finished_at
            FROM dbo.sys_execution_audit
            WHERE execution_uuid = ?
              AND entidade = ?
              AND status_execucao IN ('COMPLETO', 'RECONCILIADO', 'RECONCILED')
              AND api_completa = 1
              AND started_at IS NOT NULL
              AND finished_at IS NOT NULL
            ORDER BY finished_at DESC
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, executionUuid);
            stmt.setString(2, entidade);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new JanelaExecucao(
                        rs.getTimestamp("started_at").toLocalDateTime(),
                        rs.getTimestamp("finished_at").toLocalDateTime(),
                        true
                    ));
                }
            }
        }

        return Optional.empty();
    }

    Optional<PeriodoConsulta> buscarPeriodoConsultaDaExecucao(final Connection conexao,
                                                              final String executionUuid,
                                                              final String entidade) throws SQLException {
        if (conexao == null
            || executionUuid == null
            || executionUuid.isBlank()
            || entidade == null
            || entidade.isBlank()
            || !tabelaExiste(conexao, "sys_execution_audit")) {
            return Optional.empty();
        }

        final String sql = """
            SELECT TOP 1 janela_consulta_inicio, janela_consulta_fim
            FROM dbo.sys_execution_audit
            WHERE execution_uuid = ?
              AND entidade = ?
              AND status_execucao IN ('COMPLETO', 'RECONCILIADO', 'RECONCILED')
              AND api_completa = 1
              AND janela_consulta_inicio IS NOT NULL
              AND janela_consulta_fim IS NOT NULL
            ORDER BY finished_at DESC
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, executionUuid);
            stmt.setString(2, entidade);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final LocalDateTime janelaInicio = rs.getTimestamp("janela_consulta_inicio").toLocalDateTime();
                    final LocalDateTime janelaFim = rs.getTimestamp("janela_consulta_fim").toLocalDateTime();
                    final LocalDate dataInicio = janelaInicio.toLocalDate();
                    final LocalDate dataFim = normalizarFimPeriodoConsulta(janelaInicio, janelaFim);
                    return Optional.of(new PeriodoConsulta(dataInicio, dataFim));
                }
            }
        }

        return Optional.empty();
    }

    private Optional<JanelaExecucao> buscarJanelaPorPeriodo(
        final Connection conexao,
        final String entidade,
        final LocalDate dataReferencia,
        final LocalDate periodoInicio,
        final LocalDate periodoFim,
        final Optional<LocalDateTime> tetoJanelaAberta
    ) throws SQLException {
        final String sqlComPeriodo = """
            SELECT TOP 1 timestamp_inicio, timestamp_fim
            FROM dbo.log_extracoes
            WHERE entidade = ?
              AND status_final = 'COMPLETO'
              AND CAST(timestamp_inicio AS DATE) = ?
              AND (mensagem LIKE ? OR mensagem LIKE ?)
            """
            + (tetoJanelaAberta.isPresent() ? "  AND timestamp_fim <= ?\n" : "")
            + "ORDER BY timestamp_fim DESC";

        try (PreparedStatement stmt = conexao.prepareStatement(sqlComPeriodo)) {
            int parametro = 1;
            stmt.setString(parametro++, entidade);
            stmt.setDate(parametro++, java.sql.Date.valueOf(dataReferencia));
            stmt.setString(parametro++, "%" + periodoInicio + " a " + periodoFim + "%");
            stmt.setString(parametro++, "%" + "Data: " + periodoInicio + "%");
            if (tetoJanelaAberta.isPresent()) {
                stmt.setTimestamp(parametro, Timestamp.valueOf(tetoJanelaAberta.get()));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final LocalDateTime inicio = rs.getTimestamp("timestamp_inicio").toLocalDateTime();
                    final LocalDateTime fim = rs.getTimestamp("timestamp_fim").toLocalDateTime();
                    return Optional.of(new JanelaExecucao(inicio, fim, true));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<JanelaExecucao> buscarJanelaFallback(
        final Connection conexao,
        final String entidade,
        final LocalDate dataReferencia,
        final Optional<LocalDateTime> tetoJanelaAberta
    ) throws SQLException {
        final String sqlFallback = """
            SELECT TOP 1 timestamp_inicio, timestamp_fim
            FROM dbo.log_extracoes
            WHERE entidade = ?
              AND status_final = 'COMPLETO'
              AND CAST(timestamp_inicio AS DATE) = ?
            """
            + (tetoJanelaAberta.isPresent() ? "  AND timestamp_fim <= ?\n" : "")
            + "ORDER BY timestamp_fim DESC";

        try (PreparedStatement stmt = conexao.prepareStatement(sqlFallback)) {
            int parametro = 1;
            stmt.setString(parametro++, entidade);
            stmt.setDate(parametro++, java.sql.Date.valueOf(dataReferencia));
            if (tetoJanelaAberta.isPresent()) {
                stmt.setTimestamp(parametro, Timestamp.valueOf(tetoJanelaAberta.get()));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final LocalDateTime inicio = rs.getTimestamp("timestamp_inicio").toLocalDateTime();
                    final LocalDateTime fim = rs.getTimestamp("timestamp_fim").toLocalDateTime();
                    return Optional.of(new JanelaExecucao(inicio, fim, false));
                }
            }
        }

        return Optional.empty();
    }

    private Optional<LocalDateTime> resolverTetoValidacaoAberta(
        final LocalDate dataReferencia,
        final LocalDate periodoInicio,
        final LocalDate periodoFim
    ) {
        final boolean janelaAbertaPrincipal =
            periodoInicio.equals(dataReferencia.minusDays(1)) && periodoFim.equals(dataReferencia);
        if (!janelaAbertaPrincipal) {
            return Optional.empty();
        }

        return janelaAbertaCeilingProvider.obter()
            .filter(instante -> !instante.toLocalDate().isBefore(dataReferencia));
    }

    private static JanelaAbertaCeilingProvider criarAnchorValidacaoAtual() {
        final LocalDateTime inicioValidacao = RelogioSistema.agora();
        return () -> Optional.of(inicioValidacao);
    }

    Set<String> carregarChavesBancoNaJanela(
        final Connection conexao,
        final String entidade,
        final JanelaExecucao janela
    ) throws SQLException {
        return carregarChavesBancoNaJanela(
            conexao,
            entidade,
            janela,
            janela.inicio().toLocalDate(),
            janela.fim().toLocalDate()
        );
    }

    Set<String> carregarChavesBancoNaJanela(
        final Connection conexao,
        final String entidade,
        final JanelaExecucao janela,
        final LocalDate periodoInicio,
        final LocalDate periodoFim
    ) throws SQLException {
        return carregarChavesBancoNaJanela(
            conexao,
            entidade,
            janela,
            periodoInicio,
            periodoFim,
            false
        );
    }

    Set<String> carregarChavesBancoNaJanela(
        final Connection conexao,
        final String entidade,
        final JanelaExecucao janela,
        final LocalDate periodoInicio,
        final LocalDate periodoFim,
        final boolean filtroEstritoDataExtracao
    ) throws SQLException {
        final String sql = switch (entidade) {
            case ConstantesEntidades.MANIFESTOS ->
                """
                SELECT sequence_code, pick_sequence_code, mdfe_number, metadata
                FROM dbo.manifestos
                WHERE %s
                  AND sequence_code IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.COTACOES ->
                """
                SELECT CAST(sequence_code AS VARCHAR(50)) AS chave
                FROM dbo.cotacoes
                WHERE %s
                  AND sequence_code IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.LOCALIZACAO_CARGAS ->
                """
                SELECT CAST(sequence_number AS VARCHAR(50)) AS chave
                FROM dbo.localizacao_cargas
                WHERE %s
                  AND sequence_number IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade, filtroEstritoDataExtracao));
            case ConstantesEntidades.CONTAS_A_PAGAR ->
                """
                SELECT CAST(sequence_code AS VARCHAR(50)) AS chave
                FROM dbo.contas_a_pagar
                WHERE %s
                  AND sequence_code IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade, filtroEstritoDataExtracao));
            case ConstantesEntidades.FATURAS_POR_CLIENTE ->
                """
                SELECT unique_id AS chave
                FROM dbo.faturas_por_cliente
                WHERE %s
                  AND unique_id IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.INVENTARIO ->
                """
                SELECT identificador_unico AS chave
                FROM dbo.inventario
                WHERE %s
                  AND identificador_unico IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.SINISTROS ->
                """
                SELECT identificador_unico AS chave
                FROM dbo.sinistros
                WHERE %s
                  AND identificador_unico IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.FRETES ->
                """
                SELECT CAST(id AS VARCHAR(50)) AS chave
                FROM dbo.fretes
                WHERE %s
                  AND id IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade, filtroEstritoDataExtracao));
            case ConstantesEntidades.COLETAS ->
                """
                SELECT id AS chave
                FROM dbo.coletas
                WHERE %s
                  AND id IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade, filtroEstritoDataExtracao));
            case ConstantesEntidades.FATURAS_GRAPHQL ->
                """
                SELECT CAST(id AS VARCHAR(50)) AS chave
                FROM dbo.faturas_graphql
                WHERE %s
                  AND id IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.USUARIOS_SISTEMA ->
                """
                SELECT CAST(user_id AS VARCHAR(50)) AS chave
                FROM dbo.dim_usuarios
                WHERE %s
                  AND user_id IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade, filtroEstritoDataExtracao));
            default -> throw new IllegalArgumentException("Entidade nao suportada na comparacao detalhada: " + entidade);
        };

        final Set<String> chaves = new HashSet<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            preencherParametrosFiltroBanco(
                stmt,
                entidade,
                janela,
                periodoInicio,
                periodoFim,
                filtroEstritoDataExtracao
            );
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final String chave = ConstantesEntidades.MANIFESTOS.equals(entidade)
                        ? resolverChaveManifesto(rs)
                        : rs.getString("chave");
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
        return carregarHashesMetadataBancoNaJanela(
            conexao,
            entidade,
            janela,
            janela.inicio().toLocalDate(),
            janela.fim().toLocalDate()
        );
    }

    Map<String, String> carregarHashesMetadataBancoNaJanela(
        final Connection conexao,
        final String entidade,
        final JanelaExecucao janela,
        final LocalDate periodoInicio,
        final LocalDate periodoFim
    ) throws SQLException {
        return carregarHashesMetadataBancoNaJanela(
            conexao,
            entidade,
            janela,
            periodoInicio,
            periodoFim,
            false
        );
    }

    Map<String, String> carregarHashesMetadataBancoNaJanela(
        final Connection conexao,
        final String entidade,
        final JanelaExecucao janela,
        final LocalDate periodoInicio,
        final LocalDate periodoFim,
        final boolean filtroEstritoDataExtracao
    ) throws SQLException {
        final String sql = switch (entidade) {
            case ConstantesEntidades.MANIFESTOS ->
                """
                SELECT sequence_code, pick_sequence_code, mdfe_number, metadata
                FROM dbo.manifestos
                WHERE %s
                  AND sequence_code IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.COTACOES ->
                """
                SELECT CAST(sequence_code AS VARCHAR(50)) AS chave, metadata
                FROM dbo.cotacoes
                WHERE %s
                  AND sequence_code IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.LOCALIZACAO_CARGAS ->
                """
                SELECT CAST(sequence_number AS VARCHAR(50)) AS chave, metadata
                FROM dbo.localizacao_cargas
                WHERE %s
                  AND sequence_number IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade, filtroEstritoDataExtracao));
            case ConstantesEntidades.CONTAS_A_PAGAR ->
                """
                SELECT CAST(sequence_code AS VARCHAR(50)) AS chave, metadata
                FROM dbo.contas_a_pagar
                WHERE %s
                  AND sequence_code IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade, filtroEstritoDataExtracao));
            case ConstantesEntidades.FATURAS_POR_CLIENTE ->
                """
                SELECT unique_id AS chave, metadata
                FROM dbo.faturas_por_cliente
                WHERE %s
                  AND unique_id IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.INVENTARIO ->
                """
                SELECT identificador_unico AS chave, metadata
                FROM dbo.inventario
                WHERE %s
                  AND identificador_unico IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.SINISTROS ->
                """
                SELECT identificador_unico AS chave, metadata
                FROM dbo.sinistros
                WHERE %s
                  AND identificador_unico IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.FRETES ->
                """
                SELECT CAST(id AS VARCHAR(50)) AS chave, metadata
                FROM dbo.fretes
                WHERE %s
                  AND id IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade, filtroEstritoDataExtracao));
            case ConstantesEntidades.COLETAS ->
                """
                SELECT id AS chave, metadata
                FROM dbo.coletas
                WHERE %s
                  AND id IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade, filtroEstritoDataExtracao));
            case ConstantesEntidades.FATURAS_GRAPHQL ->
                """
                SELECT CAST(id AS VARCHAR(50)) AS chave, metadata
                FROM dbo.faturas_graphql
                WHERE %s
                  AND id IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.USUARIOS_SISTEMA ->
                """
                SELECT CAST(user_id AS VARCHAR(50)) AS chave, nome, data_atualizacao
                FROM dbo.dim_usuarios
                WHERE %s
                  AND user_id IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade, filtroEstritoDataExtracao));
            default -> throw new IllegalArgumentException("Entidade nao suportada na comparacao detalhada: " + entidade);
        };

        final Map<String, String> hashesPorChave = new LinkedHashMap<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            preencherParametrosFiltroBanco(
                stmt,
                entidade,
                janela,
                periodoInicio,
                periodoFim,
                filtroEstritoDataExtracao
            );
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final String chave = ConstantesEntidades.MANIFESTOS.equals(entidade)
                        ? resolverChaveManifesto(rs)
                        : rs.getString("chave");
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
        return carregarMetadataBrutaBancoNaJanela(
            conexao,
            entidade,
            janela,
            janela.inicio().toLocalDate(),
            janela.fim().toLocalDate()
        );
    }

    Map<String, String> carregarMetadataBrutaBancoNaJanela(
        final Connection conexao,
        final String entidade,
        final JanelaExecucao janela,
        final LocalDate periodoInicio,
        final LocalDate periodoFim
    ) throws SQLException {
        final String sql = switch (entidade) {
            case ConstantesEntidades.MANIFESTOS ->
                """
                SELECT sequence_code, pick_sequence_code, mdfe_number, metadata
                FROM dbo.manifestos
                WHERE %s
                  AND sequence_code IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.COTACOES ->
                """
                SELECT CAST(sequence_code AS VARCHAR(50)) AS chave, metadata
                FROM dbo.cotacoes
                WHERE %s
                  AND sequence_code IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.LOCALIZACAO_CARGAS ->
                """
                SELECT CAST(sequence_number AS VARCHAR(50)) AS chave, metadata
                FROM dbo.localizacao_cargas
                WHERE %s
                  AND sequence_number IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.CONTAS_A_PAGAR ->
                """
                SELECT CAST(sequence_code AS VARCHAR(50)) AS chave, metadata
                FROM dbo.contas_a_pagar
                WHERE %s
                  AND sequence_code IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.FATURAS_POR_CLIENTE ->
                """
                SELECT unique_id AS chave, metadata
                FROM dbo.faturas_por_cliente
                WHERE %s
                  AND unique_id IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.INVENTARIO ->
                """
                SELECT identificador_unico AS chave, metadata
                FROM dbo.inventario
                WHERE %s
                  AND identificador_unico IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.SINISTROS ->
                """
                SELECT identificador_unico AS chave, metadata
                FROM dbo.sinistros
                WHERE %s
                  AND identificador_unico IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.FRETES ->
                """
                SELECT CAST(id AS VARCHAR(50)) AS chave, metadata
                FROM dbo.fretes
                WHERE %s
                  AND id IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.COLETAS ->
                """
                SELECT id AS chave, metadata
                FROM dbo.coletas
                WHERE %s
                  AND id IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.FATURAS_GRAPHQL ->
                """
                SELECT CAST(id AS VARCHAR(50)) AS chave, metadata
                FROM dbo.faturas_graphql
                WHERE %s
                  AND id IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            case ConstantesEntidades.USUARIOS_SISTEMA ->
                """
                SELECT CAST(user_id AS VARCHAR(50)) AS chave, nome, data_atualizacao
                FROM dbo.dim_usuarios
                WHERE %s
                  AND user_id IS NOT NULL
                """.formatted(condicaoFiltroBanco(entidade));
            default -> throw new IllegalArgumentException("Entidade nao suportada na comparacao detalhada: " + entidade);
        };

        final Map<String, String> metadataPorChave = new LinkedHashMap<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            preencherParametrosFiltroBanco(stmt, entidade, janela, periodoInicio, periodoFim);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final String chave = ConstantesEntidades.MANIFESTOS.equals(entidade)
                        ? resolverChaveManifesto(rs)
                        : rs.getString("chave");
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

    static String montarChaveManifestoValidacao(final Long sequenceCode,
                                                final Long pickSequenceCode,
                                                final Integer mdfeNumber,
                                                final String metadata) {
        if (sequenceCode == null) {
            return null;
        }
        final Long pickEfetivo = pickSequenceCode != null
            ? pickSequenceCode
            : extrairLongMetadata(metadata, METADATA_PICK_SEQUENCE_CODE).orElse(-1L);
        final Long mdfeEfetivo = mdfeNumber != null
            ? mdfeNumber.longValue()
            : extrairLongMetadata(metadata, METADATA_MDFE_NUMBER).orElse(-1L);
        return sequenceCode + "|" + pickEfetivo + "|" + mdfeEfetivo;
    }

    private String resolverChaveManifesto(final ResultSet rs) throws SQLException {
        final Long sequenceCode = obterLongNullable(rs, "sequence_code");
        final Long pickSequenceCode = obterLongNullable(rs, "pick_sequence_code");
        final Integer mdfeNumber = obterIntegerNullable(rs, "mdfe_number");
        final String metadata = rs.getString("metadata");
        return montarChaveManifestoValidacao(sequenceCode, pickSequenceCode, mdfeNumber, metadata);
    }

    private static Optional<Long> extrairLongMetadata(final String metadata, final String campo) {
        if (metadata == null || metadata.isBlank() || campo == null || campo.isBlank()) {
            return Optional.empty();
        }
        try {
            final JsonNode root = MapperUtil.sharedJson().readTree(metadata);
            final JsonNode valor = root.path(campo);
            if (valor.isMissingNode() || valor.isNull()) {
                return Optional.empty();
            }
            if (valor.isIntegralNumber()) {
                return Optional.of(valor.longValue());
            }
            if (valor.isTextual()) {
                final String texto = valor.asText().trim();
                if (!texto.isEmpty()) {
                    return Optional.of(Long.parseLong(texto));
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Long obterLongNullable(final ResultSet rs, final String coluna) throws SQLException {
        final long valor = rs.getLong(coluna);
        return rs.wasNull() ? null : valor;
    }

    private Integer obterIntegerNullable(final ResultSet rs, final String coluna) throws SQLException {
        final int valor = rs.getInt(coluna);
        return rs.wasNull() ? null : valor;
    }

    private boolean existeLogCompleto24hNaData(final Connection conexao, final LocalDate data) throws SQLException {
        final LocalDate dataInicio = data.minusDays(1);
        final String sql = """
            SELECT TOP 1 1
            FROM dbo.log_extracoes
            WHERE status_final = 'COMPLETO'
              AND entidade <> ?
              AND CAST(timestamp_inicio AS DATE) = ?
              AND (mensagem LIKE ? OR mensagem LIKE ?)
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, ConstantesEntidades.COLETAS_REFERENCIAL);
            stmt.setDate(2, java.sql.Date.valueOf(data));
            stmt.setString(3, "%" + dataInicio + " a " + data + "%");
            stmt.setString(4, "%" + "Data: " + dataInicio + "%");
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
              AND entidade <> ?
              AND CAST(timestamp_inicio AS DATE) = ?
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, ConstantesEntidades.COLETAS_REFERENCIAL);
            stmt.setDate(2, java.sql.Date.valueOf(data));
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
              AND entidade <> ?
            ORDER BY timestamp_fim DESC
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, ConstantesEntidades.COLETAS_REFERENCIAL);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getDate("data_ref").toLocalDate());
                }
            }
        }
        return Optional.empty();
    }

    private boolean tabelaExiste(final Connection conexao, final String nomeTabela) throws SQLException {
        final String sql = "SELECT CASE WHEN OBJECT_ID(?, 'U') IS NULL THEN 0 ELSE 1 END";
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, "dbo." + nomeTabela);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 1;
            }
        }
    }

    private String condicaoFiltroBanco(final String entidade) {
        return condicaoFiltroBanco(entidade, false);
    }

    private String condicaoFiltroBanco(final String entidade, final boolean filtroEstritoDataExtracao) {
        return switch (entidade) {
            case ConstantesEntidades.COLETAS ->
                filtroEstritoDataExtracao
                    ? "(data_extracao >= ? AND data_extracao <= ?)"
                    : "((data_extracao >= ? AND data_extracao <= ?)"
                        + " OR (request_date BETWEEN ? AND ?))";
            case ConstantesEntidades.FRETES ->
                filtroEstritoDataExtracao
                    ? "(data_extracao >= ? AND data_extracao <= ?)"
                    : "((data_extracao >= ? AND data_extracao <= ?)"
                        + " OR (COALESCE(service_date, CONVERT(date, servico_em)) BETWEEN ? AND ?))";
            case ConstantesEntidades.MANIFESTOS,
                 ConstantesEntidades.COTACOES ->
                "(data_extracao >= ? AND data_extracao <= ?)";
            case ConstantesEntidades.LOCALIZACAO_CARGAS ->
                filtroEstritoDataExtracao
                    ? "(data_extracao >= ? AND data_extracao <= ?)"
                    : "((data_extracao >= ? AND data_extracao <= ?)"
                        + " OR (COALESCE(CAST(service_at AS DATE), CAST(predicted_delivery_at AS DATE)) BETWEEN ? AND ?))";
            case ConstantesEntidades.INVENTARIO ->
                "(("
                    + "data_extracao >= ? AND data_extracao <= ?"
                    + ") OR (COALESCE(CAST(started_at AS DATE), CAST(performance_finished_at AS DATE), CAST(predicted_delivery_at AS DATE)) BETWEEN ? AND ?))";
            case ConstantesEntidades.SINISTROS ->
                "((data_extracao >= ? AND data_extracao <= ?)"
                    + " OR (COALESCE(opening_at_date, occurrence_at_date, expected_solution_date, finished_at_date) BETWEEN ? AND ?))";
            case ConstantesEntidades.CONTAS_A_PAGAR ->
                filtroEstritoDataExtracao
                    ? "(data_extracao >= ? AND data_extracao <= ?)"
                    : "((data_extracao >= ? AND data_extracao <= ?)"
                        + " OR (COALESCE(issue_date, data_transacao, data_liquidacao, CAST(data_criacao AS DATE)) BETWEEN ? AND ?))";
            case ConstantesEntidades.FATURAS_POR_CLIENTE,
                 ConstantesEntidades.FATURAS_GRAPHQL ->
                "(data_extracao >= ? AND data_extracao <= ?)";
            case ConstantesEntidades.USUARIOS_SISTEMA ->
                "(ativo = 1 AND ((origem_atualizado_em >= ? AND origem_atualizado_em <= ?)"
                    + " OR (origem_atualizado_em IS NULL AND COALESCE(ultima_extracao_em, data_atualizacao) >= ?"
                    + " AND COALESCE(ultima_extracao_em, data_atualizacao) <= ?)))";
            default ->
                "(data_extracao >= ? AND data_extracao <= ?)";
        };
    }

    private void preencherParametrosFiltroBanco(
        final PreparedStatement stmt,
        final String entidade,
        final JanelaExecucao janela,
        final LocalDate periodoInicio,
        final LocalDate periodoFim
    ) throws SQLException {
        preencherParametrosFiltroBanco(stmt, entidade, janela, periodoInicio, periodoFim, false);
    }

    private void preencherParametrosFiltroBanco(
        final PreparedStatement stmt,
        final String entidade,
        final JanelaExecucao janela,
        final LocalDate periodoInicio,
        final LocalDate periodoFim,
        final boolean filtroEstritoDataExtracao
    ) throws SQLException {
        if (ConstantesEntidades.USUARIOS_SISTEMA.equals(entidade)) {
            stmt.setTimestamp(1, Timestamp.valueOf(periodoInicio.atStartOfDay()));
            stmt.setTimestamp(2, Timestamp.valueOf(periodoFim.atTime(java.time.LocalTime.MAX)));
            stmt.setTimestamp(3, Timestamp.valueOf(janela.inicio()));
            stmt.setTimestamp(4, Timestamp.valueOf(janela.fim()));
            return;
        }

        stmt.setTimestamp(1, Timestamp.valueOf(janela.inicio()));
        stmt.setTimestamp(2, Timestamp.valueOf(janela.fim()));
        if (filtroEstritoDataExtracao
            && (ConstantesEntidades.CONTAS_A_PAGAR.equals(entidade)
                || ConstantesEntidades.FRETES.equals(entidade)
                || ConstantesEntidades.COLETAS.equals(entidade)
                || ConstantesEntidades.LOCALIZACAO_CARGAS.equals(entidade))) {
            return;
        }

        if (ConstantesEntidades.MANIFESTOS.equals(entidade)
            || ConstantesEntidades.COTACOES.equals(entidade)
            || ConstantesEntidades.FATURAS_POR_CLIENTE.equals(entidade)
            || ConstantesEntidades.FATURAS_GRAPHQL.equals(entidade)) {
            return;
        }

        stmt.setDate(3, java.sql.Date.valueOf(periodoInicio));
        stmt.setDate(4, java.sql.Date.valueOf(periodoFim));
    }

    private LocalDate normalizarFimPeriodoConsulta(final LocalDateTime janelaInicio, final LocalDateTime janelaFim) {
        if (janelaInicio == null || janelaFim == null) {
            return janelaFim == null ? null : janelaFim.toLocalDate();
        }
        if (LocalTime.MIDNIGHT.equals(janelaFim.toLocalTime())
            && janelaFim.toLocalDate().isAfter(janelaInicio.toLocalDate())) {
            return janelaFim.toLocalDate().minusDays(1);
        }
        return janelaFim.toLocalDate();
    }

    @FunctionalInterface
    interface JanelaAbertaCeilingProvider {
        Optional<LocalDateTime> obter();
    }
}
