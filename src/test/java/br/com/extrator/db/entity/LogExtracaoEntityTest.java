/* ==[DOC-FILE]===============================================================
Arquivo : src/test/java/br/com/extrator/db/entity/LogExtracaoEntityTest.java
Classe  : LogExtracaoEntityTest (class)
Pacote  : br.com.extrator.db.entity
Modulo  : Teste automatizado
Papel   : Valida comportamento da unidade LogExtracaoEntity.

Conecta com:
- StatusExtracao (db.entity.LogExtracaoEntity)

Fluxo geral:
1) Prepara cenarios e dados de teste.
2) Executa casos para validar comportamento de LogExtracaoEntity.
3) Assegura regressao controlada nas regras principais.

Estrutura interna:
Metodos principais:
- deveMapearStatusEspecificosSemCoercao(): verifica comportamento esperado em teste automatizado.
- deveManterStatusLegadoIncompletoSemForcarLimite(): verifica comportamento esperado em teste automatizado.
- deveMapearAliasLegadosParaStatusAtual(): verifica comportamento esperado em teste automatizado.
- deveLancarErroParaStatusInvalido(): verifica comportamento esperado em teste automatizado.
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.db.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import br.com.extrator.db.entity.LogExtracaoEntity.StatusExtracao;

class LogExtracaoEntityTest {

    @Test
    void deveMapearStatusEspecificosSemCoercao() {
        assertEquals(StatusExtracao.COMPLETO, StatusExtracao.fromString("COMPLETO"));
        assertEquals(StatusExtracao.INCOMPLETO_LIMITE, StatusExtracao.fromString("INCOMPLETO_LIMITE"));
        assertEquals(StatusExtracao.INCOMPLETO_DADOS, StatusExtracao.fromString("INCOMPLETO_DADOS"));
        assertEquals(StatusExtracao.INCOMPLETO_DB, StatusExtracao.fromString("INCOMPLETO_DB"));
        assertEquals(StatusExtracao.ERRO_API, StatusExtracao.fromString("ERRO_API"));
    }

    @Test
    void deveManterStatusLegadoIncompletoSemForcarLimite() {
        assertEquals(StatusExtracao.INCOMPLETO, StatusExtracao.fromString("INCOMPLETO"));
    }

    @Test
    void deveMapearAliasLegadosParaStatusAtual() {
        assertEquals(StatusExtracao.INCOMPLETO_DADOS, StatusExtracao.fromString("INCOMPLETO_DADOS_INVALIDOS"));
        assertEquals(StatusExtracao.INCOMPLETO_DB, StatusExtracao.fromString("INCOMPLETO_SALVAMENTO"));
    }

    @Test
    void deveLancarErroParaStatusInvalido() {
        assertThrows(IllegalArgumentException.class, () -> StatusExtracao.fromString("DESCONHECIDO"));
        assertThrows(IllegalArgumentException.class, () -> StatusExtracao.fromString(" "));
    }
}

