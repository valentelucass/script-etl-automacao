-- Valida se uma recriacao limpa do banco deixou o schema essencial coerente.
-- Seguro para rodar em banco vazio: confere estrutura, migrations, views e indices.

SET NOCOUNT ON;

DECLARE @falhas TABLE (
    tipo NVARCHAR(50) NOT NULL,
    nome NVARCHAR(255) NOT NULL,
    detalhe NVARCHAR(500) NULL
);

DECLARE @tabelas TABLE (nome SYSNAME NOT NULL);
INSERT INTO @tabelas (nome) VALUES
    (N'coletas'),
    (N'fretes'),
    (N'manifestos'),
    (N'cotacoes'),
    (N'localizacao_cargas'),
    (N'contas_a_pagar'),
    (N'faturas_por_cliente'),
    (N'faturas_graphql'),
    (N'log_extracoes'),
    (N'page_audit'),
    (N'dim_usuarios'),
    (N'sys_execution_history'),
    (N'sys_auditoria_temp'),
    (N'sys_execution_audit'),
    (N'sys_execution_watermark'),
    (N'dim_usuarios_historico'),
    (N'schema_migrations'),
    (N'etl_invalid_records'),
    (N'inventario'),
    (N'sinistros'),
    (N'sys_replay_idempotency'),
    (N'sys_reconciliation_quarantine'),
    (N'raster_viagens'),
    (N'raster_viagem_paradas');

INSERT INTO @falhas (tipo, nome, detalhe)
SELECT N'TABELA', t.nome, N'Tabela dbo.' + t.nome + N' ausente'
FROM @tabelas t
WHERE OBJECT_ID(N'dbo.' + t.nome, N'U') IS NULL;

DECLARE @views TABLE (nome SYSNAME NOT NULL);
INSERT INTO @views (nome) VALUES
    (N'vw_faturas_por_cliente_powerbi'),
    (N'vw_fretes_powerbi'),
    (N'vw_coletas_powerbi'),
    (N'vw_faturas_graphql_powerbi'),
    (N'vw_cotacoes_powerbi'),
    (N'vw_contas_a_pagar_powerbi'),
    (N'vw_localizacao_cargas_powerbi'),
    (N'vw_manifestos_powerbi'),
    (N'vw_bi_monitoramento'),
    (N'vw_inventario_powerbi'),
    (N'vw_sinistros_powerbi'),
    (N'vw_raster_sm_transit_time'),
    (N'vw_dim_filiais'),
    (N'vw_dim_clientes'),
    (N'vw_dim_veiculos'),
    (N'vw_dim_motoristas'),
    (N'vw_dim_planocontas'),
    (N'vw_dim_usuarios');

INSERT INTO @falhas (tipo, nome, detalhe)
SELECT N'VIEW', v.nome, N'View dbo.' + v.nome + N' ausente'
FROM @views v
WHERE OBJECT_ID(N'dbo.' + v.nome, N'V') IS NULL;

DECLARE @migrations TABLE (migration_id NVARCHAR(255) NOT NULL);
INSERT INTO @migrations (migration_id) VALUES
    (N'001_criar_tabela_schema_migrations'),
    (N'002_corrigir_constraint_manifestos'),
    (N'003_corrigir_tipo_datetime_faturas_graphql'),
    (N'004_adicionar_request_hour_coletas'),
    (N'005_alinhar_sys_execution_history_schema'),
    (N'006_alterar_fretes_indicadores_gestao'),
    (N'007_adicionar_fk_seletiva_manifestos_coletas'),
    (N'008_criar_tabela_sys_replay_idempotency'),
    (N'009_criar_tabela_sys_reconciliation_quarantine'),
    (N'010_harden_coletas_sequence_code'),
    (N'011_alinhar_chave_merge_manifestos_orfaos'),
    (N'012_adicionar_frete_cortesia'),
    (N'013_ajustar_precisao_cubagem_fretes'),
    (N'014_criar_tabelas_raster'),
    (N'015_adicionar_cliente_cnpj_faturas_por_cliente');

IF OBJECT_ID(N'dbo.schema_migrations', N'U') IS NOT NULL
BEGIN
    INSERT INTO @falhas (tipo, nome, detalhe)
    SELECT N'MIGRATION', m.migration_id, N'Migration nao registrada em dbo.schema_migrations'
    FROM @migrations m
    WHERE NOT EXISTS (
        SELECT 1
        FROM dbo.schema_migrations sm
        WHERE sm.migration_id = m.migration_id
    );
END;

IF COL_LENGTH(N'dbo.coletas', N'request_hour') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.coletas.request_hour', N'Coluna da migration 004 ausente');

IF COL_LENGTH(N'dbo.fretes', N'finished_at') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fretes.finished_at', N'Coluna da migration 006 ausente');

IF COL_LENGTH(N'dbo.fretes', N'fit_dpn_performance_finished_at') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fretes.fit_dpn_performance_finished_at', N'Coluna da migration 006 ausente');

IF COL_LENGTH(N'dbo.fretes', N'corporation_sequence_number') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fretes.corporation_sequence_number', N'Coluna da migration 006 ausente');

IF COL_LENGTH(N'dbo.fretes', N'cortesia') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fretes.cortesia', N'Coluna da migration 012 ausente');

IF COL_LENGTH(N'dbo.faturas_por_cliente', N'cliente_cnpj') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.faturas_por_cliente.cliente_cnpj', N'Coluna da migration 015 ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_fpc_cliente_cnpj'
      AND object_id = OBJECT_ID(N'dbo.faturas_por_cliente')
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_fpc_cliente_cnpj', N'Indice filtrado da migration 015 ausente');

IF EXISTS (
    SELECT 1
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'dbo'
      AND TABLE_NAME = 'coletas'
      AND COLUMN_NAME = 'sequence_code'
      AND IS_NULLABLE = 'YES'
)
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.coletas.sequence_code', N'Deve estar NOT NULL apos migration 010');

IF EXISTS (
    SELECT 1
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'dbo'
      AND TABLE_NAME = 'fretes'
      AND COLUMN_NAME IN ('total_cubic_volume', 'cubages_cubed_weight')
      AND (NUMERIC_PRECISION <> 18 OR NUMERIC_SCALE <> 6)
)
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fretes.cubagem', N'Campos de cubagem devem ser DECIMAL(18,6)');

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = N'FK_raster_viagem_paradas_viagens'
      AND parent_object_id = OBJECT_ID(N'dbo.raster_viagem_paradas')
)
    INSERT INTO @falhas VALUES (N'FK', N'FK_raster_viagem_paradas_viagens', N'FK Raster paradas -> viagens ausente');

SELECT tipo, nome, detalhe
FROM @falhas
ORDER BY tipo, nome;

IF EXISTS (SELECT 1 FROM @falhas)
BEGIN
    THROW 51034, 'Schema de recriacao inconsistente. Verifique o resultado de database/validacao/034_validar_schema_recriacao.sql.', 1;
END;

PRINT 'Schema de recriacao validado com sucesso.';
