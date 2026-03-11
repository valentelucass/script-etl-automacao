/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/validacao/ValidacaoApiBanco24hDetalhadaMetadataHasher.java
Classe  : ValidacaoApiBanco24hDetalhadaMetadataHasher (final class)
Pacote  : br.com.extrator.aplicacao.validacao
Modulo  : Use Case - Validacao

Papel   : Gerador de hashes SHA256 para metadados de entidades (com remocao de campos volateis por entidade).

Conecta com:
- MapperUtil (suporte.mapeamento)
- ConstantesEntidades (suporte.validacao)
- LoggerConsole (suporte.console)

Fluxo geral:
1) hashMetadata(entidade, metadata) normaliza JSON removendo campos volateis.
2) Calcula SHA256 em formato hex da string normalizada.
3) Com fallback: JSON parsing error -> usa string trim().

Estrutura interna:
Atributos-chave:
- log: LoggerConsole statico para debug.
Metodos principais:
- hashMetadata(String, String): retorna hash SHA256 hex.
- normalizarMetadataParaComparacao(): parse JSON, remove campos volateis por switch de entidade.
- removerCamposVolateisComparacao(): remove status, datas de predicao, valores, volumes (dependendo entidade).
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.validacao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.mapeamento.MapperUtil;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

final class ValidacaoApiBanco24hDetalhadaMetadataHasher {
    private static final LoggerConsole log =
        LoggerConsole.getLogger(ValidacaoApiBanco24hDetalhadaMetadataHasher.class);

    String hashMetadata(final String entidade, final String metadata) {
        final String normalizado = normalizarMetadataParaComparacao(entidade, metadata);
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(normalizado.getBytes(StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder(hash.length * 2);
            for (final byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nao disponivel", e);
        }
    }

    private String normalizarMetadataParaComparacao(final String entidade, final String metadata) {
        if (metadata == null || metadata.trim().isEmpty()) {
            return "__NULL__";
        }

        try {
            final JsonNode parsed = MapperUtil.sharedJson().readTree(metadata);
            if (!parsed.isObject()) {
                return metadata.trim();
            }
            final ObjectNode obj = (ObjectNode) parsed.deepCopy();
            final ObjectNode projetado = projetarCamposEstaveis(entidade, obj);
            if (projetado != null && projetado.size() > 0) {
                return MapperUtil.sharedJson().writeValueAsString(projetado);
            }
            removerCamposVolateisComparacao(entidade, obj);
            return MapperUtil.sharedJson().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.debug(
                "Fallback de normalizacao de metadata por erro de parse JSON | entidade={} | erro={}",
                entidade,
                e.getOriginalMessage()
            );
            return metadata.trim();
        }
    }

    private ObjectNode projetarCamposEstaveis(final String entidade, final ObjectNode origem) {
        final List<String> caminhos = switch (entidade) {
            case ConstantesEntidades.MANIFESTOS -> List.of(
                "sequence_code",
                "pick_sequence_code",
                "mdfe_number",
                "mdfe_key",
                "branch_nickname",
                "distribution_pole",
                "classification",
                "vehicle_plate",
                "vehicle_type",
                "contract_number",
                "contract_type",
                "calculation_type",
                "cargo_type"
            );
            case ConstantesEntidades.FRETES -> List.of(
                "id",
                "accountingCreditId",
                "accountingCreditInstallmentId",
                "referenceNumber",
                "serviceAt",
                "createdAt",
                "serviceDate",
                "modal",
                "modalCte",
                "type",
                "serviceType",
                "total",
                "subtotal",
                "corporation.id",
                "corporation.nickname",
                "corporation.cnpj",
                "payer.id",
                "payer.cnpj",
                "payer.cpf",
                "sender.id",
                "sender.cnpj",
                "sender.cpf",
                "receiver.id",
                "receiver.cnpj",
                "receiver.cpf",
                "cte.id",
                "cte.key",
                "cte.number",
                "cte.series",
                "cte.issuedAt",
                "nfseNumber",
                "nfseSeries"
            );
            case ConstantesEntidades.COLETAS -> List.of(
                "id",
                "sequenceCode",
                "requestDate",
                "serviceDate",
                "requestHour",
                "serviceStartHour",
                "finishDate",
                "serviceEndHour",
                "requester",
                "agentId",
                "manifestItemPickId",
                "vehicleTypeId",
                "cargoClassificationId",
                "costCenterId",
                "pickTypeId",
                "pickupLocationId",
                "corporation.id",
                "corporation.person.nickname",
                "corporation.person.cnpj",
                "customer.id",
                "customer.cnpj",
                "user.id",
                "pickAddress.postalCode",
                "pickAddress.number",
                "pickAddress.city.name",
                "pickAddress.city.state.code"
            );
            case ConstantesEntidades.LOCALIZACAO_CARGAS -> List.of(
                "corporation_sequence_number",
                "sequence_number",
                "type",
                "service_at",
                "service_type",
                "total",
                "fit_crn_psn_nickname",
                "fit_dyn_name",
                "fit_dyn_drt_nickname",
                "fit_fsn_name",
                "fit_fln_cln_nickname",
                "fit_o_n_name",
                "fit_o_n_drt_nickname",
                "fit_fhe_cte_number",
                "fit_fhe_cte_key"
            );
            case ConstantesEntidades.CONTAS_A_PAGAR -> List.of(
                "ant_ils_sequence_code",
                "document",
                "issue_date",
                "type",
                "competence_month",
                "competence_year",
                "ant_rir_name",
                "ant_crn_psn_nickname",
                "ant_ces_acr_name",
                "ant_ils_pas_ant_classification",
                "ant_ils_pas_ant_name",
                "ant_aln_name",
                "ant_ils_expense_description"
            );
            default -> null;
        };

        if (caminhos == null || caminhos.isEmpty()) {
            return null;
        }

        final ObjectNode projetado = MapperUtil.sharedJson().createObjectNode();
        for (final String caminho : caminhos) {
            copiarCaminhoSePresente(origem, projetado, caminho);
        }
        return projetado;
    }

    private void copiarCaminhoSePresente(final JsonNode origem, final ObjectNode destino, final String caminho) {
        if (origem == null || destino == null || caminho == null || caminho.isBlank()) {
            return;
        }

        final String[] partes = caminho.split("\\.");
        JsonNode cursorOrigem = origem;
        for (final String parte : partes) {
            if (cursorOrigem == null || !cursorOrigem.has(parte)) {
                return;
            }
            cursorOrigem = cursorOrigem.get(parte);
        }

        ObjectNode cursorDestino = destino;
        for (int index = 0; index < partes.length - 1; index++) {
            final String parte = partes[index];
            JsonNode atual = cursorDestino.get(parte);
            if (!(atual instanceof ObjectNode)) {
                atual = cursorDestino.putObject(parte);
            }
            cursorDestino = (ObjectNode) atual;
        }
        cursorDestino.set(partes[partes.length - 1], cursorOrigem.deepCopy());
    }

    private void removerCamposVolateisComparacao(final String entidade, final ObjectNode obj) {
        if (entidade == null || obj == null) {
            return;
        }
        switch (entidade) {
            case ConstantesEntidades.LOCALIZACAO_CARGAS -> obj.remove("fit_fln_status");
            case ConstantesEntidades.FRETES -> {
                obj.remove("status");
                obj.remove("deliveryPredictionDate");
                obj.remove("delivery_prediction_date");
            }
            case ConstantesEntidades.COLETAS -> {
                obj.remove("invoicesValue");
                obj.remove("invoicesVolumes");
                obj.remove("invoicesWeight");
                obj.remove("taxedWeight");
            }
            default -> {
                // Sem tratamento especial para outras entidades.
            }
        }
    }
}
