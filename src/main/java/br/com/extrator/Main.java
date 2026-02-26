package br.com.extrator;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.auditoria.execucao.ExecutionAuditor;
import br.com.extrator.comandos.auditoria.AuditarEstruturaApiComando;
import br.com.extrator.comandos.auditoria.ExecutarAuditoriaComando;
import br.com.extrator.comandos.base.Comando;
import br.com.extrator.comandos.extracao.PartialExecutionException;
import br.com.extrator.comandos.console.ExibirAjudaComando;
import br.com.extrator.comandos.extracao.ExecutarExtracaoPorIntervaloComando;
import br.com.extrator.comandos.extracao.ExecutarFluxoCompletoComando;
import br.com.extrator.comandos.extracao.LoopDaemonComando;
import br.com.extrator.comandos.extracao.LoopExtracaoComando;
import br.com.extrator.comandos.seguranca.AuthBootstrapComando;
import br.com.extrator.comandos.seguranca.AuthCheckComando;
import br.com.extrator.comandos.seguranca.AuthCreateUserComando;
import br.com.extrator.comandos.seguranca.AuthDisableUserComando;
import br.com.extrator.comandos.seguranca.AuthInfoComando;
import br.com.extrator.comandos.seguranca.AuthResetPasswordComando;
import br.com.extrator.comandos.utilitarios.ExportarCsvComando;
import br.com.extrator.comandos.utilitarios.LimparTabelasComando;
import br.com.extrator.comandos.utilitarios.RealizarIntrospeccaoGraphQLComando;
import br.com.extrator.comandos.utilitarios.TestarApiComando;
import br.com.extrator.comandos.validacao.ValidarAcessoComando;
import br.com.extrator.comandos.validacao.ValidarApiVsBanco24hComando;
import br.com.extrator.comandos.validacao.ValidarApiVsBanco24hDetalhadoComando;
import br.com.extrator.comandos.validacao.ValidarDadosCompletoComando;
import br.com.extrator.comandos.validacao.ValidarManifestosComando;
import br.com.extrator.comandos.validacao.VerificarTimestampsComando;
import br.com.extrator.comandos.validacao.VerificarTimezoneComando;
import br.com.extrator.db.repository.ExecutionHistoryRepository;
import br.com.extrator.servicos.LoggingService;

/**
 * Orquestrador principal dos comandos do extrator.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final Map<String, Comando> COMANDOS = criarMapaComandos();

    private static Map<String, Comando> criarMapaComandos() {
        final Map<String, Comando> comandos = new HashMap<>();
        comandos.put("--fluxo-completo", new ExecutarFluxoCompletoComando());
        comandos.put("--extracao-intervalo", new ExecutarExtracaoPorIntervaloComando());
        comandos.put("--loop", new LoopExtracaoComando());
        comandos.put("--validar", new ValidarAcessoComando());
        comandos.put("--ajuda", new ExibirAjudaComando());
        comandos.put("--help", new ExibirAjudaComando());
        comandos.put("--introspeccao", new RealizarIntrospeccaoGraphQLComando());
        comandos.put("--auditoria", new ExecutarAuditoriaComando());
        comandos.put("--auditar-api", new AuditarEstruturaApiComando());
        comandos.put("--testar-api", new TestarApiComando());
        comandos.put("--limpar-tabelas", new LimparTabelasComando());
        comandos.put("--verificar-timestamps", new VerificarTimestampsComando());
        comandos.put("--verificar-timezone", new VerificarTimezoneComando());
        comandos.put("--validar-manifestos", new ValidarManifestosComando());
        comandos.put("--validar-dados", new ValidarDadosCompletoComando());
        comandos.put("--validar-api-banco-24h", new ValidarApiVsBanco24hComando());
        comandos.put("--validar-api-banco-24h-detalhado", new ValidarApiVsBanco24hDetalhadoComando());
        comandos.put("--exportar-csv", new ExportarCsvComando());

        comandos.put("--auth-check", new AuthCheckComando());
        comandos.put("--auth-bootstrap", new AuthBootstrapComando());
        comandos.put("--auth-create-user", new AuthCreateUserComando());
        comandos.put("--auth-reset-password", new AuthResetPasswordComando());
        comandos.put("--auth-disable-user", new AuthDisableUserComando());
        comandos.put("--auth-info", new AuthInfoComando());

        comandos.put("--loop-daemon-start", new LoopDaemonComando(LoopDaemonComando.Modo.START));
        comandos.put("--loop-daemon-stop", new LoopDaemonComando(LoopDaemonComando.Modo.STOP));
        comandos.put("--loop-daemon-status", new LoopDaemonComando(LoopDaemonComando.Modo.STATUS));
        comandos.put("--loop-daemon-run", new LoopDaemonComando(LoopDaemonComando.Modo.RUN));
        return comandos;
    }

    public static void main(final String[] args) {
        final String nomeComando = (args.length == 0) ? "--fluxo-completo" : args[0].toLowerCase();
        final String tipoExecucao = nomeComando.startsWith("--") ? nomeComando.substring(2) : nomeComando;

        final boolean comandoLongaDuracao = isComandoLongaDuracao(nomeComando);
        final boolean comandoSilencioso = isComandoSilencioso(nomeComando);
        final boolean capturarLogOperacao = !comandoLongaDuracao && !comandoSilencioso;
        final boolean registrarHistorico = !comandoLongaDuracao && !comandoSilencioso;

        final LoggingService loggingService = capturarLogOperacao ? new LoggingService() : null;
        final LocalDateTime inicioExecucao = LocalDateTime.now();
        final AtomicReference<String> statusRef = new AtomicReference<>("SUCCESS");
        final AtomicReference<String> errorMessageRef = new AtomicReference<>(null);
        final AtomicReference<String> errorCategoryRef = new AtomicReference<>(null);
        final AtomicBoolean historicoRegistrado = new AtomicBoolean(false);
        final AtomicBoolean finalizadoNormalmente = new AtomicBoolean(false);

        final Runnable persistirHistoricoExecucao = () -> {
            if (!registrarHistorico || !historicoRegistrado.compareAndSet(false, true)) {
                return;
            }

            final LocalDateTime fimExecucao = LocalDateTime.now();
            final long duracaoSegundos = Duration.between(inicioExecucao, fimExecucao).getSeconds();
            final int duracaoSegundosInt = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, duracaoSegundos));
            int totalRecords = 0;

            try {
                final ExecutionHistoryRepository repo = new ExecutionHistoryRepository();
                totalRecords = repo.calcularTotalRegistros(inicioExecucao, fimExecucao);
            } catch (final Throwable t) {
                logger.warn("Falha ao calcular total de registros: {}", t.getMessage());
            }

            ExecutionAuditor.registrarCsv(
                fimExecucao,
                statusRef.get(),
                duracaoSegundosInt,
                totalRecords,
                tipoExecucao,
                errorMessageRef.get()
            );

            try {
                final ExecutionHistoryRepository repo = new ExecutionHistoryRepository();
                repo.inserirHistorico(
                    inicioExecucao,
                    fimExecucao,
                    duracaoSegundosInt,
                    statusRef.get(),
                    totalRecords,
                    errorCategoryRef.get(),
                    errorMessageRef.get()
                );
            } catch (final Throwable t) {
                logger.warn("Falha ao gravar historico de execucao no banco: {}", t.getMessage());
            }
        };

        if (loggingService != null) {
            loggingService.iniciarCaptura("extracao_dados");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> loggingService.pararCaptura(statusRef.get())));
        }

        if (registrarHistorico) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (!finalizadoNormalmente.get()) {
                    statusRef.compareAndSet("SUCCESS", "INTERRUPTED");
                    if (errorCategoryRef.get() == null) {
                        errorCategoryRef.set("ShutdownHook");
                    }
                    if (errorMessageRef.get() == null) {
                        errorMessageRef.set("Execucao encerrada antes da finalizacao completa");
                    }
                }
                persistirHistoricoExecucao.run();
            }, "execution-history-shutdown-hook"));
        }

        if (capturarLogOperacao) {
            organizarLogsTxtNaPastaLogs();
        }

        int exitCode = 0;

        try {
            final Comando comando = COMANDOS.getOrDefault(nomeComando, new ExibirAjudaComando());
            if (!COMANDOS.containsKey(nomeComando)) {
                System.err.println("Comando desconhecido: " + nomeComando);
                System.err.println("Use --ajuda para ver os comandos disponiveis.");
            }
            comando.executar(args);
        } catch (final PartialExecutionException e) {
            statusRef.set("PARTIAL");
            errorMessageRef.set(e.getMessage());
            errorCategoryRef.set(e.getClass().getSimpleName());
            exitCode = 2;
            logger.warn("Execucao concluida com falhas parciais: {}", e.getMessage());
            System.err.println("Execucao parcial: " + e.getMessage());
        } catch (final Throwable e) {
            final String mensagem = (e.getMessage() == null || e.getMessage().isBlank())
                ? e.getClass().getSimpleName()
                : e.getMessage();
            statusRef.set("ERROR");
            errorMessageRef.set(mensagem);
            errorCategoryRef.set(e.getClass().getSimpleName());
            exitCode = 1;
            if (!(comandoSilencioso && isErroEsperado(e))) {
                logger.error("Erro durante execucao: {}", mensagem, e);
            }
            System.err.println("Erro durante execucao: " + mensagem);
        } finally {
            persistirHistoricoExecucao.run();
            finalizadoNormalmente.set(true);

            if (loggingService != null) {
                loggingService.pararCaptura(statusRef.get());
            }
        }

        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static void organizarLogsTxtNaPastaLogs() {
        LoggingService.organizarLogsTxtNaPastaLogs();
    }

    private static boolean isComandoLongaDuracao(final String nomeComando) {
        return "--loop".equals(nomeComando) || "--loop-daemon-run".equals(nomeComando);
    }

    private static boolean isComandoSilencioso(final String nomeComando) {
        return nomeComando.startsWith("--auth-")
            || "--ajuda".equals(nomeComando)
            || "--help".equals(nomeComando)
            || "--auditar-api".equals(nomeComando)
            || "--loop-daemon-start".equals(nomeComando)
            || "--loop-daemon-stop".equals(nomeComando)
            || "--loop-daemon-status".equals(nomeComando);
    }

    private static boolean isErroEsperado(final Throwable e) {
        return e instanceof IllegalArgumentException || e instanceof IllegalStateException;
    }
}
