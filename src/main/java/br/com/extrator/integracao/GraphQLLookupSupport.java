package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/GraphQLLookupSupport.java
Classe  :  (class)
Pacote  : br.com.extrator.integracao
Modulo  : Integracao HTTP
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;

import br.com.extrator.integracao.graphql.GraphQLQueries;
import br.com.extrator.dominio.graphql.bancos.BankAccountNodeDTO;
import br.com.extrator.dominio.graphql.faturas.CreditCustomerBillingNodeDTO;

final class GraphQLLookupSupport {
    @FunctionalInterface
    interface TypedQueryExecutor {
        <T> PaginatedGraphQLResponse<T> executar(
            String query,
            String nomeEntidade,
            Map<String, Object> variaveis,
            Class<T> tipoClasse
        );
    }

    private final TypedQueryExecutor queryExecutor;
    private final Logger logger;

    GraphQLLookupSupport(final TypedQueryExecutor queryExecutor, final Logger logger) {
        this.queryExecutor = queryExecutor;
        this.logger = logger;
    }

    Optional<CreditCustomerBillingNodeDTO> enriquecerFatura(final String billingId) {
        if (billingId == null || billingId.isBlank()) {
            logger.warn("Tentativa de enriquecer fatura com ID nulo ou vazio");
            return Optional.empty();
        }

        return buscarPrimeiraEntidade(
            "enriquecer fatura",
            "creditCustomerBilling",
            GraphQLQueries.QUERY_ENRIQUECER_FATURAS,
            Map.of("id", billingId),
            CreditCustomerBillingNodeDTO.class,
            billingId
        );
    }

    Optional<CreditCustomerBillingNodeDTO> enriquecerFaturaPorDocumento(final String document) {
        if (document == null || document.isBlank()) {
            logger.warn("Tentativa de enriquecer fatura com documento nulo ou vazio");
            return Optional.empty();
        }

        final Optional<CreditCustomerBillingNodeDTO> resultado = buscarPrimeiraEntidade(
            "enriquecer fatura por documento",
            "creditCustomerBilling",
            GraphQLQueries.QUERY_ENRIQUECER_FATURAS_POR_DOCUMENTO,
            Map.of("document", document),
            CreditCustomerBillingNodeDTO.class,
            document
        );
        if (resultado.isPresent()) {
            logger.debug("Fatura encontrada via documento: {}", document);
        } else {
            logger.debug("Fatura nao encontrada via documento: {}", document);
        }
        return resultado;
    }

    Optional<CreditCustomerBillingNodeDTO> buscarCapaFaturaPorId(final Long billingId) {
        if (billingId == null) {
            logger.warn("Tentativa de buscar capa de fatura com ID nulo");
            return Optional.empty();
        }

        return buscarPrimeiraEntidade(
            "buscar capa da fatura por ID",
            "creditCustomerBilling",
            GraphQLQueries.QUERY_FATURAS,
            Map.of("params", Map.of("id", String.valueOf(billingId))),
            CreditCustomerBillingNodeDTO.class,
            String.valueOf(billingId)
        );
    }

    Optional<CreditCustomerBillingNodeDTO> buscarDadosCobranca(final Long billingId) {
        if (billingId == null) {
            logger.warn("Tentativa de buscar dados de cobranca com ID nulo");
            return Optional.empty();
        }

        return buscarPrimeiraEntidade(
            "buscar dados de cobranca",
            "creditCustomerBilling",
            GraphQLQueries.QUERY_ENRIQUECER_COBRANCA_NFSE,
            Map.of("id", billingId.toString()),
            CreditCustomerBillingNodeDTO.class,
            String.valueOf(billingId)
        );
    }

    Optional<BankAccountNodeDTO> buscarDetalhesBanco(final Integer bankAccountId) {
        if (bankAccountId == null) {
            logger.warn("Tentativa de buscar detalhes de banco com ID nulo");
            return Optional.empty();
        }

        return buscarPrimeiraEntidade(
            "buscar detalhes de banco",
            "bankAccount",
            GraphQLQueries.QUERY_RESOLVER_CONTA_BANCARIA,
            Map.of("id", bankAccountId),
            BankAccountNodeDTO.class,
            String.valueOf(bankAccountId)
        );
    }

    private <T> Optional<T> buscarPrimeiraEntidade(final String operacao,
                                                   final String nomeEntidade,
                                                   final String query,
                                                   final Map<String, Object> variaveis,
                                                   final Class<T> tipoClasse,
                                                   final String identificador) {
        try {
            final PaginatedGraphQLResponse<T> resposta = queryExecutor.executar(
                query,
                nomeEntidade,
                variaveis,
                tipoClasse
            );

            if (resposta.isErroApi()) {
                logger.warn("Falha ao {} {}: {}", operacao, identificador, resposta.getErroDetalhe());
                return Optional.empty();
            }

            if (resposta.getEntidades() != null && !resposta.getEntidades().isEmpty()) {
                return Optional.of(resposta.getEntidades().get(0));
            }
            return Optional.empty();
        } catch (final Exception e) {
            logger.error("Erro ao {} {}: {}", operacao, identificador, e.getMessage(), e);
            return Optional.empty();
        }
    }
}
