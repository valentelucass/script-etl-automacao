/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/extracao/LogExtracaoInfo.java
Classe  : LogExtracaoInfo (class)
Pacote  : br.com.extrator.aplicacao.extracao
Modulo  : Use Case - Extracao

Papel   : Value object representando resultado de uma extracao (status, timestamp, volume).

Conecta com:
- ExtractionLogQueryPort (retorna LogExtracaoInfo nas queries)

Fluxo geral:
1) Repositorio queries retornam LogExtracaoInfo com dados do log_extracoes.
2) Use cases consultam status, timestamp fim e count para validacoes.
3) Enum StatusExtracao classifica resultado: COMPLETO, INCOMPLETO_*, ERRO_API.

Estrutura interna:
Campos:
- statusFinal: StatusExtracao enum (COMPLETO, INCOMPLETO_LIMITE, INCOMPLETO_DADOS, INCOMPLETO_DB, ERRO_API).
- timestampFim: LocalDateTime (quando a extracao terminou).
- registrosExtraidos: Integer (quantidade de records inseridos).
Enum StatusExtracao:
- COMPLETO: todos os registros extraidos e validados.
- INCOMPLETO_LIMITE: parou por limite de requests.
- INCOMPLETO_DADOS: faltaram dados na origem.
- INCOMPLETO_DB: erro ao persistir.
- ERRO_API: falha na API.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.extracao;

import java.time.LocalDateTime;

/**
 * Representacao de dominio do log de extracao, sem acoplamento com persistencia.
 */
public final class LogExtracaoInfo {

    public enum StatusExtracao {
        COMPLETO,
        INCOMPLETO,
        INCOMPLETO_LIMITE,
        INCOMPLETO_DADOS,
        INCOMPLETO_DB,
        ERRO_API
    }

    private final StatusExtracao statusFinal;
    private final LocalDateTime timestampFim;
    private final Integer registrosExtraidos;

    public LogExtracaoInfo(
        final StatusExtracao statusFinal,
        final LocalDateTime timestampFim,
        final Integer registrosExtraidos
    ) {
        this.statusFinal = statusFinal;
        this.timestampFim = timestampFim;
        this.registrosExtraidos = registrosExtraidos;
    }

    public StatusExtracao getStatusFinal() {
        return statusFinal;
    }

    public LocalDateTime getTimestampFim() {
        return timestampFim;
    }

    public Integer getRegistrosExtraidos() {
        return registrosExtraidos;
    }
}
