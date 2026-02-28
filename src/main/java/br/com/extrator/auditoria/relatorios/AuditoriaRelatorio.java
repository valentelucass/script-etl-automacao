/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/auditoria/relatorios/AuditoriaRelatorio.java
Classe  : AuditoriaRelatorio (class)
Pacote  : br.com.extrator.auditoria.relatorios
Modulo  : Modulo de auditoria
Papel   : Implementa responsabilidade de auditoria relatorio.

Conecta com:
- StatusValidacao (auditoria.enums)
- ResultadoAuditoria (auditoria.modelos)
- ResultadoValidacaoEntidade (auditoria.modelos)
- LoggerConsole (util.console)
- FormatadorData (util.formatacao)

Fluxo geral:
1) Modela resultados e estado de auditoria.
2) Apoia consolidacao de evidencias operacionais.
3) Suporta emissao de relatorios de conformidade.

Estrutura interna:
Metodos principais:
- gerarRelatorio(...1 args): realiza operacao relacionada a "gerar relatorio".
- exibirResumoConsole(...1 args): realiza operacao relacionada a "exibir resumo console".
Atributos-chave:
- log: campo de estado para "log".
- FORMATTER_ARQUIVO: campo de estado para "formatter arquivo".
- FORMATTER_RELATORIO: campo de estado para "formatter relatorio".
[DOC-FILE-END]============================================================== */

package br.com.extrator.auditoria.relatorios;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import br.com.extrator.auditoria.enums.StatusValidacao;
import br.com.extrator.auditoria.modelos.ResultadoAuditoria;
import br.com.extrator.auditoria.modelos.ResultadoValidacaoEntidade;
import br.com.extrator.util.console.LoggerConsole;
import br.com.extrator.util.formatacao.FormatadorData;

/**
 * Classe responsável por gerar relatórios de auditoria em diferentes formatos.
 * Produz relatórios detalhados sobre a validação dos dados extraídos.
 * 
 * @author Lucas Andrade (@valentelucass) - lucasmac.dev@gmail.com
 */
public class AuditoriaRelatorio {
    private static final LoggerConsole log = LoggerConsole.getLogger(AuditoriaRelatorio.class);
    private static final DateTimeFormatter FORMATTER_ARQUIVO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter FORMATTER_RELATORIO = FormatadorData.BR_DATE_TIME;
    
    /**
     * Gera relatório baseado no resultado da auditoria
     * 
     * @param resultado Resultado completo da auditoria
     */
    public void gerarRelatorio(final ResultadoAuditoria resultado) {
        // Exibir resumo no console
        exibirResumoConsole(resultado.getResultadosValidacao());
        
        // Gerar relatório completo em arquivo
        final String diretorioSaida = "relatorios";
        try {
            gerarRelatorioCompleto(resultado, diretorioSaida);
        } catch (final IOException e) {
            log.error("Erro ao gerar relatório: {}", e.getMessage(), e);
        }
    }

    /**
     * Gera um relatório completo de auditoria em formato texto.
     * 
     * @param resultado Resultado completo da auditoria
     * @param diretorioSaida Diretório onde salvar o relatório
     * @return Path do arquivo gerado
     * @throws IOException Se houver erro ao criar o arquivo
     */
    public Path gerarRelatorioCompleto(final ResultadoAuditoria resultado, final String diretorioSaida) throws IOException {
        final String nomeArquivo = String.format("auditoria_completa_%s.txt", 
                                                Instant.now().atZone(ZoneId.systemDefault()).format(FORMATTER_ARQUIVO));
        final Path caminhoArquivo = Paths.get(diretorioSaida, nomeArquivo);
        
        // Criar diretório se não existir
        Files.createDirectories(caminhoArquivo.getParent());
        
        try (final FileWriter writer = new FileWriter(caminhoArquivo.toFile())) {
            escreverCabecalhoRelatorio(writer, resultado);
            escreverResumoGeral(writer, resultado);
            escreverTabelaResultados(writer, resultado);
            escreverDetalhesResultados(writer, resultado);
            escreverEstatisticas(writer, resultado);
            escreverRodapeRelatorio(writer);
        }
        
        log.info("Relatório completo de auditoria gerado: {}", caminhoArquivo);
        return caminhoArquivo;
    }
    
    /**
     * Gera um relatório resumido apenas com problemas encontrados.
     * 
     * @param resultados Lista de resultados de validação das entidades
     * @param diretorioSaida Diretório onde salvar o relatório
     * @return Path do arquivo gerado, ou null se não houver problemas
     * @throws IOException Se houver erro ao criar o arquivo
     */
    public Path gerarRelatorioProblemas(final List<ResultadoValidacaoEntidade> resultados, final String diretorioSaida) throws IOException {
        final List<ResultadoValidacaoEntidade> problemas = resultados.stream()
            .filter(r -> r.getStatus().temProblema())
            .toList();
        
        if (problemas.isEmpty()) {
            log.info("Nenhum problema encontrado na auditoria. Relatório de problemas não será gerado.");
            return null;
        }
        
        final String nomeArquivo = String.format("auditoria_problemas_%s.txt", 
                                                Instant.now().atZone(ZoneId.systemDefault()).format(FORMATTER_ARQUIVO));
        final Path caminhoArquivo = Paths.get(diretorioSaida, nomeArquivo);
        
        // Criar diretório se não existir
        Files.createDirectories(caminhoArquivo.getParent());
        
        try (final FileWriter writer = new FileWriter(caminhoArquivo.toFile())) {
            writer.write("=".repeat(80) + "\n");
            writer.write("RELATÓRIO DE PROBLEMAS - AUDITORIA ESL CLOUD\n");
            writer.write("=".repeat(80) + "\n");
            writer.write(String.format("Data/Hora: %s\n", Instant.now().atZone(ZoneId.systemDefault()).format(FORMATTER_RELATORIO)));
            writer.write(String.format("Total de problemas encontrados: %d\n\n", problemas.size()));
            
            for (final ResultadoValidacaoEntidade resultado : problemas) {
                escreverDetalheProblema(writer, resultado);
            }
            
            writer.write("\n" + "=".repeat(80) + "\n");
            writer.write("FIM DO RELATÓRIO DE PROBLEMAS\n");
            writer.write("=".repeat(80) + "\n");
        }
        
        log.info("Relatório de problemas gerado: {}", caminhoArquivo);
        return caminhoArquivo;
    }
    
    /**
     * Exibe um resumo da auditoria no console.
     * 
     * @param resultados Lista de resultados de validação
     */
    public void exibirResumoConsole(final List<String> resultados) {
        log.console("\n" + "=".repeat(60));
        log.info("📊 RESUMO DA AUDITORIA DE DADOS");
        log.console("=".repeat(60));
        log.info("⏰ Data/Hora: {}", Instant.now().atZone(ZoneId.systemDefault()).format(FORMATTER_RELATORIO));
        log.info("📋 Total de resultados: {}", resultados.size());
        
        final long erros = resultados.stream()
                .filter(r -> r.contains("ERRO") || r.contains("❌"))
                .count();
        final long alertas = resultados.stream()
                .filter(r -> r.contains("ALERTA") || r.contains("⚠️"))
                .count();
        final long sucessos = resultados.stream()
                .filter(r -> r.contains("OK") || r.contains("✅"))
                .count();
        
        log.info("✅ Sucessos: {} | ⚠️ Alertas: {} | ❌ Erros: {}", sucessos, alertas, erros);
        
        if (erros == 0 && alertas == 0) {
            log.info("✅ Status: AUDITORIA APROVADA");
        } else if (erros > 0) {
            log.error("🚨 Status: AUDITORIA COM ERROS CRÍTICOS");
        } else {
            log.warn("⚠️ Status: AUDITORIA COM ALERTAS");
        }
        
        log.console("=".repeat(60));
        
        if (!resultados.isEmpty()) {
            log.info("📝 Primeiros resultados:");
            resultados.stream()
                    .limit(5)
                    .forEach(r -> log.console("  • " + r));
            
            if (resultados.size() > 5) {
                log.console("  ... e mais {} resultados", resultados.size() - 5);
            }
        }
        
        log.console("");
    }
    
    /**
     * Escreve o cabeçalho do relatório.
     */
    private void escreverCabecalhoRelatorio(final FileWriter writer, final ResultadoAuditoria resultado) throws IOException {
        writer.write("=".repeat(80) + "\n");
        writer.write("RELATÓRIO COMPLETO DE AUDITORIA - ESL CLOUD DATA EXTRACTOR\n");
        writer.write("=".repeat(80) + "\n");
        writer.write(String.format("Data/Hora da Auditoria: %s\n", Instant.now().atZone(ZoneId.systemDefault()).format(FORMATTER_RELATORIO)));
        writer.write("Sistema: ESL Cloud Data Extraction\n");
        writer.write("Versão: 2.2\n");
        if (resultado.getDataInicio() != null && resultado.getDataFim() != null) {
            writer.write(String.format("Período Auditado: %s a %s\n",
                resultado.getDataInicio().atZone(ZoneId.systemDefault()).format(FORMATTER_RELATORIO),
                resultado.getDataFim().atZone(ZoneId.systemDefault()).format(FORMATTER_RELATORIO)));
        }
        writer.write("Desenvolvido por: @valentelucass (lucasmac.dev@gmail.com)\n");
        writer.write("\n");
    }
    
    /**
     * Escreve o resumo geral da auditoria.
     */
    private void escreverResumoGeral(final FileWriter writer, final ResultadoAuditoria resultado) throws IOException {
        final Map<String, ResultadoValidacaoEntidade> resultadosMap = resultado.getResultadosValidacaoMap();
        
        writer.write("RESUMO GERAL\n");
        writer.write("-".repeat(80) + "\n");
        writer.write(String.format("Data/Hora da Auditoria: %s\n", Instant.now().atZone(ZoneId.systemDefault()).format(FORMATTER_RELATORIO)));
        writer.write(String.format("Total de Entidades Auditadas: %d\n", resultadosMap.size()));
        
        long sucessos = resultadosMap.values().stream().filter(r -> r.getStatus().isSucesso()).count();
        long alertas = resultadosMap.values().stream().filter(r -> r.getStatus() == StatusValidacao.ALERTA).count();
        long erros = resultadosMap.values().stream().filter(r -> r.getStatus() == StatusValidacao.ERRO).count();
        
        writer.write(String.format("✅ Sucessos: %d\n", sucessos));
        writer.write(String.format("⚠️  Alertas: %d\n", alertas));
        writer.write(String.format("❌ Erros: %d\n", erros));
        writer.write(String.format("Status Geral: %s\n", resultado.getStatusGeral() != null ? resultado.getStatusGeral().getStatusFormatado() : "NÃO DEFINIDO"));
        if (resultado.getErro() != null && !resultado.getErro().isEmpty()) {
            writer.write(String.format("Erro Geral: %s\n", resultado.getErro()));
        }
        writer.write("\n");
    }
    
    /**
     * Escreve uma tabela formatada com os resultados.
     */
    private void escreverTabelaResultados(final FileWriter writer, final ResultadoAuditoria resultado) throws IOException {
        final Map<String, ResultadoValidacaoEntidade> resultadosMap = resultado.getResultadosValidacaoMap();
        
        writer.write("TABELA RESUMO DE RESULTADOS\n");
        writer.write("-".repeat(80) + "\n");
        writer.write(String.format("%-25s | %-10s | %-12s | %-12s | %-10s | %-10s\n",
            "ENTIDADE", "STATUS", "REGISTROS", "ESPERADO API", "DIFERENÇA", "COMPLETUDE"));
        writer.write("-".repeat(80) + "\n");
        
        for (final Map.Entry<String, ResultadoValidacaoEntidade> entry : resultadosMap.entrySet()) {
            final ResultadoValidacaoEntidade r = entry.getValue();
            final String status = r.getStatus().getIcone() + " " + r.getStatus().getDescricao();
            final String registros = String.format("%,d", r.getTotalRegistros());
            final String esperado = r.getRegistrosEsperadosApi() > 0 
                ? String.format("%,d", r.getRegistrosEsperadosApi())
                : "N/A";
            final String diferenca = r.getRegistrosEsperadosApi() > 0
                ? String.format("%,d", r.getDiferencaRegistros())
                : "N/A";
            final String completude = r.getRegistrosEsperadosApi() > 0
                ? String.format("%.1f%%", r.getPercentualCompletude())
                : "N/A";
            
            writer.write(String.format("%-25s | %-10s | %-12s | %-12s | %-10s | %-10s\n",
                entry.getKey(), status, registros, esperado, diferenca, completude));
        }
        
        writer.write("-".repeat(80) + "\n\n");
    }
    
    /**
     * Escreve os detalhes de cada entidade.
     */
    private void escreverDetalhesResultados(final FileWriter writer, final ResultadoAuditoria resultado) throws IOException {
        final Map<String, ResultadoValidacaoEntidade> resultadosMap = resultado.getResultadosValidacaoMap();
        
        writer.write("DETALHES DAS VALIDAÇÕES\n");
        writer.write("-".repeat(80) + "\n\n");
        
        int num = 1;
        for (final Map.Entry<String, ResultadoValidacaoEntidade> entry : resultadosMap.entrySet()) {
            final ResultadoValidacaoEntidade r = entry.getValue();
            
            writer.write(String.format("RESULTADO %d: %s\n", num++, entry.getKey().toUpperCase()));
            writer.write("-".repeat(60) + "\n");
            writer.write(String.format("Status: %s\n", r.getStatus().getDescricaoCompleta()));
            writer.write(String.format("Registros no Banco: %,d\n", r.getTotalRegistros()));
            
            if (r.getRegistrosUltimas24h() > 0) {
                writer.write(String.format("Registros (últimas 24h): %,d\n", r.getRegistrosUltimas24h()));
            }
            
            if (r.getRegistrosEsperadosApi() > 0) {
                writer.write(String.format("Registros Esperados (API): %,d\n", r.getRegistrosEsperadosApi()));
                writer.write(String.format("Diferença: %,d\n", r.getDiferencaRegistros()));
                writer.write(String.format("Percentual de Completude: %.2f%%\n", r.getPercentualCompletude()));
            }
            
            if (r.getUltimaExtracao() != null) {
                writer.write(String.format("Última Extração: %s\n",
                    r.getUltimaExtracao().atZone(ZoneId.systemDefault()).format(FORMATTER_RELATORIO)));
            }
            
            if (r.getErro() != null && !r.getErro().isEmpty()) {
                writer.write(String.format("Erro: %s\n", r.getErro()));
            }
            
            if (!r.getObservacoes().isEmpty()) {
                writer.write("Observações:\n");
                for (final String obs : r.getObservacoes()) {
                    writer.write(String.format("  • %s\n", obs));
                }
            }
            
            writer.write("\n");
        }
    }
    
    /**
     * Escreve estatísticas gerais.
     */
    private void escreverEstatisticas(final FileWriter writer, final ResultadoAuditoria resultado) throws IOException {
        final Map<String, ResultadoValidacaoEntidade> resultadosMap = resultado.getResultadosValidacaoMap();
        
        writer.write("ESTATÍSTICAS GERAIS\n");
        writer.write("-".repeat(80) + "\n");
        
        final long totalRegistros = resultadosMap.values().stream()
            .mapToLong(ResultadoValidacaoEntidade::getTotalRegistros)
            .sum();
        final long totalEsperado = resultadosMap.values().stream()
            .filter(r -> r.getRegistrosEsperadosApi() > 0)
            .mapToLong(ResultadoValidacaoEntidade::getRegistrosEsperadosApi)
            .sum();
        final long totalUltimas24h = resultadosMap.values().stream()
            .mapToLong(ResultadoValidacaoEntidade::getRegistrosUltimas24h)
            .sum();
        
        writer.write(String.format("Total de Registros no Banco: %,d\n", totalRegistros));
        if (totalEsperado > 0) {
            writer.write(String.format("Total Esperado pela API: %,d\n", totalEsperado));
            writer.write(String.format("Taxa Geral de Completude: %.2f%%\n", 
                (totalRegistros * 100.0) / totalEsperado));
        }
        if (totalUltimas24h > 0) {
            writer.write(String.format("Total de Registros (últimas 24h): %,d\n", totalUltimas24h));
        }
        writer.write("\n");
    }
    
    /**
     * Escreve o detalhe de um problema específico.
     */
    private void escreverDetalheProblema(final FileWriter writer, final ResultadoValidacaoEntidade resultado) throws IOException {
        writer.write(String.format("🔍 ENTIDADE: %s\n", resultado.getNomeEntidade().toUpperCase()));
        writer.write(String.format("   Status: %s\n", resultado.getStatus().getDescricaoCompleta()));
        
        if (resultado.getErro() != null) {
            writer.write(String.format("   Erro: %s\n", resultado.getErro()));
        }
        
        if (!resultado.getObservacoes().isEmpty()) {
            writer.write("   Observações:\n");
            for (final String obs : resultado.getObservacoes()) {
                writer.write(String.format("     - %s\n", obs));
            }
        }
        
        writer.write(String.format("   Registros: %,d (últimas 24h: %,d)\n", 
                                  resultado.getTotalRegistros(), resultado.getRegistrosUltimas24h()));
        
        if (resultado.getUltimaExtracao() != null) {
            writer.write(String.format("   Última extração: %s\n", 
                resultado.getUltimaExtracao().atZone(ZoneId.systemDefault()).format(FORMATTER_RELATORIO)));
        }
        
        writer.write("\n" + "-".repeat(60) + "\n\n");
    }
    
    /**
     * Escreve o rodapé do relatório.
     */
    private void escreverRodapeRelatorio(final FileWriter writer) throws IOException {
        writer.write("=".repeat(80) + "\n");
        writer.write("FIM DO RELATÓRIO DE AUDITORIA\n");
        writer.write(String.format("Gerado em: %s\n", Instant.now().atZone(ZoneId.systemDefault()).format(FORMATTER_RELATORIO)));
        writer.write("Sistema desenvolvido por @valentelucass (lucasmac.dev@gmail.com)\n");
        writer.write("=".repeat(80) + "\n");
    }
}
