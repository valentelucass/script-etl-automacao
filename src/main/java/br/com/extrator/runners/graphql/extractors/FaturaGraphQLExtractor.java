/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/graphql/extractors/FaturaGraphQLExtractor.java
Classe  : FaturaGraphQLExtractor (class)
Pacote  : br.com.extrator.runners.graphql.extractors
Modulo  : Extractor GraphQL
Papel   : Implementa responsabilidade de fatura graph qlextractor.

Conecta com:
- ClienteApiGraphQL (api)
- ThreadUtil (util)
- MapperUtil (util.mapeamento)
- ResultadoExtracao (api)
- FaturaGraphQLEntity (db.entity)
- FaturaGraphQLRepository (db.repository)
- FaturaPorClienteRepository (db.repository)
- FreteRepository (db.repository)

Fluxo geral:
1) Configura query e parametros para entidade alvo.
2) Invoca cliente GraphQL com paginacao segura.
3) Encaminha dados para camada de persistencia.

Estrutura interna:
Metodos principais:
- EnriquecimentoTask(...2 args): realiza operacao relacionada a "enriquecimento task".
- FaturaGraphQLExtractor(...5 args): realiza operacao relacionada a "fatura graph qlextractor".
- extract(...2 args): realiza operacao relacionada a "extract".
- mapearDtoParaEntity(...1 args): mapeia campos para DTO/entidade de destino.
- executarEnriquecimentoProducerConsumer(...8 args): executa o fluxo principal desta responsabilidade.
- processarTaskEnriquecimento(...10 args): realiza operacao relacionada a "processar task enriquecimento".
- executarBackfillFaturasOrfasPorId(...2 args): executa o fluxo principal desta responsabilidade.
- buscarFaturaPorIdComRetentativa(...1 args): consulta e retorna dados conforme criterio.
- logProgresso(...3 args): realiza operacao relacionada a "log progresso".
- getEntityName(): expone valor atual do estado interno.
- getEmoji(): expone valor atual do estado interno.
Atributos-chave:
- apiClient: cliente de integracao externa.
- repository: dependencia de acesso a banco.
- faturasPorClienteRepository: dependencia de acesso a banco.
- freteRepository: dependencia de acesso a banco.
- log: campo de estado para "log".
- LIMITE_BACKFILL_FATURAS_ORFAAS: campo de estado para "limite backfill faturas orfaas".
- MAX_TENTATIVAS_BACKFILL_POR_ID: campo de estado para "max tentativas backfill por id".
- BACKFILL_RETRY_BASE_MS: campo de estado para "backfill retry base ms".
- dataInicioExtracao: campo de estado para "data inicio extracao".
- dataFimExtracao: campo de estado para "data fim extracao".
- totalProcessadas: campo de estado para "total processadas".
- totalEnriquecidas: campo de estado para "total enriquecidas".
- totalComErro: campo de estado para "total com erro".
- errosConsecutivos: campo de estado para "erros consecutivos".
[DOC-FILE-END]============================================================== */

package br.com.extrator.runners.graphql.extractors;

import java.time.LocalDate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

import br.com.extrator.api.ClienteApiGraphQL;
import br.com.extrator.util.ThreadUtil;
import br.com.extrator.util.mapeamento.MapperUtil;
import br.com.extrator.api.ResultadoExtracao;
import br.com.extrator.db.entity.FaturaGraphQLEntity;
import br.com.extrator.db.repository.FaturaGraphQLRepository;
import br.com.extrator.db.repository.FaturaPorClienteRepository;
import br.com.extrator.db.repository.FreteRepository;
import br.com.extrator.modelo.graphql.faturas.CreditCustomerBillingNodeDTO;
import br.com.extrator.modelo.graphql.bancos.BankAccountNodeDTO;
import br.com.extrator.runners.common.ConstantesExtracao;
import br.com.extrator.runners.common.EntityExtractor;
import br.com.extrator.runners.common.ExtractionHelper;
import br.com.extrator.util.configuracao.CarregadorConfig;
import br.com.extrator.util.console.LoggerConsole;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Extractor para entidade Faturas GraphQL.
 * Possui lógica especial de mapeamento e enriquecimento.
 * 
 * FASE 4: Implementa padrão Producer-Consumer para otimização:
 * - Thread Produtora: Faz requisições HTTP sequenciais (respeitando throttling global de 2s)
 * - Threads Consumidoras: Processam resultados em paralelo (parsing, mapeamento)
 * - Logs de progresso detalhados a cada N registros
 * - Heartbeat para indicar que o processo está vivo
 */
public class FaturaGraphQLExtractor implements EntityExtractor<CreditCustomerBillingNodeDTO> {
    
    private final ClienteApiGraphQL apiClient;
    private final FaturaGraphQLRepository repository;
    private final FaturaPorClienteRepository faturasPorClienteRepository;
    private final FreteRepository freteRepository;
    private final LoggerConsole log;
    private static final int LIMITE_BACKFILL_FATURAS_ORFAAS = 2000;
    private static final int MAX_TENTATIVAS_BACKFILL_POR_ID = 3;
    private static final long BACKFILL_RETRY_BASE_MS = 1500L;
    private LocalDate dataInicioExtracao;
    private LocalDate dataFimExtracao;
    
    // FASE 4: Contadores para logs de progresso
    private final AtomicInteger totalProcessadas = new AtomicInteger(0);
    private final AtomicInteger totalEnriquecidas = new AtomicInteger(0);
    private final AtomicInteger totalComErro = new AtomicInteger(0);
    private final AtomicInteger errosConsecutivos = new AtomicInteger(0);
    private final AtomicLong ultimoLogTimestamp = new AtomicLong(0);
    
    // FASE 4: Poison pill para sinalizar fim da fila
    private static final EnriquecimentoTask POISON_PILL = new EnriquecimentoTask(-1L, null);
    
    /**
     * FASE 4: Task para a fila de enriquecimento.
     * Contém o ID da fatura e o resultado da requisição HTTP.
     */
    private record EnriquecimentoTask(
        Long faturaId,
        Optional<CreditCustomerBillingNodeDTO> resultado
    ) {}
    
    public FaturaGraphQLExtractor(final ClienteApiGraphQL apiClient,
                                 final FaturaGraphQLRepository repository,
                                 final FaturaPorClienteRepository faturasPorClienteRepository,
                                 final FreteRepository freteRepository,
                                 final LoggerConsole log) {
        this.apiClient = apiClient;
        this.repository = repository;
        this.faturasPorClienteRepository = faturasPorClienteRepository;
        this.freteRepository = freteRepository;
        this.log = log;
    }
    
    @Override
    public ResultadoExtracao<CreditCustomerBillingNodeDTO> extract(final LocalDate dataInicio, final LocalDate dataFim) {
        this.dataInicioExtracao = dataInicio;
        this.dataFimExtracao = dataFim;
        return apiClient.buscarCapaFaturas(dataInicio, dataFim);
    }
    
    @Override
    public int save(final List<CreditCustomerBillingNodeDTO> dtos) throws java.sql.SQLException {
        if (dtos == null || dtos.isEmpty()) {
            return 0;
        }
        
        log.info("🔄 Iniciando processamento de {} faturas GraphQL com lógica híbrida", dtos.size());
        
        // PASSO 1: DEDUPLICAÇÃO (Resolve o problema das linhas repetidas)
        final Map<Long, FaturaGraphQLEntity> faturasUnicas = new HashMap<>();
        for (final CreditCustomerBillingNodeDTO dto : dtos) {
            if (dto.getId() == null) {
                log.warn("⚠️ Fatura GraphQL com ID nulo ignorada: {}", dto.getDocument());
                continue;
            }
            
            // Usa o ID da Fatura como chave para garantir unicidade
            if (!faturasUnicas.containsKey(dto.getId())) {
                final FaturaGraphQLEntity entity = mapearDtoParaEntity(dto);
                faturasUnicas.put(dto.getId(), entity);
            }
        }
        
        log.info("✓ Deduplicação concluída: {} faturas únicas de {} totais", faturasUnicas.size(), dtos.size());
        final int adicionadasPorBackfill = executarBackfillFaturasOrfasPorId(faturasUnicas);
        if (adicionadasPorBackfill > 0) {
            log.info("Backfill por accounting_credit_id adicionou {} faturas antes do enriquecimento", adicionadasPorBackfill);
        }
        
        
        // PASSO 2: PREPARAÇÃO DO CACHE BANCÁRIO (Side-Loading)
        final Set<Integer> idsBancos = new HashSet<>();
        final Map<Long, Integer> faturaIdParaBancoId = new HashMap<>();
        
        // PASSO 3: LOOP DE ENRIQUECIMENTO (GraphQL)
        // Primeiro, tenta usar dados já disponíveis na query principal
        for (final CreditCustomerBillingNodeDTO dto : dtos) {
            if (dto.getId() == null) {
                continue;
            }
            
            final FaturaGraphQLEntity entity = faturasUnicas.get(dto.getId());
            if (entity == null) {
                continue;
            }
            
            // Coleta ticketAccountId se já estiver disponível na query principal
            if (dto.getTicketAccountId() != null) {
                idsBancos.add(dto.getTicketAccountId());
                faturaIdParaBancoId.put(dto.getId(), dto.getTicketAccountId());
            }
            
            // Método de pagamento já pode estar na primeira parcela
            if (dto.getInstallments() != null && !dto.getInstallments().isEmpty()) {
                final var parcela = dto.getInstallments().get(0);
                if (parcela.getPaymentMethod() != null && !parcela.getPaymentMethod().trim().isEmpty() 
                        && (entity.getMetodoPagamento() == null || entity.getMetodoPagamento().trim().isEmpty())) {
                    entity.setMetodoPagamento(parcela.getPaymentMethod().trim());
                }
            }
        }
        
        // Agora enriquece apenas as faturas que precisam (falta NFS-e ou ticketAccountId)
        // FASE 4: Removidas variáveis locais - usar AtomicInteger da classe
        final Set<Long> faturasParaEnriquecer = new HashSet<>();
        
        for (final Map.Entry<Long, FaturaGraphQLEntity> entry : faturasUnicas.entrySet()) {
            final Long faturaId = entry.getKey();
            final FaturaGraphQLEntity entity = entry.getValue();
            
            // Verifica se precisa enriquecer (falta NFS-e ou ticketAccountId)
            final boolean precisaNfse = entity.getNfseNumero() == null || entity.getNfseNumero().trim().isEmpty();
            final boolean precisaBancoId = !faturaIdParaBancoId.containsKey(faturaId);
            
            if (precisaNfse || precisaBancoId) {
                faturasParaEnriquecer.add(faturaId);
            }
        }
        
        log.info("🔍 {} faturas precisam de enriquecimento adicional (falta NFS-e ou ticketAccountId)", faturasParaEnriquecer.size());

        // FASE 4: Reset dos contadores
        totalProcessadas.set(0);
        totalEnriquecidas.set(0);
        totalComErro.set(0);
        errosConsecutivos.set(0);
        ultimoLogTimestamp.set(System.currentTimeMillis());
        
        final int totalParaEnriquecer = faturasParaEnriquecer.size();
        final Instant inicioEnriquecimento = Instant.now();
        
        if (totalParaEnriquecer > 0) {
            // FASE 4: Producer-Consumer Pattern
            executarEnriquecimentoProducerConsumer(
                faturasParaEnriquecer,
                faturasUnicas,
                idsBancos,
                faturaIdParaBancoId,
                totalParaEnriquecer,
                inicioEnriquecimento
            );
        }

        log.info("✓ Enriquecimento concluído: {} faturas enriquecidas, {} erros, {} IDs de bancos únicos coletados",
                totalEnriquecidas.get(), totalComErro.get(), idsBancos.size());

        // PASSO 4: CACHE BANCÁRIO (Busca detalhes apenas uma vez por Banco)
        final Map<Integer, BankAccountNodeDTO> cacheBanco = new HashMap<>();
        int totalBancosBuscados = 0;

        for (final Integer idBanco : idsBancos) {
            try {
                // CHAMADA GRAPHQL 2: Detalhes do Banco
                final var dadosBancoOpt = apiClient.buscarDetalhesBanco(idBanco);

                if (dadosBancoOpt.isPresent()) {
                    cacheBanco.put(idBanco, dadosBancoOpt.get());
                    totalBancosBuscados++;
                }
            } catch (final Exception e) {
                log.warn("⚠️ Erro ao buscar detalhes do banco ID {}: {}", idBanco, e.getMessage());
            }
        }

        log.info("✓ Cache bancário preenchido: {} bancos buscados com sucesso", totalBancosBuscados);
        
        // PASSO 5: MERGE FINAL
        for (final Map.Entry<Long, FaturaGraphQLEntity> entry : faturasUnicas.entrySet()) {
            final Long faturaId = entry.getKey();
            final FaturaGraphQLEntity entity = entry.getValue();
            
            final Integer bancoIdTemporario = faturaIdParaBancoId.get(faturaId);
            if (bancoIdTemporario != null) {
                final var infoBanco = cacheBanco.get(bancoIdTemporario);
                if (infoBanco != null) {
                    // Nome do Banco
                    if (infoBanco.getBankName() != null && !infoBanco.getBankName().trim().isEmpty()) {
                        entity.setBancoNome(infoBanco.getBankName().trim());
                    }
                    
                    // Carteira (pode vir vazio se não cadastrado)
                    if (infoBanco.getPortfolioVariation() != null && !infoBanco.getPortfolioVariation().trim().isEmpty()) {
                        entity.setCarteiraBanco(infoBanco.getPortfolioVariation().trim());
                    }
                    
                    // Instrução Customizada (pode vir vazio se não cadastrado)
                    if (infoBanco.getCustomInstruction() != null && !infoBanco.getCustomInstruction().trim().isEmpty()) {
                        entity.setInstrucaoBoleto(infoBanco.getCustomInstruction().trim());
                    }
                }
            }
        }
        
        // PASSO 6: SALVAR NO SQL (Upsert)
        final List<FaturaGraphQLEntity> entitiesParaSalvar = new ArrayList<>(faturasUnicas.values());
        final int salvos = repository.salvar(entitiesParaSalvar);
        log.info("✓ Capa Faturas GraphQL salvos: {}/{}", salvos, entitiesParaSalvar.size());
        
        // Enriquecimento via tabela ponte
        try {
            final int nfseAtualizadas = faturasPorClienteRepository.enriquecerNumeroNfseViaTabelaPonte();
            log.info("✓ Relatório Faturas enriquecido com NFS-e: {} linhas atualizadas", nfseAtualizadas);
            final int pagadorAtualizadas = faturasPorClienteRepository.enriquecerPagadorViaTabelaPonte();
            log.info("✓ Relatório Faturas enriquecido com Pagador: {} linhas atualizadas", pagadorAtualizadas);
        } catch (final java.sql.SQLException e) {
            log.warn("⚠️ Enriquecimento via tabela ponte ignorado: {}", e.getMessage());
            ExtractionHelper.appendAvisoSeguranca("Faturas GraphQL: enriquecimento via tabela ponte (NFS-e/Pagador) ignorado. Erro: " + e.getMessage());
        }
        
        return salvos;
    }
    
    /**
     * Mapeia um DTO de fatura GraphQL para uma Entity.
     * Extrai todos os campos básicos (sem enriquecimento via queries adicionais).
     */
    private FaturaGraphQLEntity mapearDtoParaEntity(final CreditCustomerBillingNodeDTO dto) {
        final FaturaGraphQLEntity entity = new FaturaGraphQLEntity();
        entity.setId(dto.getId());
        entity.setDocument(dto.getDocument());
        
        // Datas
        try {
            entity.setIssueDate(dto.getIssueDate() != null ? LocalDate.parse(dto.getIssueDate()) : null);
        } catch (final Exception ignored) {
            // Ignorar erros de parsing
        }
        try {
            entity.setDueDate(dto.getDueDate() != null ? LocalDate.parse(dto.getDueDate()) : null);
        } catch (final Exception ignored) {
            // Ignorar erros de parsing
        }
        try {
            if (dto.getInstallments() != null && !dto.getInstallments().isEmpty()) {
                final String originalDueDate = dto.getInstallments().get(0).getOriginalDueDate();
                entity.setOriginalDueDate(originalDueDate != null ? LocalDate.parse(originalDueDate) : null);
            }
        } catch (final Exception ignored) {
            // Ignorar erros de parsing
        }
        
        // Valores
        entity.setValue(dto.getValue());
        entity.setPaidValue(dto.getPaidValue());
        entity.setValueToPay(dto.getValueToPay());
        entity.setDiscountValue(dto.getDiscountValue());
        entity.setInterestValue(dto.getInterestValue());
        entity.setPaid(dto.getPaid());
        
        // Status
        if (dto.getInstallments() != null && !dto.getInstallments().isEmpty()) {
            entity.setStatus(dto.getInstallments().get(0).getStatus());
        }
        
        // Tipo e comentários
        entity.setType(dto.getType());
        entity.setComments(dto.getComments());
        entity.setSequenceCode(dto.getSequenceCode());
        entity.setCompetenceMonth(dto.getCompetenceMonth());
        entity.setCompetenceYear(dto.getCompetenceYear());
        
        // Corporation
        if (dto.getCorporation() != null) {
            try {
                if (dto.getCorporation().getId() != null) {
                    try {
                        entity.setCorporationId(Long.valueOf(dto.getCorporation().getId()));
                    } catch (final NumberFormatException ex) {
                        entity.setCorporationId(null);
                    }
                }
            } catch (final Exception ignored) {
                // Ignorar erros
            }
            
            if (dto.getCorporation().getPerson() != null) {
                entity.setCorporationName(dto.getCorporation().getPerson().getNickname());
                entity.setCorporationCnpj(dto.getCorporation().getPerson().getCnpj());
            }
        }
        
        // Campos básicos da primeira parcela (podem ser sobrescritos no enriquecimento)
        if (dto.getInstallments() != null && !dto.getInstallments().isEmpty()) {
            final CreditCustomerBillingNodeDTO.InstallmentDTO primeiraParcela = dto.getInstallments().get(0);
            
            // N° NFS-e (pode ser sobrescrito no enriquecimento)
            if (primeiraParcela.getAccountingCredit() != null) {
                final String nfseNumero = primeiraParcela.getAccountingCredit().getDocument();
                if (nfseNumero != null && !nfseNumero.trim().isEmpty()) {
                    entity.setNfseNumero(nfseNumero.trim());
                }
            }
            
            // Carteira e Instrução (podem ser sobrescritos no enriquecimento)
            if (primeiraParcela.getAccountingBankAccount() != null) {
                final CreditCustomerBillingNodeDTO.AccountingBankAccountDTO contaBancaria = 
                    primeiraParcela.getAccountingBankAccount();
                
                final String carteira = contaBancaria.getPortfolioVariation();
                if (carteira != null && !carteira.trim().isEmpty()) {
                    entity.setCarteiraBanco(carteira.trim());
                }
                
                final String instrucao = contaBancaria.getCustomInstruction();
                if (instrucao != null && !instrucao.trim().isEmpty()) {
                    entity.setInstrucaoBoleto(instrucao.trim());
                }
                
                // Nome do banco (pode vir da parcela, mas será sobrescrito se houver enriquecimento)
                final String bancoNome = contaBancaria.getBankName();
                if (bancoNome != null && !bancoNome.trim().isEmpty()) {
                    entity.setBancoNome(bancoNome.trim());
                }
            }
            
            // Método de Pagamento (pode ser sobrescrito no enriquecimento)
            if (primeiraParcela.getPaymentMethod() != null && !primeiraParcela.getPaymentMethod().trim().isEmpty()) {
                entity.setMetodoPagamento(primeiraParcela.getPaymentMethod().trim());
            }
        }
        
        // Metadata (JSON completo)
        entity.setMetadata(MapperUtil.toJson(dto));
        
        return entity;
    }
    
    /**
     * FASE 4: Executa o enriquecimento usando padrão Producer-Consumer.
     * 
     * Thread Produtora: Faz requisições HTTP sequenciais (respeitando throttling global de 2s)
     * Threads Consumidoras: Processam resultados em paralelo
     */
    private void executarEnriquecimentoProducerConsumer(
            final Set<Long> faturasParaEnriquecer,
            final Map<Long, FaturaGraphQLEntity> faturasUnicas,
            final Set<Integer> idsBancos,
            final Map<Long, Integer> faturaIdParaBancoId,
            final int totalParaEnriquecer,
            final Instant inicioEnriquecimento) {
        
        final int numThreadsConsumidoras = CarregadorConfig.obterThreadsProcessamentoFaturas();
        final int intervaloLogProgresso = CarregadorConfig.obterIntervaloLogProgressoEnriquecimento();
        final int limiteErrosConsecutivos = CarregadorConfig.obterLimiteErrosConsecutivos();
        final int heartbeatSegundos = CarregadorConfig.obterHeartbeatSegundos();
        
        log.info("🚀 [FASE 4] Iniciando enriquecimento com padrão Producer-Consumer");
        log.info("   • Thread Produtora: 1 (requisições HTTP sequenciais com throttling 2s)");
        log.info("   • Threads Consumidoras: {} (processamento paralelo)", numThreadsConsumidoras);
        log.info("   • Log de progresso: a cada {} faturas", intervaloLogProgresso);
        log.info("   • Heartbeat: a cada {}s", heartbeatSegundos);
        
        // Fila de comunicação entre Producer e Consumers
        final BlockingQueue<EnriquecimentoTask> fila = new LinkedBlockingQueue<>(100);
        
        // Thread Produtora: Faz requisições HTTP sequenciais
        final Thread threadProdutora = new Thread(() -> {
            try {
                for (final Long faturaId : faturasParaEnriquecer) {
                    try {
                        // Requisição HTTP (sequencial, respeitando throttling global via Singleton)
                        final var dadosCobrancaOpt = apiClient.buscarDadosCobranca(faturaId);
                        fila.put(new EnriquecimentoTask(faturaId, dadosCobrancaOpt));
                        
                        // Reset do contador de erros consecutivos em caso de sucesso
                        errosConsecutivos.set(0);
                        
                    } catch (final Exception e) {
                        log.warn("⚠️ Erro HTTP ao buscar dados de cobrança para fatura {}: {}", faturaId, e.getMessage());
                        
                        // Incrementa contador de erros consecutivos
                        final int erros = errosConsecutivos.incrementAndGet();
                        totalComErro.incrementAndGet();
                        
                        // Se muitos erros consecutivos, aguarda mais (resiliência)
                        if (erros >= limiteErrosConsecutivos) {
                            final double multiplicador = CarregadorConfig.obterMultiplicadorDelayErros();
                            final long delayAdicional = (long) (2000 * multiplicador);
                            log.warn("⚠️ {} erros consecutivos detectados. Aguardando {}ms adicional...", erros, delayAdicional);
                            try {
                                ThreadUtil.aguardar(delayAdicional);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        
                        // Coloca resultado vazio na fila para manter a contagem
                        try {
                            fila.put(new EnriquecimentoTask(faturaId, Optional.empty()));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            } finally {
                // Sinaliza fim para todas as threads consumidoras
                for (int i = 0; i < numThreadsConsumidoras; i++) {
                    try {
                        fila.put(POISON_PILL);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }, "EnriquecimentoProducer");
        
        // Executor para threads consumidoras
        final ExecutorService executorConsumidores = Executors.newFixedThreadPool(numThreadsConsumidoras);
        
        // Threads Consumidoras: Processam resultados em paralelo
        for (int i = 0; i < numThreadsConsumidoras; i++) {
            executorConsumidores.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        final EnriquecimentoTask task = fila.take();
                        
                        // Verifica poison pill
                        if (task == POISON_PILL) {
                            break;
                        }
                        
                        // Processa a task
                        processarTaskEnriquecimento(
                            task,
                            faturasUnicas,
                            idsBancos,
                            faturaIdParaBancoId,
                            totalParaEnriquecer,
                            inicioEnriquecimento,
                            intervaloLogProgresso,
                            heartbeatSegundos
                        );
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }
        
        // Inicia a thread produtora
        threadProdutora.start();
        
        // Aguarda a thread produtora terminar
        try {
            threadProdutora.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread principal interrompida durante enriquecimento");
        }
        
        // Aguarda todas as consumidoras terminarem
        executorConsumidores.shutdown();
        try {
            if (!executorConsumidores.awaitTermination(5, TimeUnit.MINUTES)) {
                log.warn("Timeout aguardando threads consumidoras. Forçando shutdown...");
                executorConsumidores.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executorConsumidores.shutdownNow();
        }
        
        // Log final
        final Duration duracao = Duration.between(inicioEnriquecimento, Instant.now());
        final double taxaSegundo = totalParaEnriquecer > 0 && duracao.getSeconds() > 0 
            ? (double) totalParaEnriquecer / duracao.getSeconds() 
            : 0;
        
        log.info("📊 [FASE 4] Enriquecimento Producer-Consumer concluído:");
        log.info("   • Total processadas: {}/{}", totalProcessadas.get(), totalParaEnriquecer);
        log.info("   • Enriquecidas com sucesso: {}", totalEnriquecidas.get());
        log.info("   • Erros HTTP: {}", totalComErro.get());
        log.info("   • Duração: {} segundos", duracao.getSeconds());
        log.info("   • Taxa: {} faturas/segundo", String.format("%.2f", taxaSegundo));
    }
    
    /**
     * FASE 4: Processa uma task de enriquecimento (chamada pelas threads consumidoras).
     */
    private void processarTaskEnriquecimento(
            final EnriquecimentoTask task,
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
            final var dadosCobranca = task.resultado().get();
            
            // 1. Pega ID do Banco para o Cache (thread-safe)
            if (dadosCobranca.getTicketAccountId() != null) {
                synchronized (idsBancos) {
                    idsBancos.add(dadosCobranca.getTicketAccountId());
                }
                synchronized (faturaIdParaBancoId) {
                    if (!faturaIdParaBancoId.containsKey(faturaId)) {
                        faturaIdParaBancoId.put(faturaId, dadosCobranca.getTicketAccountId());
                    }
                }
            }
            
            // 2. Entra na Parcela para pegar NFS-e e Método Pagamento
            if (dadosCobranca.getInstallments() != null && !dadosCobranca.getInstallments().isEmpty()) {
                final var parcela = dadosCobranca.getInstallments().get(0);
                
                // Método de Pagamento (synchronized para entity)
                synchronized (entity) {
                    if (parcela.getPaymentMethod() != null && !parcela.getPaymentMethod().trim().isEmpty()
                            && (entity.getMetodoPagamento() == null || entity.getMetodoPagamento().trim().isEmpty())) {
                        entity.setMetodoPagamento(parcela.getPaymentMethod().trim());
                    }
                    
                    // N° NFS-e
                    if (parcela.getAccountingCredit() != null) {
                        final String nfseNumero = parcela.getAccountingCredit().getDocument();
                        if (nfseNumero != null && !nfseNumero.trim().isEmpty()
                                && (entity.getNfseNumero() == null || entity.getNfseNumero().trim().isEmpty())) {
                            entity.setNfseNumero(nfseNumero.trim());
                        }
                    }
                }
            }
            
            totalEnriquecidas.incrementAndGet();
        }
        
        final int processadas = totalProcessadas.incrementAndGet();
        
        // Log de progresso a cada N faturas
        if (processadas % intervaloLogProgresso == 0 || processadas == totalParaEnriquecer) {
            logProgresso(processadas, totalParaEnriquecer, inicioEnriquecimento);
        }
        
        // Heartbeat a cada N segundos (mesmo sem novos registros)
        final long agora = System.currentTimeMillis();
        final long ultimoLog = ultimoLogTimestamp.get();
        if ((agora - ultimoLog) > (heartbeatSegundos * 1000L)) {
            if (ultimoLogTimestamp.compareAndSet(ultimoLog, agora)) {
                final double percentualHeartbeat = totalParaEnriquecer > 0
                    ? (100.0 * processadas / totalParaEnriquecer)
                    : 0.0;
                log.info("💓 [Heartbeat] Enriquecimento em andamento: {}/{} ({}%)",
                        processadas, totalParaEnriquecer,
                        String.format("%.1f", percentualHeartbeat));
            }
        }
    }
    
    /**
     * Backfill referencial para reconciliacao:
     * busca IDs orfaos em fretes.accounting_credit_id e tenta materializar
     * as faturas faltantes por consulta direta de ID na GraphQL.
     */
    private int executarBackfillFaturasOrfasPorId(final Map<Long, FaturaGraphQLEntity> faturasUnicas) {
        if (dataInicioExtracao == null || dataFimExtracao == null) {
            return 0;
        }

        try {
            final List<Long> idsOrfaos = freteRepository.listarAccountingCreditIdsOrfaos(
                dataInicioExtracao,
                dataFimExtracao,
                LIMITE_BACKFILL_FATURAS_ORFAAS
            );

            if (idsOrfaos.isEmpty()) {
                return 0;
            }

            int adicionadas = 0;
            int jaPresentes = 0;
            int naoEncontradas = 0;
            int erros = 0;

            log.info("Backfill referencial: {} accounting_credit_id orfao(s) identificado(s) para {} a {}",
                idsOrfaos.size(), dataInicioExtracao, dataFimExtracao);

            for (final Long billingId : idsOrfaos) {
                if (billingId == null) {
                    continue;
                }
                if (faturasUnicas.containsKey(billingId)) {
                    jaPresentes++;
                    continue;
                }

                try {
                    final Optional<CreditCustomerBillingNodeDTO> faturaOpt = buscarFaturaPorIdComRetentativa(billingId);
                    if (faturaOpt.isPresent()) {
                        faturasUnicas.put(billingId, mapearDtoParaEntity(faturaOpt.get()));
                        adicionadas++;
                    } else {
                        naoEncontradas++;
                    }
                } catch (final RuntimeException e) {
                    erros++;
                    log.warn("Falha ao buscar fatura por ID {} durante backfill: {}", billingId, e.getMessage());
                }
            }

            log.info("Backfill referencial concluido: adicionadas={} | ja_presentes={} | nao_encontradas={} | erros={}",
                adicionadas, jaPresentes, naoEncontradas, erros);
            return adicionadas;

        } catch (final java.sql.SQLException e) {
            log.warn("Backfill referencial por accounting_credit_id falhou: {}", e.getMessage());
            ExtractionHelper.appendAvisoSeguranca(
                "Faturas GraphQL: backfill por accounting_credit_id falhou. Erro: " + e.getMessage()
            );
            return 0;
        }
    }

    private Optional<CreditCustomerBillingNodeDTO> buscarFaturaPorIdComRetentativa(final Long billingId) {
        final int limiteTentativas = Math.max(1, MAX_TENTATIVAS_BACKFILL_POR_ID);
        for (int tentativa = 1; tentativa <= limiteTentativas; tentativa++) {
            final Optional<CreditCustomerBillingNodeDTO> faturaOpt = apiClient.buscarCapaFaturaPorId(billingId);
            if (faturaOpt.isPresent()) {
                if (tentativa > 1) {
                    log.info("Backfill referencial: ID {} recuperado na tentativa {}", billingId, tentativa);
                }
                return faturaOpt;
            }

            if (tentativa < limiteTentativas) {
                final long esperaMs = BACKFILL_RETRY_BASE_MS * tentativa;
                log.warn("Backfill referencial: ID {} nao retornou dados na tentativa {}/{}. Nova tentativa em {} ms",
                    billingId,
                    tentativa,
                    limiteTentativas,
                    esperaMs);
                try {
                    ThreadUtil.aguardar(esperaMs);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Backfill referencial interrompido durante retentativa do ID {}", billingId);
                    break;
                }
            }
        }
        return Optional.empty();
    }


    /**
     * FASE 4: Log de progresso detalhado.
     */
    private void logProgresso(final int processadas, final int total, final Instant inicio) {
        final Duration duracao = Duration.between(inicio, Instant.now());
        final double percentual = (100.0 * processadas / total);
        final double taxaSegundo = duracao.getSeconds() > 0 ? (double) processadas / duracao.getSeconds() : 0;
        
        // Estimar tempo restante
        final int restantes = total - processadas;
        final long segundosRestantes = taxaSegundo > 0 ? (long) (restantes / taxaSegundo) : 0;
        final long minutosRestantes = segundosRestantes / 60;
        
        log.info("📊 Progresso Enriquecimento: {}/{} ({}%) | Enriquecidas: {} | Erros: {} | Taxa: {} faturas/s | Tempo restante: ~{} min",
                processadas, total, String.format("%.1f", percentual),
                totalEnriquecidas.get(), totalComErro.get(),
                String.format("%.2f", taxaSegundo), minutosRestantes);
        
        ultimoLogTimestamp.set(System.currentTimeMillis());
    }
    
    @Override
    public String getEntityName() {
        return ConstantesEntidades.FATURAS_GRAPHQL;
    }
    
    @Override
    public String getEmoji() {
        return ConstantesExtracao.EMOJI_FATURAS;
    }
}
