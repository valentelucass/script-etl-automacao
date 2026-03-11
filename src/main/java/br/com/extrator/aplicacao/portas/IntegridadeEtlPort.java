/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/portas/IntegridadeEtlPort.java
Classe  : IntegridadeEtlPort (interface)
Pacote  : br.com.extrator.aplicacao.portas
Modulo  : Porta (Interface)

Papel   : Porta para validacao de integridade ETL apos execucao de pipeline (schema, chaves, referencial).

Conecta com:
- IntegridadeEtlPortAdapter (implementacao em observabilidade/adaptador)
- FluxoCompletoUseCase, ExtracaoPorIntervaloUseCase (consomem)

Fluxo geral:
1) validarExecucao(inicioExecucao, fimExecucao, entidades, modoLoopDaemon).
2) Retorna ResultadoIntegridade (valido, falhas).
3) Valida schema, chaves estrangeiras, referencial (origin x destino).

Estrutura interna:
Inner class ResultadoIntegridade:
- valido: boolean.
- falhas: List<String> imutavel (detalhe de cada validacao falhada).
Metodos principais:
- validarExecucao(): retorna ResultadoIntegridade.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.portas;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Porta para validacao de integridade ETL apos uma execucao de pipeline.
 */
public interface IntegridadeEtlPort {

    ResultadoIntegridade validarExecucao(
        LocalDateTime inicioExecucao,
        LocalDateTime fimExecucao,
        Set<String> entidadesEsperadas,
        boolean modoLoopDaemon
    );

    final class ResultadoIntegridade {
        private final boolean valido;
        private final List<String> falhas;

        public ResultadoIntegridade(final boolean valido, final List<String> falhas) {
            this.valido = valido;
            this.falhas = List.copyOf(falhas);
        }

        public boolean isValido() {
            return valido;
        }

        public List<String> getFalhas() {
            return falhas;
        }
    }
}
