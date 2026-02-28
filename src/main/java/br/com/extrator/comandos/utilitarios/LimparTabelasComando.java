/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/utilitarios/LimparTabelasComando.java
Classe  : LimparTabelasComando (class)
Pacote  : br.com.extrator.comandos.utilitarios
Modulo  : Componente Java
Papel   : Implementa comportamento de limpar tabelas comando.

Conecta com:
- Comando (comandos.base)
- GerenciadorConexao (util.banco)

Fluxo geral:
1) Define comportamento principal deste modulo.
2) Interage com camadas relacionadas do sistema.
3) Entrega resultado para o fluxo chamador.

Estrutura interna:
Metodos principais:
- executar(...1 args): executa o fluxo principal desta responsabilidade.
- limparTabela(...2 args): realiza operacao relacionada a "limpar tabela".
Atributos-chave:
- logger: logger da classe para diagnostico.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.utilitarios;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.comandos.base.Comando;
import br.com.extrator.util.banco.GerenciadorConexao;

/**
 * Comando para limpar todas as tabelas do banco de dados.
 * Remove dados com timestamp incorreto após correção do bug de timezone.
 */
public class LimparTabelasComando implements Comando {
    private static final Logger logger = LoggerFactory.getLogger(LimparTabelasComando.class);
    
    @Override
    public void executar(String[] args) {
        logger.info("🧹 Iniciando limpeza das tabelas...");
        
        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            List<String> tabelas = obterListaTabelas(conexao);
            
            logger.info("📋 Encontradas {} tabelas para limpeza", tabelas.size());
            
            for (String tabela : tabelas) {
                limparTabela(conexao, tabela);
            }
            
            logger.info("✅ Limpeza concluída com sucesso!");
            
        } catch (SQLException e) {
            logger.error("❌ Erro durante a limpeza das tabelas: {}", e.getMessage(), e);
            throw new RuntimeException("Erro durante limpeza das tabelas", e);
        }
    }
    
    private List<String> obterListaTabelas(Connection conexao) throws SQLException {
        List<String> tabelas = new ArrayList<>();
        
        String sql = "SELECT table_name FROM information_schema.tables " +
                    "WHERE table_type = 'BASE TABLE' " +
                    "AND table_name NOT LIKE 'sys%'";
        
        try (PreparedStatement stmt = conexao.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                String nomeTabela = rs.getString("table_name");
                tabelas.add(nomeTabela);
            }
        }
        
        return tabelas;
    }
    
    private void limparTabela(Connection conexao, String nomeTabela) {
        try {
            String sql = "TRUNCATE TABLE " + nomeTabela;
            
            try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
                stmt.executeUpdate();
                logger.info("🗑️  Tabela {} limpa com sucesso", nomeTabela);
            }
            
        } catch (SQLException e) {
            logger.warn("⚠️  Não foi possível limpar a tabela {}: {}", nomeTabela, e.getMessage());
        }
    }
}