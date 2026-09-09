/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/api/graphql/GraphQLQueries.java
Classe  : GraphQLQueries (class)
Pacote  : br.com.extrator.integracao.graphql
Modulo  : Cliente de integracao API
Papel   : Implementa responsabilidade de graph qlqueries.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Monta requisicoes para endpoints externos.
2) Trata autenticacao, timeout e parse de resposta.
3) Entrega dados normalizados para os extractors.

Estrutura interna:
Metodos principais:
- GraphQLQueries(): realiza operacao relacionada a "graph qlqueries".
- BuscarColetasExpandidaV2(...2 args): consulta e retorna dados conforme criterio.
- BuscarFretes_Master_V8(...2 args): consulta e retorna dados conforme criterio.
- ExtrairFaturas_Billing_Final(...2 args): realiza operacao relacionada a "extrair faturas billing final".
- ExtracaoNfseDireta(...2 args): realiza operacao relacionada a "extracao nfse direta".
- ExtrairUsuariosSistema(...2 args): realiza operacao relacionada a "extrair usuarios sistema".
- EnriquecerFaturas(...1 args): realiza operacao relacionada a "enriquecer faturas".
- EnriquecerFaturasPorDocumento(...1 args): realiza operacao relacionada a "enriquecer faturas por documento".
- EnriquecerCobranca_Nfse(...1 args): realiza operacao relacionada a "enriquecer cobranca nfse".
- ResolverContaBancaria(...1 args): realiza operacao relacionada a "resolver conta bancaria".
Atributos-chave:
- QUERY_TESTE: campo de estado para "query teste".
[DOC-FILE-END]============================================================== */

package br.com.extrator.integracao.graphql;

/**
 * Classe que centraliza todas as queries GraphQL utilizadas no sistema.
 * Isso evita duplicação e facilita manutenção.
 */
public final class GraphQLQueries {
    
    private GraphQLQueries() {
        // Construtor privado para classe utilitária
    }
    
    /**
     * Query para buscar Coletas (Pick)
     * Tipo GraphQL: Pick
     * Campo de filtro: requestDate (aceita apenas uma data específica)
     */
    public static final String QUERY_COLETAS = """
            query BuscarColetasExpandidaV2($params: PickInput!, $after: String) {
              pick(params: $params, after: $after, first: 100) {
                edges {
                  cursor
                  node {
                    id
                    status
                    requestDate
                    serviceDate
                    sequenceCode
                    requestHour
                    serviceStartHour
                    finishDate
                    serviceEndHour
                    requester
                    corporation {
                      id
                      person { nickname cnpj }
                    }
                    customer { id name cnpj }
                    pickAddress {
                      line1
                      line2
                      number
                      neighborhood
                      postalCode
                      city { name state { code } }
                    }
                    invoicesValue
                    invoicesWeight
                    taxedWeight
                    invoicesVolumes
                    user { id name }
                    comments
                    agentId
                    manifestItemPickId
                    pickItems { id }
                    vehicleTypeId
                    invoicesCubedWeight
                    cancellationReason
                    cancellationUserId
                    cargoClassificationId
                    costCenterId
                    destroyReason
                    destroyUserId
                    lunchBreakEndHour
                    lunchBreakStartHour
                    notificationEmail
                    notificationPhone
                    pickTypeId
                    pickupLocationId
                    statusUpdatedAt
                  }
                }
                pageInfo { hasNextPage endCursor }
              }
            }""";
    
    /**
     * Query para buscar Fretes (Freight)
     * Tipo GraphQL: FreightBase
     * Campo de filtro: serviceAt (aceita intervalo "data_inicio - data_fim")
     */
    public static final String QUERY_FRETES = """
            query BuscarFretes_Master_V8($params: FreightInput!, $after: String) {
              freight(params: $params, after: $after, first: 100) {
                edges {
                  node {
                    id
                    accountingCreditId
                    accountingCreditInstallmentId
                    referenceNumber
                    serviceAt
                    createdAt
                    finishedAt
                    cte { id key number series issuedAt createdAt emissionType }
                    total
                    subtotal
                    invoicesValue
                    invoicesWeight
                    taxedWeight
                    realWeight
                    cubagesCubedWeight
                    totalCubicVolume
                    invoicesTotalVolumes
                    freightInvoices { invoice { number series key value weight } }
                    sender { id name cnpj cpf inscricaoEstadual mainAddress { city { name state { code } } } }
                    receiver { id name cnpj cpf inscricaoEstadual mainAddress { city { name state { code } } } }
                    payer { id name cnpj cpf }
                    modal
                    modalCte
                    status
                    courtesy
                    type
                    serviceDate
                    serviceType
                    deliveryPredictionDate
                    corporationSequenceNumber
                    pickItemId
                    corporation { id nickname cnpj }
                    customerPriceTable { name }
                    freightClassification { name }
                    costCenter { name }
                    originCity { name state { code } }
                    destinationCity { name state { code } }
                    destinationCityId
                    corporationId
                    freightWeightSubtotal
                    globalized
                    globalizedType
                    grisSubtotal
                    adValoremSubtotal
                    insuranceAccountableType
                    insuranceEnabled
                    insuranceId
                    insuredValue
                    itrSubtotal
                    tollSubtotal
                    km
                    nfseNumber
                    nfseSeries
                    otherFees
                    paymentAccountableType
                    paymentType
                    previousDocumentType
                    priceTableAccountableType
                    productsValue
                    redispatchSubtotal
                    secCatSubtotal
                    suframaSubtotal
                    tdeSubtotal
                    fiscalDetail { cstType cfopCode calculationBasis taxRate taxValue pisRate pisValue cofinsRate cofinsValue hasDifal difalTaxValueOrigin difalTaxValueDestination }
                    trtSubtotal
                  }
                }
                pageInfo { hasNextPage endCursor }
              }
            }""";
    
    /**
     * Query para buscar NFSe diretamente
     * Tipo GraphQL: Nfse
     * Campo de filtro: issuedAt (aceita intervalo "data_inicio - data_fim")
     */
    public static final String QUERY_NFSE = """
            query ExtracaoNfseDireta($params: NfseInput!, $after: String) {
              nfse(params: $params, first: 100, after: $after) {
                edges {
                  node {
                    id
                    freightId
                    freight { id }
                    number
                    status
                    rpsSeries
                    issuedAt
                    cancelationReason
                    pdfServiceUrl
                    xmlDocument
                    corporationId
                    nfseService { id description }
                  }
                }
                pageInfo { hasNextPage endCursor }
              }
            }""";
    
    /**
     * Query de teste de conectividade
     */
    public static final String QUERY_TESTE = "{ __schema { queryType { name } } }";
    
    /**
     * Query de introspection para descobrir campos de PickInput.
     * Usada para decidir dinamicamente filtros válidos (requestDate/serviceDate).
     */
    public static final String INTROSPECTION_PICK_INPUT = """
            query CamposPickInput {
              __type(name: "PickInput") {
                inputFields { name }
              }
            }""";
    
    /**
     * Query de introspection para descobrir o tipo de destroyUserId e cancellationUserId em Pick
     * IMPORTANTE: Usar para validar se são do tipo Individual antes de fazer JOIN
     */
    public static final String INTROSPECTION_PICK_FIELDS = """
            query IntrospectPickFields {
              __type(name: "Pick") {
                fields {
                  name
                  type {
                    name
                    kind
                    ofType {
                      name
                      kind
                    }
                  }
                }
              }
            }""";
    
    /**
     * Query para buscar Usuários do Sistema (Individual)
     * Tipo GraphQL: Individual
     * Filtros em IndividualInput: enabled: true e updatedAt: YYYY-MM-DD - YYYY-MM-DD.
     * updatedAt pertence ao input e não deve ser selecionado no node Individual.
     * Paginação: cursor-based (first: 1000 solicitado; a API pode retornar páginas menores).
     *
     * ATENÇÃO: Esta query busca usuários do tipo Individual.
     * Validar se destroyUserId e cancellationUserId em Pick são do mesmo tipo Individual
     * antes de fazer JOIN na view de coletas. Se forem de outro tipo (ex: User, Driver),
     * o cruzamento retornará dados incorretos.
     */
    public static final String QUERY_USUARIOS_SISTEMA = """
            query ExtrairUsuariosSistema($params: IndividualInput!, $after: String) {
              individual(params: $params, first: 1000, after: $after) {
                pageInfo {
                  hasNextPage
                  endCursor
                }
                edges {
                  node {
                    id
                    name
                  }
                }
              }
            }""";
    
    /**
     * Query para resolver detalhes de conta bancária via ID.
     * Usada para buscar dados do banco de forma otimizada (cache).
     * 
     * Parâmetro: id (ID da conta bancária - ticketAccountId)
     * 
     * Campos extraídos:
     * - bankName: Nome do banco
     * - portfolioVariation: Carteira/Descrição (pode vir vazio se não cadastrado)
     * - customInstruction: Instrução customizada (pode vir vazio se não cadastrado)
     */
    public static final String QUERY_RESOLVER_CONTA_BANCARIA = """
            query ResolverContaBancaria($id: Int!) {
              bankAccount(params: { id: $id }) {
                edges {
                  node {
                    id
                    bankName
                    portfolioVariation
                    customInstruction
                  }
                }
              }
            }""";
}
