/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/dataexport/extractors/LocalizacaoCargaExtractor.java
Classe  : LocalizacaoCargaExtractor (class)
Pacote  : br.com.extrator.runners.dataexport.extractors
Modulo  : Extractor DataExport
Papel   : Implementa responsabilidade de localizacao carga extractor.

Conecta com:
- ClienteApiDataExport (api)
- ResultadoExtracao (api)
- LocalizacaoCargaEntity (db.entity)
- InvalidRecordAuditRepository (db.repository)
- LocalizacaoCargaRepository (db.repository)
- LocalizacaoCargaDTO (modelo.dataexport.localizacaocarga)
- LocalizacaoCargaMapper (modelo.dataexport.localizacaocarga)
- ConstantesExtracao (runners.common)

Fluxo geral:
1) Configura requisicao da API DataExport.
2) Converte resposta em DTO/entidade de dominio.
3) Persiste lote no repositorio correspondente.

Estrutura interna:
Metodos principais:
- LocalizacaoCargaExtractor(...4 args): realiza operacao relacionada a "localizacao carga extractor".
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
import br.com.extrator.db.entity.LocalizacaoCargaEntity;
import br.com.extrator.db.repository.InvalidRecordAuditRepository;
import br.com.extrator.db.repository.LocalizacaoCargaRepository;
import br.com.extrator.modelo.dataexport.localizacaocarga.LocalizacaoCargaDTO;
import br.com.extrator.modelo.dataexport.localizacaocarga.LocalizacaoCargaMapper;
import br.com.extrator.runners.common.ConstantesExtracao;
import br.com.extrator.runners.common.DataExportEntityExtractor;
import br.com.extrator.runners.dataexport.services.Deduplicator;
import br.com.extrator.util.console.LoggerConsole;
import br.com.extrator.util.mapeamento.MapperUtil;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Extractor para entidade Localização de Cargas (DataExport).
 * Inclui deduplicação antes de salvar.
 */
public class LocalizacaoCargaExtractor implements DataExportEntityExtractor<LocalizacaoCargaDTO> {
    
    private final ClienteApiDataExport apiClient;
    private final LocalizacaoCargaRepository repository;
    private final LocalizacaoCargaMapper mapper;
    private final LoggerConsole log;
    private final InvalidRecordAuditRepository invalidRecordAuditRepository;
    
    public LocalizacaoCargaExtractor(final ClienteApiDataExport apiClient,
                                    final LocalizacaoCargaRepository repository,
                                    final LocalizacaoCargaMapper mapper,
                                    final LoggerConsole log) {
        this.apiClient = apiClient;
        this.repository = repository;
        this.mapper = mapper;
        this.log = log;
        this.invalidRecordAuditRepository = new InvalidRecordAuditRepository();
    }
    
    @Override
    public ResultadoExtracao<LocalizacaoCargaDTO> extract(final LocalDate dataInicio, final LocalDate dataFim) {
        // Usa intervalo informado quando disponível; fallback para últimas 24h
        if (dataInicio != null) {
            final LocalDate fim = (dataFim != null) ? dataFim : dataInicio;
            return apiClient.buscarLocalizacaoCarga(dataInicio, fim);
        }
        return apiClient.buscarLocalizacaoCarga();
    }
    
    @Override
    public SaveResult saveWithDeduplication(final List<LocalizacaoCargaDTO> dtos) throws java.sql.SQLException {
        if (dtos == null || dtos.isEmpty()) {
            return new SaveResult(0, 0);
        }
        
        final List<LocalizacaoCargaEntity> entities = new ArrayList<>();
        int registrosInvalidos = 0;
        for (final LocalizacaoCargaDTO dto : dtos) {
            try {
                final LocalizacaoCargaEntity entity = mapper.toEntity(dto);
                if (entity != null) {
                    entities.add(entity);
                } else {
                    registrosInvalidos++;
                    auditarRegistroInvalido(dto, "MAPPER_RETORNOU_NULL", "Mapper retornou entidade nula.");
                }
            } catch (final RuntimeException e) {
                registrosInvalidos++;
                auditarRegistroInvalido(dto, "MAPEAMENTO_INVALIDO", e.getMessage());
                log.warn("⚠️ Localização de Carga inválida descartada: {}", e.getMessage());
            }
        }
        if (registrosInvalidos > 0) {
            log.warn("⚠️ {} registro(s) inválido(s) descartado(s) em {}", registrosInvalidos, getEntityName());
        }
        if (entities.isEmpty()) {
            return new SaveResult(0, 0, registrosInvalidos);
        }
        
        // Deduplicar antes de salvar
        final List<LocalizacaoCargaEntity> entitiesUnicos = Deduplicator.deduplicarLocalizacoes(entities);
        final int totalUnicos = entitiesUnicos.size();
        
        if (entities.size() != entitiesUnicos.size()) {
            final int duplicadosRemovidos = entities.size() - entitiesUnicos.size();
            log.warn(ConstantesExtracao.MSG_LOG_DUPLICADOS_REMOVIDOS, duplicadosRemovidos);
        }
        
        final int registrosSalvos = repository.salvar(entitiesUnicos);
        return new SaveResult(registrosSalvos, totalUnicos, registrosInvalidos);
    }
    
    @Override
    public int save(final List<LocalizacaoCargaDTO> dtos) throws java.sql.SQLException {
        return saveWithDeduplication(dtos).getRegistrosSalvos();
    }
    
    @Override
    public String getEntityName() {
        return ConstantesEntidades.LOCALIZACAO_CARGAS;
    }
    
    @Override
    public String getEmoji() {
        return ConstantesExtracao.EMOJI_LOCALIZACAO;
    }

    private void auditarRegistroInvalido(final LocalizacaoCargaDTO dto,
                                         final String reasonCode,
                                         final String detalhe) {
        final String chaveReferencia = dto != null && dto.getSequenceNumber() != null
            ? String.valueOf(dto.getSequenceNumber())
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

