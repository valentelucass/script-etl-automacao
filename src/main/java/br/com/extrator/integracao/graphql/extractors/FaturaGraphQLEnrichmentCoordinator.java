package br.com.extrator.integracao.graphql.extractors;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/graphql/extractors/FaturaGraphQLEnrichmentCoordinator.java
Classe  :  (class)
Pacote  : br.com.extrator.integracao.graphql.extractors
Modulo  : Integracao - GraphQL Extrator
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import br.com.extrator.integracao.ClienteApiGraphQL;
import br.com.extrator.persistencia.entidade.FaturaGraphQLEntity;
import br.com.extrator.dominio.graphql.faturas.CreditCustomerBillingNodeDTO;
import br.com.extrator.suporte.ThreadUtil;
import br.com.extrator.suporte.configuracao.ConfigEtl;
import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.observabilidade.ExecutionContext;

final class FaturaGraphQLEnrichmentCoordinator {
    private static final EnriquecimentoTask POISON_PILL = new EnriquecimentoTask(-1L, Optional.empty());

    private final ClienteApiGraphQL apiClient;
    private final LoggerConsole log;
    private final AtomicInteger totalProcessadas = new AtomicInteger(0);
    private final AtomicInteger totalEnriquecidas = new AtomicInteger(0);
    private final AtomicInteger totalComErro = new AtomicInteger(0);
    private final AtomicInteger errosConsecutivos = new AtomicInteger(0);
    private final AtomicLong ultimoLogTimestamp = new AtomicLong(0);

    private record EnriquecimentoTask(
        Long faturaId,
        Optional<CreditCustomerBillingNodeDTO> resultado
    ) {}

    FaturaGraphQLEnrichmentCoordinator(final ClienteApiGraphQL apiClient, final LoggerConsole log) {
        this.apiClient = apiClient;
        this.log = log;
    }

    void executar(final Set<Long> faturasParaEnriquecer,
                  final Map<Long, FaturaGraphQLEntity> faturasUnicas,
                  final Set<Integer> idsBancos,
                  final Map<Long, Integer> faturaIdParaBancoId) {
        final int totalParaEnriquecer = faturasParaEnriquecer.size();
        if (totalParaEnriquecer <= 0) {
            return;
        }

        totalProcessadas.set(0);
        totalEnriquecidas.set(0);
        totalComErro.set(0);
        errosConsecutivos.set(0);
        ultimoLogTimestamp.set(System.currentTimeMillis());

        final Instant inicioEnriquecimento = Instant.now();
        final int numThreadsConsumidoras = ConfigEtl.obterThreadsProcessamentoFaturas();
        final int intervaloLogProgresso = ConfigEtl.obterIntervaloLogProgressoEnriquecimento();
        final int limiteErrosConsecutivos = ConfigEtl.obterLimiteErrosConsecutivos();
        final int heartbeatSegundos = ConfigEtl.obterHeartbeatSegundos();

        log.info("Iniciando enriquecimento GraphQL com produtor/consumidor");
        log.info("  Produtora HTTP: 1 | Consumidoras: {}", numThreadsConsumidoras);
        log.info("  Log de progresso a cada {} faturas | Heartbeat a cada {}s",
            intervaloLogProgresso,
            heartbeatSegundos);

        final BlockingQueue<EnriquecimentoTask> fila = new LinkedBlockingQueue<>(100);
        final Thread threadProdutora = criarThreadProdutora(
            faturasParaEnriquecer,
            fila,
            numThreadsConsumidoras,
            limiteErrosConsecutivos
        );
        final ExecutorService executorConsumidores = Executors.newFixedThreadPool(numThreadsConsumidoras);

        iniciarConsumidores(
            executorConsumidores,
            fila,
            numThreadsConsumidoras,
            faturasUnicas,
            idsBancos,
            faturaIdParaBancoId,
            totalParaEnriquecer,
            inicioEnriquecimento,
            intervaloLogProgresso,
            heartbeatSegundos
        );

        threadProdutora.start();
        aguardarProdutora(threadProdutora);
        aguardarConsumidores(executorConsumidores);

        final Duration duracao = Duration.between(inicioEnriquecimento, Instant.now());
        final double taxaSegundo = totalParaEnriquecer > 0 && duracao.getSeconds() > 0
            ? (double) totalParaEnriquecer / duracao.getSeconds()
            : 0;

        log.info("Enriquecimento GraphQL concluido:");
        log.info("  Total processadas: {}/{}", totalProcessadas.get(), totalParaEnriquecer);
        log.info("  Enriquecidas com sucesso: {}", totalEnriquecidas.get());
        log.info("  Erros HTTP: {}", totalComErro.get());
        log.info("  Duracao: {} segundos | Taxa: {} faturas/segundo",
            duracao.getSeconds(),
            String.format("%.2f", taxaSegundo));
    }

    private Thread criarThreadProdutora(final Set<Long> faturasParaEnriquecer,
                                        final BlockingQueue<EnriquecimentoTask> fila,
                                        final int numThreadsConsumidoras,
                                        final int limiteErrosConsecutivos) {
        return new Thread(ExecutionContext.wrapRunnable(() -> {
            try {
                for (final Long faturaId : faturasParaEnriquecer) {
                    try {
                        final Optional<CreditCustomerBillingNodeDTO> dadosCobrancaOpt =
                            apiClient.buscarDadosCobranca(faturaId);
                        fila.put(new EnriquecimentoTask(faturaId, dadosCobrancaOpt));
                        errosConsecutivos.set(0);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Thread produtora interrompida durante enfileiramento da fatura {}", faturaId);
                        break;
                    } catch (final RuntimeException e) {
                        if (!tratarErroProdutora(fila, faturaId, limiteErrosConsecutivos, e)) {
                            break;
                        }
                    }
                }
            } finally {
                publicarPoisonPills(fila, numThreadsConsumidoras);
            }
        }), "EnriquecimentoProducer");
    }

    private boolean tratarErroProdutora(final BlockingQueue<EnriquecimentoTask> fila,
                                        final Long faturaId,
                                        final int limiteErrosConsecutivos,
                                        final RuntimeException erro) {
        log.warn("Erro HTTP ao buscar dados de cobranca para fatura {}: {}", faturaId, erro.getMessage());

        final int erros = errosConsecutivos.incrementAndGet();
        totalComErro.incrementAndGet();
        if (erros >= limiteErrosConsecutivos) {
            final double multiplicador = ConfigEtl.obterMultiplicadorDelayErros();
            final long delayAdicional = (long) (2000 * multiplicador);
            log.warn("{} erros consecutivos detectados. Aguardando {}ms adicional...", erros, delayAdicional);
            try {
                ThreadUtil.aguardar(delayAdicional);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        try {
            fila.put(new EnriquecimentoTask(faturaId, Optional.empty()));
            return true;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void publicarPoisonPills(final BlockingQueue<EnriquecimentoTask> fila, final int numThreadsConsumidoras) {
        for (int i = 0; i < numThreadsConsumidoras; i++) {
            try {
                fila.put(POISON_PILL);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void iniciarConsumidores(final ExecutorService executorConsumidores,
                                     final BlockingQueue<EnriquecimentoTask> fila,
                                     final int numThreadsConsumidoras,
                                     final Map<Long, FaturaGraphQLEntity> faturasUnicas,
                                     final Set<Integer> idsBancos,
                                     final Map<Long, Integer> faturaIdParaBancoId,
                                     final int totalParaEnriquecer,
                                     final Instant inicioEnriquecimento,
                                     final int intervaloLogProgresso,
                                     final int heartbeatSegundos) {
        for (int i = 0; i < numThreadsConsumidoras; i++) {
            executorConsumidores.submit(ExecutionContext.wrapRunnable(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        final EnriquecimentoTask task = fila.take();
                        if (task == POISON_PILL) {
                            break;
                        }

                        processarTask(
                            task,
                            faturasUnicas,
                            idsBancos,
                            faturaIdParaBancoId,
                            totalParaEnriquecer,
                            inicioEnriquecimento,
                            intervaloLogProgresso,
                            heartbeatSegundos
                        );
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (final RuntimeException e) {
                        totalComErro.incrementAndGet();
                        log.warn("Falha ao processar task de enriquecimento: {}", e.getMessage());
                    }
                }
            }));
        }
    }

    private void processarTask(final EnriquecimentoTask task,
                               final Map<Long, FaturaGraphQLEntity> faturasUnicas,
                               final Set<Integer> idsBancos,
                               final Map<Long, Integer> faturaIdParaBancoId,
                               final int totalParaEnriquecer,
                               final Instant inicioEnriquecimento,
                               final int intervaloLogProgresso,
                               final int heartbeatSegundos) {
        final Long faturaId = task.faturaId();
        final FaturaGraphQLEntity entity = faturasUnicas.get(faturaId);
        if (entity == null) {
            totalProcessadas.incrementAndGet();
            return;
        }

        if (task.resultado().isPresent()) {
            final CreditCustomerBillingNodeDTO dadosCobranca = task.resultado().get();
            aplicarDadosCobranca(dadosCobranca, entity, faturaId, idsBancos, faturaIdParaBancoId);
            totalEnriquecidas.incrementAndGet();
        }

        final int processadas = totalProcessadas.incrementAndGet();
        if (processadas % intervaloLogProgresso == 0 || processadas == totalParaEnriquecer) {
            logProgresso(processadas, totalParaEnriquecer, inicioEnriquecimento);
        }

        final long agora = System.currentTimeMillis();
        final long ultimoLog = ultimoLogTimestamp.get();
        if ((agora - ultimoLog) > (heartbeatSegundos * 1000L)
            && ultimoLogTimestamp.compareAndSet(ultimoLog, agora)) {
            final double percentual = totalParaEnriquecer > 0
                ? (100.0 * processadas / totalParaEnriquecer)
                : 0.0;
            log.info("Heartbeat enriquecimento: {}/{} ({}%)",
                processadas,
                totalParaEnriquecer,
                String.format("%.1f", percentual));
        }
    }

    private void aplicarDadosCobranca(final CreditCustomerBillingNodeDTO dadosCobranca,
                                      final FaturaGraphQLEntity entity,
                                      final Long faturaId,
                                      final Set<Integer> idsBancos,
                                      final Map<Long, Integer> faturaIdParaBancoId) {
        if (dadosCobranca.getTicketAccountId() != null) {
            synchronized (idsBancos) {
                idsBancos.add(dadosCobranca.getTicketAccountId());
            }
            synchronized (faturaIdParaBancoId) {
                faturaIdParaBancoId.putIfAbsent(faturaId, dadosCobranca.getTicketAccountId());
            }
        }

        if (dadosCobranca.getInstallments() == null || dadosCobranca.getInstallments().isEmpty()) {
            return;
        }

        final CreditCustomerBillingNodeDTO.InstallmentDTO parcela = dadosCobranca.getInstallments().get(0);
        synchronized (entity) {
            if (parcela.getPaymentMethod() != null
                && !parcela.getPaymentMethod().trim().isEmpty()
                && (entity.getMetodoPagamento() == null || entity.getMetodoPagamento().trim().isEmpty())) {
                entity.setMetodoPagamento(parcela.getPaymentMethod().trim());
            }

            if (parcela.getAccountingCredit() != null) {
                final String nfseNumero = parcela.getAccountingCredit().getDocument();
                if (nfseNumero != null
                    && !nfseNumero.trim().isEmpty()
                    && (entity.getNfseNumero() == null || entity.getNfseNumero().trim().isEmpty())) {
                    entity.setNfseNumero(nfseNumero.trim());
                }
            }
        }
    }

    private void aguardarProdutora(final Thread threadProdutora) {
        try {
            threadProdutora.join();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread principal interrompida durante enriquecimento");
        }
    }

    private void aguardarConsumidores(final ExecutorService executorConsumidores) {
        executorConsumidores.shutdown();
        try {
            if (!executorConsumidores.awaitTermination(5, TimeUnit.MINUTES)) {
                log.warn("Timeout aguardando threads consumidoras. Forcando shutdown...");
                executorConsumidores.shutdownNow();
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            executorConsumidores.shutdownNow();
        }
    }

    private void logProgresso(final int processadas, final int total, final Instant inicio) {
        final Duration duracao = Duration.between(inicio, Instant.now());
        final double percentual = (100.0 * processadas / total);
        final double taxaSegundo = duracao.getSeconds() > 0 ? (double) processadas / duracao.getSeconds() : 0;
        final int restantes = total - processadas;
        final long segundosRestantes = taxaSegundo > 0 ? (long) (restantes / taxaSegundo) : 0;
        final long minutosRestantes = segundosRestantes / 60;

        log.info(
            "Progresso enriquecimento: {}/{} ({}%) | Enriquecidas: {} | Erros: {} | Taxa: {} faturas/s | Tempo restante: ~{} min",
            processadas,
            total,
            String.format("%.1f", percentual),
            totalEnriquecidas.get(),
            totalComErro.get(),
            String.format("%.2f", taxaSegundo),
            minutosRestantes
        );

        ultimoLogTimestamp.set(System.currentTimeMillis());
    }
}
