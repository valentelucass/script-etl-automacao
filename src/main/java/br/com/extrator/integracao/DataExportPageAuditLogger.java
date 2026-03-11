package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/DataExportPageAuditLogger.java
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

import com.fasterxml.jackson.databind.JsonNode;

import br.com.extrator.persistencia.entidade.PageAuditEntity;
import br.com.extrator.persistencia.repositorio.PageAuditRepository;

final class DataExportPageAuditLogger {
    private final PageAuditRepository pageAuditRepository;

    DataExportPageAuditLogger(final PageAuditRepository pageAuditRepository) {
        this.pageAuditRepository = pageAuditRepository;
    }

    void registrarPaginaVazia(final String executionUuid,
                              final String runUuid,
                              final int templateId,
                              final int pagina,
                              final int per,
                              final LocalDate janelaInicio,
                              final LocalDate janelaFim,
                              final String reqHash,
                              final String respHash,
                              final String idKey,
                              final int statusCode,
                              final int duracaoMs) {
        final PageAuditEntity audit = criarBase(
            executionUuid,
            runUuid,
            templateId,
            pagina,
            per,
            janelaInicio,
            janelaFim,
            reqHash,
            respHash,
            statusCode,
            duracaoMs
        );
        audit.setTotalItens(0);
        audit.setIdKey(idKey);
        pageAuditRepository.inserir(audit);
    }

    void registrarPaginaComDados(final String executionUuid,
                                 final String runUuid,
                                 final int templateId,
                                 final int pagina,
                                 final int per,
                                 final LocalDate janelaInicio,
                                 final LocalDate janelaFim,
                                 final String reqHash,
                                 final String respHash,
                                 final String idKey,
                                 final int statusCode,
                                 final int duracaoMs,
                                 final JsonNode dadosNode) {
        final PageAuditEntity audit = criarBase(
            executionUuid,
            runUuid,
            templateId,
            pagina,
            per,
            janelaInicio,
            janelaFim,
            reqHash,
            respHash,
            statusCode,
            duracaoMs
        );
        audit.setTotalItens(dadosNode.size());
        audit.setIdKey(idKey);

        final IdRange range = calcularIdRange(dadosNode, idKey);
        audit.setIdMinNum(range.idMinNum());
        audit.setIdMaxNum(range.idMaxNum());
        audit.setIdMinStr(range.idMinStr());
        audit.setIdMaxStr(range.idMaxStr());
        pageAuditRepository.inserir(audit);
    }

    void registrarPayloadInvalido(final String executionUuid,
                                  final String runUuid,
                                  final int templateId,
                                  final int pagina,
                                  final int per,
                                  final LocalDate janelaInicio,
                                  final LocalDate janelaFim,
                                  final String reqHash,
                                  final String respHash,
                                  final int statusCode,
                                  final int duracaoMs) {
        final PageAuditEntity audit = criarBase(
            executionUuid,
            runUuid,
            templateId,
            pagina,
            per,
            janelaInicio,
            janelaFim,
            reqHash,
            respHash,
            statusCode,
            duracaoMs
        );
        audit.setTotalItens(0);
        pageAuditRepository.inserir(audit);
    }

    private PageAuditEntity criarBase(final String executionUuid,
                                      final String runUuid,
                                      final int templateId,
                                      final int pagina,
                                      final int per,
                                      final LocalDate janelaInicio,
                                      final LocalDate janelaFim,
                                      final String reqHash,
                                      final String respHash,
                                      final int statusCode,
                                      final int duracaoMs) {
        final PageAuditEntity audit = new PageAuditEntity();
        audit.setExecutionUuid(executionUuid);
        audit.setRunUuid(runUuid);
        audit.setTemplateId(templateId);
        audit.setPage(pagina);
        audit.setPer(per);
        audit.setJanelaInicio(janelaInicio);
        audit.setJanelaFim(janelaFim);
        audit.setReqHash(reqHash);
        audit.setRespHash(respHash);
        audit.setStatusCode(statusCode);
        audit.setDuracaoMs(duracaoMs);
        return audit;
    }

    private IdRange calcularIdRange(final JsonNode dadosNode, final String idKey) {
        Long minNum = null;
        Long maxNum = null;
        String minStr = null;
        String maxStr = null;

        if (idKey == null) {
            return new IdRange(null, null, null, null);
        }

        for (final JsonNode item : dadosNode) {
            if (!item.has(idKey)) {
                continue;
            }
            final JsonNode valor = item.get(idKey);
            if (valor.isNumber()) {
                final long numero = valor.asLong();
                minNum = (minNum == null || numero < minNum) ? numero : minNum;
                maxNum = (maxNum == null || numero > maxNum) ? numero : maxNum;
                continue;
            }

            final String texto = valor.asText();
            minStr = (minStr == null || texto.compareTo(minStr) < 0) ? texto : minStr;
            maxStr = (maxStr == null || texto.compareTo(maxStr) > 0) ? texto : maxStr;
        }

        return new IdRange(minNum, maxNum, minStr, maxStr);
    }

    private record IdRange(Long idMinNum, Long idMaxNum, String idMinStr, String idMaxStr) {
    }
}
