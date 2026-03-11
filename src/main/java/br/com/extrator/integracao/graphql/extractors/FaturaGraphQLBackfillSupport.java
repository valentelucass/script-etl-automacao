package br.com.extrator.integracao.graphql.extractors;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/graphql/extractors/FaturaGraphQLBackfillSupport.java
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


import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import br.com.extrator.integracao.ClienteApiGraphQL;
import br.com.extrator.persistencia.entidade.FaturaGraphQLEntity;
import br.com.extrator.persistencia.repositorio.FreteRepository;
import br.com.extrator.dominio.graphql.faturas.CreditCustomerBillingNodeDTO;
import br.com.extrator.integracao.comum.ExtractionHelper;
import br.com.extrator.suporte.ThreadUtil;
import br.com.extrator.suporte.console.LoggerConsole;

final class FaturaGraphQLBackfillSupport {
    private static final int LIMITE_BACKFILL_FATURAS_ORFAAS = 2000;
    private static final int MAX_TENTATIVAS_BACKFILL_POR_ID = 3;
    private static final long BACKFILL_RETRY_BASE_MS = 1500L;

    private final ClienteApiGraphQL apiClient;
    private final FreteRepository freteRepository;
    private final FaturaGraphQLEntityMapper entityMapper;
    private final LoggerConsole log;

    FaturaGraphQLBackfillSupport(final ClienteApiGraphQL apiClient,
                                 final FreteRepository freteRepository,
                                 final FaturaGraphQLEntityMapper entityMapper,
                                 final LoggerConsole log) {
        this.apiClient = apiClient;
        this.freteRepository = freteRepository;
        this.entityMapper = entityMapper;
        this.log = log;
    }

    int executar(final Map<Long, FaturaGraphQLEntity> faturasUnicas,
                 final LocalDate dataInicioExtracao,
                 final LocalDate dataFimExtracao) {
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
                        faturasUnicas.put(billingId, entityMapper.mapear(faturaOpt.get()));
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
}
