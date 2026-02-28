/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/dataexport/extractors/ManifestoExtractor.java
Classe  : ManifestoExtractor (class)
Pacote  : br.com.extrator.runners.dataexport.extractors
Modulo  : Extractor DataExport
Papel   : Implementa responsabilidade de manifesto extractor.

Conecta com:
- ClienteApiDataExport (api)
- ResultadoExtracao (api)
- ManifestoEntity (db.entity)
- InvalidRecordAuditRepository (db.repository)
- ManifestoRepository (db.repository)
- ManifestoDTO (modelo.dataexport.manifestos)
- ManifestoMapper (modelo.dataexport.manifestos)
- ConstantesExtracao (runners.common)

Fluxo geral:
1) Configura requisicao da API DataExport.
2) Converte resposta em DTO/entidade de dominio.
3) Persiste lote no repositorio correspondente.

Estrutura interna:
Metodos principais:
- ManifestoExtractor(...4 args): realiza operacao relacionada a "manifesto extractor".
- extract(...2 args): realiza operacao relacionada a "extract".
- getEntityName(): expone valor atual do estado interno.
- getEmoji(): expone valor atual do estado interno.
- auditarRegistroInvalido(...3 args): realiza operacao relacionada a "auditar registro invalido".
Atributos-chave:
- apiClient: cliente de integracao externa.
- repository: dependencia de acesso a banco.
- mapper: apoio de mapeamento de dados.
- log: campo de estado para "log".
- invalidRecordAuditRepository: dependencia de acesso a banco.
[DOC-FILE-END]============================================================== */

package br.com.extrator.runners.dataexport.extractors;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import br.com.extrator.api.ClienteApiDataExport;
import br.com.extrator.api.ResultadoExtracao;
import br.com.extrator.db.entity.ManifestoEntity;
import br.com.extrator.db.repository.InvalidRecordAuditRepository;
import br.com.extrator.db.repository.ManifestoRepository;
import br.com.extrator.modelo.dataexport.manifestos.ManifestoDTO;
import br.com.extrator.modelo.dataexport.manifestos.ManifestoMapper;
import br.com.extrator.runners.common.ConstantesExtracao;
import br.com.extrator.runners.common.DataExportEntityExtractor;
import br.com.extrator.runners.dataexport.services.Deduplicator;
import br.com.extrator.util.console.LoggerConsole;
import br.com.extrator.util.mapeamento.MapperUtil;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Extractor para entidade Manifestos (DataExport).
 * Inclui deduplicação antes de salvar.
 */
public class ManifestoExtractor implements DataExportEntityExtractor<ManifestoDTO> {
    
    private final ClienteApiDataExport apiClient;
    private final ManifestoRepository repository;
    private final ManifestoMapper mapper;
    private final LoggerConsole log;
    private final InvalidRecordAuditRepository invalidRecordAuditRepository;
    
    public ManifestoExtractor(final ClienteApiDataExport apiClient,
                             final ManifestoRepository repository,
                             final ManifestoMapper mapper,
                             final LoggerConsole log) {
        this.apiClient = apiClient;
        this.repository = repository;
        this.mapper = mapper;
        this.log = log;
        this.invalidRecordAuditRepository = new InvalidRecordAuditRepository();
    }
    
    @Override
    public ResultadoExtracao<ManifestoDTO> extract(final LocalDate dataInicio, final LocalDate dataFim) {
        // Usa intervalo informado quando disponível; fallback para últimas 24h
        if (dataInicio != null) {
            final LocalDate fim = (dataFim != null) ? dataFim : dataInicio;
            return apiClient.buscarManifestos(dataInicio, fim);
        }
        return apiClient.buscarManifestos();
    }
    
    @Override
    public SaveResult saveWithDeduplication(final List<ManifestoDTO> dtos) throws java.sql.SQLException {
        if (dtos == null || dtos.isEmpty()) {
            return new SaveResult(0, 0);
        }
        
        final List<ManifestoEntity> entities = new ArrayList<>();
        int registrosInvalidos = 0;
        for (final ManifestoDTO dto : dtos) {
            try {
                final ManifestoEntity entity = mapper.toEntity(dto);
                if (entity != null) {
                    entities.add(entity);
                } else {
                    registrosInvalidos++;
                    auditarRegistroInvalido(dto, "MAPPER_RETORNOU_NULL", "Mapper retornou entidade nula.");
                }
            } catch (final RuntimeException e) {
                registrosInvalidos++;
                auditarRegistroInvalido(dto, "MAPEAMENTO_INVALIDO", e.getMessage());
                log.warn("⚠️ Manifesto inválido descartado: {}", e.getMessage());
            }
        }
        if (registrosInvalidos > 0) {
            log.warn("⚠️ {} registro(s) inválido(s) descartado(s) em {}", registrosInvalidos, getEntityName());
        }
        if (entities.isEmpty()) {
            return new SaveResult(0, 0, registrosInvalidos);
        }
        
        // Deduplicar antes de salvar
        final List<ManifestoEntity> entitiesUnicos = Deduplicator.deduplicarManifestos(entities);
        final int totalUnicos = entitiesUnicos.size();
        
        if (entities.size() != entitiesUnicos.size()) {
            final int duplicadosRemovidos = entities.size() - entitiesUnicos.size();
            log.warn(ConstantesExtracao.MSG_LOG_DUPLICADOS_REMOVIDOS, duplicadosRemovidos);
        }
        
        final int registrosSalvos = repository.salvar(entitiesUnicos);
        return new SaveResult(registrosSalvos, totalUnicos, registrosInvalidos);
    }
    
    @Override
    public int save(final List<ManifestoDTO> dtos) throws java.sql.SQLException {
        return saveWithDeduplication(dtos).getRegistrosSalvos();
    }
    
    @Override
    public String getEntityName() {
        return ConstantesEntidades.MANIFESTOS;
    }
    
    @Override
    public String getEmoji() {
        return ConstantesExtracao.EMOJI_FATURAS;
    }

    private void auditarRegistroInvalido(final ManifestoDTO dto,
                                         final String reasonCode,
                                         final String detalhe) {
        final String chaveReferencia = dto != null && dto.getSequenceCode() != null
            ? String.valueOf(dto.getSequenceCode())
            : null;
        invalidRecordAuditRepository.registrarRegistroInvalido(
            getEntityName(),
            reasonCode,
            detalhe,
            chaveReferencia,
            MapperUtil.toJson(dto)
        );
    }
}

