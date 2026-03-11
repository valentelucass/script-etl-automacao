/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/bootstrap/pipeline/SystemClockAdapter.java
Classe  : SystemClockAdapter (class)
Pacote  : br.com.extrator.bootstrap.pipeline
Modulo  : Bootstrap - Wiring

Papel   : Adapter que implementa ClockPort delegando ao RelogioSistema, provendo
          acesso ao relogio do sistema para o pipeline de forma testavel.

Conecta com:
- ClockPort (aplicacao.portas) — interface de porta de relogio implementada
- RelogioSistema (suporte.tempo) — utilitario de acesso ao clock do sistema

Fluxo geral:
1) hoje() retorna a data atual via RelogioSistema.hoje().
2) agora() retorna o instante atual via RelogioSistema.agora().
3) dormir(duration) executa Thread.sleep com a duracao informada, ignorando duracao nula/zero/negativa.

Estrutura interna:
Metodos principais:
- hoje(): retorna LocalDate atual do sistema.
- agora(): retorna LocalDateTime atual do sistema.
- dormir(duration): suspende a thread pelo tempo especificado.
[DOC-FILE-END]============================================================== */
package br.com.extrator.bootstrap.pipeline;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

import br.com.extrator.aplicacao.portas.ClockPort;
import br.com.extrator.suporte.tempo.RelogioSistema;

public final class SystemClockAdapter implements ClockPort {
    @Override
    public LocalDate hoje() {
        return RelogioSistema.hoje();
    }

    @Override
    public LocalDateTime agora() {
        return RelogioSistema.agora();
    }

    @Override
    public void dormir(final Duration duration) throws InterruptedException {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return;
        }
        Thread.sleep(duration.toMillis());
    }
}


