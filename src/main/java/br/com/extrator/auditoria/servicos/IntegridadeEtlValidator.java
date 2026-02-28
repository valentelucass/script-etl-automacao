/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/auditoria/servicos/IntegridadeEtlValidator.java
Classe  : IntegridadeEtlValidator (class)
Pacote  : br.com.extrator.auditoria.servicos
Modulo  : Servico de auditoria
Papel   : Implementa responsabilidade de integridade etl validator.

Conecta com:
- CarregadorConfig (util.configuracao)
- GerenciadorConexao (util.banco)
- ConstantesEntidades (util.validacao)

Fluxo geral:
1) Executa regras de validacao de qualidade/ETL.
2) Consolida indicadores e status de auditoria.
3) Publica resultado para relatorio tecnico.

Estrutura interna:
Metodos principais:
- criarSpecs(): instancia ou monta estrutura de dados.
- validarExecucao(...3 args): aplica regras de validacao e consistencia.
- validarExecucao(...4 args): aplica regras de validacao e consistencia.
- registrarFalha(...3 args): grava informacoes de auditoria/log.
- registrarEventoInfo(...3 args): grava informacoes de auditoria/log.
- registrarEventoAviso(...2 args): grava informacoes de auditoria/log.
- resumirMensagem(...1 args): realiza operacao relacionada a "resumir mensagem".
Atributos-chave:
- logger: logger da classe para diagnostico.
- SPECS: campo de estado para "specs".
[DOC-FILE-END]============================================================== */

package br.com.extrator.auditoria.servicos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.util.configuracao.CarregadorConfig;
import br.com.extrator.util.banco.GerenciadorConexao;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Validador estrito de integridade do pipeline ETL.
 * Qualquer divergência é tratada como falha bloqueante.
 */
public class IntegridadeEtlValidator {

    private static final Logger logger = LoggerFactory.getLogger(IntegridadeEtlValidator.class);

    private static final class EntidadeSpec {
        private final String entidade;
        private final String tabela;
        private final String colunaTimestamp;
        private final List<String> chavesUnicas;
        private final List<String> colunasObrigatorias;

        private EntidadeSpec(final String entidade,
                             final String tabela,
                             final String colunaTimestamp,
                             final List<String> chavesUnicas,
                             final List<String> colunasObrigatorias) {
            this.entidade = entidade;
            this.tabela = tabela;
            this.colunaTimestamp = colunaTimestamp;
            this.chavesUnicas = List.copyOf(chavesUnicas);
            this.colunasObrigatorias = List.copyOf(colunasObrigatorias);
        }
    }

    private static final class JanelaLog {
        private final String status;
        private final int registrosExtraidos;
        private final LocalDateTime inicio;
        private final LocalDateTime fim;
        private final String mensagem;

        private JanelaLog(final String status,
                          final int registrosExtraidos,
                          final LocalDateTime inicio,
                          final LocalDateTime fim,
                          final String mensagem) {
            this.status = status;
            this.registrosExtraidos = registrosExtraidos;
            this.inicio = inicio;
            this.fim = fim;
            this.mensagem = mensagem;
        }
    }

    public static final class ResultadoValidacao {
        private final boolean valido;
        private final List<String> falhas;

        public ResultadoValidacao(final boolean valido, final List<String> falhas) {
            this.valido = valido;
            this.falhas = List.copyOf(falhas);
        }

        public boolean isValido() {
            return valido;
        }

        public List<String> getFalhas() {
            return falhas;
        }
    }

    private static final Map<String, EntidadeSpec> SPECS = criarSpecs();

    private static Map<String, EntidadeSpec> criarSpecs() {
        final Map<String, EntidadeSpec> specs = new LinkedHashMap<>();
        specs.put(ConstantesEntidades.USUARIOS_SISTEMA, new EntidadeSpec(
            ConstantesEntidades.USUARIOS_SISTEMA,
            "dim_usuarios",
            "data_atualizacao",
            List.of("user_id"),
            List.of("user_id", "nome", "data_atualizacao")
        ));
        specs.put(ConstantesEntidades.COLETAS, new EntidadeSpec(
            ConstantesEntidades.COLETAS,
            ConstantesEntidades.COLETAS,
            "data_extracao",
            List.of("id"),
            List.of("id", "sequence_code", "metadata", "data_extracao")
        ));
        specs.put(ConstantesEntidades.FRETES, new EntidadeSpec(
            ConstantesEntidades.FRETES,
            ConstantesEntidades.FRETES,
            "data_extracao",
            List.of("id"),
            List.of("id", "metadata", "data_extracao")
        ));
        specs.put(ConstantesEntidades.MANIFESTOS, new EntidadeSpec(
            ConstantesEntidades.MANIFESTOS,
            ConstantesEntidades.MANIFESTOS,
            "data_extracao",
            List.of("sequence_code", "identificador_unico"),
            List.of("sequence_code", "identificador_unico", "metadata", "data_extracao")
        ));
        specs.put(ConstantesEntidades.COTACOES, new EntidadeSpec(
            ConstantesEntidades.COTACOES,
            ConstantesEntidades.COTACOES,
            "data_extracao",
            List.of("sequence_code"),
            List.of("sequence_code", "metadata", "data_extracao")
        ));
        specs.put(ConstantesEntidades.LOCALIZACAO_CARGAS, new EntidadeSpec(
            ConstantesEntidades.LOCALIZACAO_CARGAS,
            ConstantesEntidades.LOCALIZACAO_CARGAS,
            "data_extracao",
            List.of("sequence_number"),
            List.of("sequence_number", "metadata", "data_extracao")
        ));
        specs.put(ConstantesEntidades.CONTAS_A_PAGAR, new EntidadeSpec(
            ConstantesEntidades.CONTAS_A_PAGAR,
            ConstantesEntidades.CONTAS_A_PAGAR,
            "data_extracao",
            List.of("sequence_code"),
            List.of("sequence_code", "metadata", "data_extracao")
        ));
        specs.put(ConstantesEntidades.FATURAS_POR_CLIENTE, new EntidadeSpec(
            ConstantesEntidades.FATURAS_POR_CLIENTE,
            ConstantesEntidades.FATURAS_POR_CLIENTE,
            "data_extracao",
            List.of("unique_id"),
            List.of("unique_id", "metadata", "data_extracao")
        ));
        specs.put(ConstantesEntidades.FATURAS_GRAPHQL, new EntidadeSpec(
            ConstantesEntidades.FATURAS_GRAPHQL,
            ConstantesEntidades.FATURAS_GRAPHQL,
            "data_extracao",
            List.of("id"),
            List.of("id", "metadata", "data_extracao")
        ));
        return Map.copyOf(specs);
    }

    public ResultadoValidacao validarExecucao(final LocalDateTime inicioExecucao,
                                              final LocalDateTime fimExecucao,
                                              final Set<String> entidadesEsperadas) {
        return validarExecucao(inicioExecucao, fimExecucao, entidadesEsperadas, false);
    }

    public ResultadoValidacao validarExecucao(final LocalDateTime inicioExecucao,
                                              final LocalDateTime fimExecucao,
                                              final Set<String> entidadesEsperadas,
                                              final boolean modoLoopDaemon) {
        final List<String> falhas = new ArrayList<>();
        final Set<String> entidades = new LinkedHashSet<>(entidadesEsperadas);

        if (entidades.isEmpty()) {
            registrarFalha(falhas, "SEM_ENTIDADES", "Nenhuma entidade informada para validação de integridade.");
            return new ResultadoValidacao(false, falhas);
        }

        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            for (final String entidade : entidades) {
                final EntidadeSpec spec = SPECS.get(entidade);
                if (spec == null) {
                    registrarFalha(falhas, "ENTIDADE_NAO_SUPORTADA",
                        "Entidade sem spec de validação: " + entidade);
                    continue;
                }

                validarSchema(conexao, spec, falhas);

                final Optional<JanelaLog> logJanela = buscarLogDaExecucao(conexao, entidade, inicioExecucao, fimExecucao);
                if (logJanela.isEmpty()) {
                    registrarFalha(falhas, "LOG_AUSENTE",
                        "Sem log_extracoes para entidade '" + entidade + "' na execução atual.");
                    continue;
                }

                final JanelaLog logEntidade = logJanela.get();
                if (!ConstantesEntidades.STATUS_COMPLETO.equals(logEntidade.status)) {
                    final String detalheMensagem = logEntidade.mensagem == null || logEntidade.mensagem.isBlank()
                        ? ""
                        : " mensagem=\"" + resumirMensagem(logEntidade.mensagem) + "\".";
                    registrarFalha(falhas, "STATUS_NAO_COMPLETO",
                        "Entidade '" + entidade + "' com status " + logEntidade.status
                            + " em log_extracoes." + detalheMensagem);
                    continue;
                }

                final int totalBanco = contarRegistrosNoIntervalo(conexao, spec, logEntidade.inicio, logEntidade.fim);
                if (totalBanco != logEntidade.registrosExtraidos) {
                    registrarFalha(falhas, "DIVERGENCIA_CONTAGEM",
                        String.format("Entidade '%s': origem=%d, destino=%d (janela %s até %s).",
                            entidade, logEntidade.registrosExtraidos, totalBanco, logEntidade.inicio, logEntidade.fim));
                } else {
                    registrarEventoInfo("CONTAGEM_OK", entidade,
                        "origem=" + logEntidade.registrosExtraidos + ", destino=" + totalBanco);
                }

                final int chavesNulas = contarChavesNulas(conexao, spec, logEntidade.inicio, logEntidade.fim);
                if (chavesNulas > 0) {
                    registrarFalha(falhas, "CHAVE_NULA",
                        "Entidade '" + entidade + "' possui " + chavesNulas + " registro(s) com chave nula.");
                }

                final int gruposDuplicados = contarGruposDuplicados(conexao, spec, logEntidade.inicio, logEntidade.fim);
                if (gruposDuplicados > 0) {
                    registrarFalha(falhas, "DUPLICIDADE_CHAVE",
                        "Entidade '" + entidade + "' possui " + gruposDuplicados + " grupo(s) duplicado(s) por chave.");
                }
            }

            validarIntegridadeReferencial(conexao, inicioExecucao, fimExecucao, entidades, falhas, modoLoopDaemon);
        } catch (final SQLException e) {
            registrarFalha(falhas, "ERRO_SQL_VALIDACAO",
                "Falha SQL durante validação de integridade: " + e.getMessage());
        }

        return new ResultadoValidacao(falhas.isEmpty(), falhas);
    }

    private void validarSchema(final Connection conexao,
                               final EntidadeSpec spec,
                               final List<String> falhas) throws SQLException {
        if (!tabelaExiste(conexao, spec.tabela)) {
            registrarFalha(falhas, "TABELA_AUSENTE",
                "Tabela '" + spec.tabela + "' inexistente para entidade '" + spec.entidade + "'.");
            return;
        }

        final List<String> colunasFaltantes = new ArrayList<>();
        for (final String coluna : spec.colunasObrigatorias) {
            if (!colunaExiste(conexao, spec.tabela, coluna)) {
                colunasFaltantes.add(coluna);
            }
        }

        if (!colunasFaltantes.isEmpty()) {
            registrarFalha(falhas, "SCHEMA_INCOMPATIVEL",
                "Tabela '" + spec.tabela + "' sem colunas obrigatórias: " + String.join(", ", colunasFaltantes));
        } else {
            registrarEventoInfo("SCHEMA_OK", spec.entidade, "tabela=" + spec.tabela);
        }
    }

    private Optional<JanelaLog> buscarLogDaExecucao(final Connection conexao,
                                                    final String entidade,
                                                    final LocalDateTime inicioExecucao,
                                                    final LocalDateTime fimExecucao) throws SQLException {
        final String sql = """
            SELECT TOP 1 status_final, registros_extraidos, timestamp_inicio, timestamp_fim, mensagem
            FROM dbo.log_extracoes
            WHERE entidade = ?
              AND timestamp_fim >= ?
              AND timestamp_inicio <= ?
            ORDER BY timestamp_fim DESC
            """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, entidade);
            stmt.setTimestamp(2, Timestamp.valueOf(inicioExecucao));
            stmt.setTimestamp(3, Timestamp.valueOf(fimExecucao));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new JanelaLog(
                        rs.getString("status_final"),
                        rs.getInt("registros_extraidos"),
                        rs.getTimestamp("timestamp_inicio").toLocalDateTime(),
                        rs.getTimestamp("timestamp_fim").toLocalDateTime(),
                        rs.getString("mensagem")
                    ));
                }
            }
        }
        return Optional.empty();
    }

    private int contarRegistrosNoIntervalo(final Connection conexao,
                                           final EntidadeSpec spec,
                                           final LocalDateTime inicio,
                                           final LocalDateTime fim) throws SQLException {
        final String sql = String.format(
            "SELECT COUNT(*) FROM dbo.%s WHERE %s >= ? AND %s <= ?",
            spec.tabela, spec.colunaTimestamp, spec.colunaTimestamp
        );
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(inicio));
            stmt.setTimestamp(2, Timestamp.valueOf(fim));
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int contarChavesNulas(final Connection conexao,
                                  final EntidadeSpec spec,
                                  final LocalDateTime inicio,
                                  final LocalDateTime fim) throws SQLException {
        final String whereChavesNulas = String.join(" OR ",
            spec.chavesUnicas.stream().map(c -> c + " IS NULL").toList());

        final String sql = String.format(
            "SELECT COUNT(*) FROM dbo.%s WHERE %s >= ? AND %s <= ? AND (%s)",
            spec.tabela, spec.colunaTimestamp, spec.colunaTimestamp, whereChavesNulas
        );

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(inicio));
            stmt.setTimestamp(2, Timestamp.valueOf(fim));
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int contarGruposDuplicados(final Connection conexao,
                                       final EntidadeSpec spec,
                                       final LocalDateTime inicio,
                                       final LocalDateTime fim) throws SQLException {
        final String colunasChave = String.join(", ", spec.chavesUnicas);
        final String sql = String.format(
            """
            SELECT COUNT(*) FROM (
                SELECT %s, COUNT(*) AS qtd
                FROM dbo.%s
                WHERE %s >= ? AND %s <= ?
                GROUP BY %s
                HAVING COUNT(*) > 1
            ) d
            """,
            colunasChave,
            spec.tabela,
            spec.colunaTimestamp,
            spec.colunaTimestamp,
            colunasChave
        );

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(inicio));
            stmt.setTimestamp(2, Timestamp.valueOf(fim));
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void validarIntegridadeReferencial(final Connection conexao,
                                               final LocalDateTime inicioExecucao,
                                               final LocalDateTime fimExecucao,
                                               final Set<String> entidades,
                                               final List<String> falhas,
                                               final boolean modoLoopDaemon) throws SQLException {
        if (entidades.contains(ConstantesEntidades.MANIFESTOS) && entidades.contains(ConstantesEntidades.COLETAS)) {
            final Optional<JanelaLog> logManifestos = buscarLogDaExecucao(
                conexao, ConstantesEntidades.MANIFESTOS, inicioExecucao, fimExecucao);
            final Optional<JanelaLog> logColetas = buscarLogDaExecucao(
                conexao, ConstantesEntidades.COLETAS, inicioExecucao, fimExecucao);

            final String sqlManifestosOrfaos = """
                SELECT COUNT(*)
                FROM dbo.manifestos m
                WHERE m.data_extracao >= ? AND m.data_extracao <= ?
                  AND m.pick_sequence_code IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM dbo.coletas c
                      WHERE c.sequence_code = m.pick_sequence_code
                  )
                """;
            final int orfaosManifestos = executarCount(conexao, sqlManifestosOrfaos, inicioExecucao, fimExecucao);
            if (orfaosManifestos > 0) {
                final String sqlTotalManifestosComPick = """
                    SELECT COUNT(*)
                    FROM dbo.manifestos m
                    WHERE m.data_extracao >= ? AND m.data_extracao <= ?
                      AND m.pick_sequence_code IS NOT NULL
                    """;
                final int totalManifestosComPick = executarCount(conexao, sqlTotalManifestosComPick, inicioExecucao, fimExecucao);
                final double percentualOrfaos = (orfaosManifestos * 100.0) / Math.max(1, totalManifestosComPick);
                final int limiteOrfaosQuantidade = CarregadorConfig.obterMaxOrfaosManifestosTolerados();
                final double limiteOrfaosPercentual = CarregadorConfig.obterPercentualMaxOrfaosManifestosTolerados();

                final String sqlManifestosOrfaosAmostra = """
                    SELECT TOP 10 m.pick_sequence_code
                    FROM dbo.manifestos m
                    WHERE m.data_extracao >= ? AND m.data_extracao <= ?
                      AND m.pick_sequence_code IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1
                          FROM dbo.coletas c
                          WHERE c.sequence_code = m.pick_sequence_code
                      )
                    ORDER BY m.pick_sequence_code
                    """;
                final List<Long> amostraOrfaos = executarListaLong(conexao, sqlManifestosOrfaosAmostra, inicioExecucao, fimExecucao);
                final String contextoManifestos = logManifestos
                    .map(log -> String.format("status=%s, registros=%d, janela=[%s .. %s], mensagem=%s",
                        log.status, log.registrosExtraidos, log.inicio, log.fim, resumirMensagem(log.mensagem)))
                    .orElse("sem_log");
                final String contextoColetas = logColetas
                    .map(log -> String.format("status=%s, registros=%d, janela=[%s .. %s], mensagem=%s",
                        log.status, log.registrosExtraidos, log.inicio, log.fim, resumirMensagem(log.mensagem)))
                    .orElse("sem_log");
                final boolean coletasVaziasNaExecucao = logColetas.isPresent()
                    && logColetas.get().registrosExtraidos == 0;
                final String codigoFalha = coletasVaziasNaExecucao
                    ? "INTEGRIDADE_REFERENCIAL_MANIFESTOS_JANELA"
                    : "INTEGRIDADE_REFERENCIAL_MANIFESTOS";
                final String sugestao = coletasVaziasNaExecucao
                    ? "Possivel desalinhamento temporal entre filtros (manifestos x coletas). "
                        + "Recomendado reprocessar coletas com janela ampliada (requestDate/serviceDate)."
                    : "Validar origem e carga de coletas para os sequenceCodes ausentes.";

                final String detalheReferencial = "Manifestos orfaos (pick_sequence_code sem coleta): " + orfaosManifestos
                    + " | total_manifestos_com_pick=" + totalManifestosComPick
                    + " | percentual_orfaos=" + String.format(java.util.Locale.US, "%.2f", percentualOrfaos) + "%"
                    + " | limites_tolerancia={" + limiteOrfaosQuantidade + "; " + String.format(java.util.Locale.US, "%.2f", limiteOrfaosPercentual) + "%}"
                    + " | amostra_pick_sequence_code=" + amostraOrfaos
                    + " | contexto_manifestos={" + contextoManifestos + "}"
                    + " | contexto_coletas={" + contextoColetas + "}"
                    + " | acao_recomendada=" + sugestao;

                final boolean dentroDaTolerancia = orfaosManifestos <= limiteOrfaosQuantidade
                    && percentualOrfaos <= limiteOrfaosPercentual;
                if (dentroDaTolerancia || modoLoopDaemon) {
                    final String codigoAviso = dentroDaTolerancia
                        ? codigoFalha + "_TOLERADO"
                        : codigoFalha + "_ALERTA_LOOP";
                    final String detalheAviso = (!dentroDaTolerancia && modoLoopDaemon)
                        ? detalheReferencial + " | modo_loop_daemon=true"
                        : detalheReferencial;
                    registrarEventoAviso(codigoAviso, detalheAviso);
                } else {
                    registrarFalha(falhas, codigoFalha, detalheReferencial);
                }
            } else {
                registrarEventoInfo("REFERENCIAL_OK", ConstantesEntidades.MANIFESTOS,
                    "pick_sequence_code vinculado a coletas.sequence_code");
            }
        }

        if (entidades.contains(ConstantesEntidades.FRETES) && entidades.contains(ConstantesEntidades.FATURAS_GRAPHQL)) {
            final String sqlFretesOrfaos = """
                SELECT COUNT(*)
                FROM dbo.fretes f
                WHERE f.data_extracao >= ? AND f.data_extracao <= ?
                  AND f.accounting_credit_id IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM dbo.faturas_graphql fg
                      WHERE fg.id = f.accounting_credit_id
                  )
                """;
            final int orfaosFretes = executarCount(conexao, sqlFretesOrfaos, inicioExecucao, fimExecucao);
            if (orfaosFretes > 0) {
                final String sqlFretesOrfaosAmostra = """
                    SELECT TOP 10 f.accounting_credit_id
                    FROM dbo.fretes f
                    WHERE f.data_extracao >= ? AND f.data_extracao <= ?
                      AND f.accounting_credit_id IS NOT NULL
                      AND NOT EXISTS (
                          SELECT 1
                          FROM dbo.faturas_graphql fg
                          WHERE fg.id = f.accounting_credit_id
                      )
                    ORDER BY f.accounting_credit_id
                    """;
                final List<Long> amostraOrfaos = executarListaLong(conexao, sqlFretesOrfaosAmostra, inicioExecucao, fimExecucao);
                registrarFalha(falhas, "INTEGRIDADE_REFERENCIAL_FRETES",
                    "Fretes órfãos (accounting_credit_id sem faturas_graphql.id): " + orfaosFretes
                        + " | amostra_accounting_credit_id=" + amostraOrfaos);
            } else {
                registrarEventoInfo("REFERENCIAL_OK", ConstantesEntidades.FRETES,
                    "accounting_credit_id vinculado a faturas_graphql.id");
            }
        }
    }

    private int executarCount(final Connection conexao,
                              final String sql,
                              final LocalDateTime inicioExecucao,
                              final LocalDateTime fimExecucao) throws SQLException {
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(inicioExecucao));
            stmt.setTimestamp(2, Timestamp.valueOf(fimExecucao));
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private List<Long> executarListaLong(final Connection conexao,
                                         final String sql,
                                         final LocalDateTime inicioExecucao,
                                         final LocalDateTime fimExecucao) throws SQLException {
        final List<Long> valores = new ArrayList<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(inicioExecucao));
            stmt.setTimestamp(2, Timestamp.valueOf(fimExecucao));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final long valor = rs.getLong(1);
                    if (!rs.wasNull()) {
                        valores.add(valor);
                    }
                }
            }
        }
        return valores;
    }

    private boolean tabelaExiste(final Connection conexao, final String tabela) throws SQLException {
        final String sql = """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = ?
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, tabela);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private boolean colunaExiste(final Connection conexao, final String tabela, final String coluna) throws SQLException {
        final String sql = """
            SELECT COUNT(*)
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = 'dbo' AND TABLE_NAME = ? AND COLUMN_NAME = ?
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, tabela);
            stmt.setString(2, coluna);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private void registrarFalha(final List<String> falhas, final String codigo, final String detalhe) {
        final String mensagem = codigo + " | " + detalhe;
        falhas.add(mensagem);
        logger.error("INTEGRIDADE_ETL | resultado=FALHA | codigo={} | detalhe={}", codigo, detalhe);
    }

    private void registrarEventoInfo(final String codigo, final String entidade, final String detalhe) {
        logger.info("INTEGRIDADE_ETL | resultado=OK | codigo={} | entidade={} | detalhe={}", codigo, entidade, detalhe);
    }

    private void registrarEventoAviso(final String codigo, final String detalhe) {
        logger.warn("INTEGRIDADE_ETL | resultado=ALERTA | codigo={} | detalhe={}", codigo, detalhe);
    }

    private String resumirMensagem(final String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            return "sem_mensagem";
        }
        final String normalizada = mensagem.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalizada.length() <= 220) {
            return normalizada;
        }
        return normalizada.substring(0, 217) + "...";
    }
}
