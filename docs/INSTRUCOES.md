# Guia de Instalação e Configuração

## Pré-requisitos

- Java 11+
- Maven 3.6+
- Node.js 16+ (para dashboard)
- SQL Server
- Tokens de acesso ESL Cloud

## Configuração Rápida

### 1. Variáveis de Ambiente (Recomendado)

```powershell
# Windows PowerShell
$env:API_BASEURL="https://sua-empresa.eslcloud.com.br"
$env:API_REST_TOKEN="seu_token_rest"
$env:API_GRAPHQL_TOKEN="seu_token_graphql"
$env:API_GRAPHQL_ENDPOINT="/graphql"
$env:API_DATAEXPORT_TOKEN="seu_token_dataexport"
$env:DB_URL="jdbc:sqlserver://localhost:1433;databaseName=esl_cloud;encrypt=false;"
$env:DB_USER="sa"
$env:DB_PASSWORD="sua_senha"
```

```bash
# Linux/macOS
export API_BASEURL="https://sua-empresa.eslcloud.com.br"
export API_REST_TOKEN="seu_token_rest"
# ... demais variáveis
```

### 2. Arquivo de Configuração (Alternativo)

Edite `src/main/resources/config.properties`:

```properties
api.baseurl=https://sua-empresa.eslcloud.com.br
api.rest.token=seu_token_rest
api.graphql.token=seu_token_graphql
api.graphql.endpoint=/graphql
api.dataexport.token=seu_token_dataexport
db.url=jdbc:sqlserver://localhost:1433;databaseName=esl_cloud;encrypt=false;
db.user=sa
db.password=sua_senha
```

## Compilação e Execução

### 1. Compilar
```bash
mvn clean package
```

### 2. Testar Configuração
```bash
java -jar target/extrator-script.jar --validar
```

### 3. Executar Dashboard
```bash
# Backend
java -jar target/extrator-esl-cloud-1.0-SNAPSHOT-dashboard.jar

# Frontend (novo terminal)
cd dashboard-monitoramento
npm install
$env:PORT=3001; npm start
```

### 4. Executar Script
```bash
# Últimas 24h
java -jar target/extrator-script.jar

# Data específica
java -jar target/extrator-script.jar "2024-01-01T00:00:00"
```

## Solução de Problemas

### Erro de Conexão com Banco
- Verifique se o SQL Server está rodando
- Confirme as credenciais em `config.properties` ou variáveis de ambiente
- Teste a conectividade: `telnet localhost 1433`

### Erro de Token API
- Verifique se os tokens não expiraram
- Confirme as URLs base das APIs
- Execute o teste: `java -jar target/extrator-script.jar --validar`

### Porta em Uso
```bash
# Windows - encontrar processo na porta 7070
netstat -ano | findstr :7070
taskkill /PID [NUMERO_DO_PID] /F
```

## Logs e Monitoramento

- **Logs do Sistema**: `logs/extrator.log`
- **Métricas**: `metricas/metricas-YYYY-MM-DD.json`
- **Dashboard**: http://localhost:3001
- **API Status**: http://localhost:7070/api/status
export API_GRAPHQL_TOKEN="seu_token_aqui"
export API_GRAPHQL_ENDPOINT="/graphql"
export API_DATAEXPORT_TOKEN="seu_token_aqui"
export DB_URL="jdbc:sqlserver://localhost:1433;databaseName=esl_cloud;encrypt=false;"
export DB_USER="sa"
export DB_PASSWORD="SqlDocker!2025"
```

### Método Alternativo: Arquivo config.properties

**Para desenvolvimento local**, você pode usar o arquivo `config.properties` localizado em `src/main/resources/config.properties`. Este arquivo serve como fallback quando as variáveis de ambiente não estão configuradas.

Antes de executar o sistema, é necessário configurar o arquivo:

1. Abra o arquivo `config.properties` em um editor de texto
2. Configure os seguintes parâmetros:

```properties
# Configurações da API ESL Cloud
api.baseurl=https://rodogarcia.eslcloud.com.br

# API REST - Token do Usuário do Sistema (Faturas e Ocorrências)
api.rest.token=_Rxcmz7vrmaGvYGy6VeyJxHNy5qHsToRGsPdqA88zgEs3aAFQ8ycxw

# API GraphQL - Token do Usuário API (Coletas)
api.graphql.token=pGsc57wLxCHxTtbp8juDY3Q5BWpB2uurXB3-zoVkkDysMwf5taHqqw
api.graphql.endpoint=/graphql

# API Data Export - Token do Usuário do Sistema (Manifestos e Localização)
api.dataexport.token=_Rxcmz7vrmaGvYGy6VeyJxHNy5qHsToRGsPdqA88zgEs3aAFQ8ycxw

# Configurações do Banco de Dados SQL Server Local
db.url=jdbc:sqlserver://localhost:1433;databaseName=esl_cloud;encrypt=false;
db.user=sa
db.password=SqlDocker!2025
```

## Execução do Sistema

### Método 1: Usando o JAR compilado

1. Abra o prompt de comando (CMD) ou PowerShell
2. Navegue até a pasta do projeto:
   ```
## Logs e Monitoramento

- **Logs do Sistema**: `logs/extrator.log`
- **Métricas**: `metricas/metricas-YYYY-MM-DD.json`
- **Dashboard**: http://localhost:3001
- **API Status**: http://localhost:7070/api/status

## Estrutura do Projeto

```
script-automacao/
├── README.md                    # Guia principal
├── pom.xml                      # Configuração Maven
├── docs/                        # Documentação técnica
│   ├── README.md               # Visão técnica
│   ├── INSTRUCOES.md          # Este arquivo
│   └── ARQUITETURA-TECNICA.md # Detalhes técnicos
├── dashboard-monitoramento/     # Frontend React
├── src/main/                   # Código fonte Java
├── logs/                       # Logs de execução
└── metricas/                   # Métricas JSON
```

Os logs detalhados são gerados na pasta `logs/` no mesmo diretório onde o script é executado. Você pode verificar o arquivo `extrator.log` para acompanhar a execução e identificar possíveis erros.

## Solução de Problemas

### Configurações Não Personalizadas

Se você receber uma mensagem de erro indicando "Configuração não personalizada":

1. Abra o arquivo `src/main/resources/config.properties`
2. Substitua todos os valores entre colchetes pelos dados reais:
   - `[subdominio]` → Seu subdomínio na ESL Cloud
   - `[seu_bearer_token]` → Token de autenticação da API
   - `[servidor]` → Endereço do servidor SQL Server
   - `[nome_banco]` → Nome do banco de dados
   - `[usuario_banco]` → Usuário do banco de dados
   - `[senha_banco]` → Senha do banco de dados
3. Salve o arquivo e execute novamente o programa

### Erro de Conexão com a API

- Verifique se o token está correto e não expirou
- Confirme se a URL base está correta (formato: `https://seusubdominio.eslcloud.com.br`)
- Verifique sua conexão com a internet
- Confirme se sua conta tem permissões para acessar a API

### Erro de Conexão com o Banco de Dados

- Verifique se as credenciais do banco estão corretas
- Confirme se o servidor SQL está acessível da sua rede
- Verifique se o firewall não está bloqueando a conexão na porta 1433
- Certifique-se que o formato da URL de conexão está correto
- Confirme se o usuário tem permissões suficientes no banco de dados

### Problemas de Codificação de Caracteres

Se você observar caracteres estranhos no terminal:

1. Verifique se está usando um terminal que suporta UTF-8
2. Execute o comando com a opção de codificação explícita:
   ```
   java -Dfile.encoding=UTF-8 -jar target\extrator-script.jar
   ```

### Logs de Erro

Se o programa falhar, verifique os logs detalhados em:
```
logs/extrator.log
```

Este arquivo contém informações técnicas que podem ajudar a identificar a causa do problema.

## Suporte

Para dúvidas ou problemas, consulte:
- **README.md** na raiz do projeto para informações gerais
- **docs/ARQUITETURA-TECNICA.md** para detalhes técnicos
- **Dashboard de Monitoramento** em http://localhost:3001