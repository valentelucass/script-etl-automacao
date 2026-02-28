/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/util/validacao/ValidadorDTO.java
Classe  : ValidadorDTO (class)
Pacote  : br.com.extrator.util.validacao
Modulo  : Utilitario compartilhado
Papel   : Implementa responsabilidade de validador dto.

Conecta com:
- FormatadorData (util.formatacao)

Fluxo geral:
1) Centraliza funcoes auxiliares reutilizaveis.
2) Evita repeticao de logica transversal.
3) Apoia configuracao, formatacao e infraestrutura.

Estrutura interna:
Metodos principais:
- ValidadorDTO(): realiza operacao relacionada a "validador dto".
- validarCampoObrigatorio(...3 args): aplica regras de validacao e consistencia.
- validarId(...3 args): aplica regras de validacao e consistencia.
- validarIdString(...3 args): aplica regras de validacao e consistencia.
- validarRange(...5 args): aplica regras de validacao e consistencia.
- validarNaoNegativo(...3 args): aplica regras de validacao e consistencia.
- validarTamanhoMaximo(...4 args): aplica regras de validacao e consistencia.
- validarCnpj(...3 args): aplica regras de validacao e consistencia.
- validarCpf(...3 args): aplica regras de validacao e consistencia.
- validarDataISO(...3 args): aplica regras de validacao e consistencia.
- criarValidacao(...1 args): instancia ou monta estrutura de dados.
Atributos-chave:
- logger: logger da classe para diagnostico.
[DOC-FILE-END]============================================================== */

package br.com.extrator.util.validacao;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.util.formatacao.FormatadorData;

/**
 * Classe utilitária para validação de DTOs antes do processamento.
 * Implementa validações básicas para garantir integridade dos dados.
 * 
 * @author Sistema de Extração ESL Cloud
 * @version 1.0
 */
public final class ValidadorDTO {

    private static final Logger logger = LoggerFactory.getLogger(ValidadorDTO.class);

    private ValidadorDTO() {
        // Impede instanciação
    }

    /**
     * Resultado de uma validação contendo erros encontrados.
     */
    public static final class ResultadoValidacao {
        private final List<String> erros = new ArrayList<>();
        private final String entidade;

        public ResultadoValidacao(final String entidade) {
            this.entidade = entidade;
        }

        public void adicionarErro(final String campo, final String mensagem) {
            erros.add(String.format("[%s.%s] %s", entidade, campo, mensagem));
        }

        public boolean isValido() {
            return erros.isEmpty();
        }

        public List<String> getErros() {
            return new ArrayList<>(erros);
        }

        public void logErros() {
            if (!erros.isEmpty()) {
                logger.warn("⚠️ Validação de {} encontrou {} erro(s):", entidade, erros.size());
                erros.forEach(e -> logger.warn("  - {}", e));
            }
        }
    }

    // ========== VALIDAÇÕES GENÉRICAS ==========

    /**
     * Valida se um campo obrigatório não é nulo ou vazio.
     */
    public static boolean validarCampoObrigatorio(final ResultadoValidacao resultado, 
                                                   final String nomeCampo, 
                                                   final String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            resultado.adicionarErro(nomeCampo, "Campo obrigatório não pode ser nulo ou vazio");
            return false;
        }
        return true;
    }

    /**
     * Valida se um campo obrigatório não é nulo.
     */
    public static boolean validarCampoObrigatorio(final ResultadoValidacao resultado,
                                                   final String nomeCampo,
                                                   final Object valor) {
        if (valor == null) {
            resultado.adicionarErro(nomeCampo, "Campo obrigatório não pode ser nulo");
            return false;
        }
        return true;
    }

    /**
     * Valida se um ID numérico é válido (não nulo e maior que zero).
     */
    public static boolean validarId(final ResultadoValidacao resultado,
                                     final String nomeCampo,
                                     final Long valor) {
        if (valor == null) {
            resultado.adicionarErro(nomeCampo, "ID não pode ser nulo");
            return false;
        }
        if (valor <= 0) {
            resultado.adicionarErro(nomeCampo, "ID deve ser maior que zero");
            return false;
        }
        return true;
    }

    /**
     * Valida se um ID String é válido (não nulo/vazio e numérico positivo).
     */
    public static boolean validarIdString(final ResultadoValidacao resultado,
                                           final String nomeCampo,
                                           final String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            resultado.adicionarErro(nomeCampo, "ID não pode ser nulo ou vazio");
            return false;
        }
        try {
            final long id = Long.parseLong(valor.trim());
            if (id <= 0) {
                resultado.adicionarErro(nomeCampo, "ID deve ser maior que zero");
                return false;
            }
        } catch (final NumberFormatException e) {
            resultado.adicionarErro(nomeCampo, "ID deve ser um valor numérico válido");
            return false;
        }
        return true;
    }

    /**
     * Valida se um valor numérico está dentro de um range.
     */
    public static boolean validarRange(final ResultadoValidacao resultado,
                                        final String nomeCampo,
                                        final Number valor,
                                        final double min,
                                        final double max) {
        if (valor == null) {
            resultado.adicionarErro(nomeCampo, "Valor não pode ser nulo");
            return false;
        }
        final double v = valor.doubleValue();
        if (v < min || v > max) {
            resultado.adicionarErro(nomeCampo, 
                String.format("Valor %.2f fora do range permitido [%.2f, %.2f]", v, min, max));
            return false;
        }
        return true;
    }

    /**
     * Valida se um valor não é negativo.
     */
    public static boolean validarNaoNegativo(final ResultadoValidacao resultado,
                                              final String nomeCampo,
                                              final Number valor) {
        if (valor != null && valor.doubleValue() < 0) {
            resultado.adicionarErro(nomeCampo, "Valor não pode ser negativo");
            return false;
        }
        return true;
    }

    /**
     * Valida tamanho máximo de uma String.
     */
    public static boolean validarTamanhoMaximo(final ResultadoValidacao resultado,
                                                final String nomeCampo,
                                                final String valor,
                                                final int tamanhoMax) {
        if (valor != null && valor.length() > tamanhoMax) {
            resultado.adicionarErro(nomeCampo, 
                String.format("Tamanho %d excede o máximo permitido de %d caracteres", 
                    valor.length(), tamanhoMax));
            return false;
        }
        return true;
    }

    /**
     * Valida formato de CNPJ (apenas dígitos, 14 caracteres).
     */
    public static boolean validarCnpj(final ResultadoValidacao resultado,
                                       final String nomeCampo,
                                       final String valor) {
        if (valor == null || valor.isEmpty()) {
            return true; // CNPJ opcional
        }
        
        final String cnpjLimpo = valor.replaceAll("[^0-9]", "");
        if (cnpjLimpo.length() != 14) {
            resultado.adicionarErro(nomeCampo, 
                String.format("CNPJ deve ter 14 dígitos (encontrado: %d)", cnpjLimpo.length()));
            return false;
        }
        return true;
    }

    /**
     * Valida formato de CPF (apenas dígitos, 11 caracteres).
     */
    public static boolean validarCpf(final ResultadoValidacao resultado,
                                      final String nomeCampo,
                                      final String valor) {
        if (valor == null || valor.isEmpty()) {
            return true; // CPF opcional
        }
        
        final String cpfLimpo = valor.replaceAll("[^0-9]", "");
        if (cpfLimpo.length() != 11) {
            resultado.adicionarErro(nomeCampo, 
                String.format("CPF deve ter 11 dígitos (encontrado: %d)", cpfLimpo.length()));
            return false;
        }
        return true;
    }

    /**
     * Valida se uma data String está no formato ISO (yyyy-MM-dd).
     */
    public static boolean validarDataISO(final ResultadoValidacao resultado,
                                          final String nomeCampo,
                                          final String valor) {
        if (valor == null || valor.isEmpty()) {
            return true; // Data opcional
        }
        
        if (!valor.matches("\\d{4}-\\d{2}-\\d{2}")) {
            resultado.adicionarErro(nomeCampo, "Data deve estar no formato yyyy-MM-dd");
            return false;
        }
        
        final java.time.LocalDate data = FormatadorData.parseLocalDate(valor);
        if (data == null) {
            resultado.adicionarErro(nomeCampo, "Data inválida: " + valor);
            return false;
        }
        return true;
    }

    /**
     * Cria um novo resultado de validação para uma entidade.
     */
    public static ResultadoValidacao criarValidacao(final String nomeEntidade) {
        return new ResultadoValidacao(nomeEntidade);
    }
}

