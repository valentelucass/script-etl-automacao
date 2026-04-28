package br.com.extrator.observabilidade.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import br.com.extrator.plataforma.auditoria.dominio.ExecutionPlanContext;
import br.com.extrator.plataforma.auditoria.dominio.ExecutionWindowPlan;
import br.com.extrator.suporte.observabilidade.ExecutionContext;

class SqlServerDataQualityQueryAdapterTest {

    @Test
    void deveMapearInventarioESinistrosNaDeteccaoDeSchema() {
        assertEquals("inventario", SqlServerDataQualityQueryAdapter.resolverNomeTabela("inventario"));
        assertEquals("sinistros", SqlServerDataQualityQueryAdapter.resolverNomeTabela("sinistros"));
    }

    @Test
    void deveRetornarNullParaEntidadeSemMapeamentoGenerico() {
        assertNull(SqlServerDataQualityQueryAdapter.resolverNomeTabela("entidade_inexistente"));
    }

    @Test
    void deveBuscarCompletudePeloPeriodoRegistradoNaMensagemDoLog() {
        final SqlCapture capture = new SqlCapture();
        final Connection connection = criarConexao(capture, List.of(Map.of("1", 0L)));
        final SqlServerDataQualityQueryAdapter adapter =
            new SqlServerDataQualityQueryAdapter(() -> connection);

        final long incompletos = adapter.contarLinhasIncompletas(
            "Manifestos",
            LocalDate.of(2026, 4, 13),
            LocalDate.of(2026, 4, 14)
        );

        assertEquals(0L, incompletos);
        assertTrue(capture.sql().contains("mensagem LIKE ?"));
        assertTrue(!capture.sql().contains("CAST(timestamp_inicio AS DATE)"));
        assertEquals("manifestos", capture.parameters().get(1));
        assertEquals("%2026-04-13 a 2026-04-14%", capture.parameters().get(2));
        assertEquals("%2026-04-13%2026-04-14%", capture.parameters().get(3));
    }

    @Test
    void deveBuscarCompletudeDeJanelaDiariaPeloMarcadorDataNaMensagemDoLog() {
        final SqlCapture capture = new SqlCapture();
        final Connection connection = criarConexao(capture, List.of(Map.of("1", 1L)));
        final SqlServerDataQualityQueryAdapter adapter =
            new SqlServerDataQualityQueryAdapter(() -> connection);

        final long incompletos = adapter.contarLinhasIncompletas(
            "fretes",
            LocalDate.of(2026, 4, 14),
            LocalDate.of(2026, 4, 14)
        );

        assertEquals(1L, incompletos);
        assertEquals("fretes", capture.parameters().get(1));
        assertEquals("%Data: 2026-04-14%", capture.parameters().get(2));
        assertEquals("%Per\u00edodo: 2026-04-14 a 2026-04-14%", capture.parameters().get(3));
    }

    @Test
    void devePriorizarAuditoriaEstruturadaDaExecucaoCorrenteParaCompletude() {
        final SqlCapture capture = new SqlCapture();
        final Connection connection = criarConexao(capture, List.of(Map.of("1", 0L)));
        final SqlServerDataQualityQueryAdapter adapter =
            new SqlServerDataQualityQueryAdapter(() -> connection);

        MDC.put(ExecutionContext.MDC_EXECUTION_ID, "exec-123");
        try {
            final long incompletos = adapter.contarLinhasIncompletas(
                "faturas_graphql",
                LocalDate.of(2026, 4, 17),
                LocalDate.of(2026, 4, 23)
            );

            assertEquals(0L, incompletos);
            assertTrue(capture.sql().contains("FROM dbo.sys_execution_audit"));
            assertEquals("exec-123", capture.parameters().get(1));
            assertEquals("faturas_graphql", capture.parameters().get(2));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void deveUsarJanelaPlanejadaDaEntidadeNoFallbackDeLogExterno() {
        final SqlCapture capture = new SqlCapture();
        final Connection connection = criarConexao(capture, List.of(Map.of("1", 0L)));
        final SqlServerDataQualityQueryAdapter adapter =
            new SqlServerDataQualityQueryAdapter(() -> connection);

        ExecutionPlanContext.setPlanos(Map.of(
            "faturas_graphql",
            new ExecutionWindowPlan(
                LocalDate.of(2026, 4, 22),
                LocalDate.of(2026, 4, 23),
                LocalDateTime.of(2026, 4, 22, 0, 0),
                LocalDateTime.of(2026, 4, 23, 23, 59, 59)
            )
        ));
        try {
            final long incompletos = adapter.contarLinhasIncompletas(
                "faturas_graphql",
                LocalDate.of(2026, 4, 17),
                LocalDate.of(2026, 4, 23)
            );

            assertEquals(0L, incompletos);
            assertTrue(capture.sql().contains("FROM dbo.log_extracoes"));
            assertEquals("faturas_graphql", capture.parameters().get(1));
            assertEquals("%2026-04-22 a 2026-04-23%", capture.parameters().get(2));
            assertEquals("%2026-04-22%2026-04-23%", capture.parameters().get(3));
        } finally {
            ExecutionPlanContext.clear();
        }
    }

    private Connection criarConexao(final SqlCapture capture, final List<Map<String, Object>> rows) {
        return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(),
            new Class<?>[]{Connection.class},
            (proxy, method, args) -> {
                if ("prepareStatement".equals(method.getName())) {
                    capture.sql((String) args[0]);
                    return criarPreparedStatement(capture.parameters(), criarResultSet(rows));
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

    private PreparedStatement criarPreparedStatement(final Map<Integer, Object> parameters, final ResultSet resultSet) {
        return (PreparedStatement) Proxy.newProxyInstance(
            PreparedStatement.class.getClassLoader(),
            new Class<?>[]{PreparedStatement.class},
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "setString", "setDate", "setTimestamp", "setInt" -> {
                        parameters.put((Integer) args[0], args[1]);
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
                        final Object chave = args[0];
                        final Object value = chave instanceof String
                            ? rows.get(index[0]).get((String) chave)
                            : rows.get(index[0]).get(String.valueOf(chave));
                        wasNull[0] = value == null;
                        return wasNull[0] ? 0L : ((Number) value).longValue();
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

    private static final class SqlCapture {
        private String sql;
        private final Map<Integer, Object> parameters = new LinkedHashMap<>();

        private String sql() {
            return sql;
        }

        private void sql(final String sql) {
            this.sql = sql;
        }

        private Map<Integer, Object> parameters() {
            return parameters;
        }
    }
}
