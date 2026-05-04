/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/graphql/extractors/FreteExtractor.java
Classe  : FreteExtractor (class)
Pacote  : br.com.extrator.integracao.graphql.extractors
Modulo  : Extractor GraphQL
Papel   : Implementa responsabilidade de frete extractor.

Conecta com:
- ClienteApiGraphQL (api)
- ResultadoExtracao (api)
- FreteEntity (db.entity)
- FreteRepository (db.repository)
- FreteMapper (modelo.graphql.fretes)
- FreteNodeDTO (modelo.graphql.fretes)
- ConstantesExtracao (runners.common)
- EntityExtractor (runners.common)

Fluxo geral:
1) Configura query e parametros para entidade alvo.
2) Invoca cliente GraphQL com paginacao segura.
3) Encaminha dados para camada de persistencia.

Estrutura interna:
Metodos principais:
- FreteExtractor(...3 args): realiza operacao relacionada a "frete extractor".
- extract(...2 args): realiza operacao relacionada a "extract".
- deduplicarPorId(...1 args): realiza operacao relacionada a "deduplicar por id".
- getEntityName(): expone valor atual do estado interno.
- getEmoji(): expone valor atual do estado interno.
Atributos-chave:
- logger: logger da classe para diagnostico.
- apiClient: cliente de integracao externa.
- repository: dependencia de acesso a banco.
- mapper: apoio de mapeamento de dados.
[DOC-FILE-END]============================================================== */

package br.com.extrator.integracao.graphql.extractors;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.dominio.dataexport.fretes.FreteIndicadorDTO;
import br.com.extrator.dominio.graphql.fretes.FreteNodeDTO;
import br.com.extrator.integracao.ClienteApiGraphQL;
import br.com.extrator.integracao.ResultadoExtracao;
import br.com.extrator.integracao.comum.ConstantesExtracao;
import br.com.extrator.integracao.comum.EntityExtractor;
import br.com.extrator.integracao.mapeamento.graphql.fretes.FreteMapper;
import br.com.extrator.persistencia.entidade.FreteEntity;
import br.com.extrator.persistencia.repositorio.FreteRepository;
import br.com.extrator.suporte.configuracao.ConfigApi;
import br.com.extrator.suporte.configuracao.ConfigEtl;
import br.com.extrator.suporte.formatacao.FormatadorData;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

/**
 * Extractor para entidade Fretes (GraphQL).
 * 
 * @since 2.3.2 - Adicionada deduplicação preventiva por ID
 */
public class FreteExtractor implements EntityExtractor<FreteNodeDTO> {
    private static final Logger logger = LoggerFactory.getLogger(FreteExtractor.class);
    private static final int MAX_DIAS_POR_REQUISICAO = 30;
    private static final DateTimeFormatter[] FORMATOS_BR_DATA_HORA = {
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    };

    @FunctionalInterface
    public interface FreteIndicadoresProvider {
        ResultadoExtracao<FreteIndicadorDTO> buscar(LocalDate dataInicio, LocalDate dataFim);
    }

    @FunctionalInterface
    interface FreteApiProvider {
        ResultadoExtracao<FreteNodeDTO> buscar(LocalDate dataInicio, LocalDate dataFim);
    }

    record PeriodoConsulta(LocalDate dataInicio, LocalDate dataFim) {
    }

    private final FreteApiProvider freteApiProvider;
    private final FreteIndicadoresProvider freteIndicadoresProvider;
    private final FreteRepository repository;
    private final FreteMapper mapper;
    private LocalDate ultimaDataInicio;
    private LocalDate ultimaDataFim;
    private boolean ultimaExtracaoCompleta;

    public FreteExtractor(final ClienteApiGraphQL apiClient,
                          final FreteRepository repository,
                          final FreteMapper mapper) {
        this(apiClient, repository, mapper, null);
    }

    public FreteExtractor(final ClienteApiGraphQL apiClient,
                          final FreteRepository repository,
                          final FreteMapper mapper,
                          final FreteIndicadoresProvider freteIndicadoresProvider) {
        this(apiClient == null ? null : apiClient::buscarFretes, repository, mapper, freteIndicadoresProvider, true);
    }

    FreteExtractor(final FreteApiProvider freteApiProvider,
                   final FreteRepository repository,
                   final FreteMapper mapper,
                   final FreteIndicadoresProvider freteIndicadoresProvider,
                   final boolean usarProviderDireto) {
        this.freteApiProvider = freteApiProvider;
        this.freteIndicadoresProvider = freteIndicadoresProvider;
        this.repository = repository;
        this.mapper = mapper;
    }
    
    @Override
    public ResultadoExtracao<FreteNodeDTO> extract(final LocalDate dataInicio, final LocalDate dataFim) {
        final LocalDate dataInicioConsulta = calcularDataInicioConsulta(dataInicio);
        if (dataInicioConsulta != null && !dataInicioConsulta.equals(dataInicio)) {
            logger.info(
                "Expandindo janela de fretes para Performance: {} a {} consultado como {} a {}",
                dataInicio,
                dataFim,
                dataInicioConsulta,
                dataFim
            );
        }
        final ResultadoExtracao<FreteNodeDTO> resultado = buscarFretesComLimiteDeJanela(dataInicioConsulta, dataFim);
        registrarUltimaExtracao(dataInicioConsulta, dataFim, resultado != null && resultado.isCompleto());
        return resultado;
    }
    
    @Override
    public int save(final List<FreteNodeDTO> dtos) throws java.sql.SQLException {
        return saveWithMetrics(dtos).getRegistrosSalvos();
    }

    @Override
    public EntityExtractor.SaveMetrics saveWithMetrics(final List<FreteNodeDTO> dtos) throws java.sql.SQLException {
        if (dtos == null || dtos.isEmpty()) {
            return new EntityExtractor.SaveMetrics(0, 0, 0);
        }
        
        final List<FreteEntity> entities = dtos.stream()
            .map(mapper::toEntity)
            .collect(Collectors.toList());
        
        // Deduplicação preventiva por ID (Keep Last)
        final List<FreteEntity> entitiesUnicos = deduplicarPorId(entities);
        
        if (entities.size() != entitiesUnicos.size()) {
            logger.warn("⚠️ Duplicados removidos na API GraphQL (Fretes): {} duplicados", 
                entities.size() - entitiesUnicos.size());
        }

        enriquecerComIndicadoresDataExport(entitiesUnicos);
        
        final int registrosSalvos = repository.salvar(entitiesUnicos);
        if (devePrunarAusentesNoPeriodo()) {
            final int removidos = repository.removerAusentesNoPeriodo(
                ultimaDataInicio,
                ultimaDataFim,
                entitiesUnicos.stream()
                    .map(FreteEntity::getId)
                    .filter(Objects::nonNull)
                    .toList()
            );
            if (removidos > 0) {
                logger.warn(
                    "Reconciliacao por periodo removeu {} frete(s) ausente(s) na API para {} a {}",
                    removidos,
                    ultimaDataInicio,
                    ultimaDataFim
                );
            }
        }
        return new EntityExtractor.SaveMetrics(
            registrosSalvos,
            entitiesUnicos.size(),
            0,
            repository.getUltimoResumoSalvamento().getRegistrosPersistidos(),
            repository.getUltimoResumoSalvamento().getRegistrosNoOpIdempotente()
        );
    }
    
    /**
     * Deduplica entidades por ID, mantendo o último (Keep Last).
     * 
     * Proteção preventiva contra duplicados na resposta da API GraphQL.
     * 
     * @param entities Lista de entidades
     * @return Lista deduplicada
     * @since 2.3.2
     */
    private List<FreteEntity> deduplicarPorId(final List<FreteEntity> entities) {
        return entities.stream()
            .peek(this::validarChavePrimaria)
            .collect(Collectors.toMap(
                FreteEntity::getId,
                e -> e,
                (primeiro, segundo) -> {
                    logger.warn("⚠️ Duplicado detectado na API GraphQL: id={}. Mantendo o último.", 
                        primeiro.getId());
                    return segundo; // Keep Last
                }
            ))
            .values()
            .stream()
            .collect(Collectors.toList());
    }

    private void validarChavePrimaria(final FreteEntity entity) {
        if (entity == null || entity.getId() == null) {
            throw new IllegalStateException("Frete sem ID estavel nao pode ser persistido.");
        }
    }

    void registrarUltimaExtracaoParaTeste(final LocalDate dataInicio,
                                          final LocalDate dataFim,
                                          final boolean completa) {
        registrarUltimaExtracao(dataInicio, dataFim, completa);
    }

    static LocalDate calcularDataInicioConsulta(final LocalDate dataInicio) {
        return calcularDataInicioConsulta(dataInicio, ConfigEtl.obterFretesPerformanceLookbackDiasEfetivo());
    }

    static LocalDate calcularDataInicioConsulta(final LocalDate dataInicio, final int lookbackDias) {
        if (dataInicio == null) {
            return null;
        }
        if (lookbackDias <= 0) {
            return dataInicio;
        }
        return dataInicio.minusDays(lookbackDias);
    }

    static List<PeriodoConsulta> dividirJanelaEmBlocos(final LocalDate dataInicio, final LocalDate dataFim) {
        if (dataInicio == null || dataFim == null || dataFim.isBefore(dataInicio)) {
            return List.of(new PeriodoConsulta(dataInicio, dataFim));
        }

        final List<PeriodoConsulta> blocos = new ArrayList<>();
        LocalDate inicioBloco = dataInicio;
        while (!inicioBloco.isAfter(dataFim)) {
            final LocalDate fimMaximo = inicioBloco.plusDays(MAX_DIAS_POR_REQUISICAO - 1L);
            final LocalDate fimBloco = fimMaximo.isAfter(dataFim) ? dataFim : fimMaximo;
            blocos.add(new PeriodoConsulta(inicioBloco, fimBloco));
            inicioBloco = fimBloco.plusDays(1);
        }
        return List.copyOf(blocos);
    }

    private ResultadoExtracao<FreteNodeDTO> buscarFretesComLimiteDeJanela(final LocalDate dataInicio,
                                                                          final LocalDate dataFim) {
        if (freteApiProvider == null) {
            throw new IllegalStateException("Cliente GraphQL de fretes nao configurado.");
        }

        final List<PeriodoConsulta> blocos = dividirJanelaEmBlocos(dataInicio, dataFim);
        if (blocos.size() == 1) {
            final PeriodoConsulta periodo = blocos.get(0);
            return freteApiProvider.buscar(periodo.dataInicio(), periodo.dataFim());
        }

        logger.info(
            "Dividindo consulta GraphQL de fretes em {} bloco(s) de ate {} dias: {} a {}",
            blocos.size(),
            MAX_DIAS_POR_REQUISICAO,
            dataInicio,
            dataFim
        );

        final List<FreteNodeDTO> dados = new ArrayList<>();
        int paginasProcessadas = 0;
        int registrosExtraidos = 0;
        boolean completo = true;
        String motivoInterrupcao = null;

        for (final PeriodoConsulta bloco : blocos) {
            logger.info(
                "Consultando bloco GraphQL de fretes: {} a {}",
                bloco.dataInicio(),
                bloco.dataFim()
            );
            final ResultadoExtracao<FreteNodeDTO> resultadoBloco =
                freteApiProvider.buscar(bloco.dataInicio(), bloco.dataFim());
            if (resultadoBloco == null) {
                completo = false;
                motivoInterrupcao = selecionarMotivoInterrupcao(
                    motivoInterrupcao,
                    ResultadoExtracao.MotivoInterrupcao.ERRO_API.getCodigo()
                );
                continue;
            }
            dados.addAll(resultadoBloco.getDados());
            paginasProcessadas += resultadoBloco.getPaginasProcessadas();
            registrosExtraidos += resultadoBloco.getRegistrosExtraidos();
            if (!resultadoBloco.isCompleto()) {
                completo = false;
                motivoInterrupcao = selecionarMotivoInterrupcao(
                    motivoInterrupcao,
                    resultadoBloco.getMotivoInterrupcao()
                );
            }
        }

        return completo
            ? ResultadoExtracao.completo(dados, paginasProcessadas, registrosExtraidos)
            : ResultadoExtracao.incompleto(
                dados,
                motivoInterrupcao == null
                    ? ResultadoExtracao.MotivoInterrupcao.ERRO_API.getCodigo()
                    : motivoInterrupcao,
                paginasProcessadas,
                registrosExtraidos
            );
    }

    private String selecionarMotivoInterrupcao(final String atual, final String candidato) {
        if (candidato == null || candidato.isBlank()) {
            return atual;
        }
        if (ResultadoExtracao.MotivoInterrupcao.ERRO_API.getCodigo().equals(candidato)
            || ResultadoExtracao.MotivoInterrupcao.CIRCUIT_BREAKER.getCodigo().equals(candidato)
            || ResultadoExtracao.MotivoInterrupcao.LACUNA_PAGINACAO_422.getCodigo().equals(candidato)
            || ResultadoExtracao.MotivoInterrupcao.PAGINA_VAZIA_INESPERADA.getCodigo().equals(candidato)) {
            return candidato;
        }
        return (atual == null || atual.isBlank()) ? candidato : atual;
    }

    private void registrarUltimaExtracao(final LocalDate dataInicio,
                                         final LocalDate dataFim,
                                         final boolean completa) {
        this.ultimaDataInicio = dataInicio;
        this.ultimaDataFim = dataFim;
        this.ultimaExtracaoCompleta = completa;
    }

    private boolean devePrunarAusentesNoPeriodo() {
        return ConfigEtl.isPruneAusentesFretesAtivo()
            && ultimaExtracaoCompleta
            && ultimaDataInicio != null
            && ultimaDataFim != null
            && !ultimaDataFim.isBefore(ultimaDataInicio);
    }

    private void enriquecerComIndicadoresDataExport(final List<FreteEntity> entities) {
        if (freteIndicadoresProvider == null || entities == null || entities.isEmpty()) {
            return;
        }
        if (ultimaDataInicio == null || ultimaDataFim == null) {
            logger.debug("Enriquecimento de fretes ignorado: janela da ultima extracao nao foi registrada.");
            return;
        }

        final ResultadoExtracao<FreteIndicadorDTO> resultadoIndicadores;
        try {
            resultadoIndicadores = freteIndicadoresProvider.buscar(ultimaDataInicio, ultimaDataFim);
        } catch (final RuntimeException e) {
            logger.warn(
                "Falha ao enriquecer fretes com Data Export 6389 para {} a {}: {}",
                ultimaDataInicio,
                ultimaDataFim,
                e.getMessage()
            );
            return;
        }

        if (resultadoIndicadores == null
            || resultadoIndicadores.getDados() == null
            || resultadoIndicadores.getDados().isEmpty()) {
            logger.info(
                "Enriquecimento de fretes via Data Export 6389 sem dados para {} a {}",
                ultimaDataInicio,
                ultimaDataFim
            );
            return;
        }

        final Map<Long, FreteIndicadorDTO> indicadoresPorMinuta = indexarIndicadores(resultadoIndicadores.getDados());
        int totalEnriquecidos = 0;
        int totalPerformanceOficial = 0;
        int totalFinishedAtFallback = 0;

        for (final FreteEntity entity : entities) {
            if (entity == null || entity.getCorporationSequenceNumber() == null) {
                continue;
            }
            final FreteIndicadorDTO indicador = indicadoresPorMinuta.get(entity.getCorporationSequenceNumber());
            if (indicador == null) {
                continue;
            }

            boolean alterado = false;
            final OffsetDateTime performanceFinishedAt =
                parsePerformanceFinishedAt(indicador.getPerformanceFinishedAt(), entity);
            if (performanceFinishedAt != null) {
                entity.setFitDpnPerformanceFinishedAt(performanceFinishedAt);
                totalPerformanceOficial++;
                alterado = true;
            }

            if (entity.getFinishedAt() == null) {
                final OffsetDateTime finishedAt = FormatadorData.parseOffsetDateTime(indicador.getFinishedAt());
                if (finishedAt != null) {
                    entity.setFinishedAt(finishedAt);
                    totalFinishedAtFallback++;
                    alterado = true;
                }
            }

            if (alterado) {
                totalEnriquecidos++;
            }
        }

        if (!resultadoIndicadores.isCompleto()) {
            logger.warn(
                "Enriquecimento de fretes via Data Export 6389 retornou parcialmente para {} a {} | motivo={} | paginas={}",
                ultimaDataInicio,
                ultimaDataFim,
                resultadoIndicadores.getMotivoInterrupcao(),
                resultadoIndicadores.getPaginasProcessadas()
            );
        }

        logger.info(
            "Enriquecimento de fretes via Data Export 6389 concluido para {} a {} | correspondencias={} | performance_oficial={} | finished_at_fallback={}",
            ultimaDataInicio,
            ultimaDataFim,
            totalEnriquecidos,
            totalPerformanceOficial,
            totalFinishedAtFallback
        );
    }

    private Map<Long, FreteIndicadorDTO> indexarIndicadores(final List<FreteIndicadorDTO> indicadores) {
        final Map<Long, FreteIndicadorDTO> index = new LinkedHashMap<>();
        for (final FreteIndicadorDTO indicador : indicadores) {
            if (indicador == null || indicador.getCorporationSequenceNumber() == null) {
                continue;
            }
            final FreteIndicadorDTO atual = index.get(indicador.getCorporationSequenceNumber());
            if (atual == null || deveSubstituirIndicador(atual, indicador)) {
                index.put(indicador.getCorporationSequenceNumber(), indicador);
            }
        }
        return index;
    }

    private boolean deveSubstituirIndicador(final FreteIndicadorDTO atual, final FreteIndicadorDTO candidato) {
        final boolean atualTemPerformance =
            atual.getPerformanceFinishedAt() != null && !atual.getPerformanceFinishedAt().isBlank();
        final boolean candidatoTemPerformance =
            candidato.getPerformanceFinishedAt() != null && !candidato.getPerformanceFinishedAt().isBlank();
        if (candidatoTemPerformance && !atualTemPerformance) {
            return true;
        }
        final boolean atualTemFinishedAt = atual.getFinishedAt() != null && !atual.getFinishedAt().isBlank();
        final boolean candidatoTemFinishedAt = candidato.getFinishedAt() != null && !candidato.getFinishedAt().isBlank();
        return candidatoTemFinishedAt && !atualTemFinishedAt;
    }

    private OffsetDateTime parsePerformanceFinishedAt(final String valorBruto, final FreteEntity entity) {
        final OffsetDateTime parsed = FormatadorData.parseOffsetDateTime(valorBruto);
        if (valorBruto == null || valorBruto.isBlank()) {
            return parsed;
        }
        if (parsed == null || entity == null || entity.getServicoEm() == null || !valorBruto.contains("/")) {
            return parsed;
        }

        final OffsetDateTime parsedBr = parseOffsetDateTimeBr(valorBruto);
        if (parsedBr == null) {
            return parsed;
        }

        if (parsed.toLocalDate().isBefore(entity.getServicoEm().toLocalDate())
            && !parsedBr.toLocalDate().isBefore(entity.getServicoEm().toLocalDate())) {
            logger.warn(
                "Performance oficial ajustada para formato BR em frete/minuta {}: valor='{}' | parser_padrao={} | parser_br={}",
                entity.getCorporationSequenceNumber(),
                valorBruto,
                parsed,
                parsedBr
            );
            return parsedBr;
        }

        return parsed;
    }

    private OffsetDateTime parseOffsetDateTimeBr(final String valor) {
        final java.time.ZoneId zoneId = ConfigApi.obterZoneIdDataExport();
        for (final DateTimeFormatter formatter : FORMATOS_BR_DATA_HORA) {
            try {
                return java.time.LocalDateTime.parse(valor, formatter).atZone(zoneId).toOffsetDateTime();
            } catch (final DateTimeParseException ignored) {
                // tenta o proximo formato BR
            }
        }
        return null;
    }
    
    @Override
    public String getEntityName() {
        return ConstantesEntidades.FRETES;
    }
    
    @Override
    public String getEmoji() {
        return ConstantesExtracao.EMOJI_FRETES;
    }
}
