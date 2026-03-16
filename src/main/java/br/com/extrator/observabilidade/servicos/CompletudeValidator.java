/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/auditoria/servicos/CompletudeValidator.java
Classe  : CompletudeValidator (class)
Pacote  : br.com.extrator.observabilidade.servicos
Modulo  : Servico de auditoria
Papel   : Implementa responsabilidade de completude validator.

Conecta com:
- ClienteApiDataExport (api)
- ClienteApiGraphQL (api)
- GerenciadorConexao (util.banco)
- ConstantesEntidades (util.validacao)

Fluxo geral:
1) Executa regras de validacao de qualidade/ETL.
2) Consolida indicadores e status de auditoria.
3) Publica resultado para relatorio tecnico.

Estrutura interna:
Metodos principais:
- CompletudeValidator(): realiza operacao relacionada a "completude validator".
- CompletudeValidator(...2 args): realiza operacao relacionada a "completude validator".
- buscarTotaisEslCloud(...1 args): consulta e retorna dados conforme criterio.
- validarCompletudePorLogs(...1 args): aplica regras de validacao e consistencia.
- validarCompletude(...3 args): aplica regras de validacao e consistencia.
- extrairMetricaInteira(...2 args): realiza operacao relacionada a "extrair metrica inteira".
- determinarStatusValidacao(...2 args): realiza operacao relacionada a "determinar status validacao".
- obterIconeStatus(...1 args): recupera dados configurados ou calculados.
- validarGapsOcorrencias(...1 args): aplica regras de validacao e consistencia.
- validarJanelaTemporal(...1 args): aplica regras de validacao e consistencia.
- validarJanelaTemporalEntidade(...3 args): aplica regras de validacao e consistencia.
- contarRegistrosDuranteJanela(...3 args): realiza operacao relacionada a "contar registros durante janela".
- contarRegistrosApiGraphQL(...3 args): realiza operacao relacionada a "contar registros api graph ql".
- contarRegistrosApiDataExport(...3 args): realiza operacao relacionada a "contar registros api data export".
Atributos-chave:
- logger: logger da classe para diagnostico.
- PADRAO_DB_UPSERTS: campo de estado para "padrao db upserts".
- PADRAO_UNIQUE_COUNT: campo de estado para "padrao unique count".
- clienteApiGraphQL: campo de estado para "cliente api graph ql".
- clienteApiDataExport: campo de estado para "cliente api data export".
[DOC-FILE-END]============================================================== */

package br.com.extrator.observabilidade.servicos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.integracao.ClienteApiDataExport;
import br.com.extrator.integracao.ClienteApiGraphQL;
import br.com.extrator.suporte.banco.GerenciadorConexao;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

/**
 * Motor central da auditoria comparativa que orquestra a busca de contagens
 * e a comparaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o com o banco de dados.
 * 
 * Esta classe implementa o TÃƒÆ’Ã‚Â³pico 2 da documentaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o, sendo responsÃƒÆ’Ã‚Â¡vel por:
 * - Orquestrar chamadas aos clientes de API para obter contagens do ESL Cloud
 * - Comparar essas contagens com os dados armazenados no banco de dados local
 * - Gerar relatÃƒÆ’Ã‚Â³rios de completude com status claros (ÃƒÂ¢Ã…â€œÃ¢â‚¬Â¦ OK, ÃƒÂ¢Ã‚ÂÃ…â€™ INCOMPLETO, ÃƒÂ¢Ã…Â¡Ã‚Â ÃƒÂ¯Ã‚Â¸Ã‚Â DUPLICADOS)
 * 
 * @author Sistema de ExtraÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o ESL Cloud
 * @version 1.0
 */
public class CompletudeValidator {
    private static final Logger logger = LoggerFactory.getLogger(CompletudeValidator.class);
    private static final Pattern PADRAO_DB_UPSERTS = Pattern.compile("\\bdb_upserts=(\\d+)\\b");
    private static final Pattern PADRAO_UNIQUE_COUNT = Pattern.compile("\\bunique_count=(\\d+)\\b");
    
    // Clientes de API para buscar contagens do ESL Cloud
    private final ClienteApiGraphQL clienteApiGraphQL;
    private final ClienteApiDataExport clienteApiDataExport;
    private final CompletudeGapValidator gapValidator;
    private final CompletudeJanelaTemporalValidator janelaTemporalValidator;
    
    // Mapeamento de entidades para nomes de tabelas no banco
    private static final Map<String, String> MAPEAMENTO_ENTIDADES_TABELAS = Map.of(
        ConstantesEntidades.FRETES, ConstantesEntidades.FRETES,
        ConstantesEntidades.COLETAS, ConstantesEntidades.COLETAS,
        ConstantesEntidades.FATURAS_GRAPHQL, ConstantesEntidades.FATURAS_GRAPHQL,
        ConstantesEntidades.MANIFESTOS, ConstantesEntidades.MANIFESTOS,
        ConstantesEntidades.COTACOES, ConstantesEntidades.COTACOES,
        ConstantesEntidades.LOCALIZACAO_CARGAS, ConstantesEntidades.LOCALIZACAO_CARGAS,
        ConstantesEntidades.CONTAS_A_PAGAR, ConstantesEntidades.CONTAS_A_PAGAR,
        ConstantesEntidades.FATURAS_POR_CLIENTE, ConstantesEntidades.FATURAS_POR_CLIENTE
    );
    
    /**
     * Construtor que inicializa os clientes de API necessÃƒÆ’Ã‚Â¡rios.
     * Utiliza injeÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o de dependÃƒÆ’Ã‚Âªncia para facilitar testes e manutenÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o.
     */
    public CompletudeValidator() {
        this.clienteApiGraphQL = new ClienteApiGraphQL();
        this.clienteApiDataExport = new ClienteApiDataExport();
        this.gapValidator = new CompletudeGapValidator(logger);
        this.janelaTemporalValidator =
            new CompletudeJanelaTemporalValidator(logger, clienteApiGraphQL, clienteApiDataExport);
        
        logger.info("CompletudeValidator inicializado (GraphQL + DataExport)");
    }
    
    /**
     * Construtor alternativo para injeÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o de dependÃƒÆ’Ã‚Âªncia (ÃƒÆ’Ã‚Âºtil para testes).
     * 
     * @param clienteApiRest Cliente da API REST
     * @param clienteApiGraphQL Cliente da API GraphQL
     * @param clienteApiDataExport Cliente da API DataExport
     */
    public CompletudeValidator(final ClienteApiGraphQL clienteApiGraphQL,
                              final ClienteApiDataExport clienteApiDataExport) {
        this.clienteApiGraphQL = clienteApiGraphQL;
        this.clienteApiDataExport = clienteApiDataExport;
        this.gapValidator = new CompletudeGapValidator(logger);
        this.janelaTemporalValidator =
            new CompletudeJanelaTemporalValidator(logger, clienteApiGraphQL, clienteApiDataExport);
        
        logger.info("CompletudeValidator inicializado com clientes injetados");
    }
    
    /**
     * Orquestrador principal que busca totais de todas as entidades do ESL Cloud.
     * 
     * Este mÃƒÆ’Ã‚Â©todo ÃƒÆ’Ã‚Â© o coraÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o do TÃƒÆ’Ã‚Â³pico 1, coordenando chamadas sequenciais para:
     * - ClienteApiRest: ocorrÃƒÆ’Ã‚Âªncias, faturas a receber, faturas a pagar
     * - ClienteApiGraphQL: fretes, coletas  
     * - ClienteApiDataExport: manifestos, cotaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Âµes, localizaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Âµes de carga
     * 
     * @param dataReferencia Data de referÃƒÆ’Ã‚Âªncia para buscar as contagens
     * @return Optional com Map contendo chave=nome_entidade e valor=contagem_esl_cloud, ou Optional.empty() se falhar
     */
    public Optional<Map<String, Integer>> buscarTotaisEslCloud(final LocalDate dataReferencia) {
        return buscarTotaisEslCloud(dataReferencia, true);
    }

    public Optional<Map<String, Integer>> buscarTotaisEslCloud(final LocalDate dataReferencia,
                                                                final boolean incluirFaturasGraphQL) {
        logger.info("ÃƒÂ°Ã…Â¸Ã¢â‚¬ÂÃ‚Â Iniciando busca de totais do ESL Cloud para data: {}", dataReferencia);
        
        final Map<String, Integer> totaisEslCloud = new HashMap<>();
        
        try {
        // Contagens via APIs disponÃƒÆ’Ã‚Â­veis
            
            // === API GraphQL - Fretes, Coletas e Faturas GraphQL ===
            logger.info("ÃƒÂ°Ã…Â¸Ã¢â‚¬Å“Ã…Â  Buscando contagens via API GraphQL...");
            
            final var resFretes = clienteApiGraphQL.buscarFretes(dataReferencia);
            totaisEslCloud.put(ConstantesEntidades.FRETES, resFretes.getRegistrosExtraidos());
            logger.info("ÃƒÂ¢Ã…â€œÃ¢â‚¬Â¦ Fretes: {} registros", resFretes.getRegistrosExtraidos());
            
            final var resColetas = clienteApiGraphQL.buscarColetas(dataReferencia);
            totaisEslCloud.put(ConstantesEntidades.COLETAS, resColetas.getRegistrosExtraidos());
            logger.info("ÃƒÂ¢Ã…â€œÃ¢â‚¬Â¦ Coletas: {} registros", resColetas.getRegistrosExtraidos());
            if (incluirFaturasGraphQL) {
                final var resFaturasGraphQL = clienteApiGraphQL.buscarCapaFaturas(dataReferencia);
                totaisEslCloud.put(ConstantesEntidades.FATURAS_GRAPHQL, resFaturasGraphQL.getRegistrosExtraidos());
                logger.info("Ã¢Å“â€¦ Faturas GraphQL: {} registros", resFaturasGraphQL.getRegistrosExtraidos());
            } else {
                logger.info("Faturas GraphQL ignoradas na busca de totais (flag --sem-faturas-graphql).");
            }            
            // === API DataExport - Manifestos, CotaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Âµes, LocalizaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Âµes, Contas a Pagar, Faturas/Cliente ===
            logger.info("ÃƒÂ°Ã…Â¸Ã¢â‚¬Å“Ã…Â  Buscando contagens via API DataExport (ÃƒÆ’Ã‚Âºltimas 24h)...");

            logger.info(
                "Observacao operacional: estas contagens DataExport usam a data de referencia {} e podem refletir granularidade diaria conforme o template.",
                dataReferencia
            );
            final int contagemManifestos = clienteApiDataExport.obterContagemManifestos(dataReferencia);
            totaisEslCloud.put(ConstantesEntidades.MANIFESTOS, contagemManifestos);
            logger.info("ÃƒÂ¢Ã…â€œÃ¢â‚¬Â¦ Manifestos: {} registros", contagemManifestos);

            final int contagemCotacoes = clienteApiDataExport.obterContagemCotacoes(dataReferencia);
            totaisEslCloud.put(ConstantesEntidades.COTACOES, contagemCotacoes);
            logger.info("ÃƒÂ¢Ã…â€œÃ¢â‚¬Â¦ CotaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Âµes: {} registros", contagemCotacoes);

            final int contagemLocalizacoes = clienteApiDataExport.obterContagemLocalizacoesCarga(dataReferencia);
            totaisEslCloud.put(ConstantesEntidades.LOCALIZACAO_CARGAS, contagemLocalizacoes);
            logger.info("ÃƒÂ¢Ã…â€œÃ¢â‚¬Â¦ LocalizaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Âµes de Carga: {} registros", contagemLocalizacoes);

            final int contagemContasAPagar = clienteApiDataExport.obterContagemContasAPagar(dataReferencia);
            totaisEslCloud.put(ConstantesEntidades.CONTAS_A_PAGAR, contagemContasAPagar);
            logger.info("ÃƒÂ¢Ã…â€œÃ¢â‚¬Â¦ Contas a Pagar: {} registros", contagemContasAPagar);

            final int contagemFaturasPorCliente = clienteApiDataExport.obterContagemFaturasPorCliente(dataReferencia);
            totaisEslCloud.put(ConstantesEntidades.FATURAS_POR_CLIENTE, contagemFaturasPorCliente);
            logger.info("ÃƒÂ¢Ã…â€œÃ¢â‚¬Â¦ Faturas por Cliente: {} registros", contagemFaturasPorCliente);

            
            // Log do resumo final
            final int totalGeralRegistros = totaisEslCloud.values().stream()
                .filter(v -> v >= 0)
                .mapToInt(Integer::intValue)
                .sum();
            logger.info("ÃƒÂ°Ã…Â¸Ã…Â½Ã‚Â¯ Busca de totais ESL Cloud concluÃƒÆ’Ã‚Â­da: {} entidades, {} registros totais", 
                    totaisEslCloud.size(), totalGeralRegistros);
            
        } catch (final Exception e) {
            logger.warn("ÃƒÂ¢Ã‚ÂÃ…â€™ Todas as 3 tentativas falharam ao buscar totais da API");
            logger.debug("ÃƒÆ’Ã…Â¡ltima exceÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o capturada:", e);
            return Optional.empty();
        }
        
        return Optional.of(totaisEslCloud);
    }

    /**
     * Valida completude usando exclusivamente os logs da prÃƒÆ’Ã‚Â³pria execuÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o.
     *
     * Esse modo evita uma segunda rodada de chamadas ÃƒÆ’Ã‚Â s APIs ao final do fluxo
     * (que pode ser lenta), mantendo a comparaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o entre referÃƒÆ’Ã‚Âªncia de extraÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o
     * (log_extracoes) e dados persistidos no banco.
     *
     * @param dataReferencia Data de referÃƒÆ’Ã‚Âªncia da execuÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o
     * @return Map com status de validaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o por entidade
     */
    public Map<String, StatusValidacao> validarCompletudePorLogs(final LocalDate dataReferencia) {
        logger.info("ÃƒÂ°Ã…Â¸Ã¢â‚¬ÂÃ‚Â Iniciando validaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o de completude baseada em log_extracoes para data: {}", dataReferencia);
        return validarCompletude(Collections.emptyMap(), dataReferencia);
    }
    
    /**
     * Valida a completude dos dados comparando contagens do ESL Cloud com o banco local.
     * 
     * Implementa a lÃƒÆ’Ã‚Â³gica de comparaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o usando queries SQL eficientes com String.format
     * (seguro pois os nomes das tabelas vÃƒÆ’Ã‚Âªm de fonte controlada - as chaves do Map).
     * 
     * Gera logs com status claros:
     * - ÃƒÂ¢Ã…â€œÃ¢â‚¬Â¦ OK: contagens coincidem
     * - ÃƒÂ¢Ã‚ÂÃ…â€™ INCOMPLETO: banco tem menos registros que ESL Cloud  
     * - ÃƒÂ¢Ã…Â¡Ã‚Â ÃƒÂ¯Ã‚Â¸Ã‚Â DUPLICADOS: banco tem mais registros que ESL Cloud
     * 
     * @param totaisEslCloud Map com contagens obtidas do ESL Cloud
     * @param dataReferencia Data de referÃƒÆ’Ã‚Âªncia para filtrar consultas no banco
     * @return Map com resultado da validaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o por entidade
     */
    public Map<String, StatusValidacao> validarCompletude(final Map<String, Integer> totaisEslCloud, 
                                                         final LocalDate dataReferencia) {
        logger.info("ÃƒÂ°Ã…Â¸Ã¢â‚¬ÂÃ‚Â Iniciando validaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o de completude para {} entidades na data: {}", 
                MAPEAMENTO_ENTIDADES_TABELAS.size(), dataReferencia);
        
        final Map<String, StatusValidacao> resultadosValidacao = new HashMap<>();
        
        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            
            for (final String nomeEntidade : MAPEAMENTO_ENTIDADES_TABELAS.keySet()) {
                final String nomeTabela = MAPEAMENTO_ENTIDADES_TABELAS.get(nomeEntidade);
                if (nomeTabela == null) {
                    resultadosValidacao.put(nomeEntidade, StatusValidacao.ERRO);
                    continue;
                }
                try {
                    final String sqlLog = """
                        SELECT TOP 1 timestamp_inicio, timestamp_fim, registros_extraidos, mensagem
                        FROM dbo.log_extracoes
                        WHERE entidade = ? AND CAST(timestamp_inicio AS DATE) = ? AND status_final = 'COMPLETO'
                        ORDER BY timestamp_fim DESC
                    """;
                    java.sql.Timestamp tsInicio = null;
                    java.sql.Timestamp tsFim = null;
                    String mensagemLog = null;
                    int contagemEslCloud = -1;
                    try (PreparedStatement stmtLog = conexao.prepareStatement(sqlLog)) {
                        stmtLog.setString(1, nomeEntidade);
                        stmtLog.setDate(2, java.sql.Date.valueOf(dataReferencia));
                        try (ResultSet rsLog = stmtLog.executeQuery()) {
                            if (rsLog.next()) {
                                tsInicio = rsLog.getTimestamp("timestamp_inicio");
                                tsFim = rsLog.getTimestamp("timestamp_fim");
                                contagemEslCloud = rsLog.getInt("registros_extraidos");
                                mensagemLog = rsLog.getString("mensagem");
                                final OptionalInt uniqueCount = extrairMetricaInteira(mensagemLog, PADRAO_UNIQUE_COUNT);
                                if (uniqueCount.isPresent()) {
                                    contagemEslCloud = uniqueCount.getAsInt();
                                }
                            }
                        }
                    }

                    int contagemBanco;
                    final OptionalInt dbUpserts = extrairMetricaInteira(mensagemLog, PADRAO_DB_UPSERTS);
                    if (dbUpserts.isPresent()) {
                        contagemBanco = dbUpserts.getAsInt();
                    } else if (tsInicio != null && tsFim != null) {
                        final String colunaTemporal = ConstantesEntidades.USUARIOS_SISTEMA.equals(nomeEntidade)
                            ? "data_atualizacao"
                            : "data_extracao";
                        final String sqlDb = String.format(
                            "SELECT COUNT(*) FROM %s WHERE %s >= ? AND %s <= ?",
                            nomeTabela,
                            colunaTemporal,
                            colunaTemporal
                        );
                        try (PreparedStatement stmtDb = conexao.prepareStatement(sqlDb)) {
                            stmtDb.setTimestamp(1, tsInicio);
                            stmtDb.setTimestamp(2, tsFim);
                            try (ResultSet rsDb = stmtDb.executeQuery()) {
                                rsDb.next();
                                contagemBanco = rsDb.getInt(1);
                            }
                        }
                    } else {
                        final Integer contagemReferencia = totaisEslCloud.get(nomeEntidade);
                        if (contagemReferencia == null) {
                            logger.warn("ÃƒÂ¢Ã…Â¡Ã‚Â ÃƒÂ¯Ã‚Â¸Ã‚Â Sem referÃƒÆ’Ã‚Âªncia de contagem para '{}' (sem log COMPLETO e sem total de API).", nomeEntidade);
                            resultadosValidacao.put(nomeEntidade, StatusValidacao.ERRO);
                            continue;
                        }

                        final String colunaTemporal = ConstantesEntidades.USUARIOS_SISTEMA.equals(nomeEntidade)
                            ? "data_atualizacao"
                            : "data_extracao";
                        final String sqlDb = String.format(
                            "SELECT COUNT(*) FROM %s WHERE %s >= DATEADD(hour, -24, GETDATE())",
                            nomeTabela,
                            colunaTemporal
                        );
                        try (PreparedStatement stmtDb = conexao.prepareStatement(sqlDb);
                             ResultSet rsDb = stmtDb.executeQuery()) {
                            rsDb.next();
                            contagemBanco = rsDb.getInt(1);
                        }
                        contagemEslCloud = contagemReferencia;
                    }

                    final StatusValidacao status = determinarStatusValidacao(contagemEslCloud, contagemBanco);
                    resultadosValidacao.put(nomeEntidade, status);
                    final String iconeStatus = obterIconeStatus(status);
                    logger.info("{} {}: ESL Cloud={}, Banco={}", iconeStatus, nomeEntidade, contagemEslCloud, contagemBanco);
                } catch (final SQLException e) {
                    logger.error("ÃƒÂ¢Ã‚ÂÃ…â€™ Erro SQL ao validar entidade '{}': {}", nomeEntidade, e.getMessage(), e);
                    resultadosValidacao.put(nomeEntidade, StatusValidacao.ERRO);
                }
            }
            
            // Log do resumo final
            final long totalOk = resultadosValidacao.values().stream()
                    .filter(status -> status == StatusValidacao.OK).count();
            final long totalIncompleto = resultadosValidacao.values().stream()
                    .filter(status -> status == StatusValidacao.INCOMPLETO).count();
            final long totalDuplicados = resultadosValidacao.values().stream()
                    .filter(status -> status == StatusValidacao.DUPLICADOS).count();
            final long totalErros = resultadosValidacao.values().stream()
                    .filter(status -> status == StatusValidacao.ERRO).count();
            
            logger.info("ÃƒÂ°Ã…Â¸Ã¢â‚¬Å“Ã…Â  ValidaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o de completude concluÃƒÆ’Ã‚Â­da: ÃƒÂ¢Ã…â€œÃ¢â‚¬Â¦ {} OK, ÃƒÂ¢Ã‚ÂÃ…â€™ {} INCOMPLETO, ÃƒÂ¢Ã…Â¡Ã‚Â ÃƒÂ¯Ã‚Â¸Ã‚Â {} DUPLICADOS, ÃƒÂ°Ã…Â¸Ã¢â‚¬â„¢Ã‚Â¥ {} ERROS", 
                    totalOk, totalIncompleto, totalDuplicados, totalErros);
            
        } catch (final SQLException e) {
            logger.error("ÃƒÂ¢Ã‚ÂÃ…â€™ Erro ao conectar com banco de dados para validaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o: {}", e.getMessage(), e);
            throw new RuntimeException("Falha na conexÃƒÆ’Ã‚Â£o com banco de dados", e);
        }
        
        return resultadosValidacao;
    }

    private OptionalInt extrairMetricaInteira(final String mensagem, final Pattern padrao) {
        if (mensagem == null || mensagem.isBlank()) {
            return OptionalInt.empty();
        }
        final Matcher matcher = padrao.matcher(mensagem);
        if (!matcher.find()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(matcher.group(1)));
        } catch (final NumberFormatException e) {
            logger.debug("Nao foi possivel converter metrica numÃƒÆ’Ã‚Â©rica de '{}': {}", mensagem, e.getMessage());
            return OptionalInt.empty();
        }
    }
    
    /**
     * Determina o status de validaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o baseado na comparaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o entre contagens.
     * 
     * @param contagemEslCloud Contagem obtida do ESL Cloud
     * @param contagemBanco Contagem obtida do banco local
     * @return Status da validaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o
     */
    private StatusValidacao determinarStatusValidacao(final int contagemEslCloud, final int contagemBanco) {
        if (contagemEslCloud == contagemBanco) {
            return StatusValidacao.OK;
        } else if (contagemBanco < contagemEslCloud) {
            return StatusValidacao.INCOMPLETO;
        } else {
            return StatusValidacao.DUPLICADOS;
        }
    }
    
    /**
     * ObtÃƒÆ’Ã‚Â©m o ÃƒÆ’Ã‚Â­cone visual correspondente ao status de validaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o.
     * 
     * @param status Status da validaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o
     * @return String com ÃƒÆ’Ã‚Â­cone visual
     */
    private String obterIconeStatus(final StatusValidacao status) {
        return switch (status) {
            case OK -> "ÃƒÂ¢Ã…â€œÃ¢â‚¬Â¦ OK";
            case INCOMPLETO -> "ÃƒÂ¢Ã‚ÂÃ…â€™ INCOMPLETO";
            case DUPLICADOS -> "ÃƒÂ¢Ã…Â¡Ã‚Â ÃƒÂ¯Ã‚Â¸Ã‚Â DUPLICADOS";
            case ERRO -> "ÃƒÂ°Ã…Â¸Ã¢â‚¬â„¢Ã‚Â¥ ERRO";
        };
    }
    
    /**
     * TÃƒÆ’Ã¢â‚¬Å“PICO 4: ValidaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o de Gaps - Verifica se os IDs das ocorrÃƒÆ’Ã‚Âªncias sÃƒÆ’Ã‚Â£o sequenciais
     * 
     * PrÃƒÆ’Ã‚Â©-requisito: Esta validaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o sÃƒÆ’Ã‚Â³ deve ser executada se os IDs forem realmente sequenciais.
     * Caso contrÃƒÆ’Ã‚Â¡rio, a estratÃƒÆ’Ã‚Â©gia de detecÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o de gaps nÃƒÆ’Ã‚Â£o funcionarÃƒÆ’Ã‚Â¡.
     * 
     * @param dataReferencia Data de referÃƒÆ’Ã‚Âªncia para anÃƒÆ’Ã‚Â¡lise
     * @return StatusValidacao indicando se hÃƒÆ’Ã‚Â¡ gaps nos IDs
     */
    public StatusValidacao validarGapsOcorrencias(final LocalDate dataReferencia) {
        return gapValidator.validarGapsOcorrencias(dataReferencia);
    }

    public Map<String, StatusValidacao> validarJanelaTemporal(final LocalDate dataReferencia) {
        return janelaTemporalValidator.validarJanelaTemporal(MAPEAMENTO_ENTIDADES_TABELAS.keySet(), dataReferencia);
    }

    public Optional<Map<String, Integer>> buscarTotaisEslCloudJanelaPrincipal(final LocalDate dataReferencia,
                                                                               final boolean incluirFaturasGraphQL) {
        final LocalDate dataInicio = dataReferencia.minusDays(1);
        logger.info(
            "Iniciando busca de totais do ESL Cloud para a janela principal {} a {} (D-1..D).",
            dataInicio,
            dataReferencia
        );

        final Map<String, Integer> totaisEslCloud = new HashMap<>();

        try {
            logger.info("Buscando contagens via API GraphQL...");

            final var resFretes = clienteApiGraphQL.buscarFretes(dataReferencia);
            totaisEslCloud.put(ConstantesEntidades.FRETES, resFretes.getRegistrosExtraidos());
            logger.info("GraphQL fretes: {} registros", resFretes.getRegistrosExtraidos());

            final var resColetas = clienteApiGraphQL.buscarColetas(dataReferencia);
            totaisEslCloud.put(ConstantesEntidades.COLETAS, resColetas.getRegistrosExtraidos());
            logger.info("GraphQL coletas: {} registros", resColetas.getRegistrosExtraidos());

            if (incluirFaturasGraphQL) {
                final var resFaturasGraphQL = clienteApiGraphQL.buscarCapaFaturas(dataReferencia);
                totaisEslCloud.put(ConstantesEntidades.FATURAS_GRAPHQL, resFaturasGraphQL.getRegistrosExtraidos());
                logger.info("GraphQL faturas_graphql: {} registros", resFaturasGraphQL.getRegistrosExtraidos());
            } else {
                logger.info("GraphQL faturas_graphql ignoradas na busca de totais (flag --sem-faturas-graphql).");
            }

            logger.info(
                "Buscando contagens via API DataExport na janela principal {} a {} (D-1..D)...",
                dataInicio,
                dataReferencia
            );

            final int contagemManifestos = contarResultadoDataExport(
                clienteApiDataExport.buscarManifestos(dataInicio, dataReferencia)
            );
            totaisEslCloud.put(ConstantesEntidades.MANIFESTOS, contagemManifestos);
            logger.info("DataExport manifestos: {} registros", contagemManifestos);

            final int contagemCotacoes = contarResultadoDataExport(
                clienteApiDataExport.buscarCotacoes(dataInicio, dataReferencia)
            );
            totaisEslCloud.put(ConstantesEntidades.COTACOES, contagemCotacoes);
            logger.info("DataExport cotacoes: {} registros", contagemCotacoes);

            final int contagemLocalizacoes = contarResultadoDataExport(
                clienteApiDataExport.buscarLocalizacaoCarga(dataInicio, dataReferencia)
            );
            totaisEslCloud.put(ConstantesEntidades.LOCALIZACAO_CARGAS, contagemLocalizacoes);
            logger.info("DataExport localizacao_cargas: {} registros", contagemLocalizacoes);

            final int contagemContasAPagar = contarResultadoDataExport(
                clienteApiDataExport.buscarContasAPagar(dataInicio, dataReferencia)
            );
            totaisEslCloud.put(ConstantesEntidades.CONTAS_A_PAGAR, contagemContasAPagar);
            logger.info("DataExport contas_a_pagar: {} registros", contagemContasAPagar);

            final int contagemFaturasPorCliente = contarResultadoDataExport(
                clienteApiDataExport.buscarFaturasPorCliente(dataInicio, dataReferencia)
            );
            totaisEslCloud.put(ConstantesEntidades.FATURAS_POR_CLIENTE, contagemFaturasPorCliente);
            logger.info("DataExport faturas_por_cliente: {} registros", contagemFaturasPorCliente);

            final int totalGeralRegistros = totaisEslCloud.values().stream()
                .filter(v -> v >= 0)
                .mapToInt(Integer::intValue)
                .sum();
            logger.info(
                "Busca de totais ESL Cloud na janela principal concluida: {} entidades, {} registros totais",
                totaisEslCloud.size(),
                totalGeralRegistros
            );
        } catch (final Exception e) {
            logger.warn("Falha ao buscar totais do ESL Cloud na janela principal.");
            logger.debug("Detalhes da falha na busca de totais da janela principal:", e);
            return Optional.empty();
        }

        return Optional.of(totaisEslCloud);
    }

    private int contarResultadoDataExport(final br.com.extrator.integracao.ResultadoExtracao<?> resultadoExtracao) {
        if (resultadoExtracao == null || resultadoExtracao.getDados() == null) {
            return 0;
        }
        return resultadoExtracao.getDados().size();
    }

    public enum StatusValidacao {
        /** Contagens coincidem - dados completos */
        OK,
        /** Banco tem menos registros que ESL Cloud - dados incompletos */
        INCOMPLETO,
        /** Banco tem mais registros que ESL Cloud - possÃƒÆ’Ã‚Â­veis duplicados */
        DUPLICADOS,
        /** Erro durante a validaÃƒÆ’Ã‚Â§ÃƒÆ’Ã‚Â£o */
        ERRO
    }
}
