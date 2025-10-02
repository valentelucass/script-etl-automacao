# Instruções para Execução do Extrator ESL Cloud

## Visão Geral

Este documento contém instruções detalhadas para configurar e executar o sistema de extração de dados da ESL Cloud para SQL Server. O sistema extrai faturas da API ESL Cloud e as armazena em um banco de dados SQL Server para posterior análise.

## Pré-requisitos

- Java 11 ou superior instalado
- Acesso à API ESL Cloud (token de autenticação)
- Banco de dados SQL Server configurado
- Permissões de escrita no banco de dados

## Configuração

### Método Recomendado: Variáveis de Ambiente

**Para maior segurança, recomendamos configurar o sistema usando variáveis de ambiente.** Este método evita armazenar informações sensíveis em arquivos de código.

Configure as seguintes variáveis de ambiente no seu sistema:

| Variável de Ambiente | Descrição | Exemplo |
|---------------------|-----------|---------|
| `API_BASEURL` | URL base da API ESL Cloud | `https://rodogarcia.eslcloud.com.br` |
| `API_REST_TOKEN` | Token para API REST (Faturas/Ocorrências) | `_Rxcmz7vrmaGvYGy6VeyJx...` |
| `API_GRAPHQL_TOKEN` | Token para API GraphQL (Coletas) | `pGsc57wLxCHxTtbp8juDY3...` |
| `API_GRAPHQL_ENDPOINT` | Endpoint GraphQL | `/graphql` |
| `API_DATAEXPORT_TOKEN` | Token para API Data Export (Manifestos/Localização) | `_Rxcmz7vrmaGvYGy6VeyJx...` |
| `DB_URL` | URL de conexão do banco SQL Server | `jdbc:sqlserver://localhost:1433;databaseName=esl_cloud;encrypt=false;` |
| `DB_USER` | Usuário do banco de dados | `sa` |
| `DB_PASSWORD` | Senha do banco de dados | `SqlDocker!2025` |

#### Como configurar variáveis de ambiente:

**Windows (PowerShell):**
```powershell
$env:API_BASEURL="https://rodogarcia.eslcloud.com.br"
$env:API_REST_TOKEN="seu_token_aqui"
$env:API_GRAPHQL_TOKEN="seu_token_aqui"
$env:API_GRAPHQL_ENDPOINT="/graphql"
$env:API_DATAEXPORT_TOKEN="seu_token_aqui"
$env:DB_URL="jdbc:sqlserver://localhost:1433;databaseName=esl_cloud;encrypt=false;"
$env:DB_USER="sa"
$env:DB_PASSWORD="SqlDocker!2025"
```

**Linux/macOS:**
```bash
export API_BASEURL="https://rodogarcia.eslcloud.com.br"
export API_REST_TOKEN="seu_token_aqui"
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
   cd caminho\para\script-automacao
   ```
3. Execute o comando:
   ```
   java -jar target\extrator-esl-cloud-1.0-SNAPSHOT-jar-with-dependencies.jar
   ```

### Método 2: Especificando uma data de início diferente

Por padrão, o sistema extrai faturas das últimas 24 horas. Se você quiser especificar uma data de início diferente:

```
java -jar target\extrator-esl-cloud-1.0-SNAPSHOT-jar-with-dependencies.jar "2023-01-01T00:00:00"
```

O formato da data deve ser: `yyyy-MM-dd'T'HH:mm:ss`

## Verificando a Execução

Durante a execução, o sistema exibirá informações no terminal sobre o progresso:

1. Banner inicial do sistema
2. Etapa 1/4: Inicialização do banco de dados
3. Etapa 2/4: Inicialização do cliente API
4. Etapa 3/4: Extração de dados da API
5. Etapa 4/4: Salvamento dos dados no banco

Ao final, será exibido um resumo com o total de faturas extraídas e processadas.

## Logs

Os logs detalhados são gerados na pasta `logs/` no mesmo diretório onde o script é executado. Você pode verificar o arquivo `extrator-esl.log` para acompanhar a execução e identificar possíveis erros.

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
   java -Dfile.encoding=UTF-8 -jar target\extrator-esl-cloud-1.0-SNAPSHOT-jar-with-dependencies.jar
   ```

### Logs de Erro

Se o programa falhar, verifique os logs detalhados em:
```
logs/extrator-esl.log
```

Este arquivo contém informações técnicas que podem ajudar a identificar a causa do problema.

## Suporte

Para dúvidas ou problemas, entre em contato com a equipe de desenvolvimento.