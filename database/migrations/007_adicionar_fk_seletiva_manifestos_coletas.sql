PRINT 'Migration 007: adicionar FK seletiva manifestos.pick_sequence_code -> coletas.sequence_code';
GO

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

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_manifestos_pick_sequence_code'
      AND object_id = OBJECT_ID('dbo.manifestos')
)
BEGIN
    CREATE INDEX IX_manifestos_pick_sequence_code ON dbo.manifestos(pick_sequence_code);
    PRINT 'Indice IX_manifestos_pick_sequence_code criado.';
END
ELSE
BEGIN
    PRINT 'Indice IX_manifestos_pick_sequence_code ja existe.';
END
GO

IF EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = 'FK_manifestos_pick_sequence_code_coletas'
      AND parent_object_id = OBJECT_ID('dbo.manifestos')
)
BEGIN
    PRINT 'FK_manifestos_pick_sequence_code_coletas ja existe. Nada a fazer.';
END
ELSE IF EXISTS (
    SELECT 1
    FROM dbo.manifestos m
    WHERE m.pick_sequence_code IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM dbo.coletas c
          WHERE c.sequence_code = m.pick_sequence_code
      )
)
BEGIN
    UPDATE m
       SET pick_sequence_code = NULL
      FROM dbo.manifestos m
     WHERE m.pick_sequence_code IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
             FROM dbo.coletas c
            WHERE c.sequence_code = m.pick_sequence_code
       );
    PRINT 'Manifestos orfaos normalizados com pick_sequence_code = NULL antes da ativacao da FK.';
END

IF EXISTS (
    SELECT 1
    FROM dbo.manifestos m
    WHERE m.pick_sequence_code IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM dbo.coletas c
          WHERE c.sequence_code = m.pick_sequence_code
      )
)
BEGIN
    THROW 51007, 'Persistem manifestos orfaos apos normalizacao; abortando ativacao da FK.', 1;
END

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = 'FK_manifestos_pick_sequence_code_coletas'
      AND parent_object_id = OBJECT_ID('dbo.manifestos')
)
BEGIN
    ALTER TABLE dbo.manifestos WITH CHECK
        ADD CONSTRAINT FK_manifestos_pick_sequence_code_coletas
            FOREIGN KEY (pick_sequence_code)
            REFERENCES dbo.coletas(sequence_code);

    ALTER TABLE dbo.manifestos CHECK CONSTRAINT FK_manifestos_pick_sequence_code_coletas;
    PRINT 'FK_manifestos_pick_sequence_code_coletas criada com sucesso.';
END
GO

IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE migration_id = N'007_adicionar_fk_seletiva_manifestos_coletas')
BEGIN
    INSERT INTO dbo.schema_migrations (migration_id, notes)
    VALUES (
        N'007_adicionar_fk_seletiva_manifestos_coletas',
        N'Cria indice e FK seletiva de manifestos.pick_sequence_code para coletas.sequence_code.'
    );
END;
GO
