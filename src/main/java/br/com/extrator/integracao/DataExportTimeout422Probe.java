package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/DataExportTimeout422Probe.java
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


import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;

import br.com.extrator.integracao.constantes.ConstantesApiDataExport.ConfiguracaoEntidade;
import br.com.extrator.suporte.mapeamento.MapperUtil;

final class DataExportTimeout422Probe {
    private static final List<String> PERS_SONDA_TIMEOUT_422 = List.of("10", "50", "100");

    enum ResultadoSondaTimeout422 {
        PAGINA_VAZIA,
        PAGINA_COM_DADOS,
        INDETERMINADO
    }

    private final Logger logger;
    private final DataExportRequestBodyFactory requestBodyFactory;
    private final DataExportHttpExecutor httpExecutor;

    DataExportTimeout422Probe(final Logger logger,
                              final DataExportRequestBodyFactory requestBodyFactory,
                              final DataExportHttpExecutor httpExecutor) {
        this.logger = logger;
        this.requestBodyFactory = requestBodyFactory;
        this.httpExecutor = httpExecutor;
    }

    ResultadoSondaTimeout422 sondarPaginaTimeout422(final String url,
                                                    final String nomeTabela,
                                                    final String campoData,
                                                    final Instant dataInicio,
                                                    final Instant dataFim,
                                                    final int pagina,
                                                    final ConfiguracaoEntidade config,
                                                    final Duration timeoutOriginal,
                                                    final int templateId) {
        final Duration timeoutSonda = Duration.ofSeconds(Math.max(30L, Math.min(180L, timeoutOriginal.getSeconds())));
        final String perOriginal = config.valorPer();

        for (final String perSonda : PERS_SONDA_TIMEOUT_422) {
            if (perSonda == null || perSonda.isBlank() || perSonda.equals(perOriginal)) {
                continue;
            }

            final ConfiguracaoEntidade configSonda = new ConfiguracaoEntidade(
                config.templateId(),
                config.campoData(),
                config.tabelaApi(),
                perSonda,
                timeoutSonda,
                config.orderBy(),
                config.usaSearchNested()
            );
            final String corpoSonda = requestBodyFactory.construirCorpoRequisicao(
                nomeTabela,
                campoData,
                dataInicio,
                dataFim,
                pagina,
                configSonda
            );
            final HttpResponse<String> respostaSonda = httpExecutor.executarRequisicaoDataExportJson(
                url,
                corpoSonda,
                timeoutSonda,
                "DataExport-Template-" + templateId + "-Page-" + pagina + "-timeout422-sonda-per-" + perSonda
            );
            if (respostaSonda == null || respostaSonda.statusCode() != 200) {
                continue;
            }

            try {
                final JsonNode raizJson = MapperUtil.sharedJson().readTree(respostaSonda.body());
                final JsonNode dadosNode = raizJson.has("data") ? raizJson.get("data") : raizJson;
                if (dadosNode != null && dadosNode.isArray()) {
                    final int totalItens = dadosNode.size();
                    logger.warn(
                        "Sonda timeout422 pagina {} com per={} retornou status=200 e itens={}.",
                        pagina,
                        perSonda,
                        totalItens
                    );
                    return totalItens == 0
                        ? ResultadoSondaTimeout422.PAGINA_VAZIA
                        : ResultadoSondaTimeout422.PAGINA_COM_DADOS;
                }
            } catch (final Exception e) {
                logger.debug(
                    "Sonda timeout422 nao conseguiu parsear resposta da pagina {} com per={}: {}",
                    pagina,
                    perSonda,
                    e.getMessage()
                );
            }
        }

        return ResultadoSondaTimeout422.INDETERMINADO;
    }
}
