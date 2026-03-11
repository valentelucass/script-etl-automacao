/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/validacao/VerificarTimezoneComando.java
Classe  : VerificarTimezoneComando (class)
Pacote  : br.com.extrator.comandos.cli.validacao
Modulo  : Comando CLI (validacao)
Papel   : Implementa responsabilidade de verificar timezone comando.

Conecta com:
- Comando (comandos.base)
- GerenciadorConexao (util.banco)

Fluxo geral:
1) Executa validacoes de acesso, timestamps e consistencia.
2) Compara API versus banco quando aplicavel.
3) Emite resultado de qualidade para operacao.

Estrutura interna:
Metodos principais:
- executar(...1 args): executa o fluxo principal desta responsabilidade.
Atributos-chave:
- logger: logger da classe para diagnostico.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.cli.validacao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.comandos.cli.base.Comando;
import br.com.extrator.suporte.banco.GerenciadorConexao;

/**
 * Comando para verificar o timezone do SQL Server e diagnosticar problemas de timestamp
 */
public class VerificarTimezoneComando implements Comando {
    
    private static final Logger logger = LoggerFactory.getLogger(VerificarTimezoneComando.class);
    
    @Override
    public void executar(String[] args) {
        logger.info("🕐 Iniciando verificação de timezone do SQL Server...");
        
        try (Connection conn = GerenciadorConexao.obterConexao()) {
            verificarTimezone(conn);
        } catch (SQLException e) {
            logger.error("❌ Erro ao verificar timezone: {}", e.getMessage(), e);
        }
    }
    
    private void verificarTimezone(Connection conn) throws SQLException {
        String query = """
            SELECT
                SYSDATETIMEOFFSET() as horario_com_timezone,
                GETDATE() as horario_sem_timezone,
                CURRENT_TIMEZONE() as timezone_servidor,
                GETUTCDATE() as horario_utc
            """;
        
        try (PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                logger.info("📊 DIAGNÓSTICO DE TIMEZONE DO SQL SERVER:");
                logger.info("   🕐 Horário com timezone: {}", rs.getString("horario_com_timezone"));
                logger.info("   🕐 GETDATE() (local): {}", rs.getString("horario_sem_timezone"));
                logger.info("   🕐 GETUTCDATE() (UTC): {}", rs.getString("horario_utc"));
                logger.info("   🌍 Timezone do servidor: {}", rs.getString("timezone_servidor"));
                
                String timezone = rs.getString("timezone_servidor");
                if (!"E. South America Standard Time".equals(timezone)) {
                    logger.warn("⚠️  PROBLEMA DETECTADO!");
                    logger.warn("   Timezone atual: {}", timezone);
                    logger.warn("   Timezone esperado: E. South America Standard Time");
                    logger.warn("   SOLUÇÃO: Usar Timestamp.from(Instant.now()) no Java em vez de GETDATE()");
                } else {
                    logger.info("✅ Timezone está correto para Brasília");
                }
            }
        }
    }
    

}