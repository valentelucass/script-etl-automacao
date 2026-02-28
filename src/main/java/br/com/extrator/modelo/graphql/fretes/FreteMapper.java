/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/graphql/fretes/FreteMapper.java
Classe  : FreteMapper (class)
Pacote  : br.com.extrator.modelo.graphql.fretes
Modulo  : DTO/Mapper GraphQL
Papel   : Implementa responsabilidade de frete mapper.

Conecta com:
- FreteEntity (db.entity)
- MapperUtil (util.mapeamento)
- NumeroUtil (util.mapeamento)

Fluxo geral:
1) Modela payloads da API GraphQL.
2) Mapeia estrutura remota para modelo interno.
3) Apoia persistencia e validacao do extrator.

Estrutura interna:
Metodos principais:
- FreteMapper(): realiza operacao relacionada a "frete mapper".
- parseIntegerOrNull(...1 args): realiza operacao relacionada a "parse integer or null".
- toEntity(...1 args): realiza operacao relacionada a "to entity".
Atributos-chave:
- logger: logger da classe para diagnostico.
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.graphql.fretes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.FreteEntity;
import br.com.extrator.util.mapeamento.MapperUtil;
import br.com.extrator.util.mapeamento.NumeroUtil;

/**
 * Mapper (Tradutor) que transforma o FreteNodeDTO (dados brutos do GraphQL)
 * em uma FreteEntity (pronta para o banco de dados).
 * Converte tipos de data/hora e preserva 100% dos dados originais
 * na coluna de metadados.
 */
public class FreteMapper {

    private static final Logger logger = LoggerFactory.getLogger(FreteMapper.class);

    public FreteMapper() {
        // Usando MapperUtil para ObjectMapper compartilhado
    }

    /**
     * Converte String para Integer de forma segura.
     * Delega para NumeroUtil.parseIntegerOrNull().
     */
    private Integer parseIntegerOrNull(final String value) {
        return NumeroUtil.parseIntegerOrNull(value);
    }

    /**
     * Converte o DTO de Frete em uma Entidade.
     * @param dto O objeto DTO com os dados do frete.
     * @return Um objeto FreteEntity pronto para ser salvo.
     */
    public FreteEntity toEntity(final FreteNodeDTO dto) {
        if (dto == null) {
            return null;
        }

        final FreteEntity entity = new FreteEntity();

        // 1. Mapeamento dos campos essenciais
        entity.setId(dto.getId());
        entity.setStatus(dto.getStatus());
        entity.setModal(dto.getModal());
        entity.setTipoFrete(dto.getType());
        entity.setAccountingCreditId(dto.getAccountingCreditId());
        entity.setAccountingCreditInstallmentId(dto.getAccountingCreditInstallmentId());
        entity.setValorTotal(dto.getTotalValue());
        entity.setValorNotas(dto.getInvoicesValue());
        entity.setPesoNotas(dto.getInvoicesWeight());
        entity.setIdCorporacao(dto.getCorporationId());
        entity.setIdCidadeDestino(dto.getDestinationCityId());

        // 1.1. Mapeamento dos campos expandidos (22 campos do CSV)
        if (dto.getPayer() != null) {
            entity.setPagadorId(dto.getPayer().getId());
            entity.setPagadorNome(dto.getPayer().getName());
            final String pagadorDoc = dto.getPayer().getCnpj() != null && !dto.getPayer().getCnpj().isBlank()
                ? dto.getPayer().getCnpj()
                : dto.getPayer().getCpf();
            entity.setPagadorDocumento(pagadorDoc);
        }

        if (dto.getSender() != null) {
            entity.setRemetenteId(dto.getSender().getId());
            entity.setRemetenteNome(dto.getSender().getName());
            final String remetenteDoc = dto.getSender().getCnpj() != null && !dto.getSender().getCnpj().isBlank()
                ? dto.getSender().getCnpj()
                : dto.getSender().getCpf();
            entity.setRemetenteDocumento(remetenteDoc);
        }

        if (dto.getOriginCity() != null) {
            entity.setOrigemCidade(dto.getOriginCity().getName());
            if (dto.getOriginCity().getState() != null) {
                entity.setOrigemUf(dto.getOriginCity().getState().getCode());
            }
        } else if (dto.getSender() != null && dto.getSender().getMainAddress() != null 
                   && dto.getSender().getMainAddress().getCity() != null) {
            entity.setOrigemCidade(dto.getSender().getMainAddress().getCity().getName());
            if (dto.getSender().getMainAddress().getCity().getState() != null) {
                entity.setOrigemUf(dto.getSender().getMainAddress().getCity().getState().getCode());
            }
        }

        if (dto.getReceiver() != null) {
            entity.setDestinatarioId(dto.getReceiver().getId());
            entity.setDestinatarioNome(dto.getReceiver().getName());
            final String destinatarioDoc = dto.getReceiver().getCnpj() != null && !dto.getReceiver().getCnpj().isBlank()
                ? dto.getReceiver().getCnpj()
                : dto.getReceiver().getCpf();
            entity.setDestinatarioDocumento(destinatarioDoc);
        }

        if (dto.getDestinationCity() != null) {
            entity.setDestinoCidade(dto.getDestinationCity().getName());
            if (dto.getDestinationCity().getState() != null) {
                entity.setDestinoUf(dto.getDestinationCity().getState().getCode());
            }
        } else if (dto.getReceiver() != null && dto.getReceiver().getMainAddress() != null 
                   && dto.getReceiver().getMainAddress().getCity() != null) {
            entity.setDestinoCidade(dto.getReceiver().getMainAddress().getCity().getName());
            if (dto.getReceiver().getMainAddress().getCity().getState() != null) {
                entity.setDestinoUf(dto.getReceiver().getMainAddress().getCity().getState().getCode());
            }
        }

        // Mapear campos expandidos adicionais
        if (dto.getCorporation() != null) {
            String apelido = null;
            String cnpj = null;
            if (dto.getCorporation().getPerson() != null) {
                apelido = dto.getCorporation().getPerson().getNickname();
                cnpj = dto.getCorporation().getPerson().getCnpj();
            }
            if ((apelido == null || apelido.isBlank()) && dto.getCorporation().getNickname() != null) {
                apelido = dto.getCorporation().getNickname();
            }
            if ((cnpj == null || cnpj.isBlank()) && dto.getCorporation().getCnpj() != null) {
                cnpj = dto.getCorporation().getCnpj();
            }
            if (apelido != null && !apelido.isBlank()) {
                entity.setFilialNome(apelido);
                entity.setFilialApelido(apelido);
            }
            entity.setFilialCnpj(cnpj);
        }

        if (dto.getFreightInvoices() != null && !dto.getFreightInvoices().isEmpty()) {
            final java.util.List<String> numeros = new java.util.ArrayList<>();
            for (final FreightInvoiceDTO fi : dto.getFreightInvoices()) {
                if (fi != null && fi.getInvoice() != null && fi.getInvoice().getNumber() != null) {
                    numeros.add(fi.getInvoice().getNumber());
                }
            }
            if (!numeros.isEmpty()) {
                entity.setNumeroNotaFiscal(String.join(", ", numeros));
            }
        }

        if (dto.getCustomerPriceTable() != null) {
            entity.setTabelaPrecoNome(dto.getCustomerPriceTable().getName());
        }

        if (dto.getFreightClassification() != null) {
            entity.setClassificacaoNome(dto.getFreightClassification().getName());
        }

        if (dto.getCostCenter() != null) {
            entity.setCentroCustoNome(dto.getCostCenter().getName());
        }

        if (dto.getUser() != null) {
            entity.setUsuarioNome(dto.getUser().getName());
        }

        // Mapear campos simples adicionais (22 campos do CSV)
        entity.setReferenceNumber(dto.getReferenceNumber());
        entity.setInvoicesTotalVolumes(dto.getInvoicesTotalVolumes());
        entity.setTaxedWeight(dto.getTaxedWeight());
        entity.setRealWeight(dto.getRealWeight());
        entity.setCubagesCubedWeight(dto.getCubagesCubedWeight());
        entity.setTotalCubicVolume(dto.getTotalCubicVolume());
        entity.setSubtotal(dto.getSubtotal());

        entity.setServiceType(parseIntegerOrNull(dto.getServiceType()));
        entity.setInsuranceEnabled(dto.getInsuranceEnabled());
        entity.setGrisSubtotal(dto.getGrisSubtotal());
        entity.setTdeSubtotal(dto.getTdeSubtotal());
        entity.setModalCte(dto.getModalCte());
        entity.setRedispatchSubtotal(dto.getRedispatchSubtotal());
        entity.setSuframaSubtotal(dto.getSuframaSubtotal());
        entity.setPaymentType(dto.getPaymentType());
        entity.setPreviousDocumentType(dto.getPreviousDocumentType());
        entity.setProductsValue(dto.getProductsValue());
        entity.setTrtSubtotal(dto.getTrtSubtotal());
        entity.setFreightWeightSubtotal(dto.getFreightWeightSubtotal());
        entity.setAdValoremSubtotal(dto.getAdValoremSubtotal());
        entity.setTollSubtotal(dto.getTollSubtotal());
        entity.setItrSubtotal(dto.getItrSubtotal());
        entity.setNfseSeries(dto.getNfseSeries());
        entity.setNfseNumber(parseIntegerOrNull(dto.getNfseNumber()));
        entity.setInsuranceId(dto.getInsuranceId());
        entity.setOtherFees(dto.getOtherFees());
        entity.setKm(dto.getKm());
        entity.setPaymentAccountableType(parseIntegerOrNull(dto.getPaymentAccountableType()));
        entity.setInsuredValue(dto.getInsuredValue());
        entity.setGlobalized(dto.getGlobalized());
        entity.setSecCatSubtotal(dto.getSecCatSubtotal());
        entity.setGlobalizedType(dto.getGlobalizedType());
        entity.setPriceTableAccountableType(parseIntegerOrNull(dto.getPriceTableAccountableType()));
        entity.setInsuranceAccountableType(parseIntegerOrNull(dto.getInsuranceAccountableType()));

        if (dto.getFiscalDetail() != null) {
            entity.setFiscalCstType(dto.getFiscalDetail().getCstType());
            entity.setFiscalCfopCode(dto.getFiscalDetail().getCfopCode());
            entity.setFiscalTaxValue(dto.getFiscalDetail().getTaxValue() != null ? java.math.BigDecimal.valueOf(dto.getFiscalDetail().getTaxValue()) : null);
            entity.setFiscalPisValue(dto.getFiscalDetail().getPisValue() != null ? java.math.BigDecimal.valueOf(dto.getFiscalDetail().getPisValue()) : null);
            entity.setFiscalCofinsValue(dto.getFiscalDetail().getCofinsValue() != null ? java.math.BigDecimal.valueOf(dto.getFiscalDetail().getCofinsValue()) : null);
            entity.setFiscalCalculationBasis(dto.getFiscalDetail().getCalculationBasis() != null ? java.math.BigDecimal.valueOf(dto.getFiscalDetail().getCalculationBasis()) : null);
            entity.setFiscalTaxRate(dto.getFiscalDetail().getTaxRate() != null ? java.math.BigDecimal.valueOf(dto.getFiscalDetail().getTaxRate()) : null);
            entity.setFiscalPisRate(dto.getFiscalDetail().getPisRate() != null ? java.math.BigDecimal.valueOf(dto.getFiscalDetail().getPisRate()) : null);
            entity.setFiscalCofinsRate(dto.getFiscalDetail().getCofinsRate() != null ? java.math.BigDecimal.valueOf(dto.getFiscalDetail().getCofinsRate()) : null);
            entity.setFiscalHasDifal(dto.getFiscalDetail().getHasDifal());
            entity.setFiscalDifalOrigin(dto.getFiscalDetail().getDifalTaxValueOrigin() != null ? java.math.BigDecimal.valueOf(dto.getFiscalDetail().getDifalTaxValueOrigin()) : null);
            entity.setFiscalDifalDestination(dto.getFiscalDetail().getDifalTaxValueDestination() != null ? java.math.BigDecimal.valueOf(dto.getFiscalDetail().getDifalTaxValueDestination()) : null);
        }

        if (dto.getCte() != null) {
            entity.setChaveCte(dto.getCte().getKey());
            entity.setNumeroCte(dto.getCte().getNumber());
            entity.setSerieCte(dto.getCte().getSeries());
            entity.setCteEmissionType(dto.getCte().getEmissionType());
            entity.setCteId(dto.getCte().getId());
        }

        // 2. Conversão segura de tipos de data e hora
        try {
            if (dto.getServiceAt() != null && !dto.getServiceAt().trim().isEmpty()) {
                entity.setServicoEm(OffsetDateTime.parse(dto.getServiceAt()));
            }
            if (dto.getCreatedAt() != null && !dto.getCreatedAt().trim().isEmpty()) {
                entity.setCriadoEm(OffsetDateTime.parse(dto.getCreatedAt()));
            }
            if (dto.getCte() != null && dto.getCte().getIssuedAt() != null && !dto.getCte().getIssuedAt().trim().isEmpty()) {
                entity.setCteIssuedAt(OffsetDateTime.parse(dto.getCte().getIssuedAt()));
            }
            if (dto.getCte() != null && dto.getCte().getCreatedAt() != null && !dto.getCte().getCreatedAt().trim().isEmpty()) {
                entity.setCteCreatedAt(OffsetDateTime.parse(dto.getCte().getCreatedAt()));
            }
            if (dto.getDeliveryPredictionDate() != null && !dto.getDeliveryPredictionDate().trim().isEmpty()) {
                entity.setDataPrevisaoEntrega(LocalDate.parse(dto.getDeliveryPredictionDate()));
            }
            if (dto.getServiceDate() != null && !dto.getServiceDate().trim().isEmpty()) {
                entity.setServiceDate(LocalDate.parse(dto.getServiceDate()));
            }
        } catch (final DateTimeParseException e) {
            logger.error("❌ Erro ao converter data para frete ID {}: serviceAt='{}', createdAt='{}', deliveryPredictionDate='{}', serviceDate='{}' - {}", 
                dto.getId(), dto.getServiceAt(), dto.getCreatedAt(), dto.getDeliveryPredictionDate(), dto.getServiceDate(), e.getMessage());
            logger.debug("Stack trace completo:", e);
        }

        // 3. Empacotamento de todos os metadados
        final String metadata = MapperUtil.toJson(dto);
        entity.setMetadata(metadata);

        return entity;
    }
}
