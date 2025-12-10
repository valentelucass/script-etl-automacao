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
