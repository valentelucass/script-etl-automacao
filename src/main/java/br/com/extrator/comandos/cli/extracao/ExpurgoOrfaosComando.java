package br.com.extrator.comandos.cli.extracao;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import br.com.extrator.aplicacao.expurgo.ColetaHistoricalReconciliationJob;
import br.com.extrator.aplicacao.expurgo.ColetaHistoricalReconciliationReport;
import br.com.extrator.aplicacao.expurgo.ColetaHistoricalReconciliationRequest;
import br.com.extrator.aplicacao.expurgo.EntityReconciliationSpec;
import br.com.extrator.aplicacao.expurgo.EntityReconciliationSpecs;
import br.com.extrator.aplicacao.expurgo.OrphanReconciliationEntityReport;
import br.com.extrator.aplicacao.expurgo.OrphanReconciliationJob;
import br.com.extrator.aplicacao.expurgo.OrphanReconciliationReport;
import br.com.extrator.aplicacao.expurgo.OrphanReconciliationRequest;
import br.com.extrator.comandos.cli.base.Comando;
import br.com.extrator.suporte.configuracao.ConfigEtl;
import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.tempo.RelogioSistema;

public class ExpurgoOrfaosComando implements Comando {
    private static final LoggerConsole log = LoggerConsole.getLogger(ExpurgoOrfaosComando.class);

    private final OrphanReconciliationJob job;
    private final ColetaHistoricalReconciliationJob coletaJob;

    public ExpurgoOrfaosComando() {
        this(new OrphanReconciliationJob(), new ColetaHistoricalReconciliationJob());
    }

    ExpurgoOrfaosComando(final OrphanReconciliationJob job) {
        this(job, new ColetaHistoricalReconciliationJob());
    }

    ExpurgoOrfaosComando(final OrphanReconciliationJob job,
                         final ColetaHistoricalReconciliationJob coletaJob) {
        this.job = job;
        this.coletaJob = coletaJob;
    }

    @Override
    public void executar(final String[] args) throws Exception {
        final Opcoes opcoes = parseArgs(args);
        final boolean incluirColetas = opcoes.entidades.isEmpty()
            || opcoes.entidades.stream().anyMatch(this::ehColetas);
        final List<String> entidadesDataExport = opcoes.entidades.stream()
            .filter(entidade -> !ehColetas(entidade))
            .toList();

        if (opcoes.entidades.isEmpty() || !entidadesDataExport.isEmpty()) {
            final List<EntityReconciliationSpec> specs = EntityReconciliationSpecs.resolverDataExport(entidadesDataExport);
            final OrphanReconciliationRequest request = new OrphanReconciliationRequest(
                opcoes.dataInicio,
                opcoes.dataFim,
                specs,
                opcoes.dryRun,
                opcoes.batchSize
            );
            imprimirResumo(job.executar(request));
        }

        if (incluirColetas) {
            final LocalDate inicioColetas = opcoes.periodoInformado
                ? opcoes.dataInicio
                : RelogioSistema.hoje().minusDays(ConfigEtl.obterColetasReconciliacaoDias() - 1L);
            final LocalDate fimColetas = opcoes.periodoInformado ? opcoes.dataFim : RelogioSistema.hoje();
            final ColetaHistoricalReconciliationReport report = coletaJob.executar(
                new ColetaHistoricalReconciliationRequest(
                    inicioColetas,
                    fimColetas,
                    opcoes.dryRun,
                    opcoes.batchSize,
                    ConfigEtl.obterColetasReconciliacaoConfirmacoesAusencia()
                )
            );
            imprimirResumoColetas(report);
        }
    }

    private Opcoes parseArgs(final String[] args) {
        final Opcoes opcoes = new Opcoes();
        for (int i = 1; args != null && i < args.length; i++) {
            final String arg = args[i];
            if ("--dry-run".equalsIgnoreCase(arg)) {
                opcoes.dryRun = true;
                continue;
            }
            if ("--periodo".equalsIgnoreCase(arg)) {
                if (i + 2 >= args.length) {
                    throw new IllegalArgumentException("Uso: --expurgo-orfaos --periodo YYYY-MM-DD YYYY-MM-DD");
                }
                opcoes.dataInicio = LocalDate.parse(args[++i]);
                opcoes.dataFim = LocalDate.parse(args[++i]);
                opcoes.periodoInformado = true;
                continue;
            }
            if ("--entidade".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Uso: --expurgo-orfaos --entidade coletas|faturas_por_cliente|manifestos");
                }
                adicionarEntidades(opcoes.entidades, args[++i]);
                continue;
            }
            if ("--batch-size".equalsIgnoreCase(arg)) {
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Uso: --expurgo-orfaos --batch-size 500");
                }
                opcoes.batchSize = Integer.parseInt(args[++i]);
                if (opcoes.batchSize <= 0) {
                    throw new IllegalArgumentException("--batch-size deve ser maior que zero");
                }
                continue;
            }
            throw new IllegalArgumentException("Parametro desconhecido para --expurgo-orfaos: " + arg);
        }
        return opcoes;
    }

    private void adicionarEntidades(final List<String> entidades, final String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        for (final String entidade : valor.split(",")) {
            if (!entidade.isBlank()) {
                entidades.add(entidade.trim());
            }
        }
    }

    private void imprimirResumo(final OrphanReconciliationReport report) {
        log.console("Expurgo logico de orfaos concluido");
        log.console("Run ID: {}", report.runId());
        log.console("Periodo API: {} a {}", report.dataInicio(), report.dataFim());
        log.console("Dry-run: {}", report.dryRun());
        for (final OrphanReconciliationEntityReport entity : report.entities()) {
            log.console(
                "  {} | api_keys={} | db_ativas={} | orfaos={} | atualizados={}",
                entity.entityName(),
                entity.apiKeyCount(),
                entity.dbActiveKeyCount(),
                entity.orphanCount(),
                entity.updatedCount()
            );
        }
        log.console(
            "Totais | orfaos={} | atualizados={} | duracao_ms={}",
            report.totalOrphans(),
            report.totalUpdated(),
            report.duration().toMillis()
        );
    }

    private void imprimirResumoColetas(final ColetaHistoricalReconciliationReport report) {
        log.console(
            "Reconciliação histórica de Coletas | run_id={} | periodo={} a {} | dry_run={}",
            report.runId(), report.dataInicio(), report.dataFim(), report.dryRun()
        );
        log.console(
            "  source_rows={} | source_keys={} | db_ativas={} | persistidos={} | ausentes={} | excluidas={} | paginas={}",
            report.sourceRows(), report.sourceKeys(), report.dbActiveKeys(), report.recordsPersisted(),
            report.missingCandidates(), report.logicallyExcluded(), report.pagesProcessed()
        );
    }

    private boolean ehColetas(final String entidade) {
        return entidade != null && "coletas".equals(entidade.trim().toLowerCase(Locale.ROOT));
    }

    private static final class Opcoes {
        private final List<String> entidades = new ArrayList<>();
        private LocalDate dataInicio = RelogioSistema.hoje().minusDays(1);
        private LocalDate dataFim = RelogioSistema.hoje();
        private boolean periodoInformado;
        private boolean dryRun;
        private int batchSize = ConfigEtl.obterOrphanReconciliationBatchSize();
    }
}
