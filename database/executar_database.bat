@echo off
REM ==[DOC-FILE]===============================================================
REM Arquivo : database/executar_database.bat
REM Tipo    : Script operacional Windows (.bat)
REM Papel   : Automatiza a rotina "executar database" para uso operacional.
REM
REM Conecta com:
REM - call: config.bat
REM
REM Fluxo geral:
REM 1) Carrega parametros de conexao e scripts SQL.
REM 2) Executa criacao/atualizacao de objetos no banco.
REM 3) Exibe resultado para validacao operacional.
REM
REM Variaveis-chave:
REM - AUTH_CMD: controle de estado do script.
REM - SQLCMDPASSWORD: controle de estado do script.
REM - CURRENT_SCRIPT: controle de estado do script.
REM [DOC-FILE-END]===========================================================

chcp 65001 >nul
setlocal

REM ============================================
REM Executar todos os scripts SQL do banco de dados
REM Usa sqlcmd + config.bat (sem precisar do SSMS)
REM Inclui: tabelas, indices, views, views-dimensao, seguranca e validacao
REM ============================================

cd /d "%~dp0"

echo.
echo ============================================
echo   EXECUTAR DATABASE - Scripts SQL
echo ============================================
echo.

REM --- 1. Verificar se config.bat existe ---
if not exist "config.bat" (
    echo [ERRO] Arquivo config.bat nao encontrado!
    echo.
    echo Copie config_exemplo.bat para config.bat e preencha:
    echo   DB_SERVER, DB_NAME e, se usar autenticacao SQL: DB_USER, DB_PASSWORD
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

REM --- 4. Verificar se sqlcmd esta disponivel ---
where sqlcmd >nul 2>nul
if errorlevel 1 (
    echo [ERRO] sqlcmd nao encontrado no PATH.
    echo.
    echo Instale as "SQL Server Command Line Utilities" ou adicione o diretorio
    echo do SQL Server ao PATH. Ex: C:\Program Files\Microsoft SQL Server\Client SDK\ODBC\170\Tools\Binn
    echo.
    pause
    exit /b 1
)

REM --- 5. Definir autenticacao: -E (Windows) ou -U + SQLCMDPASSWORD (SQL) ---
if "%DB_USER%"=="" (
    set "AUTH_CMD=-E"
    set "SQLCMDPASSWORD="
    echo Autenticacao: Windows ^(integrada^)
) else (
    if "%DB_PASSWORD%"=="" (
        echo [ERRO] DB_PASSWORD nao definido no config.bat para autenticacao SQL
        pause
        exit /b 1
    )
    set "AUTH_CMD=-U %DB_USER%"
    set "SQLCMDPASSWORD=%DB_PASSWORD%"
    echo Autenticacao: SQL ^(%DB_USER%^)
)
echo Servidor: %DB_SERVER%  ^|  Banco: %DB_NAME%
echo.

REM --- 6. Executar scripts na ordem (tabelas, indices, views, views-dimensao, seguranca, validacao) ---
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
    "indices\001_criar_indices_performance.sql"
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
    "seguranca\024_configurar_permissoes_usuario.sql"
    "validacao\025_validar_views_dimensao.sql"
    "validacao\026_validar_tipo_destroy_user_id.sql"
    "validacao\027_diagnosticar_campos_null_coletas.sql"
    "validacao\028_validacao_rapida_extracao.sql"
    "validacao\029_verificar_duplicacao_faturas.sql"
) do (
    if not exist %%F (
        echo [AVISO] Ignorado - nao encontrado: %%F
    ) else (
        set "CURRENT_SCRIPT=%%F"
        echo [EXEC] %%F
        sqlcmd -S %DB_SERVER% -d %DB_NAME% %AUTH_CMD% -i "%%~F" -b
        if errorlevel 1 goto :err
        echo.
    )
)
goto :fim

:err
echo.
echo [ERRO] Falha ao executar: %CURRENT_SCRIPT%
echo.
pause
exit /b 1

:fim

set "SQLCMDPASSWORD="

echo.
echo ============================================
echo   CONCLUIDO - Todos os scripts executados
echo ============================================
echo.
pause
