package br.com.extrator.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.FreteEntity;

/**
 * Repositório para operações de persistência da entidade FreteEntity.
 * Implementa a arquitetura de persistência híbrida: colunas-chave para indexação
 * e uma coluna de metadados para resiliência e completude dos dados.
 * Utiliza operações MERGE (UPSERT) com a chave primária (id) do frete.
 */
public class FreteRepository extends AbstractRepository<FreteEntity> {
    private static final Logger logger = LoggerFactory.getLogger(FreteRepository.class);
    private static final String NOME_TABELA = "fretes";
    private static boolean viewVerificada = false;

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }

    /**
     * Cria a tabela 'fretes' se ela não existir, seguindo o modelo híbrido.
     * A estrutura contém apenas colunas essenciais para busca e uma coluna NVARCHAR(MAX)
     * para armazenar o JSON completo, garantindo resiliência.
     */
    @Override
    protected void criarTabelaSeNaoExistir(final Connection conexao) throws SQLException {
        if (!verificarTabelaExiste(conexao, NOME_TABELA)) {
            logger.info("Criando tabela {} com arquitetura híbrida...", NOME_TABELA);

            final String sql = """
                CREATE TABLE fretes (
                    -- Coluna de Chave Primária
                    id BIGINT PRIMARY KEY,

                    -- Colunas Essenciais "Promovidas" para Indexação e Relatórios
                    servico_em DATETIMEOFFSET,
                    criado_em DATETIMEOFFSET,
                    status NVARCHAR(50),
                    modal NVARCHAR(50),
                    tipo_frete NVARCHAR(100),
                    valor_total DECIMAL(18, 2),
                    valor_notas DECIMAL(18, 2),
                    peso_notas DECIMAL(18, 3),
                    id_corporacao BIGINT,
                    id_cidade_destino BIGINT,
                    data_previsao_entrega DATE,

                    -- Campos Expandidos (22 campos do CSV)
                    pagador_id BIGINT,
                    pagador_nome NVARCHAR(255),
                    remetente_id BIGINT,
                    remetente_nome NVARCHAR(255),
                    origem_cidade NVARCHAR(255),
                    origem_uf NVARCHAR(10),
                    destinatario_id BIGINT,
                    destinatario_nome NVARCHAR(255),
                    destino_cidade NVARCHAR(255),
                    destino_uf NVARCHAR(10),
                    filial_nome NVARCHAR(255),
                    filial_apelido NVARCHAR(255),
                    numero_nota_fiscal NVARCHAR(MAX),
                    tabela_preco_nome NVARCHAR(255),
                    classificacao_nome NVARCHAR(255),
                    centro_custo_nome NVARCHAR(255),
                    usuario_nome NVARCHAR(255),
                    reference_number NVARCHAR(100),
                    invoices_total_volumes INT,
                    taxed_weight DECIMAL(18, 3),
                    real_weight DECIMAL(18, 3),
                    total_cubic_volume DECIMAL(18, 3),
                    subtotal DECIMAL(18, 2),

                    -- CT-e (chave, número, série)
                    chave_cte NVARCHAR(100),
                    numero_cte INT,
                    serie_cte INT,
                    cte_id BIGINT,
                    cte_emission_type NVARCHAR(50),
                    cte_created_at DATETIMEOFFSET,

                    service_type INT,
                    insurance_enabled BIT,
                    gris_subtotal DECIMAL(18, 2),
                    tde_subtotal DECIMAL(18, 2),
                    modal_cte NVARCHAR(50),
                    redispatch_subtotal DECIMAL(18, 2),
                    suframa_subtotal DECIMAL(18, 2),
                    payment_type NVARCHAR(50),
                    previous_document_type NVARCHAR(50),
                    products_value DECIMAL(18, 2),
                    trt_subtotal DECIMAL(18, 2),
                    nfse_series NVARCHAR(50),
                    nfse_number INT,
                    insurance_id BIGINT,
                    other_fees DECIMAL(18, 2),
                    km DECIMAL(18, 2),
                    payment_accountable_type INT,
                    insured_value DECIMAL(18, 2),
                    globalized BIT,
                    sec_cat_subtotal DECIMAL(18, 2),
                    globalized_type NVARCHAR(50),
                    price_table_accountable_type INT,
                    insurance_accountable_type INT,

                    fiscal_calculation_basis DECIMAL(18, 2),
                    fiscal_tax_rate DECIMAL(18, 6),
                    fiscal_pis_rate DECIMAL(18, 6),
                    fiscal_cofins_rate DECIMAL(18, 6),
                    fiscal_has_difal BIT,
                    fiscal_difal_origin DECIMAL(18, 2),
                    fiscal_difal_destination DECIMAL(18, 2),

                    -- Coluna de Metadados para Resiliência e Completude
                    metadata NVARCHAR(MAX),

                    -- Coluna de Auditoria
                    data_extracao DATETIME2 DEFAULT GETDATE()
                )
                """;

            executarDDL(conexao, sql);
            logger.info("Tabela {} criada com sucesso.", NOME_TABELA);
        }
        ajustarTamanhoColunaNotasSeNecessario(conexao);
        adicionarColunasNovasSeNecessario(conexao);
        if (!viewVerificada) {
            criarViewPowerBISeNaoExistir(conexao);
            viewVerificada = true;
            logger.info("View do Power BI verificada/atualizada para {}.", NOME_TABELA);
        }
    }

    private void ajustarTamanhoColunaNotasSeNecessario(final Connection conexao) throws SQLException {
        final String sqlVerifica = """
            SELECT CHARACTER_MAXIMUM_LENGTH
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = 'fretes' AND COLUMN_NAME = 'numero_nota_fiscal'
        """;
        Integer tamanho = null;
        try (PreparedStatement stmt = conexao.prepareStatement(sqlVerifica);
             java.sql.ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                tamanho = rs.getInt(1);
            }
        }
        if (tamanho != null && tamanho != -1) { // -1 indica NVARCHAR(MAX)
            final String alterar = "ALTER TABLE dbo.fretes ALTER COLUMN numero_nota_fiscal NVARCHAR(MAX)";
            executarDDL(conexao, alterar);
            logger.info("🔧 Ajustado tamanho de 'numero_nota_fiscal' para NVARCHAR(MAX) em dbo.fretes");
        }
    }

    private void criarViewPowerBISeNaoExistir(final Connection conexao) throws SQLException {
        final String sqlView = """
            CREATE OR ALTER VIEW dbo.vw_fretes_powerbi AS
                SELECT
                    id AS [ID],
                    chave_cte AS [Chave CT-e],
                    numero_cte AS [Nº CT-e],
                    serie_cte AS [Série],
                    cte_issued_at AS [CT-e Emissão],
                    cte_emission_type AS [CT-e Tipo Emissão],
                    cte_id AS [CT-e ID],
                    cte_created_at AS [CT-e Criado em],
                    servico_em AS [Data frete],
                    criado_em AS [Criado em],
                valor_total AS [Valor Total do Serviço],
                valor_notas AS [Valor NF],
                peso_notas AS [Kg NF],
                subtotal AS [Valor Frete],
                invoices_total_volumes AS [Volumes],
                taxed_weight AS [Kg Taxado],
                real_weight AS [Kg Real],
                cubages_cubed_weight AS [Kg Cubado],
                total_cubic_volume AS [M3],
                pagador_nome AS [Pagador],
                pagador_documento AS [Pagador Doc],
                pagador_id AS [Pagador ID],
                remetente_nome AS [Remetente],
                remetente_documento AS [Remetente Doc],
                remetente_id AS [Remetente ID],
                origem_cidade AS [Origem],
                origem_uf AS [UF Origem],
                destinatario_nome AS [Destinatario],
                destinatario_documento AS [Destinatario Doc],
                destinatario_id AS [Destinatario ID],
                destino_cidade AS [Destino],
                    destino_uf AS [UF Destino],
                    filial_nome AS [Filial],
                    filial_apelido AS [Filial Apelido],
                    filial_cnpj AS [Filial CNPJ],
                tabela_preco_nome AS [Tabela de Preço],
                classificacao_nome AS [Classificação],
                centro_custo_nome AS [Centro de Custo],
                usuario_nome AS [Usuário],
                numero_nota_fiscal AS [NF],
                reference_number AS [Referência],
                id_corporacao AS [Corp ID],
                id_cidade_destino AS [Cidade Destino ID],
                data_previsao_entrega AS [Previsão de Entrega],
                modal AS [Modal],
                CASE status
                    WHEN 'pending' THEN 'pendente'
                    WHEN 'finished' THEN 'finalizado'
                    WHEN 'in_transit' THEN 'em trânsito'
                    WHEN 'standby' THEN 'aguardando / parado'
                    WHEN 'manifested' THEN 'manifesto emitido / pré-registro'
                    WHEN 'occurrence_treatment' THEN 'tratamento de ocorrência'
                    ELSE status
                END AS [Status],
                REPLACE(tipo_frete, 'Freight::', '') AS [Tipo Frete],
                service_type AS [Service Type],
                CASE WHEN insurance_enabled = 1 THEN 'Com seguro'
                     WHEN insurance_enabled = 0 THEN 'Sem seguro'
                     ELSE NULL
                END AS [Seguro Habilitado],
                gris_subtotal AS [GRIS],
                tde_subtotal AS [TDE],
                freight_weight_subtotal AS [Frete Peso],
                ad_valorem_subtotal AS [Ad Valorem],
                toll_subtotal AS [Pedágio],
                itr_subtotal AS [ITR],
                modal_cte AS [Modal CT-e],
                redispatch_subtotal AS [Redispatch],
                suframa_subtotal AS [SUFRAMA],
                CASE payment_type
                    WHEN 'bill' THEN 'boleto / fatura / cobrança bancária'
                    WHEN 'cash' THEN 'dinheiro'
                    ELSE payment_type
                END AS [Tipo Pagamento],
                CASE previous_document_type
                    WHEN 'electronic' THEN 'eletrônico'
                    ELSE previous_document_type
                END AS [Doc Anterior],
                products_value AS [Valor Produtos],
                trt_subtotal AS [TRT],
                fiscal_cst_type AS [ICMS CST],
                    fiscal_cfop_code AS [CFOP],
                    fiscal_tax_value AS [Valor ICMS],
                    fiscal_pis_value AS [Valor PIS],
                    fiscal_cofins_value AS [Valor COFINS],
                    fiscal_calculation_basis AS [Base de Cálculo ICMS],
                    fiscal_tax_rate AS [Alíquota ICMS %],
                    fiscal_pis_rate AS [Alíquota PIS %],
                    fiscal_cofins_rate AS [Alíquota COFINS %],
                    CASE WHEN fiscal_has_difal = 1 THEN 'possui'
                         WHEN fiscal_has_difal = 0 THEN 'não possui'
                         ELSE NULL
                    END AS [Possui DIFAL],
                    fiscal_difal_origin AS [DIFAL Origem],
                    fiscal_difal_destination AS [DIFAL Destino],
                nfse_series AS [Série NFS-e],
                nfse_number AS [Nº NFS-e],
                insurance_id AS [Seguro ID],
                other_fees AS [Outras Tarifas],
                km AS [KM],
                payment_accountable_type AS [Tipo Contábil Pagamento],
                insured_value AS [Valor Segurado],
                CASE WHEN globalized = 1 THEN 'verdadeiro'
                     WHEN globalized = 0 THEN 'falso'
                     ELSE NULL
                END AS [Globalizado],
                sec_cat_subtotal AS [SEC/CAT],
                CASE globalized_type
                    WHEN 'none' THEN 'nenhum'
                    ELSE globalized_type
                END AS [Tipo Globalizado],
                price_table_accountable_type AS [Tipo Contábil Tabela],
                insurance_accountable_type AS [Tipo Contábil Seguro],
                metadata AS [Metadata],
                data_extracao AS [Data de extracao]
            FROM dbo.fretes;
        """;
        executarDDL(conexao, sqlView);
    }

    private void adicionarColunaSeNaoExistir(final Connection conexao, final String nomeColuna, final String tipoDef) throws SQLException {
        final String verifica = "SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='fretes' AND COLUMN_NAME='" + nomeColuna + "'";
        try (PreparedStatement stmt = conexao.prepareStatement(verifica); java.sql.ResultSet rs = stmt.executeQuery()) {
            if (!rs.next()) {
                final String alterar = "ALTER TABLE dbo.fretes ADD " + nomeColuna + " " + tipoDef;
                executarDDL(conexao, alterar);
                logger.info("Adicionada coluna {} em dbo.fretes", nomeColuna);
            }
        }
    }

    private void adicionarColunasNovasSeNecessario(final Connection conexao) throws SQLException {
        adicionarColunaSeNaoExistir(conexao, "service_type", "INT");
        adicionarColunaSeNaoExistir(conexao, "insurance_enabled", "BIT");
        adicionarColunaSeNaoExistir(conexao, "gris_subtotal", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "tde_subtotal", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "modal_cte", "NVARCHAR(50)");
        adicionarColunaSeNaoExistir(conexao, "redispatch_subtotal", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "suframa_subtotal", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "payment_type", "NVARCHAR(50)");
        adicionarColunaSeNaoExistir(conexao, "previous_document_type", "NVARCHAR(50)");
        adicionarColunaSeNaoExistir(conexao, "products_value", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "trt_subtotal", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "nfse_series", "NVARCHAR(50)");
        adicionarColunaSeNaoExistir(conexao, "nfse_number", "INT");
        adicionarColunaSeNaoExistir(conexao, "insurance_id", "BIGINT");
        adicionarColunaSeNaoExistir(conexao, "other_fees", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "km", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "payment_accountable_type", "INT");
        adicionarColunaSeNaoExistir(conexao, "insured_value", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "globalized", "BIT");
        adicionarColunaSeNaoExistir(conexao, "sec_cat_subtotal", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "globalized_type", "NVARCHAR(50)");
        adicionarColunaSeNaoExistir(conexao, "price_table_accountable_type", "INT");
        adicionarColunaSeNaoExistir(conexao, "insurance_accountable_type", "INT");
        adicionarColunaSeNaoExistir(conexao, "filial_cnpj", "NVARCHAR(50)");
        adicionarColunaSeNaoExistir(conexao, "filial_apelido", "NVARCHAR(255)");
        adicionarColunaSeNaoExistir(conexao, "pagador_documento", "NVARCHAR(50)");
        adicionarColunaSeNaoExistir(conexao, "remetente_documento", "NVARCHAR(50)");
        adicionarColunaSeNaoExistir(conexao, "destinatario_documento", "NVARCHAR(50)");
        adicionarColunaSeNaoExistir(conexao, "cte_issued_at", "DATETIMEOFFSET");
        adicionarColunaSeNaoExistir(conexao, "cte_id", "BIGINT");
        adicionarColunaSeNaoExistir(conexao, "cte_emission_type", "NVARCHAR(50)");
        adicionarColunaSeNaoExistir(conexao, "cte_created_at", "DATETIMEOFFSET");
        adicionarColunaSeNaoExistir(conexao, "cubages_cubed_weight", "DECIMAL(18, 3)");
        adicionarColunaSeNaoExistir(conexao, "freight_weight_subtotal", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "ad_valorem_subtotal", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "toll_subtotal", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "itr_subtotal", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "fiscal_cst_type", "NVARCHAR(10)");
        adicionarColunaSeNaoExistir(conexao, "fiscal_cfop_code", "NVARCHAR(10)");
        adicionarColunaSeNaoExistir(conexao, "fiscal_tax_value", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "fiscal_pis_value", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "fiscal_cofins_value", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "fiscal_calculation_basis", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "fiscal_tax_rate", "DECIMAL(18, 6)");
        adicionarColunaSeNaoExistir(conexao, "fiscal_pis_rate", "DECIMAL(18, 6)");
        adicionarColunaSeNaoExistir(conexao, "fiscal_cofins_rate", "DECIMAL(18, 6)");
        adicionarColunaSeNaoExistir(conexao, "fiscal_has_difal", "BIT");
        adicionarColunaSeNaoExistir(conexao, "fiscal_difal_origin", "DECIMAL(18, 2)");
        adicionarColunaSeNaoExistir(conexao, "fiscal_difal_destination", "DECIMAL(18, 2)");
    }

    /**
     * Executa a operação MERGE (UPSERT) para inserir ou atualizar um frete no banco.
     * A lógica é segura e baseada na nova arquitetura de Entidade.
     */
    @Override
    protected int executarMerge(final Connection conexao, final FreteEntity frete) throws SQLException {
        // Para Fretes, o 'id' é a única chave confiável para o MERGE.
        if (frete.getId() == null) {
            throw new SQLException("Não é possível executar o MERGE para Frete sem um ID.");
        }

        final String sql = String.format("""
            MERGE %s AS target
            USING (VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?,
                ?, ?
            ))
                AS source (id, servico_em, criado_em, status, modal, tipo_frete, valor_total, valor_notas, peso_notas, id_corporacao, id_cidade_destino, data_previsao_entrega,
                           pagador_id, pagador_nome, remetente_id, remetente_nome, origem_cidade, origem_uf, destinatario_id, destinatario_nome, destino_cidade, destino_uf,
                           filial_nome, numero_nota_fiscal, tabela_preco_nome, classificacao_nome, centro_custo_nome, usuario_nome, reference_number, chave_cte, numero_cte, serie_cte, invoices_total_volumes,
                           taxed_weight, real_weight, total_cubic_volume, subtotal,
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
                INSERT (id, servico_em, criado_em, status, modal, tipo_frete, valor_total, valor_notas, peso_notas, id_corporacao, id_cidade_destino, data_previsao_entrega,
                        pagador_id, pagador_nome, remetente_id, remetente_nome, origem_cidade, origem_uf, destinatario_id, destinatario_nome, destino_cidade, destino_uf,
                        filial_nome, numero_nota_fiscal, tabela_preco_nome, classificacao_nome, centro_custo_nome, usuario_nome, reference_number, chave_cte, numero_cte, serie_cte, invoices_total_volumes,
                        taxed_weight, real_weight, total_cubic_volume, subtotal,
                        service_type, insurance_enabled, gris_subtotal, tde_subtotal, modal_cte, redispatch_subtotal, suframa_subtotal, payment_type, previous_document_type,
                        products_value, trt_subtotal, nfse_series, nfse_number, insurance_id, other_fees, km, payment_accountable_type, insured_value, globalized, sec_cat_subtotal, globalized_type, price_table_accountable_type, insurance_accountable_type,
                        pagador_documento, remetente_documento, destinatario_documento, filial_cnpj, cte_issued_at, cubages_cubed_weight, freight_weight_subtotal, ad_valorem_subtotal, toll_subtotal, itr_subtotal,
                        fiscal_cst_type, fiscal_cfop_code, fiscal_tax_value, fiscal_pis_value, fiscal_cofins_value,
                        filial_apelido, cte_id, cte_emission_type, cte_created_at,
                        fiscal_calculation_basis, fiscal_tax_rate, fiscal_pis_rate, fiscal_cofins_rate, fiscal_has_difal, fiscal_difal_origin, fiscal_difal_destination,
                        metadata, data_extracao)
                VALUES (source.id, source.servico_em, source.criado_em, source.status, source.modal, source.tipo_frete, source.valor_total, source.valor_notas, source.peso_notas, source.id_corporacao, source.id_cidade_destino, source.data_previsao_entrega,
                        source.pagador_id, source.pagador_nome, source.remetente_id, source.remetente_nome, source.origem_cidade, source.origem_uf, source.destinatario_id, source.destinatario_nome, source.destino_cidade, source.destino_uf,
                        source.filial_nome, source.numero_nota_fiscal, source.tabela_preco_nome, source.classificacao_nome, source.centro_custo_nome, source.usuario_nome, source.reference_number, source.chave_cte, source.numero_cte, source.serie_cte, source.invoices_total_volumes,
                        source.taxed_weight, source.real_weight, source.total_cubic_volume, source.subtotal,
                        source.service_type, source.insurance_enabled, source.gris_subtotal, source.tde_subtotal, source.modal_cte, source.redispatch_subtotal, source.suframa_subtotal, source.payment_type, source.previous_document_type,
                        source.products_value, source.trt_subtotal, source.nfse_series, source.nfse_number, source.insurance_id, source.other_fees, source.km, source.payment_accountable_type, source.insured_value, source.globalized, source.sec_cat_subtotal, source.globalized_type, source.price_table_accountable_type, source.insurance_accountable_type,
                        source.pagador_documento, source.remetente_documento, source.destinatario_documento, source.filial_cnpj, source.cte_issued_at, source.cubages_cubed_weight, source.freight_weight_subtotal, source.ad_valorem_subtotal, source.toll_subtotal, source.itr_subtotal,
                        source.fiscal_cst_type, source.fiscal_cfop_code, source.fiscal_tax_value, source.fiscal_pis_value, source.fiscal_cofins_value,
                        source.filial_apelido, source.cte_id, source.cte_emission_type, source.cte_created_at,
                        source.fiscal_calculation_basis, source.fiscal_tax_rate, source.fiscal_pis_rate, source.fiscal_cofins_rate, source.fiscal_has_difal, source.fiscal_difal_origin, source.fiscal_difal_destination,
                        source.metadata, source.data_extracao);
            """, NOME_TABELA);

        try (PreparedStatement statement = conexao.prepareStatement(sql)) {
            // Define os parâmetros de forma segura e na ordem correta.
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
                throw new SQLException(String.format("Número incorreto de parâmetros: esperado %d, definido %d", expected, (paramIndex - 1)));
            }

            final int rowsAffected = statement.executeUpdate();
            logger.debug("MERGE executado para Frete ID {}: {} linha(s) afetada(s)", frete.getId(), rowsAffected);
            return rowsAffected;
        }
    }
}
