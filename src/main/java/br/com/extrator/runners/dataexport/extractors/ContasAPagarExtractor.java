/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/dataexport/extractors/ContasAPagarExtractor.java
Classe  : ContasAPagarExtractor (class)
Pacote  : br.com.extrator.runners.dataexport.extractors
Modulo  : Extractor DataExport
Papel   : Implementa responsabilidade de contas apagar extractor.

Conecta com:
- ClienteApiDataExport (api)
- ResultadoExtracao (api)
- ContasAPagarDataExportEntity (db.entity)
- InvalidRecordAuditRepository (db.repository)
- ContasAPagarRepository (db.repository)
- ContasAPagarDTO (modelo.dataexport.contasapagar)
- ContasAPagarMapper (modelo.dataexport.contasapagar)
- ConstantesExtracao (runners.common)

Fluxo geral:
1) Configura requisicao da API DataExport.
2) Converte resposta em DTO/entidade de dominio.
3) Persiste lote no repositorio correspondente.

Estrutura interna:
Metodos principais:
- ContasAPagarExtractor(...4 args): realiza operacao relacionada a "contas apagar extractor".
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
import br.com.extrator.db.entity.ContasAPagarDataExportEntity;
import br.com.extrator.db.repository.InvalidRecordAuditRepository;
import br.com.extrator.db.repository.ContasAPagarRepository;
import br.com.extrator.modelo.dataexport.contasapagar.ContasAPagarDTO;
import br.com.extrator.modelo.dataexport.contasapagar.ContasAPagarMapper;
import br.com.extrator.runners.common.ConstantesExtracao;
import br.com.extrator.runners.common.DataExportEntityExtractor;
import br.com.extrator.runners.dataexport.services.Deduplicator;
import br.com.extrator.util.console.LoggerConsole;
import br.com.extrator.util.mapeamento.MapperUtil;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Extractor para entidade Contas a Pagar (DataExport).
 * Inclui deduplicação antes de salvar.
 */
public class ContasAPagarExtractor implements DataExportEntityExtractor<ContasAPagarDTO> {
    
    private final ClienteApiDataExport apiClient;
    private final ContasAPagarRepository repository;
    private final ContasAPagarMapper mapper;
    private final LoggerConsole log;
    private final InvalidRecordAuditRepository invalidRecordAuditRepository;
    
    public ContasAPagarExtractor(final ClienteApiDataExport apiClient,
                                 final ContasAPagarRepository repository,
                                 final ContasAPagarMapper mapper,
                                 final LoggerConsole log) {
        this.apiClient = apiClient;
        this.repository = repository;
        this.mapper = mapper;
        this.log = log;
        this.invalidRecordAuditRepository = new InvalidRecordAuditRepository();
    }
    
    @Override
    public ResultadoExtracao<ContasAPagarDTO> extract(final LocalDate dataInicio, final LocalDate dataFim) {
        // Usa intervalo informado quando disponível; fallback para últimas 24h
        if (dataInicio != null) {
            final LocalDate fim = (dataFim != null) ? dataFim : dataInicio;
            return apiClient.buscarContasAPagar(dataInicio, fim);
        }
        return apiClient.buscarContasAPagar();
    }
    
    @Override
    public SaveResult saveWithDeduplication(final List<ContasAPagarDTO> dtos) throws java.sql.SQLException {
        if (dtos == null || dtos.isEmpty()) {
            return new SaveResult(0, 0);
        }
        
        final List<ContasAPagarDataExportEntity> entities = new ArrayList<>();
        int registrosInvalidos = 0;
        for (final ContasAPagarDTO dto : dtos) {
            try {
                final ContasAPagarDataExportEntity entity = mapper.toEntity(dto);
                if (entity != null) {
                    entities.add(entity);
                } else {
                    registrosInvalidos++;
                    auditarRegistroInvalido(dto, "MAPPER_RETORNOU_NULL", "Mapper retornou entidade nula.");
                }
            } catch (final RuntimeException e) {
                registrosInvalidos++;
                auditarRegistroInvalido(dto, "MAPEAMENTO_INVALIDO", e.getMessage());
                log.warn("⚠️ Conta a pagar inválida descartada: {}", e.getMessage());
            }
        }
        if (registrosInvalidos > 0) {
            log.warn("⚠️ {} registro(s) inválido(s) descartado(s) em {}", registrosInvalidos, getEntityName());
        }
        if (entities.isEmpty()) {
            return new SaveResult(0, 0, registrosInvalidos);
        }
        
        // Deduplicar antes de salvar
        final List<ContasAPagarDataExportEntity> entitiesUnicos = Deduplicator.deduplicarFaturasAPagar(entities);
        final int totalUnicos = entitiesUnicos.size();
        
        if (entities.size() != entitiesUnicos.size()) {
            final int duplicadosRemovidos = entities.size() - entitiesUnicos.size();
            log.warn(ConstantesExtracao.MSG_LOG_DUPLICADOS_REMOVIDOS, duplicadosRemovidos);
        }
        
        final int registrosSalvos = repository.salvar(entitiesUnicos);
        return new SaveResult(registrosSalvos, totalUnicos, registrosInvalidos);
    }
    
    @Override
    public int save(final List<ContasAPagarDTO> dtos) throws java.sql.SQLException {
        return saveWithDeduplication(dtos).getRegistrosSalvos();
    }
    
    @Override
    public String getEntityName() {
        return ConstantesEntidades.CONTAS_A_PAGAR;
    }
    
    @Override
    public String getEmoji() {
        return ConstantesExtracao.EMOJI_CONTAS_PAGAR;
    }

    private void auditarRegistroInvalido(final ContasAPagarDTO dto,
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

