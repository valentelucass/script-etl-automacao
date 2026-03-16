/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/repository/ContasAPagarRepository.java
Classe  : ContasAPagarRepository (class)
Pacote  : br.com.extrator.persistencia.repositorio
Modulo  : Repositorio de dados
Papel   : Implementa responsabilidade de contas apagar repository.

Conecta com:
- ContasAPagarDataExportEntity (db.entity)
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.persistencia.entidade.ContasAPagarDataExportEntity;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

/**
 * Repository para persistÃªncia de dados de Contas a Pagar (Data Export) no SQL Server.
 * Tabela: contas_a_pagar
 * Template ID: 8636
 *
 * âš ï¸ IMPORTANTE: A estrutura da tabela deve ser criada via scripts SQL (pasta database/).
 * Este repositÃ³rio apenas executa operaÃ§Ãµes DML (INSERT/UPDATE/MERGE).
 */
public class ContasAPagarRepository extends AbstractRepository<ContasAPagarDataExportEntity> {
    private static final Logger logger = LoggerFactory.getLogger(ContasAPagarRepository.class);
    private static final String NOME_TABELA = ConstantesEntidades.CONTAS_A_PAGAR;

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }

    @Override
    protected boolean aceitarMergeSemAlteracoesComoSucesso(final ContasAPagarDataExportEntity entity) {
        return true;
    }

    /**
     * Executa a operaÃ§Ã£o MERGE (UPSERT) para inserir ou atualizar uma conta a pagar no banco.
     * Usa sequence_code como chave de negÃ³cio.
     */
    @Override
    protected int executarMerge(final Connection conexao, final ContasAPagarDataExportEntity entity) throws SQLException {
        if (entity.getSequenceCode() == null) {
            logger.warn("Entidade com sequence_code null ignorada");
            throw new SQLException("NÃ£o Ã© possÃ­vel executar o MERGE para Contas a Pagar sem um 'sequence_code'.");
        }

        final String freshnessGuard = buildMonotonicUpdateGuard(
            "COALESCE(CAST(target.data_transacao AS datetime2), CAST(target.data_liquidacao AS datetime2), CAST(target.data_criacao AS datetime2))",
            "COALESCE(CAST(source.data_transacao AS datetime2), CAST(source.data_liquidacao AS datetime2), CAST(source.data_criacao AS datetime2))"
        );
        final String sqlMerge = """
            MERGE INTO contas_a_pagar AS target
            USING (
                SELECT
                    ? AS sequence_code,
                    ? AS document_number,
                    ? AS issue_date,
                    ? AS tipo_lancamento,
                    ? AS valor_original,
                    ? AS valor_juros,
                    ? AS valor_desconto,
                    ? AS valor_a_pagar,
                    ? AS valor_pago,
                    ? AS status_pagamento,
                    ? AS mes_competencia,
                    ? AS ano_competencia,
                    ? AS data_criacao,
                    ? AS data_liquidacao,
                    ? AS data_transacao,
                    ? AS nome_fornecedor,
                    ? AS nome_filial,
                    ? AS nome_centro_custo,
                    ? AS valor_centro_custo,
                    ? AS classificacao_contabil,
                    ? AS descricao_contabil,
                    ? AS valor_contabil,
                    ? AS area_lancamento,
                    ? AS observacoes,
                    ? AS descricao_despesa,
                    ? AS nome_usuario,
                    ? AS reconciliado,
                    ? AS metadata,
                    ? AS data_extracao
            ) AS source
            ON target.sequence_code = source.sequence_code
            WHEN MATCHED AND %s THEN
                UPDATE SET
                    document_number = source.document_number,
                    issue_date = source.issue_date,
                    tipo_lancamento = source.tipo_lancamento,
                    valor_original = source.valor_original,
                    valor_juros = source.valor_juros,
                    valor_desconto = source.valor_desconto,
                    valor_a_pagar = source.valor_a_pagar,
                    valor_pago = source.valor_pago,
                    status_pagamento = source.status_pagamento,
                    mes_competencia = source.mes_competencia,
                    ano_competencia = source.ano_competencia,
                    data_criacao = source.data_criacao,
                    data_liquidacao = source.data_liquidacao,
                    data_transacao = source.data_transacao,
                    nome_fornecedor = source.nome_fornecedor,
                    nome_filial = source.nome_filial,
                    nome_centro_custo = source.nome_centro_custo,
                    valor_centro_custo = source.valor_centro_custo,
                    classificacao_contabil = source.classificacao_contabil,
                    descricao_contabil = source.descricao_contabil,
                    valor_contabil = source.valor_contabil,
                    area_lancamento = source.area_lancamento,
                    observacoes = source.observacoes,
                    descricao_despesa = source.descricao_despesa,
                    nome_usuario = source.nome_usuario,
                    reconciliado = source.reconciliado,
                    metadata = source.metadata,
                    data_extracao = source.data_extracao
            WHEN NOT MATCHED THEN
                INSERT (
                    sequence_code, document_number, issue_date, tipo_lancamento,
                    valor_original, valor_juros, valor_desconto, valor_a_pagar, valor_pago,
                    status_pagamento, mes_competencia, ano_competencia,
                    data_criacao, data_liquidacao, data_transacao,
                    nome_fornecedor, nome_filial, nome_centro_custo, valor_centro_custo,
                    classificacao_contabil, descricao_contabil, valor_contabil, area_lancamento,
                    observacoes, descricao_despesa, nome_usuario, reconciliado,
                    metadata, data_extracao
                )
                VALUES (
                    source.sequence_code, source.document_number, source.issue_date, source.tipo_lancamento,
                    source.valor_original, source.valor_juros, source.valor_desconto, source.valor_a_pagar, source.valor_pago,
                    source.status_pagamento, source.mes_competencia, source.ano_competencia,
                    source.data_criacao, source.data_liquidacao, source.data_transacao,
                    source.nome_fornecedor, source.nome_filial, source.nome_centro_custo, source.valor_centro_custo,
                    source.classificacao_contabil, source.descricao_contabil, source.valor_contabil, source.area_lancamento,
                    source.observacoes, source.descricao_despesa, source.nome_usuario, source.reconciliado,
                    source.metadata, source.data_extracao
                );
            """.formatted(freshnessGuard);

        try (PreparedStatement ps = conexao.prepareStatement(sqlMerge)) {
            int paramIndex = 1;
            ps.setLong(paramIndex++, entity.getSequenceCode());
            setStringParameter(ps, paramIndex++, entity.getDocumentNumber());
            setDateParameter(ps, paramIndex++, entity.getIssueDate());
            setStringParameter(ps, paramIndex++, entity.getTipoLancamento());
            setBigDecimalParameter(ps, paramIndex++, entity.getValorOriginal());
            setBigDecimalParameter(ps, paramIndex++, entity.getValorJuros());
            setBigDecimalParameter(ps, paramIndex++, entity.getValorDesconto());
            setBigDecimalParameter(ps, paramIndex++, entity.getValorAPagar());
            setBigDecimalParameter(ps, paramIndex++, entity.getValorPago());
            setStringParameter(ps, paramIndex++, entity.getStatusPagamento());
            setIntegerParameter(ps, paramIndex++, entity.getMesCompetencia());
            setIntegerParameter(ps, paramIndex++, entity.getAnoCompetencia());
            setOffsetDateTimeParameter(ps, paramIndex++, entity.getDataCriacao());
            setDateParameter(ps, paramIndex++, entity.getDataLiquidacao());
            setDateParameter(ps, paramIndex++, entity.getDataTransacao());
            setStringParameter(ps, paramIndex++, entity.getNomeFornecedor());
            setStringParameter(ps, paramIndex++, entity.getNomeFilial());
            setStringParameter(ps, paramIndex++, entity.getNomeCentroCusto());
            setBigDecimalParameter(ps, paramIndex++, entity.getValorCentroCusto());
            setStringParameter(ps, paramIndex++, entity.getClassificacaoContabil());
            setStringParameter(ps, paramIndex++, entity.getDescricaoContabil());
            setBigDecimalParameter(ps, paramIndex++, entity.getValorContabil());
            setStringParameter(ps, paramIndex++, entity.getAreaLancamento());
            setStringParameter(ps, paramIndex++, entity.getObservacoes());
            setStringParameter(ps, paramIndex++, entity.getDescricaoDespesa());
            setStringParameter(ps, paramIndex++, entity.getNomeUsuario());
            setBooleanParameter(ps, paramIndex++, entity.getReconciliado());
            setStringParameter(ps, paramIndex++, entity.getMetadata());
            setDateTimeParameter(ps, paramIndex++, entity.getDataExtracao());

            final int rowsAffected = ps.executeUpdate();

            if (rowsAffected == 0) {
                logger.warn("âš ï¸ MERGE retornou 0 linhas para conta a pagar sequence_code={}", entity.getSequenceCode());
                return 0;
            }

            return rowsAffected;
        } catch (final SQLException e) {
            logger.error("âŒ SQLException ao salvar conta a pagar sequence_code={}: {}",
                entity.getSequenceCode(), e.getMessage(), e);
            throw e;
        }
    }
}
