package br.com.extrator.suporte.configuracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/suporte/configuracao/ConfigLoop.java
Classe  : ConfigLoop (class)
Pacote  : br.com.extrator.suporte.configuracao
Modulo  : Suporte - Config
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


public final class ConfigLoop {
    private ConfigLoop() {
    }

    public static boolean isReconciliacaoAtiva() {
        final String valor = ConfigSource.obterConfiguracao("LOOP_RECONCILIACAO_ATIVA", "loop.reconciliacao.ativa");
        return valor == null || valor.isBlank() || Boolean.parseBoolean(valor.trim());
    }

    public static int obterReconciliacaoMaxPorCiclo() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao("LOOP_RECONCILIACAO_MAX_POR_CICLO", "loop.reconciliacao.max_por_ciclo"),
            2,
            value -> value > 0,
            null,
            null,
            null
        );
    }

    public static int obterReconciliacaoDiasRetroativosFalha() {
        return ConfigValueParser.parseInt(
            ConfigSource.obterConfiguracao(
                "LOOP_RECONCILIACAO_DIAS_RETROATIVOS_FALHA",
                "loop.reconciliacao.dias_retroativos_falha"
            ),
            1,
            value -> value >= 0,
            null,
            null,
            null
        );
    }
}
