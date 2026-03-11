/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/portas/CompletudePort.java
Classe  : CompletudePort (interface)
Pacote  : br.com.extrator.aplicacao.portas
Modulo  : Porta (Interface)

Papel   : Porta para validacao de completude dos dados extraidos (origem x destino).

Conecta com:
- CompletudePortAdapter (implementacao em observabilidade/adaptador)
- FluxoCompletoUseCase (consume)

Fluxo geral:
1) validarCompletudePorLogs(dataReferencia) retorna Map<entidade, StatusCompletude>.
2) StatusCompletude: OK, INCOMPLETO, DUPLICADOS, ERRO.
3) Use case avalia resultado para decidir se extracao completa ou falhou.

Estrutura interna:
Enum StatusCompletude:
- OK: entidade completa e consistente.
- INCOMPLETO: registros faltam (origem x destino mismatch).
- DUPLICADOS: registros duplicados no destino.
- ERRO: erro ao validar completude.
Metodos principais:
- validarCompletudePorLogs(LocalDate dataReferencia): retorna mapa de status por entidade.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.portas;

import java.time.LocalDate;
import java.util.Map;

/**
 * Porta para validacao de completude dos dados extraidos (origem x destino).
 */
public interface CompletudePort {

    Map<String, StatusCompletude> validarCompletudePorLogs(LocalDate dataReferencia);

    enum StatusCompletude {
        OK,
        INCOMPLETO,
        DUPLICADOS,
        ERRO
    }
}
