package br.com.extrator.persistencia.repositorio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.extrator.dominio.coletas.ColetaStatusPolicy;
import br.com.extrator.suporte.configuracao.ConfigBanco;

class ColetaStatusCatalogParityTest {

    @Test
    void deveManterCatalogoSqlEmParidadeComContratoJava() throws SQLException {
        final Map<String, Boolean> catalogoSql = new HashMap<>();
        try (Connection conexao = DriverManager.getConnection(
            ConfigBanco.obterUrlBancoDados(),
            ConfigBanco.obterUsuarioBancoDados(),
            ConfigBanco.obterSenhaBancoDados()
        ); PreparedStatement statement = conexao.prepareStatement("""
            SELECT codigo_status, estado_terminal
              FROM dbo.dim_status_coleta
             WHERE ativo = 1
            """ ); ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                catalogoSql.put(resultSet.getString("codigo_status"), resultSet.getBoolean("estado_terminal"));
            }
        }

        assertEquals(ColetaStatusPolicy.knownCodes(), catalogoSql.keySet());
        ColetaStatusPolicy.knownCodes().forEach(status ->
            assertEquals(ColetaStatusPolicy.isTerminal(status), catalogoSql.get(status), status)
        );
    }
}
