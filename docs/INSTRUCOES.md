# Guia de Instalação e Configuração

## Pré-requisitos

- **Java 17** (LTS)
- **Maven 3.6+**
- **Node.js 16+** (para dashboard)
- **SQL Server** com JDBC Driver 13.2.0.jre11
- **Tokens de acesso ESL Cloud**

## 🚀 Início Rápido com Scripts de Automação

### Scripts .bat Disponíveis

Para facilitar o uso, utilize os scripts prontos na raiz do projeto:

```bash
# 1. Iniciar dashboard completo (backend + frontend)
01_iniciar_dashboard_completo.bat

# 2. Extrair dados das últimas 24 horas
02_extrair_dados_24h.bat

# 3. Extrair dados de uma data específica
03_extrair_dados_por_data.bat

# 4. Testar APIs das últimas 24 horas
04_testar_api_24h.bat

# 5. Testar APIs de uma data específica
05_testar_api_por_data.bat
```

### Vantagens dos Scripts
- ✅ **Configuração automática** de ambiente
- ✅ **Compilação automática** do projeto
- ✅ **Execução padronizada** das operações
- ✅ **Logs organizados** e fáceis de acompanhar
- ✅ **Redução de erros** manuais

## Configuração Manual (Avançado)

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

### Método Recomendado: Scripts de Automação

**Use os scripts .bat** para operações padronizadas (veja seção anterior).

### Método Manual

#### 1. Compilar
```bash
mvn clean package
```

#### 2. Testar Configuração
```bash
java -jar target/extrator-script.jar --validar
```

#### 3. Executar Dashboard
```bash
# Backend
java -jar target/extrator-esl-cloud-1.0-SNAPSHOT-dashboard.jar

# Frontend (novo terminal)
cd dashboard-monitoramento
npm install
$env:PORT=3001; npm start
```

#### 4. Executar Script
```bash
# Últimas 24h
java -jar target/extrator-script.jar

# Data específica
java -jar target/extrator-script.jar "2024-01-01T00:00:00"
```

## Requisitos Técnicos Atualizados

### Dependências Principais
- **Spring Boot 3.5.6** (suporte estendido até 2026)
- **Jackson** (com correções de depreciação)
- **SQL Server JDBC Driver 13.2.0.jre11**
- **React 18+** (frontend)

### Compatibilidade
- ✅ **Java 17** (LTS) - melhor performance
- ✅ **Spring Boot 3.x** - recursos modernos
- ✅ **Windows/Linux/macOS** - multiplataforma

## Solução de Problemas

### Problemas Comuns e Soluções

#### Erro de Conexão com Banco
- Verifique se o SQL Server está rodando
- Confirme as credenciais em `config.properties` ou variáveis de ambiente
- Teste a conectividade: `telnet localhost 1433`
- **Novo**: Verifique compatibilidade JDBC Driver 13.2.0.jre11 com Java 17

#### Erro de Token API
- Verifique se os tokens não expiraram
- Confirme as URLs base das APIs
- Execute o teste: `java -jar target/extrator-script.jar --validar`
- **Use o script**: `04_testar_api_24h.bat` para validação rápida

#### Problemas de Compilação
- **Java 17**: Certifique-se de usar Java 17 (não 11 ou 8)
- **Spring Boot 3.5.6**: Verifique compatibilidade das dependências
- **Métodos Depreciados**: Já corrigidos (JsonNode.fields → properties)

#### Porta em Uso
```bash
# Windows - encontrar processo na porta 7070
netstat -ano | findstr :7070
taskkill /PID [NUMERO_DO_PID] /F
```

#### Scripts .bat Não Funcionam
- Execute como Administrador se necessário
- Verifique se Java 17 está no PATH
- Confirme se Maven está instalado e configurado
- Use `java -version` para verificar a versão

### Logs e Monitoramento Aprimorado

- **Logs do Sistema**: `logs/extrator.log`
- **Métricas Automáticas**: `metricas/metricas-YYYY-MM-DD.json`
- **Dashboard em Tempo Real**: http://localhost:3001
- **API Status**: http://localhost:7070/api/status
- **Taxa de Sucesso**: Objetivo 100% em todas as APIs
- **Performance**: Monitoramento de registros/segundo

## 🔄 Atualizações Recentes (2025)

### Melhorias Implementadas
- ✅ **Upgrade para Spring Boot 3.5.6** (suporte até 2026)
- ✅ **Migração para Java 17** (LTS, melhor performance)
- ✅ **Correção de métodos depreciados** (Jackson)
- ✅ **Scripts de automação** (.bat) para facilitar uso
- ✅ **Limpeza de código** (remoção de métodos não utilizados)
- ✅ **Métricas aprimoradas** com coleta automática

### Benefícios das Atualizações
- 🚀 **Performance melhorada** com Java 17
- 🔒 **Maior estabilidade** com Spring Boot 3.5.6
- 🛠️ **Facilidade de uso** com scripts automatizados
- 📊 **Monitoramento aprimorado** com métricas detalhadas
- 🧹 **Código mais limpo** sem dependências desnecessárias

---

## Configuração de Variáveis de Ambiente

### Método Recomendado: Variáveis de Ambiente

Configure as seguintes variáveis no seu sistema:

```bash
# Windows (PowerShell)
$env:API_REST_TOKEN="seu_token_aqui"
$env:API_GRAPHQL_TOKEN="seu_token_aqui"
$env:API_GRAPHQL_ENDPOINT="/graphql"
$env:API_DATAEXPORT_TOKEN="seu_token_aqui"
$env:DB_URL="jdbc:sqlserver://localhost:1433;databaseName=esl_cloud;encrypt=false;"
$env:DB_USER="sa"
$env:DB_PASSWORD="SqlDocker!2025"
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

## Estrutura do Projeto Atualizada

```
script-automacao/
├── README.md                    # Guia principal
├── pom.xml                      # Configuração Maven (Spring Boot 3.5.6)
├── docs/                        # Documentação técnica
│   ├── README.md               # Visão técnica
│   ├── INSTRUCOES.md          # Este arquivo
│   └── ARQUITETURA-TECNICA.md # Detalhes técnicos
├── dashboard-monitoramento/     # Frontend React 18+
├── src/main/                   # Código fonte Java 17
├── logs/                       # Logs de execução
├── metricas/                   # Métricas JSON automáticas
├── *.bat                       # Scripts de automação (5 arquivos)
└── target/                     # Artefatos compilados
```

## Suporte e Troubleshooting

### Recursos de Ajuda
- 📖 **Documentação**: Consulte `docs/ARQUITETURA-TECNICA.md`
- 🔧 **Scripts de Teste**: Use `04_testar_api_24h.bat`
- 📊 **Dashboard**: Monitore em tempo real via http://localhost:3001
- 📝 **Logs Detalhados**: Verifique `logs/extrator.log`

### Contato e Suporte
- **Logs de Erro**: Sempre em `logs/extrator.log`
- **Métricas**: Arquivos JSON em `metricas/`
- **Status APIs**: http://localhost:7070/api/status
- **Performance**: Monitoramento contínuo de registros/segundo

---

*Documentação atualizada para Spring Boot 3.5.6 e Java 17 - Janeiro 2025*