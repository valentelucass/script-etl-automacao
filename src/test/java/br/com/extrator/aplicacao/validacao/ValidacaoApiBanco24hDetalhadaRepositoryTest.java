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
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    @Test
    void deveAncorarValidacaoAbertaAoInstanteInformado() throws SQLException {
        final LocalDateTime tetoValidacaoAberta = LocalDateTime.of(2026, 3, 27, 7, 47, 33);
        final ValidacaoApiBanco24hDetalhadaRepository repositoryComAnchor =
            new ValidacaoApiBanco24hDetalhadaRepository(
                LoggerConsole.getLogger(ValidacaoApiBanco24hDetalhadaRepositoryTest.class),
                new ValidacaoApiBanco24hDetalhadaMetadataHasher(),
                () -> Optional.of(tetoValidacaoAberta)
            );
        final Map<Integer, Object> captured = new LinkedHashMap<>();
        final LocalDateTime inicio = LocalDateTime.of(2026, 3, 27, 7, 44, 30);
        final LocalDateTime fim = LocalDateTime.of(2026, 3, 27, 7, 47, 22);
        final Connection conexao = criarConexao(sql -> {
            assertTrue(sql.contains("timestamp_fim <= ?"));
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

        final Optional<JanelaExecucao> janela = repositoryComAnchor.buscarUltimaJanelaCompletaDoDia(
            conexao,
            ConstantesEntidades.FRETES,
            LocalDate.of(2026, 3, 27),
            LocalDate.of(2026, 3, 26),
            LocalDate.of(2026, 3, 27),
            true
        );

        assertTrue(janela.isPresent());
        assertEquals(inicio, janela.get().inicio());
        assertEquals(fim, janela.get().fim());
        assertEquals(Timestamp.valueOf(tetoValidacaoAberta), captured.get(5));
    }

    @Test
    void deveFiltrarUsuariosSistemaPorOrigemNoPeriodoEPorJanelaDeExecucaoQuandoNaoHouverOrigem() throws SQLException {
        final Map<Integer, Object> captured = new LinkedHashMap<>();
        final Connection conexao = criarConexao(sql -> criarPreparedStatement(captured, criarResultSet(List.of())));
        final JanelaExecucao janela = new JanelaExecucao(
            LocalDateTime.of(2026, 4, 8, 13, 12, 2),
            LocalDateTime.of(2026, 4, 8, 13, 12, 33),
            true
        );
        final LocalDate periodoInicio = LocalDate.of(2026, 4, 7);
        final LocalDate periodoFim = LocalDate.of(2026, 4, 8);

        repository.carregarChavesBancoNaJanela(
            conexao,
            ConstantesEntidades.USUARIOS_SISTEMA,
            janela,
            periodoInicio,
            periodoFim
        );

        assertEquals(Timestamp.valueOf(periodoInicio.atStartOfDay()), captured.get(1));
        assertEquals(Timestamp.valueOf(periodoFim.atTime(java.time.LocalTime.MAX)), captured.get(2));
        assertEquals(Timestamp.valueOf(janela.inicio()), captured.get(3));
        assertEquals(Timestamp.valueOf(janela.fim()), captured.get(4));
    }

    @Test
    void deveAplicarFiltroEstritoPorJanelaEstruturadaEmContasAPagar() throws SQLException {
        final Map<Integer, Object> captured = new LinkedHashMap<>();
        final Connection conexao = criarConexao(sql -> {
            assertTrue(sql.contains("FROM dbo.contas_a_pagar"));
            assertTrue(sql.contains("data_extracao >= ? AND data_extracao <= ?"));
            assertTrue(!sql.contains("COALESCE(issue_date"));
            return criarPreparedStatement(captured, criarResultSet(List.of()));
        });
        final JanelaExecucao janela = new JanelaExecucao(
            LocalDateTime.of(2026, 4, 13, 17, 46, 14),
            LocalDateTime.of(2026, 4, 13, 17, 46, 39),
            true
        );

        repository.carregarChavesBancoNaJanela(
            conexao,
            ConstantesEntidades.CONTAS_A_PAGAR,
            janela,
            LocalDate.of(2026, 4, 12),
            LocalDate.of(2026, 4, 12),
            true
        );

        assertEquals(Timestamp.valueOf(janela.inicio()), captured.get(1));
        assertEquals(Timestamp.valueOf(janela.fim()), captured.get(2));
        assertEquals(2, captured.size());
    }

    @Test
    void deveAplicarFiltroEstritoPorJanelaEstruturadaEmFretes() throws SQLException {
        final Map<Integer, Object> captured = new LinkedHashMap<>();
        final Connection conexao = criarConexao(sql -> {
            assertTrue(sql.contains("FROM dbo.fretes"));
            assertTrue(sql.contains("data_extracao >= ? AND data_extracao <= ?"));
            assertTrue(!sql.contains("service_date"));
            return criarPreparedStatement(captured, criarResultSet(List.of()));
        });
        final JanelaExecucao janela = new JanelaExecucao(
            LocalDateTime.of(2026, 4, 15, 1, 4, 25),
            LocalDateTime.of(2026, 4, 15, 1, 15, 13),
            true
        );

        repository.carregarChavesBancoNaJanela(
            conexao,
            ConstantesEntidades.FRETES,
            janela,
            LocalDate.of(2026, 4, 9),
            LocalDate.of(2026, 4, 15),
            true
        );

        assertEquals(Timestamp.valueOf(janela.inicio()), captured.get(1));
        assertEquals(Timestamp.valueOf(janela.fim()), captured.get(2));
        assertEquals(2, captured.size());
    }

    @Test
    void deveAplicarFiltroEstritoPorJanelaEstruturadaEmColetas() throws SQLException {
        final Map<Integer, Object> captured = new LinkedHashMap<>();
        final Connection conexao = criarConexao(sql -> {
            assertTrue(sql.contains("FROM dbo.coletas"));
            assertTrue(sql.contains("data_extracao >= ? AND data_extracao <= ?"));
            assertTrue(!sql.contains("request_date BETWEEN"));
            return criarPreparedStatement(captured, criarResultSet(List.of()));
        });
        final JanelaExecucao janela = new JanelaExecucao(
            LocalDateTime.of(2026, 4, 15, 1, 2, 14),
            LocalDateTime.of(2026, 4, 15, 1, 4, 23),
            true
        );

        repository.carregarChavesBancoNaJanela(
            conexao,
            ConstantesEntidades.COLETAS,
            janela,
            LocalDate.of(2026, 4, 9),
            LocalDate.of(2026, 4, 15),
            true
        );

        assertEquals(Timestamp.valueOf(janela.inicio()), captured.get(1));
        assertEquals(Timestamp.valueOf(janela.fim()), captured.get(2));
        assertEquals(2, captured.size());
    }

    @Test
    void deveAplicarFiltroEstritoPorJanelaEstruturadaEmLocalizacaoCargas() throws SQLException {
        final Map<Integer, Object> captured = new LinkedHashMap<>();
        final Connection conexao = criarConexao(sql -> {
            assertTrue(sql.contains("FROM dbo.localizacao_cargas"));
            assertTrue(sql.contains("data_extracao >= ? AND data_extracao <= ?"));
            assertTrue(!sql.contains("predicted_delivery_at"));
            return criarPreparedStatement(captured, criarResultSet(List.of()));
        });
        final JanelaExecucao janela = new JanelaExecucao(
            LocalDateTime.of(2026, 4, 15, 1, 17, 53),
            LocalDateTime.of(2026, 4, 15, 1, 19, 32),
            true
        );

        repository.carregarChavesBancoNaJanela(
            conexao,
            ConstantesEntidades.LOCALIZACAO_CARGAS,
            janela,
            LocalDate.of(2026, 4, 9),
            LocalDate.of(2026, 4, 15),
            true
        );

        assertEquals(Timestamp.valueOf(janela.inicio()), captured.get(1));
        assertEquals(Timestamp.valueOf(janela.fim()), captured.get(2));
        assertEquals(2, captured.size());
    }

    @Test
    void deveUsarChaveIdentificadorUnicoParaInventarioESinistros() throws SQLException {
        final Connection conexaoInventario = criarConexao(sql -> {
            assertTrue(sql.contains("FROM dbo.inventario"));
            assertTrue(sql.contains("identificador_unico AS chave"));
            return criarPreparedStatement(new LinkedHashMap<>(), criarResultSet(List.of()));
        });
        final Connection conexaoSinistros = criarConexao(sql -> {
            assertTrue(sql.contains("FROM dbo.sinistros"));
            assertTrue(sql.contains("identificador_unico AS chave"));
            return criarPreparedStatement(new LinkedHashMap<>(), criarResultSet(List.of()));
        });
        final JanelaExecucao janela = new JanelaExecucao(
            LocalDateTime.of(2026, 4, 22, 10, 0),
            LocalDateTime.of(2026, 4, 22, 10, 5),
            true
        );

        repository.carregarChavesBancoNaJanela(
            conexaoInventario,
            ConstantesEntidades.INVENTARIO,
            janela,
            LocalDate.of(2026, 4, 22),
            LocalDate.of(2026, 4, 23)
        );
        repository.carregarChavesBancoNaJanela(
            conexaoSinistros,
            ConstantesEntidades.SINISTROS,
            janela,
            LocalDate.of(2026, 4, 22),
            LocalDate.of(2026, 4, 23)
        );
    }

    @Test
    void devePreservarPickSequenceCodeOriginalDoMetadataNaChaveDeManifestos() {
        final String metadata = """
            {
              "mft_pfs_pck_sequence_code": 71920,
              "mft_mfs_number": 1503
            }
            """;

        final String chave = ValidacaoApiBanco24hDetalhadaRepository.montarChaveManifestoValidacao(
            48831L,
            null,
            null,
            metadata
        );

        assertEquals("48831|71920|1503", chave);
    }

    @Test
    void deveCarregarChavesDeManifestosUsandoPickPreservadoNoMetadataQuandoColunaEstiverNormalizada() throws SQLException {
        final Connection conexao = criarConexao(sql -> {
            assertTrue(sql.contains("FROM dbo.manifestos"));
            assertTrue(sql.contains("sequence_code"));
            assertTrue(sql.contains("pick_sequence_code"));
            assertTrue(sql.contains("mdfe_number"));
            assertTrue(sql.contains("metadata"));
            final Map<String, Object> row = new LinkedHashMap<>();
            row.put("sequence_code", 48831L);
            row.put("pick_sequence_code", null);
            row.put("mdfe_number", 1503);
            row.put("metadata", "{\"mft_pfs_pck_sequence_code\":71920,\"mft_mfs_number\":1503}");
            return criarPreparedStatement(
                new LinkedHashMap<>(),
                criarResultSet(List.of(row))
            );
        });
        final JanelaExecucao janela = new JanelaExecucao(
            LocalDateTime.of(2026, 4, 23, 9, 0),
            LocalDateTime.of(2026, 4, 23, 9, 5),
            true
        );

        final Set<String> chaves = repository.carregarChavesBancoNaJanela(
            conexao,
            ConstantesEntidades.MANIFESTOS,
            janela,
            LocalDate.of(2026, 4, 22),
            LocalDate.of(2026, 4, 23)
        );

        assertEquals(Set.of("48831|71920|1503"), chaves);
    }

    @Test
    void deveResolverExecutionUuidAncoraAPartirDaAuditoriaEstruturada() throws SQLException {
        final Map<Integer, Object> captured = new LinkedHashMap<>();
        final Connection conexao = criarConexao(sql -> {
            if (sql.contains("OBJECT_ID")) {
                return criarPreparedStatement(
                    new LinkedHashMap<>(),
                    criarResultSet(List.of(Map.of("", 1, "1", 1)))
                );
            }
            assertTrue(sql.contains("FROM dbo.sys_execution_audit"));
            assertTrue(sql.contains("COUNT(DISTINCT entidade)"));
            return criarPreparedStatement(
                captured,
                criarResultSet(List.of(Map.of("execution_uuid", "exec-intervalo-mais-recente")))
            );
        });
        final Set<String> entidades = new LinkedHashSet<>(List.of(
            ConstantesEntidades.FRETES,
            ConstantesEntidades.COLETAS,
            ConstantesEntidades.COTACOES
        ));
        final LocalDate periodoInicio = LocalDate.of(2026, 4, 7);
        final LocalDate periodoFim = LocalDate.of(2026, 4, 8);
        final LocalDateTime inicioValidacao = LocalDateTime.of(2026, 4, 8, 14, 29, 37);

        final Optional<String> executionUuid = repository.resolverExecutionUuidAncora(
            conexao,
            entidades,
            periodoInicio,
            periodoFim,
            inicioValidacao
        );

        assertTrue(executionUuid.isPresent());
        assertEquals("exec-intervalo-mais-recente", executionUuid.get());
        assertEquals(Timestamp.valueOf(inicioValidacao), captured.get(1));
        assertEquals(Timestamp.valueOf(periodoInicio.atStartOfDay()), captured.get(2));
        assertEquals(Timestamp.valueOf(periodoFim.plusDays(1).atStartOfDay()), captured.get(3));
        assertEquals(ConstantesEntidades.FRETES, captured.get(4));
        assertEquals(ConstantesEntidades.COLETAS, captured.get(5));
        assertEquals(ConstantesEntidades.COTACOES, captured.get(6));
    }

    @Test
    void deveResolverExecutionUuidAncoraRecenteSemExigirMesmaJanela() throws SQLException {
        final Map<Integer, Object> captured = new LinkedHashMap<>();
        final Connection conexao = criarConexao(sql -> {
            if (sql.contains("OBJECT_ID")) {
                return criarPreparedStatement(
                    new LinkedHashMap<>(),
                    criarResultSet(List.of(Map.of("", 1, "1", 1)))
                );
            }
            assertTrue(sql.contains("FROM dbo.sys_execution_audit"));
            assertTrue(!sql.contains("janela_consulta_inicio = ?"));
            return criarPreparedStatement(
                captured,
                criarResultSet(List.of(Map.of("execution_uuid", "exec-recente")) )
            );
        });
        final Set<String> entidades = new LinkedHashSet<>(List.of(
            ConstantesEntidades.FRETES,
            ConstantesEntidades.COLETAS
        ));
        final LocalDateTime inicioValidacao = LocalDateTime.of(2026, 4, 15, 2, 10, 5);

        final Optional<String> executionUuid = repository.resolverExecutionUuidAncoraRecente(
            conexao,
            entidades,
            inicioValidacao
        );

        assertTrue(executionUuid.isPresent());
        assertEquals("exec-recente", executionUuid.get());
        assertEquals(Timestamp.valueOf(inicioValidacao), captured.get(1));
        assertEquals(ConstantesEntidades.FRETES, captured.get(2));
        assertEquals(ConstantesEntidades.COLETAS, captured.get(3));
    }

    @Test
    void deveCarregarPeriodoConsultaDaExecucaoEstruturada() throws SQLException {
        final LocalDateTime inicio = LocalDateTime.of(2026, 4, 9, 0, 0);
        final LocalDateTime fim = LocalDateTime.of(2026, 4, 15, 23, 59, 59);
        final Connection conexao = criarConexao(sql -> {
            if (sql.contains("OBJECT_ID")) {
                return criarPreparedStatement(
                    new LinkedHashMap<>(),
                    criarResultSet(List.of(Map.of("", 1, "1", 1)))
                );
            }
            assertTrue(sql.contains("janela_consulta_inicio"));
            return criarPreparedStatement(
                new LinkedHashMap<>(),
                criarResultSet(List.of(Map.of(
                    "janela_consulta_inicio", Timestamp.valueOf(inicio),
                    "janela_consulta_fim", Timestamp.valueOf(fim)
                )))
            );
        });

        final Optional<ValidacaoApiBanco24hDetalhadaTypes.PeriodoConsulta> periodo =
            repository.buscarPeriodoConsultaDaExecucao(
                conexao,
                "exec-estruturada",
                ConstantesEntidades.MANIFESTOS
            );

        assertTrue(periodo.isPresent());
        assertEquals(LocalDate.of(2026, 4, 9), periodo.get().inicio());
        assertEquals(LocalDate.of(2026, 4, 15), periodo.get().fim());
    }

    @Test
    void deveNormalizarFimExclusivoArredondadoPeloSqlServerAoCarregarPeriodoConsulta() throws SQLException {
        final LocalDateTime inicio = LocalDateTime.of(2026, 4, 9, 0, 0);
        final LocalDateTime fimExclusivo = LocalDateTime.of(2026, 4, 16, 0, 0);
        final Connection conexao = criarConexao(sql -> {
            if (sql.contains("OBJECT_ID")) {
                return criarPreparedStatement(
                    new LinkedHashMap<>(),
                    criarResultSet(List.of(Map.of("", 1, "1", 1)))
                );
            }
            return criarPreparedStatement(
                new LinkedHashMap<>(),
                criarResultSet(List.of(Map.of(
                    "janela_consulta_inicio", Timestamp.valueOf(inicio),
                    "janela_consulta_fim", Timestamp.valueOf(fimExclusivo)
                )))
            );
        });

        final Optional<ValidacaoApiBanco24hDetalhadaTypes.PeriodoConsulta> periodo =
            repository.buscarPeriodoConsultaDaExecucao(
                conexao,
                "exec-exclusiva",
                ConstantesEntidades.FRETES
            );

        assertTrue(periodo.isPresent());
        assertEquals(LocalDate.of(2026, 4, 9), periodo.get().inicio());
        assertEquals(LocalDate.of(2026, 4, 15), periodo.get().fim());
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
                    case "getInt" -> {
                        final Object chave = args[0];
                        final Object value = chave instanceof String
                            ? rows.get(index[0]).get((String) chave)
                            : rows.get(index[0]).get(String.valueOf(chave));
                        wasNull[0] = value == null;
                        return wasNull[0] ? 0 : ((Number) value).intValue();
                    }
                    case "getString" -> {
                        final Object chave = args[0];
                        final Object value = chave instanceof String
                            ? rows.get(index[0]).get((String) chave)
                            : rows.get(index[0]).get(String.valueOf(chave));
                        wasNull[0] = value == null;
                        return value == null ? null : value.toString();
                    }
                    case "getTimestamp" -> {
                        final Object chave = args[0];
                        final Object value = chave instanceof String
                            ? rows.get(index[0]).get((String) chave)
                            : rows.get(index[0]).get(String.valueOf(chave));
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
