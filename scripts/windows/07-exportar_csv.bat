@echo off
setlocal EnableDelayedExpansion
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%.") do set "SCRIPT_DIR=%%~fI"
for %%I in ("%SCRIPT_DIR%\..\..") do set "REPO_ROOT=%%~fI"
set "JAVA_BASE_OPTS=--enable-native-access=ALL-UNNAMED -DETL_BASE_DIR=%REPO_ROOT% -Detl.base.dir=%REPO_ROOT%"
if not defined JAR_PATH set "JAR_PATH=%REPO_ROOT%\target\extrator.jar"
if not defined MVN_CMD set "MVN_CMD=%REPO_ROOT%\mvn.bat"
set "EXPORT_DIR=%REPO_ROOT%\runtime\exports"
REM ==[DOC-FILE]===============================================================
REM Arquivo : 07-exportar_csv.bat
REM Tipo    : Script operacional Windows (.bat)
REM Papel   : Automatiza a rotina "07-exportar csv" para uso operacional.
REM
REM Conecta com:
REM - call: %~dp0mvn.bat
REM - call: :AUTH_CHECK
REM - java -jar: target\extrator.jar
REM
REM Fluxo geral:
REM 1) Inicializa variaveis de controle do script.
REM 2) Executa rotina principal com validacoes basicas.
REM 3) Finaliza com codigo de retorno apropriado.
REM
REM Variaveis-chave:
REM - JAVA_HOME: controle de estado do script.
REM - TABELA: controle de estado do script.
REM [DOC-FILE-END]===========================================================

if /i not "%EXTRATOR_SKIP_CHCP%"=="1" chcp 65001 >nul

REM ================================================================
REM Script: 07-exportar_csv.bat
REM Finalidade: Exporta dados para CSV/Excel com menu interativo
REM Uso: Execute este script apos a extracao
REM ================================================================

echo ================================================================
echo   EXPORTADOR CSV - TODOS OS DADOS
echo ================================================================
echo.
echo Escopo atual de exportacao:
echo   Views/tabelas operacionais, incluindo inventario, sinistros e Raster
echo   Referencias BI: vw_inventario_powerbi, vw_sinistros_powerbi e vw_raster_sm_transit_time
echo.

REM Compilar e gerar JAR antes de executar
if /i "%PROD_MODE%"=="1" (
    echo Modo producao: pulando compilacao.
) else (
    echo Compilando projeto ^(se necessario^)...
    call "%MVN_CMD%" -q -DskipTests package
    if errorlevel 1 (
        echo ERRO: Compilacao falhou
        echo.
        pause
        exit /b 1
    )
)

REM Verificar se o JAR existe
if not exist "%JAR_PATH%" (
    echo ERRO: Arquivo target\extrator.jar nao encontrado!
    if /i "%PROD_MODE%"=="1" (
        echo Modo producao requer JAR precompilado.
    )
    echo.
    pause
    exit /b 1
)

REM Configurar JAVA_HOME automaticamente (Java 17+)
if not defined JAVA_HOME (
    REM Tenta encontrar JDK 17+ no Eclipse Adoptium
    for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Eclipse Adoptium\jdk-17*" 2^>nul') do (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\%%D"
        goto :javahomefound
    )
    REM Se nao encontrar, tenta qualquer JDK 17+ no Adoptium
    for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Eclipse Adoptium\jdk-*" 2^>nul') do (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\%%D"
        goto :javahomefound
    )
)
:javahomefound
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "PATH=%JAVA_HOME%\bin;%PATH%"
    ) else (
        echo AVISO: JAVA_HOME configurado, mas java.exe nao encontrado
    )
)

call :AUTH_CHECK RUN_EXPORTAR_CSV "Exportar dados para CSV"
if errorlevel 1 exit /b 1

echo.
echo ================================================================
echo   MENU DE EXPORTACAO
echo ================================================================
echo.
echo Escolha uma opcao:
echo.
echo   1. Exportar TODAS as tabelas
echo   2. Exportar uma tabela ESPECIFICA
echo   0. Cancelar
echo.
set /p OPCAO="Digite o numero da opcao: "

if "%OPCAO%"=="0" (
    echo Operacao cancelada.
    exit /b 0
)

if "%OPCAO%"=="1" (
    echo.
    echo Exportando TODAS as tabelas...
    echo.
    java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --exportar-csv
    goto :END
)

if "%OPCAO%"=="2" (
    echo.
    echo ================================================================
    echo   TABELAS DISPONIVEIS
    echo ================================================================
    echo.
    echo   1. cotacoes              ^(Cotacoes^)
    echo   2. coletas               ^(Coletas^)
    echo   3. contas_a_pagar        ^(Contas a Pagar^)
    echo   4. faturas_por_cliente   ^(Faturas por Cliente^)
    echo   5. faturas_graphql       ^(Faturas GraphQL^)
    echo   6. fretes                ^(Fretes^)
    echo   7. manifestos            ^(Manifestos^)
    echo   8. localizacao_cargas    ^(Localizacao de Cargas^)
    echo   9. inventario            ^(Inventario / vw_inventario_powerbi^)
    echo  10. sinistros             ^(Sinistros / vw_sinistros_powerbi^)
    echo  11. raster_viagens        ^(Raster viagens^)
    echo  12. raster_viagem_paradas ^(Raster paradas^)
    echo  13. dim_usuarios          ^(Usuarios do Sistema - Dimensao^)
    echo   0. Voltar ao menu anterior
    echo.
    set /p TABELA_NUM="Digite o numero da tabela: "
    
    if "!TABELA_NUM!"=="0" (
        echo Voltando ao menu anterior...
        goto :END
    )
    
    REM Mapear numero para nome da tabela
    set "TABELA="
    if "!TABELA_NUM!"=="1" set "TABELA=cotacoes"
    if "!TABELA_NUM!"=="2" set "TABELA=coletas"
    if "!TABELA_NUM!"=="3" set "TABELA=contas_a_pagar"
    if "!TABELA_NUM!"=="4" set "TABELA=faturas_por_cliente"
    if "!TABELA_NUM!"=="5" set "TABELA=faturas_graphql"
    if "!TABELA_NUM!"=="6" set "TABELA=fretes"
    if "!TABELA_NUM!"=="7" set "TABELA=manifestos"
    if "!TABELA_NUM!"=="8" set "TABELA=localizacao_cargas"
    if "!TABELA_NUM!"=="9" set "TABELA=inventario"
    if "!TABELA_NUM!"=="10" set "TABELA=sinistros"
    if "!TABELA_NUM!"=="11" set "TABELA=raster_viagens"
    if "!TABELA_NUM!"=="12" set "TABELA=raster_viagem_paradas"
    if "!TABELA_NUM!"=="13" set "TABELA=dim_usuarios"
    
    if not defined TABELA (
        echo ERRO: Numero invalido!
        pause
        exit /b 1
    )
    
    echo.
    echo Exportando tabela: !TABELA!
    echo.
    java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --exportar-csv "!TABELA!"
    goto :END
)

REM Opcao invalida
echo ERRO: Opcao invalida!
pause
exit /b 1

:END
if errorlevel 1 (
    echo.
    echo ================================================================
    echo   ERRO AO EXECUTAR EXPORTADOR
    echo ================================================================
    echo.
    echo Possiveis causas:
    echo 1. Classe ExportadorCSV nao existe
    echo 2. Banco de dados nao acessivel
    echo 3. Tabelas vazias
    echo.
    echo Solucao:
    echo 1. Execute a extracao primeiro: 01-executar_extracao_completa.bat
    echo 2. Verifique as configuracoes: 03-validar_configuracoes.bat
    echo.
    pause
    exit /b 1
)

echo.
echo ================================================================
echo   EXPORTACAO CONCLUIDA COM SUCESSO!
echo ================================================================
echo.

REM Verificar se a pasta runtime\exports existe e tem arquivos
if exist "%EXPORT_DIR%\*.csv" (
    echo Arquivos CSV gerados:
    dir /B "%EXPORT_DIR%\*.csv"
    echo.
    echo Abrindo pasta runtime\exports...
    start "" "%EXPORT_DIR%"
) else (
    echo AVISO: Nenhum arquivo CSV encontrado em runtime\exports\
    echo Verifique se ha dados nas tabelas
)

echo.
echo ================================================================
echo   ARQUIVOS DISPONIVEIS
echo ================================================================
echo.
echo Os seguintes arquivos podem ter sido gerados:
echo.
echo   - contas_a_pagar.csv                 (Contas a Pagar)
echo   - faturas_por_cliente.csv            (Faturas por Cliente)
echo   - faturas_graphql.csv                (Faturas GraphQL/Ponte)
echo   - fretes.csv                          (Fretes)
echo   - coletas.csv                         (Coletas)
echo   - manifestos.csv                      (Manifestos)
echo   - cotacoes.csv                        (Cotacoes)
echo   - localizacao_cargas.csv              (Localizacao da Carga)
echo   - inventario.csv                      (Inventario / vw_inventario_powerbi)
echo   - sinistros.csv                       (Sinistros / vw_sinistros_powerbi)
echo   - raster_viagens.csv                  (Raster viagens)
echo   - raster_viagem_paradas.csv           (Raster paradas)
echo   - dim_usuarios.csv                    (Usuarios do Sistema - Dimensao)
echo.
echo Verifique a pasta 'runtime\exports' para os arquivos gerados.
echo.
pause
endlocal
exit /b 0

:AUTH_CHECK
if /i "%EXTRATOR_SKIP_AUTH_CHECK%"=="1" exit /b 0
echo.
echo Autenticacao obrigatoria para executar esta acao.
java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --auth-check %~1 "%~2"
if errorlevel 1 (
    echo Acesso negado.
    echo.
    pause
    exit /b 1
)
exit /b 0
