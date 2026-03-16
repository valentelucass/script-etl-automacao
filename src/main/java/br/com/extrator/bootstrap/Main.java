/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/Main.java
Classe  : Main (class)
Pacote  : br.com.extrator
Modulo  : Orquestrador principal
Papel   : Orquestra a execucao da aplicacao e despacho dos comandos CLI.

Conecta com:
- ExecutionAuditor (auditoria.execucao)
- CommandRegistry (comandos)
- Comando (comandos.base)
- ExibirAjudaComando (comandos.console)
- PartialExecutionException (aplicacao.extracao)
- ExecutionHistoryRepository (db.repository)
- LoggingService (servicos)

Fluxo geral:
1) Interpreta argumentos e seleciona comando de execucao.
2) Coordena log operacional e persistencia de historico.
3) Centraliza tratamento de erros e codigo de saida.

Estrutura interna:
Metodos principais:
- main(...1 args): ponto de entrada da execucao.
- organizarLogsTxtNaPastaLogs(): realiza operacao relacionada a "organizar logs txt na pasta logs".
- isComandoLongaDuracao(...1 args): retorna estado booleano de controle.
- isComandoSilencioso(...1 args): retorna estado booleano de controle.
- isErroEsperado(...1 args): retorna estado booleano de controle.
Atributos-chave:
- logger: logger da classe para diagnostico.
- COMANDOS: campo de estado para "comandos".
[DOC-FILE-END]============================================================== */

package br.com.extrator.bootstrap;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.aplicacao.extracao.PartialExecutionException;
import br.com.extrator.bootstrap.pipeline.PipelineCompositionRoot;
import br.com.extrator.observabilidade.execucao.ExecutionAuditor;
import br.com.extrator.comandos.cli.CommandRegistry;
import br.com.extrator.comandos.cli.base.Comando;
import br.com.extrator.comandos.cli.console.ExibirAjudaComando;
import br.com.extrator.persistencia.repositorio.ExecutionHistoryRepository;
import br.com.extrator.persistencia.repositorio.HistoryPersistenceInterruptedException;
import br.com.extrator.observabilidade.LoggingService;
import br.com.extrator.suporte.banco.GerenciadorConexao;
import br.com.extrator.suporte.log.SensitiveDataSanitizer;
import br.com.extrator.suporte.observabilidade.ExecutionContext;
import br.com.extrator.suporte.tempo.RelogioSistema;

/**
 * Orquestrador principal dos comandos do extrator.
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final Path ARQUIVO_FALLBACK_HISTORICO = Path.of("logs", "execution_history_fallback.ndjson");
    private static final AtomicBoolean CONTEXTO_INICIALIZADO = new AtomicBoolean(false);
    private static final Map<String, Comando> COMANDOS = CommandRegistry.criarMapaComandos();

    public static void main(final String[] args) {
        final String nomeComando = (args.length == 0) ? "--fluxo-completo" : args[0].toLowerCase();
        final String tipoExecucao = nomeComando.startsWith("--") ? nomeComando.substring(2) : nomeComando;
        final String executionId = ExecutionContext.initialize(nomeComando);
        logger.info("Iniciando execucao | comando={} | execution_id={}", nomeComando, executionId);

        final boolean comandoLongaDuracao = isComandoLongaDuracao(nomeComando);
        final boolean comandoSilencioso = isComandoSilencioso(nomeComando);
        final boolean capturarLogOperacao = !comandoLongaDuracao && !comandoSilencioso;
        final boolean registrarHistorico = !comandoLongaDuracao && !comandoSilencioso;

        final LoggingService loggingService = capturarLogOperacao ? new LoggingService() : null;
        final LocalDateTime inicioExecucao = RelogioSistema.agora();
        final AtomicReference<String> statusRef = new AtomicReference<>("SUCCESS");
        final AtomicReference<String> errorMessageRef = new AtomicReference<>(null);
        final AtomicReference<String> errorCategoryRef = new AtomicReference<>(null);
        final AtomicBoolean historicoRegistrado = new AtomicBoolean(false);
        final AtomicBoolean finalizadoNormalmente = new AtomicBoolean(false);
        final ExecutionHistoryRepository executionHistoryRepository =
            registrarHistorico ? new ExecutionHistoryRepository() : null;

        final Runnable persistirHistoricoExecucao = () -> {
            if (!registrarHistorico || !historicoRegistrado.compareAndSet(false, true)) {
                return;
            }

            final LocalDateTime fimExecucao = RelogioSistema.agora();
            final long duracaoMillis = Math.max(0L, Duration.between(inicioExecucao, fimExecucao).toMillis());
            final long duracaoSegundos = duracaoMillis == 0L ? 0L : (duracaoMillis + 999L) / 1000L;
            final int duracaoSegundosInt = (int) Math.min(Integer.MAX_VALUE, duracaoSegundos);
            int totalRecords = 0;

            try {
                totalRecords = executionHistoryRepository.calcularTotalRegistros(inicioExecucao, fimExecucao);
            } catch (final Throwable t) {
                rethrowIfFatal(t);
                logger.warn("Falha ao calcular total de registros: {}", sanitizeMessage(t.getMessage()));
            }

            ExecutionAuditor.registrarCsv(
                fimExecucao,
                statusRef.get(),
                duracaoSegundosInt,
                totalRecords,
                tipoExecucao,
                errorMessageRef.get()
            );

            Throwable ultimoErroPersistencia = null;
            boolean historicoPersistidoNoBanco = false;

            try {
                executionHistoryRepository.inserirHistorico(
                    inicioExecucao,
                    fimExecucao,
                    duracaoSegundosInt,
                    statusRef.get(),
                    totalRecords,
                    errorCategoryRef.get(),
                    errorMessageRef.get()
                );
                logger.info(
                    "EVT_EXEC_HISTORY_DB_SAVE_OK status={} tipo_execucao={} inicio={} fim={} total_records={}",
                    statusRef.get(),
                    tipoExecucao,
                    inicioExecucao,
                    fimExecucao,
                    totalRecords
                );
                historicoPersistidoNoBanco = true;
            } catch (final Throwable t) {
                rethrowIfFatal(t);
                ultimoErroPersistencia = t;
                if (t instanceof HistoryPersistenceInterruptedException) {
                    Thread.currentThread().interrupt();
                }
                logger.warn(
                    "EVT_EXEC_HISTORY_DB_SAVE_FAIL status={} tipo_execucao={} erro={}",
                    statusRef.get(),
                    tipoExecucao,
                    sanitizeMessage(t.getMessage())
                );
            }

            if (!historicoPersistidoNoBanco) {
                registrarFallbackHistorico(
                    inicioExecucao,
                    fimExecucao,
                    duracaoSegundosInt,
                    statusRef.get(),
                    totalRecords,
                    tipoExecucao,
                    errorCategoryRef.get(),
                    errorMessageRef.get(),
                    ultimoErroPersistencia
                );
            }
        };

        if (loggingService != null) {
            loggingService.iniciarCaptura("extracao_dados");
            Runtime.getRuntime().addShutdownHook(
                new Thread(
                    ExecutionContext.wrapRunnable(() -> loggingService.pararCaptura(statusRef.get())),
                    "logging-shutdown-hook"
                )
            );
        }

        if (registrarHistorico) {
            Runtime.getRuntime().addShutdownHook(new Thread(ExecutionContext.wrapRunnable(() -> {
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
            }), "execution-history-shutdown-hook"));
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
            } else {
                inicializarContextoSeNecessario(nomeComando);
            }
            comando.executar(args);
        } catch (final PartialExecutionException e) {
            statusRef.set("PARTIAL");
            final String mensagem = sanitizeMessage(e.getMessage());
            errorMessageRef.set(mensagem);
            errorCategoryRef.set(e.getClass().getSimpleName());
            exitCode = 2;
            logger.warn("Execucao concluida com falhas parciais: {}", mensagem);
            System.err.println("Execucao parcial: " + mensagem);
        } catch (final Error e) {
            logger.error("Erro irrecuperavel durante execucao: {}", e.getMessage(), e);
            throw e;
        } catch (final Exception e) {
            final String mensagem = (e.getMessage() == null || e.getMessage().isBlank())
                ? e.getClass().getSimpleName()
                : sanitizeMessage(e.getMessage());
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
            GerenciadorConexao.fecharPool();

            if (loggingService != null) {
                loggingService.pararCaptura(statusRef.get());
            }
            ExecutionContext.clear();
        }

        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static void organizarLogsTxtNaPastaLogs() {
        LoggingService.organizarLogsTxtNaPastaLogs();
    }

    private static void rethrowIfFatal(final Throwable throwable) {
        if (throwable instanceof VirtualMachineError virtualMachineError) {
            throw virtualMachineError;
        }
        if (throwable instanceof ThreadDeath threadDeath) {
            throw threadDeath;
        }
    }

    private static boolean isComandoLongaDuracao(final String nomeComando) {
        return "--loop".equals(nomeComando) || "--loop-daemon-run".equals(nomeComando);
    }

    static boolean requerInicializacaoContexto(final String nomeComando) {
        if (nomeComando == null) {
            return false;
        }
        return switch (nomeComando.toLowerCase()) {
            case "--fluxo-completo",
                "--extracao-intervalo",
                "--recovery",
                "--loop",
                "--auditar-api",
                "--testar-api",
                "--validar-api-banco-24h",
                "--validar-api-banco-24h-detalhado",
                "--validar-etl-extremo",
                "--loop-daemon-run" -> true;
            default -> false;
        };
    }

    private static void inicializarContextoSeNecessario(final String nomeComando) {
        if (!requerInicializacaoContexto(nomeComando)) {
            return;
        }
        if (CONTEXTO_INICIALIZADO.compareAndSet(false, true)) {
            PipelineCompositionRoot.inicializarContexto();
        }
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

    private static boolean isErroEsperado(final Exception e) {
        return e instanceof IllegalArgumentException || e instanceof IllegalStateException;
    }

    private static void registrarFallbackHistorico(final LocalDateTime inicioExecucao,
                                                   final LocalDateTime fimExecucao,
                                                   final int duracaoSegundos,
                                                   final String status,
                                                   final int totalRecords,
                                                   final String tipoExecucao,
                                                   final String categoriaErro,
                                                   final String mensagemErro,
                                                   final Throwable erroPersistencia) {
        final String linhaJson = "{"
            + "\"event_code\":\"EVT_EXEC_HISTORY_DB_FALLBACK\","
            + "\"timestamp\":\"" + escapeJson(RelogioSistema.agora().toString()) + "\","
            + "\"inicio_execucao\":\"" + escapeJson(String.valueOf(inicioExecucao)) + "\","
            + "\"fim_execucao\":\"" + escapeJson(String.valueOf(fimExecucao)) + "\","
            + "\"duracao_segundos\":" + duracaoSegundos + ","
            + "\"status\":\"" + escapeJson(status) + "\","
            + "\"total_records\":" + totalRecords + ","
            + "\"tipo_execucao\":\"" + escapeJson(tipoExecucao) + "\","
            + "\"categoria_erro\":\"" + escapeJson(categoriaErro) + "\","
            + "\"mensagem_erro\":\"" + escapeJson(mensagemErro) + "\","
            + "\"erro_persistencia\":\""
            + escapeJson(erroPersistencia == null ? null : sanitizeMessage(erroPersistencia.getMessage()))
            + "\""
            + "}"
            + System.lineSeparator();

        try {
            final Path diretorio = ARQUIVO_FALLBACK_HISTORICO.getParent();
            if (diretorio != null) {
                Files.createDirectories(diretorio);
            }
            Files.writeString(
                ARQUIVO_FALLBACK_HISTORICO,
                linhaJson,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
            logger.error(
                "EVT_EXEC_HISTORY_DB_FALLBACK_WRITTEN arquivo={} status={} tipo_execucao={} erro={}",
                ARQUIVO_FALLBACK_HISTORICO.toAbsolutePath(),
                status,
                tipoExecucao,
                erroPersistencia == null ? "<desconhecido>" : sanitizeMessage(erroPersistencia.getMessage())
            );
        } catch (final IOException io) {
            logger.error(
                "EVT_EXEC_HISTORY_DB_FALLBACK_WRITE_FAIL arquivo={} erro_original={} erro_fallback={}",
                ARQUIVO_FALLBACK_HISTORICO.toAbsolutePath(),
                erroPersistencia == null ? "<desconhecido>" : sanitizeMessage(erroPersistencia.getMessage()),
                sanitizeMessage(io.getMessage()),
                io
            );
        }
    }

    private static String escapeJson(final String valor) {
        if (valor == null) {
            return "";
        }
        return valor
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }

    private static String sanitizeMessage(final String mensagem) {
        return SensitiveDataSanitizer.sanitize(mensagem);
    }
}
