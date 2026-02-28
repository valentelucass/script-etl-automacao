@echo off
REM ==[DOC-FILE]===============================================================
REM Arquivo : database/config_exemplo.bat
REM Tipo    : Script operacional Windows (.bat)
REM Papel   : Automatiza a rotina "config exemplo" para uso operacional.
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
REM Exemplo de configuracao de variaveis de ambiente
REM Copie este arquivo, renomeie para config.bat e configure com suas credenciais
REM NUNCA commite o arquivo config.bat no Git!
REM ============================================

REM Opcao 1: Autenticacao SQL Server
set DB_SERVER=
set DB_NAME=
set DB_USER=
set DB_PASSWORD=

REM Opcao 2: Autenticacao Integrada do Windows (deixe DB_USER e DB_PASSWORD vazios)
REM set DB_SERVER=localhost
REM set DB_NAME=seu_banco_de_dados
