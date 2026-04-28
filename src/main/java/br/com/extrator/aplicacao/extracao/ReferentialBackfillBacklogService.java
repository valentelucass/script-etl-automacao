package br.com.extrator.aplicacao.extracao;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.observabilidade.LogStoragePaths;

final class ReferentialBackfillBacklogService {
    static final Path DEFAULT_STATE_FILE = LogStoragePaths.RUNTIME_STATE_DIR.resolve("coletas_referential_backfill.properties");

    private static final String KEY_PENDING_START = "pending_start";
    private static final String KEY_PENDING_END = "pending_end";
    private static final String KEY_REASON = "reason";
    private static final String KEY_UPDATED_AT = "updated_at";

    private final Path stateFile;
    private final Clock clock;
    private final Logger logger;

    ReferentialBackfillBacklogService() {
        this(DEFAULT_STATE_FILE, Clock.systemDefaultZone(), LoggerFactory.getLogger(ReferentialBackfillBacklogService.class));
    }

    ReferentialBackfillBacklogService(final Path stateFile, final Clock clock, final Logger logger) {
        this.stateFile = Objects.requireNonNull(stateFile, "stateFile nao pode ser null");
        this.clock = Objects.requireNonNull(clock, "clock nao pode ser null");
        this.logger = Objects.requireNonNull(logger, "logger nao pode ser null");
    }

    Optional<BacklogWindow> loadPending() {
        if (!Files.exists(stateFile)) {
            return Optional.empty();
        }

        final Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(stateFile)) {
            properties.load(input);
        } catch (final IOException e) {
            logger.warn("Falha ao carregar backlog referencial de coletas {}: {}", stateFile.toAbsolutePath(), e.getMessage());
            return Optional.empty();
        }

        final LocalDate inicio = parseDate(properties.getProperty(KEY_PENDING_START));
        final LocalDate fim = parseDate(properties.getProperty(KEY_PENDING_END));
        if (inicio == null || fim == null || inicio.isAfter(fim)) {
            return Optional.empty();
        }
        return Optional.of(new BacklogWindow(inicio, fim, properties.getProperty(KEY_REASON, "")));
    }

    void mergePending(final LocalDate inicio, final LocalDate fim, final String reason) {
        if (inicio == null || fim == null || inicio.isAfter(fim)) {
            return;
        }
        final Optional<BacklogWindow> atual = loadPending();
        final LocalDate inicioMesclado = atual.map(BacklogWindow::inicio).map(valor -> valor.isBefore(inicio) ? valor : inicio).orElse(inicio);
        final LocalDate fimMesclado = atual.map(BacklogWindow::fim).map(valor -> valor.isAfter(fim) ? valor : fim).orElse(fim);
        final String motivoMesclado = reason == null || reason.isBlank()
            ? atual.map(BacklogWindow::reason).orElse("")
            : reason;
        save(Optional.of(new BacklogWindow(inicioMesclado, fimMesclado, motivoMesclado)));
    }

    void markProcessedUntil(final LocalDate processedEnd) {
        if (processedEnd == null) {
            return;
        }
        final Optional<BacklogWindow> atual = loadPending();
        if (atual.isEmpty()) {
            return;
        }
        final BacklogWindow janela = atual.get();
        if (processedEnd.isBefore(janela.inicio())) {
            return;
        }

        final LocalDate proximoInicio = processedEnd.plusDays(1);
        if (proximoInicio.isAfter(janela.fim())) {
            clear();
            return;
        }
        save(Optional.of(new BacklogWindow(proximoInicio, janela.fim(), janela.reason())));
    }

    void clear() {
        try {
            Files.deleteIfExists(stateFile);
        } catch (final IOException e) {
            logger.warn("Falha ao limpar backlog referencial de coletas {}: {}", stateFile.toAbsolutePath(), e.getMessage());
        }
    }

    private void save(final Optional<BacklogWindow> janela) {
        if (janela.isEmpty()) {
            clear();
            return;
        }

        final Properties properties = new Properties();
        properties.setProperty(KEY_PENDING_START, janela.get().inicio().toString());
        properties.setProperty(KEY_PENDING_END, janela.get().fim().toString());
        properties.setProperty(KEY_REASON, janela.get().reason() == null ? "" : janela.get().reason());
        properties.setProperty(KEY_UPDATED_AT, LocalDateTime.now(clock).toString());

        try {
            final Path parent = stateFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(
                stateFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )) {
                properties.store(output, "referential-backfill-backlog");
            }
        } catch (final IOException e) {
            logger.warn("Falha ao salvar backlog referencial de coletas {}: {}", stateFile.toAbsolutePath(), e.getMessage());
        }
    }

    private LocalDate parseDate(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (final RuntimeException e) {
            logger.warn("Data invalida no backlog referencial de coletas: {}", value);
            return null;
        }
    }

    record BacklogWindow(LocalDate inicio, LocalDate fim, String reason) {
    }
}
