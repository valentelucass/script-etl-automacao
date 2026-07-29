/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/repository/ColetaRepository.java
Classe  : ColetaRepository (class)
Pacote  : br.com.extrator.persistencia.repositorio
Modulo  : Repositorio de dados
Papel   : Implementa responsabilidade de coleta repository.

Conecta com:
- ColetaEntity (db.entity)
- ConstantesEntidades (util.validacao)

Fluxo geral:
1) Monta comandos SQL e parametros.
2) Executa operacoes de persistencia/consulta no banco.
3) Converte resultado para entidades de dominio.

Estrutura interna:
Metodos principais:
- getNomeTabela(): expone valor atual do estado interno.
Atributos-chave:
- logger: logger da classe para diagnostico.
- NOME_TABELA: campo de estado para "nome tabela".
[DOC-FILE-END]============================================================== */

package br.com.extrator.persistencia.repositorio;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.persistencia.entidade.ColetaEntity;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

/**
 * Repositório para operações de persistência da entidade ColetaEntity.
 * Implementa a arquitetura de persistência híbrida: colunas-chave para indexação
 * e uma coluna de metadados para resiliência e completude dos dados.
 * Utiliza operações MERGE (UPSERT) com a chave primária (id) da coleta.
 */
public class ColetaRepository extends AbstractRepository<ColetaEntity> {
    private static final Logger logger = LoggerFactory.getLogger(ColetaRepository.class);
    private static final String NOME_TABELA = ConstantesEntidades.COLETAS;

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }

    @Override
    protected boolean aceitarMergeSemAlteracoesComoSucesso(final ColetaEntity coleta) {
        return true;
    }

    /**
     * Executa a operação MERGE (UPSERT) para inserir ou atualizar uma coleta no banco.
     * A lógica é segura e baseada na nova arquitetura de Entidade.
     */
    @Override
    protected int executarMerge(final Connection conexao, final ColetaEntity coleta) throws SQLException {
        // Para Coletas, o 'id' (string) é a única chave confiável para o MERGE.
        if (coleta.getId() == null || coleta.getId().trim().isEmpty()) {
            throw new SQLException("Não é possível executar o MERGE para Coleta sem um ID.");
        }

        final String freshnessGuard = buildMonotonicUpdateGuard(
            "COALESCE(target.status_updated_at_em, TODATETIMEOFFSET(CAST(target.finish_date AS datetime2), '+00:00'), TODATETIMEOFFSET(CAST(target.service_date AS datetime2), '+00:00'), TODATETIMEOFFSET(CAST(target.request_date AS datetime2), '+00:00'))",
            "COALESCE(source.status_updated_at_em, TODATETIMEOFFSET(CAST(source.finish_date AS datetime2), '+00:00'), TODATETIMEOFFSET(CAST(source.service_date AS datetime2), '+00:00'), TODATETIMEOFFSET(CAST(source.request_date AS datetime2), '+00:00'))"
        );
        final String terminalStatusTransitionGuard = buildTerminalStatusTransitionGuard();
        final String sql = String.format("""
            MERGE dbo.%s WITH (HOLDLOCK) AS target
            USING (
                SELECT
                    ? AS id, ? AS sequence_code, ? AS request_date, ? AS request_hour, ? AS service_date, ? AS status, ? AS total_value, ? AS total_weight, ? AS total_volumes,
                    ? AS cliente_nome, ? AS cliente_doc, ? AS local_coleta, ? AS numero_coleta, ? AS complemento_coleta, ? AS cidade_coleta, ? AS bairro_coleta, ? AS uf_coleta, ? AS cep_coleta, ? AS filial_id, ? AS filial_nome, ? AS usuario_nome,
                    ? AS finish_date, ? AS manifest_item_pick_id, ? AS pick_items_ids, ? AS vehicle_type_id,
                    ? AS cancellation_reason, ? AS cancellation_user_id,
                    ? AS destroy_reason, ? AS destroy_user_id, ? AS status_updated_at, ? AS status_updated_at_em,
                    ? AS taxed_weight, ? AS pick_region, ? AS last_occurrence, ? AS acao_ocorrencia, ? AS numero_tentativas,
                    ? AS metadata, ? AS data_extracao, CAST(0 AS bit) AS excluido_na_origem,
                    CAST(NULL AS datetime2(0)) AS data_exclusao_origem
            ) AS source
            ON target.id = source.id
            WHEN MATCHED AND ((%s) OR (%s) OR target.excluido_na_origem = 1 OR target.ausente_na_origem_desde IS NOT NULL) THEN
                UPDATE SET
                    sequence_code = source.sequence_code,
                    request_date = source.request_date,
                    request_hour = source.request_hour,
                    service_date = source.service_date,
                    status = source.status,
                    total_value = source.total_value,
                    total_weight = source.total_weight,
                    total_volumes = source.total_volumes,
                    cliente_nome = source.cliente_nome,
                    cliente_doc = source.cliente_doc,
                    local_coleta = source.local_coleta,
                    numero_coleta = source.numero_coleta,
                    complemento_coleta = source.complemento_coleta,
                    cidade_coleta = source.cidade_coleta,
                    bairro_coleta = source.bairro_coleta,
                    uf_coleta = source.uf_coleta,
                    cep_coleta = source.cep_coleta,
                    filial_id = source.filial_id,
                    filial_nome = source.filial_nome,
                    usuario_nome = source.usuario_nome,
                    finish_date = source.finish_date,
                    manifest_item_pick_id = source.manifest_item_pick_id,
                    pick_items_ids = source.pick_items_ids,
                    vehicle_type_id = source.vehicle_type_id,
                    cancellation_reason = source.cancellation_reason,
                    cancellation_user_id = source.cancellation_user_id,
                    destroy_reason = source.destroy_reason,
                    destroy_user_id = source.destroy_user_id,
                    status_updated_at = source.status_updated_at,
                    status_updated_at_em = source.status_updated_at_em,
                    taxed_weight = source.taxed_weight,
                    pick_region = source.pick_region,
                    last_occurrence = source.last_occurrence,
                    acao_ocorrencia = source.acao_ocorrencia,
                    numero_tentativas = source.numero_tentativas,
                    metadata = source.metadata,
                    data_extracao = source.data_extracao,
                    excluido_na_origem = source.excluido_na_origem,
                    data_exclusao_origem = source.data_exclusao_origem,
                    ausente_na_origem_desde = NULL,
                    confirmacoes_ausencia_origem = 0,
                    ultima_reconciliacao_origem_em = NULL,
                    reconciliacao_origem_run_id = NULL,
                    motivo_exclusao_origem = NULL
            WHEN NOT MATCHED THEN
                INSERT (
                    id, sequence_code, request_date, request_hour, service_date, status, total_value, total_weight, total_volumes,
                    cliente_nome, cliente_doc, local_coleta, numero_coleta, complemento_coleta, cidade_coleta, bairro_coleta, uf_coleta, cep_coleta, filial_id, filial_nome, usuario_nome,
                    finish_date, manifest_item_pick_id, pick_items_ids, vehicle_type_id,
                    cancellation_reason, cancellation_user_id,
                    destroy_reason, destroy_user_id, status_updated_at,
                    status_updated_at_em,
                    taxed_weight, pick_region, last_occurrence, acao_ocorrencia, numero_tentativas,
                    metadata, data_extracao, excluido_na_origem, data_exclusao_origem
                )
                VALUES (
                    source.id, source.sequence_code, source.request_date, source.request_hour, source.service_date, source.status, source.total_value, source.total_weight, source.total_volumes,
                    source.cliente_nome, source.cliente_doc, source.local_coleta, source.numero_coleta, source.complemento_coleta, source.cidade_coleta, source.bairro_coleta, source.uf_coleta, source.cep_coleta, source.filial_id, source.filial_nome, source.usuario_nome,
                    source.finish_date, source.manifest_item_pick_id, source.pick_items_ids, source.vehicle_type_id,
                    source.cancellation_reason, source.cancellation_user_id,
                    source.destroy_reason, source.destroy_user_id, source.status_updated_at,
                    source.status_updated_at_em,
                    source.taxed_weight, source.pick_region, source.last_occurrence, source.acao_ocorrencia, source.numero_tentativas,
                    source.metadata, source.data_extracao, source.excluido_na_origem, source.data_exclusao_origem
                );
            """, NOME_TABELA, freshnessGuard, terminalStatusTransitionGuard);

        logger.debug("Preparando MERGE de Coleta ID {}", coleta.getId());
        PreparedStatement statement;
        try {
            statement = conexao.prepareStatement(sql);
        } catch (final SQLException e) {
            logger.error("Falha ao preparar MERGE de Coleta ID {}: {}", coleta.getId(), e.getMessage());
            throw e;
        }
        try (statement) {
            int expectedCount;
            try {
                final int metaCount = statement.getParameterMetaData().getParameterCount();
                expectedCount = (metaCount > 0 ? metaCount : 38);
                logger.debug("MERGE de Coletas preparado: {} parâmetro(s) esperado(s)", expectedCount);
            } catch (final SQLException pmEx) {
                logger.debug("Não foi possível obter ParameterMetaData: {}", pmEx.getMessage());
                expectedCount = 38;
            }
            // Define os parâmetros de forma segura e na ordem correta.
            int paramIndex = 1;
            statement.setString(paramIndex++, coleta.getId());
            statement.setObject(paramIndex++, coleta.getSequenceCode(), Types.BIGINT);
            setDateParameter(statement, paramIndex++, coleta.getRequestDate());
            statement.setString(paramIndex++, coleta.getRequestHour());
            setDateParameter(statement, paramIndex++, coleta.getServiceDate());
            statement.setString(paramIndex++, coleta.getStatus());
            setBigDecimalParameter(statement, paramIndex++, coleta.getTotalValue());
            setBigDecimalParameter(statement, paramIndex++, coleta.getTotalWeight());
            statement.setObject(paramIndex++, coleta.getTotalVolumes(), Types.INTEGER);
            // Campos expandidos (apenas os que existem na tabela)
            statement.setString(paramIndex++, coleta.getClienteNome());
            statement.setString(paramIndex++, coleta.getClienteDoc());
            statement.setString(paramIndex++, coleta.getLocalColeta());
            statement.setString(paramIndex++, coleta.getNumeroColeta());
            statement.setString(paramIndex++, coleta.getComplementoColeta());
            statement.setString(paramIndex++, coleta.getCidadeColeta());
            statement.setString(paramIndex++, coleta.getBairroColeta());
            statement.setString(paramIndex++, coleta.getUfColeta());
            statement.setString(paramIndex++, coleta.getCepColeta());
            statement.setObject(paramIndex++, coleta.getFilialId(), Types.BIGINT);
            statement.setString(paramIndex++, coleta.getFilialNome());
            statement.setString(paramIndex++, coleta.getUsuarioNome());
            setDateParameter(statement, paramIndex++, coleta.getFinishDate());
            statement.setObject(paramIndex++, coleta.getManifestItemPickId(), Types.BIGINT);
            statement.setString(paramIndex++, coleta.getPickItemsIds());
            statement.setObject(paramIndex++, coleta.getVehicleTypeId(), Types.BIGINT);
            statement.setString(paramIndex++, coleta.getCancellationReason());
            statement.setObject(paramIndex++, coleta.getCancellationUserId(), Types.BIGINT);
            statement.setString(paramIndex++, coleta.getDestroyReason());
            statement.setObject(paramIndex++, coleta.getDestroyUserId(), Types.BIGINT);
            statement.setString(paramIndex++, coleta.getStatusUpdatedAt());
            statement.setObject(paramIndex++, coleta.getStatusUpdatedAtEm(), Types.TIMESTAMP_WITH_TIMEZONE);
            setBigDecimalParameter(statement, paramIndex++, coleta.getTaxedWeight());
            statement.setString(paramIndex++, coleta.getPickRegion());
            statement.setString(paramIndex++, coleta.getLastOccurrence());
            statement.setString(paramIndex++, coleta.getAcaoOcorrencia());
            statement.setObject(paramIndex++, coleta.getNumeroTentativas(), Types.INTEGER);
            statement.setString(paramIndex++, coleta.getMetadata());
            setInstantParameter(statement, paramIndex++, Instant.now()); // UTC timestamp
            
            // Verificar se todos os parâmetros foram definidos
            if ((paramIndex - 1) != expectedCount) {
                throw new SQLException(String.format("Número incorreto de parâmetros: esperado %d, definido %d", expectedCount, (paramIndex - 1)));
            }

            final int rowsAffected = statement.executeUpdate();
            if (rowsAffected == 0) {
                return refrescarDataExtracaoNoOp(conexao, coleta);
            }
            logger.debug("MERGE executado para Coleta ID {}: {} linha(s) afetada(s)", coleta.getId(), rowsAffected);
            return rowsAffected;
        }
    }

    /**
     * A API ESL pode publicar a conclusao/cancelamento com uma data de evento anterior
     * ao timestamp salvo para o estado pendente. Nessa situacao, a guarda monotônica
     * temporal isolada impediria uma transicao de negocio valida. Um estado terminal
     * sempre supera um estado ainda aberto, mas nunca regride um terminal ja gravado.
     */
    private static String buildTerminalStatusTransitionGuard() {
        return """
            (
                EXISTS (
                    SELECT 1
                      FROM dbo.dim_status_coleta status_origem
                     WHERE status_origem.codigo_status = LOWER(LTRIM(RTRIM(COALESCE(source.status, N''))))
                       AND status_origem.estado_terminal = 1
                       AND status_origem.ativo = 1
                )
                AND NOT EXISTS (
                    SELECT 1
                      FROM dbo.dim_status_coleta status_destino
                     WHERE status_destino.codigo_status = LOWER(LTRIM(RTRIM(COALESCE(target.status, N''))))
                       AND status_destino.estado_terminal = 1
                       AND status_destino.ativo = 1
                )
            )
            """;
    }

    private int refrescarDataExtracaoNoOp(final Connection conexao, final ColetaEntity coleta) throws SQLException {
        final String sql = """
            UPDATE dbo.coletas
               SET data_extracao = ?
             WHERE id = ?
               AND ? IS NOT NULL
               AND (data_extracao IS NULL OR data_extracao < ?)
            """;
        try (PreparedStatement statement = conexao.prepareStatement(sql)) {
            final Instant agora = Instant.now();
            setInstantParameter(statement, 1, agora);
            statement.setString(2, coleta.getId());
            setInstantParameter(statement, 3, agora);
            setInstantParameter(statement, 4, agora);
            return statement.executeUpdate();
        }
    }
}
