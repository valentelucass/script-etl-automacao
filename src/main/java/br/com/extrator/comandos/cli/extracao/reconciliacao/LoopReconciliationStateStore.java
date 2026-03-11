/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/cli/extracao/reconciliacao/LoopReconciliationStateStore.java
Classe  : LoopReconciliationStateStore (service), ReconciliationState (value class)
Pacote  : br.com.extrator.comandos.cli.extracao.reconciliacao
Modulo  : CLI - Reconciliacao
Papel   : Persiste/carrega estado de loop de reconciliação em arquivo Properties (datas agendadas, sucesso, pendências).
Conecta com: Sem dependencia interna (usa Clock, Logger, Path)
Fluxo geral:
1) load() lê Properties de arquivo, restaura ReconciliationState ou retorna vazio
2) save() serializa estado (datas, pendências, último erro) em Properties
3) parseDate/toStringDate convertem entre LocalDate e ISO_DATE string
Estrutura interna:
Atributos: stateFile, clock, logger
Metodos: load(), save(), parseDate(), toStringDate() [private]
[DOC-FILE-END]============================================================== */
package br.com.extrator.comandos.cli.extracao.reconciliacao;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;

final class LoopReconciliationStateStore {
    private static final String KEY_LAST_DAILY_SCHEDULED_DATE = "last_daily_scheduled_date";
    private static final String KEY_LAST_SUCCESSFUL_RECONCILIATION_DATE = "last_successful_reconciliation_date";
    private static final String KEY_PENDING_DATES = "pending_dates";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_UPDATED_AT = "updated_at";

    private final Path stateFile;
    private final Clock clock;
    private final Logger logger;

    LoopReconciliationStateStore(final Path stateFile, final Clock clock, final Logger logger) {
        this.stateFile = stateFile;
        this.clock = clock;
        this.logger = logger;
    }

    ReconciliationState load() {
        final ReconciliationState estado = new ReconciliationState();
        if (!Files.exists(stateFile)) {
            return estado;
        }

        final Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(stateFile)) {
            properties.load(in);
        } catch (final IOException e) {
            logger.warn("Falha ao carregar estado de reconciliacao {}: {}", stateFile.toAbsolutePath(), e.getMessage());
            return estado;
        }

        estado.lastDailyScheduledDate = parseDate(properties.getProperty(KEY_LAST_DAILY_SCHEDULED_DATE));
        estado.lastSuccessfulReconciliationDate = parseDate(properties.getProperty(KEY_LAST_SUCCESSFUL_RECONCILIATION_DATE));

        final String pendencias = properties.getProperty(KEY_PENDING_DATES, "");
        if (!pendencias.isBlank()) {
            for (final String token : pendencias.split(",")) {
                final LocalDate data = parseDate(token);
                if (data != null) {
                    estado.pendingDates.add(data);
                }
            }
        }

        return estado;
    }

    void save(final ReconciliationState estado, final List<String> detalhesFalha) {
        final Properties properties = new Properties();
        properties.setProperty(KEY_LAST_DAILY_SCHEDULED_DATE, toStringDate(estado.lastDailyScheduledDate));
        properties.setProperty(KEY_LAST_SUCCESSFUL_RECONCILIATION_DATE, toStringDate(estado.lastSuccessfulReconciliationDate));
        properties.setProperty(
            KEY_PENDING_DATES,
            estado.pendingDates.stream()
                .sorted()
                .map(LocalDate::toString)
                .collect(Collectors.joining(","))
        );
        properties.setProperty(KEY_LAST_ERROR, detalhesFalha.isEmpty() ? "" : String.join(" | ", detalhesFalha));
        properties.setProperty(KEY_UPDATED_AT, LocalDateTime.now(clock).toString());

        try {
            final Path parent = stateFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(
                stateFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )) {
                properties.store(out, "loop-reconciliation-state");
            }
        } catch (final IOException e) {
            logger.error("Falha ao salvar estado de reconciliacao {}: {}", stateFile.toAbsolutePath(), e.getMessage(), e);
        }
    }

    private LocalDate parseDate(final String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim(), java.time.format.DateTimeFormatter.ISO_DATE);
        } catch (final RuntimeException e) {
            logger.warn("Data invalida no estado de reconciliacao: {}", valor);
            return null;
        }
    }

    private String toStringDate(final LocalDate data) {
        return data == null ? "" : data.toString();
    }

    static final class ReconciliationState {
        LocalDate lastDailyScheduledDate;
        LocalDate lastSuccessfulReconciliationDate;
        final Set<LocalDate> pendingDates = new LinkedHashSet<>();
    }
}
