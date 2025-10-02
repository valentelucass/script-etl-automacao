# Extrator de Dados ESL Cloud

Sistema de automação em Java que extrai dados de múltiplas APIs do ESL Cloud e carrega em SQL Server, com dashboard de monitoramento em tempo real.

## 🚀 Início Rápido

### 1. Compilar o Projeto
```bash
mvn clean package
```

### 2. Executar o Dashboard (Servidor Web)
```bash
# Iniciar backend na porta 7070
java -jar target/extrator-esl-cloud-1.0-SNAPSHOT-dashboard.jar

# Em outro terminal, iniciar frontend na porta 3001
cd dashboard-monitoramento
npm install
$env:PORT=3001; npm start
```

**Acesse:** http://localhost:3001

### 3. Executar Script de Extração
```bash
# Extrair dados das últimas 24 horas
java -jar target/extrator-script.jar

# Extrair dados de uma data específica
java -jar target/extrator-script.jar "2024-01-01T00:00:00"
```

## 📋 Funcionalidades

- **3 APIs Integradas**: REST, GraphQL e Data Export
- **Dashboard em Tempo Real**: Monitoramento visual das extrações
- **Dois Executáveis**: Servidor web e script de linha de comando
- **Prevenção de Duplicatas**: Sistema MERGE para evitar dados duplicados
- **Logs Detalhados**: Acompanhamento completo das operações

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

## 🔧 Requisitos

- Java 11+
- Maven 3.6+
- Node.js 16+ (para o dashboard)
- SQL Server
- Acesso às APIs ESL Cloud

## 📊 Métricas

O sistema gera métricas automáticas em `metricas/metricas-YYYY-MM-DD.json` com:
- Tempos de execução por API
- Quantidade de registros processados
- Taxa de sucesso/falha
- Performance geral

## 🆘 Suporte

Para problemas ou dúvidas:
1. Verifique os logs em `logs/`
2. Consulte a documentação em `docs/`
3. Execute o teste de validação: `java -jar target/extrator-script.jar --validar`