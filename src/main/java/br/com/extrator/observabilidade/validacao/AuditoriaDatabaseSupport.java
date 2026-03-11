package br.com.extrator.observabilidade.validacao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/validacao/AuditoriaDatabaseSupport.java
Classe  :  (class)
Pacote  : br.com.extrator.observabilidade.validacao
Modulo  : Observabilidade - Validacao
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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.observabilidade.modelos.ResultadoValidacaoEntidade;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

final class AuditoriaDatabaseSupport {
    private static final Logger logger = LoggerFactory.getLogger(AuditoriaDatabaseSupport.class);

    private final Map<String, Boolean> cacheValidacaoColunas = new HashMap<>();

    boolean verificarExistenciaTabela(final Connection conexao, final String nomeTabela) throws SQLException {
        final String sql = """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_NAME = ? AND TABLE_SCHEMA = 'dbo'
            """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, nomeTabela);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    boolean validarColunaExiste(final Connection conexao,
                                final String nomeTabela,
                                final String nomeColuna) throws SQLException {
        final String chaveCache = nomeTabela + "." + nomeColuna;
        if (cacheValidacaoColunas.containsKey(chaveCache)) {
            final boolean existe = cacheValidacaoColunas.get(chaveCache);
            logger.debug("Cache hit para coluna {}.{}: {}", nomeTabela, nomeColuna, existe);
            return existe;
        }

        final String sql = """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_NAME = ? AND COLUMN_NAME = ? AND TABLE_SCHEMA = 'dbo'
            """;

        logger.debug("Validando existencia da coluna {}.{}", nomeTabela, nomeColuna);
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, nomeTabela);
            stmt.setString(2, nomeColuna);
            try (ResultSet rs = stmt.executeQuery()) {
                final boolean existe = rs.next() && rs.getInt(1) > 0;
                cacheValidacaoColunas.put(chaveCache, existe);
                logger.debug("Coluna {}.{} existe: {}", nomeTabela, nomeColuna, existe);
                return existe;
            }
        }
    }

    long contarRegistrosPorDataExtracao(final Connection conexao,
                                        final String nomeEntidade,
                                        final Instant dataInicio,
                                        final Instant dataFim,
                                        final ResultadoValidacaoEntidade resultado) throws SQLException {
        final String nomeTabela = mapearNomeTabela(nomeEntidade);
        final String sql;

        if (ConstantesEntidades.CONTAS_A_PAGAR.equals(nomeEntidade)) {
            sql = String.format("""
                SELECT COUNT(*)
                FROM %s
                WHERE issue_date >= CAST(DATEADD(day, -1, GETDATE()) AS DATE)
                  AND issue_date <= CAST(GETDATE() AS DATE)
                """, nomeTabela);

            logger.debug("Query executada (CONTAS_A_PAGAR usando issue_date): {}", sql);
            if (resultado != null) {
                resultado.setColunaUtilizada("issue_date (contagem banco - ultimas 24h)");
                resultado.setQueryExecutada(sql);
            }

            try (PreparedStatement stmt = conexao.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                final long count = rs.next() ? rs.getLong(1) : 0;
                logger.debug("Resultado: {} registros encontrados", count);
                return count;
            }
        }

        sql = String.format("""
            SELECT COUNT(*)
            FROM %s
            WHERE data_extracao >= CAST(? AS DATETIME2) AND data_extracao < CAST(? AS DATETIME2)
            """, nomeTabela);

        logger.debug("Query executada: {}", sql);
        logger.debug("Parametros: dataInicio={}, dataFim={}", dataInicio, dataFim);

        if (resultado != null) {
            resultado.setColunaUtilizada("data_extracao (contagem banco)");
            resultado.setQueryExecutada(sql);
        }

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.from(dataInicio));
            stmt.setTimestamp(2, Timestamp.from(dataFim));
            try (ResultSet rs = stmt.executeQuery()) {
                final long count = rs.next() ? rs.getLong(1) : 0;
                logger.debug("Resultado: {} registros encontrados", count);
                return count;
            }
        }
    }

    void investigarCausaRaizZeroRegistros(final Connection conexao,
                                          final String nomeEntidade,
                                          final ResultadoValidacaoEntidade resultado) throws SQLException {
        final String sqlTotal = String.format("SELECT COUNT(*) FROM %s", nomeEntidade);
        try (PreparedStatement stmt = conexao.prepareStatement(sqlTotal);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                final long totalGeral = rs.getLong(1);
                if (totalGeral == 0) {
                    resultado.adicionarObservacao("Tabela esta vazia");
                    logger.warn("Tabela {} esta completamente vazia", nomeEntidade);
                } else {
                    resultado.adicionarObservacao(
                        String.format("Tabela tem %d registros mas nenhum no periodo especificado", totalGeral)
                    );
                    logger.warn("Tabela {} tem {} registros mas nenhum no periodo auditado", nomeEntidade, totalGeral);
                }
            }
        }
    }

    long contarRegistrosComNulos(final Connection conexao, final String nomeEntidade) throws SQLException {
        final Map<String, String> camposCriticos = Map.of(
            ConstantesEntidades.COTACOES, "sequence_code IS NULL OR total_value IS NULL",
            ConstantesEntidades.COLETAS, "id IS NULL",
            ConstantesEntidades.CONTAS_A_PAGAR, "sequence_code IS NULL OR document_number IS NULL",
            ConstantesEntidades.FATURAS_POR_CLIENTE, "unique_id IS NULL OR numero_fatura IS NULL",
            ConstantesEntidades.FRETES, "id IS NULL",
            ConstantesEntidades.MANIFESTOS, "sequence_code IS NULL",
            ConstantesEntidades.LOCALIZACAO_CARGAS, "sequence_number IS NULL"
        );

        final String condicaoNulos = camposCriticos.getOrDefault(nomeEntidade, "id IS NULL");
        final String sql = String.format("SELECT COUNT(*) FROM %s WHERE %s", nomeEntidade, condicaoNulos);
        try (PreparedStatement stmt = conexao.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    Instant obterDataUltimaExtracao(final Connection conexao, final String nomeEntidade) throws SQLException {
        final String sql = String.format("SELECT MAX(data_extracao) FROM %s", nomeEntidade);
        logger.debug("Obtendo ultima extracao para {}: {}", nomeEntidade, sql);
        try (PreparedStatement stmt = conexao.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                final Timestamp timestamp = rs.getTimestamp(1);
                if (timestamp != null) {
                    final Instant dataUltimaExtracao = timestamp.toInstant();
                    logger.debug("Ultima extracao para {}: {}", nomeEntidade, dataUltimaExtracao);
                    return dataUltimaExtracao;
                }
            }
            logger.debug("Nenhuma extracao encontrada para {}", nomeEntidade);
            return null;
        }
    }

    private String mapearNomeTabela(final String nomeEntidade) {
        return switch (nomeEntidade) {
            case "faturas_a_pagar_data_export" -> ConstantesEntidades.CONTAS_A_PAGAR;
            default -> nomeEntidade;
        };
    }
}
