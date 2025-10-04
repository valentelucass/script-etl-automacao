package br.com.extrator.db;

import br.com.extrator.modelo.EntidadeDinamica;
import br.com.extrator.util.CarregadorConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Classe responsável pela comunicação com o banco de dados para entidades dinâmicas
 */
public class ServicoBancoDadosDinamico {
    private static final Logger logger = LoggerFactory.getLogger(ServicoBancoDadosDinamico.class);
    private final String urlConexao;
    private final String usuario;
    private final String senha;
    private final GeradorTabelasDinamico geradorTabelas;

    /**
     * Construtor que inicializa as configurações de conexão com o banco de dados
     */
    public ServicoBancoDadosDinamico() {
        this.urlConexao = CarregadorConfig.obterUrlBancoDados();
        this.usuario = CarregadorConfig.obterUsuarioBancoDados();
        this.senha = CarregadorConfig.obterSenhaBancoDados();
        this.geradorTabelas = new GeradorTabelasDinamico();
    }

    /**
     * Obtém uma conexão com o banco de dados
     * @return Conexão com o banco de dados
     * @throws SQLException Se ocorrer um erro ao conectar
     */
    public Connection obterConexao() throws SQLException {
        logger.debug("Conectando ao banco de dados: {}", urlConexao);
        return DriverManager.getConnection(urlConexao, usuario, senha);
    }

    /**
     * Salva uma lista de entidades dinâmicas no banco de dados
     * @param entidades Lista de entidades a serem salvas
     * @param nomeTabela Nome da tabela onde as entidades serão salvas
     * @return Número de entidades salvas com sucesso
     */
    public int salvarEntidades(List<EntidadeDinamica> entidades, String nomeTabela) {
        if (entidades == null || entidades.isEmpty()) {
            logger.warn("Lista de entidades vazia. Nada para salvar.");
            return 0;
        }

        logger.info("Iniciando salvamento de {} entidades na tabela {}", entidades.size(), nomeTabela);
        int totalSalvos = 0;

        try (Connection conexao = obterConexao()) {
            // Cria ou atualiza a tabela conforme necessário
            boolean tabelaOk = geradorTabelas.criarOuAtualizarTabela(conexao, entidades, nomeTabela);
            if (!tabelaOk) {
                logger.error("Não foi possível criar/atualizar a tabela {}. Abortando salvamento.", nomeTabela);
                return 0;
            }

            // Processa as entidades em lotes para melhor performance
            int tamLote = 100;
            List<EntidadeDinamica> loteAtual = new ArrayList<>(tamLote);

            for (EntidadeDinamica entidade : entidades) {
                loteAtual.add(entidade);

                if (loteAtual.size() >= tamLote) {
                    totalSalvos += salvarLote(conexao, loteAtual, nomeTabela);
                    loteAtual.clear();
                }
            }

            // Salva o último lote (se houver)
            if (!loteAtual.isEmpty()) {
                totalSalvos += salvarLote(conexao, loteAtual, nomeTabela);
            }

        } catch (SQLException e) {
            logger.error("Erro ao salvar entidades: {}", e.getMessage());
        }

        logger.info("Salvamento concluído. {} de {} entidades salvas com sucesso.", totalSalvos, entidades.size());
        return totalSalvos;
    }

    /**
     * Salva um lote de entidades no banco de dados
     * @param conexao Conexão com o banco de dados
     * @param lote Lista de entidades a serem salvas
     * @param nomeTabela Nome da tabela onde as entidades serão salvas
     * @return Número de entidades salvas com sucesso
     * @throws SQLException Se ocorrer um erro ao salvar
     */
    private int salvarLote(Connection conexao, List<EntidadeDinamica> lote, String nomeTabela) throws SQLException {
        if (lote.isEmpty()) return 0;

        // Obtém os campos da primeira entidade para construir o SQL
        EntidadeDinamica primeiraEntidade = lote.get(0);
        Set<String> campos = primeiraEntidade.getNomesCampos();

        // Constrói o SQL de inserção simples para SQL Server
        StringBuilder sqlCampos = new StringBuilder();
        StringBuilder sqlValores = new StringBuilder();

        for (String campo : campos) {
            String campoNormalizado = normalizarNomeCampo(campo);
            
            if (sqlCampos.length() > 0) {
                sqlCampos.append(", ");
                sqlValores.append(", ");
            }
            
            sqlCampos.append(campoNormalizado);
            sqlValores.append("?");
        }

        // Usa INSERT simples - duplicatas serão tratadas pela chave primária
        String sql = "INSERT INTO " + nomeTabela + " (" + sqlCampos + ") VALUES (" + sqlValores + ")";
        
        logger.debug("SQL de inserção: {}", sql);

        int totalProcessados = 0;
        int totalInseridos = 0;
        int totalDuplicatas = 0;
        
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            for (EntidadeDinamica entidade : lote) {
                int indice = 1;
                for (String campo : campos) {
                    Object valor = entidade.getCampo(campo);
                    stmt.setObject(indice++, valor);
                }

                try {
                    int resultado = stmt.executeUpdate();
                    if (resultado > 0) {
                        totalProcessados++;
                        totalInseridos++;
                    }
                    
                } catch (SQLException e) {
                    // Verifica se é erro de chave primária duplicada (SQL Server error code 2627)
                    if (e.getErrorCode() == 2627 || e.getMessage().contains("PRIMARY KEY constraint") || 
                        e.getMessage().contains("duplicate key")) {
                        totalDuplicatas++;
                        logger.debug("Registro duplicado ignorado na tabela {}", nomeTabela);
                    } else {
                        logger.warn("Erro ao processar entidade na tabela {}: {} (Código: {})", 
                                nomeTabela, e.getMessage(), e.getErrorCode());
                    }
                }
            }
        }

        if (totalDuplicatas > 0) {
            logger.info("Lote processado na tabela {}: {} inserções novas, {} duplicatas ignoradas", 
                    nomeTabela, totalInseridos, totalDuplicatas);
        } else {
            logger.debug("Lote processado na tabela {}: {} inserções novas", nomeTabela, totalInseridos);
        }

        return totalProcessados;
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