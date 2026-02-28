/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/entity/LogExtracaoEntity.java
Classe  : LogExtracaoEntity (class)
Pacote  : br.com.extrator.db.entity
Modulo  : Entidade de persistencia
Papel   : Implementa responsabilidade de log extracao entity.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Define estrutura de dados persistida no banco.
2) Representa campos de tabela/view no dominio Java.
3) Suporta transporte de dados entre camadas.

Estrutura interna:
Metodos principais:
- LogExtracaoEntity(): realiza operacao relacionada a "log extracao entity".
- LogExtracaoEntity(...7 args): realiza operacao relacionada a "log extracao entity".
- getId(): expone valor atual do estado interno.
- setId(...1 args): ajusta valor em estado interno.
- getEntidade(): expone valor atual do estado interno.
- setEntidade(...1 args): ajusta valor em estado interno.
- getTimestampInicio(): expone valor atual do estado interno.
- setTimestampInicio(...1 args): ajusta valor em estado interno.
- getTimestampFim(): expone valor atual do estado interno.
- setTimestampFim(...1 args): ajusta valor em estado interno.
- getStatusFinal(): expone valor atual do estado interno.
- setStatusFinal(...1 args): ajusta valor em estado interno.
- getRegistrosExtraidos(): expone valor atual do estado interno.
- setRegistrosExtraidos(...1 args): ajusta valor em estado interno.
Atributos-chave:
- id: campo de estado para "id".
- entidade: campo de estado para "entidade".
- timestampInicio: campo de estado para "timestamp inicio".
- timestampFim: campo de estado para "timestamp fim".
- statusFinal: campo de estado para "status final".
- registrosExtraidos: campo de estado para "registros extraidos".
- paginasProcessadas: campo de estado para "paginas processadas".
- mensagem: campo de estado para "mensagem".
[DOC-FILE-END]============================================================== */

package br.com.extrator.db.entity;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Entidade para controle de status das extrações de dados
 */
public class LogExtracaoEntity {
    
    private Long id;
    private String entidade;
    private LocalDateTime timestampInicio;
    private LocalDateTime timestampFim;
    private StatusExtracao statusFinal;
    private Integer registrosExtraidos;
    private Integer paginasProcessadas;
    private String mensagem;
    
    public enum StatusExtracao {
        COMPLETO("COMPLETO"),
        INCOMPLETO("INCOMPLETO"),
        INCOMPLETO_LIMITE("INCOMPLETO_LIMITE"),
        INCOMPLETO_DADOS("INCOMPLETO_DADOS"),
        INCOMPLETO_DB("INCOMPLETO_DB"),
        ERRO_API("ERRO_API");
        
        private final String valor;
        
        StatusExtracao(final String valor) {
            this.valor = valor;
        }
        
        public String getValor() {
            return valor;
        }
        
        public static StatusExtracao fromString(final String valor) {
            if (valor == null || valor.isBlank()) {
                throw new IllegalArgumentException("Status inválido: valor nulo/vazio");
            }
            final String normalizado = valor.trim().toUpperCase(Locale.ROOT);

            // Compatibilidade com status antigos/alternativos gravados em versões anteriores
            if ("INCOMPLETO_DADOS_INVALIDOS".equals(normalizado)) {
                return INCOMPLETO_DADOS;
            }
            if ("INCOMPLETO_SALVAMENTO".equals(normalizado)) {
                return INCOMPLETO_DB;
            }
            
            for (final StatusExtracao status : StatusExtracao.values()) {
                if (status.valor.equals(normalizado)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Status inválido: " + valor);
        }
    }
    
    // Construtores
    public LogExtracaoEntity() {}
    
    public LogExtracaoEntity(final String entidade, final LocalDateTime timestampInicio, final LocalDateTime timestampFim,
                           final StatusExtracao statusFinal, final Integer registrosExtraidos, final Integer paginasProcessadas,
                           final String mensagem) {
        this.entidade = entidade;
        this.timestampInicio = timestampInicio;
        this.timestampFim = timestampFim;
        this.statusFinal = statusFinal;
        this.registrosExtraidos = registrosExtraidos;
        this.paginasProcessadas = paginasProcessadas;
        this.mensagem = mensagem;
    }
    
    /**
     * Construtor que aceita String para status (converte automaticamente para enum)
     */
    public LogExtracaoEntity(final String entidade, final LocalDateTime timestampInicio, final LocalDateTime timestampFim,
                           final String statusFinal, final Integer registrosExtraidos, final Integer paginasProcessadas,
                           final String mensagem) {
        this.entidade = entidade;
        this.timestampInicio = timestampInicio;
        this.timestampFim = timestampFim;
        this.statusFinal = StatusExtracao.fromString(statusFinal);
        this.registrosExtraidos = registrosExtraidos;
        this.paginasProcessadas = paginasProcessadas;
        this.mensagem = mensagem;
    }
    
    // Getters e Setters
    public Long getId() {
        return id;
    }
    
    public void setId(final Long id) {
        this.id = id;
    }
    
    public String getEntidade() {
        return entidade;
    }
    
    public void setEntidade(final String entidade) {
        this.entidade = entidade;
    }
    
    public LocalDateTime getTimestampInicio() {
        return timestampInicio;
    }
    
    public void setTimestampInicio(final LocalDateTime timestampInicio) {
        this.timestampInicio = timestampInicio;
    }
    
    public LocalDateTime getTimestampFim() {
        return timestampFim;
    }
    
    public void setTimestampFim(final LocalDateTime timestampFim) {
        this.timestampFim = timestampFim;
    }
    
    public StatusExtracao getStatusFinal() {
        return statusFinal;
    }
    
    public void setStatusFinal(final StatusExtracao statusFinal) {
        this.statusFinal = statusFinal;
    }
    
    public Integer getRegistrosExtraidos() {
        return registrosExtraidos;
    }
    
    public void setRegistrosExtraidos(final Integer registrosExtraidos) {
        this.registrosExtraidos = registrosExtraidos;
    }
    
    public Integer getPaginasProcessadas() {
        return paginasProcessadas;
    }
    
    public void setPaginasProcessadas(final Integer paginasProcessadas) {
        this.paginasProcessadas = paginasProcessadas;
    }
    
    public String getMensagem() {
        return mensagem;
    }
    
    public void setMensagem(final String mensagem) {
        this.mensagem = mensagem;
    }
    
    @Override
    public String toString() {
        return "LogExtracaoEntity{" +
                "id=" + id +
                ", entidade='" + entidade + '\'' +
                ", timestampInicio=" + timestampInicio +
                ", timestampFim=" + timestampFim +
                ", statusFinal=" + statusFinal +
                ", registrosExtraidos=" + registrosExtraidos +
                ", paginasProcessadas=" + paginasProcessadas +
                ", mensagem='" + mensagem + '\'' +
                '}';
    }
}

