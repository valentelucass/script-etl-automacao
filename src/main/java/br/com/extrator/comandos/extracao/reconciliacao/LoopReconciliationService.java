/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/extracao/reconciliacao/LoopReconciliationService.java
Classe  : LoopReconciliationService (class)
Pacote  : br.com.extrator.comandos.extracao.reconciliacao
Modulo  : Comando CLI (extracao)
Papel   : Implementa responsabilidade de loop reconciliation service.

Conecta com:
- ExecutarExtracaoPorIntervaloComando (comandos.extracao)
- CarregadorConfig (util.configuracao)

Fluxo geral:
1) Interpreta parametros e escopo de extracao.
2) Dispara runners/extratores conforme alvo.
3) Consolida status final e tratamento de falhas.

Estrutura interna:
Metodos principais:
- LoopReconciliationService(...6 args): realiza operacao relacionada a "loop reconciliation service".
- criarPadrao(...1 args): instancia ou monta estrutura de dados.
- processarPosCiclo(...4 args): realiza operacao relacionada a "processar pos ciclo".
- agendarPendenciasPorFalha(...3 args): realiza operacao relacionada a "agendar pendencias por falha".
- carregarEstado(): realiza operacao relacionada a "carregar estado".
- salvarEstado(...2 args): persiste dados em armazenamento.
- parseData(...1 args): realiza operacao relacionada a "parse data".
- toStringDate(...1 args): realiza operacao relacionada a "to string date".
- maiorData(...2 args): realiza operacao relacionada a "maior data".
- resumirMensagem(...1 args): realiza operacao relacionada a "resumir mensagem".
Atributos-chave:
- logger: logger da classe para diagnostico.
- KEY_LAST_DAILY_SCHEDULED_DATE: campo de estado para "key last daily scheduled date".
- KEY_LAST_SUCCESSFUL_RECONCILIATION_DATE: campo de estado para "key last successful reconciliation date".
- KEY_PENDING_DATES: campo de estado para "key pending dates".
- KEY_LAST_ERROR: campo de estado para "key last error".
- KEY_UPDATED_AT: campo de estado para "key updated at".
- stateFile: campo de estado para "state file".
- clock: campo de estado para "clock".
- ativo: campo de estado para "ativo".
- maxTentativasPorCiclo: campo de estado para "max tentativas por ciclo".
- diasRetroativosFalha: campo de estado para "dias retroativos falha".
- executor: campo de estado para "executor".
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.extracao.reconciliacao;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.comandos.extracao.ExecutarExtracaoPorIntervaloComando;
import br.com.extrator.util.configuracao.CarregadorConfig;

/**
 * Orquestra a reconciliacao automatica do loop daemon.
 *
 * Regras aplicadas:
 * - Agenda reconciliacao diaria para D-1 (uma vez por dia).
 * - Em falha de ciclo, adiciona janelas retroativas para reprocessamento.
 * - Reexecuta pendencias com limite de tentativas por ciclo.
 */
public final class LoopReconciliationService {
    private static final Logger logger = LoggerFactory.getLogger(LoopReconciliationService.class);

    private static final String KEY_LAST_DAILY_SCHEDULED_DATE = "last_daily_scheduled_date";
    private static final String KEY_LAST_SUCCESSFUL_RECONCILIATION_DATE = "last_successful_reconciliation_date";
    private static final String KEY_PENDING_DATES = "pending_dates";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_UPDATED_AT = "updated_at";

    @FunctionalInterface
    public interface ReconciliationExecutor {
        void execute(LocalDate data, boolean incluirFaturasGraphQL) throws Exception;
    }

    private final Path stateFile;
    private final Clock clock;
    private final boolean ativo;
    private final int maxTentativasPorCiclo;
    private final int diasRetroativosFalha;
    private final ReconciliationExecutor executor;

    public LoopReconciliationService(final Path stateFile,
                                     final Clock clock,
                                     final boolean ativo,
                                     final int maxTentativasPorCiclo,
                                     final int diasRetroativosFalha,
                                     final ReconciliationExecutor executor) {
        this.stateFile = Objects.requireNonNull(stateFile, "stateFile nao pode ser null");
        this.clock = Objects.requireNonNull(clock, "clock nao pode ser null");
        this.ativo = ativo;
        this.maxTentativasPorCiclo = Math.max(1, maxTentativasPorCiclo);
        this.diasRetroativosFalha = Math.max(0, diasRetroativosFalha);
        this.executor = Objects.requireNonNull(executor, "executor nao pode ser null");
    }

    public static LoopReconciliationService criarPadrao(final Path stateFile) {
        return new LoopReconciliationService(
            stateFile,
            Clock.systemDefaultZone(),
            CarregadorConfig.isLoopReconciliacaoAtiva(),
            CarregadorConfig.obterLoopReconciliacaoMaxPorCiclo(),
            CarregadorConfig.obterLoopReconciliacaoDiasRetroativosFalha(),
            LoopReconciliationService::executarReconciliacaoPadrao
        );
    }

    private static void executarReconciliacaoPadrao(final LocalDate data, final boolean incluirFaturasGraphQL) throws Exception {
        final List<String> args = new ArrayList<>();
        args.add("--extracao-intervalo");
        args.add(data.toString());
        args.add(data.toString());
        args.add("--modo-loop-daemon");
        if (!incluirFaturasGraphQL) {
            args.add("--sem-faturas-graphql");
        }
        new ExecutarExtracaoPorIntervaloComando().executar(args.toArray(String[]::new));
    }

    public ReconciliationSummary processarPosCiclo(final LocalDateTime inicioCiclo,
                                                   final LocalDateTime fimCiclo,
                                                   final boolean cicloSucesso,
                                                   final boolean incluirFaturasGraphQL) {
        if (!ativo) {
            return ReconciliationSummary.inativo();
        }

        final ReconciliationState estado = carregarEstado();
        final LocalDate hoje = LocalDate.now(clock);
        final LocalDate ontem = hoje.minusDays(1);

        boolean agendouDiaria = false;
        boolean adicionouPorFalha = false;
        int tentativas = 0;
        int executadas = 0;
        int falhas = 0;
        final List<String> detalhesFalha = new ArrayList<>();

        if (estado.lastDailyScheduledDate == null || estado.lastDailyScheduledDate.isBefore(ontem)) {
            estado.pendingDates.add(ontem);
            estado.lastDailyScheduledDate = ontem;
            agendouDiaria = true;
            logger.info("Reconciliacao diaria agendada para {}", ontem);
        }

        if (!cicloSucesso) {
            adicionouPorFalha = agendarPendenciasPorFalha(estado, inicioCiclo, fimCiclo);
        }

        final List<LocalDate> pendenciasOrdenadas = estado.pendingDates.stream()
            .filter(data -> !data.isAfter(hoje))
            .sorted()
            .toList();

        for (final LocalDate dataPendente : pendenciasOrdenadas) {
            if (tentativas >= maxTentativasPorCiclo) {
                break;
            }
            tentativas++;
            try {
                logger.info("Iniciando reconciliacao automatica para {}", dataPendente);
                executor.execute(dataPendente, incluirFaturasGraphQL);
                estado.pendingDates.remove(dataPendente);
                estado.lastSuccessfulReconciliationDate = maiorData(estado.lastSuccessfulReconciliationDate, dataPendente);
                executadas++;
                logger.info("Reconciliacao concluida para {}", dataPendente);
            } catch (final Exception e) {
                falhas++;
                final String detalhe = dataPendente + ": " + resumirMensagem(e.getMessage());
                detalhesFalha.add(detalhe);
                logger.error("Falha na reconciliacao automatica para {}: {}", dataPendente, e.getMessage(), e);
            }
        }

        salvarEstado(estado, detalhesFalha);

        final List<LocalDate> pendenciasRestantes = estado.pendingDates.stream()
            .sorted()
            .toList();

        return new ReconciliationSummary(
            true,
            executadas,
            falhas,
            pendenciasRestantes,
            detalhesFalha,
            agendouDiaria,
            adicionouPorFalha
        );
    }

    private boolean agendarPendenciasPorFalha(final ReconciliationState estado,
                                              final LocalDateTime inicioCiclo,
                                              final LocalDateTime fimCiclo) {
        LocalDate inicioPendencia = inicioCiclo.toLocalDate().minusDays(diasRetroativosFalha);
        final LocalDate fimPendencia = fimCiclo.toLocalDate();
        if (inicioPendencia.isAfter(fimPendencia)) {
            inicioPendencia = fimPendencia;
        }

        boolean adicionou = false;
        LocalDate atual = inicioPendencia;
        while (!atual.isAfter(fimPendencia)) {
            if (estado.pendingDates.add(atual)) {
                adicionou = true;
            }
            atual = atual.plusDays(1);
        }

        if (adicionou) {
            logger.warn("Pendencias de reconciliacao adicionadas por falha de ciclo: {} ate {}", inicioPendencia, fimPendencia);
        }
        return adicionou;
    }

    private ReconciliationState carregarEstado() {
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

        estado.lastDailyScheduledDate = parseData(properties.getProperty(KEY_LAST_DAILY_SCHEDULED_DATE));
        estado.lastSuccessfulReconciliationDate = parseData(properties.getProperty(KEY_LAST_SUCCESSFUL_RECONCILIATION_DATE));

        final String pendencias = properties.getProperty(KEY_PENDING_DATES, "");
        if (!pendencias.isBlank()) {
            for (final String token : pendencias.split(",")) {
                final LocalDate data = parseData(token);
                if (data != null) {
                    estado.pendingDates.add(data);
                }
            }
        }

        return estado;
    }

    private void salvarEstado(final ReconciliationState estado, final List<String> detalhesFalha) {
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

    private LocalDate parseData(final String valor) {
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

    private LocalDate maiorData(final LocalDate atual, final LocalDate candidato) {
        if (atual == null) {
            return candidato;
        }
        return candidato.isAfter(atual) ? candidato : atual;
    }

    private String resumirMensagem(final String mensagem) {
        if (mensagem == null || mensagem.isBlank()) {
            return "sem detalhes";
        }
        final String limpa = mensagem.replace('\n', ' ').replace('\r', ' ').trim();
        return limpa.length() > 180 ? limpa.substring(0, 180) + "..." : limpa;
    }

    private static final class ReconciliationState {
        private LocalDate lastDailyScheduledDate;
        private LocalDate lastSuccessfulReconciliationDate;
        private final Set<LocalDate> pendingDates = new LinkedHashSet<>();
    }

    public static final class ReconciliationSummary {
        private final boolean ativo;
        private final int reconciliacoesExecutadas;
        private final int falhas;
        private final List<LocalDate> pendenciasRestantes;
        private final List<String> detalhesFalha;
        private final boolean agendouReconciliacaoDiaria;
        private final boolean pendenciaPorFalha;

        private ReconciliationSummary(final boolean ativo,
                                      final int reconciliacoesExecutadas,
                                      final int falhas,
                                      final List<LocalDate> pendenciasRestantes,
                                      final List<String> detalhesFalha,
                                      final boolean agendouReconciliacaoDiaria,
                                      final boolean pendenciaPorFalha) {
            this.ativo = ativo;
            this.reconciliacoesExecutadas = reconciliacoesExecutadas;
            this.falhas = falhas;
            this.pendenciasRestantes = List.copyOf(pendenciasRestantes);
            this.detalhesFalha = List.copyOf(detalhesFalha);
            this.agendouReconciliacaoDiaria = agendouReconciliacaoDiaria;
            this.pendenciaPorFalha = pendenciaPorFalha;
        }

        private static ReconciliationSummary inativo() {
            return new ReconciliationSummary(false, 0, 0, List.of(), List.of(), false, false);
        }

        public boolean isAtivo() {
            return ativo;
        }

        public int getReconciliacoesExecutadas() {
            return reconciliacoesExecutadas;
        }

        public int getFalhas() {
            return falhas;
        }

        public List<LocalDate> getPendenciasRestantes() {
            return pendenciasRestantes;
        }

        public List<String> getDetalhesFalha() {
            return detalhesFalha;
        }

        public boolean isAgendouReconciliacaoDiaria() {
            return agendouReconciliacaoDiaria;
        }

        public boolean isPendenciaPorFalha() {
            return pendenciaPorFalha;
        }
    }
}
