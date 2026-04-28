/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/validacao/ValidacaoApiBanco24hDetalhadaComparator.java
Classe  : ValidacaoApiBanco24hDetalhadaComparator (final class)
Pacote  : br.com.extrator.aplicacao.validacao
Modulo  : Use Case - Validacao

Papel   : Compara dados API vs banco de dados, detectando faltantes, excedentes, divergencias de metadados com suporte a modos fecha do e tolerancias.

Conecta com:
- ValidacaoApiBanco24hDetalhadaRepository (para consultas de janela e carregamento de chaves/hashes)

Fluxo geral:
1) compararEntidade() busca janela COMPLETA compativel no banco.
2) Carrega chaves e hashes do banco na janela, compara com API.
3) Detecta faltantes (em API mas nao banco), excedentes (em banco mas nao API), divergencias_dados (hash diferente).
4) Retorna ResultadoComparacao com contagens, amostras de discrepancias, detalhe com modo fechado/fallback.

Estrutura interna:
Atributos-chave:
- repository: ValidacaoApiBanco24hDetalhadaRepository (para queries BD).
- AMOSTRA_MAX: limite de amostras em detalhe (15).
Metodos principais:
- compararEntidade(Connection, String, ResultadoApiChaves, LocalDate, LocalDate, LocalDate, boolean, boolean, Optional<String>): comparacao principal.
- compararEntidade(Connection, String, ResultadoApiChaves, LocalDate, LocalDate, LocalDate, boolean, boolean): overload retrocompativel sem ancora estruturada explicita.
- somenteDivergenciaDadosTolerada(ResultadoComparacao): verifica se tolerancia aplica (COTACOES, LOCALIZACAO_CARGAS, FRETES, COLETAS).
- completudeDinamicaTolerada(ResultadoComparacao): identifica pequenos desvios de snapshot em entidades dinamicas.
- construirDetalheComparacao(): monta string detalhe com amostras.
- amostraChaves(Set<String>): retorna primeiras AMOSTRA_MAX chaves ordenadas.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.validacao;

import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.JanelaExecucao;
import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.ResultadoApiChaves;
import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.ResultadoComparacao;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import br.com.extrator.suporte.tempo.RelogioSistema;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

final class ValidacaoApiBanco24hDetalhadaComparator {
    private static final int AMOSTRA_MAX = 15;
    private static final int LIMIAR_MANIFESTOS_VARIACAO_ABSOLUTO = 5;
    private static final double LIMIAR_MANIFESTOS_VARIACAO_PERCENTUAL = 0.02d;
    private static final int LIMIAR_MANIFESTOS_VARIACAO_ABSOLUTO_ABERTO = 20;
    private static final double LIMIAR_MANIFESTOS_VARIACAO_PERCENTUAL_ABERTO = 0.05d;
    private static final int LIMIAR_USUARIOS_FALTANTES_ABSOLUTO_ABERTO = 30;
    private static final double LIMIAR_USUARIOS_FALTANTES_PERCENTUAL_ABERTO = 0.10d;
    private static final int LIMIAR_COLETAS_VARIACAO_ABSOLUTO_ABERTO = 40;
    private static final double LIMIAR_COLETAS_VARIACAO_PERCENTUAL_ABERTO = 0.08d;
    private static final int LIMIAR_COLETAS_DIVERGENCIA_ABSOLUTO_ABERTO = 60;
    private static final double LIMIAR_COLETAS_DIVERGENCIA_PERCENTUAL_ABERTO = 0.12d;
    private static final int LIMIAR_FRETES_VARIACAO_ABSOLUTO_ABERTO = 250;
    private static final double LIMIAR_FRETES_VARIACAO_PERCENTUAL_ABERTO = 0.18d;
    private static final int LIMIAR_FRETES_DIVERGENCIA_ABSOLUTO_ABERTO = 50;
    private static final double LIMIAR_FRETES_DIVERGENCIA_PERCENTUAL_ABERTO = 0.04d;
    private static final int LIMIAR_CONTAS_A_PAGAR_VARIACAO_ABSOLUTO_ABERTO = 5;
    private static final double LIMIAR_CONTAS_A_PAGAR_VARIACAO_PERCENTUAL_ABERTO = 0.03d;
    private static final int LIMIAR_CONTAS_A_PAGAR_DIVERGENCIA_ABSOLUTO_ABERTO = 5;
    private static final double LIMIAR_CONTAS_A_PAGAR_DIVERGENCIA_PERCENTUAL_ABERTO = 0.03d;
    private static final int LIMIAR_COTACOES_VARIACAO_ABSOLUTO_ABERTO = 30;
    private static final double LIMIAR_COTACOES_VARIACAO_PERCENTUAL_ABERTO = 0.05d;
    private static final int LIMIAR_COTACOES_DIVERGENCIA_ABSOLUTO_ABERTO = 12;
    private static final double LIMIAR_COTACOES_DIVERGENCIA_PERCENTUAL_ABERTO = 0.02d;
    private static final int LIMIAR_LOCALIZACAO_CARGAS_VARIACAO_ABSOLUTO_ABERTO = 160;
    private static final double LIMIAR_LOCALIZACAO_CARGAS_VARIACAO_PERCENTUAL_ABERTO = 0.10d;
    private static final int LIMIAR_LOCALIZACAO_CARGAS_DIVERGENCIA_ABSOLUTO_ABERTO = 180;
    private static final double LIMIAR_LOCALIZACAO_CARGAS_DIVERGENCIA_PERCENTUAL_ABERTO = 0.12d;
    private static final int LIMIAR_FATURAS_POR_CLIENTE_VARIACAO_ABSOLUTO_ABERTO = 160;
    private static final double LIMIAR_FATURAS_POR_CLIENTE_VARIACAO_PERCENTUAL_ABERTO = 0.10d;
    private static final int LIMIAR_FATURAS_POR_CLIENTE_DIVERGENCIA_ABSOLUTO_ABERTO = 60;
    private static final double LIMIAR_FATURAS_POR_CLIENTE_DIVERGENCIA_PERCENTUAL_ABERTO = 0.04d;
    private static final int LIMIAR_INVENTARIO_VARIACAO_ABSOLUTO_ABERTO = 20;
    private static final double LIMIAR_INVENTARIO_VARIACAO_PERCENTUAL_ABERTO = 0.01d;
    private static final int LIMIAR_INVENTARIO_DIVERGENCIA_ABSOLUTO_ABERTO = 30;
    private static final double LIMIAR_INVENTARIO_DIVERGENCIA_PERCENTUAL_ABERTO = 0.012d;
    private static final int LIMIAR_FATURAS_GRAPHQL_VARIACAO_ABSOLUTO_ABERTO = 15;
    private static final double LIMIAR_FATURAS_GRAPHQL_VARIACAO_PERCENTUAL_ABERTO = 0.03d;

    private final ValidacaoApiBanco24hDetalhadaRepository repository;
    private boolean periodoFechadoContexto;

    ValidacaoApiBanco24hDetalhadaComparator(final ValidacaoApiBanco24hDetalhadaRepository repository) {
        this.repository = repository;
    }

    void definirPeriodoFechado(final boolean periodoFechado) {
        this.periodoFechadoContexto = periodoFechado;
    }

    ResultadoComparacao compararEntidade(
        final Connection conexao,
        final String entidade,
        final ResultadoApiChaves api,
        final LocalDate dataReferencia,
        final LocalDate periodoInicio,
        final LocalDate periodoFim,
        final boolean periodoFechado,
        final boolean permitirFallbackJanela
    ) throws SQLException {
        return compararEntidade(
            conexao,
            entidade,
            api,
            dataReferencia,
            periodoInicio,
            periodoFim,
            periodoFechado,
            permitirFallbackJanela,
            Optional.empty()
        );
    }

    ResultadoComparacao compararEntidade(
        final Connection conexao,
        final String entidade,
        final ResultadoApiChaves api,
        final LocalDate dataReferencia,
        final LocalDate periodoInicio,
        final LocalDate periodoFim,
        final boolean periodoFechado,
        final boolean permitirFallbackJanela,
        final Optional<String> executionUuidAncora
    ) throws SQLException {
        final Optional<JanelaExecucao> janelaEstruturada = executionUuidAncora
            .flatMap(executionUuid -> {
                try {
                    return repository.buscarJanelaEstruturadaDaExecucao(conexao, executionUuid, entidade);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        final Optional<JanelaExecucao> janelaOpt;
        if (janelaEstruturada.isPresent()) {
            janelaOpt = janelaEstruturada;
        } else {
            janelaOpt = repository.buscarUltimaJanelaCompletaDoDia(
                conexao,
                entidade,
                dataReferencia,
                periodoInicio,
                periodoFim,
                permitirFallbackJanela
            );
        }
        if (janelaOpt.isEmpty()) {
            return new ResultadoComparacao(
                entidade,
                api.apiBruto(),
                api.apiUnico(),
                api.invalidos(),
                0,
                api.apiUnico(),
                0,
                0,
                api.extracaoCompleta(),
                api.motivoInterrupcao(),
                "INCONCLUSIVO: sem janela COMPLETA compativel para comparar",
                0
            );
        }

        final JanelaExecucao janela = janelaOpt.get();
        final int idadeJanelaMinutos = janela.fim() == null
            ? 0
            : (int) Math.max(0, ChronoUnit.MINUTES.between(janela.fim(), RelogioSistema.agora()));
        final boolean usarFiltroEstritoDataExtracao =
            periodoFechado
                && janelaEstruturada.isPresent()
                && !ConstantesEntidades.USUARIOS_SISTEMA.equals(entidade);
        final Set<String> chavesBanco = repository.carregarChavesBancoNaJanela(
            conexao,
            entidade,
            janela,
            periodoInicio,
            periodoFim,
            usarFiltroEstritoDataExtracao
        );
        final Map<String, String> hashesBanco = repository.carregarHashesMetadataBancoNaJanela(
            conexao,
            entidade,
            janela,
            periodoInicio,
            periodoFim,
            usarFiltroEstritoDataExtracao
        );

        final Set<String> faltantes = new HashSet<>(api.chaves());
        faltantes.removeAll(chavesBanco);

        final Set<String> excedentes = new HashSet<>(chavesBanco);
        excedentes.removeAll(api.chaves());

        int excedentesTolerados = 0;
        if (ConstantesEntidades.FATURAS_GRAPHQL.equals(entidade)
            && !excedentes.isEmpty()
            && api.chavesToleradasNoBanco() != null
            && !api.chavesToleradasNoBanco().isEmpty()) {
            final int antes = excedentes.size();
            excedentes.removeIf(api.chavesToleradasNoBanco()::contains);
            excedentesTolerados = antes - excedentes.size();
        }

        final Set<String> chavesComparaveis = new HashSet<>(api.chaves());
        chavesComparaveis.retainAll(chavesBanco);
        final Set<String> divergenciasDados = new HashSet<>();
        for (final String chave : chavesComparaveis) {
            final String hashApi = api.hashesPorChave().get(chave);
            final String hashBanco = hashesBanco.get(chave);
            final Set<String> hashesAceitos = api.hashesAceitosPorChave() == null
                ? null
                : api.hashesAceitosPorChave().get(chave);
            final boolean hashCompativel;
            if (hashBanco == null) {
                hashCompativel = false;
            } else if (hashesAceitos != null && !hashesAceitos.isEmpty()) {
                hashCompativel = hashesAceitos.contains(hashBanco);
            } else {
                hashCompativel = hashApi != null && hashApi.equals(hashBanco);
            }
            if (!hashCompativel) {
                divergenciasDados.add(chave);
            }
        }

        int excedentesSuprimidosModoFechado = 0;
        int divergenciasSuprimidasModoFechado = 0;
        if (periodoFechado && !janela.alinhadaAoPeriodo()) {
            excedentesSuprimidosModoFechado = excedentes.size();
            divergenciasSuprimidasModoFechado = divergenciasDados.size();
            excedentes.clear();
            divergenciasDados.clear();
        }

        final StringBuilder detalhe = new StringBuilder(
            construirDetalheComparacao(janela, faltantes, excedentes, divergenciasDados, api.detalhe())
        );
        if (!api.extracaoCompleta()) {
            detalhe.append(" | api_extracao=INCOMPLETA");
            if (api.motivoInterrupcao() != null && !api.motivoInterrupcao().isBlank()) {
                detalhe.append(" | api_motivo=").append(api.motivoInterrupcao());
            }
            if (api.paginasProcessadas() > 0) {
                detalhe.append(" | api_paginas=").append(api.paginasProcessadas());
            }
        }
        if (!janela.alinhadaAoPeriodo()) {
            detalhe.append(" | origem_janela=FALLBACK_SEM_FILTRO_PERIODO");
        }
        if (periodoFechado && !janela.alinhadaAoPeriodo()) {
            detalhe.append(" | comparacao_modo=subconjunto_api");
            if (excedentesSuprimidosModoFechado > 0) {
                detalhe.append(" | excedentes_suprimidos=").append(excedentesSuprimidosModoFechado);
            }
            if (divergenciasSuprimidasModoFechado > 0) {
                detalhe.append(" | divergencias_suprimidas=").append(divergenciasSuprimidasModoFechado);
            }
        }
        if (excedentesTolerados > 0) {
            detalhe.append(" | excedentes_tolerados_referenciais=").append(excedentesTolerados);
        }
        if (janelaEstruturada.isPresent() && executionUuidAncora.isPresent()) {
            detalhe.append(" | origem_janela=EXECUTION_AUDIT");
            detalhe.append(" | execution_uuid=").append(executionUuidAncora.get());
        }
        if (usarFiltroEstritoDataExtracao) {
            detalhe.append(" | filtro_banco=janela_estruturada_estrita");
        }

        return new ResultadoComparacao(
            entidade,
            api.apiBruto(),
            api.apiUnico(),
            api.invalidos(),
            chavesBanco.size(),
            faltantes.size(),
            excedentes.size(),
            divergenciasDados.size(),
            api.extracaoCompleta(),
            api.motivoInterrupcao(),
            detalhe.toString(),
            idadeJanelaMinutos
        );
    }

    boolean somenteDivergenciaDadosTolerada(final ResultadoComparacao resultado) {
        if (resultado == null) {
            return false;
        }
        if (!resultado.apiCompleta()
            || resultado.faltantes() != 0
            || resultado.excedentes() != 0
            || resultado.divergenciasDados() <= 0) {
            return false;
        }
        return switch (resultado.entidade()) {
            case ConstantesEntidades.COTACOES,
                 ConstantesEntidades.LOCALIZACAO_CARGAS,
                 ConstantesEntidades.FRETES,
                 ConstantesEntidades.COLETAS -> true;
            case ConstantesEntidades.MANIFESTOS -> manifestosDivergenciaMarginalTolerada(resultado);
            default -> false;
        };
    }

    boolean completudeDinamicaTolerada(final ResultadoComparacao resultado) {
        if (resultado == null || !resultado.apiCompleta()) {
            return false;
        }
        return usuariosSistemaDinamicosTolerados(resultado)
            || fretesMarginalmenteDinamicos(resultado)
            || coletasMarginaisToleradas(resultado)
            || manifestosMarginaisTolerados(resultado)
            || cotacoesMarginalmenteDinamicas(resultado)
            || localizacaoCargasMarginalmenteDinamicas(resultado)
            || faturasPorClienteMarginalmenteDinamicas(resultado)
            || inventarioMarginalmenteDinamico(resultado)
            || faturasGraphqlMarginaisToleradas(resultado)
            || contasAPagarMarginaisToleradas(resultado);
    }

    private boolean usuariosSistemaDinamicosTolerados(final ResultadoComparacao resultado) {
        if (!ConstantesEntidades.USUARIOS_SISTEMA.equals(resultado.entidade())
            || periodoFechadoContexto
            || resultado.divergenciasDados() != 0
            || resultado.excedentes() != 0
            || resultado.faltantes() <= 0) {
            return false;
        }
        final int base = Math.max(resultado.apiUnico(), resultado.banco());
        final int limitePercentual = (int) Math.ceil(base * LIMIAR_USUARIOS_FALTANTES_PERCENTUAL_ABERTO);
        final int limite = Math.min(
            LIMIAR_USUARIOS_FALTANTES_ABSOLUTO_ABERTO,
            Math.max(10, limitePercentual)
        );
        return resultado.faltantes() <= limite;
    }

    private boolean coletasMarginaisToleradas(final ResultadoComparacao resultado) {
        if (periodoFechadoContexto || !ConstantesEntidades.COLETAS.equals(resultado.entidade())) {
            return false;
        }
        final int base = Math.max(resultado.apiUnico(), resultado.banco());
        final int limiteCompletude = calcularLimite(
            base,
            LIMIAR_COLETAS_VARIACAO_PERCENTUAL_ABERTO,
            2,
            LIMIAR_COLETAS_VARIACAO_ABSOLUTO_ABERTO
        );
        final int limiteDivergencia = calcularLimite(
            base,
            LIMIAR_COLETAS_DIVERGENCIA_PERCENTUAL_ABERTO,
            10,
            LIMIAR_COLETAS_DIVERGENCIA_ABSOLUTO_ABERTO
        );
        final int limiteDivergenciaComIdade = ajustarLimitePorIdade(resultado, limiteDivergencia, 5, 50);
        return resultado.faltantes() <= limiteCompletude
            && resultado.excedentes() <= limiteCompletude
            && resultado.divergenciasDados() <= limiteDivergenciaComIdade
            && Math.abs(resultado.apiUnico() - resultado.banco()) <= limiteCompletude;
    }

    private boolean manifestosMarginaisTolerados(final ResultadoComparacao resultado) {
        if (!ConstantesEntidades.MANIFESTOS.equals(resultado.entidade())
            || resultado.divergenciasDados() != 0) {
            return false;
        }
        final int delta = resultado.faltantes() + resultado.excedentes();
        if (delta <= 0) {
            return false;
        }
        final int base = Math.max(resultado.apiUnico(), resultado.banco());
        final double limitePercentualBase = periodoFechadoContexto
            ? LIMIAR_MANIFESTOS_VARIACAO_PERCENTUAL
            : LIMIAR_MANIFESTOS_VARIACAO_PERCENTUAL_ABERTO;
        final int limiteAbsoluto = periodoFechadoContexto
            ? LIMIAR_MANIFESTOS_VARIACAO_ABSOLUTO
            : LIMIAR_MANIFESTOS_VARIACAO_ABSOLUTO_ABERTO;
        final int limitePercentual = (int) Math.ceil(base * limitePercentualBase);
        final int limite = Math.max(2, Math.min(limiteAbsoluto, limitePercentual));
        final int limiteComIdade = ajustarLimitePorIdade(resultado, limite, 5, 35);
        return delta <= limiteComIdade;
    }

    private boolean cotacoesMarginalmenteDinamicas(final ResultadoComparacao resultado) {
        if (periodoFechadoContexto || !ConstantesEntidades.COTACOES.equals(resultado.entidade())) {
            return false;
        }
        final int base = Math.max(resultado.apiUnico(), resultado.banco());
        final int limiteCompletude = calcularLimite(
            base,
            LIMIAR_COTACOES_VARIACAO_PERCENTUAL_ABERTO,
            2,
            LIMIAR_COTACOES_VARIACAO_ABSOLUTO_ABERTO
        );
        final int limiteDivergencia = calcularLimite(
            base,
            LIMIAR_COTACOES_DIVERGENCIA_PERCENTUAL_ABERTO,
            2,
            LIMIAR_COTACOES_DIVERGENCIA_ABSOLUTO_ABERTO
        );
        return resultado.faltantes() <= limiteCompletude
            && resultado.excedentes() <= limiteCompletude
            && resultado.divergenciasDados() <= limiteDivergencia
            && Math.abs(resultado.apiUnico() - resultado.banco()) <= limiteCompletude;
    }

    private boolean manifestosDivergenciaMarginalTolerada(final ResultadoComparacao resultado) {
        return !periodoFechadoContexto
            && ConstantesEntidades.MANIFESTOS.equals(resultado.entidade())
            && resultado.faltantes() == 0
            && resultado.excedentes() == 0
            && resultado.apiUnico() == resultado.banco()
            && resultado.divergenciasDados() <= 1;
    }

    private boolean localizacaoCargasMarginalmenteDinamicas(final ResultadoComparacao resultado) {
        if (periodoFechadoContexto || !ConstantesEntidades.LOCALIZACAO_CARGAS.equals(resultado.entidade())) {
            return false;
        }
        final int base = Math.max(resultado.apiUnico(), resultado.banco());
        final int limiteCompletude = calcularLimite(
            base,
            LIMIAR_LOCALIZACAO_CARGAS_VARIACAO_PERCENTUAL_ABERTO,
            2,
            LIMIAR_LOCALIZACAO_CARGAS_VARIACAO_ABSOLUTO_ABERTO
        );
        final int limiteDivergencia = ajustarLimitePorIdade(
            resultado,
            calcularLimite(
                base,
                LIMIAR_LOCALIZACAO_CARGAS_DIVERGENCIA_PERCENTUAL_ABERTO,
                4,
                LIMIAR_LOCALIZACAO_CARGAS_DIVERGENCIA_ABSOLUTO_ABERTO
            ),
            8,
            80
        );
        return resultado.faltantes() <= limiteCompletude
            && resultado.excedentes() <= limiteCompletude
            && resultado.divergenciasDados() <= limiteDivergencia
            && Math.abs(resultado.apiUnico() - resultado.banco()) <= limiteCompletude;
    }

    private boolean fretesMarginalmenteDinamicos(final ResultadoComparacao resultado) {
        if (periodoFechadoContexto || !ConstantesEntidades.FRETES.equals(resultado.entidade())) {
            return false;
        }
        final int base = Math.max(resultado.apiUnico(), resultado.banco());
        final int limiteCompletude = calcularLimite(
            base,
            LIMIAR_FRETES_VARIACAO_PERCENTUAL_ABERTO,
            1,
            LIMIAR_FRETES_VARIACAO_ABSOLUTO_ABERTO
        );
        final int limiteDivergencia = ajustarLimitePorIdade(
            resultado,
            calcularLimite(
                base,
                LIMIAR_FRETES_DIVERGENCIA_PERCENTUAL_ABERTO,
                4,
                LIMIAR_FRETES_DIVERGENCIA_ABSOLUTO_ABERTO
            ),
            6,
            40
        );
        return resultado.faltantes() <= limiteCompletude
            && resultado.excedentes() <= limiteCompletude
            && resultado.divergenciasDados() <= limiteDivergencia
            && Math.abs(resultado.apiUnico() - resultado.banco()) <= limiteCompletude;
    }

    private boolean faturasPorClienteMarginalmenteDinamicas(final ResultadoComparacao resultado) {
        if (periodoFechadoContexto || !ConstantesEntidades.FATURAS_POR_CLIENTE.equals(resultado.entidade())) {
            return false;
        }
        final int base = Math.max(resultado.apiUnico(), resultado.banco());
        final int limiteCompletude = ajustarLimitePorIdade(
            resultado,
            calcularLimite(
                base,
                LIMIAR_FATURAS_POR_CLIENTE_VARIACAO_PERCENTUAL_ABERTO,
                2,
                LIMIAR_FATURAS_POR_CLIENTE_VARIACAO_ABSOLUTO_ABERTO
            ),
            6,
            60
        );
        final int limiteDivergencia = ajustarLimitePorIdade(
            resultado,
            calcularLimite(
                base,
                LIMIAR_FATURAS_POR_CLIENTE_DIVERGENCIA_PERCENTUAL_ABERTO,
                1,
                LIMIAR_FATURAS_POR_CLIENTE_DIVERGENCIA_ABSOLUTO_ABERTO
            ),
            4,
            40
        );
        return resultado.faltantes() <= limiteCompletude
            && resultado.excedentes() <= limiteCompletude
            && resultado.divergenciasDados() <= limiteDivergencia
            && Math.abs(resultado.banco() - resultado.apiUnico()) <= limiteCompletude;
    }

    private boolean inventarioMarginalmenteDinamico(final ResultadoComparacao resultado) {
        if (periodoFechadoContexto || !ConstantesEntidades.INVENTARIO.equals(resultado.entidade())) {
            return false;
        }
        final int base = Math.max(resultado.apiUnico(), resultado.banco());
        final int limiteCompletude = ajustarLimitePorIdade(
            resultado,
            calcularLimite(
                base,
                LIMIAR_INVENTARIO_VARIACAO_PERCENTUAL_ABERTO,
                2,
                LIMIAR_INVENTARIO_VARIACAO_ABSOLUTO_ABERTO
            ),
            2,
            10
        );
        final int limiteDivergencia = ajustarLimitePorIdade(
            resultado,
            calcularLimite(
                base,
                LIMIAR_INVENTARIO_DIVERGENCIA_PERCENTUAL_ABERTO,
                4,
                LIMIAR_INVENTARIO_DIVERGENCIA_ABSOLUTO_ABERTO
            ),
            4,
            20
        );
        return resultado.faltantes() <= limiteCompletude
            && resultado.excedentes() <= limiteCompletude
            && resultado.divergenciasDados() <= limiteDivergencia
            && Math.abs(resultado.apiUnico() - resultado.banco()) <= limiteCompletude;
    }

    private boolean faturasGraphqlMarginaisToleradas(final ResultadoComparacao resultado) {
        if (periodoFechadoContexto
            || !ConstantesEntidades.FATURAS_GRAPHQL.equals(resultado.entidade())
            || resultado.divergenciasDados() != 0) {
            return false;
        }
        final int base = Math.max(resultado.apiUnico(), resultado.banco());
        final int limiteCompletude = ajustarLimitePorIdade(
            resultado,
            calcularLimite(
                base,
                LIMIAR_FATURAS_GRAPHQL_VARIACAO_PERCENTUAL_ABERTO,
                2,
                LIMIAR_FATURAS_GRAPHQL_VARIACAO_ABSOLUTO_ABERTO
            ),
            2,
            10
        );
        return resultado.faltantes() <= limiteCompletude
            && resultado.excedentes() <= limiteCompletude
            && Math.abs(resultado.apiUnico() - resultado.banco()) <= limiteCompletude;
    }

    private boolean contasAPagarMarginaisToleradas(final ResultadoComparacao resultado) {
        if (periodoFechadoContexto || !ConstantesEntidades.CONTAS_A_PAGAR.equals(resultado.entidade())) {
            return false;
        }
        final int base = Math.max(resultado.apiUnico(), resultado.banco());
        final int limiteCompletude = calcularLimite(
            base,
            LIMIAR_CONTAS_A_PAGAR_VARIACAO_PERCENTUAL_ABERTO,
            1,
            LIMIAR_CONTAS_A_PAGAR_VARIACAO_ABSOLUTO_ABERTO
        );
        final int limiteDivergencia = calcularLimite(
            base,
            LIMIAR_CONTAS_A_PAGAR_DIVERGENCIA_PERCENTUAL_ABERTO,
            1,
            LIMIAR_CONTAS_A_PAGAR_DIVERGENCIA_ABSOLUTO_ABERTO
        );
        return resultado.faltantes() <= limiteCompletude
            && resultado.excedentes() <= limiteCompletude
            && resultado.divergenciasDados() <= limiteDivergencia
            && Math.abs(resultado.apiUnico() - resultado.banco()) <= limiteCompletude;
    }

    private int calcularLimite(final int base,
                               final double percentual,
                               final int minimo,
                               final int maximo) {
        final int limitePercentual = (int) Math.ceil(base * percentual);
        return Math.min(maximo, Math.max(minimo, limitePercentual));
    }

    private int ajustarLimitePorIdade(final ResultadoComparacao resultado,
                                      final int base,
                                      final int acrescimoPorBlocoDezMinutos,
                                      final int acrescimoMaximo) {
        if (resultado == null || resultado.idadeJanelaMinutos() <= 0 || acrescimoPorBlocoDezMinutos <= 0) {
            return base;
        }
        final int blocos = resultado.idadeJanelaMinutos() / 10;
        if (blocos <= 0) {
            return base;
        }
        final int acrescimo = Math.min(acrescimoMaximo, blocos * acrescimoPorBlocoDezMinutos);
        return base + acrescimo;
    }

    private String construirDetalheComparacao(
        final JanelaExecucao janela,
        final Set<String> faltantes,
        final Set<String> excedentes,
        final Set<String> divergenciasDados,
        final String detalheApi
    ) {
        final StringBuilder sb = new StringBuilder();
        sb.append("janela=[").append(janela.inicio()).append(" .. ").append(janela.fim()).append("]");
        if (detalheApi != null && !detalheApi.isBlank()) {
            sb.append(" | ").append(detalheApi);
        }
        if (!faltantes.isEmpty()) {
            sb.append(" | amostra_faltantes=").append(amostraChaves(faltantes));
        }
        if (!excedentes.isEmpty()) {
            sb.append(" | amostra_excedentes=").append(amostraChaves(excedentes));
        }
        if (!divergenciasDados.isEmpty()) {
            sb.append(" | amostra_divergencias_dados=").append(amostraChaves(divergenciasDados));
        }
        return sb.toString();
    }

    private String amostraChaves(final Set<String> chaves) {
        return chaves.stream()
            .sorted()
            .limit(AMOSTRA_MAX)
            .collect(Collectors.joining(",", "[", "]"));
    }
}
