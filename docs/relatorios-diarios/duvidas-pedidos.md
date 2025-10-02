**Assunto:** Solicitação de Suporte Técnico - Política de Uso e Parâmetros das APIs (REST, GraphQL, Data Export)

**Prezada equipe de suporte da ESL,**

Estamos finalizando a implementação de um script de automação para extrair dados do ESL Cloud para nosso sistema de BI, utilizando as APIs REST, GraphQL e Data Export.

Recentemente, nossas tentativas de extração de dados passaram a ser consistentemente bloqueadas com o erro **`HTTP 429 - quota exceeded`**. Para configurar nossos scripts de forma adequada e garantir uma operação estável, precisamos de esclarecimentos sobre a política de uso das APIs, além de alguns detalhes técnicos para finalizar a integração.

### **1. Esclarecimentos sobre a Política de Uso e Rate Limiting**

* Qual é a política exata de *Rate Limit* para cada API (REST, GraphQL e DataExport)? Quantas requisições são permitidas por minuto e/ou por hora?
* Como a quota é aplicada? O limite é por token de autenticação, por endereço IP ou por conta de cliente?
* Quando o status **`429`** é ativado, por quanto tempo nossa aplicação deve esperar antes de tentar fazer uma nova requisição?
* As respostas das APIs incluem algum cabeçalho (*Header*) que nos informe o estado atual do nosso limite (ex: `X-RateLimit-Remaining`, `Retry-After`)?

### **2. Dúvidas Específicas sobre Filtros e Parâmetros**

Além das questões de limite, temos as seguintes dúvidas técnicas:

**API GraphQL**
* **Para a entidade `Coletas`:** Estamos recebendo o erro `Field 'Coletas' doesn't exist`, embora a query apareça no esquema. Precisamos da confirmação do **nome exato da query**, da confirmação de que nosso **token tem permissão para executá-la** e de quais **filtros são obrigatórios** no Input.
* **Para a entidade `Fretes`:** A query `freight` executa com sucesso, mas sempre retorna 0 resultados. Qual é a **combinação exata de filtros obrigatórios** no `FreightInput` (além de `serviceAt` e `corporationId`) para listar fretes de um período?

**API REST**
* **Para a entidade `Ocorrências`:** O endpoint `/api/invoice_occurrences` retorna `Erro 500 (Internal Server Error)` consistentemente em buscas com períodos longos. Qual é a **estratégia recomendada para buscar dados históricos** neste endpoint sem causar este erro?

**API Data Export**
* **Para `Manifestos` e `Localização da Carga`:** Estamos recebendo `Erro 404 (Not Found)`. Precisamos da confirmação dos **endpoints corretos** e dos **nomes ou IDs de template exatos** que devemos usar.

---