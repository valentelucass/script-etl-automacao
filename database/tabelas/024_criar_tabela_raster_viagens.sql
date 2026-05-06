-- ============================================
-- Script de criacao da tabela 'raster_viagens'
-- ============================================

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

    PRINT 'Tabela raster_viagens criada com sucesso!';
END
ELSE
BEGIN
    PRINT 'Tabela raster_viagens ja existe. Pulando criacao.';
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
