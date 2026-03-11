/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/portas/ClockPort.java
Classe  : ClockPort (interface)
Pacote  : br.com.extrator.aplicacao.portas
Modulo  : Porta (Interface)

Papel   : Porta abstrata para acesso a tempo do sistema (hoje, agora, dormir).

Conecta com:
- RelogioSistema (implementacao em suporte/tempo)
- CircuitBreaker, RetryPolicy (consomem)

Fluxo geral:
1) hoje(): retorna LocalDate do dia de execucao.
2) agora(): retorna LocalDateTime (timestamp atual).
3) dormir(Duration): thread sleeps (abstrair para mockear em testes).

Estrutura interna:
Metodos principais:
- hoje(): LocalDate.
- agora(): LocalDateTime.
- dormir(Duration): thread sleep com InterruptedException.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.portas;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface ClockPort {
    LocalDate hoje();

    LocalDateTime agora();

    void dormir(Duration duration) throws InterruptedException;
}
