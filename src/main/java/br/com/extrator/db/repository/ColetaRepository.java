package br.com.extrator.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.ColetaEntity;

/**
 * Repositório para operações de persistência da entidade ColetaEntity.
 * Implementa a arquitetura de persistência híbrida: colunas-chave para indexação
 * e uma coluna de metadados para resiliência e completude dos dados.
 * Utiliza operações MERGE (UPSERT) com a chave primária (id) da coleta.
 */
public class ColetaRepository extends AbstractRepository<ColetaEntity> {
    private static final Logger logger = LoggerFactory.getLogger(ColetaRepository.class);
    private static final String NOME_TABELA = "coletas";
    private static boolean viewVerificada = false;

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }

    /**
     * Cria a tabela 'coletas' se ela não existir, seguindo o modelo híbrido.
     * A estrutura contém apenas colunas essenciais para busca e uma coluna NVARCHAR(MAX)
     * para armazenar o JSON completo, garantindo resiliência.
     */
    @Override
    protected void criarTabelaSeNaoExistir(final Connection conexao) throws SQLException {
        if (!verificarTabelaExiste(conexao, NOME_TABELA)) {
            logger.info("Criando tabela {} com arquitetura híbrida...", NOME_TABELA);

            final String sql = """
                CREATE TABLE dbo.coletas (
                    -- Coluna de Chave Primária (String, conforme API GraphQL)
                    id NVARCHAR(50) PRIMARY KEY,

                    -- Colunas Essenciais para Indexação e Relatórios
                    sequence_code BIGINT,
                    request_date DATE,
                    service_date DATE,
                    status NVARCHAR(50),
                    total_value DECIMAL(18, 2),
                    total_weight DECIMAL(18, 3),
                    total_volumes INT,

                    -- Campos Expandidos (22 campos do CSV)
                    cliente_id BIGINT,
                    cliente_nome NVARCHAR(255),
                    cliente_doc NVARCHAR(50),
                    local_coleta NVARCHAR(500),
                    numero_coleta NVARCHAR(50),
                    complemento_coleta NVARCHAR(255),
                    cidade_coleta NVARCHAR(255),
                    bairro_coleta NVARCHAR(255),
                    uf_coleta NVARCHAR(10),
                    cep_coleta NVARCHAR(20),
                    filial_id BIGINT,
                    filial_nome NVARCHAR(255),
                    filial_cnpj NVARCHAR(50),
                    usuario_id BIGINT,
                    usuario_nome NVARCHAR(255),
                    request_hour NVARCHAR(20),
                    service_start_hour NVARCHAR(20),
                    finish_date DATE,
                    service_end_hour NVARCHAR(20),
                    requester NVARCHAR(255),
                    taxed_weight DECIMAL(18, 3),
                    comments NVARCHAR(MAX),
                    agent_id BIGINT,
                    manifest_item_pick_id BIGINT,
                    vehicle_type_id BIGINT,

                    cancellation_reason NVARCHAR(MAX),
                    cancellation_user_id BIGINT,
                    cargo_classification_id BIGINT,
                    cost_center_id BIGINT,
                    destroy_reason NVARCHAR(MAX),
                    destroy_user_id BIGINT,
                    invoices_cubed_weight DECIMAL(18, 3),
                    lunch_break_end_hour NVARCHAR(20),
                    lunch_break_start_hour NVARCHAR(20),
                    notification_email NVARCHAR(255),
                    notification_phone NVARCHAR(255),
                    pick_type_id BIGINT,
                    pickup_location_id BIGINT,
                    status_updated_at NVARCHAR(50),

                    -- Coluna de Metadados para Resiliência e Completude
                    metadata NVARCHAR(MAX),

                    -- Coluna de Auditoria
                    data_extracao DATETIME2 DEFAULT GETDATE(),
                    -- Constraint para chave de negócio
                    UNIQUE (sequence_code)
                )
                """;

            executarDDL(conexao, sql);
            logger.info("Tabela {} criada com sucesso.", NOME_TABELA);
        } else {
            // Ajuste defensivo de schema: garantir que colunas de horas comportem valores como "1999-12-31" ou "HH:mm:ss"
            // Nota: A validação no ColetaMapper já trata datas como null ou extrai HH:mm[:ss],
            // mas este ajuste garante que o schema suporte eventuais variações da API
            try {
                executarDDL(conexao, "ALTER TABLE dbo.coletas ALTER COLUMN request_hour NVARCHAR(20) NULL");
                logger.debug("Coluna request_hour ajustada para NVARCHAR(20)");
            } catch (final SQLException e) {
                logger.warn("⚠️ Não foi possível ajustar coluna request_hour: {} (pode já estar no tamanho correto)", e.getMessage());
            }
            try {
                executarDDL(conexao, "ALTER TABLE dbo.coletas ALTER COLUMN service_start_hour NVARCHAR(20) NULL");
                logger.debug("Coluna service_start_hour ajustada para NVARCHAR(20)");
            } catch (final SQLException e) {
                logger.warn("⚠️ Não foi possível ajustar coluna service_start_hour: {} (pode já estar no tamanho correto)", e.getMessage());
            }
            try {
                executarDDL(conexao, "ALTER TABLE dbo.coletas ALTER COLUMN service_end_hour NVARCHAR(20) NULL");
                logger.debug("Coluna service_end_hour ajustada para NVARCHAR(20)");
            } catch (final SQLException e) {
                logger.warn("⚠️ Não foi possível ajustar coluna service_end_hour: {} (pode já estar no tamanho correto)", e.getMessage());
            }
            try {
                executarDDL(conexao, "ALTER TABLE dbo.coletas ADD cancellation_reason NVARCHAR(MAX) NULL");
                logger.debug("Coluna cancellation_reason adicionada");
            } catch (final SQLException e) {
                logger.warn("⚠️ Não foi possível adicionar coluna cancellation_reason: {} (pode já existir)", e.getMessage());
            }
            try {
                executarDDL(conexao, "ALTER TABLE dbo.coletas ADD cancellation_user_id BIGINT NULL");
                logger.debug("Coluna cancellation_user_id adicionada");
            } catch (final SQLException e) {
                logger.warn("⚠️ Não foi possível adicionar coluna cancellation_user_id: {} (pode já existir)", e.getMessage());
            }
            try {
                executarDDL(conexao, "ALTER TABLE dbo.coletas ADD cargo_classification_id BIGINT NULL");
                logger.debug("Coluna cargo_classification_id adicionada");
            } catch (final SQLException e) {
                logger.warn("⚠️ Não foi possível adicionar coluna cargo_classification_id: {} (pode já existir)", e.getMessage());
            }
            try {
                executarDDL(conexao, "ALTER TABLE dbo.coletas ADD cost_center_id BIGINT NULL");
                logger.debug("Coluna cost_center_id adicionada");
            } catch (final SQLException e) {
                logger.warn("⚠️ Não foi possível adicionar coluna cost_center_id: {} (pode já existir)", e.getMessage());
            }
            try {
                executarDDL(conexao, "ALTER TABLE dbo.coletas ADD destroy_reason NVARCHAR(MAX) NULL");
                logger.debug("Coluna destroy_reason adicionada");
            } catch (final SQLException e) {
                logger.warn("⚠️ Não foi possível adicionar coluna destroy_reason: {} (pode já existir)", e.getMessage());
            }
            try {
                executarDDL(conexao, "ALTER TABLE dbo.coletas ADD destroy_user_id BIGINT NULL");
                logger.debug("Coluna destroy_user_id adicionada");
            } catch (final SQLException e) {
                logger.warn("⚠️ Não foi possível adicionar coluna destroy_user_id: {} (pode já existir)", e.getMessage());
            }
            try {
                executarDDL(conexao, "ALTER TABLE dbo.coletas ADD invoices_cubed_weight DECIMAL(18, 3) NULL");
                logger.debug("Coluna invoices_cubed_weight adicionada");
            } catch (final SQLException e) {
                logger.warn("⚠️ Não foi possível adicionar coluna invoices_cubed_weight: {} (pode já existir)", e.getMessage());
            }
            try {
                executarDDL(conexao, "ALTER TABLE dbo.coletas ALTER COLUMN lunch_break_end_hour NVARCHAR(20) NULL");
                logger.debug("Coluna lunch_break_end_hour ajustada para NVARCHAR(20)");
            } catch (final SQLException e) {
                logger.warn("⚠️ Não foi possível ajustar coluna lunch_break_end_hour: {} (pode já estar no tamanho correto)", e.getMessage());
            }
            try {
                executarDDL(conexao, "ALTER TABLE dbo.coletas ALTER COLUMN lunch_break_start_hour NVARCHAR(20) NULL");
                logger.debug("Coluna lunch_break_start_hour ajustada para NVARCHAR(20)");
            } catch (final SQLException e) {
                logger.warn("⚠️ Não foi possível ajustar coluna lunch_break_start_hour: {} (pode já estar no tamanho correto)", e.getMessage());
            }
            try {
                executarDDL(conexao, "ALTER TABLE dbo.coletas ADD notification_email NVARCHAR(255) NULL");
                logger.debug("Coluna notification_email adicionada");
            } catch (final SQLException e) {
                logger.warn("⚠️ Não foi possível adicionar coluna notification_email: {} (pode já existir)", e.getMessage());
            }
            try {
                executarDDL(conexao, "ALTER TABLE dbo.coletas ADD notification_phone NVARCHAR(255) NULL");
                logger.debug("Coluna notification_phone adicionada");
            } catch (final SQLException e) {
                logger.warn("⚠️ Não foi possível adicionar coluna notification_phone: {} (pode já existir)", e.getMessage());
            }
            try {
                executarDDL(conexao, "ALTER TABLE dbo.coletas ADD pick_type_id BIGINT NULL");
                logger.debug("Coluna pick_type_id adicionada");
            } catch (final SQLException e) {
                logger.warn("⚠️ Não foi possível adicionar coluna pick_type_id: {} (pode já existir)", e.getMessage());
            }
            try {
                executarDDL(conexao, "ALTER TABLE dbo.coletas ADD pickup_location_id BIGINT NULL");
                logger.debug("Coluna pickup_location_id adicionada");
            } catch (final SQLException e) {
                logger.warn("⚠️ Não foi possível adicionar coluna pickup_location_id: {} (pode já existir)", e.getMessage());
            }
            try {
                executarDDL(conexao, "ALTER TABLE dbo.coletas ADD status_updated_at NVARCHAR(50) NULL");
                logger.debug("Coluna status_updated_at adicionada");
            } catch (final SQLException e) {
                logger.warn("⚠️ Não foi possível adicionar coluna status_updated_at: {} (pode já existir)", e.getMessage());
            }

            adicionarColunaSeNaoExistir(conexao, "cliente_doc", "NVARCHAR(50)");
            adicionarColunaSeNaoExistir(conexao, "numero_coleta", "NVARCHAR(50)");
            adicionarColunaSeNaoExistir(conexao, "complemento_coleta", "NVARCHAR(255)");
            adicionarColunaSeNaoExistir(conexao, "bairro_coleta", "NVARCHAR(255)");
            adicionarColunaSeNaoExistir(conexao, "cep_coleta", "NVARCHAR(20)");
            adicionarColunaSeNaoExistir(conexao, "filial_id", "BIGINT");
            adicionarColunaSeNaoExistir(conexao, "filial_nome", "NVARCHAR(255)");
            adicionarColunaSeNaoExistir(conexao, "filial_cnpj", "NVARCHAR(50)");
        }
        if (!viewVerificada) {
            criarViewPowerBISeNaoExistir(conexao);
            viewVerificada = true;
            logger.info("View do Power BI verificada/atualizada para {}.", NOME_TABELA);
        }
    }

    private void adicionarColunaSeNaoExistir(final Connection conexao, final String nomeColuna, final String tipoDef) throws SQLException {
        final String verifica = "SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME='coletas' AND COLUMN_NAME='" + nomeColuna + "'";
        try (PreparedStatement stmt = conexao.prepareStatement(verifica); java.sql.ResultSet rs = stmt.executeQuery()) {
            if (!rs.next()) {
                final String alterar = "ALTER TABLE dbo.coletas ADD " + nomeColuna + " " + tipoDef;
                executarDDL(conexao, alterar);
                logger.info("Adicionada coluna {} em dbo.coletas", nomeColuna);
            }
        }
    }

    private void criarViewPowerBISeNaoExistir(final Connection conexao) throws SQLException {
        final String sqlView = """
            CREATE OR ALTER VIEW dbo.vw_coletas_powerbi AS
            SELECT
                id AS [ID],
                sequence_code AS [Coleta],
                request_date AS [Solicitacao],
                request_hour AS [Hora (Solicitacao)],
                service_date AS [Agendamento],
                service_start_hour AS [Horario (Inicio)],
                finish_date AS [Finalizacao],
                service_end_hour AS [Hora (Fim)],
                CASE LOWER(status)
                    WHEN 'pending' THEN N'pendente'
                    WHEN 'done' THEN N'concluído'
                    WHEN 'canceled' THEN N'cancelado'
                    WHEN 'finished' THEN N'finalizado'
                    WHEN 'treatment' THEN N'em tratamento'
                    WHEN 'in_transit' THEN N'em trânsito'
                    ELSE status
                END AS [Status],
                requester AS [Solicitante],
                total_volumes AS [Volumes],
                total_weight AS [Peso Real],
                taxed_weight AS [Peso Taxado],
                total_value AS [Valor NF],
                comments AS [Observacoes],
                agent_id AS [Agente],
                manifest_item_pick_id AS [Numero Manifesto],
                vehicle_type_id AS [Veiculo],
                cliente_id AS [Cliente ID],
                cliente_nome AS [Cliente],
                cliente_doc AS [Cliente Doc],
                local_coleta AS [Local da Coleta],
                numero_coleta AS [Numero],
                complemento_coleta AS [Complemento],
                cidade_coleta AS [Cidade],
                bairro_coleta AS [Bairro],
                uf_coleta AS [UF],
                cep_coleta AS [CEP],
                filial_id AS [Filial ID],
                filial_nome AS [Filial],
                filial_cnpj AS [Filial CNPJ],
                usuario_id AS [Usuario ID],
                usuario_nome AS [Usuario],
                cancellation_reason AS [Motivo Cancel.],
                cancellation_user_id AS [Usuario Cancel. ID],
                cargo_classification_id AS [Classificacao Carga ID],
                cost_center_id AS [Centro de Custo ID],
                destroy_reason AS [Motivo Exclusao],
                destroy_user_id AS [Usuario Exclusao ID],
                invoices_cubed_weight AS [Peso Cubado NF],
                lunch_break_end_hour AS [Hora Fim Almoco],
                lunch_break_start_hour AS [Hora Inicio Almoco],
                notification_email AS [Email Notificacao],
                notification_phone AS [Telefone Notificacao],
                pick_type_id AS [Tipo Coleta ID],
                pickup_location_id AS [Local Coleta ID],
                status_updated_at AS [Status Atualizado Em],
                metadata AS [Metadata],
                data_extracao AS [Data de extracao]
            FROM dbo.coletas;
        """;
        executarDDL(conexao, sqlView);
    }

    /**
     * Executa a operação MERGE (UPSERT) para inserir ou atualizar uma coleta no banco.
     * A lógica é segura e baseada na nova arquitetura de Entidade.
     */
    @Override
    protected int executarMerge(final Connection conexao, final ColetaEntity coleta) throws SQLException {
        // Para Coletas, o 'id' (string) é a única chave confiável para o MERGE.
        if (coleta.getId() == null || coleta.getId().trim().isEmpty()) {
            throw new SQLException("Não é possível executar o MERGE para Coleta sem um ID.");
        }

        final String sql = String.format("""
            MERGE dbo.%s AS target
            USING (
                SELECT
                    ? AS id, ? AS sequence_code, ? AS request_date, ? AS service_date, ? AS status, ? AS total_value, ? AS total_weight, ? AS total_volumes,
                    ? AS cliente_id, ? AS cliente_nome, ? AS cliente_doc, ? AS local_coleta, ? AS numero_coleta, ? AS complemento_coleta, ? AS cidade_coleta, ? AS bairro_coleta, ? AS uf_coleta, ? AS cep_coleta, ? AS filial_id, ? AS filial_nome, ? AS filial_cnpj, ? AS usuario_id, ? AS usuario_nome,
                    ? AS request_hour, ? AS service_start_hour, ? AS finish_date, ? AS service_end_hour, ? AS requester, ? AS taxed_weight,
                    ? AS comments, ? AS agent_id, ? AS manifest_item_pick_id, ? AS vehicle_type_id,
                    ? AS cancellation_reason, ? AS cancellation_user_id, ? AS cargo_classification_id, ? AS cost_center_id,
                    ? AS destroy_reason, ? AS destroy_user_id, ? AS invoices_cubed_weight, ? AS lunch_break_end_hour, ? AS lunch_break_start_hour,
                    ? AS notification_email, ? AS notification_phone, ? AS pick_type_id, ? AS pickup_location_id, ? AS status_updated_at,
                    ? AS metadata, ? AS data_extracao
            ) AS source
            ON target.id = source.id
            WHEN MATCHED THEN
                UPDATE SET
                    sequence_code = source.sequence_code,
                    request_date = source.request_date,
                    service_date = source.service_date,
                    status = source.status,
                    total_value = source.total_value,
                    total_weight = source.total_weight,
                    total_volumes = source.total_volumes,
                    cliente_id = source.cliente_id,
                    cliente_nome = source.cliente_nome,
                    cliente_doc = source.cliente_doc,
                    local_coleta = source.local_coleta,
                    numero_coleta = source.numero_coleta,
                    complemento_coleta = source.complemento_coleta,
                    cidade_coleta = source.cidade_coleta,
                    bairro_coleta = source.bairro_coleta,
                    uf_coleta = source.uf_coleta,
                    cep_coleta = source.cep_coleta,
                    filial_id = source.filial_id,
                    filial_nome = source.filial_nome,
                    filial_cnpj = source.filial_cnpj,
                    usuario_id = source.usuario_id,
                    usuario_nome = source.usuario_nome,
                    request_hour = source.request_hour,
                    service_start_hour = source.service_start_hour,
                    finish_date = source.finish_date,
                    service_end_hour = source.service_end_hour,
                    requester = source.requester,
                    taxed_weight = source.taxed_weight,
                    comments = source.comments,
                    agent_id = source.agent_id,
                    manifest_item_pick_id = source.manifest_item_pick_id,
                    vehicle_type_id = source.vehicle_type_id,
                    cancellation_reason = source.cancellation_reason,
                    cancellation_user_id = source.cancellation_user_id,
                    cargo_classification_id = source.cargo_classification_id,
                    cost_center_id = source.cost_center_id,
                    destroy_reason = source.destroy_reason,
                    destroy_user_id = source.destroy_user_id,
                    invoices_cubed_weight = source.invoices_cubed_weight,
                    lunch_break_end_hour = source.lunch_break_end_hour,
                    lunch_break_start_hour = source.lunch_break_start_hour,
                    notification_email = source.notification_email,
                    notification_phone = source.notification_phone,
                    pick_type_id = source.pick_type_id,
                    pickup_location_id = source.pickup_location_id,
                    status_updated_at = source.status_updated_at,
                    metadata = source.metadata,
                    data_extracao = source.data_extracao
            WHEN NOT MATCHED THEN
                INSERT (
                    id, sequence_code, request_date, service_date, status, total_value, total_weight, total_volumes,
                    cliente_id, cliente_nome, cliente_doc, local_coleta, numero_coleta, complemento_coleta, cidade_coleta, bairro_coleta, uf_coleta, cep_coleta, filial_id, filial_nome, filial_cnpj, usuario_id, usuario_nome,
                    request_hour, service_start_hour, finish_date, service_end_hour, requester, taxed_weight,
                    comments, agent_id, manifest_item_pick_id, vehicle_type_id,
                    cancellation_reason, cancellation_user_id, cargo_classification_id, cost_center_id,
                    destroy_reason, destroy_user_id, invoices_cubed_weight, lunch_break_end_hour, lunch_break_start_hour,
                    notification_email, notification_phone, pick_type_id, pickup_location_id, status_updated_at,
                    metadata, data_extracao
                )
                VALUES (
                    source.id, source.sequence_code, source.request_date, source.service_date, source.status, source.total_value, source.total_weight, source.total_volumes,
                    source.cliente_id, source.cliente_nome, source.cliente_doc, source.local_coleta, source.numero_coleta, source.complemento_coleta, source.cidade_coleta, source.bairro_coleta, source.uf_coleta, source.cep_coleta, source.filial_id, source.filial_nome, source.filial_cnpj, source.usuario_id, source.usuario_nome,
                    source.request_hour, source.service_start_hour, source.finish_date, source.service_end_hour, source.requester, source.taxed_weight,
                    source.comments, source.agent_id, source.manifest_item_pick_id, source.vehicle_type_id,
                    source.cancellation_reason, source.cancellation_user_id, source.cargo_classification_id, source.cost_center_id,
                    source.destroy_reason, source.destroy_user_id, source.invoices_cubed_weight, source.lunch_break_end_hour, source.lunch_break_start_hour,
                    source.notification_email, source.notification_phone, source.pick_type_id, source.pickup_location_id, source.status_updated_at,
                    source.metadata, source.data_extracao
                );
            """, NOME_TABELA);

        logger.debug("Preparando MERGE de Coleta ID {}", coleta.getId());
        PreparedStatement statement;
        try {
            statement = conexao.prepareStatement(sql);
        } catch (final SQLException e) {
            logger.error("Falha ao preparar MERGE de Coleta ID {}: {}", coleta.getId(), e.getMessage());
            throw e;
        }
        try (statement) {
            int expectedCount;
            try {
                final int metaCount = statement.getParameterMetaData().getParameterCount();
                expectedCount = (metaCount > 0 ? metaCount : 49);
                logger.debug("MERGE de Coletas preparado: {} parâmetro(s) esperado(s)", expectedCount);
            } catch (final SQLException pmEx) {
                logger.debug("Não foi possível obter ParameterMetaData: {}", pmEx.getMessage());
                expectedCount = 48;
            }
            // Define os parâmetros de forma segura e na ordem correta.
            int paramIndex = 1;
            statement.setString(paramIndex++, coleta.getId());
            statement.setObject(paramIndex++, coleta.getSequenceCode(), Types.BIGINT);
            setDateParameter(statement, paramIndex++, coleta.getRequestDate());
            setDateParameter(statement, paramIndex++, coleta.getServiceDate());
            statement.setString(paramIndex++, coleta.getStatus());
            setBigDecimalParameter(statement, paramIndex++, coleta.getTotalValue());
            setBigDecimalParameter(statement, paramIndex++, coleta.getTotalWeight());
            statement.setObject(paramIndex++, coleta.getTotalVolumes(), Types.INTEGER);
            // Campos expandidos (22 campos do CSV)
            statement.setObject(paramIndex++, coleta.getClienteId(), Types.BIGINT);
            statement.setString(paramIndex++, coleta.getClienteNome());
            statement.setString(paramIndex++, coleta.getClienteDoc());
            statement.setString(paramIndex++, coleta.getLocalColeta());
            statement.setString(paramIndex++, coleta.getNumeroColeta());
            statement.setString(paramIndex++, coleta.getComplementoColeta());
            statement.setString(paramIndex++, coleta.getCidadeColeta());
            statement.setString(paramIndex++, coleta.getBairroColeta());
            statement.setString(paramIndex++, coleta.getUfColeta());
            statement.setString(paramIndex++, coleta.getCepColeta());
            statement.setObject(paramIndex++, coleta.getFilialId(), Types.BIGINT);
            statement.setString(paramIndex++, coleta.getFilialNome());
            statement.setString(paramIndex++, coleta.getFilialCnpj());
            statement.setObject(paramIndex++, coleta.getUsuarioId(), Types.BIGINT);
            statement.setString(paramIndex++, coleta.getUsuarioNome());
            statement.setString(paramIndex++, coleta.getRequestHour());
            statement.setString(paramIndex++, coleta.getServiceStartHour());
            setDateParameter(statement, paramIndex++, coleta.getFinishDate());
            statement.setString(paramIndex++, coleta.getServiceEndHour());
            statement.setString(paramIndex++, coleta.getRequester());
            setBigDecimalParameter(statement, paramIndex++, coleta.getTaxedWeight());
            statement.setString(paramIndex++, coleta.getComments());
            statement.setObject(paramIndex++, coleta.getAgentId(), Types.BIGINT);
            statement.setObject(paramIndex++, coleta.getManifestItemPickId(), Types.BIGINT);
            statement.setObject(paramIndex++, coleta.getVehicleTypeId(), Types.BIGINT);
            statement.setString(paramIndex++, coleta.getCancellationReason());
            statement.setObject(paramIndex++, coleta.getCancellationUserId(), Types.BIGINT);
            statement.setObject(paramIndex++, coleta.getCargoClassificationId(), Types.BIGINT);
            statement.setObject(paramIndex++, coleta.getCostCenterId(), Types.BIGINT);
            statement.setString(paramIndex++, coleta.getDestroyReason());
            statement.setObject(paramIndex++, coleta.getDestroyUserId(), Types.BIGINT);
            setBigDecimalParameter(statement, paramIndex++, coleta.getInvoicesCubedWeight());
            statement.setString(paramIndex++, coleta.getLunchBreakEndHour());
            statement.setString(paramIndex++, coleta.getLunchBreakStartHour());
            statement.setString(paramIndex++, coleta.getNotificationEmail());
            statement.setString(paramIndex++, coleta.getNotificationPhone());
            statement.setObject(paramIndex++, coleta.getPickTypeId(), Types.BIGINT);
            statement.setObject(paramIndex++, coleta.getPickupLocationId(), Types.BIGINT);
            statement.setString(paramIndex++, coleta.getStatusUpdatedAt());
            statement.setString(paramIndex++, coleta.getMetadata());
            setInstantParameter(statement, paramIndex++, Instant.now()); // UTC timestamp
            
            // Verificar se todos os parâmetros foram definidos
            if ((paramIndex - 1) != expectedCount) {
                throw new SQLException(String.format("Número incorreto de parâmetros: esperado %d, definido %d", expectedCount, (paramIndex - 1)));
            }

            final int rowsAffected = statement.executeUpdate();
            logger.debug("MERGE executado para Coleta ID {}: {} linha(s) afetada(s)", coleta.getId(), rowsAffected);
            return rowsAffected;
        }
    }
}
