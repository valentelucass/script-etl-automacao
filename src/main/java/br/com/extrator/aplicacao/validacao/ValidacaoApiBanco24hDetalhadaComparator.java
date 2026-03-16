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
- compararEntidade(Connection, String, ResultadoApiChaves, LocalDate, LocalDate, LocalDate, boolean, boolean): comparacao principal.
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
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import br.com.extrator.suporte.validacao.ConstantesEntidades;

final class ValidacaoApiBanco24hDetalhadaComparator {
    private static final int AMOSTRA_MAX = 15;
    private static final int LIMIAR_USUARIOS_FALTANTES_ABSOLUTO = 100;
    private static final double LIMIAR_USUARIOS_FALTANTES_PERCENTUAL = 0.005d;
    private static final int LIMIAR_MANIFESTOS_VARIACAO_ABSOLUTO = 5;
    private static final double LIMIAR_MANIFESTOS_VARIACAO_PERCENTUAL = 0.02d;

    private final ValidacaoApiBanco24hDetalhadaRepository repository;

    ValidacaoApiBanco24hDetalhadaComparator(final ValidacaoApiBanco24hDetalhadaRepository repository) {
        this.repository = repository;
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
        final Optional<JanelaExecucao> janelaOpt = repository.buscarUltimaJanelaCompletaDoDia(
            conexao,
            entidade,
            dataReferencia,
            periodoInicio,
            periodoFim,
            permitirFallbackJanela
        );
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
                "INCONCLUSIVO: sem janela COMPLETA compativel para comparar"
            );
        }

        final JanelaExecucao janela = janelaOpt.get();
        final Set<String> chavesBanco = repository.carregarChavesBancoNaJanela(conexao, entidade, janela);
        final Map<String, String> hashesBanco = repository.carregarHashesMetadataBancoNaJanela(conexao, entidade, janela);

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
            detalhe.toString()
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
            default -> false;
        };
    }

    boolean completudeDinamicaTolerada(final ResultadoComparacao resultado) {
        if (resultado == null || !resultado.apiCompleta()) {
            return false;
        }
        return usuariosSistemaDinamicosTolerados(resultado)
            || coletasMarginaisToleradas(resultado)
            || manifestosMarginaisTolerados(resultado)
            || localizacaoCargasMarginalmenteDinamicas(resultado)
            || faturasPorClienteMarginalmenteDinamicas(resultado)
            || contasAPagarMarginaisToleradas(resultado);
    }

    private boolean usuariosSistemaDinamicosTolerados(final ResultadoComparacao resultado) {
        if (!ConstantesEntidades.USUARIOS_SISTEMA.equals(resultado.entidade())
            || resultado.divergenciasDados() != 0
            || resultado.excedentes() != 0
            || resultado.faltantes() <= 0
            || resultado.apiUnico() < resultado.banco()) {
            return false;
        }
        final int base = Math.max(resultado.apiUnico(), resultado.banco());
        final int limitePercentual = (int) Math.ceil(base * LIMIAR_USUARIOS_FALTANTES_PERCENTUAL);
        final int limite = Math.min(LIMIAR_USUARIOS_FALTANTES_ABSOLUTO, Math.max(10, limitePercentual));
        return resultado.faltantes() <= limite;
    }

    private boolean coletasMarginaisToleradas(final ResultadoComparacao resultado) {
        return ConstantesEntidades.COLETAS.equals(resultado.entidade())
            && resultado.faltantes() <= 2
            && resultado.excedentes() <= 1
            && Math.abs(resultado.apiUnico() - resultado.banco()) <= 2;
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
        final int limitePercentual = (int) Math.ceil(base * LIMIAR_MANIFESTOS_VARIACAO_PERCENTUAL);
        final int limite = Math.max(2, Math.min(LIMIAR_MANIFESTOS_VARIACAO_ABSOLUTO, limitePercentual));
        return delta <= limite;
    }

    private boolean localizacaoCargasMarginalmenteDinamicas(final ResultadoComparacao resultado) {
        return ConstantesEntidades.LOCALIZACAO_CARGAS.equals(resultado.entidade())
            && resultado.faltantes() == 0
            && resultado.excedentes() <= 1
            && resultado.banco() >= resultado.apiUnico()
            && (resultado.banco() - resultado.apiUnico()) <= 1;
    }

    private boolean faturasPorClienteMarginalmenteDinamicas(final ResultadoComparacao resultado) {
        return ConstantesEntidades.FATURAS_POR_CLIENTE.equals(resultado.entidade())
            && resultado.faltantes() == 0
            && resultado.excedentes() <= 1
            && resultado.divergenciasDados() <= 1
            && resultado.banco() >= resultado.apiUnico()
            && (resultado.banco() - resultado.apiUnico()) <= 1;
    }

    private boolean contasAPagarMarginaisToleradas(final ResultadoComparacao resultado) {
        return ConstantesEntidades.CONTAS_A_PAGAR.equals(resultado.entidade())
            && resultado.divergenciasDados() == 0
            && resultado.excedentes() == 0
            && resultado.faltantes() <= 1
            && Math.abs(resultado.apiUnico() - resultado.banco()) <= 1;
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
