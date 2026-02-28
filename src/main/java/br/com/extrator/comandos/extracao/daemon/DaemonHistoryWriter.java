/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/extracao/daemon/DaemonHistoryWriter.java
Classe  : DaemonHistoryWriter (class)
Pacote  : br.com.extrator.comandos.extracao.daemon
Modulo  : Comando CLI (daemon)
Papel   : Implementa responsabilidade de daemon history writer.

Conecta com:
- ExecutionAuditor (auditoria.execucao)
- ReconciliationSummary (comandos.extracao.reconciliacao.LoopReconciliationService)
- ExecutionHistoryRepository (db.repository)

Fluxo geral:
1) Recebe parametros de execucao em modo daemon.
2) Coordena ciclo, persistencia de estado e logs de runtime.
3) Controla retomada e historico de ciclos.

Estrutura interna:
Metodos principais:
- criarPadrao(): instancia ou monta estrutura de dados.
- DaemonHistoryWriter(...5 args): realiza operacao relacionada a "daemon history writer".
- buildCycleSummary(...5 args): realiza operacao relacionada a "build cycle summary".
- appendFinalSummary(...2 args): realiza operacao relacionada a "append final summary".
- registerCycleHistory(...1 args): realiza operacao relacionada a "register cycle history".
- registerCompatibilityHistory(...1 args): realiza operacao relacionada a "register compatibility history".
- registerReconciliationHistory(...5 args): realiza operacao relacionada a "register reconciliation history".
- summarizeMessage(...1 args): realiza operacao relacionada a "summarize message".
- organizarCiclosLegadosPorData(): realiza operacao relacionada a "organizar ciclos legados por data".
- moverCicloLegadoParaPastaData(...1 args): realiza operacao relacionada a "mover ciclo legado para pasta data".
- calculateTotalRecords(...2 args): realiza operacao relacionada a "calculate total records".
- determineCycleStatus(...4 args): realiza operacao relacionada a "determine cycle status".
- buildDetailSummary(...6 args): realiza operacao relacionada a "build detail summary".
- determineReconciliationStatus(...1 args): realiza operacao relacionada a "determine reconciliation status".
Atributos-chave:
- CYCLE_LOG_FORMAT: campo de estado para "cycle log format".
- HISTORY_MONTH_FORMAT: campo de estado para "history month format".
- HISTORY_LINE_FORMAT: campo de estado para "history line format".
- daemonDir: campo de estado para "daemon dir".
- cyclesDir: campo de estado para "cycles dir".
- daemonHistoryDir: campo de estado para "daemon history dir".
- reconciliacaoHistoryDirDefault: campo de estado para "reconciliacao history dir default".
- reconciliacaoHistoryDirOverrideKey: campo de estado para "reconciliacao history dir override key".
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.extracao.daemon;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import br.com.extrator.auditoria.execucao.ExecutionAuditor;
import br.com.extrator.comandos.extracao.reconciliacao.LoopReconciliationService.ReconciliationSummary;
import br.com.extrator.db.repository.ExecutionHistoryRepository;

/**
 * Consolida escrita de logs e historicos do loop daemon.
 */
public final class DaemonHistoryWriter {
    private static final DateTimeFormatter CYCLE_LOG_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter HISTORY_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy_MM");
    private static final DateTimeFormatter HISTORY_LINE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern CYCLE_FILENAME_PATTERN =
        Pattern.compile("^extracao_daemon_(\\d{4}-\\d{2}-\\d{2})_\\d{2}-\\d{2}-\\d{2}\\.log$");

    private static final String DAEMON_HISTORY_HEADER =
        "DATA_HORA_FIM;INICIO;FIM;DURACAO_S;STATUS;TOTAL_RECORDS;WARNS;ERRORS;DETALHE;LOG_CICLO";
    private static final String RECONCILIACAO_HISTORY_HEADER =
        "DATA_HORA;INICIO_CICLO;FIM_EXTRACAO;CICLO_SUCESSO;STATUS_RECONCILIACAO;ATIVA;EXECUTADAS;FALHAS;PENDENTES;"
            + "AGENDOU_DIARIA;PENDENCIA_POR_FALHA;DETALHE;LOG_CICLO";

    private final Path daemonDir;
    private final Path cyclesDir;
    private final Path daemonHistoryDir;
    private final Path reconciliacaoHistoryDirDefault;
    private final String reconciliacaoHistoryDirOverrideKey;

    public static DaemonHistoryWriter criarPadrao() {
        return new DaemonHistoryWriter(
            DaemonPaths.DAEMON_DIR,
            DaemonPaths.CYCLES_DIR,
            DaemonPaths.DAEMON_HISTORY_DIR,
            DaemonPaths.RECONCILIACAO_HISTORY_DIR_DEFAULT,
            DaemonPaths.RECONCILIACAO_HISTORY_DIR_OVERRIDE_KEY
        );
    }

    public DaemonHistoryWriter(final Path daemonDir,
                               final Path cyclesDir,
                               final Path daemonHistoryDir,
                               final Path reconciliacaoHistoryDirDefault,
                               final String reconciliacaoHistoryDirOverrideKey) {
        this.daemonDir = daemonDir;
        this.cyclesDir = cyclesDir;
        this.daemonHistoryDir = daemonHistoryDir;
        this.reconciliacaoHistoryDirDefault = reconciliacaoHistoryDirDefault;
        this.reconciliacaoHistoryDirOverrideKey = reconciliacaoHistoryDirOverrideKey;
    }

    public void ensureDirectories() throws IOException {
        if (!Files.exists(daemonDir)) {
            Files.createDirectories(daemonDir);
        }
        if (!Files.exists(cyclesDir)) {
            Files.createDirectories(cyclesDir);
        }
        if (!Files.exists(daemonHistoryDir)) {
            Files.createDirectories(daemonHistoryDir);
        }
        final Path reconciliacaoHistoryDir = resolveReconciliationHistoryDir();
        if (!Files.exists(reconciliacaoHistoryDir)) {
            Files.createDirectories(reconciliacaoHistoryDir);
        }
        organizarCiclosLegadosPorData();
    }

    public Path createCycleLogFile(final LocalDateTime inicio) throws IOException {
        ensureDirectories();
        final String timestamp = inicio.format(CYCLE_LOG_FORMAT);
        final LocalDate dataCiclo = inicio.toLocalDate();
        final Path pastaDia = cyclesDir.resolve(dataCiclo.toString());
        if (!Files.exists(pastaDia)) {
            Files.createDirectories(pastaDia);
        }
        final Path arquivo = pastaDia.resolve("extracao_daemon_" + timestamp + ".log");
        if (!Files.exists(arquivo)) {
            Files.createFile(arquivo);
        }
        return arquivo;
    }

    public CycleSummary buildCycleSummary(final LocalDateTime inicio,
                                          final LocalDateTime fim,
                                          final Path cicloLog,
                                          final boolean sucesso,
                                          final String detalheBase) {
        int warns = 0;
        int errors = 0;
        boolean alertaIntegridade = false;
        boolean statusIncompleto = false;
        boolean resumoFinalSucesso = false;

        try {
            final List<String> linhas = Files.readAllLines(cicloLog, StandardCharsets.UTF_8);
            for (final String linha : linhas) {
                if (linha.contains(" WARN ")) {
                    warns++;
                }
                if (linha.contains(" ERROR ")) {
                    errors++;
                }
                if (linha.contains("ALERTA_LOOP")
                    || linha.contains("EXTRACAO CONCLUIDA COM ALERTA DE INTEGRIDADE")
                    || linha.contains("RESUMO DA EXTRACAO (com alerta de integridade no loop)")) {
                    alertaIntegridade = true;
                }
                if (linha.contains("status_code=INCOMPLETO")) {
                    statusIncompleto = true;
                }
                if (linha.contains("Todas as APIs foram processadas com sucesso.")) {
                    resumoFinalSucesso = true;
                }
            }
        } catch (final IOException ignored) {
            // Em caso de falha de leitura, o resumo segue com valores default.
        }

        final long duracaoSegundos = Math.max(0L, Duration.between(inicio, fim).getSeconds());
        final int totalRegistros = calculateTotalRecords(inicio, fim);
        final String statusCiclo = determineCycleStatus(sucesso, alertaIntegridade, statusIncompleto, errors);
        final String detalheResumo = buildDetailSummary(
            detalheBase,
            alertaIntegridade,
            statusIncompleto,
            resumoFinalSucesso,
            warns,
            errors
        );

        return new CycleSummary(
            inicio,
            fim,
            duracaoSegundos,
            statusCiclo,
            totalRegistros,
            warns,
            errors,
            detalheResumo,
            cicloLog.toAbsolutePath().toString()
        );
    }

    public void appendFinalSummary(final Path cicloLog, final CycleSummary resumo) {
        final List<String> linhas = new ArrayList<>();
        linhas.add("");
        linhas.add("============================================================");
        linhas.add("RESUMO FINAL DO CICLO (DAEMON)");
        linhas.add("============================================================");
        linhas.add("Inicio: " + HISTORY_LINE_FORMAT.format(resumo.inicio));
        linhas.add("Fim: " + HISTORY_LINE_FORMAT.format(resumo.fim));
        linhas.add("Duracao (segundos): " + resumo.duracaoSegundos);
        linhas.add("Status do ciclo: " + resumo.status);
        linhas.add("Total de registros (log_extracoes): " + resumo.totalRegistros);
        linhas.add("Warnings: " + resumo.warns + " | Errors: " + resumo.errors);
        linhas.add("Detalhe: " + resumo.detalhe);
        linhas.add("Log do ciclo: " + resumo.logCiclo);
        linhas.add("============================================================");
        try {
            Files.write(cicloLog, linhas, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (final IOException ignored) {
            // Falha ao anexar resumo nao deve interromper o loop.
        }
    }

    public void registerCycleHistory(final CycleSummary resumo) {
        final String nomeArquivo = "execucao_daemon_" + resumo.fim.format(HISTORY_MONTH_FORMAT) + ".csv";
        final Path arquivoCsv = daemonHistoryDir.resolve(nomeArquivo);
        final boolean escreverHeader;
        try {
            escreverHeader = !Files.exists(arquivoCsv) || Files.size(arquivoCsv) == 0L;
        } catch (final IOException e) {
            return;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(
            arquivoCsv,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )) {
            if (escreverHeader) {
                writer.write(DAEMON_HISTORY_HEADER);
                writer.newLine();
            }

            final String linha = String.join(";",
                HISTORY_LINE_FORMAT.format(resumo.fim),
                HISTORY_LINE_FORMAT.format(resumo.inicio),
                HISTORY_LINE_FORMAT.format(resumo.fim),
                String.valueOf(resumo.duracaoSegundos),
                sanitizeCsv(resumo.status),
                String.valueOf(resumo.totalRegistros),
                String.valueOf(resumo.warns),
                String.valueOf(resumo.errors),
                sanitizeCsv(resumo.detalhe),
                sanitizeCsv(resumo.logCiclo)
            );
            writer.write(linha);
            writer.newLine();
        } catch (final IOException ignored) {
            // Falha de historico nao interrompe o daemon.
        }
    }

    public void registerCompatibilityHistory(final CycleSummary resumo) {
        ExecutionAuditor.registrarCsv(
            resumo.fim,
            resumo.status,
            resumo.duracaoSegundos,
            resumo.totalRegistros,
            "loop_daemon_ciclo",
            resumo.detalhe + " | log_ciclo=" + resumo.logCiclo
        );
    }

    public void registerReconciliationHistory(final LocalDateTime inicioCiclo,
                                              final LocalDateTime fimExtracao,
                                              final boolean cicloSucesso,
                                              final ReconciliationSummary resumo,
                                              final Path cicloLog) {
        final String nomeArquivo = "reconciliacao_daemon_" + fimExtracao.format(HISTORY_MONTH_FORMAT) + ".csv";
        final Path arquivoCsv = resolveReconciliationHistoryDir().resolve(nomeArquivo);
        final boolean escreverHeader;
        try {
            escreverHeader = !Files.exists(arquivoCsv) || Files.size(arquivoCsv) == 0L;
        } catch (final IOException e) {
            return;
        }

        final String statusReconciliacao = determineReconciliationStatus(resumo);
        final boolean ativa = resumo != null && resumo.isAtivo();
        final int executadas = resumo == null ? 0 : resumo.getReconciliacoesExecutadas();
        final int falhas = resumo == null ? 0 : resumo.getFalhas();
        final int pendentes = resumo == null ? -1 : resumo.getPendenciasRestantes().size();
        final boolean agendouDiaria = resumo != null && resumo.isAgendouReconciliacaoDiaria();
        final boolean pendenciaPorFalha = resumo != null && resumo.isPendenciaPorFalha();
        final String detalhe = buildReconciliationDetail(resumo);

        try (BufferedWriter writer = Files.newBufferedWriter(
            arquivoCsv,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )) {
            if (escreverHeader) {
                writer.write(RECONCILIACAO_HISTORY_HEADER);
                writer.newLine();
            }

            final String linha = String.join(";",
                HISTORY_LINE_FORMAT.format(fimExtracao),
                HISTORY_LINE_FORMAT.format(inicioCiclo),
                HISTORY_LINE_FORMAT.format(fimExtracao),
                String.valueOf(cicloSucesso),
                sanitizeCsv(statusReconciliacao),
                String.valueOf(ativa),
                String.valueOf(executadas),
                String.valueOf(falhas),
                String.valueOf(pendentes),
                String.valueOf(agendouDiaria),
                String.valueOf(pendenciaPorFalha),
                sanitizeCsv(detalhe),
                sanitizeCsv(cicloLog.toAbsolutePath().toString())
            );
            writer.write(linha);
            writer.newLine();
        } catch (final IOException ignored) {
            // Falha no historico de reconciliacao nao interrompe o daemon.
        }
    }

    public String summarizeMessage(final String msg) {
        if (msg == null || msg.isBlank()) {
            return "Sem detalhes.";
        }
        final String limpa = msg.replace('\n', ' ').replace('\r', ' ').trim();
        return limpa.length() > 240 ? limpa.substring(0, 240) + "..." : limpa;
    }

    private void organizarCiclosLegadosPorData() {
        if (!Files.exists(cyclesDir)) {
            return;
        }
        try (var stream = Files.list(cyclesDir)) {
            stream.filter(Files::isRegularFile).forEach(this::moverCicloLegadoParaPastaData);
        } catch (final IOException ignored) {
            // Se a organizacao falhar, nao interrompe o daemon.
        }
    }

    private void moverCicloLegadoParaPastaData(final Path arquivo) {
        final String nome = arquivo.getFileName().toString();
        final Matcher matcher = CYCLE_FILENAME_PATTERN.matcher(nome);
        if (!matcher.matches()) {
            return;
        }
        final String data = matcher.group(1);
        final Path destinoDir = cyclesDir.resolve(data);
        final Path destino = destinoDir.resolve(nome);
        try {
            if (!Files.exists(destinoDir)) {
                Files.createDirectories(destinoDir);
            }
            Files.move(arquivo, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (final IOException ignored) {
            // Mantem arquivo no local atual em caso de falha de movimentacao.
        }
    }

    private int calculateTotalRecords(final LocalDateTime inicio, final LocalDateTime fim) {
        try {
            final ExecutionHistoryRepository repo = new ExecutionHistoryRepository();
            return repo.calcularTotalRegistros(inicio, fim);
        } catch (final Exception ignored) {
            return 0;
        }
    }

    private String determineCycleStatus(final boolean sucesso,
                                        final boolean alertaIntegridade,
                                        final boolean statusIncompleto,
                                        final int errors) {
        if (!sucesso) {
            return "ERROR";
        }
        if (alertaIntegridade || statusIncompleto || errors > 0) {
            return "ALERT";
        }
        return "SUCCESS";
    }

    private String buildDetailSummary(final String detalheBase,
                                      final boolean alertaIntegridade,
                                      final boolean statusIncompleto,
                                      final boolean resumoFinalSucesso,
                                      final int warns,
                                      final int errors) {
        final StringBuilder detalhe = new StringBuilder();
        detalhe.append(detalheBase == null ? "Sem detalhes." : detalheBase);
        detalhe.append(" | warns=").append(warns);
        detalhe.append(" | errors=").append(errors);
        if (alertaIntegridade) {
            detalhe.append(" | alerta_integridade=true");
        }
        if (statusIncompleto) {
            detalhe.append(" | status_incompleto=true");
        }
        detalhe.append(" | resumo_final_ok=").append(resumoFinalSucesso);
        return summarizeMessage(detalhe.toString());
    }

    private String determineReconciliationStatus(final ReconciliationSummary resumo) {
        if (resumo == null) {
            return "ERRO_PROCESSAMENTO";
        }
        if (!resumo.isAtivo()) {
            return "INATIVA";
        }
        if (resumo.getFalhas() > 0) {
            return "COM_FALHAS";
        }
        if (resumo.getReconciliacoesExecutadas() > 0 || resumo.isAgendouReconciliacaoDiaria() || resumo.isPendenciaPorFalha()) {
            return "EXECUTADA";
        }
        return "SEM_ACAO";
    }

    private String buildReconciliationDetail(final ReconciliationSummary resumo) {
        if (resumo == null) {
            return "Falha ao processar reconciliacao automatica.";
        }
        if (!resumo.isAtivo()) {
            return "Reconciliacao desativada por configuracao.";
        }

        final StringBuilder detalhe = new StringBuilder();
        detalhe.append("pendencias_restantes=");
        if (resumo.getPendenciasRestantes().isEmpty()) {
            detalhe.append("nenhuma");
        } else {
            detalhe.append(
                resumo.getPendenciasRestantes().stream()
                    .map(LocalDate::toString)
                    .collect(Collectors.joining(","))
            );
        }
        if (!resumo.getDetalhesFalha().isEmpty()) {
            detalhe.append(" | falhas=").append(String.join(" | ", resumo.getDetalhesFalha()));
        }
        return detalhe.toString();
    }

    private String sanitizeCsv(final String valor) {
        if (valor == null) {
            return "";
        }
        return valor.replace("\r", " ").replace("\n", " ").replace(";", ",").trim();
    }

    private Path resolveReconciliationHistoryDir() {
        final String override = System.getProperty(reconciliacaoHistoryDirOverrideKey);
        if (override != null && !override.isBlank()) {
            return Path.of(override.trim());
        }
        return reconciliacaoHistoryDirDefault;
    }

    public static final class CycleSummary {
        private final LocalDateTime inicio;
        private final LocalDateTime fim;
        private final long duracaoSegundos;
        private final String status;
        private final int totalRegistros;
        private final int warns;
        private final int errors;
        private final String detalhe;
        private final String logCiclo;

        public CycleSummary(final LocalDateTime inicio,
                            final LocalDateTime fim,
                            final long duracaoSegundos,
                            final String status,
                            final int totalRegistros,
                            final int warns,
                            final int errors,
                            final String detalhe,
                            final String logCiclo) {
            this.inicio = inicio;
            this.fim = fim;
            this.duracaoSegundos = duracaoSegundos;
            this.status = status;
            this.totalRegistros = totalRegistros;
            this.warns = warns;
            this.errors = errors;
            this.detalhe = detalhe;
            this.logCiclo = logCiclo;
        }

        public LocalDateTime getInicio() {
            return inicio;
        }

        public LocalDateTime getFim() {
            return fim;
        }

        public long getDuracaoSegundos() {
            return duracaoSegundos;
        }

        public String getStatus() {
            return status;
        }

        public int getTotalRegistros() {
            return totalRegistros;
        }

        public int getWarns() {
            return warns;
        }

        public int getErrors() {
            return errors;
        }

        public String getDetalhe() {
            return detalhe;
        }

        public String getLogCiclo() {
            return logCiclo;
        }
    }
}
