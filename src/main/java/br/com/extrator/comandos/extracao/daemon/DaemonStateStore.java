/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/extracao/daemon/DaemonStateStore.java
Classe  : DaemonStateStore (class)
Pacote  : br.com.extrator.comandos.extracao.daemon
Modulo  : Comando CLI (daemon)
Papel   : Implementa responsabilidade de daemon state store.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Recebe parametros de execucao em modo daemon.
2) Coordena ciclo, persistencia de estado e logs de runtime.
3) Controla retomada e historico de ciclos.

Estrutura interna:
Metodos principais:
- criarPadrao(): instancia ou monta estrutura de dados.
- DaemonStateStore(...5 args): realiza operacao relacionada a "daemon state store".
- getPidFile(): expone valor atual do estado interno.
- getStopFile(): expone valor atual do estado interno.
- getForceRunFile(): expone valor atual do estado interno.
- stopRequested(): realiza operacao relacionada a "stop requested".
- forceRunRequested(): realiza operacao relacionada a "force run requested".
- readPidFile(): realiza operacao relacionada a "read pid file".
- readPidState(): realiza operacao relacionada a "read pid state".
- loadState(): realiza operacao relacionada a "load state".
- saveState(...5 args): realiza operacao relacionada a "save state".
- syncPidFile(...1 args): realiza operacao relacionada a "sync pid file".
Atributos-chave:
- daemonDir: campo de estado para "daemon dir".
- stateFile: campo de estado para "state file".
- pidFile: campo de estado para "pid file".
- stopFile: campo de estado para "stop file".
- forceRunFile: campo de estado para "force run file".
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.extracao.daemon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.OptionalLong;
import java.util.Properties;

/**
 * Gerencia arquivos de estado/controle do loop daemon.
 */
public final class DaemonStateStore {
    private final Path daemonDir;
    private final Path stateFile;
    private final Path pidFile;
    private final Path stopFile;
    private final Path forceRunFile;

    public static DaemonStateStore criarPadrao() {
        return new DaemonStateStore(
            DaemonPaths.DAEMON_DIR,
            DaemonPaths.STATE_FILE,
            DaemonPaths.PID_FILE,
            DaemonPaths.STOP_FILE,
            DaemonPaths.FORCE_RUN_FILE
        );
    }

    public DaemonStateStore(final Path daemonDir,
                            final Path stateFile,
                            final Path pidFile,
                            final Path stopFile,
                            final Path forceRunFile) {
        this.daemonDir = daemonDir;
        this.stateFile = stateFile;
        this.pidFile = pidFile;
        this.stopFile = stopFile;
        this.forceRunFile = forceRunFile;
    }

    public Path getPidFile() {
        return pidFile;
    }

    public Path getStopFile() {
        return stopFile;
    }

    public Path getForceRunFile() {
        return forceRunFile;
    }

    public void ensureDaemonDirectory() throws IOException {
        if (!Files.exists(daemonDir)) {
            Files.createDirectories(daemonDir);
        }
    }

    public void clearFileIfExists(final Path path) throws IOException {
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }

    public void requestStop() throws IOException {
        Files.writeString(stopFile, "stop@" + LocalDateTime.now(), StandardCharsets.UTF_8);
    }

    public void requestForceRun() throws IOException {
        Files.writeString(forceRunFile, "force-run@" + LocalDateTime.now(), StandardCharsets.UTF_8);
    }

    public boolean stopRequested() {
        return Files.exists(stopFile);
    }

    public boolean forceRunRequested() {
        return Files.exists(forceRunFile);
    }

    public OptionalLong readPidFile() {
        if (!Files.exists(pidFile)) {
            return OptionalLong.empty();
        }
        try {
            final String conteudo = Files.readString(pidFile, StandardCharsets.UTF_8).trim();
            if (conteudo.isEmpty()) {
                return OptionalLong.empty();
            }
            return OptionalLong.of(Long.parseLong(conteudo));
        } catch (final Exception e) {
            return OptionalLong.empty();
        }
    }

    public OptionalLong readPidState() {
        final Properties estado = loadState();
        final String pidTexto = estado.getProperty("pid", "").trim();
        if (pidTexto.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(pidTexto));
        } catch (final NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    public Properties loadState() {
        final Properties properties = new Properties();
        if (!Files.exists(stateFile)) {
            return properties;
        }
        try (var in = Files.newInputStream(stateFile)) {
            properties.load(in);
        } catch (final IOException ignored) {
            // Mantem estado vazio em caso de falha de leitura.
        }
        return properties;
    }

    public void saveState(final String status,
                          final long pid,
                          final String detalhe,
                          final String lastRunAt,
                          final String nextRunAt) {
        final Properties properties = new Properties();
        properties.setProperty("status", status);
        properties.setProperty("pid", pid > 0 ? String.valueOf(pid) : "");
        properties.setProperty("detail", detalhe == null ? "" : detalhe);
        properties.setProperty("updated_at", LocalDateTime.now().toString());
        properties.setProperty("last_run_at", lastRunAt == null ? "" : lastRunAt);
        properties.setProperty("next_run_at", nextRunAt == null ? "" : nextRunAt);

        try (var out = Files.newOutputStream(
            stateFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )) {
            properties.store(out, "loop-daemon-state");
        } catch (final IOException e) {
            throw new RuntimeException("Falha ao salvar estado do loop daemon.", e);
        }
    }

    public void syncPidFile(final long pid) {
        if (pid <= 0) {
            return;
        }
        try {
            Files.writeString(pidFile, String.valueOf(pid), StandardCharsets.UTF_8);
        } catch (final IOException ignored) {
            // Falha ao sincronizar PID nao deve interromper fluxo principal.
        }
    }
}
