@echo off
REM ============================================================
REM Arquivo : database/config_exemplo.bat
REM Papel   : Modelo de configuracao para conexao SQL Server.
REM
REM INSTRUCOES:
REM   1. Copie este arquivo para config.bat
REM   2. Preencha as variaveis abaixo com seus dados reais
REM   3. NUNCA commite config.bat no Git (ja esta no .gitignore)
REM
REM BANCO DE AUTENTICACAO (SQLite):
REM   NAO configurar aqui. O banco SQLite de autenticacao fica em:
REM   C:\ProgramData\ExtratorESL\security\users.db
REM   Ele e criado e gerenciado exclusivamente pela aplicacao Java.
REM   Inicialize com: java -jar target\extrator.jar --auth-bootstrap
REM ============================================================

REM ============================================================
REM OPCAO 1: Autenticacao Windows (recomendada em producao)
REM Deixe DB_USER e DB_PASSWORD vazios.
REM O sqlcmd usara automaticamente -E (Windows Integrated Auth).
REM ============================================================
set DB_SERVER=SEU_SERVIDOR_SQL
set DB_NAME=esl_cloud
set DB_USER=
set DB_PASSWORD=

REM ============================================================
REM OPCAO 2: Autenticacao SQL Server
REM Preencha DB_USER e DB_PASSWORD com um usuario dedicado.
REM NAO use "sa". Crie um usuario com permissoes minimas necessarias.
REM ============================================================
REM set DB_SERVER=SEU_SERVIDOR_SQL
REM set DB_NAME=esl_cloud
REM set DB_USER=extrator_user
REM set DB_PASSWORD=SuaSenhaSegura
