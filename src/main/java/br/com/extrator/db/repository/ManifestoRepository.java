/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/repository/ManifestoRepository.java
Classe  : ManifestoRepository (class)
Pacote  : br.com.extrator.db.repository
Modulo  : Repositorio de dados
Papel   : Implementa responsabilidade de manifesto repository.

Conecta com:
- ManifestoEntity (db.entity)
- ConstantesEntidades (util.validacao)

Fluxo geral:
1) Monta comandos SQL e parametros.
2) Executa operacoes de persistencia/consulta no banco.
3) Converte resultado para entidades de dominio.

Estrutura interna:
Metodos principais:
- getNomeTabela(): expone valor atual do estado interno.
- truncate(...3 args): realiza operacao relacionada a "truncate".
Atributos-chave:
- logger: logger da classe para diagnostico.
- NOME_TABELA: campo de estado para "nome tabela".
[DOC-FILE-END]============================================================== */

package br.com.extrator.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.ManifestoEntity;
import br.com.extrator.util.validacao.ConstantesEntidades;

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
    private static final String NOME_TABELA = ConstantesEntidades.MANIFESTOS;

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }
    
    /**
     * Executa a operação MERGE (UPSERT) para inserir ou atualizar um manifesto no banco.
     * 
     * ⚠️ IMPORTANTE: A tabela deve ter a estrutura nova (com identificador_unico e chave composta).
     * A estrutura deve ser criada via scripts SQL (pasta database/).
     * 
     * O MERGE usa (sequence_code, pick_sequence_code, mdfe_number) para matching,
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
     * CORREÇÃO CRÍTICA #2: Validação do nome da tabela para prevenir SQL injection
     */
    @Override
    protected int executarMerge(final Connection conexao, final ManifestoEntity manifesto) throws SQLException {
        // ✅ VALIDAÇÃO INICIAL
        if (manifesto == null) {
            logger.error("❌ Tentativa de salvar ManifestoEntity NULL");
            throw new SQLException("Não é possível executar MERGE para Manifesto nulo");
        }
        
        // ✅ CORREÇÃO CRÍTICA #2: Validar nome da tabela (prevenir SQL injection)
        final String nomeTabela = getNomeTabela();
        if (!nomeTabela.matches("^[a-zA-Z0-9_]+$")) {
            logger.error("❌ Nome de tabela inválido detectado: {}", nomeTabela);
            throw new SQLException("Nome de tabela contém caracteres inválidos: " + nomeTabela);
        }
        
        // Para Manifestos, o 'sequence_code' é a chave de negócio primária.
        if (manifesto.getSequenceCode() == null) {
            logger.error("❌ Manifesto com sequence_code NULL");
            throw new SQLException("Não é possível executar o MERGE para Manifesto sem um 'sequence_code'.");
        }
        
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
        
        logger.debug("→ Salvando manifesto sequence_code={}, identificador_unico={}", 
                    manifesto.getSequenceCode(), identificadorUnico);
        
        logger.debug("→ Salvando manifesto sequence_code={}, identificador_unico={} (estrutura nova)", 
                    manifesto.getSequenceCode(), identificadorUnico);

        // ✅ Nome da tabela já foi validado acima (apenas caracteres alfanuméricos e underscore)
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
                        capacidade_kg = ?, obs_operacional = ?, obs_financeira = ?, \
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
                    setBigDecimalParameter(upd, i++, manifesto.getCapacidadeKg());
                    upd.setString(i++, truncate(manifesto.getObsOperacional(), 4000, "obs_operacional"));
                    upd.setString(i++, truncate(manifesto.getObsFinanceira(), 4000, "obs_financeira"));
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
