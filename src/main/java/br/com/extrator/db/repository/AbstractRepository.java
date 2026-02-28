/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/repository/AbstractRepository.java
Classe  : AbstractRepository (class)
Pacote  : br.com.extrator.db.repository
Modulo  : Repositorio de dados
Papel   : Implementa responsabilidade de abstract repository.

Conecta com:
- CarregadorConfig (util.configuracao)
- GerenciadorConexao (util.banco)

Fluxo geral:
1) Monta comandos SQL e parametros.
2) Executa operacoes de persistencia/consulta no banco.
3) Converte resultado para entidades de dominio.

Estrutura interna:
Metodos principais:
- getBatchSize(): expone valor atual do estado interno.
- isContinuarAposErro(): retorna estado booleano de controle.
- AbstractRepository(): realiza operacao relacionada a "abstract repository".
- obterIdentificadorEntidade(...1 args): recupera dados configurados ou calculados.
- encontrarGetter(...2 args): realiza operacao relacionada a "encontrar getter".
- converterSnakeToCamel(...1 args): transforma dados entre formatos/modelos.
- normalizarNomeCampo(...1 args): realiza operacao relacionada a "normalizar nome campo".
- truncate(...2 args): realiza operacao relacionada a "truncate".
Atributos-chave:
- logger: logger da classe para diagnostico.
[DOC-FILE-END]============================================================== */

package br.com.extrator.db.repository;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.util.configuracao.CarregadorConfig;
import br.com.extrator.util.banco.GerenciadorConexao;

/**
 * Classe base abstrata para repositórios com operações comuns de banco de dados.
 * Fornece métodos de conexão, MERGE (UPSERT) e utilitários para conversão de tipos.
 *
 * VERSÃO CORRIGIDA: 
 * - Usa HikariCP pool via GerenciadorConexao (CORRIGE vazamento de conexões)
 * - Implementa tratamento robusto de erros individuais
 * - Commit em batches e logging detalhado para identificar registros problemáticos
 * - Isolamento de transação apropriado para ETL
 *
 * @param <T> Tipo da entidade gerenciada pelo repositório
 */
public abstract class AbstractRepository<T> {
    private static final Logger logger = LoggerFactory.getLogger(AbstractRepository.class);
    
    /**
     * Obtém o tamanho do batch para commits (configurável via config.properties).
     * @return Tamanho do batch
     */
    protected static int getBatchSize() {
        return CarregadorConfig.obterBatchSize();
    }
    
    /**
     * Verifica se deve continuar após erro (configurável via config.properties).
     * @return true para continuar após erros
     */
    protected static boolean isContinuarAposErro() {
        return CarregadorConfig.isContinuarAposErro();
    }

    /**
     * Construtor padrão.
     * 
     * NOTA: Configurações de banco de dados agora são gerenciadas pelo GerenciadorConexao.
     * Não é mais necessário armazenar URL, usuário e senha aqui.
     */
    protected AbstractRepository() {
        // Configurações gerenciadas pelo GerenciadorConexao (pool HikariCP)
    }

    /**
     * Obtém uma conexão com o banco de dados do pool HikariCP.
     * 
     * CORREÇÃO CRÍTICA: Agora usa GerenciadorConexao em vez de DriverManager.getConnection().
     * Isso elimina o vazamento de recursos e melhora drasticamente a performance.
     * 
     * @return Conexão do pool HikariCP
     * @throws SQLException Se ocorrer um erro ao obter conexão do pool
     */
    protected Connection obterConexao() throws SQLException {
        logger.debug("Obtendo conexão do pool HikariCP");
        return GerenciadorConexao.obterConexao();
    }

    /**
     * Salva uma lista de entidades no banco de dados usando operação MERGE (UPSERT)
     * 
     * VERSÃO CORRIGIDA:
     * - Trata cada registro individualmente (não perde todos por causa de 1 erro)
     * - Commit em batches para evitar transações gigantes
     * - Log detalhado de erros com identificação do registro problemático
     * - Retorna quantidade de registros salvos com sucesso
     * 
     * @param entidades Lista de entidades a serem salvas
     * @return Número de registros SALVOS COM SUCESSO (não total tentado)
     * @throws SQLException Se ocorrer um erro crítico na conexão
     */
    public int salvar(final List<T> entidades) throws SQLException {
        if (entidades == null || entidades.isEmpty()) {
            logger.warn("Lista de entidades vazia para {}", getClass().getSimpleName());
            return 0;
        }

        int totalSucesso = 0;
        int totalFalhas = 0;
        int registroAtual = 0;
        final int totalRegistros = entidades.size();

        final int batchSize = getBatchSize();
        logger.info("🔄 Iniciando salvamento de {} registros de {} (batch size: {})", 
            totalRegistros, getClass().getSimpleName(), batchSize);

        try (Connection conexao = obterConexao()) {
            // ✅ CORREÇÃO CRÍTICA #3: Configurar isolamento de transação apropriado
            // READ_COMMITTED é suficiente para ETL e evita locks desnecessários
            conexao.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            conexao.setAutoCommit(false);
            
            // ✅ CORREÇÃO CRÍTICA #3: Definir timeout de transação (30 segundos)
            // Previne deadlocks e transações travadas
            try (Statement stmt = conexao.createStatement()) {
                stmt.execute("SET LOCK_TIMEOUT 30000"); // 30 segundos
                logger.debug("Timeout de transação configurado: 30s");
            } catch (SQLException e) {
                logger.warn("Não foi possível definir LOCK_TIMEOUT: {}", e.getMessage());
                // Continua - não é crítico
            }

            try {
                // Verificar que a tabela existe (NÃO criar - schema deve ser gerenciado via scripts SQL)
                verificarTabelaExisteOuLancarErro(conexao);

                // Processar cada entidade individualmente
                for (final T entidade : entidades) {
                    registroAtual++;
                    
                    try {
                        // Tenta executar o MERGE para este registro
                        final int rowsAffected = executarMerge(conexao, entidade);
                        
                        // Contar como sucesso apenas se rowsAffected > 0
                        // MERGE normalmente retorna 1 para INSERT ou UPDATE bem-sucedido
                        // Se retornar 0, significa que não inseriu nem atualizou (não conta como salvo)
                        if (rowsAffected > 0) {
                            // Contar como 1 registro salvo (não somar rowsAffected, que pode ser > 1 em casos raros)
                            totalSucesso++;
                        } else {
                            // Se rowsAffected == 0, não conta como sucesso
                            // O executarMerge() já deve ter logado o erro
                            logger.warn("⚠️ MERGE retornou 0 para registro {}/{} de {}: {} (não foi salvo)", 
                                registroAtual, totalRegistros, getClass().getSimpleName(),
                                obterIdentificadorEntidade(entidade));
                        }
                        
                        // Commit em batches para evitar transações muito grandes
                        if (registroAtual % batchSize == 0) {
                            conexao.commit();
                            logger.debug("✅ Batch commit: {}/{} registros processados", registroAtual, totalRegistros);
                        }
                        
                    } catch (final SQLException e) {
                        totalFalhas++;
                        
                        // Log detalhado do erro COM o registro que falhou
                        logger.error("❌ Erro ao salvar registro {}/{} de {}: {} | Detalhes: {}", 
                            registroAtual, 
                            totalRegistros,
                            getClass().getSimpleName(),
                            e.getMessage(),
                            obterIdentificadorEntidade(entidade)); // ← Novo método auxiliar
                        
                        // Log da stack trace completa em nível DEBUG
                        logger.debug("Stack trace completo do erro:", e);
                        
                        if (!isContinuarAposErro()) {
                            // Se configurado para parar na primeira falha
                            logger.error("🚨 Abortando salvamento devido a erro crítico");
                            conexao.rollback();
                            throw e;
                        }
                        
                        // Caso contrário, continua processando os próximos registros
                        // Não faz rollback - mantém os registros salvos com sucesso
                    }
                }

                // Commit final dos registros restantes
                conexao.commit();
                logger.debug("✅ Commit final executado com sucesso");
                
                // Log final com estatísticas
                // IMPORTANTE: "totalSucesso" = operações bem-sucedidas (INSERTs + UPDATEs)
                // UPDATEs não adicionam novas linhas, apenas atualizam existentes
                // Por isso, o número de registros no banco pode ser menor que "totalSucesso"
                // quando há UPDATEs (comportamento esperado em execuções periódicas)
                if (totalFalhas > 0 || totalSucesso < totalRegistros) {
                    final int registrosNaoSalvos = totalRegistros - totalSucesso - totalFalhas;
                    logger.warn("⚠️ Salvamento concluído: {} operações bem-sucedidas (INSERTs + UPDATEs), {} falhas, {} não processados (rowsAffected=0) de {} total ({}%)", 
                        totalSucesso, 
                        totalFalhas,
                        registrosNaoSalvos,
                        totalRegistros,
                        String.format("%.1f", (totalSucesso * 100.0 / totalRegistros)));
                    logger.info("💡 Nota: 'Operações bem-sucedidas' inclui INSERTs (novos registros) e UPDATEs (registros atualizados). " +
                               "UPDATEs não adicionam novas linhas ao banco, apenas atualizam existentes.");
                } else {
                    logger.info("✅ Salvamento 100% concluído: {} operações bem-sucedidas (INSERTs + UPDATEs) de {} processados", 
                        totalSucesso, totalRegistros);
                    logger.info("""
                        💡 Nota: 'Operações bem-sucedidas' inclui INSERTs (novos registros) e UPDATEs (registros atualizados). \
                        Se houver UPDATEs, o número de registros no banco pode ser menor que o número de operações. \
                        Isso é esperado quando o script roda periodicamente (execuções a cada 1h buscando últimas 24h).""");
                }

            } catch (final SQLException e) {
                // Erro crítico na conexão/transação
                try {
                    conexao.rollback();
                    logger.warn("⚠️ Rollback executado devido a erro: {}", e.getMessage());
                } catch (SQLException rollbackEx) {
                    logger.error("🚨 ERRO ao executar rollback: {}", rollbackEx.getMessage());
                }
                logger.error("🚨 Erro crítico ao salvar entidades de {}: {}", 
                    getClass().getSimpleName(), e.getMessage());
                throw e;
            }
        }

        // Retornar número de linhas afetadas (rowsAffected) - comportamento original
        // Isso é consistente com o comportamento anterior onde somava rowsAffected
        return totalSucesso;
    }

    /**
     * Salva uma única entidade no banco de dados
     * @param entidade Entidade a ser salva
     * @return Número de registros afetados (0 ou 1)
     * @throws SQLException Se ocorrer um erro durante a operação
     */
    public int salvar(final T entidade) throws SQLException {
        if (entidade == null) {
            logger.warn("Entidade nula para {}", getClass().getSimpleName());
            return 0;
        }

        final List<T> entidades = new ArrayList<>();
        entidades.add(entidade);
        return salvar(entidades);
    }

    /**
     * Método auxiliar para obter um identificador legível da entidade para logs.
     * Tenta extrair informações básicas da entidade usando reflexão.
     * 
     * @param entidade Entidade a ser identificada
     * @return String com identificação básica da entidade
     */
    private String obterIdentificadorEntidade(final T entidade) {
        if (entidade == null) {
            return "null";
        }
        
        try {
            // Tenta obter campos comuns via reflexão
            final StringBuilder info = new StringBuilder();
            info.append(entidade.getClass().getSimpleName()).append("{");
            
            // Tenta pegar alguns campos comuns
            final String[] camposPossiveis = {"id", "sequenceCode", "sequence_code", "documentNumber"};
            boolean encontrouCampo = false;
            
            for (final String campo : camposPossiveis) {
                try {
                    final java.lang.reflect.Method getter = encontrarGetter(entidade.getClass(), campo);
                    if (getter != null) {
                        final Object valor = getter.invoke(entidade);
                        if (valor != null) {
                            if (encontrouCampo) info.append(", ");
                            info.append(campo).append("=").append(valor);
                            encontrouCampo = true;
                        }
                    }
                } catch (final java.lang.reflect.InvocationTargetException | 
                        java.lang.IllegalAccessException | 
                        java.lang.IllegalArgumentException ignored) {
                    // Ignora se não conseguir acessar o campo via reflexão
                }
            }
            
            if (!encontrouCampo) {
                info.append("toString=").append(entidade.toString());
            }
            
            info.append("}");
            return info.toString();
            
        } catch (final Exception e) {
            // Fallback geral: retorna identificador simples se qualquer operação falhar
            return entidade.getClass().getSimpleName() + "@" + entidade.hashCode();
        }
    }
    
    /**
     * Encontra o método getter para um campo usando convenções Java
     */
    private java.lang.reflect.Method encontrarGetter(final Class<?> clazz, final String nomeCampo) {
        try {
            // Tenta get + CamelCase
            final String getterName = "get" + nomeCampo.substring(0, 1).toUpperCase() + nomeCampo.substring(1);
            return clazz.getMethod(getterName);
        } catch (final NoSuchMethodException e1) {
            try {
                // Tenta snake_case convertido para camelCase
                final String camelCase = converterSnakeToCamel(nomeCampo);
                final String getterName = "get" + camelCase.substring(0, 1).toUpperCase() + camelCase.substring(1);
                return clazz.getMethod(getterName);
            } catch (final NoSuchMethodException e2) {
                return null;
            }
        }
    }
    
    /**
     * Converte snake_case para camelCase
     */
    private String converterSnakeToCamel(final String snake) {
        final StringBuilder camel = new StringBuilder();
        boolean proximaMaiuscula = false;
        for (final char c : snake.toCharArray()) {
            if (c == '_') {
                proximaMaiuscula = true;
            } else {
                camel.append(proximaMaiuscula ? Character.toUpperCase(c) : c);
                proximaMaiuscula = false;
            }
        }
        return camel.toString();
    }


    /**
     * Método abstrato que deve ser implementado por cada repositório específico
     * para executar a operação MERGE (UPSERT) da entidade
     * @param conexao Conexão com o banco de dados
     * @param entidade Entidade a ser inserida/atualizada
     * @return Número de registros afetados (0 ou 1)
     * @throws SQLException Se ocorrer um erro durante a operação
     */
    protected abstract int executarMerge(Connection conexao, T entidade) throws SQLException;

    /**
     * Método abstrato que retorna o nome da tabela para este repositório
     * @return Nome da tabela
     */
    protected abstract String getNomeTabela();

    /**
     * Verifica se uma tabela existe no banco de dados
     * @param conexao Conexão com o banco de dados
     * @param nomeTabela Nome da tabela a verificar
     * @return true se a tabela existe, false caso contrário
     * @throws SQLException Se ocorrer um erro durante a verificação
     */
    protected boolean verificarTabelaExiste(final Connection conexao, final String nomeTabela) throws SQLException {
        final DatabaseMetaData metaData = conexao.getMetaData();
        try (ResultSet resultSet = metaData.getTables(null, null, nomeTabela.toUpperCase(), new String[]{"TABLE"})) {
            return resultSet.next();
        }
    }

    /**
     * Verifica se a tabela existe e lança exceção se não existir.
     * 
     * IMPORTANTE: Em produção, as tabelas devem ser criadas via scripts SQL versionados (pasta database/).
     * Este método apenas valida que o schema foi aplicado corretamente.
     * 
     * @param conexao Conexão com o banco de dados
     * @throws SQLException Se a tabela não existir ou ocorrer erro durante a verificação
     */
    protected void verificarTabelaExisteOuLancarErro(final Connection conexao) throws SQLException {
        final String nomeTabela = getNomeTabela();
        if (!verificarTabelaExiste(conexao, nomeTabela)) {
            final String mensagem = String.format(
                """
                    ❌ ERRO CRÍTICO: Tabela '%s' não existe no banco de dados. \
                    Execute os scripts SQL da pasta 'database/' antes de rodar a aplicação. \
                    Veja database/README.md para instruções.""",
                nomeTabela
            );
            logger.error(mensagem);
            throw new SQLException(mensagem);
        }
        logger.debug("✅ Tabela '{}' verificada e existe no banco de dados", nomeTabela);
    }


    /**
     * Define um parâmetro no PreparedStatement, tratando valores nulos adequadamente
     * @param statement PreparedStatement
     * @param index Índice do parâmetro (1-based)
     * @param value Valor a ser definido
     * @param sqlType Tipo SQL do parâmetro
     * @throws SQLException Se ocorrer um erro ao definir o parâmetro
     */
    protected void setParameter(final PreparedStatement statement, final int index, final Object value, final int sqlType) throws SQLException {
        if (value == null) {
            statement.setNull(index, sqlType);
        } else {
            statement.setObject(index, value, sqlType);
        }
    }

    /**
     * Define um parâmetro String no PreparedStatement
     * @param statement PreparedStatement
     * @param index Índice do parâmetro (1-based)
     * @param value Valor String a ser definido
     * @throws SQLException Se ocorrer um erro ao definir o parâmetro
     */
    protected void setStringParameter(final PreparedStatement statement, final int index, final String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    /**
     * Define um parâmetro Integer no PreparedStatement
     * @param statement PreparedStatement
     * @param index Índice do parâmetro (1-based)
     * @param value Valor Integer a ser definido
     * @throws SQLException Se ocorrer um erro ao definir o parâmetro
     */
    protected void setIntegerParameter(final PreparedStatement statement, final int index, final Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    /**
     * Define um parâmetro Double no PreparedStatement
     * @param statement PreparedStatement
     * @param index Índice do parâmetro (1-based)
     * @param value Valor Double a ser definido
     * @throws SQLException Se ocorrer um erro ao definir o parâmetro
     */
    protected void setDoubleParameter(final PreparedStatement statement, final int index, final Double value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DOUBLE);
        } else {
            statement.setDouble(index, value);
        }
    }

    /**
     * Define um parâmetro Boolean no PreparedStatement
     * @param statement PreparedStatement
     * @param index Índice do parâmetro (1-based)
     * @param value Valor Boolean a ser definido
     * @throws SQLException Se ocorrer um erro ao definir o parâmetro
     */
    protected void setBooleanParameter(final PreparedStatement statement, final int index, final Boolean value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BOOLEAN);
        } else {
            statement.setBoolean(index, value);
        }
    }

    /**
     * Define um parâmetro BigDecimal no PreparedStatement
     * @param statement PreparedStatement
     * @param index Índice do parâmetro (1-based)
     * @param value Valor BigDecimal a ser definido
     * @throws SQLException Se ocorrer um erro ao definir o parâmetro
     */
    protected void setBigDecimalParameter(final PreparedStatement statement, final int index, final java.math.BigDecimal value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    /**
     * Define um parâmetro LocalDateTime no PreparedStatement
     * @param statement PreparedStatement
     * @param index Índice do parâmetro (1-based)
     * @param value Valor LocalDateTime a ser definido
     * @throws SQLException Se ocorrer um erro ao definir o parâmetro
     */
    protected void setDateTimeParameter(final PreparedStatement statement, final int index, final LocalDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.valueOf(value));
        }
    }

    /**
     * Define um parâmetro Instant no PreparedStatement
     * @param statement PreparedStatement
     * @param index Índice do parâmetro (1-based)
     * @param value Valor Instant a ser definido
     * @throws SQLException Se ocorrer um erro ao definir o parâmetro
     */
    protected void setInstantParameter(final PreparedStatement statement, final int index, final Instant value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(value));
        }
    }

    /**
     * Define um parâmetro OffsetDateTime no PreparedStatement
     * @param statement PreparedStatement
     * @param index Índice do parâmetro (1-based)
     * @param value Valor OffsetDateTime a ser definido
     * @throws SQLException Se ocorrer um erro ao definir o parâmetro
     */
    protected void setOffsetDateTimeParameter(final PreparedStatement statement, final int index, final java.time.OffsetDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(value.toInstant()));
        }
    }

    /**
     * Define um parâmetro Long no PreparedStatement
     * @param statement PreparedStatement
     * @param index Índice do parâmetro (1-based)
     * @param value Valor Long a ser definido
     * @throws SQLException Se ocorrer um erro ao definir o parâmetro
     */
    protected void setLongParameter(final PreparedStatement statement, final int index, final Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    /**
     * Define um parâmetro LocalDate no PreparedStatement
     * @param statement PreparedStatement
     * @param index Índice do parâmetro (1-based)
     * @param value Valor LocalDate a ser definido
     * @throws SQLException Se ocorrer um erro ao definir o parâmetro
     */
    protected void setDateParameter(final PreparedStatement statement, final int index, final LocalDate value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DATE);
        } else {
            statement.setDate(index, Date.valueOf(value));
        }
    }

    /**
     * Normaliza o nome de um campo para uso em SQL (remove caracteres especiais, etc.)
     * @param nomeCampo Nome do campo original
     * @return Nome do campo normalizado
     */
    protected String normalizarNomeCampo(final String nomeCampo) {
        if (nomeCampo == null) {
            return null;
        }

        return nomeCampo
                .replaceAll("[^a-zA-Z0-9_]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "")
                .toLowerCase();
    }

    /**
     * Adiciona uma coluna à tabela se ela não existir.
     * 
     * Utilitário centralizado para evitar duplicação em ColetaRepository e ManifestoRepository.
     * 
     * @param conn Conexão com o banco
     * @param tabela Nome da tabela
     * @param coluna Nome da coluna
     * @param definicao Definição SQL da coluna (ex: "NVARCHAR(MAX)")
     * @throws SQLException Se ocorrer erro ao verificar ou adicionar coluna
     * @since 2.3.2
     */
    protected void adicionarColunaSeNaoExistir(
        final Connection conn,
        final String tabela,
        final String coluna,
        final String definicao
    ) throws SQLException {
        final String checkSql = 
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
            "WHERE TABLE_NAME = ? AND COLUMN_NAME = ?";
        
        try (final PreparedStatement ps = conn.prepareStatement(checkSql)) {
            ps.setString(1, tabela);
            ps.setString(2, coluna);
            try (final ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    final String alterSql = String.format(
                        "ALTER TABLE %s ADD %s %s",
                        tabela, coluna, definicao
                    );
                    try (final java.sql.Statement stmt = conn.createStatement()) {
                        stmt.execute(alterSql);
                        logger.info("Coluna {} adicionada à tabela {}", coluna, tabela);
                    }
                }
            }
        }
    }

    /**
     * Trunca string para tamanho máximo.
     * 
     * Utilitário centralizado para evitar duplicação em ManifestoRepository.
     * 
     * @param valor String a ser truncada
     * @param maxLen Tamanho máximo
     * @return String truncada ou original se menor que maxLen
     * @since 2.3.2
     */
    protected String truncate(final String valor, final int maxLen) {
        if (valor == null || valor.length() <= maxLen) {
            return valor;
        }
        return valor.substring(0, maxLen);
    }
}