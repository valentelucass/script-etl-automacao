/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/repository/PageAuditRepository.java
Classe  : PageAuditRepository (class)
Pacote  : br.com.extrator.db.repository
Modulo  : Repositorio de dados
Papel   : Implementa responsabilidade de page audit repository.

Conecta com:
- PageAuditEntity (db.entity)
- GerenciadorConexao (util.banco)

Fluxo geral:
1) Monta comandos SQL e parametros.
2) Executa operacoes de persistencia/consulta no banco.
3) Converte resultado para entidades de dominio.

Estrutura interna:
Metodos principais:
- inserir(...1 args): inclui registros no destino configurado.
Atributos-chave:
- logger: logger da classe para diagnostico.
[DOC-FILE-END]============================================================== */

package br.com.extrator.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.PageAuditEntity;
import br.com.extrator.util.banco.GerenciadorConexao;

/**
 * Repository para persistência de auditoria de páginas (PageAudit) no banco de dados.
 * 
 * ⚠️ NOTA: Este repositório não estende AbstractRepository porque PageAudit
 * tem uma operação específica (INSERT apenas, sem MERGE) e não segue o padrão
 * de outras entidades. Mantém uso direto de GerenciadorConexao para consistência
 * com LogExtracaoRepository.
 */
public class PageAuditRepository {
    private static final Logger logger = LoggerFactory.getLogger(PageAuditRepository.class);
    private static final String SQL =
        """
        INSERT INTO dbo.page_audit(\
        execution_uuid, run_uuid, template_id, page, per, janela_inicio, janela_fim, \
        req_hash, resp_hash, total_itens, id_key, id_min_num, id_max_num, \
        id_min_str, id_max_str, status_code, duracao_ms) \
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    /**
     * Insere um novo registro de auditoria de página.
     * 
     * @param a Entidade PageAuditEntity a ser inserida
     * @throws RuntimeException Se ocorrer erro ao inserir
     */
    public void inserir(final PageAuditEntity a) {
        if (a == null) {
            logger.warn("⚠️ Tentativa de inserir PageAuditEntity NULL");
            throw new IllegalArgumentException("PageAuditEntity não pode ser null");
        }
        
        try (Connection conn = GerenciadorConexao.obterConexao();
             PreparedStatement stmt = conn.prepareStatement(SQL)) {
            stmt.setString(1, a.getExecutionUuid());
            stmt.setString(2, a.getRunUuid());
            stmt.setInt(3, a.getTemplateId());
            stmt.setInt(4, a.getPage());
            stmt.setInt(5, a.getPer());
            stmt.setObject(6, a.getJanelaInicio());
            stmt.setObject(7, a.getJanelaFim());
            stmt.setString(8, a.getReqHash());
            stmt.setString(9, a.getRespHash());
            stmt.setInt(10, a.getTotalItens());
            stmt.setString(11, a.getIdKey());
            stmt.setObject(12, a.getIdMinNum());
            stmt.setObject(13, a.getIdMaxNum());
            stmt.setString(14, a.getIdMinStr());
            stmt.setString(15, a.getIdMaxStr());
            stmt.setInt(16, a.getStatusCode());
            stmt.setInt(17, a.getDuracaoMs());
            
            final int rowsAffected = stmt.executeUpdate();
            
            if (rowsAffected > 0) {
                logger.debug("✅ PageAudit inserido: execution_uuid={}, template_id={}, page={}", 
                    a.getExecutionUuid(), a.getTemplateId(), a.getPage());
            } else {
                logger.warn("⚠️ INSERT não afetou nenhuma linha para PageAudit: execution_uuid={}", 
                    a.getExecutionUuid());
            }
        } catch (final SQLException e) {
            logger.error("❌ Erro ao inserir PageAudit: execution_uuid={}, template_id={}, page={} - {}", 
                a.getExecutionUuid(), a.getTemplateId(), a.getPage(), e.getMessage(), e);
            throw new RuntimeException("Falha ao gravar page_audit", e);
        }
    }
}
