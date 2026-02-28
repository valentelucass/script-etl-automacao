/* ==[DOC-FILE]===============================================================
Arquivo : src/test/java/br/com/extrator/comandos/extracao/daemon/DaemonStateStoreTest.java
Classe  : DaemonStateStoreTest (class)
Pacote  : br.com.extrator.comandos.extracao.daemon
Modulo  : Teste automatizado
Papel   : Valida comportamento da unidade DaemonStateStore.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Prepara cenarios e dados de teste.
2) Executa casos para validar comportamento de DaemonStateStore.
3) Assegura regressao controlada nas regras principais.

Estrutura interna:
Metodos principais:
- novoStore(): realiza operacao relacionada a "novo store".
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.extracao.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DaemonStateStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void devePersistirEstadoEPid() throws Exception {
        final DaemonStateStore store = novoStore();
        store.ensureDaemonDirectory();
        store.syncPidFile(321L);
        store.saveState("RUNNING", 321L, "detalhe", "2026-02-27T10:00:00", "2026-02-27T10:30:00");

        final OptionalLong pidArquivo = store.readPidFile();
        final OptionalLong pidEstado = store.readPidState();
        final Properties estado = store.loadState();

        assertTrue(pidArquivo.isPresent(), "PID em arquivo deve existir");
        assertTrue(pidEstado.isPresent(), "PID em estado deve existir");
        assertEquals(321L, pidArquivo.getAsLong());
        assertEquals(321L, pidEstado.getAsLong());
        assertEquals("RUNNING", estado.getProperty("status"));
        assertEquals("detalhe", estado.getProperty("detail"));
    }

    @Test
    void deveGerenciarArquivosDeControle() throws Exception {
        final DaemonStateStore store = novoStore();
        store.ensureDaemonDirectory();

        assertFalse(store.stopRequested());
        assertFalse(store.forceRunRequested());

        store.requestStop();
        store.requestForceRun();
        assertTrue(store.stopRequested());
        assertTrue(store.forceRunRequested());

        store.clearFileIfExists(store.getStopFile());
        store.clearFileIfExists(store.getForceRunFile());
        assertFalse(store.stopRequested());
        assertFalse(store.forceRunRequested());
    }

    private DaemonStateStore novoStore() {
        return new DaemonStateStore(
            tempDir.resolve("daemon"),
            tempDir.resolve("daemon").resolve("loop_daemon.state"),
            tempDir.resolve("daemon").resolve("loop_daemon.pid"),
            tempDir.resolve("daemon").resolve("loop_daemon.stop"),
            tempDir.resolve("daemon").resolve("loop_daemon.force_run")
        );
    }
}
