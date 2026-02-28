/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/api/ResultadoExtracao.java
Classe  : ResultadoExtracao (class)
Pacote  : br.com.extrator.api
Modulo  : Cliente de integracao API
Papel   : Implementa responsabilidade de resultado extracao.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Monta requisicoes para endpoints externos.
2) Trata autenticacao, timeout e parse de resposta.
3) Entrega dados normalizados para os extractors.

Estrutura interna:
Metodos principais:
- ResultadoExtracao(...3 args): realiza operacao relacionada a "resultado extracao".
- ResultadoExtracao(...4 args): realiza operacao relacionada a "resultado extracao".
- getDados(): expone valor atual do estado interno.
- isCompleto(): retorna estado booleano de controle.
- getMotivoInterrupcao(): expone valor atual do estado interno.
- getPaginasProcessadas(): expone valor atual do estado interno.
- getRegistrosExtraidos(): expone valor atual do estado interno.
- completo(...3 args): realiza operacao relacionada a "completo".
- incompleto(...4 args): realiza operacao relacionada a "incompleto".
- toString(): realiza operacao relacionada a "to string".
Atributos-chave:
- dados: campo de estado para "dados".
- completo: campo de estado para "completo".
- motivoInterrupcao: campo de estado para "motivo interrupcao".
- paginasProcessadas: campo de estado para "paginas processadas".
- registrosExtraidos: campo de estado para "registros extraidos".
[DOC-FILE-END]============================================================== */

package br.com.extrator.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Classe que encapsula o resultado de uma extração de dados,
 * incluindo informações sobre se a extração foi completa ou interrompida.
 */
public class ResultadoExtracao<T> {
    
    private final List<T> dados;
    private final boolean completo;
    private final String motivoInterrupcao;
    private final int paginasProcessadas;
    private final int registrosExtraidos;
    
    public enum MotivoInterrupcao {
        LIMITE_PAGINAS("LIMITE_PAGINAS"),
        LIMITE_REGISTROS("LIMITE_REGISTROS"),
        LOOP_DETECTADO("LOOP_DETECTADO"),
        ERRO_API("ERRO_API"),
        CIRCUIT_BREAKER("CIRCUIT_BREAKER"),
        PAGINA_VAZIA("PAGINA_VAZIA");
        
        private final String codigo;
        
        MotivoInterrupcao(String codigo) {
            this.codigo = codigo;
        }
        
        public String getCodigo() {
            return codigo;
        }
    }
    
    // Construtor para extração completa
    public ResultadoExtracao(List<T> dados, int paginasProcessadas, int registrosExtraidos) {
        this.dados = dados != null ? dados : new ArrayList<>();
        this.completo = true;
        this.motivoInterrupcao = null;
        this.paginasProcessadas = paginasProcessadas;
        this.registrosExtraidos = registrosExtraidos;
    }
    
    // Construtor para extração incompleta
    public ResultadoExtracao(List<T> dados, MotivoInterrupcao motivo, int paginasProcessadas, int registrosExtraidos) {
        this.dados = dados != null ? dados : new ArrayList<>();
        this.completo = false;
        this.motivoInterrupcao = motivo.getCodigo();
        this.paginasProcessadas = paginasProcessadas;
        this.registrosExtraidos = registrosExtraidos;
    }
    
    // Construtor para extração incompleta com motivo customizado
    public ResultadoExtracao(List<T> dados, String motivoCustomizado, int paginasProcessadas, int registrosExtraidos) {
        this.dados = dados != null ? dados : new ArrayList<>();
        this.completo = false;
        this.motivoInterrupcao = motivoCustomizado;
        this.paginasProcessadas = paginasProcessadas;
        this.registrosExtraidos = registrosExtraidos;
    }
    
    // Getters
    public List<T> getDados() {
        return dados;
    }
    
    public boolean isCompleto() {
        return completo;
    }
    
    public String getMotivoInterrupcao() {
        return motivoInterrupcao;
    }
    
    public int getPaginasProcessadas() {
        return paginasProcessadas;
    }
    
    public int getRegistrosExtraidos() {
        return registrosExtraidos;
    }
    
    /**
     * Cria um resultado de extração completa
     */
    public static <T> ResultadoExtracao<T> completo(List<T> dados, int paginasProcessadas, int registrosExtraidos) {
        return new ResultadoExtracao<>(dados, paginasProcessadas, registrosExtraidos);
    }
    
    /**
     * Cria um resultado de extração incompleta
     */
    public static <T> ResultadoExtracao<T> incompleto(List<T> dados, MotivoInterrupcao motivo, int paginasProcessadas, int registrosExtraidos) {
        return new ResultadoExtracao<>(dados, motivo, paginasProcessadas, registrosExtraidos);
    }
    
    /**
     * Cria um resultado de extração incompleta com motivo customizado
     */
    public static <T> ResultadoExtracao<T> incompleto(List<T> dados, String motivoCustomizado, int paginasProcessadas, int registrosExtraidos) {
        return new ResultadoExtracao<>(dados, motivoCustomizado, paginasProcessadas, registrosExtraidos);
    }
    
    @Override
    public String toString() {
        return "ResultadoExtracao{" +
                "registrosExtraidos=" + registrosExtraidos +
                ", paginasProcessadas=" + paginasProcessadas +
                ", completo=" + completo +
                (motivoInterrupcao != null ? ", motivoInterrupcao='" + motivoInterrupcao + '\'' : "") +
                '}';
    }
}