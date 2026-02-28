/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/auditoria/execucao/ExecutionAuditor.java
Classe  : ExecutionAuditor (class)
Pacote  : br.com.extrator.auditoria.execucao
Modulo  : Modulo de auditoria
Papel   : Implementa responsabilidade de execution auditor.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela resultados e estado de auditoria.
2) Apoia consolidacao de evidencias operacionais.
3) Suporta emissao de relatorios de conformidade.

Estrutura interna:
Metodos principais:
- ExecutionAuditor(): realiza operacao relacionada a "execution auditor".
- registrarCsv(...6 args): grava informacoes de auditoria/log.
- construirLinha(...6 args): realiza operacao relacionada a "construir linha".
- sanitizar(...1 args): realiza operacao relacionada a "sanitizar".
Atributos-chave:
- logger: logger da classe para diagnostico.
- LOGS_DIR: campo de estado para "logs dir".
- HISTORY_DIR: campo de estado para "history dir".
- MONTH_FORMAT: campo de estado para "month format".
- LINE_FORMAT: campo de estado para "line format".
[DOC-FILE-END]============================================================== */

package br.com.extrator.auditoria.execucao;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility to register execution audit entries in a monthly CSV file.
 */
public final class ExecutionAuditor {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionAuditor.class);

    private static final String LOGS_DIR = "logs";
    private static final String HISTORY_DIR = "history";
    private static final String HEADER = "DATA_HORA;STATUS;DURATION_S;TOTAL_RECORDS;TYPE;ERROR_MSG";
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy_MM");
    private static final DateTimeFormatter LINE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private ExecutionAuditor() {
        // Utility class
    }

    /**
     * Writes a line to the monthly audit CSV. Creates folder and header when needed.
     */
    public static void registrarCsv(final LocalDateTime dataHora,
                                    final String status,
                                    final long durationSeconds,
                                    final int totalRecords,
                                    final String type,
                                    final String errorMessage) {
        try {
            final Path pastaHistory = Paths.get(LOGS_DIR, HISTORY_DIR);
            if (!Files.exists(pastaHistory)) {
                Files.createDirectories(pastaHistory);
            }

            final String arquivoNome = "execucao_" + dataHora.format(MONTH_FORMAT) + ".csv";
            final Path arquivo = pastaHistory.resolve(arquivoNome);

            final boolean escreverHeader = !Files.exists(arquivo) || Files.size(arquivo) == 0;

            try (BufferedWriter writer = Files.newBufferedWriter(
                    arquivo,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {

                if (escreverHeader) {
                    writer.write(HEADER);
                    writer.newLine();
                }

                final String linha = construirLinha(
                    dataHora,
                    status,
                    durationSeconds,
                    totalRecords,
                    type,
                    errorMessage
                );
                writer.write(linha);
                writer.newLine();
            }
        } catch (final IOException e) {
            logger.warn("Falha ao gravar auditoria de execucao em CSV: {}", e.getMessage());
        }
    }

    private static String construirLinha(final LocalDateTime dataHora,
                                         final String status,
                                         final long durationSeconds,
                                         final int totalRecords,
                                         final String type,
                                         final String errorMessage) {
        final String statusSafe = sanitizar(status);
        final String typeSafe = sanitizar(type);
        final String errorSafe = sanitizar(errorMessage);

        return dataHora.format(LINE_FORMAT)
            + ";" + statusSafe
            + ";" + durationSeconds
            + ";" + totalRecords
            + ";" + typeSafe
            + ";" + errorSafe;
    }

    private static String sanitizar(final String valor) {
        if (valor == null) {
            return "";
        }
        String texto = valor.replace("\r", " ").replace("\n", " ").replace(";", ",");
        texto = texto.trim();
        return texto;
    }
}
