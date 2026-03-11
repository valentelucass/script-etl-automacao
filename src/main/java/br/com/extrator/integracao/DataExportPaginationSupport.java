package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/DataExportPaginationSupport.java
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
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

final class DataExportPaginationSupport {
    private final Logger logger;
    private final int maxFalhasConsecutivas;
    private final Duration janelaReaberturaCircuito;
    private final Map<String, Integer> contadorFalhasConsecutivas;
    private final Set<String> templatesComCircuitAberto;
    private final Map<String, Instant> templateCircuitoAbertoDesde;

    DataExportPaginationSupport(final Logger logger,
                                final int maxFalhasConsecutivas,
                                final Duration janelaReaberturaCircuito,
                                final Map<String, Integer> contadorFalhasConsecutivas,
                                final Set<String> templatesComCircuitAberto,
                                final Map<String, Instant> templateCircuitoAbertoDesde) {
        this.logger = logger;
        this.maxFalhasConsecutivas = maxFalhasConsecutivas;
        this.janelaReaberturaCircuito = janelaReaberturaCircuito;
        this.contadorFalhasConsecutivas = contadorFalhasConsecutivas;
        this.templatesComCircuitAberto = templatesComCircuitAberto;
        this.templateCircuitoAbertoDesde = templateCircuitoAbertoDesde;
    }

    void incrementarContadorFalhas(final String chaveTemplate, final String tipoAmigavel) {
        final int falhas = contadorFalhasConsecutivas.getOrDefault(chaveTemplate, 0) + 1;
        contadorFalhasConsecutivas.put(chaveTemplate, falhas);
        if (falhas >= maxFalhasConsecutivas) {
            templatesComCircuitAberto.add(chaveTemplate);
            templateCircuitoAbertoDesde.put(chaveTemplate, Instant.now());
            logger.error(
                "ðŸš¨ CIRCUIT BREAKER ATIVADO - Template {} ({}): {} falhas consecutivas. Template temporariamente desabilitado.",
                chaveTemplate,
                tipoAmigavel,
                falhas
            );
            return;
        }
        logger.warn("âš ï¸ Falha {}/{} para template {} ({})", falhas, maxFalhasConsecutivas, chaveTemplate, tipoAmigavel);
    }

    boolean isCircuitBreakerAtivo(final String chaveTemplate) {
        if (!templatesComCircuitAberto.contains(chaveTemplate)) {
            return false;
        }
        final Instant abertoDesde = templateCircuitoAbertoDesde.get(chaveTemplate);
        if (abertoDesde == null) {
            return true;
        }
        if (Duration.between(abertoDesde, Instant.now()).compareTo(janelaReaberturaCircuito) >= 0) {
            logger.info(
                "Circuit breaker reabilitado para {} apos janela de {} minutos.",
                chaveTemplate,
                janelaReaberturaCircuito.toMinutes()
            );
            resetarEstadoFalhasTemplate(chaveTemplate);
            return false;
        }
        return true;
    }

    void resetarEstadoFalhasTemplate(final String chaveTemplate) {
        contadorFalhasConsecutivas.put(chaveTemplate, 0);
        templatesComCircuitAberto.remove(chaveTemplate);
        templateCircuitoAbertoDesde.remove(chaveTemplate);
    }

    boolean ehErroTimeoutOu422(final Throwable erro) {
        Throwable atual = erro;
        while (atual != null) {
            final String mensagem = atual.getMessage();
            if (mensagem != null) {
                final String msg = mensagem.toLowerCase(Locale.ROOT);
                if (msg.contains("http 422") || msg.contains("tempo limite") || msg.contains("timeout")) {
                    return true;
                }
            }
            atual = atual.getCause();
        }
        return false;
    }

    boolean deveRetentarResultadoIncompleto(final ResultadoExtracao<?> resultado) {
        if (resultado == null || resultado.isCompleto()) {
            return false;
        }
        final String motivo = resultado.getMotivoInterrupcao();
        return ResultadoExtracao.MotivoInterrupcao.ERRO_API.getCodigo().equals(motivo)
            || ResultadoExtracao.MotivoInterrupcao.CIRCUIT_BREAKER.getCodigo().equals(motivo)
            || ResultadoExtracao.MotivoInterrupcao.LACUNA_PAGINACAO_422.getCodigo().equals(motivo);
    }

    <T> ResultadoExtracao<T> selecionarMelhorResultadoParcial(final ResultadoExtracao<T> atual,
                                                              final ResultadoExtracao<T> candidato) {
        if (candidato == null) {
            return atual;
        }
        if (atual == null) {
            return candidato;
        }
        if (candidato.getRegistrosExtraidos() > atual.getRegistrosExtraidos()) {
            return candidato;
        }
        if (candidato.getRegistrosExtraidos() == atual.getRegistrosExtraidos()
            && candidato.getPaginasProcessadas() > atual.getPaginasProcessadas()) {
            return candidato;
        }
        return atual;
    }
}
