/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/extracao/ValidadorLimiteExtracao.java
Classe  : ValidadorLimiteExtracao (class)
Pacote  : br.com.extrator.aplicacao.extracao
Modulo  : Use Case - Extracao

Papel   : Valida regras de limitacao de frequencia de extracao por periodo (31d=1h, 180d=12h, <31d=sem limite).

Conecta com:
- ExtractionLogQueryPort (consulta ultima extracao do periodo)

Fluxo geral:
1) validarLimiteExtracao() calcula duracao do periodo e obtém limite.
2) Se < 31 dias: sem limite (DIAS_31 = 31).
3) Se 31-180 dias: limite 1 hora entre execucoes.
4) Se > 180 dias: limite 12 horas entre execucoes.
5) Consulta log da ultima extracao e valida tempo decorrido.
6) Retorna ResultadoValidacao (permitido | bloqueado com motivo e horas restantes).

Estrutura interna:
Inner class ResultadoValidacao:
- permitido: boolean.
- motivo: String descritivo.
- horasRestantes, limiteHoras: para feedback ao usuario.
Metodos principais:
- validarLimiteExtracao(String, LocalDate, LocalDate): valida limite por entidade.
- calcularDuracaoPeriodo(LocalDate, LocalDate): calcula dias (inclusive).
- obterLimiteHoras(long diasPeriodo): retorna limite conforme regra.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.extracao;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import br.com.extrator.aplicacao.contexto.AplicacaoContexto;
import br.com.extrator.aplicacao.portas.ExtractionLogQueryPort;
import br.com.extrator.suporte.console.LoggerConsole;

/**
 * Valida regras de limitacao de execucao por periodo consultado.
 */
public class ValidadorLimiteExtracao {

    private static final LoggerConsole log = LoggerConsole.getLogger(ValidadorLimiteExtracao.class);

    private static final int DIAS_31 = 31;
    private static final int DIAS_6_MESES = 180;

    private final ExtractionLogQueryPort logQueryPort;

    public ValidadorLimiteExtracao() {
        this(AplicacaoContexto.extractionLogQueryPort());
    }

    public ValidadorLimiteExtracao(final ExtractionLogQueryPort logQueryPort) {
        this.logQueryPort = logQueryPort;
    }

    public static class ResultadoValidacao {
        private final boolean permitido;
        private final String motivo;
        private final long horasRestantes;
        private final int limiteHoras;

        private ResultadoValidacao(
            final boolean permitido,
            final String motivo,
            final long horasRestantes,
            final int limiteHoras
        ) {
            this.permitido = permitido;
            this.motivo = motivo;
            this.horasRestantes = horasRestantes;
            this.limiteHoras = limiteHoras;
        }

        public static ResultadoValidacao permitido() {
            return new ResultadoValidacao(true, "Extracao permitida", 0, 0);
        }

        public static ResultadoValidacao bloqueado(final String motivo, final long horasRestantes, final int limiteHoras) {
            return new ResultadoValidacao(false, motivo, horasRestantes, limiteHoras);
        }

        public boolean isPermitido() {
            return permitido;
        }

        public String getMotivo() {
            return motivo;
        }

        public long getHorasRestantes() {
            return horasRestantes;
        }

        public int getLimiteHoras() {
            return limiteHoras;
        }
    }

    public ResultadoValidacao validarLimiteExtracao(
        final String entidade,
        final LocalDate dataInicio,
        final LocalDate dataFim
    ) {
        final long diasPeriodo = calcularDuracaoPeriodo(dataInicio, dataFim);
        final int limiteHoras = obterLimiteHoras(diasPeriodo);

        if (limiteHoras == 0) {
            log.debug("Periodo de {} dias (< 31 dias) - sem limite de tempo", diasPeriodo);
            return ResultadoValidacao.permitido();
        }

        final Optional<LogExtracaoInfo> ultimaExtracao =
            logQueryPort.buscarUltimaExtracaoPorPeriodo(entidade, dataInicio, dataFim);

        if (ultimaExtracao.isEmpty()) {
            log.debug(
                "Nenhuma extracao anterior encontrada para periodo {} a {} - permitindo",
                dataInicio,
                dataFim
            );
            return ResultadoValidacao.permitido();
        }

        final LogExtracaoInfo logExtracao = ultimaExtracao.get();
        final LocalDateTime agora = LocalDateTime.now();
        final LocalDateTime ultimaExtracaoFim = logExtracao.getTimestampFim();

        final Duration tempoDecorrido = Duration.between(ultimaExtracaoFim, agora);
        final long horasDecorridas = tempoDecorrido.toHours();
        final long minutosRestantes = tempoDecorrido.toMinutes() % 60;

        if (horasDecorridas >= limiteHoras) {
            log.info(
                "Limite de {} horas ja foi atingido (decorridas: {}h {}min) - permitindo extracao",
                limiteHoras,
                horasDecorridas,
                minutosRestantes
            );
            return ResultadoValidacao.permitido();
        }

        final long horasRestantes = limiteHoras - horasDecorridas;
        final long minutosRestantesTotal = (limiteHoras * 60L) - tempoDecorrido.toMinutes();
        final String motivo = String.format(
            "Extracao bloqueada: necessario aguardar %d hora(s) desde ultima extracao (decorridas: %dh %dmin, restam: %dh %dmin)",
            limiteHoras,
            horasDecorridas,
            minutosRestantes,
            horasRestantes,
            minutosRestantesTotal % 60
        );

        log.warn("{}", motivo);
        return ResultadoValidacao.bloqueado(motivo, horasRestantes, limiteHoras);
    }

    /**
     * @deprecated Para blocos, use {@link #validarLimiteExtracao(String, LocalDate, LocalDate)}.
     */
    @Deprecated
    public ResultadoValidacao validarLimiteExtracaoPorPeriodoTotal(
        final String entidade,
        final LocalDate dataInicio,
        final LocalDate dataFim,
        final long diasPeriodoTotal
    ) {
        final int limiteHoras = obterLimiteHoras(diasPeriodoTotal);

        if (limiteHoras == 0) {
            log.debug("Periodo total de {} dias (< 31 dias) - sem limite de tempo", diasPeriodoTotal);
            return ResultadoValidacao.permitido();
        }

        final Optional<LogExtracaoInfo> ultimaExtracao =
            logQueryPort.buscarUltimaExtracaoPorPeriodo(entidade, dataInicio, dataFim);

        if (ultimaExtracao.isEmpty()) {
            log.debug(
                "Nenhuma extracao anterior encontrada para periodo {} a {} - permitindo",
                dataInicio,
                dataFim
            );
            return ResultadoValidacao.permitido();
        }

        final LogExtracaoInfo logExtracao = ultimaExtracao.get();
        final LocalDateTime agora = LocalDateTime.now();
        final LocalDateTime ultimaExtracaoFim = logExtracao.getTimestampFim();

        final Duration tempoDecorrido = Duration.between(ultimaExtracaoFim, agora);
        final long horasDecorridas = tempoDecorrido.toHours();
        final long minutosDecorridos = tempoDecorrido.toMinutes() % 60;

        if (horasDecorridas >= limiteHoras) {
            log.info(
                "Limite de {} horas ja foi atingido (decorridas: {}h {}min) - permitindo extracao",
                limiteHoras,
                horasDecorridas,
                minutosDecorridos
            );
            return ResultadoValidacao.permitido();
        }

        final long horasRestantes = limiteHoras - horasDecorridas;
        final long minutosRestantesTotal = (limiteHoras * 60L) - tempoDecorrido.toMinutes();
        final String motivo = String.format(
            "Extracao bloqueada: necessario aguardar %d hora(s) desde ultima extracao (decorridas: %dh %dmin, restam: %dh %dmin)",
            limiteHoras,
            horasDecorridas,
            minutosDecorridos,
            horasRestantes,
            minutosRestantesTotal % 60
        );

        log.warn("{}", motivo);
        return ResultadoValidacao.bloqueado(motivo, horasRestantes, limiteHoras);
    }

    public long calcularDuracaoPeriodo(final LocalDate dataInicio, final LocalDate dataFim) {
        return ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
    }

    public int obterLimiteHoras(final long diasPeriodo) {
        if (diasPeriodo < DIAS_31) {
            return 0;
        }
        if (diasPeriodo <= DIAS_6_MESES) {
            return 1;
        }
        return 12;
    }
}
