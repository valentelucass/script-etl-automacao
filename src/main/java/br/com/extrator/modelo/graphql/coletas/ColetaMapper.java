/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/coletas/ColetaMapper.java
Classe  : ColetaMapper (class)
Pacote  : br.com.extrator.modelo.graphql.coletas
Modulo  : DTO/Mapper GraphQL
Papel   : Implementa responsabilidade de coleta mapper.

Conecta com:
- ColetaEntity (db.entity)
- MapperUtil (util.mapeamento)

Fluxo geral:
1) Modela payloads da API GraphQL.
2) Mapeia estrutura remota para modelo interno.
3) Apoia persistencia e validacao do extrator.

Estrutura interna:
Metodos principais:
- ColetaMapper(): realiza operacao relacionada a "coleta mapper".
- toEntity(...1 args): realiza operacao relacionada a "to entity".
- validarCampoHora(...1 args): aplica regras de validacao e consistencia.
- traduzirStatus(...1 args): realiza operacao relacionada a "traduzir status".
- calcularAcaoOcorrencia(...2 args): realiza operacao relacionada a "calcular acao ocorrencia".
- calcularNumeroTentativas(...1 args): realiza operacao relacionada a "calcular numero tentativas".
Atributos-chave:
- logger: logger da classe para diagnostico.
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.graphql.coletas;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.ColetaEntity;
import br.com.extrator.util.mapeamento.MapperUtil;

/**
 * Mapper (Tradutor) que transforma o ColetaNodeDTO (dados brutos do GraphQL)
 * em uma ColetaEntity (pronta para o banco de dados).
 * Realiza a conversão de tipos (String para LocalDate) e garante que
 * 100% dos dados originais sejam preservados na coluna de metadados.
 */
public class ColetaMapper {

    private static final Logger logger = LoggerFactory.getLogger(ColetaMapper.class);

    public ColetaMapper() {
        // Usando MapperUtil para ObjectMapper compartilhado
    }

    /**
     * Converte o DTO de Coleta em uma Entidade.
     * @param dto O objeto DTO com os dados da coleta.
     * @return Um objeto ColetaEntity pronto para ser salvo.
     */
    public ColetaEntity toEntity(final ColetaNodeDTO dto) {
        if (dto == null) {
            return null;
        }

        final ColetaEntity entity = new ColetaEntity();

        // 1. Mapeamento dos campos essenciais
        entity.setId(dto.getId());
        entity.setSequenceCode(dto.getSequenceCode());
        entity.setStatus(dto.getStatus());
        entity.setTotalValue(dto.getInvoicesValue());
        entity.setTotalWeight(dto.getInvoicesWeight());
        entity.setTotalVolumes(dto.getInvoicesVolumes());
        entity.setTaxedWeight(dto.getTaxedWeight()); // Peso Taxado

        // 1.0. Lógica De-Para para campos calculados
        entity.setLastOccurrence(traduzirStatus(dto.getStatus()));
        entity.setAcaoOcorrencia(calcularAcaoOcorrencia(dto.getStatus(), dto.getCancellationReason()));
        entity.setNumeroTentativas(calcularNumeroTentativas(dto.getStatus()));

        // 1.1. Mapeamento dos campos expandidos (22 campos do CSV)
        if (dto.getCustomer() != null) {
            entity.setClienteId(dto.getCustomer().getId());
            entity.setClienteNome(dto.getCustomer().getName());
            entity.setClienteDoc(dto.getCustomer().getCnpj());
        }

        if (dto.getPickAddress() != null) {
            entity.setLocalColeta(dto.getPickAddress().getLine1());
            entity.setComplementoColeta(dto.getPickAddress().getLine2());
            entity.setNumeroColeta(dto.getPickAddress().getNumber());
            entity.setBairroColeta(dto.getPickAddress().getNeighborhood());
            entity.setCepColeta(dto.getPickAddress().getPostalCode());
            if (dto.getPickAddress().getCity() != null) {
                entity.setCidadeColeta(dto.getPickAddress().getCity().getName());
                if (dto.getPickAddress().getCity().getState() != null) {
                    entity.setUfColeta(dto.getPickAddress().getCity().getState().getCode());
                    // Região da Coleta: cidade + estado
                    final String cidade = dto.getPickAddress().getCity().getName();
                    final String estado = dto.getPickAddress().getCity().getState().getCode();
                    if (cidade != null && estado != null) {
                        entity.setPickRegion(cidade + " - " + estado);
                    } else if (cidade != null) {
                        entity.setPickRegion(cidade);
                    } else if (estado != null) {
                        entity.setPickRegion(estado);
                    }
                } else if (dto.getPickAddress().getCity().getName() != null) {
                    entity.setPickRegion(dto.getPickAddress().getCity().getName());
                }
            }
        }

        if (dto.getCorporation() != null) {
            entity.setFilialId(dto.getCorporation().getId());
            if (dto.getCorporation().getPerson() != null) {
                entity.setFilialNome(dto.getCorporation().getPerson().getNickname());
                entity.setFilialCnpj(dto.getCorporation().getPerson().getCnpj());
            }
        }

        if (dto.getUser() != null) {
            entity.setUsuarioId(dto.getUser().getId());
            entity.setUsuarioNome(dto.getUser().getName());
        }

        entity.setCancellationReason(dto.getCancellationReason());
        entity.setCancellationUserId(dto.getCancellationUserId());
        entity.setCargoClassificationId(dto.getCargoClassificationId());
        entity.setCostCenterId(dto.getCostCenterId());
        entity.setDestroyReason(dto.getDestroyReason());
        entity.setDestroyUserId(dto.getDestroyUserId());
        entity.setInvoicesCubedWeight(dto.getInvoicesCubedWeight());
        entity.setLunchBreakEndHour(validarCampoHora(dto.getLunchBreakEndHour()));
        entity.setLunchBreakStartHour(validarCampoHora(dto.getLunchBreakStartHour()));
        entity.setNotificationEmail(dto.getNotificationEmail());
        entity.setNotificationPhone(dto.getNotificationPhone());
        entity.setPickTypeId(dto.getPickTypeId());
        entity.setPickupLocationId(dto.getPickupLocationId());
        entity.setStatusUpdatedAt(dto.getStatusUpdatedAt());

        // Validação: campos de hora podem vir como datas (ex: "1999-12-31", "2000-01-01") quando não há hora disponível
        entity.setRequestHour(validarCampoHora(dto.getRequestHour()));
        entity.setServiceStartHour(validarCampoHora(dto.getServiceStartHour()));
        try {
            if (dto.getFinishDate() != null && !dto.getFinishDate().trim().isEmpty()) {
                entity.setFinishDate(LocalDate.parse(dto.getFinishDate()));
            }
        } catch (final Exception e) {
            logger.error("❌ Erro ao converter finishDate para coleta ID {}: finishDate='{}' - {}", 
                dto.getId(), dto.getFinishDate(), e.getMessage());
        }
        entity.setServiceEndHour(validarCampoHora(dto.getServiceEndHour()));
        entity.setRequester(dto.getRequester());
        entity.setComments(dto.getComments());
        entity.setAgentId(dto.getAgentId());
        entity.setManifestItemPickId(dto.getManifestItemPickId());
        entity.setVehicleTypeId(dto.getVehicleTypeId());

        // 2. Conversão segura de tipos de data
        try {
            if (dto.getRequestDate() != null && !dto.getRequestDate().trim().isEmpty()) {
                entity.setRequestDate(LocalDate.parse(dto.getRequestDate()));
            }
            if (dto.getServiceDate() != null && !dto.getServiceDate().trim().isEmpty()) {
                entity.setServiceDate(LocalDate.parse(dto.getServiceDate()));
            }
        } catch (final Exception e) {
            logger.error("❌ Erro ao converter data para coleta ID {}: requestDate='{}', serviceDate='{}' - {}", 
                dto.getId(), dto.getRequestDate(), dto.getServiceDate(), e.getMessage());
            logger.debug("Stack trace completo:", e);
        }

        // 3. Empacotamento de todos os metadados
        // Serializa o DTO original completo para a coluna de metadados usando MapperUtil
        final String metadata = MapperUtil.toJson(dto);
        entity.setMetadata(metadata);

        return entity;
    }

    /**
     * Valida se o campo de hora é realmente uma hora ou se é uma data (formato YYYY-MM-DD).
     * Se for uma data, retorna null para evitar truncamento no banco.
     * @param valor Campo de hora recebido da API
     * @return String com a hora válida ou null se for uma data
     */
    private String validarCampoHora(final String valor) {
        if (valor == null) {
            return null;
        }

        final String v = valor.trim();
        if (v.isEmpty()) {
            return null;
        }

        // Caso comum: já é hora válida (HH:mm ou HH:mm:ss)
        if (v.matches("^\\d{2}:\\d{2}(:\\d{2})?$")) {
            return v.length() <= 10 ? v : v.substring(0, 8);
        }

        // Detecta valores ISO de data/hora e extrai a parte de hora se existir
        // Exemplos: "2025-11-06T12:38:41Z", "2025-11-06 12:38:41", "2025-11-06T12:38"
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^\\d{4}-\\d{2}-\\d{2}[ T](\\d{2}:\\d{2}(?::\\d{2})?)")
                .matcher(v);
        if (m.find()) {
            String hora = m.group(1);
            // Limitar a HH:mm:ss se vier com frações
            if (hora.length() > 8) {
                hora = hora.substring(0, 8);
            }
            return hora;
        }

        // Se for somente data (YYYY-MM-DD), tratar como null para não truncar
        if (v.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            logger.debug("Campo de hora contém data (YYYY-MM-DD): '{}' , convertendo para null", v);
            return null;
        }

        // Captura horas no início mesmo com sufixos (ex.: "12:38:41Z", "12:38:41.123")
        m = java.util.regex.Pattern.compile("^(\\d{2}:\\d{2}(:\\d{2})?)").matcher(v);
        if (m.find()) {
            String hora = m.group(1);
            if (hora.length() > 8) {
                hora = hora.substring(0, 8);
            }
            return hora;
        }

        // Tratar strings como "NULL" ou valores muito longos sem padrão de hora
        if ("NULL".equalsIgnoreCase(v) || v.length() > 10) {
            return null;
        }

        // Retorna o valor original (curto) quando não se enquadra nos casos acima
        return v;
    }

    /**
     * Traduz o status da API para português (Última Ocorrência).
     * @param status Status da coleta (ex: "finished", "canceled", "pending")
     * @return Status traduzido ou o status original se não houver tradução
     */
    private String traduzirStatus(final String status) {
        if (status == null) {
            return null;
        }
        final String statusLower = status.toLowerCase();
        return switch (statusLower) {
            case "finished" -> "Finalizado";
            case "canceled" -> "Cancelado";
            case "draft" -> "Rascunho";
            case "pending" -> "Pendente";
            case "done" -> "Coletada";
            case "treatment" -> "Em tratativa";
            case "in_transit" -> "Em trânsito";
            case "manifested" -> "Manifestada";
            default -> status; // Retorna o status original se não houver tradução
        };
    }

    /**
     * Calcula a Ação da Ocorrência baseado nas regras de negócio.
     * Regras:
     * - Se status == 'finished' -> "Coleta Realizada"
     * - Se cancellationReason != null -> usa o cancellationReason
     * - Senão -> "Pendente"
     * 
     * @param status Status da coleta
     * @param cancellationReason Motivo de cancelamento (pode ser null)
     * @return Ação da ocorrência calculada
     */
    private String calcularAcaoOcorrencia(final String status, final String cancellationReason) {
        if (status == null) {
            return "Pendente";
        }
        final String statusLower = status.toLowerCase();
        
        // Se status == 'finished' -> "Coleta Realizada"
        if ("finished".equals(statusLower) || "done".equals(statusLower)) {
            return "Coleta Realizada";
        }
        
        // Se cancellationReason != null -> usa o cancellationReason
        if (cancellationReason != null && !cancellationReason.trim().isEmpty()) {
            return cancellationReason;
        }
        
        // Senão -> "Pendente"
        return "Pendente";
    }

    /**
     * Calcula o Nº de Tentativas baseado no status.
     * Regras:
     * - Se status == 'finished' OU status == 'canceled' -> 1
     * - Senão -> 0
     * 
     * @param status Status da coleta
     * @return Número de tentativas (0 ou 1)
     */
    private Integer calcularNumeroTentativas(final String status) {
        if (status == null) {
            return 0;
        }
        final String statusLower = status.toLowerCase();
        
        // Se status == 'finished' OU status == 'canceled' -> 1
        if ("finished".equals(statusLower) || "done".equals(statusLower) || "canceled".equals(statusLower)) {
            return 1;
        }
        
        // Senão -> 0
        return 0;
    }
}
