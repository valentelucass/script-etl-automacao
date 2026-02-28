/* ==[DOC-FILE]===============================================================
Arquivo : src/test/java/br/com/extrator/util/formatacao/FormatadorDataTest.java
Classe  : FormatadorDataTest (class)
Pacote  : br.com.extrator.util.formatacao
Modulo  : Teste automatizado
Papel   : Valida comportamento da unidade FormatadorData.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Prepara cenarios e dados de teste.
2) Executa casos para validar comportamento de FormatadorData.
3) Assegura regressao controlada nas regras principais.

Estrutura interna:
Metodos principais:
- deveParsearOffsetDateTimeIsoComTimezone(): verifica comportamento esperado em teste automatizado.
- deveParsearDataSemHoraComoInicioDoDiaNoOffsetPadrao(): verifica comportamento esperado em teste automatizado.
- deveRetornarNullParaValorInvalido(): verifica comportamento esperado em teste automatizado.
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.util.formatacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class FormatadorDataTest {

    @Test
    void deveParsearOffsetDateTimeIsoComTimezone() {
        final OffsetDateTime resultado = FormatadorData.parseOffsetDateTime("2025-10-02T00:00:00.000-03:00");

        assertNotNull(resultado);
        assertEquals(2025, resultado.getYear());
        assertEquals(10, resultado.getMonthValue());
        assertEquals(2, resultado.getDayOfMonth());
        assertEquals(-3, resultado.getOffset().getTotalSeconds() / 3600);
    }

    @Test
    void deveParsearDataSemHoraComoInicioDoDiaNoOffsetPadrao() {
        final OffsetDateTime resultado = FormatadorData.parseOffsetDateTime("2025-10-07");

        assertNotNull(resultado);
        assertEquals(2025, resultado.getYear());
        assertEquals(10, resultado.getMonthValue());
        assertEquals(7, resultado.getDayOfMonth());
        assertEquals(0, resultado.getHour());
        assertEquals(-3, resultado.getOffset().getTotalSeconds() / 3600);
    }

    @Test
    void deveRetornarNullParaValorInvalido() {
        final OffsetDateTime resultado = FormatadorData.parseOffsetDateTime("data-invalida");

        assertNull(resultado);
    }
}
