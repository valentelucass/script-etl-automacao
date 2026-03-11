package br.com.extrator.aplicacao.validacao;

import static br.com.extrator.aplicacao.validacao.ValidacaoApiBanco24hDetalhadaTypes.JanelaExecucao;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

class ValidacaoApiBanco24hDetalhadaRepositoryTest {

    private final ValidacaoApiBanco24hDetalhadaRepository repository =
        new ValidacaoApiBanco24hDetalhadaRepository(
            LoggerConsole.getLogger(ValidacaoApiBanco24hDetalhadaRepositoryTest.class),
            new ValidacaoApiBanco24hDetalhadaMetadataHasher()
        );

    @Test
    void deveListarAccountingCreditIdsFretesNaJanelaDeExecucao() throws SQLException {
        final Map<Integer, Object> captured = new LinkedHashMap<>();
        final Connection conexao = criarConexao(sql -> {
            assertTrue(sql.contains("FROM dbo.fretes f"));
            return criarPreparedStatement(
                captured,
                criarResultSet(
                    List.of(
                        Map.of("accounting_credit_id", 101L),
                        Map.of("accounting_credit_id", 202L)
                    )
                )
            );
        });
        final JanelaExecucao janela = new JanelaExecucao(
            LocalDateTime.of(2026, 3, 10, 16, 2, 51),
            LocalDateTime.of(2026, 3, 10, 16, 31, 30),
            true
        );

        final List<Long> ids = repository.listarAccountingCreditIdsFretes(conexao, janela, 10);

        assertEquals(List.of(101L, 202L), ids);
        assertEquals(10, captured.get(1));
        assertEquals(Timestamp.valueOf(janela.inicio()), captured.get(2));
        assertEquals(Timestamp.valueOf(janela.fim()), captured.get(3));
    }

    @Test
    void deveAceitarMensagemDeDataUnicaAoBuscarJanelaCompletaDoDia() throws SQLException {
        final Map<Integer, Object> captured = new LinkedHashMap<>();
        final LocalDateTime inicio = LocalDateTime.of(2026, 3, 10, 16, 2, 51);
        final LocalDateTime fim = LocalDateTime.of(2026, 3, 10, 16, 31, 30);
        final Connection conexao = criarConexao(sql -> {
            assertTrue(sql.contains("FROM dbo.log_extracoes"));
            return criarPreparedStatement(
                captured,
                criarResultSet(
                    List.of(
                        Map.of(
                            "timestamp_inicio", Timestamp.valueOf(inicio),
                            "timestamp_fim", Timestamp.valueOf(fim)
                        )
                    )
                )
            );
        });

        final Optional<JanelaExecucao> janela = repository.buscarUltimaJanelaCompletaDoDia(
            conexao,
            ConstantesEntidades.FRETES,
            LocalDate.of(2026, 3, 10),
            LocalDate.of(2026, 3, 9),
            LocalDate.of(2026, 3, 9),
            true
        );

        assertTrue(janela.isPresent());
        assertEquals(inicio, janela.get().inicio());
        assertEquals(fim, janela.get().fim());
        assertTrue(janela.get().alinhadaAoPeriodo());
        assertEquals(ConstantesEntidades.FRETES, captured.get(1));
        assertEquals(java.sql.Date.valueOf(LocalDate.of(2026, 3, 10)), captured.get(2));
        assertEquals("%2026-03-09 a 2026-03-09%", captured.get(3));
        assertEquals("%Data: 2026-03-09%", captured.get(4));
    }

    private Connection criarConexao(final StatementFactory factory) {
        return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class},
            (proxy, method, args) -> {
                if ("prepareStatement".equals(method.getName())) {
                    return factory.criar((String) args[0]);
                }
                if ("close".equals(method.getName())) {
                    return null;
                }
                if ("isClosed".equals(method.getName())) {
                    return false;
                }
                return valorPadrao(method.getReturnType());
            }
        );
    }

    private PreparedStatement criarPreparedStatement(
        final Map<Integer, Object> captured,
        final ResultSet resultSet
    ) {
        return (PreparedStatement) Proxy.newProxyInstance(
            PreparedStatement.class.getClassLoader(),
            new Class<?>[]{PreparedStatement.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "setInt", "setTimestamp", "setString", "setDate" -> {
                        captured.put((Integer) args[0], args[1]);
                        return null;
                    }
                    case "executeQuery" -> {
                        return resultSet;
                    }
                    case "close" -> {
                        return null;
                    }
                    default -> {
                        return valorPadrao(method.getReturnType());
                    }
                }
            }
        );
    }

    private ResultSet criarResultSet(final List<Map<String, Object>> rows) {
        final int[] index = {-1};
        final boolean[] wasNull = {false};
        return (ResultSet) Proxy.newProxyInstance(
            ResultSet.class.getClassLoader(),
            new Class<?>[]{ResultSet.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "next" -> {
                        index[0]++;
                        return index[0] < rows.size();
                    }
                    case "getLong" -> {
                        final Object value = rows.get(index[0]).get((String) args[0]);
                        wasNull[0] = value == null;
                        return wasNull[0] ? 0L : ((Number) value).longValue();
                    }
                    case "getTimestamp" -> {
                        final Object value = rows.get(index[0]).get((String) args[0]);
                        wasNull[0] = value == null;
                        return value;
                    }
                    case "wasNull" -> {
                        return wasNull[0];
                    }
                    case "close" -> {
                        return null;
                    }
                    default -> {
                        return valorPadrao(method.getReturnType());
                    }
                }
            }
        );
    }

    private Object valorPadrao(final Class<?> returnType) {
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (returnType == Double.TYPE) {
            return 0D;
        }
        if (returnType == Float.TYPE) {
            return 0F;
        }
        if (returnType == Short.TYPE) {
            return (short) 0;
        }
        if (returnType == Byte.TYPE) {
            return (byte) 0;
        }
        if (returnType == Character.TYPE) {
            return '\0';
        }
        return null;
    }

    @FunctionalInterface
    private interface StatementFactory {
        PreparedStatement criar(String sql) throws SQLException;
    }
}
