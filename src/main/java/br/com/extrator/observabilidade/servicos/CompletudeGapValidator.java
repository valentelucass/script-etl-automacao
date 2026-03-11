package br.com.extrator.observabilidade.servicos;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/servicos/CompletudeGapValidator.java
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

import org.slf4j.Logger;

import br.com.extrator.suporte.banco.GerenciadorConexao;

/**
 * Isola a validacao de gaps sequenciais de ocorrencias.
 */
final class CompletudeGapValidator {
    private final Logger logger;

    CompletudeGapValidator(final Logger logger) {
        this.logger = logger;
    }

    CompletudeValidator.StatusValidacao validarGapsOcorrencias(final LocalDate dataReferencia) {
        logger.info("🔍 Iniciando validacao de gaps para ocorrencias...");

        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            if (!tabelaOcorrenciasExiste(conexao)) {
                logger.warn("⚠️ Tabela 'ocorrencias' nao encontrada - validacao de gaps ignorada");
                return CompletudeValidator.StatusValidacao.OK;
            }

            if (!verificarIdsSequenciais(conexao, "ocorrencias")) {
                logger.warn("⚠️ IDs das ocorrencias nao sao sequenciais - validacao de gaps nao aplicavel");
                return CompletudeValidator.StatusValidacao.OK;
            }

            return detectarGapsSequenciais(conexao, "ocorrencias", dataReferencia);
        } catch (final SQLException e) {
            logger.error("❌ Erro ao validar gaps nas ocorrencias: {}", e.getMessage(), e);
            return CompletudeValidator.StatusValidacao.ERRO;
        }
    }

    private boolean tabelaOcorrenciasExiste(final Connection conexao) throws SQLException {
        final String sqlExisteTabela = """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = 'ocorrencias' AND TABLE_SCHEMA = 'dbo'
        """;
        try (PreparedStatement stmt = conexao.prepareStatement(sqlExisteTabela);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean verificarIdsSequenciais(final Connection conexao, final String nomeTabela) throws SQLException {
        final String sql = """
            WITH ids_ordenados AS (
                SELECT id, ROW_NUMBER() OVER (ORDER BY id) as posicao
                FROM %s
                WHERE data_extracao >= DATEADD(day, -7, GETDATE())
            ),
            gaps AS (
                SELECT COUNT(*) as total_gaps
                FROM ids_ordenados
                WHERE id != (SELECT MIN(id) FROM ids_ordenados) + posicao - 1
            )
            SELECT CASE WHEN total_gaps = 0 THEN 1 ELSE 0 END as ids_sequenciais
            FROM gaps
            """.formatted(nomeTabela);

        try (PreparedStatement stmt = conexao.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                final boolean sequencial = rs.getInt("ids_sequenciais") == 1;
                logger.info(
                    "📊 Analise de sequencialidade para {}: {}",
                    nomeTabela,
                    sequencial ? "IDs sao sequenciais" : "IDs tem gaps/pulos"
                );
                return sequencial;
            }
            return false;
        }
    }

    private CompletudeValidator.StatusValidacao detectarGapsSequenciais(
        final Connection conexao,
        final String nomeTabela,
        final LocalDate dataReferencia
    ) throws SQLException {
        final String sql = """
            WITH ids_esperados AS (
                SELECT MIN(id) + n.number as id_esperado
                FROM %s,
                     (SELECT TOP ((SELECT MAX(id) - MIN(id) + 1 FROM %s WHERE data_extracao >= ?))
                             ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) - 1 as number
                      FROM sys.objects a CROSS JOIN sys.objects b) n
                WHERE data_extracao >= ?
            ),
            gaps AS (
                SELECT ie.id_esperado
                FROM ids_esperados ie
                LEFT JOIN %s o ON ie.id_esperado = o.id AND o.data_extracao >= ?
                WHERE o.id IS NULL
            )
            SELECT COUNT(*) as total_gaps
            FROM gaps
            """.formatted(nomeTabela, nomeTabela, nomeTabela);

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            final java.sql.Date sqlDate = java.sql.Date.valueOf(dataReferencia);
            stmt.setDate(1, sqlDate);
            stmt.setDate(2, sqlDate);
            stmt.setDate(3, sqlDate);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final int totalGaps = rs.getInt("total_gaps");
                    if (totalGaps == 0) {
                        logger.info("✅ Nenhum gap detectado nos IDs de {}", nomeTabela);
                        return CompletudeValidator.StatusValidacao.OK;
                    }
                    logger.warn(
                        "⚠️ Detectados {} gaps nos IDs de {} - possivel perda de dados",
                        totalGaps,
                        nomeTabela
                    );
                    return CompletudeValidator.StatusValidacao.INCOMPLETO;
                }
                return CompletudeValidator.StatusValidacao.ERRO;
            }
        }
    }
}
