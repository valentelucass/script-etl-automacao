/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/portas/ExtractionLoggerPort.java
Classe  : ExtractionLoggerPort (interface)
Pacote  : br.com.extrator.aplicacao.portas
Modulo  : Porta (Interface)

Papel   : Porta para logging estruturado de eventos de extracao (observabilidade).

Conecta com:
- ExtractionLoggerAdapter (implementacao em observabilidade)

Fluxo geral:
1) logarEstruturado(eventName, fields) registra evento com campos.
2) Abstrai para JSON/syslog/elk/splunk.

Estrutura interna:
Metodos principais:
- logarEstruturado(String eventName, Map<String, Object> fields): logging estruturado.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.portas;

import java.util.Map;

public interface ExtractionLoggerPort {
    void logarEstruturado(String eventName, Map<String, Object> fields);
}
