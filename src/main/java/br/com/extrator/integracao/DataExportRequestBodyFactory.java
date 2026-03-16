package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/DataExportRequestBodyFactory.java
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


import java.time.Instant;

import org.slf4j.Logger;

import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.extrator.integracao.constantes.ConstantesApiDataExport.ConfiguracaoEntidade;
import br.com.extrator.suporte.mapeamento.MapperUtil;

final class DataExportRequestBodyFactory {
    private final Logger logger;
    private final DataExportTimeWindowSupport timeWindowSupport;

    DataExportRequestBodyFactory(final Logger logger, final DataExportTimeWindowSupport timeWindowSupport) {
        this.logger = logger;
        this.timeWindowSupport = timeWindowSupport;
    }

    String construirCorpoRequisicao(final String nomeTabela,
                                    final String campoData,
                                    final Instant dataInicio,
                                    final Instant dataFim,
                                    final int pagina,
                                    final ConfiguracaoEntidade config) {
        try {
            final ObjectNode corpo = MapperUtil.sharedJson().createObjectNode();
            final ObjectNode search = MapperUtil.sharedJson().createObjectNode();
            final ObjectNode table = MapperUtil.sharedJson().createObjectNode();
            final String range = formatarRange(dataInicio, dataFim);

            if (config.usaSearchNested()) {
                final ObjectNode searchNested = MapperUtil.sharedJson().createObjectNode();
                searchNested.put(campoData, range);
                searchNested.put("created_at", "");
                search.set(nomeTabela, searchNested);
            } else {
                table.put(campoData, range);
                search.set(nomeTabela, table);
            }

            corpo.set("search", search);
            corpo.put("page", String.valueOf(pagina));
            corpo.put("per", config.valorPer());
            if (config.orderBy() != null && !config.orderBy().isBlank()) {
                corpo.put("order_by", config.orderBy());
            }

            final String corpoJson = MapperUtil.toJson(corpo);
            logger.debug("Corpo JSON construido: {}", corpoJson);
            return corpoJson;
        } catch (final Exception e) {
            logger.error("Erro ao construir corpo da requisicao: {}", e.getMessage(), e);
            throw new IllegalStateException(
                "Falha ao construir corpo da requisicao DataExport para tabela " + nomeTabela
                    + ", campo " + campoData + ", pagina " + pagina,
                e
            );
        }
    }

    String construirCorpoRequisicaoCsv(final String nomeTabela,
                                       final String campoData,
                                       final Instant dataInicio,
                                       final Instant dataFim) {
        try {
            final ObjectNode corpo = MapperUtil.sharedJson().createObjectNode();
            final ObjectNode search = MapperUtil.sharedJson().createObjectNode();
            final ObjectNode table = MapperUtil.sharedJson().createObjectNode();

            table.put(campoData, formatarRange(dataInicio, dataFim));
            search.set(nomeTabela, table);

            corpo.set("search", search);
            corpo.put("page", "1");
            corpo.put("per", "10000");

            final String corpoJson = MapperUtil.toJson(corpo);
            logger.debug("Corpo JSON para contagem CSV construido: {}", corpoJson);
            return corpoJson;
        } catch (final Exception e) {
            logger.error("Erro ao construir corpo da requisicao para contagem CSV: {}", e.getMessage(), e);
            throw new IllegalStateException(
                "Falha ao construir corpo da requisicao de contagem CSV para tabela "
                    + nomeTabela + ", campo " + campoData,
                e
            );
        }
    }

    private String formatarRange(final Instant dataInicio, final Instant dataFim) {
        return timeWindowSupport.formatarRange(dataInicio, dataFim);
    }
}
