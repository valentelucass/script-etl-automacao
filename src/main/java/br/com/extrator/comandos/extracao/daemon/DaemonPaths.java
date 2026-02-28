/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/extracao/daemon/DaemonPaths.java
Classe  : DaemonPaths (class)
Pacote  : br.com.extrator.comandos.extracao.daemon
Modulo  : Comando CLI (daemon)
Papel   : Implementa responsabilidade de daemon paths.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Recebe parametros de execucao em modo daemon.
2) Coordena ciclo, persistencia de estado e logs de runtime.
3) Controla retomada e historico de ciclos.

Estrutura interna:
Metodos principais:
- DaemonPaths(): realiza operacao relacionada a "daemon paths".
Atributos-chave:
- DAEMON_DIR: campo de estado para "daemon dir".
- CYCLES_DIR: campo de estado para "cycles dir".
- DAEMON_HISTORY_DIR: campo de estado para "daemon history dir".
- RECONCILIACAO_HISTORY_DIR_DEFAULT: campo de estado para "reconciliacao history dir default".
- RECONCILIACAO_HISTORY_DIR_OVERRIDE_KEY: campo de estado para "reconciliacao history dir override key".
- RUNTIME_DIR: campo de estado para "runtime dir".
- PID_FILE: campo de estado para "pid file".
- STATE_FILE: campo de estado para "state file".
- RECONCILIACAO_STATE_FILE: campo de estado para "reconciliacao state file".
- STOP_FILE: campo de estado para "stop file".
- FORCE_RUN_FILE: campo de estado para "force run file".
- DAEMON_STDOUT_FILE: campo de estado para "daemon stdout file".
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.extracao.daemon;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Caminhos padrao utilizados pelo loop daemon.
 */
public final class DaemonPaths {
    public static final Path DAEMON_DIR = Paths.get("logs", "daemon");
    public static final Path CYCLES_DIR = DAEMON_DIR.resolve("ciclos");
    public static final Path DAEMON_HISTORY_DIR = DAEMON_DIR.resolve("history");
    public static final Path RECONCILIACAO_HISTORY_DIR_DEFAULT = DAEMON_DIR.resolve("reconciliacao");
    public static final String RECONCILIACAO_HISTORY_DIR_OVERRIDE_KEY = "extrator.loop.reconciliacao.history.dir";
    public static final Path RUNTIME_DIR = DAEMON_DIR.resolve("runtime");
    public static final Path PID_FILE = DAEMON_DIR.resolve("loop_daemon.pid");
    public static final Path STATE_FILE = DAEMON_DIR.resolve("loop_daemon.state");
    public static final Path RECONCILIACAO_STATE_FILE = DAEMON_DIR.resolve("loop_reconciliation.state");
    public static final Path STOP_FILE = DAEMON_DIR.resolve("loop_daemon.stop");
    public static final Path FORCE_RUN_FILE = DAEMON_DIR.resolve("loop_daemon.force_run");
    public static final Path DAEMON_STDOUT_FILE = DAEMON_DIR.resolve("loop_daemon_console.log");

    private DaemonPaths() {
        // utility class
    }
}
