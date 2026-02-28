/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/dataexport/extractors/CotacaoExtractor.java
Classe  : CotacaoExtractor (class)
Pacote  : br.com.extrator.runners.dataexport.extractors
Modulo  : Extractor DataExport
Papel   : Implementa responsabilidade de cotacao extractor.

Conecta com:
- ClienteApiDataExport (api)
- ResultadoExtracao (api)
- CotacaoEntity (db.entity)
- InvalidRecordAuditRepository (db.repository)
- CotacaoRepository (db.repository)
- CotacaoDTO (modelo.dataexport.cotacao)
- CotacaoMapper (modelo.dataexport.cotacao)
- ConstantesExtracao (runners.common)

Fluxo geral:
1) Configura requisicao da API DataExport.
2) Converte resposta em DTO/entidade de dominio.
3) Persiste lote no repositorio correspondente.

Estrutura interna:
Metodos principais:
- CotacaoExtractor(...4 args): realiza operacao relacionada a "cotacao extractor".
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
import br.com.extrator.db.entity.CotacaoEntity;
import br.com.extrator.db.repository.InvalidRecordAuditRepository;
import br.com.extrator.db.repository.CotacaoRepository;
import br.com.extrator.modelo.dataexport.cotacao.CotacaoDTO;
import br.com.extrator.modelo.dataexport.cotacao.CotacaoMapper;
import br.com.extrator.runners.common.ConstantesExtracao;
import br.com.extrator.runners.common.DataExportEntityExtractor;
import br.com.extrator.runners.dataexport.services.Deduplicator;
import br.com.extrator.util.console.LoggerConsole;
import br.com.extrator.util.mapeamento.MapperUtil;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Extractor para entidade Cotações (DataExport).
 * Inclui deduplicação antes de salvar.
 */
public class CotacaoExtractor implements DataExportEntityExtractor<CotacaoDTO> {
    
    private final ClienteApiDataExport apiClient;
    private final CotacaoRepository repository;
    private final CotacaoMapper mapper;
    private final LoggerConsole log;
    private final InvalidRecordAuditRepository invalidRecordAuditRepository;
    
    public CotacaoExtractor(final ClienteApiDataExport apiClient,
                           final CotacaoRepository repository,
                           final CotacaoMapper mapper,
                           final LoggerConsole log) {
        this.apiClient = apiClient;
        this.repository = repository;
        this.mapper = mapper;
        this.log = log;
        this.invalidRecordAuditRepository = new InvalidRecordAuditRepository();
    }
    
    @Override
    public ResultadoExtracao<CotacaoDTO> extract(final LocalDate dataInicio, final LocalDate dataFim) {
        // Usa intervalo informado quando disponível; fallback para últimas 24h
        if (dataInicio != null) {
            final LocalDate fim = (dataFim != null) ? dataFim : dataInicio;
            return apiClient.buscarCotacoes(dataInicio, fim);
        }
        return apiClient.buscarCotacoes();
    }
    
    @Override
    public SaveResult saveWithDeduplication(final List<CotacaoDTO> dtos) throws java.sql.SQLException {
        if (dtos == null || dtos.isEmpty()) {
            return new SaveResult(0, 0);
        }
        
        final List<CotacaoEntity> entities = new ArrayList<>();
        int registrosInvalidos = 0;
        for (final CotacaoDTO dto : dtos) {
            try {
                final CotacaoEntity entity = mapper.toEntity(dto);
                if (entity != null) {
                    entities.add(entity);
                } else {
                    registrosInvalidos++;
                    auditarRegistroInvalido(dto, "MAPPER_RETORNOU_NULL", "Mapper retornou entidade nula.");
                }
            } catch (final RuntimeException e) {
                registrosInvalidos++;
                auditarRegistroInvalido(dto, "MAPEAMENTO_INVALIDO", e.getMessage());
                log.warn("⚠️ Cotação inválida descartada: {}", e.getMessage());
            }
        }
        if (registrosInvalidos > 0) {
            log.warn("⚠️ {} registro(s) inválido(s) descartado(s) em {}", registrosInvalidos, getEntityName());
        }
        if (entities.isEmpty()) {
            return new SaveResult(0, 0, registrosInvalidos);
        }
        
        // Deduplicar antes de salvar
        final List<CotacaoEntity> entitiesUnicos = Deduplicator.deduplicarCotacoes(entities);
        final int totalUnicos = entitiesUnicos.size();
        
        if (entities.size() != entitiesUnicos.size()) {
            final int duplicadosRemovidos = entities.size() - entitiesUnicos.size();
            log.warn(ConstantesExtracao.MSG_LOG_DUPLICADOS_REMOVIDOS, duplicadosRemovidos);
        }
        
        final int registrosSalvos = repository.salvar(entitiesUnicos);
        return new SaveResult(registrosSalvos, totalUnicos, registrosInvalidos);
    }
    
    @Override
    public int save(final List<CotacaoDTO> dtos) throws java.sql.SQLException {
        return saveWithDeduplication(dtos).getRegistrosSalvos();
    }
    
    @Override
    public String getEntityName() {
        return ConstantesEntidades.COTACOES;
    }
    
    @Override
    public String getEmoji() {
        return ConstantesExtracao.EMOJI_COTACOES;
    }

    private void auditarRegistroInvalido(final CotacaoDTO dto,
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

