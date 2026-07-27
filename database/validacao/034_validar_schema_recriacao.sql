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
    (N'localizacao_cargas_regiao_destino_alias'),
    (N'contas_a_pagar'),
    (N'faturas_por_cliente'),
    (N'dim_calendario'),
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
    (N'raster_viagem_paradas'),
    (N'manifestos_frota_propria_cnpjs'),
    (N'fato_gestao_vista_fretes'),
    (N'fato_gestao_vista_coletores'),
    (N'fato_fretes_faturamento'),
    (N'fato_gestao_vista_faturas'),
    (N'fato_gestao_vista_manifestos'),
    (N'regras_atribuicao_filial'),
    (N'dim_regiao_logistica_rules');

INSERT INTO @falhas (tipo, nome, detalhe)
SELECT N'TABELA', t.nome, N'Tabela dbo.' + t.nome + N' ausente'
FROM @tabelas t
WHERE OBJECT_ID(N'dbo.' + t.nome, N'U') IS NULL;

DECLARE @views TABLE (nome SYSNAME NOT NULL);
INSERT INTO @views (nome) VALUES
    (N'vw_faturas_por_cliente_powerbi'),
    (N'vw_fretes_powerbi'),
    (N'vw_coletas_powerbi'),
    (N'vw_cotacoes_powerbi'),
    (N'vw_contas_a_pagar_powerbi'),
    (N'vw_localizacao_cargas_powerbi'),
    (N'vw_manifestos_powerbi'),
    (N'vw_fato_manifestos_dash'),
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
    (N'015_adicionar_cliente_cnpj_faturas_por_cliente'),
    (N'016_materializar_faturamento_fretes'),
    (N'017_localizacao_cargas_dashboard_operacional'),
    (N'018_adicionar_indice_coletas_request_date_dashboard'),
    (N'019_adicionar_comprovante_fretes_performance'),
    (N'020_adicionar_tipo_motorista_manifestos'),
    (N'021_materializar_comprovante_inventario'),
    (N'022_corrigir_volumes_fretes_faturamento'),
    (N'023_adicionar_noop_count_log_extracoes'),
    (N'024_drop_faturas_graphql'),
    (N'025_materializar_chave_responsavel_destino'),
    (N'026_materializar_chave_usuario_cotacoes'),
    (N'027_adicionar_excluido_na_origem'),
    (N'028_corrigir_chave_unica_manifestos'),
    (N'029_criar_fato_gestao_vista_fretes'),
    (N'030_criar_fato_gestao_vista_coletores'),
    (N'031_criar_fato_fretes_faturamento'),
    (N'032_criar_fato_gestao_vista_faturas'),
    (N'033_tuning_indices_fatos'),
    (N'034_adicionar_hash_linha_usuarios'),
    (N'035_drop_views_legadas_powerbi'),
    (N'036_corrigir_chave_manifestos_fallback_identificador'),
    (N'037_adicionar_status_fatura'),
    (N'038_atualizar_min_frete_cotacoes_matriz_uf'),
    (N'039_criar_dim_calendario_referencia_faturamento'),
    (N'040_criar_indice_performance_fretes'),
    (N'041_adicionar_chave_pick_item_coletas_fretes'),
    (N'042_criar_fato_gestao_vista_manifestos'),
    (N'043_materializar_tipo_contrato_manifestos'),
    (N'044_adicionar_data_exclusao_origem_tabelas_base'),
    (N'045_criar_indice_manifestos_competencia_operacional'),
    (N'046_reclassificar_tipo_contrato_manifestos'),
    (N'047_criar_tabela_regras_atribuicao_filial'),
    (N'048_inserir_regra_frigelar_cwb'),
    (N'049_criar_dim_regiao_logistica_rules'),
    (N'050_garantir_regra_frigelar_cwb');

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

IF OBJECT_ID(N'dbo.regras_atribuicao_filial', N'U') IS NOT NULL
BEGIN
    DECLARE @colunasRegrasAtribuicao TABLE (nome SYSNAME NOT NULL);
    INSERT INTO @colunasRegrasAtribuicao (nome) VALUES
        (N'id'),
        (N'pagador_documento_key'),
        (N'filial_destino_nome'),
        (N'filial_destino_key'),
        (N'ativo'),
        (N'motivo');

    INSERT INTO @falhas (tipo, nome, detalhe)
    SELECT N'COLUNA', N'dbo.regras_atribuicao_filial.' + c.nome, N'Coluna essencial da tabela de regras ausente'
    FROM @colunasRegrasAtribuicao c
    WHERE COL_LENGTH(N'dbo.regras_atribuicao_filial', c.nome) IS NULL;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE name = N'UX_regras_atribuicao_filial_pagador_ativo'
          AND object_id = OBJECT_ID(N'dbo.regras_atribuicao_filial')
          AND is_unique = 1
          AND filter_definition LIKE N'%ativo%'
    )
        INSERT INTO @falhas VALUES (N'INDICE', N'UX_regras_atribuicao_filial_pagador_ativo', N'Indice unico de regra ativa por pagador ausente');

    IF NOT EXISTS (
        SELECT 1
        FROM dbo.regras_atribuicao_filial
        WHERE pagador_documento_key = N'92660406007040'
          AND filial_destino_nome = N'CWB - RODOGARCIA'
          AND filial_destino_key = N'cwb - rodogarcia'
          AND ativo = 1
    )
        INSERT INTO @falhas VALUES (N'DADO', N'dbo.regras_atribuicao_filial.FRIGELAR', N'Regra ativa FRIGELAR -> CWB ausente ou fora do contrato');
END;

IF OBJECT_ID(N'dbo.dim_regiao_logistica_rules', N'U') IS NOT NULL
BEGIN
    DECLARE @colunasRegiaoLogistica TABLE (nome SYSNAME NOT NULL);
    INSERT INTO @colunasRegiaoLogistica (nome) VALUES
        (N'id'),
        (N'cep_inicio'),
        (N'cep_fim'),
        (N'cidade'),
        (N'uf'),
        (N'regiao_logistica');

    INSERT INTO @falhas (tipo, nome, detalhe)
    SELECT N'COLUNA', N'dbo.dim_regiao_logistica_rules.' + c.nome, N'Coluna essencial da dimensao de regiao logistica ausente'
    FROM @colunasRegiaoLogistica c
    WHERE COL_LENGTH(N'dbo.dim_regiao_logistica_rules', c.nome) IS NULL;

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE name = N'IX_dim_regiao_logistica_rules_cep_range'
          AND object_id = OBJECT_ID(N'dbo.dim_regiao_logistica_rules')
          AND filter_definition LIKE N'%cep_inicio%'
          AND filter_definition LIKE N'%cep_fim%'
    )
        INSERT INTO @falhas VALUES (N'INDICE', N'IX_dim_regiao_logistica_rules_cep_range', N'Indice filtrado de faixa de CEP da dimensao logistica ausente');

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE name = N'IX_dim_regiao_logistica_rules_cidade_uf'
          AND object_id = OBJECT_ID(N'dbo.dim_regiao_logistica_rules')
          AND filter_definition LIKE N'%cidade%'
          AND filter_definition LIKE N'%uf%'
    )
        INSERT INTO @falhas VALUES (N'INDICE', N'IX_dim_regiao_logistica_rules_cidade_uf', N'Indice filtrado de Cidade/UF da dimensao logistica ausente');
END;

IF COL_LENGTH(N'dbo.coletas', N'request_hour') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.coletas.request_hour', N'Coluna da migration 004 ausente');

IF COL_LENGTH(N'dbo.fretes', N'finished_at') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fretes.finished_at', N'Coluna da migration 006 ausente');

IF COL_LENGTH(N'dbo.fretes', N'fit_dpn_performance_finished_at') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fretes.fit_dpn_performance_finished_at', N'Coluna da migration 006 ausente');

IF COL_LENGTH(N'dbo.fretes', N'corporation_sequence_number') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fretes.corporation_sequence_number', N'Coluna da migration 006 ausente');

IF COL_LENGTH(N'dbo.fretes', N'pick_item_id') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fretes.pick_item_id', N'Coluna da migration 041 ausente');

IF COL_LENGTH(N'dbo.coletas', N'pick_items_ids') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.coletas.pick_items_ids', N'Coluna da migration 041 ausente');

IF COL_LENGTH(N'dbo.manifestos', N'driver_contract_type') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.manifestos.driver_contract_type', N'Contrato do motorista da migration 043 ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes i
    JOIN sys.index_columns k1
      ON k1.object_id = i.object_id
     AND k1.index_id = i.index_id
     AND k1.key_ordinal = 1
    JOIN sys.columns c1
      ON c1.object_id = k1.object_id
     AND c1.column_id = k1.column_id
    JOIN sys.index_columns k2
      ON k2.object_id = i.object_id
     AND k2.index_id = i.index_id
     AND k2.key_ordinal = 2
    JOIN sys.columns c2
      ON c2.object_id = k2.object_id
     AND c2.column_id = k2.column_id
    WHERE i.name = N'IX_manifestos_competencia_operacional'
      AND i.object_id = OBJECT_ID(N'dbo.manifestos')
      AND i.type_desc = N'NONCLUSTERED'
      AND i.is_disabled = 0
      AND c1.name = N'departured_at'
      AND c2.name = N'created_at'
)
    INSERT INTO @falhas VALUES (
        N'INDICE',
        N'IX_manifestos_competencia_operacional',
        N'Indice para competencia operacional de manifestos ausente ou fora do contrato'
    );

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes i
    INNER JOIN sys.index_columns ic
        ON ic.object_id = i.object_id
       AND ic.index_id = i.index_id
       AND ic.key_ordinal = 1
    INNER JOIN sys.columns c
        ON c.object_id = ic.object_id
       AND c.column_id = ic.column_id
    WHERE i.name = N'IX_fretes_performance_minuta_cobertura'
      AND i.object_id = OBJECT_ID(N'dbo.fretes')
      AND i.type_desc = N'NONCLUSTERED'
      AND i.is_disabled = 0
      AND c.name = N'corporation_sequence_number'
)
    INSERT INTO @falhas VALUES (
        N'INDICE',
        N'IX_fretes_performance_minuta_cobertura',
        N'Indice de cobertura por minuta do Dashboard de Performance ausente ou fora do contrato'
    );

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes i
    INNER JOIN sys.index_columns ic
        ON ic.object_id = i.object_id
       AND ic.index_id = i.index_id
       AND ic.key_ordinal = 1
    INNER JOIN sys.columns c
        ON c.object_id = ic.object_id
       AND c.column_id = ic.column_id
    WHERE i.name = N'IX_fretes_pick_item_id'
      AND i.object_id = OBJECT_ID(N'dbo.fretes')
      AND i.type_desc = N'NONCLUSTERED'
      AND i.is_disabled = 0
      AND c.name = N'pick_item_id'
)
    INSERT INTO @falhas VALUES (
        N'INDICE',
        N'IX_fretes_pick_item_id',
        N'Indice de ligacao Freight.pickItemId -> Pick.pickItems[].id ausente ou fora do contrato'
    );

IF COL_LENGTH(N'dbo.log_extracoes', N'noop_count') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.log_extracoes.noop_count', N'Coluna da migration 023 ausente');

IF COL_LENGTH(N'dbo.fretes', N'cortesia') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fretes.cortesia', N'Coluna da migration 012 ausente');

IF COL_LENGTH(N'dbo.fretes', N'data_referencia_faturamento') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fretes.data_referencia_faturamento', N'Coluna da migration 016 ausente');

IF COL_LENGTH(N'dbo.fretes', N'is_elegivel_faturamento') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fretes.is_elegivel_faturamento', N'Coluna da migration 016 ausente');

IF COL_LENGTH(N'dbo.fretes', N'filial_nome_key') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fretes.filial_nome_key', N'Chave materializada de filial/responsavel fallback ausente');

IF COL_LENGTH(N'dbo.cotacoes', N'user_name_key') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.cotacoes.user_name_key', N'Chave materializada de usuario emissor de cotacao ausente');

IF COL_LENGTH(N'dbo.inventario', N'flag_comprovante_anexado') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.inventario.flag_comprovante_anexado', N'Flag materializada de comprovante anexado ausente');

IF COL_LENGTH(N'dbo.faturas_por_cliente', N'cliente_cnpj') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.faturas_por_cliente.cliente_cnpj', N'Coluna da migration 015 ausente');

IF COL_LENGTH(N'dbo.faturas_por_cliente', N'status') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.faturas_por_cliente.status', N'Coluna da migration 037 ausente');

IF COL_LENGTH(N'dbo.dim_calendario', N'is_dia_util') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.dim_calendario.is_dia_util', N'Flag de dia util da dimensao calendario ausente');

IF COL_LENGTH(N'dbo.dim_calendario', N'data_referencia_faturamento') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.dim_calendario.data_referencia_faturamento', N'Data retroativa de faturamento da dimensao calendario ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_dim_calendario_referencia_faturamento'
      AND object_id = OBJECT_ID(N'dbo.dim_calendario')
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_dim_calendario_referencia_faturamento', N'Indice de referencia de faturamento da dimensao calendario ausente');

IF COL_LENGTH(N'dbo.localizacao_cargas', N'localizacao_hash') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.localizacao_cargas.localizacao_hash', N'Coluna da migration 017 ausente');

IF COL_LENGTH(N'dbo.localizacao_cargas', N'status_normalized') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.localizacao_cargas.status_normalized', N'Coluna da migration 017 ausente');

IF COL_LENGTH(N'dbo.localizacao_cargas', N'taxed_weight_decimal') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.localizacao_cargas.taxed_weight_decimal', N'Coluna da migration 017 ausente');

IF COL_LENGTH(N'dbo.localizacao_cargas', N'destination_branch_key') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.localizacao_cargas.destination_branch_key', N'Chave materializada de responsavel destino ausente');

DECLARE @manifestosChaveMergeDef NVARCHAR(MAX);
SELECT @manifestosChaveMergeDef = cc.definition
FROM sys.computed_columns cc
WHERE cc.object_id = OBJECT_ID(N'dbo.manifestos')
  AND cc.name = N'chave_merge_hash';

IF @manifestosChaveMergeDef IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.manifestos.chave_merge_hash', N'Coluna computada de chave de merge ausente');
ELSE IF @manifestosChaveMergeDef NOT LIKE N'%pick_sequence_code%'
     OR @manifestosChaveMergeDef NOT LIKE N'%mdfe_number%'
     OR @manifestosChaveMergeDef NOT LIKE N'%identificador_unico%'
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.manifestos.chave_merge_hash', N'Chave computada deve usar pick_sequence_code, identificador_unico e mdfe_number');

DECLARE @softDeleteTables TABLE (
    tabela NVARCHAR(128) NOT NULL,
    indice SYSNAME NOT NULL
);

INSERT INTO @softDeleteTables (tabela, indice) VALUES
    (N'dbo.coletas', N'IX_coletas_ativos_origem'),
    (N'dbo.fretes', N'IX_fretes_ativos_origem'),
    (N'dbo.manifestos', N'IX_manifestos_ativos_origem'),
    (N'dbo.cotacoes', N'IX_cotacoes_ativos_origem'),
    (N'dbo.localizacao_cargas', N'IX_localizacao_cargas_ativos_origem'),
    (N'dbo.contas_a_pagar', N'IX_contas_a_pagar_ativos_origem'),
    (N'dbo.faturas_por_cliente', N'IX_faturas_por_cliente_ativos_origem'),
    (N'dbo.inventario', N'IX_inventario_ativos_origem'),
    (N'dbo.sinistros', N'IX_sinistros_ativos_origem'),
    (N'dbo.dim_usuarios', N'IX_dim_usuarios_ativos_origem'),
    (N'dbo.raster_viagens', N'IX_raster_viagens_ativos_origem'),
    (N'dbo.raster_viagem_paradas', N'IX_raster_viagem_paradas_ativos_origem');

INSERT INTO @falhas (tipo, nome, detalhe)
SELECT N'COLUNA', tabela + N'.excluido_na_origem', N'Coluna de soft delete da migration 027 ausente'
FROM @softDeleteTables
WHERE COL_LENGTH(tabela, N'excluido_na_origem') IS NULL;

DECLARE @softDeleteTimestampTables TABLE (
    tabela NVARCHAR(128) NOT NULL
);

INSERT INTO @softDeleteTimestampTables (tabela) VALUES
    (N'dbo.coletas'),
    (N'dbo.fretes'),
    (N'dbo.manifestos'),
    (N'dbo.cotacoes'),
    (N'dbo.localizacao_cargas'),
    (N'dbo.contas_a_pagar'),
    (N'dbo.faturas_por_cliente'),
    (N'dbo.inventario'),
    (N'dbo.sinistros'),
    (N'dbo.dim_usuarios'),
    (N'dbo.raster_viagens'),
    (N'dbo.raster_viagem_paradas');

INSERT INTO @falhas (tipo, nome, detalhe)
SELECT N'COLUNA', tabela + N'.data_exclusao_origem', N'Carimbo temporal do expurgo logico da migration 044 ausente'
FROM @softDeleteTimestampTables
WHERE COL_LENGTH(tabela, N'data_exclusao_origem') IS NULL;

INSERT INTO @falhas (tipo, nome, detalhe)
SELECT N'INDICE', indice, N'Indice filtrado de ativos da migration 027 ausente ou sem filtro esperado'
FROM @softDeleteTables sdt
WHERE NOT EXISTS (
    SELECT 1
    FROM sys.indexes i
    WHERE i.name = sdt.indice
      AND i.object_id = OBJECT_ID(sdt.tabela)
      AND i.filter_definition LIKE N'%excluido_na_origem%'
);

DECLARE @hashDimTables TABLE (
    tabela NVARCHAR(128) NOT NULL
);

INSERT INTO @hashDimTables (tabela) VALUES
    (N'dbo.dim_usuarios'),
    (N'dbo.dim_usuarios_historico');

INSERT INTO @falhas (tipo, nome, detalhe)
SELECT N'COLUNA', tabela + N'.hash_linha', N'Hash SHA2_256 de idempotencia dimensional ausente'
FROM @hashDimTables
WHERE COL_LENGTH(tabela, N'hash_linha') IS NULL;

INSERT INTO @falhas (tipo, nome, detalhe)
SELECT N'COLUNA', hdt.tabela + N'.hash_linha', N'hash_linha deve ser VARBINARY(32)'
FROM @hashDimTables hdt
INNER JOIN sys.columns c
    ON c.object_id = OBJECT_ID(hdt.tabela)
   AND c.name = N'hash_linha'
INNER JOIN sys.types ty
    ON ty.user_type_id = c.user_type_id
WHERE ty.name <> N'varbinary'
   OR c.max_length <> 32;

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_fpc_cliente_cnpj'
      AND object_id = OBJECT_ID(N'dbo.faturas_por_cliente')
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_fpc_cliente_cnpj', N'Indice filtrado da migration 015 ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_fretes_faturamento_data_elegivel'
      AND object_id = OBJECT_ID(N'dbo.fretes')
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_fretes_faturamento_data_elegivel', N'Indice da migration 016 ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_fretes_faturamento_responsavel_key'
      AND object_id = OBJECT_ID(N'dbo.fretes')
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_fretes_faturamento_responsavel_key', N'Indice da migration 025 ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_fretes_performance_previsao_key'
      AND object_id = OBJECT_ID(N'dbo.fretes')
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_fretes_performance_previsao_key', N'Indice da migration 025 ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_localizacao_tracking_dashboard'
      AND object_id = OBJECT_ID(N'dbo.localizacao_cargas')
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_localizacao_tracking_dashboard', N'Indice da migration 017 ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_localizacao_destination_branch_key'
      AND object_id = OBJECT_ID(N'dbo.localizacao_cargas')
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_localizacao_destination_branch_key', N'Indice da migration 025 ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_cotacoes_requested_at_usuario_key'
      AND object_id = OBJECT_ID(N'dbo.cotacoes')
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_cotacoes_requested_at_usuario_key', N'Indice da migration 026 ausente');

IF EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_cotacoes_usuario_key_requested_at'
      AND object_id = OBJECT_ID(N'dbo.cotacoes')
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_cotacoes_usuario_key_requested_at', N'Indice legado com usuario como chave lider deve ser removido');

IF EXISTS (
    SELECT 1
    FROM sys.indexes i
    WHERE i.name = N'IX_cotacoes_requested_at_usuario_key'
      AND i.object_id = OBJECT_ID(N'dbo.cotacoes')
)
AND NOT EXISTS (
    SELECT 1
    FROM sys.indexes i
    INNER JOIN sys.index_columns ic1
        ON ic1.object_id = i.object_id
       AND ic1.index_id = i.index_id
       AND ic1.key_ordinal = 1
    INNER JOIN sys.columns c1
        ON c1.object_id = ic1.object_id
       AND c1.column_id = ic1.column_id
    INNER JOIN sys.index_columns ic2
        ON ic2.object_id = i.object_id
       AND ic2.index_id = i.index_id
       AND ic2.key_ordinal = 2
    INNER JOIN sys.columns c2
        ON c2.object_id = ic2.object_id
       AND c2.column_id = ic2.column_id
    WHERE i.name = N'IX_cotacoes_requested_at_usuario_key'
      AND i.object_id = OBJECT_ID(N'dbo.cotacoes')
      AND c1.name = N'requested_at'
      AND ic1.is_descending_key = 1
      AND c2.name = N'user_name_key'
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_cotacoes_requested_at_usuario_key', N'Ordem esperada: requested_at DESC como chave lider e user_name_key como segunda chave');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_inventario_comprovante_minuta'
      AND object_id = OBJECT_ID(N'dbo.inventario')
      AND filter_definition LIKE N'%flag_comprovante_anexado%'
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_inventario_comprovante_minuta', N'Indice filtrado de comprovante anexado ausente ou desatualizado');

IF NOT EXISTS (
    SELECT 1
    FROM sys.partition_functions
    WHERE name = N'PF_fato_gv_data_referencia_mes'
)
    INSERT INTO @falhas VALUES (N'PARTITION_FUNCTION', N'PF_fato_gv_data_referencia_mes', N'Partition Function da fato Gestao a Vista ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.partition_schemes
    WHERE name = N'PS_fato_gv_data_referencia_mes'
)
    INSERT INTO @falhas VALUES (N'PARTITION_SCHEME', N'PS_fato_gv_data_referencia_mes', N'Partition Scheme da fato Gestao a Vista ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'CCI_fato_gv_fretes'
      AND object_id = OBJECT_ID(N'dbo.fato_gestao_vista_fretes')
      AND type_desc = N'CLUSTERED COLUMNSTORE'
)
    INSERT INTO @falhas VALUES (N'INDICE', N'CCI_fato_gv_fretes', N'Clustered Columnstore Index da fato Gestao a Vista ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'UX_fato_gv_fretes_indicador_minuta'
      AND object_id = OBJECT_ID(N'dbo.fato_gestao_vista_fretes')
      AND is_unique = 1
)
    INSERT INTO @falhas VALUES (N'INDICE', N'UX_fato_gv_fretes_indicador_minuta', N'Indice unico de MERGE da fato Gestao a Vista ausente');

IF OBJECT_ID(N'dbo.sp_carga_fato_gestao_vista_fretes', N'P') IS NULL
    INSERT INTO @falhas VALUES (N'PROCEDURE', N'dbo.sp_carga_fato_gestao_vista_fretes', N'Procedure de carga da fato Gestao a Vista ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'CCI_fato_gv_coletores'
      AND object_id = OBJECT_ID(N'dbo.fato_gestao_vista_coletores')
      AND type_desc = N'CLUSTERED COLUMNSTORE'
)
    INSERT INTO @falhas VALUES (N'INDICE', N'CCI_fato_gv_coletores', N'Clustered Columnstore Index da fato de coletores ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'UX_fato_gv_coletores_data_filial_classif'
      AND object_id = OBJECT_ID(N'dbo.fato_gestao_vista_coletores')
      AND is_unique = 1
)
    INSERT INTO @falhas VALUES (N'INDICE', N'UX_fato_gv_coletores_data_filial_classif', N'Indice unico de MERGE da fato de coletores ausente');

IF OBJECT_ID(N'dbo.sp_carga_fato_gestao_vista_coletores', N'P') IS NULL
    INSERT INTO @falhas VALUES (N'PROCEDURE', N'dbo.sp_carga_fato_gestao_vista_coletores', N'Procedure de carga da fato de coletores ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes i
    JOIN sys.index_columns k1
      ON k1.object_id = i.object_id
     AND k1.index_id = i.index_id
     AND k1.key_ordinal = 1
    JOIN sys.columns c1
      ON c1.object_id = k1.object_id
     AND c1.column_id = k1.column_id
    JOIN sys.index_columns k2
      ON k2.object_id = i.object_id
     AND k2.index_id = i.index_id
     AND k2.key_ordinal = 2
    JOIN sys.columns c2
      ON c2.object_id = k2.object_id
     AND c2.column_id = k2.column_id
    WHERE i.name = N'IX_fato_gv_coletores_periodo_filial'
      AND i.object_id = OBJECT_ID(N'dbo.fato_gestao_vista_coletores')
      AND c1.name = N'data_referencia'
      AND k1.is_descending_key = 1
      AND c2.name = N'filial_key'
      AND k2.is_descending_key = 0
      AND i.filter_definition LIKE N'%is_linha_valida_indicador%'
      AND i.filter_definition LIKE N'%excluido_na_origem%'
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_fato_gv_coletores_periodo_filial', N'Indice de Ranking da fato de coletores ausente ou fora do contrato');

IF NOT EXISTS (
    SELECT 1
    FROM sys.partition_functions
    WHERE name = N'PF_fato_ff_data_referencia_mes'
)
    INSERT INTO @falhas VALUES (N'PARTITION_FUNCTION', N'PF_fato_ff_data_referencia_mes', N'Partition Function da fato de faturamento de fretes ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.partition_schemes
    WHERE name = N'PS_fato_ff_data_referencia_mes'
)
    INSERT INTO @falhas VALUES (N'PARTITION_SCHEME', N'PS_fato_ff_data_referencia_mes', N'Partition Scheme da fato de faturamento de fretes ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'CCI_fato_ff'
      AND object_id = OBJECT_ID(N'dbo.fato_fretes_faturamento')
      AND type_desc = N'CLUSTERED COLUMNSTORE'
)
    INSERT INTO @falhas VALUES (N'INDICE', N'CCI_fato_ff', N'Clustered Columnstore Index da fato de faturamento de fretes ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_fato_ff_paginacao_periodo'
      AND object_id = OBJECT_ID(N'dbo.fato_fretes_faturamento')
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_fato_ff_paginacao_periodo', N'Indice de paginacao da fato de faturamento de fretes ausente');

IF OBJECT_ID(N'dbo.sp_carga_fato_fretes_faturamento', N'P') IS NULL
    INSERT INTO @falhas VALUES (N'PROCEDURE', N'dbo.sp_carga_fato_fretes_faturamento', N'Procedure de carga da fato de faturamento de fretes ausente');

IF COL_LENGTH(N'dbo.fato_fretes_faturamento', N'data_referencia_faturamento_real') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fato_fretes_faturamento.data_referencia_faturamento_real', N'Data real de faturamento ausente na fato');

IF COL_LENGTH(N'dbo.fato_fretes_faturamento', N'is_data_faturamento_retroagida') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fato_fretes_faturamento.is_data_faturamento_retroagida', N'Flag de retroacao de faturamento ausente na fato');

IF NOT EXISTS (
    SELECT 1
    FROM sys.partition_functions
    WHERE name = N'PF_fato_gvf_data_emissao_mes'
)
    INSERT INTO @falhas VALUES (N'PARTITION_FUNCTION', N'PF_fato_gvf_data_emissao_mes', N'Partition Function da fato de faturas por cliente ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.partition_schemes
    WHERE name = N'PS_fato_gvf_data_emissao_mes'
)
    INSERT INTO @falhas VALUES (N'PARTITION_SCHEME', N'PS_fato_gvf_data_emissao_mes', N'Partition Scheme da fato de faturas por cliente ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'CCI_fato_gvf'
      AND object_id = OBJECT_ID(N'dbo.fato_gestao_vista_faturas')
      AND type_desc = N'CLUSTERED COLUMNSTORE'
)
    INSERT INTO @falhas VALUES (N'INDICE', N'CCI_fato_gvf', N'Clustered Columnstore Index da fato de faturas por cliente ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes i
    JOIN sys.index_columns k1
      ON k1.object_id = i.object_id
     AND k1.index_id = i.index_id
     AND k1.key_ordinal = 1
    JOIN sys.columns c1
      ON c1.object_id = k1.object_id
     AND c1.column_id = k1.column_id
    JOIN sys.index_columns k2
      ON k2.object_id = i.object_id
     AND k2.index_id = i.index_id
     AND k2.key_ordinal = 2
    JOIN sys.columns c2
      ON c2.object_id = k2.object_id
     AND c2.column_id = k2.column_id
    WHERE i.name = N'IX_fato_gvf_aging'
      AND i.object_id = OBJECT_ID(N'dbo.fato_gestao_vista_faturas')
      AND c1.name = N'data_emissao_cte'
      AND k1.is_descending_key = 1
      AND c2.name = N'unique_id'
      AND k2.is_descending_key = 1
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_fato_gvf_aging', N'Indice de Aging da fato de faturas por cliente ausente ou fora do contrato');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_fato_gvf_paginacao_periodo'
      AND object_id = OBJECT_ID(N'dbo.fato_gestao_vista_faturas')
      AND filter_definition LIKE N'%excluido_na_origem%'
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_fato_gvf_paginacao_periodo', N'Indice de paginacao da fato de faturas por cliente ausente');

IF OBJECT_ID(N'dbo.sp_carga_fato_gestao_vista_faturas', N'P') IS NULL
    INSERT INTO @falhas VALUES (N'PROCEDURE', N'dbo.sp_carga_fato_gestao_vista_faturas', N'Procedure de carga da fato de faturas por cliente ausente');

IF COL_LENGTH(N'dbo.fato_gestao_vista_manifestos', N'sequence_code') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fato_gestao_vista_manifestos.sequence_code', N'Chave operacional da fato de manifestos ausente');

IF COL_LENGTH(N'dbo.fato_gestao_vista_manifestos', N'receita_total') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fato_gestao_vista_manifestos.receita_total', N'Receita materializada da fato de manifestos ausente');

IF COL_LENGTH(N'dbo.fato_gestao_vista_manifestos', N'capacidade_lotacao_kg') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fato_gestao_vista_manifestos.capacidade_lotacao_kg', N'Capacidade materializada da fato de manifestos ausente');

IF COL_LENGTH(N'dbo.fato_gestao_vista_manifestos', N'tipo_contrato_veiculo') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fato_gestao_vista_manifestos.tipo_contrato_veiculo', N'Tipo de contrato do veiculo materializado ausente');

IF COL_LENGTH(N'dbo.fato_gestao_vista_manifestos', N'tipo_contrato_motorista') IS NULL
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.fato_gestao_vista_manifestos.tipo_contrato_motorista', N'Tipo de contrato do motorista materializado ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.key_constraints
    WHERE name = N'PK_fato_gv_manifestos'
      AND parent_object_id = OBJECT_ID(N'dbo.fato_gestao_vista_manifestos')
      AND type = N'PK'
)
    INSERT INTO @falhas VALUES (N'PK', N'PK_fato_gv_manifestos', N'PK por sequence_code da fato de manifestos ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_fato_manifestos_data_filial'
      AND object_id = OBJECT_ID(N'dbo.fato_gestao_vista_manifestos')
      AND filter_definition LIKE N'%excluido_na_origem%'
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_fato_manifestos_data_filial', N'Indice de periodo/filial da fato de manifestos ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = N'IX_fato_manifestos_filtros'
      AND object_id = OBJECT_ID(N'dbo.fato_gestao_vista_manifestos')
      AND filter_definition LIKE N'%excluido_na_origem%'
)
    INSERT INTO @falhas VALUES (N'INDICE', N'IX_fato_manifestos_filtros', N'Indice de filtros da fato de manifestos ausente');

IF OBJECT_ID(N'dbo.sp_carga_fato_gestao_vista_manifestos', N'P') IS NULL
    INSERT INTO @falhas VALUES (N'PROCEDURE', N'dbo.sp_carga_fato_gestao_vista_manifestos', N'Procedure de carga da fato de manifestos ausente');

IF OBJECT_DEFINITION(OBJECT_ID(N'dbo.vw_fato_manifestos_dash')) NOT LIKE N'%fato_gestao_vista_manifestos%'
    INSERT INTO @falhas VALUES (N'VIEW_DEFINICAO', N'dbo.vw_fato_manifestos_dash', N'View leve de manifestos deve consumir a fato materializada');

IF OBJECT_DEFINITION(OBJECT_ID(N'dbo.vw_manifestos_powerbi')) LIKE N'%OPENJSON%'
    INSERT INTO @falhas VALUES (N'VIEW_DEFINICAO', N'dbo.vw_manifestos_powerbi', N'View de compatibilidade de manifestos nao deve executar OPENJSON sob demanda');

IF NOT EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.vw_fretes_powerbi')
      AND name = N'Comprovante Anexado'
)
    INSERT INTO @falhas VALUES (N'VIEW_COLUNA', N'dbo.vw_fretes_powerbi.[Comprovante Anexado]', N'Coluna do KPI Comprovante Anexado ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.vw_fretes_powerbi')
      AND name = N'Volumes'
)
    INSERT INTO @falhas VALUES (N'VIEW_COLUNA', N'dbo.vw_fretes_powerbi.[Volumes]', N'Coluna do KPI Volumes ausente');

-- Contrato textual para inspeção: dbo.vw_fretes_powerbi.[Responsável Região Destino Key]
DECLARE @colResponsavelRegiaoDestinoKey SYSNAME =
    N'Respons' + NCHAR(225) + N'vel Regi' + NCHAR(227) + N'o Destino Key';
DECLARE @nomeResponsavelRegiaoDestinoKey NVARCHAR(300) =
    N'dbo.vw_fretes_powerbi.[' + @colResponsavelRegiaoDestinoKey + N']';

IF NOT EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.vw_fretes_powerbi')
      AND name = @colResponsavelRegiaoDestinoKey
)
    INSERT INTO @falhas VALUES (N'VIEW_COLUNA', @nomeResponsavelRegiaoDestinoKey, N'Chave controlada para filtro de responsavel ausente');

IF NOT EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.vw_cotacoes_powerbi')
      AND name = N'Usuario Key'
)
    INSERT INTO @falhas VALUES (N'VIEW_COLUNA', N'dbo.vw_cotacoes_powerbi.[Usuario Key]', N'Chave controlada para filtro de usuario emissor ausente');

IF OBJECT_DEFINITION(OBJECT_ID(N'dbo.vw_fretes_powerbi')) NOT LIKE N'%lc.invoices_volumes%'
    INSERT INTO @falhas VALUES (N'VIEW_DEFINICAO', N'dbo.vw_fretes_powerbi.[Volumes]', N'Volumes deve usar localizacao_cargas.invoices_volumes como fonte oficial');

DECLARE @viewsSoftDelete TABLE (nome SYSNAME NOT NULL);
INSERT INTO @viewsSoftDelete (nome) VALUES
    (N'vw_faturas_por_cliente_powerbi'),
    (N'vw_fretes_powerbi'),
    (N'vw_coletas_powerbi'),
    (N'vw_cotacoes_powerbi'),
    (N'vw_contas_a_pagar_powerbi'),
    (N'vw_localizacao_cargas_powerbi'),
    (N'vw_manifestos_powerbi'),
    (N'vw_fato_manifestos_dash'),
    (N'vw_inventario_powerbi'),
    (N'vw_sinistros_powerbi'),
    (N'vw_raster_sm_transit_time'),
    (N'vw_dim_clientes'),
    (N'vw_dim_usuarios');

INSERT INTO @falhas (tipo, nome, detalhe)
SELECT N'VIEW_DEFINICAO', N'dbo.' + nome, N'View de consumo deve filtrar excluido_na_origem por padrao'
FROM @viewsSoftDelete
WHERE OBJECT_DEFINITION(OBJECT_ID(N'dbo.' + nome)) NOT LIKE N'%excluido_na_origem%';

IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.vw_fretes_powerbi')
      AND name = N'Comprovante Anexado'
)
AND COL_LENGTH(N'dbo.inventario', N'flag_comprovante_anexado') IS NOT NULL
BEGIN
    DECLARE @inventarioComComprovante BIT = 0;
    DECLARE @viewComComprovante BIT = 0;

    EXEC sys.sp_executesql
        N'SELECT @saida = CASE WHEN EXISTS (SELECT 1 FROM dbo.inventario WHERE flag_comprovante_anexado = 1) THEN 1 ELSE 0 END',
        N'@saida BIT OUTPUT',
        @saida = @inventarioComComprovante OUTPUT;

    EXEC sys.sp_executesql
        N'SELECT @saida = CASE WHEN EXISTS (SELECT 1 FROM dbo.vw_fretes_powerbi WHERE [Comprovante Anexado] = N''Sim'') THEN 1 ELSE 0 END',
        N'@saida BIT OUTPUT',
        @saida = @viewComComprovante OUTPUT;

    IF @inventarioComComprovante = 1 AND @viewComComprovante = 0
        INSERT INTO @falhas VALUES (N'DADO', N'dbo.vw_fretes_powerbi.[Comprovante Anexado]', N'Inventario possui comprovantes, mas a view de fretes nao publica Sim');
END;

IF EXISTS (
    SELECT 1
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = 'dbo'
      AND TABLE_NAME = 'coletas'
      AND COLUMN_NAME = 'sequence_code'
      AND IS_NULLABLE = 'YES'
)
    INSERT INTO @falhas VALUES (N'COLUNA', N'dbo.coletas.sequence_code', N'Deve estar NOT NULL apos migration 010');

IF NOT EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.vw_coletas_powerbi')
      AND name = N'Região Logística'
)
    INSERT INTO @falhas VALUES (N'VIEW_COLUNA', N'dbo.vw_coletas_powerbi.[Região Logística]', N'View de coletas deve publicar regiao logistica resolvida pelo ETL');

IF OBJECT_ID(N'dbo.vw_coletas_powerbi', N'V') IS NOT NULL
   AND OBJECT_DEFINITION(OBJECT_ID(N'dbo.vw_coletas_powerbi')) NOT LIKE N'%dim_regiao_logistica_rules%'
    INSERT INTO @falhas VALUES (N'VIEW_DEFINICAO', N'dbo.vw_coletas_powerbi', N'View de coletas deve resolver regiao logistica pela dimensao governada');

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
