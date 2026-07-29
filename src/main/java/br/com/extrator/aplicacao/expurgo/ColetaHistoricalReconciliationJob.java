package br.com.extrator.aplicacao.expurgo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.aplicacao.extracao.ExecutionLockManager;
import br.com.extrator.dominio.graphql.coletas.ColetaNodeDTO;
import br.com.extrator.integracao.ColetaSnapshotPersistence;
import br.com.extrator.integracao.GraphQLColetaKeySnapshotClient;
import br.com.extrator.persistencia.repositorio.ColetaReconciliationRepository;
import br.com.extrator.suporte.banco.SqlServerExecutionLockManager;

/**
 * Reconciliador histórico de Coletas. Só persiste ou confirma ausências quando
 * o snapshot GraphQL inteiro da janela foi obtido com sucesso.
 */
public final class ColetaHistoricalReconciliationJob {
    private static final Logger logger = LoggerFactory.getLogger(ColetaHistoricalReconciliationJob.class);
    static final String LOCK_RESOURCE = "etl-coletas-historical-reconciliation";

    private final ColetaSourceSnapshotPort source;
    private final ColetaSnapshotPersistencePort persistence;
    private final ColetaReconciliationStore store;
    private final ExecutionLockManager lockManager;

    public ColetaHistoricalReconciliationJob() {
        this(
            new GraphQLColetaKeySnapshotClient(),
            new ColetaSnapshotPersistence(),
            new ColetaReconciliationRepository(),
            new SqlServerExecutionLockManager()
        );
    }

    ColetaHistoricalReconciliationJob(final ColetaSourceSnapshotPort source,
                                      final ColetaSnapshotPersistencePort persistence,
                                      final ColetaReconciliationStore store,
                                      final ExecutionLockManager lockManager) {
        this.source = source;
        this.persistence = persistence;
        this.store = store;
        this.lockManager = lockManager;
    }

    public ColetaHistoricalReconciliationReport executar(final ColetaHistoricalReconciliationRequest request) throws Exception {
        final String runId = UUID.randomUUID().toString();
        final Instant startedAt = Instant.now();
        try (AutoCloseable ignored = lockManager.acquire(LOCK_RESOURCE)) {
            final ColetaSourceSnapshot snapshot = source.carregar(request.dataInicio(), request.dataFim());
            if (!snapshot.complete()) {
                throw new IllegalStateException(
                    "Snapshot GraphQL de Coletas incompleto; reconciliação abortada sem persistir ou excluir. Motivo="
                        + snapshot.interruptionReason()
                );
            }

            final Set<String> sourceKeys = extractKeysOrAbort(snapshot.records());
            final Set<String> dbActiveKeys = store.buscarIdsAtivos(request.dataInicio(), request.dataFim());
            if (sourceKeys.isEmpty() && !dbActiveKeys.isEmpty()) {
                throw new IllegalStateException(
                    "Snapshot GraphQL de Coletas retornou zero chaves com " + dbActiveKeys.size()
                        + " registros ativos no banco; reconciliação abortada para evitar falso positivo."
                );
            }

            final int persisted = request.dryRun() ? 0 : persistence.persistir(snapshot.records());
            final List<String> missing = dbActiveKeys.stream()
                .filter(id -> !sourceKeys.contains(id))
                .sorted()
                .toList();
            final ColetaMissingRegistration registration = request.dryRun()
                ? new ColetaMissingRegistration(0, 0)
                : store.registrarAusencias(
                    missing,
                    runId,
                    request.confirmacoesAusenciaNecessarias(),
                    request.batchSize()
                );

            final Duration duration = Duration.between(startedAt, Instant.now());
            logger.info(
                "Reconciliação histórica de Coletas concluída | run_id={} | periodo={}..{} | source_keys={} | db_ativas={} | ausentes={} | candidatos_registrados={} | excluidas={} | dry_run={}",
                runId,
                request.dataInicio(),
                request.dataFim(),
                sourceKeys.size(),
                dbActiveKeys.size(),
                missing.size(),
                registration.markedMissing(),
                registration.logicallyExcluded(),
                request.dryRun()
            );
            return new ColetaHistoricalReconciliationReport(
                runId,
                request.dataInicio(),
                request.dataFim(),
                request.dryRun(),
                snapshot.rowsRead(),
                sourceKeys.size(),
                dbActiveKeys.size(),
                persisted,
                missing.size(),
                registration.logicallyExcluded(),
                snapshot.pagesProcessed(),
                duration
            );
        }
    }

    private Set<String> extractKeysOrAbort(final List<ColetaNodeDTO> records) {
        final Set<String> keys = new LinkedHashSet<>();
        final List<String> invalid = new ArrayList<>();
        for (final ColetaNodeDTO record : records) {
            if (record == null || record.getId() == null || record.getId().isBlank()) {
                invalid.add(record == null ? "null" : String.valueOf(record.getSequenceCode()));
                continue;
            }
            keys.add(record.getId().trim());
        }
        if (!invalid.isEmpty()) {
            throw new IllegalStateException(
                "Snapshot GraphQL de Coletas possui " + invalid.size()
                    + " registro(s) sem id; reconciliação abortada para evitar exclusões indevidas."
            );
        }
        return keys;
    }
}
