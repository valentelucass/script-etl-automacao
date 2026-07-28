package br.com.extrator.persistencia.repositorio;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;

import br.com.extrator.persistencia.entidade.InventarioEntity;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public class InventarioRepository extends AbstractRepository<InventarioEntity> {

    private static final String NOME_TABELA = ConstantesEntidades.INVENTARIO;

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }

    @Override
    protected boolean aceitarMergeSemAlteracoesComoSucesso(final InventarioEntity inventario) {
        return true;
    }

    @Override
    protected int refrescarDataExtracaoQuandoNoOp(final Connection conexao,
                                                  final InventarioEntity inventario) throws SQLException {
        if (inventario == null
            || inventario.getIdentificadorUnico() == null
            || inventario.getIdentificadorUnico().isBlank()) {
            return 0;
        }

        final String sql = """
            UPDATE dbo.inventario
               SET data_extracao = ?
             WHERE identificador_unico = ?
               AND (data_extracao IS NULL OR data_extracao < ?)
            """;

        try (PreparedStatement statement = conexao.prepareStatement(sql)) {
            final Instant agora = Instant.now();
            setInstantParameter(statement, 1, agora);
            setStringParameter(statement, 2, inventario.getIdentificadorUnico());
            setInstantParameter(statement, 3, agora);
            return statement.executeUpdate();
        }
    }

    @Override
    protected int executarMerge(final Connection conexao, final InventarioEntity inventario) throws SQLException {
        if (inventario.getIdentificadorUnico() == null || inventario.getIdentificadorUnico().isBlank()) {
            throw new SQLException("Nao e possivel executar o MERGE para inventario sem identificador_unico.");
        }

        final String freshnessGuard = buildMonotonicUpdateGuard(
            "COALESCE(CAST(target.performance_finished_at AS datetime2), CAST(target.finished_at AS datetime2), CAST(target.started_at AS datetime2))",
            "COALESCE(CAST(source.performance_finished_at AS datetime2), CAST(source.finished_at AS datetime2), CAST(source.started_at AS datetime2))"
        );
        final String sql = String.format("""
            MERGE %s AS target
            USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(0 AS bit), CAST(NULL AS datetime2(0))))
                AS source (
                    identificador_unico, sequence_code, numero_minuta, pagador_nome, remetente_nome, origem_cidade,
                    destinatario_nome, destino_cidade, regiao_entrega, filial_entregadora, branch_nickname, type,
                    started_at, finished_at, status, conferente_nome, invoices_mapping, invoices_value, real_weight,
                    total_cubic_volume, taxed_weight, invoices_volumes, read_volumes, predicted_delivery_at,
                    performance_finished_at, ultima_ocorrencia_at, ultima_ocorrencia_descricao,
                    flag_comprovante_anexado, metadata, data_extracao, excluido_na_origem, data_exclusao_origem
                )
            ON target.identificador_unico = source.identificador_unico
            WHEN MATCHED AND (%s OR target.excluido_na_origem = 1) THEN
                UPDATE SET
                    sequence_code = source.sequence_code,
                    numero_minuta = source.numero_minuta,
                    pagador_nome = source.pagador_nome,
                    remetente_nome = source.remetente_nome,
                    origem_cidade = source.origem_cidade,
                    destinatario_nome = source.destinatario_nome,
                    destino_cidade = source.destino_cidade,
                    regiao_entrega = source.regiao_entrega,
                    filial_entregadora = source.filial_entregadora,
                    branch_nickname = source.branch_nickname,
                    type = source.type,
                    started_at = source.started_at,
                    finished_at = source.finished_at,
                    status = source.status,
                    conferente_nome = source.conferente_nome,
                    invoices_mapping = source.invoices_mapping,
                    invoices_value = source.invoices_value,
                    real_weight = source.real_weight,
                    total_cubic_volume = source.total_cubic_volume,
                    taxed_weight = source.taxed_weight,
                    invoices_volumes = source.invoices_volumes,
                    read_volumes = source.read_volumes,
                    predicted_delivery_at = source.predicted_delivery_at,
                    performance_finished_at = source.performance_finished_at,
                    ultima_ocorrencia_at = source.ultima_ocorrencia_at,
                    ultima_ocorrencia_descricao = source.ultima_ocorrencia_descricao,
                    flag_comprovante_anexado = CAST(
                        CASE
                            WHEN COALESCE(target.flag_comprovante_anexado, CAST(0 AS bit)) = CAST(1 AS bit)
                              OR COALESCE(source.flag_comprovante_anexado, CAST(0 AS bit)) = CAST(1 AS bit)
                            THEN 1
                            ELSE 0
                        END AS bit
                    ),
                    metadata = source.metadata,
                    data_extracao = source.data_extracao,
                    excluido_na_origem = source.excluido_na_origem,
                    data_exclusao_origem = source.data_exclusao_origem
            WHEN NOT MATCHED THEN
                INSERT (
                    identificador_unico, sequence_code, numero_minuta, pagador_nome, remetente_nome, origem_cidade,
                    destinatario_nome, destino_cidade, regiao_entrega, filial_entregadora, branch_nickname, type,
                    started_at, finished_at, status, conferente_nome, invoices_mapping, invoices_value, real_weight,
                    total_cubic_volume, taxed_weight, invoices_volumes, read_volumes, predicted_delivery_at,
                    performance_finished_at, ultima_ocorrencia_at, ultima_ocorrencia_descricao,
                    flag_comprovante_anexado, metadata, data_extracao, excluido_na_origem, data_exclusao_origem
                )
                VALUES (
                    source.identificador_unico, source.sequence_code, source.numero_minuta, source.pagador_nome, source.remetente_nome, source.origem_cidade,
                    source.destinatario_nome, source.destino_cidade, source.regiao_entrega, source.filial_entregadora, source.branch_nickname, source.type,
                    source.started_at, source.finished_at, source.status, source.conferente_nome, source.invoices_mapping, source.invoices_value, source.real_weight,
                    source.total_cubic_volume, source.taxed_weight, source.invoices_volumes, source.read_volumes, source.predicted_delivery_at,
                    source.performance_finished_at, source.ultima_ocorrencia_at, source.ultima_ocorrencia_descricao,
                    source.flag_comprovante_anexado, source.metadata, source.data_extracao, source.excluido_na_origem, source.data_exclusao_origem
                );
            """, NOME_TABELA, freshnessGuard);

        try (PreparedStatement statement = conexao.prepareStatement(sql)) {
            int paramIndex = 1;
            setStringParameter(statement, paramIndex++, inventario.getIdentificadorUnico());
            setLongParameter(statement, paramIndex++, inventario.getSequenceCode());
            setLongParameter(statement, paramIndex++, inventario.getNumeroMinuta());
            setStringParameter(statement, paramIndex++, inventario.getPagadorNome());
            setStringParameter(statement, paramIndex++, inventario.getRemetenteNome());
            setStringParameter(statement, paramIndex++, inventario.getOrigemCidade());
            setStringParameter(statement, paramIndex++, inventario.getDestinatarioNome());
            setStringParameter(statement, paramIndex++, inventario.getDestinoCidade());
            setStringParameter(statement, paramIndex++, inventario.getRegiaoEntrega());
            setStringParameter(statement, paramIndex++, inventario.getFilialEntregadora());
            setStringParameter(statement, paramIndex++, inventario.getBranchNickname());
            setStringParameter(statement, paramIndex++, inventario.getType());
            statement.setObject(paramIndex++, inventario.getStartedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            statement.setObject(paramIndex++, inventario.getFinishedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            setStringParameter(statement, paramIndex++, inventario.getStatus());
            setStringParameter(statement, paramIndex++, inventario.getConferenteNome());
            setStringParameter(statement, paramIndex++, inventario.getInvoicesMapping());
            setBigDecimalParameter(statement, paramIndex++, inventario.getInvoicesValue());
            setBigDecimalParameter(statement, paramIndex++, inventario.getRealWeight());
            setBigDecimalParameter(statement, paramIndex++, inventario.getTotalCubicVolume());
            setBigDecimalParameter(statement, paramIndex++, inventario.getTaxedWeight());
            setIntegerParameter(statement, paramIndex++, inventario.getInvoicesVolumes());
            setIntegerParameter(statement, paramIndex++, inventario.getReadVolumes());
            statement.setObject(paramIndex++, inventario.getPredictedDeliveryAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            statement.setObject(paramIndex++, inventario.getPerformanceFinishedAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            statement.setObject(paramIndex++, inventario.getUltimaOcorrenciaAt(), Types.TIMESTAMP_WITH_TIMEZONE);
            setStringParameter(statement, paramIndex++, inventario.getUltimaOcorrenciaDescricao());
            setBooleanParameter(statement, paramIndex++, inventario.isFlagComprovanteAnexado());
            setStringParameter(statement, paramIndex++, inventario.getMetadata());
            setInstantParameter(statement, paramIndex++, Instant.now());
            return statement.executeUpdate();
        }
    }
}
