/* ==[DOC-FILE]===============================================================
Arquivo : src/test/java/br/com/extrator/runners/common/ExtractionLoggerTest.java
Classe  : ExtractionLoggerTest (class)
Pacote  : br.com.extrator.runners.common
Modulo  : Teste automatizado
Papel   : Valida comportamento da unidade ExtractionLogger.

Conecta com:
- ResultadoExtracao (api)
- ConstantesEntidades (util.validacao)

Fluxo geral:
1) Prepara cenarios e dados de teste.
2) Executa casos para validar comportamento de ExtractionLogger.
3) Assegura regressao controlada nas regras principais.

Estrutura interna:
Metodos principais:
- deveClassificarComoCompletoQuandoSemDivergencias(): verifica comportamento esperado em teste automatizado.
- deveClassificarComoIncompletoDadosQuandoHaInvalidos(): verifica comportamento esperado em teste automatizado.
- deveClassificarComoIncompletoDbQuandoHaDivergenciaPersistencia(): verifica comportamento esperado em teste automatizado.
- deveConsiderarCompletoFaturasGraphqlQuandoBackfillAumentaVolumeSalvo(): verifica comportamento esperado em teste automatizado.
- deveClassificarComoErroApiQuandoMotivoForErroApi(): verifica comportamento esperado em teste automatizado.
- deveClassificarComoIncompletoLimiteQuandoMotivoNaoForErroApi(): verifica comportamento esperado em teste automatizado.
- executar(...2 args): executa o fluxo principal desta responsabilidade.
- executarGraphqlFaturas(...2 args): executa o fluxo principal desta responsabilidade.
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.runners.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.extrator.api.ResultadoExtracao;
import br.com.extrator.util.validacao.ConstantesEntidades;

class ExtractionLoggerTest {

    @Test
    void deveClassificarComoCompletoQuandoSemDivergencias() {
        final ResultadoExtracao<String> resultadoExtracao = ResultadoExtracao.completo(List.of("a", "b"), 1, 2);
        final DataExportEntityExtractor.SaveResult saveResult = new DataExportEntityExtractor.SaveResult(2, 2, 0);
        final ExtractionResult result = executar(resultadoExtracao, saveResult);

        assertEquals(ConstantesEntidades.STATUS_COMPLETO, result.getStatus());
    }

    @Test
    void deveClassificarComoIncompletoDadosQuandoHaInvalidos() {
        final ResultadoExtracao<String> resultadoExtracao = ResultadoExtracao.completo(List.of("a", "b"), 1, 2);
        final DataExportEntityExtractor.SaveResult saveResult = new DataExportEntityExtractor.SaveResult(2, 2, 1);
        final ExtractionResult result = executar(resultadoExtracao, saveResult);

        assertEquals(ConstantesEntidades.STATUS_INCOMPLETO_DADOS, result.getStatus());
    }

    @Test
    void deveClassificarComoIncompletoDbQuandoHaDivergenciaPersistencia() {
        final ResultadoExtracao<String> resultadoExtracao = ResultadoExtracao.completo(List.of("a", "b"), 1, 2);
        final DataExportEntityExtractor.SaveResult saveResult = new DataExportEntityExtractor.SaveResult(1, 2, 0);
        final ExtractionResult result = executar(resultadoExtracao, saveResult);

        assertEquals(ConstantesEntidades.STATUS_INCOMPLETO_DB, result.getStatus());
    }

    @Test
    void deveConsiderarCompletoFaturasGraphqlQuandoBackfillAumentaVolumeSalvo() {
        final ResultadoExtracao<String> resultadoExtracao = ResultadoExtracao.completo(List.of("a", "b"), 1, 2);
        final ExtractionResult result = executarGraphqlFaturas(resultadoExtracao, 5);

        assertEquals(ConstantesEntidades.STATUS_COMPLETO, result.getStatus());
        assertEquals(5, result.getTotalUnicos());
    }

    @Test
    void deveClassificarComoErroApiQuandoMotivoForErroApi() {
        final ResultadoExtracao<String> resultadoExtracao = ResultadoExtracao.incompleto(
            List.of("a"),
            ResultadoExtracao.MotivoInterrupcao.ERRO_API,
            1,
            1
        );
        final DataExportEntityExtractor.SaveResult saveResult = new DataExportEntityExtractor.SaveResult(1, 1, 0);
        final ExtractionResult result = executar(resultadoExtracao, saveResult);

        assertEquals(ConstantesEntidades.STATUS_ERRO_API, result.getStatus());
    }

    @Test
    void deveClassificarComoIncompletoLimiteQuandoMotivoNaoForErroApi() {
        final ResultadoExtracao<String> resultadoExtracao = ResultadoExtracao.incompleto(
            List.of("a"),
            ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS,
            1,
            1
        );
        final DataExportEntityExtractor.SaveResult saveResult = new DataExportEntityExtractor.SaveResult(1, 1, 0);
        final ExtractionResult result = executar(resultadoExtracao, saveResult);

        assertEquals(ConstantesEntidades.STATUS_INCOMPLETO_LIMITE, result.getStatus());
    }

    private ExtractionResult executar(final ResultadoExtracao<String> resultadoExtracao,
                                      final DataExportEntityExtractor.SaveResult saveResult) {
        final ExtractionLogger logger = new ExtractionLogger(ExtractionLoggerTest.class);
        final DummyDataExportExtractor extractor = new DummyDataExportExtractor(resultadoExtracao, saveResult);
        return logger.executeWithLogging(extractor, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1), "");
    }

    private ExtractionResult executarGraphqlFaturas(final ResultadoExtracao<String> resultadoExtracao,
                                                    final int salvos) {
        final ExtractionLogger logger = new ExtractionLogger(ExtractionLoggerTest.class);
        final DummyGraphqlFaturasExtractor extractor = new DummyGraphqlFaturasExtractor(resultadoExtracao, salvos);
        return logger.executeWithLogging(extractor, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1), "");
    }

    private static final class DummyDataExportExtractor implements DataExportEntityExtractor<String> {
        private final ResultadoExtracao<String> resultadoExtracao;
        private final SaveResult saveResult;

        private DummyDataExportExtractor(final ResultadoExtracao<String> resultadoExtracao,
                                         final SaveResult saveResult) {
            this.resultadoExtracao = resultadoExtracao;
            this.saveResult = saveResult;
        }

        @Override
        public ResultadoExtracao<String> extract(final LocalDate dataInicio, final LocalDate dataFim) {
            return resultadoExtracao;
        }

        @Override
        public SaveResult saveWithDeduplication(final List<String> dtos) {
            return saveResult;
        }

        @Override
        public String getEntityName() {
            return "entidade_teste";
        }

        @Override
        public String getEmoji() {
            return "";
        }
    }

    private static final class DummyGraphqlFaturasExtractor implements EntityExtractor<String> {
        private final ResultadoExtracao<String> resultadoExtracao;
        private final int salvos;

        private DummyGraphqlFaturasExtractor(final ResultadoExtracao<String> resultadoExtracao,
                                             final int salvos) {
            this.resultadoExtracao = resultadoExtracao;
            this.salvos = salvos;
        }

        @Override
        public ResultadoExtracao<String> extract(final LocalDate dataInicio, final LocalDate dataFim) {
            return resultadoExtracao;
        }

        @Override
        public int save(final List<String> dtos) {
            return salvos;
        }

        @Override
        public String getEntityName() {
            return ConstantesEntidades.FATURAS_GRAPHQL;
        }

        @Override
        public String getEmoji() {
            return "";
        }
    }
}
