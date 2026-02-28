/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/repository/FaturaPorClienteRepository.java
Classe  : FaturaPorClienteRepository (class)
Pacote  : br.com.extrator.db.repository
Modulo  : Repositorio de dados
Papel   : Implementa responsabilidade de fatura por cliente repository.

Conecta com:
- FaturaPorClienteEntity (db.entity)
- ConstantesEntidades (util.validacao)

Fluxo geral:
1) Monta comandos SQL e parametros.
2) Executa operacoes de persistencia/consulta no banco.
3) Converte resultado para entidades de dominio.

Estrutura interna:
Metodos principais:
- getNomeTabela(): expone valor atual do estado interno.
- validarEntidade(...1 args): aplica regras de validacao e consistencia.
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

import br.com.extrator.db.entity.FaturaPorClienteEntity;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Repository para persistência de Faturas por Cliente no banco de dados.
 * Implementa operações MERGE (UPSERT).
 * 
 * @author Sistema de Extração ESL Cloud
 * @version 1.0
 */
public class FaturaPorClienteRepository extends AbstractRepository<FaturaPorClienteEntity> {
    private static final Logger logger = LoggerFactory.getLogger(FaturaPorClienteRepository.class);

    private static final String NOME_TABELA = ConstantesEntidades.FATURAS_POR_CLIENTE;

    /**
     * SQL MERGE para INSERT ou UPDATE usando unique_id como chave.
     */
    private static final String SQL_MERGE = """
        MERGE INTO faturas_por_cliente AS target
        USING (
            SELECT
                ? AS unique_id,
                ? AS valor_frete,
                ? AS valor_fatura,
                ? AS third_party_ctes_value,
                ? AS numero_cte,
                ? AS chave_cte,
                ? AS numero_nfse,
                ? AS status_cte,
                ? AS status_cte_result,
                ? AS data_emissao_cte,
                ? AS numero_fatura,
                ? AS data_emissao_fatura,
                ? AS data_vencimento_fatura,
                ? AS data_baixa_fatura,
                ? AS fit_ant_ils_original_due_date,
                ? AS fit_ant_document,
                ? AS fit_ant_issue_date,
                ? AS fit_ant_value,
                ? AS filial,
                ? AS tipo_frete,
                ? AS classificacao,
                ? AS estado,
                ? AS pagador_nome,
                ? AS pagador_documento,
                ? AS remetente_nome,
                ? AS remetente_documento,
                ? AS destinatario_nome,
                ? AS destinatario_documento,
                ? AS vendedor_nome,
                ? AS notas_fiscais,
                ? AS pedidos_cliente,
                ? AS metadata,
                ? AS data_extracao
        ) AS source
        ON target.unique_id = source.unique_id
        WHEN MATCHED THEN
            UPDATE SET
                valor_frete = source.valor_frete,
                valor_fatura = source.valor_fatura,
                third_party_ctes_value = source.third_party_ctes_value,
                numero_cte = source.numero_cte,
                chave_cte = source.chave_cte,
                numero_nfse = source.numero_nfse,
                status_cte = source.status_cte,
                status_cte_result = source.status_cte_result,
                data_emissao_cte = source.data_emissao_cte,
                numero_fatura = source.numero_fatura,
                data_emissao_fatura = source.data_emissao_fatura,
                data_vencimento_fatura = source.data_vencimento_fatura,
                data_baixa_fatura = source.data_baixa_fatura,
                fit_ant_ils_original_due_date = source.fit_ant_ils_original_due_date,
                fit_ant_document = source.fit_ant_document,
                fit_ant_issue_date = source.fit_ant_issue_date,
                fit_ant_value = source.fit_ant_value,
                filial = source.filial,
                tipo_frete = source.tipo_frete,
                classificacao = source.classificacao,
                estado = source.estado,
                pagador_nome = source.pagador_nome,
                pagador_documento = source.pagador_documento,
                remetente_nome = source.remetente_nome,
                remetente_documento = source.remetente_documento,
                destinatario_nome = source.destinatario_nome,
                destinatario_documento = source.destinatario_documento,
                vendedor_nome = source.vendedor_nome,
                notas_fiscais = source.notas_fiscais,
                pedidos_cliente = source.pedidos_cliente,
                metadata = source.metadata,
                data_extracao = source.data_extracao
        WHEN NOT MATCHED THEN
            INSERT (unique_id, valor_frete, valor_fatura, third_party_ctes_value, numero_cte, chave_cte, numero_nfse, status_cte, status_cte_result, data_emissao_cte, numero_fatura, data_emissao_fatura, data_vencimento_fatura, data_baixa_fatura, fit_ant_ils_original_due_date, fit_ant_document, fit_ant_issue_date, fit_ant_value, filial, tipo_frete, classificacao, estado, pagador_nome, pagador_documento, remetente_nome, remetente_documento, destinatario_nome, destinatario_documento, vendedor_nome, notas_fiscais, pedidos_cliente, metadata, data_extracao)
            VALUES (source.unique_id, source.valor_frete, source.valor_fatura, source.third_party_ctes_value, source.numero_cte, source.chave_cte, source.numero_nfse, source.status_cte, source.status_cte_result, source.data_emissao_cte, source.numero_fatura, source.data_emissao_fatura, source.data_vencimento_fatura, source.data_baixa_fatura, source.fit_ant_ils_original_due_date, source.fit_ant_document, source.fit_ant_issue_date, source.fit_ant_value, source.filial, source.tipo_frete, source.classificacao, source.estado, source.pagador_nome, source.pagador_documento, source.remetente_nome, source.remetente_documento, source.destinatario_nome, source.destinatario_documento, source.vendedor_nome, source.notas_fiscais, source.pedidos_cliente, source.metadata, source.data_extracao);
        """;

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }

    @Override
    protected int executarMerge(final Connection conexao, final FaturaPorClienteEntity entity) throws SQLException {
        validarEntidade(entity);

        try (PreparedStatement pstmt = conexao.prepareStatement(SQL_MERGE)) {
            int idx = 1;

            pstmt.setString(idx++, entity.getUniqueId());
            setBigDecimalParameter(pstmt, idx++, entity.getValorFrete());
            setBigDecimalParameter(pstmt, idx++, entity.getValorFatura());
            setBigDecimalParameter(pstmt, idx++, entity.getThirdPartyCtesValue());
            setLongParameter(pstmt, idx++, entity.getNumeroCte());
            pstmt.setString(idx++, entity.getChaveCte());
            setLongParameter(pstmt, idx++, entity.getNumeroNfse());
            pstmt.setString(idx++, entity.getStatusCte());
            pstmt.setString(idx++, entity.getStatusCteResult());
            if (entity.getDataEmissaoCte() != null) {
                pstmt.setObject(idx++, entity.getDataEmissaoCte(), java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                pstmt.setNull(idx++, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
            }
            pstmt.setString(idx++, entity.getNumeroFatura());
            setDateParameter(pstmt, idx++, entity.getDataEmissaoFatura());
            setDateParameter(pstmt, idx++, entity.getDataVencimentoFatura());
            setDateParameter(pstmt, idx++, entity.getDataBaixaFatura());
            setDateParameter(pstmt, idx++, entity.getFitAntOriginalDueDate());
            pstmt.setString(idx++, entity.getFitAntDocument());
            setDateParameter(pstmt, idx++, entity.getFitAntIssueDate());
            setBigDecimalParameter(pstmt, idx++, entity.getFitAntValue());
            pstmt.setString(idx++, entity.getFilial());
            pstmt.setString(idx++, entity.getTipoFrete());
            pstmt.setString(idx++, entity.getClassificacao());
            pstmt.setString(idx++, entity.getEstado());
            pstmt.setString(idx++, entity.getPagadorNome());
            pstmt.setString(idx++, entity.getPagadorDocumento());
            pstmt.setString(idx++, entity.getRemetenteNome());
            pstmt.setString(idx++, entity.getRemetenteDocumento());
            pstmt.setString(idx++, entity.getDestinatarioNome());
            pstmt.setString(idx++, entity.getDestinatarioDocumento());
            pstmt.setString(idx++, entity.getVendedorNome());
            pstmt.setString(idx++, entity.getNotasFiscais());
            pstmt.setString(idx++, entity.getPedidosCliente());
            pstmt.setString(idx++, entity.getMetadata());
            setInstantParameter(pstmt, idx++, Instant.now());

            final int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                logger.warn("MERGE não afetou nenhuma linha para unique_id: {}", entity.getUniqueId());
            }
            return rowsAffected;
        }
    }

    

    /**
     * Valida campos obrigatórios da entidade antes de salvar.
     */
    private void validarEntidade(final FaturaPorClienteEntity entity) {
        if (entity.getUniqueId() == null || entity.getUniqueId().trim().isEmpty()) {
            throw new IllegalArgumentException("unique_id não pode ser null ou vazio");
        }
        // Validações adicionais podem ser adicionadas aqui
    }

    public int enriquecerNumeroNfseViaTabelaPonte() throws SQLException {
        try (Connection conn = obterConexao();
             PreparedStatement ps = conn.prepareStatement("""
                UPDATE fpc
                SET 
                    fpc.numero_nfse = f.nfse_number,
                    fpc.serie_nfse  = f.nfse_series
                FROM dbo.faturas_por_cliente fpc
                INNER JOIN dbo.faturas_graphql fg ON fg.document = fpc.fit_ant_document
                INNER JOIN dbo.fretes f ON f.accounting_credit_id = fg.id
                WHERE f.nfse_number IS NOT NULL
            """)) {
            return ps.executeUpdate();
        }
    }

    public int enriquecerPagadorViaTabelaPonte() throws SQLException {
        try (Connection conn = obterConexao();
             PreparedStatement ps = conn.prepareStatement("""
                UPDATE fpc
                SET 
                    fpc.pagador_nome = COALESCE(fpc.pagador_nome, fg.corporation_name),
                    fpc.pagador_documento = COALESCE(fpc.pagador_documento, fg.corporation_cnpj)
                FROM dbo.faturas_por_cliente fpc
                INNER JOIN dbo.faturas_graphql fg ON fg.document = fpc.fit_ant_document
                WHERE fpc.pagador_nome IS NULL OR fpc.pagador_documento IS NULL
            """)) {
            return ps.executeUpdate();
        }
    }
}
