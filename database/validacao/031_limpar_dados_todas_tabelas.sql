-- Limpeza completa dos dados do ETL sem remover estrutura, indices ou migrations.
-- Cuidado: irreversivel.
-- Nao limpa dbo.schema_migrations de proposito.

SET NOCOUNT ON;
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    DELETE FROM dbo.page_audit;
    DELETE FROM dbo.log_extracoes;
    DELETE FROM dbo.sys_execution_history;
    DELETE FROM dbo.sys_auditoria_temp;
    DELETE FROM dbo.sys_execution_audit;
    DELETE FROM dbo.sys_execution_watermark;
    DELETE FROM dbo.sys_replay_idempotency;
    DELETE FROM dbo.sys_reconciliation_quarantine;
    DELETE FROM dbo.etl_invalid_records;
    DELETE FROM dbo.dim_usuarios_historico;

    DELETE FROM dbo.raster_viagem_paradas;
    DELETE FROM dbo.raster_viagens;
    DELETE FROM dbo.manifestos;
    DELETE FROM dbo.fretes;
    DELETE FROM dbo.faturas_graphql;
    DELETE FROM dbo.faturas_por_cliente;
    DELETE FROM dbo.cotacoes;
    DELETE FROM dbo.localizacao_cargas;
    DELETE FROM dbo.contas_a_pagar;
    DELETE FROM dbo.inventario;
    DELETE FROM dbo.sinistros;
    DELETE FROM dbo.coletas;
    DELETE FROM dbo.dim_usuarios;

    COMMIT TRANSACTION;
    PRINT 'Limpeza concluida com sucesso.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;

    THROW;
END CATCH;
