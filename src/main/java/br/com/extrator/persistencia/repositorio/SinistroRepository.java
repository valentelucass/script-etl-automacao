package br.com.extrator.persistencia.repositorio;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;

import br.com.extrator.persistencia.entidade.SinistroEntity;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public class SinistroRepository extends AbstractRepository<SinistroEntity> {

    private static final String NOME_TABELA = ConstantesEntidades.SINISTROS;

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }

    @Override
    protected boolean aceitarMergeSemAlteracoesComoSucesso(final SinistroEntity sinistro) {
        return true;
    }

    @Override
    protected int refrescarDataExtracaoQuandoNoOp(final Connection conexao,
                                                  final SinistroEntity sinistro) throws SQLException {
        if (sinistro == null
            || sinistro.getIdentificadorUnico() == null
            || sinistro.getIdentificadorUnico().isBlank()) {
            return 0;
        }

        final String sql = """
            UPDATE dbo.sinistros
               SET data_extracao = ?
             WHERE identificador_unico = ?
               AND (data_extracao IS NULL OR data_extracao < ?)
            """;

        try (PreparedStatement statement = conexao.prepareStatement(sql)) {
            final Instant agora = Instant.now();
            setInstantParameter(statement, 1, agora);
            setStringParameter(statement, 2, sinistro.getIdentificadorUnico());
            setInstantParameter(statement, 3, agora);
            return statement.executeUpdate();
        }
    }

    @Override
    protected int executarMerge(final Connection conexao, final SinistroEntity sinistro) throws SQLException {
        if (sinistro.getIdentificadorUnico() == null || sinistro.getIdentificadorUnico().isBlank()) {
            throw new SQLException("Nao e possivel executar o MERGE para sinistros sem identificador_unico.");
        }

        final String freshnessGuard = buildMonotonicUpdateGuard(
            "COALESCE(CAST(target.treatment_at AS datetime2), CAST(target.opening_at_date AS datetime2))",
            "COALESCE(CAST(source.treatment_at AS datetime2), CAST(source.opening_at_date AS datetime2))"
        );
        final String sql = String.format("""
            MERGE %s AS target
            USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?))
                AS source (
                    identificador_unico, sequence_code, opening_at_date, occurrence_at_date, occurrence_at_time,
                    expected_solution_date, insurance_claim_location, informed_by, finished_at_date, finished_at_time,
                    invoices_count, corporation_sequence_number, insurance_occurrence_number, invoices_volumes,
                    invoices_weight, invoices_value, payer_nickname, customer_debits_subtotal,
                    customer_credit_entries_subtotal, responsible_credits_subtotal,
                    responsible_debit_entries_subtotal, insurer_credits_subtotal, insurance_claim_total,
                    branch_nickname, event_name, user_name, vehicle_plate, occurrence_description, occurrence_code,
                    treatment_at, dealing_type, solution_type, metadata, data_extracao
                )
            ON target.identificador_unico = source.identificador_unico
            WHEN MATCHED AND %s THEN
                UPDATE SET
                    sequence_code = source.sequence_code,
                    opening_at_date = source.opening_at_date,
                    occurrence_at_date = source.occurrence_at_date,
                    occurrence_at_time = source.occurrence_at_time,
                    expected_solution_date = source.expected_solution_date,
                    insurance_claim_location = source.insurance_claim_location,
                    informed_by = source.informed_by,
                    finished_at_date = source.finished_at_date,
                    finished_at_time = source.finished_at_time,
                    invoices_count = source.invoices_count,
                    corporation_sequence_number = source.corporation_sequence_number,
                    insurance_occurrence_number = source.insurance_occurrence_number,
                    invoices_volumes = source.invoices_volumes,
                    invoices_weight = source.invoices_weight,
                    invoices_value = source.invoices_value,
                    payer_nickname = source.payer_nickname,
                    customer_debits_subtotal = source.customer_debits_subtotal,
                    customer_credit_entries_subtotal = source.customer_credit_entries_subtotal,
                    responsible_credits_subtotal = source.responsible_credits_subtotal,
                    responsible_debit_entries_subtotal = source.responsible_debit_entries_subtotal,
                    insurer_credits_subtotal = source.insurer_credits_subtotal,
                    insurance_claim_total = source.insurance_claim_total,
                    branch_nickname = source.branch_nickname,
                    event_name = source.event_name,
                    user_name = source.user_name,
                    vehicle_plate = source.vehicle_plate,
                    occurrence_description = source.occurrence_description,
                    occurrence_code = source.occurrence_code,
                    treatment_at = source.treatment_at,
                    dealing_type = source.dealing_type,
                    solution_type = source.solution_type,
                    metadata = source.metadata,
                    data_extracao = source.data_extracao
            WHEN NOT MATCHED THEN
                INSERT (
                    identificador_unico, sequence_code, opening_at_date, occurrence_at_date, occurrence_at_time,
                    expected_solution_date, insurance_claim_location, informed_by, finished_at_date, finished_at_time,
                    invoices_count, corporation_sequence_number, insurance_occurrence_number, invoices_volumes,
                    invoices_weight, invoices_value, payer_nickname, customer_debits_subtotal,
                    customer_credit_entries_subtotal, responsible_credits_subtotal,
                    responsible_debit_entries_subtotal, insurer_credits_subtotal, insurance_claim_total,
                    branch_nickname, event_name, user_name, vehicle_plate, occurrence_description, occurrence_code,
                    treatment_at, dealing_type, solution_type, metadata, data_extracao
                )
                VALUES (
                    source.identificador_unico, source.sequence_code, source.opening_at_date, source.occurrence_at_date, source.occurrence_at_time,
                    source.expected_solution_date, source.insurance_claim_location, source.informed_by, source.finished_at_date, source.finished_at_time,
                    source.invoices_count, source.corporation_sequence_number, source.insurance_occurrence_number, source.invoices_volumes,
                    source.invoices_weight, source.invoices_value, source.payer_nickname, source.customer_debits_subtotal,
                    source.customer_credit_entries_subtotal, source.responsible_credits_subtotal,
                    source.responsible_debit_entries_subtotal, source.insurer_credits_subtotal, source.insurance_claim_total,
                    source.branch_nickname, source.event_name, source.user_name, source.vehicle_plate, source.occurrence_description, source.occurrence_code,
                    source.treatment_at, source.dealing_type, source.solution_type, source.metadata, source.data_extracao
                );
            """, NOME_TABELA, freshnessGuard);

        try (PreparedStatement statement = conexao.prepareStatement(sql)) {
            int paramIndex = 1;
            setStringParameter(statement, paramIndex++, sinistro.getIdentificadorUnico());
            setLongParameter(statement, paramIndex++, sinistro.getSequenceCode());
            statement.setObject(paramIndex++, sinistro.getOpeningAtDate(), Types.DATE);
            statement.setObject(paramIndex++, sinistro.getOccurrenceAtDate(), Types.DATE);
            setStringParameter(statement, paramIndex++, sinistro.getOccurrenceAtTime());
            statement.setObject(paramIndex++, sinistro.getExpectedSolutionDate(), Types.DATE);
            setStringParameter(statement, paramIndex++, sinistro.getInsuranceClaimLocation());
            setStringParameter(statement, paramIndex++, sinistro.getInformedBy());
            statement.setObject(paramIndex++, sinistro.getFinishedAtDate(), Types.DATE);
            setStringParameter(statement, paramIndex++, sinistro.getFinishedAtTime());
            setIntegerParameter(statement, paramIndex++, sinistro.getInvoicesCount());
            setLongParameter(statement, paramIndex++, sinistro.getCorporationSequenceNumber());
            setLongParameter(statement, paramIndex++, sinistro.getInsuranceOccurrenceNumber());
            setIntegerParameter(statement, paramIndex++, sinistro.getInvoicesVolumes());
            setBigDecimalParameter(statement, paramIndex++, sinistro.getInvoicesWeight());
            setBigDecimalParameter(statement, paramIndex++, sinistro.getInvoicesValue());
            setStringParameter(statement, paramIndex++, sinistro.getPayerNickname());
            setBigDecimalParameter(statement, paramIndex++, sinistro.getCustomerDebitsSubtotal());
            setBigDecimalParameter(statement, paramIndex++, sinistro.getCustomerCreditEntriesSubtotal());
            setBigDecimalParameter(statement, paramIndex++, sinistro.getResponsibleCreditsSubtotal());
            setBigDecimalParameter(statement, paramIndex++, sinistro.getResponsibleDebitEntriesSubtotal());
            setBigDecimalParameter(statement, paramIndex++, sinistro.getInsurerCreditsSubtotal());
            setBigDecimalParameter(statement, paramIndex++, sinistro.getInsuranceClaimTotal());
            setStringParameter(statement, paramIndex++, sinistro.getBranchNickname());
            setStringParameter(statement, paramIndex++, sinistro.getEventName());
            setStringParameter(statement, paramIndex++, sinistro.getUserName());
            setStringParameter(statement, paramIndex++, sinistro.getVehiclePlate());
            setStringParameter(statement, paramIndex++, sinistro.getOccurrenceDescription());
            setStringParameter(statement, paramIndex++, sinistro.getOccurrenceCode());
            statement.setObject(paramIndex++, sinistro.getTreatmentAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            setStringParameter(statement, paramIndex++, sinistro.getDealingType());
            setStringParameter(statement, paramIndex++, sinistro.getSolutionType());
            setStringParameter(statement, paramIndex++, sinistro.getMetadata());
            setInstantParameter(statement, paramIndex++, Instant.now());
            return statement.executeUpdate();
        }
    }
}
