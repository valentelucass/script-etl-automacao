/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/db/entity/PageAuditEntity.java
Classe  : PageAuditEntity (class)
Pacote  : br.com.extrator.db.entity
Modulo  : Entidade de persistencia
Papel   : Implementa responsabilidade de page audit entity.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Define estrutura de dados persistida no banco.
2) Representa campos de tabela/view no dominio Java.
3) Suporta transporte de dados entre camadas.

Estrutura interna:
Metodos principais:
- getExecutionUuid(): expone valor atual do estado interno.
- setExecutionUuid(...1 args): ajusta valor em estado interno.
- getRunUuid(): expone valor atual do estado interno.
- setRunUuid(...1 args): ajusta valor em estado interno.
- getTemplateId(): expone valor atual do estado interno.
- setTemplateId(...1 args): ajusta valor em estado interno.
- getPage(): expone valor atual do estado interno.
- setPage(...1 args): ajusta valor em estado interno.
- getPer(): expone valor atual do estado interno.
- setPer(...1 args): ajusta valor em estado interno.
- getJanelaInicio(): expone valor atual do estado interno.
- setJanelaInicio(...1 args): ajusta valor em estado interno.
- getJanelaFim(): expone valor atual do estado interno.
- setJanelaFim(...1 args): ajusta valor em estado interno.
Atributos-chave:
- executionUuid: campo de estado para "execution uuid".
- runUuid: campo de estado para "run uuid".
- templateId: campo de estado para "template id".
- page: campo de estado para "page".
- per: campo de estado para "per".
- janelaInicio: campo de estado para "janela inicio".
- janelaFim: campo de estado para "janela fim".
- reqHash: campo de estado para "req hash".
- respHash: campo de estado para "resp hash".
- totalItens: campo de estado para "total itens".
- idKey: campo de estado para "id key".
- idMinNum: campo de estado para "id min num".
- idMaxNum: campo de estado para "id max num".
- idMinStr: campo de estado para "id min str".
[DOC-FILE-END]============================================================== */

package br.com.extrator.db.entity;

import java.time.LocalDate;

public class PageAuditEntity {
    private String executionUuid;
    private String runUuid;
    private int templateId;
    private int page;
    private int per;
    private LocalDate janelaInicio;
    private LocalDate janelaFim;
    private String reqHash;
    private String respHash;
    private int totalItens;
    private String idKey;
    private Long idMinNum;
    private Long idMaxNum;
    private String idMinStr;
    private String idMaxStr;
    private int statusCode;
    private int duracaoMs;
    private java.time.LocalDateTime timestamp;

    public String getExecutionUuid() { return executionUuid; }
    public void setExecutionUuid(final String executionUuid) { this.executionUuid = executionUuid; }
    public String getRunUuid() { return runUuid; }
    public void setRunUuid(final String runUuid) { this.runUuid = runUuid; }
    public int getTemplateId() { return templateId; }
    public void setTemplateId(final int templateId) { this.templateId = templateId; }
    public int getPage() { return page; }
    public void setPage(final int page) { this.page = page; }
    public int getPer() { return per; }
    public void setPer(final int per) { this.per = per; }
    public LocalDate getJanelaInicio() { return janelaInicio; }
    public void setJanelaInicio(final LocalDate janelaInicio) { this.janelaInicio = janelaInicio; }
    public LocalDate getJanelaFim() { return janelaFim; }
    public void setJanelaFim(final LocalDate janelaFim) { this.janelaFim = janelaFim; }
    public String getReqHash() { return reqHash; }
    public void setReqHash(final String reqHash) { this.reqHash = reqHash; }
    public String getRespHash() { return respHash; }
    public void setRespHash(final String respHash) { this.respHash = respHash; }
    public int getTotalItens() { return totalItens; }
    public void setTotalItens(final int totalItens) { this.totalItens = totalItens; }
    public String getIdKey() { return idKey; }
    public void setIdKey(final String idKey) { this.idKey = idKey; }
    public Long getIdMinNum() { return idMinNum; }
    public void setIdMinNum(final Long idMinNum) { this.idMinNum = idMinNum; }
    public Long getIdMaxNum() { return idMaxNum; }
    public void setIdMaxNum(final Long idMaxNum) { this.idMaxNum = idMaxNum; }
    public String getIdMinStr() { return idMinStr; }
    public void setIdMinStr(final String idMinStr) { this.idMinStr = idMinStr; }
    public String getIdMaxStr() { return idMaxStr; }
    public void setIdMaxStr(final String idMaxStr) { this.idMaxStr = idMaxStr; }
    public int getStatusCode() { return statusCode; }
    public void setStatusCode(final int statusCode) { this.statusCode = statusCode; }
    public int getDuracaoMs() { return duracaoMs; }
    public void setDuracaoMs(final int duracaoMs) { this.duracaoMs = duracaoMs; }
    public java.time.LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(final java.time.LocalDateTime timestamp) { this.timestamp = timestamp; }
}
