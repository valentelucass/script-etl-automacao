/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/adaptador/IntegridadeEtlPortAdapter.java
Classe  : IntegridadeEtlPortAdapter (class)
Pacote  : br.com.extrator.observabilidade.adaptador
Modulo  : Observabilidade - Adaptador

Papel   : Adapter que implementa IntegridadeEtlPort usando IntegridadeEtlValidator (wraps validador).

Conecta com:
- IntegridadeEtlPort (interface que implementa)
- IntegridadeEtlValidator (delegacao)

Fluxo geral:
1) validarExecucao(...) delega a validator.
2) Mapeia ResultadoValidacao -> ResultadoIntegridade.

Estrutura interna:
Atributos-chave:
- validator: IntegridadeEtlValidator (instancia no-arg).
Metodos principais:
- validarExecucao(): retorna ResultadoIntegridade com isValido + falhas.
[DOC-FILE-END]============================================================== */
package br.com.extrator.observabilidade.adaptador;

import java.time.LocalDateTime;
import java.util.Set;

import br.com.extrator.aplicacao.portas.IntegridadeEtlPort;
import br.com.extrator.observabilidade.servicos.IntegridadeEtlValidator;

/**
 * Adapter que implementa IntegridadeEtlPort usando IntegridadeEtlValidator.
 */
public class IntegridadeEtlPortAdapter implements IntegridadeEtlPort {

    private final IntegridadeEtlValidator validator;

    public IntegridadeEtlPortAdapter() {
        this.validator = new IntegridadeEtlValidator();
    }

    @Override
    public ResultadoIntegridade validarExecucao(
        final LocalDateTime inicioExecucao,
        final LocalDateTime fimExecucao,
        final Set<String> entidadesEsperadas,
        final boolean modoLoopDaemon
    ) {
        final IntegridadeEtlValidator.ResultadoValidacao resultado =
            validator.validarExecucao(inicioExecucao, fimExecucao, entidadesEsperadas, modoLoopDaemon);
        return new ResultadoIntegridade(resultado.isValido(), resultado.getFalhas());
    }
}
