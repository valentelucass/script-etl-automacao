/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/dataexport/extractors/FaturaPorClienteExtractor.java
Classe  : FaturaPorClienteExtractor (class)
Pacote  : br.com.extrator.runners.dataexport.extractors
Modulo  : Extractor DataExport
Papel   : Implementa responsabilidade de fatura por cliente extractor.

Conecta com:
- ClienteApiDataExport (api)
- ResultadoExtracao (api)
- FaturaPorClienteEntity (db.entity)
- FaturaPorClienteRepository (db.repository)
- InvalidRecordAuditRepository (db.repository)
- FaturaPorClienteDTO (modelo.dataexport.faturaporcliente)
- FaturaPorClienteMapper (modelo.dataexport.faturaporcliente)
- ConstantesExtracao (runners.common)

Fluxo geral:
1) Configura requisicao da API DataExport.
2) Converte resposta em DTO/entidade de dominio.
3) Persiste lote no repositorio correspondente.

Estrutura interna:
Metodos principais:
- FaturaPorClienteExtractor(...4 args): realiza operacao relacionada a "fatura por cliente extractor".
- extract(...2 args): realiza operacao relacionada a "extract".
- calcularUniqueIdTemporario(...1 args): realiza operacao relacionada a "calcular unique id temporario".
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.com.extrator.api.ClienteApiDataExport;
import br.com.extrator.api.ResultadoExtracao;
import br.com.extrator.db.entity.FaturaPorClienteEntity;
import br.com.extrator.db.repository.FaturaPorClienteRepository;
import br.com.extrator.db.repository.InvalidRecordAuditRepository;
import br.com.extrator.modelo.dataexport.faturaporcliente.FaturaPorClienteDTO;
import br.com.extrator.modelo.dataexport.faturaporcliente.FaturaPorClienteMapper;
import br.com.extrator.runners.common.ConstantesExtracao;
import br.com.extrator.runners.common.DataExportEntityExtractor;
import br.com.extrator.runners.dataexport.services.Deduplicator;
import br.com.extrator.util.console.LoggerConsole;
import br.com.extrator.util.mapeamento.MapperUtil;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Extractor para entidade faturas_por_cliente (DataExport).
 * Inclui deduplicacao antes de salvar.
 */
public class FaturaPorClienteExtractor implements DataExportEntityExtractor<FaturaPorClienteDTO> {

    private final ClienteApiDataExport apiClient;
    private final FaturaPorClienteRepository repository;
    private final FaturaPorClienteMapper mapper;
    private final LoggerConsole log;
    private final InvalidRecordAuditRepository invalidRecordAuditRepository;

    public FaturaPorClienteExtractor(final ClienteApiDataExport apiClient,
                                     final FaturaPorClienteRepository repository,
                                     final FaturaPorClienteMapper mapper,
                                     final LoggerConsole log) {
        this.apiClient = apiClient;
        this.repository = repository;
        this.mapper = mapper;
        this.log = log;
        this.invalidRecordAuditRepository = new InvalidRecordAuditRepository();
    }

    @Override
    public ResultadoExtracao<FaturaPorClienteDTO> extract(final LocalDate dataInicio, final LocalDate dataFim) {
        // Usa intervalo informado quando disponivel; fallback para ultimas 24h.
        if (dataInicio != null) {
            final LocalDate fim = (dataFim != null) ? dataFim : dataInicio;
            return apiClient.buscarFaturasPorCliente(dataInicio, fim);
        }
        return apiClient.buscarFaturasPorCliente();
    }

    @Override
    public SaveResult saveWithDeduplication(final List<FaturaPorClienteDTO> dtos) throws java.sql.SQLException {
        if (dtos == null || dtos.isEmpty()) {
            return new SaveResult(0, 0);
        }

        // PASSO 1: Deduplicar no nivel DTO (antes de converter para Entity)
        // Usa o mesmo calculo de unique_id do mapper.
        final Map<String, FaturaPorClienteDTO> faturasUnicas = new HashMap<>();
        int descartesSemChave = 0;
        for (final FaturaPorClienteDTO dto : dtos) {
            final String uniqueId = calcularUniqueIdTemporario(dto);
            if (uniqueId != null && !faturasUnicas.containsKey(uniqueId)) {
                faturasUnicas.put(uniqueId, dto);
            } else if (uniqueId == null) {
                descartesSemChave++;
                auditarRegistroInvalido(dto, "UNIQUE_ID_AUSENTE", "Nao foi possivel calcular unique_id temporario.");
            }
        }

        if (descartesSemChave > 0) {
            log.warn("⚠️ {} faturas descartadas por falta de unique_id temporario", descartesSemChave);
        }

        final int duplicadosRemovidos = dtos.size() - faturasUnicas.size();
        if (duplicadosRemovidos > 0) {
            log.warn("⚠️ {} faturas duplicadas removidas no nivel DTO (antes do enriquecimento GraphQL)",
                duplicadosRemovidos);
        }

        // PASSO 2: Converter DTOs unicos para Entities
        final List<FaturaPorClienteEntity> entities = new ArrayList<>();
        int registrosInvalidos = descartesSemChave;
        for (final FaturaPorClienteDTO dto : faturasUnicas.values()) {
            try {
                final FaturaPorClienteEntity entity = mapper.toEntity(dto);
                if (entity != null) {
                    entities.add(entity);
                } else {
                    registrosInvalidos++;
                    auditarRegistroInvalido(dto, "MAPPER_RETORNOU_NULL", "Mapper retornou entidade nula.");
                }
            } catch (final RuntimeException e) {
                registrosInvalidos++;
                auditarRegistroInvalido(dto, "MAPEAMENTO_INVALIDO", e.getMessage());
                log.warn("⚠️ Fatura por cliente invalida descartada: {}", e.getMessage());
            }
        }

        if (registrosInvalidos > 0) {
            log.warn("⚠️ {} registro(s) invalido(s) descartado(s) em {}", registrosInvalidos, getEntityName());
        }

        if (entities.isEmpty()) {
            return new SaveResult(0, 0, registrosInvalidos);
        }

        // PASSO 3: Deduplicar novamente no nivel Entity (protecao adicional)
        final List<FaturaPorClienteEntity> entitiesUnicos = Deduplicator.deduplicarFaturasPorCliente(entities);
        final int totalUnicos = entitiesUnicos.size();

        if (entities.size() != entitiesUnicos.size()) {
            final int duplicadosEntity = entities.size() - entitiesUnicos.size();
            log.warn("⚠️ {} duplicados adicionais removidos no nivel Entity", duplicadosEntity);
        }

        // PASSO 4: Salvar no banco
        final int registrosSalvos = repository.salvar(entitiesUnicos);
        return new SaveResult(registrosSalvos, totalUnicos, registrosInvalidos);
    }

    /**
     * Calcula unique_id temporario para deduplicacao no nivel DTO.
     * Usa a mesma regra final do mapper para evitar divergencia.
     */
    private String calcularUniqueIdTemporario(final FaturaPorClienteDTO dto) {
        if (dto == null) {
            return null;
        }
        try {
            return mapper.calcularIdentificadorUnico(dto);
        } catch (final RuntimeException e) {
            log.debug("Falha ao calcular unique_id temporario para fatura por cliente: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public int save(final List<FaturaPorClienteDTO> dtos) throws java.sql.SQLException {
        return saveWithDeduplication(dtos).getRegistrosSalvos();
    }

    @Override
    public String getEntityName() {
        return ConstantesEntidades.FATURAS_POR_CLIENTE;
    }

    @Override
    public String getEmoji() {
        return ConstantesExtracao.EMOJI_FATURAS_CLIENTE;
    }

    private void auditarRegistroInvalido(final FaturaPorClienteDTO dto,
                                         final String reasonCode,
                                         final String detalhe) {
        final String chaveReferencia = dto != null ? calcularUniqueIdTemporario(dto) : null;
        invalidRecordAuditRepository.registrarRegistroInvalido(
            getEntityName(),
            reasonCode,
            detalhe,
            chaveReferencia,
            MapperUtil.toJson(dto)
        );
    }
}
