/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/repository/InvalidRecordAuditRepository.java
Classe  : InvalidRecordAuditRepository (class)
Pacote  : br.com.extrator.db.repository
Modulo  : Repositorio de dados
Papel   : Implementa responsabilidade de invalid record audit repository.

Conecta com:
- GerenciadorConexao (util.banco)

Fluxo geral:
1) Monta comandos SQL e parametros.
2) Executa operacoes de persistencia/consulta no banco.
3) Converte resultado para entidades de dominio.

Estrutura interna:
Metodos principais:
- registrarRegistroInvalido(...5 args): grava informacoes de auditoria/log.
- limitar(...2 args): realiza operacao relacionada a "limitar".
Atributos-chave:
- logger: logger da classe para diagnostico.
- LOCK: campo de estado para "lock".
- tabelaGarantida: campo de estado para "tabela garantida".
[DOC-FILE-END]============================================================== */

package br.com.extrator.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.util.banco.GerenciadorConexao;

/**
 * Repositorio para registrar registros invalidos descartados no pipeline ETL.
 * Mantem rastreabilidade de causa raiz sem silencionar falhas de origem.
 */
public class InvalidRecordAuditRepository {

    private static final Logger logger = LoggerFactory.getLogger(InvalidRecordAuditRepository.class);
    private static final Object LOCK = new Object();
    private static volatile boolean tabelaGarantida;

    public void registrarRegistroInvalido(final String entidade,
                                          final String reasonCode,
                                          final String detalhe,
                                          final String chaveReferencia,
                                          final String payloadJson) {
        final String sql = """
            INSERT INTO dbo.etl_invalid_records
            (created_at, entidade, reason_code, detalhe, chave_referencia, payload_json)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = GerenciadorConexao.obterConexao()) {
            garantirTabela(conn);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
                stmt.setString(2, limitar(entidade, 80));
                stmt.setString(3, limitar(reasonCode, 50));
                stmt.setString(4, limitar(detalhe, 500));
                stmt.setString(5, limitar(chaveReferencia, 150));
                stmt.setString(6, payloadJson);
                stmt.executeUpdate();
            }
        } catch (final SQLException e) {
            logger.error("Falha ao registrar registro invalido (entidade={}, reasonCode={}): {}",
                entidade, reasonCode, e.getMessage(), e);
        }
    }

    private void garantirTabela(final Connection conn) throws SQLException {
        if (tabelaGarantida) {
            return;
        }
        synchronized (LOCK) {
            if (tabelaGarantida) {
                return;
            }
            final String ddl = """
                IF OBJECT_ID(N'dbo.etl_invalid_records', N'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.etl_invalid_records (
                        id BIGINT IDENTITY(1,1) PRIMARY KEY,
                        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
                        entidade NVARCHAR(80) NOT NULL,
                        reason_code NVARCHAR(50) NOT NULL,
                        detalhe NVARCHAR(500) NULL,
                        chave_referencia NVARCHAR(150) NULL,
                        payload_json NVARCHAR(MAX) NULL
                    );
                    CREATE INDEX IX_etl_invalid_records_entidade_data
                        ON dbo.etl_invalid_records (entidade, created_at DESC);
                END
                """;
            try (PreparedStatement stmt = conn.prepareStatement(ddl)) {
                stmt.execute();
            }
            tabelaGarantida = true;
        }
    }

    private String limitar(final String valor, final int max) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        if (texto.length() > max) {
            texto = texto.substring(0, max);
        }
        return texto;
    }
}

