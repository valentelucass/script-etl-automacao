/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/modelo/dataexport/contasapagar/ContasAPagarMapper.java
Classe  : ContasAPagarMapper (class)
Pacote  : br.com.extrator.modelo.dataexport.contasapagar
Modulo  : DTO/Mapper DataExport
Papel   : Implementa responsabilidade de contas apagar mapper.

Conecta com:
- ContasAPagarDataExportEntity (db.entity)
- FormatadorData (util.formatacao)
- ValidadorDTO (util.validacao)
- ResultadoValidacao (util.validacao.ValidadorDTO)
- MapperUtil (util.mapeamento)

Fluxo geral:
1) Modela payloads da API DataExport.
2) Mapeia resposta para entidades internas.
3) Apoia carga e deduplicacao no destino.

Estrutura interna:
Metodos principais:
- toEntity(...1 args): realiza operacao relacionada a "to entity".
- parseLong(...1 args): realiza operacao relacionada a "parse long".
- parseInteger(...1 args): realiza operacao relacionada a "parse integer".
- parseBigDecimal(...1 args): realiza operacao relacionada a "parse big decimal".
- parseLocalDate(...1 args): realiza operacao relacionada a "parse local date".
- parseOffsetDateTime(...1 args): realiza operacao relacionada a "parse offset date time".
- limparETraduzirTipoLancamento(...1 args): realiza operacao relacionada a "limpar etraduzir tipo lancamento".
- traduzirClassificacaoContabil(...1 args): realiza operacao relacionada a "traduzir classificacao contabil".
Atributos-chave:
- logger: logger da classe para diagnostico.
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.dataexport.contasapagar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.ContasAPagarDataExportEntity;
import br.com.extrator.util.formatacao.FormatadorData;
import br.com.extrator.util.validacao.ValidadorDTO;
import br.com.extrator.util.validacao.ValidadorDTO.ResultadoValidacao;
import br.com.extrator.util.mapeamento.MapperUtil;

/**
 * Mapper para conversão de FaturaAPagarDataExportDTO em FaturaAPagarDataExportEntity.
 */
public class ContasAPagarMapper {
    private static final Logger logger = LoggerFactory.getLogger(ContasAPagarMapper.class);
    
    /**
     * Converte DTO em Entity com tratamento de erros robusto.
     * PROBLEMA #6 CORRIGIDO: Adicionada validação de campos críticos.
     * 
     * @throws IllegalArgumentException se campos críticos forem inválidos
     */
    public ContasAPagarDataExportEntity toEntity(final ContasAPagarDTO dto) {
        if (dto == null) {
            return null;
        }
        
        // PROBLEMA #6: Validação de campos críticos
        final ResultadoValidacao validacao = ValidadorDTO.criarValidacao("ContasAPagar");
        ValidadorDTO.validarIdString(validacao, "sequence_code", dto.getSequenceCode());
        
        if (!validacao.isValido()) {
            validacao.logErros();
            throw new IllegalArgumentException("Conta a Pagar inválida: sequence_code é obrigatório. Erros: " + validacao.getErros());
        }
        
        final ContasAPagarDataExportEntity entity = new ContasAPagarDataExportEntity();
        
        // CHAVE PRIMÁRIA
        entity.setSequenceCode(parseLong(dto.getSequenceCode()));
        
        // DADOS DO DOCUMENTO
        entity.setDocumentNumber(dto.getDocumentNumber());
        entity.setIssueDate(parseLocalDate(dto.getIssueDate()));
        entity.setTipoLancamento(limparETraduzirTipoLancamento(dto.getType()));
        
        // VALORES FINANCEIROS
        entity.setValorOriginal(parseBigDecimal(dto.getOriginalValue()));
        entity.setValorJuros(parseBigDecimal(dto.getInterestValue()));
        entity.setValorDesconto(parseBigDecimal(dto.getDiscountValue()));
        entity.setValorAPagar(parseBigDecimal(dto.getValueToPay()));
        entity.setValorPago(parseBigDecimal(dto.getPaidValue()));
        
        // STATUS DE PAGAMENTO
        entity.setStatusPagamento(dto.getPaid() != null && dto.getPaid() ? "PAGO" : "ABERTO");
        
        // COMPETÊNCIA
        entity.setMesCompetencia(parseInteger(dto.getCompetenceMonth()));
        entity.setAnoCompetencia(parseInteger(dto.getCompetenceYear()));
        
        // DATAS
        entity.setDataCriacao(parseOffsetDateTime(dto.getCreatedAt()));
        entity.setDataLiquidacao(parseLocalDate(dto.getLiquidationDate()));
        entity.setDataTransacao(parseLocalDate(dto.getTransactionDate()));
        
        // FORNECEDOR
        entity.setNomeFornecedor(dto.getProviderName());
        
        // FILIAL
        entity.setNomeFilial(dto.getBranchName());
        
        // CENTRO DE CUSTO
        entity.setNomeCentroCusto(dto.getCostCenterName());
        entity.setValorCentroCusto(parseBigDecimal(dto.getCostCenterValue()));
        
        // CONTA CONTÁBIL
        entity.setClassificacaoContabil(traduzirClassificacaoContabil(dto.getAccountingClassification()));
        entity.setDescricaoContabil(dto.getAccountingDescription());
        entity.setValorContabil(parseBigDecimal(dto.getAccountingValue()));
        
        // ÁREA DE LANÇAMENTO
        entity.setAreaLancamento(dto.getLaunchAreaName());
        
        // OBSERVAÇÕES
        entity.setObservacoes(dto.getComments());
        entity.setDescricaoDespesa(dto.getExpenseDescription());
        
        // USUÁRIO
        entity.setNomeUsuario(dto.getUserName());
        
        // RECONCILIAÇÃO
        entity.setReconciliado(dto.getReconciled());
        
        // GERAR METADATA (JSON completo do DTO)
        final String metadata = MapperUtil.toJson(dto.getAllProperties());
        entity.setMetadata(metadata != null ? metadata : "{}");
        
        // DATA DE EXTRAÇÃO (sempre now)
        entity.setDataExtracao(LocalDateTime.now());
        
        return entity;
    }
    
    // === MÉTODOS AUXILIARES DE PARSING ===
    
    private Long parseLong(String valor) {
        if (valor == null) return null;
        valor = valor.trim();
        if (valor.isEmpty()) return null;
        try {
            return Long.valueOf(valor);
        } catch (final NumberFormatException e) {
            logger.warn("Erro ao parsear Long: {}", valor);
            return null;
        }
    }
    
    private Integer parseInteger(String valor) {
        if (valor == null) return null;
        valor = valor.trim();
        if (valor.isEmpty()) return null;
        try {
            return Integer.valueOf(valor);
        } catch (final NumberFormatException e) {
            logger.warn("Erro ao parsear Integer: {}", valor);
            return null;
        }
    }
    
    /**
     * Converte String para BigDecimal forçando Locale.US.
     * ISSO É CRÍTICO para garantir que o ponto (.) seja lido como decimal
     * e não como separador de milhar (evitando que 179.0 vire 1790.00).
     */
    private BigDecimal parseBigDecimal(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return BigDecimal.ZERO; // Retorna Zero para evitar NULL em somas no banco
        }
        
        try {
            // Limpeza preventiva de símbolos de moeda se houver
            String limpo = valor.trim().replace("R$", "").trim();
            
            // CRÍTICO: Configura formatador US (Padrão 123.45)
            // Isso força o Java a entender o ponto como separador decimal
            java.text.DecimalFormat nf = (java.text.DecimalFormat) java.text.NumberFormat.getInstance(java.util.Locale.US);
            nf.setParseBigDecimal(true);
            
            return (BigDecimal) nf.parse(limpo);
            
        } catch (java.text.ParseException e) {
            logger.warn("Falha ao converter valor monetário '{}' usando Locale.US. Tentando fallback simples.", valor);
            try {
                // Fallback para o construtor padrão se o formato US falhar
                return new BigDecimal(valor.trim());
            } catch (Exception ex) {
                logger.error("Erro irrecuperável na conversão de valor: {}", valor);
                return BigDecimal.ZERO;
            }
        }
    }
    
    private LocalDate parseLocalDate(final String dateStr) {
        return FormatadorData.parseLocalDate(dateStr);
    }
    
    private OffsetDateTime parseOffsetDateTime(final String dateTimeStr) {
        return FormatadorData.parseOffsetDateTime(dateTimeStr);
    }
    
    private String limparETraduzirTipoLancamento(final String tipo) {
        if (tipo == null || tipo.trim().isEmpty()) {
            return null;
        }
        final String[] partes = tipo.split("::");
        final String tipoLimpo = partes.length > 0 ? partes[partes.length - 1].trim() : tipo.trim();
        return switch (tipoLimpo) {
            case "Manual" -> "Manual";
            case "Advance" -> "Adiantamento";
            case "CiotBilling" -> "Pagamento CIOT";
            case "DriverBilling" -> "Pagamento Motorista";
            case "StorageInvoice" -> "Nota Fiscal Armazenagem";
            default -> tipoLimpo;
        };
    }

    private String traduzirClassificacaoContabil(final String slug) {
        if (slug == null || slug.trim().isEmpty()) {
            return null;
        }
        return switch (slug.trim()) {
            case "variable_costs" -> "4. Custos Variáveis";
            case "static_expenses" -> "3. Custos Fixos";
            case "revenue" -> "1. Receitas";
            case "deductions" -> "2. Deduções";
            case "financial_result" -> "5. Resultado Financeiro";
            case "taxes" -> "6. Impostos";
            default -> slug;
        };
    }
}
