/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/auditoria/modelos/ResultadoValidacaoEntidade.java
Classe  : ResultadoValidacaoEntidade (class)
Pacote  : br.com.extrator.auditoria.modelos
Modulo  : Modulo de auditoria
Papel   : Implementa responsabilidade de resultado validacao entidade.

Conecta com:
- StatusValidacao (auditoria.enums)

Fluxo geral:
1) Modela resultados e estado de auditoria.
2) Apoia consolidacao de evidencias operacionais.
3) Suporta emissao de relatorios de conformidade.

Estrutura interna:
Metodos principais:
- ResultadoValidacaoEntidade(): realiza operacao relacionada a "resultado validacao entidade".
- erro(...3 args): realiza operacao relacionada a "erro".
- alerta(...3 args): realiza operacao relacionada a "alerta".
- ok(...2 args): realiza operacao relacionada a "ok".
- completo(...3 args): realiza operacao relacionada a "completo".
- incompleto(...3 args): realiza operacao relacionada a "incompleto".
- duplicados(...3 args): realiza operacao relacionada a "duplicados".
- getNomeEntidade(): expone valor atual do estado interno.
- setNomeEntidade(...1 args): ajusta valor em estado interno.
- getDataInicio(): expone valor atual do estado interno.
- setDataInicio(...1 args): ajusta valor em estado interno.
- getDataFim(): expone valor atual do estado interno.
- setDataFim(...1 args): ajusta valor em estado interno.
- getTotalRegistros(): expone valor atual do estado interno.
Atributos-chave:
- nomeEntidade: campo de estado para "nome entidade".
- dataInicio: campo de estado para "data inicio".
- dataFim: campo de estado para "data fim".
- totalRegistros: campo de estado para "total registros".
- registrosUltimas24h: campo de estado para "registros ultimas24h".
- registrosComNulos: campo de estado para "registros com nulos".
- ultimaExtracao: campo de estado para "ultima extracao".
- status: campo de estado para "status".
- erro: campo de estado para "erro".
- observacoes: campo de estado para "observacoes".
- colunaUtilizada: campo de estado para "coluna utilizada".
- queryExecutada: campo de estado para "query executada".
- registrosEsperadosApi: campo de estado para "registros esperados api".
- diferencaRegistros: campo de estado para "diferenca registros".
[DOC-FILE-END]============================================================== */

package br.com.extrator.auditoria.modelos;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import br.com.extrator.auditoria.enums.StatusValidacao;

/**
 * Classe que representa o resultado da validação de uma entidade específica.
 * Contém informações sobre a completude dos dados, estatísticas e observações.
 */
public class ResultadoValidacaoEntidade {
    private String nomeEntidade;
    private Instant dataInicio;
    private Instant dataFim;
    private long totalRegistros;
    private long registrosUltimas24h;
    private long registrosComNulos;
    private Instant ultimaExtracao;
    private StatusValidacao status;
    private String erro;
    private List<String> observacoes;
    
    // Novos campos para debug e causa raiz
    private String colunaUtilizada;
    private String queryExecutada;
    
    // Novos campos para comparação API vs Banco
    private int registrosEsperadosApi;
    private int diferencaRegistros;
    private double percentualCompletude;
    
    public ResultadoValidacaoEntidade() {
        this.observacoes = new ArrayList<>();
        this.status = StatusValidacao.PENDENTE;
    }
    
    // ✅ MÉTODOS ESTÁTICOS PARA CRIAÇÃO PADRONIZADA
    
    /**
     * Cria um resultado de validação com status de ERRO.
     * 
     * @param entidade Nome da entidade
     * @param registros Número de registros encontrados
     * @param mensagem Mensagem de erro
     * @return ResultadoValidacaoEntidade com status ERRO
     */
    public static ResultadoValidacaoEntidade erro(String entidade, long registros, String mensagem) {
        ResultadoValidacaoEntidade resultado = new ResultadoValidacaoEntidade();
        resultado.setNomeEntidade(entidade);
        resultado.setTotalRegistros(registros);
        resultado.setStatus(StatusValidacao.ERRO);
        resultado.setErro(mensagem);
        return resultado;
    }
    
    /**
     * Cria um resultado de validação com status de ALERTA.
     * 
     * @param entidade Nome da entidade
     * @param registros Número de registros encontrados
     * @param mensagem Mensagem de alerta
     * @return ResultadoValidacaoEntidade com status ALERTA
     */
    public static ResultadoValidacaoEntidade alerta(String entidade, long registros, String mensagem) {
        ResultadoValidacaoEntidade resultado = new ResultadoValidacaoEntidade();
        resultado.setNomeEntidade(entidade);
        resultado.setTotalRegistros(registros);
        resultado.setStatus(StatusValidacao.ALERTA);
        resultado.adicionarObservacao(mensagem);
        return resultado;
    }
    
    /**
     * Cria um resultado de validação com status OK.
     * 
     * @param entidade Nome da entidade
     * @param registros Número de registros encontrados
     * @return ResultadoValidacaoEntidade com status OK
     */
    public static ResultadoValidacaoEntidade ok(String entidade, long registros) {
        ResultadoValidacaoEntidade resultado = new ResultadoValidacaoEntidade();
        resultado.setNomeEntidade(entidade);
        resultado.setTotalRegistros(registros);
        resultado.setStatus(StatusValidacao.OK);
        return resultado;
    }
    
    /**
     * Cria um resultado de validação para dados COMPLETOS (API = Banco).
     * 
     * @param entidade Nome da entidade
     * @param esperadosApi Registros esperados da API
     * @param extraidosBanco Registros extraídos do banco
     * @return ResultadoValidacaoEntidade com status OK e dados de comparação
     */
    public static ResultadoValidacaoEntidade completo(String entidade, int esperadosApi, int extraidosBanco) {
        ResultadoValidacaoEntidade resultado = new ResultadoValidacaoEntidade();
        resultado.setNomeEntidade(entidade);
        resultado.setTotalRegistros(extraidosBanco);
        resultado.setRegistrosEsperadosApi(esperadosApi);
        resultado.setDiferencaRegistros(extraidosBanco - esperadosApi);
        resultado.setPercentualCompletude(100.0);
        resultado.setStatus(StatusValidacao.OK);
        resultado.adicionarObservacao(String.format("Dados completos: %d registros (API: %d, Banco: %d)", 
            extraidosBanco, esperadosApi, extraidosBanco));
        return resultado;
    }
    
    /**
     * Cria um resultado de validação para dados INCOMPLETOS (Banco < API).
     * 
     * @param entidade Nome da entidade
     * @param esperadosApi Registros esperados da API
     * @param extraidosBanco Registros extraídos do banco
     * @return ResultadoValidacaoEntidade com status ALERTA e dados de comparação
     */
    public static ResultadoValidacaoEntidade incompleto(String entidade, int esperadosApi, int extraidosBanco) {
        ResultadoValidacaoEntidade resultado = new ResultadoValidacaoEntidade();
        resultado.setNomeEntidade(entidade);
        resultado.setTotalRegistros(extraidosBanco);
        resultado.setRegistrosEsperadosApi(esperadosApi);
        resultado.setDiferencaRegistros(extraidosBanco - esperadosApi);
        resultado.setPercentualCompletude((extraidosBanco * 100.0) / esperadosApi);
        resultado.setStatus(StatusValidacao.ALERTA);
        resultado.adicionarObservacao(String.format("Dados incompletos: %d/%d registros (%.1f%% - faltam %d)", 
            extraidosBanco, esperadosApi, resultado.getPercentualCompletude(), esperadosApi - extraidosBanco));
        return resultado;
    }
    
    /**
     * Cria um resultado de validação para dados DUPLICADOS (Banco > API).
     * 
     * @param entidade Nome da entidade
     * @param esperadosApi Registros esperados da API
     * @param extraidosBanco Registros extraídos do banco
     * @return ResultadoValidacaoEntidade com status ALERTA e dados de comparação
     */
    public static ResultadoValidacaoEntidade duplicados(String entidade, int esperadosApi, int extraidosBanco) {
        ResultadoValidacaoEntidade resultado = new ResultadoValidacaoEntidade();
        resultado.setNomeEntidade(entidade);
        resultado.setTotalRegistros(extraidosBanco);
        resultado.setRegistrosEsperadosApi(esperadosApi);
        resultado.setDiferencaRegistros(extraidosBanco - esperadosApi);
        resultado.setPercentualCompletude((esperadosApi * 100.0) / extraidosBanco);
        resultado.setStatus(StatusValidacao.ALERTA);
        resultado.adicionarObservacao(String.format("Possíveis duplicados: %d registros (esperado: %d, excesso: %d)", 
            extraidosBanco, esperadosApi, extraidosBanco - esperadosApi));
        return resultado;
    }
    
    // Getters e Setters
    public String getNomeEntidade() {
        return nomeEntidade;
    }
    
    public void setNomeEntidade(final String nomeEntidade) {
        this.nomeEntidade = nomeEntidade;
    }
    
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
    
    public long getTotalRegistros() {
        return totalRegistros;
    }
    
    public void setTotalRegistros(final long totalRegistros) {
        this.totalRegistros = totalRegistros;
    }
    
    public long getRegistrosUltimas24h() {
        return registrosUltimas24h;
    }
    
    public void setRegistrosUltimas24h(final long registrosUltimas24h) {
        this.registrosUltimas24h = registrosUltimas24h;
    }
    
    public long getRegistrosComNulos() {
        return registrosComNulos;
    }
    
    public void setRegistrosComNulos(final long registrosComNulos) {
        this.registrosComNulos = registrosComNulos;
    }
    
    public Instant getUltimaExtracao() {
        return ultimaExtracao;
    }
    
    public void setUltimaExtracao(final Instant ultimaExtracao) {
        this.ultimaExtracao = ultimaExtracao;
    }
    
    public StatusValidacao getStatus() {
        return status;
    }
    
    public void setStatus(final StatusValidacao status) {
        this.status = status;
    }
    
    public String getErro() {
        return erro;
    }
    
    public void setErro(final String erro) {
        this.erro = erro;
    }
    
    public List<String> getObservacoes() {
        return observacoes;
    }
    
    public void setObservacoes(final List<String> observacoes) {
        this.observacoes = observacoes != null ? observacoes : new ArrayList<>();
    }
    
    public void adicionarObservacao(final String observacao) {
        if (observacao != null && !observacao.trim().isEmpty()) {
            this.observacoes.add(observacao);
        }
    }
    
    /**
     * Verifica se a validação foi bem-sucedida (status OK).
     * 
     * @return true se o status for OK, false caso contrário
     */
    public boolean isValida() {
        return status == StatusValidacao.OK;
    }
    
    /**
     * Verifica se há alertas na validação.
     * 
     * @return true se o status for ALERTA, false caso contrário
     */
    public boolean temAlerta() {
        return status == StatusValidacao.ALERTA;
    }
    
    /**
     * Verifica se houve erro na validação.
     * 
     * @return true se o status for ERRO, false caso contrário
     */
    public boolean temErro() {
        return status == StatusValidacao.ERRO;
    }
    
    public String getColunaUtilizada() {
        return colunaUtilizada;
    }
    
    public void setColunaUtilizada(final String colunaUtilizada) {
        this.colunaUtilizada = colunaUtilizada;
    }
    
    public String getQueryExecutada() {
        return queryExecutada;
    }
    
    public void setQueryExecutada(final String queryExecutada) {
        this.queryExecutada = queryExecutada;
    }
    
    public int getRegistrosEsperadosApi() {
        return registrosEsperadosApi;
    }
    
    public void setRegistrosEsperadosApi(final int registrosEsperadosApi) {
        this.registrosEsperadosApi = registrosEsperadosApi;
    }
    
    public int getDiferencaRegistros() {
        return diferencaRegistros;
    }
    
    public void setDiferencaRegistros(final int diferencaRegistros) {
        this.diferencaRegistros = diferencaRegistros;
    }
    
    public double getPercentualCompletude() {
        return percentualCompletude;
    }
    
    public void setPercentualCompletude(final double percentualCompletude) {
        this.percentualCompletude = percentualCompletude;
    }
    
    /**
     * Retorna um resumo textual do resultado da validação.
     * 
     * @return String com resumo do resultado
     */
    public String getResumo() {
        final StringBuilder resumo = new StringBuilder();
        resumo.append(String.format("Entidade: %s | Status: %s", nomeEntidade, status));
        
        if (totalRegistros > 0) {
            resumo.append(String.format(" | Registros: %d", totalRegistros));
        }
        
        if (registrosUltimas24h > 0) {
            resumo.append(String.format(" | Últimas 24h: %d", registrosUltimas24h));
        }
        
        if (erro != null) {
            resumo.append(String.format(" | Erro: %s", erro));
        }
        
        if (!observacoes.isEmpty()) {
            resumo.append(String.format(" | Observações: %d", observacoes.size()));
        }
        
        return resumo.toString();
    }
    
    @Override
    public String toString() {
        // Se tem dados de comparação API vs Banco, mostra formato detalhado
        if (registrosEsperadosApi > 0) {
            return String.format("ResultadoValidacaoEntidade{entidade='%s', status=%s, esperado=%d, extraido=%d, diferenca=%d, completude=%.1f%%}", 
                               nomeEntidade, status, registrosEsperadosApi, (int)totalRegistros, diferencaRegistros, percentualCompletude);
        } else {
            // Formato antigo para compatibilidade
            return String.format("ResultadoValidacaoEntidade{entidade='%s', status=%s, registros=%d, erro='%s'}", 
                               nomeEntidade, status, totalRegistros, erro);
        }
    }
}