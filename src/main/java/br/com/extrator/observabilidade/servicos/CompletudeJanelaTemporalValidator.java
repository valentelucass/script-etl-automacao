package br.com.extrator.observabilidade.servicos;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/servicos/CompletudeJanelaTemporalValidator.java
Classe  :  (class)
Pacote  : br.com.extrator.observabilidade.servicos
Modulo  : Observabilidade - Servico
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import br.com.extrator.integracao.ClienteApiDataExport;
import br.com.extrator.integracao.ClienteApiGraphQL;
import br.com.extrator.suporte.banco.GerenciadorConexao;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

/**
 * Isola a validacao de janela temporal da auditoria de completude.
 */
final class CompletudeJanelaTemporalValidator {
    private final Logger logger;
    @SuppressWarnings("unused")
    private final ClienteApiGraphQL clienteApiGraphQL;
    @SuppressWarnings("unused")
    private final ClienteApiDataExport clienteApiDataExport;

    CompletudeJanelaTemporalValidator(
        final Logger logger,
        final ClienteApiGraphQL clienteApiGraphQL,
        final ClienteApiDataExport clienteApiDataExport
    ) {
        this.logger = logger;
        this.clienteApiGraphQL = clienteApiGraphQL;
        this.clienteApiDataExport = clienteApiDataExport;
    }

    Map<String, CompletudeValidator.StatusValidacao> validarJanelaTemporal(
        final Set<String> entidades,
        final LocalDate dataReferencia
    ) {
        logger.info("🔐 Iniciando validacao de janela temporal para data: {}", dataReferencia);

        final Map<String, CompletudeValidator.StatusValidacao> resultados = new HashMap<>();
        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            final Map<String, TimestampsExtracao> timestampsExtracao = buscarTimestampsExtracao(conexao, dataReferencia);
            for (final String entidade : entidades) {
                final TimestampsExtracao timestamps = timestampsExtracao.get(entidade);
                if (timestamps == null) {
                    logger.warn("⚠️ Nenhum log de extracao encontrado para {} na data {}", entidade, dataReferencia);
                    resultados.put(entidade, CompletudeValidator.StatusValidacao.ERRO);
                    continue;
                }
                resultados.put(entidade, validarJanelaTemporalEntidade(entidade, timestamps, dataReferencia));
            }
        } catch (final SQLException e) {
            logger.error("❌ Erro ao validar janela temporal: {}", e.getMessage(), e);
            for (final String entidade : entidades) {
                resultados.put(entidade, CompletudeValidator.StatusValidacao.ERRO);
            }
        }
        return resultados;
    }

    private Map<String, TimestampsExtracao> buscarTimestampsExtracao(
        final Connection conexao,
        final LocalDate dataReferencia
    ) throws SQLException {
        final String sql = """
            SELECT entidade, timestamp_inicio, timestamp_fim
            FROM log_extracoes
            WHERE CAST(timestamp_inicio AS DATE) = ?
            AND status_final = 'COMPLETO'
            ORDER BY timestamp_inicio DESC
            """;

        final Map<String, TimestampsExtracao> timestamps = new HashMap<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setDate(1, java.sql.Date.valueOf(dataReferencia));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final String entidade = rs.getString("entidade");
                    final java.sql.Timestamp inicio = rs.getTimestamp("timestamp_inicio");
                    final java.sql.Timestamp fim = rs.getTimestamp("timestamp_fim");
                    timestamps.put(entidade, new TimestampsExtracao(inicio, fim));
                }
            }
        }

        logger.info("📊 Encontrados timestamps para {} entidades na data {}", timestamps.size(), dataReferencia);
        return timestamps;
    }

    private CompletudeValidator.StatusValidacao validarJanelaTemporalEntidade(
        final String entidade,
        final TimestampsExtracao timestamps,
        final LocalDate dataReferencia
    ) {
        try {
            final int registrosDuranteExtracao = contarRegistrosDuranteJanela(entidade, timestamps, dataReferencia);
            if (registrosDuranteExtracao == 0) {
                logger.info("✅ Nenhum registro criado durante extracao de {} - janela temporal OK", entidade);
                return CompletudeValidator.StatusValidacao.OK;
            }
            logger.error(
                "❌ CRITICO: {} registros de {} foram criados durante a extracao",
                registrosDuranteExtracao,
                entidade
            );
            return CompletudeValidator.StatusValidacao.INCOMPLETO;
        } catch (final Exception e) {
            logger.error("❌ Erro ao validar janela temporal para {}: {}", entidade, e.getMessage(), e);
            return CompletudeValidator.StatusValidacao.ERRO;
        }
    }

    private int contarRegistrosDuranteJanela(
        final String entidade,
        final TimestampsExtracao timestamps,
        final LocalDate dataReferencia
    ) {
        return switch (entidade) {
            case ConstantesEntidades.FRETES, ConstantesEntidades.COLETAS, ConstantesEntidades.FATURAS_GRAPHQL ->
                contarRegistrosApiGraphQL(entidade, timestamps, dataReferencia);
            case ConstantesEntidades.MANIFESTOS,
                ConstantesEntidades.COTACOES,
                ConstantesEntidades.LOCALIZACAO_CARGAS,
                ConstantesEntidades.CONTAS_A_PAGAR,
                ConstantesEntidades.FATURAS_POR_CLIENTE ->
                contarRegistrosApiDataExport(entidade, timestamps, dataReferencia);
            default -> {
                logger.warn("⚠️ Entidade {} nao mapeada para validacao temporal", entidade);
                yield 0;
            }
        };
    }

    private int contarRegistrosApiGraphQL(
        final String entidade,
        final TimestampsExtracao timestamps,
        final LocalDate dataReferencia
    ) {
        logger.debug(
            "Contagem temporal via API GraphQL para {} ainda nao implementada (janela: {} - {}, data: {})",
            entidade,
            timestamps.getInicio(),
            timestamps.getFim(),
            dataReferencia
        );
        return 0;
    }

    private int contarRegistrosApiDataExport(
        final String entidade,
        final TimestampsExtracao timestamps,
        final LocalDate dataReferencia
    ) {
        logger.debug(
            "Contagem temporal via API Data Export para {} ainda nao implementada (janela: {} - {}, data: {})",
            entidade,
            timestamps.getInicio(),
            timestamps.getFim(),
            dataReferencia
        );
        return 0;
    }

    private static final class TimestampsExtracao {
        private final java.sql.Timestamp inicio;
        private final java.sql.Timestamp fim;

        private TimestampsExtracao(final java.sql.Timestamp inicio, final java.sql.Timestamp fim) {
            this.inicio = inicio;
            this.fim = fim;
        }

        private java.sql.Timestamp getInicio() {
            return inicio;
        }

        private java.sql.Timestamp getFim() {
            return fim;
        }
    }
}
