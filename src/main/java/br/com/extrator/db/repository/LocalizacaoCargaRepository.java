/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/repository/LocalizacaoCargaRepository.java
Classe  : LocalizacaoCargaRepository (class)
Pacote  : br.com.extrator.db.repository
Modulo  : Repositorio de dados
Papel   : Implementa responsabilidade de localizacao carga repository.

Conecta com:
- LocalizacaoCargaEntity (db.entity)
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

import br.com.extrator.db.entity.LocalizacaoCargaEntity;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Repositório para operações de persistência da entidade LocalizacaoCargaEntity.
 * Implementa a arquitetura de persistência híbrida: colunas-chave para indexação
 * e uma coluna de metadados para resiliência e completude dos dados.
 * Utiliza operações MERGE (UPSERT) com a chave de negócio (sequenceNumber).
 */
public class LocalizacaoCargaRepository extends AbstractRepository<LocalizacaoCargaEntity> {
    private static final Logger logger = LoggerFactory.getLogger(LocalizacaoCargaRepository.class);
    private static final String NOME_TABELA = ConstantesEntidades.LOCALIZACAO_CARGAS;

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }

    /**
     * Executa a operação MERGE (UPSERT) para inserir ou atualizar um registro de localização no banco.
     * A lógica é segura e baseada na nova arquitetura de Entidade.
     */
    @Override
    protected int executarMerge(final Connection conexao, final LocalizacaoCargaEntity carga) throws SQLException {
        // Para Localização de Cargas, o 'sequence_number' é a chave de negócio primária.
        if (carga.getSequenceNumber() == null) {
            throw new SQLException("Não é possível executar o MERGE para Localização de Carga sem um 'sequence_number'.");
        }

        final String sql = String.format("""
            MERGE %s AS target
            USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?))
                AS source (sequence_number, type, service_at, invoices_volumes, taxed_weight, invoices_value, total_value, service_type, branch_nickname, predicted_delivery_at, destination_location_name, destination_branch_nickname, classification, status, status_branch_nickname, origin_location_name, origin_branch_nickname, fit_fln_cln_nickname, metadata, data_extracao)
            ON target.sequence_number = source.sequence_number
            WHEN MATCHED THEN
                UPDATE SET
                    type = source.type,
                    service_at = source.service_at,
                    invoices_volumes = source.invoices_volumes,
                    taxed_weight = source.taxed_weight,
                    invoices_value = source.invoices_value,
                    total_value = source.total_value,
                    service_type = source.service_type,
                    branch_nickname = source.branch_nickname,
                    predicted_delivery_at = source.predicted_delivery_at,
                    destination_location_name = source.destination_location_name,
                    destination_branch_nickname = source.destination_branch_nickname,
                    classification = source.classification,
                    status = source.status,
                    status_branch_nickname = source.status_branch_nickname,
                    origin_location_name = source.origin_location_name,
                    origin_branch_nickname = source.origin_branch_nickname,
                    fit_fln_cln_nickname = source.fit_fln_cln_nickname,
                    metadata = source.metadata,
                    data_extracao = source.data_extracao
            WHEN NOT MATCHED THEN
                INSERT (sequence_number, type, service_at, invoices_volumes, taxed_weight, invoices_value, total_value, service_type, branch_nickname, predicted_delivery_at, destination_location_name, destination_branch_nickname, classification, status, status_branch_nickname, origin_location_name, origin_branch_nickname, fit_fln_cln_nickname, metadata, data_extracao)
                VALUES (source.sequence_number, source.type, source.service_at, source.invoices_volumes, source.taxed_weight, source.invoices_value, source.total_value, source.service_type, source.branch_nickname, source.predicted_delivery_at, source.destination_location_name, source.destination_branch_nickname, source.classification, source.status, source.status_branch_nickname, source.origin_location_name, source.origin_branch_nickname, source.fit_fln_cln_nickname, source.metadata, source.data_extracao);
            """, NOME_TABELA);

        try (PreparedStatement statement = conexao.prepareStatement(sql)) {
            // Define os parâmetros de forma segura e na ordem correta conforme MERGE SQL
            int paramIndex = 1;
            setLongParameter(statement, paramIndex++, carga.getSequenceNumber());
            setStringParameter(statement, paramIndex++, carga.getType());
            // Usar helper methods para tipos especiais
            if (carga.getServiceAt() != null) {
                statement.setObject(paramIndex++, carga.getServiceAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setNull(paramIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            setIntegerParameter(statement, paramIndex++, carga.getInvoicesVolumes());
            setStringParameter(statement, paramIndex++, carga.getTaxedWeight());
            setStringParameter(statement, paramIndex++, carga.getInvoicesValue());
            setBigDecimalParameter(statement, paramIndex++, carga.getTotalValue());
            setStringParameter(statement, paramIndex++, carga.getServiceType());
            setStringParameter(statement, paramIndex++, carga.getBranchNickname());
            if (carga.getPredictedDeliveryAt() != null) {
                statement.setObject(paramIndex++, carga.getPredictedDeliveryAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setNull(paramIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            setStringParameter(statement, paramIndex++, carga.getDestinationLocationName());
            setStringParameter(statement, paramIndex++, carga.getDestinationBranchNickname());
            setStringParameter(statement, paramIndex++, carga.getClassification());
            setStringParameter(statement, paramIndex++, carga.getStatus());
            setStringParameter(statement, paramIndex++, carga.getStatusBranchNickname());
            setStringParameter(statement, paramIndex++, carga.getOriginLocationName());
            setStringParameter(statement, paramIndex++, carga.getOriginBranchNickname());
            setStringParameter(statement, paramIndex++, carga.getFitFlnClnNickname());
            setStringParameter(statement, paramIndex++, carga.getMetadata());
            setInstantParameter(statement, paramIndex++, Instant.now()); // UTC timestamp
            
            // Verificar se todos os parâmetros foram definidos (20 parâmetros = paramIndex final = 21)
            if (paramIndex != 21) {
                throw new SQLException(String.format("Número incorreto de parâmetros: esperado 20, definido %d", paramIndex - 1));
            }

            final int rowsAffected = statement.executeUpdate();
            logger.debug("MERGE executado para Localização de Carga sequence_number {}: {} linha(s) afetada(s)", carga.getSequenceNumber(), rowsAffected);
            return rowsAffected;
        }
    }
}
