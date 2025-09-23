package br.com.extrator.modelo;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Classe genérica que representa qualquer tipo de entidade recebida da API.
 * Permite armazenar dinamicamente qualquer campo sem necessidade de definir atributos específicos.
 */
public class EntidadeDinamica {
    
    // Mapa que armazena todos os campos da entidade (nome do campo -> valor)
    private Map<String, Object> campos = new HashMap<>();
    
    // Nome do tipo de entidade (ex: "fatura", "cliente", "produto")
    private String tipoEntidade;
    
    /**
     * Construtor padrão
     */
    public EntidadeDinamica() {
    }
    
    /**
     * Construtor que define o tipo da entidade
     * @param tipoEntidade Nome do tipo de entidade
     */
    public EntidadeDinamica(String tipoEntidade) {
        this.tipoEntidade = tipoEntidade;
    }
    
    /**
     * Obtém o tipo da entidade
     * @return Nome do tipo de entidade
     */
    public String getTipoEntidade() {
        return tipoEntidade;
    }
    
    /**
     * Define o tipo da entidade
     * @param tipoEntidade Nome do tipo de entidade
     */
    public void setTipoEntidade(String tipoEntidade) {
        this.tipoEntidade = tipoEntidade;
    }
    
    /**
     * Método que permite ao Jackson deserializar qualquer campo JSON para o mapa
     * @param nome Nome do campo
     * @param valor Valor do campo
     */
    @JsonAnySetter
    public void adicionarCampo(String nome, Object valor) {
        campos.put(nome, valor);
    }
    
    /**
     * Método que permite ao Jackson serializar todos os campos do mapa para JSON
     * @return Mapa com todos os campos da entidade
     */
    @JsonAnyGetter
    public Map<String, Object> getCampos() {
        return campos;
    }
    
    /**
     * Obtém o valor de um campo específico
     * @param nomeCampo Nome do campo
     * @return Valor do campo ou null se não existir
     */
    public Object getCampo(String nomeCampo) {
        return campos.get(nomeCampo);
    }
    
    /**
     * Obtém o valor de um campo como String
     * @param nomeCampo Nome do campo
     * @return Valor do campo como String ou null se não existir
     */
    public String getCampoComoString(String nomeCampo) {
        Object valor = campos.get(nomeCampo);
        return valor != null ? valor.toString() : null;
    }
    
    /**
     * Obtém o valor de um campo como Integer
     * @param nomeCampo Nome do campo
     * @return Valor do campo como Integer ou null se não existir ou não for conversível
     */
    public Integer getCampoComoInteger(String nomeCampo) {
        Object valor = campos.get(nomeCampo);
        if (valor == null) return null;
        
        if (valor instanceof Integer) {
            return (Integer) valor;
        } else if (valor instanceof Number) {
            return ((Number) valor).intValue();
        } else {
            try {
                return Integer.parseInt(valor.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
    
    /**
     * Obtém o valor de um campo como Double
     * @param nomeCampo Nome do campo
     * @return Valor do campo como Double ou null se não existir ou não for conversível
     */
    public Double getCampoComoDouble(String nomeCampo) {
        Object valor = campos.get(nomeCampo);
        if (valor == null) return null;
        
        if (valor instanceof Double) {
            return (Double) valor;
        } else if (valor instanceof Number) {
            return ((Number) valor).doubleValue();
        } else {
            try {
                return Double.parseDouble(valor.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
    
    /**
     * Obtém o valor de um campo como Boolean
     * @param nomeCampo Nome do campo
     * @return Valor do campo como Boolean ou null se não existir
     */
    public Boolean getCampoComoBoolean(String nomeCampo) {
        Object valor = campos.get(nomeCampo);
        if (valor == null) return null;
        
        if (valor instanceof Boolean) {
            return (Boolean) valor;
        } else {
            String valorStr = valor.toString().toLowerCase();
            return "true".equals(valorStr) || "1".equals(valorStr) || "sim".equals(valorStr) || "s".equals(valorStr);
        }
    }
    
    /**
     * Obtém todos os nomes dos campos disponíveis
     * @return Conjunto com os nomes dos campos
     */
    @JsonIgnore
    public Set<String> getNomesCampos() {
        return campos.keySet();
    }
    
    /**
     * Verifica se um campo específico existe
     * @param nomeCampo Nome do campo
     * @return true se o campo existir, false caso contrário
     */
    public boolean temCampo(String nomeCampo) {
        return campos.containsKey(nomeCampo);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("EntidadeDinamica[tipo=").append(tipoEntidade).append(", campos={");
        
        boolean primeiro = true;
        for (Map.Entry<String, Object> entry : campos.entrySet()) {
            if (!primeiro) {
                sb.append(", ");
            }
            primeiro = false;
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        
        sb.append("}]");
        return sb.toString();
    }
}