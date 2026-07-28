package br.com.extrator.persistencia.repositorio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import br.com.extrator.persistencia.entidade.InventarioEntity;
import br.com.extrator.suporte.configuracao.ConfigBanco;

class InventarioComprovanteMergeTest {

    @Test
    void devePreservarComprovanteQuandoOcorrenciaPosteriorNaoORepetir() throws SQLException {
        final String identificador = "it-inventario-comprovante-" + System.nanoTime();
        final long sequenceCode = Math.abs(System.nanoTime() % 1_000_000_000L) + 7_000_000_000L;
        final OffsetDateTime inicio = OffsetDateTime.of(2026, 7, 1, 10, 0, 0, 0, ZoneOffset.ofHours(-3));

        try (Connection conexao = DriverManager.getConnection(
            ConfigBanco.obterUrlBancoDados(),
            ConfigBanco.obterUsuarioBancoDados(),
            ConfigBanco.obterSenhaBancoDados()
        )) {
            conexao.setAutoCommit(false);
            try {
                final TestableInventarioRepository repository = new TestableInventarioRepository();
                assertEquals(1, repository.merge(conexao, inventario(identificador, sequenceCode, inicio, true)));
                assertEquals(1, repository.merge(conexao, inventario(identificador, sequenceCode, inicio.plusHours(1), false)));

                try (PreparedStatement statement = conexao.prepareStatement("""
                    SELECT flag_comprovante_anexado
                      FROM dbo.inventario
                     WHERE identificador_unico = ?
                    """)) {
                    statement.setString(1, identificador);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        assertTrue(resultSet.next());
                        assertEquals(true, resultSet.getBoolean("flag_comprovante_anexado"));
                    }
                }
            } finally {
                conexao.rollback();
            }
        }
    }

    private static InventarioEntity inventario(final String identificador,
                                               final long sequenceCode,
                                               final OffsetDateTime performanceFinishedAt,
                                               final boolean comprovanteAnexado) {
        final InventarioEntity entity = new InventarioEntity();
        entity.setIdentificadorUnico(identificador);
        entity.setSequenceCode(sequenceCode);
        entity.setNumeroMinuta(sequenceCode);
        entity.setStartedAt(performanceFinishedAt.minusHours(1));
        entity.setFinishedAt(performanceFinishedAt.minusMinutes(30));
        entity.setPerformanceFinishedAt(performanceFinishedAt);
        entity.setStatus("finished");
        entity.setFlagComprovanteAnexado(comprovanteAnexado);
        entity.setMetadata("{\"integrationTest\":true}");
        return entity;
    }

    private static final class TestableInventarioRepository extends InventarioRepository {
        private int merge(final Connection conexao, final InventarioEntity inventario) throws SQLException {
            return executarMerge(conexao, inventario);
        }
    }
}
