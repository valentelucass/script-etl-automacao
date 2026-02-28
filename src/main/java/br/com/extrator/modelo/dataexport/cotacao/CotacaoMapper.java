/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/dataexport/cotacao/CotacaoMapper.java
Classe  : CotacaoMapper (class)
Pacote  : br.com.extrator.modelo.dataexport.cotacao
Modulo  : DTO/Mapper DataExport
Papel   : Implementa responsabilidade de cotacao mapper.

Conecta com:
- CotacaoEntity (db.entity)
- ValidadorDTO (util.validacao)
- ResultadoValidacao (util.validacao.ValidadorDTO)
- FormatadorData (util.formatacao)
- MapperUtil (util.mapeamento)

Fluxo geral:
1) Modela payloads da API DataExport.
2) Mapeia resposta para entidades internas.
3) Apoia carga e deduplicacao no destino.

Estrutura interna:
Metodos principais:
- CotacaoMapper(): realiza operacao relacionada a "cotacao mapper".
- toEntity(...1 args): realiza operacao relacionada a "to entity".
Atributos-chave:
- logger: logger da classe para diagnostico.
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.dataexport.cotacao;

import br.com.extrator.db.entity.CotacaoEntity;
import br.com.extrator.util.validacao.ValidadorDTO;
import br.com.extrator.util.validacao.ValidadorDTO.ResultadoValidacao;
import br.com.extrator.util.formatacao.FormatadorData;
import br.com.extrator.util.mapeamento.MapperUtil;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mapper (Tradutor) que transforma o CotacaoDTO (dados brutos da API Data Export)
 * em uma CotacaoEntity (pronta para o banco de dados).
 * É responsável pela conversão de tipos (String para BigDecimal/OffsetDateTime)
 * e pela serialização de todos os dados na coluna de metadados.
 *
 * @author Lucas
 */
public class CotacaoMapper {

    private static final Logger logger = LoggerFactory.getLogger(CotacaoMapper.class);

    public CotacaoMapper() {
        // Usando MapperUtil para ObjectMapper compartilhado
    }

    /**
     * Converte o DTO de Cotação em uma Entidade.
     * PROBLEMA #6 CORRIGIDO: Adicionada validação de campos críticos.
     * 
     * @param dto O objeto DTO com os dados da cotação.
     * @return Um objeto CotacaoEntity pronto para ser salvo.
     * @throws IllegalArgumentException se campos críticos forem inválidos
     */
    public CotacaoEntity toEntity(final CotacaoDTO dto) {
        if (dto == null) {
            return null;
        }

        // PROBLEMA #6: Validação de campos críticos
        final ResultadoValidacao validacao = ValidadorDTO.criarValidacao("Cotacao");
        ValidadorDTO.validarId(validacao, "sequence_code", dto.getSequenceCode());
        
        if (!validacao.isValido()) {
            validacao.logErros();
            throw new IllegalArgumentException("Cotação inválida: sequence_code é obrigatório. Erros: " + validacao.getErros());
        }

        final CotacaoEntity entity = new CotacaoEntity();

        // 1. Mapeamento dos campos essenciais conforme docs/descobertas-endpoints/cotacoes.md
        entity.setSequenceCode(dto.getSequenceCode());
        entity.setOperationType(dto.getOperationType());
        entity.setCustomerDoc(dto.getCustomerDocument());
        entity.setCustomerName(dto.getCustomerName());
        entity.setOriginCity(dto.getOriginCity());
        entity.setOriginState(dto.getOriginState());
        entity.setDestinationCity(dto.getDestinationCity());
        entity.setDestinationState(dto.getDestinationState());
        entity.setPriceTable(dto.getPriceTable());
        entity.setVolumes(dto.getVolumes());
        entity.setUserName(dto.getUserName());
        entity.setBranchNickname(dto.getBranchNickname());
        entity.setCompanyName(dto.getCompanyName());
        entity.setRequesterName(dto.getRequesterName());
        entity.setRealWeight(dto.getRealWeight());
        entity.setOriginPostalCode(dto.getOriginPostalCode());
        entity.setDestinationPostalCode(dto.getDestinationPostalCode());
        entity.setCustomerNickname(dto.getCustomerNickname());
        entity.setSenderDocument(dto.getSenderDocument());
        entity.setSenderNickname(dto.getSenderNickname());
        entity.setReceiverDocument(dto.getReceiverDocument());
        entity.setReceiverNickname(dto.getReceiverNickname());
        entity.setDisapproveComments(dto.getDisapproveComments());
        entity.setFreightComments(dto.getFreightComments());

        // 2. Conversão segura de tipos de dados (String para tipos específicos)
        try {
            if (dto.getRequestedAt() != null && !dto.getRequestedAt().trim().isEmpty()) {
                final OffsetDateTime requestedAt = FormatadorData.parseOffsetDateTime(dto.getRequestedAt());
                if (requestedAt != null) {
                    entity.setRequestedAt(requestedAt);
                }
            }
            if (dto.getTotalValue() != null && !dto.getTotalValue().trim().isEmpty()) {
                entity.setTotalValue(new BigDecimal(dto.getTotalValue()));
            }
            if (dto.getTaxedWeight() != null && !dto.getTaxedWeight().trim().isEmpty()) {
                entity.setTaxedWeight(new BigDecimal(dto.getTaxedWeight()));
            }
            if (dto.getInvoicesValue() != null && !dto.getInvoicesValue().trim().isEmpty()) {
                entity.setInvoicesValue(new BigDecimal(dto.getInvoicesValue()));
            }
            if (dto.getDiscountSubtotal() != null && !dto.getDiscountSubtotal().trim().isEmpty()) {
                entity.setDiscountSubtotal(new BigDecimal(dto.getDiscountSubtotal()));
            }
            if (dto.getItrSubtotal() != null && !dto.getItrSubtotal().trim().isEmpty()) {
                entity.setItrSubtotal(new BigDecimal(dto.getItrSubtotal()));
            }
            if (dto.getTdeSubtotal() != null && !dto.getTdeSubtotal().trim().isEmpty()) {
                entity.setTdeSubtotal(new BigDecimal(dto.getTdeSubtotal()));
            }
            if (dto.getCollectSubtotal() != null && !dto.getCollectSubtotal().trim().isEmpty()) {
                entity.setCollectSubtotal(new BigDecimal(dto.getCollectSubtotal()));
            }
            if (dto.getDeliverySubtotal() != null && !dto.getDeliverySubtotal().trim().isEmpty()) {
                entity.setDeliverySubtotal(new BigDecimal(dto.getDeliverySubtotal()));
            }
            if (dto.getOtherFees() != null && !dto.getOtherFees().trim().isEmpty()) {
                entity.setOtherFees(new BigDecimal(dto.getOtherFees()));
            }
            if (dto.getCteIssuedAt() != null && !dto.getCteIssuedAt().trim().isEmpty()) {
                final OffsetDateTime cteIssuedAt = FormatadorData.parseOffsetDateTime(dto.getCteIssuedAt());
                if (cteIssuedAt != null) {
                    entity.setCteIssuedAt(cteIssuedAt);
                }
            }
            if (dto.getNfseIssuedAt() != null && !dto.getNfseIssuedAt().trim().isEmpty()) {
                final OffsetDateTime nfseIssuedAt = FormatadorData.parseOffsetDateTime(dto.getNfseIssuedAt());
                if (nfseIssuedAt != null) {
                    entity.setNfseIssuedAt(nfseIssuedAt);
                }
            }
        } catch (NumberFormatException e) {
            logger.error("❌ Erro ao converter dados para cotação {}: requestedAt='{}', totalValue='{}', taxedWeight='{}', invoicesValue='{}' - {}", 
                dto.getSequenceCode(), dto.getRequestedAt(), dto.getTotalValue(), dto.getTaxedWeight(), dto.getInvoicesValue(), e.getMessage());
            logger.debug("Stack trace completo:", e);
        }

        // 3. Empacotamento de todos os metadados
        // Serializa o mapa completo que inclui campos explícitos e o "resto"
        String metadata = MapperUtil.toJson(dto.getAllProperties());
        entity.setMetadata(metadata);

        return entity;
    }
}
