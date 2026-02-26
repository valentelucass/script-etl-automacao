package br.com.extrator.runners.common;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import br.com.extrator.api.ResultadoExtracao;
// DataExportEntityExtractor ÃƒÂ© usado em instanceof e cast (linhas 54, 56, 79) - falso positivo do linter
import br.com.extrator.runners.common.DataExportEntityExtractor;
import br.com.extrator.util.configuracao.CarregadorConfig;
import br.com.extrator.util.console.LoggerConsole;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Classe utilitÃƒÂ¡ria para logging padronizado e detalhado de extraÃƒÂ§ÃƒÂµes.
 * Fornece logs ricos com mÃƒÂ©tricas, estatÃƒÂ­sticas e informaÃƒÂ§ÃƒÂµes de performance.
 */
@SuppressWarnings("unused") // DataExportEntityExtractor ÃƒÂ© usado em instanceof e cast (linhas 59, 60, 61, 85)
public class ExtractionLogger {
    private final LoggerConsole log;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    // ReferÃƒÂªncia estÃƒÂ¡tica ao tipo para forÃƒÂ§ar o linter a reconhecer o import
    private static final Class<?> DATA_EXPORT_EXTRACTOR_TYPE = DataExportEntityExtractor.class;
    
    public ExtractionLogger(final Class<?> clazz) {
        this.log = LoggerConsole.getLogger(clazz);
    }
    
    /**
     * Executa uma extraÃƒÂ§ÃƒÂ£o com logging padronizado e detalhado.
     * 
     * @param extractor Extractor a ser executado
     * @param dataInicio Data de inÃƒÂ­cio
     * @param dataFim Data de fim
     * @param emoji Emoji para identificaÃƒÂ§ÃƒÂ£o visual
     * @return Resultado da extraÃƒÂ§ÃƒÂ£o
     */
    public <T> ExtractionResult executeWithLogging(
            final EntityExtractor<T> extractor,
            final LocalDate dataInicio,
            final LocalDate dataFim,
            final String emoji) {
        
        final LocalDateTime inicio = LocalDateTime.now();
        final String entityName = extractor.getEntityName();
        final String displayEmoji = emoji != null ? emoji : extractor.getEmoji();
        
        // Log inicial detalhado
        log.info("{}", "=".repeat(80));
        log.info("{} {} INICIANDO EXTRAÃƒâ€¡ÃƒÆ’O: {}", displayEmoji, displayEmoji, entityName.toUpperCase());
        log.info("{}", "=".repeat(80));
        log.info("Ã°Å¸â€œâ€¦ PerÃƒÂ­odo: {} a {}", 
            formatarPeriodo(dataInicio, dataFim), 
            dataFim != null && !dataInicio.equals(dataFim) ? dataFim : dataInicio);
        log.info("Ã¢ÂÂ° InÃƒÂ­cio: {}", inicio.format(TIME_FORMATTER));
        log.info("{}", "-".repeat(80));

        int registrosExtraidosAteFalha = 0;
        int paginasProcessadasAteFalha = 0;
        
        try {
            final LocalDateTime inicioExtracao = LocalDateTime.now();
            final ResultadoExtracao<T> resultado = extractor.extract(dataInicio, dataFim);
            final LocalDateTime fimExtracao = LocalDateTime.now();
            final Duration duracaoExtracao = Duration.between(inicioExtracao, fimExtracao);
            
            final List<T> dtos = resultado.getDados();
            final int totalPaginas = resultado.getPaginasProcessadas();
            final boolean completo = resultado.isCompleto();
            final String statusMsg = completo ? "Ã¢Å“â€¦ COMPLETO" : "Ã¢Å¡Â Ã¯Â¸Â INCOMPLETO (" + resultado.getMotivoInterrupcao() + ")";
            registrosExtraidosAteFalha = resultado.getRegistrosExtraidos();
            paginasProcessadasAteFalha = totalPaginas;
            
            // Log de extraÃƒÂ§ÃƒÂ£o detalhado
            log.info("{}", "-".repeat(80));
            log.info("Ã°Å¸â€œÅ  RESULTADO DA EXTRAÃƒâ€¡ÃƒÆ’O:");
            log.info("   Ã¢â‚¬Â¢ Total extraÃƒÂ­do da API: {} registros", formatarNumero(dtos.size()));
            log.info("   Ã¢â‚¬Â¢ PÃƒÂ¡ginas processadas: {}", totalPaginas);
            log.info("   Ã¢â‚¬Â¢ Status: {}", statusMsg);
            final double segundosExtracao = duracaoExtracao.toMillis() / 1000.0;
            log.info("   Ã¢â‚¬Â¢ Tempo de extraÃƒÂ§ÃƒÂ£o (apenas busca na API): {} ms ({} s)",
                duracaoExtracao.toMillis(),
                String.format("%.2f", segundosExtracao));
            log.info("      Ã¢â€ Â³ enriquecimento e gravaÃƒÂ§ÃƒÂ£o entram no Tempo de salvamento abaixo");
            if (dtos.size() > 0 && duracaoExtracao.toMillis() > 0) {
                final double registrosPorSegundo = (dtos.size() * 1000.0) / duracaoExtracao.toMillis();
                log.info("   Ã¢â‚¬Â¢ Taxa de extraÃƒÂ§ÃƒÂ£o: {} registros/segundo", String.format("%.2f", registrosPorSegundo));
            }
            
            int registrosSalvos = 0;
            int totalUnicos = dtos.size(); // PadrÃƒÂ£o para GraphQL
            int registrosInvalidos = 0;
            final LocalDateTime inicioSalvamento = LocalDateTime.now();
            
            if (!dtos.isEmpty()) {
                try {
                    final EntityExtractor.SaveMetrics saveMetrics = extractor.saveWithMetrics(dtos);
                    registrosSalvos = saveMetrics.getRegistrosSalvos();
                    totalUnicos = saveMetrics.getTotalUnicos();
                    registrosInvalidos = saveMetrics.getRegistrosInvalidos();

                    final LocalDateTime fimSalvamento = LocalDateTime.now();
                    final Duration duracaoSalvamento = Duration.between(inicioSalvamento, fimSalvamento);
                    final boolean isDataExportExtractor = extractor instanceof DataExportEntityExtractor;

                    if (dtos.size() != totalUnicos) {
                        final int duplicadosRemovidos = dtos.size() - totalUnicos;
                        final double percentualDuplicados = (duplicadosRemovidos * 100.0) / dtos.size();
                        log.warn("   Duplicados removidos: {} ({}% do total)",
                            formatarNumero(duplicadosRemovidos), String.format("%.2f", percentualDuplicados));
                    }

                    log.info("{}", "-".repeat(80));
                    if (isDataExportExtractor) {
                        log.info("RESULTADO DO SALVAMENTO (DataExport):");
                        log.info("   Registros unicos apos deduplicacao: {}", formatarNumero(totalUnicos));
                        log.info("   Operacoes no banco (INSERTs + UPDATEs): {}", formatarNumero(registrosSalvos));
                    } else {
                        log.info("RESULTADO DO SALVAMENTO (GraphQL):");
                        log.info("   Registros salvos: {}", formatarNumero(registrosSalvos));
                    }
                    final double segundosSalvamento = duracaoSalvamento.toMillis() / 1000.0;
                    log.info("   Tempo de salvamento: {} ms ({} s)",
                        duracaoSalvamento.toMillis(),
                        String.format("%.2f", segundosSalvamento));
                    if (registrosSalvos > 0 && duracaoSalvamento.toMillis() > 0) {
                        final double registrosPorSegundo = (registrosSalvos * 1000.0) / duracaoSalvamento.toMillis();
                        log.info("   Taxa de salvamento: {} registros/segundo", String.format("%.2f", registrosPorSegundo));
                    }
                    if (registrosInvalidos > 0) {
                        log.warn("   Registros invalidos descartados: {}", formatarNumero(registrosInvalidos));
                    }
                } catch (final java.sql.SQLException e) {
                    log.error("ERRO CRITICO ao salvar {}: {}", entityName, e.getMessage());
                    throw new RuntimeException("Erro ao salvar " + entityName, e);
                }
            } else {
                log.info("Nenhum registro para salvar (lista vazia)");
            }
            
            totalUnicos = ajustarTotalUnicosAposSalvamento(entityName, totalUnicos, registrosSalvos);

            final LocalDateTime fim = LocalDateTime.now();
            final Duration duracaoTotal = Duration.between(inicio, fim);
            final int totalRecebido = dtos.size();
            final int deltaIgnorados = Math.max(0, totalUnicos - registrosSalvos);
            final boolean salvamentoConsistente = registrosSalvos == totalUnicos;
            final boolean invalidosDentroTolerancia = isInvalidosDentroTolerancia(registrosInvalidos, totalRecebido);
            final String statusFinal = determinarStatusFinal(resultado, salvamentoConsistente, invalidosDentroTolerancia);
            final String motivoStatus = determinarMotivoStatus(
                resultado,
                salvamentoConsistente,
                invalidosDentroTolerancia,
                registrosInvalidos
            );
            final String mensagem = buildMensagem(
                dataInicio,
                dataFim,
                totalRecebido,
                registrosSalvos,
                totalUnicos,
                deltaIgnorados,
                registrosInvalidos,
                duracaoTotal,
                statusFinal,
                motivoStatus
            );

            if (!salvamentoConsistente) {
                log.error("Ã¢ÂÅ’ DivergÃƒÂªncia de carga detectada em {}: ÃƒÂºnicos={} | salvos={}",
                    entityName, formatarNumero(totalUnicos), formatarNumero(registrosSalvos));
            }
            if (registrosInvalidos > 0 && !invalidosDentroTolerancia) {
                log.error("âŒ Registros invÃ¡lidos descartados em {}: {}", entityName, formatarNumero(registrosInvalidos));
            } else if (registrosInvalidos > 0) {
                final double percentualInvalidos = (registrosInvalidos * 100.0) / Math.max(1, totalRecebido);
                log.warn("âš ï¸ Registros invÃ¡lidos descartados em {} dentro da tolerÃ¢ncia operacional: {} ({}%)",
                    entityName,
                    formatarNumero(registrosInvalidos),
                    String.format("%.2f", percentualInvalidos));
            }
            log.info("   - ETL_DIAG status_code={} | reason_code={} | api_count={} | unique_count={} | db_upserts={} | invalid_count={} | pages={}",
                statusFinal,
                motivoStatus,
                formatarNumero(totalRecebido),
                formatarNumero(totalUnicos),
                formatarNumero(registrosSalvos),
                formatarNumero(registrosInvalidos),
                formatarNumero(totalPaginas));
            
            // Log de resumo final
            log.info("{}", "=".repeat(80));
            log.info("{} {} RESUMO FINAL: {}", displayEmoji, displayEmoji, entityName.toUpperCase());
            log.info("{}", "=".repeat(80));
            log.info("Ã°Å¸â€œË† EstatÃƒÂ­sticas:");
            log.info("   Ã¢â‚¬Â¢ API Ã¢â€ â€™ DB: {} Ã¢â€ â€™ {} registros", formatarNumero(totalRecebido), formatarNumero(registrosSalvos));
            if (totalRecebido != totalUnicos) {
                log.info("   Ã¢â‚¬Â¢ ÃƒÅ¡nicos apÃƒÂ³s deduplicaÃƒÂ§ÃƒÂ£o: {}", formatarNumero(totalUnicos));
            }
            if (deltaIgnorados > 0) {
                log.info("   Ã¢â‚¬Â¢ Ignorados/duplicados: {}", formatarNumero(deltaIgnorados));
            }
            log.info("   Ã¢â‚¬Â¢ PÃƒÂ¡ginas: {}", totalPaginas);
            log.info("   Ã¢â‚¬Â¢ Tempo total: {} ms ({} s)", 
                duracaoTotal.toMillis(), 
                String.format("%.2f", duracaoTotal.toMillis() / 1000.0));
            if (registrosInvalidos > 0) {
                log.info("   Ã¢â‚¬Â¢ Registros invÃƒÂ¡lidos descartados: {}", formatarNumero(registrosInvalidos));
            }
            log.info("   Ã¢â‚¬Â¢ Status: {}", formatarStatusHumano(statusFinal));
            log.info("Ã¢ÂÂ° Fim: {}", fim.format(TIME_FORMATTER));
            log.info("{}", "=".repeat(80));
            log.info(""); // Linha em branco para separaÃƒÂ§ÃƒÂ£o visual
            
            // Usar sucessoComUnicos se for DataExport (tem deduplicaÃƒÂ§ÃƒÂ£o)
            final boolean usarUnicos = (extractor instanceof DataExportEntityExtractor)
                || totalUnicos != resultado.getDados().size();
            if (usarUnicos) {
                return ExtractionResult.sucessoComUnicos(entityName, inicio, resultado, registrosSalvos, totalUnicos, mensagem)
                    .status(statusFinal)
                    .build();
            } else {
                return ExtractionResult.sucesso(entityName, inicio, resultado, registrosSalvos, mensagem)
                    .status(statusFinal)
                    .build();
            }
                
        } catch (final Exception e) {
            final LocalDateTime fim = LocalDateTime.now();
            final Duration duracaoTotal = Duration.between(inicio, fim);
            log.error("{}", "=".repeat(80));
            log.error("Ã¢ÂÅ’ ERRO NA EXTRAÃƒâ€¡ÃƒÆ’O: {}", entityName.toUpperCase());
            log.error("{}", "=".repeat(80));
            log.error("   Ã¢â‚¬Â¢ Erro: {}", e.getMessage());
            log.error("   Ã¢â‚¬Â¢ Tipo: {}", e.getClass().getSimpleName());
            log.error("   Ã¢â‚¬Â¢ Tempo atÃƒÂ© erro: {} ms ({} s)", 
                duracaoTotal.toMillis(), 
                String.format("%.2f", duracaoTotal.toMillis() / 1000.0));
            if (registrosExtraidosAteFalha > 0 || paginasProcessadasAteFalha > 0) {
                log.error("   Ã¢â‚¬Â¢ Progresso antes da falha: {} registros da API, {} pÃƒÂ¡ginas",
                    formatarNumero(registrosExtraidosAteFalha),
                    formatarNumero(paginasProcessadasAteFalha));
            }
            log.error("{}", "=".repeat(80));
            log.error(""); // Linha em branco para separaÃƒÂ§ÃƒÂ£o visual
            return ExtractionResult.erroComParcial(
                entityName,
                inicio,
                e,
                registrosExtraidosAteFalha,
                paginasProcessadasAteFalha
            ).build();
        }
    }
    
    private String formatarNumero(final int numero) {
        return String.format("%,d", numero);
    }
    
    private String formatarPeriodo(final LocalDate dataInicio, final LocalDate dataFim) {
        if (dataFim != null && !dataInicio.equals(dataFim)) {
            return dataInicio + " a " + dataFim;
        }
        return dataInicio.toString();
    }
    
    private String buildMensagem(final LocalDate dataInicio,
                                 final LocalDate dataFim,
                                 final int totalRecebido,
                                 final int registrosSalvos,
                                 final int totalUnicos,
                                 final int deltaIgnorados,
                                 final int registrosInvalidos,
                                 final Duration duracaoTotal,
                                 final String statusCode,
                                 final String reasonCode) {
        final StringBuilder sb = new StringBuilder();
        sb.append("API: ").append(formatarNumero(totalRecebido)).append(" recebidos");
        if (totalRecebido != totalUnicos) {
            sb.append(" (ÃƒÂºnicos: ").append(formatarNumero(totalUnicos)).append(")");
        }
        sb.append(" | DB: ").append(formatarNumero(registrosSalvos)).append(" processados");
        if (deltaIgnorados > 0) {
            sb.append(" | Delta: ").append(formatarNumero(deltaIgnorados)).append(" (duplicados/ignorados)");
        }
        if (registrosInvalidos > 0) {
            sb.append(" | InvÃƒÂ¡lidos descartados: ").append(formatarNumero(registrosInvalidos));
        }
        sb.append(" | Tempo: ").append(duracaoTotal.toMillis()).append("ms");
        
        if (dataFim != null && !dataInicio.equals(dataFim)) {
            sb.append(" | PerÃƒÂ­odo: ").append(dataInicio).append(" a ").append(dataFim);
        } else {
            sb.append(" | Data: ").append(dataInicio);
        }
        sb.append(" | status_code=").append(statusCode);
        sb.append(" | reason_code=").append(reasonCode);
        sb.append(" | api_count=").append(totalRecebido);
        sb.append(" | unique_count=").append(totalUnicos);
        sb.append(" | db_upserts=").append(registrosSalvos);
        sb.append(" | invalid_count=").append(registrosInvalidos);
        
        return sb.toString();
    }

    private String determinarStatusFinal(final ResultadoExtracao<?> resultado,
                                         final boolean salvamentoConsistente,
                                         final boolean invalidosDentroTolerancia) {
        if (!resultado.isCompleto()) {
            final String motivo = resultado.getMotivoInterrupcao();
            if (ResultadoExtracao.MotivoInterrupcao.ERRO_API.getCodigo().equals(motivo)
                || ResultadoExtracao.MotivoInterrupcao.CIRCUIT_BREAKER.getCodigo().equals(motivo)) {
                return ConstantesEntidades.STATUS_ERRO_API;
            }
            return ConstantesEntidades.STATUS_INCOMPLETO_LIMITE;
        }
        if (!salvamentoConsistente) {
            return ConstantesEntidades.STATUS_INCOMPLETO_DB;
        }
        if (!invalidosDentroTolerancia) {
            return ConstantesEntidades.STATUS_INCOMPLETO_DADOS;
        }
        return ConstantesEntidades.STATUS_COMPLETO;
    }

    private String determinarMotivoStatus(final ResultadoExtracao<?> resultado,
                                          final boolean salvamentoConsistente,
                                          final boolean invalidosDentroTolerancia,
                                          final int registrosInvalidos) {
        if (!resultado.isCompleto()) {
            final String motivo = resultado.getMotivoInterrupcao();
            return motivo != null && !motivo.isBlank()
                ? motivo
                : ResultadoExtracao.MotivoInterrupcao.LIMITE_PAGINAS.getCodigo();
        }
        if (!salvamentoConsistente) {
            return "DIVERGENCIA_SALVAMENTO";
        }
        if (!invalidosDentroTolerancia) {
            return "DADOS_INVALIDOS_ORIGEM";
        }
        if (registrosInvalidos > 0) {
            return "INVALIDOS_TOLERADOS";
        }
        return "OK";
    }

    private boolean isInvalidosDentroTolerancia(final int registrosInvalidos, final int totalRecebido) {
        if (registrosInvalidos <= 0) {
            return true;
        }

        final int limiteAbsoluto = CarregadorConfig.obterMaxInvalidosToleradosPorEntidade();
        final double limitePercentual = CarregadorConfig.obterPercentualMaxInvalidosToleradosPorEntidade();
        final double percentualInvalidos = (registrosInvalidos * 100.0) / Math.max(1, totalRecebido);

        return registrosInvalidos <= limiteAbsoluto && percentualInvalidos <= limitePercentual;
    }

    private int ajustarTotalUnicosAposSalvamento(final String entityName,
                                                  final int totalUnicosAtual,
                                                  final int registrosSalvos) {
        if (ConstantesEntidades.FATURAS_GRAPHQL.equals(entityName) && registrosSalvos > totalUnicosAtual) {
            log.info("   - {}: ajuste de total_unicos apos backfill referencial (api_unicos={} | total_processado={})",
                entityName,
                formatarNumero(totalUnicosAtual),
                formatarNumero(registrosSalvos));
            return registrosSalvos;
        }
        return totalUnicosAtual;
    }

    private String formatarStatusHumano(final String statusCode) {
        if (ConstantesEntidades.STATUS_COMPLETO.equals(statusCode)) {
            return "Ã¢Å“â€¦ COMPLETO";
        }
        return "Ã¢Å¡Â Ã¯Â¸Â " + statusCode;
    }
}



