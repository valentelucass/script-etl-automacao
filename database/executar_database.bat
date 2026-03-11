@echo off
SETLOCAL EnableExtensions DisableDelayedExpansion

REM ================================================================
REM Script : database/executar_database.bat
REM Papel  : Executa scripts SQL Server do sistema ESL Cloud.
REM
REM MODOS DE USO:
REM
REM   executar_database.bat
REM     Modo PADRAO (seguro):
REM     - Banco ja deve existir
REM     - Garante tabelas base sem usar DROP/CREATE DATABASE
REM     - Executa: migrations, indices, views, validacoes
REM     - NAO executa DROP/CREATE DATABASE
REM     - Idempotente - pode rodar multiplas vezes
REM
REM   executar_database.bat --recriar
REM     Modo DEV (destrutivo - requer confirmacao):
REM     - Apaga e recria o banco do zero
REM     - Executa: tabelas, migrations, indices, views, validacoes
REM     - ATENCAO: todos os dados serao perdidos
REM
REM AUTENTICACAO SQL SERVER:
REM   Windows Auth (padrao): deixe DB_USER vazio em config.bat
REM   SQL Auth              : preencha DB_USER e DB_PASSWORD em config.bat
REM   NAO use "sa". Crie um usuario dedicado com permissoes minimas.
REM
REM BANCO SQLite DE AUTENTICACAO:
REM   Gerenciado exclusivamente pela aplicacao Java (extrator.jar).
REM   Caminho: C:\ProgramData\ExtratorESL\security\users.db
REM   Scripts em database/security_sqlite/ sao apenas referencia.
REM   NAO sao executados via sqlcmd neste script.
REM ================================================================

chcp 65001 >nul
cd /d "%~dp0"

REM --- Detectar modo ---
set "MODO_RECRIAR=0"
if /i "%~1"=="--recriar" set "MODO_RECRIAR=1"

echo.
if "%MODO_RECRIAR%"=="1" (
    echo ============================================
    echo   EXECUTAR DATABASE - MODO DEV ^(--recriar^)
    echo   ATENCAO: banco sera apagado e recriado.
    echo ============================================
) else (
    echo ============================================
    echo   EXECUTAR DATABASE - MODO PADRAO
    echo   Banco existente - sem DROP/CREATE
    echo ============================================
)
echo.

REM --- 1. Verificar config.bat ---
if not exist "config.bat" (
    echo [ERRO] Arquivo config.bat nao encontrado!
    echo.
    echo Copie config_exemplo.bat para config.bat e preencha:
    echo   DB_SERVER, DB_NAME
    echo   DB_USER e DB_PASSWORD apenas para autenticacao SQL
    echo   ^(deixe vazios para usar Windows Authentication^)
    echo.
    pause
    exit /b 1
)

REM --- 2. Carregar config.bat ---
call config.bat

REM --- 3. Validar variaveis obrigatorias ---
if "%DB_SERVER%"=="" (
    echo [ERRO] DB_SERVER nao definido no config.bat
    pause
    exit /b 1
)
if "%DB_NAME%"=="" (
    echo [ERRO] DB_NAME nao definido no config.bat
    pause
    exit /b 1
)

REM --- 4. Verificar sqlcmd ---
where sqlcmd >nul 2>nul
if errorlevel 1 (
    echo [ERRO] sqlcmd nao encontrado no PATH.
    echo.
    echo Instale as "SQL Server Command Line Utilities" ou adicione o diretorio
    echo do SQL Server ao PATH.
    echo Ex: C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\170\Tools\Binn
    echo.
    pause
    exit /b 1
)

REM --- 5. Definir autenticacao: Windows (-E) ou SQL (-U + SQLCMDPASSWORD) ---
if "%DB_USER%"=="" (
    set "AUTH_CMD=-E"
    set "SQLCMDPASSWORD="
    echo Autenticacao: Windows ^(integrada^)
) else (
    if "%DB_PASSWORD%"=="" (
        echo [ERRO] DB_USER definido mas DB_PASSWORD esta vazio no config.bat
        pause
        exit /b 1
    )
    set "AUTH_CMD=-U %DB_USER%"
    set "SQLCMDPASSWORD=%DB_PASSWORD%"
    echo Autenticacao: SQL ^(%DB_USER%^)
)
echo Servidor: %DB_SERVER%  ^|  Banco: %DB_NAME%
echo.

REM ================================================================
REM MODO DEV: recriar banco do zero (apenas com --recriar)
REM ================================================================
if /i "%MODO_RECRIAR%"=="1" (
    call :RECRIAR_BANCO
    if errorlevel 2 exit /b 0
    if errorlevel 1 exit /b 1
)

REM ================================================================
REM AMBOS OS MODOS: tabelas base, migrations, indices, views, validacoes
REM ================================================================

REM --- Tabelas base (idempotente - cria faltantes sem recriar o banco) ---
if /i not "%MODO_RECRIAR%"=="1" (
    call :GARANTIR_TABELAS_BASE
    if errorlevel 1 exit /b 1
)

REM --- Migrations (criticas - para em erro) ---
echo [ETAPA] Migrations...
for %%F in (
    "migrations\001_criar_tabela_schema_migrations.sql"
    "migrations\002_corrigir_constraint_manifestos.sql"
    "migrations\003_corrigir_tipo_datetime_faturas_graphql.sql"
    "migrations\004_adicionar_request_hour_coletas.sql"
) do (
    if not exist %%F (
        echo   [SKIP] Nao encontrada: %%~F
    ) else (
        echo   [EXEC] %%~F
        sqlcmd -S %DB_SERVER% -d %DB_NAME% %AUTH_CMD% -i "%%~F" -b
        if errorlevel 1 (
            echo [ERRO] Falha critica na migration: %%~F
            set "SQLCMDPASSWORD="
            pause
            exit /b 1
        )
    )
)
echo [OK] Migrations concluidas.
echo.

REM --- Indices (nao-criticos - avisa e continua) ---
echo [ETAPA] Indices de performance...
for %%F in (
    "indices\001_criar_indices_performance.sql"
) do (
    if exist %%F (
        echo   [EXEC] %%~F
        sqlcmd -S %DB_SERVER% -d %DB_NAME% %AUTH_CMD% -i "%%~F"
        if errorlevel 1 echo   [AVISO] Indice pode ja existir: %%~F
    )
)
echo [OK] Indices concluidos.
echo.

REM --- Views PowerBI e Dimensao (nao-criticas - avisa e continua) ---
echo [ETAPA] Views ^(PowerBI + Dimensao^)...
for %%F in (
    "views\011_criar_view_faturas_por_cliente_powerbi.sql"
    "views\012_criar_view_fretes_powerbi.sql"
    "views\013_criar_view_coletas_powerbi.sql"
    "views\014_criar_view_faturas_graphql_powerbi.sql"
    "views\015_criar_view_cotacoes_powerbi.sql"
    "views\016_criar_view_contas_a_pagar_powerbi.sql"
    "views\017_criar_view_localizacao_cargas_powerbi.sql"
    "views\018_criar_view_manifestos_powerbi.sql"
    "views\019_criar_view_bi_monitoramento.sql"
    "views-dimensao\019_criar_view_dim_filiais.sql"
    "views-dimensao\020_criar_view_dim_clientes.sql"
    "views-dimensao\021_criar_view_dim_veiculos.sql"
    "views-dimensao\022_criar_view_dim_motoristas.sql"
    "views-dimensao\023_criar_view_dim_planocontas.sql"
    "views-dimensao\024_criar_view_dim_usuarios.sql"
) do (
    if exist %%F (
        echo   [EXEC] %%~F
        sqlcmd -S %DB_SERVER% -d %DB_NAME% %AUTH_CMD% -i "%%~F"
        if errorlevel 1 echo   [AVISO] View pode ja existir: %%~F
    )
)
echo [OK] Views concluidas.
echo.

REM --- Validacoes de leitura (seguras, sem scripts destrutivos) ---
REM Excluidos: 027 diagnosticar_null, 030 api_vs_banco, 031 limpar_dados
echo [ETAPA] Validacoes...
for %%F in (
    "validacao\025_validar_views_dimensao.sql"
    "validacao\026_validar_tipo_destroy_user_id.sql"
    "validacao\028_validacao_rapida_extracao.sql"
    "validacao\029_verificar_duplicacao_faturas.sql"
) do (
    if exist %%F (
        echo   [EXEC] %%~F
        sqlcmd -S %DB_SERVER% -d %DB_NAME% %AUTH_CMD% -i "%%~F"
        if errorlevel 1 echo   [AVISO] Validacao retornou aviso: %%~F
    )
)
echo [OK] Validacoes concluidas.
echo.

REM Limpar senha da memoria
set "SQLCMDPASSWORD="

echo ============================================
if "%MODO_RECRIAR%"=="1" (
    echo   CONCLUIDO - Banco recriado e configurado.
) else (
    echo   CONCLUIDO - Scripts SQL executados sem recriar o banco.
)
echo ============================================
echo.
if /i not "%EXTRATOR_DB_SILENT%"=="1" pause
exit /b 0

:GARANTIR_TABELAS_BASE
echo [ETAPA] Garantindo tabelas base...
for %%F in (
    "tabelas\001_criar_tabela_coletas.sql"
    "tabelas\002_criar_tabela_fretes.sql"
    "tabelas\003_criar_tabela_manifestos.sql"
    "tabelas\004_criar_tabela_cotacoes.sql"
    "tabelas\005_criar_tabela_localizacao_cargas.sql"
    "tabelas\006_criar_tabela_contas_a_pagar.sql"
    "tabelas\007_criar_tabela_faturas_por_cliente.sql"
    "tabelas\008_criar_tabela_faturas_graphql.sql"
    "tabelas\009_criar_tabela_log_extracoes.sql"
    "tabelas\010_criar_tabela_page_audit.sql"
    "tabelas\011_criar_tabela_dim_usuarios.sql"
    "tabelas\012_criar_tabela_sys_execution_history.sql"
    "tabelas\013_criar_tabela_sys_auditoria_temp.sql"
) do (
    if not exist %%F (
        echo [ERRO] Script nao encontrado: %%~F
        set "SQLCMDPASSWORD="
        pause
        exit /b 1
    )
    echo   [EXEC] %%~F
    sqlcmd -S %DB_SERVER% -d %DB_NAME% %AUTH_CMD% -i "%%~F" -b
    if errorlevel 1 (
        echo [ERRO] Falha em: %%~F
        set "SQLCMDPASSWORD="
        pause
        exit /b 1
    )
)
echo [OK] Tabelas base garantidas.
echo.
exit /b 0

:RECRIAR_BANCO
echo ATENCAO: Esta operacao vai APAGAR todos os dados do banco [%DB_NAME%].
echo.
set "CONFIRMA="
set /p "CONFIRMA=Confirma a recreacao do banco? (RECRIAR/N): "
if /i not "%CONFIRMA%"=="RECRIAR" (
    echo Operacao cancelada.
    set "SQLCMDPASSWORD="
    exit /b 2
)

echo.
echo [EXEC] DROP / CREATE DATABASE [%DB_NAME%]...
sqlcmd -S %DB_SERVER% %AUTH_CMD% -d master -Q "IF DB_ID('%DB_NAME%') IS NOT NULL BEGIN ALTER DATABASE [%DB_NAME%] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [%DB_NAME%]; END; CREATE DATABASE [%DB_NAME%];"
if errorlevel 1 (
    echo [ERRO] Falha ao recriar banco de dados: %DB_NAME%
    set "SQLCMDPASSWORD="
    pause
    exit /b 1
)
echo [OK] Banco [%DB_NAME%] recriado.
echo.

call :GARANTIR_TABELAS_BASE
if errorlevel 1 exit /b 1

REM Seguranca SQL Server (permissoes - apenas no recriar)
if exist "seguranca\024_configurar_permissoes_usuario.sql" (
    echo   [EXEC] seguranca\024_configurar_permissoes_usuario.sql
    sqlcmd -S %DB_SERVER% -d %DB_NAME% %AUTH_CMD% -i "seguranca\024_configurar_permissoes_usuario.sql" -b
    if errorlevel 1 echo   [AVISO] Permissoes retornaram erro - verifique manualmente.
    echo.
)
exit /b 0
