-- ============================================
-- Script de criacao da tabela 'raster_viagem_paradas'
-- ============================================

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

    PRINT 'Tabela raster_viagem_paradas criada com sucesso!';
END
ELSE
BEGIN
    PRINT 'Tabela raster_viagem_paradas ja existe. Pulando criacao.';
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

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_raster_viagem_paradas_data_extracao' AND object_id = OBJECT_ID('dbo.raster_viagem_paradas'))
    CREATE INDEX IX_raster_viagem_paradas_data_extracao ON dbo.raster_viagem_paradas(data_extracao);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_raster_viagem_paradas_prev_chegada' AND object_id = OBJECT_ID('dbo.raster_viagem_paradas'))
    CREATE INDEX IX_raster_viagem_paradas_prev_chegada ON dbo.raster_viagem_paradas(data_hora_prev_chegada);
GO
