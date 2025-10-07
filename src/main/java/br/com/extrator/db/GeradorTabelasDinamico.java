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
     */
    private boolean verificarTabelaExiste(Connection conexao, String nomeTabela) throws SQLException {
        DatabaseMetaData metadados = conexao.getMetaData();
        try (ResultSet rs = metadados.getTables(null, null, nomeTabela, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
    
    /**
     * Cria uma nova tabela com base nas entidades dinâmicas
     */
    private boolean criarTabela(Connection conexao, List<EntidadeDinamica> entidades, String nomeTabela) throws SQLException {
        Map<String, String> tiposCampos = mapearTiposCampos(entidades);
        
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE ").append(nomeTabela).append(" (\n");
        sql.append("  pk_id INT IDENTITY(1,1) PRIMARY KEY,\n");
        
        for (Map.Entry<String, String> campo : tiposCampos.entrySet()) {
            String nomeCampoSQL = campo.getKey();
            sql.append("  ").append(nomeCampoSQL).append(" ").append(campo.getValue()).append(",\n");
        }
        
        sql.append("  data_importacao DATETIME2 DEFAULT SYSUTCDATETIME()");
        sql.append("\n)");
        
        logger.debug("SQL para criar tabela: \n{}", sql.toString());
        
        try (Statement stmt = conexao.createStatement()) {
            stmt.execute(sql.toString());
            logger.info("Tabela {} criada com sucesso.", nomeTabela);
            return true;
        } catch (SQLException e) {
            logger.error("Erro ao criar tabela {}: {}", nomeTabela, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Atualiza uma tabela existente com base nas entidades dinâmicas
     */
    private boolean atualizarTabela(Connection conexao, List<EntidadeDinamica> entidades, String nomeTabela) throws SQLException {
        Map<String, String> camposExistentes = obterCamposExistentes(conexao, nomeTabela);
        Map<String, String> tiposCampos = mapearTiposCampos(entidades);
        
        List<String> camposParaAdicionar = new ArrayList<>();
        for (Map.Entry<String, String> campo : tiposCampos.entrySet()) {
            String nomeCampoSQL = campo.getKey();
            if (!camposExistentes.containsKey(nomeCampoSQL)) {
                camposParaAdicionar.add(nomeCampoSQL + " " + campo.getValue());
            }
        }
        
        if (camposParaAdicionar.isEmpty()) {
            logger.info("Tabela {} já contém todos os campos necessários.", nomeTabela);
            return true;
        }
        
        try (Statement stmt = conexao.createStatement()) {
            for (String campo : camposParaAdicionar) {
                String sql = "ALTER TABLE " + nomeTabela + " ADD " + campo;
                logger.debug("SQL para adicionar campo: {}", sql);
                stmt.execute(sql);
            }
            logger.info("Tabela {} atualizada com sucesso. {} novos campos adicionados.", nomeTabela, camposParaAdicionar.size());
            return true;
        } catch (SQLException e) {
            logger.error("Erro ao atualizar tabela {}: {}", nomeTabela, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Obtém os campos existentes em uma tabela
     */
    private Map<String, String> obterCamposExistentes(Connection conexao, String nomeTabela) throws SQLException {
        Map<String, String> campos = new HashMap<>();
        DatabaseMetaData metadados = conexao.getMetaData();
        try (ResultSet rs = metadados.getColumns(null, null, nomeTabela, null)) {
            while (rs.next()) {
                campos.put(rs.getString("COLUMN_NAME").toLowerCase(), rs.getString("TYPE_NAME"));
            }
        }
        return campos;
    }
    
    /**
     * Mapeia os tipos de dados dos campos nas entidades
     */
    private Map<String, String> mapearTiposCampos(List<EntidadeDinamica> entidades) {
        Map<String, String> tiposCampos = new HashMap<>();
        
        for (EntidadeDinamica entidade : entidades) {
            for (String nomeCampo : entidade.getNomesCampos()) {
                String nomeCampoNormalizado = normalizarNomeCampo(nomeCampo);
                
                if (!tiposCampos.containsKey(nomeCampoNormalizado)) {
                    Object valor = entidade.getCampo(nomeCampo);
                    String tipoSQL = inferirTipoSQL(nomeCampo, valor);
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
     * Infere o tipo SQL com base no nome e no valor do campo.
     * MODIFICADO: Agora, campos que são JSON ou muito longos se tornam VARCHAR(MAX).
     * @param nomeCampo Nome do campo
     * @param valor Valor do campo
     * @return Tipo SQL correspondente
     */
    private String inferirTipoSQL(String nomeCampo, Object valor) {
        // Campos que são sempre booleanos (true/false)
        if (nomeCampo.equalsIgnoreCase("insuranceEnabled") || nomeCampo.equalsIgnoreCase("globalized")) {
            return "BIT";
        }

        // Se o valor for uma String, fazemos uma análise mais detalhada
        if (valor instanceof String) {
            String str = (String) valor;
            String strTrimmed = str.trim();

            // Se o texto for um JSON (começa com { ou [), usa VARCHAR(MAX) para garantir que caiba
            if (strTrimmed.startsWith("{") || strTrimmed.startsWith("[")) {
                return "VARCHAR(MAX)";
            }
            // Se for um texto muito longo, também usa VARCHAR(MAX)
            if (str.length() > 255) {
                return "VARCHAR(MAX)";
            }
        }

        // Padrão para todos os outros casos (números, datas, textos curtos)
        // Usar VARCHAR(255) como padrão é mais seguro contra erros de tipo de dados mistos.
        return "VARCHAR(255)";
    }
    
    /**
     * Normaliza o nome do campo para SQL
     */
    private String normalizarNomeCampo(String nomeCampo) {
        // Substitui caracteres especiais por underscore
        String normalizado = nomeCampo.replaceAll("[^a-zA-Z0-9_]", "_");
        
        // Garante que o nome não começa com número e não está vazio
        if (!normalizado.isEmpty() && Character.isDigit(normalizado.charAt(0))) {
            normalizado = "c_" + normalizado;
        }
        
        return normalizado.toLowerCase();
    }
}