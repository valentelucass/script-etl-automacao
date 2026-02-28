/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/repository/FaturaGraphQLRepository.java
Classe  : FaturaGraphQLRepository (class)
Pacote  : br.com.extrator.db.repository
Modulo  : Repositorio de dados
Papel   : Implementa responsabilidade de fatura graph qlrepository.

Conecta com:
- FaturaGraphQLEntity (db.entity)
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
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.FaturaGraphQLEntity;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Repository para persistência de Faturas GraphQL no banco de dados.
 * Implementa operações MERGE (UPSERT).
 */
public class FaturaGraphQLRepository extends AbstractRepository<FaturaGraphQLEntity> {
    private static final Logger logger = LoggerFactory.getLogger(FaturaGraphQLRepository.class);
    private static final String NOME_TABELA = ConstantesEntidades.FATURAS_GRAPHQL;

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }

    /**
     * Executa a operação MERGE (UPSERT) para inserir ou atualizar uma fatura GraphQL no banco.
     * Usa id como chave primária.
     */
    @Override
    protected int executarMerge(final Connection conexao, final FaturaGraphQLEntity e) throws SQLException {
        if (e == null) {
            logger.error("❌ Tentativa de salvar FaturaGraphQLEntity NULL");
            throw new SQLException("Não é possível executar MERGE para Fatura GraphQL nula");
        }
        
        if (e.getId() == null) {
            logger.error("❌ Fatura GraphQL com id NULL");
            throw new SQLException("Não é possível executar o MERGE para Fatura GraphQL sem um 'id'.");
        }
        final String sql = """
            MERGE dbo.faturas_graphql AS target
            USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)) AS source
                  (id, document, issue_date, due_date, original_due_date, value, paid_value, value_to_pay, discount_value, interest_value, paid, status, type, comments, sequence_code, competence_month, competence_year, created_at, updated_at, corporation_id, corporation_name, corporation_cnpj, nfse_numero, carteira_banco, instrucao_boleto, banco_nome, metodo_pagamento, metadata, data_extracao)
            ON target.id = source.id
            WHEN MATCHED THEN
                UPDATE SET
                    document = source.document,
                    issue_date = source.issue_date,
                    due_date = source.due_date,
                    original_due_date = source.original_due_date,
                    value = source.value,
                    paid_value = source.paid_value,
                    value_to_pay = source.value_to_pay,
                    discount_value = source.discount_value,
                    interest_value = source.interest_value,
                    paid = source.paid,
                    status = source.status,
                    type = source.type,
                    comments = source.comments,
                    sequence_code = source.sequence_code,
                    competence_month = source.competence_month,
                    competence_year = source.competence_year,
                    created_at = source.created_at,
                    updated_at = source.updated_at,
                    corporation_id = source.corporation_id,
                    corporation_name = source.corporation_name,
                    corporation_cnpj = source.corporation_cnpj,
                    nfse_numero = source.nfse_numero,
                    carteira_banco = source.carteira_banco,
                    instrucao_boleto = source.instrucao_boleto,
                    banco_nome = source.banco_nome,
                    metodo_pagamento = source.metodo_pagamento,
                    metadata = source.metadata,
                    data_extracao = source.data_extracao
            WHEN NOT MATCHED THEN
                INSERT (id, document, issue_date, due_date, original_due_date, value, paid_value, value_to_pay, discount_value, interest_value, paid, status, type, comments, sequence_code, competence_month, competence_year, created_at, updated_at, corporation_id, corporation_name, corporation_cnpj, nfse_numero, carteira_banco, instrucao_boleto, banco_nome, metodo_pagamento, metadata, data_extracao)
                VALUES (source.id, source.document, source.issue_date, source.due_date, source.original_due_date, source.value, source.paid_value, source.value_to_pay, source.discount_value, source.interest_value, source.paid, source.status, source.type, source.comments, source.sequence_code, source.competence_month, source.competence_year, source.created_at, source.updated_at, source.corporation_id, source.corporation_name, source.corporation_cnpj, source.nfse_numero, source.carteira_banco, source.instrucao_boleto, source.banco_nome, source.metodo_pagamento, source.metadata, source.data_extracao);
        """;
        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            int idx = 1;
            setLongParameter(ps, idx++, e.getId());
            setStringParameter(ps, idx++, e.getDocument());
            setDateParameter(ps, idx++, e.getIssueDate());
            setDateParameter(ps, idx++, e.getDueDate());
            setDateParameter(ps, idx++, e.getOriginalDueDate());
            setBigDecimalParameter(ps, idx++, e.getValue());
            setBigDecimalParameter(ps, idx++, e.getPaidValue());
            setBigDecimalParameter(ps, idx++, e.getValueToPay());
            setBigDecimalParameter(ps, idx++, e.getDiscountValue());
            setBigDecimalParameter(ps, idx++, e.getInterestValue());
            setBooleanParameter(ps, idx++, e.getPaid());
            setStringParameter(ps, idx++, e.getStatus());
            setStringParameter(ps, idx++, e.getType());
            setStringParameter(ps, idx++, e.getComments());
            setIntegerParameter(ps, idx++, e.getSequenceCode());
            setIntegerParameter(ps, idx++, e.getCompetenceMonth());
            setIntegerParameter(ps, idx++, e.getCompetenceYear());
            setOffsetDateTimeParameter(ps, idx++, e.getCreatedAt());
            setOffsetDateTimeParameter(ps, idx++, e.getUpdatedAt());
            setLongParameter(ps, idx++, e.getCorporationId());
            setStringParameter(ps, idx++, e.getCorporationName());
            setStringParameter(ps, idx++, e.getCorporationCnpj());
            setStringParameter(ps, idx++, e.getNfseNumero());
            setStringParameter(ps, idx++, e.getCarteiraBanco());
            setStringParameter(ps, idx++, e.getInstrucaoBoleto());
            setStringParameter(ps, idx++, e.getBancoNome());
            setStringParameter(ps, idx++, e.getMetodoPagamento());
            setStringParameter(ps, idx++, e.getMetadata());
            setInstantParameter(ps, idx++, Instant.now());
            
            final int rowsAffected = ps.executeUpdate();
            
            if (rowsAffected == 0) {
                logger.warn("⚠️ MERGE retornou 0 linhas para fatura GraphQL id={}", e.getId());
                return 0;
            }
            
            logger.debug("✅ Fatura GraphQL id={} salva com sucesso: {} linha(s) afetada(s)", 
                e.getId(), rowsAffected);
            
            return rowsAffected;
        } catch (final SQLException ex) {
            logger.error("❌ SQLException ao salvar fatura GraphQL id={}: {} - SQLState: {} - ErrorCode: {}", 
                e.getId(), ex.getMessage(), ex.getSQLState(), ex.getErrorCode(), ex);
            throw ex;
        }
    }
}
