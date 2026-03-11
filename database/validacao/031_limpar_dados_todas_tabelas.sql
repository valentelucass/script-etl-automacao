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

    DELETE FROM dbo.coletas;
    DELETE FROM dbo.fretes;
    DELETE FROM dbo.faturas_graphql;
    DELETE FROM dbo.faturas_por_cliente;
    DELETE FROM dbo.manifestos;
    DELETE FROM dbo.cotacoes;
    DELETE FROM dbo.localizacao_cargas;
    DELETE FROM dbo.contas_a_pagar;
    DELETE FROM dbo.dim_usuarios;

    COMMIT TRANSACTION;
    PRINT 'Limpeza concluida com sucesso.';
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;

    THROW;
END CATCH;
