/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/repository/UsuarioSistemaRepository.java
Classe  : UsuarioSistemaRepository (class)
Pacote  : br.com.extrator.db.repository
Modulo  : Repositorio de dados
Papel   : Implementa responsabilidade de usuario sistema repository.

Conecta com:
- UsuarioSistemaEntity (db.entity)

Fluxo geral:
1) Monta comandos SQL e parametros.
2) Executa operacoes de persistencia/consulta no banco.
3) Converte resultado para entidades de dominio.

Estrutura interna:
Metodos principais:
- getNomeTabela(): expone valor atual do estado interno.
Atributos-chave:
- logger: logger da classe para diagnostico.
- NOME_TABELA: campo de estado para "nome tabela".
[DOC-FILE-END]============================================================== */

package br.com.extrator.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.UsuarioSistemaEntity;

/**
 * Repositório para operações de persistência da entidade UsuarioSistemaEntity.
 * Utiliza operações MERGE (UPSERT) com a chave primária (user_id).
 */
public class UsuarioSistemaRepository extends AbstractRepository<UsuarioSistemaEntity> {
    private static final Logger logger = LoggerFactory.getLogger(UsuarioSistemaRepository.class);
    private static final String NOME_TABELA = "dim_usuarios";

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }

    /**
     * Executa a operação MERGE (UPSERT) para inserir ou atualizar um usuário no banco.
     */
    @Override
    protected int executarMerge(final Connection conexao, final UsuarioSistemaEntity usuario) throws SQLException {
        if (usuario.getUserId() == null) {
            throw new SQLException("Não é possível executar o MERGE para Usuário do Sistema sem um 'user_id'.");
        }

        final String sql = String.format("""
            MERGE dbo.%s AS T
            USING (VALUES (?, ?, ?)) AS S (id, nome, data_atualizacao)
            ON T.user_id = S.id
            WHEN MATCHED THEN
                UPDATE SET T.nome = S.nome, T.data_atualizacao = S.data_atualizacao
            WHEN NOT MATCHED THEN
                INSERT (user_id, nome, data_atualizacao)
                VALUES (S.id, S.nome, S.data_atualizacao);
            """, NOME_TABELA);

        try (PreparedStatement statement = conexao.prepareStatement(sql)) {
            int paramIndex = 1;
            setLongParameter(statement, paramIndex++, usuario.getUserId());
            setStringParameter(statement, paramIndex++, usuario.getNome());
            setDateTimeParameter(statement, paramIndex++, usuario.getDataAtualizacao());
            
            if (paramIndex != 4) {
                throw new SQLException(String.format("Número incorreto de parâmetros: esperado 3, definido %d", paramIndex - 1));
            }

            final int rowsAffected = statement.executeUpdate();
            logger.debug("MERGE executado para Usuário do Sistema user_id {}: {} linha(s) afetada(s)", usuario.getUserId(), rowsAffected);
            return rowsAffected;
        }
    }
}
