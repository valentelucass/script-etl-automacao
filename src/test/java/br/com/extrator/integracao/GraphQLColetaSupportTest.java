package br.com.extrator.integracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import br.com.extrator.dominio.graphql.coletas.ColetaNodeDTO;

class GraphQLColetaSupportTest {

    @Test
    void deveFalharQuandoNaoHouverIdNemSequenceCode() {
        final ColetaNodeDTO coleta = new ColetaNodeDTO();

        assertThrows(IllegalStateException.class, () -> GraphQLColetaSupport.resolverChaveDeduplicacao(coleta));
    }

    @Test
    void deveUsarSequenceCodeQuandoIdNaoExistir() {
        final ColetaNodeDTO coleta = new ColetaNodeDTO();
        coleta.setSequenceCode(123L);

        assertEquals("SEQ:123", GraphQLColetaSupport.resolverChaveDeduplicacao(coleta));
    }
}
