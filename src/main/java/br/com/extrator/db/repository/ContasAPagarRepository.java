/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/repository/ContasAPagarRepository.java
Classe  : ContasAPagarRepository (class)
Pacote  : br.com.extrator.db.repository
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

package br.com.extrator.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.ContasAPagarDataExportEntity;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Repository para persistência de dados de Contas a Pagar (Data Export) no SQL Server.
 * Tabela: contas_a_pagar
 * Template ID: 8636
 * 
 * ⚠️ IMPORTANTE: A estrutura da tabela deve ser criada via scripts SQL (pasta database/).
 * Este repositório apenas executa operações DML (INSERT/UPDATE/MERGE).
 */
public class ContasAPagarRepository extends AbstractRepository<ContasAPagarDataExportEntity> {
    private static final Logger logger = LoggerFactory.getLogger(ContasAPagarRepository.class);
    private static final String NOME_TABELA = ConstantesEntidades.CONTAS_A_PAGAR;

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }

    /**
     * Executa a operação MERGE (UPSERT) para inserir ou atualizar uma conta a pagar no banco.
     * Usa sequence_code como chave de negócio.
     */
    @Override
    protected int executarMerge(final Connection conexao, final ContasAPagarDataExportEntity entity) throws SQLException {
        if (entity.getSequenceCode() == null) {
            logger.warn("Entidade com sequence_code null ignorada");
            throw new SQLException("Não é possível executar o MERGE para Contas a Pagar sem um 'sequence_code'.");
        }

        final String sqlMerge = String.join("\n",
            "MERGE INTO contas_a_pagar AS target",
            "USING (SELECT ? AS sequence_code) AS source",
            "ON target.sequence_code = source.sequence_code",
            "WHEN MATCHED THEN",
            "    UPDATE SET",
            "        document_number = ?,",
            "        issue_date = ?,",
            "        tipo_lancamento = ?,",
            "        valor_original = ?,",
            "        valor_juros = ?,",
            "        valor_desconto = ?,",
            "        valor_a_pagar = ?,",
            "        valor_pago = ?,",
            "        status_pagamento = ?,",
            "        mes_competencia = ?,",
            "        ano_competencia = ?,",
            "        data_criacao = ?,",
            "        data_liquidacao = ?,",
            "        data_transacao = ?,",
            "        nome_fornecedor = ?,",
            "        nome_filial = ?,",
            "        nome_centro_custo = ?,",
            "        valor_centro_custo = ?,",
            "        classificacao_contabil = ?,",
            "        descricao_contabil = ?,",
            "        valor_contabil = ?,",
            "        area_lancamento = ?,",
            "        observacoes = ?,",
            "        descricao_despesa = ?,",
            "        nome_usuario = ?,",
            "        reconciliado = ?,",
            "        metadata = ?,",
            "        data_extracao = ?",
            "WHEN NOT MATCHED THEN",
            "    INSERT (sequence_code, document_number, issue_date, tipo_lancamento,",
            "            valor_original, valor_juros, valor_desconto, valor_a_pagar, valor_pago,",
            "            status_pagamento, mes_competencia, ano_competencia,",
            "            data_criacao, data_liquidacao, data_transacao,",
            "            nome_fornecedor, nome_filial, nome_centro_custo, valor_centro_custo,",
            "            classificacao_contabil, descricao_contabil, valor_contabil, area_lancamento,",
            "            observacoes, descricao_despesa, nome_usuario, reconciliado,",
            "            metadata, data_extracao)",
            "    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);"
        );

        try (PreparedStatement ps = conexao.prepareStatement(sqlMerge)) {
            int paramIndex = 1;
            
            // Parâmetros do MERGE (ON)
            ps.setLong(paramIndex++, entity.getSequenceCode());
            
            // Parâmetros do UPDATE
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
            
            // Parâmetros do INSERT (mesmos valores)
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
                logger.warn("⚠️ MERGE retornou 0 linhas para conta a pagar sequence_code={}", entity.getSequenceCode());
                return 0;
            }
            
            return rowsAffected;
            
        } catch (final SQLException e) {
            logger.error("❌ SQLException ao salvar conta a pagar sequence_code={}: {}", 
                entity.getSequenceCode(), e.getMessage(), e);
            throw e;
        }
    }
}
