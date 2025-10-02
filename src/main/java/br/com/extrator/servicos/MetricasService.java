package br.com.extrator.servicos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serviço singleton para coleta e gerenciamento de métricas de execução
 * Monitora sucessos, falhas, registros processados e tempos de execução
 */
public class MetricasService {
    private static final Logger logger = LoggerFactory.getLogger(MetricasService.class);
    private static volatile MetricasService instancia;
    
    // Contadores de sucessos e falhas por API
    private final Map<String, Integer> sucessosPorApi = new ConcurrentHashMap<>();
    private final Map<String, Integer> falhasPorApi = new ConcurrentHashMap<>();
    
    // Registros processados por entidade
    private final Map<String, Integer> registrosPorEntidade = new ConcurrentHashMap<>();
    
    // Controle de timers para operações
    private final Map<String, LocalDateTime> timersInicio = new ConcurrentHashMap<>();
    private final Map<String, Duration> duracaoOperacoes = new ConcurrentHashMap<>();
    
    // Timestamp de início da coleta de métricas
    private final LocalDateTime inicioColeta;
    
    /**
     * Construtor privado para implementação singleton
     */
    private MetricasService() {
        this.inicioColeta = LocalDateTime.now();
        logger.info("MetricasService inicializado em {}", 
                   inicioColeta.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
    }
    
    /**
     * Obtém a instância singleton do MetricasService
     * Thread-safe usando double-checked locking
     * 
     * @return Instância única do MetricasService
     */
    public static MetricasService getInstance() {
        if (instancia == null) {
            synchronized (MetricasService.class) {
                if (instancia == null) {
                    instancia = new MetricasService();
                }
            }
        }
        return instancia;
    }
    
    /**
     * Registra um sucesso para uma API específica
     * 
     * @param nomeApi Nome da API (ex: "REST", "GraphQL", "DataExport")
     */
    public void registrarSucesso(String nomeApi) {
        sucessosPorApi.merge(nomeApi, 1, Integer::sum);
        logger.debug("Sucesso registrado para API: {}", nomeApi);
    }
    
    /**
     * Registra uma falha para uma API específica
     * 
     * @param nomeApi Nome da API (ex: "REST", "GraphQL", "DataExport")
     */
    public void registrarFalha(String nomeApi) {
        falhasPorApi.merge(nomeApi, 1, Integer::sum);
        logger.debug("Falha registrada para API: {}", nomeApi);
    }
    
    /**
     * Adiciona registros processados para uma entidade específica
     * 
     * @param nomeEntidade Nome da entidade (ex: "faturas", "ocorrencias", "coletas")
     * @param quantidade Quantidade de registros processados
     */
    public void adicionarRegistrosProcessados(String nomeEntidade, int quantidade) {
        registrosPorEntidade.merge(nomeEntidade, quantidade, Integer::sum);
        logger.debug("Registrados {} registros para entidade: {}", quantidade, nomeEntidade);
    }
    
    /**
     * Inicia um timer para uma operação específica
     * 
     * @param nomeOperacao Nome da operação (ex: "extracao_faturas_receber")
     */
    public void iniciarTimer(String nomeOperacao) {
        timersInicio.put(nomeOperacao, LocalDateTime.now());
        logger.debug("Timer iniciado para operação: {}", nomeOperacao);
    }
    
    /**
     * Para um timer e calcula a duração da operação
     * 
     * @param nomeOperacao Nome da operação
     */
    public void pararTimer(String nomeOperacao) {
        LocalDateTime inicio = timersInicio.remove(nomeOperacao);
        if (inicio != null) {
            Duration duracao = Duration.between(inicio, LocalDateTime.now());
            duracaoOperacoes.put(nomeOperacao, duracao);
            logger.debug("Timer parado para operação: {} - Duração: {}ms", 
                        nomeOperacao, duracao.toMillis());
        } else {
            logger.warn("Tentativa de parar timer não iniciado: {}", nomeOperacao);
        }
    }
    
    /**
     * Obtém a duração total da coleta de métricas
     * 
     * @return Duração desde o início da coleta
     */
    public Duration getDuracaoTotal() {
        return Duration.between(inicioColeta, LocalDateTime.now());
    }
    
    /**
     * Obtém o total de sucessos registrados
     * 
     * @return Soma de todos os sucessos
     */
    public int getTotalSucessos() {
        return sucessosPorApi.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    /**
     * Obtém o total de falhas registradas
     * 
     * @return Soma de todas as falhas
     */
    public int getTotalFalhas() {
        return falhasPorApi.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    /**
     * Obtém o total de registros processados
     * 
     * @return Soma de todos os registros processados
     */
    public int getTotalRegistrosProcessados() {
        return registrosPorEntidade.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    /**
     * Gera um relatório completo das métricas coletadas
     * 
     * @return String formatada com o relatório de métricas
     */
    public String gerarRelatorio() {
        StringBuilder relatorio = new StringBuilder();
        
        relatorio.append("=".repeat(60)).append("\n");
        relatorio.append("MÉTRICAS DE EXECUÇÃO").append("\n");
        relatorio.append("=".repeat(60)).append("\n");
        
        // Informações gerais
        Duration duracaoTotal = getDuracaoTotal();
        relatorio.append("Duração Total: ").append(formatarDuracao(duracaoTotal)).append("\n");
        relatorio.append("Início da Coleta: ").append(inicioColeta.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append("\n");
        relatorio.append("Total de Operações: ").append(getTotalSucessos() + getTotalFalhas()).append("\n");
        relatorio.append("\n");
        
        // Sucessos e Falhas por API
        if (!sucessosPorApi.isEmpty() || !falhasPorApi.isEmpty()) {
            relatorio.append("SUCESSOS E FALHAS POR API:").append("\n");
            
            // Coleta todas as APIs mencionadas
            Map<String, String> statusPorApi = new HashMap<>();
            sucessosPorApi.forEach((api, sucessos) -> {
                int falhas = falhasPorApi.getOrDefault(api, 0);
                double taxaSucesso = (sucessos * 100.0) / (sucessos + falhas);
                statusPorApi.put(api, String.format("   %-12s: %d sucessos, %d falhas (%.1f%% sucesso)", 
                                                   api, sucessos, falhas, taxaSucesso));
            });
            
            falhasPorApi.forEach((api, falhas) -> {
                if (!sucessosPorApi.containsKey(api)) {
                    statusPorApi.put(api, String.format("   %-12s: %d sucessos, %d falhas (%.1f%% sucesso)", 
                                                       api, 0, falhas, 0.0));
                }
            });
            
            statusPorApi.values().forEach(linha -> relatorio.append(linha).append("\n"));
            relatorio.append("\n");
        }
        
        // Registros processados por entidade
        if (!registrosPorEntidade.isEmpty()) {
            relatorio.append("REGISTROS PROCESSADOS POR ENTIDADE:").append("\n");
            registrosPorEntidade.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> relatorio.append(String.format("   %-20s: %,d registros%n", 
                                                                 entry.getKey(), entry.getValue())));
            relatorio.append(String.format("   %-20s: %,d registros%n", "TOTAL", getTotalRegistrosProcessados()));
            relatorio.append("\n");
        }
        
        // Tempos de execução por operação
        if (!duracaoOperacoes.isEmpty()) {
            relatorio.append("TEMPOS DE EXECUÇÃO POR OPERAÇÃO:").append("\n");
            duracaoOperacoes.entrySet().stream()
                .sorted(Map.Entry.<String, Duration>comparingByValue().reversed())
                .forEach(entry -> relatorio.append(String.format("   %-25s: %s%n", 
                                                                 entry.getKey(), formatarDuracao(entry.getValue()))));
            relatorio.append("\n");
        }
        
        // Resumo de performance
        relatorio.append("RESUMO DE PERFORMANCE:").append("\n");
        if (getTotalRegistrosProcessados() > 0 && duracaoTotal.toMillis() > 0) {
            double registrosPorSegundo = getTotalRegistrosProcessados() / (duracaoTotal.toMillis() / 1000.0);
            relatorio.append(String.format("   Taxa de Processamento: %.2f registros/segundo%n", registrosPorSegundo));
        }
        
        if (getTotalSucessos() + getTotalFalhas() > 0) {
            double taxaSucessoGeral = (getTotalSucessos() * 100.0) / (getTotalSucessos() + getTotalFalhas());
            relatorio.append(String.format("   Taxa de Sucesso Geral: %.1f%%%n", taxaSucessoGeral));
        }
        
        relatorio.append("=".repeat(60)).append("\n");
        
        return relatorio.toString();
    }
    
    /**
     * Formata uma duração para exibição amigável
     * 
     * @param duracao Duração a ser formatada
     * @return String formatada (ex: "2m 30s", "45s", "1.2s")
     */
    private String formatarDuracao(Duration duracao) {
        long totalSegundos = duracao.getSeconds();
        long minutos = totalSegundos / 60;
        long segundos = totalSegundos % 60;
        long milissegundos = duracao.toMillis() % 1000;
        
        if (minutos > 0) {
            return String.format("%dm %ds", minutos, segundos);
        } else if (segundos > 0) {
            return String.format("%ds", segundos);
        } else {
            return String.format("%.1fs", milissegundos / 1000.0);
        }
    }
    
    /**
     * Reseta todas as métricas coletadas
     * Útil para testes ou reinicializações
     */
    public void resetar() {
        sucessosPorApi.clear();
        falhasPorApi.clear();
        registrosPorEntidade.clear();
        timersInicio.clear();
        duracaoOperacoes.clear();
        logger.info("Métricas resetadas");
    }
}