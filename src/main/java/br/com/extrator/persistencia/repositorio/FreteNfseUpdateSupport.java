package br.com.extrator.persistencia.repositorio;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/persistencia/repositorio/FreteNfseUpdateSupport.java
Classe  :  (class)
Pacote  : br.com.extrator.persistencia.repositorio
Modulo  : Persistencia - Repositorio
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import br.com.extrator.dominio.graphql.fretes.nfse.FreteNfsePayload;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.slf4j.Logger;

/**
 * Isola o enriquecimento de NFSe em fretes para manter o repositorio focado no MERGE principal.
 */
final class FreteNfseUpdateSupport {
    private final Logger logger;

    FreteNfseUpdateSupport(final Logger logger) {
        this.logger = logger;
    }

    int atualizarCamposNfse(
        final Connection conexao,
        final java.util.List<? extends FreteNfsePayload> nfseList
    ) throws SQLException {
        if (nfseList == null || nfseList.isEmpty()) {
            return 0;
        }

        int totalAtualizados = 0;
        int totalIgnoradosSemId = 0;
        int totalNaoEncontradosNoBanco = 0;
        final String sqlById = """
            UPDATE dbo.fretes
            SET
                nfse_integration_id = ?,
                nfse_status = ?,
                nfse_issued_at = ?,
                nfse_cancelation_reason = ?,
                nfse_pdf_service_url = ?,
                nfse_corporation_id = ?,
                nfse_service_description = ?,
                nfse_xml_document = ?,
                nfse_series = ?,
                nfse_number = ?
            WHERE id = ?
        """;
        final String sqlByNumber = """
            UPDATE dbo.fretes
            SET
                nfse_integration_id = ?,
                nfse_status = ?,
                nfse_issued_at = ?,
                nfse_cancelation_reason = ?,
                nfse_pdf_service_url = ?,
                nfse_corporation_id = ?,
                nfse_service_description = ?,
                nfse_xml_document = ?,
                nfse_series = ?,
                nfse_number = ?
            WHERE nfse_number = ?
        """;

        try (PreparedStatement psId = conexao.prepareStatement(sqlById);
             PreparedStatement psNumber = conexao.prepareStatement(sqlByNumber)) {
            for (final FreteNfsePayload nfse : nfseList) {
                final java.time.LocalDate dt = parseIssuedAt(nfse.getIssuedAt());
                final String serviceDesc = (nfse.getServiceDescription() != null
                    && !nfse.getServiceDescription().isBlank())
                    ? nfse.getServiceDescription()
                    : extrairDiscriminacaoDoXml(nfse.getXmlDocument());

                final int linhasAfetadas = atualizarNfse(psId, psNumber, nfse, dt, serviceDesc);
                if (linhasAfetadas < 0) {
                    totalIgnoradosSemId++;
                } else if (linhasAfetadas > 0) {
                    totalAtualizados++;
                } else {
                    totalNaoEncontradosNoBanco++;
                }
            }
        }

        if (totalIgnoradosSemId > 0) {
            logger.warn(
                "{} NFSe ignoradas por ausência de vínculo de frete (freightId null)",
                totalIgnoradosSemId
            );
        }
        logger.info(
            "Resumo Enriquecimento NFSe: Processados={}, Atualizados={}, Sem Frete Pai no Banco={}, Sem ID={}",
            nfseList.size(),
            totalAtualizados,
            totalNaoEncontradosNoBanco,
            totalIgnoradosSemId
        );
        return totalAtualizados;
    }

    private int atualizarNfse(
        final PreparedStatement psId,
        final PreparedStatement psNumber,
        final FreteNfsePayload nfse,
        final java.time.LocalDate dt,
        final String serviceDesc
    ) throws SQLException {
        if (nfse.getFreightId() != null) {
            preencherParametrosComuns(psId, nfse, dt, serviceDesc);
            psId.setObject(11, nfse.getFreightId(), java.sql.Types.BIGINT);
            int linhasAfetadas = psId.executeUpdate();
            if (linhasAfetadas == 0 && nfse.getNumber() != null) {
                linhasAfetadas = atualizarPorNumero(psNumber, nfse, dt, serviceDesc);
            }
            return linhasAfetadas;
        }

        if (nfse.getNumber() != null) {
            return atualizarPorNumero(psNumber, nfse, dt, serviceDesc);
        }

        return -1;
    }

    private int atualizarPorNumero(
        final PreparedStatement psNumber,
        final FreteNfsePayload nfse,
        final java.time.LocalDate dt,
        final String serviceDesc
    ) throws SQLException {
        preencherParametrosComuns(psNumber, nfse, dt, serviceDesc);
        psNumber.setObject(11, nfse.getNumber(), java.sql.Types.INTEGER);
        return psNumber.executeUpdate();
    }

    private void preencherParametrosComuns(
        final PreparedStatement statement,
        final FreteNfsePayload nfse,
        final java.time.LocalDate dt,
        final String serviceDesc
    ) throws SQLException {
        statement.setString(1, nfse.getId());
        statement.setString(2, nfse.getStatus());
        if (dt != null) {
            statement.setObject(3, dt, java.sql.Types.DATE);
        } else {
            statement.setNull(3, java.sql.Types.DATE);
        }
        statement.setString(4, nfse.getCancelationReason());
        statement.setString(5, nfse.getPdfServiceUrl());
        if (nfse.getCorporationId() != null) {
            statement.setObject(6, nfse.getCorporationId(), java.sql.Types.BIGINT);
        } else {
            statement.setNull(6, java.sql.Types.BIGINT);
        }
        statement.setString(7, serviceDesc);
        statement.setString(8, nfse.getXmlDocument());
        statement.setString(9, nfse.getRpsSeries());
        if (nfse.getNumber() != null) {
            statement.setObject(10, nfse.getNumber(), java.sql.Types.INTEGER);
        } else {
            statement.setNull(10, java.sql.Types.INTEGER);
        }
    }

    private static java.time.LocalDate parseIssuedAt(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.time.LocalDate.parse(value);
        } catch (final Exception ignored1) {
            // Tenta formatos mais completos abaixo.
        }
        try {
            return java.time.OffsetDateTime.parse(value).toLocalDate();
        } catch (final Exception ignored2) {
            // Mantem compatibilidade com payloads heterogeneos.
        }
        try {
            return java.time.LocalDateTime.parse(value).toLocalDate();
        } catch (final Exception ignored3) {
            // Ultima tentativa abaixo.
        }
        try {
            return java.time.LocalDate.parse(value.substring(0, Math.min(10, value.length())));
        } catch (final Exception ignored4) {
            return null;
        }
    }

    private static String extrairDiscriminacaoDoXml(final String xml) {
        if (xml == null || xml.isBlank()) {
            return null;
        }
        try {
            final String lower = xml.toLowerCase();
            final String tag = "discriminacao";
            final int start = lower.indexOf("<" + tag + ">");
            final int end = lower.indexOf("</" + tag + ">");
            if (start >= 0 && end > start) {
                final String inner = xml.substring(start + tag.length() + 2, end);
                final String cdataStart = "<![CDATA[";
                final String cdataEnd = "]]>";
                String text = inner.trim();
                if (text.startsWith(cdataStart) && text.endsWith(cdataEnd)) {
                    text = text.substring(cdataStart.length(), text.length() - cdataEnd.length()).trim();
                }
                return text.isBlank() ? null : text;
            }
        } catch (final Exception ignored) {
            return null;
        }
        return null;
    }
}
