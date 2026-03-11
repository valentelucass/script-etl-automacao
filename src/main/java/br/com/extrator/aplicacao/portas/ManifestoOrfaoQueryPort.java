/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/portas/ManifestoOrfaoQueryPort.java
Classe  : ManifestoOrfaoQueryPort (interface)
Pacote  : br.com.extrator.aplicacao.portas
Modulo  : Porta (Interface)

Papel   : Porta de consulta para manifestos orfaos (sem coleta correspondente) para backfill dinamico.

Conecta com:
- ManifestoOrfaoQueryAdapter (implementacao em persistencia/adaptador)
- PreBackfillReferencialColetasUseCase (consume)

Fluxo geral:
1) buscarDataMaisAntigaManifestoOrfao() retorna MIN(created_at) de orfaos.
2) Use: pre-backfill dinamico, fallback com janela fixa se vazio.
3) Falha silenciosa => retorna Optional.empty().

Estrutura interna:
Metodos principais:
- buscarDataMaisAntigaManifestoOrfao(): Optional<LocalDate> (MIN data orfao ou vazio).
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.portas;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Porta de consulta para dados de manifestos orfaos.
 * Usada pelo pre-backfill referencial para determinar dinamicamente
 * a janela minima de retroatividade necessaria para cobrir coletas ausentes.
 */
public interface ManifestoOrfaoQueryPort {

    /**
     * Retorna a data mais antiga de criacao de um manifesto que possui
     * pick_sequence_code sem coleta correspondente no banco.
     * Retorna vazio se nao ha orphans registrados.
     */
    Optional<LocalDate> buscarDataMaisAntigaManifestoOrfao();
}
