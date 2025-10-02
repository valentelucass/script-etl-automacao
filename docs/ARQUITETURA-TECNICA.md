## Arquitetura Técnica do Extrator ESL Cloud (Multi-API → SQL Server)

Este documento descreve a arquitetura e o comportamento do sistema em nível operacional e de protocolo, priorizando precisão técnica para consumo por pessoas e por IA.

### 1. Stack e Dependências
- **Linguagem**: Java 11+
- **Build**: Maven
- **HTTP**: java.net.http.HttpClient (blocking)
- **JSON**: Jackson (ObjectMapper)
- **Logs**: SLF4J + Logback
- **Persistência**: JDBC (SQL Server) via serviços internos

### 2. Módulos e Responsabilidades
- `br.com.extrator.api.ClienteApiRest`
  - Endpoints REST (faturas, ocorrências)
  - Paginação incremental (`since`/`start`)
  - Conversão JSON→`EntidadeDinamica`
- `br.com.extrator.api.ClienteApiGraphQL`
  - Query GraphQL (coletas)
  - Variáveis com `dataInicio` ISO 8601
  - Conversão estruturada (objetos/arrays) para Map/List em `EntidadeDinamica`
- `br.com.extrator.api.ClienteApiDataExport`
  - Fluxo assíncrono POST→status polling→download (manifestos, localização da carga)
  - Conversão JSON→`EntidadeDinamica`
- `br.com.extrator.db.ServicoBancoDadosDinamico` e `GeradorTabelasDinamico`
  - Criação de tabelas dinâmicas e MERGE/UPSERT (evita duplicatas)
- `br.com.extrator.modelo.EntidadeDinamica`
  - Envelope genérico de entidade com `tipoEntidade` e `Map<String,Object>` campos
- `br.com.extrator.util.CarregadorConfig`
  - Leitura de `src/main/resources/config.properties`
  - Exposição de chaves: `api.baseurl`, tokens de cada API e endpoint GraphQL

### 3. Configuração (chaves e origem)
Arquivo: `src/main/resources/config.properties`
- `api.baseurl`: URL base comum (ex.: `https://<subdominio>.eslcloud.com.br`)
- `api.rest.token`: Bearer token REST
- `api.graphql.token`: Bearer token GraphQL
- `api.graphql.endpoint`: caminho do endpoint GraphQL (ex.: `/graphql`)
- `api.dataexport.token`: Bearer token Data Export
- `db.url` | `db.user` | `db.password`: credenciais SQL Server

Mapeamento em código:
- `CarregadorConfig.obterUrlBaseApi()` → `api.baseurl`
- `obterTokenApiRest()` → `api.rest.token`
- `obterTokenApiGraphQL()` → `api.graphql.token`
- `obterEndpointGraphQL()` → `api.graphql.endpoint`
- `obterTokenApiDataExport()` → `api.dataexport.token`

Validações implementadas nos clientes: checagem de `urlBase/token` (e `endpoint` no GraphQL) antes de chamadas.

### 4. Contratos HTTP
Headers padrão
- `Authorization: Bearer <token>`
- `Accept: application/json`
- `Content-Type: application/json` (quando POST)

Timeouts
- Conexão: definido no construtor do `HttpClient`
- Requisição: `.timeout(Duration.ofSeconds(30..60))` por request

Retentativas
- REST e GraphQL: não há retentativa transparente; falhas geram log e exceção/retorno vazio
- Data Export: polling com no máx. 30 tentativas, intervalo 10s

### 5. Fluxos Detalhados
5.1 REST (faturas/ocorrências)
- Endpoint base (exemplos):
  - Faturas: `/api/accounting/credit/billings`
  - Ocorrências: `/api/occurrences`
- Paginação:
  - Primeira página: `GET <base><endpoint>?since=<ISO_LOCAL_DATE_TIME>`
  - Seguinte: `GET <base><endpoint>?start=<next_id>`
- Response esperado:
```json
{
  "data": [ { /* registro */ } ],
  "paging": { "next_id": "<cursor>" }
}
```
- Erros: status != 200 lança `RuntimeException` com log de status/body.

5.2 GraphQL (coletas)
- URL: `<api.baseurl><api.graphql.endpoint>`
- Payload (exemplo canônico):
```json
{
  "query": "query BuscarColetas($dataInicio: String!) { coletas(where: { createdAt: { gte: $dataInicio } }) { id numero status /* ... */ } }",
  "variables": { "dataInicio": "2025-01-01T00:00:00" }
}
```
- Processamento:
  - Se `errors` presente no body → log de erro e retorno vazio
  - `data["coletas"]` é iterado; objetos aninhados → Map; arrays → List

5.3 Data Export (manifestos/localização)
- Solicitação (POST):
  - Manifestos: `POST <base>/api/data-export/manifestos`
  - Localização: `POST <base>/api/data-export/localizacao-carga`
  - Body exemplo:
```json
{ "dataInicio": "2025-01-01T00:00:00", "formato": "json", "incluirDetalhes": true }
```
- Resposta 202 retornando `requestId`
- Polling de status: `GET <base>/api/data-export/status/<requestId>`
  - Estados: `processing` | `completed` | `failed`
  - Quando `completed`, retorna `downloadUrl`
- Download: `GET <downloadUrl>` → JSON array ou `{"data": [...]}`

### 6. Modelo de Dados Interno
`EntidadeDinamica`
- Atributos essenciais:
  - `tipoEntidade: String` (ex.: `fatura`, `ocorrencia`, `coletas`, `manifestos`, `localizacao_carga`)
  - `campos: Map<String, Object>`
- Conversão JSON:
  - Strings/Number/Boolean → tipos Java nativos
  - Objetos → `Map<String,Object>`
  - Arrays → `List<Object>` (elementos podem ser Map ou valores primitivos)

### 7. Persistência
- Serviço: `ServicoBancoDadosDinamico` + `GeradorTabelasDinamico`
- Estratégia:
  - Geração de tabela dinâmica por `tipoEntidade`
  - Inserção em lote (batch)
  - MERGE/UPSERT para evitar duplicatas baseadas em chaves definidas por entidade

### 8. Observabilidade e Logs
- Níveis:
  - `info`: início/fim de operações, totais processados
  - `debug`: URLs, tentativas, paginação, status polling
  - `error`: códigos de status, corpo de erro, exceções
- Métricas de duração (ms) adicionadas em REST/GraphQL/Data Export
- Arquivos: `logs/extrator-esl.log` (controlado por `logback.xml`)

### 9. Tratamento de Erros e Interrupções
- `InterruptedException`: restaura flag de interrupção (`Thread.currentThread().interrupt()`) e loga; fluxo aborta
- `IOException`: loga como erro de I/O com contexto (status/body quando disponível)
- GraphQL com `errors`: retorno vazio + log detalhado

### 10. Segurança
- Tokens são lidos de `config.properties` e enviados em Bearer
- Nunca logar tokens
- Banco pode usar `encrypt=true;trustServerCertificate=false;` conforme ambiente

### 11. Performance e Backpressure
- Pausa de 2s entre páginas REST quando `next_id` presente
- Polling Data Export: 10s entre tentativas, até 30 tentativas (máx ~5 min)
- Timeouts curtos em HTTP para evitar threads bloqueadas indefinidamente

### 12. Compatibilidade Temporal
- Datas em ISO 8601 local (`DateTimeFormatter.ISO_LOCAL_DATE_TIME`)
- Recomenda-se alinhar fuso/offset na origem para consistência entre APIs

### 13. Pontos de Extensão
- Novas entidades: adicionar método dedicado no cliente adequado e mapear para `EntidadeDinamica`
- Novos campos: conversores já tratam objetos/arrays arbitrários
- Novas APIs: criar novo cliente sob `br.com.extrator.api` e reutilizar utilitários

### 14. Execução
- Build: `mvn clean package -DskipTests`
- Run (Dashboard): `java -jar target/extrator-esl-cloud-1.0-SNAPSHOT-dashboard.jar`
- Run (Script): `java -jar target/extrator-script.jar [opcional:dataInicio]`

### 15. Exemplos de Entrada/Saída
REST (resumo):
```json
{"data":[{"id":"123","valor":42.1}],"paging":{"next_id":"abc"}}
```

GraphQL (resumo):
```json
{"data":{"coletas":[{"id":"c1","status":"OK","endereco":{"cidade":"SP"}}]}}
```

Data Export (status):
```json
{"status":"completed","downloadUrl":"https://.../file.json"}
```

Data Export (download):
```json
[{"id":"m1","rota":"XYZ"}]
```

### 16. Garantias e Limitações
- Garantias: idempotência via MERGE no banco, conversão JSON resiliente a esquemas flexíveis
- Limitações: sem retentativa automática em REST/GraphQL; autenticação apenas Bearer; sem paralelismo por padrão

### 17. Checklist de Diagnóstico
- Config OK? `api.baseurl`, tokens e endpoint GraphQL presentes
- Rede OK? status != 200 traz body e URL no log
- GraphQL `errors`? revisar query e permissões
- Data Export travado? verificar `MAX_TENTATIVAS_STATUS` e intervalos
- Banco OK? conferir credenciais e conectividade (porta 1433)


