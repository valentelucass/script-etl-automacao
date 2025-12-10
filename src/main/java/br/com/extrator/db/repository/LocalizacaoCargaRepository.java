package br.com.extrator.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.LocalizacaoCargaEntity;

/**
 * Repositório para operações de persistência da entidade LocalizacaoCargaEntity.
 * Implementa a arquitetura de persistência híbrida: colunas-chave para indexação
 * e uma coluna de metadados para resiliência e completude dos dados.
 * Utiliza operações MERGE (UPSERT) com a chave de negócio (sequenceNumber).
 */
public class LocalizacaoCargaRepository extends AbstractRepository<LocalizacaoCargaEntity> {
    private static final Logger logger = LoggerFactory.getLogger(LocalizacaoCargaRepository.class);
    private static final String NOME_TABELA = "localizacao_cargas";
    private static boolean viewVerificada = false;

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }

    /**
     * Cria a tabela 'localizacao_cargas' se ela não existir, seguindo o modelo híbrido.
     * A estrutura contém apenas colunas essenciais para busca e uma coluna NVARCHAR(MAX)
     * para armazenar o JSON completo, garantindo resiliência.
     */
    @Override
    protected void criarTabelaSeNaoExistir(final Connection conexao) throws SQLException {
        if (!verificarTabelaExiste(conexao, NOME_TABELA)) {
            logger.info("Criando tabela {} com arquitetura híbrida...", NOME_TABELA);

            final String sql = """
                CREATE TABLE localizacao_cargas (
                    -- Coluna de Chave Primária (Chave de Negócio)
                    sequence_number BIGINT PRIMARY KEY,

                    -- Colunas Essenciais para Indexação e Relatórios conforme docs/descobertas-endpoints/localizacaocarga.md
                    type NVARCHAR(100),
                    service_at DATETIMEOFFSET,
                    invoices_volumes INT,
                    taxed_weight NVARCHAR(50),
                    invoices_value NVARCHAR(50),
                    total_value DECIMAL(18, 2),
                    service_type NVARCHAR(50),
                    branch_nickname NVARCHAR(255),
                    predicted_delivery_at DATETIMEOFFSET,
                    destination_location_name NVARCHAR(255),
                    destination_branch_nickname NVARCHAR(255),
                    classification NVARCHAR(255),
                    status NVARCHAR(50),
                    status_branch_nickname NVARCHAR(255),
                    origin_location_name NVARCHAR(255),
                    origin_branch_nickname NVARCHAR(255),

                    -- Coluna de Metadados para Resiliência e Completude
                    metadata NVARCHAR(MAX),

                    -- Coluna de Auditoria
                    data_extracao DATETIME2 DEFAULT GETDATE()
                )
                """;

            executarDDL(conexao, sql);
            logger.info("Tabela {} criada com sucesso.", NOME_TABELA);
        }
        if (!viewVerificada) {
            criarViewPowerBISeNaoExistir(conexao);
            viewVerificada = true;
            logger.info("View do Power BI verificada/atualizada para {}.", NOME_TABELA);
        }
    }

    private void criarViewPowerBISeNaoExistir(final Connection conexao) throws SQLException {
        final String sqlView = """
            CREATE OR ALTER VIEW dbo.vw_localizacao_cargas_powerbi AS
            SELECT
                sequence_number AS [N° Minuta],
                REPLACE(type, 'Freight::', '') AS [Tipo],
                service_at AS [Data do frete],
                invoices_volumes AS [Volumes],
                taxed_weight AS [Peso Taxado],
                invoices_value AS [Valor NF],
                total_value AS [Valor Frete],
                service_type AS [Tipo Serviço],
                branch_nickname AS [Filial Emissora],
                predicted_delivery_at AS [Previsão Entrega/Previsão de entrega],
                destination_location_name AS [Região Destino],
                destination_branch_nickname AS [Filial Destino],
                classification AS [Classificação],
                CASE status
                    WHEN 'pending' THEN 'Pendente'
                    WHEN 'delivering' THEN 'Em entrega'
                    WHEN 'in_warehouse' THEN 'Em armazém'
                    WHEN 'in_transfer' THEN 'Em transferência'
                    WHEN 'manifested' THEN 'Manifestado'
                    WHEN 'finished' THEN 'Finalizado'
                    ELSE status
                END AS [Status Carga],
                status_branch_nickname AS [Filial Atual],
                origin_location_name AS [Região Origem],
                origin_branch_nickname AS [Filial Origem],
                TRY_CONVERT(DECIMAL(10,6), JSON_VALUE(metadata, '$.latitude')) AS [Latitude],
                TRY_CONVERT(DECIMAL(10,6), JSON_VALUE(metadata, '$.longitude')) AS [Longitude],
                TRY_CONVERT(DECIMAL(10,2), JSON_VALUE(metadata, '$.speed')) AS [Velocidade],
                TRY_CONVERT(DECIMAL(10,2), JSON_VALUE(metadata, '$.altitude')) AS [Altitude],
                JSON_VALUE(metadata, '$.device_id') AS [Dispositivo ID],
                JSON_VALUE(metadata, '$.device_type') AS [Dispositivo Tipo],
                JSON_VALUE(metadata, '$.address') AS [Endereço],
                metadata AS [Metadata],
                data_extracao AS [Data de extracao]
            FROM dbo.localizacao_cargas;
        """;
        executarDDL(conexao, sqlView);
    }

    /**
     * Executa a operação MERGE (UPSERT) para inserir ou atualizar um registro de localização no banco.
     * A lógica é segura e baseada na nova arquitetura de Entidade.
     */
    @Override
    protected int executarMerge(final Connection conexao, final LocalizacaoCargaEntity carga) throws SQLException {
        // Para Localização de Cargas, o 'sequence_number' é a chave de negócio primária.
        if (carga.getSequenceNumber() == null) {
            throw new SQLException("Não é possível executar o MERGE para Localização de Carga sem um 'sequence_number'.");
        }

        final String sql = String.format("""
            MERGE %s AS target
            USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?))
                AS source (sequence_number, type, service_at, invoices_volumes, taxed_weight, invoices_value, total_value, service_type, branch_nickname, predicted_delivery_at, destination_location_name, destination_branch_nickname, classification, status, status_branch_nickname, origin_location_name, origin_branch_nickname, metadata, data_extracao)
            ON target.sequence_number = source.sequence_number
            WHEN MATCHED THEN
                UPDATE SET
                    type = source.type,
                    service_at = source.service_at,
                    invoices_volumes = source.invoices_volumes,
                    taxed_weight = source.taxed_weight,
                    invoices_value = source.invoices_value,
                    total_value = source.total_value,
                    service_type = source.service_type,
                    branch_nickname = source.branch_nickname,
                    predicted_delivery_at = source.predicted_delivery_at,
                    destination_location_name = source.destination_location_name,
                    destination_branch_nickname = source.destination_branch_nickname,
                    classification = source.classification,
                    status = source.status,
                    status_branch_nickname = source.status_branch_nickname,
                    origin_location_name = source.origin_location_name,
                    origin_branch_nickname = source.origin_branch_nickname,
                    metadata = source.metadata,
                    data_extracao = source.data_extracao
            WHEN NOT MATCHED THEN
                INSERT (sequence_number, type, service_at, invoices_volumes, taxed_weight, invoices_value, total_value, service_type, branch_nickname, predicted_delivery_at, destination_location_name, destination_branch_nickname, classification, status, status_branch_nickname, origin_location_name, origin_branch_nickname, metadata, data_extracao)
                VALUES (source.sequence_number, source.type, source.service_at, source.invoices_volumes, source.taxed_weight, source.invoices_value, source.total_value, source.service_type, source.branch_nickname, source.predicted_delivery_at, source.destination_location_name, source.destination_branch_nickname, source.classification, source.status, source.status_branch_nickname, source.origin_location_name, source.origin_branch_nickname, source.metadata, source.data_extracao);
            """, NOME_TABELA);

        try (PreparedStatement statement = conexao.prepareStatement(sql)) {
            // Define os parâmetros de forma segura e na ordem correta conforme MERGE SQL
            int paramIndex = 1;
            statement.setObject(paramIndex++, carga.getSequenceNumber(), Types.BIGINT);
            statement.setString(paramIndex++, carga.getType());
            // Usar helper methods para tipos especiais
            if (carga.getServiceAt() != null) {
                statement.setObject(paramIndex++, carga.getServiceAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setNull(paramIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            statement.setObject(paramIndex++, carga.getInvoicesVolumes(), Types.INTEGER);
            statement.setString(paramIndex++, carga.getTaxedWeight());
            statement.setString(paramIndex++, carga.getInvoicesValue());
            setBigDecimalParameter(statement, paramIndex++, carga.getTotalValue());
            statement.setString(paramIndex++, carga.getServiceType());
            statement.setString(paramIndex++, carga.getBranchNickname());
            if (carga.getPredictedDeliveryAt() != null) {
                statement.setObject(paramIndex++, carga.getPredictedDeliveryAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                statement.setNull(paramIndex++, Types.TIMESTAMP_WITH_TIMEZONE);
            }
            statement.setString(paramIndex++, carga.getDestinationLocationName());
            statement.setString(paramIndex++, carga.getDestinationBranchNickname());
            statement.setString(paramIndex++, carga.getClassification());
            statement.setString(paramIndex++, carga.getStatus());
            statement.setString(paramIndex++, carga.getStatusBranchNickname());
            statement.setString(paramIndex++, carga.getOriginLocationName());
            statement.setString(paramIndex++, carga.getOriginBranchNickname());
            statement.setString(paramIndex++, carga.getMetadata());
            setInstantParameter(statement, paramIndex++, Instant.now()); // UTC timestamp
            
            // Verificar se todos os parâmetros foram definidos (19 parâmetros = paramIndex final = 20)
            if (paramIndex != 20) {
                throw new SQLException(String.format("Número incorreto de parâmetros: esperado 19, definido %d", paramIndex - 1));
            }

            final int rowsAffected = statement.executeUpdate();
            logger.debug("MERGE executado para Localização de Carga sequence_number {}: {} linha(s) afetada(s)", carga.getSequenceNumber(), rowsAffected);
            return rowsAffected;
        }
    }
}
