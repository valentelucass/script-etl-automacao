package br.com.extrator.web;

import br.com.extrator.servicos.MetricasService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.*;

/**
 * Controlador REST para fornecer informações detalhadas de status do sistema de extração
 * Inclui dados para gráficos, métricas avançadas e informações sobre múltiplas tabelas por API
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:3001", "http://localhost:3002", "http://localhost:3003"}) // Permite CORS para o React
public class StatusController {

    @Autowired
    private MetricasService metricas;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        // Força a releitura do arquivo de métricas do dia a cada requisição
        if (metricas != null) {
            metricas.carregarMetricasDoDia();
        }
        
        Map<String, Object> status = new HashMap<>();
        
        // Status geral do sistema
        status.put("statusGeral", "OPERACIONAL");
        status.put("ultimaAtualizacao", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        status.put("versaoSistema", "2.1.0");
        status.put("tempoOnline", "15h 32m");
        
        // Status detalhado das APIs com múltiplas tabelas
        Map<String, Map<String, Object>> statusAPIs = new HashMap<>();
        
        // API REST - Múltiplas tabelas
        Map<String, Object> apiRest = criarStatusApiRest();
        statusAPIs.put("API REST", apiRest);
        
        // API GraphQL - Múltiplas tabelas
        Map<String, Object> apiGraphQL = criarStatusApiGraphQL();
        statusAPIs.put("API GraphQL", apiGraphQL);
        
        // API Data Export - Múltiplas tabelas
        Map<String, Object> apiDataExport = criarStatusApiDataExport();
        statusAPIs.put("API Data Export", apiDataExport);
        
        status.put("statusAPIs", statusAPIs);
        
        // Métricas avançadas do sistema
        status.put("metricas", criarMetricasAvancadas());
        
        // Dados para gráficos
        status.put("dadosGraficos", criarDadosGraficos());
        
        // Consumo de APIs (nova seção)
        status.put("consumoApis", criarConsumoApis());
        
        // Histórico de execuções (últimas 24h)
        status.put("historicoExecucoes", criarHistoricoExecucoes());
        
        // Alertas e notificações
        status.put("alertas", criarAlertas());
        
        return ResponseEntity.ok(status);
    }
    
    private Map<String, Object> criarStatusApiRest() {
        Map<String, Object> apiRest = new HashMap<>();
        apiRest.put("nome", "API REST");
        apiRest.put("status", "OPERACIONAL");
        apiRest.put("ultimaExecucao", LocalDateTime.now().minusMinutes(5).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        apiRest.put("proximaExecucao", LocalDateTime.now().plusMinutes(25).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        // Usar dados reais das métricas se disponível
        if (metricas != null) {
            Map<String, Integer> sucessos = metricas.getSucessosPorApi();
            Map<String, Integer> falhas = metricas.getFalhasPorApi();
            Map<String, Duration> duracoes = metricas.getDuracaoOperacoes();
            
            // Calcular tempo de resposta médio das operações REST
            long tempoResposta = duracoes.entrySet().stream()
                .filter(entry -> entry.getKey().contains("faturas") || entry.getKey().contains("ocorrencias"))
                .mapToLong(entry -> entry.getValue().toMillis())
                .findFirst()
                .orElse(1250L);
            
            apiRest.put("tempoResposta", tempoResposta);
            
            // Calcular taxa de sucesso para APIs REST
            int sucessosRest = sucessos.getOrDefault("API_REST_Faturas_Receber", 0) + 
                              sucessos.getOrDefault("API_REST_Faturas_Pagar", 0) + 
                              sucessos.getOrDefault("API_REST_Ocorrencias", 0);
            int falhasRest = falhas.getOrDefault("API_REST_Faturas_Receber", 0) + 
                            falhas.getOrDefault("API_REST_Faturas_Pagar", 0) + 
                            falhas.getOrDefault("API_REST_Ocorrencias", 0);
            
            double taxaSucesso = (sucessosRest + falhasRest) > 0 ? 
                (sucessosRest * 100.0) / (sucessosRest + falhasRest) : 98.5;
            apiRest.put("taxaSucesso", taxaSucesso);
        } else {
            apiRest.put("tempoResposta", 1250);
            apiRest.put("taxaSucesso", 98.5);
        }
        
        // Tabelas processadas pela API REST
        List<Map<String, Object>> tabelas = new ArrayList<>();
        
        Map<String, Object> faturas = new HashMap<>();
        faturas.put("nome", "Faturas a Receber");
        if (metricas != null) {
            Map<String, Integer> registros = metricas.getRegistrosPorEntidade();
            faturas.put("registros", registros.getOrDefault("faturas_receber", 0));
        } else {
            faturas.put("registros", 1247);
        }
        faturas.put("ultimaAtualizacao", LocalDateTime.now().minusMinutes(5).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        faturas.put("status", "SUCESSO");
        faturas.put("tempoProcessamento", 850);
        tabelas.add(faturas);
        
        Map<String, Object> faturasAPagar = new HashMap<>();
        faturasAPagar.put("nome", "Faturas a Pagar");
        if (metricas != null) {
            Map<String, Integer> registros = metricas.getRegistrosPorEntidade();
            faturasAPagar.put("registros", registros.getOrDefault("faturas_pagar", 0));
        } else {
            faturasAPagar.put("registros", 892);
        }
        faturasAPagar.put("ultimaAtualizacao", LocalDateTime.now().minusMinutes(6).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        faturasAPagar.put("status", "SUCESSO");
        faturasAPagar.put("tempoProcessamento", 720);
        tabelas.add(faturasAPagar);
        
        Map<String, Object> ocorrencias = new HashMap<>();
        ocorrencias.put("nome", "Ocorrências");
        if (metricas != null) {
            Map<String, Integer> registros = metricas.getRegistrosPorEntidade();
            ocorrencias.put("registros", registros.getOrDefault("ocorrencias", 0));
        } else {
            ocorrencias.put("registros", 156);
        }
        ocorrencias.put("ultimaAtualizacao", LocalDateTime.now().minusMinutes(4).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        ocorrencias.put("status", "SUCESSO");
        ocorrencias.put("tempoProcessamento", 320);
        tabelas.add(ocorrencias);
        
        apiRest.put("tabelas", tabelas);
        
        // Calcular total de registros
        int totalRegistros = tabelas.stream()
            .mapToInt(tabela -> (Integer) tabela.get("registros"))
            .sum();
        apiRest.put("totalRegistros", totalRegistros);
        
        return apiRest;
    }
    
    private Map<String, Object> criarStatusApiGraphQL() {
        Map<String, Object> apiGraphQL = new HashMap<>();
        apiGraphQL.put("nome", "API GraphQL");
        apiGraphQL.put("status", "OPERACIONAL");
        apiGraphQL.put("ultimaExecucao", LocalDateTime.now().minusMinutes(3).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        apiGraphQL.put("proximaExecucao", LocalDateTime.now().plusMinutes(27).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        apiGraphQL.put("tempoResposta", 980);
        apiGraphQL.put("taxaSucesso", 99.2);
        
        // Tabelas processadas pela API GraphQL
        List<Map<String, Object>> tabelas = new ArrayList<>();
        
        Map<String, Object> coletas = new HashMap<>();
        coletas.put("nome", "Coletas");
        coletas.put("registros", 543);
        coletas.put("ultimaAtualizacao", LocalDateTime.now().minusMinutes(3).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        coletas.put("status", "SUCESSO");
        coletas.put("tempoProcessamento", 650);
        tabelas.add(coletas);
        
        Map<String, Object> entregas = new HashMap<>();
        entregas.put("nome", "Entregas");
        entregas.put("registros", 789);
        entregas.put("ultimaAtualizacao", LocalDateTime.now().minusMinutes(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        entregas.put("status", "SUCESSO");
        entregas.put("tempoProcessamento", 420);
        tabelas.add(entregas);
        
        Map<String, Object> fretes = new HashMap<>();
        fretes.put("nome", "Fretes");
        fretes.put("registros", 234);
        fretes.put("ultimaAtualizacao", LocalDateTime.now().minusMinutes(4).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        fretes.put("status", "SUCESSO");
        fretes.put("tempoProcessamento", 380);
        tabelas.add(fretes);
        
        apiGraphQL.put("tabelas", tabelas);
        apiGraphQL.put("totalRegistros", 1566);
        
        return apiGraphQL;
    }
    
    private Map<String, Object> criarStatusApiDataExport() {
        Map<String, Object> apiDataExport = new HashMap<>();
        apiDataExport.put("nome", "API Data Export");
        apiDataExport.put("status", "OPERACIONAL");
        apiDataExport.put("ultimaExecucao", LocalDateTime.now().minusMinutes(7).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        apiDataExport.put("proximaExecucao", LocalDateTime.now().plusMinutes(23).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        apiDataExport.put("tempoResposta", 2150);
        apiDataExport.put("taxaSucesso", 96.8);
        
        // Tabelas processadas pela API Data Export (3 tabelas)
        List<Map<String, Object>> tabelas = new ArrayList<>();
        
        Map<String, Object> manifestos = new HashMap<>();
        manifestos.put("nome", "Manifestos");
        manifestos.put("registros", 156);
        manifestos.put("ultimaAtualizacao", LocalDateTime.now().minusMinutes(7).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        manifestos.put("status", "SUCESSO");
        manifestos.put("tempoProcessamento", 1200);
        tabelas.add(manifestos);
        
        Map<String, Object> localizacao = new HashMap<>();
        localizacao.put("nome", "Localização da Carga");
        localizacao.put("registros", 145);
        localizacao.put("ultimaAtualizacao", LocalDateTime.now().minusMinutes(6).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        localizacao.put("status", "SUCESSO");
        localizacao.put("tempoProcessamento", 950);
        tabelas.add(localizacao);
        
        Map<String, Object> cotacoes = new HashMap<>();
        cotacoes.put("nome", "Cotações");
        cotacoes.put("registros", 89);
        cotacoes.put("ultimaAtualizacao", LocalDateTime.now().minusMinutes(8).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        cotacoes.put("status", "SUCESSO");
        cotacoes.put("tempoProcessamento", 1100);
        tabelas.add(cotacoes);
        
        apiDataExport.put("tabelas", tabelas);
        apiDataExport.put("totalRegistros", 390);
        
        return apiDataExport;
    }
    

    
    private Map<String, Object> criarMetricasAvancadas() {
        Map<String, Object> metricasData = new HashMap<>();
        
        // Se o serviço de métricas estiver disponível, usa dados reais
        if (metricas != null) {
            // Usar dados reais do serviço
            metricasData.put("totalExecucoes", 1); // Assumindo uma execução por vez
            metricasData.put("sucessos", metricas.getTotalSucessos());
            metricasData.put("erros", metricas.getTotalFalhas());
            metricasData.put("avisos", 0); // Não temos avisos no sistema atual
            metricasData.put("taxaSucessoGeral", metricas.getTaxaSucessoGeral());
            metricasData.put("tempoMedioExecucao", metricas.getTempoMedioExecucao());
            metricasData.put("totalRegistrosProcessados", metricas.getTotalRegistrosProcessados());
        } else {
            // Dados simulados para quando o serviço não estiver disponível
            metricasData.put("totalExecucoes", 156);
            metricasData.put("sucessos", 149);
            metricasData.put("erros", 4);
            metricasData.put("avisos", 3);
            metricasData.put("taxaSucessoGeral", 95.5);
            metricasData.put("tempoMedioExecucao", 1850);
            metricasData.put("totalRegistrosProcessados", 4162);
        }
        
        // Métricas por período (mantém simulado por enquanto)
        Map<String, Object> metricas24h = new HashMap<>();
        metricas24h.put("execucoes", 48);
        if (metricas != null) {
            metricas24h.put("registros", metricas.getTotalRegistrosProcessados());
        } else {
            metricas24h.put("registros", 4162);
        }
        metricas24h.put("tempoTotal", "2h 15m");
        metricas24h.put("taxaSucesso", 97.9);
        metricasData.put("ultimas24h", metricas24h);
        
        Map<String, Object> metricas7d = new HashMap<>();
        metricas7d.put("execucoes", 336);
        metricas7d.put("registros", 28934);
        metricas7d.put("tempoTotal", "15h 42m");
        metricas7d.put("taxaSucesso", 96.2);
        metricasData.put("ultimos7dias", metricas7d);
        
        // Performance por API - usar dados reais se disponível
        Map<String, Object> performanceAPIs = new HashMap<>();
        if (metricas != null) {
            Map<String, Integer> sucessos = metricas.getSucessosPorApi();
            Map<String, Integer> falhas = metricas.getFalhasPorApi();
            Map<String, Duration> duracoes = metricas.getDuracaoOperacoes();
            
            // Calcular performance para API REST
            int sucessosRest = sucessos.getOrDefault("API_REST_Faturas_Receber", 0) + 
                              sucessos.getOrDefault("API_REST_Faturas_Pagar", 0) + 
                              sucessos.getOrDefault("API_REST_Ocorrencias", 0);
            int falhasRest = falhas.getOrDefault("API_REST_Faturas_Receber", 0) + 
                            falhas.getOrDefault("API_REST_Faturas_Pagar", 0) + 
                            falhas.getOrDefault("API_REST_Ocorrencias", 0);
            
            long tempoMedioRest = duracoes.entrySet().stream()
                .filter(entry -> entry.getKey().contains("faturas") || entry.getKey().contains("ocorrencias"))
                .mapToLong(entry -> entry.getValue().toMillis())
                .findFirst()
                .orElse(1250L);
            
            double taxaSucessoRest = (sucessosRest + falhasRest) > 0 ? 
                (sucessosRest * 100.0) / (sucessosRest + falhasRest) : 98.5;
            
            performanceAPIs.put("API REST", Map.of("tempoMedio", tempoMedioRest, "taxaSucesso", taxaSucessoRest));
            
            // GraphQL e Data Export mantêm valores simulados por enquanto
            performanceAPIs.put("API GraphQL", Map.of("tempoMedio", 980, "taxaSucesso", 99.2));
            performanceAPIs.put("API Data Export", Map.of("tempoMedio", 2150, "taxaSucesso", 96.8));
        } else {
            performanceAPIs.put("API REST", Map.of("tempoMedio", 1250, "taxaSucesso", 98.5));
            performanceAPIs.put("API GraphQL", Map.of("tempoMedio", 980, "taxaSucesso", 99.2));
            performanceAPIs.put("API Data Export", Map.of("tempoMedio", 2150, "taxaSucesso", 96.8));
        }
        metricasData.put("performanceAPIs", performanceAPIs);
        
        return metricasData;
    }
    
    private Map<String, Object> criarDadosGraficos() {
        Map<String, Object> graficos = new HashMap<>();
        
        // Dados para gráfico de execuções por hora (últimas 24h)
        List<Map<String, Object>> execucoesPorHora = new ArrayList<>();
        for (int i = 23; i >= 0; i--) {
            Map<String, Object> ponto = new HashMap<>();
            ponto.put("hora", LocalDateTime.now().minusHours(i).getHour());
            ponto.put("execucoes", (int) (Math.random() * 10) + 1);
            ponto.put("sucessos", (int) (Math.random() * 9) + 1);
            ponto.put("erros", (int) (Math.random() * 2));
            execucoesPorHora.add(ponto);
        }
        graficos.put("execucoesPorHora", execucoesPorHora);
        
        // Dados para gráfico de registros por API
        List<Map<String, Object>> registrosPorAPI = new ArrayList<>();
        
        if (metricas != null) {
            Map<String, Integer> registrosReais = metricas.getRegistrosPorEntidade();
            
            // Somar registros por tipo de API
            int restRegistros = registrosReais.getOrDefault("faturas_a_receber", 0) + 
                               registrosReais.getOrDefault("faturas_a_pagar", 0) + 
                               registrosReais.getOrDefault("ocorrencias_rest", 0);
            
            int graphqlRegistros = registrosReais.getOrDefault("ocorrencias_graphql", 0) + 
                                  registrosReais.getOrDefault("coletas", 0) + 
                                  registrosReais.getOrDefault("fretes_graphql", 0);
            
            int dataExportRegistros = registrosReais.getOrDefault("fretes_dataexport", 0) + 
                                     registrosReais.getOrDefault("manifestos", 0) + 
                                     registrosReais.getOrDefault("localizacao", 0);
            
            registrosPorAPI.add(Map.of("api", "API REST", "registros", restRegistros, "cor", "#4CAF50"));
            registrosPorAPI.add(Map.of("api", "API GraphQL", "registros", graphqlRegistros, "cor", "#2196F3"));
            registrosPorAPI.add(Map.of("api", "API Data Export", "registros", dataExportRegistros, "cor", "#FF9800"));
        } else {
            // Dados simulados quando métricas não estão disponíveis
            registrosPorAPI.add(Map.of("api", "API REST", "registros", 2295, "cor", "#4CAF50"));
            registrosPorAPI.add(Map.of("api", "API GraphQL", "registros", 1566, "cor", "#2196F3"));
            registrosPorAPI.add(Map.of("api", "API Data Export", "registros", 301, "cor", "#FF9800"));
        }
        graficos.put("registrosPorAPI", registrosPorAPI);
        
        // Dados para gráfico de tempo de resposta (últimos 30 pontos)
        List<Map<String, Object>> temposResposta = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            Map<String, Object> ponto = new HashMap<>();
            ponto.put("timestamp", LocalDateTime.now().minusMinutes(i * 2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            ponto.put("apiRest", 1200 + (int) (Math.random() * 300));
            ponto.put("apiGraphQL", 900 + (int) (Math.random() * 200));
            ponto.put("apiDataExport", 2000 + (int) (Math.random() * 400));
            temposResposta.add(ponto);
        }
        graficos.put("temposResposta", temposResposta);
        
        // Dados para gráfico de distribuição de status
        List<Map<String, Object>> distribuicaoStatus = new ArrayList<>();
        distribuicaoStatus.add(Map.of("status", "OPERACIONAL", "quantidade", 7, "cor", "#4CAF50"));
        distribuicaoStatus.add(Map.of("status", "AVISO_SEM_DADOS", "quantidade", 1, "cor", "#FF9800"));
        distribuicaoStatus.add(Map.of("status", "ERRO", "quantidade", 0, "cor", "#F44336"));
        graficos.put("distribuicaoStatus", distribuicaoStatus);
        
        return graficos;
    }
    
    private Map<String, Object> criarConsumoApis() {
        Map<String, Object> consumo = new HashMap<>();
        
        // Se o serviço de métricas estiver disponível, usa dados reais
        if (metricas != null) {
            Map<String, Integer> sucessosReais = metricas.getSucessosPorApi();
            
            // Somar todas as requisições relacionadas a cada tipo de API
            int restReqs = sucessosReais.getOrDefault("API_REST_Faturas_Receber", 0) + 
                          sucessosReais.getOrDefault("API_REST_Faturas_Pagar", 0) + 
                          sucessosReais.getOrDefault("API_REST_Ocorrencias", 0);
            
            int graphqlReqs = sucessosReais.getOrDefault("API_GraphQL_Ocorrencias", 0) + 
                             sucessosReais.getOrDefault("API_GraphQL_Coletas", 0) + 
                             sucessosReais.getOrDefault("API_GraphQL_Fretes", 0);
            
            int dataExportReqs = sucessosReais.getOrDefault("API_DataExport_Fretes", 0) + 
                                sucessosReais.getOrDefault("API_DataExport_Manifestos", 0) + 
                                sucessosReais.getOrDefault("API_DataExport_Localizacao", 0);
            
            // Dados reais das requisições por API
            consumo.put("apiRest", restReqs);
            consumo.put("apiGraphQL", graphqlReqs);
            consumo.put("apiDataExport", dataExportReqs);
            consumo.put("totalRequisicoes", restReqs + graphqlReqs + dataExportReqs);
            
            // Timestamp da última atualização das métricas
            consumo.put("ultimaAtualizacao", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        } else {
            // Dados simulados para quando o serviço não estiver disponível
            consumo.put("apiRest", 15);
            consumo.put("apiGraphQL", 2);
            consumo.put("apiDataExport", 5);
            consumo.put("totalRequisicoes", 22);
            consumo.put("ultimaAtualizacao", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        
        // Informações adicionais sobre o consumo
        Map<String, Object> detalhes = new HashMap<>();
        detalhes.put("descricao", "Número de requisições HTTP realizadas por cada cliente de API na última execução");
        detalhes.put("unidade", "requisições");
        detalhes.put("periodo", "última execução");
        consumo.put("detalhes", detalhes);
        
        return consumo;
    }
    
    private List<Map<String, Object>> criarHistoricoExecucoes() {
        List<Map<String, Object>> historico = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            Map<String, Object> execucao = new HashMap<>();
            execucao.put("timestamp", LocalDateTime.now().minusMinutes(i * 30).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            execucao.put("duracao", 1800 + (int) (Math.random() * 600));
            execucao.put("registros", 4000 + (int) (Math.random() * 500));
            execucao.put("status", i == 0 ? "EM_ANDAMENTO" : "CONCLUIDA");
            execucao.put("sucessos", 3);
            execucao.put("erros", i % 5 == 0 ? 1 : 0);
            historico.add(execucao);
        }
        
        return historico;
    }
    
    private List<Map<String, Object>> criarAlertas() {
        List<Map<String, Object>> alertas = new ArrayList<>();
        
        Map<String, Object> alerta1 = new HashMap<>();
        alerta1.put("tipo", "AVISO");
        alerta1.put("mensagem", "API Data Export - Cotações sem dados há 2 execuções");
        alerta1.put("timestamp", LocalDateTime.now().minusMinutes(15).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        alerta1.put("severidade", "MEDIA");
        alertas.add(alerta1);
        
        Map<String, Object> alerta2 = new HashMap<>();
        alerta2.put("tipo", "INFO");
        alerta2.put("mensagem", "Sistema funcionando normalmente - 97.9% de sucesso nas últimas 24h");
        alerta2.put("timestamp", LocalDateTime.now().minusMinutes(5).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        alerta2.put("severidade", "BAIXA");
        alertas.add(alerta2);
        
        return alertas;
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return ResponseEntity.ok(health);
    }
}