package br.com.extrator.observabilidade.validacao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/validacao/AuditoriaValidator.java
Classe  : AuditoriaValidator (class)
Pacote  : br.com.extrator.observabilidade.validacao
Modulo  : Observabilidade - Validacao
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.observabilidade.enums.StatusValidacao;
import br.com.extrator.observabilidade.modelos.ResultadoValidacaoEntidade;
import br.com.extrator.persistencia.entidade.LogExtracaoEntity;
import br.com.extrator.persistencia.entidade.LogExtracaoEntity.StatusExtracao;
import br.com.extrator.persistencia.repositorio.LogExtracaoRepository;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public class AuditoriaValidator {
    private static final Logger logger = LoggerFactory.getLogger(AuditoriaValidator.class);

    private final LogExtracaoRepository logExtracaoRepository;
    private final AuditoriaDatabaseSupport databaseSupport;
    private final AuditoriaStatusResolver statusResolver;

    public AuditoriaValidator() {
        this.logExtracaoRepository = new LogExtracaoRepository();
        this.databaseSupport = new AuditoriaDatabaseSupport();
        this.statusResolver = new AuditoriaStatusResolver();
    }

    private LogExtracaoEntity consultarLogExtracao(final String nomeEntidade, final Instant dataInicio) {
        try {
            logger.debug("Consultando log de extracao para entidade: {}", nomeEntidade);
            final Optional<LogExtracaoEntity> ultimoLog = logExtracaoRepository.buscarUltimoLogPorEntidade(nomeEntidade);
            if (ultimoLog.isPresent()) {
                final LogExtracaoEntity log = ultimoLog.get();
                final LocalDateTime dataInicioLocal = LocalDateTime.ofInstant(dataInicio, ZoneOffset.UTC);
                if (log.getTimestampFim().isAfter(dataInicioLocal)) {
                    logger.info(
                        "Log de extracao encontrado para {}: Status={}, Registros={}, Paginas={}",
                        nomeEntidade,
                        log.getStatusFinal(),
                        log.getRegistrosExtraidos(),
                        log.getPaginasProcessadas()
                    );
                    return log;
                }
                logger.debug("Log de extracao encontrado para {} mas e anterior ao periodo de interesse", nomeEntidade);
            } else {
                logger.debug("Nenhum log de extracao encontrado para {}", nomeEntidade);
            }
        } catch (Exception e) {
            logger.error("Erro ao consultar log de extracao para {}: {}", nomeEntidade, e.getMessage(), e);
        }
        return null;
    }

    public ResultadoValidacaoEntidade validarEntidade(final Connection conexao,
                                                      final String nomeEntidade,
                                                      final Instant dataInicio,
                                                      final Instant dataFim) {
        logger.info("Auditando {}...", nomeEntidade);
        logger.debug("Parametros: dataInicio={}, dataFim={}", dataInicio, dataFim);

        final ResultadoValidacaoEntidade resultado = new ResultadoValidacaoEntidade();
        resultado.setNomeEntidade(nomeEntidade);
        resultado.setDataInicio(dataInicio);
        resultado.setDataFim(dataFim);

        final String nomeTabela = mapearNomeTabela(nomeEntidade);
        final LogExtracaoEntity logExtracao = consultarLogExtracao(nomeEntidade, dataInicio);
        registrarContextoDoLog(resultado, nomeEntidade, logExtracao);

        try {
            if (!databaseSupport.verificarExistenciaTabela(conexao, nomeTabela)) {
                final String erro = String.format(
                    "Tabela '%s' nao encontrada. Execute os scripts SQL da pasta 'database/' antes de rodar a aplicacao. "
                        + "Veja database/README.md para instrucoes.",
                    nomeEntidade
                );
                logger.error("{}", erro);
                resultado.setErro(erro);
                resultado.setStatus(StatusValidacao.ERRO);
                return resultado;
            }

            if (!databaseSupport.validarColunaExiste(conexao, nomeTabela, "data_extracao")) {
                final String erro = "Coluna 'data_extracao' nao encontrada na tabela: " + nomeEntidade;
                logger.error("{}", erro);
                resultado.setErro(erro);
                resultado.setStatus(StatusValidacao.ERRO);
                return resultado;
            }

            preencherContagens(conexao, nomeTabela, dataInicio, dataFim, resultado, logExtracao);

            final long registrosComNulos = databaseSupport.contarRegistrosComNulos(conexao, nomeTabela);
            resultado.setRegistrosComNulos(registrosComNulos);
            resultado.setUltimaExtracao(databaseSupport.obterDataUltimaExtracao(conexao, nomeTabela));

            if (resultado.getTotalRegistros() == 0) {
                databaseSupport.investigarCausaRaizZeroRegistros(conexao, nomeTabela, resultado);
            }

            statusResolver.determinarStatusValidacao(resultado, logExtracao, logger);
            logger.info("{}: {} registros, coluna: {}", nomeEntidade, resultado.getTotalRegistros(), resultado.getColunaUtilizada());
        } catch (SQLException e) {
            logger.error("Erro SQL ao validar entidade {}: {}", nomeEntidade, e.getMessage(), e);
            resultado.setErro("Erro SQL: " + e.getMessage());
            resultado.setStatus(StatusValidacao.ERRO);
        } catch (Exception e) {
            logger.error("Erro inesperado ao validar entidade {}: {}", nomeEntidade, e.getMessage(), e);
            resultado.setErro("Erro inesperado: " + e.getMessage());
            resultado.setStatus(StatusValidacao.ERRO);
        }

        return resultado;
    }

    public boolean verificarExistenciaDadosRecentes(final Connection conexao,
                                                    final Instant dataInicio,
                                                    final Instant dataFim) {
        try {
            final List<String> entidades = List.of(
                ConstantesEntidades.COTACOES,
                ConstantesEntidades.COLETAS,
                ConstantesEntidades.CONTAS_A_PAGAR,
                ConstantesEntidades.FATURAS_POR_CLIENTE,
                ConstantesEntidades.FRETES,
                ConstantesEntidades.FATURAS_GRAPHQL,
                ConstantesEntidades.MANIFESTOS,
                ConstantesEntidades.LOCALIZACAO_CARGAS
            );

            for (String entidade : entidades) {
                if (!databaseSupport.verificarExistenciaTabela(conexao, entidade)) {
                    continue;
                }
                final long registros = databaseSupport.contarRegistrosPorDataExtracao(
                    conexao, entidade, dataInicio, dataFim, null
                );
                if (registros > 0) {
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            logger.error("Erro ao verificar existencia de dados recentes: {}", e.getMessage(), e);
            return false;
        }
    }

    public void verificarTodasTabelasExistem(final Connection conexao) throws SQLException {
        final List<String> entidades = List.of(
            ConstantesEntidades.COTACOES,
            ConstantesEntidades.COLETAS,
            ConstantesEntidades.CONTAS_A_PAGAR,
            ConstantesEntidades.FATURAS_POR_CLIENTE,
            ConstantesEntidades.FRETES,
            ConstantesEntidades.FATURAS_GRAPHQL,
            ConstantesEntidades.MANIFESTOS,
            ConstantesEntidades.LOCALIZACAO_CARGAS
        );

        logger.info("Verificando se todas as tabelas existem...");
        final List<String> tabelasFaltando = new ArrayList<>();
        for (String entidade : entidades) {
            final String nomeTabela = mapearNomeEntidadeParaTabela(entidade);
            if (!databaseSupport.verificarExistenciaTabela(conexao, nomeTabela)) {
                tabelasFaltando.add(entidade);
                logger.error("Tabela '{}' nao encontrada para entidade '{}'", nomeTabela, entidade);
            } else {
                logger.debug("Tabela '{}' existe", nomeTabela);
            }
        }

        if (!tabelasFaltando.isEmpty()) {
            final String mensagem = String.format(
                "As seguintes tabelas nao existem: %s. Execute os scripts SQL da pasta 'database/' antes de rodar a aplicacao. "
                    + "Veja database/README.md para instrucoes.",
                String.join(", ", tabelasFaltando)
            );
            logger.error("{}", mensagem);
            throw new SQLException(mensagem);
        }

        logger.info("Todas as tabelas verificadas e existem no banco de dados");
    }

    @Deprecated
    public void criarTodasTabelasSeNaoExistirem(final Connection conexao) {
        try {
            verificarTodasTabelasExistem(conexao);
        } catch (SQLException e) {
            logger.error("Erro ao verificar tabelas: {}", e.getMessage());
        }
    }

    private void registrarContextoDoLog(final ResultadoValidacaoEntidade resultado,
                                        final String nomeEntidade,
                                        final LogExtracaoEntity logExtracao) {
        if (logExtracao == null) {
            logger.warn("Nenhum log de extracao encontrado para {} no periodo especificado", nomeEntidade);
            resultado.adicionarObservacao("Nenhum log de extracao encontrado para o periodo");
            return;
        }

        logger.info(
            "Log de extracao encontrado para {}: status={}, registros={}, paginas={}",
            nomeEntidade,
            logExtracao.getStatusFinal(),
            logExtracao.getRegistrosExtraidos(),
            logExtracao.getPaginasProcessadas()
        );

        switch (logExtracao.getStatusFinal()) {
            case INCOMPLETO_LIMITE -> resultado.adicionarObservacao(
                "Extracao interrompida por limite: " + logExtracao.getMensagem()
            );
            case INCOMPLETO_DADOS -> resultado.adicionarObservacao(
                "Extracao com dados invalidos na origem: " + logExtracao.getMensagem()
            );
            case INCOMPLETO_DB -> resultado.adicionarObservacao(
                "Extracao com divergencia de persistencia: " + logExtracao.getMensagem()
            );
            case INCOMPLETO -> resultado.adicionarObservacao(
                "Extracao incompleta (status legado): " + logExtracao.getMensagem()
            );
            case ERRO_API -> resultado.adicionarObservacao("Erro na extracao: " + logExtracao.getMensagem());
            case COMPLETO -> resultado.adicionarObservacao("Extracao completada com sucesso");
            default -> logger.debug("Status de extracao nao reconhecido: {}", logExtracao.getStatusFinal());
        }
    }

    private void preencherContagens(final Connection conexao,
                                    final String nomeTabela,
                                    final Instant dataInicio,
                                    final Instant dataFim,
                                    final ResultadoValidacaoEntidade resultado,
                                    final LogExtracaoEntity logExtracao) throws SQLException {
        if (logExtracao != null && logExtracao.getStatusFinal() == StatusExtracao.COMPLETO) {
            final int registrosEsperados = logExtracao.getRegistrosExtraidos();
            final Instant agora = Instant.now();
            final Instant inicio24h = agora.minusSeconds(24L * 60 * 60);
            final long registros24h = databaseSupport.contarRegistrosPorDataExtracao(
                conexao, nomeTabela, inicio24h, agora, resultado
            );

            resultado.setTotalRegistros(registros24h);
            resultado.setRegistrosUltimas24h(registros24h);
            resultado.setRegistrosEsperadosApi(registrosEsperados);
            resultado.setDiferencaRegistros((int) (registros24h - registrosEsperados));
            if (registrosEsperados > 0) {
                resultado.setPercentualCompletude((registros24h * 100.0) / registrosEsperados);
            }
            resultado.setColunaUtilizada("log_extracoes (comparacao banco vs log - ultimas 24h)");
            logger.info(
                "Comparando banco vs log para {}: {} registros no banco (24h), {} esperados do log",
                nomeTabela,
                registros24h,
                registrosEsperados
            );
            return;
        }

        final long totalRegistros = databaseSupport.contarRegistrosPorDataExtracao(
            conexao, nomeTabela, dataInicio, dataFim, resultado
        );
        resultado.setTotalRegistros(totalRegistros);

        final Instant agora = Instant.now();
        final Instant inicio24h = agora.minusSeconds(24L * 60 * 60);
        final long registros24h = databaseSupport.contarRegistrosPorDataExtracao(
            conexao, nomeTabela, inicio24h, agora, null
        );
        resultado.setRegistrosUltimas24h(registros24h);
        logger.debug("Contagem do banco para {} (tabela: {}): {} registros", nomeTabela, nomeTabela, totalRegistros);
    }

    private String mapearNomeEntidadeParaTabela(final String nomeEntidade) {
        return nomeEntidade;
    }

    private String mapearNomeTabela(final String nomeEntidade) {
        return switch (nomeEntidade) {
            case "faturas_a_pagar_data_export" -> ConstantesEntidades.CONTAS_A_PAGAR;
            default -> nomeEntidade;
        };
    }
}
