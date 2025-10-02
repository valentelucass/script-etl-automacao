@echo off
echo ========================================
echo Executando processo ETL para gerar metricas reais
echo ========================================
echo.

cd /d "c:\Users\lucas\OneDrive\Área de Trabalho\Projetos\ESTAGIO\script-automacao"

echo Compilando projeto...
call mvn compile -q

if %ERRORLEVEL% NEQ 0 (
    echo ERRO: Falha na compilacao
    pause
    exit /b 1
)

echo.
echo Executando processo ETL...
echo.

call mvn exec:java -Dexec.mainClass="br.com.extrator.Main" -q

echo.
echo ========================================
echo Processo ETL concluido!
echo Verificando metricas geradas...
echo ========================================

if exist "metricas\metricas-2025-10-02.json" (
    echo ✓ Arquivo de metricas encontrado
    type "metricas\metricas-2025-10-02.json"
) else (
    echo ✗ Arquivo de metricas nao encontrado
)

echo.
echo Pressione qualquer tecla para continuar...
pause >nul