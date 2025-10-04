# Extrator de Dados ESL Cloud

Sistema de automação em Java que extrai dados de múltiplas APIs do ESL Cloud e carrega em SQL Server, com dashboard de monitoramento em tempo real.

## 🚀 Início Rápido

### Scripts de Automação (.bat)
Para facilitar o uso, utilize os scripts prontos:

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

### Execução Manual

#### 1. Compilar o Projeto
```bash
mvn clean package
```

#### 2. Executar o Dashboard (Servidor Web)
```bash
# Iniciar backend na porta 7070
java -jar target/extrator-esl-cloud-1.0-SNAPSHOT-dashboard.jar

# Em outro terminal, iniciar frontend na porta 3001
cd dashboard-monitoramento
npm install
$env:PORT=3001; npm start
```

**Acesse:** http://localhost:3001

#### 3. Executar Script de Extração
```bash
# Extrair dados das últimas 24 horas
java -jar target/extrator-script.jar

# Extrair dados de uma data específica
java -jar target/extrator-script.jar "2024-01-01T00:00:00"
```

## 📋 Funcionalidades

- **3 APIs Integradas**: REST, GraphQL e Data Export
- **Dashboard em Tempo Real**: Monitoramento visual das extrações
- **Scripts de Automação**: 5 scripts .bat para facilitar operações
- **Dois Executáveis**: Servidor web e script de linha de comando
- **Prevenção de Duplicatas**: Sistema MERGE para evitar dados duplicados
- **Logs Detalhados**: Acompanhamento completo das operações
- **Métricas Automáticas**: Coleta de performance e estatísticas

## ⚙️ Configuração

Configure as variáveis de ambiente ou edite `src/main/resources/config.properties`:

```bash
# Variáveis de Ambiente (Recomendado)
$env:API_BASEURL="https://sua-empresa.eslcloud.com.br"
$env:API_REST_TOKEN="seu_token_rest"
$env:API_GRAPHQL_TOKEN="seu_token_graphql"
$env:API_DATAEXPORT_TOKEN="seu_token_dataexport"
$env:DB_URL="jdbc:sqlserver://localhost:1433;databaseName=esl_cloud"
$env:DB_USER="sa"
$env:DB_PASSWORD="sua_senha"
```

## 🏗️ Arquitetura

### APIs Suportadas
- **API REST**: Faturas e Ocorrências
- **API GraphQL**: Coletas e Fretes  
- **API Data Export**: Manifestos e Localização da Carga

### Componentes
- **Script de Extração** (`Main.java`): Execução única para extrair dados
- **Servidor Dashboard** (`WebApplication.java`): API REST + interface web
- **Frontend React**: Dashboard de monitoramento visual

## 📚 Documentação

- **[Guia de Instalação](docs/INSTRUCOES.md)**: Configuração detalhada
- **[Arquitetura Técnica](docs/ARQUITETURA-TECNICA.md)**: Detalhes técnicos
- **[Dashboard](dashboard-monitoramento/COMO-INICIAR.md)**: Instruções do frontend

## 🔧 Requisitos Técnicos

- **Java 17** (LTS)
- **Spring Boot 3.5.6**
- **Maven 3.6+**
- **Node.js 16+** (para o dashboard)
- **SQL Server** com JDBC Driver 13.2.0.jre11
- **Acesso às APIs ESL Cloud**

### Dependências Principais
- Spring Boot 3.5.6
- Jackson (com correções de depreciação)
- SQL Server JDBC Driver 13.2.0.jre11
- React 18+ (frontend)

## 📊 Métricas e Monitoramento

O sistema gera métricas automáticas em `metricas/metricas-YYYY-MM-DD.json` com:
- Tempos de execução por API
- Quantidade de registros processados
- Taxa de sucesso/falha (objetivo: 100%)
- Performance geral (registros/segundo)
- Histórico de execuções

## 🆘 Suporte e Troubleshooting

Para problemas ou dúvidas:
1. **Verifique os logs** em `logs/`
2. **Consulte a documentação** em `docs/`
3. **Execute o teste de validação**: `java -jar target/extrator-script.jar --validar`
4. **Use os scripts .bat** para operações padronizadas
5. **Monitore as métricas** para identificar problemas de performance

## 🔄 Atualizações Recentes

- ✅ **Upgrade para Spring Boot 3.5.6** (compatível com Java 17)
- ✅ **Correção de métodos depreciados** (Jackson JsonNode.fields → properties)
- ✅ **Remoção de código não utilizado** (limpeza técnica)
- ✅ **Scripts de automação** (.bat) para facilitar operações
- ✅ **Métricas aprimoradas** com coleta automática de performance