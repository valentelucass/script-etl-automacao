/* ==[DOC-FILE]===============================================================
Arquivo : src/test/java/br/com/extrator/api/graphql/GraphQLIntervaloHelperTest.java
Classe  : GraphQLIntervaloHelperTest (class)
Pacote  : br.com.extrator.api.graphql
Modulo  : Teste automatizado
Papel   : Valida comportamento da unidade GraphQLIntervaloHelper.

Conecta com:
- ResultadoExtracao (api)

Fluxo geral:
1) Prepara cenarios e dados de teste.
2) Executa casos para validar comportamento de GraphQLIntervaloHelper.
3) Assegura regressao controlada nas regras principais.

Estrutura interna:
Metodos principais:
- devePropagarErroApiQuandoAlgumDiaFalha(): verifica comportamento esperado em teste automatizado.
- devePropagarCircuitBreakerNaExecucaoSilenciosa(): verifica comportamento esperado em teste automatizado.
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.api.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.extrator.api.ResultadoExtracao;

class GraphQLIntervaloHelperTest {

    @Test
    void devePropagarErroApiQuandoAlgumDiaFalha() {
        final LocalDate inicio = LocalDate.of(2026, 2, 19);
        final LocalDate fim = LocalDate.of(2026, 2, 20);

        final ResultadoExtracao<Integer> resultado = GraphQLIntervaloHelper.executarPorDia(
            inicio,
            fim,
            dia -> {
                if (dia.equals(inicio)) {
                    return ResultadoExtracao.completo(List.of(1, 2), 1, 2);
                }
                return ResultadoExtracao.incompleto(
                    List.of(),
                    ResultadoExtracao.MotivoInterrupcao.ERRO_API,
                    1,
                    0
                );
            },
            "Teste"
        );

        assertFalse(resultado.isCompleto());
        assertEquals(ResultadoExtracao.MotivoInterrupcao.ERRO_API.getCodigo(), resultado.getMotivoInterrupcao());
    }

    @Test
    void devePropagarCircuitBreakerNaExecucaoSilenciosa() {
        final LocalDate dia = LocalDate.of(2026, 2, 19);

        final ResultadoExtracao<Integer> resultado = GraphQLIntervaloHelper.executarPorDia(
            dia,
            dia,
            ignored -> ResultadoExtracao.incompleto(
                List.of(),
                ResultadoExtracao.MotivoInterrupcao.CIRCUIT_BREAKER,
                0,
                0
            ),
            "Teste",
            false
        );

        assertFalse(resultado.isCompleto());
        assertEquals(ResultadoExtracao.MotivoInterrupcao.CIRCUIT_BREAKER.getCodigo(), resultado.getMotivoInterrupcao());
    }
}
