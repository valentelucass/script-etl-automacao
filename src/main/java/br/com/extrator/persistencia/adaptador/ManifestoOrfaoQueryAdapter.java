package br.com.extrator.persistencia.adaptador;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/persistencia/adaptador/ManifestoOrfaoQueryAdapter.java
Classe  : ManifestoOrfaoQueryPort (class)
Pacote  : br.com.extrator.persistencia.adaptador
Modulo  : Persistencia - Adaptador
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.aplicacao.portas.ManifestoOrfaoQueryPort;
import br.com.extrator.suporte.banco.GerenciadorConexao;

/**
 * Adapter JDBC que consulta o banco para identificar a data mais antiga
 * de um manifesto orfao (pick_sequence_code sem coleta correspondente).
 */
public class ManifestoOrfaoQueryAdapter implements ManifestoOrfaoQueryPort {

    private static final Logger logger = LoggerFactory.getLogger(ManifestoOrfaoQueryAdapter.class);

    private static final String SQL_MIN_CREATED_AT_ORFAO = """
        SELECT CAST(MIN(m.created_at) AS DATE)
        FROM dbo.manifestos m
        LEFT JOIN dbo.coletas c ON c.sequence_code = m.pick_sequence_code
        WHERE m.pick_sequence_code IS NOT NULL
          AND c.sequence_code IS NULL
        """;

    @Override
    public Optional<LocalDate> buscarDataMaisAntigaManifestoOrfao() {
        try (Connection conn = GerenciadorConexao.obterConexao();
             PreparedStatement ps = conn.prepareStatement(SQL_MIN_CREATED_AT_ORFAO);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                final java.sql.Date date = rs.getDate(1);
                if (date != null) {
                    return Optional.of(date.toLocalDate());
                }
            }
            return Optional.empty();
        } catch (final SQLException e) {
            logger.warn("Falha ao consultar data mais antiga de manifesto orfao: {}. Backfill usara janela estatica.", e.getMessage());
            return Optional.empty();
        }
    }
}
