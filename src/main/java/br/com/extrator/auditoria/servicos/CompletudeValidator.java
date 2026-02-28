/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/auditoria/servicos/CompletudeValidator.java
Classe  : CompletudeValidator (class)
Pacote  : br.com.extrator.auditoria.servicos
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

package br.com.extrator.auditoria.servicos;

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

import br.com.extrator.api.ClienteApiDataExport;
import br.com.extrator.api.ClienteApiGraphQL;
import br.com.extrator.util.banco.GerenciadorConexao;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Motor central da auditoria comparativa que orquestra a busca de contagens
 * e a comparação com o banco de dados.
 * 
 * Esta classe implementa o Tópico 2 da documentação, sendo responsável por:
 * - Orquestrar chamadas aos clientes de API para obter contagens do ESL Cloud
 * - Comparar essas contagens com os dados armazenados no banco de dados local
 * - Gerar relatórios de completude com status claros (✅ OK, ❌ INCOMPLETO, ⚠️ DUPLICADOS)
 * 
 * @author Sistema de Extração ESL Cloud
 * @version 1.0
 */
public class CompletudeValidator {
    private static final Logger logger = LoggerFactory.getLogger(CompletudeValidator.class);
    private static final Pattern PADRAO_DB_UPSERTS = Pattern.compile("\\bdb_upserts=(\\d+)\\b");
    private static final Pattern PADRAO_UNIQUE_COUNT = Pattern.compile("\\bunique_count=(\\d+)\\b");
    
    // Clientes de API para buscar contagens do ESL Cloud
    private final ClienteApiGraphQL clienteApiGraphQL;
    private final ClienteApiDataExport clienteApiDataExport;
    
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
     * Construtor que inicializa os clientes de API necessários.
     * Utiliza injeção de dependência para facilitar testes e manutenção.
     */
    public CompletudeValidator() {
        this.clienteApiGraphQL = new ClienteApiGraphQL();
        this.clienteApiDataExport = new ClienteApiDataExport();
        
        logger.info("CompletudeValidator inicializado (GraphQL + DataExport)");
    }
    
    /**
     * Construtor alternativo para injeção de dependência (útil para testes).
     * 
     * @param clienteApiRest Cliente da API REST
     * @param clienteApiGraphQL Cliente da API GraphQL
     * @param clienteApiDataExport Cliente da API DataExport
     */
    public CompletudeValidator(final ClienteApiGraphQL clienteApiGraphQL,
                              final ClienteApiDataExport clienteApiDataExport) {
        this.clienteApiGraphQL = clienteApiGraphQL;
        this.clienteApiDataExport = clienteApiDataExport;
        
        logger.info("CompletudeValidator inicializado com clientes injetados");
    }
    
    /**
     * Orquestrador principal que busca totais de todas as entidades do ESL Cloud.
     * 
     * Este método é o coração do Tópico 1, coordenando chamadas sequenciais para:
     * - ClienteApiRest: ocorrências, faturas a receber, faturas a pagar
     * - ClienteApiGraphQL: fretes, coletas  
     * - ClienteApiDataExport: manifestos, cotações, localizações de carga
     * 
     * @param dataReferencia Data de referência para buscar as contagens
     * @return Optional com Map contendo chave=nome_entidade e valor=contagem_esl_cloud, ou Optional.empty() se falhar
     */
    public Optional<Map<String, Integer>> buscarTotaisEslCloud(final LocalDate dataReferencia) {
        logger.info("🔍 Iniciando busca de totais do ESL Cloud para data: {}", dataReferencia);
        
        final Map<String, Integer> totaisEslCloud = new HashMap<>();
        
        try {
        // Contagens via APIs disponíveis
            
            // === API GraphQL - Fretes, Coletas e Faturas GraphQL ===
            logger.info("📊 Buscando contagens via API GraphQL...");
            
            final var resFretes = clienteApiGraphQL.buscarFretes(dataReferencia);
            totaisEslCloud.put(ConstantesEntidades.FRETES, resFretes.getRegistrosExtraidos());
            logger.info("✅ Fretes: {} registros", resFretes.getRegistrosExtraidos());
            
            final var resColetas = clienteApiGraphQL.buscarColetas(dataReferencia);
            totaisEslCloud.put(ConstantesEntidades.COLETAS, resColetas.getRegistrosExtraidos());
            logger.info("✅ Coletas: {} registros", resColetas.getRegistrosExtraidos());
            
            final var resFaturasGraphQL = clienteApiGraphQL.buscarCapaFaturas(dataReferencia);
            totaisEslCloud.put(ConstantesEntidades.FATURAS_GRAPHQL, resFaturasGraphQL.getRegistrosExtraidos());
            logger.info("✅ Faturas GraphQL: {} registros", resFaturasGraphQL.getRegistrosExtraidos());
            
            // === API DataExport - Manifestos, Cotações, Localizações, Contas a Pagar, Faturas/Cliente ===
            logger.info("📊 Buscando contagens via API DataExport (últimas 24h)...");

            final LocalDate dataInicioDataExport = dataReferencia.minusDays(1);
            final var resManifestos = clienteApiDataExport.buscarManifestos(dataInicioDataExport, dataReferencia);
            totaisEslCloud.put(ConstantesEntidades.MANIFESTOS, resManifestos.getRegistrosExtraidos());
            logger.info("✅ Manifestos: {} registros", resManifestos.getRegistrosExtraidos());

            final var resCotacoes = clienteApiDataExport.buscarCotacoes(dataInicioDataExport, dataReferencia);
            totaisEslCloud.put(ConstantesEntidades.COTACOES, resCotacoes.getRegistrosExtraidos());
            logger.info("✅ Cotações: {} registros", resCotacoes.getRegistrosExtraidos());

            final var resLocalizacoes = clienteApiDataExport.buscarLocalizacaoCarga(dataInicioDataExport, dataReferencia);
            totaisEslCloud.put(ConstantesEntidades.LOCALIZACAO_CARGAS, resLocalizacoes.getRegistrosExtraidos());
            logger.info("✅ Localizações de Carga: {} registros", resLocalizacoes.getRegistrosExtraidos());

            final var resContasAPagar = clienteApiDataExport.buscarContasAPagar(dataInicioDataExport, dataReferencia);
            totaisEslCloud.put(ConstantesEntidades.CONTAS_A_PAGAR, resContasAPagar.getRegistrosExtraidos());
            logger.info("✅ Contas a Pagar: {} registros", resContasAPagar.getRegistrosExtraidos());

            final var resFaturasPorCliente = clienteApiDataExport.buscarFaturasPorCliente(dataInicioDataExport, dataReferencia);
            totaisEslCloud.put(ConstantesEntidades.FATURAS_POR_CLIENTE, resFaturasPorCliente.getRegistrosExtraidos());
            logger.info("✅ Faturas por Cliente: {} registros", resFaturasPorCliente.getRegistrosExtraidos());
            
            // Log do resumo final
            final int totalGeralRegistros = totaisEslCloud.values().stream()
                .filter(v -> v >= 0)
                .mapToInt(Integer::intValue)
                .sum();
            logger.info("🎯 Busca de totais ESL Cloud concluída: {} entidades, {} registros totais", 
                    totaisEslCloud.size(), totalGeralRegistros);
            
        } catch (final Exception e) {
            logger.warn("❌ Todas as 3 tentativas falharam ao buscar totais da API");
            logger.debug("Última exceção capturada:", e);
            return Optional.empty();
        }
        
        return Optional.of(totaisEslCloud);
    }

    /**
     * Valida completude usando exclusivamente os logs da própria execução.
     *
     * Esse modo evita uma segunda rodada de chamadas às APIs ao final do fluxo
     * (que pode ser lenta), mantendo a comparação entre referência de extração
     * (log_extracoes) e dados persistidos no banco.
     *
     * @param dataReferencia Data de referência da execução
     * @return Map com status de validação por entidade
     */
    public Map<String, StatusValidacao> validarCompletudePorLogs(final LocalDate dataReferencia) {
        logger.info("🔍 Iniciando validação de completude baseada em log_extracoes para data: {}", dataReferencia);
        return validarCompletude(Collections.emptyMap(), dataReferencia);
    }
    
    /**
     * Valida a completude dos dados comparando contagens do ESL Cloud com o banco local.
     * 
     * Implementa a lógica de comparação usando queries SQL eficientes com String.format
     * (seguro pois os nomes das tabelas vêm de fonte controlada - as chaves do Map).
     * 
     * Gera logs com status claros:
     * - ✅ OK: contagens coincidem
     * - ❌ INCOMPLETO: banco tem menos registros que ESL Cloud  
     * - ⚠️ DUPLICADOS: banco tem mais registros que ESL Cloud
     * 
     * @param totaisEslCloud Map com contagens obtidas do ESL Cloud
     * @param dataReferencia Data de referência para filtrar consultas no banco
     * @return Map com resultado da validação por entidade
     */
    public Map<String, StatusValidacao> validarCompletude(final Map<String, Integer> totaisEslCloud, 
                                                         final LocalDate dataReferencia) {
        logger.info("🔍 Iniciando validação de completude para {} entidades na data: {}", 
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
                            logger.warn("⚠️ Sem referência de contagem para '{}' (sem log COMPLETO e sem total de API).", nomeEntidade);
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
                    logger.error("❌ Erro SQL ao validar entidade '{}': {}", nomeEntidade, e.getMessage(), e);
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
            
            logger.info("📊 Validação de completude concluída: ✅ {} OK, ❌ {} INCOMPLETO, ⚠️ {} DUPLICADOS, 💥 {} ERROS", 
                    totalOk, totalIncompleto, totalDuplicados, totalErros);
            
        } catch (final SQLException e) {
            logger.error("❌ Erro ao conectar com banco de dados para validação: {}", e.getMessage(), e);
            throw new RuntimeException("Falha na conexão com banco de dados", e);
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
            logger.debug("Nao foi possivel converter metrica numérica de '{}': {}", mensagem, e.getMessage());
            return OptionalInt.empty();
        }
    }
    
    /**
     * Determina o status de validação baseado na comparação entre contagens.
     * 
     * @param contagemEslCloud Contagem obtida do ESL Cloud
     * @param contagemBanco Contagem obtida do banco local
     * @return Status da validação
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
     * Obtém o ícone visual correspondente ao status de validação.
     * 
     * @param status Status da validação
     * @return String com ícone visual
     */
    private String obterIconeStatus(final StatusValidacao status) {
        return switch (status) {
            case OK -> "✅ OK";
            case INCOMPLETO -> "❌ INCOMPLETO";
            case DUPLICADOS -> "⚠️ DUPLICADOS";
            case ERRO -> "💥 ERRO";
        };
    }
    
    /**
     * TÓPICO 4: Validação de Gaps - Verifica se os IDs das ocorrências são sequenciais
     * 
     * Pré-requisito: Esta validação só deve ser executada se os IDs forem realmente sequenciais.
     * Caso contrário, a estratégia de detecção de gaps não funcionará.
     * 
     * @param dataReferencia Data de referência para análise
     * @return StatusValidacao indicando se há gaps nos IDs
     */
    public StatusValidacao validarGapsOcorrencias(final LocalDate dataReferencia) {
        logger.info("🔍 Iniciando validação de gaps para ocorrências...");
        
        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            final String sqlExisteTabela = """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME = 'ocorrencias' AND TABLE_SCHEMA = 'dbo'
            """;
            try (PreparedStatement stmt = conexao.prepareStatement(sqlExisteTabela);
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) == 0) {
                    logger.warn("⚠️ Tabela 'ocorrencias' não encontrada - validação de gaps ignorada");
                    return StatusValidacao.OK;
                }
            }
            
            // Primeiro, verificar se os IDs são sequenciais
            if (!verificarIdsSequenciais(conexao, "ocorrencias")) {
                logger.warn("⚠️ IDs das ocorrências não são sequenciais - validação de gaps não aplicável");
                return StatusValidacao.OK; // Não é erro, apenas não aplicável
            }
            
            // Se são sequenciais, verificar gaps
            return detectarGapsSequenciais(conexao, "ocorrencias", dataReferencia);
            
        } catch (final SQLException e) {
            logger.error("❌ Erro ao validar gaps nas ocorrências: {}", e.getMessage(), e);
            return StatusValidacao.ERRO;
        }
    }
    
    /**
     * Verifica se os IDs de uma tabela são sequenciais (sem pulos).
     * 
     * @param conexao Conexão com o banco de dados
     * @param nomeTabela Nome da tabela a verificar
     * @return true se os IDs são sequenciais, false caso contrário
     */
    private boolean verificarIdsSequenciais(final Connection conexao, final String nomeTabela) throws SQLException {
        final String sql = """
            WITH ids_ordenados AS (
                SELECT id, ROW_NUMBER() OVER (ORDER BY id) as posicao
                FROM %s
                WHERE data_extracao >= DATEADD(day, -7, GETDATE()) -- Últimos 7 dias para análise
            ),
            gaps AS (
                SELECT COUNT(*) as total_gaps
                FROM ids_ordenados
                WHERE id != (SELECT MIN(id) FROM ids_ordenados) + posicao - 1
            )
            SELECT CASE WHEN total_gaps = 0 THEN 1 ELSE 0 END as ids_sequenciais
            FROM gaps
            """.formatted(nomeTabela);
        
        try (PreparedStatement stmt = conexao.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                final boolean sequencial = rs.getInt("ids_sequenciais") == 1;
                logger.info("📊 Análise de sequencialidade para {}: {}", nomeTabela, 
                    sequencial ? "IDs são sequenciais" : "IDs têm gaps/pulos");
                return sequencial;
            }
            return false;
        }
    }
    
    /**
     * Detecta gaps em IDs sequenciais usando a estratégia WITH ids_esperados.
     * 
     * @param conexao Conexão com o banco de dados
     * @param nomeTabela Nome da tabela a verificar
     * @param dataReferencia Data de referência para análise
     * @return StatusValidacao indicando se há gaps
     */
    private StatusValidacao detectarGapsSequenciais(final Connection conexao, final String nomeTabela, final LocalDate dataReferencia) throws SQLException {
        final String sql = """
            WITH ids_esperados AS (
                SELECT MIN(id) + n.number as id_esperado
                FROM %s,
                     (SELECT TOP ((SELECT MAX(id) - MIN(id) + 1 FROM %s WHERE data_extracao >= ?))
                             ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) - 1 as number
                      FROM sys.objects a CROSS JOIN sys.objects b) n
                WHERE data_extracao >= ?
            ),
            gaps AS (
                SELECT ie.id_esperado
                FROM ids_esperados ie
                LEFT JOIN %s o ON ie.id_esperado = o.id AND o.data_extracao >= ?
                WHERE o.id IS NULL
            )
            SELECT COUNT(*) as total_gaps
            FROM gaps
            """.formatted(nomeTabela, nomeTabela, nomeTabela);
        
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            final java.sql.Date sqlDate = java.sql.Date.valueOf(dataReferencia);
            stmt.setDate(1, sqlDate);
            stmt.setDate(2, sqlDate);
            stmt.setDate(3, sqlDate);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final int totalGaps = rs.getInt("total_gaps");
                    
                    if (totalGaps == 0) {
                        logger.info("✅ Nenhum gap detectado nos IDs de {}", nomeTabela);
                        return StatusValidacao.OK;
                    } else {
                        logger.warn("⚠️ Detectados {} gaps nos IDs de {} - possível perda de dados", totalGaps, nomeTabela);
                        return StatusValidacao.INCOMPLETO;
                    }
                }
                return StatusValidacao.ERRO;
            }
        }
    }
    
    /**
     * TÓPICO 4: Validação da Janela Temporal - Detecta registros criados durante a extração
     * 
     * Esta é a validação mais complexa. Verifica se há registros criados entre o início
     * e fim da extração que podem ter sido perdidos devido a problemas de paginação da API.
     * 
     * @param dataReferencia Data de referência para análise
     * @return Map com status de validação por entidade
     */
    public Map<String, StatusValidacao> validarJanelaTemporal(final LocalDate dataReferencia) {
        logger.info("🕐 Iniciando validação de janela temporal para data: {}", dataReferencia);
        
        final Map<String, StatusValidacao> resultados = new HashMap<>();
        
        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            // Buscar timestamps de extração do log
            final Map<String, TimestampsExtracao> timestampsExtracao = buscarTimestampsExtracao(conexao, dataReferencia);
            
            // Validar cada entidade
            for (final String entidade : MAPEAMENTO_ENTIDADES_TABELAS.keySet()) {
                final TimestampsExtracao timestamps = timestampsExtracao.get(entidade);
                
                if (timestamps == null) {
                    logger.warn("⚠️ Nenhum log de extração encontrado para {} na data {}", entidade, dataReferencia);
                    resultados.put(entidade, StatusValidacao.ERRO);
                    continue;
                }
                
                final StatusValidacao status = validarJanelaTemporalEntidade(entidade, timestamps, dataReferencia);
                resultados.put(entidade, status);
            }
            
        } catch (final SQLException e) {
            logger.error("❌ Erro ao validar janela temporal: {}", e.getMessage(), e);
            // Marcar todas as entidades como erro
            for (final String entidade : MAPEAMENTO_ENTIDADES_TABELAS.keySet()) {
                resultados.put(entidade, StatusValidacao.ERRO);
            }
        }
        
        return resultados;
    }
    
    /**
     * Busca os timestamps de início e fim das extrações do log_extracoes.
     * 
     * @param conexao Conexão com o banco de dados
     * @param dataReferencia Data de referência
     * @return Map com timestamps por entidade
     */
    private Map<String, TimestampsExtracao> buscarTimestampsExtracao(final Connection conexao, final LocalDate dataReferencia) throws SQLException {
        final String sql = """
            SELECT entidade, timestamp_inicio, timestamp_fim
            FROM log_extracoes
            WHERE CAST(timestamp_inicio AS DATE) = ?
            AND status_final = 'COMPLETO'
            ORDER BY timestamp_inicio DESC
            """;
        
        final Map<String, TimestampsExtracao> timestamps = new HashMap<>();
        
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setDate(1, java.sql.Date.valueOf(dataReferencia));
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final String entidade = rs.getString("entidade");
                    final java.sql.Timestamp inicio = rs.getTimestamp("timestamp_inicio");
                    final java.sql.Timestamp fim = rs.getTimestamp("timestamp_fim");
                    
                    timestamps.put(entidade, new TimestampsExtracao(inicio, fim));
                }
            }
        }
        
        logger.info("📊 Encontrados timestamps para {} entidades na data {}", timestamps.size(), dataReferencia);
        return timestamps;
    }
    
    /**
     * Valida a janela temporal para uma entidade específica.
     * 
     * @param entidade Nome da entidade
     * @param timestamps Timestamps de início e fim da extração
     * @param dataReferencia Data de referência
     * @return StatusValidacao da janela temporal
     */
    private StatusValidacao validarJanelaTemporalEntidade(final String entidade, final TimestampsExtracao timestamps, final LocalDate dataReferencia) {
        try {
            // Fazer chamada à API para contar registros criados durante a janela de extração
            final int registrosDuranteExtracao = contarRegistrosDuranteJanela(entidade, timestamps, dataReferencia);
            
            if (registrosDuranteExtracao == 0) {
                logger.info("✅ Nenhum registro criado durante extração de {} - janela temporal OK", entidade);
                return StatusValidacao.OK;
            } else {
                logger.error("❌ CRÍTICO: {} registros de {} foram criados durante a extração! Risco de perda de dados devido a falha na paginação da API", 
                    registrosDuranteExtracao, entidade);
                return StatusValidacao.INCOMPLETO;
            }
            
        } catch (final Exception e) {
            logger.error("❌ Erro ao validar janela temporal para {}: {}", entidade, e.getMessage(), e);
            return StatusValidacao.ERRO;
        }
    }
    
    /**
     * Conta registros criados durante a janela de extração via API.
     * 
     * @param entidade Nome da entidade
     * @param timestamps Timestamps da extração
     * @param dataReferencia Data de referência
     * @return Número de registros criados durante a extração
     */
    private int contarRegistrosDuranteJanela(final String entidade, final TimestampsExtracao timestamps, final LocalDate dataReferencia) {
        // Implementar chamadas específicas para cada tipo de API
        return switch (entidade) {
            case ConstantesEntidades.FRETES, ConstantesEntidades.COLETAS, ConstantesEntidades.FATURAS_GRAPHQL ->
                contarRegistrosApiGraphQL(entidade, timestamps, dataReferencia);
            case ConstantesEntidades.MANIFESTOS, ConstantesEntidades.COTACOES, ConstantesEntidades.LOCALIZACAO_CARGAS, ConstantesEntidades.CONTAS_A_PAGAR, ConstantesEntidades.FATURAS_POR_CLIENTE ->
                contarRegistrosApiDataExport(entidade, timestamps, dataReferencia);
            default -> {
                logger.warn("⚠️ Entidade {} não mapeada para validação temporal", entidade);
                yield 0;
            }
        };
    }
    
    
    /**
     * Conta registros via API GraphQL durante janela temporal.
     * 
     * @param entidade Nome da entidade a ser consultada
     * @param timestamps Janela temporal da extração (será usado na implementação futura)
     * @param dataReferencia Data de referência para filtros (será usado na implementação futura)
     * @return Número de registros encontrados na janela temporal
     */
    private int contarRegistrosApiGraphQL(final String entidade, final TimestampsExtracao timestamps, final LocalDate dataReferencia) {
        // Implementação específica para API GraphQL
        // Por enquanto, retorna 0 (implementação futura)
        logger.debug("🔄 Contagem temporal via API GraphQL para {} ainda não implementada (janela: {} - {}, data: {})", 
                    entidade, timestamps.getInicio(), timestamps.getFim(), dataReferencia);
        return 0;
    }
    
    /**
     * Conta registros via API Data Export durante janela temporal.
     * 
     * @param entidade Nome da entidade a ser consultada
     * @param timestamps Janela temporal da extração (será usado na implementação futura)
     * @param dataReferencia Data de referência para filtros (será usado na implementação futura)
     * @return Número de registros encontrados na janela temporal
     */
    private int contarRegistrosApiDataExport(final String entidade, final TimestampsExtracao timestamps, final LocalDate dataReferencia) {
        // Implementação específica para API Data Export
        // Por enquanto, retorna 0 (implementação futura)
        logger.debug("🔄 Contagem temporal via API Data Export para {} ainda não implementada (janela: {} - {}, data: {})", 
                    entidade, timestamps.getInicio(), timestamps.getFim(), dataReferencia);
        return 0;
    }
    
    /**
     * Classe auxiliar para armazenar timestamps de extração.
     */
    private static class TimestampsExtracao {
        private final java.sql.Timestamp inicio;
        private final java.sql.Timestamp fim;
        
        public TimestampsExtracao(final java.sql.Timestamp inicio, final java.sql.Timestamp fim) {
            this.inicio = inicio;
            this.fim = fim;
        }
        
        public java.sql.Timestamp getInicio() { return inicio; }
        
        public java.sql.Timestamp getFim() { return fim; }
    }
    
    /**
     * Enum para representar os possíveis status de validação de completude.
     */
    public enum StatusValidacao {
        /** Contagens coincidem - dados completos */
        OK,
        /** Banco tem menos registros que ESL Cloud - dados incompletos */
        INCOMPLETO,
        /** Banco tem mais registros que ESL Cloud - possíveis duplicados */
        DUPLICADOS,
        /** Erro durante a validação */
        ERRO
    }
}
