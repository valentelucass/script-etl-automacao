package br.com.extrator.integracao.comum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.extrator.integracao.ResultadoExtracao;

class UsuariosCompletudeContencaoTest {
    @Test
    void devePreservarIncompletudeDeUsuariosAoAtingirLimiteDePaginas() {
        final ExtractionResult resultado = executar(false);

        assertEquals("INCOMPLETO_LIMITE", resultado.getStatus());
        assertFalse(resultado.isSucesso());
        assertFalse(resultado.isApiCompleta());
    }

    @Test
    void deveConfirmarUsuariosQuandoPaginacaoESalvamentoEstaoCompletos() {
        final ExtractionResult resultado = executar(true);

        assertEquals("COMPLETO", resultado.getStatus());
        assertTrue(resultado.isSucesso());
        assertTrue(resultado.isApiCompleta());
    }

    private ExtractionResult executar(final boolean completo) {
        final EntityExtractor<String> extractor = new EntityExtractor<>() {
            @Override
            public ResultadoExtracao<String> extract(final LocalDate inicio, final LocalDate fim) {
                return completo ? ResultadoExtracao.completo(List.of("synthetic"), 1, 1)
                    : ResultadoExtracao.incompleto(
                        List.of("synthetic"), ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS, 1, 1
                    );
            }

            @Override
            public int save(final List<String> registros) {
                return registros.size();
            }

            @Override
            public String getEntityName() {
                return "usuarios_sistema";
            }

            @Override
            public String getEmoji() {
                return "";
            }
        };
        final LocalDate dia = LocalDate.of(2026, 9, 8);
        return new ExtractionLogger(getClass()).executeWithLogging(extractor, dia, dia, "");
    }
}
