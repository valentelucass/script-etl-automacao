/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/graphql/extractors/FreteExtractor.java
Classe  : FreteExtractor (class)
Pacote  : br.com.extrator.runners.graphql.extractors
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

package br.com.extrator.runners.graphql.extractors;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.api.ClienteApiGraphQL;
import br.com.extrator.api.ResultadoExtracao;
import br.com.extrator.db.entity.FreteEntity;
import br.com.extrator.db.repository.FreteRepository;
import br.com.extrator.modelo.graphql.fretes.FreteMapper;
import br.com.extrator.modelo.graphql.fretes.FreteNodeDTO;
import br.com.extrator.runners.common.ConstantesExtracao;
import br.com.extrator.runners.common.EntityExtractor;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Extractor para entidade Fretes (GraphQL).
 * 
 * @since 2.3.2 - Adicionada deduplicação preventiva por ID
 */
public class FreteExtractor implements EntityExtractor<FreteNodeDTO> {
    private static final Logger logger = LoggerFactory.getLogger(FreteExtractor.class);
    
    private final ClienteApiGraphQL apiClient;
    private final FreteRepository repository;
    private final FreteMapper mapper;
    
    public FreteExtractor(final ClienteApiGraphQL apiClient,
                         final FreteRepository repository,
                         final FreteMapper mapper) {
        this.apiClient = apiClient;
        this.repository = repository;
        this.mapper = mapper;
    }
    
    @Override
    public ResultadoExtracao<FreteNodeDTO> extract(final LocalDate dataInicio, final LocalDate dataFim) {
        return apiClient.buscarFretes(dataInicio, dataFim);
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
    private List<FreteEntity> deduplicarPorId(final List<FreteEntity> entities) {
        return entities.stream()
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
    
    @Override
    public String getEntityName() {
        return ConstantesEntidades.FRETES;
    }
    
    @Override
    public String getEmoji() {
        return ConstantesExtracao.EMOJI_FRETES;
    }
}
