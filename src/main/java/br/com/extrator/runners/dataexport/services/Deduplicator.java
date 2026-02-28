/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/dataexport/services/Deduplicator.java
Classe  : Deduplicator (class)
Pacote  : br.com.extrator.runners.dataexport.services
Modulo  : Servico de execucao DataExport
Papel   : Implementa responsabilidade de deduplicator.

Conecta com:
- ContasAPagarDataExportEntity (db.entity)
- CotacaoEntity (db.entity)
- FaturaPorClienteEntity (db.entity)
- LocalizacaoCargaEntity (db.entity)
- ManifestoEntity (db.entity)

Fluxo geral:
1) Coordena extractors da API DataExport.
2) Aplica deduplicacao/normalizacao quando necessario.
3) Encaminha resultado consolidado para o runner.

Estrutura interna:
Metodos principais:
- Deduplicator(): realiza operacao relacionada a "deduplicator".
- deduplicarManifestos(...1 args): realiza operacao relacionada a "deduplicar manifestos".
- obterMaisRecenteManifesto(...2 args): recupera dados configurados ou calculados.
- deduplicarCotacoes(...1 args): realiza operacao relacionada a "deduplicar cotacoes".
- obterMaisRecenteCotacao(...2 args): recupera dados configurados ou calculados.
- deduplicarLocalizacoes(...1 args): realiza operacao relacionada a "deduplicar localizacoes".
- obterMaisRecenteLocalizacao(...2 args): recupera dados configurados ou calculados.
- deduplicarFaturasAPagar(...1 args): realiza operacao relacionada a "deduplicar faturas apagar".
- deduplicarFaturasPorCliente(...1 args): realiza operacao relacionada a "deduplicar faturas por cliente".
Atributos-chave:
- logger: logger da classe para diagnostico.
[DOC-FILE-END]============================================================== */

package br.com.extrator.runners.dataexport.services;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.ContasAPagarDataExportEntity;
import br.com.extrator.db.entity.CotacaoEntity;
import br.com.extrator.db.entity.FaturaPorClienteEntity;
import br.com.extrator.db.entity.LocalizacaoCargaEntity;
import br.com.extrator.db.entity.ManifestoEntity;

/**
 * Classe utilitária para deduplicação de entidades do DataExport.
 * Remove registros duplicados da resposta da API antes de salvar no banco.
 * 
 * Estratégia: "Keep Last" (último vence) para preservar dados mais recentes.
 * Chaves de deduplicação são alinhadas com as chaves do MERGE SQL.
 */
public final class Deduplicator {
    private static final Logger logger = LoggerFactory.getLogger(Deduplicator.class);
    
    private Deduplicator() {}
    
    /**
     * Deduplica lista de manifestos removendo registros duplicados da API.
     * 
     * ⚠️ CRÍTICO: Usa a MESMA chave composta do MERGE SQL:
     * (sequence_code, pick_sequence_code, mdfe_number)
     * 
     * Estratégia: "Keep Last" - mantém o registro mais recente baseado em finishedAt/createdAt.
     * 
     * @param manifestos Lista de manifestos a deduplicar
     * @return Lista deduplicada de manifestos
     */
    public static List<ManifestoEntity> deduplicarManifestos(final List<ManifestoEntity> manifestos) {
        if (manifestos == null || manifestos.isEmpty()) {
            return manifestos;
        }
        
        return manifestos.stream()
            .collect(Collectors.toMap(
                m -> {
                    // ✅ CHAVE ALINHADA COM MERGE SQL: (sequence_code, pick_sequence_code, mdfe_number)
                    if (m.getSequenceCode() == null) {
                        throw new IllegalStateException("Manifesto com sequence_code NULL não pode ser deduplicado");
                    }
                    // Usar -1 como sentinela para NULL (igual ao MERGE SQL com COALESCE)
                    final Long pickSeq = m.getPickSequenceCode() != null ? m.getPickSequenceCode() : -1L;
                    final Integer mdfe = m.getMdfeNumber() != null ? m.getMdfeNumber() : -1;
                    return m.getSequenceCode() + "_" + pickSeq + "_" + mdfe;
                },
                m -> m,
                (primeiro, segundo) -> {
                    // ✅ ESTRATÉGIA "KEEP LAST": Manter o registro mais recente
                    final ManifestoEntity maisRecente = obterMaisRecenteManifesto(primeiro, segundo);
                    logger.warn("⚠️ Duplicado detectado na API: sequence_code={}, pick={}, mdfe={}. Mantendo o mais recente (finishedAt/createdAt).", 
                        primeiro.getSequenceCode(), 
                        primeiro.getPickSequenceCode(), 
                        primeiro.getMdfeNumber());
                    return maisRecente;
                }
            ))
            .values()
            .stream()
            .collect(Collectors.toList());
    }
    
    /**
     * Determina qual manifesto é mais recente baseado em finishedAt ou createdAt.
     * Se ambos forem NULL, mantém o segundo (assume que é o último processado).
     */
    private static ManifestoEntity obterMaisRecenteManifesto(final ManifestoEntity primeiro, final ManifestoEntity segundo) {
        // Prioridade 1: finishedAt (data de finalização é mais confiável)
        final OffsetDateTime finishedAt1 = primeiro.getFinishedAt();
        final OffsetDateTime finishedAt2 = segundo.getFinishedAt();
        if (finishedAt1 != null && finishedAt2 != null) {
            return finishedAt1.isAfter(finishedAt2) ? primeiro : segundo;
        }
        if (finishedAt2 != null) {
            return segundo; // Segundo tem finishedAt, primeiro não → segundo é mais recente
        }
        if (finishedAt1 != null) {
            return primeiro; // Primeiro tem finishedAt, segundo não → primeiro é mais recente
        }
        
        // Prioridade 2: createdAt (fallback)
        final OffsetDateTime createdAt1 = primeiro.getCreatedAt();
        final OffsetDateTime createdAt2 = segundo.getCreatedAt();
        if (createdAt1 != null && createdAt2 != null) {
            return createdAt1.isAfter(createdAt2) ? primeiro : segundo;
        }
        if (createdAt2 != null) {
            return segundo;
        }
        if (createdAt1 != null) {
            return primeiro;
        }
        
        // Fallback final: manter o segundo (assume que é o último processado na lista)
        logger.debug("Nenhum timestamp disponível para decidir. Mantendo o segundo (último processado).");
        return segundo;
    }
    
    /**
     * Deduplica lista de cotações removendo registros duplicados da API.
     * Usa sequence_code como chave única (PRIMARY KEY da tabela).
     * 
     * Estratégia: "Keep Last" - mantém o registro mais recente baseado em requestedAt.
     * 
     * @param cotacoes Lista de cotações a deduplicar
     * @return Lista deduplicada de cotações
     */
    public static List<CotacaoEntity> deduplicarCotacoes(final List<CotacaoEntity> cotacoes) {
        if (cotacoes == null || cotacoes.isEmpty()) {
            return cotacoes;
        }
        
        return cotacoes.stream()
            .collect(Collectors.toMap(
                c -> {
                    // Chave única: sequence_code
                    if (c.getSequenceCode() == null) {
                        throw new IllegalStateException("Cotação com sequence_code NULL não pode ser deduplicada");
                    }
                    return c.getSequenceCode();
                },
                c -> c,
                (primeiro, segundo) -> {
                    // ✅ ESTRATÉGIA "KEEP LAST": Manter o registro mais recente
                    final CotacaoEntity maisRecente = obterMaisRecenteCotacao(primeiro, segundo);
                    logger.warn("⚠️ Duplicado detectado na API: sequence_code={}. Mantendo o mais recente (requestedAt).", 
                        segundo.getSequenceCode());
                    return maisRecente;
                }
            ))
            .values()
            .stream()
            .collect(Collectors.toList());
    }
    
    /**
     * Determina qual cotação é mais recente baseado em requestedAt.
     */
    private static CotacaoEntity obterMaisRecenteCotacao(final CotacaoEntity primeiro, final CotacaoEntity segundo) {
        final OffsetDateTime requestedAt1 = primeiro.getRequestedAt();
        final OffsetDateTime requestedAt2 = segundo.getRequestedAt();
        if (requestedAt1 != null && requestedAt2 != null) {
            return requestedAt1.isAfter(requestedAt2) ? primeiro : segundo;
        }
        if (requestedAt2 != null) {
            return segundo;
        }
        if (requestedAt1 != null) {
            return primeiro;
        }
        // Fallback: manter o segundo (último processado)
        return segundo;
    }
    
    /**
     * Deduplica lista de localizações removendo registros duplicados da API.
     * Usa sequence_number como chave única (PRIMARY KEY da tabela).
     * 
     * Estratégia: "Keep Last" - mantém o registro mais recente baseado em serviceAt.
     * 
     * @param localizacoes Lista de localizações a deduplicar
     * @return Lista deduplicada de localizações
     */
    public static List<LocalizacaoCargaEntity> deduplicarLocalizacoes(final List<LocalizacaoCargaEntity> localizacoes) {
        if (localizacoes == null || localizacoes.isEmpty()) {
            return localizacoes;
        }
        
        return localizacoes.stream()
            .collect(Collectors.toMap(
                l -> {
                    // Chave única: sequence_number
                    if (l.getSequenceNumber() == null) {
                        throw new IllegalStateException("Localização com sequence_number NULL não pode ser deduplicada");
                    }
                    return l.getSequenceNumber();
                },
                l -> l,
                (primeiro, segundo) -> {
                    // ✅ ESTRATÉGIA "KEEP LAST": Manter o registro mais recente
                    final LocalizacaoCargaEntity maisRecente = obterMaisRecenteLocalizacao(primeiro, segundo);
                    logger.warn("⚠️ Duplicado detectado na API: sequence_number={}. Mantendo o mais recente (serviceAt).", 
                        segundo.getSequenceNumber());
                    return maisRecente;
                }
            ))
            .values()
            .stream()
            .collect(Collectors.toList());
    }
    
    /**
     * Determina qual localização é mais recente baseado em serviceAt.
     */
    private static LocalizacaoCargaEntity obterMaisRecenteLocalizacao(
            final LocalizacaoCargaEntity primeiro, 
            final LocalizacaoCargaEntity segundo) {
        final OffsetDateTime serviceAt1 = primeiro.getServiceAt();
        final OffsetDateTime serviceAt2 = segundo.getServiceAt();
        if (serviceAt1 != null && serviceAt2 != null) {
            return serviceAt1.isAfter(serviceAt2) ? primeiro : segundo;
        }
        if (serviceAt2 != null) {
            return segundo;
        }
        if (serviceAt1 != null) {
            return primeiro;
        }
        // Fallback: manter o segundo (último processado)
        return segundo;
    }

    /**
     * Deduplica Faturas a Pagar por sequence_code (chave primária).
     * 
     * Estratégia: "Keep Last" - mantém o último processado (sem timestamp específico).
     */
    public static List<ContasAPagarDataExportEntity> deduplicarFaturasAPagar(final List<ContasAPagarDataExportEntity> lista) {
        if (lista == null || lista.isEmpty()) {
            return lista;
        }

        return lista.stream()
            .collect(Collectors.toMap(
                e -> {
                    if (e.getSequenceCode() == null) {
                        throw new IllegalStateException("Fatura a pagar com sequence_code NULL não pode ser deduplicada");
                    }
                    return e.getSequenceCode();
                },
                e -> e,
                (primeiro, segundo) -> {
                    // ✅ ESTRATÉGIA "KEEP LAST": Manter o segundo (último processado)
                    logger.warn("⚠️ Duplicado detectado: sequence_code={}. Mantendo o último processado.", 
                        segundo.getSequenceCode());
                    return segundo;
                }
            ))
            .values()
            .stream()
            .collect(Collectors.toList());
    }

    /**
     * Deduplica Faturas por Cliente por unique_id (chave primária).
     * 
     * Estratégia: "Keep Last" - mantém o último processado (sem timestamp específico).
     */
    public static List<FaturaPorClienteEntity> deduplicarFaturasPorCliente(final List<FaturaPorClienteEntity> lista) {
        if (lista == null || lista.isEmpty()) {
            return lista;
        }

        return lista.stream()
            .collect(Collectors.toMap(
                e -> {
                    if (e.getUniqueId() == null || e.getUniqueId().trim().isEmpty()) {
                        throw new IllegalStateException(
                            "Fatura por cliente com unique_id NULL não pode ser deduplicada");
                    }
                    return e.getUniqueId();
                },
                e -> e,
                (primeiro, segundo) -> {
                    // ✅ ESTRATÉGIA "KEEP LAST": Manter o segundo (último processado)
                    logger.warn("⚠️ Duplicado detectado: unique_id={}. Mantendo o último processado.", 
                        segundo.getUniqueId());
                    return segundo;
                }
            ))
            .values()
            .stream()
            .collect(Collectors.toList());
    }
}
