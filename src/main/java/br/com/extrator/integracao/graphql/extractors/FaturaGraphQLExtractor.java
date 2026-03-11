package br.com.extrator.integracao.graphql.extractors;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/graphql/extractors/FaturaGraphQLExtractor.java
Classe  : EntityExtractor (class)
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


import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.extrator.integracao.ClienteApiGraphQL;
import br.com.extrator.integracao.ResultadoExtracao;
import br.com.extrator.persistencia.entidade.FaturaGraphQLEntity;
import br.com.extrator.persistencia.repositorio.FaturaGraphQLRepository;
import br.com.extrator.persistencia.repositorio.FaturaPorClienteRepository;
import br.com.extrator.persistencia.repositorio.FreteRepository;
import br.com.extrator.dominio.graphql.bancos.BankAccountNodeDTO;
import br.com.extrator.dominio.graphql.faturas.CreditCustomerBillingNodeDTO;
import br.com.extrator.integracao.comum.ConstantesExtracao;
import br.com.extrator.integracao.comum.EntityExtractor;
import br.com.extrator.integracao.comum.ExtractionHelper;
import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

/**
 * Extractor para entidade Faturas GraphQL.
 * Mantem a orquestracao do save enquanto delega deduplicacao, backfill
 * e enriquecimento concorrente para suportes dedicados.
 */
public class FaturaGraphQLExtractor implements EntityExtractor<CreditCustomerBillingNodeDTO> {
    private final ClienteApiGraphQL apiClient;
    private final FaturaGraphQLRepository repository;
    private final FaturaPorClienteRepository faturasPorClienteRepository;
    private final FaturaGraphQLSaveSupport saveSupport;
    private final FaturaGraphQLBackfillSupport backfillSupport;
    private final FaturaGraphQLEnrichmentCoordinator enrichmentCoordinator;
    private final LoggerConsole log;
    private LocalDate dataInicioExtracao;
    private LocalDate dataFimExtracao;

    public FaturaGraphQLExtractor(final ClienteApiGraphQL apiClient,
                                  final FaturaGraphQLRepository repository,
                                  final FaturaPorClienteRepository faturasPorClienteRepository,
                                  final FreteRepository freteRepository,
                                  final LoggerConsole log) {
        final FaturaGraphQLEntityMapper entityMapper = new FaturaGraphQLEntityMapper();
        this.apiClient = apiClient;
        this.repository = repository;
        this.faturasPorClienteRepository = faturasPorClienteRepository;
        this.saveSupport = new FaturaGraphQLSaveSupport(apiClient, entityMapper, log);
        this.backfillSupport = new FaturaGraphQLBackfillSupport(apiClient, freteRepository, entityMapper, log);
        this.enrichmentCoordinator = new FaturaGraphQLEnrichmentCoordinator(apiClient, log);
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

        log.info("Iniciando processamento de {} faturas GraphQL com logica hibrida", dtos.size());

        final Map<Long, FaturaGraphQLEntity> faturasUnicas = saveSupport.deduplicarDtos(dtos);
        log.info("Deduplicacao concluida: {} faturas unicas de {} totais", faturasUnicas.size(), dtos.size());

        final int adicionadasPorBackfill = backfillSupport.executar(
            faturasUnicas,
            dataInicioExtracao,
            dataFimExtracao
        );
        if (adicionadasPorBackfill > 0) {
            log.info("Backfill por accounting_credit_id adicionou {} faturas antes do enriquecimento", adicionadasPorBackfill);
        }

        final Set<Integer> idsBancos = new HashSet<>();
        final Map<Long, Integer> faturaIdParaBancoId = new HashMap<>();

        saveSupport.coletarDadosDisponiveisNaQueryPrincipal(dtos, faturasUnicas, idsBancos, faturaIdParaBancoId);

        final Set<Long> faturasParaEnriquecer = saveSupport.determinarFaturasParaEnriquecer(
            faturasUnicas,
            faturaIdParaBancoId
        );

        log.info("{} faturas precisam de enriquecimento adicional", faturasParaEnriquecer.size());
        enrichmentCoordinator.executar(
            faturasParaEnriquecer,
            faturasUnicas,
            idsBancos,
            faturaIdParaBancoId
        );

        final Map<Integer, BankAccountNodeDTO> cacheBanco = saveSupport.carregarCacheBanco(idsBancos);
        saveSupport.aplicarDadosBancarios(faturasUnicas, faturaIdParaBancoId, cacheBanco);

        final List<FaturaGraphQLEntity> entitiesParaSalvar = new ArrayList<>(faturasUnicas.values());
        final int salvos = repository.salvar(entitiesParaSalvar);
        log.info("Capa Faturas GraphQL salvos: {}/{}", salvos, entitiesParaSalvar.size());

        enriquecerRelatorioViaTabelaPonte();
        return salvos;
    }

    private void enriquecerRelatorioViaTabelaPonte() {
        try {
            final int nfseAtualizadas = faturasPorClienteRepository.enriquecerNumeroNfseViaTabelaPonte();
            log.info("Relatorio Faturas enriquecido com NFS-e: {} linhas atualizadas", nfseAtualizadas);

            final int pagadorAtualizadas = faturasPorClienteRepository.enriquecerPagadorViaTabelaPonte();
            log.info("Relatorio Faturas enriquecido com Pagador: {} linhas atualizadas", pagadorAtualizadas);
        } catch (final java.sql.SQLException e) {
            log.warn("Enriquecimento via tabela ponte ignorado: {}", e.getMessage());
            ExtractionHelper.appendAvisoSeguranca(
                "Faturas GraphQL: enriquecimento via tabela ponte (NFS-e/Pagador) ignorado. Erro: " + e.getMessage()
            );
        }
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
