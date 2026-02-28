/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/dataexport/faturaporcliente/FaturaPorClienteMapper.java
Classe  : FaturaPorClienteMapper (class)
Pacote  : br.com.extrator.modelo.dataexport.faturaporcliente
Modulo  : DTO/Mapper DataExport
Papel   : Implementa responsabilidade de fatura por cliente mapper.

Conecta com:
- FaturaPorClienteEntity (db.entity)
- DataUtil (util.mapeamento)
- MapperUtil (util.mapeamento)
- NumeroUtil (util.mapeamento)

Fluxo geral:
1) Modela payloads da API DataExport.
2) Mapeia resposta para entidades internas.
3) Apoia carga e deduplicacao no destino.

Estrutura interna:
Metodos principais:
- FaturaPorClienteMapper(): realiza operacao relacionada a "fatura por cliente mapper".
- toEntity(...1 args): realiza operacao relacionada a "to entity".
- calcularIdentificadorUnico(...1 args): realiza operacao relacionada a "calcular identificador unico".
- limitarOuHashear(...1 args): realiza operacao relacionada a "limitar ou hashear".
- montarRepresentacaoCanonica(...1 args): construi estrutura de entrada/saida.
- appendCampo(...3 args): realiza operacao relacionada a "append campo".
- normalizarTexto(...1 args): realiza operacao relacionada a "normalizar texto".
- normalizarLista(...1 args): realiza operacao relacionada a "normalizar lista".
- calcularSha256Hex(...1 args): realiza operacao relacionada a "calcular sha256 hex".
- converterParaBigDecimal(...1 args): transforma dados entre formatos/modelos.
- converterParaLocalDate(...1 args): transforma dados entre formatos/modelos.
- converterParaOffsetDateTime(...1 args): transforma dados entre formatos/modelos.
- traduzirStatus(...1 args): realiza operacao relacionada a "traduzir status".
- traduzirTipoFrete(...1 args): realiza operacao relacionada a "traduzir tipo frete".
Atributos-chave:
- logger: logger da classe para diagnostico.
- UNIQUE_ID_MAX_LENGTH: campo de estado para "unique id max length".
- HASH_PREFIX: campo de estado para "hash prefix".
- KEY_HASH_PREFIX: campo de estado para "key hash prefix".
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.dataexport.faturaporcliente;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.FaturaPorClienteEntity;
import br.com.extrator.util.mapeamento.DataUtil;
import br.com.extrator.util.mapeamento.MapperUtil;
import br.com.extrator.util.mapeamento.NumeroUtil;

/**
 * Mapper para converter FaturaPorClienteDTO em FaturaPorClienteEntity.
 */
public class FaturaPorClienteMapper {
    private static final Logger logger = LoggerFactory.getLogger(FaturaPorClienteMapper.class);
    private static final int UNIQUE_ID_MAX_LENGTH = 100;
    private static final String HASH_PREFIX = "FPC-HASH-";
    private static final String KEY_HASH_PREFIX = "FPC-KEYHASH-";

    public FaturaPorClienteMapper() {
        // Usa utilitarios compartilhados (MapperUtil/NumeroUtil/DataUtil)
    }

    /**
     * Converte DTO para Entity, aplicando transformacoes necessarias.
     */
    public FaturaPorClienteEntity toEntity(final FaturaPorClienteDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("DTO nao pode ser null");
        }

        final FaturaPorClienteEntity entity = new FaturaPorClienteEntity();

        try {
            // 1. Calcula identificador unico
            final String uniqueId = calcularIdentificadorUnico(dto);
            entity.setUniqueId(uniqueId);

            // 2. Documentos fiscais (NFS-e tem prioridade)
            final boolean isNfse = dto.getNfseNumber() != null;
            if (isNfse) {
                entity.setNumeroNfse(dto.getNfseNumber());
                entity.setNumeroCte(null);
                entity.setChaveCte(null);
                entity.setStatusCte(null);
                entity.setStatusCteResult(null);
                entity.setDataEmissaoCte(null);
            } else {
                final boolean isCte = (dto.getCteKey() != null && !dto.getCteKey().trim().isEmpty())
                    || dto.getCteNumber() != null;
                if (isCte) {
                    entity.setNumeroCte(dto.getCteNumber());
                    entity.setChaveCte(dto.getCteKey());
                    entity.setNumeroNfse(null);
                    entity.setStatusCte(traduzirStatus(dto.getCteStatus()));
                    entity.setStatusCteResult(dto.getCteStatusResult());
                    entity.setDataEmissaoCte(converterParaOffsetDateTime(dto.getCteIssuedAt()));
                } else {
                    entity.setNumeroCte(null);
                    entity.setChaveCte(null);
                    entity.setNumeroNfse(null);
                    entity.setStatusCte(null);
                    entity.setStatusCteResult(null);
                    entity.setDataEmissaoCte(null);
                }
            }

            // 3. Dados da fatura
            entity.setNumeroFatura(dto.getFaturaDocument());
            entity.setDataEmissaoFatura(converterParaLocalDate(dto.getFaturaIssueDate()));
            entity.setDataVencimentoFatura(converterParaLocalDate(dto.getFaturaDueDate()));
            entity.setDataBaixaFatura(converterParaLocalDate(dto.getFaturaBaixaDate()));
            entity.setFitAntOriginalDueDate(converterParaLocalDate(dto.getFaturaOriginalDueDate()));

            entity.setFitAntDocument(dto.getFaturaDocument());
            entity.setFitAntIssueDate(converterParaLocalDate(dto.getFaturaIssueDate()));
            entity.setFitAntValue(converterParaBigDecimal(dto.getFaturaValue()));

            // 4. Valores
            entity.setValorFrete(converterParaBigDecimal(dto.getValorFrete()));
            entity.setValorFatura(converterParaBigDecimal(dto.getFaturaValue()));
            entity.setThirdPartyCtesValue(converterParaBigDecimal(dto.getThirdPartyCtesValue()));

            // 5. Classificacao operacional
            entity.setFilial(dto.getFilial());
            entity.setTipoFrete(traduzirTipoFrete(dto.getTipoFrete()));
            entity.setClassificacao(dto.getClassificacao());
            entity.setEstado(dto.getEstado());

            // 6. Envolvidos
            entity.setPagadorNome(dto.getPagadorNome());
            entity.setPagadorDocumento(dto.getPagadorDocumento());
            entity.setRemetenteNome(dto.getRemetenteNome());
            entity.setRemetenteDocumento(dto.getRemetenteDocumento());
            entity.setDestinatarioNome(dto.getDestinatarioNome());
            entity.setDestinatarioDocumento(dto.getDestinatarioDocumento());
            entity.setVendedorNome(dto.getVendedorNome());

            // 7. Listas
            entity.setNotasFiscais(converterListaParaString(dto.getNotasFiscais()));
            entity.setPedidosCliente(converterListaParaString(dto.getPedidosCliente()));

            // 8. Metadata completo
            final String metadata = MapperUtil.toJson(dto);
            entity.setMetadata(metadata);

        } catch (final Exception e) {
            logger.error("Erro ao mapear FaturaPorClienteDTO para Entity: {}", e.getMessage(), e);
            throw new RuntimeException("Falha no mapeamento de Fatura por Cliente", e);
        }

        return entity;
    }

    /**
     * Calcula identificador unico conforme regras de negocio:
     * 1) NFS-e
     * 2) Chave CT-e
     * 3) Documento da fatura
     * 4) Billing ID
     * 5) Hash deterministico dos campos estaveis
     */
    public String calcularIdentificadorUnico(final FaturaPorClienteDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("DTO nao pode ser null ao calcular unique_id");
        }

        if (dto.getNfseNumber() != null) {
            return "NFSE-" + dto.getNfseNumber();
        }

        if (dto.getCteKey() != null && !dto.getCteKey().trim().isEmpty()) {
            return limitarOuHashear(dto.getCteKey().trim());
        }

        if (dto.getFaturaDocument() != null && !dto.getFaturaDocument().trim().isEmpty()) {
            return limitarOuHashear("FATURA-" + dto.getFaturaDocument().trim());
        }

        if (dto.getBillingId() != null && !dto.getBillingId().trim().isEmpty()) {
            return limitarOuHashear("BILLING-" + dto.getBillingId().trim());
        }

        final String hashId = HASH_PREFIX + calcularSha256Hex(montarRepresentacaoCanonica(dto));
        logger.debug("Gerando unique_id por hash deterministico para registro sem chave natural: {}",
            hashId.substring(0, Math.min(18, hashId.length())));
        return hashId;
    }

    private String limitarOuHashear(final String valor) {
        if (valor.length() <= UNIQUE_ID_MAX_LENGTH) {
            return valor;
        }

        final String hash = KEY_HASH_PREFIX + calcularSha256Hex(valor);
        logger.warn("unique_id acima de {} caracteres. Aplicando hash para manter integridade: {}",
            UNIQUE_ID_MAX_LENGTH, hash.substring(0, Math.min(18, hash.length())));
        return hash;
    }

    private String montarRepresentacaoCanonica(final FaturaPorClienteDTO dto) {
        final StringBuilder sb = new StringBuilder(512);
        appendCampo(sb, "nfse", dto.getNfseNumber() != null ? String.valueOf(dto.getNfseNumber()) : null);
        appendCampo(sb, "cteNumber", dto.getCteNumber() != null ? String.valueOf(dto.getCteNumber()) : null);
        appendCampo(sb, "cteKey", dto.getCteKey());
        appendCampo(sb, "cteIssuedAt", dto.getCteIssuedAt());
        appendCampo(sb, "cteStatus", dto.getCteStatus());
        appendCampo(sb, "cteStatusResult", dto.getCteStatusResult());
        appendCampo(sb, "document", dto.getFaturaDocument());
        appendCampo(sb, "issueDate", dto.getFaturaIssueDate());
        appendCampo(sb, "dueDate", dto.getFaturaDueDate());
        appendCampo(sb, "baixaDate", dto.getFaturaBaixaDate());
        appendCampo(sb, "originalDueDate", dto.getFaturaOriginalDueDate());
        appendCampo(sb, "faturaValue", dto.getFaturaValue());
        appendCampo(sb, "valorFrete", dto.getValorFrete());
        appendCampo(sb, "thirdParty", dto.getThirdPartyCtesValue());
        appendCampo(sb, "tipoFrete", dto.getTipoFrete());
        appendCampo(sb, "filial", dto.getFilial());
        appendCampo(sb, "estado", dto.getEstado());
        appendCampo(sb, "classificacao", dto.getClassificacao());
        appendCampo(sb, "pagadorNome", dto.getPagadorNome());
        appendCampo(sb, "pagadorDocumento", dto.getPagadorDocumento());
        appendCampo(sb, "remetenteNome", dto.getRemetenteNome());
        appendCampo(sb, "remetenteDocumento", dto.getRemetenteDocumento());
        appendCampo(sb, "destinatarioNome", dto.getDestinatarioNome());
        appendCampo(sb, "destinatarioDocumento", dto.getDestinatarioDocumento());
        appendCampo(sb, "vendedorNome", dto.getVendedorNome());
        appendCampo(sb, "billingId", dto.getBillingId());
        appendCampo(sb, "notasFiscais", normalizarLista(dto.getNotasFiscais()));
        appendCampo(sb, "pedidosCliente", normalizarLista(dto.getPedidosCliente()));
        return sb.toString();
    }

    private void appendCampo(final StringBuilder sb, final String nome, final String valor) {
        sb.append(nome).append('=').append(normalizarTexto(valor)).append('|');
    }

    private String normalizarTexto(final String valor) {
        if (valor == null) {
            return "<null>";
        }
        final String texto = valor.trim();
        return texto.isEmpty() ? "<empty>" : texto;
    }

    private String normalizarLista(final List<String> lista) {
        if (lista == null || lista.isEmpty()) {
            return "<null>";
        }

        final List<String> normalizada = new ArrayList<>();
        for (final String item : lista) {
            final String valor = normalizarTexto(item);
            if (!"<null>".equals(valor) && !"<empty>".equals(valor)) {
                normalizada.add(valor);
            }
        }

        if (normalizada.isEmpty()) {
            return "<empty>";
        }

        Collections.sort(normalizada);
        return String.join(",", normalizada);
    }

    private String calcularSha256Hex(final String texto) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(texto.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(hash.length * 2);
            for (final byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("Nao foi possivel calcular hash SHA-256 para unique_id", e);
        }
    }

    /**
     * Converte string para BigDecimal usando Locale.US (ponto decimal).
     */
    private BigDecimal converterParaBigDecimal(final String valor) {
        return NumeroUtil.parseBigDecimalUS(valor);
    }

    /**
     * Converte string ISO para LocalDate (yyyy-MM-dd).
     */
    private LocalDate converterParaLocalDate(final String data) {
        return DataUtil.parseLocalDate(data);
    }

    /**
     * Converte string ISO para OffsetDateTime.
     */
    private OffsetDateTime converterParaOffsetDateTime(final String dataHora) {
        return DataUtil.parseOffsetDateTime(dataHora);
    }

    /**
     * Traduz status do CT-e.
     */
    private String traduzirStatus(final String status) {
        if (status == null || status.trim().isEmpty()) {
            return null;
        }
        return switch (status.toLowerCase().trim()) {
            case "authorized" -> "Autorizado";
            case "cancelled" -> "Cancelado";
            case "denied" -> "Negado";
            case "pending" -> "Pendente";
            default -> status;
        };
    }

    /**
     * Traduz tipo de frete (ex: Freight::Normal -> Normal).
     */
    private String traduzirTipoFrete(final String tipo) {
        if (tipo == null || tipo.trim().isEmpty()) {
            return null;
        }
        return tipo.replace("Freight::", "").trim();
    }

    /**
     * Converte lista em string separada por virgula.
     */
    private String converterListaParaString(final List<String> lista) {
        if (lista == null || lista.isEmpty()) {
            return null;
        }
        return String.join(", ", lista);
    }
}
