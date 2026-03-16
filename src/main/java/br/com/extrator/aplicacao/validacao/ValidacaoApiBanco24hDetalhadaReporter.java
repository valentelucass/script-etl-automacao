/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/validacao/ValidacaoApiBanco24hDetalhadaReporter.java
Classe  : ValidacaoApiBanco24hDetalhadaReporter (final class)
Pacote  : br.com.extrator.aplicacao.validacao
Modulo  : Use Case - Validacao

Papel   : Reporter de resultados de validacao API vs Banco (log e consolidacao).

Conecta com:
- ValidacaoApiBanco24hDetalhadaComparator (para check de divergencia dinamica tolerada)
- LoggerConsole (suporte.console)

Fluxo geral:
1) reportar(List<ResultadoComparacao>) itera resultados.
2) Classifica cada um: INCONCLUSIVO, OK, OK_DADOS_DINAMICOS, ou FALHA (com log apropriado).
3) Retorna ResumoExecucao com contadores ok/falhas.

Estrutura interna:
Atributos-chave:
- log: LoggerConsole (para logging de cada resultado).
- comparator: ValidacaoApiBanco24hDetalhadaComparator (para verificacao de tolerancia).
Metodos principais:
- reportar(List<ResultadoComparacao>): orquestra logging e consolidacao.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.validacao;

import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.ResumoExecucao;
import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.ResultadoComparacao;

import java.util.List;

import br.com.extrator.suporte.console.LoggerConsole;

final class ValidacaoApiBanco24hDetalhadaReporter {
    private final LoggerConsole log;
    private final ValidacaoApiBanco24hDetalhadaComparator comparator;

    ValidacaoApiBanco24hDetalhadaReporter(
        final LoggerConsole log,
        final ValidacaoApiBanco24hDetalhadaComparator comparator
    ) {
        this.log = log;
        this.comparator = comparator;
    }

    ResumoExecucao reportar(final List<ResultadoComparacao> resultados) {
        int totalOk = 0;
        int totalFalhas = 0;

        for (final ResultadoComparacao resultado : resultados) {
            final boolean divergenciaDinamicaTolerada = comparator.somenteDivergenciaDadosTolerada(resultado);
            final boolean completudeDinamicaTolerada = comparator.completudeDinamicaTolerada(resultado);
            final boolean inconclusivo =
                resultado.detalhe() != null && resultado.detalhe().startsWith("INCONCLUSIVO:");
            final boolean apiIncompleta = !resultado.apiCompleta();

            if (inconclusivo || apiIncompleta) {
                totalFalhas++;
                log.warn(
                    "API_VS_BANCO_DETALHADO | entidade={} | status={} | api_bruto={} | api_unico={} | invalidos={} | banco={} | faltantes={} | excedentes={} | divergencias_dados={}",
                    resultado.entidade(),
                    inconclusivo ? "INCONCLUSIVO" : "API_INCOMPLETA",
                    resultado.apiBruto(),
                    resultado.apiUnico(),
                    resultado.invalidos(),
                    resultado.banco(),
                    resultado.faltantes(),
                    resultado.excedentes(),
                    resultado.divergenciasDados()
                );
            } else if (resultado.ok()) {
                totalOk++;
                log.info(
                    "API_VS_BANCO_DETALHADO | entidade={} | status=OK | api_bruto={} | api_unico={} | invalidos={} | banco={} | faltantes={} | excedentes={} | divergencias_dados={}",
                    resultado.entidade(),
                    resultado.apiBruto(),
                    resultado.apiUnico(),
                    resultado.invalidos(),
                    resultado.banco(),
                    resultado.faltantes(),
                    resultado.excedentes(),
                    resultado.divergenciasDados()
                );
            } else if (completudeDinamicaTolerada) {
                totalOk++;
                log.warn(
                    "API_VS_BANCO_DETALHADO | entidade={} | status=OK_DADOS_DINAMICOS | api_bruto={} | api_unico={} | invalidos={} | banco={} | faltantes={} | excedentes={} | divergencias_dados={}",
                    resultado.entidade(),
                    resultado.apiBruto(),
                    resultado.apiUnico(),
                    resultado.invalidos(),
                    resultado.banco(),
                    resultado.faltantes(),
                    resultado.excedentes(),
                    resultado.divergenciasDados()
                );
            } else if (resultado.falhaCompletude()) {
                totalFalhas++;
                log.error(
                    "API_VS_BANCO_DETALHADO | entidade={} | status=FALHA_COMPLETUDE | api_bruto={} | api_unico={} | invalidos={} | banco={} | faltantes={} | excedentes={} | divergencias_dados={}",
                    resultado.entidade(),
                    resultado.apiBruto(),
                    resultado.apiUnico(),
                    resultado.invalidos(),
                    resultado.banco(),
                    resultado.faltantes(),
                    resultado.excedentes(),
                    resultado.divergenciasDados()
                );
            } else if (divergenciaDinamicaTolerada) {
                totalOk++;
                log.warn(
                    "API_VS_BANCO_DETALHADO | entidade={} | status=OK_DADOS_DINAMICOS | api_bruto={} | api_unico={} | invalidos={} | banco={} | faltantes={} | excedentes={} | divergencias_dados={}",
                    resultado.entidade(),
                    resultado.apiBruto(),
                    resultado.apiUnico(),
                    resultado.invalidos(),
                    resultado.banco(),
                    resultado.faltantes(),
                    resultado.excedentes(),
                    resultado.divergenciasDados()
                );
            } else {
                totalFalhas++;
                log.error(
                    "API_VS_BANCO_DETALHADO | entidade={} | status=FALHA_CONTEUDO | api_bruto={} | api_unico={} | invalidos={} | banco={} | faltantes={} | excedentes={} | divergencias_dados={}",
                    resultado.entidade(),
                    resultado.apiBruto(),
                    resultado.apiUnico(),
                    resultado.invalidos(),
                    resultado.banco(),
                    resultado.faltantes(),
                    resultado.excedentes(),
                    resultado.divergenciasDados()
                );
            }

            if (resultado.detalhe() != null && !resultado.detalhe().isBlank()) {
                log.info(
                    "API_VS_BANCO_DETALHADO | entidade={} | detalhe={}",
                    resultado.entidade(),
                    resultado.detalhe()
                );
            }
        }

        log.console("=".repeat(88));
        log.info("RESUMO_API_VS_BANCO_DETALHADO | ok={} | falhas={}", totalOk, totalFalhas);
        log.console("=".repeat(88));
        return new ResumoExecucao(totalOk, totalFalhas);
    }
}
