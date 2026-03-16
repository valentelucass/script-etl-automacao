package br.com.extrator.observabilidade.servicos;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/servicos/IntegridadeEtlValidator.java
Classe  : IntegridadeEtlValidator (class)
Pacote  : br.com.extrator.observabilidade.servicos
Modulo  : Observabilidade - Servico
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
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.suporte.banco.GerenciadorConexao;
import br.com.extrator.suporte.configuracao.ConfigEtl;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public class IntegridadeEtlValidator {

    private static final Logger logger = LoggerFactory.getLogger(IntegridadeEtlValidator.class);
    private static final Pattern PADRAO_METRICA_LOG = Pattern.compile("\\b%s=(-?\\d+)\\b");
    private static final Map<String, IntegridadeEtlSpec> SPECS = IntegridadeEtlSpecCatalog.carregarSpecs();
    private final IntegridadeEtlSqlSupport sqlSupport = new IntegridadeEtlSqlSupport();

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
            registrarFalha(falhas, "SEM_ENTIDADES", "Nenhuma entidade informada para validacao de integridade.");
            return new ResultadoValidacao(false, falhas);
        }

        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            for (String entidade : entidades) {
                final IntegridadeEtlSpec spec = SPECS.get(entidade);
                if (spec == null) {
                    registrarFalha(falhas, "ENTIDADE_NAO_SUPORTADA", "Entidade sem spec de validacao: " + entidade);
                    continue;
                }

                validarSchema(conexao, spec, falhas);

                final Optional<IntegridadeEtlLogWindow> logJanela =
                    buscarLogDaExecucao(conexao, entidade, inicioExecucao, fimExecucao);
                if (logJanela.isEmpty()) {
                    registrarFalha(falhas, "LOG_AUSENTE",
                        "Sem log_extracoes para entidade '" + entidade + "' na execucao atual.");
                    continue;
                }

                final IntegridadeEtlLogWindow logEntidade = logJanela.get();
                if (!ConstantesEntidades.STATUS_COMPLETO.equals(logEntidade.status())) {
                    final String detalheMensagem = logEntidade.mensagem() == null || logEntidade.mensagem().isBlank()
                        ? ""
                        : " mensagem=\"" + resumirMensagem(logEntidade.mensagem()) + "\".";
                    registrarFalha(falhas, "STATUS_NAO_COMPLETO",
                        "Entidade '" + entidade + "' com status " + logEntidade.status()
                            + " em log_extracoes." + detalheMensagem);
                    continue;
                }

                final Integer esperadoPersistido = extrairNumeroCampoMensagem(logEntidade.mensagem(), "db_persisted");
                final int origemEsperada = esperadoPersistido != null
                    ? esperadoPersistido
                    : logEntidade.registrosExtraidos();
                final int totalBanco = contarRegistrosNoIntervalo(
                    conexao, spec, logEntidade.inicio(), logEntidade.fim()
                );
                if (totalBanco != origemEsperada) {
                    registrarFalha(falhas, "DIVERGENCIA_CONTAGEM",
                        String.format("Entidade '%s': origem=%d, destino=%d (janela %s ate %s).",
                            entidade,
                            origemEsperada,
                            totalBanco,
                            logEntidade.inicio(),
                            logEntidade.fim()));
                } else {
                    registrarEventoInfo("CONTAGEM_OK", entidade,
                        "origem=" + origemEsperada + ", destino=" + totalBanco);
                }

                final int chavesNulas = contarChavesNulas(conexao, spec, logEntidade.inicio(), logEntidade.fim());
                if (chavesNulas > 0) {
                    registrarFalha(falhas, "CHAVE_NULA",
                        "Entidade '" + entidade + "' possui " + chavesNulas + " registro(s) com chave nula.");
                }

                final int gruposDuplicados = contarGruposDuplicados(
                    conexao, spec, logEntidade.inicio(), logEntidade.fim()
                );
                if (gruposDuplicados > 0) {
                    registrarFalha(falhas, "DUPLICIDADE_CHAVE",
                        "Entidade '" + entidade + "' possui " + gruposDuplicados + " grupo(s) duplicado(s) por chave.");
                }
            }

            validarIntegridadeReferencial(conexao, inicioExecucao, fimExecucao, entidades, falhas, modoLoopDaemon);
        } catch (SQLException e) {
            registrarFalha(falhas, "ERRO_SQL_VALIDACAO",
                "Falha SQL durante validacao de integridade: " + e.getMessage());
        }

        return new ResultadoValidacao(falhas.isEmpty(), falhas);
    }

    private Integer extrairNumeroCampoMensagem(final String mensagem, final String campo) {
        if (mensagem == null || mensagem.isBlank() || campo == null || campo.isBlank()) {
            return null;
        }
        final Pattern pattern = Pattern.compile(PADRAO_METRICA_LOG.pattern().formatted(Pattern.quote(campo)));
        final Matcher matcher = pattern.matcher(mensagem);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (final NumberFormatException e) {
            return null;
        }
    }

    private void validarSchema(final Connection conexao,
                               final IntegridadeEtlSpec spec,
                               final List<String> falhas) throws SQLException {
        if (!sqlSupport.tabelaExiste(conexao, spec.tabela())) {
            registrarFalha(falhas, "TABELA_AUSENTE",
                "Tabela '" + spec.tabela() + "' inexistente para entidade '" + spec.entidade() + "'.");
            return;
        }

        final List<String> colunasFaltantes = new ArrayList<>();
        for (String coluna : spec.colunasObrigatorias()) {
            if (!sqlSupport.colunaExiste(conexao, spec.tabela(), coluna)) {
                colunasFaltantes.add(coluna);
            }
        }

        if (!colunasFaltantes.isEmpty()) {
            registrarFalha(falhas, "SCHEMA_INCOMPATIVEL",
                "Tabela '" + spec.tabela() + "' sem colunas obrigatorias: " + String.join(", ", colunasFaltantes));
        } else {
            registrarEventoInfo("SCHEMA_OK", spec.entidade(), "tabela=" + spec.tabela());
        }
    }

    private Optional<IntegridadeEtlLogWindow> buscarLogDaExecucao(final Connection conexao,
                                                                  final String entidade,
                                                                  final LocalDateTime inicioExecucao,
                                                                  final LocalDateTime fimExecucao)
            throws SQLException {
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
                    return Optional.of(new IntegridadeEtlLogWindow(
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
                                           final IntegridadeEtlSpec spec,
                                           final LocalDateTime inicio,
                                           final LocalDateTime fim) throws SQLException {
        final String sql = String.format(
            "SELECT COUNT(*) FROM dbo.%s WHERE %s >= ? AND %s <= ?",
            spec.tabela(), spec.colunaTimestamp(), spec.colunaTimestamp()
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
                                  final IntegridadeEtlSpec spec,
                                  final LocalDateTime inicio,
                                  final LocalDateTime fim) throws SQLException {
        final String whereChavesNulas = String.join(" OR ",
            spec.chavesUnicas().stream().map(coluna -> coluna + " IS NULL").toList());

        final String sql = String.format(
            "SELECT COUNT(*) FROM dbo.%s WHERE %s >= ? AND %s <= ? AND (%s)",
            spec.tabela(), spec.colunaTimestamp(), spec.colunaTimestamp(), whereChavesNulas
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
                                       final IntegridadeEtlSpec spec,
                                       final LocalDateTime inicio,
                                       final LocalDateTime fim) throws SQLException {
        final String colunasChave = String.join(", ", spec.chavesUnicas());
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
            spec.tabela(),
            spec.colunaTimestamp(),
            spec.colunaTimestamp(),
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
        validarReferencialManifestos(conexao, inicioExecucao, fimExecucao, entidades, falhas, modoLoopDaemon);
        validarReferencialFretes(conexao, inicioExecucao, fimExecucao, entidades, falhas);
    }

    private void validarReferencialManifestos(final Connection conexao,
                                              final LocalDateTime inicioExecucao,
                                              final LocalDateTime fimExecucao,
                                              final Set<String> entidades,
                                              final List<String> falhas,
                                              final boolean modoLoopDaemon) throws SQLException {
        if (!(entidades.contains(ConstantesEntidades.MANIFESTOS) && entidades.contains(ConstantesEntidades.COLETAS))) {
            return;
        }

        final Optional<IntegridadeEtlLogWindow> logManifestos = buscarLogDaExecucao(
            conexao, ConstantesEntidades.MANIFESTOS, inicioExecucao, fimExecucao
        );
        final Optional<IntegridadeEtlLogWindow> logColetas = buscarLogDaExecucao(
            conexao, ConstantesEntidades.COLETAS, inicioExecucao, fimExecucao
        );

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
        final int orfaosManifestos = sqlSupport.executarCount(conexao, sqlManifestosOrfaos, inicioExecucao, fimExecucao);
        if (orfaosManifestos == 0) {
            registrarEventoInfo("REFERENCIAL_OK", ConstantesEntidades.MANIFESTOS,
                "pick_sequence_code vinculado a coletas.sequence_code");
            return;
        }

        final String sqlTotalManifestosComPick = """
            SELECT COUNT(*)
            FROM dbo.manifestos m
            WHERE m.data_extracao >= ? AND m.data_extracao <= ?
              AND m.pick_sequence_code IS NOT NULL
            """;
        final int totalManifestosComPick = sqlSupport.executarCount(
            conexao, sqlTotalManifestosComPick, inicioExecucao, fimExecucao
        );
        final double percentualOrfaos = (orfaosManifestos * 100.0) / Math.max(1, totalManifestosComPick);
        final int limiteOrfaosQuantidade = ConfigEtl.obterMaxOrfaosManifestosTolerados();
        final double limiteOrfaosPercentual = ConfigEtl.obterPercentualMaxOrfaosManifestosTolerados();

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
        final List<Long> amostraOrfaos = sqlSupport.executarListaLong(
            conexao, sqlManifestosOrfaosAmostra, inicioExecucao, fimExecucao
        );
        final String contextoManifestos = resumirJanela(logManifestos);
        final String contextoColetas = resumirJanela(logColetas);
        final boolean coletasVaziasNaExecucao = logColetas.isPresent() && logColetas.get().registrosExtraidos() == 0;
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
            + " | limites_tolerancia={" + limiteOrfaosQuantidade + "; "
            + String.format(java.util.Locale.US, "%.2f", limiteOrfaosPercentual) + "%}"
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
            return;
        }

        registrarFalha(falhas, codigoFalha, detalheReferencial);
    }

    private void validarReferencialFretes(final Connection conexao,
                                          final LocalDateTime inicioExecucao,
                                          final LocalDateTime fimExecucao,
                                          final Set<String> entidades,
                                          final List<String> falhas) throws SQLException {
        if (!(entidades.contains(ConstantesEntidades.FRETES)
            && entidades.contains(ConstantesEntidades.FATURAS_GRAPHQL))) {
            return;
        }

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
        final int orfaosFretes = sqlSupport.executarCount(conexao, sqlFretesOrfaos, inicioExecucao, fimExecucao);
        if (orfaosFretes == 0) {
            registrarEventoInfo("REFERENCIAL_OK", ConstantesEntidades.FRETES,
                "accounting_credit_id vinculado a faturas_graphql.id");
            return;
        }

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
        final List<Long> amostraOrfaos = sqlSupport.executarListaLong(
            conexao, sqlFretesOrfaosAmostra, inicioExecucao, fimExecucao
        );
        registrarFalha(falhas, "INTEGRIDADE_REFERENCIAL_FRETES",
            "Fretes orfaos (accounting_credit_id sem faturas_graphql.id): " + orfaosFretes
                + " | amostra_accounting_credit_id=" + amostraOrfaos);
    }

    private String resumirJanela(final Optional<IntegridadeEtlLogWindow> janela) {
        return janela
            .map(log -> String.format("status=%s, registros=%d, janela=[%s .. %s], mensagem=%s",
                log.status(), log.registrosExtraidos(), log.inicio(), log.fim(), resumirMensagem(log.mensagem())))
            .orElse("sem_log");
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
