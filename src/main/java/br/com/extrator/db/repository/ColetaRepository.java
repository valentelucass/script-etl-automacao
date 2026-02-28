/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/repository/ColetaRepository.java
Classe  : ColetaRepository (class)
Pacote  : br.com.extrator.db.repository
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

package br.com.extrator.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.ColetaEntity;
import br.com.extrator.util.validacao.ConstantesEntidades;

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

        final String sql = String.format("""
            MERGE dbo.%s AS target
            USING (
                SELECT
                    ? AS id, ? AS sequence_code, ? AS request_date, ? AS service_date, ? AS status, ? AS total_value, ? AS total_weight, ? AS total_volumes,
                    ? AS cliente_nome, ? AS cliente_doc, ? AS local_coleta, ? AS numero_coleta, ? AS complemento_coleta, ? AS cidade_coleta, ? AS bairro_coleta, ? AS uf_coleta, ? AS cep_coleta, ? AS filial_id, ? AS filial_nome, ? AS usuario_nome,
                    ? AS finish_date, ? AS manifest_item_pick_id, ? AS vehicle_type_id,
                    ? AS cancellation_reason, ? AS cancellation_user_id,
                    ? AS destroy_reason, ? AS destroy_user_id, ? AS status_updated_at,
                    ? AS taxed_weight, ? AS pick_region, ? AS last_occurrence, ? AS acao_ocorrencia, ? AS numero_tentativas,
                    ? AS metadata, ? AS data_extracao
            ) AS source
            ON target.id = source.id
            WHEN MATCHED THEN
                UPDATE SET
                    sequence_code = source.sequence_code,
                    request_date = source.request_date,
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
                    vehicle_type_id = source.vehicle_type_id,
                    cancellation_reason = source.cancellation_reason,
                    cancellation_user_id = source.cancellation_user_id,
                    destroy_reason = source.destroy_reason,
                    destroy_user_id = source.destroy_user_id,
                    status_updated_at = source.status_updated_at,
                    taxed_weight = source.taxed_weight,
                    pick_region = source.pick_region,
                    last_occurrence = source.last_occurrence,
                    acao_ocorrencia = source.acao_ocorrencia,
                    numero_tentativas = source.numero_tentativas,
                    metadata = source.metadata,
                    data_extracao = source.data_extracao
            WHEN NOT MATCHED THEN
                INSERT (
                    id, sequence_code, request_date, service_date, status, total_value, total_weight, total_volumes,
                    cliente_nome, cliente_doc, local_coleta, numero_coleta, complemento_coleta, cidade_coleta, bairro_coleta, uf_coleta, cep_coleta, filial_id, filial_nome, usuario_nome,
                    finish_date, manifest_item_pick_id, vehicle_type_id,
                    cancellation_reason, cancellation_user_id,
                    destroy_reason, destroy_user_id, status_updated_at,
                    taxed_weight, pick_region, last_occurrence, acao_ocorrencia, numero_tentativas,
                    metadata, data_extracao
                )
                VALUES (
                    source.id, source.sequence_code, source.request_date, source.service_date, source.status, source.total_value, source.total_weight, source.total_volumes,
                    source.cliente_nome, source.cliente_doc, source.local_coleta, source.numero_coleta, source.complemento_coleta, source.cidade_coleta, source.bairro_coleta, source.uf_coleta, source.cep_coleta, source.filial_id, source.filial_nome, source.usuario_nome,
                    source.finish_date, source.manifest_item_pick_id, source.vehicle_type_id,
                    source.cancellation_reason, source.cancellation_user_id,
                    source.destroy_reason, source.destroy_user_id, source.status_updated_at,
                    source.taxed_weight, source.pick_region, source.last_occurrence, source.acao_ocorrencia, source.numero_tentativas,
                    source.metadata, source.data_extracao
                );
            """, NOME_TABELA);

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
                expectedCount = (metaCount > 0 ? metaCount : 35);
                logger.debug("MERGE de Coletas preparado: {} parâmetro(s) esperado(s)", expectedCount);
            } catch (final SQLException pmEx) {
                logger.debug("Não foi possível obter ParameterMetaData: {}", pmEx.getMessage());
                expectedCount = 35;
            }
            // Define os parâmetros de forma segura e na ordem correta.
            int paramIndex = 1;
            statement.setString(paramIndex++, coleta.getId());
            statement.setObject(paramIndex++, coleta.getSequenceCode(), Types.BIGINT);
            setDateParameter(statement, paramIndex++, coleta.getRequestDate());
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
            statement.setObject(paramIndex++, coleta.getVehicleTypeId(), Types.BIGINT);
            statement.setString(paramIndex++, coleta.getCancellationReason());
            statement.setObject(paramIndex++, coleta.getCancellationUserId(), Types.BIGINT);
            statement.setString(paramIndex++, coleta.getDestroyReason());
            statement.setObject(paramIndex++, coleta.getDestroyUserId(), Types.BIGINT);
            statement.setString(paramIndex++, coleta.getStatusUpdatedAt());
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
            logger.debug("MERGE executado para Coleta ID {}: {} linha(s) afetada(s)", coleta.getId(), rowsAffected);
            return rowsAffected;
        }
    }
}
