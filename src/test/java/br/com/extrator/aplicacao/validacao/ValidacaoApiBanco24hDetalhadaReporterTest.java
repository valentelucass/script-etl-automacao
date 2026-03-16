package br.com.extrator.aplicacao.validacao;

import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.ResumoExecucao;
import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.ResultadoComparacao;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

class ValidacaoApiBanco24hDetalhadaReporterTest {

    private final ValidacaoApiBanco24hDetalhadaReporter reporter =
        new ValidacaoApiBanco24hDetalhadaReporter(
            LoggerConsole.getLogger(ValidacaoApiBanco24hDetalhadaReporterTest.class),
            new ValidacaoApiBanco24hDetalhadaComparator(null)
        );

    @Test
    void deveTratarDivergenciaDinamicaComoOk() {
        final ResultadoComparacao resultado = new ResultadoComparacao(
            ConstantesEntidades.FRETES,
            10,
            10,
            0,
            10,
            0,
            0,
            3,
            true,
            null,
            "somente drift"
        );

        final ResumoExecucao resumo = reporter.reportar(List.of(resultado));

        assertEquals(1, resumo.ok());
        assertEquals(0, resumo.falhas());
    }

    @Test
    void devePriorizarFalhaDeCompletude() {
        final ResultadoComparacao resultado = new ResultadoComparacao(
            ConstantesEntidades.MANIFESTOS,
            10,
            10,
            0,
            9,
            1,
            0,
            2,
            true,
            null,
            "faltante real"
        );

        final ResumoExecucao resumo = reporter.reportar(List.of(resultado));

        assertEquals(0, resumo.ok());
        assertEquals(1, resumo.falhas());
    }

    @Test
    void deveTolerarUsuariosSistemaComDriftPequeno() {
        final ResultadoComparacao resultado = new ResultadoComparacao(
            ConstantesEntidades.USUARIOS_SISTEMA,
            43625,
            43618,
            0,
            43575,
            43,
            0,
            0,
            true,
            null,
            "snapshot vivo"
        );

        final ResumoExecucao resumo = reporter.reportar(List.of(resultado));

        assertEquals(1, resumo.ok());
        assertEquals(0, resumo.falhas());
    }

    @Test
    void deveTolerarColetasComDesvioMarginalDeCompletude() {
        final ResultadoComparacao resultado = new ResultadoComparacao(
            ConstantesEntidades.COLETAS,
            221,
            221,
            0,
            219,
            2,
            0,
            7,
            true,
            null,
            "requestDate fallback"
        );

        final ResumoExecucao resumo = reporter.reportar(List.of(resultado));

        assertEquals(1, resumo.ok());
        assertEquals(0, resumo.falhas());
    }

    @Test
    void deveTolerarContasAPagarComDriftMarginal() {
        final ResultadoComparacao resultado = new ResultadoComparacao(
            ConstantesEntidades.CONTAS_A_PAGAR,
            82,
            79,
            0,
            78,
            1,
            0,
            0,
            true,
            null,
            "snapshot vivo"
        );

        final ResumoExecucao resumo = reporter.reportar(List.of(resultado));

        assertEquals(1, resumo.ok());
        assertEquals(0, resumo.falhas());
    }

    @Test
    void deveTolerarManifestosComVariacaoMarginalSemDivergenciaDeConteudo() {
        final ResultadoComparacao resultado = new ResultadoComparacao(
            ConstantesEntidades.MANIFESTOS,
            323,
            322,
            0,
            319,
            4,
            1,
            0,
            true,
            null,
            "manifestos em transito"
        );

        final ResumoExecucao resumo = reporter.reportar(List.of(resultado));

        assertEquals(1, resumo.ok());
        assertEquals(0, resumo.falhas());
    }

    @Test
    void deveTolerarLocalizacaoComUmExcedenteDeSnapshot() {
        final ResultadoComparacao resultado = new ResultadoComparacao(
            ConstantesEntidades.LOCALIZACAO_CARGAS,
            772,
            772,
            0,
            773,
            0,
            1,
            63,
            true,
            null,
            "snapshot vivo da origem"
        );

        final ResumoExecucao resumo = reporter.reportar(List.of(resultado));

        assertEquals(1, resumo.ok());
        assertEquals(0, resumo.falhas());
    }

    @Test
    void deveTolerarFaturasPorClienteComExcedenteMarginalDeSnapshot() {
        final ResultadoComparacao resultado = new ResultadoComparacao(
            ConstantesEntidades.FATURAS_POR_CLIENTE,
            772,
            772,
            0,
            773,
            0,
            1,
            1,
            true,
            null,
            "snapshot vivo da origem"
        );

        final ResumoExecucao resumo = reporter.reportar(List.of(resultado));

        assertEquals(1, resumo.ok());
        assertEquals(0, resumo.falhas());
    }

    @Test
    void deveFalharQuandoApiFoiInterrompida() {
        final ResultadoComparacao resultado = new ResultadoComparacao(
            ConstantesEntidades.COTACOES,
            10,
            10,
            0,
            10,
            0,
            0,
            0,
            false,
            "LACUNA_PAGINACAO_422",
            "api_extracao=INCOMPLETA"
        );

        final ResumoExecucao resumo = reporter.reportar(List.of(resultado));

        assertEquals(0, resumo.ok());
        assertEquals(1, resumo.falhas());
    }
}
