# Dashboard de Monitoramento ESL Cloud

Dashboard React para monitoramento em tempo real das extrações de dados ESL Cloud.

## 🚀 Início Rápido

### 1. Iniciar Backend
Na pasta raiz do projeto (`script-automacao`):
```bash
mvn package
java -jar target/extrator-esl-cloud-1.0-SNAPSHOT-dashboard.jar
```

### 2. Iniciar Frontend
Nesta pasta (`dashboard-monitoramento`):
```bash
npm install
$env:PORT=3001; npm start
```

### 3. Acessar
- **Dashboard**: http://localhost:3001
- **API Backend**: http://localhost:7070/api/status

## 📋 Scripts Disponíveis

- `npm start`: Executa em desenvolvimento na porta 3001
- `npm test`: Executa testes
- `npm run build`: Build para produção
- `npm run eject`: Ejeta configuração (irreversível)

## 🔧 Funcionalidades

- **Monitoramento em Tempo Real**: Status das 3 APIs (REST, GraphQL, Data Export)
- **Métricas Visuais**: Gráficos de registros processados por API
- **Indicadores de Status**: Verde/Vermelho para cada API
- **Atualização Automática**: Dados atualizados a cada 30 segundos
- **Interface Responsiva**: Funciona em desktop e mobile

## 🏗️ Estrutura

```
src/
├── componentes/          # Componentes React
├── servicos/            # Chamadas para API
├── estilos/             # CSS e estilos
├── utilitarios/         # Funções auxiliares
└── testes/              # Testes unitários
```

## 🔗 Dependências

- React 18+
- Axios (requisições HTTP)
- Chart.js (gráficos)
- CSS Modules (estilos)

## 📊 APIs Consumidas

O dashboard consome a API do backend Spring Boot:
- `GET /api/status`: Status geral das 3 APIs
- Dados incluem: registros por tabela, timestamps, status de conexão

## 🔧 Configuração

O dashboard se conecta automaticamente ao backend na porta 7070. Certifique-se de que o backend esteja rodando antes de iniciar o frontend.

## 📚 Saiba Mais

Você pode aprender mais na [documentação do Create React App](https://facebook.github.io/create-react-app/docs/getting-started).

Para aprender React, confira a [documentação do React](https://reactjs.org/).

### Code Splitting

This section has moved here: [https://facebook.github.io/create-react-app/docs/code-splitting](https://facebook.github.io/create-react-app/docs/code-splitting)

### Analyzing the Bundle Size

This section has moved here: [https://facebook.github.io/create-react-app/docs/analyzing-the-bundle-size](https://facebook.github.io/create-react-app/docs/analyzing-the-bundle-size)

### Making a Progressive Web App

This section has moved here: [https://facebook.github.io/create-react-app/docs/making-a-progressive-web-app](https://facebook.github.io/create-react-app/docs/making-a-progressive-web-app)

### Advanced Configuration

This section has moved here: [https://facebook.github.io/create-react-app/docs/advanced-configuration](https://facebook.github.io/create-react-app/docs/advanced-configuration)

### Deployment

This section has moved here: [https://facebook.github.io/create-react-app/docs/deployment](https://facebook.github.io/create-react-app/docs/deployment)

### `npm run build` fails to minify

This section has moved here: [https://facebook.github.io/create-react-app/docs/troubleshooting#npm-run-build-fails-to-minify](https://facebook.github.io/create-react-app/docs/troubleshooting#npm-run-build-fails-to-minify)
