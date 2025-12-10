@echo off
REM ================================================================
REM Script: 06-exportar_csv.bat
REM Finalidade: Exporta todos os dados para CSV/Excel
REM Uso: Execute este script apos a extracao
REM ================================================================

echo ================================================================
echo   EXPORTADOR CSV - TODOS OS DADOS
echo ================================================================
echo.

echo Compilando projeto...
call "%~dp0mvn.bat" -q -DskipTests package
if errorlevel 1 (
    echo ERRO: Compilacao falhou
    echo.
    pause
    exit /b 1
)

REM Verificar se o JAR existe
if not exist "target\extrator.jar" (
    echo ERRO: JAR nao encontrado!
    echo.
    echo Execute primeiro: mvn clean package
    echo   ou: 05-compilar_projeto.bat
    echo.
    pause
    exit /b 1
)

echo Verificando classe ExportadorCSV...
if not exist "src\main\java\br\com\extrator\util\ExportadorCSV.java" (
    echo AVISO: ExportadorCSV.java nao encontrado
    echo O exportador pode nao estar disponivel
    echo.
)

echo.
echo Escolha da tabela (opcional):
echo   - Deixe vazio para exportar todas
echo   - Exemplos: fretes, cotacoes, page_audit
echo.
setlocal EnableExtensions
set "TABELA=%~1"
if not defined TABELA goto ASK_TABLE
goto RUN

:ASK_TABLE
set /p TABELA=Informe a tabela desejada ^(vazio = todas^): 
echo.
if not defined TABELA goto RUN_ALL

:RUN
echo Executando exportador...
echo.
echo ================================================================
echo.
if not defined TABELA goto RUN_ALL
java -cp "target\extrator.jar" br.com.extrator.util.ExportadorCSV "%TABELA%"
goto END

:RUN_ALL
echo Executando exportador...
echo.
echo ================================================================
echo.
java -cp "target\extrator.jar" br.com.extrator.util.ExportadorCSV
goto END

:END
endlocal

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

REM Verificar se a pasta exports existe e tem arquivos
if exist "exports\*.csv" (
    echo Arquivos CSV gerados:
    dir /B exports\*.csv
    echo.
    echo Abrindo pasta exports...
    start "" "exports"
) else (
    echo AVISO: Nenhum arquivo CSV encontrado em exports/
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
echo   - fretes.csv                          (Fretes)
echo   - coletas.csv                         (Coletas)
echo   - manifestos.csv                      (Manifestos)
echo   - cotacoes.csv                        (Cotacoes)
echo   - localizacao_cargas.csv              (Localizacao da Carga)
echo   - page_audit.csv                      (Auditoria de Paginas)
echo.
echo Verifique a pasta 'exports' para os arquivos gerados.
echo.
pause
