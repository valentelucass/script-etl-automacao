package br.com.extrator.observabilidade.validacao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/validacao/AuditoriaStatusResolver.java
Classe  :  (class)
Pacote  : br.com.extrator.observabilidade.validacao
Modulo  : Observabilidade - Validacao
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;

import br.com.extrator.observabilidade.enums.StatusValidacao;
import br.com.extrator.observabilidade.modelos.ResultadoValidacaoEntidade;
import br.com.extrator.persistencia.entidade.LogExtracaoEntity;
import br.com.extrator.persistencia.entidade.LogExtracaoEntity.StatusExtracao;

final class AuditoriaStatusResolver {

    void determinarStatusValidacao(final ResultadoValidacaoEntidade resultado,
                                   final LogExtracaoEntity logExtracao,
                                   final Logger logger) {
        if (resultado.getErro() != null) {
            resultado.setStatus(StatusValidacao.ERRO);
            return;
        }

        if (logExtracao == null) {
            logger.error("Nenhum log de extracao encontrado para {}", resultado.getNomeEntidade());
            resultado.setStatus(StatusValidacao.ERRO);
            resultado.setErro("Sem registro de extracao. Verifique se o Runner esta executando.");
            resultado.adicionarObservacao("Nenhum log de extracao encontrado");
            return;
        }

        if (logExtracao.getStatusFinal() == StatusExtracao.ERRO_API) {
            resultado.setStatus(StatusValidacao.ERRO);
            resultado.setErro("Extracao falhou: " + logExtracao.getMensagem());
            resultado.adicionarObservacao("Extracao falhou: " + logExtracao.getMensagem());
            return;
        }

        if (logExtracao.getStatusFinal() == StatusExtracao.INCOMPLETO_LIMITE) {
            resultado.setStatus(StatusValidacao.ALERTA);
            resultado.adicionarObservacao("Extracao interrompida por limite: " + logExtracao.getMensagem());
            logger.info("Validacao ajustada para extracao interrompida de {}", resultado.getNomeEntidade());
            return;
        }

        if (logExtracao.getStatusFinal() == StatusExtracao.INCOMPLETO_DADOS
            || logExtracao.getStatusFinal() == StatusExtracao.INCOMPLETO_DB
            || logExtracao.getStatusFinal() == StatusExtracao.INCOMPLETO) {
            resultado.setStatus(StatusValidacao.ERRO);
            resultado.setErro("Extracao incompleta por divergencia de qualidade/persistencia: "
                + logExtracao.getMensagem());
            resultado.adicionarObservacao("Extracao incompleta por dados/persistencia: "
                + logExtracao.getMensagem());
            return;
        }

        logger.info("Extracao de {} foi completada com sucesso", resultado.getNomeEntidade());

        if (logExtracao.getRegistrosExtraidos() == 0) {
            resultado.setStatus(StatusValidacao.ALERTA);
            resultado.adicionarObservacao("Nenhum registro foi extraido na ultima execucao");
            return;
        }

        if (resultado.getRegistrosComNulos() > 0) {
            final double percentualNulos = logExtracao.getRegistrosExtraidos() > 0
                ? (double) resultado.getRegistrosComNulos() / logExtracao.getRegistrosExtraidos() * 100
                : 0;
            if (percentualNulos > 10.0) {
                resultado.setStatus(StatusValidacao.ALERTA);
                resultado.adicionarObservacao(
                    String.format("%.1f%% dos registros possuem campos criticos nulos", percentualNulos)
                );
                return;
            }
        }

        if (resultado.getUltimaExtracao() != null) {
            final long horasDesdeUltimaExtracao = Duration.between(resultado.getUltimaExtracao(), Instant.now())
                .toHours();
            if (horasDesdeUltimaExtracao > 25) {
                resultado.setStatus(StatusValidacao.ALERTA);
                resultado.adicionarObservacao(String.format("Ultima extracao ha %d horas", horasDesdeUltimaExtracao));
                return;
            }
        }

        resultado.setStatus(StatusValidacao.OK);
        resultado.adicionarObservacao(
            String.format("Extracao completa: %d registros salvos com sucesso", logExtracao.getRegistrosExtraidos())
        );
    }
}
