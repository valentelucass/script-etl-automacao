-- ============================================
-- Migration 014 - Cria tabelas Raster v1
-- ============================================

IF OBJECT_ID(N'dbo.schema_migrations', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.schema_migrations (
        migration_id NVARCHAR(255) NOT NULL,
        applied_at DATETIME2(0) NOT NULL CONSTRAINT DF_schema_migrations_applied_at DEFAULT SYSUTCDATETIME(),
        checksum_sha256 VARCHAR(64) NULL,
        notes NVARCHAR(500) NULL,
        CONSTRAINT PK_schema_migrations PRIMARY KEY (migration_id)
    );
END;
GO

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.raster_viagens') AND type in (N'U'))
BEGIN
    CREATE TABLE dbo.raster_viagens (
        cod_solicitacao BIGINT NOT NULL PRIMARY KEY,
        sequencial BIGINT NULL,
        cod_filial INT NULL,
        status_viagem NVARCHAR(10) NULL,
        placa_veiculo NVARCHAR(20) NULL,
        placa_carreta1 NVARCHAR(20) NULL,
        placa_carreta2 NVARCHAR(20) NULL,
        placa_carreta3 NVARCHAR(20) NULL,
        cpf_motorista1 NVARCHAR(20) NULL,
        cpf_motorista2 NVARCHAR(20) NULL,
        cnpj_cliente_orig NVARCHAR(20) NULL,
        cnpj_cliente_dest NVARCHAR(20) NULL,
        cod_ibge_cidade_orig BIGINT NULL,
        cod_ibge_cidade_dest BIGINT NULL,
        data_hora_prev_ini DATETIMEOFFSET(3) NULL,
        data_hora_prev_fim DATETIMEOFFSET(3) NULL,
        data_hora_real_ini DATETIMEOFFSET(3) NULL,
        data_hora_real_fim DATETIMEOFFSET(3) NULL,
        data_hora_identificou_fim_viagem DATETIMEOFFSET(3) NULL,
        tempo_total_viagem_min INT NULL,
        dentro_prazo_raster NVARCHAR(5) NULL,
        percentual_atraso_raster DECIMAL(10, 2) NULL,
        rodou_fora_horario NVARCHAR(5) NULL,
        velocidade_media DECIMAL(10, 2) NULL,
        eventos_velocidade INT NULL,
        desvios_de_rota INT NULL,
        cod_rota BIGINT NULL,
        rota_descricao NVARCHAR(500) NULL,
        link_timeline NVARCHAR(1000) NULL,
        metadata NVARCHAR(MAX) NULL,
        data_extracao DATETIME2(3) NOT NULL CONSTRAINT DF_raster_viagens_data_extracao DEFAULT SYSUTCDATETIME()
    );
END
GO

IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'dbo.raster_viagem_paradas') AND type in (N'U'))
BEGIN
    CREATE TABLE dbo.raster_viagem_paradas (
        cod_solicitacao BIGINT NOT NULL,
        ordem INT NOT NULL,
        tipo NVARCHAR(5) NULL,
        cod_ibge_cidade BIGINT NULL,
        cnpj_cliente NVARCHAR(20) NULL,
        codigo_cliente NVARCHAR(50) NULL,
        data_hora_prev_chegada DATETIMEOFFSET(3) NULL,
        data_hora_prev_saida DATETIMEOFFSET(3) NULL,
        data_hora_real_chegada DATETIMEOFFSET(3) NULL,
        data_hora_real_saida DATETIMEOFFSET(3) NULL,
        latitude DECIMAL(18, 8) NULL,
        longitude DECIMAL(18, 8) NULL,
        dentro_prazo_raster NVARCHAR(5) NULL,
        diferenca_tempo_raster NVARCHAR(80) NULL,
        km_percorrido_entrega DECIMAL(18, 3) NULL,
        km_restante_entrega DECIMAL(18, 3) NULL,
        chegou_na_entrega NVARCHAR(5) NULL,
        data_hora_ultima_posicao DATETIMEOFFSET(3) NULL,
        latitude_ultima_posicao DECIMAL(18, 8) NULL,
        longitude_ultima_posicao DECIMAL(18, 8) NULL,
        referencia_ultima_posicao NVARCHAR(500) NULL,
        metadata NVARCHAR(MAX) NULL,
        data_extracao DATETIME2(3) NOT NULL CONSTRAINT DF_raster_viagem_paradas_data_extracao DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_raster_viagem_paradas PRIMARY KEY (cod_solicitacao, ordem)
    );
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = 'FK_raster_viagem_paradas_viagens')
BEGIN
    ALTER TABLE dbo.raster_viagem_paradas
    ADD CONSTRAINT FK_raster_viagem_paradas_viagens
        FOREIGN KEY (cod_solicitacao)
        REFERENCES dbo.raster_viagens(cod_solicitacao);
END
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_raster_viagens_data_extracao' AND object_id = OBJECT_ID('dbo.raster_viagens'))
    CREATE INDEX IX_raster_viagens_data_extracao ON dbo.raster_viagens(data_extracao);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_raster_viagens_status_viagem' AND object_id = OBJECT_ID('dbo.raster_viagens'))
    CREATE INDEX IX_raster_viagens_status_viagem ON dbo.raster_viagens(status_viagem);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_raster_viagens_placa_veiculo' AND object_id = OBJECT_ID('dbo.raster_viagens'))
    CREATE INDEX IX_raster_viagens_placa_veiculo ON dbo.raster_viagens(placa_veiculo);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_raster_viagens_prev_ini' AND object_id = OBJECT_ID('dbo.raster_viagens'))
    CREATE INDEX IX_raster_viagens_prev_ini ON dbo.raster_viagens(data_hora_prev_ini);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_raster_viagens_real_fim' AND object_id = OBJECT_ID('dbo.raster_viagens'))
    CREATE INDEX IX_raster_viagens_real_fim ON dbo.raster_viagens(data_hora_real_fim);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_raster_viagem_paradas_data_extracao' AND object_id = OBJECT_ID('dbo.raster_viagem_paradas'))
    CREATE INDEX IX_raster_viagem_paradas_data_extracao ON dbo.raster_viagem_paradas(data_extracao);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_raster_viagem_paradas_prev_chegada' AND object_id = OBJECT_ID('dbo.raster_viagem_paradas'))
    CREATE INDEX IX_raster_viagem_paradas_prev_chegada ON dbo.raster_viagem_paradas(data_hora_prev_chegada);
GO

IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE migration_id = N'014_criar_tabelas_raster')
BEGIN
    INSERT INTO dbo.schema_migrations (migration_id, notes)
    VALUES (
        N'014_criar_tabelas_raster',
        N'Cria tabelas Raster v1 e indices operacionais para viagens e paradas.'
    );
END;
GO
