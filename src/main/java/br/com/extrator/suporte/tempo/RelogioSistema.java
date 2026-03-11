/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/util/tempo/RelogioSistema.java
Classe  : RelogioSistema
Pacote  : br.com.extrator.suporte.tempo
Modulo  : Utilitarios de tempo
Papel   : Centraliza obtencao de data/hora com Clock configuravel e padrao America/Sao_Paulo.

Conecta com:
- Main
- ValidarApiVsBanco24hComando
- ValidarApiVsBanco24hDetalhadoComando
- ExecutarFluxoCompletoComando
- TestarApiComando
- ClienteApiDataExport
- AuditorEstruturaApi

Fluxo geral:
1) Resolve timezone via APP_CLOCK_ZONE (fallback America/Sao_Paulo).
2) Aplica instante fixo opcional via APP_CLOCK_FIXED_INSTANT.
3) Expoe Clock e metodos de tempo para uso uniforme no sistema.

Estrutura interna:
Metodos principais:
- clock(...0 args): retorna Clock global configurado.
- hoje(...0 args): data atual no clock configurado.
- agora(...0 args): data/hora local no clock configurado.
- agoraInstant(...0 args): instante atual no clock configurado.
Atributos-chave:
- CLOCK: instancia unica de Clock para toda a aplicacao.
- ENV_CLOCK_ZONE: nome da variavel de timezone.
- ENV_CLOCK_FIXED_INSTANT: nome da variavel de instante fixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.suporte.tempo;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provedor central de relogio para reduzir dependencia de timezone do host.
 *
 * Variaveis opcionais:
 * - APP_CLOCK_ZONE: timezone IANA (padrao America/Sao_Paulo)
 * - APP_CLOCK_FIXED_INSTANT: instante fixo ISO-8601 para testes/diagnosticos
 */
public final class RelogioSistema {
    private static final Logger logger = LoggerFactory.getLogger(RelogioSistema.class);

    private static final String ENV_CLOCK_ZONE = "APP_CLOCK_ZONE";
    private static final String ENV_CLOCK_FIXED_INSTANT = "APP_CLOCK_FIXED_INSTANT";
    private static final ZoneId ZONA_PADRAO = ZoneId.of("America/Sao_Paulo");

    private static final Clock CLOCK = inicializarClock();

    private RelogioSistema() {
    }

    public static Clock clock() {
        return CLOCK;
    }

    public static LocalDate hoje() {
        return LocalDate.now(CLOCK);
    }

    public static LocalDateTime agora() {
        return LocalDateTime.now(CLOCK);
    }

    public static Instant agoraInstant() {
        return Instant.now(CLOCK);
    }

    private static Clock inicializarClock() {
        final ZoneId zoneId = resolverZoneId();
        final String fixedInstant = System.getenv(ENV_CLOCK_FIXED_INSTANT);

        if (fixedInstant != null && !fixedInstant.isBlank()) {
            try {
                final Instant instant = Instant.parse(fixedInstant.trim());
                logger.info("RelogioSistema inicializado em modo FIXED. zone={} instant={}", zoneId, instant);
                return Clock.fixed(instant, zoneId);
            } catch (final DateTimeParseException e) {
                logger.warn(
                    "Valor invalido para {}='{}'. Usando relogio de sistema em {}.",
                    ENV_CLOCK_FIXED_INSTANT,
                    fixedInstant,
                    zoneId
                );
            }
        }

        logger.info("RelogioSistema inicializado em modo SYSTEM. zone={}", zoneId);
        return Clock.system(zoneId);
    }

    private static ZoneId resolverZoneId() {
        final String zoneRaw = System.getenv(ENV_CLOCK_ZONE);
        if (zoneRaw == null || zoneRaw.isBlank()) {
            return ZONA_PADRAO;
        }

        try {
            return ZoneId.of(zoneRaw.trim());
        } catch (final Exception e) {
            logger.warn("Valor invalido para {}='{}'. Usando {}.", ENV_CLOCK_ZONE, zoneRaw, ZONA_PADRAO);
            return ZONA_PADRAO;
        }
    }
}
