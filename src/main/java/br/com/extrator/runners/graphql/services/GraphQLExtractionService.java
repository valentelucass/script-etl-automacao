/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/graphql/services/GraphQLExtractionService.java
Classe  : GraphQLExtractionService (class)
Pacote  : br.com.extrator.runners.graphql.services
Modulo  : Servico de execucao GraphQL
Papel   : Implementa responsabilidade de graph qlextraction service.

Conecta com:
- ClienteApiGraphQL (api)
- ColetaRepository (db.repository)
- FreteRepository (db.repository)
- FaturaGraphQLRepository (db.repository)
- FaturaPorClienteRepository (db.repository)
- LogExtracaoRepository (db.repository)
- UsuarioSistemaRepository (db.repository)
- ColetaMapper (modelo.graphql.coletas)

Fluxo geral:
1) Coordena extractors da API GraphQL.
2) Controla ordem, limites e logging do processamento.
3) Propaga resultado consolidado para o runner.

Estrutura interna:
Metodos principais:
- GraphQLExtractionService(): realiza operacao relacionada a "graph qlextraction service".
- execute(...3 args): realiza operacao relacionada a "execute".
- shouldExecute(...2 args): realiza operacao relacionada a "should execute".
- shouldExecute(...3 args): realiza operacao relacionada a "should execute".
- extractUsuarios(...3 args): realiza operacao relacionada a "extract usuarios".
- extractColetas(...2 args): realiza operacao relacionada a "extract coletas".
- extractFretes(...2 args): realiza operacao relacionada a "extract fretes".
- extractFaturasGraphQL(...2 args): realiza operacao relacionada a "extract faturas graph ql".
- exibirResumoConsolidado(...2 args): realiza operacao relacionada a "exibir resumo consolidado".
- formatarNumero(...1 args): realiza operacao relacionada a "formatar numero".
Atributos-chave:
- apiClient: cliente de integracao externa.
- logRepository: dependencia de acesso a banco.
- logger: logger da classe para diagnostico.
- log: campo de estado para "log".
- entidadeEspecifica: campo de estado para "entidade especifica".
[DOC-FILE-END]============================================================== */

package br.com.extrator.runners.graphql.services;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import br.com.extrator.api.ClienteApiGraphQL;
import br.com.extrator.db.repository.ColetaRepository;
import br.com.extrator.db.repository.FreteRepository;
import br.com.extrator.db.repository.FaturaGraphQLRepository;
import br.com.extrator.db.repository.FaturaPorClienteRepository;
import br.com.extrator.db.repository.LogExtracaoRepository;
import br.com.extrator.db.repository.UsuarioSistemaRepository;
import br.com.extrator.modelo.graphql.coletas.ColetaMapper;
import br.com.extrator.modelo.graphql.fretes.FreteMapper;
import br.com.extrator.modelo.graphql.usuarios.UsuarioSistemaMapper;
import br.com.extrator.runners.common.ConstantesExtracao;
import br.com.extrator.runners.common.ExtractionHelper;
import br.com.extrator.runners.common.ExtractionLogger;
import br.com.extrator.runners.common.ExtractionResult;
import br.com.extrator.runners.graphql.extractors.ColetaExtractor;
import br.com.extrator.runners.graphql.extractors.FreteExtractor;
import br.com.extrator.runners.graphql.extractors.FaturaGraphQLExtractor;
import br.com.extrator.runners.graphql.extractors.UsuarioSistemaExtractor;
import br.com.extrator.util.configuracao.CarregadorConfig;
import br.com.extrator.util.console.LoggerConsole;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Serviço de orquestração para extrações GraphQL.
 * Coordena a execução de todas as entidades GraphQL com logs detalhados e resumos consolidados.
 */
public class GraphQLExtractionService {
    
    private final ClienteApiGraphQL apiClient;
    private final LogExtracaoRepository logRepository;
    private final ExtractionLogger logger;
    private final LoggerConsole log;
    
    public GraphQLExtractionService() {
        this.apiClient = new ClienteApiGraphQL();
        this.apiClient.setExecutionUuid(java.util.UUID.randomUUID().toString());
        this.logRepository = new LogExtracaoRepository();
        this.logger = new ExtractionLogger(GraphQLExtractionService.class);
        this.log = LoggerConsole.getLogger(GraphQLExtractionService.class);
    }
    
    /**
     * Executa extrações GraphQL baseado nos parâmetros fornecidos.
     * 
     * @param dataInicio Data de início
     * @param dataFim Data de fim
     * @param entidade Nome da entidade específica (null = todas)
     * @throws RuntimeException Se houver falha crítica na extração
     */
    // Referência para a entidade específica (usada na lógica de faturas_graphql)
    private String entidadeEspecifica;
    
    public void execute(final LocalDate dataInicio, final LocalDate dataFim, final String entidade) {
        this.entidadeEspecifica = entidade;
        final LocalDateTime inicioExecucao = LocalDateTime.now();
        final List<ExtractionResult> resultados = new ArrayList<>();
        
        log.info("");
        log.info("╔" + "═".repeat(78) + "╗");
        log.info("║" + " ".repeat(20) + "🚀 INICIANDO EXTRAÇÕES GRAPHQL" + " ".repeat(26) + "║");
        log.info("╚" + "═".repeat(78) + "╝");
        log.info("📅 Período: {} a {}", dataInicio, dataFim != null ? dataFim : dataInicio);
        log.info("⏰ Início: {}", inicioExecucao);
        log.info("🎯 Entidade(s): {}", entidade == null || entidade.isBlank() ? "TODAS" : entidade);
        log.info("");
        
        CarregadorConfig.validarConexaoBancoDados();
        CarregadorConfig.validarTabelasEssenciais();
        ExtractionHelper.limparAvisosSeguranca();

        final boolean executarColetas = shouldExecute(entidade, ConstantesEntidades.COLETAS);
        final boolean executarFretes = shouldExecute(entidade, ConstantesEntidades.FRETES);
        final boolean executarFaturasGraphql = shouldExecute(entidade, ConstantesEntidades.FATURAS_GRAPHQL,
            ConstantesEntidades.ALIASES_FATURAS_GRAPHQL);
        final boolean executarUsuariosSistema = shouldExecute(entidade, ConstantesEntidades.USUARIOS_SISTEMA);

        // Extrair usuários ANTES de coletas (dependência)
        if (executarColetas) {
            try {
                final ExtractionResult resultUsuarios = extractUsuarios(dataInicio, dataFim, false);
                if (resultUsuarios != null) {
                    resultados.add(resultUsuarios);
                }
            } catch (final Exception e) {
                log.error("❌ Erro ao extrair Usuários do Sistema: {}. Indo para a próxima entidade; será reextraída na próxima execução.", e.getMessage(), e);
                resultados.add(ExtractionResult.erro(ConstantesEntidades.USUARIOS_SISTEMA, LocalDateTime.now(), e).build());
            }
            ExtractionHelper.aplicarDelay();
        } else if (executarUsuariosSistema) {
            try {
                final ExtractionResult resultUsuarios = extractUsuarios(dataInicio, dataFim, true);
                if (resultUsuarios != null) {
                    resultados.add(resultUsuarios);
                }
            } catch (final Exception e) {
                log.error("❌ Erro ao extrair Usuários do Sistema: {}. Indo para a próxima entidade; será reextraída na próxima execução.", e.getMessage(), e);
                resultados.add(ExtractionResult.erro(ConstantesEntidades.USUARIOS_SISTEMA, LocalDateTime.now(), e).build());
            }
            ExtractionHelper.aplicarDelay();
        }

        if (executarColetas) {
            try {
                final ExtractionResult result = extractColetas(dataInicio, dataFim);
                if (result != null) {
                    resultados.add(result);
                }
            } catch (final Exception e) {
                log.error("❌ Erro ao extrair Coletas: {}. Indo para a próxima entidade; será reextraída na próxima execução.", e.getMessage(), e);
                resultados.add(ExtractionResult.erro(ConstantesEntidades.COLETAS, LocalDateTime.now(), e).build());
            }
            ExtractionHelper.aplicarDelay();
        }

        if (executarFretes) {
            try {
                final ExtractionResult result = extractFretes(dataInicio, dataFim);
                if (result != null) {
                    resultados.add(result);
                }
            } catch (final Exception e) {
                log.error("❌ Erro ao extrair Fretes: {}. Indo para a próxima entidade; será reextraída na próxima execução.", e.getMessage(), e);
                resultados.add(ExtractionResult.erro(ConstantesEntidades.FRETES, LocalDateTime.now(), e).build());
            }
            ExtractionHelper.aplicarDelay();
        }

        // FASE 3: FATURAS_GRAPHQL foi movido para ser executado POR ÚLTIMO
        // A extração de faturas_graphql agora é controlada pelos comandos (ExecutarFluxoCompletoComando
        // e ExecutarExtracaoPorIntervaloComando) que chamam GraphQLRunner.executarFaturasGraphQLPorIntervalo()
        // APÓS todas as outras entidades serem extraídas.
        // 
        // Motivo: O enriquecimento de faturas_graphql é muito demorado (50+ minutos),
        // então as outras entidades são priorizadas para garantir dados parciais atualizados no BI.
        //
        // Se executarFaturasGraphql for true E estivermos em modo de extração específica de faturas_graphql,
        // executamos aqui. Caso contrário, deixamos para o comando orquestrador.
        final boolean isSomenteFaturasGraphQL = entidadeEspecifica != null && 
            (ConstantesEntidades.FATURAS_GRAPHQL.equalsIgnoreCase(entidadeEspecifica) ||
             java.util.Arrays.stream(ConstantesEntidades.ALIASES_FATURAS_GRAPHQL)
                 .anyMatch(alias -> alias.equalsIgnoreCase(entidadeEspecifica)));
        
        if (executarFaturasGraphql && isSomenteFaturasGraphQL) {
            // Extração específica de faturas_graphql foi solicitada explicitamente
            try {
                final ExtractionResult result = extractFaturasGraphQL(dataInicio, dataFim);
                if (result != null) {
                    resultados.add(result);
                }
            } catch (final Exception e) {
                log.error("❌ Erro ao extrair Faturas GraphQL: {}. Indo para a próxima entidade; será reextraída na próxima execução.", e.getMessage(), e);
                resultados.add(ExtractionResult.erro(ConstantesEntidades.FATURAS_GRAPHQL, LocalDateTime.now(), e).build());
            }
            ExtractionHelper.aplicarDelay();
        } else if (executarFaturasGraphql) {
            // Faturas GraphQL será executado POR ÚLTIMO pelo comando orquestrador
            log.info("ℹ️ Faturas GraphQL será extraído POR ÚLTIMO após todas as outras entidades (FASE 3)");
        }

        // Resumo consolidado final
        exibirResumoConsolidado(resultados, inicioExecucao);

        // Se alguma entidade falhou, propagar falha para o comando não marcar extração como sucesso
        final List<String> entidadesComFalha = resultados.stream()
            .filter(r -> !r.isSucesso())
            .map(ExtractionResult::getEntityName)
            .toList();
        if (!entidadesComFalha.isEmpty()) {
            throw new RuntimeException("Extração GraphQL com falhas: " + String.join(", ", entidadesComFalha)
                + ". Verifique os logs. A extração NÃO deve ser considerada concluída com sucesso.");
        }
    }
    
    private boolean shouldExecute(final String entidade, final String entityName) {
        return entidade == null || entidade.isBlank() || entityName.equalsIgnoreCase(entidade);
    }
    
    private boolean shouldExecute(final String entidade, final String entityName, final String[] aliases) {
        if (entidade == null || entidade.isBlank()) {
            return true;
        }
        if (entityName.equalsIgnoreCase(entidade)) {
            return true;
        }
        return Arrays.stream(aliases).anyMatch(alias -> alias.equalsIgnoreCase(entidade));
    }
    
    
    private ExtractionResult extractUsuarios(final LocalDate dataInicio, final LocalDate dataFim, final boolean throwOnError) {
        final UsuarioSistemaExtractor extractor = new UsuarioSistemaExtractor(
            apiClient,
            new UsuarioSistemaRepository(),
            new UsuarioSistemaMapper()
        );
        
        final String motivo = throwOnError ? "" : ConstantesExtracao.MSG_MOTIVO_USUARIOS_COLETAS;
        log.info(ConstantesExtracao.MSG_LOG_EXTRAINDO_COM_MOTIVO, extractor.getEmoji(), "Usuários do Sistema", motivo);
        
        final ExtractionResult result = logger.executeWithLogging(extractor, dataInicio, dataFim, extractor.getEmoji());
        logRepository.gravarLogExtracao(result.toLogEntity());
        
        if (!result.isSucesso() && throwOnError) {
            throw new RuntimeException(String.format(ConstantesExtracao.MSG_ERRO_EXTRACAO, "usuários do sistema"), result.getErro());
        }
        
        return result;
    }
    
    private ExtractionResult extractColetas(final LocalDate dataInicio, final LocalDate dataFim) {
        final ColetaExtractor extractor = new ColetaExtractor(
            apiClient,
            new ColetaRepository(),
            new ColetaMapper()
        );
        
        final ExtractionResult result = logger.executeWithLogging(extractor, dataInicio, dataFim, extractor.getEmoji());
        logRepository.gravarLogExtracao(result.toLogEntity());
        
        return result;
    }

    private ExtractionResult extractFretes(final LocalDate dataInicio, final LocalDate dataFim) {
        final FreteExtractor extractor = new FreteExtractor(
            apiClient,
            new FreteRepository(),
            new FreteMapper()
        );
        
        final ExtractionResult result = logger.executeWithLogging(extractor, dataInicio, dataFim, extractor.getEmoji());
        logRepository.gravarLogExtracao(result.toLogEntity());
        
        if (!result.isSucesso()) {
            throw new RuntimeException(String.format(ConstantesExtracao.MSG_ERRO_EXTRACAO, "fretes"), result.getErro());
        }
        
        return result;
    }
    
    private ExtractionResult extractFaturasGraphQL(final LocalDate dataInicio, final LocalDate dataFim) {
        final FaturaGraphQLExtractor extractor = new FaturaGraphQLExtractor(
            apiClient,
            new FaturaGraphQLRepository(),
            new FaturaPorClienteRepository(),
            new FreteRepository(),
            log
        );
        
        final ExtractionResult result = logger.executeWithLogging(extractor, dataInicio, dataFim, extractor.getEmoji());
        logRepository.gravarLogExtracao(result.toLogEntity());
        
        return result;
    }

    /**
     * Exibe resumo consolidado de todas as extrações GraphQL executadas.
     */
    private void exibirResumoConsolidado(final List<ExtractionResult> resultados, final LocalDateTime inicioExecucao) {
        final LocalDateTime fimExecucao = LocalDateTime.now();
        final Duration duracaoTotal = Duration.between(inicioExecucao, fimExecucao);
        
        log.info("");
        log.info("╔" + "═".repeat(78) + "╗");
        log.info("║" + " ".repeat(20) + "📊 RESUMO CONSOLIDADO GRAPHQL" + " ".repeat(26) + "║");
        log.info("╚" + "═".repeat(78) + "╝");
        
        final int totalEntidades = resultados.size();
        int entidadesComSucesso = 0;
        int entidadesIncompletas = 0;
        int entidadesComErro = 0;
        int totalRegistrosExtraidos = 0;
        int totalRegistrosSalvos = 0;
        int totalPaginas = 0;
        
        for (final ExtractionResult result : resultados) {
            if (result.isSucesso()) {
                entidadesComSucesso++;
                if (!ConstantesEntidades.STATUS_COMPLETO.equals(result.getStatus())) {
                    entidadesIncompletas++;
                }
            } else {
                entidadesComErro++;
            }
            totalRegistrosExtraidos += result.getRegistrosExtraidos();
            totalRegistrosSalvos += result.getRegistrosSalvos();
            totalPaginas += result.getPaginasProcessadas();
        }
        
        log.info("📈 Estatísticas Gerais:");
        log.info("   • Entidades processadas: {}", totalEntidades);
        log.info("   • ✅ Sucessos: {}", entidadesComSucesso);
        if (entidadesIncompletas > 0) {
            log.info("   • ⚠️ Incompletas: {}", entidadesIncompletas);
        }
        if (entidadesComErro > 0) {
            log.info("   • ❌ Erros: {}", entidadesComErro);
        }
        log.info("");
        log.info("📊 Volumes:");
        log.info("   • Total extraído da API: {} registros", formatarNumero(totalRegistrosExtraidos));
        log.info("   • Total salvo no banco: {} registros", formatarNumero(totalRegistrosSalvos));
        log.info("   • Total de páginas: {}", formatarNumero(totalPaginas));
        log.info("");
        log.info("⏱️ Performance:");
        log.info("   • Tempo total: {} ms ({} s)", 
            duracaoTotal.toMillis(), 
            String.format("%.2f", duracaoTotal.toMillis() / 1000.0));
        if (totalRegistrosSalvos > 0 && duracaoTotal.toMillis() > 0) {
            final double registrosPorSegundo = (totalRegistrosSalvos * 1000.0) / duracaoTotal.toMillis();
            log.info("   • Taxa média: {} registros/segundo", String.format("%.2f", registrosPorSegundo));
        }
        log.info("");
        log.info("📋 Detalhamento por Entidade:");
        for (int i = 0; i < resultados.size(); i++) {
            final ExtractionResult result = resultados.get(i);
            final String statusIcon = result.isSucesso()
                ? (ConstantesEntidades.STATUS_COMPLETO.equals(result.getStatus()) ? "✅" : "⚠️")
                : "❌";
            log.info("   {}. {} {}: {} registros salvos | {} páginas | {}",
                i + 1,
                statusIcon,
                result.getEntityName(),
                formatarNumero(result.getRegistrosSalvos()),
                result.getPaginasProcessadas(),
                result.getStatus());
        }

        // EVENTOS / OBSERVAÇÕES: timeouts, entidades com erro (ficam gravados no log)
        final List<String> eventos = new ArrayList<>(ExtractionHelper.drenarAvisosSeguranca());
        for (final ExtractionResult r : resultados) {
            if (!r.isSucesso()) {
                eventos.add("Entidade " + r.getEntityName() + " falhou. Será reextraída na próxima execução.");
            }
        }
        if (!eventos.isEmpty()) {
            log.info("");
            log.info("⚠️ EVENTOS / OBSERVAÇÕES (registrado para auditoria):");
            for (final String ev : eventos) {
                log.info("   • {}", ev);
            }
        }

        log.info("");
        log.info("⏰ Fim: {}", fimExecucao);
        log.info("╔" + "═".repeat(78) + "╗");
        log.info("║" + " ".repeat(20) + "✅ EXTRAÇÕES GRAPHQL CONCLUÍDAS" + " ".repeat(26) + "║");
        log.info("╚" + "═".repeat(78) + "╝");
        log.info("");
    }
    
    private String formatarNumero(final int numero) {
        return String.format("%,d", numero);
    }
}
