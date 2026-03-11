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
import java.sql.Types;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.persistencia.entidade.FreteEntity;
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
    private final FreteNfseUpdateSupport nfseUpdateSupport = new FreteNfseUpdateSupport(logger);

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
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
        // Para Fretes, o 'id' ÃƒÂ© a ÃƒÂºnica chave confiÃƒÂ¡vel para o MERGE.
        if (frete.getId() == null) {
            throw new SQLException("NÃƒÂ£o ÃƒÂ© possÃƒÂ­vel executar o MERGE para Frete sem um ID.");
        }

        final String sql = String.format("""
            MERGE %s AS target
            USING (VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
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
                AS source (id, servico_em, criado_em, status, modal, tipo_frete, valor_total, valor_notas, peso_notas, id_corporacao, id_cidade_destino, data_previsao_entrega, service_date,
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
            WHEN MATCHED THEN
                UPDATE SET
                    servico_em = source.servico_em,
                    criado_em = source.criado_em,
                    status = source.status,
                    modal = source.modal,
                    tipo_frete = source.tipo_frete,
                    valor_total = source.valor_total,
                    valor_notas = source.valor_notas,
                    peso_notas = source.peso_notas,
                    id_corporacao = source.id_corporacao,
                    id_cidade_destino = source.id_cidade_destino,
                    data_previsao_entrega = source.data_previsao_entrega,
                    service_date = source.service_date,
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
                INSERT (id, servico_em, criado_em, status, modal, tipo_frete, valor_total, valor_notas, peso_notas, id_corporacao, id_cidade_destino, data_previsao_entrega, service_date,
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
                VALUES (source.id, source.servico_em, source.criado_em, source.status, source.modal, source.tipo_frete, source.valor_total, source.valor_notas, source.peso_notas, source.id_corporacao, source.id_cidade_destino, source.data_previsao_entrega, source.service_date,
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
            """, NOME_TABELA);

        try (PreparedStatement statement = conexao.prepareStatement(sql)) {
            // Define os parÃƒÂ¢metros de forma segura e na ordem correta.
            int paramIndex = 1;
            statement.setObject(paramIndex++, frete.getId(), Types.BIGINT);
            statement.setObject(paramIndex++, frete.getServicoEm(), Types.TIMESTAMP_WITH_TIMEZONE);
            statement.setObject(paramIndex++, frete.getCriadoEm(), Types.TIMESTAMP_WITH_TIMEZONE);
            statement.setString(paramIndex++, frete.getStatus());
            statement.setString(paramIndex++, frete.getModal());
            statement.setString(paramIndex++, frete.getTipoFrete());
            statement.setBigDecimal(paramIndex++, frete.getValorTotal());
            statement.setBigDecimal(paramIndex++, frete.getValorNotas());
            statement.setBigDecimal(paramIndex++, frete.getPesoNotas());
            statement.setObject(paramIndex++, frete.getIdCorporacao(), Types.BIGINT);
            statement.setObject(paramIndex++, frete.getIdCidadeDestino(), Types.BIGINT);
            statement.setObject(paramIndex++, frete.getDataPrevisaoEntrega(), Types.DATE);
            statement.setObject(paramIndex++, frete.getServiceDate(), Types.DATE);
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
            final int expected = statement.getParameterMetaData().getParameterCount();
            if ((paramIndex - 1) != expected) {
                throw new SQLException(String.format("NÃƒÂºmero incorreto de parÃƒÂ¢metros: esperado %d, definido %d", expected, (paramIndex - 1)));
            }

            final int rowsAffected = statement.executeUpdate();
            logger.debug("MERGE executado para Frete ID {}: {} linha(s) afetada(s)", frete.getId(), rowsAffected);
            return rowsAffected;
        }
    }
}
