PRINT 'Migration 008: criar tabela sys_replay_idempotency';
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

IF OBJECT_ID(N'dbo.sys_replay_idempotency', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.sys_replay_idempotency (
        idempotency_key NVARCHAR(255) NOT NULL,
        api NVARCHAR(100) NOT NULL CONSTRAINT DF_sys_replay_idempotency_api DEFAULT (''),
        entidade NVARCHAR(100) NOT NULL CONSTRAINT DF_sys_replay_idempotency_entidade DEFAULT (''),
        data_inicio DATE NOT NULL,
        data_fim DATE NOT NULL,
        modo NVARCHAR(50) NOT NULL CONSTRAINT DF_sys_replay_idempotency_modo DEFAULT ('replay'),
        status NVARCHAR(20) NOT NULL,
        execution_uuid NVARCHAR(100) NULL,
        started_at DATETIME2 NOT NULL,
        finished_at DATETIME2 NULL,
        expires_at DATETIME2 NULL,
        last_error NVARCHAR(1000) NULL,
        updated_at DATETIME2 NOT NULL CONSTRAINT DF_sys_replay_idempotency_updated_at DEFAULT SYSDATETIME(),
        CONSTRAINT PK_sys_replay_idempotency PRIMARY KEY CLUSTERED (idempotency_key)
    );
    PRINT 'Tabela sys_replay_idempotency criada com sucesso.';
END
ELSE
BEGIN
    PRINT 'Tabela sys_replay_idempotency ja existe. Nada a fazer.';
END
GO

IF NOT EXISTS (SELECT 1 FROM dbo.schema_migrations WHERE migration_id = N'008_criar_tabela_sys_replay_idempotency')
BEGIN
    INSERT INTO dbo.schema_migrations (migration_id, notes)
    VALUES (
        N'008_criar_tabela_sys_replay_idempotency',
        N'Cria tabela de idempotencia de replay e indice operacional.'
    );
END;
GO

IF NOT EXISTS (
    SELECT 1
      FROM sys.indexes
     WHERE name = 'IX_sys_replay_idempotency_status_expires'
       AND object_id = OBJECT_ID('dbo.sys_replay_idempotency')
)
BEGIN
    CREATE INDEX IX_sys_replay_idempotency_status_expires
        ON dbo.sys_replay_idempotency (status, expires_at, updated_at DESC);
    PRINT 'Indice IX_sys_replay_idempotency_status_expires criado.';
END
ELSE
BEGIN
    PRINT 'Indice IX_sys_replay_idempotency_status_expires ja existe.';
END
GO
