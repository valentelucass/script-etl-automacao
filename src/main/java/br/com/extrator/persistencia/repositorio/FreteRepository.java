/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/repository/FreteRepository.java
Classe  : FreteRepository (class)
Pacote  : br.com.extrator.persistencia.repositorio
Modulo  : Repositorio de dados
Papel   : Implementa responsabilidade de frete repository.

Conecta com:
- FreteEntity (db.entity)
- ConstantesEntidades (util.validacao)

Fluxo geral:
1) Monta comandos SQL e parametros.
2) Executa operacoes de persistencia/consulta no banco.
3) Converte resultado para entidades de dominio.

Estrutura interna:
Metodos principais:
- getNomeTabela(): expone valor atual do estado interno.
- parseIssuedAt(...1 args): realiza operacao relacionada a "parse issued at".
- extrairDiscriminacaoDoXml(...1 args): realiza operacao relacionada a "extrair discriminacao do xml".
Atributos-chave:
- logger: logger da classe para diagnostico.
- NOME_TABELA: campo de estado para "nome tabela".
[DOC-FILE-END]============================================================== */

package br.com.extrator.persistencia.repositorio;

import br.com.extrator.dominio.graphql.fretes.nfse.FreteNfsePayload;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.persistencia.entidade.FreteEntity;
import br.com.extrator.suporte.configuracao.ConfigEtl;
import br.com.extrator.suporte.observabilidade.ExecutionContext;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

/**
 * RepositÃƒÂ³rio para operaÃƒÂ§ÃƒÂµes de persistÃƒÂªncia da entidade FreteEntity.
 * Implementa a arquitetura de persistÃƒÂªncia hÃƒÂ­brida: colunas-chave para indexaÃƒÂ§ÃƒÂ£o
 * e uma coluna de metadados para resiliÃƒÂªncia e completude dos dados.
 * Utiliza operaÃƒÂ§ÃƒÂµes MERGE (UPSERT) com a chave primÃƒÂ¡ria (id) do frete.
 */
public class FreteRepository extends AbstractRepository<FreteEntity> {
    private static final Logger logger = LoggerFactory.getLogger(FreteRepository.class);
    private static final String NOME_TABELA = ConstantesEntidades.FRETES;
    private static final String NOME_TABELA_STAGING = "#stg_fretes";
    private static final String NOME_TABELA_QUARENTENA = "dbo.sys_reconciliation_quarantine";
    private static final String ENTIDADE_QUARENTENA = "fretes";
    private static final List<String> COLUNAS_MERGE = List.of(
        "id", "servico_em", "criado_em", "status", "cortesia", "modal", "tipo_frete", "valor_total", "valor_notas", "peso_notas",
        "id_corporacao", "id_cidade_destino", "data_previsao_entrega", "service_date", "finished_at",
        "fit_dpn_performance_finished_at", "corporation_sequence_number", "pagador_id", "pagador_nome",
        "remetente_id", "remetente_nome", "origem_cidade", "origem_uf", "destinatario_id", "destinatario_nome",
        "destino_cidade", "destino_uf", "filial_nome", "numero_nota_fiscal", "tabela_preco_nome",
        "classificacao_nome", "centro_custo_nome", "usuario_nome", "reference_number", "chave_cte", "numero_cte",
        "serie_cte", "invoices_total_volumes", "taxed_weight", "real_weight", "total_cubic_volume", "subtotal",
        "accounting_credit_id", "accounting_credit_installment_id", "service_type", "insurance_enabled",
        "gris_subtotal", "tde_subtotal", "modal_cte", "redispatch_subtotal", "suframa_subtotal", "payment_type",
        "previous_document_type", "products_value", "trt_subtotal", "nfse_series", "nfse_number", "insurance_id",
        "other_fees", "km", "payment_accountable_type", "insured_value", "globalized", "sec_cat_subtotal",
        "globalized_type", "price_table_accountable_type", "insurance_accountable_type", "pagador_documento",
        "remetente_documento", "destinatario_documento", "filial_cnpj", "cte_issued_at", "cubages_cubed_weight",
        "freight_weight_subtotal", "ad_valorem_subtotal", "toll_subtotal", "itr_subtotal", "fiscal_cst_type",
        "fiscal_cfop_code", "fiscal_tax_value", "fiscal_pis_value", "fiscal_cofins_value", "filial_apelido",
        "cte_id", "cte_emission_type", "cte_created_at", "fiscal_calculation_basis", "fiscal_tax_rate",
        "fiscal_pis_rate", "fiscal_cofins_rate", "fiscal_has_difal", "fiscal_difal_origin",
        "fiscal_difal_destination", "metadata", "data_extracao"
    );
    private static final List<String> COLUNAS_ATUALIZAVEIS = List.copyOf(COLUNAS_MERGE.subList(1, COLUNAS_MERGE.size()));
    private final FreteNfseUpdateSupport nfseUpdateSupport = new FreteNfseUpdateSupport(logger);

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }

    @Override
    protected boolean aceitarMergeSemAlteracoesComoSucesso(final FreteEntity frete) {
        return true;
    }

    @Override
    protected boolean usarStagingPorExecucao() {
        return true;
    }

    @Override
    protected void prepararStagingPorExecucao(final Connection conexao) throws SQLException {
        recriarTabelaTemporariaPorExecucao(conexao, NOME_TABELA_STAGING);
    }

    @Override
    protected int executarMergeNoDestinoDaExecucao(final Connection conexao, final FreteEntity frete) throws SQLException {
        return executarMergeEmTabela(conexao, frete, validarNomeTabelaTemporaria(NOME_TABELA_STAGING));
    }

    @Override
    protected int promoverStagingPorExecucao(final Connection conexao) throws SQLException {
        final String freshnessGuard = buildMonotonicUpdateGuard(
            "COALESCE(CAST(target.cte_created_at AS datetime2), CAST(target.cte_issued_at AS datetime2), CAST(target.criado_em AS datetime2), CAST(target.servico_em AS datetime2))",
            "COALESCE(CAST(source.cte_created_at AS datetime2), CAST(source.cte_issued_at AS datetime2), CAST(source.criado_em AS datetime2), CAST(source.servico_em AS datetime2))"
        );
        return promoverStagingComMerge(
            conexao,
            NOME_TABELA_STAGING,
            "target.id = source.id",
            freshnessGuard,
            COLUNAS_MERGE,
            COLUNAS_ATUALIZAVEIS
        );
    }

    /**
     * Lista IDs de accounting_credit_id presentes em fretes e ausentes em faturas_graphql.
     * A busca e limitada ao periodo operacional de service_date/servico_em.
     */
    public List<Long> listarAccountingCreditIdsOrfaos(final LocalDate dataInicio,
                                                      final LocalDate dataFim,
                                                      final int limite) throws SQLException {
        if (dataInicio == null || dataFim == null || dataFim.isBefore(dataInicio)) {
            return List.of();
        }

        final int limiteEfetivo = Math.max(1, Math.min(limite, 5000));
        final String sql = """
            SELECT DISTINCT TOP (%d) CAST(f.accounting_credit_id AS BIGINT) AS accounting_credit_id
            FROM dbo.fretes f
            WHERE f.accounting_credit_id IS NOT NULL
              AND COALESCE(f.service_date, CONVERT(date, f.servico_em)) BETWEEN ? AND ?
              AND NOT EXISTS (
                    SELECT 1
                    FROM dbo.faturas_graphql fg
                    WHERE fg.id = f.accounting_credit_id
              )
            ORDER BY CAST(f.accounting_credit_id AS BIGINT)
            """.formatted(limiteEfetivo);

        final List<Long> ids = new ArrayList<>();
        try (Connection conexao = obterConexao();
             PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setObject(1, dataInicio, Types.DATE);
            ps.setObject(2, dataFim, Types.DATE);
            try (ResultSet rs = ps.executeQuery()) {
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

    public int removerAusentesNoPeriodo(final LocalDate dataInicio,
                                        final LocalDate dataFim,
                                        final Collection<Long> idsPresentes) throws SQLException {
        if (dataInicio == null || dataFim == null || dataFim.isBefore(dataInicio)) {
            return 0;
        }

        final List<Long> idsNormalizados = idsPresentes == null
            ? List.of()
            : idsPresentes.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        try (Connection conexao = obterConexao()) {
            final boolean autoCommitOriginal = conexao.getAutoCommit();
            conexao.setAutoCommit(false);
            try {
                final boolean tabelaQuarentenaDisponivel = tabelaExiste(conexao, NOME_TABELA_QUARENTENA);
                try (Statement stmt = conexao.createStatement()) {
                    stmt.execute("CREATE TABLE #fretes_periodo_api_ids (id BIGINT NOT NULL PRIMARY KEY)");
                    stmt.execute("CREATE TABLE #fretes_periodo_candidatos (id BIGINT NOT NULL PRIMARY KEY)");
                    stmt.execute("CREATE TABLE #fretes_periodo_delete (id BIGINT NOT NULL PRIMARY KEY)");
                }

                if (!idsNormalizados.isEmpty()) {
                    try (PreparedStatement insert = conexao.prepareStatement(
                        "INSERT INTO #fretes_periodo_api_ids (id) VALUES (?)"
                    )) {
                        int contadorBatch = 0;
                        for (final Long id : idsNormalizados) {
                            insert.setLong(1, id);
                            insert.addBatch();
                            contadorBatch++;
                            if (contadorBatch % 500 == 0) {
                                insert.executeBatch();
                            }
                        }
                        if (contadorBatch % 500 != 0) {
                            insert.executeBatch();
                        }
                    }
                }

                final int candidatos = popularCandidatosAusentes(conexao, dataInicio, dataFim);
                if (candidatos == 0) {
                    if (tabelaQuarentenaDisponivel) {
                        liberarRegistrosPresentesNaQuarentena(conexao, dataInicio, dataFim, now(), null, null);
                    }
                    conexao.commit();
                    logger.info(
                        "Reconciliacao de fretes por periodo concluida sem ausencias: periodo={}..{} | ids_api={} | removidos=0",
                        dataInicio,
                        dataFim,
                        idsNormalizados.size()
                    );
                    return 0;
                }

                if (!tabelaQuarentenaDisponivel) {
                    logger.error(
                        "Prune de fretes bloqueado: tabela de quarentena {} ausente. periodo={}..{} | candidatos={}",
                        NOME_TABELA_QUARENTENA,
                        dataInicio,
                        dataFim,
                        candidatos
                    );
                    conexao.rollback();
                    return 0;
                }

                final List<Integer> volumesHistoricos = buscarVolumesHistoricosComparaveis(
                    conexao,
                    diasDaJanela(dataInicio, dataFim),
                    ConfigEtl.obterFretePruneHistoricoExecucoes()
                );
                final FretePruneGuardrailEvaluator.Decision decision = avaliarGuardrail(volumesHistoricos, idsNormalizados.size());
                final LocalDateTime agora = now();
                final String executionUuid = currentExecutionId();
                final String cycleId = currentCycleId();

                liberarRegistrosPresentesNaQuarentena(conexao, dataInicio, dataFim, agora, executionUuid, cycleId);

                final boolean executarDelete = decision.allowDeletion() || !ConfigEtl.isFretePruneGuardrailAtivo();
                registrarCandidatosNaQuarentena(
                    conexao,
                    dataInicio,
                    dataFim,
                    agora,
                    executionUuid,
                    cycleId,
                    executarDelete,
                    buildGuardrailReason(decision)
                );

                if (!executarDelete) {
                    conexao.commit();
                    logger.warn(
                        "Prune de fretes bloqueado por guardrail: periodo={}..{} | ids_api={} | candidatos={} | baseline_mediana={} | ratio={}",
                        dataInicio,
                        dataFim,
                        idsNormalizados.size(),
                        candidatos,
                        decision.baselineMedian(),
                        String.format(Locale.US, "%.3f", decision.currentToBaselineRatio())
                    );
                    return 0;
                }

                final int removidos = deletarCandidatosElegiveis(
                    conexao,
                    dataInicio,
                    dataFim,
                    agora,
                    executionUuid,
                    cycleId,
                    ConfigEtl.obterFretePruneMinAusenciasConsecutivas()
                );

                if (candidatos > removidos) {
                    logger.info(
                        "Quarentena de prune de fretes atualizada sem delete imediato: periodo={}..{} | candidatos={} | removidos={} | minimo_ausencias={}",
                        dataInicio,
                        dataFim,
                        candidatos,
                        removidos,
                        ConfigEtl.obterFretePruneMinAusenciasConsecutivas()
                    );
                }

                conexao.commit();
                logger.info(
                    "Reconciliacao de fretes por periodo concluida: periodo={}..{} | ids_api={} | removidos={}",
                    dataInicio,
                    dataFim,
                    idsNormalizados.size(),
                    removidos
                );
                return removidos;
            } catch (final SQLException e) {
                conexao.rollback();
                throw e;
            } finally {
                conexao.setAutoCommit(autoCommitOriginal);
            }
        }
    }

    private FretePruneGuardrailEvaluator.Decision avaliarGuardrail(final List<Integer> volumesHistoricos,
                                                                   final int volumeAtual) {
        final FretePruneGuardrailEvaluator evaluator = new FretePruneGuardrailEvaluator(
            ConfigEtl.obterFretePruneGuardrailMinRatio(),
            ConfigEtl.obterFretePruneBaselineMinRegistros()
        );
        if (!ConfigEtl.isFretePruneGuardrailAtivo()) {
            return new FretePruneGuardrailEvaluator.Decision(true, null, 0, 1.0d);
        }
        return evaluator.evaluate(volumesHistoricos, volumeAtual);
    }

    private List<Integer> buscarVolumesHistoricosComparaveis(final Connection conexao,
                                                             final int diasJanela,
                                                             final int limite) throws SQLException {
        final String sql = """
            SELECT TOP (?) api_total_unico
              FROM dbo.sys_execution_audit
             WHERE entidade = ?
               AND api_completa = 1
               AND status_execucao IN ('COMPLETO', 'RECONCILIADO', 'RECONCILED')
               AND api_total_unico >= 0
               AND janela_consulta_inicio IS NOT NULL
               AND janela_consulta_fim IS NOT NULL
               AND DATEDIFF(DAY, CAST(janela_consulta_inicio AS date), CAST(janela_consulta_fim AS date)) + 1 = ?
             ORDER BY finished_at DESC
            """;
        final List<Integer> volumes = new ArrayList<>();
        if (!tabelaExiste(conexao, "dbo.sys_execution_audit")) {
            return volumes;
        }
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limite));
            ps.setString(2, ConstantesEntidades.FRETES);
            ps.setInt(3, Math.max(1, diasJanela));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    volumes.add(rs.getInt("api_total_unico"));
                }
            }
        }
        return volumes;
    }

    private int popularCandidatosAusentes(final Connection conexao,
                                          final LocalDate dataInicio,
                                          final LocalDate dataFim) throws SQLException {
        final String sql = """
            INSERT INTO #fretes_periodo_candidatos (id)
            SELECT f.id
              FROM dbo.fretes f
             WHERE COALESCE(f.service_date, CONVERT(date, f.servico_em)) BETWEEN ? AND ?
               AND NOT EXISTS (
                     SELECT 1
                       FROM #fretes_periodo_api_ids ids
                      WHERE ids.id = f.id
               )
            """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setObject(1, dataInicio, Types.DATE);
            ps.setObject(2, dataFim, Types.DATE);
            return ps.executeUpdate();
        }
    }

    private void liberarRegistrosPresentesNaQuarentena(final Connection conexao,
                                                       final LocalDate dataInicio,
                                                       final LocalDate dataFim,
                                                       final LocalDateTime agora,
                                                       final String executionUuid,
                                                       final String cycleId) throws SQLException {
        final String sql = """
            UPDATE q
               SET released_at = ?,
                   release_reason = 'PRESENTE_NA_API',
                   last_guardrail_reason = NULL,
                   last_execution_uuid = COALESCE(?, last_execution_uuid),
                   last_cycle_id = COALESCE(?, last_cycle_id),
                   updated_at = SYSDATETIME()
              FROM dbo.sys_reconciliation_quarantine q
              JOIN #fretes_periodo_api_ids ids
                ON ids.id = q.record_id
             WHERE q.entity_name = ?
               AND q.window_start = ?
               AND q.window_end = ?
               AND q.released_at IS NULL
            """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setTimestamp(1, java.sql.Timestamp.valueOf(agora));
            ps.setString(2, executionUuid);
            ps.setString(3, cycleId);
            ps.setString(4, ENTIDADE_QUARENTENA);
            ps.setObject(5, dataInicio, Types.DATE);
            ps.setObject(6, dataFim, Types.DATE);
            ps.executeUpdate();
        }
    }

    private void registrarCandidatosNaQuarentena(final Connection conexao,
                                                 final LocalDate dataInicio,
                                                 final LocalDate dataFim,
                                                 final LocalDateTime agora,
                                                 final String executionUuid,
                                                 final String cycleId,
                                                 final boolean contarAusencia,
                                                 final String guardrailReason) throws SQLException {
        final String sql = """
            MERGE dbo.sys_reconciliation_quarantine AS target
            USING (
                SELECT id
                  FROM #fretes_periodo_candidatos
            ) AS source
               ON target.entity_name = ?
              AND target.record_id = source.id
              AND target.window_start = ?
              AND target.window_end = ?
            WHEN MATCHED THEN
                UPDATE SET
                    first_seen_absent_at = CASE WHEN target.released_at IS NULL THEN target.first_seen_absent_at ELSE ? END,
                    last_seen_absent_at = ?,
                    absence_hits = CASE
                        WHEN ? = 1 THEN CASE WHEN target.released_at IS NULL THEN target.absence_hits + 1 ELSE 1 END
                        ELSE CASE WHEN target.released_at IS NULL THEN target.absence_hits ELSE 0 END
                    END,
                    first_execution_uuid = CASE WHEN target.released_at IS NULL THEN target.first_execution_uuid ELSE ? END,
                    last_execution_uuid = ?,
                    first_cycle_id = CASE WHEN target.released_at IS NULL THEN target.first_cycle_id ELSE ? END,
                    last_cycle_id = ?,
                    released_at = NULL,
                    release_reason = NULL,
                    last_guardrail_reason = ?,
                    updated_at = SYSDATETIME()
            WHEN NOT MATCHED THEN
                INSERT (
                    entity_name, record_id, window_start, window_end,
                    first_seen_absent_at, last_seen_absent_at, absence_hits,
                    first_execution_uuid, last_execution_uuid, first_cycle_id, last_cycle_id,
                    released_at, release_reason, last_guardrail_reason
                )
                VALUES (
                    ?, source.id, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?,
                    NULL, NULL, ?
                );
            """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            int index = 1;
            ps.setString(index++, ENTIDADE_QUARENTENA);
            ps.setObject(index++, dataInicio, Types.DATE);
            ps.setObject(index++, dataFim, Types.DATE);
            ps.setTimestamp(index++, java.sql.Timestamp.valueOf(agora));
            ps.setTimestamp(index++, java.sql.Timestamp.valueOf(agora));
            ps.setInt(index++, contarAusencia ? 1 : 0);
            ps.setString(index++, executionUuid);
            ps.setString(index++, executionUuid);
            ps.setString(index++, cycleId);
            ps.setString(index++, cycleId);
            ps.setString(index++, guardrailReason);
            ps.setString(index++, ENTIDADE_QUARENTENA);
            ps.setObject(index++, dataInicio, Types.DATE);
            ps.setObject(index++, dataFim, Types.DATE);
            ps.setTimestamp(index++, java.sql.Timestamp.valueOf(agora));
            ps.setTimestamp(index++, java.sql.Timestamp.valueOf(agora));
            ps.setInt(index++, contarAusencia ? 1 : 0);
            ps.setString(index++, executionUuid);
            ps.setString(index++, executionUuid);
            ps.setString(index++, cycleId);
            ps.setString(index++, cycleId);
            ps.setString(index++, guardrailReason);
            ps.executeUpdate();
        }
    }

    private int deletarCandidatosElegiveis(final Connection conexao,
                                           final LocalDate dataInicio,
                                           final LocalDate dataFim,
                                           final LocalDateTime agora,
                                           final String executionUuid,
                                           final String cycleId,
                                           final int minimoAusencias) throws SQLException {
        final String sqlPopularDelete = """
            INSERT INTO #fretes_periodo_delete (id)
            SELECT q.record_id
              FROM dbo.sys_reconciliation_quarantine q
              JOIN #fretes_periodo_candidatos c
                ON c.id = q.record_id
             WHERE q.entity_name = ?
               AND q.window_start = ?
               AND q.window_end = ?
               AND q.released_at IS NULL
               AND q.absence_hits >= ?
            """;
        try (PreparedStatement ps = conexao.prepareStatement(sqlPopularDelete)) {
            ps.setString(1, ENTIDADE_QUARENTENA);
            ps.setObject(2, dataInicio, Types.DATE);
            ps.setObject(3, dataFim, Types.DATE);
            ps.setInt(4, Math.max(1, minimoAusencias));
            ps.executeUpdate();
        }

        final String sqlDelete = """
            DELETE f
              FROM dbo.fretes f
              JOIN #fretes_periodo_delete d
                ON d.id = f.id
             WHERE COALESCE(f.service_date, CONVERT(date, f.servico_em)) BETWEEN ? AND ?
            """;
        final int removidos;
        try (PreparedStatement ps = conexao.prepareStatement(sqlDelete)) {
            ps.setObject(1, dataInicio, Types.DATE);
            ps.setObject(2, dataFim, Types.DATE);
            removidos = ps.executeUpdate();
        }

        if (removidos > 0) {
            final String sqlAtualizarQuarentena = """
                UPDATE q
                   SET released_at = ?,
                       release_reason = 'PRUNE_DELETE',
                       last_guardrail_reason = NULL,
                       last_execution_uuid = COALESCE(?, last_execution_uuid),
                       last_cycle_id = COALESCE(?, last_cycle_id),
                       updated_at = SYSDATETIME()
                  FROM dbo.sys_reconciliation_quarantine q
                  JOIN #fretes_periodo_delete d
                    ON d.id = q.record_id
                 WHERE q.entity_name = ?
                   AND q.window_start = ?
                   AND q.window_end = ?
                   AND q.released_at IS NULL
                """;
            try (PreparedStatement ps = conexao.prepareStatement(sqlAtualizarQuarentena)) {
                ps.setTimestamp(1, java.sql.Timestamp.valueOf(agora));
                ps.setString(2, executionUuid);
                ps.setString(3, cycleId);
                ps.setString(4, ENTIDADE_QUARENTENA);
                ps.setObject(5, dataInicio, Types.DATE);
                ps.setObject(6, dataFim, Types.DATE);
                ps.executeUpdate();
            }
        }
        return removidos;
    }

    private String buildGuardrailReason(final FretePruneGuardrailEvaluator.Decision decision) {
        if (decision == null || decision.blockReason() == null) {
            return null;
        }
        return decision.blockReason().name();
    }

    private int diasDaJanela(final LocalDate dataInicio, final LocalDate dataFim) {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
    }

    private boolean tabelaExiste(final Connection conexao, final String nomeTabela) throws SQLException {
        final String sql = "SELECT CASE WHEN OBJECT_ID(?, 'U') IS NULL THEN 0 ELSE 1 END";
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, nomeTabela);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) == 1;
            }
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }

    private String currentExecutionId() {
        final String executionUuid = ExecutionContext.currentExecutionId();
        return "n/a".equalsIgnoreCase(executionUuid) ? null : executionUuid;
    }

    private String currentCycleId() {
        final String cycleId = ExecutionContext.currentCycleId();
        return "n/a".equalsIgnoreCase(cycleId) ? null : cycleId;
    }

    /**
     * Atualiza colunas de NFSe em fretes, vinculando pelo numero da NFSe.
     * Retorna o total de linhas afetadas.
     */
    public int atualizarCamposNfse(final java.util.List<? extends FreteNfsePayload> nfseList) throws SQLException {
        if (nfseList == null || nfseList.isEmpty()) {
            return 0;
        }
        try (Connection conexao = obterConexao()) {
            return nfseUpdateSupport.atualizarCamposNfse(conexao, nfseList);
        }
    }

    @Override
    protected int executarMerge(final Connection conexao, final FreteEntity frete) throws SQLException {
        return executarMergeEmTabela(conexao, frete, qualificarTabelaDestino());
    }

    private int executarMergeEmTabela(final Connection conexao,
                                      final FreteEntity frete,
                                      final String tabelaAlvo) throws SQLException {
        // Para Fretes, o 'id' ÃƒÂ© a ÃƒÂºnica chave confiÃƒÂ¡vel para o MERGE.
        if (frete.getId() == null) {
            throw new SQLException("NÃƒÂ£o ÃƒÂ© possÃƒÂ­vel executar o MERGE para Frete sem um ID.");
        }

        final String freshnessGuard = buildMonotonicUpdateGuard(
            "COALESCE(CAST(target.cte_created_at AS datetime2), CAST(target.cte_issued_at AS datetime2), CAST(target.criado_em AS datetime2), CAST(target.servico_em AS datetime2))",
            "COALESCE(CAST(source.cte_created_at AS datetime2), CAST(source.cte_issued_at AS datetime2), CAST(source.criado_em AS datetime2), CAST(source.servico_em AS datetime2))"
        );
        final String sql = String.format("""
            MERGE %s WITH (HOLDLOCK) AS target
            USING (VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?,
                ?, ?
            ))
                AS source (id, servico_em, criado_em, status, cortesia, modal, tipo_frete, valor_total, valor_notas, peso_notas, id_corporacao, id_cidade_destino, data_previsao_entrega, service_date,
                           finished_at, fit_dpn_performance_finished_at, corporation_sequence_number,
                           pagador_id, pagador_nome, remetente_id, remetente_nome, origem_cidade, origem_uf, destinatario_id, destinatario_nome, destino_cidade, destino_uf,
                           filial_nome, numero_nota_fiscal, tabela_preco_nome, classificacao_nome, centro_custo_nome, usuario_nome, reference_number, chave_cte, numero_cte, serie_cte, invoices_total_volumes,
                           taxed_weight, real_weight, total_cubic_volume, subtotal,
                           accounting_credit_id, accounting_credit_installment_id,
                           service_type, insurance_enabled, gris_subtotal, tde_subtotal, modal_cte, redispatch_subtotal, suframa_subtotal, payment_type, previous_document_type,
                           products_value, trt_subtotal, nfse_series, nfse_number, insurance_id, other_fees, km, payment_accountable_type, insured_value, globalized, sec_cat_subtotal, globalized_type, price_table_accountable_type, insurance_accountable_type,
                           pagador_documento, remetente_documento, destinatario_documento, filial_cnpj, cte_issued_at, cubages_cubed_weight, freight_weight_subtotal, ad_valorem_subtotal, toll_subtotal, itr_subtotal,
                           fiscal_cst_type, fiscal_cfop_code, fiscal_tax_value, fiscal_pis_value, fiscal_cofins_value,
                           filial_apelido, cte_id, cte_emission_type, cte_created_at,
                           fiscal_calculation_basis, fiscal_tax_rate, fiscal_pis_rate, fiscal_cofins_rate, fiscal_has_difal, fiscal_difal_origin, fiscal_difal_destination,
                           metadata, data_extracao)
            ON target.id = source.id
            WHEN MATCHED AND %s THEN
                UPDATE SET
                    servico_em = source.servico_em,
                    criado_em = source.criado_em,
                    status = source.status,
                    cortesia = source.cortesia,
                    modal = source.modal,
                    tipo_frete = source.tipo_frete,
                    valor_total = source.valor_total,
                    valor_notas = source.valor_notas,
                    peso_notas = source.peso_notas,
                    id_corporacao = source.id_corporacao,
                    id_cidade_destino = source.id_cidade_destino,
                    data_previsao_entrega = source.data_previsao_entrega,
                    service_date = source.service_date,
                    finished_at = source.finished_at,
                    fit_dpn_performance_finished_at = source.fit_dpn_performance_finished_at,
                    corporation_sequence_number = source.corporation_sequence_number,
                    pagador_id = source.pagador_id,
                    pagador_nome = source.pagador_nome,
                    remetente_id = source.remetente_id,
                    remetente_nome = source.remetente_nome,
                    origem_cidade = source.origem_cidade,
                    origem_uf = source.origem_uf,
                    destinatario_id = source.destinatario_id,
                    destinatario_nome = source.destinatario_nome,
                    destino_cidade = source.destino_cidade,
                    destino_uf = source.destino_uf,
                    filial_nome = source.filial_nome,
                    numero_nota_fiscal = source.numero_nota_fiscal,
                    tabela_preco_nome = source.tabela_preco_nome,
                    classificacao_nome = source.classificacao_nome,
                    centro_custo_nome = source.centro_custo_nome,
                    usuario_nome = source.usuario_nome,
                    reference_number = source.reference_number,
                    chave_cte = source.chave_cte,
                    numero_cte = source.numero_cte,
                    serie_cte = source.serie_cte,
                    invoices_total_volumes = source.invoices_total_volumes,
                    taxed_weight = source.taxed_weight,
                    real_weight = source.real_weight,
                    total_cubic_volume = source.total_cubic_volume,
                    subtotal = source.subtotal,
                    accounting_credit_id = source.accounting_credit_id,
                    accounting_credit_installment_id = source.accounting_credit_installment_id,
                    service_type = source.service_type,
                    insurance_enabled = source.insurance_enabled,
                    gris_subtotal = source.gris_subtotal,
                    tde_subtotal = source.tde_subtotal,
                    modal_cte = source.modal_cte,
                    redispatch_subtotal = source.redispatch_subtotal,
                    suframa_subtotal = source.suframa_subtotal,
                    payment_type = source.payment_type,
                    previous_document_type = source.previous_document_type,
                    products_value = source.products_value,
                    trt_subtotal = source.trt_subtotal,
                    nfse_series = source.nfse_series,
                    nfse_number = source.nfse_number,
                    insurance_id = source.insurance_id,
                    other_fees = source.other_fees,
                    km = source.km,
                    payment_accountable_type = source.payment_accountable_type,
                    insured_value = source.insured_value,
                    globalized = source.globalized,
                    sec_cat_subtotal = source.sec_cat_subtotal,
                    globalized_type = source.globalized_type,
                    price_table_accountable_type = source.price_table_accountable_type,
                    insurance_accountable_type = source.insurance_accountable_type,
                    pagador_documento = source.pagador_documento,
                    remetente_documento = source.remetente_documento,
                    destinatario_documento = source.destinatario_documento,
                    filial_cnpj = source.filial_cnpj,
                    cte_issued_at = source.cte_issued_at,
                    cubages_cubed_weight = source.cubages_cubed_weight,
                    freight_weight_subtotal = source.freight_weight_subtotal,
                    ad_valorem_subtotal = source.ad_valorem_subtotal,
                    toll_subtotal = source.toll_subtotal,
                    itr_subtotal = source.itr_subtotal,
                    fiscal_cst_type = source.fiscal_cst_type,
                    fiscal_cfop_code = source.fiscal_cfop_code,
                    fiscal_tax_value = source.fiscal_tax_value,
                    fiscal_pis_value = source.fiscal_pis_value,
                    fiscal_cofins_value = source.fiscal_cofins_value,
                    filial_apelido = source.filial_apelido,
                    cte_id = source.cte_id,
                    cte_emission_type = source.cte_emission_type,
                    cte_created_at = source.cte_created_at,
                    fiscal_calculation_basis = source.fiscal_calculation_basis,
                    fiscal_tax_rate = source.fiscal_tax_rate,
                    fiscal_pis_rate = source.fiscal_pis_rate,
                    fiscal_cofins_rate = source.fiscal_cofins_rate,
                    fiscal_has_difal = source.fiscal_has_difal,
                    fiscal_difal_origin = source.fiscal_difal_origin,
                    fiscal_difal_destination = source.fiscal_difal_destination,
                    metadata = source.metadata,
                    data_extracao = source.data_extracao
            WHEN NOT MATCHED THEN
                INSERT (id, servico_em, criado_em, status, cortesia, modal, tipo_frete, valor_total, valor_notas, peso_notas, id_corporacao, id_cidade_destino, data_previsao_entrega, service_date,
                        finished_at, fit_dpn_performance_finished_at, corporation_sequence_number,
                        pagador_id, pagador_nome, remetente_id, remetente_nome, origem_cidade, origem_uf, destinatario_id, destinatario_nome, destino_cidade, destino_uf,
                           filial_nome, numero_nota_fiscal, tabela_preco_nome, classificacao_nome, centro_custo_nome, usuario_nome, reference_number, chave_cte, numero_cte, serie_cte, invoices_total_volumes,
                           taxed_weight, real_weight, total_cubic_volume, subtotal,
                        accounting_credit_id, accounting_credit_installment_id,
                        service_type, insurance_enabled, gris_subtotal, tde_subtotal, modal_cte, redispatch_subtotal, suframa_subtotal, payment_type, previous_document_type,
                        products_value, trt_subtotal, nfse_series, nfse_number, insurance_id, other_fees, km, payment_accountable_type, insured_value, globalized, sec_cat_subtotal, globalized_type, price_table_accountable_type, insurance_accountable_type,
                        pagador_documento, remetente_documento, destinatario_documento, filial_cnpj, cte_issued_at, cubages_cubed_weight, freight_weight_subtotal, ad_valorem_subtotal, toll_subtotal, itr_subtotal,
                        fiscal_cst_type, fiscal_cfop_code, fiscal_tax_value, fiscal_pis_value, fiscal_cofins_value,
                        filial_apelido, cte_id, cte_emission_type, cte_created_at,
                        fiscal_calculation_basis, fiscal_tax_rate, fiscal_pis_rate, fiscal_cofins_rate, fiscal_has_difal, fiscal_difal_origin, fiscal_difal_destination,
                        metadata, data_extracao)
                VALUES (source.id, source.servico_em, source.criado_em, source.status, source.cortesia, source.modal, source.tipo_frete, source.valor_total, source.valor_notas, source.peso_notas, source.id_corporacao, source.id_cidade_destino, source.data_previsao_entrega, source.service_date,
                        source.finished_at, source.fit_dpn_performance_finished_at, source.corporation_sequence_number,
                        source.pagador_id, source.pagador_nome, source.remetente_id, source.remetente_nome, source.origem_cidade, source.origem_uf, source.destinatario_id, source.destinatario_nome, source.destino_cidade, source.destino_uf,
                        source.filial_nome, source.numero_nota_fiscal, source.tabela_preco_nome, source.classificacao_nome, source.centro_custo_nome, source.usuario_nome, source.reference_number, source.chave_cte, source.numero_cte, source.serie_cte, source.invoices_total_volumes,
                        source.taxed_weight, source.real_weight, source.total_cubic_volume, source.subtotal,
                        source.accounting_credit_id, source.accounting_credit_installment_id,
                        source.service_type, source.insurance_enabled, source.gris_subtotal, source.tde_subtotal, source.modal_cte, source.redispatch_subtotal, source.suframa_subtotal, source.payment_type, source.previous_document_type,
                        source.products_value, source.trt_subtotal, source.nfse_series, source.nfse_number, source.insurance_id, source.other_fees, source.km, source.payment_accountable_type, source.insured_value, source.globalized, source.sec_cat_subtotal, source.globalized_type, source.price_table_accountable_type, source.insurance_accountable_type,
                        source.pagador_documento, source.remetente_documento, source.destinatario_documento, source.filial_cnpj, source.cte_issued_at, source.cubages_cubed_weight, source.freight_weight_subtotal, source.ad_valorem_subtotal, source.toll_subtotal, source.itr_subtotal,
                        source.fiscal_cst_type, source.fiscal_cfop_code, source.fiscal_tax_value, source.fiscal_pis_value, source.fiscal_cofins_value,
                        source.filial_apelido, source.cte_id, source.cte_emission_type, source.cte_created_at,
                        source.fiscal_calculation_basis, source.fiscal_tax_rate, source.fiscal_pis_rate, source.fiscal_cofins_rate, source.fiscal_has_difal, source.fiscal_difal_origin, source.fiscal_difal_destination,
                        source.metadata, source.data_extracao);
            """, tabelaAlvo, freshnessGuard);

        try (PreparedStatement statement = conexao.prepareStatement(sql)) {
            // Define os parÃƒÂ¢metros de forma segura e na ordem correta.
            int paramIndex = 1;
            statement.setObject(paramIndex++, frete.getId(), Types.BIGINT);
            statement.setObject(paramIndex++, frete.getServicoEm(), Types.TIMESTAMP_WITH_TIMEZONE);
            statement.setObject(paramIndex++, frete.getCriadoEm(), Types.TIMESTAMP_WITH_TIMEZONE);
            statement.setString(paramIndex++, frete.getStatus());
            if (frete.getCortesia() != null) {
                statement.setObject(paramIndex++, frete.getCortesia(), Types.BOOLEAN);
            } else {
                statement.setNull(paramIndex++, Types.BOOLEAN);
            }
            statement.setString(paramIndex++, frete.getModal());
            statement.setString(paramIndex++, frete.getTipoFrete());
            statement.setBigDecimal(paramIndex++, frete.getValorTotal());
            statement.setBigDecimal(paramIndex++, frete.getValorNotas());
            statement.setBigDecimal(paramIndex++, frete.getPesoNotas());
            statement.setObject(paramIndex++, frete.getIdCorporacao(), Types.BIGINT);
            statement.setObject(paramIndex++, frete.getIdCidadeDestino(), Types.BIGINT);
            statement.setObject(paramIndex++, frete.getDataPrevisaoEntrega(), Types.DATE);
            statement.setObject(paramIndex++, frete.getServiceDate(), Types.DATE);
            statement.setObject(paramIndex++, frete.getFinishedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            statement.setObject(paramIndex++, frete.getFitDpnPerformanceFinishedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            statement.setObject(paramIndex++, frete.getCorporationSequenceNumber(), Types.BIGINT);
            // Campos expandidos (22 campos do CSV)
            statement.setObject(paramIndex++, frete.getPagadorId(), Types.BIGINT);
            statement.setString(paramIndex++, frete.getPagadorNome());
            statement.setObject(paramIndex++, frete.getRemetenteId(), Types.BIGINT);
            statement.setString(paramIndex++, frete.getRemetenteNome());
            statement.setString(paramIndex++, frete.getOrigemCidade());
            statement.setString(paramIndex++, frete.getOrigemUf());
            statement.setObject(paramIndex++, frete.getDestinatarioId(), Types.BIGINT);
            statement.setString(paramIndex++, frete.getDestinatarioNome());
            statement.setString(paramIndex++, frete.getDestinoCidade());
            statement.setString(paramIndex++, frete.getDestinoUf());
            statement.setString(paramIndex++, frete.getFilialNome());
            statement.setString(paramIndex++, frete.getNumeroNotaFiscal());
            statement.setString(paramIndex++, frete.getTabelaPrecoNome());
            statement.setString(paramIndex++, frete.getClassificacaoNome());
            statement.setString(paramIndex++, frete.getCentroCustoNome());
            statement.setString(paramIndex++, frete.getUsuarioNome());
            statement.setString(paramIndex++, frete.getReferenceNumber());
            statement.setString(paramIndex++, frete.getChaveCte());
            if (frete.getNumeroCte() != null) {
                statement.setObject(paramIndex++, frete.getNumeroCte(), Types.INTEGER);
            } else {
                statement.setNull(paramIndex++, Types.INTEGER);
            }
            if (frete.getSerieCte() != null) {
                statement.setObject(paramIndex++, frete.getSerieCte(), Types.INTEGER);
            } else {
                statement.setNull(paramIndex++, Types.INTEGER);
            }
            statement.setObject(paramIndex++, frete.getInvoicesTotalVolumes(), Types.INTEGER);
            statement.setBigDecimal(paramIndex++, frete.getTaxedWeight());
            statement.setBigDecimal(paramIndex++, frete.getRealWeight());
            statement.setBigDecimal(paramIndex++, frete.getTotalCubicVolume());
            statement.setBigDecimal(paramIndex++, frete.getSubtotal());
            if (frete.getAccountingCreditId() != null) { statement.setObject(paramIndex++, frete.getAccountingCreditId(), Types.BIGINT); } else { statement.setNull(paramIndex++, Types.BIGINT); }
            if (frete.getAccountingCreditInstallmentId() != null) { statement.setObject(paramIndex++, frete.getAccountingCreditInstallmentId(), Types.BIGINT); } else { statement.setNull(paramIndex++, Types.BIGINT); }
            if (frete.getServiceType() != null) {
                statement.setObject(paramIndex++, frete.getServiceType(), Types.INTEGER);
            } else { statement.setNull(paramIndex++, Types.INTEGER); }
            if (frete.getInsuranceEnabled() != null) {
                statement.setObject(paramIndex++, frete.getInsuranceEnabled(), Types.BOOLEAN);
            } else { statement.setNull(paramIndex++, Types.BOOLEAN); }
            statement.setBigDecimal(paramIndex++, frete.getGrisSubtotal());
            statement.setBigDecimal(paramIndex++, frete.getTdeSubtotal());
            statement.setString(paramIndex++, frete.getModalCte());
            statement.setBigDecimal(paramIndex++, frete.getRedispatchSubtotal());
            statement.setBigDecimal(paramIndex++, frete.getSuframaSubtotal());
            statement.setString(paramIndex++, frete.getPaymentType());
            statement.setString(paramIndex++, frete.getPreviousDocumentType());
            statement.setBigDecimal(paramIndex++, frete.getProductsValue());
            statement.setBigDecimal(paramIndex++, frete.getTrtSubtotal());
            statement.setString(paramIndex++, frete.getNfseSeries());
            if (frete.getNfseNumber() != null) { statement.setObject(paramIndex++, frete.getNfseNumber(), Types.INTEGER); } else { statement.setNull(paramIndex++, Types.INTEGER); }
            if (frete.getInsuranceId() != null) { statement.setObject(paramIndex++, frete.getInsuranceId(), Types.BIGINT); } else { statement.setNull(paramIndex++, Types.BIGINT); }
            statement.setBigDecimal(paramIndex++, frete.getOtherFees());
            statement.setBigDecimal(paramIndex++, frete.getKm());
            if (frete.getPaymentAccountableType() != null) { statement.setObject(paramIndex++, frete.getPaymentAccountableType(), Types.INTEGER); } else { statement.setNull(paramIndex++, Types.INTEGER); }
            statement.setBigDecimal(paramIndex++, frete.getInsuredValue());
            if (frete.getGlobalized() != null) { statement.setObject(paramIndex++, frete.getGlobalized(), Types.BOOLEAN); } else { statement.setNull(paramIndex++, Types.BOOLEAN); }
            statement.setBigDecimal(paramIndex++, frete.getSecCatSubtotal());
            statement.setString(paramIndex++, frete.getGlobalizedType());
            if (frete.getPriceTableAccountableType() != null) { statement.setObject(paramIndex++, frete.getPriceTableAccountableType(), Types.INTEGER); } else { statement.setNull(paramIndex++, Types.INTEGER); }
            if (frete.getInsuranceAccountableType() != null) { statement.setObject(paramIndex++, frete.getInsuranceAccountableType(), Types.INTEGER); } else { statement.setNull(paramIndex++, Types.INTEGER); }
            statement.setString(paramIndex++, frete.getPagadorDocumento());
            statement.setString(paramIndex++, frete.getRemetenteDocumento());
            statement.setString(paramIndex++, frete.getDestinatarioDocumento());
            statement.setString(paramIndex++, frete.getFilialCnpj());
            statement.setObject(paramIndex++, frete.getCteIssuedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            statement.setBigDecimal(paramIndex++, frete.getCubagesCubedWeight());
            statement.setBigDecimal(paramIndex++, frete.getFreightWeightSubtotal());
            statement.setBigDecimal(paramIndex++, frete.getAdValoremSubtotal());
            statement.setBigDecimal(paramIndex++, frete.getTollSubtotal());
            statement.setBigDecimal(paramIndex++, frete.getItrSubtotal());
            statement.setString(paramIndex++, frete.getFiscalCstType());
            statement.setString(paramIndex++, frete.getFiscalCfopCode());
            statement.setBigDecimal(paramIndex++, frete.getFiscalTaxValue());
            statement.setBigDecimal(paramIndex++, frete.getFiscalPisValue());
            statement.setBigDecimal(paramIndex++, frete.getFiscalCofinsValue());
            statement.setString(paramIndex++, frete.getFilialApelido());
            if (frete.getCteId() != null) { statement.setObject(paramIndex++, frete.getCteId(), Types.BIGINT); } else { statement.setNull(paramIndex++, Types.BIGINT); }
            statement.setString(paramIndex++, frete.getCteEmissionType());
            statement.setObject(paramIndex++, frete.getCteCreatedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            statement.setBigDecimal(paramIndex++, frete.getFiscalCalculationBasis());
            statement.setBigDecimal(paramIndex++, frete.getFiscalTaxRate());
            statement.setBigDecimal(paramIndex++, frete.getFiscalPisRate());
            statement.setBigDecimal(paramIndex++, frete.getFiscalCofinsRate());
            if (frete.getFiscalHasDifal() != null) { statement.setObject(paramIndex++, frete.getFiscalHasDifal(), Types.BOOLEAN); } else { statement.setNull(paramIndex++, Types.BOOLEAN); }
            statement.setBigDecimal(paramIndex++, frete.getFiscalDifalOrigin());
            statement.setBigDecimal(paramIndex++, frete.getFiscalDifalDestination());
            statement.setString(paramIndex++, frete.getMetadata());
            setInstantParameter(statement, paramIndex++, Instant.now());
            final int rowsAffected = statement.executeUpdate();
            logger.debug("MERGE executado para Frete ID {}: {} linha(s) afetada(s)", frete.getId(), rowsAffected);
            return rowsAffected;
        }
    }
}
