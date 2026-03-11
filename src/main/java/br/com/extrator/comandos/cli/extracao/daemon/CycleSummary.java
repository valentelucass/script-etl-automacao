/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/cli/extracao/daemon/CycleSummary.java
Classe  : CycleSummary (value class)
Pacote  : br.com.extrator.comandos.cli.extracao.daemon
Modulo  : CLI - Daemon
Papel   : Encapsula resultado de um ciclo de execução daemon, com timestamps, status e contadores.
Conecta com: Sem dependencia interna
Fluxo geral:
1) Instantiação com parâmetros (início, fim, duração, status, registros, warns, errors, detalhe, log)
2) Accessors para consulta de estado pós-execução
Estrutura interna:
Atributos: inicio, fim, duracaoSegundos, status, totalRegistros, warns, errors, detalhe, logCiclo
Metodos: getters para todos os campos
[DOC-FILE-END]============================================================== */
package br.com.extrator.comandos.cli.extracao.daemon;

import java.time.LocalDateTime;

public final class CycleSummary {
    private final LocalDateTime inicio;
    private final LocalDateTime fim;
    private final long duracaoSegundos;
    private final String status;
    private final int totalRegistros;
    private final int warns;
    private final int errors;
    private final String detalhe;
    private final String logCiclo;

    public CycleSummary(final LocalDateTime inicio,
                        final LocalDateTime fim,
                        final long duracaoSegundos,
                        final String status,
                        final int totalRegistros,
                        final int warns,
                        final int errors,
                        final String detalhe,
                        final String logCiclo) {
        this.inicio = inicio;
        this.fim = fim;
        this.duracaoSegundos = duracaoSegundos;
        this.status = status;
        this.totalRegistros = totalRegistros;
        this.warns = warns;
        this.errors = errors;
        this.detalhe = detalhe;
        this.logCiclo = logCiclo;
    }

    public LocalDateTime getInicio() {
        return inicio;
    }

    public LocalDateTime getFim() {
        return fim;
    }

    public long getDuracaoSegundos() {
        return duracaoSegundos;
    }

    public String getStatus() {
        return status;
    }

    public int getTotalRegistros() {
        return totalRegistros;
    }

    public int getWarns() {
        return warns;
    }

    public int getErrors() {
        return errors;
    }

    public String getDetalhe() {
        return detalhe;
    }

    public String getLogCiclo() {
        return logCiclo;
    }
}
