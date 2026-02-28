/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/common/ExtractionLogger.java
Classe  : ExtractionLogger (class)
Pacote  : br.com.extrator.runners.common
Modulo  : Componente compartilhado de extracao
Papel   : Implementa responsabilidade de extraction logger.

Conecta com:
- ResultadoExtracao (api)
- DataExportEntityExtractor (runners.common)
- CarregadorConfig (util.configuracao)
- LoggerConsole (util.console)
- ConstantesEntidades (util.validacao)

Fluxo geral:
1) Disponibiliza contratos e utilitarios transversais.
2) Padroniza resultado, log e comportamento comum.
3) Reduz duplicacao entre GraphQL e DataExport.

Estrutura interna:
Metodos principais:
- ExtractionLogger(...1 args): realiza operacao relacionada a "extraction logger".
- executeWithLogging(...4 args): realiza operacao relacionada a "execute with logging".
- formatarNumero(...1 args): realiza operacao relacionada a "formatar numero".
- formatarPeriodo(...2 args): realiza operacao relacionada a "formatar periodo".
- buildMensagem(...10 args): realiza operacao relacionada a "build mensagem".
- determinarStatusFinal(...3 args): realiza operacao relacionada a "determinar status final".
- determinarMotivoStatus(...4 args): realiza operacao relacionada a "determinar motivo status".
- isInvalidosDentroTolerancia(...2 args): retorna estado booleano de controle.
- ajustarTotalUnicosAposSalvamento(...3 args): realiza operacao relacionada a "ajustar total unicos apos salvamento".
- formatarStatusHumano(...1 args): realiza operacao relacionada a "formatar status humano".
Atributos-chave:
- log: campo de estado para "log".
- TIME_FORMATTER: campo de estado para "time formatter".
- DATA_EXPORT_EXTRACTOR_TYPE: campo de estado para "data export extractor type".
[DOC-FILE-END]============================================================== */

package br.com.extrator.runners.common;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import br.com.extrator.api.ResultadoExtracao;
// DataExportEntityExtractor é usado em instanceof e cast (linhas 54, 56, 79) - falso positivo do linter
import br.com.extrator.runners.common.DataExportEntityExtractor;
import br.com.extrator.util.configuracao.CarregadorConfig;
import br.com.extrator.util.console.LoggerConsole;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Classe utilitária para logging padronizado e detalhado de extrações.
 * Fornece logs ricos com métricas, estatísticas e informações de performance.
 */
@SuppressWarnings("unused") // DataExportEntityExtractor é usado em instanceof e cast (linhas 59, 60, 61, 85)
public class ExtractionLogger {
    private final LoggerConsole log;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    
    // Referência estática ao tipo para forçar o linter a reconhecer o import
    private static final Class<?> DATA_EXPORT_EXTRACTOR_TYPE = DataExportEntityExtractor.class;
    
    public ExtractionLogger(final Class<?> clazz) {
        this.log = LoggerConsole.getLogger(clazz);
    }
    
    /**
     * Executa uma extração com logging padronizado e detalhado.
     * 
     * @param extractor Extractor a ser executado
     * @param dataInicio Data de início
     * @param dataFim Data de fim
     * @param emoji Emoji para identificação visual
     * @return Resultado da extração
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
        log.info("{} {} INICIANDO EXTRAÇÃO: {}", displayEmoji, displayEmoji, entityName.toUpperCase());
        log.info("{}", "=".repeat(80));
        log.info("📅 Período: {} a {}", 
            formatarPeriodo(dataInicio, dataFim), 
            dataFim != null && !dataInicio.equals(dataFim) ? dataFim : dataInicio);
        log.info("⏰ Início: {}", inicio.format(TIME_FORMATTER));
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
            final String statusMsg = completo ? "✅ COMPLETO" : "⚠️ INCOMPLETO (" + resultado.getMotivoInterrupcao() + ")";
            registrosExtraidosAteFalha = resultado.getRegistrosExtraidos();
            paginasProcessadasAteFalha = totalPaginas;
            
            // Log de extração detalhado
            log.info("{}", "-".repeat(80));
            log.info("📊 RESULTADO DA EXTRAÇÃO:");
            log.info("   • Total extraído da API: {} registros", formatarNumero(dtos.size()));
            log.info("   • Páginas processadas: {}", totalPaginas);
            log.info("   • Status: {}", statusMsg);
            final double segundosExtracao = duracaoExtracao.toMillis() / 1000.0;
            log.info("   • Tempo de extração (apenas busca na API): {} ms ({} s)",
                duracaoExtracao.toMillis(),
                String.format("%.2f", segundosExtracao));
            log.info("      ↳ enriquecimento e gravação entram no Tempo de salvamento abaixo");
            if (dtos.size() > 0 && duracaoExtracao.toMillis() > 0) {
                final double registrosPorSegundo = (dtos.size() * 1000.0) / duracaoExtracao.toMillis();
                log.info("   • Taxa de extração: {} registros/segundo", String.format("%.2f", registrosPorSegundo));
            }
            
            int registrosSalvos = 0;
            int totalUnicos = dtos.size(); // Padrão para GraphQL
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
                log.error("❌ Divergência de carga detectada em {}: únicos={} | salvos={}",
                    entityName, formatarNumero(totalUnicos), formatarNumero(registrosSalvos));
            }
            if (registrosInvalidos > 0 && !invalidosDentroTolerancia) {
                log.error("? Registros inválidos descartados em {}: {}", entityName, formatarNumero(registrosInvalidos));
            } else if (registrosInvalidos > 0) {
                final double percentualInvalidos = (registrosInvalidos * 100.0) / Math.max(1, totalRecebido);
                log.warn("?? Registros inválidos descartados em {} dentro da tolerância operacional: {} ({}%)",
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
            log.info("📈 Estatísticas:");
            log.info("   • API → DB: {} → {} registros", formatarNumero(totalRecebido), formatarNumero(registrosSalvos));
            if (totalRecebido != totalUnicos) {
                log.info("   • Únicos após deduplicação: {}", formatarNumero(totalUnicos));
            }
            if (deltaIgnorados > 0) {
                log.info("   • Ignorados/duplicados: {}", formatarNumero(deltaIgnorados));
            }
            log.info("   • Páginas: {}", totalPaginas);
            log.info("   • Tempo total: {} ms ({} s)", 
                duracaoTotal.toMillis(), 
                String.format("%.2f", duracaoTotal.toMillis() / 1000.0));
            if (registrosInvalidos > 0) {
                log.info("   • Registros inválidos descartados: {}", formatarNumero(registrosInvalidos));
            }
            log.info("   • Status: {}", formatarStatusHumano(statusFinal));
            log.info("⏰ Fim: {}", fim.format(TIME_FORMATTER));
            log.info("{}", "=".repeat(80));
            log.info(""); // Linha em branco para separação visual
            
            // Usar sucessoComUnicos se for DataExport (tem deduplicação)
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
            log.error("❌ ERRO NA EXTRAÇÃO: {}", entityName.toUpperCase());
            log.error("{}", "=".repeat(80));
            log.error("   • Erro: {}", e.getMessage());
            log.error("   • Tipo: {}", e.getClass().getSimpleName());
            log.error("   • Tempo até erro: {} ms ({} s)", 
                duracaoTotal.toMillis(), 
                String.format("%.2f", duracaoTotal.toMillis() / 1000.0));
            if (registrosExtraidosAteFalha > 0 || paginasProcessadasAteFalha > 0) {
                log.error("   • Progresso antes da falha: {} registros da API, {} páginas",
                    formatarNumero(registrosExtraidosAteFalha),
                    formatarNumero(paginasProcessadasAteFalha));
            }
            log.error("{}", "=".repeat(80));
            log.error(""); // Linha em branco para separação visual
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
            sb.append(" (únicos: ").append(formatarNumero(totalUnicos)).append(")");
        }
        sb.append(" | DB: ").append(formatarNumero(registrosSalvos)).append(" processados");
        if (deltaIgnorados > 0) {
            sb.append(" | Delta: ").append(formatarNumero(deltaIgnorados)).append(" (duplicados/ignorados)");
        }
        if (registrosInvalidos > 0) {
            sb.append(" | Inválidos descartados: ").append(formatarNumero(registrosInvalidos));
        }
        sb.append(" | Tempo: ").append(duracaoTotal.toMillis()).append("ms");
        
        if (dataFim != null && !dataInicio.equals(dataFim)) {
            sb.append(" | Período: ").append(dataInicio).append(" a ").append(dataFim);
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
            return "✅ COMPLETO";
        }
        return "⚠️ " + statusCode;
    }
}



