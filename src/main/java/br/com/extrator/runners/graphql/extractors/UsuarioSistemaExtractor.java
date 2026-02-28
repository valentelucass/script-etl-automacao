/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/graphql/extractors/UsuarioSistemaExtractor.java
Classe  : UsuarioSistemaExtractor (class)
Pacote  : br.com.extrator.runners.graphql.extractors
Modulo  : Extractor GraphQL
Papel   : Implementa responsabilidade de usuario sistema extractor.

Conecta com:
- ClienteApiGraphQL (api)
- ResultadoExtracao (api)
- UsuarioSistemaEntity (db.entity)
- UsuarioSistemaRepository (db.repository)
- IndividualNodeDTO (modelo.graphql.usuarios)
- UsuarioSistemaMapper (modelo.graphql.usuarios)
- ConstantesExtracao (runners.common)
- EntityExtractor (runners.common)

Fluxo geral:
1) Configura query e parametros para entidade alvo.
2) Invoca cliente GraphQL com paginacao segura.
3) Encaminha dados para camada de persistencia.

Estrutura interna:
Metodos principais:
- UsuarioSistemaExtractor(...3 args): realiza operacao relacionada a "usuario sistema extractor".
- extract(...2 args): realiza operacao relacionada a "extract".
- deduplicarPorUserId(...1 args): realiza operacao relacionada a "deduplicar por user id".
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
import br.com.extrator.db.entity.UsuarioSistemaEntity;
import br.com.extrator.db.repository.UsuarioSistemaRepository;
import br.com.extrator.modelo.graphql.usuarios.IndividualNodeDTO;
import br.com.extrator.modelo.graphql.usuarios.UsuarioSistemaMapper;
import br.com.extrator.runners.common.ConstantesExtracao;
import br.com.extrator.runners.common.EntityExtractor;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Extractor para entidade Usuários do Sistema (Individual - GraphQL).
 * Não utiliza filtro de data, apenas filtra por enabled: true.
 * Deduplica por user_id (Keep Last) antes de salvar para que o log e o banco batam na validação API vs banco.
 */
public class UsuarioSistemaExtractor implements EntityExtractor<IndividualNodeDTO> {

    private static final Logger logger = LoggerFactory.getLogger(UsuarioSistemaExtractor.class);

    private final ClienteApiGraphQL apiClient;
    private final UsuarioSistemaRepository repository;
    private final UsuarioSistemaMapper mapper;

    public UsuarioSistemaExtractor(final ClienteApiGraphQL apiClient,
                                  final UsuarioSistemaRepository repository,
                                  final UsuarioSistemaMapper mapper) {
        this.apiClient = apiClient;
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public ResultadoExtracao<IndividualNodeDTO> extract(final LocalDate dataInicio, final LocalDate dataFim) {
        // Usuários não usam filtro de data, apenas enabled: true
        return apiClient.buscarUsuariosSistema();
    }

    @Override
    public int save(final List<IndividualNodeDTO> dtos) throws java.sql.SQLException {
        return saveWithMetrics(dtos).getRegistrosSalvos();
    }

    @Override
    public EntityExtractor.SaveMetrics saveWithMetrics(final List<IndividualNodeDTO> dtos) throws java.sql.SQLException {
        if (dtos == null || dtos.isEmpty()) {
            return new EntityExtractor.SaveMetrics(0, 0, 0);
        }

        final List<UsuarioSistemaEntity> entities = dtos.stream()
            .map(mapper::toEntity)
            .collect(Collectors.toList());

        final List<UsuarioSistemaEntity> unicos = deduplicarPorUserId(entities);
        if (unicos.size() < entities.size()) {
            logger.warn("⚠️ usuarios_sistema: {} nós da API, {} user_id únicos (duplicados removidos).",
                entities.size(), unicos.size());
        }
        final int registrosSalvos = repository.salvar(unicos);
        return new EntityExtractor.SaveMetrics(registrosSalvos, unicos.size(), 0);
    }

    /**
     * Deduplica por user_id (Keep Last) para que contagem no banco e no log coincidam.
     */
    private List<UsuarioSistemaEntity> deduplicarPorUserId(final List<UsuarioSistemaEntity> entities) {
        return entities.stream()
            .collect(Collectors.toMap(
                UsuarioSistemaEntity::getUserId,
                e -> e,
                (a, b) -> b
            ))
            .values()
            .stream()
            .collect(Collectors.toList());
    }
    
    @Override
    public String getEntityName() {
        return ConstantesEntidades.USUARIOS_SISTEMA;
    }
    
    @Override
    public String getEmoji() {
        return ConstantesExtracao.EMOJI_USUARIOS;
    }
}
