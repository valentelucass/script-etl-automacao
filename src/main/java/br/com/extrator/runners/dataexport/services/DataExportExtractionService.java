/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/dataexport/services/DataExportExtractionService.java
Classe  : DataExportExtractionService (class)
Pacote  : br.com.extrator.runners.dataexport.services
Modulo  : Servico de execucao DataExport
Papel   : Implementa responsabilidade de data export extraction service.

Conecta com:
- ClienteApiDataExport (api)
- ContasAPagarRepository (db.repository)
- CotacaoRepository (db.repository)
- FaturaPorClienteRepository (db.repository)
- LocalizacaoCargaRepository (db.repository)
- LogExtracaoRepository (db.repository)
- ManifestoRepository (db.repository)
- ContasAPagarMapper (modelo.dataexport.contasapagar)

Fluxo geral:
1) Coordena extractors da API DataExport.
2) Aplica deduplicacao/normalizacao quando necessario.
3) Encaminha resultado consolidado para o runner.

Estrutura interna:
Metodos principais:
- DataExportExtractionService(): realiza operacao relacionada a "data export extraction service".
- execute(...3 args): realiza operacao relacionada a "execute".
- extractManifestos(...2 args): realiza operacao relacionada a "extract manifestos".
- extractCotacoes(...2 args): realiza operacao relacionada a "extract cotacoes".
- extractLocalizacoes(...2 args): realiza operacao relacionada a "extract localizacoes".
- extractContasAPagar(...2 args): realiza operacao relacionada a "extract contas apagar".
- extractFaturasPorCliente(...2 args): realiza operacao relacionada a "extract faturas por cliente".
- exibirResumoConsolidado(...2 args): realiza operacao relacionada a "exibir resumo consolidado".
- formatarNumero(...1 args): realiza operacao relacionada a "formatar numero".
Atributos-chave:
- apiClient: cliente de integracao externa.
- logRepository: dependencia de acesso a banco.
- logger: logger da classe para diagnostico.
- log: campo de estado para "log".
[DOC-FILE-END]============================================================== */

package br.com.extrator.runners.dataexport.services;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import br.com.extrator.api.ClienteApiDataExport;
import br.com.extrator.db.repository.ContasAPagarRepository;
import br.com.extrator.db.repository.CotacaoRepository;
import br.com.extrator.db.repository.FaturaPorClienteRepository;
import br.com.extrator.db.repository.LocalizacaoCargaRepository;
import br.com.extrator.db.repository.LogExtracaoRepository;
import br.com.extrator.db.repository.ManifestoRepository;
import br.com.extrator.modelo.dataexport.contasapagar.ContasAPagarMapper;
import br.com.extrator.modelo.dataexport.cotacao.CotacaoMapper;
import br.com.extrator.modelo.dataexport.faturaporcliente.FaturaPorClienteMapper;
import br.com.extrator.modelo.dataexport.localizacaocarga.LocalizacaoCargaMapper;
import br.com.extrator.modelo.dataexport.manifestos.ManifestoMapper;
import br.com.extrator.runners.common.ConstantesExtracao;
import br.com.extrator.runners.common.ExtractionHelper;
import br.com.extrator.runners.common.ExtractionLogger;
import br.com.extrator.runners.common.ExtractionResult;
import br.com.extrator.runners.dataexport.extractors.ContasAPagarExtractor;
import br.com.extrator.runners.dataexport.extractors.CotacaoExtractor;
import br.com.extrator.runners.dataexport.extractors.FaturaPorClienteExtractor;
import br.com.extrator.runners.dataexport.extractors.LocalizacaoCargaExtractor;
import br.com.extrator.runners.dataexport.extractors.ManifestoExtractor;
import br.com.extrator.util.configuracao.CarregadorConfig;
import br.com.extrator.util.console.LoggerConsole;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Serviço de orquestração para extrações DataExport.
 * Coordena a execução de todas as entidades DataExport com logs detalhados e resumos consolidados.
 */
public class DataExportExtractionService {
    
    private final ClienteApiDataExport apiClient;
    private final LogExtracaoRepository logRepository;
    private final ExtractionLogger logger;
    private final LoggerConsole log;
    
    public DataExportExtractionService() {
        this.apiClient = new ClienteApiDataExport();
        this.apiClient.setExecutionUuid(java.util.UUID.randomUUID().toString());
        this.logRepository = new LogExtracaoRepository();
        this.logger = new ExtractionLogger(DataExportExtractionService.class);
        this.log = LoggerConsole.getLogger(DataExportExtractionService.class);
    }
    
    /**
     * Executa extrações DataExport baseado nos parâmetros fornecidos.
     * 
     * @param dataInicio Data de início
     * @param dataFim Data de fim
     * @param entidade Nome da entidade específica (null = todas)
     * @throws RuntimeException Se houver falha crítica na extração
     */
    public void execute(final LocalDate dataInicio, final LocalDate dataFim, final String entidade) {
        final LocalDateTime inicioExecucao = LocalDateTime.now();
        final List<ExtractionResult> resultados = new ArrayList<>();
        
        log.info("");
        log.info("╔" + "═".repeat(78) + "╗");
        log.info("║" + " ".repeat(20) + "🚀 INICIANDO EXTRAÇÕES DATAEXPORT" + " ".repeat(25) + "║");
        log.info("╚" + "═".repeat(78) + "╝");
        log.info("📅 Período: {} a {}", dataInicio, dataFim != null ? dataFim : dataInicio);
        log.info("⏰ Início: {}", inicioExecucao);
        log.info("🎯 Entidade(s): {}", entidade == null || entidade.isBlank() ? "TODAS" : entidade);
        log.info("");
        
        CarregadorConfig.validarConexaoBancoDados();
        CarregadorConfig.validarTabelasEssenciais();
        ExtractionHelper.limparAvisosSeguranca();

        final String ent = entidade == null ? "" : entidade.trim().toLowerCase();
        final boolean executarManifestos = ent.isEmpty() || ConstantesEntidades.MANIFESTOS.equals(ent);
        final boolean executarCotacoes = ent.isEmpty() || ConstantesEntidades.COTACOES.equals(ent) 
            || Arrays.stream(ConstantesEntidades.ALIASES_COTACOES).anyMatch(alias -> alias.equals(ent));
        final boolean executarLocalizacao = ent.isEmpty() || ConstantesEntidades.LOCALIZACAO_CARGAS.equals(ent) 
            || Arrays.stream(ConstantesEntidades.ALIASES_LOCALIZACAO).anyMatch(alias -> alias.equals(ent));
        final boolean executarContasAPagar = ent.isEmpty() || ConstantesEntidades.CONTAS_A_PAGAR.equals(ent) 
            || Arrays.stream(ConstantesEntidades.ALIASES_CONTAS_PAGAR).anyMatch(alias -> alias.equals(ent))
            || "constas a pagar".equals(ent) || "constas-a-pagar".equals(ent);
        final boolean executarFaturasPorCliente = ent.isEmpty() || ConstantesEntidades.FATURAS_POR_CLIENTE.equals(ent) 
            || Arrays.stream(ConstantesEntidades.ALIASES_FATURAS_CLIENTE).anyMatch(alias -> alias.equals(ent));
        
        if (executarManifestos) {
            try {
                final ExtractionResult result = extractManifestos(dataInicio, dataFim);
                if (result != null) {
                    resultados.add(result);
                }
            } catch (final Exception e) {
                log.error("❌ Erro ao extrair Manifestos: {}", e.getMessage());
                resultados.add(ExtractionResult.erro("manifestos", LocalDateTime.now(), e).build());
            }
            ExtractionHelper.aplicarDelay();
        }
        
        if (executarCotacoes) {
            try {
                final ExtractionResult result = extractCotacoes(dataInicio, dataFim);
                if (result != null) {
                    resultados.add(result);
                }
            } catch (final Exception e) {
                log.error("❌ Erro ao extrair Cotações: {}. Indo para a próxima entidade; será reextraída na próxima execução.", e.getMessage());
                resultados.add(ExtractionResult.erro(ConstantesEntidades.COTACOES, LocalDateTime.now(), e).build());
            }
            ExtractionHelper.aplicarDelay();
        }
        
        if (executarLocalizacao) {
            try {
                final ExtractionResult result = extractLocalizacoes(dataInicio, dataFim);
                if (result != null) {
                    resultados.add(result);
                }
            } catch (final Exception e) {
                log.error("❌ Erro ao extrair Localização de Cargas: {}", e.getMessage());
                resultados.add(ExtractionResult.erro("localizacao_cargas", LocalDateTime.now(), e).build());
            }
            ExtractionHelper.aplicarDelay();
        }
        
        if (executarContasAPagar) {
            try {
                final ExtractionResult result = extractContasAPagar(dataInicio, dataFim);
                if (result != null) {
                    resultados.add(result);
                }
            } catch (final Exception e) {
                log.error("❌ Erro ao extrair Contas a Pagar: {}. Indo para a próxima entidade; será reextraída na próxima execução.", e.getMessage());
                resultados.add(ExtractionResult.erro(ConstantesEntidades.CONTAS_A_PAGAR, LocalDateTime.now(), e).build());
            }
            ExtractionHelper.aplicarDelay();
        }
        
        if (executarFaturasPorCliente) {
            try {
                final ExtractionResult result = extractFaturasPorCliente(dataInicio, dataFim);
                if (result != null) {
                    resultados.add(result);
                }
            } catch (final Exception e) {
                log.error("❌ Erro ao extrair Faturas por Cliente: {}", e.getMessage());
                resultados.add(ExtractionResult.erro("faturas_por_cliente", LocalDateTime.now(), e).build());
            }
            ExtractionHelper.aplicarDelay();
        }
        
        // Resumo consolidado final
        exibirResumoConsolidado(resultados, inicioExecucao);

        // Se alguma entidade falhou, propagar falha para o comando não marcar extração como sucesso
        final List<String> entidadesComFalha = resultados.stream()
            .filter(r -> !r.isSucesso())
            .map(ExtractionResult::getEntityName)
            .toList();
        if (!entidadesComFalha.isEmpty()) {
            throw new RuntimeException("Extração DataExport com falhas: " + String.join(", ", entidadesComFalha)
                + ". Verifique os logs. A extração NÃO deve ser considerada concluída com sucesso.");
        }
    }
    
    private ExtractionResult extractManifestos(final LocalDate dataInicio, final LocalDate dataFim) {
        final ManifestoExtractor extractor = new ManifestoExtractor(
            apiClient,
            new ManifestoRepository(),
            new ManifestoMapper(),
            log
        );
        
        final ExtractionResult result = logger.executeWithLogging(extractor, dataInicio, dataFim, extractor.getEmoji());
        logRepository.gravarLogExtracao(result.toLogEntity());
        
        if (!result.isSucesso()) {
            throw new RuntimeException(String.format(ConstantesExtracao.MSG_ERRO_EXTRACAO, "manifestos"), result.getErro());
        }
        
        return result;
    }
    
    private ExtractionResult extractCotacoes(final LocalDate dataInicio, final LocalDate dataFim) {
        final CotacaoExtractor extractor = new CotacaoExtractor(
            apiClient,
            new CotacaoRepository(),
            new CotacaoMapper(),
            log
        );
        
        final ExtractionResult result = logger.executeWithLogging(extractor, dataInicio, dataFim, extractor.getEmoji());
        logRepository.gravarLogExtracao(result.toLogEntity());
        
        if (!result.isSucesso()) {
            throw new RuntimeException(String.format(ConstantesExtracao.MSG_ERRO_EXTRACAO, "cotações"), result.getErro());
        }
        
        return result;
    }
    
    private ExtractionResult extractLocalizacoes(final LocalDate dataInicio, final LocalDate dataFim) {
        final LocalizacaoCargaExtractor extractor = new LocalizacaoCargaExtractor(
            apiClient,
            new LocalizacaoCargaRepository(),
            new LocalizacaoCargaMapper(),
            log
        );
        
        final ExtractionResult result = logger.executeWithLogging(extractor, dataInicio, dataFim, extractor.getEmoji());
        logRepository.gravarLogExtracao(result.toLogEntity());
        
        if (!result.isSucesso()) {
            throw new RuntimeException(String.format(ConstantesExtracao.MSG_ERRO_EXTRACAO, "localização de cargas"), result.getErro());
        }
        
        return result;
    }
    
    private ExtractionResult extractContasAPagar(final LocalDate dataInicio, final LocalDate dataFim) {
        final ContasAPagarExtractor extractor = new ContasAPagarExtractor(
            apiClient,
            new ContasAPagarRepository(),
            new ContasAPagarMapper(),
            log
        );
        
        final ExtractionResult result = logger.executeWithLogging(extractor, dataInicio, dataFim, extractor.getEmoji());
        logRepository.gravarLogExtracao(result.toLogEntity());
        
        if (!result.isSucesso()) {
            throw new RuntimeException(String.format(ConstantesExtracao.MSG_ERRO_EXTRACAO, "faturas a pagar"), result.getErro());
        }
        
        return result;
    }
    
    private ExtractionResult extractFaturasPorCliente(final LocalDate dataInicio, final LocalDate dataFim) {
        final FaturaPorClienteExtractor extractor = new FaturaPorClienteExtractor(
            apiClient,
            new FaturaPorClienteRepository(),
            new FaturaPorClienteMapper(),
            log
        );
        
        final ExtractionResult result = logger.executeWithLogging(extractor, dataInicio, dataFim, extractor.getEmoji());
        logRepository.gravarLogExtracao(result.toLogEntity());
        
        if (!result.isSucesso()) {
            throw new RuntimeException(String.format(ConstantesExtracao.MSG_ERRO_EXTRACAO, "faturas por cliente"), result.getErro());
        }
        
        return result;
    }
    
    /**
     * Exibe resumo consolidado de todas as extrações DataExport executadas.
     */
    private void exibirResumoConsolidado(final List<ExtractionResult> resultados, final LocalDateTime inicioExecucao) {
        final LocalDateTime fimExecucao = LocalDateTime.now();
        final Duration duracaoTotal = Duration.between(inicioExecucao, fimExecucao);
        
        log.info("");
        log.info("╔" + "═".repeat(78) + "╗");
        log.info("║" + " ".repeat(18) + "📊 RESUMO CONSOLIDADO DATAEXPORT" + " ".repeat(26) + "║");
        log.info("╚" + "═".repeat(78) + "╝");
        
        final int totalEntidades = resultados.size();
        int entidadesComSucesso = 0;
        int entidadesIncompletas = 0;
        int entidadesComErro = 0;
        int totalRegistrosExtraidos = 0;
        int totalRegistrosSalvos = 0;
        int totalUnicos = 0;
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
            totalUnicos += result.getTotalUnicos();
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
        log.info("   • Total únicos após deduplicação: {} registros", formatarNumero(totalUnicos));
        log.info("   • Total salvo no banco: {} registros", formatarNumero(totalRegistrosSalvos));
        if (totalRegistrosExtraidos != totalUnicos) {
            final int duplicadosRemovidos = totalRegistrosExtraidos - totalUnicos;
            final double percentualDuplicados = (duplicadosRemovidos * 100.0) / totalRegistrosExtraidos;
            log.info("   • Duplicados removidos: {} ({}%)", formatarNumero(duplicadosRemovidos), String.format("%.2f", percentualDuplicados));
        }
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
        if (entidadesComErro > 0) {
            log.info("║" + " ".repeat(14) + "⚠️ EXTRAÇÕES DATAEXPORT CONCLUÍDAS COM FALHAS (" + entidadesComErro + ")" + " ".repeat(10) + "║");
        } else {
            log.info("║" + " ".repeat(18) + "✅ EXTRAÇÕES DATAEXPORT CONCLUÍDAS" + " ".repeat(26) + "║");
        }
        log.info("╚" + "═".repeat(78) + "╝");
        log.info("");
    }

    private String formatarNumero(final int numero) {
        return String.format("%,d", numero);
    }
}
