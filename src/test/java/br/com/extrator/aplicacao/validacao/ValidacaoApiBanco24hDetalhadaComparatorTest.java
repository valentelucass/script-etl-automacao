package br.com.extrator.aplicacao.validacao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.JanelaExecucao;
import br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.ResultadoApiChaves;
import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

class ValidacaoApiBanco24hDetalhadaComparatorTest {

    private final ValidacaoApiBanco24hDetalhadaComparator comparator =
        new ValidacaoApiBanco24hDetalhadaComparator(
            new ValidacaoApiBanco24hDetalhadaRepository(
                LoggerConsole.getLogger(ValidacaoApiBanco24hDetalhadaComparatorTest.class),
                new ValidacaoApiBanco24hDetalhadaMetadataHasher()
            )
        );

    @Test
    void deveTolerarManifestosMarginaisNaJanelaAberta() {
        comparator.definirPeriodoFechado(false);

        assertTrue(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.MANIFESTOS,
            477,
            464,
            16,
            3,
            0
        )));
    }

    @Test
    void naoDeveTolerarManifestosMarginaisNoPeriodoFechado() {
        comparator.definirPeriodoFechado(true);

        assertFalse(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.MANIFESTOS,
            477,
            464,
            16,
            3,
            0
        )));
    }

    @Test
    void deveTolerarCotacoesMarginaisNaJanelaAberta() {
        comparator.definirPeriodoFechado(false);

        assertTrue(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.COTACOES,
            540,
            517,
            23,
            0,
            7
        )));
    }

    @Test
    void deveTolerarLocalizacaoContasEFaturasComDriftCurto() {
        comparator.definirPeriodoFechado(false);

        assertTrue(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.LOCALIZACAO_CARGAS,
            1401,
            1350,
            61,
            10,
            143
        )));
        assertTrue(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.CONTAS_A_PAGAR,
            218,
            216,
            2,
            0,
            0
        )));
        assertTrue(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.FATURAS_POR_CLIENTE,
            1453,
            1343,
            136,
            26,
            44
        )));
    }

    @Test
    void deveTolerarDriftAbertoObservadoEmColetasFretesContasEUsuarios() {
        comparator.definirPeriodoFechado(false);

        assertTrue(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.COLETAS,
            429,
            408,
            29,
            8,
            45
        )));
        assertTrue(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.FRETES,
            1401,
            1314,
            91,
            4,
            22
        )));
        assertTrue(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.CONTAS_A_PAGAR,
            114,
            114,
            1,
            1,
            1
        )));
        assertTrue(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.USUARIOS_SISTEMA,
            216,
            195,
            21,
            0,
            0
        )));
        assertTrue(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.FATURAS_GRAPHQL,
            331,
            324,
            7,
            0,
            0
        )));
        assertTrue(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.INVENTARIO,
            1690,
            1686,
            5,
            1,
            16
        )));
    }

    @Test
    void naoDeveTolerarDerivaAbertaAcimaDosLimitesConfigurados() {
        comparator.definirPeriodoFechado(false);

        assertFalse(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.COLETAS,
            429,
            408,
            41,
            8,
            45
        )));
        assertFalse(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.FRETES,
            1211,
            1070,
            251,
            0,
            13
        )));
        assertFalse(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.COTACOES,
            540,
            517,
            23,
            0,
            13
        )));
        assertFalse(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.LOCALIZACAO_CARGAS,
            1401,
            1350,
            61,
            10,
            181
        )));
        assertFalse(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.FATURAS_POR_CLIENTE,
            1453,
            1343,
            161,
            26,
            61
        )));
        assertFalse(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.FATURAS_GRAPHQL,
            331,
            324,
            16,
            0,
            0
        )));
        assertFalse(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.INVENTARIO,
            1690,
            1686,
            5,
            1,
            31
        )));
    }

    @Test
    void naoDeveTolerarInventarioEFaturasGraphqlEmPeriodoFechado() {
        comparator.definirPeriodoFechado(true);

        assertFalse(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.FATURAS_GRAPHQL,
            331,
            324,
            7,
            0,
            0
        )));
        assertFalse(comparator.completudeDinamicaTolerada(resultado(
            ConstantesEntidades.INVENTARIO,
            1690,
            1686,
            5,
            1,
            16
        )));
    }

    @Test
    void deveEscalarToleranciaPelaIdadeDaJanelaAberta() {
        comparator.definirPeriodoFechado(false);

        final var resultadoRecente = new ValidacaoApiBanco24hDetalhadaTypes.ResultadoComparacao(
            ConstantesEntidades.LOCALIZACAO_CARGAS,
            1455,
            1455,
            0,
            1350,
            115,
            10,
            176,
            true,
            null,
            "janela aberta recente",
            0
        );
        final var resultadoEnvelhecido = new ValidacaoApiBanco24hDetalhadaTypes.ResultadoComparacao(
            ConstantesEntidades.LOCALIZACAO_CARGAS,
            1455,
            1455,
            0,
            1350,
            115,
            10,
            176,
            true,
            null,
            "janela aberta envelhecida",
            50
        );

        assertFalse(comparator.completudeDinamicaTolerada(resultadoRecente));
        assertTrue(comparator.completudeDinamicaTolerada(resultadoEnvelhecido));
    }

    @Test
    void deveTolerarManifestosMarginaisQuandoJanelaAbertaEnvelhece() {
        comparator.definirPeriodoFechado(false);

        final var resultadoRecente = new ValidacaoApiBanco24hDetalhadaTypes.ResultadoComparacao(
            ConstantesEntidades.MANIFESTOS,
            651,
            651,
            0,
            632,
            22,
            3,
            0,
            true,
            null,
            "manifestos recentes",
            0
        );
        final var resultadoEnvelhecido = new ValidacaoApiBanco24hDetalhadaTypes.ResultadoComparacao(
            ConstantesEntidades.MANIFESTOS,
            651,
            651,
            0,
            632,
            22,
            3,
            0,
            true,
            null,
            "manifestos envelhecidos",
            60
        );

        assertFalse(comparator.completudeDinamicaTolerada(resultadoRecente));
        assertTrue(comparator.completudeDinamicaTolerada(resultadoEnvelhecido));
    }

    @Test
    void deveUsarFiltroEstritoEmContasAPagarQuandoPeriodoFechadoEstiverAncoradoEmExecucaoEstruturada() throws SQLException {
        final JanelaExecucao janelaEstruturada = new JanelaExecucao(
            LocalDateTime.of(2026, 4, 13, 17, 46, 14),
            LocalDateTime.of(2026, 4, 13, 17, 46, 39),
            true
        );
        final AtomicBoolean filtroEstritoChaves = new AtomicBoolean();
        final AtomicBoolean filtroEstritoHashes = new AtomicBoolean();
        final ValidacaoApiBanco24hDetalhadaRepository repositoryEspia =
            new ValidacaoApiBanco24hDetalhadaRepository(
                LoggerConsole.getLogger(ValidacaoApiBanco24hDetalhadaComparatorTest.class),
                new ValidacaoApiBanco24hDetalhadaMetadataHasher()
            ) {
                @Override
                Optional<JanelaExecucao> buscarJanelaEstruturadaDaExecucao(
                    final Connection conexao,
                    final String executionUuid,
                    final String entidade
                ) {
                    return Optional.of(janelaEstruturada);
                }

                @Override
                Set<String> carregarChavesBancoNaJanela(
                    final Connection conexao,
                    final String entidade,
                    final JanelaExecucao janela,
                    final LocalDate periodoInicio,
                    final LocalDate periodoFim,
                    final boolean filtroEstritoDataExtracao
                ) {
                    filtroEstritoChaves.set(filtroEstritoDataExtracao);
                    return Set.of("115589");
                }

                @Override
                Map<String, String> carregarHashesMetadataBancoNaJanela(
                    final Connection conexao,
                    final String entidade,
                    final JanelaExecucao janela,
                    final LocalDate periodoInicio,
                    final LocalDate periodoFim,
                    final boolean filtroEstritoDataExtracao
                ) {
                    filtroEstritoHashes.set(filtroEstritoDataExtracao);
                    return Map.of("115589", "hash-api");
                }
            };
        final ValidacaoApiBanco24hDetalhadaComparator comparatorEstrito =
            new ValidacaoApiBanco24hDetalhadaComparator(repositoryEspia);
        comparatorEstrito.definirPeriodoFechado(true);
        final ResultadoApiChaves api = new ResultadoApiChaves(
            1,
            1,
            0,
            Set.of("115589"),
            Map.of("115589", "hash-api"),
            Map.of(),
            "api fechada",
            Set.of(),
            Map.of(),
            true,
            null,
            1,
            Set.of()
        );

        final var resultado = comparatorEstrito.compararEntidade(
            null,
            ConstantesEntidades.CONTAS_A_PAGAR,
            api,
            LocalDate.of(2026, 4, 13),
            LocalDate.of(2026, 4, 12),
            LocalDate.of(2026, 4, 12),
            true,
            false,
            Optional.of("exec-fechada")
        );

        assertTrue(resultado.ok());
        assertTrue(filtroEstritoChaves.get());
        assertTrue(filtroEstritoHashes.get());
        assertTrue(resultado.detalhe().contains("filtro_banco=janela_estruturada_estrita"));
        assertEquals(1, resultado.banco());
    }

    private ValidacaoApiBanco24hDetalhadaTypes.ResultadoComparacao resultado(
        final String entidade,
        final int apiUnico,
        final int banco,
        final int faltantes,
        final int excedentes,
        final int divergenciasDados
    ) {
        return new ValidacaoApiBanco24hDetalhadaTypes.ResultadoComparacao(
            entidade,
            apiUnico,
            apiUnico,
            0,
            banco,
            faltantes,
            excedentes,
            divergenciasDados,
            true,
            null,
            "teste"
        );
    }
}
