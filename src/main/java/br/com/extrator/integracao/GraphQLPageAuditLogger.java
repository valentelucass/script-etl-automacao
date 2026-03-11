package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/GraphQLPageAuditLogger.java
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


import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.integracao.constantes.ConstantesApiGraphQL;
import br.com.extrator.persistencia.entidade.PageAuditEntity;
import br.com.extrator.persistencia.repositorio.PageAuditRepository;
import br.com.extrator.dominio.graphql.faturas.CreditCustomerBillingNodeDTO;

final class GraphQLPageAuditLogger {
    private static final Logger logger = LoggerFactory.getLogger(GraphQLPageAuditLogger.class);
    private final PageAuditRepository pageAuditRepository;

    GraphQLPageAuditLogger(final PageAuditRepository pageAuditRepository) {
        this.pageAuditRepository = pageAuditRepository;
    }

    <T> void registrarPagina(final String executionUuid,
                             final String runUuid,
                             final int pagina,
                             final int per,
                             final LocalDate janelaInicio,
                             final LocalDate janelaFim,
                             final PaginatedGraphQLResponse<T> resposta,
                             final Class<T> tipoClasse) {
        final PageAuditEntity audit = new PageAuditEntity();
        audit.setExecutionUuid(executionUuid);
        audit.setRunUuid(runUuid);
        audit.setTemplateId(ConstantesApiGraphQL.TEMPLATE_ID_AUDIT);
        audit.setPage(pagina);
        audit.setPer(per);
        audit.setJanelaInicio(janelaInicio);
        audit.setJanelaFim(janelaFim);
        audit.setReqHash(resposta.getReqHash() != null ? resposta.getReqHash() : "");
        audit.setRespHash(resposta.getRespHash() != null ? resposta.getRespHash() : "");
        audit.setTotalItens(resposta.getTotalItens());
        audit.setIdKey("id");

        final MinMaxNumerico minMax = extrairMinMaxNumerico(resposta, tipoClasse);
        audit.setIdMinNum(minMax.minimo());
        audit.setIdMaxNum(minMax.maximo());
        audit.setStatusCode(resposta.getStatusCode());
        audit.setDuracaoMs(resposta.getDuracaoMs());
        pageAuditRepository.inserir(audit);
    }

    private <T> MinMaxNumerico extrairMinMaxNumerico(final PaginatedGraphQLResponse<T> resposta, final Class<T> tipoClasse) {
        if (tipoClasse == null || !CreditCustomerBillingNodeDTO.class.isAssignableFrom(tipoClasse)) {
            return MinMaxNumerico.vazio();
        }

        Long minNum = null;
        Long maxNum = null;
        for (final T item : resposta.getEntidades()) {
            try {
                final Long idVal = ((CreditCustomerBillingNodeDTO) item).getId();
                if (idVal != null) {
                    minNum = (minNum == null || idVal < minNum) ? idVal : minNum;
                    maxNum = (maxNum == null || idVal > maxNum) ? idVal : maxNum;
                }
            } catch (final RuntimeException ignored) {
                logger.debug("Falha ao extrair id de item de fatura para page audit: {}", ignored.getMessage());
            }
        }
        return new MinMaxNumerico(minNum, maxNum);
    }

    private record MinMaxNumerico(Long minimo, Long maximo) {
        private static MinMaxNumerico vazio() {
            return new MinMaxNumerico(null, null);
        }
    }
}
