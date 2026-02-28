/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/repository/CotacaoRepository.java
Classe  : CotacaoRepository (class)
Pacote  : br.com.extrator.db.repository
Modulo  : Repositorio de dados
Papel   : Implementa responsabilidade de cotacao repository.

Conecta com:
- CotacaoEntity (db.entity)
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

import br.com.extrator.db.entity.CotacaoEntity;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Repositório para operações de persistência da entidade CotacaoEntity.
 * Implementa a arquitetura de persistência híbrida: colunas-chave para
 * indexação
 * e uma coluna de metadados para resiliência e completude dos dados.
 * Utiliza operações MERGE (UPSERT) com a chave de negócio (sequenceCode).
 */
public class CotacaoRepository extends AbstractRepository<CotacaoEntity> {
    private static final Logger logger = LoggerFactory.getLogger(CotacaoRepository.class);
    private static final String NOME_TABELA = ConstantesEntidades.COTACOES;

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }

    /**
     * Executa a operação MERGE (UPSERT) para inserir ou atualizar uma cotação no
     * banco.
     * A lógica é segura e baseada na nova arquitetura de Entidade.
     */
    @Override
    protected int executarMerge(final Connection conexao, final CotacaoEntity cotacao) throws SQLException {
        // Para Cotações, o 'sequence_code' é a chave de negócio primária.
        if (cotacao.getSequenceCode() == null) {
            throw new SQLException("Não é possível executar o MERGE para Cotação sem um 'sequence_code'.");
        }

        final String sql = String.format(
                """
                        MERGE %s AS target
                        USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?))
                            AS source (
                                sequence_code, requested_at, operation_type, customer_doc, customer_name,
                                origin_city, origin_state, destination_city, destination_state, price_table,
                                volumes, taxed_weight, invoices_value, total_value, user_name, branch_nickname,
                                company_name, requester_name, real_weight, origin_postal_code, destination_postal_code,
                                customer_nickname, sender_document, sender_nickname, receiver_document, receiver_nickname,
                                disapprove_comments, freight_comments, discount_subtotal, itr_subtotal, tde_subtotal,
                                collect_subtotal, delivery_subtotal, other_fees, cte_issued_at, nfse_issued_at,
                                metadata, data_extracao
                            )
                        ON target.sequence_code = source.sequence_code
                        WHEN MATCHED THEN
                            UPDATE SET
                                requested_at = source.requested_at,
                                operation_type = source.operation_type,
                                customer_doc = source.customer_doc,
                                customer_name = source.customer_name,
                                origin_city = source.origin_city,
                                origin_state = source.origin_state,
                                destination_city = source.destination_city,
                                destination_state = source.destination_state,
                                price_table = source.price_table,
                                volumes = source.volumes,
                                taxed_weight = source.taxed_weight,
                                invoices_value = source.invoices_value,
                                total_value = source.total_value,
                                user_name = source.user_name,
                                branch_nickname = source.branch_nickname,
                                company_name = source.company_name,
                                requester_name = source.requester_name,
                                real_weight = source.real_weight,
                                origin_postal_code = source.origin_postal_code,
                                destination_postal_code = source.destination_postal_code,
                                customer_nickname = source.customer_nickname,
                                sender_document = source.sender_document,
                                sender_nickname = source.sender_nickname,
                                receiver_document = source.receiver_document,
                                receiver_nickname = source.receiver_nickname,
                                disapprove_comments = source.disapprove_comments,
                                freight_comments = source.freight_comments,
                                discount_subtotal = source.discount_subtotal,
                                itr_subtotal = source.itr_subtotal,
                                tde_subtotal = source.tde_subtotal,
                                collect_subtotal = source.collect_subtotal,
                                delivery_subtotal = source.delivery_subtotal,
                                other_fees = source.other_fees,
                                cte_issued_at = source.cte_issued_at,
                                nfse_issued_at = source.nfse_issued_at,
                                metadata = source.metadata,
                                data_extracao = source.data_extracao
                        WHEN NOT MATCHED THEN
                            INSERT (
                                sequence_code, requested_at, operation_type, customer_doc, customer_name,
                                origin_city, origin_state, destination_city, destination_state, price_table,
                                volumes, taxed_weight, invoices_value, total_value, user_name, branch_nickname,
                                company_name, requester_name, real_weight, origin_postal_code, destination_postal_code,
                                customer_nickname, sender_document, sender_nickname, receiver_document, receiver_nickname,
                                disapprove_comments, freight_comments, discount_subtotal, itr_subtotal, tde_subtotal,
                                collect_subtotal, delivery_subtotal, other_fees, cte_issued_at, nfse_issued_at,
                                metadata, data_extracao
                            )
                            VALUES (
                                source.sequence_code, source.requested_at, source.operation_type, source.customer_doc, source.customer_name,
                                source.origin_city, source.origin_state, source.destination_city, source.destination_state, source.price_table,
                                source.volumes, source.taxed_weight, source.invoices_value, source.total_value, source.user_name, source.branch_nickname,
                                source.company_name, source.requester_name, source.real_weight, source.origin_postal_code, source.destination_postal_code,
                                source.customer_nickname, source.sender_document, source.sender_nickname, source.receiver_document, source.receiver_nickname,
                                source.disapprove_comments, source.freight_comments, source.discount_subtotal, source.itr_subtotal, source.tde_subtotal,
                                source.collect_subtotal, source.delivery_subtotal, source.other_fees, source.cte_issued_at, source.nfse_issued_at,
                                source.metadata, source.data_extracao
                            );
                        """,
                NOME_TABELA);

        try (PreparedStatement statement = conexao.prepareStatement(sql)) {
            // Define os parâmetros de forma segura e na ordem correta conforme MERGE SQL
            int paramIndex = 1;
            statement.setObject(paramIndex++, cotacao.getSequenceCode(), Types.BIGINT);
            // Usar helper methods para tipos especiais
            if (cotacao.getRequestedAt() != null) {
                statement.setObject(paramIndex++, cotacao.getRequestedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setNull(paramIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            statement.setString(paramIndex++, cotacao.getOperationType());
            statement.setString(paramIndex++, cotacao.getCustomerDoc());
            statement.setString(paramIndex++, cotacao.getCustomerName());
            statement.setString(paramIndex++, cotacao.getOriginCity());
            statement.setString(paramIndex++, cotacao.getOriginState());
            statement.setString(paramIndex++, cotacao.getDestinationCity());
            statement.setString(paramIndex++, cotacao.getDestinationState());
            statement.setString(paramIndex++, cotacao.getPriceTable());
            statement.setObject(paramIndex++, cotacao.getVolumes(), Types.INTEGER);
            setBigDecimalParameter(statement, paramIndex++, cotacao.getTaxedWeight());
            setBigDecimalParameter(statement, paramIndex++, cotacao.getInvoicesValue());
            setBigDecimalParameter(statement, paramIndex++, cotacao.getTotalValue());
            statement.setString(paramIndex++, cotacao.getUserName());
            statement.setString(paramIndex++, cotacao.getBranchNickname());
            statement.setString(paramIndex++, cotacao.getCompanyName());
            statement.setString(paramIndex++, cotacao.getRequesterName());
            statement.setString(paramIndex++, cotacao.getRealWeight());
            statement.setString(paramIndex++, cotacao.getOriginPostalCode());
            statement.setString(paramIndex++, cotacao.getDestinationPostalCode());
            statement.setString(paramIndex++, cotacao.getCustomerNickname());
            statement.setString(paramIndex++, cotacao.getSenderDocument());
            statement.setString(paramIndex++, cotacao.getSenderNickname());
            statement.setString(paramIndex++, cotacao.getReceiverDocument());
            statement.setString(paramIndex++, cotacao.getReceiverNickname());
            statement.setString(paramIndex++, cotacao.getDisapproveComments());
            statement.setString(paramIndex++, cotacao.getFreightComments());
            setBigDecimalParameter(statement, paramIndex++, cotacao.getDiscountSubtotal());
            setBigDecimalParameter(statement, paramIndex++, cotacao.getItrSubtotal());
            setBigDecimalParameter(statement, paramIndex++, cotacao.getTdeSubtotal());
            setBigDecimalParameter(statement, paramIndex++, cotacao.getCollectSubtotal());
            setBigDecimalParameter(statement, paramIndex++, cotacao.getDeliverySubtotal());
            setBigDecimalParameter(statement, paramIndex++, cotacao.getOtherFees());
            if (cotacao.getCteIssuedAt() != null) {
                statement.setObject(paramIndex++, cotacao.getCteIssuedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setNull(paramIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            if (cotacao.getNfseIssuedAt() != null) {
                statement.setObject(paramIndex++, cotacao.getNfseIssuedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setNull(paramIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            statement.setString(paramIndex++, cotacao.getMetadata());
            setInstantParameter(statement, paramIndex++, Instant.now());

            if (paramIndex != 39) {
                throw new SQLException(
                        String.format("Número incorreto de parâmetros: esperado 38, definido %d", paramIndex - 1));
            }

            final int rowsAffected = statement.executeUpdate();

            // ✅ VERIFICAR rows affected
            if (rowsAffected == 0) {
                logger.error("❌ MERGE retornou 0 linhas para cotação sequence_code={}. " +
                        "Possível violação de constraint ou dados inválidos.",
                        cotacao.getSequenceCode());
                // Não lançar exceção aqui - deixar o AbstractRepository tratar
                return 0;
            }

            if (rowsAffected > 0) {
                logger.debug("✅ Cotação sequence_code={} salva com sucesso: {} linha(s) afetada(s)",
                        cotacao.getSequenceCode(), rowsAffected);
            }

            return rowsAffected;
        } catch (final SQLException e) {
            logger.error("❌ SQLException ao salvar cotação sequence_code={}: {} - SQLState: {} - ErrorCode: {}",
                    cotacao.getSequenceCode(), e.getMessage(), e.getSQLState(), e.getErrorCode(), e);
            throw e;
        }
    }
}
