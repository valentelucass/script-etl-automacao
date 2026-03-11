package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/DataExportRetryConfigFactory.java
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


import java.time.Duration;
import java.util.List;

import br.com.extrator.integracao.constantes.ConstantesApiDataExport.ConfiguracaoEntidade;

final class DataExportRetryConfigFactory {

    List<ConfiguracaoEntidade> criarTentativasManifestos(final ConfiguracaoEntidade config) {
        final long timeoutBaseSegundos = config.timeout().getSeconds();
        return List.of(
            config,
            criarConfiguracao(config, "50", Math.max(180, timeoutBaseSegundos), config.orderBy()),
            criarConfiguracao(config, "20", Math.max(240, timeoutBaseSegundos), config.orderBy()),
            criarConfiguracao(config, "10", Math.max(300, timeoutBaseSegundos), config.orderBy()),
            criarConfiguracao(config, "5", Math.max(360, timeoutBaseSegundos), ""),
            criarConfiguracao(config, "1", Math.max(480, timeoutBaseSegundos), "sequence_code asc"),
            criarConfiguracao(config, "1", Math.max(600, timeoutBaseSegundos), "")
        );
    }

    List<ConfiguracaoEntidade> criarTentativasContasAPagar(final ConfiguracaoEntidade config) {
        final long timeoutBaseSegundos = config.timeout().getSeconds();
        return List.of(
            config,
            criarConfiguracao(config, "50", Math.max(120, timeoutBaseSegundos), "issue_date desc"),
            criarConfiguracao(config, "25", Math.max(180, timeoutBaseSegundos), "issue_date desc")
        );
    }

    private ConfiguracaoEntidade criarConfiguracao(
        final ConfiguracaoEntidade configBase,
        final String valorPer,
        final long timeoutSegundos,
        final String orderBy
    ) {
        return new ConfiguracaoEntidade(
            configBase.templateId(),
            configBase.campoData(),
            configBase.tabelaApi(),
            valorPer,
            Duration.ofSeconds(timeoutSegundos),
            orderBy,
            configBase.usaSearchNested()
        );
    }
}
