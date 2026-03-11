# Configuração desta máquina (Windows) para rodar o projeto

Este documento consolida tudo o que é necessário para compilar, executar o extrator Java e, opcionalmente, iniciar os dashboards Python nesta máquina específica.

## Visão Geral
- Projeto principal em Java 17, build com Maven e geração de `target\extrator.jar`.
- Configurações sensíveis via variáveis de ambiente (sem segredos no código).
- Wrapper `mvn.bat` na raiz, ajustado para funcionar com qualquer instalação de Java/Maven.
- Dashboards opcionais em Python (Dash/Plotly) usando as mesmas variáveis de banco.

## Pré‑requisitos
- JDK 17 instalado (x64).
- Maven 3.9+ instalado OU usar o `mvn.bat` do projeto.
- Acesso ao SQL Server (rede/credenciais válidas).
- Variáveis de ambiente configuradas (ver seção abaixo).
- Opcional: Python 3.10+ para dashboards com pacotes do `src/dashboards/requisitos.txt`.

## Java e Maven
- Verificar Java:
  - `java -version` deve reportar JDK 17.
  - `echo %JAVA_HOME%` deve apontar para a pasta da instalação do JDK 17.
- Maven:
  - Se `mvn -version` funcionar, o wrapper usará o Maven do PATH.
  - Alternativamente, defina `MAVEN_HOME` para sua instalação e o wrapper localizará `bin\mvn.cmd`.
- Wrapper portátil (sem caminhos fixos): `c:\Users\lucas.costa\Desktop\PROJETOS\script-automacao\mvn.bat:14`.
  - Detecta `JAVA_HOME` automaticamente a partir do `java` em uso.
  - Procura `mvn.cmd` no PATH; se não, usa `MAVEN_HOME`.
  - Evita recursão quando `mvn` resolve para `mvn.bat` local.

## Variáveis de Ambiente Obrigatórias
Configure estas variáveis no Windows (Usuário ou Sistema) antes de rodar:
- `DB_URL`: JDBC do SQL Server, ex.: `jdbc:sqlserver://servidor:1433;databaseName=SeuBanco`
- `DB_USER`: Usuário do banco
- `DB_PASSWORD`: Senha do banco
- `API_BASEURL`: Base das APIs, ex.: `https://rodogarcia.eslcloud.com.br` (padrão em `config.properties`)
- `API_REST_BASE_URL`: Base da API REST (se aplicável)
- `API_REST_TOKEN`: Token da API REST (se aplicável)
- `API_GRAPHQL_ENDPOINT`: Caminho GraphQL, ex.: `/graphql` (padrão em `config.properties`)
- Opcional (dashboards): `ODBC_DRIVER` ex.: `ODBC Driver 17 for SQL Server`

Referências de leitura de config no código:
- Banco por variáveis de ambiente: `src/main/java/br/com/extrator/util/CarregadorConfig.java:217`, `src/main/java/br/com/extrator/util/GerenciadorConexao.java:12`.
- Config de APIs e limites: `src/main/resources/config.properties:6`, `src/main/resources/config.properties:28`.

## Compilação e Execução (Java)
- Compilar (limpo e rápido, pula testes):
  - `mvn clean package -DskipTests`
- Alternativa pelo script: `.05-compilar_projeto.bat` (usa o wrapper portátil `mvn.bat`).
  - Sem argumentos, `mvn.bat` já executa `clean package -DskipTests` automaticamente.
- Resultado:
  - `target\extrator.jar` (JAR executável com dependências)
- Executar extração completa:
  - `.01-executar_extracao_completa.bat` (com fallback automático para `java -cp ... br.com.extrator.Main` caso o JAR não tenha `Main-Class`).
- Ajuda/diagnósticos úteis:
  - Validação de conexão de banco: `src/main/java/br/com/extrator/util/CarregadorConfig.java:44`
  - Validacao de acesso e conexao: `--validar`
  - Teste rápido de DataExport: `.02-testar_api_especifica.bat dataexport cotacoes` (com fallback de execução caso `java -jar` falhe).

### Observação sobre banco nesta máquina

- Se `DB_URL`, `DB_USER`, `DB_PASSWORD` não estiverem definidos, o projeto tenta ler `db.url`, `db.user`, `db.password` do `src/main/resources/config.properties`.
- Esses campos estão vazios por padrão. Preencha apenas em desenvolvimento local. Não comite segredos.
- Exemplo de valores (ajuste para seu servidor):
  - `db.url=jdbc:sqlserver://localhost:1433;databaseName=SeuBanco`
  - `db.user=seu_usuario`
  - `db.password=sua_senha`

### Como definir variáveis de ambiente via PowerShell (perfil de usuário)

```
[System.Environment]::SetEnvironmentVariable("DB_URL","jdbc:sqlserver://servidor:1433;databaseName=SeuBanco","User")
[System.Environment]::SetEnvironmentVariable("DB_USER","seu_usuario","User")
[System.Environment]::SetEnvironmentVariable("DB_PASSWORD","sua_senha","User")
```

Feche e reabra o terminal após definir as variáveis.
  - Limpeza de tabelas (cuidado): `src/main/java/br/com/extrator/comandos/LimparTabelasComando.java:43`

## Dashboards (Opcional)
- Requisitos (Windows): `src/dashboards/requisitos.txt:1`
- Instalação rápida:
  - `python -m venv .venv`
  - `.venv\Scripts\Activate.ps1`
  - `pip install -r src/dashboards/requisitos.txt`
- Usa as mesmas variáveis `DB_URL`, `DB_USER`, `DB_PASSWORD` e opcional `ODBC_DRIVER` (padrão: 17): `src/dashboards/ambiente.py:12`, `src/dashboards/banco.py:16`.
- Subida local (exemplo): `python src/dashboards/aplicacao.py`.

## VS Code
- `settings.json` já aponta para Java 17 via `${env:JAVA_HOME}`: `c:\Users\lucas.costa\Desktop\PROJETOS\script-automacao\.vscode\settings.json:3`.
- Recomendações de extensões: `c:\Users\lucas.costa\Desktop\PROJETOS\script-automacao\.vscode\extensions.json:2`.

## Problemas Comuns e Soluções
- Erro de limpeza do Maven (arquivo em uso):
  - Sintoma: `Failed to delete ... target\archive-tmp\extrator.jar`.
  - Causa: `java.exe` mantendo handle aberto.
  - Solução: `taskkill /F /IM java.exe` e repetir `mvn clean package -DskipTests`.
- Recursão ao executar `mvn`:
  - Sintoma: “Atingido o nível máximo de recursão local”.
  - Causa: `mvn` resolvendo para `mvn.bat` local e chamando `mvn` novamente.
  - Solução: usar o `mvn.bat` portátil (já corrigido) que resolve `mvn.cmd` real sem caminhos fixos.
- JAVA_HOME ausente ou versão errada:
  - Verificar `java -version` deve ser 17.
  - Ajustar `JAVA_HOME` e `PATH` para o JDK 17.
- Falha de conexão com banco:
  - Verifique `DB_URL`, `DB_USER`, `DB_PASSWORD`.
  - Alternativa local: preencher `db.url`, `db.user`, `db.password` em `config.properties`.
  - Mensagens detalhadas: `src/main/java/br/com/extrator/util/CarregadorConfig.java:72`.

## Checklist Rápido
- [ ] `java -version` → JDK 17
- [ ] `mvn -version` ou configurar `MAVEN_HOME`
- [ ] `DB_URL`, `DB_USER`, `DB_PASSWORD` definidos
- [ ] `API_BASEURL` e tokens/configs conforme necessidade
- [ ] Compilou: `mvn clean package -DskipTests`
- [ ] Executou: `.\u003001-executar_extracao_completa.bat`

---

Atualize este documento sempre que houver novos requisitos de ambiente ou alterações nos scripts/plug-ins.

