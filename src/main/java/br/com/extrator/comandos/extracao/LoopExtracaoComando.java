/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/extracao/LoopExtracaoComando.java
Classe  : LoopExtracaoComando (class)
Pacote  : br.com.extrator.comandos.extracao
Modulo  : Comando CLI (extracao)
Papel   : Implementa responsabilidade de loop extracao comando.

Conecta com:
- Comando (comandos.base)
- LoggerConsole (util.console)

Fluxo geral:
1) Interpreta parametros e escopo de extracao.
2) Dispara runners/extratores conforme alvo.
3) Consolida status final e tratamento de falhas.

Estrutura interna:
Metodos principais:
- iniciar(): inicia recursos e prepara o processamento.
- pausar(): realiza operacao relacionada a "pausar".
- retomar(): realiza operacao relacionada a "retomar".
- parar(): encerra recursos e finaliza operacao com seguranca.
- alternarFaturasGraphQL(): realiza operacao relacionada a "alternar faturas graph ql".
- imprimirStatus(): realiza operacao relacionada a "imprimir status".
- executarCiclo(): executa o fluxo principal desta responsabilidade.
- possuiFlag(...2 args): realiza operacao relacionada a "possui flag".
Atributos-chave:
- log: campo de estado para "log".
- FLAG_SEM_FATURAS_GRAPHQL: campo de estado para "flag sem faturas graphql".
- running: campo de estado para "running".
- paused: campo de estado para "paused".
- executing: campo de estado para "executing".
- incluirFaturasGraphQL: campo de estado para "incluir faturas graph ql".
- scheduler: campo de estado para "scheduler".
- future: campo de estado para "future".
- nextRunAt: campo de estado para "next run at".
- intervalMinutes: campo de estado para "interval minutes".
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.extracao;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import br.com.extrator.comandos.base.Comando;
import br.com.extrator.util.console.LoggerConsole;

public class LoopExtracaoComando implements Comando {
    private static final LoggerConsole log = LoggerConsole.getLogger(LoopExtracaoComando.class);
    private static final String FLAG_SEM_FATURAS_GRAPHQL = "--sem-faturas-graphql";

    private volatile boolean running = false;
    private volatile boolean paused = false;
    private volatile boolean executing = false;
    private volatile boolean incluirFaturasGraphQL = true;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> future;
    private volatile LocalDateTime nextRunAt;
    private final int intervalMinutes = 30;

    @Override
    public void executar(final String[] args) throws Exception {
        incluirFaturasGraphQL = !possuiFlag(args, FLAG_SEM_FATURAS_GRAPHQL);

        log.console("=".repeat(80));
        log.info("LOOP DE EXTRACAO A CADA 30 MINUTOS");
        log.info("Se uma entidade falhar, sera reextraida no proximo ciclo.");
        log.info("Faturas GraphQL no loop: {}", incluirFaturasGraphQL ? "INCLUIDO" : "DESABILITADO (" + FLAG_SEM_FATURAS_GRAPHQL + ")");
        log.console("=".repeat(80));

        imprimirStatus();

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                log.console("");
                log.console("[I] Iniciar  [N] Nova imediata  [P] Pausar  [R] Retomar  [G] Alternar Faturas GraphQL  [S] Parar  [T] Status  [X] Sair");
                System.out.print("Opcao: ");

                final String opcao = scanner.nextLine().trim();
                if ("I".equalsIgnoreCase(opcao)) {
                    iniciar();
                } else if ("N".equalsIgnoreCase(opcao)) {
                    executarCiclo();
                } else if ("P".equalsIgnoreCase(opcao)) {
                    pausar();
                } else if ("R".equalsIgnoreCase(opcao)) {
                    retomar();
                } else if ("G".equalsIgnoreCase(opcao)) {
                    alternarFaturasGraphQL();
                } else if ("S".equalsIgnoreCase(opcao)) {
                    parar();
                } else if ("T".equalsIgnoreCase(opcao)) {
                    imprimirStatus();
                } else if ("X".equalsIgnoreCase(opcao)) {
                    parar();
                    log.info("Encerrando console de loop.");
                    break;
                } else {
                    log.warn("Opcao invalida");
                }
            }
        }
    }

    private void iniciar() {
        if (running) {
            if (paused) {
                paused = false;
                log.info("Loop retomado e iniciando nova extracao imediata");
                executarCiclo();
            } else {
                log.info("Loop ja esta ativo - iniciando nova extracao imediata");
                executarCiclo();
            }
        } else {
            scheduler = Executors.newSingleThreadScheduledExecutor();
            running = true;
            paused = false;
            future = scheduler.scheduleWithFixedDelay(this::executarCiclo, 0, intervalMinutes, TimeUnit.MINUTES);
            log.info("Loop iniciado (intervalo: 30 minutos)");
            imprimirStatus();
        }
    }

    private void pausar() {
        if (!running) {
            log.warn("Loop nao esta ativo");
            return;
        }
        if (paused) {
            log.warn("Loop ja esta pausado");
            return;
        }
        paused = true;
        log.info("Loop pausado");
        imprimirStatus();
    }

    private void retomar() {
        if (!running) {
            log.warn("Loop nao esta ativo");
            return;
        }
        if (!paused) {
            log.warn("Loop nao esta pausado");
            return;
        }
        paused = false;
        log.info("Loop retomado");
        imprimirStatus();
    }

    private void parar() {
        if (!running) {
            log.warn("Loop nao esta ativo");
            return;
        }
        running = false;
        paused = false;

        if (future != null) {
            future.cancel(true);
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        log.info("Loop parado");
        imprimirStatus();
    }

    private void alternarFaturasGraphQL() {
        incluirFaturasGraphQL = !incluirFaturasGraphQL;
        log.info("Faturas GraphQL agora esta {}", incluirFaturasGraphQL ? "INCLUIDO" : "DESABILITADO (" + FLAG_SEM_FATURAS_GRAPHQL + ")");
    }

    private void imprimirStatus() {
        final String status = running ? (paused ? "PAUSADO" : "ATIVO") : "INATIVO";
        log.info("Status atual: {}", status);
        log.info("Faturas GraphQL: {}", incluirFaturasGraphQL ? "INCLUIDO" : "DESABILITADO (" + FLAG_SEM_FATURAS_GRAPHQL + ")");

        if (nextRunAt != null) {
            final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            final LocalDateTime agora = LocalDateTime.now();
            final Duration restante = Duration.between(agora, nextRunAt);
            final long mm = Math.max(0, restante.toMinutes());
            final long ss = Math.max(0, restante.minusMinutes(mm).toSeconds());
            log.info("Proxima execucao: {} (em {}m {}s)", nextRunAt.format(fmt), mm, ss);
        } else {
            log.info("Proxima execucao: indefinida");
        }
    }

    private void executarCiclo() {
        if (paused) {
            return;
        }
        if (executing) {
            log.warn("Uma extracao ja esta em execucao");
            return;
        }

        executing = true;
        final LocalDateTime inicio = LocalDateTime.now();
        log.info("Iniciando extracao completa...");

        try {
            final br.com.extrator.servicos.LoggingService ls = new br.com.extrator.servicos.LoggingService();
            ls.iniciarCaptura("extracao_dados_loop");
            String statusExecucao = "SUCCESS";
            try {
                if (incluirFaturasGraphQL) {
                    new ExecutarFluxoCompletoComando().executar(new String[] { "--fluxo-completo" });
                } else {
                    new ExecutarFluxoCompletoComando().executar(new String[] { "--fluxo-completo", FLAG_SEM_FATURAS_GRAPHQL });
                }
            } catch (final Exception e) {
                statusExecucao = "ERROR";
                throw e;
            } finally {
                ls.pararCaptura(statusExecucao);
            }
        } catch (final Exception e) {
            log.error("Falha ao executar extracao: {}", e.getMessage());
        } finally {
            executing = false;
        }

        final LocalDateTime fim = LocalDateTime.now();
        final Duration duracao = Duration.between(inicio, fim);
        final long dm = Math.max(0, duracao.toMinutes());
        final long ds = Math.max(0, duracao.minusMinutes(dm).toSeconds());

        nextRunAt = fim.plusMinutes(intervalMinutes);
        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        final Duration restante = Duration.between(fim, nextRunAt);
        final long mm = Math.max(0, restante.toMinutes());
        final long ss = Math.max(0, restante.minusMinutes(mm).toSeconds());

        log.info("Extracao concluida em {} (duracao {}m {}s) | Proxima em {} (em {}m {}s)",
            fim.format(fmt), dm, ds, nextRunAt.format(fmt), mm, ss);
        log.console("Durante a espera: [N] Nova imediata  [P] Pausar  [R] Retomar  [G] Alternar Faturas GraphQL  [S] Parar  [T] Status  [X] Parar/Sair");
    }

    private boolean possuiFlag(final String[] args, final String flag) {
        if (args == null || flag == null) {
            return false;
        }
        for (final String arg : args) {
            if (arg != null && flag.equalsIgnoreCase(arg.trim())) {
                return true;
            }
        }
        return false;
    }
}
