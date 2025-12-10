package br.com.extrator.util;

 
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Exportador de dados para CSV
 * Exporta todas as 8 entidades do banco para arquivos CSV
 */
public class ExportadorCSV {
    
    private static final String[] ENTIDADES = {
        "cotacoes",
        "coletas",
        "contas_a_pagar",
        "faturas_por_cliente",
        "fretes",
        "manifestos",
        "localizacao_cargas",
        "page_audit"
    };
    
    private static final String PASTA_DESTINO = "exports";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    public static void main(final String[] args) {
        System.out.println("===============================================================");
        System.out.println("  EXPORTADOR CSV - ESL CLOUD DATA");
        System.out.println("===============================================================");
        System.out.println();
        
        final String tabelaEspecifica = (args != null && args.length > 0) ? args[0].trim() : null;
        final boolean somenteUmaTabela = tabelaEspecifica != null && !tabelaEspecifica.isEmpty();

        // Criar pasta de destino
        final Path pastaExports = Paths.get(PASTA_DESTINO);
        try {
            if (!Files.exists(pastaExports)) {
                Files.createDirectories(pastaExports);
                System.out.println("✅ Pasta criada: " + PASTA_DESTINO);
            }
        } catch (final IOException e) {
            System.err.println("❌ Erro ao criar pasta: " + e.getMessage());
            return;
        }
        
        final String timestamp = LocalDateTime.now().format(FORMATTER);
        int totalRegistros = 0;
        int entidadesExportadas = 0;
        
        System.out.println("📅 Timestamp: " + timestamp);
        System.out.println("📁 Destino: " + PASTA_DESTINO);
        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();
        
        try {
            atualizarViewManifestos();
            atualizarViewsPowerBI();
        } catch (final Exception e) {
        }
        
        if (somenteUmaTabela) {
            System.out.println("📌 Exportando tabela específica: " + tabelaEspecifica);
            try {
                final int registros = exportarEntidade(tabelaEspecifica, timestamp);
                System.out.println("    ✅ Sucesso: " + registros + " registros");
                totalRegistros += registros;
                entidadesExportadas++;
            } catch (final Exception e) {
                System.err.println("    ❌ Erro: " + e.getMessage());
            }
            System.out.println();
        } else {
            for (int i = 0; i < ENTIDADES.length; i++) {
                final String entidade = ENTIDADES[i];
                System.out.printf("[%d/%d] 📊 Exportando: %s%n", i + 1, ENTIDADES.length, entidade);
                try {
                    final int registros = exportarEntidade(entidade, timestamp);
                    System.out.println("    ✅ Sucesso: " + registros + " registros");
                    totalRegistros += registros;
                    entidadesExportadas++;
                } catch (final Exception e) {
                    System.err.println("    ❌ Erro: " + e.getMessage());
                }
                System.out.println();
            }
        }
        
        // Resumo final
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  EXPORTAÇÃO CONCLUÍDA!");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();
        System.out.println("📊 Resumo:");
        System.out.println("   - Entidades exportadas: " + entidadesExportadas + "/" + ENTIDADES.length);
        System.out.println("   - Total de registros: " + totalRegistros);
        System.out.println("   - Pasta: " + Paths.get(PASTA_DESTINO).toAbsolutePath());
        System.out.println();
        System.out.println("💡 Próximos passos:");
        System.out.println("   1. Abra a pasta 'exports'");
        System.out.println("   2. Abra os CSVs no Excel/Google Sheets");
        System.out.println("   3. Compare com dados do portal");
        System.out.println();
    }

    private static void atualizarViewsPowerBI() throws Exception {
        try (Connection conn = GerenciadorConexao.obterConexao();
             Statement stmt = conn.createStatement()) {
            final String sqlFaturas = """
                CREATE OR ALTER VIEW dbo.vw_faturas_por_cliente_powerbi AS
                SELECT
                    unique_id AS [ID Único],
                    filial AS [Filial],
                    numero_cte AS [CT-e/Número],
                    chave_cte AS [CT-e/Chave],
                    data_emissao_cte AS [CT-e/Data de emissão],
                    valor_frete AS [Frete/Valor dos CT-es],
                    third_party_ctes_value AS [Terceiros/Valor CT-es],
                    status_cte AS [CT-e/Status],
                    tipo_frete AS [Tipo],
                    classificacao AS [Classificação],
                    pagador_nome AS [Pagador do frete/Nome],
                    pagador_documento AS [Pagador do frete/Documento],
                    remetente_nome AS [Remetente/Nome],
                    destinatario_nome AS [Destinatário/Nome],
                    vendedor_nome AS [Vendedor/Nome],
                    CASE WHEN fit_ant_document IS NOT NULL THEN 'Faturado' ELSE 'Aguardando Faturamento' END AS [Status do Processo],
                    fit_ant_document AS [Fatura/N° Documento],
                    fit_ant_issue_date AS [Fatura/Emissão],
                    fit_ant_value AS [Fatura/Valor],
                    valor_fatura AS [Fatura/Valor Total],
                    numero_fatura AS [Fatura/Número],
                    data_emissao_fatura AS [Fatura/Emissão Fatura],
                    data_vencimento_fatura AS [Parcelas/Vencimento],
                    data_baixa_fatura AS [Fatura/Baixa],
                    fit_ant_ils_original_due_date AS [Fatura/Data Vencimento Original],
                    metadata AS [Metadata],
                    data_extracao AS [Data da Última Atualização]
                FROM dbo.faturas_por_cliente;
            """;
            stmt.execute(sqlFaturas);

            final String sqlFretes = """
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
                    status AS [Status],
                    tipo_frete AS [Tipo Frete],
                    service_type AS [Service Type],
                    insurance_enabled AS [Seguro Habilitado],
                    gris_subtotal AS [GRIS],
                    tde_subtotal AS [TDE],
                    freight_weight_subtotal AS [Frete Peso],
                    ad_valorem_subtotal AS [Ad Valorem],
                    toll_subtotal AS [Pedágio],
                    itr_subtotal AS [ITR],
                    modal_cte AS [Modal CT-e],
                    redispatch_subtotal AS [Redispatch],
                    suframa_subtotal AS [SUFRAMA],
                    payment_type AS [Tipo Pagamento],
                    previous_document_type AS [Doc Anterior],
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
                    fiscal_has_difal AS [Possui DIFAL],
                    fiscal_difal_origin AS [DIFAL Origem],
                    fiscal_difal_destination AS [DIFAL Destino],
                    nfse_series AS [Série NFS-e],
                    nfse_number AS [Nº NFS-e],
                    insurance_id AS [Seguro ID],
                    other_fees AS [Outras Tarifas],
                    km AS [KM],
                    payment_accountable_type AS [Tipo Contábil Pagamento],
                    insured_value AS [Valor Segurado],
                    globalized AS [Globalizado],
                    sec_cat_subtotal AS [SEC/CAT],
                    globalized_type AS [Tipo Globalizado],
                    price_table_accountable_type AS [Tipo Contábil Tabela],
                    insurance_accountable_type AS [Tipo Contábil Seguro],
                    metadata AS [Metadata],
                    data_extracao AS [Data de extracao]
                FROM dbo.fretes;
            """;
            stmt.execute(sqlFretes);

            final String sqlCotacoes = """
                CREATE OR ALTER VIEW dbo.vw_cotacoes_powerbi AS
                SELECT
                    sequence_code AS [N° Cotação],
                    requested_at AS [Data Cotação],
                    branch_nickname AS [Filial],
                    requester_name AS [Solicitante],
                    customer_name AS [Cliente Pagador],
                    customer_doc AS [CNPJ/CPF Cliente],
                    origin_city AS [Cidade Origem],
                    origin_state AS [UF Origem],
                    destination_city AS [Cidade Destino],
                    destination_state AS [UF Destino],
                    volumes AS [Volume],
                    real_weight AS [Peso real],
                    taxed_weight AS [Peso taxado],
                    invoices_value AS [Valor NF],
                    total_value AS [Valor frete],
                    price_table AS [Tabela],
                    user_name AS [Usuário],
                    company_name AS [Empresa],
                    operation_type AS [Tipo de operação],
                    origin_postal_code AS [CEP Origem],
                    destination_postal_code AS [CEP Destino],
                    metadata AS [Metadata],
                    data_extracao AS [Data de extracao],
                    CASE
                      WHEN cte_issued_at IS NOT NULL OR nfse_issued_at IS NOT NULL THEN 'Convertida'
                      WHEN disapprove_comments IS NOT NULL AND LEN(disapprove_comments) > 0 THEN 'Reprovada'
                      ELSE 'Pendente'
                    END AS [Status Conversão],
                    disapprove_comments AS [Motivo Perda],
                    freight_comments AS [Observações para o frete],
                    cte_issued_at AS [CT-e/Data de emissão],
                    nfse_issued_at AS [Nfse/Data de emissão],
                    customer_nickname AS [Pagador/Nome fantasia],
                    sender_document AS [Remetente/CNPJ],
                    sender_nickname AS [Remetente/Nome fantasia],
                    receiver_document AS [Destinatário/CNPJ],
                    receiver_nickname AS [Destinatário/Nome fantasia],
                    discount_subtotal AS [Descontos/Subtotal parcelas],
                    itr_subtotal AS [Trechos/ITR],
                    tde_subtotal AS [Trechos/TDE],
                    collect_subtotal AS [Trechos/Coleta],
                    delivery_subtotal AS [Trechos/Entrega],
                    other_fees AS [Trechos/Outros valores]
                FROM dbo.cotacoes;
            """;
            stmt.execute(sqlCotacoes);

            final String sqlContas = """
                CREATE OR ALTER VIEW dbo.vw_contas_a_pagar_powerbi AS
                SELECT
                    sequence_code AS [Lançamento a Pagar/N°],
                    document_number AS [N° Documento],
                    issue_date AS [Emissão],
                    tipo_lancamento AS [Tipo],
                    valor_original AS [Valor],
                    valor_juros AS [Juros],
                    valor_desconto AS [Desconto],
                    valor_a_pagar AS [Valor a pagar],
                    CASE WHEN status_pagamento = 'PAGO' THEN 'Sim' ELSE 'Não' END AS [Pago],
                    valor_pago AS [Valor pago],
                    nome_fornecedor AS [Fornecedor/Nome],
                    nome_filial AS [Filial],
                    classificacao_contabil AS [Conta Contábil/Classificação],
                    descricao_contabil AS [Conta Contábil/Descrição],
                    valor_contabil AS [Conta Contábil/Valor],
                    nome_centro_custo AS [Centro de custo/Nome],
                    valor_centro_custo AS [Centro de custo/Valor],
                    area_lancamento AS [Área de Lançamento],
                    mes_competencia AS [Mês de Competência],
                    ano_competencia AS [Ano de Competência],
                    data_criacao AS [Data criação],
                    observacoes AS [Observações],
                    descricao_despesa AS [Descrição da despesa],
                    data_liquidacao AS [Baixa/Data liquidação],
                    data_transacao AS [Data transação],
                    nome_usuario AS [Usuário/Nome],
                    status_pagamento AS [Status Pagamento],
                    reconciliado AS [Conciliado],
                    metadata AS [Metadata],
                    data_extracao AS [Data de extracao]
                FROM dbo.contas_a_pagar;
            """;
            stmt.execute(sqlContas);

            final String sqlLocalizacao = """
                CREATE OR ALTER VIEW dbo.vw_localizacao_cargas_powerbi AS
                SELECT
                    sequence_number AS [N° Minuta],
                    type AS [Tipo],
                    service_at AS [Data do frete],
                    invoices_volumes AS [Volumes],
                    taxed_weight AS [Peso Taxado],
                    invoices_value AS [Valor NF],
                    total_value AS [Valor Frete],
                    service_type AS [Tipo Serviço],
                    branch_nickname AS [Filial Emissora],
                    predicted_delivery_at AS [Previsão Entrega/Previsão de entrega],
                    destination_location_name AS [Cidade Destino],
                    destination_branch_nickname AS [Filial Destino],
                    classification AS [Serviço],
                    status AS [Status Carga],
                    status_branch_nickname AS [Filial Atual],
                    origin_location_name AS [Cidade Origem],
                    origin_branch_nickname AS [Filial Origem],
                    TRY_CONVERT(DECIMAL(10,6), JSON_VALUE(metadata, '$.latitude')) AS [Latitude],
                    TRY_CONVERT(DECIMAL(10,6), JSON_VALUE(metadata, '$.longitude')) AS [Longitude],
                    TRY_CONVERT(DECIMAL(10,2), JSON_VALUE(metadata, '$.speed')) AS [Velocidade],
                    TRY_CONVERT(DECIMAL(10,2), JSON_VALUE(metadata, '$.altitude')) AS [Altitude],
                    JSON_VALUE(metadata, '$.device_id') AS [Dispositivo ID],
                    JSON_VALUE(metadata, '$.device_type') AS [Dispositivo Tipo],
                    JSON_VALUE(metadata, '$.address') AS [Endereço],
                    data_extracao AS [Data de extracao]
                FROM dbo.localizacao_cargas;
            """;
            stmt.execute(sqlLocalizacao);
        }
    }
    
    private static void atualizarViewManifestos() throws Exception {
        try (Connection conn = GerenciadorConexao.obterConexao();
             Statement stmt = conn.createStatement()) {
            final String sqlView = """
                CREATE OR ALTER VIEW dbo.vw_manifestos_powerbi AS
                SELECT
                    sequence_code                                       AS [Número],
                    identificador_unico                                 AS [Identificador Único],
                    status                                              AS [Status],
                    classification                                      AS [Classificação],
                    branch_nickname                                     AS [Filial],
                    created_at                                          AS [Data criação],
                    departured_at                                       AS [Saída],
                    closed_at                                           AS [Fechamento],
                    finished_at                                         AS [Chegada],
                    mdfe_number                                         AS [MDFe],
                    mdfe_key                                            AS [MDF-es/Chave],
                    mdfe_status                                         AS [MDFe/Status],
                    distribution_pole                                   AS [Polo de distribuição],
                    vehicle_plate                                       AS [Veículo/Placa],
                    vehicle_type                                        AS [Tipo Veículo/Nome],
                    vehicle_owner                                       AS [Proprietário/Nome],
                    driver_name                                         AS [Motorista],
                    vehicle_departure_km                                AS [Km saída],
                    closing_km                                          AS [Km chegada],
                    traveled_km                                         AS [KM viagem],
                    manual_km                                           AS [Km manual],
                    invoices_count                                      AS [Qtd NF],
                    invoices_volumes                                    AS [Volumes NF],
                    invoices_weight                                     AS [Peso NF],
                    total_taxed_weight                                  AS [Total peso taxado],
                    total_cubic_volume                                  AS [Total M3],
                    invoices_value                                      AS [Valor NF],
                    manifest_freights_total                             AS [Fretes/Total],
                    pick_sequence_code                                   AS [Coleta/Sequence Code],
                    contract_number                                     AS [Contrato/Número],
                    contract_type                                       AS [Tipo de contrato],
                    calculation_type                                    AS [Tipo de cálculo],
                    cargo_type                                          AS [Tipo de carga],
                    daily_subtotal                                      AS [Diária],
                    total_cost                                          AS [Custo total],
                    freight_subtotal                                    AS [Valor frete],
                    fuel_subtotal                                       AS [Combustível],
                    toll_subtotal                                       AS [Pedágio],
                    driver_services_total                               AS [Serviços motorista/Total],
                    operational_expenses_total                          AS [Despesa operacional],
                    inss_value                                          AS [Dados do agregado/INSS],
                    sest_senat_value                                    AS [Dados do agregado/SEST/SENAT],
                    ir_value                                            AS [Dados do agregado/IR],
                    paying_total                                        AS [Saldo a pagar],
                    uniq_destinations_count                             AS [Destinos únicos/Qtd],
                    generate_mdfe                                       AS [Gerar MDF-e],
                    monitoring_request                                  AS [Solicitou Monitoramento],
                    mobile_read_at                                      AS [Leitura Móvel/Em],
                    km                                                  AS [KM Total],
                    delivery_manifest_items_count                       AS [Itens/Entrega],
                    transfer_manifest_items_count                       AS [Itens/Transferência],
                    pick_manifest_items_count                           AS [Itens/Coleta],
                    dispatch_draft_manifest_items_count                 AS [Itens/Despacho Rascunho],
                    consolidation_manifest_items_count                  AS [Itens/Consolidação],
                    reverse_pick_manifest_items_count                   AS [Itens/Coleta Reversa],
                    manifest_items_count                                AS [Itens/Total],
                    finalized_manifest_items_count                      AS [Itens/Finalizados],
                    calculated_pick_count                               AS [Calculado/Coleta],
                    calculated_delivery_count                           AS [Calculado/Entrega],
                    calculated_dispatch_count                           AS [Calculado/Despacho],
                    calculated_consolidation_count                      AS [Calculado/Consolidação],
                    calculated_reverse_pick_count                       AS [Calculado/Coleta Reversa],
                    pick_subtotal                                       AS [Coletas.1],
                    delivery_subtotal                                   AS [Entregas.1],
                    dispatch_subtotal                                   AS [Despachos],
                    consolidation_subtotal                              AS [Consolidações],
                    reverse_pick_subtotal                               AS [Coleta Reversa],
                    advance_subtotal                                    AS [Adiantamento],
                    fleet_costs_subtotal                                AS [Custos Frota],
                    additionals_subtotal                                AS [Adicionais],
                    discounts_subtotal                                  AS [Descontos],
                    discount_value                                      AS [Desconto/Valor],
                    iks_id                                              AS [IKS ID],
                    programacao_sequence_code                           AS [Programação/Sequence Code],
                    programacao_starting_at                             AS [Programação/Início],
                    programacao_ending_at                               AS [Programação/Término],
                    trailer1_license_plate                              AS [Carreta 1/Placa],
                    trailer1_weight_capacity                            AS [Carreta 1/Capacidade Peso],
                    trailer2_license_plate                              AS [Carreta 2/Placa],
                    trailer2_weight_capacity                            AS [Carreta 2/Capacidade Peso],
                    vehicle_weight_capacity                             AS [Veículo/Capacidade Peso],
                    vehicle_cubic_weight                                AS [Veículo/Peso Cubado],
                    unloading_recipient_names                           AS [Descarregamento/Destinatários],
                    delivery_region_names                               AS [Entrega/Regiões],
                    programacao_cliente                                 AS [Programação/Cliente],
                    programacao_tipo_servico                            AS [Programação/Tipo Serviço],
                    creation_user_name                                  AS [Usuário/Emissor],
                    adjustment_user_name                                AS [Usuário/Ajuste],
                    metadata                                            AS [Metadata],
                    data_extracao                                       AS [Data de extracao]
                FROM dbo.manifestos;
            """;
            stmt.execute(sqlView);
        }
    }
    
    /**
     * Exporta uma entidade para CSV
     */
    private static int exportarEntidade(final String entidade, final String timestamp) throws Exception {
        final String entidadeArquivo = entidade.replaceAll("[^A-Za-z0-9_]+", "_");
        final String nomeArquivo = String.format("%s/%s_%s.csv", PASTA_DESTINO, entidadeArquivo, timestamp);
        
        System.out.println("    🔍 Contando registros no banco...");
        
        // Primeiro, contar quantos registros existem no banco
        int totalNoBanco = 0;
        try (Connection connCount = GerenciadorConexao.obterConexao();
             Statement stmtCount = connCount.createStatement();
             ResultSet rsCount = stmtCount.executeQuery("SELECT COUNT(*) as total FROM " + entidade)) {
            if (rsCount.next()) {
                totalNoBanco = rsCount.getInt("total");
            }
        }
        
        System.out.println("    📊 Total de registros no banco: " + totalNoBanco);
        
        if (totalNoBanco == 0) {
            System.out.println("    ⚠️ Nenhum registro encontrado. CSV será criado apenas com cabeçalho.");
        }
        
        // Obter conexão do GerenciadorConexao
        try (Connection conn = GerenciadorConexao.obterConexao();
             Statement stmt = conn.createStatement();
             java.io.OutputStream os = new java.io.FileOutputStream(nomeArquivo);
             java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(os, StandardCharsets.UTF_8))) {
            os.write(new byte[] {(byte)0xEF, (byte)0xBB, (byte)0xBF});
            
            // Configurar Statement para não limitar resultados
            // SQL Server pode ter limite padrão, então garantimos que não há limitação
            stmt.setFetchSize(0); // 0 = fetch all (padrão do driver)
            stmt.setMaxRows(0); // 0 = sem limite
            
            System.out.println("    🔄 Executando query SELECT * FROM " + entidade + "...");
            
            String origem;
            switch (entidade) {
                case "fretes" -> origem = "dbo.vw_fretes_powerbi";
                case "coletas" -> origem = "dbo.vw_coletas_powerbi";
                case "cotacoes" -> origem = "dbo.vw_cotacoes_powerbi";
                case "contas_a_pagar" -> origem = "dbo.vw_contas_a_pagar_powerbi";
                case "faturas_por_cliente" -> origem = "dbo.vw_faturas_por_cliente_powerbi";
                case "manifestos" -> origem = "dbo.vw_manifestos_powerbi";
                case "localizacao_cargas" -> origem = "dbo.vw_localizacao_cargas_powerbi";
                default -> origem = entidade;
            }
            final boolean usarViewPowerBI = !origem.equals(entidade);
            String query = "SELECT * FROM " + origem;
            // Ordenação consistente
            if (usarViewPowerBI) {
                // Evitar problemas com nomes com espaços usando posição
                query += " ORDER BY 1"; // ID
            } else {
                switch (entidade) {
                    case "manifestos", "cotacoes", "contas_a_pagar" -> query += " ORDER BY sequence_code";
                    case "localizacao_cargas" -> query += " ORDER BY sequence_number";
                    case "coletas", "fretes" -> query += " ORDER BY id";
                    case "faturas_por_cliente" -> query += " ORDER BY unique_id";
                    default -> {}
                }
            }
            
            try (ResultSet rs = stmt.executeQuery(query)) {
                final ResultSetMetaData metaData = rs.getMetaData();
                final int columnCount = metaData.getColumnCount();
                
                System.out.println("    📋 Colunas encontradas: " + columnCount);
                
                // Escrever cabeçalho com alias (label) quando existente
                for (int i = 1; i <= columnCount; i++) {
                    writer.append(metaData.getColumnLabel(i));
                    if (i < columnCount) {
                        writer.append(";");
                    }
                }
                writer.append("\r\n");
                
                // Escrever dados
                int count = 0;
                final int logInterval = Math.max(100, totalNoBanco / 10); // Log a cada 10% ou 100 registros
                
                System.out.println("    📝 Escrevendo registros no CSV...");
                
                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        final Object value = rs.getObject(i);
                        if (value != null) {
                            String strValue = value.toString();
                            // Escapar valores que contenham vírgula ou aspas
                            if (strValue.contains(";") || strValue.contains("\"") || strValue.contains("\n")) {
                                strValue = "\"" + strValue.replace("\"", "\"\"") + "\"";
                            }
                            writer.append(strValue);
                        }
                        if (i < columnCount) {
                            writer.append(";");
                        }
                    }
                    writer.append("\r\n");
                    count++;
                    
                    // Log de progresso
                    if (count % logInterval == 0 || count == totalNoBanco) {
                        System.out.printf("    ⏳ Progresso: %d/%d registros (%.1f%%)\n", 
                                count, totalNoBanco, (count * 100.0 / totalNoBanco));
                    }
                }
                
                // Garantir que o buffer seja escrito
                writer.flush();
                
                System.out.println("    ✅ Exportação concluída: " + count + " registros escritos");
                
                // Verificar se há discrepância
                if (count != totalNoBanco) {
                    System.err.println("    ⚠️ ATENÇÃO: Discrepância detectada!");
                    System.err.println("       - Registros no banco: " + totalNoBanco);
                    System.err.println("       - Registros exportados: " + count);
                    System.err.println("       - Diferença: " + (totalNoBanco - count) + " registros");
                }
                
                return count;
            }
        }
    }
}
