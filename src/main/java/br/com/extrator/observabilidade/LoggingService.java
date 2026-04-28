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

package br.com.extrator.observabilidade;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.suporte.observabilidade.ExecutionContext;
import br.com.extrator.suporte.tempo.RelogioSistema;

/**
 * Serviço responsável por capturar e salvar logs do terminal em arquivos
 */
public class LoggingService {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingService.class);
    private static final DateTimeFormatter FORMATTER_ARQUIVO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter FORMATTER_LOG = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SEPARADOR = "=".repeat(80);
    private static final String SEPARADOR_SECAO = "-".repeat(80);
    private static final int MAX_RUNTIME_SUMMARY_LINES = 80;
    
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
        this.inicioOperacao = RelogioSistema.agora();
        
        // Salvar referências originais
        originalOut = System.out;
        originalErr = System.err;
        
        // Criar buffers para capturar a saída
        outputBuffer = new ByteArrayOutputStream();
        errorBuffer = new ByteArrayOutputStream();
        
        // Criar PrintStreams que escrevem tanto no console quanto no buffer
        teeOut = new PrintStream(new TeeOutputStream(originalOut, outputBuffer), true, StandardCharsets.UTF_8);
        teeErr = new PrintStream(new TeeOutputStream(originalErr, errorBuffer), true, StandardCharsets.UTF_8);
        
        // Redirecionar System.out e System.err
        System.setOut(teeOut);
        System.setErr(teeErr);
        
        // Cabecalho simples e seguro para console Windows
        System.out.println();
        System.out.println(SEPARADOR);
        System.out.println("INICIANDO OPERACAO");
        System.out.println(SEPARADOR);
        System.out.println("Operacao: " + nomeOperacao);
        System.out.println("Data/Hora de Inicio: " + inicioOperacao.format(FORMATTER_LOG));
        System.out.println("Diretorio de Logs: " + LogStoragePaths.APP_OPERATIONS_DIR.toAbsolutePath());
        System.out.println("Log runtime detalhado: " + caminhoLogRuntime().toAbsolutePath());
        System.out.println(SEPARADOR_SECAO);
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
        
        final LocalDateTime fimOperacao = RelogioSistema.agora();
        final java.time.Duration duracao = java.time.Duration.between(inicioOperacao, fimOperacao);
        final String statusNormalizado = statusExecucao == null ? "" : statusExecucao.trim().toUpperCase();
        final String tituloFinal;
        if ("SUCCESS".equals(statusNormalizado)) {
            tituloFinal = "[OK] OPERACAO CONCLUIDA";
        } else if ("PARTIAL".equals(statusNormalizado)) {
            tituloFinal = "[AVISO] OPERACAO CONCLUIDA COM FALHAS";
        } else {
            tituloFinal = "[ERRO] OPERACAO FALHOU";
        }
        
        // Rodape em ASCII para evitar caracteres quebrados no console
        System.out.println();
        System.out.println(SEPARADOR_SECAO);
        System.out.println(SEPARADOR);
        System.out.println(tituloFinal);
        System.out.println(SEPARADOR);
        System.out.println("Operacao: " + nomeOperacao);
        System.out.println("Data/Hora de Fim: " + fimOperacao.format(FORMATTER_LOG));
        System.out.println("Duracao Total: " + formatarDuracao(duracao));
        System.out.println("Status final: " + (statusNormalizado.isEmpty() ? "DESCONHECIDO" : statusNormalizado));
        System.out.println();

        if (teeOut != null) {
            teeOut.flush();
        }
        if (teeErr != null) {
            teeErr.flush();
        }
        
        // Restaurar streams originais
        System.setOut(originalOut);
        System.setErr(originalErr);
        
        // Salvar logs em arquivo
        salvarLogsEmArquivo(fimOperacao, duracao, statusNormalizado);
        
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

    /**
     * Salva os logs capturados em um arquivo
     */
    private void salvarLogsEmArquivo(
        final LocalDateTime fimOperacao,
        final java.time.Duration duracao,
        final String statusNormalizado
    ) {
        try {
            final String nomeArquivo = gerarNomeArquivoLog();
            final Path caminhoArquivo = LogStoragePaths.APP_OPERATIONS_DIR.resolve(nomeArquivo);
            
            final StringBuilder conteudoLog = new StringBuilder();
            
            // Cabecalho do log em ASCII
            conteudoLog.append(SEPARADOR).append("\n");
            conteudoLog.append("LOG DA OPERACAO\n");
            conteudoLog.append(SEPARADOR).append("\n");
            conteudoLog.append("\n");
            conteudoLog.append("Operacao: ").append(nomeOperacao).append("\n");
            conteudoLog.append("Data/Hora de Inicio: ").append(inicioOperacao.format(FORMATTER_LOG)).append("\n");
            conteudoLog.append("Data/Hora de Fim: ").append(fimOperacao.format(FORMATTER_LOG)).append("\n");
            conteudoLog.append("Duracao Total: ").append(formatarDuracao(duracao)).append("\n");
            conteudoLog.append("Status final: ")
                .append(statusNormalizado == null || statusNormalizado.isBlank() ? "DESCONHECIDO" : statusNormalizado)
                .append("\n");
            conteudoLog.append("Arquivo: ").append(nomeArquivo).append("\n");
            conteudoLog.append("Log runtime detalhado: ").append(caminhoLogRuntime().toAbsolutePath()).append("\n");
            conteudoLog.append(SEPARADOR_SECAO).append("\n");
            conteudoLog.append("\n");
            
            // Estatísticas do log
            final int tamanhoOutput = outputBuffer.size();
            final int tamanhoError = errorBuffer.size();
            final int totalLinhas = contarLinhas(outputBuffer) + contarLinhas(errorBuffer);
            
            conteudoLog.append("ESTATISTICAS DO LOG:\n");
            conteudoLog.append("   - Tamanho da saida padrao: ").append(formatarTamanho(tamanhoOutput)).append("\n");
            conteudoLog.append("   - Tamanho da saida de erro: ").append(formatarTamanho(tamanhoError)).append("\n");
            conteudoLog.append("   - Total de linhas: ").append(formatarNumero(totalLinhas)).append("\n");
            conteudoLog.append(SEPARADOR_SECAO).append("\n");
            conteudoLog.append("\n");

            final String resumoRuntime = capturarResumoRuntimeAtual(MAX_RUNTIME_SUMMARY_LINES);
            if (!resumoRuntime.isBlank()) {
                conteudoLog.append(SEPARADOR).append("\n");
                conteudoLog.append("RESUMO DO LOG RUNTIME (ultimas linhas desta execucao)\n");
                conteudoLog.append(SEPARADOR).append("\n");
                conteudoLog.append("\n");
                conteudoLog.append(resumoRuntime).append("\n\n");
            }
            
            // Saída padrão (System.out)
            if (tamanhoOutput > 0) {
                conteudoLog.append(SEPARADOR).append("\n");
                conteudoLog.append("SAIDA PADRAO (System.out)\n");
                conteudoLog.append(SEPARADOR).append("\n");
                conteudoLog.append("\n");
                conteudoLog.append(outputBuffer.toString(StandardCharsets.UTF_8));
                conteudoLog.append("\n");
            }
            
            // Saída de erro (System.err)
            if (tamanhoError > 0) {
                conteudoLog.append(SEPARADOR).append("\n");
                conteudoLog.append("SAIDA DE ERRO (System.err)\n");
                conteudoLog.append(SEPARADOR).append("\n");
                conteudoLog.append("\n");
                conteudoLog.append(errorBuffer.toString(StandardCharsets.UTF_8));
                conteudoLog.append("\n");
            }
            
            // Rodape do log
            conteudoLog.append("\n");
            conteudoLog.append(SEPARADOR).append("\n");
            conteudoLog.append("FIM DO LOG\n");
            conteudoLog.append(SEPARADOR).append("\n");
            
            // Escrever arquivo
            final byte[] bytes = conteudoLog.toString().getBytes(StandardCharsets.UTF_8);
            Files.write(caminhoArquivo, bytes);
            final long tamanhoArquivo = Files.size(caminhoArquivo);
            
            System.out.println("[OK] Log salvo com sucesso!");
            System.out.println("   - Arquivo: " + caminhoArquivo.toAbsolutePath());
            System.out.println("   - Tamanho: " + formatarTamanho((int) tamanhoArquivo));
            System.out.println("   - Linhas: " + formatarNumero(totalLinhas));
            System.out.println();
            
            aplicarRetencaoLogs();
            
        } catch (final IOException e) {
            System.err.println("[ERRO] Erro ao salvar log em arquivo: " + e.getMessage());
        }
    }
    
    /**
     * Formata duração em formato legível
     */
    private String formatarDuracao(final java.time.Duration duracao) {
        final long duracaoMillis = Math.max(0L, duracao.toMillis());
        final long segundos = duracaoMillis == 0L ? 0L : (duracaoMillis + 999L) / 1000L;
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
            final String conteudo = buffer.toString(StandardCharsets.UTF_8);
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
        final String executionId = sanitizarComponenteArquivo(ExecutionContext.currentExecutionId());
        final String cycleId = sanitizarComponenteArquivo(ExecutionContext.currentCycleId());

        final StringBuilder nome = new StringBuilder(operacaoLimpa);
        if (!"na".equals(executionId)) {
            nome.append("_exec_").append(executionId);
        }
        if (!"na".equals(cycleId)) {
            nome.append("_cycle_").append(cycleId);
        }
        nome.append('_').append(timestamp).append(".log");
        return nome.toString();
    }

    private Path caminhoLogRuntime() {
        return LogStoragePaths.APP_RUNTIME_DIR.resolve("extrator-esl.log");
    }

    private String capturarResumoRuntimeAtual(final int maxLines) {
        final String executionId = ExecutionContext.currentExecutionId();
        if (executionId == null || executionId.isBlank() || "n/a".equalsIgnoreCase(executionId)) {
            return "";
        }

        final Path runtimeLog = caminhoLogRuntime();
        if (!Files.exists(runtimeLog)) {
            return "";
        }

        final String executionMarker = "[exec:" + executionId + "]";
        final Deque<String> linhas = new ArrayDeque<>();

        try (var stream = Files.lines(runtimeLog, StandardCharsets.UTF_8)) {
            stream
                .filter(line -> line.contains(executionMarker))
                .forEach(line -> {
                    linhas.addLast(line);
                    while (linhas.size() > maxLines) {
                        linhas.removeFirst();
                    }
                });
        } catch (final IOException e) {
            logger.warn("Nao foi possivel anexar resumo do runtime ao log operacional: {}", e.getMessage());
        }

        return String.join(System.lineSeparator(), linhas);
    }
    
    /**
     * Cria o diretório de logs se não existir
     */
    private void criarDiretorioLogs() {
        try {
            LogStoragePaths.ensureBaseDirectories();
        } catch (IOException e) {
            System.err.println("[ERRO] Erro ao criar diretorio de logs: " + e.getMessage());
        }
    }
    
    /**
     * Aplica retenção máxima de arquivos .log na pasta de logs.
     * Mantém apenas os MAX_LOG_FILES mais recentes, removendo os demais (mais antigos).
     */
    private static void aplicarRetencaoLogs() {
        LogRetentionPolicy.retainRecentFiles(
            LogStoragePaths.APP_OPERATIONS_DIR,
            LogStoragePaths.MAX_FILES_PER_BUCKET,
            path -> LogRetentionPolicy.hasExtension(path, ".log")
        );
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
            LogStoragePaths.ensureBaseDirectories();
            try (var arquivos = Files.list(LogStoragePaths.PROJECT_ROOT)) {
                for (final Path arquivo : arquivos.toList()) {
                    if (!Files.isRegularFile(arquivo)) {
                        continue;
                    }
                    final String nome = arquivo.getFileName().toString();
                    final String nomeNormalizado = nome.toLowerCase();
                    final boolean elegivel =
                        (nomeNormalizado.endsWith(".txt")
                            || nomeNormalizado.endsWith(".md")
                            || nomeNormalizado.endsWith(".out")
                            || nomeNormalizado.endsWith(".err"))
                            && !"readme.txt".equals(nomeNormalizado);
                    if (!elegivel) {
                        continue;
                    }
                    final Path destino = LogStoragePaths.REPORTS_DIR.resolve(nome);
                    if (!arquivo.toAbsolutePath().normalize().equals(destino.toAbsolutePath().normalize())) {
                        Files.move(
                            arquivo,
                            destino,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        );
                        logger.debug("Arquivo operacional movido: {} -> {}", nome, destino);
                    }
                }
            }
            LogRetentionPolicy.retainRecentFiles(
                LogStoragePaths.REPORTS_DIR,
                LogStoragePaths.MAX_FILES_PER_BUCKET,
                path -> LogRetentionPolicy.hasExtension(path, ".txt", ".md", ".out", ".err")
            );
        } catch (final IOException | SecurityException e) {
            logger.warn("Não foi possível organizar artefatos operacionais: {}", e.getMessage());
        }
    }

    private String sanitizarComponenteArquivo(final String value) {
        if (value == null || value.isBlank() || "n/a".equalsIgnoreCase(value)) {
            return "na";
        }
        return value
            .toLowerCase()
            .replaceAll("[^a-z0-9_-]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
    }
}
