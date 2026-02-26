package br.com.extrator.runners.graphql.extractors;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.api.ClienteApiGraphQL;
import br.com.extrator.api.ResultadoExtracao;
import br.com.extrator.db.entity.ColetaEntity;
import br.com.extrator.db.repository.ColetaRepository;
import br.com.extrator.modelo.graphql.coletas.ColetaMapper;
import br.com.extrator.modelo.graphql.coletas.ColetaNodeDTO;
import br.com.extrator.runners.common.ConstantesExtracao;
import br.com.extrator.runners.common.EntityExtractor;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Extractor para entidade Coletas (GraphQL).
 * 
 * @since 2.3.2 - Adicionada deduplicação preventiva por ID
 */
public class ColetaExtractor implements EntityExtractor<ColetaNodeDTO> {
    private static final Logger logger = LoggerFactory.getLogger(ColetaExtractor.class);
    
    private final ClienteApiGraphQL apiClient;
    private final ColetaRepository repository;
    private final ColetaMapper mapper;
    
    public ColetaExtractor(final ClienteApiGraphQL apiClient,
                          final ColetaRepository repository,
                          final ColetaMapper mapper) {
        this.apiClient = apiClient;
        this.repository = repository;
        this.mapper = mapper;
    }
    
    @Override
    public ResultadoExtracao<ColetaNodeDTO> extract(final LocalDate dataInicio, final LocalDate dataFim) {
        return apiClient.buscarColetas(dataInicio, dataFim);
    }
    
    @Override
    public int save(final List<ColetaNodeDTO> dtos) throws java.sql.SQLException {
        return saveWithMetrics(dtos).getRegistrosSalvos();
    }

    @Override
    public EntityExtractor.SaveMetrics saveWithMetrics(final List<ColetaNodeDTO> dtos) throws java.sql.SQLException {
        if (dtos == null || dtos.isEmpty()) {
            return new EntityExtractor.SaveMetrics(0, 0, 0);
        }
        
        final List<ColetaEntity> entities = dtos.stream()
            .map(mapper::toEntity)
            .collect(Collectors.toList());
        
        // Deduplicação preventiva por ID (Keep Last)
        final List<ColetaEntity> entitiesUnicos = deduplicarPorId(entities);
        
        if (entities.size() != entitiesUnicos.size()) {
            logger.warn("⚠️ Duplicados removidos na API GraphQL (Coletas): {} duplicados", 
                entities.size() - entitiesUnicos.size());
        }
        
        final int registrosSalvos = repository.salvar(entitiesUnicos);
        return new EntityExtractor.SaveMetrics(registrosSalvos, entitiesUnicos.size(), 0);
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
    private List<ColetaEntity> deduplicarPorId(final List<ColetaEntity> entities) {
        return entities.stream()
            .collect(Collectors.toMap(
                ColetaEntity::getId,
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
    
    @Override
    public String getEntityName() {
        return ConstantesEntidades.COLETAS;
    }
    
    @Override
    public String getEmoji() {
        return ConstantesExtracao.EMOJI_COLETAS;
    }
}
