package br.com.extrator.observabilidade;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Estrutura canonica de diretórios de logs do projeto.
 */
public final class LogStoragePaths {
    public static final int MAX_FILES_PER_BUCKET = 20;

    public static final Path PROJECT_ROOT = ProjectPaths.projectRoot();
    public static final Path ROOT_DIR = PROJECT_ROOT.resolve("logs");
    public static final Path RUNTIME_DIR = PROJECT_ROOT.resolve("runtime");
    public static final Path RUNTIME_STATE_DIR = RUNTIME_DIR.resolve("state");
    public static final Path RUNTIME_REPORTS_DIR = RUNTIME_DIR.resolve("reports");

    public static final Path APP_DIR = ROOT_DIR.resolve("aplicacao");
    public static final Path APP_RUNTIME_DIR = APP_DIR.resolve("runtime");
    public static final Path APP_OPERATIONS_DIR = APP_DIR.resolve("operacoes");

    public static final Path AUDITORIA_DIR = ROOT_DIR.resolve("auditoria");
    public static final Path EXECUTION_HISTORY_DIR = AUDITORIA_DIR.resolve("execucao");

    public static final Path DAEMON_DIR = ROOT_DIR.resolve("daemon");
    public static final Path DAEMON_RUNTIME_DIR = DAEMON_DIR.resolve("runtime");
    public static final Path DAEMON_CYCLES_DIR = DAEMON_DIR.resolve("ciclos");
    public static final Path DAEMON_HISTORY_DIR = DAEMON_DIR.resolve("historico");
    public static final Path DAEMON_RECONCILIATION_DIR = DAEMON_DIR.resolve("reconciliacao");

    public static final Path ISOLATED_STEPS_DIR = ROOT_DIR.resolve("processos_isolados");
    public static final Path REPORTS_DIR = ROOT_DIR.resolve("relatorios");

    private LogStoragePaths() {
        // utility class
    }

    public static void ensureBaseDirectories() throws IOException {
        for (final Path dir : allDirectories()) {
            Files.createDirectories(dir);
        }
    }

    public static List<Path> allDirectories() {
        return List.of(
            PROJECT_ROOT,
            ROOT_DIR,
            RUNTIME_DIR,
            RUNTIME_STATE_DIR,
            RUNTIME_REPORTS_DIR,
            APP_DIR,
            APP_RUNTIME_DIR,
            APP_OPERATIONS_DIR,
            AUDITORIA_DIR,
            EXECUTION_HISTORY_DIR,
            DAEMON_DIR,
            DAEMON_RUNTIME_DIR,
            DAEMON_CYCLES_DIR,
            DAEMON_HISTORY_DIR,
            DAEMON_RECONCILIATION_DIR,
            ISOLATED_STEPS_DIR,
            REPORTS_DIR
        );
    }
}
