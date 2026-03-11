package br.com.extrator.integracao.graphql.extractors;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/graphql/extractors/FaturaGraphQLEntityMapper.java
Classe  :  (class)
Pacote  : br.com.extrator.integracao.graphql.extractors
Modulo  : Integracao - GraphQL Extrator
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import br.com.extrator.persistencia.entidade.FaturaGraphQLEntity;
import br.com.extrator.dominio.graphql.faturas.CreditCustomerBillingNodeDTO;
import br.com.extrator.suporte.mapeamento.MapperUtil;

final class FaturaGraphQLEntityMapper {
    FaturaGraphQLEntity mapear(final CreditCustomerBillingNodeDTO dto) {
        final FaturaGraphQLEntity entity = new FaturaGraphQLEntity();
        entity.setId(dto.getId());
        entity.setDocument(dto.getDocument());
        entity.setIssueDate(parseLocalDateOrNull(dto.getIssueDate()));
        entity.setDueDate(parseLocalDateOrNull(dto.getDueDate()));

        if (dto.getInstallments() != null && !dto.getInstallments().isEmpty()) {
            final CreditCustomerBillingNodeDTO.InstallmentDTO primeiraParcela = dto.getInstallments().get(0);
            entity.setOriginalDueDate(parseLocalDateOrNull(primeiraParcela.getOriginalDueDate()));
            entity.setStatus(primeiraParcela.getStatus());

            if (primeiraParcela.getAccountingCredit() != null) {
                final String nfseNumero = primeiraParcela.getAccountingCredit().getDocument();
                if (nfseNumero != null && !nfseNumero.trim().isEmpty()) {
                    entity.setNfseNumero(nfseNumero.trim());
                }
            }

            if (primeiraParcela.getAccountingBankAccount() != null) {
                final CreditCustomerBillingNodeDTO.AccountingBankAccountDTO contaBancaria =
                    primeiraParcela.getAccountingBankAccount();
                if (contaBancaria.getPortfolioVariation() != null && !contaBancaria.getPortfolioVariation().trim().isEmpty()) {
                    entity.setCarteiraBanco(contaBancaria.getPortfolioVariation().trim());
                }
                if (contaBancaria.getCustomInstruction() != null && !contaBancaria.getCustomInstruction().trim().isEmpty()) {
                    entity.setInstrucaoBoleto(contaBancaria.getCustomInstruction().trim());
                }
                if (contaBancaria.getBankName() != null && !contaBancaria.getBankName().trim().isEmpty()) {
                    entity.setBancoNome(contaBancaria.getBankName().trim());
                }
            }

            if (primeiraParcela.getPaymentMethod() != null && !primeiraParcela.getPaymentMethod().trim().isEmpty()) {
                entity.setMetodoPagamento(primeiraParcela.getPaymentMethod().trim());
            }
        }

        entity.setValue(dto.getValue());
        entity.setPaidValue(dto.getPaidValue());
        entity.setValueToPay(dto.getValueToPay());
        entity.setDiscountValue(dto.getDiscountValue());
        entity.setInterestValue(dto.getInterestValue());
        entity.setPaid(dto.getPaid());
        entity.setType(dto.getType());
        entity.setComments(dto.getComments());
        entity.setSequenceCode(dto.getSequenceCode());
        entity.setCompetenceMonth(dto.getCompetenceMonth());
        entity.setCompetenceYear(dto.getCompetenceYear());

        if (dto.getCorporation() != null) {
            if (dto.getCorporation().getId() != null) {
                entity.setCorporationId(parseLongOrNull(dto.getCorporation().getId()));
            }
            if (dto.getCorporation().getPerson() != null) {
                entity.setCorporationName(dto.getCorporation().getPerson().getNickname());
                entity.setCorporationCnpj(dto.getCorporation().getPerson().getCnpj());
            }
        }

        entity.setMetadata(MapperUtil.toJson(dto));
        return entity;
    }

    private LocalDate parseLocalDateOrNull(final String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDate.parse(value);
        } catch (final DateTimeParseException e) {
            return null;
        }
    }

    private Long parseLongOrNull(final String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value);
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
