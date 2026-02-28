/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/servicos/LoggingService.java
Classe  : LoggingService (class)
Pacote  : br.com.extrator.servicos
Modulo  : Servico de negocio
Papel   : Implementa responsabilidade de logging service.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Encapsula regras de processo.
2) Coordena validacoes e limites operacionais.
3) Expone API interna para comandos/runners.

Estrutura interna:
Metodos principais:
- LoggingService(): realiza operacao relacionada a "logging service".
- iniciarCaptura(...1 args): inicia recursos e prepara o processamento.
- pararCaptura(): encerra recursos e finaliza operacao com seguranca.
- pararCaptura(...1 args): encerra recursos e finaliza operacao com seguranca.
- centralizarLinha(...1 args): realiza operacao relacionada a "centralizar linha".
- salvarLogsEmArquivo(...2 args): persiste dados em armazenamento.
- formatarDuracao(...1 args): realiza operacao relacionada a "formatar duracao".
- formatarTamanho(...1 args): realiza operacao relacionada a "formatar tamanho".
- formatarNumero(...1 args): realiza operacao relacionada a "formatar numero".
- contarLinhas(...1 args): realiza operacao relacionada a "contar linhas".
- gerarNomeArquivoLog(): realiza operacao relacionada a "gerar nome arquivo log".
- criarDiretorioLogs(): instancia ou monta estrutura de dados.
- aplicarRetencaoLogs(): realiza operacao relacionada a "aplicar retencao logs".
- organizarLogsTxtNaPastaLogs(): realiza operacao relacionada a "organizar logs txt na pasta logs".
Atributos-chave:
- logger: logger da classe para diagnostico.
- DIRETORIO_LOGS: campo de estado para "diretorio logs".
- FORMATTER_ARQUIVO: campo de estado para "formatter arquivo".
- FORMATTER_LOG: campo de estado para "formatter log".
- MAX_LOG_FILES: campo de estado para "max log files".
- originalOut: campo de estado para "original out".
- originalErr: campo de estado para "original err".
- outputBuffer: campo de estado para "output buffer".
- errorBuffer: campo de estado para "error buffer".
- teeOut: campo de estado para "tee out".
- teeErr: campo de estado para "tee err".
- nomeOperacao: campo de estado para "nome operacao".
- inicioOperacao: campo de estado para "inicio operacao".
[DOC-FILE-END]============================================================== */

package br.com.extrator.servicos;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serviço responsável por capturar e salvar logs do terminal em arquivos
 */
public class LoggingService {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingService.class);
    private static final String DIRETORIO_LOGS = "logs";
    private static final DateTimeFormatter FORMATTER_ARQUIVO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter FORMATTER_LOG = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_LOG_FILES = 20;
    
    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream outputBuffer;
    private ByteArrayOutputStream errorBuffer;
    private PrintStream teeOut;
    private PrintStream teeErr;
    private String nomeOperacao;
    private LocalDateTime inicioOperacao;
    
    public LoggingService() {
        criarDiretorioLogs();
    }
    
    /**
     * Inicia a captura de logs para uma operação específica
     */
    public void iniciarCaptura(final String nomeOperacao) {
        this.nomeOperacao = nomeOperacao;
        this.inicioOperacao = LocalDateTime.now();
        
        // Salvar referências originais
        originalOut = System.out;
        originalErr = System.err;
        
        // Criar buffers para capturar a saída
        outputBuffer = new ByteArrayOutputStream();
        errorBuffer = new ByteArrayOutputStream();
        
        // Criar PrintStreams que escrevem tanto no console quanto no buffer
        teeOut = new PrintStream(new TeeOutputStream(originalOut, outputBuffer));
        teeErr = new PrintStream(new TeeOutputStream(originalErr, errorBuffer));
        
        // Redirecionar System.out e System.err
        System.setOut(teeOut);
        System.setErr(teeErr);
        
        // Cabeçalho melhorado
        System.out.println();
        System.out.println("╔" + "═".repeat(78) + "╗");
        System.out.println("║" + " ".repeat(25) + "🚀 INICIANDO OPERAÇÃO" + " ".repeat(30) + "║");
        System.out.println("╚" + "═".repeat(78) + "╝");
        System.out.println("📋 Operação: " + nomeOperacao);
        System.out.println("⏰ Data/Hora de Início: " + inicioOperacao.format(FORMATTER_LOG));
        System.out.println("📁 Diretório de Logs: " + Paths.get(DIRETORIO_LOGS).toAbsolutePath());
        System.out.println("─".repeat(80));
        System.out.println();
    }
    
    /**
     * Para a captura e salva os logs em arquivo
     */
    public void pararCaptura() {
        pararCaptura("SUCCESS");
    }

    /**
     * Para a captura e salva os logs em arquivo, exibindo status final da operação.
     *
     * @param statusExecucao Status final da execução (SUCCESS, PARTIAL, ERROR, etc.)
     */
    public void pararCaptura(final String statusExecucao) {
        if (originalOut == null || originalErr == null) {
            return; // Captura não foi iniciada
        }
        
        final LocalDateTime fimOperacao = LocalDateTime.now();
        final java.time.Duration duracao = java.time.Duration.between(inicioOperacao, fimOperacao);
        final String statusNormalizado = statusExecucao == null ? "" : statusExecucao.trim().toUpperCase();
        final String tituloFinal;
        if ("SUCCESS".equals(statusNormalizado)) {
            tituloFinal = "✅ OPERAÇÃO CONCLUÍDA";
        } else if ("PARTIAL".equals(statusNormalizado)) {
            tituloFinal = "⚠️ OPERAÇÃO CONCLUÍDA COM FALHAS";
        } else {
            tituloFinal = "❌ OPERAÇÃO FALHOU";
        }
        
        // Rodapé melhorado
        System.out.println();
        System.out.println("─".repeat(80));
        System.out.println("╔" + "═".repeat(78) + "╗");
        System.out.println(centralizarLinha(tituloFinal));
        System.out.println("╚" + "═".repeat(78) + "╝");
        System.out.println("📋 Operação: " + nomeOperacao);
        System.out.println("⏰ Data/Hora de Fim: " + fimOperacao.format(FORMATTER_LOG));
        System.out.println("⏱️ Duração Total: " + formatarDuracao(duracao));
        System.out.println("📌 Status final: " + (statusNormalizado.isEmpty() ? "DESCONHECIDO" : statusNormalizado));
        System.out.println();
        
        // Restaurar streams originais
        System.setOut(originalOut);
        System.setErr(originalErr);
        
        // Salvar logs em arquivo
        salvarLogsEmArquivo(fimOperacao, duracao);
        
        // Limpar recursos
        try {
            if (teeOut != null) teeOut.close();
            if (teeErr != null) teeErr.close();
            if (outputBuffer != null) outputBuffer.close();
            if (errorBuffer != null) errorBuffer.close();
        } catch (final IOException e) {
            System.err.println("Erro ao fechar recursos de logging: " + e.getMessage());
        }
        
        // Resetar variáveis
        originalOut = null;
        originalErr = null;
        outputBuffer = null;
        errorBuffer = null;
        teeOut = null;
        teeErr = null;
        nomeOperacao = null;
        inicioOperacao = null;
    }

    private String centralizarLinha(final String texto) {
        final String conteudo = texto == null ? "" : texto;
        final int larguraInterna = 78;
        final int tamanho = Math.min(conteudo.length(), larguraInterna);
        final String textoLimitado = conteudo.substring(0, tamanho);
        final int espacosAntes = Math.max(0, (larguraInterna - textoLimitado.length()) / 2);
        final int espacosDepois = Math.max(0, larguraInterna - textoLimitado.length() - espacosAntes);
        return "║" + " ".repeat(espacosAntes) + textoLimitado + " ".repeat(espacosDepois) + "║";
    }
    
    /**
     * Salva os logs capturados em um arquivo
     */
    private void salvarLogsEmArquivo(final LocalDateTime fimOperacao, final java.time.Duration duracao) {
        try {
            final String nomeArquivo = gerarNomeArquivoLog();
            final Path caminhoArquivo = Paths.get(DIRETORIO_LOGS, nomeArquivo);
            
            final StringBuilder conteudoLog = new StringBuilder();
            
            // Cabeçalho do log melhorado
            conteudoLog.append("╔").append("═".repeat(78)).append("╗\n");
            conteudoLog.append("║").append(" ".repeat(25)).append("📋 LOG DA OPERAÇÃO").append(" ".repeat(33)).append("║\n");
            conteudoLog.append("╚").append("═".repeat(78)).append("╝\n");
            conteudoLog.append("\n");
            conteudoLog.append("📋 Operação: ").append(nomeOperacao).append("\n");
            conteudoLog.append("⏰ Data/Hora de Início: ").append(inicioOperacao.format(FORMATTER_LOG)).append("\n");
            conteudoLog.append("⏰ Data/Hora de Fim: ").append(fimOperacao.format(FORMATTER_LOG)).append("\n");
            conteudoLog.append("⏱️ Duração Total: ").append(formatarDuracao(duracao)).append("\n");
            conteudoLog.append("📁 Arquivo: ").append(nomeArquivo).append("\n");
            conteudoLog.append("─".repeat(80)).append("\n");
            conteudoLog.append("\n");
            
            // Estatísticas do log
            final int tamanhoOutput = outputBuffer.size();
            final int tamanhoError = errorBuffer.size();
            final int totalLinhas = contarLinhas(outputBuffer) + contarLinhas(errorBuffer);
            
            conteudoLog.append("📊 ESTATÍSTICAS DO LOG:\n");
            conteudoLog.append("   • Tamanho da saída padrão: ").append(formatarTamanho(tamanhoOutput)).append("\n");
            conteudoLog.append("   • Tamanho da saída de erro: ").append(formatarTamanho(tamanhoError)).append("\n");
            conteudoLog.append("   • Total de linhas: ").append(formatarNumero(totalLinhas)).append("\n");
            conteudoLog.append("─".repeat(80)).append("\n");
            conteudoLog.append("\n");
            
            // Saída padrão (System.out)
            if (tamanhoOutput > 0) {
                conteudoLog.append("╔").append("═".repeat(78)).append("╗\n");
                conteudoLog.append("║").append(" ".repeat(28)).append("📤 SAÍDA PADRÃO (System.out)").append(" ".repeat(25)).append("║\n");
                conteudoLog.append("╚").append("═".repeat(78)).append("╝\n");
                conteudoLog.append("\n");
                conteudoLog.append(outputBuffer.toString("UTF-8"));
                conteudoLog.append("\n");
            }
            
            // Saída de erro (System.err)
            if (tamanhoError > 0) {
                conteudoLog.append("╔").append("═".repeat(78)).append("╗\n");
                conteudoLog.append("║").append(" ".repeat(30)).append("⚠️ SAÍDA DE ERRO (System.err)").append(" ".repeat(25)).append("║\n");
                conteudoLog.append("╚").append("═".repeat(78)).append("╝\n");
                conteudoLog.append("\n");
                conteudoLog.append(errorBuffer.toString("UTF-8"));
                conteudoLog.append("\n");
            }
            
            // Rodapé do log melhorado
            conteudoLog.append("\n");
            conteudoLog.append("╔").append("═".repeat(78)).append("╗\n");
            conteudoLog.append("║").append(" ".repeat(30)).append("✅ FIM DO LOG").append(" ".repeat(35)).append("║\n");
            conteudoLog.append("╚").append("═".repeat(78)).append("╝\n");
            
            // Escrever arquivo
            final byte[] bytes = conteudoLog.toString().getBytes("UTF-8");
            Files.write(caminhoArquivo, bytes);
            final long tamanhoArquivo = Files.size(caminhoArquivo);
            
            System.out.println("📄 Log salvo com sucesso!");
            System.out.println("   • Arquivo: " + caminhoArquivo.toAbsolutePath());
            System.out.println("   • Tamanho: " + formatarTamanho((int) tamanhoArquivo));
            System.out.println("   • Linhas: " + formatarNumero(totalLinhas));
            System.out.println();
            
            aplicarRetencaoLogs();
            
        } catch (final IOException e) {
            System.err.println("❌ Erro ao salvar log em arquivo: " + e.getMessage());
        }
    }
    
    /**
     * Formata duração em formato legível
     */
    private String formatarDuracao(final java.time.Duration duracao) {
        final long segundos = duracao.getSeconds();
        final long minutos = segundos / 60;
        final long horas = minutos / 60;
        
        if (horas > 0) {
            return String.format("%d h %d min %d s", horas, minutos % 60, segundos % 60);
        } else if (minutos > 0) {
            return String.format("%d min %d s", minutos, segundos % 60);
        } else {
            return String.format("%d s", segundos);
        }
    }
    
    /**
     * Formata tamanho em bytes para formato legível
     */
    private String formatarTamanho(final int bytes) {
        if (bytes < 1024) {
            return bytes + " bytes";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
    }
    
    /**
     * Formata número com separadores de milhares
     */
    private String formatarNumero(final int numero) {
        return String.format("%,d", numero);
    }
    
    /**
     * Conta o número de linhas em um ByteArrayOutputStream
     */
    private int contarLinhas(final ByteArrayOutputStream buffer) {
        if (buffer.size() == 0) {
            return 0;
        }
        try {
            final String conteudo = buffer.toString("UTF-8");
            return conteudo.split("\r?\n").length;
        } catch (final Exception e) {
            return 0;
        }
    }
    
    /**
     * Gera o nome do arquivo de log baseado na operação e timestamp
     */
    private String gerarNomeArquivoLog() {
        final String timestamp = inicioOperacao.format(FORMATTER_ARQUIVO);
        final String operacaoLimpa = nomeOperacao.toLowerCase()
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        
        return String.format("%s_%s.log", operacaoLimpa, timestamp);
    }
    
    /**
     * Cria o diretório de logs se não existir
     */
    private void criarDiretorioLogs() {
        try {
            Path diretorio = Paths.get(DIRETORIO_LOGS);
            if (!Files.exists(diretorio)) {
                Files.createDirectories(diretorio);
                System.out.println("📁 Diretório de logs criado: " + diretorio.toAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("❌ Erro ao criar diretório de logs: " + e.getMessage());
        }
    }
    
    /**
     * Aplica retenção máxima de arquivos .log na pasta de logs.
     * Mantém apenas os MAX_LOG_FILES mais recentes, removendo os demais (mais antigos).
     */
    private static void aplicarRetencaoLogs() {
        try {
            final File pastaLogs = new File(DIRETORIO_LOGS);
            if (!pastaLogs.exists()) {
                return;
            }
            final File[] arquivosLog = pastaLogs.listFiles((dir, name) -> name.toLowerCase().endsWith(".log"));
            if (arquivosLog == null) {
                return;
            }
            if (arquivosLog.length <= MAX_LOG_FILES) {
                return;
            }
            java.util.Arrays.sort(arquivosLog, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            final int excedente = arquivosLog.length - MAX_LOG_FILES;
            for (int i = 0; i < excedente; i++) {
                try {
                    Files.deleteIfExists(arquivosLog[i].toPath());
                    logger.debug("🧹 Log antigo removido por retenção: {}", arquivosLog[i].getName());
                } catch (final IOException | SecurityException e) {
                    logger.warn("Falha ao remover log antigo {}: {}", arquivosLog[i].getName(), e.getMessage());
                }
            }
        } catch (final SecurityException e) {
            logger.warn("Não foi possível aplicar retenção de logs: {}", e.getMessage());
        }
    }
    
    /**
     * Classe interna para implementar um OutputStream que escreve em dois destinos
     */
    private static class TeeOutputStream extends OutputStream {
        private final OutputStream out1;
        private final OutputStream out2;
        
        public TeeOutputStream(OutputStream out1, OutputStream out2) {
            this.out1 = out1;
            this.out2 = out2;
        }
        
        @Override
        public void write(int b) throws IOException {
            out1.write(b);
            out2.write(b);
        }
        
        @Override
        public void write(byte[] b) throws IOException {
            out1.write(b);
            out2.write(b);
        }
        
        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out1.write(b, off, len);
            out2.write(b, off, len);
        }
        
        @Override
        public void flush() throws IOException {
            out1.flush();
            out2.flush();
        }
        
        @Override
        public void close() throws IOException {
            try { out1.flush(); } catch (IOException ignored) {}
            try { out2.flush(); } catch (IOException ignored) {}
        }
    }
    
    /**
     * Organiza arquivos de log .txt na pasta logs.
     * Move arquivos .txt da raiz do projeto para a pasta logs, exceto README.txt.
     */
    public static void organizarLogsTxtNaPastaLogs() {
        try {
            final File pastaRaiz = new File(".");
            final File pastaLogs = new File(DIRETORIO_LOGS);
            
            if (!pastaLogs.exists()) {
                pastaLogs.mkdirs();
            }
            
            final File[] arquivosTxt = pastaRaiz.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".txt") && !"README.txt".equals(name));
            
            if (arquivosTxt != null) {
                for (final File arquivo : arquivosTxt) {
                    final File destino = new File(pastaLogs, arquivo.getName());
                    if (arquivo.renameTo(destino)) {
                        logger.debug("Log movido: {} -> logs/{}", arquivo.getName(), arquivo.getName());
                    }
                }
            }
        } catch (final SecurityException e) {
            logger.warn("Não foi possível organizar logs .txt: {}", e.getMessage());
        }
    }
}
