@echo off
REM ==[DOC-FILE]===============================================================
REM Arquivo : database/config.bat
REM Tipo    : Script operacional Windows (.bat)
REM Papel   : Automatiza a rotina "config" para uso operacional.
REM
REM Conecta com:
REM - Conexoes diretas nao detectadas por analise estatica.
REM
REM Fluxo geral:
REM 1) Inicializa variaveis de controle do script.
REM 2) Executa rotina principal com validacoes basicas.
REM 3) Finaliza com codigo de retorno apropriado.
REM
REM Variaveis-chave:
REM - DB_SERVER: controle de estado do script.
REM - DB_NAME: controle de estado do script.
REM - DB_USER: controle de estado do script.
REM - DB_PASSWORD: controle de estado do script.
REM [DOC-FILE-END]===========================================================

REM ============================================
REM Arquivo local de configuracao de banco
REM Nao versionar no Git. Use apenas localmente.
REM ============================================

REM Configuracao SQL Server (preencha localmente)
set DB_SERVER=localhost
set DB_NAME=esl_cloud
set DB_USER=sa
set DB_PASSWORD=SqlDocker!2025
