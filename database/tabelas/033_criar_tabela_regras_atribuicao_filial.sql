-- ============================================================================
-- Tabela parametrica para regras comerciais de atribuicao de filial no faturamento
-- ============================================================================

SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
GO

IF OBJECT_ID(N'dbo.regras_atribuicao_filial', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.regras_atribuicao_filial (
        id INT IDENTITY(1,1) NOT NULL,
        pagador_documento_key NVARCHAR(50) NOT NULL,
        filial_destino_nome NVARCHAR(255) NOT NULL,
        filial_destino_key NVARCHAR(255) NOT NULL,
        ativo BIT NOT NULL CONSTRAINT DF_regras_atribuicao_filial_ativo DEFAULT (1),
        motivo NVARCHAR(255) NULL,
        CONSTRAINT PK_regras_atribuicao_filial PRIMARY KEY (id),
        CONSTRAINT CK_regras_atribuicao_filial_pagador_documento_key
            CHECK (LTRIM(RTRIM(pagador_documento_key)) <> N''),
        CONSTRAINT CK_regras_atribuicao_filial_destino_nome
            CHECK (LTRIM(RTRIM(filial_destino_nome)) <> N''),
        CONSTRAINT CK_regras_atribuicao_filial_destino_key
            CHECK (LTRIM(RTRIM(filial_destino_key)) <> N'')
    );

    PRINT 'Tabela dbo.regras_atribuicao_filial criada.';
END
ELSE
BEGIN
    PRINT 'Tabela dbo.regras_atribuicao_filial ja existe.';
END;
GO

IF OBJECT_ID(N'dbo.regras_atribuicao_filial', N'U') IS NOT NULL
   AND NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE name = N'UX_regras_atribuicao_filial_pagador_ativo'
          AND object_id = OBJECT_ID(N'dbo.regras_atribuicao_filial')
   )
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX UX_regras_atribuicao_filial_pagador_ativo
        ON dbo.regras_atribuicao_filial(pagador_documento_key)
        WHERE ativo = 1;

    PRINT 'Indice UX_regras_atribuicao_filial_pagador_ativo criado.';
END;
GO

IF EXISTS (
    SELECT 1
    FROM dbo.regras_atribuicao_filial
    WHERE pagador_documento_key = N'92660406007040'
      AND ativo = 1
)
BEGIN
    UPDATE dbo.regras_atribuicao_filial
       SET filial_destino_nome = N'CWB - RODOGARCIA',
           filial_destino_key = N'cwb - rodogarcia',
           motivo = N'Migracao financeira solicitada pela diretoria (emissao original na NHB)'
     WHERE pagador_documento_key = N'92660406007040'
       AND ativo = 1;
END
ELSE
BEGIN
    INSERT INTO dbo.regras_atribuicao_filial (
        pagador_documento_key,
        filial_destino_nome,
        filial_destino_key,
        ativo,
        motivo
    ) VALUES (
        N'92660406007040',
        N'CWB - RODOGARCIA',
        N'cwb - rodogarcia',
        CAST(1 AS BIT),
        N'Migracao financeira solicitada pela diretoria (emissao original na NHB)'
    );
END;
GO
