/* ==[DOC-FILE]===============================================================
Arquivo : src/test/java/br/com/extrator/comandos/extracao/daemon/DaemonLifecycleServiceTest.java
Classe  : DaemonLifecycleServiceTest (class)
Pacote  : br.com.extrator.comandos.cli.extracao.daemon
Modulo  : Teste automatizado
Papel   : Valida comportamento da unidade DaemonLifecycleService.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Prepara cenarios e dados de teste.
2) Executa casos para validar comportamento de DaemonLifecycleService.
3) Assegura regressao controlada nas regras principais.

Estrutura interna:
Metodos principais:
- novoService(): realiza operacao relacionada a "novo service".
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.cli.extracao.daemon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DaemonLifecycleServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void deveMontarComandoFilhoComFlagLoopDaemon() throws Exception {
        final DaemonLifecycleService service = novoService();

        final List<String> comando = service.construirComandoFilho(true);

        assertFalse(comando.isEmpty(), "Comando do processo filho nao pode ser vazio");
        assertTrue(comando.stream().anyMatch(arg -> arg.contains("java")), "Comando deve conter executavel Java");
        assertTrue(comando.contains("--loop-daemon-run"), "Comando deve conter flag de loop daemon");
        assertFalse(comando.contains("--sem-faturas-graphql"), "Modo padrao deve incluir faturas GraphQL");
    }

    @Test
    void deveIncluirFlagSemFaturasQuandoDesabilitado() throws Exception {
        final DaemonLifecycleService service = novoService();

        final List<String> comando = service.construirComandoFilho(false);

        assertTrue(comando.contains("--loop-daemon-run"), "Comando deve conter flag de loop daemon");
        assertTrue(comando.contains("--sem-faturas-graphql"), "Comando deve carregar flag de desabilitar faturas GraphQL");
    }

    @Test
    void naoDeveConfundirStepIsoladoComDaemonPorParentCommand() {
        final String comandoStep =
            "\"C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.2.10-hotspot\\bin\\java.exe\" "
                + "-Detl.parent.command=--loop-daemon-run "
                + "-jar C:\\repo\\logs\\daemon\\runtime\\extrator-daemon-runtime-1.jar "
                + "--executar-step-isolado graphql 2026-04-24 2026-04-30 all";

        assertFalse(
            DaemonLifecycleService.ehComandoLoopDaemon(comandoStep),
            "Step isolado herdado do daemon nao deve ser classificado como daemon"
        );
        assertTrue(
            DaemonLifecycleService.ehComandoStepIsoladoDoDaemon(comandoStep),
            "Step isolado herdado do daemon deve ser alvo da parada do loop"
        );
    }

    @Test
    void deveReconhecerComandoRealDoLoopDaemon() {
        final String comandoDaemon =
            "\"C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.2.10-hotspot\\bin\\java.exe\" "
                + "-jar C:\\repo\\logs\\daemon\\runtime\\extrator-daemon-runtime-1.jar "
                + "--loop-daemon-run --sem-faturas-graphql";

        assertTrue(DaemonLifecycleService.ehComandoLoopDaemon(comandoDaemon));
        assertFalse(DaemonLifecycleService.ehComandoStepIsoladoDoDaemon(comandoDaemon));
    }

    @Test
    void devePropagarSystemPropertiesDeConfiguracaoParaProcessoFilho() throws Exception {
        final String chaveApi = "API_THROTTLING_MINIMO_MS";
        final String chaveEtl = "ETL_GRAPHQL_TIMEOUT_ENTIDADE_USUARIOS_SISTEMA_MS";
        final String chaveLower = "etl.custom.flag";
        final String chaveIgnorada = "user.language";
        final String valorApiAnterior = System.getProperty(chaveApi);
        final String valorEtlAnterior = System.getProperty(chaveEtl);
        final String valorLowerAnterior = System.getProperty(chaveLower);
        final String valorIgnoradoAnterior = System.getProperty(chaveIgnorada);

        try {
            System.setProperty(chaveApi, "500");
            System.setProperty(chaveEtl, "5400000");
            System.setProperty(chaveLower, "habilitado");
            System.setProperty(chaveIgnorada, "pt");

            final DaemonLifecycleService service = novoService();
            final List<String> comando = service.construirComandoFilho(false);

            assertTrue(comando.contains("-D" + chaveApi + "=500"));
            assertTrue(comando.contains("-D" + chaveEtl + "=5400000"));
            assertTrue(comando.contains("-D" + chaveLower + "=habilitado"));
            assertFalse(
                comando.stream().anyMatch(arg -> arg.startsWith("-D" + chaveIgnorada + "=")),
                "Propriedades alheias a API/ETL nao devem vazar para o daemon"
            );
            assertEquals(
                1L,
                comando.stream().filter(arg -> arg.equals("-Dfile.encoding=UTF-8")).count(),
                "Flags fixas nao devem ser duplicadas ao propagar propriedades"
            );
        } finally {
            restaurarSystemProperty(chaveApi, valorApiAnterior);
            restaurarSystemProperty(chaveEtl, valorEtlAnterior);
            restaurarSystemProperty(chaveLower, valorLowerAnterior);
            restaurarSystemProperty(chaveIgnorada, valorIgnoradoAnterior);
        }
    }

    @Test
    void deveReconhecerPidPersistidoComoDaemonAtivoQuandoEstadoIndicarExecucao() {
        final DaemonStateStore store = novoStore();
        final DaemonLifecycleService service = novoService(store);
        final long pidAtual = ProcessHandle.current().pid();

        garantirDiretorio(store);
        store.syncPidFile(pidAtual);
        store.saveState("WAITING_NEXT_CYCLE", pidAtual, "Daemon aguardando proximo ciclo.", null, null);

        assertTrue(
            service.processoEhLoopDaemonAtivo(pidAtual),
            "PID persistido com estado ativo deve ser reconhecido mesmo quando o comando do processo nao estiver acessivel"
        );
    }

    @Test
    void naoDeveReconhecerPidPersistidoQuandoEstadoEstiverParado() {
        final DaemonStateStore store = novoStore();
        final DaemonLifecycleService service = novoService(store);
        final long pidAtual = ProcessHandle.current().pid();

        garantirDiretorio(store);
        store.syncPidFile(pidAtual);
        store.saveState("STOPPED", pidAtual, "Daemon parado.", null, null);

        assertFalse(
            service.processoEhLoopDaemonAtivo(pidAtual),
            "Estado parado nao deve tratar PID persistido como daemon ativo"
        );
    }

    private DaemonLifecycleService novoService() {
        return novoService(novoStore());
    }

    private DaemonLifecycleService novoService(final DaemonStateStore store) {
        return new DaemonLifecycleService(
            store,
            tempDir.resolve("daemon.log"),
            tempDir.resolve("runtime")
        );
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

    private void garantirDiretorio(final DaemonStateStore store) {
        try {
            store.ensureDaemonDirectory();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void restaurarSystemProperty(final String chave, final String valorAnterior) {
        if (valorAnterior == null) {
            System.clearProperty(chave);
            return;
        }
        System.setProperty(chave, valorAnterior);
    }
}
