package br.com.extrator.db;

import br.com.extrator.modelo.EntidadeDinamica;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classe responsável por gerar tabelas SQL dinamicamente com base nos dados recebidos
 */
public class GeradorTabelasDinamico {
    private static final Logger logger = LoggerFactory.getLogger(GeradorTabelasDinamico.class);
    
    /**
     * Gera ou atualiza uma tabela SQL com base nas entidades dinâmicas
     * @param conexao Conexão com o banco de dados
     * @param entidades Lista de entidades dinâmicas
     * @param nomeTabela Nome da tabela a ser criada/atualizada
     * @return true se a operação foi bem-sucedida, false caso contrário
     */
    public boolean criarOuAtualizarTabela(Connection conexao, List<EntidadeDinamica> entidades, String nomeTabela) {
        if (entidades == null || entidades.isEmpty()) {
            logger.warn("Lista de entidades vazia. Não é possível criar tabela.");
            return false;
        }
        
        try {
            // Verifica se a tabela já existe
            boolean tabelaExiste = verificarTabelaExiste(conexao, nomeTabela);
            
            if (tabelaExiste) {
                logger.info("Tabela {} já existe. Verificando se é necessário atualizar.", nomeTabela);
                return atualizarTabela(conexao, entidades, nomeTabela);
            } else {
                logger.info("Tabela {} não existe. Criando nova tabela.", nomeTabela);
                return criarTabela(conexao, entidades, nomeTabela);
            }
        } catch (SQLException e) {
            logger.error("Erro ao criar ou atualizar tabela {}: {}", nomeTabela, e.getMessage());
            return false;
        }
    }
    
    /**
     * Verifica se uma tabela existe no banco de dados
     * @param conexao Conexão com o banco de dados
     * @param nomeTabela Nome da tabela a ser verificada
     * @return true se a tabela existe, false caso contrário
     */
    private boolean verificarTabelaExiste(Connection conexao, String nomeTabela) throws SQLException {
        DatabaseMetaData metadados = conexao.getMetaData();
        
        // SQL Server pode ser case-sensitive, então testamos ambos os casos
        ResultSet rs = metadados.getTables(null, null, nomeTabela.toUpperCase(), new String[]{"TABLE"});
        if (rs.next()) {
            logger.debug("Tabela {} encontrada (uppercase)", nomeTabela);
            return true;
        }
        
        rs = metadados.getTables(null, null, nomeTabela.toLowerCase(), new String[]{"TABLE"});
        if (rs.next()) {
            logger.debug("Tabela {} encontrada (lowercase)", nomeTabela);
            return true;
        }
        
        rs = metadados.getTables(null, null, nomeTabela, new String[]{"TABLE"});
        boolean existe = rs.next();
        logger.debug("Tabela {} {} (case original)", nomeTabela, existe ? "encontrada" : "não encontrada");
        return existe;
    }
    
    /**
     * Cria uma nova tabela com base nas entidades dinâmicas
     * @param conexao Conexão com o banco de dados
     * @param entidades Lista de entidades dinâmicas
     * @param nomeTabela Nome da tabela a ser criada
     * @return true se a operação foi bem-sucedida, false caso contrário
     */
    private boolean criarTabela(Connection conexao, List<EntidadeDinamica> entidades, String nomeTabela) throws SQLException {
        // Mapeia os tipos de dados dos campos
        Map<String, String> tiposCampos = mapearTiposCampos(entidades);
        
        // Constrói o SQL para criar a tabela
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE ").append(nomeTabela).append(" (\n");
        sql.append("  pk_id INT IDENTITY(1,1) PRIMARY KEY,\n");
        
        boolean primeiro = true;
        for (Map.Entry<String, String> campo : tiposCampos.entrySet()) {
            if (!primeiro) {
                sql.append(",\n");
            }
            primeiro = false;
            
            // O nome do campo já está normalizado no mapearTiposCampos
            // Não precisa normalizar novamente aqui
            String nomeCampoSQL = campo.getKey();
            sql.append("  ").append(nomeCampoSQL).append(" ").append(campo.getValue());
        }
        
        sql.append(",\n  data_importacao DATETIME2 DEFAULT SYSUTCDATETIME()");
        sql.append("\n)");
        
        logger.debug("SQL para criar tabela: {}", sql.toString());
        
        try (Statement stmt = conexao.createStatement()) {
            stmt.execute(sql.toString());
            logger.info("Tabela {} criada com sucesso.", nomeTabela);
            return true;
        } catch (SQLException e) {
            logger.error("Erro ao criar tabela {}: {}", nomeTabela, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Atualiza uma tabela existente com base nas entidades dinâmicas
     * @param conexao Conexão com o banco de dados
     * @param entidades Lista de entidades dinâmicas
     * @param nomeTabela Nome da tabela a ser atualizada
     * @return true se a operação foi bem-sucedida, false caso contrário
     */
    private boolean atualizarTabela(Connection conexao, List<EntidadeDinamica> entidades, String nomeTabela) throws SQLException {
        // Obtém os campos existentes na tabela
        Map<String, String> camposExistentes = obterCamposExistentes(conexao, nomeTabela);
        
        // Mapeia os tipos de dados dos campos nas entidades
        Map<String, String> tiposCampos = mapearTiposCampos(entidades);
        
        // Verifica se há novos campos para adicionar
        List<String> camposParaAdicionar = new ArrayList<>();
        for (Map.Entry<String, String> campo : tiposCampos.entrySet()) {
            // O nome do campo já está normalizado no mapearTiposCampos
            String nomeCampoSQL = campo.getKey();
            if (!camposExistentes.containsKey(nomeCampoSQL)) {
                camposParaAdicionar.add(nomeCampoSQL + " " + campo.getValue());
            }
        }
        
        // Se não há novos campos, não precisa atualizar a tabela
        if (camposParaAdicionar.isEmpty()) {
            logger.info("Tabela {} já contém todos os campos necessários.", nomeTabela);
            return true;
        }
        
        // Adiciona os novos campos à tabela
        try (Statement stmt = conexao.createStatement()) {
            for (String campo : camposParaAdicionar) {
                String sql = "ALTER TABLE " + nomeTabela + " ADD " + campo;
                logger.debug("SQL para adicionar campo: {}", sql);
                stmt.execute(sql);
            }
            logger.info("Tabela {} atualizada com sucesso. {} novos campos adicionados.", nomeTabela, camposParaAdicionar.size());
            return true;
        } catch (SQLException e) {
            logger.error("Erro ao atualizar tabela {}: {}", nomeTabela, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Obtém os campos existentes em uma tabela
     * @param conexao Conexão com o banco de dados
     * @param nomeTabela Nome da tabela
     * @return Mapa com os nomes e tipos dos campos existentes
     */
    private Map<String, String> obterCamposExistentes(Connection conexao, String nomeTabela) throws SQLException {
        Map<String, String> campos = new HashMap<>();
        
        DatabaseMetaData metadados = conexao.getMetaData();
        try (ResultSet rs = metadados.getColumns(null, null, nomeTabela, null)) {
            while (rs.next()) {
                String nomeCampo = rs.getString("COLUMN_NAME");
                String tipoCampo = rs.getString("TYPE_NAME");
                campos.put(nomeCampo, tipoCampo);
            }
        }
        
        return campos;
    }
    
    /**
     * Mapeia os tipos de dados dos campos nas entidades
     * @param entidades Lista de entidades dinâmicas
     * @return Mapa com os nomes e tipos SQL dos campos
     */
    private Map<String, String> mapearTiposCampos(List<EntidadeDinamica> entidades) {
        Map<String, String> tiposCampos = new HashMap<>();
        
        // Primeiro, coleta todos os campos de todas as entidades
        for (EntidadeDinamica entidade : entidades) {
            for (String nomeCampo : entidade.getNomesCampos()) {
                // Normaliza o nome do campo ANTES de usar como chave
                // Isso evita duplicatas quando campos como "id", "ID", "Id" são normalizados para "id"
                String nomeCampoNormalizado = normalizarNomeCampo(nomeCampo);
                
                if (!tiposCampos.containsKey(nomeCampoNormalizado)) {
                    Object valor = entidade.getCampo(nomeCampo);
                    String tipoSQL = inferirTipoSQL(valor);
                    tiposCampos.put(nomeCampoNormalizado, tipoSQL);
                    logger.debug("Campo mapeado: '{}' -> '{}' (tipo: {})", nomeCampo, nomeCampoNormalizado, tipoSQL);
                } else {
                    logger.debug("Campo '{}' já mapeado como '{}', ignorando duplicata", nomeCampo, nomeCampoNormalizado);
                }
            }
        }
        
        logger.info("Total de campos únicos mapeados: {}", tiposCampos.size());
        return tiposCampos;
    }
    
    /**
     * Infere o tipo SQL com base no valor do campo
     * @param valor Valor do campo
     * @return Tipo SQL correspondente
     */
    private String inferirTipoSQL(Object valor) {
        if (valor == null) {
            return "VARCHAR(255)";
        } else if (valor instanceof Integer || valor instanceof Long) {
            return "INT";
        } else if (valor instanceof Double || valor instanceof Float) {
            return "DECIMAL(18,4)";
        } else if (valor instanceof Boolean) {
            return "BIT";
        } else if (valor instanceof String) {
            String str = (String) valor;
            if (str.length() > 255) {
                return "VARCHAR(MAX)";
            } else {
                return "VARCHAR(255)";
            }
        } else {
            return "VARCHAR(255)";
        }
    }
    
    /**
     * Normaliza o nome do campo para SQL
     * @param nomeCampo Nome original do campo
     * @return Nome normalizado para SQL
     */
    private String normalizarNomeCampo(String nomeCampo) {
        // Substitui caracteres especiais por underscore
        String normalizado = nomeCampo.replaceAll("[^a-zA-Z0-9]", "_");
        
        // Garante que o nome não começa com número
        if (Character.isDigit(normalizado.charAt(0))) {
            normalizado = "c_" + normalizado;
        }
        
        return normalizado.toLowerCase();
    }
}