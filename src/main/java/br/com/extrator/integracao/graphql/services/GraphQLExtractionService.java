/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/graphql/services/GraphQLExtractionService.java
Classe  : GraphQLExtractionService (class)
Pacote  : br.com.extrator.integracao.graphql.services
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

package br.com.extrator.integracao.graphql.services;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import br.com.extrator.integracao.ClienteApiGraphQL;
import br.com.extrator.persistencia.repositorio.ColetaRepository;
import br.com.extrator.persistencia.repositorio.FreteRepository;
import br.com.extrator.persistencia.repositorio.FaturaGraphQLRepository;
import br.com.extrator.persistencia.repositorio.FaturaPorClienteRepository;
import br.com.extrator.persistencia.repositorio.LogExtracaoRepository;
import br.com.extrator.persistencia.repositorio.UsuarioSistemaRepository;
import br.com.extrator.integracao.mapeamento.graphql.coletas.ColetaMapper;
import br.com.extrator.integracao.mapeamento.graphql.fretes.FreteMapper;
import br.com.extrator.integracao.mapeamento.graphql.usuarios.UsuarioSistemaMapper;
import br.com.extrator.integracao.comum.ConstantesExtracao;
import br.com.extrator.integracao.comum.ExtractionHelper;
import br.com.extrator.integracao.comum.ExtractionLogger;
import br.com.extrator.integracao.comum.ExtractionResult;
import br.com.extrator.integracao.graphql.extractors.ColetaExtractor;
import br.com.extrator.integracao.graphql.extractors.FreteExtractor;
import br.com.extrator.integracao.graphql.extractors.FaturaGraphQLExtractor;
import br.com.extrator.integracao.graphql.extractors.UsuarioSistemaExtractor;
import br.com.extrator.suporte.configuracao.ConfigBanco;
import br.com.extrator.suporte.configuracao.ConfigEtl;
import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.tempo.RelogioSistema;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

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
        final String pipelineExecutionId =
            br.com.extrator.suporte.observabilidade.ExecutionContext.currentExecutionId();
        this.apiClient.setExecutionUuid(
            "n/a".equals(pipelineExecutionId)
                ? java.util.UUID.randomUUID().toString()
                : pipelineExecutionId
        );
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
        final LocalDateTime inicioExecucao = RelogioSistema.agora();
        final List<ExtractionResult> resultados = new ArrayList<>();
        
        log.info("");
        log.info("=".repeat(80));
        log.info("INICIANDO EXTRACOES GRAPHQL");
        log.info("=".repeat(80));
        log.info("Periodo: {} a {}", dataInicio, dataFim != null ? dataFim : dataInicio);
        log.info("Inicio: {}", inicioExecucao);
        log.info("Entidade(s): {}", entidade == null || entidade.isBlank() ? "TODAS" : entidade);
        log.info("Modo de integridade: {}", ConfigEtl.obterModoIntegridadeEtl());
        log.info("");
        
        ConfigBanco.validarConexaoBancoDados();
        ConfigBanco.validarTabelasEssenciais();
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
                log.error("[ERRO] Erro ao extrair Usuarios do Sistema: {}. Indo para a proxima entidade; sera reextraida na proxima execucao.", e.getMessage(), e);
                resultados.add(ExtractionResult.erro(ConstantesEntidades.USUARIOS_SISTEMA, RelogioSistema.agora(), e).build());
            }
            ExtractionHelper.aplicarDelay();
        } else if (executarUsuariosSistema) {
            try {
                final ExtractionResult resultUsuarios = extractUsuarios(dataInicio, dataFim, true);
                if (resultUsuarios != null) {
                    resultados.add(resultUsuarios);
                }
            } catch (final Exception e) {
                log.error("[ERRO] Erro ao extrair Usuarios do Sistema: {}. Indo para a proxima entidade; sera reextraida na proxima execucao.", e.getMessage(), e);
                resultados.add(ExtractionResult.erro(ConstantesEntidades.USUARIOS_SISTEMA, RelogioSistema.agora(), e).build());
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
                log.error("[ERRO] Erro ao extrair Coletas: {}. Indo para a proxima entidade; sera reextraida na proxima execucao.", e.getMessage(), e);
                resultados.add(ExtractionResult.erro(ConstantesEntidades.COLETAS, RelogioSistema.agora(), e).build());
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
                log.error("[ERRO] Erro ao extrair Fretes: {}. Indo para a proxima entidade; sera reextraida na proxima execucao.", e.getMessage(), e);
                resultados.add(ExtractionResult.erro(ConstantesEntidades.FRETES, RelogioSistema.agora(), e).build());
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
                log.error("[ERRO] Erro ao extrair Faturas GraphQL: {}. Indo para a proxima entidade; sera reextraida na proxima execucao.", e.getMessage(), e);
                resultados.add(ExtractionResult.erro(ConstantesEntidades.FATURAS_GRAPHQL, RelogioSistema.agora(), e).build());
            }
            ExtractionHelper.aplicarDelay();
        } else if (executarFaturasGraphql) {
            // Faturas GraphQL será executado POR ÚLTIMO pelo comando orquestrador
            log.info("[INFO] Faturas GraphQL sera extraido POR ULTIMO apos todas as outras entidades (FASE 3)");
        }

        // Resumo consolidado final
        exibirResumoConsolidado(resultados, inicioExecucao);

        // Se alguma entidade falhou, propagar falha para o comando não marcar extração como sucesso
        final boolean modoEstrito = ConfigEtl.isModoIntegridadeEstrito();
        final List<String> entidadesComFalha = resultados.stream()
            .filter(r -> modoEstrito
                ? !ConstantesEntidades.STATUS_COMPLETO.equals(r.getStatus())
                : ConstantesEntidades.STATUS_ERRO_API.equals(r.getStatus()))
            .map(r -> r.getEntityName() + "(" + r.getStatus() + ")")
            .toList();
        if (!entidadesComFalha.isEmpty()) {
            throw new RuntimeException("Extração GraphQL com falhas: " + String.join(", ", entidadesComFalha)
                + ". Verifique os logs. A extração NÃO deve ser considerada concluída com sucesso.");
        }
    }
    
    public ExtractionResult executarSomenteColetasReferencial(final LocalDate dataInicio, final LocalDate dataFim) {
        this.entidadeEspecifica = ConstantesEntidades.COLETAS;
        final LocalDateTime inicioExecucao = RelogioSistema.agora();

        log.info("");
        log.info("=".repeat(80));
        log.info("INICIANDO EXTRACAO GRAPHQL AUXILIAR: COLETAS REFERENCIAL");
        log.info("=".repeat(80));
        log.info("Periodo: {} a {}", dataInicio, dataFim != null ? dataFim : dataInicio);
        log.info("Inicio: {}", inicioExecucao);
        log.info("Modo: AUXILIAR_REFERENCIAL");
        log.info("");

        ConfigBanco.validarConexaoBancoDados();
        ConfigBanco.validarTabelasEssenciais();
        ExtractionHelper.limparAvisosSeguranca();

        final ExtractionResult resultado = extractColetas(dataInicio, dataFim);
        exibirResumoConsolidado(List.of(resultado), inicioExecucao);

        final boolean modoEstrito = ConfigEtl.isModoIntegridadeEstrito();
        final boolean possuiFalha = modoEstrito
            ? !ConstantesEntidades.STATUS_COMPLETO.equals(resultado.getStatus())
            : ConstantesEntidades.STATUS_ERRO_API.equals(resultado.getStatus());
        if (possuiFalha) {
            throw new RuntimeException(
                "Extracao GraphQL auxiliar de coletas com falhas: "
                    + resultado.getEntityName()
                    + "(" + resultado.getStatus() + "). Verifique os logs."
            );
        }

        return resultado;
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
        log.info(ConstantesExtracao.MSG_LOG_EXTRAINDO_COM_MOTIVO, extractor.getEmoji(), "Usuarios do Sistema", motivo);
        
        final ExtractionResult result = logger.executeWithLogging(extractor, dataInicio, dataFim, extractor.getEmoji());
        logRepository.gravarLogExtracao(result.toLogEntity());
        
        if (throwOnError && ConstantesEntidades.STATUS_ERRO_API.equals(result.getStatus())) {
            throw new RuntimeException(String.format(ConstantesExtracao.MSG_ERRO_EXTRACAO, "usuarios do sistema"), result.getErro());
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
        final LocalDateTime fimExecucao = RelogioSistema.agora();
        final Duration duracaoTotal = Duration.between(inicioExecucao, fimExecucao);
        
        log.info("");
        log.info("=".repeat(80));
        log.info("RESUMO CONSOLIDADO GRAPHQL");
        log.info("=".repeat(80));
        
        final int totalEntidades = resultados.size();
        int entidadesComSucesso = 0;
        int entidadesIncompletas = 0;
        int entidadesComErro = 0;
        int totalRegistrosExtraidos = 0;
        int totalRegistrosSalvos = 0;
        int totalPaginas = 0;
        
        for (final ExtractionResult result : resultados) {
            if (ConstantesEntidades.STATUS_COMPLETO.equals(result.getStatus())) {
                entidadesComSucesso++;
            } else if (ConstantesEntidades.STATUS_ERRO_API.equals(result.getStatus())) {
                entidadesComErro++;
            } else {
                entidadesIncompletas++;
            }
            totalRegistrosExtraidos += result.getRegistrosExtraidos();
            totalRegistrosSalvos += result.getRegistrosSalvos();
            totalPaginas += result.getPaginasProcessadas();
        }
        
        log.info("Estatisticas Gerais:");
        log.info("   - Entidades processadas: {}", totalEntidades);
        log.info("   - [OK] Sucessos: {}", entidadesComSucesso);
        if (entidadesIncompletas > 0) {
            log.info("   - [AVISO] Incompletas: {}", entidadesIncompletas);
        }
        if (entidadesComErro > 0) {
            log.info("   - [ERRO] Erros: {}", entidadesComErro);
        }
        log.info("");
        log.info("Volumes:");
        log.info("   - Total extraido da API: {} registros", formatarNumero(totalRegistrosExtraidos));
        log.info("   - Total salvo no banco: {} registros", formatarNumero(totalRegistrosSalvos));
        log.info("   - Total de paginas: {}", formatarNumero(totalPaginas));
        log.info("");
        log.info("Performance:");
        log.info("   - Tempo total: {} ms ({} s)", 
            duracaoTotal.toMillis(), 
            String.format("%.2f", duracaoTotal.toMillis() / 1000.0));
        if (totalRegistrosSalvos > 0 && duracaoTotal.toMillis() > 0) {
            final double registrosPorSegundo = (totalRegistrosSalvos * 1000.0) / duracaoTotal.toMillis();
            log.info("   - Taxa media: {} registros/segundo", String.format("%.2f", registrosPorSegundo));
        }
        log.info("");
        log.info("Detalhamento por Entidade:");
        for (int i = 0; i < resultados.size(); i++) {
            final ExtractionResult result = resultados.get(i);
            final String statusIcon;
            if (ConstantesEntidades.STATUS_COMPLETO.equals(result.getStatus())) {
                statusIcon = "[OK]";
            } else if (ConstantesEntidades.STATUS_ERRO_API.equals(result.getStatus())) {
                statusIcon = "[ERRO]";
            } else {
                statusIcon = "[AVISO]";
            }
            log.info("   {}. {} {}: {} registros salvos | {} paginas | {}",
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
            if (!ConstantesEntidades.STATUS_COMPLETO.equals(r.getStatus())) {
                eventos.add("Entidade " + r.getEntityName() + " nao concluiu como COMPLETO (status="
                    + r.getStatus() + "). Sera reextraida na proxima execucao.");
            }
        }
        if (!eventos.isEmpty()) {
            log.info("");
            log.info("[AVISO] EVENTOS / OBSERVACOES (registrado para auditoria):");
            for (final String ev : eventos) {
                log.info("   - {}", ev);
            }
        }

        log.info("");
        log.info("Fim: {}", fimExecucao);
        log.info("=".repeat(80));
        if (entidadesComErro > 0 || entidadesIncompletas > 0) {
            final String resumoFalhas = "[AVISO] EXTRACOES GRAPHQL COM NAO CONFORMIDADES "
                + "(incompletas=" + entidadesIncompletas + ", erros=" + entidadesComErro + ")";
            log.info(resumoFalhas);
        } else {
            log.info("[OK] EXTRACOES GRAPHQL CONCLUIDAS");
        }
        log.info("=".repeat(80));
        log.info("");
    }
    
    public void executar(final LocalDate dataInicio, final LocalDate dataFim, final String entidade) {
        execute(dataInicio, dataFim, entidade);
    }

    private String formatarNumero(final int numero) {
        return String.format("%,d", numero);
    }
}
