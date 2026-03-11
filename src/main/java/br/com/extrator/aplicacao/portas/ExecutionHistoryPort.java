/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/portas/ExecutionHistoryPort.java
Classe  : ExecutionHistoryPort (interface)
Pacote  : br.com.extrator.aplicacao.portas
Modulo  : Porta (Interface)

Papel   : Porta para consultar total de registros extraidos em intervalo de tempo.

Conecta com:
- ExecutionHistoryRepository (implementacao em persistencia)

Fluxo geral:
1) calcularTotalRegistros(inicio, fim) consulta BD.
2) Retorna count de registros naquele intervalo.
3) Usa: metricas, decisoes de qualidade.

Estrutura interna:
Metodos principais:
- calcularTotalRegistros(LocalDateTime, LocalDateTime): int total de registros.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.portas;

import java.time.LocalDateTime;

@FunctionalInterface
public interface ExecutionHistoryPort {
    int calcularTotalRegistros(LocalDateTime inicio, LocalDateTime fim);
}
