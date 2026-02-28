/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/repository/LogExtracaoRepository.java
Classe  : LogExtracaoRepository (class)
Pacote  : br.com.extrator.db.repository
Modulo  : Repositorio de dados
Papel   : Implementa responsabilidade de log extracao repository.

Conecta com:
- LogExtracaoEntity (db.entity)
- StatusExtracao (db.entity.LogExtracaoEntity)
- GerenciadorConexao (util.banco)

Fluxo geral:
1) Monta comandos SQL e parametros.
2) Executa operacoes de persistencia/consulta no banco.
3) Converte resultado para entidades de dominio.

Estrutura interna:
Metodos principais:
- gravarLogExtracao(...1 args): realiza operacao relacionada a "gravar log extracao".
- buscarUltimoLogPorEntidade(...1 args): consulta e retorna dados conforme criterio.
- buscarUltimoLogPorEntidadeNoIntervaloExecucao(...3 args): consulta e retorna dados conforme criterio.
- buscarUltimaExtracaoPorPeriodo(...3 args): consulta e retorna dados conforme criterio.
Atributos-chave:
- logger: logger da classe para diagnostico.
[DOC-FILE-END]============================================================== */

package br.com.extrator.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.LogExtracaoEntity;
import br.com.extrator.db.entity.LogExtracaoEntity.StatusExtracao;
import br.com.extrator.util.banco.GerenciadorConexao;

/**
 * Repository para gerenciar logs de extração
 */
public class LogExtracaoRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(LogExtracaoRepository.class);
    
    /**
     * Grava um novo log de extração
     */
    public void gravarLogExtracao(final LogExtracaoEntity logExtracao) {
        final String sql = """
            INSERT INTO dbo.log_extracoes
            (entidade, timestamp_inicio, timestamp_fim, status_final, registros_extraidos, paginas_processadas, mensagem)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        
        try (Connection conn = GerenciadorConexao.obterConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, logExtracao.getEntidade());
            stmt.setTimestamp(2, Timestamp.valueOf(logExtracao.getTimestampInicio()));
            stmt.setTimestamp(3, Timestamp.valueOf(logExtracao.getTimestampFim()));
            stmt.setString(4, logExtracao.getStatusFinal().getValor());
            stmt.setInt(5, logExtracao.getRegistrosExtraidos());
            stmt.setInt(6, logExtracao.getPaginasProcessadas());
            stmt.setString(7, logExtracao.getMensagem());
            
            final int linhasAfetadas = stmt.executeUpdate();
            
            if (linhasAfetadas > 0) {
                logger.debug("Log de extração gravado: entidade={}, status={}, registros={}", 
                    logExtracao.getEntidade(), logExtracao.getStatusFinal(), logExtracao.getRegistrosExtraidos());
            }
            
        } catch (final SQLException e) {
            logger.error("Erro ao gravar log de extração para entidade {}: {}", 
                logExtracao.getEntidade(), e.getMessage(), e);
            throw new RuntimeException("Falha ao gravar log de extração", e);
        }
    }
    
    /**
     * Busca o último log de extração para uma entidade
     */
    public Optional<LogExtracaoEntity> buscarUltimoLogPorEntidade(final String entidade) {
        final String sql = """
            SELECT TOP 1 id, entidade, timestamp_inicio, timestamp_fim, status_final,
                   registros_extraidos, paginas_processadas, mensagem
            FROM dbo.log_extracoes
            WHERE entidade = ?
            ORDER BY timestamp_fim DESC
            """;
        
        try (Connection conn = GerenciadorConexao.obterConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, entidade);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final LogExtracaoEntity log = new LogExtracaoEntity();
                    log.setId(rs.getLong("id"));
                    log.setEntidade(rs.getString("entidade"));
                    log.setTimestampInicio(rs.getTimestamp("timestamp_inicio").toLocalDateTime());
                    log.setTimestampFim(rs.getTimestamp("timestamp_fim").toLocalDateTime());
                    log.setStatusFinal(StatusExtracao.fromString(rs.getString("status_final")));
                    log.setRegistrosExtraidos(rs.getInt("registros_extraidos"));
                    log.setPaginasProcessadas(rs.getInt("paginas_processadas"));
                    log.setMensagem(rs.getString("mensagem"));
                    
                    return Optional.of(log);
                }
            }
            
        } catch (final SQLException e) {
            logger.error("Erro ao buscar último log para entidade {}: {}", entidade, e.getMessage(), e);
        }
        
        return Optional.empty();
    }

    /**
     * Busca o último log de extração de uma entidade dentro de uma janela de execução.
     * Útil para correlacionar logs do bloco atual sem risco de capturar execução antiga.
     *
     * @param entidade Nome da entidade
     * @param inicioExecucao Início da janela de execução
     * @param fimExecucao Fim da janela de execução
     * @return Optional com o último log encontrado na janela
     */
    public Optional<LogExtracaoEntity> buscarUltimoLogPorEntidadeNoIntervaloExecucao(final String entidade,
                                                                                       final LocalDateTime inicioExecucao,
                                                                                       final LocalDateTime fimExecucao) {
        final String sql = """
            SELECT TOP 1 id, entidade, timestamp_inicio, timestamp_fim, status_final,
                   registros_extraidos, paginas_processadas, mensagem
            FROM dbo.log_extracoes
            WHERE entidade = ?
              AND timestamp_fim >= ?
              AND timestamp_fim <= ?
            ORDER BY timestamp_fim DESC
            """;

        try (Connection conn = GerenciadorConexao.obterConexao();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, entidade);
            stmt.setTimestamp(2, Timestamp.valueOf(inicioExecucao));
            stmt.setTimestamp(3, Timestamp.valueOf(fimExecucao));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(criarLogExtracaoEntity(rs));
                }
            }

        } catch (final SQLException e) {
            logger.warn("Erro ao buscar log da entidade {} na janela {} até {}: {}",
                entidade, inicioExecucao, fimExecucao, e.getMessage());
        }

        return Optional.empty();
    }
    
    /**
     * Busca a última extração que tenha extraído dados do mesmo período.
     * Verifica logs onde a mensagem contenha informações sobre o período extraído,
     * ou busca o último log da entidade se não houver informação específica.
     * 
     * @param entidade Nome da entidade
     * @param dataInicio Data de início do período solicitado
     * @param dataFim Data de fim do período solicitado
     * @return Optional com o log da última extração do mesmo período, ou empty se não encontrado
     */
    public Optional<LogExtracaoEntity> buscarUltimaExtracaoPorPeriodo(final String entidade,
                                                                       final LocalDate dataInicio,
                                                                       final LocalDate dataFim) {
        // Primeiro, tentar buscar logs onde a mensagem contenha o período exato
        // Formato esperado na mensagem: "Período: YYYY-MM-DD a YYYY-MM-DD"
        final String sqlComPeriodo = """
            SELECT TOP 1 id, entidade, timestamp_inicio, timestamp_fim, status_final,
                   registros_extraidos, paginas_processadas, mensagem
            FROM dbo.log_extracoes
            WHERE entidade = ?
              AND (mensagem LIKE ? OR mensagem LIKE ?)
            ORDER BY timestamp_fim DESC
            """;
        
        // Formatar datas para busca na mensagem (formato YYYY-MM-DD)
        final String dataInicioStr = dataInicio.toString();
        final String dataFimStr = dataFim.toString();
        // Padrão esperado: "Período: YYYY-MM-DD a YYYY-MM-DD"
        final String padraoCompleto = "%Período: " + dataInicioStr + " a " + dataFimStr + "%";
        // Padrão alternativo: apenas as datas
        final String padraoSimples = "%" + dataInicioStr + "%" + dataFimStr + "%";
        
        try (Connection conn = GerenciadorConexao.obterConexao();
             PreparedStatement stmt = conn.prepareStatement(sqlComPeriodo)) {
            
            stmt.setString(1, entidade);
            stmt.setString(2, padraoCompleto);
            stmt.setString(3, padraoSimples);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final LogExtracaoEntity log = criarLogExtracaoEntity(rs);
                    logger.debug("Encontrado log com período específico para {}: {}", entidade, log.getTimestampFim());
                    return Optional.of(log);
                }
            }
        } catch (final SQLException e) {
            logger.warn("Erro ao buscar log com período específico para {}: {}", entidade, e.getMessage());
        }
        
        // Se não encontrou com período específico, buscar logs que contenham
        // QUALQUER uma das datas do período (início OU fim)
        final String sqlComUmaDatas = """
            SELECT TOP 1 id, entidade, timestamp_inicio, timestamp_fim, status_final,
                   registros_extraidos, paginas_processadas, mensagem
            FROM dbo.log_extracoes
            WHERE entidade = ?
              AND (mensagem LIKE ? OR mensagem LIKE ?)
            ORDER BY timestamp_fim DESC
            """;
        
        try (Connection conn = GerenciadorConexao.obterConexao();
             PreparedStatement stmt = conn.prepareStatement(sqlComUmaDatas)) {
            
            stmt.setString(1, entidade);
            stmt.setString(2, "%" + dataInicioStr + "%");
            stmt.setString(3, "%" + dataFimStr + "%");
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final LogExtracaoEntity log = criarLogExtracaoEntity(rs);
                    logger.debug("Encontrado log com data parcial para {}: {}", entidade, log.getTimestampFim());
                    return Optional.of(log);
                }
            }
        } catch (final SQLException e) {
            logger.warn("Erro ao buscar log com data parcial para {}: {}", entidade, e.getMessage());
        }
        
        // Se não encontrou com período específico, buscar último log da entidade
        // que foi executado recentemente (últimas 24 horas)
        // Apenas para extrações de intervalo, não para extrações diárias
        final LocalDateTime limiteRecente = LocalDateTime.now().minusHours(24);
        final String sqlUltimoRecente = """
            SELECT TOP 1 id, entidade, timestamp_inicio, timestamp_fim, status_final,
                   registros_extraidos, paginas_processadas, mensagem
            FROM dbo.log_extracoes
            WHERE entidade = ?
              AND timestamp_fim >= ?
              AND mensagem LIKE '%Período:%'
            ORDER BY timestamp_fim DESC
            """;
        
        try (Connection conn = GerenciadorConexao.obterConexao();
             PreparedStatement stmt = conn.prepareStatement(sqlUltimoRecente)) {
            
            stmt.setString(1, entidade);
            stmt.setTimestamp(2, Timestamp.valueOf(limiteRecente));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final LogExtracaoEntity log = criarLogExtracaoEntity(rs);
                    logger.debug("Encontrado log recente de intervalo para {}: {}", entidade, log.getTimestampFim());
                    return Optional.of(log);
                }
            }
        } catch (final SQLException e) {
            logger.warn("Erro ao buscar último log recente para {}: {}", entidade, e.getMessage());
        }
        
        return Optional.empty();
    }
    
    /**
     * Método auxiliar para criar LogExtracaoEntity a partir de ResultSet
     */
    private LogExtracaoEntity criarLogExtracaoEntity(final ResultSet rs) throws SQLException {
        final LogExtracaoEntity log = new LogExtracaoEntity();
        log.setId(rs.getLong("id"));
        log.setEntidade(rs.getString("entidade"));
        log.setTimestampInicio(rs.getTimestamp("timestamp_inicio").toLocalDateTime());
        log.setTimestampFim(rs.getTimestamp("timestamp_fim").toLocalDateTime());
        log.setStatusFinal(StatusExtracao.fromString(rs.getString("status_final")));
        log.setRegistrosExtraidos(rs.getInt("registros_extraidos"));
        log.setPaginasProcessadas(rs.getInt("paginas_processadas"));
        log.setMensagem(rs.getString("mensagem"));
        return log;
    }
    
}
