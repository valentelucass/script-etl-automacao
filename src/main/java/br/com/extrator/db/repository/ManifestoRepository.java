package br.com.extrator.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.ManifestoEntity;

/**
 * Repositório para operações de persistência da entidade ManifestoEntity.
 * Implementa a arquitetura de persistência híbrida: colunas-chave para indexação
 * e uma coluna de metadados para resiliência e completude dos dados.
 * 
 * Utiliza chave composta (sequence_code, identificador_unico) para permitir
 * duplicados naturais (mesmo sequence_code mas dados diferentes) enquanto
 * mantém MERGE funcional para evitar duplicação não natural.
 * 
 * O identificador_unico é calculado como:
 * - pick_sequence_code (quando disponível)
 * - hash SHA-256 do metadata (quando pick_sequence_code é NULL)
 */
public class ManifestoRepository extends AbstractRepository<ManifestoEntity> {
    private static final Logger logger = LoggerFactory.getLogger(ManifestoRepository.class);
    private static final String NOME_TABELA = "manifestos";
    private static boolean viewVerificada = false;

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }
    
    /**
     * Verifica se a tabela tem a estrutura nova (com identificador_unico) ou antiga (apenas sequence_code como PK).
     * @param conexao Conexão com o banco de dados
     * @return true se a tabela tem identificador_unico (estrutura nova), false caso contrário (estrutura antiga)
     */
    private boolean tabelaTemEstruturaNova(final Connection conexao) throws SQLException {
        try {
            final String sql = """
                SELECT COUNT(*) as existe
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ? AND COLUMN_NAME = 'identificador_unico'
                """;
            try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
                stmt.setString(1, NOME_TABELA);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("existe") > 0;
                    }
                }
            }
        } catch (final SQLException e) {
            logger.warn("Erro ao verificar estrutura da tabela: {}", e.getMessage());
            // Se houver erro, assume estrutura antiga por segurança
            return false;
        }
        return false;
    }

    /**
     * Cria a tabela 'manifestos' se ela não existir, seguindo o modelo híbrido.
     * A estrutura contém apenas colunas essenciais para busca e uma coluna NVARCHAR(MAX)
     * para armazenar o JSON completo, garantindo resiliência.
     * 
     * Utiliza chave composta (sequence_code, identificador_unico) para permitir
     * duplicados naturais (mesmo sequence_code mas dados diferentes) enquanto
     * mantém MERGE funcional para evitar duplicação não natural.
     */
    @Override
    protected void criarTabelaSeNaoExistir(final Connection conexao) throws SQLException {
        if (!verificarTabelaExiste(conexao, NOME_TABELA)) {
            logger.info("Criando tabela {} com arquitetura híbrida e chave composta...", NOME_TABELA);

            final String sql = """
                CREATE TABLE manifestos (
                    -- Coluna de Chave Primária (Auto-incrementado)
                    id BIGINT IDENTITY(1,1) PRIMARY KEY,

                    -- Chave Composta (para identificar unicamente)
                    sequence_code BIGINT NOT NULL,
                    identificador_unico NVARCHAR(100) NOT NULL,

                    -- Colunas Essenciais para Indexação e Relatórios conforme docs/descobertas-endpoints/manifestos.md
                    status NVARCHAR(50),
                    created_at DATETIMEOFFSET,
                    departured_at DATETIMEOFFSET,
                    closed_at DATETIMEOFFSET,
                    finished_at DATETIMEOFFSET,
                    mdfe_number INT,
                    mdfe_key NVARCHAR(100),
                    mdfe_status NVARCHAR(50),
                    distribution_pole NVARCHAR(255),
                    classification NVARCHAR(255),
                    vehicle_plate NVARCHAR(10),
                    vehicle_type NVARCHAR(255),
                    vehicle_owner NVARCHAR(255),
                    driver_name NVARCHAR(255),
                    branch_nickname NVARCHAR(255),
                    vehicle_departure_km INT,
                    closing_km INT,
                    traveled_km INT,
                    invoices_count INT,
                    invoices_volumes INT,
                    invoices_weight DECIMAL(18, 3),
                    total_taxed_weight DECIMAL(18, 3),
                    total_cubic_volume DECIMAL(18, 6),
                    invoices_value DECIMAL(18, 2),
                    manifest_freights_total DECIMAL(18, 2),
                    pick_sequence_code BIGINT,
                    contract_number NVARCHAR(50),
                    contract_type NVARCHAR(50),
                    calculation_type NVARCHAR(50),
                    cargo_type NVARCHAR(255),
                    daily_subtotal DECIMAL(18, 2),
                    total_cost DECIMAL(18, 2),
                    freight_subtotal DECIMAL(18, 2),
                    fuel_subtotal DECIMAL(18, 2),
                    toll_subtotal DECIMAL(18, 2),
                    driver_services_total DECIMAL(18, 2),
                    operational_expenses_total DECIMAL(18, 2),
                    inss_value DECIMAL(18, 2),
                    sest_senat_value DECIMAL(18, 2),
                    ir_value DECIMAL(18, 2),
                    paying_total DECIMAL(18, 2),
                    manual_km BIT,
                    generate_mdfe BIT,
                    monitoring_request BIT,
                    uniq_destinations_count INT,
                    mobile_read_at DATETIMEOFFSET,
                    km DECIMAL(18, 2),
                    delivery_manifest_items_count INT,
                    transfer_manifest_items_count INT,
                    pick_manifest_items_count INT,
                    dispatch_draft_manifest_items_count INT,
                    consolidation_manifest_items_count INT,
                    reverse_pick_manifest_items_count INT,
                    manifest_items_count INT,
                    finalized_manifest_items_count INT,
                    calculated_pick_count INT,
                    calculated_delivery_count INT,
                    calculated_dispatch_count INT,
                    calculated_consolidation_count INT,
                    calculated_reverse_pick_count INT,
                    pick_subtotal DECIMAL(18, 2),
                    delivery_subtotal DECIMAL(18, 2),
                    dispatch_subtotal DECIMAL(18, 2),
                    consolidation_subtotal DECIMAL(18, 2),
                    reverse_pick_subtotal DECIMAL(18, 2),
                    advance_subtotal DECIMAL(18, 2),
                    fleet_costs_subtotal DECIMAL(18, 2),
                    additionals_subtotal DECIMAL(18, 2),
                    discounts_subtotal DECIMAL(18, 2),
                    discount_value DECIMAL(18, 2),
                    adjustment_comments NVARCHAR(MAX),
                    contract_status NVARCHAR(50),
                    iks_id NVARCHAR(100),
                    programacao_sequence_code NVARCHAR(50),
                    programacao_starting_at DATETIMEOFFSET,
                    programacao_ending_at DATETIMEOFFSET,
                    trailer1_license_plate NVARCHAR(10),
                    trailer1_weight_capacity DECIMAL(18, 2),
                    trailer2_license_plate NVARCHAR(10),
                    trailer2_weight_capacity DECIMAL(18, 2),
                    vehicle_weight_capacity DECIMAL(18, 2),
                    vehicle_cubic_weight DECIMAL(18, 2),
                    unloading_recipient_names NVARCHAR(MAX),
                    delivery_region_names NVARCHAR(MAX),
                    programacao_cliente NVARCHAR(255),
                    programacao_tipo_servico NVARCHAR(255),
                    creation_user_name NVARCHAR(255),
                    adjustment_user_name NVARCHAR(255),

                    -- Coluna de Metadados para Resiliência e Completude
                    metadata NVARCHAR(MAX),

                    -- Coluna de Auditoria
                    data_extracao DATETIME2 DEFAULT GETDATE(),

                    -- Constraint UNIQUE para chave composta
                    -- IMPORTANTE: O MERGE usa (sequence_code, pick_sequence_code, mdfe_number) para matching, mas
                    -- a constraint é (sequence_code, identificador_unico) para garantir integridade.
                    -- O identificador_unico é calculado como:
                    --   - pick_sequence_code (se disponível)
                    --   - sequence_code + "_MDFE_" + mdfe_number (se não há pick mas há MDF-e)
                    --   - hash do metadata (se não há pick nem MDF-e)
                    -- Isso garante que múltiplos MDF-es tenham identificadores diferentes e sejam preservados.
                    -- O identificador_unico é atualizado no UPDATE quando há match, então não há violação.
                    CONSTRAINT UQ_manifestos_sequence_identificador UNIQUE (sequence_code, identificador_unico)
                )
                """;

            final String sqlIndex = """
                CREATE INDEX IX_manifestos_sequence_code ON manifestos(sequence_code);
                """;

            executarDDL(conexao, sql);
            executarDDL(conexao, sqlIndex);
            logger.info("Tabela {} criada com sucesso (chave composta: sequence_code + identificador_unico).", NOME_TABELA);
        }
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "mobile_read_at", "DATETIMEOFFSET");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "km", "DECIMAL(18,2)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "delivery_manifest_items_count", "INT");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "transfer_manifest_items_count", "INT");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "pick_manifest_items_count", "INT");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "dispatch_draft_manifest_items_count", "INT");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "consolidation_manifest_items_count", "INT");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "reverse_pick_manifest_items_count", "INT");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "manifest_items_count", "INT");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "finalized_manifest_items_count", "INT");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "calculated_pick_count", "INT");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "calculated_delivery_count", "INT");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "calculated_dispatch_count", "INT");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "calculated_consolidation_count", "INT");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "calculated_reverse_pick_count", "INT");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "pick_subtotal", "DECIMAL(18,2)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "delivery_subtotal", "DECIMAL(18,2)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "dispatch_subtotal", "DECIMAL(18,2)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "consolidation_subtotal", "DECIMAL(18,2)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "reverse_pick_subtotal", "DECIMAL(18,2)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "advance_subtotal", "DECIMAL(18,2)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "fleet_costs_subtotal", "DECIMAL(18,2)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "additionals_subtotal", "DECIMAL(18,2)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "discounts_subtotal", "DECIMAL(18,2)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "discount_value", "DECIMAL(18,2)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "adjustment_comments", "NVARCHAR(MAX)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "contract_status", "NVARCHAR(50)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "iks_id", "NVARCHAR(100)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "programacao_sequence_code", "NVARCHAR(50)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "programacao_starting_at", "DATETIMEOFFSET");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "programacao_ending_at", "DATETIMEOFFSET");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "trailer1_license_plate", "NVARCHAR(10)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "trailer1_weight_capacity", "DECIMAL(18,2)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "trailer2_license_plate", "NVARCHAR(10)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "trailer2_weight_capacity", "DECIMAL(18,2)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "vehicle_weight_capacity", "DECIMAL(18,2)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "vehicle_cubic_weight", "DECIMAL(18,2)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "unloading_recipient_names", "NVARCHAR(MAX)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "delivery_region_names", "NVARCHAR(MAX)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "programacao_cliente", "NVARCHAR(255)");
        adicionarColunaSeNaoExistir(conexao, NOME_TABELA, "programacao_tipo_servico", "NVARCHAR(255)");
        if (!viewVerificada) {
            criarViewPowerBISeNaoExistir(conexao);
            viewVerificada = true;
            logger.info("View do Power BI verificada/atualizada para {}.", NOME_TABELA);
        }
    }

    private boolean colunaExiste(final Connection conn, final String tabela, final String coluna) throws SQLException {
        final java.sql.DatabaseMetaData md = conn.getMetaData();
        try (java.sql.ResultSet rs = md.getColumns(null, null, tabela.toUpperCase(), coluna.toUpperCase())) {
            return rs.next();
        }
    }

    private void adicionarColunaSeNaoExistir(final Connection conn, final String tabela, final String coluna, final String definicao) throws SQLException {
        if (!colunaExiste(conn, tabela, coluna)) {
            try (PreparedStatement ps = conn.prepareStatement("ALTER TABLE " + tabela + " ADD " + coluna + " " + definicao)) {
                ps.execute();
                logger.info("Coluna adicionada: {}.{}", tabela, coluna);
            }
        }
    }

    private void criarViewPowerBISeNaoExistir(final Connection conexao) throws SQLException {
        final String sqlView = """
            CREATE OR ALTER VIEW dbo.vw_manifestos_powerbi AS
            SELECT
                sequence_code                                       AS [Número],
                identificador_unico                                 AS [Identificador Único],
                CASE status
                    WHEN 'closed' THEN 'encerrado'
                    WHEN 'in_transit' THEN 'em trânsito'
                    WHEN 'pending' THEN 'pendente'
                    ELSE status
                END                                                 AS [Status],
                classification                                      AS [Classificação],
                branch_nickname                                     AS [Filial],
                created_at                                          AS [Data criação],
                departured_at                                       AS [Saída],
                closed_at                                           AS [Fechamento],
                finished_at                                         AS [Chegada],
                mdfe_number                                         AS [MDFe],
                mdfe_key                                            AS [MDF-es/Chave],
                CASE mdfe_status
                    WHEN 'pending' THEN 'pendente'
                    WHEN 'closed' THEN 'encerrado'
                    WHEN 'issued' THEN 'emitido'
                    WHEN 'rejected' THEN 'rejeitado'
                    ELSE mdfe_status
                END                                                 AS [MDFe/Status],
                distribution_pole                                   AS [Polo de distribuição],
                vehicle_plate                                       AS [Veículo/Placa],
                vehicle_type                                        AS [Tipo Veículo/Nome],
                vehicle_owner                                       AS [Proprietário/Nome],
                driver_name                                         AS [Motorista],
                vehicle_departure_km                                AS [Km saída],
                closing_km                                          AS [Km chegada],
                traveled_km                                         AS [KM viagem],
                CASE WHEN manual_km = 1 THEN 'é manual'
                     WHEN manual_km = 0 THEN 'não é manual'
                     ELSE NULL
                END                                                 AS [Km manual],
                invoices_count                                      AS [Qtd NF],
                invoices_volumes                                    AS [Volumes NF],
                invoices_weight                                     AS [Peso NF],
                total_taxed_weight                                  AS [Total peso taxado],
                total_cubic_volume                                  AS [Total M3],
                invoices_value                                      AS [Valor NF],
                manifest_freights_total                             AS [Fretes/Total],
                pick_sequence_code                                   AS [Coleta/Número],
                contract_number                                     AS [CIOT/Número],
                CASE contract_type
                    WHEN 'aggregate' THEN 'prestador agregado'
                    WHEN 'driver' THEN 'motorista autônomo'
                    ELSE contract_type
                END                                                 AS [Tipo de contrato],
                CASE calculation_type
                    WHEN 'price_table' THEN 'tabela de preço'
                    WHEN 'agreed' THEN 'acordado'
                    ELSE calculation_type
                END                                                 AS [Tipo de cálculo],
                CASE cargo_type
                    WHEN 'fractioned' THEN 'carga fracionada'
                    WHEN 'closed' THEN 'carga fechada'
                    ELSE cargo_type
                END                                                 AS [Tipo de carga],
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
                CASE monitoring_request
                    WHEN 'true' THEN 'sim'
                    WHEN 'false' THEN 'não'
                    ELSE monitoring_request
                END                                                 AS [Solicitação Monitoramento],
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
                pick_subtotal                                       AS [Valor/Coletas],
                delivery_subtotal                                   AS [Valor/Entregas],
                dispatch_subtotal                                   AS [Despachos],
                consolidation_subtotal                              AS [Consolidações],
                reverse_pick_subtotal                               AS [Coleta Reversa],
                advance_subtotal                                    AS [Adiantamento],
                fleet_costs_subtotal                                AS [Custos Frota],
                additionals_subtotal                                AS [Adicionais],
                discounts_subtotal                                  AS [Descontos],
                discount_value                                      AS [Desconto/Valor],
                iks_id                                              AS [IKS ID],
                programacao_sequence_code                           AS [Programação/Número],
                programacao_starting_at                             AS [Programação/Início],
                programacao_ending_at                               AS [Programação/Término],
                trailer1_license_plate                              AS [Carreta 1/Placa],
                trailer1_weight_capacity                            AS [Carreta 1/Capacidade Peso],
                trailer2_license_plate                              AS [Carreta 2/Placa],
                trailer2_weight_capacity                            AS [Carreta 2/Capacidade Peso],
                vehicle_weight_capacity                             AS [Veículo/Capacidade Peso],
                vehicle_cubic_weight                                AS [Veículo/Peso Cubado],
                REPLACE(REPLACE(unloading_recipient_names, '[', ''), ']', '')
                                                                  AS [Descarregamento/Destinatários],
                REPLACE(REPLACE(REPLACE(delivery_region_names, '[', ''), ']', ''), '"', '')
                                                                  AS [Entrega/Regiões],
                programacao_cliente                                 AS [Programação/Cliente],
                programacao_tipo_servico                            AS [Programação/Tipo Serviço],
                creation_user_name                                  AS [Usuário/Emissor],
                adjustment_user_name                                AS [Usuário/Ajuste],
                metadata                                            AS [Metadata],
                data_extracao                                       AS [Data de extracao]
            FROM dbo.manifestos;
        """;
        executarDDL(conexao, sqlView);
    }

    /**
     * Executa a operação MERGE (UPSERT) para inserir ou atualizar um manifesto no banco.
     * Versão melhorada com validações robustas, logging detalhado e truncamento de strings.
     * 
     * Compatível com estrutura antiga (apenas sequence_code como PK) e nova (chave composta).
     * Detecta automaticamente qual estrutura a tabela tem e usa o MERGE apropriado.
     */
    @Override
    protected int executarMerge(final Connection conexao, final ManifestoEntity manifesto) throws SQLException {
        // ✅ VALIDAÇÃO INICIAL
        if (manifesto == null) {
            logger.error("❌ Tentativa de salvar ManifestoEntity NULL");
            throw new SQLException("Não é possível executar MERGE para Manifesto nulo");
        }
        
        // Para Manifestos, o 'sequence_code' é a chave de negócio primária.
        if (manifesto.getSequenceCode() == null) {
            logger.error("❌ Manifesto com sequence_code NULL");
            throw new SQLException("Não é possível executar o MERGE para Manifesto sem um 'sequence_code'.");
        }
        
        // Verificar se a tabela tem estrutura nova ou antiga
        final boolean estruturaNova = tabelaTemEstruturaNova(conexao);
        
        if (estruturaNova) {
            // Estrutura nova: usar chave composta (sequence_code, identificador_unico)
            return executarMergeEstruturaNova(conexao, manifesto);
        } else {
            // Estrutura antiga: usar apenas sequence_code como PK
            return executarMergeEstruturaAntiga(conexao, manifesto);
        }
    }
    
    /**
     * Executa MERGE com estrutura nova.
     * 
     * IMPORTANTE: O MERGE usa (sequence_code, pick_sequence_code, mdfe_number) para matching,
     * NÃO (sequence_code, identificador_unico).
     * 
     * Lógica do MERGE (usando COALESCE para simplificar):
     * - Compara (sequence_code, pick_sequence_code, mdfe_number):
     *   - Se ambos têm pick_sequence_code: compara os valores (ex: 71920 = 71920)
     *   - Se ambos são NULL: ambos viram -1 → match! (mesmo manifesto sem coleta)
     *   - Se um é NULL e outro não: não match (registros diferentes)
     *   - Se ambos têm mdfe_number: compara os valores (ex: 1503 = 1503)
     *   - Se ambos são NULL: ambos viram -1 → match! (mesmo manifesto sem MDF-e)
     * 
     * Isso garante que:
     * - Duplicados naturais (mesmo sequence_code, diferentes pick_sequence_code) são preservados
     * - Múltiplos MDF-es (mesmo sequence_code, diferentes mdfe_number) são preservados
     * - Duplicados falsos (mesmo sequence_code, pick NULL, mdfe NULL, hash diferente) são eliminados
     * - O identificador_unico é atualizado no UPDATE, mas não é usado para matching
     * 
     * Com essa correção, futuras extrações não criarão mais duplicados falsos e preservarão
     * todos os MDF-es legítimos, pois o MERGE sempre encontrará o registro correto baseado
     * em (sequence_code, pick_sequence_code, mdfe_number).
     */
    private int executarMergeEstruturaNova(final Connection conexao, final ManifestoEntity manifesto) throws SQLException {
        // Validar que identificador_unico foi calculado
        if (manifesto.getIdentificadorUnico() == null || manifesto.getIdentificadorUnico().trim().isEmpty()) {
            logger.error("❌ Manifesto com identificador_unico NULL ou vazio (sequence_code={})", manifesto.getSequenceCode());
            throw new SQLException("Não é possível executar o MERGE para Manifesto sem um 'identificador_unico'. Certifique-se de que calcularIdentificadorUnico() foi chamado.");
        }
        
        // Validar tamanho máximo (100 caracteres)
        final String identificadorUnico = manifesto.getIdentificadorUnico();
        if (identificadorUnico.length() > 100) {
            logger.error("❌ Manifesto com identificador_unico muito longo ({} caracteres, máximo 100) - sequence_code={}", 
                        identificadorUnico.length(), manifesto.getSequenceCode());
            throw new SQLException(String.format(
                "identificador_unico excedeu tamanho máximo: %d caracteres (máximo 100). sequence_code=%d", 
                identificadorUnico.length(), manifesto.getSequenceCode()));
        }
        
        logger.debug("→ Salvando manifesto sequence_code={}, identificador_unico={} (estrutura nova)", 
                    manifesto.getSequenceCode(), identificadorUnico);

        final String sql = String.format("""
            MERGE %s AS target
            USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?))
                AS source (sequence_code, identificador_unico, status, created_at, departured_at, closed_at, finished_at, mdfe_number, mdfe_key, mdfe_status, distribution_pole, classification, vehicle_plate, vehicle_type, vehicle_owner, driver_name, branch_nickname, vehicle_departure_km, closing_km, traveled_km, invoices_count, invoices_volumes, invoices_weight, total_taxed_weight, total_cubic_volume, invoices_value, manifest_freights_total, pick_sequence_code, contract_number, contract_type, calculation_type, cargo_type, daily_subtotal, total_cost, freight_subtotal, fuel_subtotal, toll_subtotal, driver_services_total, operational_expenses_total, inss_value, sest_senat_value, ir_value, paying_total, manual_km, generate_mdfe, monitoring_request, uniq_destinations_count, creation_user_name, adjustment_user_name, metadata, data_extracao)
            ON target.sequence_code = source.sequence_code
               AND COALESCE(target.pick_sequence_code, -1) = COALESCE(source.pick_sequence_code, -1)
               AND COALESCE(target.mdfe_number, -1) = COALESCE(source.mdfe_number, -1)
            WHEN MATCHED THEN
                UPDATE SET
                    status = source.status,
                    created_at = source.created_at,
                    departured_at = source.departured_at,
                    closed_at = source.closed_at,
                    finished_at = source.finished_at,
                    mdfe_number = source.mdfe_number,
                    mdfe_key = source.mdfe_key,
                    mdfe_status = source.mdfe_status,
                    distribution_pole = source.distribution_pole,
                    classification = source.classification,
                    vehicle_plate = source.vehicle_plate,
                    vehicle_type = source.vehicle_type,
                    vehicle_owner = source.vehicle_owner,
                    driver_name = source.driver_name,
                    branch_nickname = source.branch_nickname,
                    vehicle_departure_km = source.vehicle_departure_km,
                    closing_km = source.closing_km,
                    traveled_km = source.traveled_km,
                    invoices_count = source.invoices_count,
                    invoices_volumes = source.invoices_volumes,
                    invoices_weight = source.invoices_weight,
                    total_taxed_weight = source.total_taxed_weight,
                    total_cubic_volume = source.total_cubic_volume,
                    invoices_value = source.invoices_value,
                    manifest_freights_total = source.manifest_freights_total,
                    pick_sequence_code = source.pick_sequence_code,
                    contract_number = source.contract_number,
                    contract_type = source.contract_type,
                    calculation_type = source.calculation_type,
                    cargo_type = source.cargo_type,
                    daily_subtotal = source.daily_subtotal,
                    total_cost = source.total_cost,
                    freight_subtotal = source.freight_subtotal,
                    fuel_subtotal = source.fuel_subtotal,
                    toll_subtotal = source.toll_subtotal,
                    driver_services_total = source.driver_services_total,
                    operational_expenses_total = source.operational_expenses_total,
                    inss_value = source.inss_value,
                    sest_senat_value = source.sest_senat_value,
                    ir_value = source.ir_value,
                    paying_total = source.paying_total,
                    manual_km = source.manual_km,
                    generate_mdfe = source.generate_mdfe,
                    monitoring_request = source.monitoring_request,
                    uniq_destinations_count = source.uniq_destinations_count,
                    creation_user_name = source.creation_user_name,
                    adjustment_user_name = source.adjustment_user_name,
                    metadata = source.metadata,
                    identificador_unico = source.identificador_unico,
                    data_extracao = source.data_extracao
            WHEN NOT MATCHED THEN
                INSERT (sequence_code, identificador_unico, status, created_at, departured_at, closed_at, finished_at, mdfe_number, mdfe_key, mdfe_status, distribution_pole, classification, vehicle_plate, vehicle_type, vehicle_owner, driver_name, branch_nickname, vehicle_departure_km, closing_km, traveled_km, invoices_count, invoices_volumes, invoices_weight, total_taxed_weight, total_cubic_volume, invoices_value, manifest_freights_total, pick_sequence_code, contract_number, contract_type, calculation_type, cargo_type, daily_subtotal, total_cost, freight_subtotal, fuel_subtotal, toll_subtotal, driver_services_total, operational_expenses_total, inss_value, sest_senat_value, ir_value, paying_total, manual_km, generate_mdfe, monitoring_request, uniq_destinations_count, creation_user_name, adjustment_user_name, metadata, data_extracao)
                VALUES (source.sequence_code, source.identificador_unico, source.status, source.created_at, source.departured_at, source.closed_at, source.finished_at, source.mdfe_number, source.mdfe_key, source.mdfe_status, source.distribution_pole, source.classification, source.vehicle_plate, source.vehicle_type, source.vehicle_owner, source.driver_name, source.branch_nickname, source.vehicle_departure_km, source.closing_km, source.traveled_km, source.invoices_count, source.invoices_volumes, source.invoices_weight, source.total_taxed_weight, source.total_cubic_volume, source.invoices_value, source.manifest_freights_total, source.pick_sequence_code, source.contract_number, source.contract_type, source.calculation_type, source.cargo_type, source.daily_subtotal, source.total_cost, source.freight_subtotal, source.fuel_subtotal, source.toll_subtotal, source.driver_services_total, source.operational_expenses_total, source.inss_value, source.sest_senat_value, source.ir_value, source.paying_total, source.manual_km, source.generate_mdfe, source.monitoring_request, source.uniq_destinations_count, source.creation_user_name, source.adjustment_user_name, source.metadata, source.data_extracao);
            """, NOME_TABELA);

        try (PreparedStatement statement = conexao.prepareStatement(sql)) {
            // Define os parâmetros de forma segura e na ordem correta conforme MERGE SQL
            int paramIndex = 1;
            statement.setObject(paramIndex++, manifesto.getSequenceCode(), Types.BIGINT);
            // Identificador único NÃO deve ser truncado - validação já foi feita acima
            // Se exceder 100 caracteres, a validação lança exceção (melhor que truncar e causar colisões)
            statement.setString(paramIndex++, identificadorUnico);
            statement.setString(paramIndex++, truncate(manifesto.getStatus(), 50, "status"));
            // Usar helper methods para tipos especiais (DATETIMEOFFSET)
            if (manifesto.getCreatedAt() != null) {
                statement.setObject(paramIndex++, manifesto.getCreatedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setNull(paramIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            if (manifesto.getDeparturedAt() != null) {
                statement.setObject(paramIndex++, manifesto.getDeparturedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setNull(paramIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            if (manifesto.getClosedAt() != null) {
                statement.setObject(paramIndex++, manifesto.getClosedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setNull(paramIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            if (manifesto.getFinishedAt() != null) {
                statement.setObject(paramIndex++, manifesto.getFinishedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setNull(paramIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            statement.setObject(paramIndex++, manifesto.getMdfeNumber(), Types.INTEGER);
            statement.setString(paramIndex++, truncate(manifesto.getMdfeKey(), 100, "mdfe_key"));
            statement.setString(paramIndex++, truncate(manifesto.getMdfeStatus(), 50, "mdfe_status"));
            statement.setString(paramIndex++, truncate(manifesto.getDistributionPole(), 255, "distribution_pole"));
            statement.setString(paramIndex++, truncate(manifesto.getClassification(), 255, "classification"));
            statement.setString(paramIndex++, truncate(manifesto.getVehiclePlate(), 10, "vehicle_plate"));
            statement.setString(paramIndex++, truncate(manifesto.getVehicleType(), 255, "vehicle_type"));
            statement.setString(paramIndex++, truncate(manifesto.getVehicleOwner(), 255, "vehicle_owner"));
            statement.setString(paramIndex++, truncate(manifesto.getDriverName(), 255, "driver_name"));
            statement.setString(paramIndex++, truncate(manifesto.getBranchNickname(), 255, "branch_nickname"));
            statement.setObject(paramIndex++, manifesto.getVehicleDepartureKm(), Types.INTEGER);
            statement.setObject(paramIndex++, manifesto.getClosingKm(), Types.INTEGER);
            statement.setObject(paramIndex++, manifesto.getTraveledKm(), Types.INTEGER);
            statement.setObject(paramIndex++, manifesto.getInvoicesCount(), Types.INTEGER);
            statement.setObject(paramIndex++, manifesto.getInvoicesVolumes(), Types.INTEGER);
            setBigDecimalParameter(statement, paramIndex++, manifesto.getInvoicesWeight());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getTotalTaxedWeight());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getTotalCubicVolume());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getInvoicesValue());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getManifestFreightsTotal());
            statement.setObject(paramIndex++, manifesto.getPickSequenceCode(), Types.BIGINT);
            statement.setString(paramIndex++, manifesto.getContractNumber());
            statement.setString(paramIndex++, truncate(manifesto.getContractType(), 50, "contract_type"));
            statement.setString(paramIndex++, truncate(manifesto.getCalculationType(), 50, "calculation_type"));
            statement.setString(paramIndex++, truncate(manifesto.getCargoType(), 255, "cargo_type"));
            setBigDecimalParameter(statement, paramIndex++, manifesto.getDailySubtotal());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getTotalCost());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getFreightSubtotal());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getFuelSubtotal());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getTollSubtotal());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getDriverServicesTotal());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getOperationalExpensesTotal());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getInssValue());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getSestSenatValue());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getIrValue());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getPayingTotal());
            statement.setObject(paramIndex++, manifesto.getManualKm(), Types.BIT);
            statement.setObject(paramIndex++, manifesto.getGenerateMdfe(), Types.BIT);
            statement.setObject(paramIndex++, manifesto.getMonitoringRequest(), Types.BIT);
            statement.setObject(paramIndex++, manifesto.getUniqDestinationsCount(), Types.INTEGER);
            statement.setString(paramIndex++, truncate(manifesto.getCreationUserName(), 255, "creation_user_name"));
            statement.setString(paramIndex++, truncate(manifesto.getAdjustmentUserName(), 255, "adjustment_user_name"));
            statement.setString(paramIndex++, manifesto.getMetadata()); // JSON - sem limite, mas pode ser grande
            setInstantParameter(statement, paramIndex++, Instant.now()); // UTC timestamp
            
            // ✅ VALIDAR número de parâmetros
            final int expectedParams = 51;
            if (paramIndex != expectedParams + 1) { // +1 porque paramIndex é 1-based
                throw new SQLException(String.format(
                    "ERRO DE PROGRAMAÇÃO: SQL espera %d parâmetros, mas apenas %d foram setados!",
                    expectedParams, paramIndex - 1));
            }

            final int rowsAffected = statement.executeUpdate();
            
            // ✅ VERIFICAR rows affected
            if (rowsAffected == 0) {
                logger.error("❌ MERGE retornou 0 linhas para manifesto sequence_code={}. " +
                           "Possível violação de constraint ou dados inválidos.", 
                           manifesto.getSequenceCode());
                // Não lançar exceção aqui - deixar o AbstractRepository tratar
                return 0;
            }
            
            if (rowsAffected > 0) {
                logger.debug("✅ Manifesto sequence_code={}, identificador_unico={} salvo com sucesso: {} linha(s) afetada(s)", 
                            manifesto.getSequenceCode(), manifesto.getIdentificadorUnico(), rowsAffected);
                final String sqlUpdateExtras = String.format(
                    """
                        UPDATE %s SET \
                        mobile_read_at = ?, km = ?, \
                        delivery_manifest_items_count = ?, transfer_manifest_items_count = ?, pick_manifest_items_count = ?, dispatch_draft_manifest_items_count = ?, consolidation_manifest_items_count = ?, reverse_pick_manifest_items_count = ?, manifest_items_count = ?, finalized_manifest_items_count = ?, \
                        calculated_pick_count = ?, calculated_delivery_count = ?, calculated_dispatch_count = ?, calculated_consolidation_count = ?, calculated_reverse_pick_count = ?, \
                        pick_subtotal = ?, delivery_subtotal = ?, dispatch_subtotal = ?, consolidation_subtotal = ?, reverse_pick_subtotal = ?, advance_subtotal = ?, fleet_costs_subtotal = ?, additionals_subtotal = ?, discounts_subtotal = ?, discount_value = ?, \
                        adjustment_comments = ?, contract_status = ?, iks_id = ?, programacao_sequence_code = ?, programacao_starting_at = ?, programacao_ending_at = ?, \
                        trailer1_license_plate = ?, trailer1_weight_capacity = ?, trailer2_license_plate = ?, trailer2_weight_capacity = ?, vehicle_weight_capacity = ?, vehicle_cubic_weight = ?, \
                        unloading_recipient_names = ?, delivery_region_names = ?, programacao_cliente = ?, programacao_tipo_servico = ? \
                        WHERE sequence_code = ? AND COALESCE(pick_sequence_code, -1) = COALESCE(?, -1) AND COALESCE(mdfe_number, -1) = COALESCE(?, -1)""",
                    NOME_TABELA);
                try (PreparedStatement upd = conexao.prepareStatement(sqlUpdateExtras)) {
                    int i = 1;
                    if (manifesto.getMobileReadAt() != null) { upd.setObject(i++, manifesto.getMobileReadAt(), Types.TIMESTAMP_WITH_TIMEZONE); } else { upd.setNull(i++, Types.TIMESTAMP_WITH_TIMEZONE); }
                    setBigDecimalParameter(upd, i++, manifesto.getKm());
                    upd.setObject(i++, manifesto.getDeliveryManifestItemsCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getTransferManifestItemsCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getPickManifestItemsCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getDispatchDraftManifestItemsCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getConsolidationManifestItemsCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getReversePickManifestItemsCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getManifestItemsCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getFinalizedManifestItemsCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getCalculatedPickCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getCalculatedDeliveryCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getCalculatedDispatchCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getCalculatedConsolidationCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getCalculatedReversePickCount(), Types.INTEGER);
                    setBigDecimalParameter(upd, i++, manifesto.getPickSubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getDeliverySubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getDispatchSubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getConsolidationSubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getReversePickSubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getAdvanceSubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getFleetCostsSubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getAdditionalsSubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getDiscountsSubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getDiscountValue());
                    upd.setString(i++, truncate(manifesto.getAdjustmentComments(), 4000, "adjustment_comments"));
                    upd.setString(i++, truncate(manifesto.getContractStatus(), 50, "contract_status"));
                    upd.setString(i++, truncate(manifesto.getIksId(), 100, "iks_id"));
                    upd.setString(i++, truncate(manifesto.getProgramacaoSequenceCode(), 50, "programacao_sequence_code"));
                    if (manifesto.getProgramacaoStartingAt() != null) { upd.setObject(i++, manifesto.getProgramacaoStartingAt(), Types.TIMESTAMP_WITH_TIMEZONE); } else { upd.setNull(i++, Types.TIMESTAMP_WITH_TIMEZONE); }
                    if (manifesto.getProgramacaoEndingAt() != null) { upd.setObject(i++, manifesto.getProgramacaoEndingAt(), Types.TIMESTAMP_WITH_TIMEZONE); } else { upd.setNull(i++, Types.TIMESTAMP_WITH_TIMEZONE); }
                    upd.setString(i++, truncate(manifesto.getTrailer1LicensePlate(), 10, "trailer1_license_plate"));
                    setBigDecimalParameter(upd, i++, manifesto.getTrailer1WeightCapacity());
                    upd.setString(i++, truncate(manifesto.getTrailer2LicensePlate(), 10, "trailer2_license_plate"));
                    setBigDecimalParameter(upd, i++, manifesto.getTrailer2WeightCapacity());
                    setBigDecimalParameter(upd, i++, manifesto.getVehicleWeightCapacity());
                    setBigDecimalParameter(upd, i++, manifesto.getVehicleCubicWeight());
                    upd.setString(i++, manifesto.getUnloadingRecipientNames());
                    upd.setString(i++, manifesto.getDeliveryRegionNames());
                    upd.setString(i++, truncate(manifesto.getProgramacaoCliente(), 255, "programacao_cliente"));
                    upd.setString(i++, truncate(manifesto.getProgramacaoTipoServico(), 255, "programacao_tipo_servico"));
                    upd.setObject(i++, manifesto.getSequenceCode(), Types.BIGINT);
                    upd.setObject(i++, manifesto.getPickSequenceCode(), Types.BIGINT);
                    upd.setObject(i++, manifesto.getMdfeNumber(), Types.INTEGER);
                    upd.executeUpdate();
                }
            }
            
            return rowsAffected;
            
        } catch (final SQLException e) {
            logger.error("❌ SQLException ao salvar manifesto sequence_code={}: {} - SQLState: {} - ErrorCode: {}", 
                        manifesto.getSequenceCode(), e.getMessage(), e.getSQLState(), e.getErrorCode(), e);
            
            // Log stacktrace completo para constraint violations (SQLState 23xxx)
            if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                logger.error("Constraint violation detectada - stacktrace completo:", e);
            }
            
            // Re-lançar exceção para que o AbstractRepository possa tratar
            throw e;
        }
    }
    
    /**
     * Executa MERGE com estrutura antiga (apenas sequence_code como PRIMARY KEY).
     * Este método é usado quando a tabela ainda não foi migrada para chave composta.
     * 
     * ⚠️ ATENÇÃO: Esta estrutura NÃO preserva duplicados naturais!
     * Manifestos com mesmo sequence_code serão sobrescritos.
     */
    private int executarMergeEstruturaAntiga(final Connection conexao, final ManifestoEntity manifesto) throws SQLException {
        logger.debug("→ Salvando manifesto sequence_code={} (estrutura antiga - sem identificador_unico)", 
                    manifesto.getSequenceCode());
        
        // MERGE usando apenas sequence_code como chave (estrutura antiga)
        // NOTA: Esta estrutura NÃO preserva duplicados naturais (múltiplos MDF-es ou coletas)
        // Recomenda-se migrar para estrutura nova com chave composta
        final String sql = String.format("""
            MERGE %s AS target
            USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?))
                AS source (sequence_code, status, created_at, departured_at, closed_at, finished_at, mdfe_number, mdfe_key, mdfe_status, distribution_pole, classification, vehicle_plate, vehicle_type, vehicle_owner, driver_name, branch_nickname, vehicle_departure_km, closing_km, traveled_km, invoices_count, invoices_volumes, invoices_weight, total_taxed_weight, total_cubic_volume, invoices_value, manifest_freights_total, pick_sequence_code, contract_number, daily_subtotal, total_cost, operational_expenses_total, inss_value, sest_senat_value, ir_value, paying_total, creation_user_name, adjustment_user_name, metadata, data_extracao)
            ON target.sequence_code = source.sequence_code
            WHEN MATCHED THEN
                UPDATE SET
                    status = source.status,
                    created_at = source.created_at,
                    departured_at = source.departured_at,
                    closed_at = source.closed_at,
                    finished_at = source.finished_at,
                    mdfe_number = source.mdfe_number,
                    mdfe_key = source.mdfe_key,
                    mdfe_status = source.mdfe_status,
                    distribution_pole = source.distribution_pole,
                    classification = source.classification,
                    vehicle_plate = source.vehicle_plate,
                    vehicle_type = source.vehicle_type,
                    vehicle_owner = source.vehicle_owner,
                    driver_name = source.driver_name,
                    branch_nickname = source.branch_nickname,
                    vehicle_departure_km = source.vehicle_departure_km,
                    closing_km = source.closing_km,
                    traveled_km = source.traveled_km,
                    invoices_count = source.invoices_count,
                    invoices_volumes = source.invoices_volumes,
                    invoices_weight = source.invoices_weight,
                    total_taxed_weight = source.total_taxed_weight,
                    total_cubic_volume = source.total_cubic_volume,
                    invoices_value = source.invoices_value,
                    manifest_freights_total = source.manifest_freights_total,
                    pick_sequence_code = source.pick_sequence_code,
                    contract_number = source.contract_number,
                    daily_subtotal = source.daily_subtotal,
                    total_cost = source.total_cost,
                    operational_expenses_total = source.operational_expenses_total,
                    inss_value = source.inss_value,
                    sest_senat_value = source.sest_senat_value,
                    ir_value = source.ir_value,
                    paying_total = source.paying_total,
                    creation_user_name = source.creation_user_name,
                    adjustment_user_name = source.adjustment_user_name,
                    metadata = source.metadata,
                    data_extracao = source.data_extracao
            WHEN NOT MATCHED THEN
                INSERT (sequence_code, status, created_at, departured_at, closed_at, finished_at, mdfe_number, mdfe_key, mdfe_status, distribution_pole, classification, vehicle_plate, vehicle_type, vehicle_owner, driver_name, branch_nickname, vehicle_departure_km, closing_km, traveled_km, invoices_count, invoices_volumes, invoices_weight, total_taxed_weight, total_cubic_volume, invoices_value, manifest_freights_total, pick_sequence_code, contract_number, daily_subtotal, total_cost, operational_expenses_total, inss_value, sest_senat_value, ir_value, paying_total, creation_user_name, adjustment_user_name, metadata, data_extracao)
                VALUES (source.sequence_code, source.status, source.created_at, source.departured_at, source.closed_at, source.finished_at, source.mdfe_number, source.mdfe_key, source.mdfe_status, source.distribution_pole, source.classification, source.vehicle_plate, source.vehicle_type, source.vehicle_owner, source.driver_name, source.branch_nickname, source.vehicle_departure_km, source.closing_km, source.traveled_km, source.invoices_count, source.invoices_volumes, source.invoices_weight, source.total_taxed_weight, source.total_cubic_volume, source.invoices_value, source.manifest_freights_total, source.pick_sequence_code, source.contract_number, source.daily_subtotal, source.total_cost, source.operational_expenses_total, source.inss_value, source.sest_senat_value, source.ir_value, source.paying_total, source.creation_user_name, source.adjustment_user_name, source.metadata, source.data_extracao);
            """, NOME_TABELA);

        try (PreparedStatement statement = conexao.prepareStatement(sql)) {
            // Define os parâmetros (39 parâmetros - SEM identificador_unico)
            int paramIndex = 1;
            statement.setObject(paramIndex++, manifesto.getSequenceCode(), Types.BIGINT);
            statement.setString(paramIndex++, truncate(manifesto.getStatus(), 50, "status"));
            // Usar helper methods para tipos especiais (DATETIMEOFFSET)
            if (manifesto.getCreatedAt() != null) {
                statement.setObject(paramIndex++, manifesto.getCreatedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setNull(paramIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            if (manifesto.getDeparturedAt() != null) {
                statement.setObject(paramIndex++, manifesto.getDeparturedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setNull(paramIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            if (manifesto.getClosedAt() != null) {
                statement.setObject(paramIndex++, manifesto.getClosedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setNull(paramIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            if (manifesto.getFinishedAt() != null) {
                statement.setObject(paramIndex++, manifesto.getFinishedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setNull(paramIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            statement.setObject(paramIndex++, manifesto.getMdfeNumber(), Types.INTEGER);
            statement.setString(paramIndex++, truncate(manifesto.getMdfeKey(), 100, "mdfe_key"));
            statement.setString(paramIndex++, truncate(manifesto.getMdfeStatus(), 50, "mdfe_status"));
            statement.setString(paramIndex++, truncate(manifesto.getDistributionPole(), 255, "distribution_pole"));
            statement.setString(paramIndex++, truncate(manifesto.getClassification(), 255, "classification"));
            statement.setString(paramIndex++, truncate(manifesto.getVehiclePlate(), 10, "vehicle_plate"));
            statement.setString(paramIndex++, truncate(manifesto.getVehicleType(), 255, "vehicle_type"));
            statement.setString(paramIndex++, truncate(manifesto.getVehicleOwner(), 255, "vehicle_owner"));
            statement.setString(paramIndex++, truncate(manifesto.getDriverName(), 255, "driver_name"));
            statement.setString(paramIndex++, truncate(manifesto.getBranchNickname(), 255, "branch_nickname"));
            statement.setObject(paramIndex++, manifesto.getVehicleDepartureKm(), Types.INTEGER);
            statement.setObject(paramIndex++, manifesto.getClosingKm(), Types.INTEGER);
            statement.setObject(paramIndex++, manifesto.getTraveledKm(), Types.INTEGER);
            statement.setObject(paramIndex++, manifesto.getInvoicesCount(), Types.INTEGER);
            statement.setObject(paramIndex++, manifesto.getInvoicesVolumes(), Types.INTEGER);
            setBigDecimalParameter(statement, paramIndex++, manifesto.getInvoicesWeight());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getTotalTaxedWeight());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getTotalCubicVolume());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getInvoicesValue());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getManifestFreightsTotal());
            statement.setObject(paramIndex++, manifesto.getPickSequenceCode(), Types.BIGINT);
            statement.setString(paramIndex++, manifesto.getContractNumber());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getDailySubtotal());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getTotalCost());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getOperationalExpensesTotal());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getInssValue());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getSestSenatValue());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getIrValue());
            setBigDecimalParameter(statement, paramIndex++, manifesto.getPayingTotal());
            statement.setString(paramIndex++, truncate(manifesto.getCreationUserName(), 255, "creation_user_name"));
            statement.setString(paramIndex++, truncate(manifesto.getAdjustmentUserName(), 255, "adjustment_user_name"));
            statement.setString(paramIndex++, manifesto.getMetadata());
            setInstantParameter(statement, paramIndex++, Instant.now());
            
            // ✅ VALIDAR número de parâmetros (39 para estrutura antiga)
            final int expectedParams = 39;
            if (paramIndex != expectedParams + 1) {
                throw new SQLException(String.format(
                    "ERRO DE PROGRAMAÇÃO: SQL espera %d parâmetros, mas apenas %d foram setados!",
                    expectedParams, paramIndex - 1));
            }

            final int rowsAffected = statement.executeUpdate();
            
            if (rowsAffected == 0) {
                logger.error("❌ MERGE retornou 0 linhas para manifesto sequence_code={}. " +
                           "Possível violação de constraint ou dados inválidos.", 
                           manifesto.getSequenceCode());
                return 0;
            }
            
            if (rowsAffected > 0) {
                logger.debug("✅ Manifesto sequence_code={} salvo com sucesso (estrutura antiga): {} linha(s) afetada(s)", 
                            manifesto.getSequenceCode(), rowsAffected);
                final String sqlUpdateExtras = String.format(
                    """
                        UPDATE %s SET \
                        mobile_read_at = ?, km = ?, \
                        delivery_manifest_items_count = ?, transfer_manifest_items_count = ?, pick_manifest_items_count = ?, dispatch_draft_manifest_items_count = ?, consolidation_manifest_items_count = ?, reverse_pick_manifest_items_count = ?, manifest_items_count = ?, finalized_manifest_items_count = ?, \
                        calculated_pick_count = ?, calculated_delivery_count = ?, calculated_dispatch_count = ?, calculated_consolidation_count = ?, calculated_reverse_pick_count = ?, \
                        pick_subtotal = ?, delivery_subtotal = ?, dispatch_subtotal = ?, consolidation_subtotal = ?, reverse_pick_subtotal = ?, advance_subtotal = ?, fleet_costs_subtotal = ?, additionals_subtotal = ?, discounts_subtotal = ?, discount_value = ?, \
                        adjustment_comments = ?, contract_status = ?, iks_id = ?, programacao_sequence_code = ?, programacao_starting_at = ?, programacao_ending_at = ?, \
                        trailer1_license_plate = ?, trailer1_weight_capacity = ?, trailer2_license_plate = ?, trailer2_weight_capacity = ?, vehicle_weight_capacity = ?, vehicle_cubic_weight = ?, \
                        unloading_recipient_names = ?, delivery_region_names = ?, programacao_cliente = ?, programacao_tipo_servico = ? \
                        WHERE sequence_code = ?""",
                    NOME_TABELA);
                try (PreparedStatement upd = conexao.prepareStatement(sqlUpdateExtras)) {
                    int i = 1;
                    if (manifesto.getMobileReadAt() != null) { upd.setObject(i++, manifesto.getMobileReadAt(), Types.TIMESTAMP_WITH_TIMEZONE); } else { upd.setNull(i++, Types.TIMESTAMP_WITH_TIMEZONE); }
                    setBigDecimalParameter(upd, i++, manifesto.getKm());
                    upd.setObject(i++, manifesto.getDeliveryManifestItemsCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getTransferManifestItemsCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getPickManifestItemsCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getDispatchDraftManifestItemsCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getConsolidationManifestItemsCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getReversePickManifestItemsCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getManifestItemsCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getFinalizedManifestItemsCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getCalculatedPickCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getCalculatedDeliveryCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getCalculatedDispatchCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getCalculatedConsolidationCount(), Types.INTEGER);
                    upd.setObject(i++, manifesto.getCalculatedReversePickCount(), Types.INTEGER);
                    setBigDecimalParameter(upd, i++, manifesto.getPickSubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getDeliverySubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getDispatchSubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getConsolidationSubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getReversePickSubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getAdvanceSubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getFleetCostsSubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getAdditionalsSubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getDiscountsSubtotal());
                    setBigDecimalParameter(upd, i++, manifesto.getDiscountValue());
                    upd.setString(i++, truncate(manifesto.getAdjustmentComments(), 4000, "adjustment_comments"));
                    upd.setString(i++, truncate(manifesto.getContractStatus(), 50, "contract_status"));
                    upd.setString(i++, truncate(manifesto.getIksId(), 100, "iks_id"));
                    upd.setString(i++, truncate(manifesto.getProgramacaoSequenceCode(), 50, "programacao_sequence_code"));
                    if (manifesto.getProgramacaoStartingAt() != null) { upd.setObject(i++, manifesto.getProgramacaoStartingAt(), Types.TIMESTAMP_WITH_TIMEZONE); } else { upd.setNull(i++, Types.TIMESTAMP_WITH_TIMEZONE); }
                    if (manifesto.getProgramacaoEndingAt() != null) { upd.setObject(i++, manifesto.getProgramacaoEndingAt(), Types.TIMESTAMP_WITH_TIMEZONE); } else { upd.setNull(i++, Types.TIMESTAMP_WITH_TIMEZONE); }
                    upd.setString(i++, truncate(manifesto.getTrailer1LicensePlate(), 10, "trailer1_license_plate"));
                    setBigDecimalParameter(upd, i++, manifesto.getTrailer1WeightCapacity());
                    upd.setString(i++, truncate(manifesto.getTrailer2LicensePlate(), 10, "trailer2_license_plate"));
                    setBigDecimalParameter(upd, i++, manifesto.getTrailer2WeightCapacity());
                    setBigDecimalParameter(upd, i++, manifesto.getVehicleWeightCapacity());
                    setBigDecimalParameter(upd, i++, manifesto.getVehicleCubicWeight());
                    upd.setString(i++, manifesto.getUnloadingRecipientNames());
                    upd.setString(i++, manifesto.getDeliveryRegionNames());
                    upd.setString(i++, truncate(manifesto.getProgramacaoCliente(), 255, "programacao_cliente"));
                    upd.setString(i++, truncate(manifesto.getProgramacaoTipoServico(), 255, "programacao_tipo_servico"));
                    upd.setObject(i++, manifesto.getSequenceCode(), Types.BIGINT);
                    upd.executeUpdate();
                }
            }
            
            return rowsAffected;
            
        } catch (final SQLException e) {
            logger.error("❌ SQLException ao salvar manifesto sequence_code={} (estrutura antiga): {} - SQLState: {} - ErrorCode: {}", 
                        manifesto.getSequenceCode(), e.getMessage(), e.getSQLState(), e.getErrorCode(), e);
            throw e;
        }
    }
    
    /**
     * Trunca uma string para o tamanho máximo especificado.
     * Loga warning quando há truncamento para facilitar debug.
     * 
     * @param value Valor a ser truncado
     * @param maxLength Tamanho máximo permitido
     * @param fieldName Nome do campo (para log)
     * @return String truncada ou original se menor que maxLength
     */
    private String truncate(final String value, final int maxLength, final String fieldName) {
        if (value != null && value.length() > maxLength) {
            logger.warn("⚠️ Truncando campo {} de {} para {} chars (sequence_code pode estar próximo): '{}'...", 
                       fieldName, value.length(), maxLength, 
                       value.substring(0, Math.min(50, value.length())));
            return value.substring(0, maxLength);
        }
        return value;
    }
}
