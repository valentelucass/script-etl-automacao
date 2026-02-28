/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/auditoria/modelos/ResultadoAuditoria.java
Classe  : ResultadoAuditoria (class)
Pacote  : br.com.extrator.auditoria.modelos
Modulo  : Modulo de auditoria
Papel   : Implementa responsabilidade de resultado auditoria.

Conecta com:
- StatusAuditoria (auditoria.enums)
- StatusValidacao (auditoria.enums)

Fluxo geral:
1) Modela resultados e estado de auditoria.
2) Apoia consolidacao de evidencias operacionais.
3) Suporta emissao de relatorios de conformidade.

Estrutura interna:
Metodos principais:
- ResultadoAuditoria(): realiza operacao relacionada a "resultado auditoria".
- ResultadoAuditoria(...3 args): realiza operacao relacionada a "resultado auditoria".
- getDataInicio(): expone valor atual do estado interno.
- setDataInicio(...1 args): ajusta valor em estado interno.
- getDataFim(): expone valor atual do estado interno.
- setDataFim(...1 args): ajusta valor em estado interno.
- getResultadosValidacao(): expone valor atual do estado interno.
- setResultadosValidacao(...1 args): ajusta valor em estado interno.
- getStatusGeral(): expone valor atual do estado interno.
- setStatusGeral(...1 args): ajusta valor em estado interno.
- getErro(): expone valor atual do estado interno.
- setErro(...1 args): ajusta valor em estado interno.
- setDataExecucao(...1 args): ajusta valor em estado interno.
- adicionarValidacao(...2 args): realiza operacao relacionada a "adicionar validacao".
Atributos-chave:
- dataInicio: campo de estado para "data inicio".
- dataFim: campo de estado para "data fim".
- resultadosValidacao: campo de estado para "resultados validacao".
- resultadosValidacaoMap: campo de estado para "resultados validacao map".
- statusGeral: campo de estado para "status geral".
- erro: campo de estado para "erro".
[DOC-FILE-END]============================================================== */

package br.com.extrator.auditoria.modelos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.extrator.auditoria.enums.StatusAuditoria;
import br.com.extrator.auditoria.enums.StatusValidacao;

public class ResultadoAuditoria {

    private Instant dataInicio;
    private Instant dataFim;
    private List<String> resultadosValidacao; // Mantido para compatibilidade
    private Map<String, ResultadoValidacaoEntidade> resultadosValidacaoMap; // Novo: objetos completos
    private StatusAuditoria statusGeral;
    private String erro;

    public ResultadoAuditoria() {
        this.dataInicio = Instant.now();
        this.resultadosValidacao = new ArrayList<>();
        this.resultadosValidacaoMap = new LinkedHashMap<>();
    }

    public ResultadoAuditoria(final Instant dataInicio, final Instant dataFim, final List<String> resultadosValidacao) {
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
        this.resultadosValidacao = resultadosValidacao != null ? resultadosValidacao : new ArrayList<>();
        this.resultadosValidacaoMap = new LinkedHashMap<>();
    }

    // Getters e Setters
    public Instant getDataInicio() {
        return dataInicio;
    }

    public void setDataInicio(final Instant dataInicio) {
        this.dataInicio = dataInicio;
    }

    public Instant getDataFim() {
        return dataFim;
    }

    public void setDataFim(final Instant dataFim) {
        this.dataFim = dataFim;
    }

    public List<String> getResultadosValidacao() {
        return resultadosValidacao;
    }

    public void setResultadosValidacao(final List<String> resultadosValidacao) {
        this.resultadosValidacao = resultadosValidacao;
    }

    public StatusAuditoria getStatusGeral() {
        return statusGeral;
    }

    public void setStatusGeral(final StatusAuditoria statusGeral) {
        this.statusGeral = statusGeral;
    }

    public String getErro() {
        return erro;
    }

    public void setErro(final String erro) {
        this.erro = erro;
    }

    public void setDataExecucao(final Instant dataExecucao) {
        this.dataInicio = dataExecucao;
    }

    public void adicionarValidacao(final String entidade, final ResultadoValidacaoEntidade resultado) {
        if (this.resultadosValidacao == null) {
            this.resultadosValidacao = new ArrayList<>();
        }
        if (this.resultadosValidacaoMap == null) {
            this.resultadosValidacaoMap = new LinkedHashMap<>();
        }
        // Mantém string para compatibilidade
        this.resultadosValidacao.add(entidade + ": " + resultado.toString());
        // Mantém objeto para relatório detalhado
        this.resultadosValidacaoMap.put(entidade, resultado);
    }
    
    /**
     * Retorna o mapa com os resultados de validação (objetos completos).
     * 
     * @return Map com nome da entidade -> ResultadoValidacaoEntidade
     */
    public Map<String, ResultadoValidacaoEntidade> getResultadosValidacaoMap() {
        return resultadosValidacaoMap != null ? resultadosValidacaoMap : new LinkedHashMap<>();
    }

    public void determinarStatusGeral() {
        if (erro != null && !erro.isEmpty()) {
            this.statusGeral = StatusAuditoria.ERRO;
        } else if (resultadosValidacaoMap != null && !resultadosValidacaoMap.isEmpty()) {
            // Usar o mapa para determinar o status geral
            final boolean temErro = resultadosValidacaoMap.values().stream()
                .anyMatch(r -> r.getStatus() == StatusValidacao.ERRO);
            final boolean temAlerta = resultadosValidacaoMap.values().stream()
                .anyMatch(r -> r.getStatus() == StatusValidacao.ALERTA);
            
            if (temErro) {
                this.statusGeral = StatusAuditoria.ERRO;
            } else if (temAlerta) {
                this.statusGeral = StatusAuditoria.CONCLUIDA_COM_ALERTAS;
            } else {
                this.statusGeral = StatusAuditoria.CONCLUIDA;
            }
        } else {
            // Fallback para lista de strings (compatibilidade)
            if (resultadosValidacao.stream().anyMatch(r -> r.toLowerCase().contains("erro"))) {
                this.statusGeral = StatusAuditoria.CONCLUIDA_COM_ALERTAS;
            } else {
                this.statusGeral = StatusAuditoria.CONCLUIDA;
            }
        }
    }

    // Método utilitário opcional (pode estar sendo chamado em AuditoriaRelatorio)
    public void adicionarResultado(final String resultado) {
        if (this.resultadosValidacao == null) {
            this.resultadosValidacao = new ArrayList<>();
        }
        this.resultadosValidacao.add(resultado);
    }

    public boolean isSucesso() {
        // Exemplo simples de lógica
        return resultadosValidacao.stream().noneMatch(r -> r.toLowerCase().contains("erro"));
    }

    @Override
    public String toString() {
        return "ResultadoAuditoria{" +
                "dataInicio=" + dataInicio +
                ", dataFim=" + dataFim +
                ", resultadosValidacao=" + resultadosValidacao +
                ", statusGeral=" + statusGeral +
                ", erro='" + erro + '\'' +
                '}';
    }
}