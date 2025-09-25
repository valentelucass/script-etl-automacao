# Assunto: Esclarecimentos sobre a Política de Rate Limiting das APIs

Para garantir a operação estável das nossas integrações, precisamos, por favor, de esclarecimentos sobre a política de uso das APIs **REST**, **GraphQL** e **DataExport**.  
As nossas tentativas de extração de dados estão a ser consistentemente bloqueadas com o erro **HTTP 429 - quota exceeded**.

---

## Questões para a Equipe ESL Cloud

### 1. Política de Rate Limit
- Qual é a política exata de Rate Limit para cada API (REST, GraphQL e DataExport)?
- Quantas requisições são permitidas por minuto e/ou por hora em cada API?
- Como a quota é aplicada?  
  - O limite é por token de autenticação, por endereço IP ou por conta de cliente?
- Qual é a duração de um bloqueio?
- Quando o status **429** é ativado, por quanto tempo (segundos, minutos) a nossa aplicação deve esperar antes de tentar fazer uma nova requisição?

### 2. Headers de Controle na Resposta
- As respostas das APIs incluem algum cabeçalho (Header) que nos informe o estado atual do nosso limite?  
  *(Exemplos comuns: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `Retry-After`)*

---

Estas informações são cruciais para configurarmos os nossos scripts de forma adequada e evitarmos sobrecarregar as APIs.
