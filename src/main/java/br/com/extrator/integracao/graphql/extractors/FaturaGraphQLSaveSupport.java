package br.com.extrator.integracao.graphql.extractors;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/graphql/extractors/FaturaGraphQLSaveSupport.java
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


import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import br.com.extrator.integracao.ClienteApiGraphQL;
import br.com.extrator.persistencia.entidade.FaturaGraphQLEntity;
import br.com.extrator.dominio.graphql.bancos.BankAccountNodeDTO;
import br.com.extrator.dominio.graphql.faturas.CreditCustomerBillingNodeDTO;
import br.com.extrator.suporte.console.LoggerConsole;

final class FaturaGraphQLSaveSupport {
    private final ClienteApiGraphQL apiClient;
    private final FaturaGraphQLEntityMapper entityMapper;
    private final LoggerConsole log;

    FaturaGraphQLSaveSupport(final ClienteApiGraphQL apiClient,
                             final FaturaGraphQLEntityMapper entityMapper,
                             final LoggerConsole log) {
        this.apiClient = apiClient;
        this.entityMapper = entityMapper;
        this.log = log;
    }

    Map<Long, FaturaGraphQLEntity> deduplicarDtos(final List<CreditCustomerBillingNodeDTO> dtos) {
        final Map<Long, FaturaGraphQLEntity> faturasUnicas = new HashMap<>();
        for (final CreditCustomerBillingNodeDTO dto : dtos) {
            if (dto.getId() == null) {
                log.warn("Fatura GraphQL com ID nulo ignorada: {}", dto.getDocument());
                continue;
            }
            if (!faturasUnicas.containsKey(dto.getId())) {
                faturasUnicas.put(dto.getId(), entityMapper.mapear(dto));
            }
        }
        return faturasUnicas;
    }

    void coletarDadosDisponiveisNaQueryPrincipal(final List<CreditCustomerBillingNodeDTO> dtos,
                                                 final Map<Long, FaturaGraphQLEntity> faturasUnicas,
                                                 final Set<Integer> idsBancos,
                                                 final Map<Long, Integer> faturaIdParaBancoId) {
        for (final CreditCustomerBillingNodeDTO dto : dtos) {
            if (dto.getId() == null) {
                continue;
            }

            final FaturaGraphQLEntity entity = faturasUnicas.get(dto.getId());
            if (entity == null) {
                continue;
            }

            if (dto.getTicketAccountId() != null) {
                idsBancos.add(dto.getTicketAccountId());
                faturaIdParaBancoId.put(dto.getId(), dto.getTicketAccountId());
            }

            if (dto.getInstallments() != null && !dto.getInstallments().isEmpty()) {
                final var parcela = dto.getInstallments().get(0);
                if (parcela.getPaymentMethod() != null
                    && !parcela.getPaymentMethod().trim().isEmpty()
                    && (entity.getMetodoPagamento() == null || entity.getMetodoPagamento().trim().isEmpty())) {
                    entity.setMetodoPagamento(parcela.getPaymentMethod().trim());
                }
            }
        }
    }

    Set<Long> determinarFaturasParaEnriquecer(final Map<Long, FaturaGraphQLEntity> faturasUnicas,
                                              final Map<Long, Integer> faturaIdParaBancoId) {
        final Set<Long> faturasParaEnriquecer = new HashSet<>();
        for (final Map.Entry<Long, FaturaGraphQLEntity> entry : faturasUnicas.entrySet()) {
            final Long faturaId = entry.getKey();
            final FaturaGraphQLEntity entity = entry.getValue();
            final boolean precisaNfse = entity.getNfseNumero() == null || entity.getNfseNumero().trim().isEmpty();
            final boolean precisaBancoId = !faturaIdParaBancoId.containsKey(faturaId);
            if (precisaNfse || precisaBancoId) {
                faturasParaEnriquecer.add(faturaId);
            }
        }
        return faturasParaEnriquecer;
    }

    Map<Integer, BankAccountNodeDTO> carregarCacheBanco(final Set<Integer> idsBancos) {
        final Map<Integer, BankAccountNodeDTO> cacheBanco = new HashMap<>();
        int totalBancosBuscados = 0;
        for (final Integer idBanco : idsBancos) {
            try {
                final var dadosBancoOpt = apiClient.buscarDetalhesBanco(idBanco);
                if (dadosBancoOpt.isPresent()) {
                    cacheBanco.put(idBanco, dadosBancoOpt.get());
                    totalBancosBuscados++;
                }
            } catch (final RuntimeException e) {
                log.warn("Erro ao buscar detalhes do banco ID {}: {}", idBanco, e.getMessage());
            }
        }
        log.info("Cache bancario preenchido: {} bancos buscados com sucesso", totalBancosBuscados);
        return cacheBanco;
    }

    void aplicarDadosBancarios(final Map<Long, FaturaGraphQLEntity> faturasUnicas,
                               final Map<Long, Integer> faturaIdParaBancoId,
                               final Map<Integer, BankAccountNodeDTO> cacheBanco) {
        for (final Map.Entry<Long, FaturaGraphQLEntity> entry : faturasUnicas.entrySet()) {
            final Long faturaId = entry.getKey();
            final FaturaGraphQLEntity entity = entry.getValue();
            final Integer bancoIdTemporario = faturaIdParaBancoId.get(faturaId);
            if (bancoIdTemporario == null) {
                continue;
            }

            final BankAccountNodeDTO infoBanco = cacheBanco.get(bancoIdTemporario);
            if (infoBanco == null) {
                continue;
            }

            if (infoBanco.getBankName() != null && !infoBanco.getBankName().trim().isEmpty()) {
                entity.setBancoNome(infoBanco.getBankName().trim());
            }
            if (infoBanco.getPortfolioVariation() != null && !infoBanco.getPortfolioVariation().trim().isEmpty()) {
                entity.setCarteiraBanco(infoBanco.getPortfolioVariation().trim());
            }
            if (infoBanco.getCustomInstruction() != null && !infoBanco.getCustomInstruction().trim().isEmpty()) {
                entity.setInstrucaoBoleto(infoBanco.getCustomInstruction().trim());
            }
        }
    }
}
