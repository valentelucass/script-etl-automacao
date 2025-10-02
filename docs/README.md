# Extrator de Dados ESL Cloud

## Descrição do Projeto

Este projeto é um script de automação em Java que extrai dados de múltiplas APIs do sistema ESL Cloud, processa essas informações e as carrega em um banco de dados SQL Server. O objetivo principal é automatizar a atualização de painéis de Power BI, substituindo o processo manual de exportação de planilhas.

### Arquitetura Multi-API

O sistema foi projetado para trabalhar com **3 APIs distintas** do ESL Cloud:

1. **API REST**: Extração de Faturas e Ocorrências
2. **API GraphQL**: Extração de Coletas  
3. **API Data Export**: Extração de Manifestos e Localização da Carga (fluxo POST+GET)

## Funcionalidades

- **Extração Multi-API**: Comunicação com 3 APIs diferentes do ESL Cloud usando HttpClient nativo do Java
- **Processamento Especializado**: Cada API possui seu cliente específico otimizado para seu protocolo
- **Fluxo POST+GET**: Suporte ao fluxo especial da API Data Export (solicitação + processamento + busca)
- **Paginação Automática**: Gerenciamento automático da paginação das APIs REST e GraphQL
- **Processamento de JSON**: Conversão de dados JSON para objetos Java usando Jackson
- **Persistência em Banco**: Armazenamento dos dados em SQL Server usando JDBC
- **Prevenção de Duplicatas**: Uso de comando MERGE para evitar registros duplicados
- **Processamento em Lotes**: Inserção de dados em lotes para melhor performance
- **Logging Completo**: Sistema de logs detalhados usando SLF4J e Logback
- **Interface de Terminal Amigável**: Exibição clara do progresso e resultados no terminal
- **Validação de Acesso**: Teste automático de conectividade com todas as 3 APIs
- **Documentação Detalhada**: Instruções completas para configuração e execução

## Requisitos

- Java 11 ou superior
- Maven 3.6 ou superior
- Acesso à API ESL Cloud (URL e token de autenticação)
- Banco de dados SQL Server
- Acesso à internet para download das dependências

## Instalação

1. Clone este repositório:
   ```
   git clone [URL_DO_REPOSITÓRIO]
   ```

2. Navegue até a pasta do projeto:
   ```
   cd script-automacao
   ```

3. Compile o projeto usando Maven:
   ```
   mvn clean package
   ```

4. O processo de compilação irá gerar um arquivo JAR executável na pasta `target` com o nome `extrator-esl-cloud-1.0-SNAPSHOT-jar-with-dependencies.jar`.

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

Edite o arquivo com suas informações:

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

### Obtendo os Tokens das APIs ESL Cloud

Para obter os tokens de acesso às APIs ESL Cloud:

1. **API REST e Data Export**: 
   - Faça login no sistema ESL Cloud com suas credenciais
   - Acesse o menu de configurações ou perfil
   - Procure por "API" ou "Tokens de acesso"
   - Gere novos tokens para REST e Data Export
   
2. **API GraphQL**:
   - Pode usar o mesmo token da API REST ou gerar um específico
   - Verifique com o administrador do sistema sobre permissões GraphQL

3. Copie os tokens gerados e cole no arquivo `config.properties` nas respectivas propriedades

## Estrutura do Projeto

O projeto está organizado da seguinte forma:

```
script-automacao/
├── pom.xml                                  # Configuração do Maven e dependências
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── br/
│   │   │       └── com/
│   │   │           └── extrator/
│   │   │               ├── Main.java       # Classe principal com arquitetura multi-API
│   │   │               ├── api/
│   │   │               │   ├── ClienteApiRest.java      # Cliente para API REST (Faturas/Ocorrências)
│   │   │               │   ├── ClienteApiGraphQL.java   # Cliente para API GraphQL (Coletas)
│   │   │               │   └── ClienteApiDataExport.java # Cliente para API Data Export (Manifestos/Localização)
│   │   │               ├── db/
│   │   │               │   └── ServicoBancoDados.java   # Comunicação com o banco SQL Server
│   │   │               ├── modelo/
│   │   │               │   └── EntidadeDinamica.java    # Classe modelo para entidades
│   │   │               └── util/
│   │   │                   ├── CarregadorConfig.java   # Carregador de configurações das 3 APIs
│   │   │                   └── TerminalCores.java      # Utilitário para cores no terminal
│   │   └── resources/
│   │       ├── config.properties  # Arquivo de configuração das 3 APIs
│   │       └── logback.xml        # Configuração de logs
│   └── test/                      # Testes unitários (se implementados)
└── target/                        # Arquivos compilados (gerados pelo Maven)
    └── extrator-esl-cloud-1.0-SNAPSHOT-jar-with-dependencies.jar  # JAR executável
```

### Descrição dos Arquivos Principais

- **Main.java**: Orquestra o fluxo de extração das 3 APIs (REST, GraphQL, Data Export) com validação e processamento sequencial.
- **ClienteApiRest.java**: Responsável pela comunicação com a API REST para extração de Faturas e Ocorrências.
- **ClienteApiGraphQL.java**: Gerencia a comunicação com a API GraphQL para extração de Coletas.
- **ClienteApiDataExport.java**: Implementa o fluxo POST+GET da API Data Export para Manifestos e Localização da Carga.
- **ServicoBancoDados.java**: Gerencia a conexão e operações com o SQL Server para todas as entidades.
- **EntidadeDinamica.java**: Classe modelo que mapeia os dados das diferentes APIs.
- **CarregadorConfig.java**: Gerencia o carregamento das configurações das 3 APIs do arquivo properties.
- **TerminalCores.java**: Utilitário para exibição colorida no terminal.
- **logback.xml**: Configuração do sistema de logs.

## Como Executar

Após configurar o arquivo `config.properties`, você pode executar o script de duas maneiras:

### 1. Usando o JAR gerado pelo Maven (Arquitetura Multi-API)

```
java -jar target/extrator-esl-cloud-1.0-SNAPSHOT-jar-with-dependencies.jar
```

### 2. Especificando uma data de início diferente

Por padrão, o script busca dados das últimas 24 horas. Se você quiser especificar uma data de início diferente:

```
java -jar target/extrator-esl-cloud-1.0-SNAPSHOT-jar-with-dependencies.jar "2023-01-01T00:00:00"
```

O formato da data deve ser: `yyyy-MM-dd'T'HH:mm:ss`

### Acompanhando a Execução

Durante a execução, o sistema exibirá informações detalhadas no terminal com o processamento das 3 APIs:

```
╔══════════════════════════════════════════════════════════╗
║                                                          ║
║               EXTRATOR DE DADOS ESL CLOUD                ║
║          Processamento de 3 APIs Especializadas         ║
║                                                          ║
╚══════════════════════════════════════════════════════════╝
  Versão 1.0 - Extração Multi-API para SQL Server

[INICIANDO] Processo de extração de dados da ESL Cloud

[ETAPA 1/9] Verificando configurações das 3 APIs...
✓ Configurações validadas com sucesso!

[ETAPA 2/9] Inicializando conexão com o banco de dados SQL Server...
✓ Conexão com banco de dados estabelecida com sucesso!

[ETAPA 3/9] Inicializando clientes das 3 APIs ESL Cloud...
✓ Clientes das 3 APIs inicializados com sucesso!
  → API REST: Faturas e Ocorrências
  → API GraphQL: Coletas  
  → API Data Export: Manifestos e Localização da Carga

[ETAPA 4/9] Extraindo Faturas da API REST...
✓ Extração de faturas concluída! Total: 15

[ETAPA 5/9] Extraindo Ocorrências da API REST...
✓ Extração de ocorrências concluída! Total: 8

[ETAPA 6/9] Extraindo Coletas da API GraphQL...
✓ Extração de coletas concluída! Total: 42

[ETAPA 7/9] Extraindo Manifestos da API Data Export...
✓ Extração de manifestos concluída! Total: 23

[ETAPA 8/9] Extraindo Localização da Carga da API Data Export...
✓ Extração de localização da carga concluída! Total: 31

[ETAPA 9/9] Gerando resumo final...
✓ Extração concluída com sucesso!
  Total de entidades extraídas: 119
  Total de entidades processadas: 119
  APIs processadas: REST (Faturas + Ocorrências), GraphQL (Coletas), Data Export (Manifestos + Localização)
  Logs detalhados disponíveis em: logs/extrator-esl.log
```

Para instruções mais detalhadas sobre a execução e configuração do sistema, consulte o arquivo `INSTRUCOES.md`.

## Logs

Os logs são gerados na pasta `logs/` no mesmo diretório onde o script é executado. Você pode verificar o arquivo `extrator-esl.log` para acompanhar a execução e identificar possíveis erros.

## Integração com Power BI

Para integrar os dados extraídos com o Power BI, siga estes passos:

1. **Abra o Power BI Desktop**

2. **Conecte-se ao SQL Server**:
   - Clique em "Obter Dados" > "Banco de Dados" > "SQL Server"
   - Insira o nome do servidor e o nome do banco de dados
   - Selecione "Importar" ou "DirectQuery" dependendo do seu caso de uso

3. **Configure a Consulta**:
   - Selecione as tabelas criadas pelo script:
     - **faturas** (dados da API REST)
     - **ocorrencias** (dados da API REST)  
     - **coletas** (dados da API GraphQL)
     - **manifestos** (dados da API Data Export)
     - **localizacao_carga** (dados da API Data Export)
   - Você pode usar o Editor de Consultas para personalizar os dados conforme necessário

4. **Agende Atualizações**:
   - Para automatizar completamente o processo, você pode:
     - Configurar o script Java para executar periodicamente usando o Agendador de Tarefas do Windows ou cron no Linux
     - Configurar o Power BI Service para atualizar o conjunto de dados periodicamente

5. **Crie seus Relatórios**:
   - Use os dados das 5 tabelas para criar visualizações e relatórios no Power BI
   - Combine dados das diferentes APIs para análises mais completas

### Exemplo de Agendamento no Windows

1. Abra o Agendador de Tarefas do Windows
2. Crie uma nova tarefa básica
3. Defina um nome como "Extração ESL Cloud Multi-API"
4. Escolha a frequência (diária, semanal, etc.)
5. Configure a ação para iniciar um programa
6. Navegue até o arquivo .bat que executa o JAR (crie um se necessário)
7. Finalize o assistente

## Solução de Problemas

### Erro de Conexão com as APIs

- Verifique se os tokens das 3 APIs estão corretos e não expiraram
- Confirme se as URLs base estão corretas para cada API
- Verifique sua conexão com a internet
- Para a API Data Export, verifique se o fluxo POST+GET está funcionando corretamente

### Erro de Conexão com o Banco de Dados

- Verifique se as credenciais do banco estão corretas
- Confirme se o servidor SQL está acessível da sua rede
- Verifique se o firewall não está bloqueando a conexão

### Logs de Erro

Consulte os arquivos de log em `logs/extrator-esl.log` para informações detalhadas sobre erros.

## Suporte

Para dúvidas ou problemas, entre em contato com a equipe de desenvolvimento.

---

Desenvolvido por Lucas para automatizar a extração de dados de múltiplas APIs da ESL Cloud e integração com Power BI.