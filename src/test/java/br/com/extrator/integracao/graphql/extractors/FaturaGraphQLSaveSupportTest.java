package br.com.extrator.integracao.graphql.extractors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.extrator.persistencia.entidade.FaturaGraphQLEntity;
import br.com.extrator.suporte.console.LoggerConsole;

class FaturaGraphQLSaveSupportTest {

    private final FaturaGraphQLSaveSupport support =
        new FaturaGraphQLSaveSupport(null, new FaturaGraphQLEntityMapper(), LoggerConsole.getLogger(FaturaGraphQLSaveSupportTest.class));

    @Test
    void deveEnriquecerApenasQuandoFaltaremCamposQueONaoVieramNaQueryPrincipal() {
        final FaturaGraphQLEntity completa = criarEntity(1L, "NFSE-1", "boleto", "Banco A", null, null);
        final FaturaGraphQLEntity precisaTicket = criarEntity(2L, "NFSE-2", "boleto", null, null, null);
        final FaturaGraphQLEntity precisaNfse = criarEntity(3L, null, "pix", "Banco B", null, null);

        final Set<Long> paraEnriquecer = support.determinarFaturasParaEnriquecer(
            Map.of(
                1L, completa,
                2L, precisaTicket,
                3L, precisaNfse
            ),
            Map.of(2L, 999)
        );

        assertFalse(paraEnriquecer.contains(1L));
        assertFalse(paraEnriquecer.contains(2L));
        assertTrue(paraEnriquecer.contains(3L));
    }

    @Test
    void deveEnriquecerQuandoNaoHouverDadosBancariosNemReferenciaDeBanco() {
        final FaturaGraphQLEntity entity = criarEntity(10L, "NFSE-10", "boleto", null, null, null);

        final Set<Long> paraEnriquecer = support.determinarFaturasParaEnriquecer(
            Map.of(10L, entity),
            Map.of()
        );

        assertTrue(paraEnriquecer.contains(10L));
    }

    private FaturaGraphQLEntity criarEntity(final Long id,
                                            final String nfse,
                                            final String metodoPagamento,
                                            final String bancoNome,
                                            final String carteiraBanco,
                                            final String instrucaoBoleto) {
        final FaturaGraphQLEntity entity = new FaturaGraphQLEntity();
        entity.setId(id);
        entity.setNfseNumero(nfse);
        entity.setMetodoPagamento(metodoPagamento);
        entity.setBancoNome(bancoNome);
        entity.setCarteiraBanco(carteiraBanco);
        entity.setInstrucaoBoleto(instrucaoBoleto);
        return entity;
    }
}
